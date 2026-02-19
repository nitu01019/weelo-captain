package com.weelo.logistics.ui.transporter

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
import com.weelo.logistics.data.api.TripData
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.components.responsiveHorizontalPadding
import com.weelo.logistics.ui.components.ProvideShimmerBrush
import com.weelo.logistics.ui.components.SkeletonList
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.DataSanitizer
import timber.log.Timber

private const val TAG = "TripListScreen"

/**
 * =============================================================================
 * TRIP LIST SCREEN — Real API Data
 * =============================================================================
 *
 * Shows all trips for transporter from GET /api/v1/driver/trips.
 * ALL data from backend — zero MockDataRepository references.
 *
 * SCALABILITY: Backend uses indexed queries, paginated responses.
 * MODULARITY: Screen fetches directly via RetrofitClient.
 * EASY UNDERSTANDING: One API call → list display.
 * SAME CODING STANDARD: Composable + LaunchedEffect + State.
 * =============================================================================
 */
@Composable
fun TripListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateTrip: () -> Unit,
    onNavigateToTripDetails: (String) -> Unit
) {
    var trips by remember { mutableStateOf<List<TripData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("All") }

    // Load trips from real API
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val response = RetrofitClient.driverApi.getDriverTrips(limit = 50)
            val data = response.body()?.data
            if (data != null) {
                trips = data.trips
                Timber.i("✅ Loaded ${trips.size} trips from API")
            }
        } catch (e: Exception) {
            Timber.e("❌ Error loading trips: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    val filteredTrips = trips.filter { trip ->
        when (selectedFilter) {
            "Active" -> trip.status in listOf("in_progress", "active", "assigned", "driver_accepted")
            "Completed" -> trip.status == "completed"
            "Pending" -> trip.status in listOf("pending", "partially_filled")
            else -> true
        }
    }
    
    // Responsive layout
    val horizontalPadding = responsiveHorizontalPadding()
    
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Surface)) {
            PrimaryTopBar(title = "Trips (${trips.size})", onBackClick = onNavigateBack)
            
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(White)
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = selectedFilter == "All", onClick = { selectedFilter = "All" }, label = { Text("All") })
                FilterChip(selected = selectedFilter == "Active", onClick = { selectedFilter = "Active" }, label = { Text("Active") })
                FilterChip(selected = selectedFilter == "Completed", onClick = { selectedFilter = "Completed" }, label = { Text("Completed") })
                FilterChip(selected = selectedFilter == "Pending", onClick = { selectedFilter = "Pending" }, label = { Text("Pending") })
            }
            
            if (isLoading) {
                ProvideShimmerBrush {
                    SkeletonList(itemCount = 4, modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 16.dp))
                }
            } else if (filteredTrips.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.LocalShipping, null, Modifier.size(64.dp), tint = TextDisabled)
                        Spacer(Modifier.height(16.dp))
                        Text("No trips found", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredTrips,
                        key = { it.id },
                        contentType = { "trip_data_card" }
                    ) { trip ->
                        TripDataCard(trip) { onNavigateToTripDetails(trip.id) }
                    }
                }
            }
        }
        
        FloatingActionButton(
            onClick = onNavigateToCreateTrip,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Primary
        ) {
            Icon(Icons.Default.Add, "Create Trip", tint = White)
        }
    }
}

@Composable
fun TripDataCard(trip: TripData, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(2.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(White)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    DataSanitizer.sanitizeForDisplay(trip.customerName ?: "Customer"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusChip(
                    text = when (trip.status) {
                        "pending" -> "Pending"
                        "assigned", "driver_accepted" -> "Assigned"
                        "in_progress", "in_transit" -> "In Progress"
                        "completed" -> "Completed"
                        "cancelled" -> "Cancelled"
                        else -> trip.status.replaceFirstChar { it.uppercase() }
                    },
                    status = when (trip.status) {
                        "pending", "partially_filled" -> ChipStatus.PENDING
                        "in_progress", "in_transit", "active" -> ChipStatus.IN_PROGRESS
                        "completed" -> ChipStatus.COMPLETED
                        else -> ChipStatus.CANCELLED
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${DataSanitizer.sanitizeForDisplay(trip.pickup.address)} → ${DataSanitizer.sanitizeForDisplay(trip.drop.address)}",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "₹${String.format("%.0f", trip.fare)} • ${String.format("%.1f", trip.distanceKm)} km",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )
        }
    }
}
