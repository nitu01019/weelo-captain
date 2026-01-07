package com.weelo.logistics.data.model

/**
 * Transporter Dashboard Data Model
 * 
 * Contains aggregated statistics and data for the transporter dashboard screen.
 * 
 * @property totalVehicles Total number of vehicles owned by transporter
 * @property activeVehicles Number of vehicles currently available/in-transit
 * @property totalDrivers Total number of drivers registered
 * @property activeDrivers Number of drivers currently available/on-trip
 * @property activeTrips Number of trips currently in progress
 * @property todayRevenue Total revenue earned today (in rupees)
 * @property todayTrips Number of trips completed today
 * @property completedTrips Total completed trips (all time)
 * @property recentTrips List of recent trips for quick view
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
 * Driver Dashboard Data Model
 * 
 * Contains performance metrics and current status for the driver dashboard screen.
 * 
 * @property isAvailable Driver availability status (online/offline)
 * @property activeTrip Currently active trip (null if no active trip)
 * @property todayTrips Number of trips completed today
 * @property todayEarnings Total earnings for today (in rupees)
 * @property todayDistance Total distance covered today (in kilometers)
 * @property weekEarnings Total earnings for current week
 * @property monthEarnings Total earnings for current month
 * @property rating Driver's average rating (0-5 scale)
 * @property totalTrips Total trips completed (all time)
 * @property pendingTrips List of pending trip assignments
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
 * Notification Data Model
 * 
 * Represents a push notification or in-app notification for users.
 * 
 * @property id Unique notification identifier
 * @property userId Target user's ID
 * @property title Notification title/headline
 * @property message Notification body/description
 * @property type Type of notification (see NotificationType enum)
 * @property timestamp Creation time in milliseconds since epoch
 * @property isRead Read status flag
 * @property data Optional key-value pairs for additional context (e.g., tripId, driverId)
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
 * Notification Type Enum
 * 
 * Defines the type of notification for categorization and handling.
 */
enum class NotificationType {
    /** Trip has been assigned to a driver */
    TRIP_ASSIGNED,
    
    /** Driver has accepted a trip assignment */
    TRIP_ACCEPTED,
    
    /** Driver has rejected a trip assignment */
    TRIP_REJECTED,
    
    /** Trip has started (driver began journey) */
    TRIP_STARTED,
    
    /** Trip has been completed successfully */
    TRIP_COMPLETED,
    
    /** Trip was cancelled by user or driver */
    TRIP_CANCELLED,
    
    /** New driver has registered with the transporter */
    DRIVER_REGISTERED,
    
    /** New vehicle has been added to fleet */
    VEHICLE_ADDED,
    
    /** Payment has been received for a trip */
    PAYMENT_RECEIVED,
    
    /** General notification (announcements, updates) */
    GENERAL
}
