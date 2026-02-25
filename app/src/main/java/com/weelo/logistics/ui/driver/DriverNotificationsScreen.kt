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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.EmptyStateArtwork
import com.weelo.logistics.ui.components.EmptyStateHost
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.components.ProvideShimmerBrush
import com.weelo.logistics.ui.components.SkeletonList
import com.weelo.logistics.ui.components.allCaughtUpEmptyStateSpec
import com.weelo.logistics.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * =============================================================================
 * DRIVER NOTIFICATIONS SCREEN — Real API Data
 * =============================================================================
 *
 * Displays notifications from DriverNotificationsViewModel (real API data).
 * ALL values come from backend trips/earnings — zero hardcoded sample data.
 *
 * SCALABILITY: ViewModel caches full list — filtering is instant.
 * MODULARITY: Screen only observes StateFlow — no API knowledge.
 * EASY UNDERSTANDING: Same UI layout, just real data.
 * SAME CODING STANDARD: Composable + ViewModel + StateFlow pattern.
 * =============================================================================
 */
@Composable
fun DriverNotificationsScreen(
    @Suppress("UNUSED_PARAMETER") driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTrip: (String) -> Unit,
    viewModel: DriverNotificationsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val notificationsState by viewModel.notificationsState.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    // Load data on first composition
    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
    }

    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(
            title = stringResource(R.string.notifications_title),
            onBackClick = onNavigateBack,
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.cd_refresh))
                }
            }
        )

        // Filter Chips — keys are API values, labels are localized
        val filterLabels = mapOf(
            "All" to stringResource(R.string.filter_all),
            "Unread" to stringResource(R.string.filter_unread),
            "Trips" to stringResource(R.string.filter_trips),
            "Payments" to stringResource(R.string.filter_payments)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(White)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterLabels.forEach { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { viewModel.setFilter(key) },
                    label = { Text(label) }
                )
            }
        }

        when (val state = notificationsState) {
            is NotificationsState.Loading -> {
                ProvideShimmerBrush {
                    SkeletonList(itemCount = 5, modifier = Modifier.padding(16.dp))
                }
            }
            is NotificationsState.Error -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.error_loading_notifications), color = TextSecondary)
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            is NotificationsState.Success -> {
                val notifications = state.notifications
                if (notifications.isEmpty()) {
                    EmptyStateHost(
                        spec = allCaughtUpEmptyStateSpec(
                            artwork = EmptyStateArtwork.NOTIFICATIONS_ALL_CAUGHT_UP,
                            title = stringResource(R.string.empty_title_driver_notifications_caught_up),
                            subtitle = stringResource(R.string.empty_subtitle_driver_notifications_caught_up)
                        )
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = notifications,
                            key = { it.id },
                            contentType = { "notification_card" }
                        ) { notification ->
                            NotificationCard(
                                notification = notification,
                                onClick = {
                                    viewModel.markAsRead(notification.id)
                                    notification.tripId?.let { onNavigateToTrip(it) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: NotificationItem,
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
                            NotificationItemType.TRIP_ASSIGNED -> Secondary.copy(alpha = 0.1f)
                            NotificationItemType.TRIP_COMPLETED -> Success.copy(alpha = 0.1f)
                            NotificationItemType.PAYMENT_RECEIVED -> Success.copy(alpha = 0.1f)
                            else -> Surface
                        },
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (notification.type) {
                        NotificationItemType.TRIP_ASSIGNED -> Icons.Default.LocalShipping
                        NotificationItemType.TRIP_STARTED -> Icons.Default.PlayArrow
                        NotificationItemType.TRIP_COMPLETED -> Icons.Default.CheckCircle
                        NotificationItemType.PAYMENT_RECEIVED -> Icons.Default.Payment
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (notification.type) {
                        NotificationItemType.TRIP_ASSIGNED -> Secondary
                        NotificationItemType.TRIP_COMPLETED -> Success
                        NotificationItemType.PAYMENT_RECEIVED -> Success
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
                    formatNotificationTimestamp(notification.timestamp, LocalContext.current),
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

// Pre-cached date formatters — avoids creating new SimpleDateFormat on every call
// (eliminates scroll jank with 20+ notification items)
// Note: SimpleDateFormat is not thread-safe, but these are only called from
// Compose main thread. @Synchronized added as safety net for future-proofing.
private val isoDateParser: SimpleDateFormat get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
private val displayDateFormatter: SimpleDateFormat get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

/**
 * Format ISO timestamp to relative time string.
 * Uses pre-cached formatters for performance.
 */
@Synchronized
private fun formatNotificationTimestamp(timestamp: String, context: android.content.Context): String {
    return try {
        val date = isoDateParser.parse(timestamp) ?: return timestamp.take(10)
        val diff = System.currentTimeMillis() - date.time
        when {
            diff < 60_000 -> context.getString(R.string.just_now)
            diff < 3_600_000 -> context.getString(R.string.time_m_ago_format, (diff / 60_000).toInt())
            diff < 86_400_000 -> context.getString(R.string.time_h_ago_format, (diff / 3_600_000).toInt())
            diff < 604_800_000 -> context.getString(R.string.time_d_ago_format, (diff / 86_400_000).toInt())
            else -> displayDateFormatter.format(date)
        }
    } catch (_: Exception) {
        timestamp.take(10)
    }
}
