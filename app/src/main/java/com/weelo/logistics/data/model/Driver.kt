package com.weelo.logistics.data.model

/**
 * Driver model - Simplified driver information for transporter's view
 */
data class Driver(
    val id: String,
    val name: String,
    val mobileNumber: String,
    val licenseNumber: String,
    val transporterId: String,
    val assignedVehicleId: String? = null,
    val isAvailable: Boolean = true,
    val rating: Float = 0f,
    val totalTrips: Int = 0,
    val profileImageUrl: String? = null,
    val status: DriverStatus = DriverStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Driver Status
 */
enum class DriverStatus {
    ACTIVE,         // Active and can take trips
    ON_TRIP,        // Currently on a trip
    INACTIVE,       // Not available
    SUSPENDED       // Account suspended
}

/**
 * Driver Earnings
 */
data class DriverEarnings(
    val driverId: String,
    val todayEarnings: Double = 0.0,
    val weekEarnings: Double = 0.0,
    val monthEarnings: Double = 0.0,
    val totalEarnings: Double = 0.0,
    val todayTrips: Int = 0,
    val totalTrips: Int = 0
)

/**
 * Driver Performance
 */
data class DriverPerformance(
    val driverId: String,
    val rating: Float,
    val totalTrips: Int,
    val completedTrips: Int,
    val cancelledTrips: Int,
    val avgTripTime: Long, // in minutes
    val totalDistance: Double, // in km
    val onTimeDeliveryRate: Float // percentage
)
