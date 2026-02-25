package com.weelo.logistics.data.api

import com.weelo.logistics.data.model.BroadcastTrip
import retrofit2.Response
import retrofit2.http.*

/**
 * Broadcast API Service - For trip broadcast management
 * 
 * BACKEND INTEGRATION NOTES:
 * ==========================
 * Base URL: https://api.weelo.in/v1/
 * Headers: Authorization: Bearer {accessToken}
 * 
 * BROADCAST FLOW:
 * ===============
 * 
 * CUSTOMER -> TRANSPORTER -> DRIVER FLOW:
 * 1. Customer request is broadcast to eligible transporters.
 * 2. Transporter reviews active requests and commits trucks/drivers.
 * 3. Driver receives trip assignment only after transporter confirms assignment.
 * 4. Driver accepts/declines assignment updates, not raw customer broadcast.
 * 
 * REAL-TIME UPDATES:
 * ==================
 * Use WebSocket connection for real-time broadcast updates
 * WebSocket URL: wss://api.weelo.in/ws
 * 
 * Events to listen:
 * - "new_broadcast": New trip broadcast available
 * - "broadcast_updated": Broadcast details changed
 * - "broadcast_cancelled": Broadcast cancelled by transporter
 * - "broadcast_assigned": Driver assigned to the trip
 */
interface BroadcastApiService {
    
    /**
     * Get active broadcasts for current transporter control surface.
     * 
     * ENDPOINT: GET /broadcasts/active
     * Headers: Authorization: Bearer {accessToken}
     * Query Params:
     * - transporterId: string (preferred for transporter feed)
     * - driverId: string (legacy compatibility fallback)
     * - vehicleType: string (optional) - filter by vehicle type
     * - maxDistance: number (optional) - filter by max distance
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "broadcasts": [
     *     {
     *       "broadcastId": "bc_123",
     *       "customerId": "customer_456",
     *       "customerName": "ABC Industries",
     *       "customerMobile": "9876543210",
     *       "pickupLocation": {
     *         "latitude": 28.7041,
     *         "longitude": 77.1025,
     *         "address": "Connaught Place, New Delhi",
     *         "city": "New Delhi",
     *         "state": "Delhi",
     *         "pincode": "110001"
     *       },
     *       "dropLocation": {...},
     *       "distance": 1420.0,
     *       "estimatedDuration": 1200,
     *       "totalTrucksNeeded": 10,
     *       "trucksFilledSoFar": 3,
     *       "vehicleType": "CONTAINER",
     *       "goodsType": "Industrial Equipment",
     *       "weight": "25 tons",
     *       "farePerTruck": 85000.0,
     *       "totalFare": 850000.0,
     *       "status": "ACTIVE",
     *       "isUrgent": true,
     *       "createdAt": "2026-01-05T10:00:00Z",
     *       "expiresAt": "2026-01-05T12:00:00Z"
     *     }
     *   ],
     *   "count": 10
     * }
     */
    @GET("broadcasts/active")
    suspend fun getActiveBroadcasts(
        @Header("Authorization") token: String,
        @Query("transporterId") transporterId: String? = null,
        @Query("driverId") driverId: String? = null,
        @Query("vehicleType") vehicleType: String? = null,
        @Query("maxDistance") maxDistance: Double? = null
    ): Response<BroadcastListResponse>
    
    /**
     * Get broadcast details by ID
     * 
     * ENDPOINT: GET /broadcasts/{broadcastId}
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "broadcast": {BroadcastTrip object}
     * }
     */
    @GET("broadcasts/{broadcastId}")
    suspend fun getBroadcastById(
        @Header("Authorization") token: String,
        @Path("broadcastId") broadcastId: String
    ): Response<BroadcastResponse>
    
    /**
     * Accept/commit broadcast assignment (legacy compatibility path).
     * 
     * ENDPOINT: POST /broadcasts/{broadcastId}/accept
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "driverId": "driver_123",
     *   "vehicleId": "vehicle_456",
     *   "estimatedArrival": "2026-01-05T11:00:00Z",
     *   "notes": "Ready to pickup" (optional)
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "message": "Broadcast accepted successfully",
     *   "assignmentId": "assign_789",
     *   "tripId": "trip_101",
     *   "status": "ASSIGNED"
     * }
     * 
     * Response (400 Bad Request):
     * {
     *   "success": false,
     *   "error": "Broadcast already filled" // or "Driver not eligible"
     * }
     */
    @POST("broadcasts/{broadcastId}/accept")
    suspend fun acceptBroadcast(
        @Header("Authorization") token: String,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null,
        @Path("broadcastId") broadcastId: String,
        @Body request: AcceptBroadcastRequest
    ): Response<AcceptBroadcastResponse>
    
