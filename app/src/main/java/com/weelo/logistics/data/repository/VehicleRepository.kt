package com.weelo.logistics.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.weelo.logistics.data.api.*
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// =============================================================================
// VEHICLE REPOSITORY - Central Data Management
// =============================================================================
// 
// This repository handles all vehicle-related data operations:
// - Fetching vehicles from backend
// - Registering new vehicles (batch support)
// - Caching for performance
// - Offline support preparation
//
// BACKEND ENDPOINTS:
// - POST /api/v1/vehicles - Register single vehicle
// - POST /api/v1/vehicles/batch - Register multiple vehicles (batch)
// - GET /api/v1/vehicles - Fetch all vehicles with filters
// - GET /api/v1/vehicles/stats - Get status counts
//
// HIERARCHY STRUCTURE:
// TransporterId → VehicleType (Truck) → Category (Open/Container) → Subtype (17ft/19ft) → Vehicle
// =============================================================================

/**
 * Batch vehicle registration request
 * Sends multiple vehicles in a single API call for efficiency
 */
data class BatchRegisterVehicleRequest(
    val vehicles: List<RegisterVehicleRequest>
)

/**
 * Batch registration response
 */
data class BatchRegisterVehicleResponse(
    val success: Boolean,
    val data: BatchRegisterData? = null,
    val message: String? = null,
    val error: ApiError? = null
)

data class BatchRegisterData(
    val vehicles: List<VehicleData>,
    val totalRegistered: Int,
    val failed: List<FailedVehicle>? = null
)

data class FailedVehicle(
    val vehicleNumber: String,
    val reason: String
)

/**
 * Vehicle entry for registration (from UI)
 * Maps to RegisterVehicleRequest for API
 */
data class VehicleRegistrationEntry(
    val vehicleType: String,          // "truck", "tractor", etc.
    val category: String,             // "open", "container", "lcv", etc.
    val categoryName: String,         // "Open Truck", "Container", etc.
    val subtype: String,              // "17_feet", "19_feet", etc.
    val subtypeName: String,          // "17 Feet", "19 Feet", etc.
    val intermediateType: String?,    // "open", "container" for LCV
    val vehicleNumber: String,        // "HR-55-A-1234"
    val manufacturer: String,         // "Tata", "Mahindra", etc.
    val model: String?,               // "LPT 1613" (optional)
    val year: Int?,                   // 2020 (optional)
    val photoUri: Uri? = null,        // Local photo URI
    val capacityTons: Double = 0.0    // From subtype
)

/**
 * Result wrapper for API operations
 */
sealed class VehicleResult<out T> {
    data class Success<T>(val data: T) : VehicleResult<T>()
    data class Error(val message: String, val code: Int? = null) : VehicleResult<Nothing>()
    object Loading : VehicleResult<Nothing>()
}

/**
 * Cached vehicle list with metadata
 */
data class CachedVehicleList(
    val vehicles: List<VehicleData>,
    val total: Int = 0,
    val available: Int = 0,
    val inTransit: Int = 0,
    val maintenance: Int = 0,
    val lastUpdated: Long,
    val isStale: Boolean = false
)

/**
 * Vehicle Repository - Single source of truth for vehicle data
 */
