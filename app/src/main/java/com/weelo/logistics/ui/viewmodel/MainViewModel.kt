package com.weelo.logistics.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.data.api.DriverData
import com.weelo.logistics.data.api.VehicleData
import com.weelo.logistics.data.cache.AppCache
import com.weelo.logistics.data.cache.VehicleStats
import com.weelo.logistics.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * =============================================================================
 * MAIN VIEW MODEL - RAPIDO-STYLE ACTIVITY-SCOPED STATE
 * =============================================================================
 * 
 * This is the CORE of Rapido-style instant navigation architecture.
 * 
 * PRINCIPLES:
 * 1. ONE ViewModel for the entire app (activity-scoped)
 * 2. Screens ONLY observe StateFlows - they NEVER fetch
 * 3. Data is loaded ONCE at app start, then refreshed silently
 * 4. Navigation NEVER waits for data
 * 5. Back button shows same data - 0ms, no reload
 * 
 * USAGE IN SCREENS:
 * ```
 * @Composable
 * fun FleetListScreen(viewModel: MainViewModel) {
 *     // Just observe - NEVER call fetch functions
 *     val vehicles by viewModel.vehicles.collectAsState()
 *     val isLoading by viewModel.vehiclesLoading.collectAsState()
 *     
 *     // Show whatever we have (cached) - NO WAITING
 *     VehicleList(vehicles)
 * }
 * ```
 * 
 * =============================================================================
 */
class MainViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "MainViewModel"
        private const val STALE_TIME_MS = 60_000L // 60 seconds
    }
    
    // =========================================================================
    // VEHICLE STATE (observed by FleetListScreen, VehicleDetailsScreen, etc.)
    // =========================================================================
    
    private val _vehicles = MutableStateFlow<List<VehicleData>>(emptyList())
    val vehicles: StateFlow<List<VehicleData>> = _vehicles.asStateFlow()
    
    private val _vehiclesLoading = MutableStateFlow(false)
    val vehiclesLoading: StateFlow<Boolean> = _vehiclesLoading.asStateFlow()
    
    private val _vehicleStats = MutableStateFlow(VehicleStats())
    val vehicleStats: StateFlow<VehicleStats> = _vehicleStats.asStateFlow()
    
    private var lastVehicleFetch: Long = 0
    private var vehiclesFetchJob: Job? = null
    
    // =========================================================================
    // DRIVER STATE (observed by DriverListScreen, DriverDetailsScreen, etc.)
    // =========================================================================
    
    private val _drivers = MutableStateFlow<List<DriverData>>(emptyList())
    val drivers: StateFlow<List<DriverData>> = _drivers.asStateFlow()
    
    private val _driversLoading = MutableStateFlow(false)
    val driversLoading: StateFlow<Boolean> = _driversLoading.asStateFlow()
    
    private var lastDriverFetch: Long = 0
    private var driversFetchJob: Job? = null
    
    // =========================================================================
    // SELECTED ITEM STATE (for detail screens - avoids re-fetch)
    // =========================================================================
    
    private val _selectedVehicle = MutableStateFlow<VehicleData?>(null)
    val selectedVehicle: StateFlow<VehicleData?> = _selectedVehicle.asStateFlow()
    
    private val _selectedDriver = MutableStateFlow<DriverData?>(null)
    val selectedDriver: StateFlow<DriverData?> = _selectedDriver.asStateFlow()
    
    // =========================================================================
    // APP INITIALIZATION - Call once from MainActivity
    // =========================================================================
    
    /**
     * Load all core data at app start
     * Called ONCE from MainActivity.onCreate()
     */
    fun initializeAppData() {
        timber.log.Timber.d("🚀 Initializing app data...")
        loadVehiclesIfNeeded()
        loadDriversIfNeeded()
    }
    
    // =========================================================================
    // VEHICLE OPERATIONS
    // =========================================================================
    
    /**
     * Check if vehicles need refresh (stale or empty)
     */
    private fun shouldRefreshVehicles(): Boolean {
        return System.currentTimeMillis() - lastVehicleFetch > STALE_TIME_MS || _vehicles.value.isEmpty()
    }
    
    /**
     * Load vehicles only if needed (not stale)
     * Screens call this but it's a no-op if data is fresh
     */
    fun loadVehiclesIfNeeded() {
        if (!shouldRefreshVehicles()) {
            timber.log.Timber.d("📦 Vehicles fresh, skipping fetch")
            return
        }
        forceRefreshVehicles()
    }
    
    /**
     * Force refresh vehicles (pull-to-refresh)
     */
    fun forceRefreshVehicles() {
        // Cancel any existing fetch
        vehiclesFetchJob?.cancel()
        
        vehiclesFetchJob = viewModelScope.launch {
            _vehiclesLoading.value = true
            
            try {
                timber.log.Timber.d("🔄 Fetching vehicles...")
                
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.vehicleApi.getVehicles()
                }
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        _vehicles.value = body.data.vehicles
                        _vehicleStats.value = VehicleStats(
                            total = body.data.total,
                            available = body.data.available,
                            inTransit = body.data.inTransit,
                            maintenance = body.data.maintenance
                        )
                        lastVehicleFetch = System.currentTimeMillis()
                        
                        // Also update AppCache for screens that haven't migrated yet
                        AppCache.setVehicles(body.data.vehicles, _vehicleStats.value)
                        
                        timber.log.Timber.d("✅ Loaded ${body.data.vehicles.size} vehicles")
                    }
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "❌ Vehicle fetch failed")
                // Don't clear existing data on error - keep showing cached
            } finally {
                _vehiclesLoading.value = false
            }
        }
    }
    
    /**
     * Select a vehicle for detail screen (instant, no API call)
     */
    fun selectVehicle(vehicleId: String) {
        _selectedVehicle.value = _vehicles.value.find { it.id == vehicleId }
    }
    
    /**
     * Get vehicle by ID (instant from cache)
     */
    fun getVehicle(vehicleId: String): VehicleData? {
        return _vehicles.value.find { it.id == vehicleId }
    }
    
    // =========================================================================
    // DRIVER OPERATIONS
    // =========================================================================
    
    /**
     * Check if drivers need refresh
     */
    private fun shouldRefreshDrivers(): Boolean {
        return System.currentTimeMillis() - lastDriverFetch > STALE_TIME_MS || _drivers.value.isEmpty()
    }
    
    /**
     * Load drivers only if needed
     */
    fun loadDriversIfNeeded() {
        if (!shouldRefreshDrivers()) {
            timber.log.Timber.d("📦 Drivers fresh, skipping fetch")
            return
        }
        forceRefreshDrivers()
    }
    
    /**
     * Force refresh drivers (pull-to-refresh)
     */
    fun forceRefreshDrivers() {
        // Cancel any existing fetch
        driversFetchJob?.cancel()
        
        driversFetchJob = viewModelScope.launch {
            _driversLoading.value = true
            
            try {
                timber.log.Timber.d("🔄 Fetching drivers...")
                
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.driverApi.getDriverList()
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    _drivers.value = data?.drivers ?: emptyList()
                    lastDriverFetch = System.currentTimeMillis()
                    
                    // Also update AppCache
                    AppCache.setDrivers(_drivers.value)
                    
                    timber.log.Timber.d("✅ Loaded ${_drivers.value.size} drivers")
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "❌ Driver fetch failed")
            } finally {
                _driversLoading.value = false
            }
        }
    }
    
    /**
     * Select a driver for detail screen (instant, no API call)
     */
    fun selectDriver(driverId: String) {
        _selectedDriver.value = _drivers.value.find { it.id == driverId }
    }
    
    /**
     * Get driver by ID (instant from cache)
     */
    fun getDriver(driverId: String): DriverData? {
        return _drivers.value.find { it.id == driverId }
    }
    
    // =========================================================================
    // CLEANUP
    // =========================================================================
    
    override fun onCleared() {
        super.onCleared()
        vehiclesFetchJob?.cancel()
        driversFetchJob?.cancel()
        timber.log.Timber.d("🗑️ ViewModel cleared")
    }
}
