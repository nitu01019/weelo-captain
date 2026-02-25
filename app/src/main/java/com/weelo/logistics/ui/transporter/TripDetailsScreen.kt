package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var reloadTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(tripId, reloadTrigger) {
        isLoading = true
        errorMessage = null
        trip = null
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
            SkeletonTripDetailsLoading(
                modifier = Modifier.fillMaxSize(),
                horizontalPadding = horizontalPadding
            )
        } else if (errorMessage != null) {
            val isTripNotFound = errorMessage == "Trip not found"
            RetryErrorStatePanel(
                title = if (isTripNotFound) "Trip not found" else "Could not load trip",
                message = if (isTripNotFound) {
                    "This trip is not available anymore or may have been removed."
                } else {
                    errorMessage ?: "Failed to load trip"
                },
                onRetry = if (isTripNotFound) null else {
                    {
                        reloadTrigger += 1
                    }
                },
                illustrationRes = EmptyStateArtwork.TRIP_DETAILS_NOT_FOUND.drawableRes,
                modifier = Modifier.fillMaxSize()
            )
        } else if (trip != null) {
            val t = trip!!
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val statusLabel = when (t.status) {
                    "pending" -> "Pending"
                    "assigned", "driver_accepted" -> "Assigned"
                    "in_progress", "in_transit" -> "In Progress"
                    "completed" -> "Completed"
                    else -> "Cancelled"
                }
                val chipStatus = when (t.status) {
                    "pending", "partially_filled", "assigned", "driver_accepted" -> ChipStatus.PENDING
                    "in_progress", "in_transit" -> ChipStatus.IN_PROGRESS
                    "completed" -> ChipStatus.COMPLETED
                    else -> ChipStatus.CANCELLED
                }

                HeroEntityCard(
                    title = t.customerName ?: "Customer",
                    subtitle = listOfNotNull(
                        t.vehicleNumber ?: t.vehicleType,
                        t.bookingId?.takeIf { it.isNotBlank() }?.let { "Booking ${it.take(8)}" }
                    ).joinToString(" • ").ifBlank { "Trip ${t.id.take(8)}" },
                    mediaSpec = CardMediaSpec(artwork = CardArtwork.DETAIL_TRIP),
                    leadingAvatar = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    },
                    statusContent = {
                        StatusChip(text = statusLabel, status = chipStatus)
                    },
                    metaContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceVariant
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Distance", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${String.format("%.1f", t.distanceKm)} km",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceVariant
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Fare", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "₹${String.format("%.0f", t.fare)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Success
                                    )
                                }
                            }
                        }
                    }
                )

                SectionCard("Location Details") {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.LocationOn, null, tint = Success, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pickup", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(t.pickup.address, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.LocationOn, null, tint = Error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
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
        }
    }
}

@Composable
private fun SkeletonTripDetailsLoading(
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionSkeletonBlock(titleLineWidthFraction = 0.52f, rowCount = 2, showLeadingAvatar = true)
        SectionSkeletonBlock(titleLineWidthFraction = 0.34f, rowCount = 2)
        SectionSkeletonBlock(titleLineWidthFraction = 0.38f, rowCount = 4)
        SectionSkeletonBlock(titleLineWidthFraction = 0.45f, rowCount = 1)
    }
}
