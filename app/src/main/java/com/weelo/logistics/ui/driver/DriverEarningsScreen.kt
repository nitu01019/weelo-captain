package com.weelo.logistics.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
 * Driver Earnings Screen - PRD-03 Compliant
 * Detailed earnings breakdown with trip-wise history
 */
/**
 * =============================================================================
 * DRIVER EARNINGS SCREEN — Real API Data (Phase 5)
 * =============================================================================
 *
 * Displays earnings from DriverEarningsViewModel (real API data).
 * UI is unchanged from original — only data source replaced.
 *
 * SCALABILITY: ViewModel caches per period — tab switching is instant.
 * MODULARITY: Screen only observes StateFlow — no API knowledge.
 * EASY UNDERSTANDING: Same UI, just real data instead of hardcoded.
 * SAME CODING STANDARD: Composable + ViewModel + StateFlow pattern.
 * =============================================================================
 */
@Composable
fun DriverEarningsScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    viewModel: DriverEarningsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val earningsState by viewModel.earningsState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val requestedDriverId = remember(driverId) { driverId.takeIf { it.isNotBlank() } }

    // Load data on first composition
    LaunchedEffect(selectedPeriod, requestedDriverId) {
        viewModel.loadEarnings(selectedPeriod, requestedDriverId)
    }
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(
            title = stringResource(R.string.earnings_title),
            onBackClick = onNavigateBack,
            actions = {
                IconButton(onClick = { /* TODO: Download */ }) {
                    Icon(Icons.Default.Download, stringResource(R.string.cd_download))
                }
            }
        )
        
        Column(Modifier.fillMaxSize()) {
            // Period Selector — keys are API values ("Today", "Week", "Month"), labels are localized
            val periodLabels = mapOf(
                "Today" to stringResource(R.string.period_today),
                "Week" to stringResource(R.string.period_week),
                "Month" to stringResource(R.string.period_month)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                periodLabels.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedPeriod == key,
                        onClick = { viewModel.loadEarnings(key, requestedDriverId) },
                        label = { Text(label) }
                    )
                }
            }

            when (val state = earningsState) {
                is EarningsState.Loading -> {
                    SkeletonEarningsLoading(Modifier.fillMaxSize())
                }
                is EarningsState.Error -> {
                    RetryErrorStatePanel(
                        title = stringResource(R.string.ui_retry_error_title),
                        message = stringResource(R.string.error_loading_earnings),
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                        illustrationRes = EmptyStateArtwork.EARNINGS_NO_TRIPS.drawableRes
                    )
                }
                is EarningsState.Success -> {
                    val data = state.data
                    EarningsContent(
                        selectedPeriod = selectedPeriod,
                        totalEarnings = data.totalEarnings,
                        tripCount = data.tripCount,
                        avgPerTrip = data.avgPerTrip,
                        pendingAmount = data.pendingAmount,
                        trips = data.trips
                    )
                }
            }
        }
    }
}

@Composable
private fun EarningsContent(
    selectedPeriod: String,
    totalEarnings: Double,
    tripCount: Int,
    avgPerTrip: Double,
    pendingAmount: Double,
    trips: List<EarningItem>
) {
    val screenConfig = rememberScreenConfig()
    val contentMaxWidth = if (screenConfig.isLandscape) 760.dp else 620.dp
    val localizedPeriod = when (selectedPeriod) {
        "Today" -> stringResource(R.string.period_today)
        "Week" -> stringResource(R.string.period_week)
        "Month" -> stringResource(R.string.period_month)
        else -> selectedPeriod
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = contentMaxWidth),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MediaHeaderCard(
                    title = "₹${String.format("%,.0f", totalEarnings)}",
                    subtitle = stringResource(R.string.ui_card_subtitle_earnings_overview),
                    mediaSpec = CardMediaSpec(
                        artwork = CardArtwork.DRIVER_EARNINGS,
                        headerHeight = if (screenConfig.isLandscape) 108.dp else 124.dp
                    ),
                    trailingHeaderContent = {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = White.copy(alpha = 0.94f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = Success, modifier = Modifier.size(16.dp))
                                Text(localizedPeriod, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                        }
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.total_earnings_format, localizedPeriod),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            EarningsStat(
                                label = stringResource(R.string.trips_label),
                                value = "$tripCount",
                                modifier = Modifier.weight(1f)
                            )
                            EarningsStat(
                                label = stringResource(R.string.avg_per_trip),
                                value = "₹${String.format("%,.0f", avgPerTrip)}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                )
            }

            item {
                SectionCard(stringResource(R.string.pending_payments)) {
                    if (pendingAmount <= 0.0) {
                        InlineSectionEmptyState(
                            spec = allCaughtUpEmptyStateSpec(
                                artwork = EmptyStateArtwork.EARNINGS_PENDING_CAUGHT_UP,
                                title = stringResource(R.string.empty_title_earnings_pending_caught_up),
                                subtitle = stringResource(R.string.empty_subtitle_earnings_pending_caught_up)
                            )
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.pending_amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    "₹${String.format("%,.0f", pendingAmount)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Warning,
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = { /* TODO */ }) {
                                Text(stringResource(R.string.request_payment))
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.trip_wise_breakdown),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (trips.isEmpty()) {
                item {
                    InlineSectionEmptyState(
                        spec = noActivityYetEmptyStateSpec(
                            artwork = EmptyStateArtwork.EARNINGS_NO_TRIPS,
                            title = stringResource(R.string.empty_title_earnings_no_trips),
                            subtitle = stringResource(R.string.empty_subtitle_earnings_no_trips)
                        )
                    )
                }
            } else {
                items(
                    items = trips,
                    key = { it.tripId },
                    contentType = { "earning_card" }
                ) { earning ->
                    EarningCard(earning)
                }
            }
        }
    }
}

@Composable
fun EarningsStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = SurfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EarningCard(earning: EarningItem) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    earning.tripId,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    earning.route,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    earning.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "₹${earning.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Success
                )
                StatusChip(
                    text = when (earning.status) {
                        "Paid" -> stringResource(R.string.status_paid)
                        "Pending" -> stringResource(R.string.status_pending)
                        else -> earning.status
                    },
                    status = if (earning.status == "Paid") ChipStatus.COMPLETED else ChipStatus.PENDING
                )
            }
        }
    }
}

data class EarningItem(
    val tripId: String,
    val route: String,
    val date: String,
    val amount: Int,
    val status: String
)

/** @deprecated Use DriverEarningsViewModel for real data. Kept for backward compatibility. */
// Sample data removed — all earnings now come from real API via DriverEarningsViewModel
