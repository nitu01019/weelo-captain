package com.weelo.logistics.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.weelo.logistics.data.api.AcceptBroadcastRequest
import com.weelo.logistics.data.api.AcceptBroadcastResponse
import com.weelo.logistics.data.api.BroadcastResponseData
import com.weelo.logistics.data.model.RequestedVehicle
import com.weelo.logistics.data.api.DeclineBroadcastRequest
import com.weelo.logistics.data.api.DeclineBroadcastResponse
import com.weelo.logistics.data.model.BroadcastStatus
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.model.Location
import com.weelo.logistics.data.model.TruckCategory
import com.weelo.logistics.data.model.VehicleCatalog
import com.weelo.logistics.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import timber.log.Timber

// =============================================================================
// BROADCAST REPOSITORY - Real-time Booking Broadcast Management
// =============================================================================
// 
// This repository handles all broadcast-related data operations:
// - Fetching active broadcasts from backend
// - Accepting/Declining broadcasts
// - Caching for performance
// - Real-time updates via WebSocket
//
// BACKEND ENDPOINTS:
// - GET /api/v1/broadcasts/active - Get active broadcasts for transporter
// - GET /api/v1/broadcasts/{id} - Get broadcast details
// - POST /api/v1/broadcasts/{id}/accept - Accept a broadcast
// - POST /api/v1/broadcasts/{id}/decline - Decline a broadcast
//
// FLOW:
// Customer creates booking → Backend broadcasts to matching transporters
// → Transporter views broadcast → Accepts/Assigns trucks
//
// SCALABILITY:
// - Designed for millions of concurrent broadcasts
// - Caching reduces API calls
// - WebSocket for real-time updates (no polling)
// - Batch operations for efficiency
// =============================================================================

/**
 * Result wrapper for broadcast API operations
 * Matches the pattern used in VehicleRepository
 */
sealed class BroadcastResult<out T> {
    data class Success<T>(val data: T) : BroadcastResult<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val apiCode: String? = null
    ) : BroadcastResult<Nothing>()
    object Loading : BroadcastResult<Nothing>()
}

/**
 * Cached broadcast list with metadata
 */
data class CachedBroadcastList(
    val broadcasts: List<BroadcastTrip>,
    val totalCount: Int,
    val lastUpdated: Long,
    val isStale: Boolean = false
)

// Note: AcceptBroadcastRequest, AcceptBroadcastResponse, 
// DeclineBroadcastRequest, DeclineBroadcastResponse 
// are imported from com.weelo.logistics.data.api.BroadcastApiService

/**
 * Broadcast Repository - Single source of truth for broadcast data
 * 
 * SINGLETON PATTERN: Same as VehicleRepository for consistency
 * THREAD-SAFE: Uses Mutex for cache synchronization
 * SCALABLE: Designed for high-throughput broadcast handling
 */
