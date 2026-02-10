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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * =============================================================================
 * AVAILABILITY MANAGER - Online/Offline Toggle for Transporters & Drivers
 * =============================================================================
 * 
 * FEATURE:
 * - Toggle button to go "Online" or "Offline"
 * - When OFFLINE: No broadcasts received, even if vehicle matches
 * - When ONLINE: Broadcasts received normally
 * - Status synced with backend
 * - Persisted locally for app restart
 * 
 * FLOW:
 * 1. Transporter toggles to OFFLINE
 * 2. App updates local state immediately (for instant UI feedback)
 * 3. App sends status to backend (async)
 * 4. Backend marks transporter as unavailable
 * 5. Backend excludes unavailable transporters from broadcasts
 * 
 * SCALABILITY:
 * - Local-first for instant response
 * - Async sync with backend
 * - Retry logic for failed syncs
 * 
 * =============================================================================
 */

private val Context.availabilityDataStore by preferencesDataStore(name = "availability_prefs")

class AvailabilityManager private constructor(
    private val context: Context
) {
    private val dataStore = context.availabilityDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val networkMonitor = NetworkMonitor.getInstance(context)
    
    // Preference keys
    private val KEY_IS_AVAILABLE = booleanPreferencesKey("is_available")
    private val KEY_LAST_UPDATED = longPreferencesKey("availability_last_updated")
    private val KEY_PENDING_SYNC = booleanPreferencesKey("availability_pending_sync")
    
    // State flows
    private val _isAvailable = MutableStateFlow(true) // Default: available
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()
    
    companion object {
        private const val TAG = "AvailabilityManager"
        
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
    
    /**
     * Initialize - load saved state
     */
    private fun initialize() {
        scope.launch {
            // Load saved availability state
            dataStore.data.collect { prefs ->
                val savedState = prefs[KEY_IS_AVAILABLE] ?: true
                _isAvailable.value = savedState
                
                // Check if there's a pending sync
                val pendingSync = prefs[KEY_PENDING_SYNC] ?: false
                if (pendingSync && networkMonitor.isCurrentlyOnline()) {
                    syncWithBackend(savedState)
                }
            }
        }
        
        // Auto-sync when network becomes available
        scope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    val pendingSync = dataStore.data.first()[KEY_PENDING_SYNC] ?: false
                    if (pendingSync) {
                        timber.log.Timber.i("📶 Network available - syncing pending availability status")
                        syncWithBackend(_isAvailable.value)
                    }
                }
            }
        }
        
        timber.log.Timber.i("✅ AvailabilityManager initialized")
    }
    
    /**
     * Toggle availability ON/OFF
     * 
     * @param available true = online (receive broadcasts), false = offline (no broadcasts)
     */
    suspend fun setAvailability(available: Boolean) {
        timber.log.Timber.i("🔄 Setting availability: ${if (available) "ONLINE ✅" else "OFFLINE ❌"}")
        
        // 1. Update local state immediately (instant UI feedback)
        _isAvailable.value = available
        
        // 2. Persist locally
        dataStore.edit { prefs ->
            prefs[KEY_IS_AVAILABLE] = available
            prefs[KEY_LAST_UPDATED] = System.currentTimeMillis()
            prefs[KEY_PENDING_SYNC] = true // Mark as pending sync
        }
        
        // 3. Sync with backend
        syncWithBackend(available)
    }
    
    /**
     * Toggle availability (convenience method)
     */
    suspend fun toggleAvailability() {
        setAvailability(!_isAvailable.value)
    }
    
    /**
     * Sync availability status with backend
     */
    private suspend fun syncWithBackend(available: Boolean) {
        if (!networkMonitor.isCurrentlyOnline()) {
            timber.log.Timber.w("📵 Offline - availability will sync when online")
            return
        }
        
        _isSyncing.value = true
        _syncError.value = null
        
        try {
            // Call backend API
            val response = RetrofitClient.transporterApi.updateAvailability(
                RetrofitClient.getAuthHeader(),
                mapOf("isAvailable" to available)
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                // Clear pending sync flag
                dataStore.edit { prefs ->
                    prefs[KEY_PENDING_SYNC] = false
                }
                timber.log.Timber.i("✅ Availability synced with backend: ${if (available) "ONLINE" else "OFFLINE"}")
            } else {
                val error = response.body()?.error?.message ?: "Sync failed"
                _syncError.value = error
                timber.log.Timber.e("❌ Failed to sync availability: $error")
            }
        } catch (e: Exception) {
            _syncError.value = e.message
            timber.log.Timber.e("❌ Availability sync error: ${e.message}")
            // Keep pending sync flag so it retries later
        } finally {
            _isSyncing.value = false
        }
    }
    
    /**
     * Force sync with backend
     */
    suspend fun forceSync() {
        syncWithBackend(_isAvailable.value)
    }
    
    /**
     * Check if availability is synced with backend
     */
    suspend fun isSynced(): Boolean {
        return !(dataStore.data.first()[KEY_PENDING_SYNC] ?: false)
    }
    
    /**
     * Get last updated timestamp
     */
    suspend fun getLastUpdated(): Long {
        return dataStore.data.first()[KEY_LAST_UPDATED] ?: 0L
    }
}
