package com.weelo.logistics.ui.transporter

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.api.TripData
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.components.responsiveHorizontalPadding
import com.weelo.logistics.ui.theme.*
import timber.log.Timber

/**
 * =============================================================================
 * TRIP DETAILS SCREEN — Real API Data
 * =============================================================================
 *
 * Shows trip details from GET /api/v1/driver/trips.
 * ALL data from backend — zero MockDataRepository references.
 * =============================================================================
 */
@Composable
fun TripDetailsScreen(
    tripId: String,
    onNavigateBack: () -> Unit
) {
    var trip by remember { mutableStateOf<TripData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tripId) {
        try {
            val response = RetrofitClient.driverApi.getDriverTrips(limit = 50)
            if (!response.isSuccessful) {
                errorMessage = "Failed to load trip (${response.code()})"
                Timber.e("TripDetails: API error ${response.code()} ${response.message()}")
                return@LaunchedEffect
            }
            val trips = response.body()?.data?.trips ?: emptyList()
            trip = trips.find { it.id == tripId }
            if (trip == null) {
                errorMessage = "Trip not found"
                Timber.w("TripDetails: Trip not found for id=$tripId")
            }
        } catch (e: Exception) {
            Timber.e("TripDetails: Failed to load: ${e.message}")
            errorMessage = e.message ?: "Failed to load trip"
        } finally {
            isLoading = false
        }
    }
    
    // Responsive layout
    val horizontalPadding = responsiveHorizontalPadding()
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Trip Details", onBackClick = onNavigateBack)
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(errorMessage ?: "Failed to load trip", color = Error)
            }
        } else if (trip != null) {
            val t = trip!!
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    if (t.status in listOf("in_progress", "in_transit")) PrimaryLight else White
                )) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t.customerName ?: "Customer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            StatusChip(
                                text = when (t.status) {
                                    "pending" -> "Pending"
                                    "assigned", "driver_accepted" -> "Assigned"
                                    "in_progress", "in_transit" -> "In Progress"
                                    "completed" -> "Completed"
                                    else -> "Cancelled"
                                },
                                status = when (t.status) {
                                    "pending", "partially_filled", "assigned", "driver_accepted" -> ChipStatus.PENDING
                                    "in_progress", "in_transit" -> ChipStatus.IN_PROGRESS
                                    "completed" -> ChipStatus.COMPLETED
                                    else -> ChipStatus.CANCELLED
                                }
                            )
                        }
                    }
                }

                SectionCard("Location Details") {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.LocationOn, null, tint = Success, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Pickup", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(t.pickup.address, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.LocationOn, null, tint = Error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Drop", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(t.drop.address, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                SectionCard("Trip Information") {
                    DetailRow("Customer", t.customerName ?: "Customer")
                    Divider()
                    DetailRow("Vehicle", t.vehicleType ?: "—")
                    Divider()
                    DetailRow("Distance", "${String.format("%.1f", t.distanceKm)} km")
                    Divider()
                    DetailRow("Fare", "₹${String.format("%.0f", t.fare)}")
                    Divider()
                    DetailRow("Status", t.status.replaceFirstChar { it.uppercase() })
                }

                if (t.status in listOf("in_progress", "in_transit")) {
                    PrimaryButton("Track Live Location", onClick = { /* Navigate to tracking */ })
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Trip not found", color = TextSecondary)
            }
        }
    }
}
