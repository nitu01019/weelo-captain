package com.weelo.logistics.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    @Suppress("UNUSED_PARAMETER") driverId: String,
    onNavigateBack: () -> Unit,
    viewModel: DriverPerformanceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val performanceState by viewModel.performanceState.collectAsState()

    // Load data on first composition
    LaunchedEffect(Unit) {
        viewModel.loadPerformance()
    }

    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = stringResource(R.string.performance_title), onBackClick = onNavigateBack)

        when (val state = performanceState) {
            is PerformanceState.Loading -> {
                SkeletonPerformanceLoading(Modifier.fillMaxSize())
            }
            is PerformanceState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ErrorOutline, null,
                            modifier = Modifier.size(48.dp), tint = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.error_loading_performance), color = TextSecondary)
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overall Rating Card
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(PrimaryLight)
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⭐", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    String.format("%.1f", data.rating),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(
                    stringResource(R.string.overall_rating),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.based_on_trips_format, data.totalRatings),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Trip Statistics
        SectionCard(stringResource(R.string.trip_statistics)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(stringResource(R.string.total_label), "${data.totalTrips}", Icons.Default.LocalShipping)
                StatItem(stringResource(R.string.completed_label), "${data.completedTrips}", Icons.Default.CheckCircle)
                StatItem(stringResource(R.string.cancelled_label), "${data.cancelledTrips}", Icons.Default.Cancel)
            }
        }

        // Performance Metrics
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

        // Monthly Trends (from real earnings breakdown)
        if (data.monthlyTrend.isNotEmpty()) {
            SectionCard(stringResource(R.string.monthly_trends)) {
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

        // Recent Feedback (from real completed trips)
        if (data.recentFeedback.isNotEmpty()) {
            SectionCard(stringResource(R.string.recent_trips_label)) {
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

// =============================================================================
// REUSABLE COMPOSABLES
// =============================================================================

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
            color = TextSecondary
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
        Text(label, style = MaterialTheme.typography.bodyMedium)
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
            modifier = Modifier.width(40.dp)
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                repeat(rating.coerceIn(0, 5)) {
                    Text("⭐", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                date,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            comment,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "- $customer",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
