package com.weelo.logistics.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*

/**
 * =============================================================================
 * DRIVER PERFORMANCE SCREEN — Real API Data (Phase 5)
 * =============================================================================
 *
 * Displays performance metrics from DriverPerformanceViewModel (real API data).
 * ALL values come from backend — zero hardcoded data.
 *
 * SCALABILITY: ViewModel caches data — back navigation shows instantly.
 * MODULARITY: Screen only observes StateFlow — no API knowledge.
 * EASY UNDERSTANDING: Same UI layout, just real data instead of hardcoded.
 * SAME CODING STANDARD: Composable + ViewModel + StateFlow pattern
 *   (identical to DriverEarningsScreen).
 * =============================================================================
 */
@Composable
fun DriverPerformanceScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    viewModel: DriverPerformanceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val performanceState by viewModel.performanceState.collectAsState()
    val requestedDriverId = remember(driverId) { driverId.takeIf { it.isNotBlank() } }

    // Load data on first composition
    LaunchedEffect(requestedDriverId) {
        viewModel.loadPerformance(requestedDriverId)
    }

    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = stringResource(R.string.performance_title), onBackClick = onNavigateBack)

        when (val state = performanceState) {
            is PerformanceState.Loading -> {
                SkeletonPerformanceLoading(Modifier.fillMaxSize())
            }
            is PerformanceState.Error -> {
                RetryErrorStatePanel(
                    title = stringResource(R.string.ui_retry_error_title),
                    message = stringResource(R.string.error_loading_performance),
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                    illustrationRes = EmptyStateArtwork.PERFORMANCE_NO_TRENDS.drawableRes
                )
            }
            is PerformanceState.Success -> {
                PerformanceContent(data = state.data)
            }
        }
    }
}

// =============================================================================
// CONTENT — All values from PerformanceData (real API)
// =============================================================================

@Composable
private fun PerformanceContent(data: PerformanceData) {
    val screenConfig = rememberScreenConfig()
    val contentMaxWidth = if (screenConfig.isLandscape) 760.dp else 620.dp

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = contentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroEntityCard(
                title = String.format("%.1f", data.rating),
                subtitle = stringResource(R.string.ui_card_subtitle_performance_overview),
                mediaSpec = CardMediaSpec(
                    artwork = CardArtwork.DRIVER_PERFORMANCE,
                    headerHeight = if (screenConfig.isLandscape) 108.dp else 124.dp
                ),
                leadingAvatar = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Primary.copy(alpha = 0.12f), shape = androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Star, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                    }
                },
                statusContent = {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = PrimaryLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Star, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.overall_rating),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                },
                metaContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PerformanceMetaTile(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.based_on_trips_format, data.totalRatings),
                            value = "${data.totalTrips}",
                            icon = Icons.Default.LocalShipping
                        )
                        PerformanceMetaTile(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.completion_rate),
                            value = "${String.format("%.0f", data.completionRate)}%",
                            icon = Icons.Default.CheckCircle
                        )
                    }
                }
            )

            SectionCard(stringResource(R.string.trip_statistics)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatItem(stringResource(R.string.total_label), "${data.totalTrips}", Icons.Default.LocalShipping)
                    StatItem(stringResource(R.string.completed_label), "${data.completedTrips}", Icons.Default.CheckCircle)
                    StatItem(stringResource(R.string.cancelled_label), "${data.cancelledTrips}", Icons.Default.Cancel)
                }
            }

            SectionCard(stringResource(R.string.performance_metrics)) {
                MetricRow(stringResource(R.string.on_time_delivery_rate), "${String.format("%.0f", data.onTimeDeliveryRate)}%", Success)
                Divider()
                MetricRow(
                    stringResource(R.string.acceptance_rate_label),
                    "${String.format("%.0f", data.acceptanceRate)}%",
                    if (data.acceptanceRate >= 80) Success else Warning
                )
                Divider()
                MetricRow(
                    stringResource(R.string.distance_covered),
                    "${String.format("%,.0f", data.totalDistanceKm)} km",
                    Secondary
                )
                Divider()
                MetricRow(
                    stringResource(R.string.completion_rate),
                    "${String.format("%.0f", data.completionRate)}%",
                    if (data.completionRate >= 90) Success else Warning
                )
            }

            SectionCard(stringResource(R.string.monthly_trends)) {
                if (data.monthlyTrend.isEmpty()) {
                    InlineSectionEmptyState(
                        spec = noActivityYetEmptyStateSpec(
                            artwork = EmptyStateArtwork.PERFORMANCE_NO_TRENDS,
                            title = stringResource(R.string.empty_title_performance_trends),
                            subtitle = stringResource(R.string.empty_subtitle_performance_trends)
                        )
                    )
                } else {
                    Text(
                        stringResource(R.string.last_months_format, data.monthlyTrend.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val maxTrips = data.monthlyTrend.maxOfOrNull { it.trips } ?: 1
                        data.monthlyTrend.forEach { monthData ->
                            MonthBar(
                                month = monthData.month,
                                trips = monthData.trips,
                                maxTrips = maxTrips,
                                color = if (monthData.trips >= maxTrips * 0.7) Success else Primary
                            )
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.recent_trips_label)) {
                if (data.recentFeedback.isEmpty()) {
                    InlineSectionEmptyState(
                        spec = noActivityYetEmptyStateSpec(
                            artwork = EmptyStateArtwork.PERFORMANCE_NO_FEEDBACK,
                            title = stringResource(R.string.empty_title_performance_feedback),
                            subtitle = stringResource(R.string.empty_subtitle_performance_feedback)
                        )
                    )
                } else {
                    data.recentFeedback.forEachIndexed { index, feedback ->
                        FeedbackItem(
                            rating = feedback.rating,
                            comment = feedback.comment,
                            customer = feedback.customer,
                            date = feedback.date
                        )
                        if (index < data.recentFeedback.lastIndex) {
                            Divider(Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// REUSABLE COMPOSABLES
// =============================================================================

@Composable
private fun PerformanceMetaTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = SurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(White, shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RowScope.StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MetricRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * MonthBar — scaled to max trips so bars are proportional.
 *
 * @param maxTrips The maximum trip count across all months (for proportional bar width).
 */
@Composable
fun MonthBar(
    month: String,
    trips: Int,
    maxTrips: Int,
    color: androidx.compose.ui.graphics.Color
) {
    val barFraction = if (maxTrips > 0) trips.toFloat() / maxTrips else 0f

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            month,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(56.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            Modifier
                .height(24.dp)
                .weight(1f)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(barFraction.coerceIn(0.02f, 1f))
                    .background(color, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "$trips",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(32.dp)
        )
    }
}

@Composable
fun FeedbackItem(rating: Int, comment: String, customer: String, date: String) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(rating.coerceIn(0, 5)) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Warning,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                date,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            comment,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "- $customer",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
