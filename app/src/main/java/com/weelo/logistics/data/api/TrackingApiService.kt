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
    
    /**
     * Upload batch of buffered location points (offline sync)
     * 
     * ENDPOINT: POST /api/v1/tracking/batch
     * AUTH: Bearer token (driver only)
     * 
     * USE CASE:
     * - Driver was offline for 5 minutes
     * - App buffered 30 location points locally
     * - On reconnect, uploads all at once
     * 
     * REQUEST:
     * {
     *   "tripId": "uuid",
     *   "points": [
     *     { "latitude": 28.7, "longitude": 77.1, "speed": 45.5, "bearing": 180, "accuracy": 10, "timestamp": "..." },
     *     ...
     *   ]
     * }
     * 
     * RESPONSE:
     * {
     *   "success": true,
     *   "data": { "processed": 30, "accepted": 25, "stale": 3, "duplicate": 1, "invalid": 1 }
     * }
     * 
     * RULES (enforced by backend):
     * - Max 100 points per batch
     * - Duplicate timestamps rejected
     * - Stale points (>60s) go to history only
     * - Unrealistic speed jumps flagged
     */
    @POST("tracking/batch")
    suspend fun uploadBatch(
        @Body request: BatchLocationRequest
    ): Response<BatchLocationResponse>
    
    /**
     * Update trip tracking status (start trip, complete trip, etc.)
     * 
     * ENDPOINT: PUT /api/v1/tracking/trip/{tripId}/status
     * AUTH: Bearer token (driver only)
     * 
     * STATUS FLOW:
     *   pending → heading_to_pickup → at_pickup → loading_complete → in_transit → arrived_at_drop → completed
     * 
     * On "completed": Backend runs completeTracking() which:
     *   - Removes driver from active fleet
     *   - Broadcasts ASSIGNMENT_STATUS_CHANGED to customer
     *   - Marks tracking as completed
     */
    @PUT("tracking/trip/{tripId}/status")
    suspend fun updateTripStatus(
        @Path("tripId") tripId: String,
        @Body request: TripStatusRequest
    ): Response<GenericResponse>
}

/**
 * Trip status update request
 * Matches backend enum:
 *   heading_to_pickup | at_pickup | loading_complete | in_transit | arrived_at_drop | completed
 *
 * STATUS FLOW (driver clicks buttons in order):
 *   heading_to_pickup → at_pickup → loading_complete → in_transit → completed
 *
 * Each status change triggers:
 *   1. Backend WebSocket broadcast to customer booking room
 *   2. Backend FCM push notification to customer (even when app closed)
 *   3. Customer sees: banner + truck card update + marker color change
 */
data class TripStatusRequest(
    val status: String
)

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

/**
 * Batch location upload request — for offline sync
 * Matches backend batchLocationSchema in tracking.schema.ts
 */
data class BatchLocationRequest(
    val tripId: String,
    val points: List<BatchLocationPoint>
)

/**
 * Single location point in a batch
 * Matches backend BatchLocationPoint type
 */
data class BatchLocationPoint(
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float? = null,
    val timestamp: String  // ISO 8601 format
)

/**
 * Batch upload response
 */
data class BatchLocationResponse(
    val success: Boolean,
    val message: String? = null,
    val data: BatchUploadResultData? = null,
    val error: ApiError? = null
)

/**
 * Batch upload result counts
 */
data class BatchUploadResultData(
    val tripId: String,
    val processed: Int,
    val accepted: Int,
    val stale: Int,
    val duplicate: Int,
    val invalid: Int,
    val lastAcceptedTimestamp: String? = null
)

// ApiError is defined in AuthApiService.kt - using that shared definition
