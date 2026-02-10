package com.weelo.logistics.data.repository

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
    
    companion object {
        private const val TAG = "DriverRepository"
        
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
                    
                    val newCache = CachedDriverList(
                        drivers = drivers,
                        total = data?.total ?: 0,
                        online = data?.online ?: 0,
                        offline = data?.offline ?: 0,
                        lastUpdated = System.currentTimeMillis()
                    )
                    
                    cachedDrivers = newCache
                    _driversState.value = DriverResult.Success(newCache)
                    
                    timber.log.Timber.i("✅ Fetched ${drivers.size} drivers (${data?.online ?: 0} online)")
                    
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
                    DriverResult.Error(errorMsg, response.code())
                }
            } catch (e: java.net.ConnectException) {
                timber.log.Timber.e("❌ Connection error: ${e.message}")
                val staleCache = cachedDrivers?.copy(isStale = true)
                if (staleCache != null) {
                    _driversState.value = DriverResult.Success(staleCache)
                    return@withContext DriverResult.Success(staleCache)
                }
                DriverResult.Error("Cannot connect to server. Is the backend running?")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "❌ Fetch error: ${e.message}")
                val staleCache = cachedDrivers?.copy(isStale = true)
                if (staleCache != null) {
                    _driversState.value = DriverResult.Success(staleCache)
                    return@withContext DriverResult.Success(staleCache)
                }
                DriverResult.Error(e.message ?: "Network error")
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
        return Driver(
            id = data.id,
            transporterId = data.transporterId,
            name = data.name ?: "Unknown Driver",
            mobileNumber = data.phone,
            licenseNumber = data.licenseNumber ?: "",
            assignedVehicleId = data.assignedVehicleId,
            isAvailable = data.isOnline && !data.isOnTrip,
            rating = data.rating ?: 0f,
            totalTrips = data.totalTrips,
            profileImageUrl = null, // API doesn't return this yet
            status = when {
                data.isOnTrip -> DriverStatus.ON_TRIP
                data.isOnline -> DriverStatus.ACTIVE
                else -> DriverStatus.INACTIVE
            },
            createdAt = try {
                java.time.Instant.parse(data.createdAt ?: "").toEpochMilli()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        )
    }
}
