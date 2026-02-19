package com.weelo.logistics.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.weelo.logistics.data.api.*
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * =============================================================================
 * ASSIGNMENT REPOSITORY - Driver Trip Accept/Decline
 * =============================================================================
 * 
 * Repository layer for assignment operations.
 * Handles API calls, error handling, and data mapping.
 * 
 * PATTERN: Follows BroadcastRepository.kt exactly
 * - Singleton with thread-safe double-checked locking
 * - Uses BroadcastResult<T> for consistent error handling
 * - All API calls on Dispatchers.IO
 * - Token authentication via RetrofitClient
 * 
 * FLOW:
 *   TripAcceptDeclineScreen → AssignmentRepository → AssignmentApiService → Backend
 *   
 * SCALABILITY:
 * - Singleton prevents multiple instances consuming memory
 * - Dispatchers.IO uses thread pool for concurrent requests
 * - RetrofitClient has connection pooling (10 connections, 5min keep-alive)
 * 
 * FOR DEVELOPERS:
 * - Backend module: weelo-backend/src/modules/assignment/
 * - API service: AssignmentApiService.kt
 * - Screen: TripAcceptDeclineScreen.kt
 * =============================================================================
 */
class AssignmentRepository private constructor(
    private val context: Context
) {
    private val assignmentApi = RetrofitClient.assignmentApi

    @SuppressLint("StaticFieldLeak")
    companion object {
        private const val TAG = "AssignmentRepository"

        @Volatile
        private var instance: AssignmentRepository? = null

        /**
         * Get singleton instance
         * Thread-safe double-checked locking (same pattern as BroadcastRepository)
         */
        fun getInstance(context: Context): AssignmentRepository {
            return instance ?: synchronized(this) {
                instance ?: AssignmentRepository(context.applicationContext).also { instance = it }
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
    // GET ASSIGNMENT DETAILS
    // =========================================================================

    /**
     * Fetch assignment details by ID
     * 
     * Called when driver opens TripAcceptDeclineScreen.
     * Maps backend AssignmentData → existing DriverTripNotification + TripAssignment models
     * so the UI code doesn't need any changes.
     * 
     * @param assignmentId The assignment UUID (from trip_assigned socket event)
     * @return AssignmentDetail with notification + assignment data for UI
     */
    suspend fun getAssignmentById(
        assignmentId: String
    ): BroadcastResult<AssignmentDetail> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }

            timber.log.Timber.d("📡 Fetching assignment details: $assignmentId")

            val response = assignmentApi.getAssignmentById(
                token = "Bearer $token",
                assignmentId = assignmentId
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val assignment = response.body()?.data
                if (assignment != null) {
                    val detail = mapToAssignmentDetail(assignment)
                    timber.log.Timber.i("✅ Assignment fetched: ${assignment.id} — Status: ${assignment.status}")
                    return@withContext BroadcastResult.Success(detail)
                }
                return@withContext BroadcastResult.Error("Assignment data missing in response")
            } else {
                val errorMsg = response.body()?.error
                    ?: response.errorBody()?.string()
                    ?: "Failed to fetch assignment"
                timber.log.Timber.e("❌ Fetch assignment failed: $errorMsg (code: ${response.code()})")
                return@withContext BroadcastResult.Error(errorMsg, response.code())
            }

        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Network error fetching assignment $assignmentId")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }

    // =========================================================================
    // ACCEPT ASSIGNMENT
    // =========================================================================

    /**
     * Accept a trip assignment
     * 
     * Called when driver taps "Accept" → "Confirm Accept" in TripAcceptDeclineScreen.
     * 
     * WHAT HAPPENS:
     * 1. Calls PATCH /assignments/:id/accept
     * 2. Backend validates & updates status to 'driver_accepted'
     * 3. Backend cancels 60s timeout timer
     * 4. Backend notifies transporter ('driver_accepted' socket event)
     * 5. Returns updated assignment with tripId for GPS tracking
     * 
     * AFTER SUCCESS (caller should):
     * - Start GPSTrackingService with tripId
     * - Navigate to DriverTripNavigationScreen
     * 
     * @param assignmentId The assignment to accept
     * @return AcceptResult with tripId for tracking
     */
    suspend fun acceptAssignment(
        assignmentId: String
    ): BroadcastResult<AcceptResult> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }

            timber.log.Timber.i("📤 Accepting assignment: $assignmentId")

            val response = assignmentApi.acceptAssignment(
                token = "Bearer $token",
                assignmentId = assignmentId
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val assignment = response.body()?.data
                if (assignment != null) {
                    timber.log.Timber.i("✅ Assignment accepted! Trip: ${assignment.tripId}, Vehicle: ${assignment.vehicleNumber}")
                    return@withContext BroadcastResult.Success(
                        AcceptResult(
                            assignmentId = assignment.id,
                            tripId = assignment.tripId,
                            vehicleNumber = assignment.vehicleNumber,
                            status = assignment.status
                        )
                    )
                }
                return@withContext BroadcastResult.Error("Assignment data missing in response")
            } else {
                val errorMsg = response.body()?.error
                    ?: response.errorBody()?.string()
                    ?: "Failed to accept assignment"
                timber.log.Timber.e("❌ Accept failed: $errorMsg (code: ${response.code()})")
                return@withContext BroadcastResult.Error(errorMsg, response.code())
            }

        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Network error accepting assignment $assignmentId")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }

    // =========================================================================
    // DECLINE ASSIGNMENT
    // =========================================================================

    /**
     * Decline a trip assignment with reason
     * 
     * Called when driver taps "Decline" → "Confirm Decline" in TripAcceptDeclineScreen.
     * 
     * WHAT HAPPENS:
     * 1. Calls PATCH /assignments/:id/decline with reason
     * 2. Backend validates & updates status to 'driver_declined'
     * 3. Backend cancels 60s timeout timer
     * 4. Backend releases vehicle back to 'available'
     * 5. Backend notifies transporter ('driver_declined' socket event)
     * 
     * AFTER SUCCESS (caller should):
     * - Navigate back to previous screen
     * 
     * @param assignmentId The assignment to decline
     * @param reason Why the driver is declining (e.g., "Vehicle Issue", "Too Far")
     * @return true on success
     */
    suspend fun declineAssignment(
        assignmentId: String,
        reason: String
    ): BroadcastResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }

            // Sanitize reason — prevent empty or excessively long strings
            val sanitizedReason = reason.trim().take(500).ifEmpty { "No reason provided" }

            timber.log.Timber.i("📤 Declining assignment: $assignmentId — Reason: $sanitizedReason")

            val response = assignmentApi.declineAssignment(
                token = "Bearer $token",
                assignmentId = assignmentId,
                request = DeclineAssignmentRequest(reason = sanitizedReason)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                timber.log.Timber.i("✅ Assignment declined: $assignmentId")
                return@withContext BroadcastResult.Success(true)
            } else {
                val errorMsg = response.body()?.error
                    ?: response.errorBody()?.string()
                    ?: "Failed to decline assignment"
                timber.log.Timber.e("❌ Decline failed: $errorMsg (code: ${response.code()})")
                return@withContext BroadcastResult.Error(errorMsg, response.code())
            }

        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Network error declining assignment $assignmentId")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }

    // =========================================================================
    // GET DRIVER'S ASSIGNMENTS
    // =========================================================================

    /**
     * Fetch all assignments for the current driver
     * 
     * @param status Optional filter: "pending", "driver_accepted", "driver_declined", etc.
     * @return List of assignments
     */
    suspend fun getDriverAssignments(
        status: String? = null
    ): BroadcastResult<List<AssignmentDetail>> = withContext(Dispatchers.IO) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext BroadcastResult.Error("Not authenticated", 401)
            }

            timber.log.Timber.d("📡 Fetching driver assignments (status: ${status ?: "all"})")

            val response = assignmentApi.getDriverAssignments(
                token = "Bearer $token",
                status = status
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val assignments = response.body()?.data?.assignments ?: emptyList()
                val mapped = assignments.map { mapToAssignmentDetail(it) }
                timber.log.Timber.i("✅ Fetched ${mapped.size} assignments")
                return@withContext BroadcastResult.Success(mapped)
            } else {
                val errorMsg = response.body()?.error
                    ?: response.errorBody()?.string()
                    ?: "Failed to fetch assignments"
                return@withContext BroadcastResult.Error(errorMsg, response.code())
            }

        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Network error fetching driver assignments")
            return@withContext BroadcastResult.Error(e.message ?: "Network error")
        }
    }

    // =========================================================================
    // MAPPING — Backend AssignmentData → UI Models
    // =========================================================================
    // 
    // The UI (TripAcceptDeclineScreen) uses DriverTripNotification + TripAssignment
    // models. We map backend data to these existing models so the UI code stays
    // unchanged — zero UI modifications needed.
    // =========================================================================

    /**
     * Map backend AssignmentData → UI-compatible AssignmentDetail
     * 
     * This mapping exists so the UI doesn't need to know about backend
     * data structures. If the backend changes field names, only this
     * mapping needs updating — not the UI.
     * 
     * Backend enriches response with booking data (pickup/drop/distance/fare)
     * so we have everything in one API call. Fallback to empty strings if
     * booking data is missing (assignment is still valid for display).
     */
    private fun mapToAssignmentDetail(data: AssignmentData): AssignmentDetail {
        // Map route points from backend RoutePointData → UI RoutePoint model
        // This includes ALL stops: PICKUP → STOP → STOP → DROP
        val mappedRoutePoints = data.routePoints?.map { rp ->
            RoutePoint(
                type = when (rp.type.uppercase()) {
                    "PICKUP" -> RoutePointType.PICKUP
                    "STOP" -> RoutePointType.STOP
                    "DROP" -> RoutePointType.DROP
                    else -> RoutePointType.STOP
                },
                latitude = rp.latitude,
                longitude = rp.longitude,
                address = rp.address,
                city = rp.city,
                stopIndex = rp.stopIndex
            )
        } ?: emptyList()
        
        return AssignmentDetail(
            assignmentId = data.id,
            tripId = data.tripId,
            bookingId = data.bookingId,
            // Notification for the screen's top section (fare, addresses, distance)
            notification = DriverTripNotification(
                notificationId = data.id,
                assignmentId = data.id,
                driverId = data.driverId,
                pickupAddress = data.pickupAddress ?: "Pickup location",
                dropAddress = data.dropAddress ?: "Drop location",
                distance = data.distanceKm ?: 0.0,
                estimatedDuration = 0, // TODO: Backend to add estimatedDuration
                fare = data.pricePerTruck ?: 0.0,
                goodsType = data.goodsType ?: "",
                receivedAt = parseTimestamp(data.assignedAt),
                status = mapNotificationStatus(data.status)
            ),
            // Assignment section showing vehicle + driver + route info
            assignment = TripAssignment(
                assignmentId = data.id,
                broadcastId = data.bookingId,
                transporterId = data.transporterId,
                trucksTaken = 1,
                assignments = listOf(
                    DriverTruckAssignment(
                        driverId = data.driverId,
                        driverName = data.driverName,
                        vehicleId = data.vehicleId ?: "",
                        vehicleNumber = data.vehicleNumber,
                        status = mapDriverResponseStatus(data.status)
                    )
                ),
                // Full route with intermediate stops (PICKUP → STOP → STOP → DROP)
                routePoints = mappedRoutePoints,
                totalStops = data.totalStops ?: mappedRoutePoints.count { it.type == RoutePointType.STOP },
                currentRouteIndex = data.currentRouteIndex ?: 0,
                // Pickup/drop locations (first and last route points, or from booking)
                pickupLocation = Location(
                    latitude = data.pickupLat ?: 0.0,
                    longitude = data.pickupLng ?: 0.0,
                    address = data.pickupAddress ?: ""
                ),
                dropLocation = Location(
                    latitude = data.dropLat ?: 0.0,
                    longitude = data.dropLng ?: 0.0,
                    address = data.dropAddress ?: ""
                ),
                distance = data.distanceKm ?: 0.0,
                farePerTruck = data.pricePerTruck ?: 0.0,
                goodsType = data.goodsType ?: ""
            ),
            vehicleNumber = data.vehicleNumber,
            transporterName = data.transporterName ?: "",
            status = data.status
        )
    }

    /**
     * Map backend status string to NotificationStatus enum
     */
    private fun mapNotificationStatus(status: String): NotificationStatus {
        return when (status.lowercase()) {
            "pending" -> NotificationStatus.PENDING_RESPONSE
            "driver_accepted" -> NotificationStatus.ACCEPTED
            "driver_declined" -> NotificationStatus.DECLINED
            "timed_out" -> NotificationStatus.EXPIRED
            "cancelled" -> NotificationStatus.EXPIRED
            else -> NotificationStatus.PENDING_RESPONSE
        }
    }

    /**
     * Map backend status string to DriverResponseStatus enum
     */
    private fun mapDriverResponseStatus(status: String): DriverResponseStatus {
        return when (status.lowercase()) {
            "pending" -> DriverResponseStatus.PENDING
            "driver_accepted" -> DriverResponseStatus.ACCEPTED
            "driver_declined" -> DriverResponseStatus.DECLINED
            "timed_out" -> DriverResponseStatus.EXPIRED
            "cancelled" -> DriverResponseStatus.DECLINED
            else -> DriverResponseStatus.PENDING
        }
    }

    /**
     * Parse ISO timestamp to epoch millis
     */
    private fun parseTimestamp(timestamp: String?): Long {
        if (timestamp == null) return System.currentTimeMillis()
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(timestamp)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.parse(timestamp)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}

// =============================================================================
// RESULT DATA CLASSES
// =============================================================================

/**
 * Assignment detail — combines notification + assignment for UI consumption
 * TripAcceptDeclineScreen uses both DriverTripNotification and TripAssignment
 */
data class AssignmentDetail(
    val assignmentId: String,
    val tripId: String,
    val bookingId: String,
    val notification: DriverTripNotification,
    val assignment: TripAssignment,
    val vehicleNumber: String,
    val transporterName: String,
    val status: String
)

/**
 * Accept result — returned after successful accept
 * Contains tripId needed to start GPS tracking
 */
data class AcceptResult(
    val assignmentId: String,
    val tripId: String,
    val vehicleNumber: String,
    val status: String
)
