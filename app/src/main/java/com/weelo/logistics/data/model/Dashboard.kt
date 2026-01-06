package com.weelo.logistics.data.model

/**
 * Transporter Dashboard Data
 */
data class TransporterDashboard(
    val totalVehicles: Int = 0,
    val activeVehicles: Int = 0,
    val totalDrivers: Int = 0,
    val activeDrivers: Int = 0,
    val activeTrips: Int = 0,
    val todayRevenue: Double = 0.0,
    val todayTrips: Int = 0,
    val completedTrips: Int = 0,
    val recentTrips: List<Trip> = emptyList()
)

/**
 * Driver Dashboard Data
 */
data class DriverDashboard(
    val isAvailable: Boolean = false,
    val activeTrip: Trip? = null,
    val todayTrips: Int = 0,
    val todayEarnings: Double = 0.0,
    val todayDistance: Double = 0.0,
    val weekEarnings: Double = 0.0,
    val monthEarnings: Double = 0.0,
    val rating: Float = 0f,
    val totalTrips: Int = 0,
    val pendingTrips: List<Trip> = emptyList()
)

/**
 * Notification model
 */
data class Notification(
    val id: String,
    val userId: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val data: Map<String, String>? = null
)

/**
 * Notification Type
 */
enum class NotificationType {
    TRIP_ASSIGNED,      // Trip assigned to driver
    TRIP_ACCEPTED,      // Driver accepted trip
    TRIP_REJECTED,      // Driver rejected trip
    TRIP_STARTED,       // Trip started
    TRIP_COMPLETED,     // Trip completed
    TRIP_CANCELLED,     // Trip cancelled
    DRIVER_REGISTERED,  // New driver registered
    VEHICLE_ADDED,      // New vehicle added
    PAYMENT_RECEIVED,   // Payment received
    GENERAL            // General notification
}
