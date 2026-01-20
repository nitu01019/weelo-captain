package com.weelo.logistics.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.Notification
import com.weelo.logistics.data.model.NotificationType
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Driver Notifications Screen - PRD-04 Compliant
 * Shows all notifications with actions
 */
@Composable
fun DriverNotificationsScreen(@Suppress("UNUSED_PARAMETER") 
    driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTrip: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val notifications = remember { getSampleNotifications() }
    
    val filteredNotifications = notifications.filter { notification ->
        when (selectedFilter) {
            "Trips" -> notification.type in listOf(
                NotificationType.TRIP_ASSIGNED,
                NotificationType.TRIP_STARTED,
                NotificationType.TRIP_COMPLETED
            )
            "Payments" -> notification.type == NotificationType.PAYMENT_RECEIVED
            "Unread" -> !notification.isRead
            else -> true
        }
    }
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(
            title = "Notifications",
            onBackClick = onNavigateBack,
            actions = {
                IconButton(onClick = { /* TODO: Mark all as read */ }) {
                    Icon(Icons.Default.DoneAll, "Mark all read")
                }
            }
        )
        
        // Filter Chips
        Row(
            Modifier
                .fillMaxWidth()
                .background(White)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "All",
                onClick = { selectedFilter = "All" },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedFilter == "Unread",
                onClick = { selectedFilter = "Unread" },
                label = { Text("Unread") }
            )
            FilterChip(
                selected = selectedFilter == "Trips",
                onClick = { selectedFilter = "Trips" },
                label = { Text("Trips") }
            )
            FilterChip(
                selected = selectedFilter == "Payments",
                onClick = { selectedFilter = "Payments" },
                label = { Text("Payments") }
            )
        }
        
        if (filteredNotifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.Notifications, null, Modifier.size(64.dp), tint = TextDisabled)
                    Spacer(Modifier.height(16.dp))
                    Text("No notifications", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // OPTIMIZATION: Add keys to prevent unnecessary recompositions
                items(
                    items = filteredNotifications,
                    key = { it.id }
                ) { notification ->
                    NotificationCard(
                        notification = notification,
                        onClick = {
                            if (notification.type in listOf(
                                    NotificationType.TRIP_ASSIGNED,
                                    NotificationType.TRIP_STARTED
                                )
                            ) {
                                notification.data?.get("tripId")?.let { onNavigateToTrip(it) }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: Notification,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) White else PrimaryLight
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon based on type
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        color = when (notification.type) {
                            NotificationType.TRIP_ASSIGNED -> Secondary.copy(alpha = 0.1f)
                            NotificationType.TRIP_COMPLETED -> Success.copy(alpha = 0.1f)
                            NotificationType.PAYMENT_RECEIVED -> Success.copy(alpha = 0.1f)
                            else -> Surface
                        },
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (notification.type) {
                        NotificationType.TRIP_ASSIGNED -> Icons.Default.LocalShipping
                        NotificationType.TRIP_STARTED -> Icons.Default.PlayArrow
                        NotificationType.TRIP_COMPLETED -> Icons.Default.CheckCircle
                        NotificationType.PAYMENT_RECEIVED -> Icons.Default.Payment
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (notification.type) {
                        NotificationType.TRIP_ASSIGNED -> Secondary
                        NotificationType.TRIP_COMPLETED -> Success
                        NotificationType.PAYMENT_RECEIVED -> Success
                        else -> TextSecondary
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    notification.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    notification.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    formatNotificationTime(notification.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            if (!notification.isRead) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(Primary, androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
    }
}

fun formatNotificationTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        diff < 604800000 -> "${diff / 86400000}d ago"
        else -> {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

fun getSampleNotifications() = listOf(
    Notification(
        id = "n1",
        userId = "d1",
        title = "New Trip Assigned",
        message = "You have been assigned a new trip from Mumbai to Pune",
        type = NotificationType.TRIP_ASSIGNED,
        timestamp = System.currentTimeMillis() - 300000, // 5 min ago
        isRead = false,
        data = mapOf("tripId" to "trip1")
    ),
    Notification(
        id = "n2",
        userId = "d1",
        title = "Payment Received",
        message = "₹2,450 has been credited to your account",
        type = NotificationType.PAYMENT_RECEIVED,
        timestamp = System.currentTimeMillis() - 3600000, // 1 hour ago
        isRead = false
    ),
    Notification(
        id = "n3",
        userId = "d1",
        title = "Trip Completed",
        message = "Your trip to Nashik has been completed successfully",
        type = NotificationType.TRIP_COMPLETED,
        timestamp = System.currentTimeMillis() - 7200000, // 2 hours ago
        isRead = true
    ),
    Notification(
        id = "n4",
        userId = "d1",
        title = "Document Expiry Alert",
        message = "Your driving license expires in 30 days. Please renew.",
        type = NotificationType.GENERAL,
        timestamp = System.currentTimeMillis() - 86400000, // 1 day ago
        isRead = true
    ),
    Notification(
        id = "n5",
        userId = "d1",
        title = "Trip Started",
        message = "Your trip to Surat has started",
        type = NotificationType.TRIP_STARTED,
        timestamp = System.currentTimeMillis() - 172800000, // 2 days ago
        isRead = true,
        data = mapOf("tripId" to "trip2")
    )
)
