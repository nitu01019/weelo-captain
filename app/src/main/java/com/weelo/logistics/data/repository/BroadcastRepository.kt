package com.weelo.logistics.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.weelo.logistics.data.api.AcceptBroadcastRequest
import com.weelo.logistics.data.api.AcceptBroadcastResponse
import com.weelo.logistics.data.api.BroadcastListResponse
import com.weelo.logistics.data.api.BroadcastResponseData
import com.weelo.logistics.data.api.DispatchReplayData
import com.weelo.logistics.data.api.BroadcastSnapshotData
import com.weelo.logistics.data.api.OrderWithRequests
import com.weelo.logistics.data.model.RequestedVehicle
import com.weelo.logistics.data.api.DeclineBroadcastRequest
import com.weelo.logistics.data.api.DeclineBroadcastResponse
import com.weelo.logistics.data.api.TruckRequestInfo
import com.weelo.logistics.data.model.BroadcastStatus
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.model.Location
import com.weelo.logistics.data.model.TruckCategory
import com.weelo.logistics.data.model.VehicleCatalog
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus as BroadcastTelemetryStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
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
import java.util.UUID
import java.io.IOException
import retrofit2.Response
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
    val syncCursor: String? = null,
    val isStale: Boolean = false
)

data class BroadcastSnapshot(
    val orderId: String,
    val state: String,
    val status: String,
    val dispatchState: String,
    val reasonCode: String?,
    val dispatchRevision: Long,
    val orderLifecycleVersion: Long,
    val eventVersion: Int,
    val serverTimeMs: Long,
    val expiresAtMs: Long,
    val syncCursor: String?,
    val broadcast: BroadcastTrip?
)

