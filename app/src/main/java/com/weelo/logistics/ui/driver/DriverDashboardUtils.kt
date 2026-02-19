package com.weelo.logistics.ui.driver

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.weelo.logistics.R
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
 * 
 * Functions that return user-visible strings take a Context parameter
 * so that they can resolve localized string resources.
 */

/**
 * Get greeting based on current time of day
 */
fun getCurrentGreeting(context: Context): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 0..11 -> context.getString(R.string.good_morning)
        in 12..16 -> context.getString(R.string.good_afternoon)
        else -> context.getString(R.string.good_evening)
    }
}

/**
 * Get human-readable status text for trip progress
 */
fun getStatusText(context: Context, status: TripProgressStatus): String {
    return when (status) {
        TripProgressStatus.EN_ROUTE_TO_PICKUP -> context.getString(R.string.heading_to_pickup)
        TripProgressStatus.AT_PICKUP -> context.getString(R.string.at_pickup_location)
        TripProgressStatus.IN_TRANSIT -> context.getString(R.string.in_transit_status)
        TripProgressStatus.AT_DROP -> context.getString(R.string.at_drop_location)
        TripProgressStatus.COMPLETED -> context.getString(R.string.completed_status)
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
fun formatTimeAgo(context: Context, timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        minutes < 1 -> context.getString(R.string.just_now)
        minutes < 60 -> context.getString(R.string.min_ago_format, minutes)
        hours < 24 -> if (hours > 1) context.getString(R.string.hours_ago_format, hours) else context.getString(R.string.hour_ago_format, hours)
        days < 7 -> if (days > 1) context.getString(R.string.days_ago_format, days) else context.getString(R.string.day_ago_format, days)
        else -> formatTripDate(timestamp)
    }
}
