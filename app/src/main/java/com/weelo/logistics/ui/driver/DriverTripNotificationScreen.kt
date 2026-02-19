package com.weelo.logistics.ui.driver

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.repository.AssignmentRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * DRIVER TRIP NOTIFICATION SCREEN
 * ================================
 * Shows all pending trip notifications for the driver.
 * When transporter assigns a trip, notification appears here with sound/vibration.
 * 
 * FLOW:
 * 1. Transporter assigns driver → Push notification sent
 * 2. Notification appears on this screen (with alert badge)
 * 3. Driver clicks to see full details
 * 4. Driver accepts or declines
 * 
 * FOR BACKEND DEVELOPER:
 * - Listen for push notifications via FCM/OneSignal
 * - Play notification sound when new assignment arrives
 * - Trigger device vibration
 * - Show badge count on app icon and notification icon
 * - Auto-refresh list when new notification arrives
 * - Handle notification expiry (e.g., 5 minutes timeout)
 */
@Composable
fun DriverTripNotificationScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTripDetails: (String) -> Unit  // notificationId
) {
    var notifications by remember { mutableStateOf<List<DriverTripNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasNewNotification by remember { mutableStateOf(false) }

    // Load pending assignments as notifications from real API
    LaunchedEffect(driverId) {
        try {
            val token = RetrofitClient.getAccessToken()
            if (token != null) {
                val assignmentResponse = RetrofitClient.assignmentApi.getDriverAssignments("Bearer $token")
                if (assignmentResponse.isSuccessful) {
                    val assignmentData = assignmentResponse.body()?.data?.assignments
                    // Map assignments to notification format
                    if (assignmentData != null) {
                        notifications = assignmentData.map { a ->
                            DriverTripNotification(
                                notificationId = a.id,
                                assignmentId = a.id,
                                driverId = a.driverId,
                                pickupAddress = a.pickupAddress ?: "Pickup",
                                dropAddress = a.dropAddress ?: "Drop",
                                distance = a.distanceKm ?: 0.0,
                                estimatedDuration = ((a.distanceKm ?: 0.0) / 30.0 * 60).toLong(),
                                fare = a.pricePerTruck ?: 0.0,
                                goodsType = a.goodsType ?: a.vehicleType ?: "General",
                                status = when (a.status) {
                                    "driver_accepted" -> NotificationStatus.ACCEPTED
                                    "driver_declined" -> NotificationStatus.DECLINED
                                    "expired" -> NotificationStatus.EXPIRED
                                    else -> NotificationStatus.PENDING_RESPONSE
                                }
                            )
                        }
                        Timber.d("TripNotifications: Loaded ${notifications.size} assignments for driver")
                    }
                } else {
                    Timber.w("TripNotifications: API error ${assignmentResponse.code()}")
                }
            }
        } catch (e: Exception) {
            Timber.e("TripNotifications: Failed to load: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // Poll for new assignments every 10 seconds (real-time via WebSocket when available)
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            try {
                val token = RetrofitClient.getAccessToken()
                if (token != null) {
                    val response = RetrofitClient.assignmentApi.getDriverAssignments("Bearer $token")
                    if (response.isSuccessful) {
                        val newData = response.body()?.data?.assignments
                        if (newData != null && newData.size != notifications.size) {
                            // New assignments arrived — update list and flash badge
                            hasNewNotification = true
                            delay(2000)
                            hasNewNotification = false
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w("TripNotifications: Poll failed: ${e.message}")
            }
        }
    }
    
    val pendingNotifications = notifications.filter { it.status == NotificationStatus.PENDING_RESPONSE }
    val readNotifications = notifications.filter { it.status != NotificationStatus.PENDING_RESPONSE }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // Top Bar with Badge
        PrimaryTopBar(
            title = "Trip Notifications",
            onBackClick = onNavigateBack,
            actions = {
                Box {
                    IconButton(onClick = { /* Mark all as read */ }) {
                        Icon(Icons.Default.DoneAll, "Mark all read", tint = White)
                    }
                    if (pendingNotifications.isNotEmpty()) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 8.dp),
                            containerColor = Error
                        ) {
                            Text(pendingNotifications.size.toString())
                        }
                    }
                }
            }
        )
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (notifications.isEmpty()) {
            // Empty State
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = TextDisabled
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No notifications yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Trip assignments will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDisabled
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // New Notifications Section
                if (pendingNotifications.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Pending Trips (${pendingNotifications.size})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            // Pulsing indicator for new notifications
                            if (hasNewNotification) {
                                PulsingDot()
                            }
                        }
                    }
                    
                    // OPTIMIZATION: Add keys to prevent unnecessary recompositions
                    items(
                        items = pendingNotifications,
                        key = { it.notificationId }
                    ) { notification ->
                        TripNotificationCard(
                            notification = notification,
                            onClick = { onNavigateToTripDetails(notification.notificationId) }
                        )
                    }
                    
                    item { Spacer(Modifier.height(8.dp)) }
                }
                
                // Read Notifications Section
                if (readNotifications.isNotEmpty()) {
                    item {
                        Text(
                            "Earlier",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                    
                    // OPTIMIZATION: Add keys to prevent unnecessary recompositions
                    items(
                        items = readNotifications,
                        key = { it.notificationId }
                    ) { notification ->
                        TripNotificationCard(
                            notification = notification,
                            onClick = { onNavigateToTripDetails(notification.notificationId) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * TRIP NOTIFICATION CARD
 * Shows individual notification with trip preview
 */
@Composable
fun TripNotificationCard(
    notification: DriverTripNotification,
    onClick: () -> Unit
) {
    val isPending = notification.status == NotificationStatus.PENDING_RESPONSE
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(if (isPending) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (notification.status) {
                NotificationStatus.PENDING_RESPONSE -> Warning.copy(alpha = 0.1f)
                NotificationStatus.ACCEPTED -> Success.copy(alpha = 0.05f)
                NotificationStatus.DECLINED -> Error.copy(alpha = 0.05f)
                else -> White
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Status + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when (notification.status) {
                                    NotificationStatus.PENDING_RESPONSE -> Warning.copy(alpha = 0.2f)
                                    NotificationStatus.ACCEPTED -> Success.copy(alpha = 0.2f)
                                    NotificationStatus.DECLINED -> Error.copy(alpha = 0.2f)
                                    else -> Surface
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (notification.status) {
                                NotificationStatus.PENDING_RESPONSE -> Icons.Default.NotificationImportant
                                NotificationStatus.ACCEPTED -> Icons.Default.CheckCircle
                                NotificationStatus.DECLINED -> Icons.Default.Cancel
                                else -> Icons.Default.Notifications
                            },
                            null,
                            tint = when (notification.status) {
                                NotificationStatus.PENDING_RESPONSE -> Warning
                                NotificationStatus.ACCEPTED -> Success
                                NotificationStatus.DECLINED -> Error
                                else -> TextDisabled
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            "New Trip Assignment",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            getTimeAgo(notification.receivedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                // Status Badge
                if (isPending) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Error
                    ) {
                        Text(
                            "RESPOND",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            
            // Trip Details
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    // Pickup
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.TripOrigin,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = Success
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "PICKUP",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                notification.pickupAddress,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Drop
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = Error
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "DROP",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                notification.dropAddress,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Trip Info Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TripInfoItem(
                    icon = Icons.Default.Route,
                    label = "Distance",
                    value = "${notification.distance} km"
                )
                TripInfoItem(
                    icon = Icons.Default.Schedule,
                    label = "Duration",
                    value = "${notification.estimatedDuration} min"
                )
                TripInfoItem(
                    icon = Icons.Default.Inventory,
                    label = "Goods",
                    value = notification.goodsType
                )
            }
            
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            
            // Fare + Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Earnings",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        "₹${String.format("%.0f", notification.fare)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Success
                    )
                }
                
                if (isPending) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("View & Respond")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * TRIP INFO ITEM - Small info display
 */
@Composable
fun TripInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = Primary)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * PULSING DOT - Animated indicator for new notifications
 */
@Composable
fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Error)
    )
}

/**
 * HELPER: Convert timestamp to "time ago" format
 */
fun getTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
        else -> "$days day${if (days > 1) "s" else ""} ago"
    }
}
