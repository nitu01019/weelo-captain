package com.weelo.logistics.data.api

import com.weelo.logistics.ui.transporter.FleetTrackingData
import retrofit2.Response
import retrofit2.http.*

/**
 * =============================================================================
 * TRACKING API SERVICE - Real-time Location Tracking
 * =============================================================================
 * 
 * Handles all tracking-related API calls.
 * 
 * ENDPOINTS:
 * ──────────
 * GET  /tracking/fleet         → Get all fleet driver locations (transporter)
 * GET  /tracking/:tripId       → Get single trip location
 * GET  /tracking/booking/:id   → Get all trucks for a booking (customer)
 * POST /tracking/update        → Update driver location (driver app)
 * 
 * BACKEND: weelo-backend/src/modules/tracking/
 * 
 * @author Weelo Team
 * @version 1.0.0
 * =============================================================================
 */
interface TrackingApiService {
    
    /**
     * ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
     * ┃  GET FLEET TRACKING - For Transporter Fleet Map                       ┃
     * ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
     * 
     * Returns all active driver locations for the transporter's fleet.
     * Used by: FleetMapScreen to show all trucks on map
     * 
     * ENDPOINT: GET /api/v1/tracking/fleet
     * AUTH: Bearer token (transporter only)
     * 
     * RESPONSE:
     * {
     *   "success": true,
     *   "data": {
     *     "transporterId": "...",
     *     "activeDrivers": 5,
     *     "drivers": [
     *       {
     *         "driverId": "...",
     *         "driverName": "John",
     *         "vehicleNumber": "MH12AB1234",
     *         "tripId": "trip_123",
     *         "latitude": 28.7041,
     *         "longitude": 77.1025,
     *         "speed": 45.5,
     *         "bearing": 180.0,
     *         "status": "in_transit",
     *         "lastUpdated": "2026-01-23T10:30:00Z"
     *       }
     *     ]
     *   }
     * }
     * 
     * USAGE:
     * - Poll every 5 seconds for fleet overview
     * - Use WebSocket for real-time single trip tracking
     */
    @GET("tracking/fleet")
    suspend fun getFleetTracking(): Response<FleetTrackingResponse>
    
    /**
     * Get single trip location
     * 
     * ENDPOINT: GET /api/v1/tracking/{tripId}
     * AUTH: Bearer token
     * 
     * RESPONSE:
     * {
     *   "success": true,
     *   "data": {
     *     "tripId": "...",
     *     "driverId": "...",
     *     "vehicleNumber": "...",
     *     "latitude": 28.7041,
     *     "longitude": 77.1025,
     *     "speed": 45.5,
     *     "bearing": 180.0,
     *     "status": "in_transit",
     *     "lastUpdated": "..."
     *   }
     * }
     */
    @GET("tracking/{tripId}")
    suspend fun getTripTracking(
        @Path("tripId") tripId: String
    ): Response<TripTrackingResponse>
    
    /**
     * Update driver location (for driver app)
     * 
     * ENDPOINT: POST /api/v1/tracking/update
     * AUTH: Bearer token (driver only)
     * 
     * REQUEST:
     * {
     *   "tripId": "trip_123",
     *   "latitude": 28.7041,
     *   "longitude": 77.1025,
     *   "speed": 45.5,
     *   "bearing": 180.0,
     *   "accuracy": 10.0
     * }
     * 
     * RESPONSE:
     * {
     *   "success": true,
     *   "message": "Location updated"
     * }
     */
    @POST("tracking/update")
    suspend fun updateLocation(
        @Body request: UpdateLocationRequest
    ): Response<UpdateLocationResponse>
}

// =============================================================================
// RESPONSE MODELS
// =============================================================================

/**
 * Fleet tracking API response
 */
data class FleetTrackingResponse(
    val success: Boolean,
    val data: FleetTrackingData?,
    val error: ApiError? = null
)

/**
 * Single trip tracking response
 */
data class TripTrackingResponse(
    val success: Boolean,
    val data: TripTrackingData?,
    val error: ApiError? = null
)

/**
 * Trip tracking data
 */
data class TripTrackingData(
    val tripId: String,
    val driverId: String,
    val vehicleNumber: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val bearing: Float,
    val status: String,
    val lastUpdated: String
)

/**
 * Update location request
 */
data class UpdateLocationRequest(
    val tripId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float? = null
)

/**
 * Update location response
 */
data class UpdateLocationResponse(
    val success: Boolean,
    val message: String? = null,
    val error: ApiError? = null
)

// ApiError is defined in AuthApiService.kt - using that shared definition
