package com.weelo.logistics.data.api

import com.weelo.logistics.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Trip API Service - For trip management and tracking
 * 
 * BACKEND INTEGRATION NOTES:
 * ==========================
 * Base URL: https://api.weelo.in/v1/
 * Headers: Authorization: Bearer {accessToken}
 * 
 * GPS TRACKING:
 * =============
 * Send location updates every 10-30 seconds during active trip
 * Use background service to ensure continuous tracking
 */
interface TripApiService {
    
    /**
     * Start trip
     * 
     * ENDPOINT: POST /trips/{tripId}/start
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "driverId": "driver_123",
     *   "startLocation": {
     *     "latitude": 28.7041,
     *     "longitude": 77.1025,
     *     "timestamp": "2026-01-05T10:00:00Z"
     *   },
     *   "vehicleId": "vehicle_456",
     *   "odometerReading": 12345.5
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "trip": {Trip object with status IN_PROGRESS},
     *   "message": "Trip started successfully"
     * }
     */
    @POST("trips/{tripId}/start")
    suspend fun startTrip(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String,
        @Body request: StartTripRequest
    ): Response<TripActionResponse>
    
    /**
     * Complete trip
     * 
     * ENDPOINT: POST /trips/{tripId}/complete
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "driverId": "driver_123",
     *   "endLocation": {Location with timestamp},
     *   "odometerReading": 12500.5,
     *   "deliveryProof": "base64_image_string" (optional),
     *   "signature": "base64_signature" (optional),
     *   "notes": "Delivered successfully"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "trip": {Trip object with status COMPLETED},
     *   "earnings": 85000.0,
     *   "message": "Trip completed successfully"
     * }
     */
    @POST("trips/{tripId}/complete")
    suspend fun completeTrip(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String,
        @Body request: CompleteTripRequest
    ): Response<TripActionResponse>
    
    /**
     * Cancel trip
     * 
     * ENDPOINT: POST /trips/{tripId}/cancel
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "driverId": "driver_123",
     *   "reason": "VEHICLE_BREAKDOWN", // or "EMERGENCY", "CUSTOMER_REQUEST", "OTHER"
     *   "notes": "Engine problem"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "message": "Trip cancelled"
     * }
     */
    @POST("trips/{tripId}/cancel")
    suspend fun cancelTrip(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String,
        @Body request: CancelTripRequest
    ): Response<GenericResponse>
    
    /**
     * Update driver location (GPS tracking)
     * 
     * ENDPOINT: POST /trips/{tripId}/location
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "driverId": "driver_123",
     *   "latitude": 28.7041,
     *   "longitude": 77.1025,
     *   "speed": 65.5,
     *   "heading": 180.0,
     *   "accuracy": 10.0,
     *   "timestamp": "2026-01-05T10:15:00Z"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "message": "Location updated"
     * }
     * 
     * NOTE: Send this every 10-30 seconds during active trip
     * Use background service for continuous tracking
     */
    @POST("trips/{tripId}/location")
    suspend fun updateLocation(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String,
        @Body request: LocationUpdateRequest
    ): Response<GenericResponse>
    
    /**
     * Get trip details
     * 
     * ENDPOINT: GET /trips/{tripId}
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "trip": {Trip object with full details}
     * }
     */
    @GET("trips/{tripId}")
    suspend fun getTripDetails(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String
    ): Response<TripDetailsResponse>
    
    /**
     * Get live tracking data for a trip
     * 
     * ENDPOINT: GET /trips/{tripId}/tracking
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "tracking": {
     *     "tripId": "trip_123",
     *     "driverId": "driver_456",
     *     "vehicleId": "vehicle_789",
     *     "currentLocation": {Location object},
     *     "currentSpeed": 65.5,
     *     "heading": 180.0,
     *     "tripStatus": "IN_PROGRESS",
     *     "startedAt": "2026-01-05T10:00:00Z",
     *     "estimatedArrival": "2026-01-05T14:00:00Z",
     *     "distanceCovered": 150.5,
     *     "distanceRemaining": 1269.5,
     *     "isLocationSharing": true
     *   }
     * }
     */
    @GET("trips/{tripId}/tracking")
    suspend fun getLiveTracking(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String
    ): Response<LiveTrackingResponse>
    
    /**
     * Get route for trip (navigation)
     * 
     * ENDPOINT: GET /trips/{tripId}/route
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "route": {
     *     "distance": 1420.0,
     *     "duration": 1200,
     *     "polyline": "encoded_polyline_string",
     *     "steps": [
     *       {
     *         "instruction": "Head north on Street Name",
     *         "distance": 1.5,
     *         "duration": 120
     *       }
     *     ]
     *   }
     * }
     */
    @GET("trips/{tripId}/route")
    suspend fun getTripRoute(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String
    ): Response<TripRouteResponse>
}

// ============== Request/Response Data Classes ==============

data class StartTripRequest(
    val driverId: String,
    val startLocation: LocationWithTimestamp,
    val vehicleId: String,
    val odometerReading: Double?
)

data class LocationWithTimestamp(
    val latitude: Double,
    val longitude: Double,
    val timestamp: String
)

data class CompleteTripRequest(
    val driverId: String,
    val endLocation: LocationWithTimestamp,
    val odometerReading: Double?,
    val deliveryProof: String?, // base64 image
    val signature: String?, // base64 signature
    val notes: String?
)

data class CancelTripRequest(
    val driverId: String,
    val reason: String, // "VEHICLE_BREAKDOWN", "EMERGENCY", "CUSTOMER_REQUEST", "OTHER"
    val notes: String?
)

data class LocationUpdateRequest(
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val heading: Float,
    val accuracy: Float,
    val timestamp: String
)

data class TripActionResponse(
    val success: Boolean,
    val trip: Trip,
    val earnings: Double? = null,
    val message: String,
    val error: String? = null
)

data class TripDetailsResponse(
    val success: Boolean,
    val trip: Trip
)

data class LiveTrackingResponse(
    val success: Boolean,
    val tracking: LiveTripTracking
)

data class TripRouteResponse(
    val success: Boolean,
    val route: RouteData
)

data class RouteData(
    val distance: Double,
    val duration: Int,
    val polyline: String,
    val steps: List<RouteStep>
)

data class RouteStep(
    val instruction: String,
    val distance: Double,
    val duration: Int
)

// Generic response for simple success/error responses
data class GenericResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)