enum class BroadcastFetchQueryMode {
    BOOKINGS_REQUESTS_PRIMARY_WITH_BROADCASTS_FALLBACK,
    TRANSPORTER_PRIMARY_WITH_FALLBACK,
    LEGACY_DRIVER_ONLY
}

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
    private val holdAttemptIdempotencyKeys = mutableMapOf<String, String>()
    private val releaseAttemptIdempotencyKeys = mutableMapOf<String, String>()
    private val confirmAttemptIdempotencyKeys = mutableMapOf<String, String>()

    private data class ActiveBroadcastFetchAttempt(
        val response: Response<BroadcastListResponse>,
        val path: String,
        val reason: String? = null
    )

    private data class ActiveBookingsRequestsFetchAttempt(
        val broadcasts: List<BroadcastTrip>,
        val path: String,
        val code: Int,
        val success: Boolean,
        val syncCursor: String? = null,
        val snapshotUnchanged: Boolean = false,
        val reason: String? = null,
        val rawError: String? = null
    )
    
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

    private suspend fun fetchActiveBroadcastsWithQueryPolicy(
        token: String,
        effectiveUserId: String,
        vehicleType: String?,
        queryMode: BroadcastFetchQueryMode,
        syncCursor: String?
    ): ActiveBroadcastFetchAttempt {
        val role = RetrofitClient.getUserRole()?.lowercase(Locale.US)
        if (queryMode == BroadcastFetchQueryMode.LEGACY_DRIVER_ONLY || role != "transporter") {
            return ActiveBroadcastFetchAttempt(
                response = broadcastApi.getActiveBroadcasts(
                    token = token,
                    driverId = effectiveUserId,
                    vehicleType = vehicleType,
                    syncCursor = syncCursor
                ),
                path = "legacy_driver_query"
            )
        }

        val transporterResponse = broadcastApi.getActiveBroadcasts(
            token = token,
            transporterId = effectiveUserId,
            driverId = null,
            vehicleType = vehicleType,
            syncCursor = syncCursor
        )
        if (transporterResponse.isSuccessful && transporterResponse.body()?.success == true) {
            return ActiveBroadcastFetchAttempt(
                response = transporterResponse,
                path = "transporter_query"
            )
        }

        if (!shouldFallbackToLegacyDriverQuery(transporterResponse)) {
            return ActiveBroadcastFetchAttempt(
                response = transporterResponse,
                path = "transporter_query",
                reason = "transporter_query_failed_no_fallback"
            )
        }

        val legacyFallbackResponse = broadcastApi.getActiveBroadcasts(
            token = token,
            transporterId = null,
            driverId = effectiveUserId,
            vehicleType = vehicleType,
            syncCursor = syncCursor
        )
        return ActiveBroadcastFetchAttempt(
            response = legacyFallbackResponse,
            path = "legacy_fallback",
            reason = "transporter_query_unavailable"
        )
    }

    private fun shouldFallbackToLegacyDriverQuery(response: Response<BroadcastListResponse>): Boolean {
        if (response.code() in setOf(400, 404, 405, 422)) return true
        val errorCode = response.body()?.error?.code?.lowercase(Locale.US)
        val errorMessage = response.body()?.error?.message?.lowercase(Locale.US)
        return (errorCode?.contains("transporter") == true) ||
            (errorMessage?.contains("transporter") == true)
    }

    private suspend fun fetchActiveBroadcastsFromBookingsRequests(
        token: String,
        vehicleType: String?,
        syncCursor: String?
    ): ActiveBookingsRequestsFetchAttempt {
        val response = broadcastApi.getActiveTruckRequests(
            token = token,
            syncCursor = syncCursor
        )
        if (response.isSuccessful && response.body()?.success == true) {
            val data = response.body()?.data
            val responseSyncCursor = data?.syncCursor
            if (data?.snapshotUnchanged == true) {
                val cached = cachedBroadcasts
                val cachedFiltered = cached?.broadcasts?.let { filterByVehicleType(it, vehicleType) }
                if (cachedFiltered != null) {
                    return ActiveBookingsRequestsFetchAttempt(
                        broadcasts = cachedFiltered,
                        path = "bookings_requests_primary",
                        code = response.code(),
                        success = true,
                        syncCursor = responseSyncCursor,
                        snapshotUnchanged = true,
                        reason = "snapshot_unchanged_cache"
                    )
                }

                if (!syncCursor.isNullOrBlank()) {
                    val fullSnapshotResponse = broadcastApi.getActiveTruckRequests(
                        token = token,
                        syncCursor = null
                    )
                    if (fullSnapshotResponse.isSuccessful && fullSnapshotResponse.body()?.success == true) {
                        val fullMapped = mapActiveRequestsToBroadcastTrips(fullSnapshotResponse.body()?.data?.orders.orEmpty())
                        return ActiveBookingsRequestsFetchAttempt(
                            broadcasts = filterByVehicleType(fullMapped, vehicleType),
                            path = "bookings_requests_primary_refetch_full",
                            code = fullSnapshotResponse.code(),
                            success = true,
                            syncCursor = fullSnapshotResponse.body()?.data?.syncCursor,
                            snapshotUnchanged = false,
                            reason = "snapshot_unchanged_refetched_full"
                        )
                    }
                }
            }

            val mapped = mapActiveRequestsToBroadcastTrips(data?.orders.orEmpty())
            val filtered = filterByVehicleType(mapped, vehicleType)
            return ActiveBookingsRequestsFetchAttempt(
                broadcasts = filtered,
                path = "bookings_requests_primary",
                code = response.code(),
                success = true,
                syncCursor = responseSyncCursor
            )
        }

        val rawError = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }

        return ActiveBookingsRequestsFetchAttempt(
            broadcasts = emptyList(),
            path = "bookings_requests_primary",
            code = response.code(),
            success = false,
            syncCursor = null,
            reason = "bookings_requests_unavailable",
            rawError = rawError
        )
    }

    private fun responseSyncCursorFromBookingsAttempt(attempt: ActiveBookingsRequestsFetchAttempt): String? {
        return attempt.syncCursor
    }

    private fun shouldFallbackToBroadcastsActive(attempt: ActiveBookingsRequestsFetchAttempt): Boolean {
        // Fallback only for explicit compatibility errors, not generic backend failures.
        if (attempt.code in setOf(404, 405, 501)) return true
        if (attempt.code !in setOf(400, 422)) return false
        val error = attempt.rawError?.lowercase(Locale.US) ?: return false
        return error.contains("bookings/requests/active") ||
            error.contains("cannot get") ||
            error.contains("route not found") ||
            error.contains("unsupported") ||
            error.contains("not implemented")
    }

    private fun filterByVehicleType(
        broadcasts: List<BroadcastTrip>,
        vehicleType: String?
    ): List<BroadcastTrip> {
        if (vehicleType.isNullOrBlank()) return broadcasts
        val normalizedVehicleType = vehicleType.trim().lowercase(Locale.US)
        return broadcasts.filter { trip ->
            @Suppress("DEPRECATION")
            val category = trip.vehicleType ?: return@filter false
            category.id.lowercase(Locale.US) == normalizedVehicleType ||
                category.name.lowercase(Locale.US).contains(normalizedVehicleType)
        }
    }
    
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
        userId: String? = null,
        syncCursor: String? = null,
        queryMode: BroadcastFetchQueryMode = BroadcastFetchQueryMode.BOOKINGS_REQUESTS_PRIMARY_WITH_BROADCASTS_FALLBACK
    ): BroadcastResult<CachedBroadcastList> = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            // Check cache validity (30 seconds for real-time data)
            val cached = cachedBroadcasts
            val now = System.currentTimeMillis()
            val cacheCursorMatches = syncCursor.isNullOrBlank() || cached?.syncCursor == syncCursor
            
            if (!forceRefresh && cached != null && (now - cached.lastUpdated) < cacheValidityMs && cacheCursorMatches) {
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
                
                val effectiveUserId = userId ?: RetrofitClient.getUserId()
                if (effectiveUserId.isNullOrBlank()) {
                    val error = BroadcastResult.Error("Missing authenticated user id", 401)
                    _broadcastsState.value = error
                    _isRefreshing.value = false
                    return@withContext error
                }

                timber.log.Timber.d("📡 Fetching broadcasts for user: $effectiveUserId")
                val normalizedToken = "Bearer $token"
                var resolvedPath: String
                var resolvedReason: String?
                var resolvedHttpCode: Int
                var resolvedError: String? = null
                var mappedBroadcasts: List<BroadcastTrip>? = null
                var aliasFallbackUsed = false

                if (
                    queryMode == BroadcastFetchQueryMode.BOOKINGS_REQUESTS_PRIMARY_WITH_BROADCASTS_FALLBACK &&
                    RetrofitClient.getUserRole()?.equals("transporter", ignoreCase = true) == true
                ) {
                    val bookingsAttempt = fetchActiveBroadcastsFromBookingsRequests(
                        token = normalizedToken,
                        vehicleType = vehicleType,
                        syncCursor = syncCursor
                    )
                    resolvedPath = bookingsAttempt.path
                    resolvedReason = bookingsAttempt.reason
                    resolvedHttpCode = bookingsAttempt.code

                    if (bookingsAttempt.success) {
                        mappedBroadcasts = bookingsAttempt.broadcasts
                    } else if (shouldFallbackToBroadcastsActive(bookingsAttempt)) {
                        aliasFallbackUsed = true
                        val fallbackAttempt = fetchActiveBroadcastsWithQueryPolicy(
                            token = normalizedToken,
                            effectiveUserId = effectiveUserId,
                            vehicleType = vehicleType,
                            queryMode = BroadcastFetchQueryMode.TRANSPORTER_PRIMARY_WITH_FALLBACK,
                            syncCursor = syncCursor
                        )
                        val fallbackResponse = fallbackAttempt.response
                        resolvedPath = "${bookingsAttempt.path}->${fallbackAttempt.path}"
                        resolvedReason = fallbackAttempt.reason ?: "bookings_primary_fallback_to_broadcasts"
                        resolvedHttpCode = fallbackResponse.code()
                        if (fallbackResponse.isSuccessful && fallbackResponse.body()?.success == true) {
                            mappedBroadcasts = fallbackResponse.body()
                                ?.broadcasts
                                .orEmpty()
                                .filterNot { acceptedBroadcastIds.contains(it.broadcastId) }
                                .map { mapToBroadcastTrip(it) }
                        } else {
                            resolvedError = fallbackResponse.body()?.error?.message
                                ?: fallbackResponse.errorBody()?.string()
                                ?: "Failed to fetch broadcasts"
                        }
                    } else {
                        resolvedError = bookingsAttempt.rawError ?: "Failed to fetch active requests"
                    }
                } else {
                    val fetchAttempt = fetchActiveBroadcastsWithQueryPolicy(
                        token = normalizedToken,
                        effectiveUserId = effectiveUserId,
                        vehicleType = vehicleType,
                        queryMode = queryMode,
                        syncCursor = syncCursor
                    )
                    val response = fetchAttempt.response
                    resolvedPath = fetchAttempt.path
                    resolvedReason = fetchAttempt.reason
                    resolvedHttpCode = response.code()
                    if (response.isSuccessful && response.body()?.success == true) {
                        mappedBroadcasts = response.body()
                            ?.broadcasts
                            .orEmpty()
                            .filterNot { acceptedBroadcastIds.contains(it.broadcastId) }
                            .map { mapToBroadcastTrip(it) }
                    } else {
                        resolvedError = response.body()?.error?.message
                            ?: response.errorBody()?.string()
                            ?: "Failed to fetch broadcasts"
                    }
                }

                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_ACTIVE_FETCH,
                    status = if (mappedBroadcasts != null) {
                        BroadcastTelemetryStatus.SUCCESS
                    } else {
                        BroadcastTelemetryStatus.FAILED
                    },
                    reason = resolvedReason,
                    attrs = mapOf(
                        "path" to resolvedPath,
                        "primary_path" to "bookings/requests/active",
                        "httpCode" to resolvedHttpCode.toString(),
                        "alias_fallback_used" to aliasFallbackUsed.toString(),
                        "alias_used" to aliasFallbackUsed.toString()
                    )
                )
                timber.log.Timber.d(
                    "📡 Active broadcast fetch path=%s reason=%s code=%d aliasFallback=%s",
                    resolvedPath,
                    resolvedReason ?: "none",
                    resolvedHttpCode,
                    aliasFallbackUsed
                )

                if (mappedBroadcasts != null) {
                    val sortedBroadcasts = mappedBroadcasts
                        .sortedWith(
                            compareByDescending<BroadcastTrip> { it.isUrgent }
                                .thenByDescending { it.broadcastTime }
                        )
                    
                    val cachedList = CachedBroadcastList(
                        broadcasts = sortedBroadcasts,
                        totalCount = sortedBroadcasts.size,
                        lastUpdated = now,
                        syncCursor = syncCursor,
                        isStale = false
                    )
                    
                    // Update cache
                    cachedBroadcasts = cachedList
                    
                    timber.log.Timber.i("✅ Fetched ${sortedBroadcasts.size} active broadcasts")
                    
                    val result = BroadcastResult.Success(cachedList)
                    _broadcastsState.value = result
                    _isRefreshing.value = false
                    return@withContext result
                    
                } else {
                    val errorMsg = resolvedError ?: "Failed to fetch broadcasts"
                    val errorCode = resolvedHttpCode
                    
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
                return@withContext BroadcastResult.Error("Session expired. Please login again.", 401, "AUTH_EXPIRED")
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

    suspend fun getBroadcastSnapshot(orderId: String): BroadcastResult<BroadcastSnapshot> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }

            val response = broadcastApi.getBroadcastSnapshot(
                token = "Bearer $token",
                orderId = orderId
            )
            if (!response.isSuccessful || response.body()?.success != true || response.body()?.data == null) {
                val errorMsg = response.body()?.error?.message ?: "Failed to fetch broadcast snapshot"
                return@withContext BroadcastResult.Error(errorMsg, response.code())
            }

            val data = response.body()!!.data!!
            val snapshot = BroadcastSnapshot(
                orderId = data.orderId,
                state = data.state,
                status = data.status,
                dispatchState = data.dispatchState,
                reasonCode = data.reasonCode,
                dispatchRevision = data.dispatchRevision,
                orderLifecycleVersion = data.orderLifecycleVersion,
                eventVersion = data.eventVersion,
                serverTimeMs = data.serverTimeMs,
                expiresAtMs = data.expiresAtMs,
                syncCursor = data.syncCursor,
                broadcast = mapBroadcastSnapshotToTrip(data)
            )
            return@withContext BroadcastResult.Success(snapshot)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Error fetching broadcast snapshot $orderId")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun getDispatchReplay(
        cursor: Long?,
        limit: Int = 50
    ): BroadcastResult<DispatchReplayData> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }

            val response = broadcastApi.getDispatchReplay(
                token = "Bearer $token",
                cursor = cursor,
                limit = limit.coerceIn(1, 100)
            )
            if (!response.isSuccessful || response.body()?.success != true || response.body()?.data == null) {
                val errorMsg = response.body()?.error?.message ?: "Failed to fetch dispatch replay"
                return@withContext BroadcastResult.Error(errorMsg, response.code())
            }

            return@withContext BroadcastResult.Success(response.body()!!.data!!)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Error fetching dispatch replay cursor=$cursor")
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

    private fun buildHoldAttemptKey(
        orderId: String,
        vehicleType: String,
        vehicleSubtype: String,
        quantity: Int
    ): String {
        return "${orderId.trim().lowercase(Locale.US)}|${vehicleType.trim().lowercase(Locale.US)}|${vehicleSubtype.trim().lowercase(Locale.US)}|$quantity"
    }

    private suspend fun reconcileActiveHold(
        orderId: String,
        vehicleType: String,
        vehicleSubtype: String
    ): HoldTrucksResult? {
        return try {
            val response = truckHoldApi.getMyActiveHold(
                orderId = orderId,
                vehicleType = vehicleType,
                vehicleSubtype = vehicleSubtype
            )
            if (!response.isSuccessful) return null
            val data = response.body()?.data ?: return null
            HoldTrucksResult(
                holdId = data.holdId,
                expiresAt = data.expiresAt,
                heldQuantity = data.quantity
            )
        } catch (e: Exception) {
            timber.log.Timber.w(e, "⚠️ Active hold reconciliation failed")
            null
        }
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
        holdAttemptIdempotencyKeys.clear()
        releaseAttemptIdempotencyKeys.clear()
        confirmAttemptIdempotencyKeys.clear()
        _broadcastsState.value = BroadcastResult.Loading
        timber.log.Timber.d("🗑️ Cache cleared")
    }
    
    // =========================================================================
    // MAPPING FUNCTIONS
    // =========================================================================

    private fun mapActiveRequestsToBroadcastTrips(orders: List<OrderWithRequests>): List<BroadcastTrip> {
        return orders.map { group -> mapOrderGroupToBroadcastTrip(group) }
    }

    private fun mapBroadcastSnapshotToTrip(snapshot: BroadcastSnapshotData): BroadcastTrip {
        val mapped = mapOrderGroupToBroadcastTrip(
            OrderWithRequests(
                order = snapshot.order,
                requests = snapshot.requests
            )
        )
        return mapped.copy(
            broadcastTime = snapshot.serverTimeMs.takeIf { it > 0 } ?: mapped.broadcastTime,
            expiryTime = snapshot.expiresAtMs.takeIf { it > 0 } ?: mapped.expiryTime,
            eventVersion = snapshot.eventVersion,
            serverTimeMs = snapshot.serverTimeMs,
            reasonCode = snapshot.reasonCode
            ,
            dispatchRevision = snapshot.dispatchRevision,
            orderLifecycleVersion = snapshot.orderLifecycleVersion
        )
    }

    private fun mapOrderGroupToBroadcastTrip(group: OrderWithRequests): BroadcastTrip {
        val order = group.order
        val requestedVehicles = mapRequestedVehiclesFromTruckRequests(group.requests)
        val primaryRequest = group.requests.firstOrNull()

        val vehicleType = parseVehicleType(primaryRequest?.vehicleType)
        val perTruckFare = primaryRequest?.pricePerTruck?.toDouble()
            ?: requestedVehicles.firstOrNull()?.farePerTruck
            ?: 0.0

        return BroadcastTrip(
            broadcastId = order.id,
            customerId = order.customerId,
            customerName = order.customerName,
            customerMobile = order.customerPhone,
            pickupLocation = Location(
                latitude = order.pickup.latitude,
                longitude = order.pickup.longitude,
                address = order.pickup.address,
                city = order.pickup.city,
                state = order.pickup.state,
                pincode = null
            ),
            dropLocation = Location(
                latitude = order.drop.latitude,
                longitude = order.drop.longitude,
                address = order.drop.address,
                city = order.drop.city,
                state = order.drop.state,
                pincode = null
            ),
            distance = order.distanceKm.toDouble(),
            estimatedDuration = 0L,
            totalTrucksNeeded = order.totalTrucks,
            trucksFilledSoFar = order.trucksFilled,
            vehicleType = vehicleType,
            goodsType = order.goodsType ?: "General",
            weight = order.weight,
            farePerTruck = perTruckFare,
            totalFare = order.totalAmount.toDouble(),
            status = parseStatus(order.status),
            broadcastTime = parseTimestamp(order.createdAt),
            expiryTime = parseTimestamp(order.expiresAt),
            notes = null,
            isUrgent = false,
            requestedVehicles = requestedVehicles,
            dispatchRevision = order.dispatchRevision,
            orderLifecycleVersion = order.lifecycleEventVersion
        )
    }

    private fun mapRequestedVehiclesFromTruckRequests(requests: List<TruckRequestInfo>): List<RequestedVehicle> {
        if (requests.isEmpty()) return emptyList()

        val filledStatuses = setOf("assigned", "accepted", "in_progress", "completed")
        return requests
            .groupBy { "${it.vehicleType.lowercase(Locale.US)}|${it.vehicleSubtype.lowercase(Locale.US)}" }
            .map { (_, grouped) ->
                val first = grouped.first()
                RequestedVehicle(
                    vehicleType = first.vehicleType,
                    vehicleSubtype = first.vehicleSubtype,
                    count = grouped.size,
                    filledCount = grouped.count { it.status.lowercase(Locale.US) in filledStatuses },
                    farePerTruck = first.pricePerTruck.toDouble(),
                    capacityTons = 0.0
                )
            }
    }
    
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
    // 1. holdTrucks() → Trucks held for server-configured hold window
    // 2. confirmHoldWithAssignments() → Assign vehicles + drivers atomically
    // 3. releaseHold() → Release if user cancels or timeout
    //
    // This prevents double-booking when multiple transporters try to accept
    // the same trucks simultaneously.
    // =========================================================================
    
    private val truckHoldApi by lazy { RetrofitClient.truckHoldApi }
    
    /**
     * Hold trucks for selection (server-configured hold window)
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
        val attemptKey = buildHoldAttemptKey(orderId, vehicleType, vehicleSubtype, quantity)
        val idempotencyKey = holdAttemptIdempotencyKeys.getOrPut(attemptKey) { UUID.randomUUID().toString() }
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Session expired. Please login again.", 401, "AUTH_EXPIRED")
            }
            
            timber.log.Timber.i("🔒 Holding $quantity trucks: $vehicleType $vehicleSubtype for order $orderId")
            
            val request = com.weelo.logistics.data.api.HoldTrucksRequest(
                orderId = orderId,
                vehicleType = vehicleType,
                vehicleSubtype = vehicleSubtype,
                quantity = quantity
            )
            
            val response = truckHoldApi.holdTrucks(request, idempotencyKey)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                timber.log.Timber.i("✅ Trucks held! HoldID: ${data.holdId}, Expires: ${data.expiresAt}")
                // Hold attempt completed; next user-initiated hold should use a fresh key.
                holdAttemptIdempotencyKeys.remove(attemptKey)
                
                return@withContext BroadcastResult.Success(
                    HoldTrucksResult(
                        holdId = data.holdId,
                        expiresAt = data.expiresAt,
                        heldQuantity = data.heldQuantity
                    )
                )
            } else {
                if (response.code() == 401) {
                    return@withContext BroadcastResult.Error("Session expired. Please login again.", 401, "AUTH_EXPIRED")
                }

                val errorMsg = response.body()?.error?.message 
                    ?: response.body()?.message
                    ?: response.errorBody()?.string()
                    ?: "Failed to hold trucks"
                val errorCode = response.body()?.error?.code

                if (
                    errorCode == "ALREADY_HOLDING" ||
                    errorCode == "HOLD_ALREADY_ACTIVE" ||
                    response.code() == 408 ||
                    response.code() == 504 ||
                    response.code() >= 500
                ) {
                    val recovered = reconcileActiveHold(orderId, vehicleType, vehicleSubtype)
                    if (recovered != null) {
                        timber.log.Timber.i("✅ Recovered active hold after uncertain hold outcome: ${recovered.holdId}")
                        holdAttemptIdempotencyKeys.remove(attemptKey)
                        return@withContext BroadcastResult.Success(recovered)
                    }
                }

                if (errorCode == "IDEMPOTENCY_CONFLICT") {
                    holdAttemptIdempotencyKeys.remove(attemptKey)
                } else if (
                    response.code() in 400..499 &&
                    response.code() != 408 &&
                    errorCode != "ALREADY_HOLDING" &&
                    errorCode != "HOLD_ALREADY_ACTIVE"
                ) {
                    // Deterministic client/business errors should not pin future attempts to old keys.
                    holdAttemptIdempotencyKeys.remove(attemptKey)
                }

                timber.log.Timber.e("❌ Hold failed: $errorMsg (code: $errorCode)")
                return@withContext BroadcastResult.Error(errorMsg, response.code(), errorCode)
            }
            
        } catch (e: Exception) {
            if (e is IOException) {
                val recovered = reconcileActiveHold(orderId, vehicleType, vehicleSubtype)
                if (recovered != null) {
                    timber.log.Timber.i("✅ Recovered active hold after network failure: ${recovered.holdId}")
                    holdAttemptIdempotencyKeys.remove(attemptKey)
                    return@withContext BroadcastResult.Success(recovered)
                }
            }
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
        val normalizedHoldId = holdId.trim()
        val idempotencyKey = confirmAttemptIdempotencyKeys.getOrPut(normalizedHoldId) { UUID.randomUUID().toString() }
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }
            
            timber.log.Timber.i("📤 Confirming hold $normalizedHoldId with ${assignments.size} assignments (idempotency: $idempotencyKey)")
            
            val request = com.weelo.logistics.data.api.ConfirmHoldWithAssignmentsRequest(
                holdId = normalizedHoldId,
                assignments = assignments.map { (vehicleId, driverId) ->
                    com.weelo.logistics.data.api.VehicleDriverAssignment(
                        vehicleId = vehicleId,
                        driverId = driverId
                    )
                }
            )
            
            val response = truckHoldApi.confirmHoldWithAssignments(request, idempotencyKey)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                timber.log.Timber.i("✅ Hold confirmed! Assignments: ${data.assignmentIds.size}, Trips: ${data.tripIds.size}")
                
                // Confirm completed; clear key so next confirm uses fresh one
                confirmAttemptIdempotencyKeys.remove(normalizedHoldId)
                
                // Invalidate cache
                invalidateCache()
                
                return@withContext BroadcastResult.Success(
                    ConfirmHoldResult(
                        assignmentIds = data.assignmentIds,
                        tripIds = data.tripIds
                    )
                )
            } else {
                if (response.code() == 401) {
                    return@withContext BroadcastResult.Error("Session expired. Please login again.", 401, "AUTH_EXPIRED")
                }
                val error = response.body()?.error
                val errorMsg = error?.message 
                    ?: response.body()?.message
                    ?: response.errorBody()?.string()
                    ?: "Failed to confirm hold"

                val apiCode = error?.code

                // Clear key on deterministic client errors (except timeout/conflict)
                if (response.code() in 400..499 && response.code() != 408 && response.code() != 409) {
                    confirmAttemptIdempotencyKeys.remove(normalizedHoldId)
                }
                    
                // Include failed assignments in error message if available
                val failedInfo = error?.failedAssignments?.joinToString("\n") { 
                    "• ${it.vehicleId}: ${it.reason}" 
                } ?: ""
                
                val fullError = if (failedInfo.isNotEmpty()) "$errorMsg\n$failedInfo" else errorMsg
                
                timber.log.Timber.e("❌ Confirm failed: $fullError")
                return@withContext BroadcastResult.Error(
                    message = fullError,
                    code = response.code(),
                    apiCode = apiCode
                )
            }
            
        } catch (e: Exception) {
            // Keep idempotency key for transient/network errors — retry should use same key
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
        val normalizedHoldId = holdId.trim()
        val idempotencyKey = releaseAttemptIdempotencyKeys.getOrPut(normalizedHoldId) { UUID.randomUUID().toString() }
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Session expired. Please login again.", 401, "AUTH_EXPIRED")
            }
            
            timber.log.Timber.i("🔓 Releasing hold: $normalizedHoldId")
            
            val request = com.weelo.logistics.data.api.ReleaseHoldRequest(holdId = normalizedHoldId)
            val response = truckHoldApi.releaseHold(request, idempotencyKey)
            
            if (response.isSuccessful && response.body()?.success == true) {
                timber.log.Timber.i("✅ Hold released")
                releaseAttemptIdempotencyKeys.remove(normalizedHoldId)
                return@withContext BroadcastResult.Success(true)
            } else {
                if (response.code() == 401) {
                    return@withContext BroadcastResult.Error("Session expired. Please login again.", 401, "AUTH_EXPIRED")
                }

                val apiCode = response.body()?.error?.code
                if (apiCode == "HOLD_NOT_FOUND") {
                    timber.log.Timber.i("ℹ️ Hold not found on release; treating as already released")
                    releaseAttemptIdempotencyKeys.remove(normalizedHoldId)
                    return@withContext BroadcastResult.Success(true)
                }

                val errorMsg = response.body()?.error?.message
                    ?: response.body()?.message 
                    ?: response.errorBody()?.string()
                    ?: "Failed to release hold"
                timber.log.Timber.e("❌ Release failed: $errorMsg")
                return@withContext BroadcastResult.Error(errorMsg, response.code(), apiCode)
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