    /**
     * Decline a broadcast request from transporter control surface.
     * 
     * ENDPOINT: POST /broadcasts/{broadcastId}/decline
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "driverId": "driver_123",
     *   "reason": "NOT_AVAILABLE" // or "VEHICLE_NOT_SUITABLE", "DISTANCE_TOO_FAR", "OTHER"
     *   "notes": "Optional additional notes"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "message": "Broadcast declined"
     * }
     */
    @POST("broadcasts/{broadcastId}/decline")
    suspend fun declineBroadcast(
        @Header("Authorization") token: String,
        @Path("broadcastId") broadcastId: String,
        @Body request: DeclineBroadcastRequest
    ): Response<DeclineBroadcastResponse>
    
    /**
     * Get broadcast history for driver
     * 
     * ENDPOINT: GET /broadcasts/history
     * Headers: Authorization: Bearer {accessToken}
     * Query Params:
     * - driverId: string (required)
     * - page: number (default: 1)
     * - limit: number (default: 20)
     * - status: string (optional) - "ACCEPTED", "DECLINED", "EXPIRED"
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "broadcasts": [BroadcastTrip array],
     *   "pagination": {
     *     "page": 1,
     *     "limit": 20,
     *     "total": 100,
     *     "pages": 5
     *   }
     * }
     */
    @GET("broadcasts/history")
    suspend fun getBroadcastHistory(
        @Header("Authorization") token: String,
        @Query("driverId") driverId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("status") status: String? = null
    ): Response<BroadcastHistoryResponse>
    
    /**
     * Create broadcast (Transporter only)
     * 
     * ENDPOINT: POST /broadcasts/create
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "transporterId": "transporter_123",
     *   "customerId": "customer_456",
     *   "pickupLocation": {Location object},
     *   "dropLocation": {Location object},
     *   "vehicleType": "CONTAINER",
     *   "totalTrucksNeeded": 10,
     *   "goodsType": "Industrial Equipment",
     *   "weight": "25 tons",
     *   "farePerTruck": 85000.0,
     *   "isUrgent": true,
     *   "expiresAt": "2026-01-05T12:00:00Z",
     *   "preferredDriverIds": ["driver_1", "driver_2"] (optional)
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "broadcast": {BroadcastTrip object},
     *   "notifiedDrivers": 50 // Number of drivers notified
     * }
     */
    @POST("broadcasts/create")
    suspend fun createBroadcast(
        @Header("Authorization") token: String,
        @Body request: CreateBroadcastRequest
    ): Response<CreateBroadcastResponse>
    
    // =================================================================
    // NEW: TRUCK REQUEST APIs (Multi-Truck Order System)
    // =================================================================
    
    /**
     * Get active truck requests for transporter
     * 
     * Returns only requests matching the transporter's registered vehicle types.
     * Grouped by order for easier display.
     * 
     * ENDPOINT: GET /bookings/requests/active
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "data": {
     *     "orders": [
     *       {
     *         "order": { order details },
     *         "requests": [ truck requests for this order ]
     *       }
     *     ],
     *     "count": 5
     *   }
     * }
     */
    @GET("bookings/requests/active")
    suspend fun getActiveTruckRequests(
        @Header("Authorization") token: String
    ): Response<ActiveTruckRequestsResponse>
    
    /**
     * Accept a specific truck request
     * 
     * LIGHTNING FAST: This instantly assigns the truck to you.
     * Other transporters are immediately notified it's no longer available.
     * 
     * ENDPOINT: POST /bookings/requests/{requestId}/accept
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "vehicleId": "veh_123",     // Your vehicle to assign
     *   "driverId": "drv_456"       // Optional: specific driver
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "data": {
     *     "request": { updated truck request with assignment },
     *   }
     * }
     * 
     * Response (400 - Already Taken):
     * {
     *   "success": false,
     *   "error": {
     *     "code": "REQUEST_ALREADY_TAKEN",
     *     "message": "This truck was just taken by another transporter"
     *   }
     * }
     */
    @POST("bookings/requests/{requestId}/accept")
    suspend fun acceptTruckRequest(
        @Header("Authorization") token: String,
        @Path("requestId") requestId: String,
        @Body request: AcceptTruckRequestBody
    ): Response<AcceptTruckRequestResponse>
    
