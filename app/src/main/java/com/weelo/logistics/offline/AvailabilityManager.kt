package com.weelo.logistics.offline

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.utils.TransporterOnlineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * =============================================================================
 * AVAILABILITY MANAGER - Production-Grade Online/Offline Toggle
 * =============================================================================
 *
 * PRODUCTION FEATURES:
 * - Optimistic UI with revert on failure
 * - 2-second frontend cooldown (prevents UI spam)
 * - Backend cooldown awareness (5s from backend)
 * - 429 / 409 error handling with user-friendly messages
 * - DataStore persistence for app restart
 * - Offline queue with auto-retry on network recovery
 *
 * FLOW:
 * 1. Transporter presses toggle → immediate UI update (optimistic)
 * 2. 2-second frontend cooldown starts (button disabled)
 * 3. API call to backend: PUT /transporter/availability
 * 4. On success → persist state, clear pending sync
 * 5. On 429/409 → revert UI to previous state, show error
 * 6. On network error → keep optimistic state, queue retry
 *
 * SCALABILITY:
 * - Frontend cooldown reduces API calls by ~80% (spam prevention)
 * - Idempotent calls return instantly from backend (zero DB writes)
 * - Rate limit protects backend even without frontend guard
 *
 * =============================================================================
 */

private val Context.availabilityDataStore by preferencesDataStore(name = "availability_prefs")

enum class AvailabilityState {
    UNKNOWN,
    OFFLINE,
    ONLINE
}