class VehicleRepository private constructor(
    private val context: Context
) {
    private val vehicleApi = RetrofitClient.vehicleApi
    private val cacheMutex = Mutex()
    
    // In-memory cache
    private var cachedVehicles: CachedVehicleList? = null
    private val cacheValidityMs = 5 * 60 * 1000L // 5 minutes cache validity
    
    // Observable state for UI
    private val _vehiclesState = MutableStateFlow<VehicleResult<CachedVehicleList>>(VehicleResult.Loading)
    val vehiclesState: StateFlow<VehicleResult<CachedVehicleList>> = _vehiclesState.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    @SuppressLint("StaticFieldLeak")
    companion object {
        @Volatile
        private var instance: VehicleRepository? = null
        
        fun getInstance(context: Context): VehicleRepository {
            return instance ?: synchronized(this) {
                instance ?: VehicleRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // =========================================================================
    // FETCH OPERATIONS
    // =========================================================================
    
    /**
     * Fetch vehicles with caching support
     * Uses cache if valid, otherwise fetches from backend
     * 
     * @param forceRefresh Force fetch from backend ignoring cache
     * @param status Optional status filter
     */
    suspend fun fetchVehicles(
        forceRefresh: Boolean = false,
        status: String? = null
    ): VehicleResult<CachedVehicleList> = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            // Check cache validity
            val cache = cachedVehicles
            if (!forceRefresh && cache != null && !isCacheStale(cache) && status == null) {
                return@withContext VehicleResult.Success(cache)
            }
            
            _isRefreshing.value = true
            
            try {
                val response = vehicleApi.getVehicles()
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    val newCache = CachedVehicleList(
                        vehicles = data?.vehicles ?: emptyList(),
                        total = data?.total ?: 0,
                        available = data?.available ?: 0,
                        inTransit = data?.inTransit ?: 0,
                        maintenance = data?.maintenance ?: 0,
                        lastUpdated = System.currentTimeMillis()
                    )
                    
                    // Update cache only for unfiltered requests
                    if (status == null) {
                        cachedVehicles = newCache
                    }
                    
                    _vehiclesState.value = VehicleResult.Success(newCache)
                    VehicleResult.Success(newCache)
                } else {
                    // Handle HTTP errors properly
                    val errorMsg = when (response.code()) {
                        401 -> "Session expired. Please login again."
                        403 -> "Access denied. Only transporters can view vehicles."
                        404 -> "Vehicles endpoint not found"
                        500 -> "Server error. Please try again later."
                        else -> response.body()?.error?.message ?: "Failed to fetch vehicles (${response.code()})"
                    }
                    VehicleResult.Error(errorMsg, response.code())
                }
            } catch (e: com.google.gson.JsonSyntaxException) {
                // JSON parsing error - server returned invalid/empty response
                timber.log.Timber.e("JSON parse error: ${e.message}")
                val staleCache = cachedVehicles?.copy(isStale = true)
                if (staleCache != null) {
                    _vehiclesState.value = VehicleResult.Success(staleCache)
                    return@withContext VehicleResult.Success(staleCache)
                }
                VehicleResult.Error("Server returned invalid response. Please check your connection.")
            } catch (e: java.net.ConnectException) {
                // Connection refused - server not running
                timber.log.Timber.e("Connection error: ${e.message}")
                val staleCache = cachedVehicles?.copy(isStale = true)
                if (staleCache != null) {
                    _vehiclesState.value = VehicleResult.Success(staleCache)
                    return@withContext VehicleResult.Success(staleCache)
                }
                VehicleResult.Error("Cannot connect to server. Is the backend running?")
            } catch (e: java.net.SocketTimeoutException) {
                // Timeout
                timber.log.Timber.e("Timeout error: ${e.message}")
                val staleCache = cachedVehicles?.copy(isStale = true)
                if (staleCache != null) {
                    _vehiclesState.value = VehicleResult.Success(staleCache)
                    return@withContext VehicleResult.Success(staleCache)
                }
                VehicleResult.Error("Connection timed out. Please try again.")
            } catch (e: Exception) {
                // Return stale cache if available
                timber.log.Timber.e(e, "Fetch error: ${e.message}")
                val staleCache = cachedVehicles?.copy(isStale = true)
                if (staleCache != null) {
                    _vehiclesState.value = VehicleResult.Success(staleCache)
                    return@withContext VehicleResult.Success(staleCache)
                }
                VehicleResult.Error(e.message ?: "Network error")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    /**
     * Preload vehicles in background (call on app start or before navigation)
     */
    suspend fun preloadVehicles() {
        if (cachedVehicles == null || isCacheStale(cachedVehicles!!)) {
            fetchVehicles(forceRefresh = false)
        }
    }
    
    /**
     * Get cached vehicles synchronously (for immediate UI rendering)
     * Returns null if cache is not available
     */
    fun getCachedVehicles(): CachedVehicleList? = cachedVehicles
    
    // =========================================================================
    // REGISTRATION OPERATIONS
    // =========================================================================
    
    /**
     * Register multiple vehicles in batch using UPSERT
     * 
     * Uses the upsert endpoint which:
     * - Creates new vehicles if they don't exist
     * - Updates existing vehicles if you own them
     * - Returns error only if vehicle is owned by someone else
     * 
     * Flow:
     * 1. Convert UI entries to API request format
     * 2. Send upsert request for each vehicle
     * 3. Invalidate cache on success
     * 4. Return registered/updated vehicles
     * 
     * @param entries List of vehicle entries from UI
     */
    suspend fun registerVehiclesBatch(
        entries: List<VehicleRegistrationEntry>
    ): VehicleResult<List<VehicleData>> = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) {
            return@withContext VehicleResult.Error("No vehicles to register")
        }
        
        try {
            // Convert to API request format
            val requests = entries.map { entry ->
                RegisterVehicleRequest(
                    vehicleNumber = entry.vehicleNumber.uppercase().trim(),
                    vehicleType = entry.category, // Use category as vehicle type for backend
                    vehicleSubtype = entry.subtypeName,
                    model = entry.model ?: entry.manufacturer, // Use manufacturer as model fallback
                    year = entry.year,
                    capacity = "${entry.capacityTons} MT"
                )
            }
            
            // Use UPSERT endpoint - creates if new, updates if exists
            val savedVehicles = mutableListOf<VehicleData>()
            val failedVehicles = mutableListOf<FailedVehicle>()
            
            for (request in requests) {
                try {
                    // Use upsert instead of register - handles existing vehicles gracefully
                    val response = vehicleApi.upsertVehicle(request)
                    
                    if (response.isSuccessful && response.body()?.success == true) {
                        response.body()?.data?.vehicle?.let { vehicle ->
                            savedVehicles.add(vehicle)
                            val action = if (response.body()?.data?.isNew == true) "registered" else "updated"
                            timber.log.Timber.i("Vehicle ${vehicle.vehicleNumber} $action successfully")
                        }
                    } else {
                        // Handle unsuccessful response
                        val errorCode = response.body()?.error?.code
                        val errorMsg = when {
                            errorCode == "VEHICLE_EXISTS" -> "Vehicle registered by another transporter"
                            response.body()?.error?.message != null -> response.body()?.error?.message
                            response.code() == 401 -> "Session expired. Please login again."
                            response.code() == 403 -> "Access denied."
                            response.code() == 409 -> "Vehicle registered by another transporter."
                            response.code() >= 500 -> "Server error. Please try again."
                            else -> "Save failed (${response.code()})"
                        }
                        failedVehicles.add(
                            FailedVehicle(
                                vehicleNumber = request.vehicleNumber,
                                reason = errorMsg ?: "Save failed"
                            )
                        )
                    }
                } catch (e: com.google.gson.JsonSyntaxException) {
                    timber.log.Timber.e("JSON parse error for ${request.vehicleNumber}: ${e.message}")
                    failedVehicles.add(
                        FailedVehicle(
                            vehicleNumber = request.vehicleNumber,
                            reason = "Server returned invalid response. Please try again."
                        )
                    )
                } catch (e: java.net.ConnectException) {
                    timber.log.Timber.e("Connection error: ${e.message}")
                    failedVehicles.add(
                        FailedVehicle(
                            vehicleNumber = request.vehicleNumber,
                            reason = "Cannot connect to server."
                        )
                    )
                } catch (e: java.net.SocketTimeoutException) {
                    timber.log.Timber.e("Timeout error: ${e.message}")
                    failedVehicles.add(
                        FailedVehicle(
                            vehicleNumber = request.vehicleNumber,
                            reason = "Connection timed out."
                        )
                    )
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Save error: ${e.message}")
                    failedVehicles.add(
                        FailedVehicle(
                            vehicleNumber = request.vehicleNumber,
                            reason = e.message ?: "Network error"
                        )
                    )
                }
            }
            
            // Invalidate cache after registration
            invalidateCache()
            
            if (savedVehicles.isNotEmpty()) {
                if (failedVehicles.isNotEmpty()) {
                    // Some succeeded, some failed - return partial success
                    timber.log.Timber.w(
                        "Partial success: ${savedVehicles.size} saved, ${failedVehicles.size} failed")
                }
                VehicleResult.Success(savedVehicles)
            } else if (failedVehicles.isNotEmpty()) {
                VehicleResult.Error("Failed to save vehicles: ${failedVehicles.first().reason}")
            } else {
                VehicleResult.Error("No vehicles were saved")
            }
        } catch (e: Exception) {
            VehicleResult.Error(e.message ?: "Save failed")
        }
    }
    
    /**
     * Register or update a single vehicle using UPSERT
     * 
     * Uses upsert endpoint which:
     * - Creates new vehicle if it doesn't exist
     * - Updates existing vehicle if you own it
     * - Returns error only if vehicle is owned by someone else
     */
    suspend fun registerVehicle(
        entry: VehicleRegistrationEntry
    ): VehicleResult<VehicleData> = withContext(Dispatchers.IO) {
        try {
            val request = RegisterVehicleRequest(
                vehicleNumber = entry.vehicleNumber.uppercase().trim(),
                vehicleType = entry.category,
                vehicleSubtype = entry.subtypeName,
                model = entry.model ?: entry.manufacturer,
                year = entry.year,
                capacity = "${entry.capacityTons} MT"
            )
            
            // Use upsert - handles both new and existing vehicles
            val response = vehicleApi.upsertVehicle(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val vehicle = response.body()?.data?.vehicle
                if (vehicle != null) {
                    invalidateCache()
                    val action = if (response.body()?.data?.isNew == true) "registered" else "updated"
                    timber.log.Timber.i("Vehicle ${vehicle.vehicleNumber} $action")
                    VehicleResult.Success(vehicle)
                } else {
                    VehicleResult.Error("Invalid response from server")
                }
            } else {
                // Handle error responses
                val errorCode = response.body()?.error?.code
                val errorMsg = when {
                    errorCode == "VEHICLE_EXISTS" -> "Vehicle registered by another transporter."
                    response.body()?.error?.message != null -> response.body()?.error?.message
                    response.code() == 401 -> "Session expired. Please login again."
                    response.code() == 403 -> "Access denied. Only transporters can register vehicles."
                    response.code() == 409 -> "Vehicle registered by another transporter."
                    response.code() >= 500 -> "Server error. Please try again later."
                    else -> "Failed to save vehicle (${response.code()})"
                }
                VehicleResult.Error(errorMsg ?: "Save failed", response.code())
            }
        } catch (e: com.google.gson.JsonSyntaxException) {
            timber.log.Timber.e("JSON parse error: ${e.message}")
            VehicleResult.Error("Server returned invalid response. Please try again.")
        } catch (e: java.net.ConnectException) {
            timber.log.Timber.e("Connection error: ${e.message}")
            VehicleResult.Error("Cannot connect to server. Please check your connection.")
        } catch (e: java.net.SocketTimeoutException) {
            timber.log.Timber.e("Timeout error: ${e.message}")
            VehicleResult.Error("Connection timed out. Please try again.")
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Save error: ${e.message}")
            VehicleResult.Error(e.message ?: "Save failed")
        }
    }
    
    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================
    
    private fun isCacheStale(cache: CachedVehicleList): Boolean {
        return System.currentTimeMillis() - cache.lastUpdated > cacheValidityMs
    }
    
    /**
     * Invalidate cache to force refresh on next fetch
     */
    fun invalidateCache() {
        cachedVehicles = null
    }
    
    /**
     * Clear all cached data (call on logout)
     */
    fun clearCache() {
        cachedVehicles = null
        _vehiclesState.value = VehicleResult.Loading
    }
    
    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    
    /**
     * Get vehicle by ID from cache or fetch
     */
    suspend fun getVehicleById(vehicleId: String): VehicleResult<VehicleData> = withContext(Dispatchers.IO) {
        // Check cache first
        cachedVehicles?.vehicles?.find { it.id == vehicleId }?.let {
            return@withContext VehicleResult.Success(it)
        }
        
        // Fetch from backend
        try {
            val response = vehicleApi.getVehicleById(vehicleId)
            if (response.isSuccessful && response.body()?.success == true) {
                val vehicle = response.body()?.data?.vehicle
                if (vehicle != null) {
                    VehicleResult.Success(vehicle)
                } else {
                    VehicleResult.Error("Vehicle not found")
                }
            } else {
                VehicleResult.Error(response.body()?.error?.message ?: "Failed to fetch vehicle")
            }
        } catch (e: Exception) {
            VehicleResult.Error(e.message ?: "Network error")
        }
    }
    
    /**
     * Convert VehicleData (API) to Vehicle (UI model)
     */
    fun mapToUiModel(data: VehicleData): Vehicle? {
        val category = VehicleCatalog.getAllCategories().find { 
            it.id == data.vehicleType || it.name.equals(data.vehicleType, ignoreCase = true)
        } ?: return null
        
        val subtypes = VehicleCatalog.getSubtypesForCategory(category.id)
        val subtype = subtypes.find { 
            it.name.equals(data.vehicleSubtype, ignoreCase = true) ||
            it.id == data.vehicleSubtype
        } ?: subtypes.firstOrNull() ?: return null
        
        return Vehicle(
            id = data.id,
            transporterId = data.transporterId,
            category = category,
            subtype = subtype,
            vehicleNumber = data.vehicleNumber,
            model = data.model,
            year = data.year,
            assignedDriverId = data.assignedDriverId,
            status = when (data.status.lowercase()) {
                "available" -> VehicleStatus.AVAILABLE
                "in_transit" -> VehicleStatus.IN_TRANSIT
                "maintenance" -> VehicleStatus.MAINTENANCE
                else -> VehicleStatus.INACTIVE
            }
        )
    }
    
    /**
     * Map list of VehicleData to UI models
     */
    fun mapToUiModels(dataList: List<VehicleData>): List<Vehicle> {
        return dataList.mapNotNull { mapToUiModel(it) }
    }
}
