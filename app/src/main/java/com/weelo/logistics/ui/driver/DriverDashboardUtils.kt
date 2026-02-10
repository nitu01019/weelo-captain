package com.weelo.logistics.ui.driver

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.weelo.logistics.data.model.DashNotificationType
import com.weelo.logistics.data.model.TripProgressStatus
import com.weelo.logistics.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * DriverDashboardUtils - Helper functions for driver dashboard
 * 
 * Extracted from DriverDashboardScreen.kt for better modularity.
 * These are pure utility functions with no side effects.
 */

/**
 * Get greeting based on current time of day
 */
fun getCurrentGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }
}

/**
 * Get human-readable status text for trip progress
 */
fun getStatusText(status: TripProgressStatus): String {
    return when (status) {
        TripProgressStatus.EN_ROUTE_TO_PICKUP -> "Heading to Pickup"
        TripProgressStatus.AT_PICKUP -> "At Pickup Location"
        TripProgressStatus.IN_TRANSIT -> "In Transit"
        TripProgressStatus.AT_DROP -> "At Drop Location"
        TripProgressStatus.COMPLETED -> "Completed"
    }
}

/**
 * Get icon for notification type
 */
fun getNotificationIcon(type: DashNotificationType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        DashNotificationType.NEW_TRIP_REQUEST -> Icons.Default.DirectionsCar
        DashNotificationType.TRIP_ASSIGNED -> Icons.Default.Assignment
        DashNotificationType.TRIP_CANCELLED -> Icons.Default.Cancel
        DashNotificationType.PAYMENT_RECEIVED -> Icons.Default.Payment
        DashNotificationType.RATING_RECEIVED -> Icons.Default.Star
        DashNotificationType.SYSTEM_ALERT -> Icons.Default.Warning
        DashNotificationType.PROMOTIONAL -> Icons.Default.CardGiftcard
    }
}

/**
 * Get color for notification type
 */
fun getNotificationColor(type: DashNotificationType): androidx.compose.ui.graphics.Color {
    return when (type) {
        DashNotificationType.NEW_TRIP_REQUEST -> Primary
        DashNotificationType.TRIP_ASSIGNED -> Success
        DashNotificationType.TRIP_CANCELLED -> Error
        DashNotificationType.PAYMENT_RECEIVED -> Success
        DashNotificationType.RATING_RECEIVED -> Warning
        DashNotificationType.SYSTEM_ALERT -> Error
        DashNotificationType.PROMOTIONAL -> Secondary
    }
}

/**
 * Format timestamp as readable date
 */
fun formatTripDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Format timestamp as relative time (e.g., "5 min ago")
 */
fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
        days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
        else -> formatTripDate(timestamp)
    }
}
