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
import com.weelo.logistics.data.model.Trip
import com.weelo.logistics.data.model.TripStatus
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.DataSanitizer
import kotlinx.coroutines.launch

@Composable
fun TripListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateTrip: () -> Unit,
    onNavigateToTripDetails: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    
    LaunchedEffect(Unit) {
        scope.launch {
            repository.getTrips("t1").onSuccess { tripList ->
                trips = tripList
                isLoading = false
            }
        }
    }
    
    val filteredTrips = trips.filter { trip ->
        when (selectedFilter) {
            "Active" -> trip.status in listOf(TripStatus.IN_PROGRESS, TripStatus.ASSIGNED)
            "Completed" -> trip.status == TripStatus.COMPLETED
            "Pending" -> trip.status == TripStatus.PENDING
            else -> true
        }
    }
    
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Surface)) {
            PrimaryTopBar(title = "Trips (${trips.size})", onBackClick = onNavigateBack)
            
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = selectedFilter == "All", onClick = { selectedFilter = "All" }, label = { Text("All") })
                FilterChip(selected = selectedFilter == "Active", onClick = { selectedFilter = "Active" }, label = { Text("Active") })
                FilterChip(selected = selectedFilter == "Completed", onClick = { selectedFilter = "Completed" }, label = { Text("Completed") })
                FilterChip(selected = selectedFilter == "Pending", onClick = { selectedFilter = "Pending" }, label = { Text("Pending") })
            }
            
            if (isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
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
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTrips) { trip ->
                        TripCard(trip) { onNavigateToTripDetails(trip.id) }
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
fun TripCard(trip: Trip, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(2.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(White)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(DataSanitizer.sanitizeForDisplay(trip.customerName), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusChip(
                    text = when (trip.status) {
                        TripStatus.PENDING -> "Pending"
                        TripStatus.ASSIGNED -> "Assigned"
                        TripStatus.ACCEPTED -> "Accepted"
                        TripStatus.IN_PROGRESS -> "In Progress"
                        TripStatus.COMPLETED -> "Completed"
                        TripStatus.REJECTED -> "Rejected"
                        TripStatus.CANCELLED -> "Cancelled"
                    },
                    status = when (trip.status) {
                        TripStatus.PENDING -> ChipStatus.PENDING
                        TripStatus.IN_PROGRESS -> ChipStatus.IN_PROGRESS
                        TripStatus.COMPLETED -> ChipStatus.COMPLETED
                        else -> ChipStatus.CANCELLED
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("${DataSanitizer.sanitizeForDisplay(trip.pickupLocation.address)} → ${DataSanitizer.sanitizeForDisplay(trip.dropLocation.address)}", 
                 style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text("₹${String.format("%.0f", trip.fare)} • ${trip.distance} km",
                 style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
