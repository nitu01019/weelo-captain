package com.weelo.logistics.data.model

/**
 * =============================================================================
 * TRIP ASSIGNED NOTIFICATION
 * =============================================================================
 *
 * Sent to driver when transporter assigns them to a trip.
 *
 * Backend emits from: truck-hold.service.ts → confirmHoldWithAssignments()
 * Event name: "trip_assigned"
 * Target: Driver's personal room (user:{driverId})
 *
 * FLOW:
 *   Backend emits → SocketIOService receives → _tripAssigned flow →
 *   UI collects → Navigate to TripAcceptDeclineScreen
 *
 * This data class matches the backend's driverNotification object exactly.
 * Any field changes in backend must be reflected here.
 */
data class TripAssignedNotification(
    val assignmentId: String,        // Unique assignment ID (UUID)
    val tripId: String,              // Trip ID for tracking (UUID)
    val orderId: String,             // Original customer order ID
    val truckRequestId: String,      // Which truck request this fulfills
    val pickup: TripLocationInfo,    // Pickup location with lat/lng
    val drop: TripLocationInfo,      // Drop location with lat/lng
    val vehicleNumber: String,       // e.g., "KA-01-AB-1234"
    val farePerTruck: Double,        // Price in ₹ for this trip
    val distanceKm: Double,          // Distance in km
    val customerName: String,        // Customer name for display
    val customerPhone: String,       // Customer phone for calling
    val assignedAt: String,          // ISO timestamp of assignment
    val expiresAt: String?,         // ISO timestamp when offer expires (60s from assignment)
    val routePoints: List<RoutePoint>?,  // Full route for multi-stop trips
    val message: String              // Human-readable message
) {
    /**
     * Get remaining seconds for countdown timer
     * Returns 0 if expiresAt is null or already expired
     */
    val remainingSeconds: Int
        get() {
            val expiryTime = expiresAt?.let { parseIso8601(it) } ?: return 60
            val now = System.currentTimeMillis()
            val remaining = (expiryTime - now) / 1000
            return if (remaining > 0) remaining.toInt() else 0
        }

    /**
     * Check if the request has expired
     */
    val isExpired: Boolean
        get() = remainingSeconds <= 0

    /**
     * Check if urgent (less than 15 seconds remaining)
     */
    val isUrgent: Boolean
        get() = remainingSeconds > 0 && remainingSeconds < 15

    /**
     * Format remaining time as string (e.g., "15s", "1m")
     */
    val remainingTimeFormatted: String
        get() {
            val secs = remainingSeconds
            return when {
                secs >= 60 -> "${secs / 60}m"
                secs > 0 -> "${secs}s"
                else -> "0s"
            }
        }

    private fun parseIso8601(isoString: String): Long {
        return try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            // Fallback: try ISO with timezone offset
            try {
                // Handle formats like: 2024-01-15T10:30:00+05:30
                val s = isoString.replace("Z", "+00:00")
                java.time.OffsetDateTime.parse(s)
                    .toInstant()
                    .toEpochMilli()
            } catch (e2: Exception) {
                0
            }
        }
    }
}

/**
 * Trip Location Info - used for pickup and drop locations
 */
data class TripLocationInfo(
    val address: String,
    val city: String,
    val latitude: Double,
    val longitude: Double
)
