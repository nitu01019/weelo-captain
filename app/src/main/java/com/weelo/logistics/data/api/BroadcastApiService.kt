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
 * TRANSPORTER SIDE (Weelo App):
 * 1. Create broadcast with trip details
 * 2. Backend broadcasts to eligible drivers via WebSocket/FCM
 * 3. Transporter sees responses from drivers in real-time
 * 4. Transporter assigns drivers to trips
 * 
 * DRIVER SIDE (Weelo Captain App):
 * 1. Receive broadcast notification via FCM
 * 2. View broadcast details
 * 3. Accept or decline the trip
 * 4. If accepted, trip is assigned and shows in active trips
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
     * Get all active broadcasts for driver
     * 
     * ENDPOINT: GET /broadcasts/active
     * Headers: Authorization: Bearer {accessToken}
     * Query Params:
     * - driverId: string (required)
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
        @Query("driverId") driverId: String,
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
     * Accept a broadcast (driver accepts the trip)
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
        @Path("broadcastId") broadcastId: String,
        @Body request: AcceptBroadcastRequest
    ): Response<AcceptBroadcastResponse>
    
    /**
     * Decline a broadcast
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
}

// ============== Request/Response Data Classes ==============

data class BroadcastListResponse(
    val success: Boolean,
    val broadcasts: List<BroadcastTrip>,
    val count: Int
)

data class BroadcastResponse(
    val success: Boolean,
    val broadcast: BroadcastTrip
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
