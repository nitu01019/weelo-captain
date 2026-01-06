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
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*

/**
 * Driver Earnings Screen - PRD-03 Compliant
 * Detailed earnings breakdown with trip-wise history
 */
@Composable
fun DriverEarningsScreen(
    driverId: String,
    onNavigateBack: () -> Unit
) {
    var selectedPeriod by remember { mutableStateOf("Month") }
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(
            title = "Earnings",
            onBackClick = onNavigateBack,
            actions = {
                IconButton(onClick = { /* TODO: Download */ }) {
                    Icon(Icons.Default.Download, "Download")
                }
            }
        )
        
        Column(Modifier.fillMaxSize()) {
            // Period Selector
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPeriod == "Today",
                    onClick = { selectedPeriod = "Today" },
                    label = { Text("Today") }
                )
                FilterChip(
                    selected = selectedPeriod == "Week",
                    onClick = { selectedPeriod = "Week" },
                    label = { Text("Week") }
                )
                FilterChip(
                    selected = selectedPeriod == "Month",
                    onClick = { selectedPeriod = "Month" },
                    label = { Text("Month") }
                )
            }
            
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
                            Text(
                                "Total Earnings ($selectedPeriod)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when (selectedPeriod) {
                                    "Today" -> "₹2,450"
                                    "Week" -> "₹14,800"
                                    else -> "₹45,000"
                                },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = Success
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                EarningsStat("Trips", if (selectedPeriod == "Today") "3" else if (selectedPeriod == "Week") "18" else "52")
                                EarningsStat("Avg/Trip", if (selectedPeriod == "Today") "₹817" else if (selectedPeriod == "Week") "₹822" else "₹865")
                            }
                        }
                    }
                }
                
                // Pending Payments
                item {
                    SectionCard("Pending Payments") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Pending Amount",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    "₹5,200",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Warning
                                )
                            }
                            TextButton(onClick = { /* TODO */ }) {
                                Text("Request")
                            }
                        }
                    }
                }
                
                // Trip-wise Breakdown
                item {
                    Text(
                        "Trip-wise Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(getSampleEarnings()) { earning ->
                    EarningCard(earning)
                }
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
                    text = earning.status,
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

fun getSampleEarnings() = listOf(
    EarningItem("TRIP-1234", "Mumbai → Pune", "Today, 10:30 AM", 850, "Pending"),
    EarningItem("TRIP-1233", "Nashik → Mumbai", "Yesterday", 1200, "Paid"),
    EarningItem("TRIP-1232", "Pune → Nashik", "2 days ago", 900, "Paid"),
    EarningItem("TRIP-1231", "Mumbai → Surat", "3 days ago", 1500, "Paid")
)
