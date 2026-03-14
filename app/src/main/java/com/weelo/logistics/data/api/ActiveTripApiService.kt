package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.GET

/**
 * =============================================================================
 * ACTIVE TRIP API SERVICE — Driver Crash Recovery (Phase 7C)
 * =============================================================================
 *
 * Handles crash recovery for active driver trips.
 *
 * ENDPOINT: GET /api/v1/tracking/active-trip
 * ACCESS: Driver only (roleGuard(['driver']))
 * BACKEND: weelo-backend/src/modules/tracking/tracking.routes.ts
 *
 * USE CASE:
 * - Captain app crashes mid-trip
 * - On relaunch, driver calls this to restore trip state
 * - Returns full trip details: pickup, drop, status, vehicle info
 * - Returns null data if no active trip
 *
 * INDUSTRY PATTERN (Uber, Ola, DoorDash):
 * ✓ Single recovery endpoint on app relaunch
 * ✓ Server is source of truth
 * ✓ Returns complete trip context for UI restoration
 * ✓ Live GPS location from Redis for last known position
 *
 * NAMING:
 * Uses "DriverActiveTrip*" prefix to avoid collision with
 * ActiveTripResponse/ActiveTripData in DriverApiService.kt
 * (which serves GET /driver/trips/active for dashboard use).
 *
 * @author Weelo Team
 * @version 1.0.0
 * =============================================================================
 */
interface ActiveTripApiService {

    /**
     * Get driver's current active trip for crash recovery
     *
     * ENDPOINT: GET /api/v1/tracking/active-trip
     * AUTH: Bearer token (driver only)
     *
     * RESPONSE (has active trip):
     * {
     *   "success": true,
     *   "data": {
     *     "assignmentId": "...",
     *     "tripId": "trip_123",
     *     "status": "in_transit",
     *     "vehicleNumber": "MH12AB1234",
     *     "pickup": { "latitude": 28.7, "longitude": 77.1, "address": "..." },
     *     "drop": { "latitude": 28.6, "longitude": 77.2, "address": "..." },
     *     "customerName": "John",
     *     "customerPhone": "9876543210",
     *     "distanceKm": 15.5,
     *     "lastLocation": { "latitude": 28.7, "longitude": 77.1, "speed": 45.5, "bearing": 180 }
     *   }
     * }
     *
     * RESPONSE (no active trip):
     * {
     *   "success": true,
     *   "data": null,
     *   "message": "No active trip"
     * }
     */
    @GET("tracking/active-trip")
    suspend fun getActiveTrip(): Response<DriverActiveTripResponse>
}

// =============================================================================
// RESPONSE MODELS — Maps 1:1 to backend tracking.routes.ts:602-627
// =============================================================================

/**
 * Response wrapper for active trip recovery
 *
 * NOTE: Named "DriverActiveTripResponse" (NOT "ActiveTripResponse")
 * to avoid collision with ActiveTripResponse in DriverApiService.kt
 */
data class DriverActiveTripResponse(
    val success: Boolean,
    val data: DriverActiveTripData?,
    val message: String? = null,
    val error: ApiError? = null
)

/**
 * Active trip data for crash recovery
 *
 * Contains all information needed to restore the driver's trip UI:
 * - Assignment + trip identifiers
 * - Vehicle info
 * - Pickup/drop locations with addresses
 * - Route points for multi-stop trips
 * - Customer contact details
 * - Last known GPS position from Redis
 *
 * NOTE: Named "DriverActiveTripData" (NOT "ActiveTripData")
 * to avoid collision with ActiveTripData in DriverApiService.kt
 * which has different shape (trip: TripData?, hasActiveTrip: Boolean)
 */
data class DriverActiveTripData(
    val assignmentId: String,
    val tripId: String,
    val status: String,
    val orderId: String? = null,
    val bookingId: String? = null,
    val vehicleNumber: String,
    val vehicleType: String? = null,
    val vehicleSubtype: String? = null,
    val driverName: String? = null,
    val pickup: LocationData? = null,
    val drop: LocationData? = null,
    val routePoints: List<LocationData>? = null,
    val currentRouteIndex: Int = 0,
    val distanceKm: Double? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val assignedAt: String? = null,
    val lastLocation: LastLocationData? = null
)

/**
 * Last known GPS location from Redis
 *
 * Stored at key: driver:location:{driverId}
 * Updated every few seconds during active tracking
 *
 * Maps to backend tracking.routes.ts:620-626:
 * {
 *   latitude: locationData.latitude,
 *   longitude: locationData.longitude,
 *   speed: locationData.speed,
 *   bearing: locationData.bearing,
 *   lastUpdated: locationData.lastUpdated
 * }
 */
data class LastLocationData(
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val lastUpdated: String? = null
)
