package com.weelo.logistics.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.weelo.logistics.R
import com.weelo.logistics.data.api.TripTrackingData
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * =============================================================================
 * LIVE TRACKING SCREEN — Real API Data
 * =============================================================================
 *
 * Real-time GPS tracking of driver's location during trip.
 * ALL data comes from backend APIs — zero MockDataRepository references.
 *
 * DATA SOURCES:
 *   - GET /api/v1/tracking/{tripId}  → Driver's live location (polled every 5s)
 *   - GET /api/v1/driver/trips/active → Trip details (pickup, drop, fare)
 *
 * SCALABILITY: Polling every 5s is lightweight. Backend can upgrade to WebSocket.
 * MODULARITY: Screen fetches directly — no ViewModel needed for simple polling.
 * EASY UNDERSTANDING: Two API calls, one timer — straightforward.
 * SAME CODING STANDARD: Composable + LaunchedEffect + State pattern.
 *
 * NOTE: Map placeholder remains — requires Google Maps SDK integration.
 *       Replace the Box with GoogleMap composable when ready.
 * =============================================================================
 */
@Composable
fun LiveTrackingScreen(
    tripId: String,
    @Suppress("UNUSED_PARAMETER") driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToComplete: () -> Unit
) {
    var trackingData by remember { mutableStateOf<TripTrackingData?>(null) }
    var tripPickup by remember { mutableStateOf("") }
    var tripDrop by remember { mutableStateOf("") }
    var tripFare by remember { mutableStateOf(0.0) }
    var tripDistance by remember { mutableStateOf(0.0) }
    var tripStatus by remember { mutableStateOf("pending") }
    var isLoading by remember { mutableStateOf(true) }
    var showTripDetails by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fetch trip details on first load
    LaunchedEffect(tripId) {
        try {
            val activeResponse = RetrofitClient.driverApi.getActiveTrip()
            if (activeResponse.isSuccessful && activeResponse.body()?.success == true) {
                val tripData = activeResponse.body()?.data?.trip
                if (tripData != null) {
                    tripPickup = tripData.pickup.address
                    tripDrop = tripData.drop.address
                    tripFare = tripData.fare
                    tripDistance = tripData.distanceKm
                    tripStatus = tripData.status
                } else {
                    errorMessage = "Trip data not found"
                }
            } else {
                errorMessage = "Failed to load trip (${activeResponse.code()})"
                Timber.w("LiveTracking: API error ${activeResponse.code()}")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "LiveTracking: Failed to load trip details")
            errorMessage = e.message ?: "Network error"
        } finally {
            isLoading = false
        }
    }

    // Poll tracking location every 5 seconds — stops when trip is completed/cancelled
    LaunchedEffect(tripId) {
        val terminalStatuses = setOf("completed", "cancelled", "failed")
        while (tripStatus !in terminalStatuses) {
            delay(5_000)
            // Re-check after delay in case status changed (e.g. via initial fetch)
            if (tripStatus in terminalStatuses) break
            try {
                val response = RetrofitClient.trackingApi.getTripTracking(tripId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        trackingData = data
                        tripStatus = data.status
                        if (data.status in terminalStatuses) {
                            Timber.i("LiveTracking: Trip ${data.status} — stopping poll")
                            break
                        }
                    }
                } else {
                    Timber.w("LiveTracking: Poll error ${response.code()}")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w("LiveTracking: Location poll failed: ${e.message}")
            }
        }
        Timber.i("LiveTracking: Poll loop exited (tripStatus=$tripStatus)")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage ?: "Error", color = TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onNavigateBack) { Text("Go Back") }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Map Placeholder — replace with GoogleMap composable when ready
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Map, null,
                            modifier = Modifier.size(80.dp),
                            tint = Primary.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "MAP VIEW",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        trackingData?.let {
                            Text(
                                stringResource(R.string.lat_lng_format, it.latitude, it.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextDisabled
                            )
                        }
                    }

                    // Live indicator
                    trackingData?.let {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Success,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MyLocation, null,
                                    tint = White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "LIVE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                            }
                        }
                    }
                }

                // Bottom Sheet with Trip Info
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TextDisabled)
                                .align(Alignment.CenterHorizontally)
                        )

                        Spacer(Modifier.height(16.dp))

                        // Trip Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    when {
                                        tripStatus == "completed" -> Icons.Default.CheckCircle
                                        tripStatus in listOf("in_transit", "heading_to_pickup") ->
                                            Icons.Default.LocalShipping
                                        else -> Icons.Default.Schedule
                                    },
                                    null, tint = Primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    when (tripStatus) {
                                        "in_transit" -> "Trip In Progress"
                                        "completed" -> "Trip Completed"
                                        "heading_to_pickup" -> "Heading to Pickup"
                                        "at_pickup" -> "At Pickup Point"
                                        "loading_complete" -> "Loading Complete"
                                        "arrived_at_drop" -> "Arrived at Drop"
                                        else -> "Trip Active"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                trackingData?.let {
                                    Text(
                                        "${it.speed.toInt()} km/h • Updated just now",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }

                            IconButton(onClick = { showTripDetails = !showTripDetails }) {
                                Icon(
                                    if (showTripDetails) Icons.Default.ExpandLess
                                    else Icons.Default.ExpandMore,
                                    null, tint = Primary
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Route Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("FROM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    tripPickup.take(30),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                Icons.Default.ArrowForward, null,
                                modifier = Modifier.padding(top = 12.dp),
                                tint = Primary
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("TO", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    tripDrop.take(30),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Expanded Details
                        if (showTripDetails) {
                            Spacer(Modifier.height(16.dp))
                            Divider()
                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TrackingInfoItem(
                                    icon = Icons.Default.Route,
                                    label = stringResource(R.string.distance),
                                    value = stringResource(R.string.distance_km_format, tripDistance)
                                )
                                TrackingInfoItem(
                                    icon = Icons.Default.Speed,
                                    label = stringResource(R.string.speed),
                                    value = stringResource(R.string.speed_kmh_format, trackingData?.speed?.toInt() ?: 0)
                                )
                                TrackingInfoItem(
                                    icon = Icons.Default.AttachMoney,
                                    label = stringResource(R.string.fare),
                                    value = stringResource(R.string.fare_amount_format, tripFare)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ArrowBack, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Back")
                            }

                            if (tripStatus in listOf("in_transit", "arrived_at_drop")) {
                                Button(
                                    onClick = onNavigateToComplete,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Success)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Complete Trip")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tracking Info Item - Small metric display
 */
@Composable
fun TrackingInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = Primary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
