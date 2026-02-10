package com.weelo.logistics.data.cache

import com.weelo.logistics.data.api.DriverData
import com.weelo.logistics.data.api.VehicleData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * =============================================================================
 * APP-WIDE IN-MEMORY CACHE - RAPIDO STYLE
 * =============================================================================
 * 
 * PRINCIPLE: Screens OBSERVE cached data. They never fetch directly.
 * 
 * FLOW:
 * 1. Screen opens → Shows cached data INSTANTLY (0ms)
 * 2. Background refresh → Updates cache → UI auto-updates
 * 3. Back button → Cache is still there → NO reload
 * 
 * BENEFITS:
 * - Navigation feels instant
 * - Back button shows data immediately
 * - No loading spinners on navigation
 * - API calls are decoupled from navigation
 * 
 * =============================================================================
 */
object AppCache {
    
    private const val TAG = "AppCache"
    
    // =========================================================================
    // VEHICLE CACHE
    // =========================================================================
    
    private val _vehicles = MutableStateFlow<List<VehicleData>>(emptyList())
    val vehicles: StateFlow<List<VehicleData>> = _vehicles.asStateFlow()
    
    private val _vehiclesLoading = MutableStateFlow(false)
    val vehiclesLoading: StateFlow<Boolean> = _vehiclesLoading.asStateFlow()
    
    private val _vehiclesError = MutableStateFlow<String?>(null)
    val vehiclesError: StateFlow<String?> = _vehiclesError.asStateFlow()
    
    // Vehicle stats
    private val _vehicleStats = MutableStateFlow(VehicleStats())
    val vehicleStats: StateFlow<VehicleStats> = _vehicleStats.asStateFlow()
    
    // Last fetch timestamp for staleness check
    private var lastVehicleFetch: Long = 0
    
    /**
     * Check if vehicles need refresh (stale after 60 seconds)
     */
    fun shouldRefreshVehicles(): Boolean {
        return System.currentTimeMillis() - lastVehicleFetch > 60_000 || _vehicles.value.isEmpty()
    }
    
    /**
     * Update vehicles cache
     */
    fun setVehicles(
        vehicles: List<VehicleData>,
        stats: VehicleStats
    ) {
        timber.log.Timber.d("📦 Caching ${vehicles.size} vehicles")
        _vehicles.value = vehicles
        _vehicleStats.value = stats
        lastVehicleFetch = System.currentTimeMillis()
        _vehiclesError.value = null
    }
    
    fun setVehiclesLoading(loading: Boolean) {
        _vehiclesLoading.value = loading
    }
    
    fun setVehiclesError(error: String?) {
        _vehiclesError.value = error
    }
    
    /**
     * Get single vehicle from cache
     */
    fun getVehicle(vehicleId: String): VehicleData? {
        return _vehicles.value.find { it.id == vehicleId }
    }
    
    // =========================================================================
    // DRIVER CACHE
    // =========================================================================
    
    private val _drivers = MutableStateFlow<List<DriverData>>(emptyList())
    val drivers: StateFlow<List<DriverData>> = _drivers.asStateFlow()
    
    private val _driversLoading = MutableStateFlow(false)
    val driversLoading: StateFlow<Boolean> = _driversLoading.asStateFlow()
    
    private val _driversError = MutableStateFlow<String?>(null)
    val driversError: StateFlow<String?> = _driversError.asStateFlow()
    
    private var lastDriverFetch: Long = 0
    
    /**
     * Check if drivers need refresh
     */
    fun shouldRefreshDrivers(): Boolean {
        return System.currentTimeMillis() - lastDriverFetch > 60_000 || _drivers.value.isEmpty()
    }
    
    /**
     * Update drivers cache
     */
    fun setDrivers(drivers: List<DriverData>) {
        timber.log.Timber.d("📦 Caching ${drivers.size} drivers")
        _drivers.value = drivers
        lastDriverFetch = System.currentTimeMillis()
        _driversError.value = null
    }
    
    fun setDriversLoading(loading: Boolean) {
        _driversLoading.value = loading
    }
    
    fun setDriversError(error: String?) {
        _driversError.value = error
    }
    
    /**
     * Get single driver from cache
     */
    fun getDriver(driverId: String): DriverData? {
        return _drivers.value.find { it.id == driverId }
    }
    
    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================
    
    /**
     * Clear all cache (on logout)
     */
    fun clearAll() {
        timber.log.Timber.d("🗑️ Clearing all cache")
        _vehicles.value = emptyList()
        _drivers.value = emptyList()
        _vehicleStats.value = VehicleStats()
        _vehiclesError.value = null
        _driversError.value = null
        lastVehicleFetch = 0
        lastDriverFetch = 0
    }
    
    /**
     * Check if we have any cached data
     */
    fun hasVehicleData(): Boolean = _vehicles.value.isNotEmpty()
    fun hasDriverData(): Boolean = _drivers.value.isNotEmpty()
}

/**
 * Vehicle statistics
 */
data class VehicleStats(
    val total: Int = 0,
    val available: Int = 0,
    val inTransit: Int = 0,
    val maintenance: Int = 0
)