class BroadcastRepository private constructor(
    private val context: Context
) {
    private val broadcastApi = RetrofitClient.broadcastApi
    private val cacheMutex = Mutex()
    
    // In-memory cache - Short validity for real-time broadcasts
    private var cachedBroadcasts: CachedBroadcastList? = null
    private val cacheValidityMs = 30 * 1000L // 30 seconds - broadcasts change frequently
    
    // Observable state for UI
    private val _broadcastsState = MutableStateFlow<BroadcastResult<CachedBroadcastList>>(BroadcastResult.Loading)
    val broadcastsState: StateFlow<BroadcastResult<CachedBroadcastList>> = _broadcastsState.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    // Track accepted broadcasts locally for immediate UI feedback
    private val acceptedBroadcastIds = mutableSetOf<String>()
    
    @SuppressLint("StaticFieldLeak")
    companion object {
        private const val TAG = "BroadcastRepository"
        private val ISO_UTC_WITH_MILLIS: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .withZone(ZoneOffset.UTC)
        private val ISO_UTC_NO_MILLIS: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .withZone(ZoneOffset.UTC)
        
        @Volatile
        private var instance: BroadcastRepository? = null
        
        /**
         * Get singleton instance
         * Thread-safe double-checked locking
         */
        fun getInstance(context: Context): BroadcastRepository {
            return instance ?: synchronized(this) {
                instance ?: BroadcastRepository(context.applicationContext).also { instance = it }
            }
        }
        
        /**
         * Clear instance (for testing or logout)
         */
        fun clearInstance() {
            instance = null
        }
    }
    
    // =========================================================================
    // FETCH OPERATIONS
    // =========================================================================
    
    /**
     * Fetch active broadcasts with caching support
     * 
     * @param forceRefresh Force fetch from backend ignoring cache
     * @param vehicleType Optional filter by vehicle type
     * @param userId Transporter/Driver ID for filtering
     * @return BroadcastResult with list of broadcasts
     */
    suspend fun fetchActiveBroadcasts(
        forceRefresh: Boolean = false,
        vehicleType: String? = null,
        userId: String? = null
    ): BroadcastResult<CachedBroadcastList> = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            // Check cache validity (30 seconds for real-time data)
            val cached = cachedBroadcasts
            val now = System.currentTimeMillis()
            
            if (!forceRefresh && cached != null && (now - cached.lastUpdated) < cacheValidityMs) {
                // Return cached data if still valid
                timber.log.Timber.d("✅ Returning cached broadcasts (${cached.broadcasts.size} items)")
                _broadcastsState.value = BroadcastResult.Success(cached)
                return@withContext BroadcastResult.Success(cached)
            }
            
            // Fetch from backend
            _isRefreshing.value = true
            
            try {
                val token = RetrofitClient.getAccessToken()
                if (token.isNullOrEmpty()) {
                    val error = BroadcastResult.Error("Not authenticated", 401)
                    _broadcastsState.value = error
                    _isRefreshing.value = false
                    return@withContext error
                }
                
                val effectiveUserId = userId ?: RetrofitClient.getUserId() ?: ""
                
                timber.log.Timber.d("📡 Fetching broadcasts for user: $effectiveUserId")
                
                val response = broadcastApi.getActiveBroadcasts(
                    token = "Bearer $token",
                    driverId = effectiveUserId,
                    vehicleType = vehicleType
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val broadcasts = response.body()?.broadcasts ?: emptyList()
                    
                    // Filter out already accepted broadcasts
                    val filteredBroadcasts = broadcasts.filterNot { 
                        acceptedBroadcastIds.contains(it.broadcastId) 
                    }
                    
                    // Map API response to domain model
                    val mappedBroadcasts = filteredBroadcasts.map { mapToBroadcastTrip(it) }
                    
                    val cachedList = CachedBroadcastList(
                        broadcasts = mappedBroadcasts,
                        totalCount = mappedBroadcasts.size,
                        lastUpdated = now,
                        isStale = false
                    )
                    
                    // Update cache
                    cachedBroadcasts = cachedList
                    
                    timber.log.Timber.i("✅ Fetched ${mappedBroadcasts.size} active broadcasts")
                    
                    val result = BroadcastResult.Success(cachedList)
                    _broadcastsState.value = result
                    _isRefreshing.value = false
                    return@withContext result
                    
                } else {
                    val errorMsg = response.body()?.error?.message 
                        ?: response.errorBody()?.string() 
                        ?: "Failed to fetch broadcasts"
                    val errorCode = response.code()
                    
                    timber.log.Timber.e("❌ Fetch broadcasts failed: $errorMsg (code: $errorCode)")
                    
                    // Return stale cache if available
                    if (cached != null) {
                        val staleCache = cached.copy(isStale = true)
                        _broadcastsState.value = BroadcastResult.Success(staleCache)
                        _isRefreshing.value = false
                        return@withContext BroadcastResult.Success(staleCache)
                    }
                    
                    val error = BroadcastResult.Error(errorMsg, errorCode)
                    _broadcastsState.value = error
                    _isRefreshing.value = false
                    return@withContext error
                }
                
            } catch (e: Exception) {
                timber.log.Timber.e(e, "❌ Network error fetching broadcasts")
                
                // Return stale cache if available
                @Suppress("NAME_SHADOWING")
                val cached = cachedBroadcasts
                if (cached != null) {
                    val staleCache = cached.copy(isStale = true)
                    _broadcastsState.value = BroadcastResult.Success(staleCache)
                    _isRefreshing.value = false
                    return@withContext BroadcastResult.Success(staleCache)
                }
                
                val error = BroadcastResult.Error(e.message ?: "Network error", null)
                _broadcastsState.value = error
                _isRefreshing.value = false
                return@withContext error
            }
        }
    }
    
    /**
     * Get single broadcast by ID
     */
    suspend fun getBroadcastById(broadcastId: String): BroadcastResult<BroadcastTrip> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }
            
            val response = broadcastApi.getBroadcastById(
                token = "Bearer $token",
                broadcastId = broadcastId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val broadcast = response.body()?.broadcast
                if (broadcast != null) {
                    return@withContext BroadcastResult.Success(mapToBroadcastTrip(broadcast))
                }
            }
            
            val errorMsg = response.body()?.error?.message ?: "Broadcast not found"
            return@withContext BroadcastResult.Error(errorMsg, response.code())
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Error fetching broadcast $broadcastId")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }
    
    // =========================================================================
    // ACCEPT/DECLINE OPERATIONS
    // =========================================================================
    
    /**
     * Accept a broadcast
     * 
     * @param broadcastId The broadcast to accept
     * @param vehicleId The vehicle to assign
     * @param driverId Optional driver ID (uses current user if not specified)
     * @param estimatedArrival Optional ETA
     * @param notes Optional notes
     */
    suspend fun acceptBroadcast(
        broadcastId: String,
        vehicleId: String,
        driverId: String? = null,
        estimatedArrival: String? = null,
        notes: String? = null,
        idempotencyKey: String? = null
    ): BroadcastResult<AcceptBroadcastResponse> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }
            
            val effectiveDriverId = driverId ?: RetrofitClient.getUserId() ?: ""
            
            timber.log.Timber.d("📤 Accepting broadcast: $broadcastId with vehicle: $vehicleId")
            
            val request = AcceptBroadcastRequest(
                driverId = effectiveDriverId,
                vehicleId = vehicleId,
                estimatedArrival = estimatedArrival,
                notes = notes
            )
            
            val response = broadcastApi.acceptBroadcast(
                token = "Bearer $token",
                idempotencyKey = idempotencyKey,
                broadcastId = broadcastId,
                request = request
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                
                // Track locally for immediate UI update
                acceptedBroadcastIds.add(broadcastId)
                
                // Invalidate cache to refresh list
                invalidateCache()
                
                timber.log.Timber.i("✅ Broadcast accepted! Assignment: ${body.assignmentId}, Trip: ${body.tripId}")
                
                return@withContext BroadcastResult.Success(body)
            } else {
                val (errorMsg, apiCode) = parseApiError(
                    response.errorBody()?.string(),
                    response.body()?.error
                )
                timber.log.Timber.e("❌ Accept broadcast failed: code=${apiCode ?: "unknown"} msg=$errorMsg")
                return@withContext BroadcastResult.Error(errorMsg, response.code(), apiCode)
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Network error accepting broadcast")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }

    private fun parseApiError(rawErrorBody: String?, fallbackMessage: String?): Pair<String, String?> {
        if (!rawErrorBody.isNullOrBlank()) {
            try {
                val json = JSONObject(rawErrorBody)
                val error = json.optJSONObject("error")
                val code = error?.optString("code")?.takeIf { it.isNotBlank() }
                val message = error?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json.optString("message").takeIf { it.isNotBlank() }
                if (!message.isNullOrBlank()) {
                    return message to code
                }
            } catch (_: Exception) {
                // Fall through to fallback parsing below.
            }
        }

        val fallback = fallbackMessage?.takeIf { it.isNotBlank() } ?: "Failed to accept broadcast"
        return fallback to null
    }
    
    /**
     * Decline a broadcast
     */
    suspend fun declineBroadcast(
        broadcastId: String,
        reason: String,
        driverId: String? = null,
        notes: String? = null
    ): BroadcastResult<DeclineBroadcastResponse> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }
            
            val effectiveDriverId = driverId ?: RetrofitClient.getUserId() ?: ""
            
            timber.log.Timber.d("📤 Declining broadcast: $broadcastId, reason: $reason")
            
            val request = DeclineBroadcastRequest(
                driverId = effectiveDriverId,
                reason = reason,
                notes = notes
            )
            
            val response = broadcastApi.declineBroadcast(
                token = "Bearer $token",
                broadcastId = broadcastId,
                request = request
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                // Remove from local list
                acceptedBroadcastIds.remove(broadcastId)
                
                // Invalidate cache
                invalidateCache()
                
                timber.log.Timber.i("✅ Broadcast declined")
                return@withContext BroadcastResult.Success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to decline broadcast"
                return@withContext BroadcastResult.Error(errorMsg, response.code())
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Network error declining broadcast")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }
    
    // =========================================================================
    // REAL-TIME UPDATES (WebSocket Integration)
    // =========================================================================
    
    /**
     * Add new broadcast from WebSocket
     * Called when 'new_broadcast' event is received
     */
    fun addBroadcastFromWebSocket(broadcast: BroadcastTrip) {
        val currentCache = cachedBroadcasts
        if (currentCache != null) {
            // Prepend new broadcast to list
            val updatedList = listOf(broadcast) + currentCache.broadcasts.filterNot { 
                it.broadcastId == broadcast.broadcastId 
            }
            cachedBroadcasts = currentCache.copy(
                broadcasts = updatedList,
                totalCount = updatedList.size,
                lastUpdated = System.currentTimeMillis()
            )
            _broadcastsState.value = BroadcastResult.Success(cachedBroadcasts!!)
            
            timber.log.Timber.i("📥 New broadcast added via WebSocket: ${broadcast.broadcastId}")
        }
    }
    
    /**
     * Update broadcast from WebSocket
     * Called when 'broadcast_updated' event is received
     */
    fun updateBroadcastFromWebSocket(broadcastId: String, updates: Map<String, Any>) {
        val currentCache = cachedBroadcasts ?: return
        
        val updatedBroadcasts = currentCache.broadcasts.map { broadcast ->
            if (broadcast.broadcastId == broadcastId) {
                // Apply updates to the broadcast
                broadcast.copy(
                    trucksFilledSoFar = (updates["trucksFilled"] as? Number)?.toInt() ?: broadcast.trucksFilledSoFar,
                    status = (updates["status"] as? String)?.let { parseStatus(it) } ?: broadcast.status
                )
            } else {
                broadcast
            }
        }
        
        cachedBroadcasts = currentCache.copy(
            broadcasts = updatedBroadcasts,
            lastUpdated = System.currentTimeMillis()
        )
        _broadcastsState.value = BroadcastResult.Success(cachedBroadcasts!!)
        
        timber.log.Timber.d("🔄 Broadcast updated via WebSocket: $broadcastId")
    }
    
    /**
     * Remove broadcast from WebSocket
     * Called when broadcast is cancelled, expired, or fully filled
     */
    fun removeBroadcastFromWebSocket(broadcastId: String) {
        val currentCache = cachedBroadcasts ?: return
        
        val filteredBroadcasts = currentCache.broadcasts.filterNot { 
            it.broadcastId == broadcastId 
        }
        
        cachedBroadcasts = currentCache.copy(
            broadcasts = filteredBroadcasts,
            totalCount = filteredBroadcasts.size,
            lastUpdated = System.currentTimeMillis()
        )
        _broadcastsState.value = BroadcastResult.Success(cachedBroadcasts!!)
        
        timber.log.Timber.d("🗑️ Broadcast removed via WebSocket: $broadcastId")
    }
    
    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================
    
    /**
     * Invalidate cache to force refresh on next fetch
     */
    fun invalidateCache() {
        cachedBroadcasts = cachedBroadcasts?.copy(isStale = true, lastUpdated = 0)
        timber.log.Timber.d("🔄 Cache invalidated")
    }
    
    /**
     * Clear all cached data
     * Call on logout or when user changes
     */
    fun clearCache() {
        cachedBroadcasts = null
        acceptedBroadcastIds.clear()
        _broadcastsState.value = BroadcastResult.Loading
        timber.log.Timber.d("🗑️ Cache cleared")
    }
    
    // =========================================================================
    // MAPPING FUNCTIONS
    // =========================================================================
    
    /**
     * Map API response to domain model
     * Uses BroadcastResponseData which handles both backend naming conventions
     */
    private fun mapToBroadcastTrip(data: BroadcastResponseData): BroadcastTrip {
        val pickup = data.getEffectivePickup()
        val drop = data.getEffectiveDrop()
        
        return BroadcastTrip(
            broadcastId = data.getEffectiveId(),
            customerId = data.customerId ?: "",
            customerName = data.customerName ?: "Customer",
            customerMobile = data.getEffectiveCustomerMobile(),
            pickupLocation = Location(
                latitude = pickup?.latitude ?: 0.0,
                longitude = pickup?.longitude ?: 0.0,
                address = pickup?.address ?: "",
                city = pickup?.city,
                state = pickup?.state,
                pincode = pickup?.pincode
            ),
            dropLocation = Location(
                latitude = drop?.latitude ?: 0.0,
                longitude = drop?.longitude ?: 0.0,
                address = drop?.address ?: "",
                city = drop?.city,
                state = drop?.state,
                pincode = drop?.pincode
            ),
            distance = data.getEffectiveDistance(),
            estimatedDuration = data.estimatedDuration ?: 0,
            totalTrucksNeeded = data.getEffectiveTrucksNeeded(),
            trucksFilledSoFar = data.getEffectiveTrucksFilled(),
            vehicleType = parseVehicleType(data.vehicleType),
            goodsType = data.goodsType ?: "General",
            weight = data.weight,
            farePerTruck = data.getEffectiveFarePerTruck(),
            totalFare = data.getEffectiveTotalFare(),
            status = parseStatus(data.status),
            broadcastTime = parseTimestamp(data.createdAt),
            expiryTime = parseTimestamp(data.expiresAt),
            notes = data.notes,
            isUrgent = data.isUrgent ?: false,
            requestedVehicles = mapRequestedVehicles(data.requestedVehicles)
        )
    }
    
    /**
     * Map requested vehicles from API response
     */
    private fun mapRequestedVehicles(vehicles: List<com.weelo.logistics.data.api.RequestedVehicleData>?): List<RequestedVehicle> {
        if (vehicles.isNullOrEmpty()) return emptyList()
        
        return vehicles.map { v ->
            RequestedVehicle(
                vehicleType = v.vehicleType ?: "",
                vehicleSubtype = v.vehicleSubtype ?: "",
                count = v.count ?: 0,
                filledCount = v.filledCount ?: 0,
                farePerTruck = v.farePerTruck ?: 0.0,
                capacityTons = v.capacityTons ?: 0.0
            )
        }.filter { it.count > 0 }  // Only include valid entries
    }
    
    private fun parseVehicleType(type: String?): TruckCategory {
        return when (type?.lowercase()) {
            "open" -> VehicleCatalog.OPEN_TRUCK
            "container" -> VehicleCatalog.CONTAINER
            "tipper" -> VehicleCatalog.TIPPER
            "tanker" -> VehicleCatalog.TANKER
            "trailer" -> VehicleCatalog.TRAILER
            "lcv" -> VehicleCatalog.LCV
            "mini" -> VehicleCatalog.MINI_PICKUP
            "bulker" -> VehicleCatalog.BULKER
            "dumper" -> VehicleCatalog.DUMPER
            else -> VehicleCatalog.OPEN_TRUCK
        }
    }
    
    private fun parseStatus(status: String?): BroadcastStatus {
        return when (status?.lowercase()) {
            "active" -> BroadcastStatus.ACTIVE
            "partially_filled" -> BroadcastStatus.PARTIALLY_FILLED
            "fully_filled" -> BroadcastStatus.FULLY_FILLED
            "expired" -> BroadcastStatus.EXPIRED
            "cancelled" -> BroadcastStatus.CANCELLED
            else -> BroadcastStatus.ACTIVE
        }
    }
    
    private fun parseTimestamp(timestamp: String?): Long {
        if (timestamp.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            Instant.from(ISO_UTC_WITH_MILLIS.parse(timestamp)).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                Instant.from(ISO_UTC_NO_MILLIS.parse(timestamp)).toEpochMilli()
            } catch (e2: DateTimeParseException) {
                Timber.w(e2, "$TAG: Failed to parse timestamp: $timestamp")
                System.currentTimeMillis()
            }
        }
    }
    
    // =========================================================================
    // TRUCK HOLD API - Atomic Locking for Race Condition Prevention
    // =========================================================================
    // 
    // FLOW:
    // 1. holdTrucks() → Trucks held for 15 seconds (Redis lock)
    // 2. confirmHoldWithAssignments() → Assign vehicles + drivers atomically
    // 3. releaseHold() → Release if user cancels or timeout
    //
    // This prevents double-booking when multiple transporters try to accept
    // the same trucks simultaneously.
    // =========================================================================
    
    private val truckHoldApi by lazy { RetrofitClient.truckHoldApi }
    
    /**
     * Hold trucks for selection (15-second lock)
     * 
     * IMPORTANT: This is the first step in the acceptance flow.
     * Trucks are locked atomically via Redis - only ONE transporter wins.
     * 
     * @param orderId The order/broadcast ID
     * @param vehicleType The vehicle type (e.g., "Open", "Container")
     * @param vehicleSubtype The subtype (e.g., "17ft", "20-24 Ton")
     * @param quantity Number of trucks to hold
     * @return HoldResult with holdId if successful
     */
    suspend fun holdTrucks(
        orderId: String,
        vehicleType: String,
        vehicleSubtype: String,
        quantity: Int
    ): BroadcastResult<HoldTrucksResult> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }
            
            timber.log.Timber.i("🔒 Holding $quantity trucks: $vehicleType $vehicleSubtype for order $orderId")
            
            val request = com.weelo.logistics.data.api.HoldTrucksRequest(
                orderId = orderId,
                vehicleType = vehicleType,
                vehicleSubtype = vehicleSubtype,
                quantity = quantity
            )
            
            val response = truckHoldApi.holdTrucks(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                timber.log.Timber.i("✅ Trucks held! HoldID: ${data.holdId}, Expires: ${data.expiresAt}")
                
                return@withContext BroadcastResult.Success(
                    HoldTrucksResult(
                        holdId = data.holdId,
                        expiresAt = data.expiresAt,
                        heldQuantity = data.heldQuantity
                    )
                )
            } else {
                val errorMsg = response.body()?.error?.message 
                    ?: response.body()?.message
                    ?: response.errorBody()?.string()
                    ?: "Failed to hold trucks"
                val errorCode = response.body()?.error?.code
                timber.log.Timber.e("❌ Hold failed: $errorMsg (code: $errorCode)")
                return@withContext BroadcastResult.Error(errorMsg, response.code())
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Network error holding trucks")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }
    
    /**
     * Confirm hold with vehicle and driver assignments
     * 
     * IMPORTANT: This is the PRODUCTION endpoint that:
     * 1. Validates vehicle availability (not in another trip)
     * 2. Validates driver availability (not on another trip)
     * 3. Creates assignment records
     * 4. Updates vehicle status to 'in_transit'
     * 5. Notifies drivers and customer
     * 
     * @param holdId The hold ID from holdTrucks()
     * @param assignments List of (vehicleId, driverId) pairs
     * @return ConfirmResult with assignmentIds and tripIds
     */
    suspend fun confirmHoldWithAssignments(
        holdId: String,
        assignments: List<Pair<String, String>> // vehicleId to driverId
    ): BroadcastResult<ConfirmHoldResult> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }
            
            timber.log.Timber.i("📤 Confirming hold $holdId with ${assignments.size} assignments")
            
            val request = com.weelo.logistics.data.api.ConfirmHoldWithAssignmentsRequest(
                holdId = holdId,
                assignments = assignments.map { (vehicleId, driverId) ->
                    com.weelo.logistics.data.api.VehicleDriverAssignment(
                        vehicleId = vehicleId,
                        driverId = driverId
                    )
                }
            )
            
            val response = truckHoldApi.confirmHoldWithAssignments(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                timber.log.Timber.i("✅ Hold confirmed! Assignments: ${data.assignmentIds.size}, Trips: ${data.tripIds.size}")
                
                // Invalidate cache
                invalidateCache()
                
                return@withContext BroadcastResult.Success(
                    ConfirmHoldResult(
                        assignmentIds = data.assignmentIds,
                        tripIds = data.tripIds
                    )
                )
            } else {
                val error = response.body()?.error
                val errorMsg = error?.message 
                    ?: response.body()?.message
                    ?: response.errorBody()?.string()
                    ?: "Failed to confirm hold"
                    
                // Include failed assignments in error message if available
                val failedInfo = error?.failedAssignments?.joinToString("\n") { 
                    "• ${it.vehicleId}: ${it.reason}" 
                } ?: ""
                
                val fullError = if (failedInfo.isNotEmpty()) "$errorMsg\n$failedInfo" else errorMsg
                
                timber.log.Timber.e("❌ Confirm failed: $fullError")
                return@withContext BroadcastResult.Error(fullError, response.code())
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Network error confirming hold")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }
    
    /**
     * Release held trucks (cancel/timeout)
     * 
     * @param holdId The hold ID to release
     */
    suspend fun releaseHold(holdId: String): BroadcastResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }
            
            timber.log.Timber.i("🔓 Releasing hold: $holdId")
            
            val request = com.weelo.logistics.data.api.ReleaseHoldRequest(holdId = holdId)
            val response = truckHoldApi.releaseHold(request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                timber.log.Timber.i("✅ Hold released")
                return@withContext BroadcastResult.Success(true)
            } else {
                val errorMsg = response.body()?.message 
                    ?: response.errorBody()?.string()
                    ?: "Failed to release hold"
                timber.log.Timber.e("❌ Release failed: $errorMsg")
                return@withContext BroadcastResult.Error(errorMsg, response.code())
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Network error releasing hold")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }
}

/**
 * Result from holdTrucks()
 */
data class HoldTrucksResult(
    val holdId: String,
    val expiresAt: String,
    val heldQuantity: Int
)

/**
 * Result from confirmHoldWithAssignments()
 */
data class ConfirmHoldResult(
    val assignmentIds: List<String>,
    val tripIds: List<String>
)

// Note: API data classes are defined in BroadcastApiService.kt
// - BroadcastListResponse
// - BroadcastResponse
// - BroadcastResponseData
// - BroadcastLocationData
// - ApiErrorInfo
