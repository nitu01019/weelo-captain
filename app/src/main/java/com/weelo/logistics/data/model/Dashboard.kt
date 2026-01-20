package com.weelo.logistics.data.model

/**
 * Dashboard Data Models - Used by MockDataRepository
 * TODO: Remove when all screens are connected to real backend API
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

data class DriverDashboard(
    val isAvailable: Boolean = false,
    val activeTrip: Trip? = null,
    val todayTrips: Int = 0,
    val todayEarnings: Double = 0.0,
    val todayDistance: Double = 0.0,
    val weekEarnings: Double = 0.0,
    val monthEarnings: Double = 0.0,
    val rating: Double = 0.0,
    val acceptanceRate: Double = 0.0,
    val totalTrips: Int = 0,
    val pendingTrips: Int = 0
)

/**
 * Notification model for DriverNotificationsScreen
 * TODO: Replace with API model when backend is ready
 */
data class Notification(
    val id: String,
    val userId: String = "",
    val type: NotificationType,
    val title: String,
    val message: String,
    val data: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

enum class NotificationType {
    TRIP_REQUEST,
    TRIP_UPDATE,
    TRIP_ASSIGNED,
    TRIP_STARTED,
    TRIP_COMPLETED,
    PAYMENT,
    PAYMENT_RECEIVED,
    SYSTEM,
    GENERAL
}
