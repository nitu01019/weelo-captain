package com.weelo.logistics.offline

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weelo.logistics.data.api.UserProfile
import com.weelo.logistics.data.api.VehicleListData
import com.weelo.logistics.data.api.DriverListData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * =============================================================================
 * OFFLINE CACHE - Persistent data storage for offline support
 * =============================================================================
 * 
 * FEATURES:
 * - Caches broadcasts, vehicles, drivers, trips locally
 * - Data persists across app restarts
 * - Automatic JSON serialization
 * - TTL-based expiration
 * - Thread-safe operations
 * 
 * USAGE:
 * val cache = OfflineCache.getInstance(context)
 * cache.saveBroadcasts(broadcasts)
 * cache.getBroadcasts().collect { broadcasts -> ... }
 * 
 * =============================================================================
 */

// DataStore instance
private val Context.offlineDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "weelo_offline_cache"
)

class OfflineCache private constructor(
    private val context: Context
) {
    private val dataStore = context.offlineDataStore
    private val gson = Gson()
    
    @SuppressLint("StaticFieldLeak")
    companion object {
        private const val TAG = "OfflineCache"
        
        // Cache keys
        private val KEY_BROADCASTS = stringPreferencesKey("cached_broadcasts")
        private val KEY_BROADCASTS_TIMESTAMP = longPreferencesKey("broadcasts_timestamp")
        private val KEY_VEHICLES = stringPreferencesKey("cached_vehicles")
        private val KEY_VEHICLES_TIMESTAMP = longPreferencesKey("vehicles_timestamp")
        private val KEY_DRIVERS = stringPreferencesKey("cached_drivers")
        private val KEY_DRIVERS_TIMESTAMP = longPreferencesKey("drivers_timestamp")
        private val KEY_PROFILE = stringPreferencesKey("cached_profile")
        private val KEY_PROFILE_TIMESTAMP = longPreferencesKey("profile_timestamp")
        private val KEY_PENDING_REQUESTS = stringPreferencesKey("pending_requests")
        private val KEY_ACTIVE_TRIPS = stringPreferencesKey("cached_active_trips")
        private val KEY_ACTIVE_TRIPS_TIMESTAMP = longPreferencesKey("active_trips_timestamp")
        
        // TTL values (milliseconds)
        private const val TTL_BROADCASTS = 5 * 60 * 1000L      // 5 minutes
        private const val TTL_VEHICLES = 30 * 60 * 1000L       // 30 minutes
        private const val TTL_DRIVERS = 30 * 60 * 1000L        // 30 minutes
        private const val TTL_PROFILE = 60 * 60 * 1000L        // 1 hour
        private const val TTL_ACTIVE_TRIPS = 2 * 60 * 1000L    // 2 minutes
        
        @Volatile
        private var instance: OfflineCache? = null
        
        fun getInstance(context: Context): OfflineCache {
            return instance ?: synchronized(this) {
                instance ?: OfflineCache(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // ===========================================================================
    // BROADCASTS CACHE
    // ===========================================================================
    
    /**
     * Save broadcasts to cache
     */
    suspend fun saveBroadcasts(broadcasts: List<Any>) {
        try {
            val json = gson.toJson(broadcasts)
            dataStore.edit { prefs ->
                prefs[KEY_BROADCASTS] = json
                prefs[KEY_BROADCASTS_TIMESTAMP] = System.currentTimeMillis()
            }
            timber.log.Timber.d("✅ Saved ${broadcasts.size} broadcasts to cache")
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to save broadcasts: ${e.message}")
        }
    }
    
    /**
     * Get cached broadcasts
     */
    fun getBroadcastsFlow(): Flow<List<Map<String, Any>>> {
        return dataStore.data
            .catch { e ->
                if (e is IOException) {
                    timber.log.Timber.e("Error reading broadcasts cache: ${e.message}")
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
            .map { prefs ->
                val json = prefs[KEY_BROADCASTS] ?: return@map emptyList()
                val timestamp = prefs[KEY_BROADCASTS_TIMESTAMP] ?: 0L
                
                // Check TTL
                if (System.currentTimeMillis() - timestamp > TTL_BROADCASTS) {
                    timber.log.Timber.d("Broadcasts cache expired")
                    return@map emptyList()
                }
                
                try {
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    gson.fromJson<List<Map<String, Any>>>(json, type) ?: emptyList()
                } catch (e: Exception) {
                    timber.log.Timber.e("Failed to parse broadcasts: ${e.message}")
                    emptyList()
                }
            }
    }
    
    /**
     * Get cached broadcasts synchronously
     */
    suspend fun getBroadcasts(): List<Map<String, Any>> {
        return getBroadcastsFlow().first()
    }
    
    // ===========================================================================
    // VEHICLES CACHE
    // ===========================================================================
    
    /**
     * Save vehicles to cache
     */
    suspend fun saveVehicles(vehicles: List<Any>) {
        try {
            val json = gson.toJson(vehicles)
            dataStore.edit { prefs ->
                prefs[KEY_VEHICLES] = json
                prefs[KEY_VEHICLES_TIMESTAMP] = System.currentTimeMillis()
            }
            timber.log.Timber.d("✅ Saved ${vehicles.size} vehicles to cache")
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to save vehicles: ${e.message}")
        }
    }
    
    /**
     * Get cached vehicles
     */
    fun getVehiclesFlow(): Flow<List<Map<String, Any>>> {
        return dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs ->
                val json = prefs[KEY_VEHICLES] ?: return@map emptyList()
                val timestamp = prefs[KEY_VEHICLES_TIMESTAMP] ?: 0L
                
                if (System.currentTimeMillis() - timestamp > TTL_VEHICLES) {
                    return@map emptyList()
                }
                
                try {
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    gson.fromJson<List<Map<String, Any>>>(json, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
    }
    
    suspend fun getVehicles(): List<Map<String, Any>> = getVehiclesFlow().first()
    
    // ===========================================================================
    // DRIVERS CACHE
    // ===========================================================================
    
    /**
     * Save drivers to cache
     */
    suspend fun saveDrivers(drivers: List<Any>) {
        try {
            val json = gson.toJson(drivers)
            dataStore.edit { prefs ->
                prefs[KEY_DRIVERS] = json
                prefs[KEY_DRIVERS_TIMESTAMP] = System.currentTimeMillis()
            }
            timber.log.Timber.d("✅ Saved ${drivers.size} drivers to cache")
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to save drivers: ${e.message}")
        }
    }
    
    /**
     * Get cached drivers
     */
    fun getDriversFlow(): Flow<List<Map<String, Any>>> {
        return dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs ->
                val json = prefs[KEY_DRIVERS] ?: return@map emptyList()
                val timestamp = prefs[KEY_DRIVERS_TIMESTAMP] ?: 0L
                
                if (System.currentTimeMillis() - timestamp > TTL_DRIVERS) {
                    return@map emptyList()
                }
                
                try {
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    gson.fromJson<List<Map<String, Any>>>(json, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
    }
    
    suspend fun getDrivers(): List<Map<String, Any>> = getDriversFlow().first()
    
    // ===========================================================================
    // PROFILE CACHE
    // ===========================================================================
    
    /**
     * Save user profile to cache
     */
    suspend fun saveProfile(profile: Any) {
        try {
            val json = gson.toJson(profile)
            dataStore.edit { prefs ->
                prefs[KEY_PROFILE] = json
                prefs[KEY_PROFILE_TIMESTAMP] = System.currentTimeMillis()
            }
            timber.log.Timber.d("✅ Saved profile to cache")
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to save profile: ${e.message}")
        }
    }
    
    /**
     * Get cached profile
     */
    fun getProfileFlow(): Flow<Map<String, Any>?> {
        return dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs ->
                val json = prefs[KEY_PROFILE] ?: return@map null
                val timestamp = prefs[KEY_PROFILE_TIMESTAMP] ?: 0L
                
                if (System.currentTimeMillis() - timestamp > TTL_PROFILE) {
                    return@map null
                }
                
                try {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    gson.fromJson<Map<String, Any>>(json, type)
                } catch (e: Exception) {
                    null
                }
            }
    }
    
    suspend fun getProfile(): Map<String, Any>? = getProfileFlow().first()
    
    // ===========================================================================
    // DASHBOARD CACHE - Typed methods for fast dashboard loading
    // ===========================================================================
    
    private val KEY_DASHBOARD_PROFILE = stringPreferencesKey("dashboard_profile")
    private val KEY_DASHBOARD_VEHICLES = stringPreferencesKey("dashboard_vehicles")
    private val KEY_DASHBOARD_DRIVERS = stringPreferencesKey("dashboard_drivers")
    private val KEY_DASHBOARD_TIMESTAMP = longPreferencesKey("dashboard_timestamp")
    
    // Dashboard cache TTL - 10 minutes (show cached, refresh in background)
    private val TTL_DASHBOARD = 10 * 60 * 1000L
    
    /**
     * Save complete dashboard data atomically
     */
    suspend fun saveDashboardData(
        profile: UserProfile?,
        vehicleStats: VehicleListData?,
        driverStats: DriverListData?
    ) {
        try {
            dataStore.edit { prefs ->
                profile?.let { prefs[KEY_DASHBOARD_PROFILE] = gson.toJson(it) }
                vehicleStats?.let { prefs[KEY_DASHBOARD_VEHICLES] = gson.toJson(it) }
                driverStats?.let { prefs[KEY_DASHBOARD_DRIVERS] = gson.toJson(it) }
                prefs[KEY_DASHBOARD_TIMESTAMP] = System.currentTimeMillis()
            }
            timber.log.Timber.d("✅ Saved dashboard data to cache")
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to save dashboard data: ${e.message}")
        }
    }
    
    /**
     * Get cached dashboard profile (UserProfile)
     */
    suspend fun getDashboardProfile(): UserProfile? {
        return try {
            val prefs = dataStore.data.first()
            val json = prefs[KEY_DASHBOARD_PROFILE] ?: return null
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to get cached profile: ${e.message}")
            null
        }
    }
    
    /**
     * Get cached vehicle stats (VehicleListData)
     */
    suspend fun getDashboardVehicleStats(): VehicleListData? {
        return try {
            val prefs = dataStore.data.first()
            val json = prefs[KEY_DASHBOARD_VEHICLES] ?: return null
            gson.fromJson(json, VehicleListData::class.java)
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to get cached vehicles: ${e.message}")
            null
        }
    }
    
    /**
     * Get cached driver stats (DriverListData)
     */
    suspend fun getDashboardDriverStats(): DriverListData? {
        return try {
            val prefs = dataStore.data.first()
            val json = prefs[KEY_DASHBOARD_DRIVERS] ?: return null
            gson.fromJson(json, DriverListData::class.java)
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to get cached drivers: ${e.message}")
            null
        }
    }
    
    /**
     * Check if dashboard cache is fresh (within TTL)
     */
    suspend fun isDashboardCacheFresh(): Boolean {
        return try {
            val prefs = dataStore.data.first()
            val timestamp = prefs[KEY_DASHBOARD_TIMESTAMP] ?: 0L
            System.currentTimeMillis() - timestamp < TTL_DASHBOARD
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get all dashboard data at once (for initial load)
     */
    data class DashboardCacheData(
        val profile: UserProfile?,
        val vehicleStats: VehicleListData?,
        val driverStats: DriverListData?,
        val isFresh: Boolean
    )
    
    suspend fun getDashboardCache(): DashboardCacheData {
        return try {
            val prefs = dataStore.data.first()
            val timestamp = prefs[KEY_DASHBOARD_TIMESTAMP] ?: 0L
            val isFresh = System.currentTimeMillis() - timestamp < TTL_DASHBOARD
            
            val profile = prefs[KEY_DASHBOARD_PROFILE]?.let {
                gson.fromJson(it, UserProfile::class.java)
            }
            val vehicles = prefs[KEY_DASHBOARD_VEHICLES]?.let {
                gson.fromJson(it, VehicleListData::class.java)
            }
            val drivers = prefs[KEY_DASHBOARD_DRIVERS]?.let {
                gson.fromJson(it, DriverListData::class.java)
            }
            
            DashboardCacheData(profile, vehicles, drivers, isFresh)
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to get dashboard cache: ${e.message}")
            DashboardCacheData(null, null, null, false)
        }
    }
    
    // ===========================================================================
    // ACTIVE TRIPS CACHE
    // ===========================================================================
    
    /**
     * Save active trips to cache
     */
    suspend fun saveActiveTrips(trips: List<Any>) {
        try {
            val json = gson.toJson(trips)
            dataStore.edit { prefs ->
                prefs[KEY_ACTIVE_TRIPS] = json
                prefs[KEY_ACTIVE_TRIPS_TIMESTAMP] = System.currentTimeMillis()
            }
            timber.log.Timber.d("✅ Saved ${trips.size} active trips to cache")
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to save active trips: ${e.message}")
        }
    }
    
    /**
     * Get cached active trips
     */
    suspend fun getActiveTrips(): List<Map<String, Any>> {
        return dataStore.data.first().let { prefs ->
            val json = prefs[KEY_ACTIVE_TRIPS] ?: return@let emptyList()
            val timestamp = prefs[KEY_ACTIVE_TRIPS_TIMESTAMP] ?: 0L
            
            if (System.currentTimeMillis() - timestamp > TTL_ACTIVE_TRIPS) {
                return@let emptyList()
            }
            
            try {
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                gson.fromJson<List<Map<String, Any>>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    // ===========================================================================
    // PENDING REQUESTS (Offline queue)
    // ===========================================================================
    
    /**
     * Save a pending request to be synced when online
     */
    suspend fun addPendingRequest(request: PendingRequest) {
        try {
            val current = getPendingRequests().toMutableList()
            current.add(request)
            
            val json = gson.toJson(current)
            dataStore.edit { prefs ->
                prefs[KEY_PENDING_REQUESTS] = json
            }
            timber.log.Timber.d("✅ Added pending request: ${request.type} (total: ${current.size})")
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to add pending request: ${e.message}")
        }
    }
    
    /**
     * Get all pending requests
     */
    suspend fun getPendingRequests(): List<PendingRequest> {
        return dataStore.data.first().let { prefs ->
            val json = prefs[KEY_PENDING_REQUESTS] ?: return@let emptyList()
            
            try {
                val type = object : TypeToken<List<PendingRequest>>() {}.type
                gson.fromJson<List<PendingRequest>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                timber.log.Timber.e("Failed to parse pending requests: ${e.message}")
                emptyList()
            }
        }
    }
    
    /**
     * Remove a pending request after successful sync
     */
    suspend fun removePendingRequest(requestId: String) {
        try {
            val current = getPendingRequests().toMutableList()
            current.removeAll { it.id == requestId }
            
            val json = gson.toJson(current)
            dataStore.edit { prefs ->
                prefs[KEY_PENDING_REQUESTS] = json
            }
            timber.log.Timber.d("✅ Removed pending request: $requestId (remaining: ${current.size})")
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to remove pending request: ${e.message}")
        }
    }
    
    /**
     * Clear all pending requests
     */
    suspend fun clearPendingRequests() {
        dataStore.edit { prefs ->
            prefs[KEY_PENDING_REQUESTS] = "[]"
        }
        timber.log.Timber.d("✅ Cleared all pending requests")
    }
    
    // ===========================================================================
    // UTILITY
    // ===========================================================================
    
    /**
     * Clear all cached data
     */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
        timber.log.Timber.i("🗑️ Cleared all offline cache")
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getStats(): CacheStats {
        val prefs = dataStore.data.first()
        
        return CacheStats(
            broadcastsCount = try {
                val json = prefs[KEY_BROADCASTS] ?: "[]"
                val type = object : TypeToken<List<Any>>() {}.type
                (gson.fromJson<List<Any>>(json, type) ?: emptyList()).size
            } catch (e: Exception) { 0 },
            vehiclesCount = try {
                val json = prefs[KEY_VEHICLES] ?: "[]"
                val type = object : TypeToken<List<Any>>() {}.type
                (gson.fromJson<List<Any>>(json, type) ?: emptyList()).size
            } catch (e: Exception) { 0 },
            driversCount = try {
                val json = prefs[KEY_DRIVERS] ?: "[]"
                val type = object : TypeToken<List<Any>>() {}.type
                (gson.fromJson<List<Any>>(json, type) ?: emptyList()).size
            } catch (e: Exception) { 0 },
            pendingRequestsCount = getPendingRequests().size,
            hasProfile = prefs[KEY_PROFILE] != null
        )
    }
}

/**
 * Pending request to be synced when online
 */
data class PendingRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: RequestType,
    val endpoint: String,
    val method: String,
    val body: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3
)

enum class RequestType {
    ACCEPT_BROADCAST,
    UPDATE_LOCATION,
    UPDATE_TRIP_STATUS,
    UPDATE_PROFILE,
    OTHER
}

/**
 * Cache statistics
 */
data class CacheStats(
    val broadcastsCount: Int,
    val vehiclesCount: Int,
    val driversCount: Int,
    val pendingRequestsCount: Int,
    val hasProfile: Boolean
)
