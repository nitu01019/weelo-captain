package com.weelo.logistics.data.model

/**
 * Driver Data Model
 * 
 * Represents a driver in the system with simplified information for transporter's view.
 * 
 * @property id Unique driver identifier
 * @property name Driver's full name
 * @property mobileNumber Driver's phone number (10 digits)
 * @property licenseNumber Driver's license number (validated)
 * @property transporterId ID of the transporter this driver is registered with
 * @property assignedVehicleId ID of vehicle currently assigned (null if unassigned)
 * @property isAvailable Availability status for accepting trips
 * @property rating Average driver rating from completed trips (0-5 scale)
 * @property totalTrips Total number of trips completed
 * @property profileImageUrl Optional URL to driver's profile picture
 * @property status Current driver account status
 * @property createdAt Account creation timestamp (milliseconds since epoch)
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
 * Driver Status Enum
 * 
 * Defines the current status of a driver's account.
 */
enum class DriverStatus {
    /** Active and available to take trips */
    ACTIVE,
    
    /** Currently on an active trip */
    ON_TRIP,
    
    /** Not available for trips (offline) */
    INACTIVE,
    
    /** Account suspended by admin */
    SUSPENDED
}

/**
 * Driver Earnings Data Model
 * 
 * Tracks driver's earnings across different time periods.
 * 
 * @property driverId Driver's unique identifier
 * @property todayEarnings Total earnings for today (in rupees)
 * @property weekEarnings Total earnings for current week
 * @property monthEarnings Total earnings for current month
 * @property totalEarnings Total lifetime earnings
 * @property todayTrips Number of trips completed today
 * @property totalTrips Total number of trips completed (all time)
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
 * Driver Performance Metrics
 * 
 * Comprehensive performance analytics for a driver.
 * 
 * @property driverId Driver's unique identifier
 * @property rating Average customer rating (0-5 scale)
 * @property totalTrips Total number of trips assigned
 * @property completedTrips Number of successfully completed trips
 * @property cancelledTrips Number of cancelled trips
 * @property avgTripTime Average trip completion time (in minutes)
 * @property totalDistance Total distance covered across all trips (in kilometers)
 * @property onTimeDeliveryRate Percentage of on-time deliveries (0-100)
 */
data class DriverPerformance(
    val driverId: String,
    val rating: Float,
    val totalTrips: Int,
    val completedTrips: Int,
    val cancelledTrips: Int,
    val avgTripTime: Long,
    val totalDistance: Double,
    val onTimeDeliveryRate: Float
)
