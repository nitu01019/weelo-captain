package com.weelo.logistics.offline.recovery

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weelo.logistics.core.security.TokenManager
import com.weelo.logistics.data.api.ActiveTripApiService
import com.weelo.logistics.data.remote.RetrofitClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =============================================================================
 * ACTIVE TRIP MANAGER - Crash Recovery (Phase 3)
 * =============================================================================
 *
 * Handles crash recovery for active driver trips.
 *
 * PROBLEM SOLVED:
 * Driver app crashes mid-trip → On relaunch, driver loses trip context.
 * Backend still has assignment as active, but app is blank.
 *
 * SOLUTION (Industry Standard - Uber, Ola):
 * 1. On app startup, call GET /tracking/active-trip
 * 2. If active trip exists → Restore state + Navigate to trip screen
 * 3. If offline → Use cached state from last check
 * 4. If no trip → Clear any cached state
 *
 * DATA STORE USAGE:
 * - lastActiveTripId: Last known trip ID
 * - lastActiveTripTimestamp: When we last checked
 * - lastActiveTripData: Cached trip JSON (for offline fallback)
 * - wasInActiveTrip: Boolean flag (quick check)
 *
 * FLOW:
 * App Start → checkActiveTrip() → [Has Trip? Yes] → Navigate to DriverTripNavigationScreen
 *                                          [No Trip] → Normal dashboard
 *
 * CONFLICT RESOLUTION:
 * - Backend is source of truth
 * - If local state exists but backend says no trip → Clear local (trip completed elsewhere)
 *
 * INDUSTRY ALIGNMENT:
 * ✓ Uber: Fetches active assignment on app launch
 * ✓ Ola: Persists minimal state offline
 * ✓ DoorDash: Uses backend as single source of truth
 * =============================================================================
 */