    /**
     * Get order details with all truck requests
     * 
     * ENDPOINT: GET /bookings/orders/{orderId}
     * Headers: Authorization: Bearer {accessToken}
     */
    @GET("bookings/orders/{orderId}")
    suspend fun getOrderDetails(
        @Header("Authorization") token: String,
        @Path("orderId") orderId: String
    ): Response<OrderDetailsResponse>
}

// ============== Request/Response Data Classes ==============

/**
 * Response for GET /broadcasts/active
 * Matches backend response format from weelo-backend
 * 
 * BACKEND FIELD MAPPING:
 * - broadcasts[] contains booking records with broadcast fields
 * - Uses flexible BroadcastResponseData to handle both naming conventions
 */
data class BroadcastListResponse(
    val success: Boolean,
    val broadcasts: List<BroadcastResponseData>? = null,
    val count: Int? = null,
    val error: ApiErrorInfo? = null
)

data class BroadcastResponse(
    val success: Boolean,
    val broadcast: BroadcastResponseData? = null,
    val error: ApiErrorInfo? = null
)

/**
 * Flexible broadcast data class that handles both naming conventions
 * from backend (booking fields) and expected API format (broadcast fields)
 * 
 * SCALABILITY: Using nullable fields with defaults for forward compatibility
 */
data class BroadcastResponseData(
    // ID fields - backend uses 'id', API uses 'broadcastId'
    val broadcastId: String? = null,
    val id: String? = null,
    
    // Customer info
    val customerId: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerMobile: String? = null,
    
    // Location - backend uses 'pickup/drop', API uses 'pickupLocation/dropLocation'
    val pickupLocation: BroadcastLocationData? = null,
    val pickup: BroadcastLocationData? = null,
    val dropLocation: BroadcastLocationData? = null,
    val drop: BroadcastLocationData? = null,
    
    // Distance - backend uses 'distanceKm', API uses 'distance'
    val distance: Double? = null,
    val distanceKm: Double? = null,
    val estimatedDuration: Long? = null,
    
    // Truck requirements - backend uses 'trucksNeeded/trucksFilled'
    val totalTrucksNeeded: Int? = null,
    val trucksNeeded: Int? = null,
    val trucksFilledSoFar: Int? = null,
    val trucksFilled: Int? = null,
    
    // Vehicle info
    val vehicleType: String? = null,
    val vehicleSubtype: String? = null,
    
    // Cargo info
    val goodsType: String? = null,
    val weight: String? = null,
    
    // Pricing - backend uses 'pricePerTruck/totalAmount'
    val farePerTruck: Double? = null,
    val pricePerTruck: Double? = null,
    val totalFare: Double? = null,
    val totalAmount: Double? = null,
    
    // Status and timing
    val status: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val broadcastTime: Long? = null,
    val expiryTime: Long? = null,
    
    // Additional info
    val notes: String? = null,
    val isUrgent: Boolean? = null,
    
    // Multi-truck support - array of requested vehicles
    val requestedVehicles: List<RequestedVehicleData>? = null
) {
    /**
     * Get effective broadcast ID (handles both naming conventions)
     */
    fun getEffectiveId(): String = broadcastId ?: id ?: ""
    
    /**
     * Get effective pickup location
     */
    fun getEffectivePickup(): BroadcastLocationData? = pickupLocation ?: pickup
    
    /**
     * Get effective drop location
     */
    fun getEffectiveDrop(): BroadcastLocationData? = dropLocation ?: drop
    
    /**
     * Get effective distance in km
     */
    fun getEffectiveDistance(): Double = distance ?: distanceKm ?: 0.0
    
    /**
     * Get effective trucks needed
     */
    fun getEffectiveTrucksNeeded(): Int = totalTrucksNeeded ?: trucksNeeded ?: 1
    
    /**
     * Get effective trucks filled
     */
    fun getEffectiveTrucksFilled(): Int = trucksFilledSoFar ?: trucksFilled ?: 0
    
    /**
     * Get effective fare per truck
     */
    fun getEffectiveFarePerTruck(): Double = farePerTruck ?: pricePerTruck ?: 0.0
    
    /**
     * Get effective total fare
     */
    fun getEffectiveTotalFare(): Double = totalFare ?: totalAmount ?: 0.0
    
    /**
     * Get effective customer mobile
     */
    fun getEffectiveCustomerMobile(): String = customerMobile ?: customerPhone ?: ""
}

