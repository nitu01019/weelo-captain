package com.weelo.logistics.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.weelo.logistics.data.api.DriverData
import com.weelo.logistics.data.api.DriverListData
import com.weelo.logistics.data.model.Driver
import com.weelo.logistics.data.model.DriverStatus
import com.weelo.logistics.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Driver Repository - Manages driver data fetching and caching
 * 
 * Connects to: GET /api/v1/driver/list
 * 
 * Used by:
 * - DriverAssignmentScreen: To show available drivers for assignment
 * - DriverListScreen: To show all drivers for transporter
 */

// ============== RESULT SEALED CLASS ==============

sealed class DriverResult<out T> {
    data class Success<T>(val data: T) : DriverResult<T>()
    data class Error(val message: String, val code: Int? = null) : DriverResult<Nothing>()
    object Loading : DriverResult<Nothing>()
}

// ============== CACHED DATA CLASS ==============

data class CachedDriverList(
    val drivers: List<Driver>,
    val total: Int,
    val online: Int,
    val offline: Int,
    val lastUpdated: Long,
    val isStale: Boolean = false
)

// ============== REPOSITORY CLASS ==============

class DriverRepository private constructor(
    private val context: Context
) {
    private val driverApi = RetrofitClient.driverApi
    private val cacheMutex = Mutex()
    
    // In-memory cache
    private var cachedDrivers: CachedDriverList? = null
    private val cacheValidityMs = 2 * 60 * 1000L // 2 minutes cache validity
    
    // Observable state for UI
    private val _driversState = MutableStateFlow<DriverResult<CachedDriverList>>(DriverResult.Loading)
    val driversState: StateFlow<DriverResult<CachedDriverList>> = _driversState.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    @SuppressLint("StaticFieldLeak")
    companion object {
        private const val TAG = "DriverRepository"
        private val STATUS_TOKEN_REGEX = Regex("[^a-z0-9]+")
        private val ON_TRIP_STATUSES = setOf("on_trip", "in_trip", "busy", "assigned")
        private val SUSPENDED_STATUSES = setOf("suspended", "blocked")
        private val OFFLINE_STATUSES = setOf("inactive", "offline", "disabled")
        private val ONLINE_STATUSES = setOf("active", "online", "available", "idle")
        
        @Volatile
        private var instance: DriverRepository? = null
        
        fun getInstance(context: Context): DriverRepository {
            return instance ?: synchronized(this) {
                instance ?: DriverRepository(context.applicationContext).also { instance = it }
            }
        }
        
        fun clearInstance() {
            instance = null
        }
    }
    
    // =========================================================================
    // FETCH OPERATIONS
    // =========================================================================
    
    /**
     * Fetch drivers with caching support
     * Uses cache if valid, otherwise fetches from backend
     * 
     * @param forceRefresh Force fetch from backend ignoring cache
     */
    suspend fun fetchDrivers(
        forceRefresh: Boolean = false
    ): DriverResult<CachedDriverList> = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            // Check cache validity
            val cache = cachedDrivers
            if (!forceRefresh && cache != null && !isCacheStale(cache)) {
                timber.log.Timber.d("✅ Returning cached drivers (${cache.drivers.size} items)")
                return@withContext DriverResult.Success(cache)
            }
            
            _isRefreshing.value = true
            
            try {
                timber.log.Timber.d("📡 Fetching drivers from backend...")
                val response = driverApi.getDriverList()
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    val drivers = data?.drivers?.map { mapToDriver(it) } ?: emptyList()
                    val totalCount = data?.total?.takeIf { it > 0 } ?: drivers.size
                    val onlineCount = (
                        data?.online?.takeIf { it > 0 }
                            ?: data?.available?.takeIf { it > 0 }
                            ?: data?.active?.takeIf { it > 0 }
                            ?: drivers.count { it.status == DriverStatus.ACTIVE && it.isAvailable }
                        ).coerceAtMost(totalCount)
                    val offlineCount = (
                        data?.offline?.takeIf { it > 0 }
                            ?: (totalCount - onlineCount)
                        ).coerceAtLeast(0)
                    
                    val newCache = CachedDriverList(
                        drivers = drivers,
                        total = totalCount,
                        online = onlineCount,
                        offline = offlineCount,
                        lastUpdated = System.currentTimeMillis()
                    )
                    
                    cachedDrivers = newCache
                    _driversState.value = DriverResult.Success(newCache)
                    
                    timber.log.Timber.i(
                        "✅ Fetched ${drivers.size} drivers ($onlineCount active/assignable, $offlineCount offline)"
                    )
                    
                    DriverResult.Success(newCache)
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "Session expired. Please login again."
                        403 -> "Access denied. Only transporters can view drivers."
                        404 -> "Drivers endpoint not found"
                        500 -> "Server error. Please try again later."
                        else -> response.body()?.error?.message ?: "Failed to fetch drivers (${response.code()})"
                    }
                    
                    timber.log.Timber.e("❌ Fetch drivers failed: $errorMsg")
                    val error = DriverResult.Error(errorMsg, response.code())
                    _driversState.value = error
                    error
                }
            } catch (e: java.net.ConnectException) {
                timber.log.Timber.e("❌ Connection error: ${e.message}")
                val staleCache = cachedDrivers?.copy(isStale = true)
                if (staleCache != null) {
                    _driversState.value = DriverResult.Success(staleCache)
                    return@withContext DriverResult.Success(staleCache)
                }
                val error = DriverResult.Error("Cannot connect to server. Is the backend running?")
                _driversState.value = error
                error
            } catch (e: Exception) {
                timber.log.Timber.e(e, "❌ Fetch error: ${e.message}")
                val staleCache = cachedDrivers?.copy(isStale = true)
                if (staleCache != null) {
                    _driversState.value = DriverResult.Success(staleCache)
                    return@withContext DriverResult.Success(staleCache)
                }
                val error = DriverResult.Error(e.message ?: "Network error")
                _driversState.value = error
                error
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    /**
     * Get driver by ID from cache
     */
    fun getDriverById(driverId: String): Driver? {
        return cachedDrivers?.drivers?.find { it.id == driverId }
    }
    
    /**
     * Get available drivers (not on trip)
     */
    fun getAvailableDrivers(): List<Driver> {
        return cachedDrivers?.drivers?.filter { 
            it.status == DriverStatus.ACTIVE && it.isAvailable
        } ?: emptyList()
    }
    
    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================
    
    private fun isCacheStale(cache: CachedDriverList): Boolean {
        return cache.isStale || System.currentTimeMillis() - cache.lastUpdated > cacheValidityMs
    }
    
    fun invalidateCache() {
        cachedDrivers = cachedDrivers?.copy(isStale = true, lastUpdated = 0)
        timber.log.Timber.d("🔄 Cache invalidated")
    }
    
    fun clearCache() {
        cachedDrivers = null
        _driversState.value = DriverResult.Loading
        timber.log.Timber.d("🗑️ Cache cleared")
    }
    
    // =========================================================================
    // MAPPING FUNCTIONS
    // =========================================================================
    
    /**
     * Map API DriverData to UI Driver model
     */
    private fun mapToDriver(data: DriverData): Driver {
        val normalizedStatus = data.status
            ?.trim()
            ?.lowercase()
            ?.replace(STATUS_TOKEN_REGEX, "_")
            ?.trim('_')
        val isOnTrip = data.isOnTrip ||
            !data.currentTripId.isNullOrBlank() ||
            normalizedStatus in ON_TRIP_STATUSES
        val isSuspended = normalizedStatus in SUSPENDED_STATUSES
        val isExplicitlyOffline = normalizedStatus in OFFLINE_STATUSES
        val hasExplicitAvailability = data.isAvailable != null
        val hasOnlineSignal = data.isOnline ||
            data.isAvailable == true ||
            ((normalizedStatus in ONLINE_STATUSES) && hasExplicitAvailability)
        val isOnline = hasOnlineSignal && !isOnTrip && !isSuspended && !isExplicitlyOffline
        val isAvailableForAssignment = if (isOnTrip) false else (data.isAvailable ?: isOnline)

        return Driver(
            id = data.id,
            transporterId = data.transporterId,
            name = data.name ?: "Unknown Driver",
            mobileNumber = data.phone,
            licenseNumber = data.licenseNumber ?: "",
            assignedVehicleId = data.assignedVehicleId,
            isAvailable = isAvailableForAssignment,
            rating = data.rating ?: 0f,
            totalTrips = data.totalTrips,
            profileImageUrl = null, // API doesn't return this yet
            status = when {
                isOnTrip -> DriverStatus.ON_TRIP
                isSuspended -> DriverStatus.SUSPENDED
                isOnline -> DriverStatus.ACTIVE
                else -> DriverStatus.INACTIVE
            },
            createdAt = try {
                val ts = data.createdAt ?: ""
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.parse(ts)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        )
    }
}
