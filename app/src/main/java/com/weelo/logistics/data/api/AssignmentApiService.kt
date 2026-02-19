package com.weelo.logistics.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

/**
 * =============================================================================
 * ASSIGNMENT API SERVICE - Driver Trip Accept/Decline
 * =============================================================================
 * 
 * Retrofit interface for assignment endpoints.
 * Used by drivers to view, accept, and decline trip assignments.
 * 
 * BACKEND INTEGRATION NOTES:
 * ==========================
 * Base URL: https://api.weelo.in/v1/
 * Headers: Authorization: Bearer {accessToken}
 * Backend module: weelo-backend/src/modules/assignment/
 * 
 * FLOW:
 * 1. Transporter confirms hold → Backend creates assignment (status: pending)
 * 2. Backend emits 'trip_assigned' to driver via Socket.IO
 * 3. Driver opens TripAcceptDeclineScreen → GET /assignments/:id
 * 4. Driver taps Accept → PATCH /assignments/:id/accept
 * 5. Driver taps Decline → PATCH /assignments/:id/decline
 * 
 * SECURITY:
 * - All endpoints require JWT authentication
 * - Driver can only access their own assignments
 * - Backend validates assignment.driverId === req.user.userId
 * =============================================================================
 */
interface AssignmentApiService {

    /**
     * Get assignment details (for driver)
     * 
     * ENDPOINT: GET /assignments/{assignmentId}
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "data": {
     *     "id": "uuid",
     *     "bookingId": "uuid",
     *     "vehicleNumber": "KA-01-AB-1234",
     *     "driverName": "Rajesh Kumar",
     *     "status": "pending",
     *     "pickupAddress": "Warehouse, Bangalore",
     *     "dropAddress": "Factory, Hubli",
     *     "distanceKm": 350.5,
     *     "pricePerTruck": 18000,
     *     "routePoints": [{ "type": "PICKUP", ... }, { "type": "STOP", ... }, { "type": "DROP", ... }],
     *     ...
     *   }
     * }
     */
    @GET("assignments/{assignmentId}")
    suspend fun getAssignmentById(
        @Header("Authorization") token: String,
        @Path("assignmentId") assignmentId: String
    ): Response<AssignmentDetailResponse>

    /**
     * Get driver's assignments
     * 
     * ENDPOINT: GET /assignments/driver
     * Headers: Authorization: Bearer {accessToken}
     * Query: ?status=pending (optional filter)
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "data": {
     *     "assignments": [...],
     *     "total": 5,
     *     "hasMore": false
     *   }
     * }
     */
    @GET("assignments/driver")
    suspend fun getDriverAssignments(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<DriverAssignmentsResponse>

    /**
     * Accept assignment (Driver accepts trip)
     * 
     * ENDPOINT: PATCH /assignments/{assignmentId}/accept
     * Headers: Authorization: Bearer {accessToken}
     * 
     * WHAT HAPPENS ON BACKEND:
     * 1. Validates assignment belongs to this driver
     * 2. Validates assignment status is 'pending'
     * 3. Checks ONE-ACTIVE-TRIP-PER-DRIVER rule
     * 4. Updates status to 'driver_accepted'
     * 5. Cancels 60s timeout timer
     * 6. Notifies transporter via Socket.IO ('driver_accepted')
     * 7. Notifies booking room ('assignment_status_changed')
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "data": { "id": "uuid", "status": "driver_accepted", "driverAcceptedAt": "...", ... }
     * }
     * 
     * NOTE: Assignment fields are directly under "data", not wrapped in { "assignment": ... }
     */
    @PATCH("assignments/{assignmentId}/accept")
    suspend fun acceptAssignment(
        @Header("Authorization") token: String,
        @Path("assignmentId") assignmentId: String
    ): Response<AssignmentDetailResponse>

    /**
     * Decline assignment with reason (Driver declines trip)
     * 
     * ENDPOINT: PATCH /assignments/{assignmentId}/decline
     * Headers: Authorization: Bearer {accessToken}
     * 
     * DIFFERENT FROM CANCEL:
     * - Cancel (DELETE /:id) = transporter removes, decrements truck count
     * - Decline (PATCH /:id/decline) = driver refuses with reason, transporter reassigns
     * 
     * WHAT HAPPENS ON BACKEND:
     * 1. Validates assignment belongs to this driver
     * 2. Validates assignment status is 'pending'
     * 3. Updates status to 'driver_declined' with reason
     * 4. Cancels 60s timeout timer
     * 5. Releases vehicle back to 'available'
     * 6. Notifies transporter via Socket.IO ('driver_declined')
     * 7. Notifies booking room ('assignment_status_changed')
     * 
     * Request Body:
     * {
     *   "reason": "Vehicle Issue" | "Too Far" | "Personal Emergency" | "Already on Trip" | "Other"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "message": "Assignment declined"
     * }
     */
    @PATCH("assignments/{assignmentId}/decline")
    suspend fun declineAssignment(
        @Header("Authorization") token: String,
        @Path("assignmentId") assignmentId: String,
        @Body request: DeclineAssignmentRequest
    ): Response<GenericResponse>
}

// =============================================================================
// REQUEST DATA CLASSES
// =============================================================================

/**
 * Request body for declining an assignment
 * Backend validates: reason is required, max 500 chars
 */
data class DeclineAssignmentRequest(
    @SerializedName("reason")
    val reason: String
)

// =============================================================================
// RESPONSE DATA CLASSES
// =============================================================================

/**
 * Response for single assignment detail
 * Used by: GET /assignments/:id, PATCH /assignments/:id/accept
 * 
 * BACKEND RESPONSE FORMAT:
 * {
 *   "success": true,
 *   "data": { "id": "...", "bookingId": "...", ... }   ← assignment fields directly in data
 * }
 * 
 * NOTE: Backend returns assignment fields DIRECTLY under "data",
 * NOT wrapped in { "assignment": { ... } }. This matches the standard
 * Express pattern used across all backend routes.
 */
data class AssignmentDetailResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("data")
    val data: AssignmentData? = null,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: String? = null
)