/**
 * Location data that handles both naming conventions
 */
/**
 * Requested Vehicle Data - For multi-truck broadcasts
 */
data class RequestedVehicleData(
    val vehicleType: String? = null,
    val vehicleSubtype: String? = null,
    val count: Int? = null,
    val filledCount: Int? = null,
    val farePerTruck: Double? = null,
    val capacityTons: Double? = null
)

data class BroadcastLocationData(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null
)

data class AcceptBroadcastRequest(
    val driverId: String,
    val vehicleId: String,
    val estimatedArrival: String? = null,
    val notes: String? = null
)

data class AcceptBroadcastResponse(
    val success: Boolean,
    val message: String,
    val assignmentId: String,
    val tripId: String,
    val status: String,
    val resultCode: String? = null,
    val replayed: Boolean? = null,
    val error: String? = null
)

data class DeclineBroadcastRequest(
    val driverId: String,
    val reason: String, // "NOT_AVAILABLE", "VEHICLE_NOT_SUITABLE", "DISTANCE_TOO_FAR", "OTHER"
    val notes: String? = null
)

data class DeclineBroadcastResponse(
    val success: Boolean,
    val message: String
)

data class BroadcastHistoryResponse(
    val success: Boolean,
    val broadcasts: List<BroadcastTrip>,
    val pagination: PaginationData
)

data class PaginationData(
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int
)

data class CreateBroadcastRequest(
    val transporterId: String,
    val customerId: String,
    val pickupLocation: LocationRequest,
    val dropLocation: LocationRequest,
    val vehicleType: String,
    val totalTrucksNeeded: Int,
    val goodsType: String,
    val weight: String,
    val farePerTruck: Double,
    val isUrgent: Boolean = false,
    val expiresAt: String? = null,
    val preferredDriverIds: List<String>? = null
)

data class LocationRequest(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String,
    val state: String,
    val pincode: String
)

data class CreateBroadcastResponse(
    val success: Boolean,
    val broadcast: BroadcastTrip,
    val notifiedDrivers: Int
)

// =================================================================
// NEW: TRUCK REQUEST DATA CLASSES (Multi-Truck Order System)
// =================================================================

/**
 * Response for active truck requests
 */
data class ActiveTruckRequestsResponse(
    val success: Boolean,
    val data: ActiveTruckRequestsData?
)

data class ActiveTruckRequestsData(
    val orders: List<OrderWithRequests>,
    val count: Int
)

data class OrderWithRequests(
    val order: OrderInfo,
    val requests: List<TruckRequestInfo>
)

data class OrderInfo(
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val pickup: LocationInfo,
    val drop: LocationInfo,
    val distanceKm: Int,
    val totalTrucks: Int,
    val trucksFilled: Int,
    val totalAmount: Int,
    val goodsType: String?,
    val weight: String?,
    val status: String,
    val expiresAt: String,
    val createdAt: String
)

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String?,
    val state: String?
)

data class TruckRequestInfo(
    val id: String,
    val orderId: String,
    val requestNumber: Int,
    val vehicleType: String,
    val vehicleSubtype: String,
    val pricePerTruck: Int,
    val status: String,
    val assignedTransporterId: String?,
    val assignedVehicleNumber: String?,
    val assignedDriverName: String?,
    val createdAt: String
)

/**
 * Request body for accepting a truck request
 */
data class AcceptTruckRequestBody(
    val vehicleId: String,
    val driverId: String? = null
)

/**
 * Response after accepting a truck request
 */
data class AcceptTruckRequestResponse(
    val success: Boolean,
    val data: AcceptTruckRequestData?,
    val error: ApiErrorInfo?
)

data class AcceptTruckRequestData(
    val request: TruckRequestInfo
)

data class ApiErrorInfo(
    val code: String,
    val message: String
)

/**
 * Response for order details
 */
data class OrderDetailsResponse(
    val success: Boolean,
    val data: OrderDetailsData?
)

data class OrderDetailsData(
    val order: OrderInfo,
    val requests: List<TruckRequestInfo>,
    val summary: OrderSummaryInfo
)

data class OrderSummaryInfo(
    val totalTrucks: Int,
    val trucksFilled: Int,
    val trucksSearching: Int,
    val trucksExpired: Int
)