class AvailabilityManager private constructor(
    context: Context
) {
    // Use applicationContext to prevent Activity leak (ViewModel outlives Activity)
    private val appContext: Context = context.applicationContext
    private val dataStore = appContext.availabilityDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val networkMonitor = NetworkMonitor.getInstance(appContext)

    // Preference keys
    private val KEY_IS_AVAILABLE = booleanPreferencesKey("is_available")
    private val KEY_LAST_UPDATED = longPreferencesKey("availability_last_updated")
    private val KEY_PENDING_SYNC = booleanPreferencesKey("availability_pending_sync")

    // ==========================================================================
    // PUBLIC STATE FLOWS — Observed by UI (Compose collectAsState)
    // ==========================================================================

    /** Tri-state availability to avoid cold-start OFFLINE race drops. */
    private val _availabilityState = MutableStateFlow(AvailabilityState.UNKNOWN)
    val availabilityState: StateFlow<AvailabilityState> = _availabilityState.asStateFlow()

    /** Compatibility boolean availability (true = online, false = offline/unknown) */
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /** True while API call is in-flight OR during frontend cooldown */
    private val _isToggling = MutableStateFlow(false)
    val isToggling: StateFlow<Boolean> = _isToggling.asStateFlow()

    /** User-friendly error message (null = no error) */
    private val _toggleError = MutableStateFlow<String?>(null)
    val toggleError: StateFlow<String?> = _toggleError.asStateFlow()

    /** Backend cooldown remaining in ms (for countdown timer in UI) */
    private val _cooldownRemainingMs = MutableStateFlow(0L)
    val cooldownRemainingMs: StateFlow<Long> = _cooldownRemainingMs.asStateFlow()

    // Legacy aliases for backward compatibility
    val isSyncing: StateFlow<Boolean> = _isToggling.asStateFlow()
    val syncError: StateFlow<String?> = _toggleError.asStateFlow()

    /**
     * Mutex guard for syncWithBackend() — truly atomic, coroutine-safe.
     * Replaces non-atomic _isSyncing StateFlow check which had a TOCTOU race:
     *   Thread A: reads _isSyncing=false → Thread B: reads _isSyncing=false →
     *   Both proceed → 409 lock conflict on backend → one reverts toggle.
     * Mutex.tryLock() is atomic — only one coroutine wins, others return false immediately.
     */
    private val syncMutex = Mutex()

    /** Monotonic counter incremented on every toggle. Used to detect stale init-sync GET responses. */
    private val _toggleCounter = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var initAvailabilityResolved = false

    // ==========================================================================
    // CONSTANTS
    // ==========================================================================
    companion object {
        /** Frontend cooldown — minimum 2s between toggle presses */
        private const val FRONTEND_COOLDOWN_MS = 2000L

        @Volatile
        private var instance: AvailabilityManager? = null

        fun getInstance(context: Context): AvailabilityManager {
            return instance ?: synchronized(this) {
                instance ?: AvailabilityManager(context.applicationContext).also {
                    instance = it
                    it.initialize()
                }
            }
        }
    }

    // ==========================================================================
    // INITIALIZATION — Load saved state, fetch backend truth, auto-sync
    // ==========================================================================

    private fun initialize() {
        // Keep startup in UNKNOWN until backend truth (or fallback) resolves.
        _availabilityState.value = AvailabilityState.UNKNOWN
        initAvailabilityResolved = false

        // 1) Load saved state for immediate UI continuity.
        scope.launch {
            dataStore.data.collect { prefs ->
                val savedState = prefs[KEY_IS_AVAILABLE] ?: false
                if (!_isToggling.value) {
                    val previous = _isAvailable.value
                    _isAvailable.value = savedState
                    if (initAvailabilityResolved) {
                        updateAvailabilityState(savedState)
                        if (previous != savedState) {
                            updateHeartbeatForAvailability(savedState)
                        }
                    }
                }
            }
        }

        // 2) Resolve startup availability from backend once.
        scope.launch {
            try {
                delay(500)
                if (_isToggling.value || syncMutex.isLocked) {
                    timber.log.Timber.i("🔄 Skipping init sync — toggle/sync in progress")
                    return@launch
                }

                val counterBeforeGet = _toggleCounter.get()
                val response = RetrofitClient.transporterApi.getAvailability(
                    RetrofitClient.getAuthHeader()
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val backendState = response.body()?.data?.isAvailable ?: false
                    val counterAfterGet = _toggleCounter.get()
                    if (counterAfterGet != counterBeforeGet) {
                        timber.log.Timber.i("🔄 Init sync discarded — toggle happened during GET (counter $counterBeforeGet→$counterAfterGet)")
                        return@launch
                    }
                    if (!_isToggling.value && !syncMutex.isLocked) {
                        setAvailabilityInternal(backendState)
                        updateHeartbeatForAvailability(backendState)
                        dataStore.edit { prefs ->
                            prefs[KEY_IS_AVAILABLE] = backendState
                            prefs[KEY_PENDING_SYNC] = false
                        }
                        timber.log.Timber.i("🔄 Backend availability resolved: ${if (backendState) "ONLINE" else "OFFLINE"}")
                    }
                }
            } catch (e: Exception) {
                timber.log.Timber.w("⚠️ Backend availability sync failed: ${e.message}")
                // Fallback to persisted UI state (do not remain UNKNOWN forever).
                updateAvailabilityState(_isAvailable.value)
            } finally {
                initAvailabilityResolved = true
                if (_availabilityState.value == AvailabilityState.UNKNOWN) {
                    updateAvailabilityState(_isAvailable.value)
                }
                updateHeartbeatForAvailability(_isAvailable.value)
            }
        }

        // 3) Auto-sync pending changes when network becomes available.
        scope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline && !_isToggling.value && !syncMutex.isLocked) {
                    val pendingSync = dataStore.data.first()[KEY_PENDING_SYNC] ?: false
                    if (pendingSync) {
                        timber.log.Timber.i("📶 Network available — syncing pending availability status")
                        syncWithBackend(_isAvailable.value)
                    }
                }
            }
        }

        timber.log.Timber.i("✅ AvailabilityManager initialized (startup state=${_availabilityState.value})")
    }

    // ==========================================================================
    // TOGGLE — Production-grade with optimistic UI + revert on failure
    // ==========================================================================

    /**
     * Toggle availability ON ↔ OFF with full production protection.
     *
     * FLOW:
     * 1. Guard: reject if already toggling (cooldown active)
     * 2. Optimistic UI: flip state immediately
     * 3. Persist locally (DataStore)
     * 4. API call to backend
     * 5. On success: confirm state
     * 6. On 429/409: revert state + show user-friendly error
     * 7. On network error: keep optimistic state, queue retry
     * 8. 2s frontend cooldown before re-enabling button
     */
    suspend fun toggleAvailability() {
        // Guard: reject during cooldown
        if (_isToggling.value) {
            timber.log.Timber.w("⚠️ Toggle rejected — cooldown active")
            return
        }

        val previousState = _isAvailable.value
        val newState = !previousState

        timber.log.Timber.i("🔄 Toggle: ${if (previousState) "ONLINE→OFFLINE" else "OFFLINE→ONLINE"}")

        // Step 1: Start cooldown (disables button)
        _isToggling.value = true
        _toggleError.value = null
        _toggleCounter.incrementAndGet() // Invalidate any in-flight init-sync GET

        try {
            // Step 2: Optimistic UI update (instant feedback)
            setAvailabilityInternal(newState)
            updateHeartbeatForAvailability(newState)

            // Step 3: Persist locally
            dataStore.edit { prefs ->
                prefs[KEY_IS_AVAILABLE] = newState
                prefs[KEY_LAST_UPDATED] = System.currentTimeMillis()
                prefs[KEY_PENDING_SYNC] = true
            }

            // Step 4: Sync with backend
            val syncSuccess = syncWithBackend(newState)

            // Step 5: If backend rejected, revert to previous state.
            // Network errors (_toggleError == null) keep optimistic state for retry.
            // Any server error (_toggleError != null) means toggle didn't happen — revert.
            if (!syncSuccess && _toggleError.value != null) {
                timber.log.Timber.w("↩️ Reverting toggle — backend rejected: ${_toggleError.value}")
                setAvailabilityInternal(previousState)
                updateHeartbeatForAvailability(previousState)
                dataStore.edit { prefs ->
                    prefs[KEY_IS_AVAILABLE] = previousState
                    prefs[KEY_PENDING_SYNC] = false
                }
            }

            // Step 6: Frontend cooldown (2s minimum before re-enabling)
            delay(FRONTEND_COOLDOWN_MS)
        } finally {
            // CRITICAL: Always reset _isToggling, even if coroutine is cancelled
            // (e.g., screen recompose, navigation away, or app backgrounding).
            // Without this, _isToggling stays true forever → toggle permanently locked.
            _isToggling.value = false
        }
    }

    /**
     * Set availability to a specific state.
     * Used by cold start sync and external callers.
     */
    suspend fun setAvailability(available: Boolean) {
        if (_availabilityState.value != AvailabilityState.UNKNOWN && _isAvailable.value == available) {
            // Idempotent — no change needed
            return
        }
        setAvailabilityInternal(available)
        updateHeartbeatForAvailability(available)
        dataStore.edit { prefs ->
            prefs[KEY_IS_AVAILABLE] = available
            prefs[KEY_LAST_UPDATED] = System.currentTimeMillis()
            prefs[KEY_PENDING_SYNC] = true
        }
        syncWithBackend(available)
    }

    // ==========================================================================
    // BACKEND SYNC — Handles all HTTP responses including 429/409
    // ==========================================================================

    /**
     * Sync availability with backend.
     *
     * CONCURRENCY GUARD: Only one sync can run at a time. If a sync is already
     * in-flight (from toggle or network monitor), subsequent calls return false
     * immediately. This prevents the 409 distributed lock conflict that caused
     * the "snap back to ONLINE" bug.
     *
     * @param available Target state to sync
     * @return true if sync succeeded, false if failed
     */
    private suspend fun syncWithBackend(available: Boolean): Boolean {
        // Atomic concurrency guard — Mutex.tryLock() is TOCTOU-safe unlike StateFlow.value check.
        // If another coroutine holds the lock, we skip immediately (no 409 from concurrent PUT).
        if (!syncMutex.tryLock()) {
            timber.log.Timber.w("⚠️ Sync already in-flight — skipping concurrent call")
            return false
        }

        try {
            if (!networkMonitor.isCurrentlyOnline()) {
                timber.log.Timber.w("📵 Offline — availability will sync when online")
                return false
            }

            // Snapshot counter BEFORE the API call. If the user toggles during the in-flight
            // request, the counter increments. We only clear KEY_PENDING_SYNC when the counter
            // still matches — preventing a mid-flight toggle from being silently lost.
            val counterSnapshot = _toggleCounter.get()

            val response = RetrofitClient.transporterApi.updateAvailability(
                RetrofitClient.getAuthHeader(),
                mapOf("isAvailable" to available)
            )

            return when {
                // ✅ Success (200)
                response.isSuccessful && response.body()?.success == true -> {
                    // Only clear pending if no new toggle happened mid-flight
                    if (_toggleCounter.get() == counterSnapshot) {
                        dataStore.edit { prefs ->
                            prefs[KEY_PENDING_SYNC] = false
                        }
                    } else {
                        timber.log.Timber.w("⚠️ State changed mid-sync — keeping pending flag for next sync")
                    }

                    // Read cooldown from backend response
                    val cooldownMs = response.body()?.data?.cooldownMs ?: 0L
                    _cooldownRemainingMs.value = cooldownMs

                    setAvailabilityInternal(available)
                    updateHeartbeatForAvailability(available)

                    val idempotent = response.body()?.data?.idempotent == true
                    timber.log.Timber.i("✅ Availability synced: ${if (available) "ONLINE" else "OFFLINE"}${if (idempotent) " (idempotent)" else ""}")
                    true
                }

                // ⏳ Rate limited (429)
                response.code() == 429 -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    timber.log.Timber.w("⏳ Toggle rate limited (429): $errorBody")
                    _toggleError.value = "429:Please wait a moment before toggling again"
                    false
                }

                // 🔒 Lock conflict (409)
                response.code() == 409 -> {
                    timber.log.Timber.w("🔒 Toggle conflict (409)")
                    _toggleError.value = "409:Update in progress, please wait"
                    false
                }

                // 🔑 Unauthorized (401)
                response.code() == 401 -> {
                    timber.log.Timber.e("🔑 Unauthorized (401) — token may be expired")
                    _toggleError.value = "Session expired. Please login again."
                    false
                }

                // 🚫 Not found (404)
                response.code() == 404 -> {
                    timber.log.Timber.e("🚫 Transporter not found (404)")
                    _toggleError.value = "Account not found. Please login again."
                    false
                }

                // ❌ Other errors
                else -> {
                    val error = response.body()?.error?.message
                        ?: response.errorBody()?.string()
                        ?: "Failed to update availability"
                    timber.log.Timber.e("❌ Toggle failed (${response.code()}): $error")
                    _toggleError.value = error
                    false
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            timber.log.Timber.e("❌ Network error during sync: ${e.message}")
            // Network error — keep pending sync, will retry when online
            // Don't set _toggleError for network errors (keep optimistic state)
            return false
        } finally {
            // Lock owner releases exactly once from this scope.
            syncMutex.unlock()
        }
    }

    // ==========================================================================
    // UTILITY METHODS
    // ==========================================================================

    private fun setAvailabilityInternal(available: Boolean) {
        _isAvailable.value = available
        updateAvailabilityState(available)
    }

    private fun updateAvailabilityState(available: Boolean) {
        _availabilityState.value = if (available) AvailabilityState.ONLINE else AvailabilityState.OFFLINE
    }

    private fun updateHeartbeatForAvailability(available: Boolean) {
        if (available) {
            TransporterOnlineService.start(appContext)
        } else {
            TransporterOnlineService.stop(appContext)
        }
    }

    /** Force sync current state with backend */
    suspend fun forceSync() {
        syncWithBackend(_isAvailable.value)
    }

    /** Check if local state is synced with backend */
    suspend fun isSynced(): Boolean {
        return !(dataStore.data.first()[KEY_PENDING_SYNC] ?: false)
    }

    /** Get last updated timestamp */
    suspend fun getLastUpdated(): Long {
        return dataStore.data.first()[KEY_LAST_UPDATED] ?: 0L
    }

    /** Clear error message (called by UI after showing snackbar) */
    fun clearError() {
        _toggleError.value = null
    }
}
