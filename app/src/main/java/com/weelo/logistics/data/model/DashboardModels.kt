package com.weelo.logistics.data.model

/**
 * Dashboard Data Models
 * 
 * These models represent the driver dashboard data structure.
 * Backend API will return data in this format.
 * 
 * API Endpoint: GET /api/driver/dashboard?driverId={id}
 */

/**
 * Complete dashboard data for a driver
 * 
 * @property driverId Unique driver identifier
 * @property earnings Earnings summary (daily, weekly, monthly)
 * @property performance Performance metrics (rating, acceptance rate, etc.)
 * @property activeTrip Currently active trip, null if no active trip
 * @property recentTrips Last 10 completed trips
 * @property notifications Unread notifications
 * @property isOnline Driver's current availability status
 * @property lastUpdated Timestamp of last data refresh
 */
data class DashboardData(
    val driverId: String,
    val earnings: EarningsSummary,
    val performance: PerformanceMetrics,
    val activeTrip: ActiveTrip?,
    val recentTrips: List<CompletedTrip>,
    val notifications: List<DriverNotification>,
    val isOnline: Boolean,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Earnings summary for different time periods
 * 
 * @property today Today's earnings (in rupees)
 * @property todayTrips Number of trips completed today
 * @property weekly This week's earnings
 * @property weeklyTrips Trips completed this week
 * @property monthly This month's earnings
 * @property monthlyTrips Trips completed this month
 * @property pendingPayment Amount pending to be paid
 */
data class EarningsSummary(
    val today: Double,
    val todayTrips: Int,
    val weekly: Double,
    val weeklyTrips: Int,
    val monthly: Double,
    val monthlyTrips: Int,
    val pendingPayment: Double
)

/**
 * Driver performance metrics
 * 
 * @property rating Overall driver rating (0.0 - 5.0)
 * @property totalRatings Number of ratings received
 * @property acceptanceRate Percentage of trip requests accepted (0-100)
 * @property onTimeDeliveryRate Percentage of on-time deliveries (0-100)
 * @property completionRate Percentage of completed trips (0-100)
 * @property totalTrips Total trips completed (lifetime)
 * @property totalDistance Total distance traveled in km (lifetime)
 */
data class PerformanceMetrics(
    val rating: Double,
    val totalRatings: Int,
    val acceptanceRate: Double,
    val onTimeDeliveryRate: Double,
    val completionRate: Double,
    val totalTrips: Int,
    val totalDistance: Double
)

/**
 * Active trip currently in progress
 * 
 * @property tripId Unique trip identifier
 * @property customerName Customer/transporter name
 * @property pickupAddress Pickup location address
 * @property dropAddress Drop location address
 * @property vehicleType Type of vehicle assigned
 * @property estimatedEarning Expected earnings for this trip
 * @property startTime Trip start timestamp
 * @property estimatedDistance Distance in kilometers
 * @property estimatedDuration Duration in minutes
 * @property currentStatus Current trip status
 */
data class ActiveTrip(
    val tripId: String,
    val customerName: String,
    val pickupAddress: String,
    val dropAddress: String,
    val vehicleType: String,
    val estimatedEarning: Double,
    val startTime: Long,
    val estimatedDistance: Double,
    val estimatedDuration: Int,
    val currentStatus: TripProgressStatus
)

/**
 * Trip progress status for active trips
 */
enum class TripProgressStatus {
    EN_ROUTE_TO_PICKUP,    // Heading to pickup location
    AT_PICKUP,              // Arrived at pickup, loading
    IN_TRANSIT,             // Goods loaded, heading to drop
    AT_DROP,                // Arrived at drop, unloading
    COMPLETED               // Trip completed
}

/**
 * Completed trip record
 * 
 * @property tripId Unique trip identifier
 * @property customerName Customer/transporter name
 * @property pickupAddress Pickup location
 * @property dropAddress Drop location
 * @property vehicleType Vehicle type used
 * @property earnings Amount earned from this trip
 * @property distance Distance traveled in km
 * @property duration Trip duration in minutes
 * @property completedAt Completion timestamp
 * @property rating Rating received from customer (null if not rated)
 */
data class CompletedTrip(
    val tripId: String,
    val customerName: String,
    val pickupAddress: String,
    val dropAddress: String,
    val vehicleType: String,
    val earnings: Double,
    val distance: Double,
    val duration: Int,
    val completedAt: Long,
    val rating: Double?
)

/**
 * Driver notification
 * 
 * @property id Unique notification identifier
 * @property type Notification type
 * @property title Notification title
 * @property message Notification message
 * @property timestamp When notification was created
 * @property isRead Whether notification has been read
 * @property actionUrl Optional deep link for action
 */
data class DriverNotification(
    val id: String,
    val type: DashNotificationType,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val actionUrl: String? = null
)

/**
 * Dashboard Notification types
 */
enum class DashNotificationType {
    NEW_TRIP_REQUEST,       // New trip available
    TRIP_ASSIGNED,          // Trip has been assigned to driver
    TRIP_CANCELLED,         // Trip was cancelled
    PAYMENT_RECEIVED,       // Payment credited
    RATING_RECEIVED,        // New rating from customer
    SYSTEM_ALERT,           // System notification
    PROMOTIONAL             // Promotional/marketing message
}

/**
 * Driver availability status for dashboard
 */
enum class DriverAvailabilityStatus {
    ONLINE,     // Available for trips
    BUSY,       // On an active trip
    OFFLINE     // Not available
}

// =============================================================================
// BACKEND API ENDPOINTS - Implementation Guide for Backend Developer
// =============================================================================

/**
 * API ENDPOINT DOCUMENTATION FOR BACKEND TEAM
 * 
 * 1. GET /api/v1/driver/dashboard
 *    Query Params: driverId (String)
 *    Response: DashboardData
 *    Description: Returns complete dashboard data including earnings, performance, active trip
 * 
 * 2. POST /api/v1/driver/status
 *    Body: { "driverId": "string", "status": "ONLINE|BUSY|OFFLINE" }
 *    Response: { "success": true, "status": "ONLINE" }
 *    Description: Update driver's availability status
 * 
 * 3. GET /api/v1/driver/earnings
 *    Query Params: driverId (String), period (daily|weekly|monthly)
 *    Response: EarningsSummary
 *    Description: Get earnings for specific period
 * 
 * 4. GET /api/v1/driver/trips/active
 *    Query Params: driverId (String)
 *    Response: ActiveTrip?
 *    Description: Get currently active trip, null if no active trip
 * 
 * 5. GET /api/v1/driver/trips/recent
 *    Query Params: driverId (String), limit (Int, default: 10)
 *    Response: List<CompletedTrip>
 *    Description: Get recent completed trips
 * 
 * 6. GET /api/v1/driver/notifications
 *    Query Params: driverId (String), unreadOnly (Boolean)
 *    Response: List<DriverNotification>
 *    Description: Get driver notifications
 * 
 * 7. PUT /api/v1/driver/notifications/{notificationId}/read
 *    Response: { "success": true }
 *    Description: Mark notification as read
 * 
 * 8. GET /api/v1/driver/performance
 *    Query Params: driverId (String)
 *    Response: PerformanceMetrics
 *    Description: Get driver performance metrics
 */