/**
 * Assignment data from backend
 * 
 * Includes both AssignmentRecord fields AND enriched booking data.
 * Backend getAssignmentById() JOINs with booking to include pickup/drop/fare
 * so the client makes ONE API call instead of two.
 * 
 * Booking fields are nullable — they're enriched server-side and may be
 * absent if booking lookup fails (assignment data is still valid).
 */
data class AssignmentData(
    // --- Assignment Record Fields ---
    @SerializedName("id")
    val id: String,

    @SerializedName("bookingId")
    val bookingId: String,

    @SerializedName("transporterId")
    val transporterId: String,

    @SerializedName("transporterName")
    val transporterName: String? = null,

    @SerializedName("vehicleId")
    val vehicleId: String? = null,

    @SerializedName("vehicleNumber")
    val vehicleNumber: String,

    @SerializedName("vehicleType")
    val vehicleType: String? = null,

    @SerializedName("vehicleSubtype")
    val vehicleSubtype: String? = null,

    @SerializedName("driverId")
    val driverId: String,

    @SerializedName("driverName")
    val driverName: String,

    @SerializedName("driverPhone")
    val driverPhone: String? = null,

    @SerializedName("tripId")
    val tripId: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("assignedAt")
    val assignedAt: String? = null,

    @SerializedName("driverAcceptedAt")
    val driverAcceptedAt: String? = null,

    @SerializedName("declineReason")
    val declineReason: String? = null,

    @SerializedName("declinedAt")
    val declinedAt: String? = null,

    // --- Enriched Booking Fields (from getAssignmentById JOIN) ---
    // These come from the associated booking/order record
    @SerializedName("pickupAddress")
    val pickupAddress: String? = null,

    @SerializedName("dropAddress")
    val dropAddress: String? = null,

    @SerializedName("pickupLat")
    val pickupLat: Double? = null,

    @SerializedName("pickupLng")
    val pickupLng: Double? = null,

    @SerializedName("dropLat")
    val dropLat: Double? = null,

    @SerializedName("dropLng")
    val dropLng: Double? = null,

    @SerializedName("distanceKm")
    val distanceKm: Double? = null,

    @SerializedName("pricePerTruck")
    val pricePerTruck: Double? = null,

    @SerializedName("goodsType")
    val goodsType: String? = null,

    @SerializedName("customerName")
    val customerName: String? = null,

    @SerializedName("customerPhone")
    val customerPhone: String? = null,

    // --- Route Points (from OrderRecord — intermediate stops) ---
    // Array of PICKUP → STOP → STOP → DROP with lat/lng/address
    // Critical for driver navigation — shows ALL stops, not just pickup/drop
    @SerializedName("routePoints")
    val routePoints: List<RoutePointData>? = null,

    @SerializedName("totalStops")
    val totalStops: Int? = null,

    @SerializedName("currentRouteIndex")
    val currentRouteIndex: Int? = null
)

/**
 * Response for driver's assignments list
 * Used by: GET /assignments/driver
 */
data class DriverAssignmentsResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("data")
    val data: DriverAssignmentsDataWrapper? = null,

    @SerializedName("error")
    val error: String? = null
)

data class DriverAssignmentsDataWrapper(
    @SerializedName("assignments")
    val assignments: List<AssignmentData>,

    @SerializedName("total")
    val total: Int,

    @SerializedName("hasMore")
    val hasMore: Boolean
)

/**
 * Route point data from backend OrderRecord.routePoints
 * 
 * Represents a single point in the route: PICKUP → STOP → STOP → DROP
 * Matches backend RoutePointRecord exactly.
 * 
 * Example route with 2 intermediate stops:
 *   [0] PICKUP  → Warehouse, Bangalore   (stopIndex: 0)
 *   [1] STOP    → Depot, Tumkur          (stopIndex: 1)
 *   [2] STOP    → Hub, Davangere         (stopIndex: 2)
 *   [3] DROP    → Factory, Hubli         (stopIndex: 3)
 */
data class RoutePointData(
    @SerializedName("type")
    val type: String,              // "PICKUP" | "STOP" | "DROP"

    @SerializedName("latitude")
    val latitude: Double,

    @SerializedName("longitude")
    val longitude: Double,

    @SerializedName("address")
    val address: String,

    @SerializedName("city")
    val city: String? = null,

    @SerializedName("state")
    val state: String? = null,

    @SerializedName("stopIndex")
    val stopIndex: Int
)
