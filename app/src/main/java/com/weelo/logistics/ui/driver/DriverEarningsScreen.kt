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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.error_loading_earnings), color = TextSecondary)
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.refresh() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
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
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Total Earnings Card
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(Success.copy(alpha = 0.1f))
            ) {
                Column(Modifier.padding(24.dp)) {
                    // Use localized period label (not raw API key) in format string
                    val localizedPeriod = when (selectedPeriod) {
                        "Today" -> stringResource(R.string.period_today)
                        "Week" -> stringResource(R.string.period_week)
                        "Month" -> stringResource(R.string.period_month)
                        else -> selectedPeriod
                    }
                    Text(
                        stringResource(R.string.total_earnings_format, localizedPeriod),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "₹${String.format("%,.0f", totalEarnings)}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Success
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        EarningsStat(stringResource(R.string.trips_label), "$tripCount")
                        EarningsStat(stringResource(R.string.avg_per_trip), "₹${String.format("%,.0f", avgPerTrip)}")
                    }
                }
            }
        }
        
        // Pending Payments
        item {
            SectionCard(stringResource(R.string.pending_payments)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.pending_amount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            "₹${String.format("%,.0f", pendingAmount)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Warning
                        )
                    }
                    TextButton(onClick = { /* TODO */ }) {
                        Text(stringResource(R.string.request_payment))
                    }
                }
            }
        }
        
        // Trip-wise Breakdown
        if (trips.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.trip_wise_breakdown),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

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

@Composable
fun EarningsStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EarningCard(earning: EarningItem) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
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
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    earning.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "₹${earning.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Success
                )
                Spacer(Modifier.height(4.dp))
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
