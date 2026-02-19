package com.weelo.logistics.offline

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weelo.logistics.data.remote.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    /** Current availability state (true = online, false = offline) */
    private val _isAvailable = MutableStateFlow(false) // Default: OFFLINE (cold start protection)
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

    /** True while syncWithBackend() is in-flight (prevents concurrent syncs from network monitor) */
    private val _isSyncing = MutableStateFlow(false)

    /** Monotonic counter incremented on every toggle. Used to detect stale init-sync GET responses. */
    private val _toggleCounter = java.util.concurrent.atomic.AtomicInteger(0)

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
        // 1. Load saved state from DataStore (local persistence)
        // NOTE: The collector ONLY reads saved state — it does NOT call syncWithBackend.
        // This eliminates the race condition where the collector's sync call steals the
        // distributed lock from the toggle's own sync call → 409 → snap-back revert.
        scope.launch {
            dataStore.data.collect { prefs ->
                val savedState = prefs[KEY_IS_AVAILABLE] ?: false // Default: OFFLINE
                // Only update if not currently toggling (prevent overwrite of optimistic state)
                if (!_isToggling.value) {
                    _isAvailable.value = savedState
                }
            }
        }

        // 2. Fetch real backend state ONCE on init (cold start sync)
        // This ensures the UI matches the backend after app restart.
        // The local DataStore may have stale state if app was killed.
        // GUARD: Uses _toggleCounter to detect stale responses. If user toggles
        // between GET request and GET response, the response is discarded because
        // it contains pre-toggle state that would overwrite the toggle's new state.
        scope.launch {
            try {
                // Small delay to let auth token load
                delay(500)

                // If user already toggled during the 500ms delay, skip this fetch
                if (_isToggling.value || _isSyncing.value) {
                    timber.log.Timber.i("🔄 Skipping init sync — toggle/sync in progress")
                    return@launch
                }

                // Capture toggle counter BEFORE sending GET request.
                // If any toggle happens while GET is in-flight, counter will change
                // and we discard the stale response.
                val counterBeforeGet = _toggleCounter.get()

                val response = RetrofitClient.transporterApi.getAvailability(
                    RetrofitClient.getAuthHeader()
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val backendState = response.body()?.data?.isAvailable ?: false
                    timber.log.Timber.i("🔄 Backend availability sync: ${if (backendState) "ONLINE" else "OFFLINE"}")
                    
                    // CRITICAL: Only write if NO toggle happened since we sent the GET.
                    // Without this check, a stale GET response (isAvailable:true from before toggle)
                    // would overwrite the toggle's new state (isAvailable:false) → snap back to ONLINE.
                    val counterAfterGet = _toggleCounter.get()
                    if (counterAfterGet != counterBeforeGet) {
                        timber.log.Timber.i("🔄 Init sync discarded — toggle happened during GET (counter $counterBeforeGet→$counterAfterGet)")
                        return@launch
                    }

                    // Also check active toggle/sync as additional safety
                    if (!_isToggling.value && !_isSyncing.value) {
                        _isAvailable.value = backendState
                        dataStore.edit { prefs ->
                            prefs[KEY_IS_AVAILABLE] = backendState
                            prefs[KEY_PENDING_SYNC] = false
                        }
                    }
                }
            } catch (e: Exception) {
                timber.log.Timber.w("⚠️ Backend availability sync failed: ${e.message}")
                // Non-critical — DataStore state is used as fallback
            }
        }

        // 3. Auto-sync pending changes when network becomes available
        // GUARD: Skip if toggle is active (_isToggling) OR sync is already in-flight (_isSyncing)
        // This prevents the network monitor from racing with the toggle's own syncWithBackend(),
        // which would cause a 409 (distributed lock conflict) → revert → snap back to ONLINE.
        scope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline && !_isToggling.value && !_isSyncing.value) {
                    val pendingSync = dataStore.data.first()[KEY_PENDING_SYNC] ?: false
                    if (pendingSync) {
                        timber.log.Timber.i("📶 Network available — syncing pending availability status")
                        syncWithBackend(_isAvailable.value)
                    }
                }
            }
        }

        timber.log.Timber.i("✅ AvailabilityManager initialized")
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
            _isAvailable.value = newState

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
                _isAvailable.value = previousState
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
        if (_isAvailable.value == available) {
            // Idempotent — no change needed
            return
        }
        _isAvailable.value = available
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
        // Concurrency guard — prevent concurrent syncs causing 409 lock conflicts
        if (_isSyncing.value) {
            timber.log.Timber.w("⚠️ Sync already in-flight — skipping concurrent call")
            return false
        }

        if (!networkMonitor.isCurrentlyOnline()) {
            timber.log.Timber.w("📵 Offline — availability will sync when online")
            return false
        }

        _isSyncing.value = true
        try {
            val response = RetrofitClient.transporterApi.updateAvailability(
                RetrofitClient.getAuthHeader(),
                mapOf("isAvailable" to available)
            )

            return when {
                // ✅ Success (200)
                response.isSuccessful && response.body()?.success == true -> {
                    dataStore.edit { prefs ->
                        prefs[KEY_PENDING_SYNC] = false
                    }

                    // Read cooldown from backend response
                    val cooldownMs = response.body()?.data?.cooldownMs ?: 0L
                    _cooldownRemainingMs.value = cooldownMs

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
            timber.log.Timber.e("❌ Network error during sync: ${e.message}")
            // Network error — keep pending sync, will retry when online
            // Don't set _toggleError for network errors (keep optimistic state)
            return false
        } finally {
            _isSyncing.value = false
        }
    }

    // ==========================================================================
    // UTILITY METHODS
    // ==========================================================================

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