@Singleton
class ActiveTripManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "ActiveTripManager"

        // DataStore file name
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "active_trip_prefs")

        // Keys
        private val KEY_WAS_IN_TRIP = booleanPreferencesKey("was_in_active_trip")
        private val KEY_LAST_TRIP_ID = stringPreferencesKey("last_trip_id")
        private val KEY_LAST_CHECK_TIMESTAMP = longPreferencesKey("last_check_timestamp")
        private val KEY_CACHED_TRIP_DATA = stringPreferencesKey("cached_trip_data")
        private val KEY_TRIP_RECOVERY_ATTEMPTED = booleanPreferencesKey("trip_recovery_attempted")
        private val KEY_TRIP_STATUS = stringPreferencesKey("trip_status")
        private val KEY_TRIP_VEHICLE_NUMBER = stringPreferencesKey("trip_vehicle_number")

        // Cache validity duration (5 minutes - fresh enough for most cases)
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L

        // Maximum age for recovered trip (24 hours - older trips not relevant)
        private const val MAX_TRIP_AGE_MS = 24 * 60 * 60 * 1000L
    }

    private val dataStore = context.dataStore

    /**
     * Check if driver has an active trip
     *
     * INDUSTRY PATTERN:
     * Uber: Check on every app launch + when network changes
     * Ola: Check in splash screen before showing dashboard
     *
     * CALL THIS:
     * - In MainActivity onCreate (before showing any screen)
     * - When network comes online (if was offline)
     * - After login
     */
    suspend fun checkActiveTrip(): ActiveTripResult {
        Timber.tag(TAG).d("Checking for active trip")

        // Check if we have cached state
        val wasInTrip = dataStore.data.first()[KEY_WAS_IN_TRIP] ?: false

        if (!wasInTrip) {
            Timber.tag(TAG).d("No cached active trip - skipping check")
            return ActiveTripResult(hasActiveTrip = false)
        }

        // Get cached data for offline fallback
        val cachedTripId = dataStore.data.first()[KEY_LAST_TRIP_ID]
        val cachedTimestamp = dataStore.data.first()[KEY_LAST_CHECK_TIMESTAMP]
        val cacheAge = System.currentTimeMillis() - (cachedTimestamp ?: 0L)

        // If cache is fresh (< 5 min) and we know trips don't complete that fast,
        // we can skip the API call for faster app startup
        if (cachedTripId != null && cacheAge < CACHE_VALIDITY_MS) {
            Timber.tag(TAG).d("Using cached active trip data (${cacheAge}ms old)")
            return getActiveTripFromCache()
        }

        // Need to check with backend
        return fetchActiveTripFromBackend()
    }

    /**
     * Fetch active trip from backend (API call)
     */
    private suspend fun fetchActiveTripFromBackend(): ActiveTripResult {
        try {
            // Get active trip API service (lazy singleton from RetrofitClient)
            val activeTripApi = RetrofitClient.activeTripApi

            // Call backend
            val token = tokenManager.getAccessToken()
            if (token.isNullOrBlank()) {
                Timber.tag(TAG).w("No auth token - cannot check active trip")
                clearActiveTripState()
                return ActiveTripResult(hasActiveTrip = false, error = "No auth token")
            }

            val response = activeTripApi.getActiveTrip()

            if (response.isSuccessful && response.body()?.success == true) {
                val activeTripData = response.body()?.data

                if (activeTripData != null) {
                    // Has active trip - cache and return
                    cacheActiveTrip(activeTripData)
                    Timber.tag(TAG).i("Active trip found: ${activeTripData.tripId} (status: ${activeTripData.status})")
                    return ActiveTripResult(
                        hasActiveTrip = true,
                        tripId = activeTripData.tripId,
                        status = activeTripData.status,
                        vehicleNumber = activeTripData.vehicleNumber,
                        pickup = activeTripData.pickup,
                        drop = activeTripData.drop,
                        customerName = activeTripData.customerName,
                        customerPhone = activeTripData.customerPhone,
                        distanceKm = activeTripData.distanceKm,
                        routePoints = activeTripData.routePoints,
                        currentRouteIndex = activeTripData.currentRouteIndex,
                        lastLocation = activeTripData.lastLocation
                    )
                } else {
                    // No active trip in backend - clear local state
                    Timber.tag(TAG).i("No active trip in backend - clearing cache")
                    clearActiveTripState()
                    return ActiveTripResult(hasActiveTrip = false)
                }
            } else {
                // API error - check error type
                val errorMsg = response.body()?.error?.message
                Timber.tag(TAG).w("Failed to check active trip: $errorMsg")

                // If cached data exists, use it as fallback (Uber pattern)
                return getActiveTripFromCache()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error checking active trip")
            // Use cached data as fallback
            return getActiveTripFromCache(error = e.message)
        }
    }

    /**
     * Get active trip from local cache (offline fallback)
     *
     * INDUSTRY PATTERN:
     * - Uber: Show "Trip may have changed" banner with cached data
     * - Ola: Queue for sync when online
     */
    private suspend fun getActiveTripFromCache(error: String? = null): ActiveTripResult {
        try {
            val cachedDataJson = dataStore.data.first()[KEY_CACHED_TRIP_DATA]
            val tripId = dataStore.data.first()[KEY_LAST_TRIP_ID]
            val status = dataStore.data.first()[KEY_TRIP_STATUS]
            val vehicleNumber = dataStore.data.first()[KEY_TRIP_VEHICLE_NUMBER]

            if (cachedDataJson != null && tripId != null) {
                // Parse cached JSON (minimal fields)
                // For full data, would need to parse the JSON properly
                Timber.tag(TAG).d("Using cached trip data: $tripId")
                return ActiveTripResult(
                    hasActiveTrip = true,
                    tripId = tripId,
                    status = status ?: "unknown",
                    vehicleNumber = vehicleNumber,
                    isCached = true,
                    error = error
                )
            }

            return ActiveTripResult(hasActiveTrip = false)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error reading cached trip")
            return ActiveTripResult(hasActiveTrip = false, error = e.message)
        }
    }

    /**
     * Cache active trip state locally
     */
    private suspend fun cacheActiveTrip(data: com.weelo.logistics.data.api.DriverActiveTripData) {
        dataStore.edit { prefs ->
            prefs[KEY_WAS_IN_TRIP] = true
            prefs[KEY_LAST_TRIP_ID] = data.tripId
            prefs[KEY_LAST_CHECK_TIMESTAMP] = System.currentTimeMillis()
            prefs[KEY_TRIP_STATUS] = data.status
            prefs[KEY_TRIP_VEHICLE_NUMBER] = data.vehicleNumber

            // Store minimal cached data (JSON would be better for full data)
            val cachedJson = buildString {
                append("{")
                append("\"tripId\":\"${data.tripId}\",")
                append("\"status\":\"${data.status}\",")
                append("\"vehicleNumber\":\"${data.vehicleNumber}\",")
                append("\"customerName\":\"${data.customerName}\"")
                append("}")
            }
            prefs[KEY_CACHED_TRIP_DATA] = cachedJson
            prefs[KEY_TRIP_RECOVERY_ATTEMPTED] = false
        }
        Timber.tag(TAG).d("Cached active trip: ${data.tripId}")
    }

    /**
     * Clear active trip state (trip completed/cancelled)
     *
     * CALL THIS:
     * - When trip completed successfully
     * - When trip cancelled
     * - When backend reports no active trip
     * - On logout
     */
    suspend fun clearActiveTripState() {
        dataStore.edit { prefs ->
            prefs[KEY_WAS_IN_TRIP] = false
            prefs.remove(KEY_LAST_TRIP_ID)
            prefs.remove(KEY_LAST_CHECK_TIMESTAMP)
            prefs.remove(KEY_CACHED_TRIP_DATA)
            prefs.remove(KEY_TRIP_STATUS)
            prefs.remove(KEY_TRIP_VEHICLE_NUMBER)
            prefs.remove(KEY_TRIP_RECOVERY_ATTEMPTED)
        }
        Timber.tag(TAG).d("Cleared active trip state")
    }

    /**
     * Mark trip recovery as attempted (to avoid duplicate navigation)
     */
    suspend fun markRecoveryAttempted(tripId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_TRIP_RECOVERY_ATTEMPTED] = if (prefs[KEY_LAST_TRIP_ID] == tripId) true else false
        }
    }

    /**
     * Check if recovery was already attempted for this trip
     */
    suspend fun wasRecoveryAttempted(tripId: String): Boolean {
        return dataStore.data.first()[KEY_TRIP_RECOVERY_ATTEMPTED] == true &&
        dataStore.data.first()[KEY_LAST_TRIP_ID] == tripId
    }

    /**
     * Get last known trip ID (for quick check)
     */
    suspend fun getLastTripId(): String? {
        return dataStore.data.first()[KEY_LAST_TRIP_ID]
    }

    /**
     * Check if driver WAS in a trip (regardless of current status)
     * Used for showing "trip may have ended" messages
     */
    suspend fun wasInActiveTrip(): Boolean {
        return dataStore.data.first()[KEY_WAS_IN_TRIP] ?: false
    }
}

// =============================================================================
// Response Data Class
// =============================================================================

/**
 * Result of active trip check
 */
data class ActiveTripResult(
    val hasActiveTrip: Boolean,
    val tripId: String? = null,
    val status: String? = null,
    val vehicleNumber: String? = null,
    val pickup: com.weelo.logistics.data.api.LocationData? = null,
    val drop: com.weelo.logistics.data.api.LocationData? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val distanceKm: Double? = null,
    val routePoints: List<com.weelo.logistics.data.api.LocationData>? = null,
    val currentRouteIndex: Int? = null,
    val lastLocation: com.weelo.logistics.data.api.LastLocationData? = null,
    val isCached: Boolean = false,
    val error: String? = null
) {
    /**
     * Check if trip status indicates GPS tracking should be active
     */
    val shouldTrackLocation: Boolean
        get() = status?.lowercase() in listOf(
            "in_transit", "en_route_pickup", "at_pickup", "loading_complete"
        ) && !isCached

    /**
     * Check if trip is actionable (driver can complete actions)
     */
    val isActionable: Boolean
        get() = hasActiveTrip && status?.lowercase() !in listOf(
            "completed", "cancelled", "driver_declined", "expired"
        )
}
