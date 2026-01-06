package com.weelo.logistics.data.model

/**
 * Trip model - Represents a delivery trip
 */
data class Trip(
    val id: String,
    val transporterId: String,
    val vehicleId: String,
    val driverId: String? = null,
    val pickupLocation: Location,
    val dropLocation: Location,
    val distance: Double = 0.0, // in km
    val estimatedDuration: Long = 0, // in minutes
    val status: TripStatus = TripStatus.PENDING,
    val customerName: String,
    val customerMobile: String,
    val goodsType: String,
    val weight: String? = null,
    val fare: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val notes: String? = null
)

/**
 * Trip Status
 */
enum class TripStatus {
    PENDING,        // Created, waiting for driver assignment/acceptance
    ASSIGNED,       // Assigned to driver
    ACCEPTED,       // Driver accepted
    REJECTED,       // Driver rejected
    IN_PROGRESS,    // Trip started
    COMPLETED,      // Trip completed
    CANCELLED       // Trip cancelled
}

/**
 * Location - Geographic location with address
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null
)

/**
 * Trip Tracking - Real-time location during trip
 */
data class TripTracking(
    val tripId: String,
    val driverId: String,
    val currentLocation: Location,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Float = 0f, // km/h
    val heading: Float = 0f // degrees
)

/**
 * Trip History - Summary for completed trips
 */
data class TripHistory(
    val tripId: String,
    val date: Long,
    val vehicleNumber: String,
    val driverName: String,
    val route: String,
    val distance: Double,
    val duration: Long,
    val fare: Double,
    val status: TripStatus
)
