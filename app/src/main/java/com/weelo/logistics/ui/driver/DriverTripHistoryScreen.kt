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
import com.weelo.logistics.data.model.Trip
import com.weelo.logistics.data.model.TripStatus
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.components.responsiveHorizontalPadding
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Driver Trip History Screen - PRD-04 Compliant
 * Shows all past trips with filter and search
 */
@Composable
fun DriverTripHistoryScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTripDetails: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    // TODO: Connect to real repository from backend
    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        scope.launch {
            repository.getTripsByDriver(driverId).onSuccess { tripList ->
                trips = tripList
                isLoading = false
            }
        }
    }
    
    val filteredTrips = trips.filter { trip ->
        val matchesSearch = trip.customerName.contains(searchQuery, ignoreCase = true) ||
                trip.pickupLocation.address.contains(searchQuery, ignoreCase = true) ||
                trip.dropLocation.address.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "Completed" -> trip.status == TripStatus.COMPLETED
            "Cancelled" -> trip.status == TripStatus.CANCELLED
            else -> true
        }
        matchesSearch && matchesFilter
    }
    
    // Responsive layout
    val horizontalPadding = responsiveHorizontalPadding()
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(
            title = "Trip History",
            onBackClick = onNavigateBack,
            actions = {
                IconButton(onClick = { /* TODO: Download report */ }) {
                    Icon(Icons.Default.Download, "Download")
                }
            }
        )
        
        // Search & Filter
        Column(Modifier.fillMaxWidth().background(White).padding(horizontal = horizontalPadding, vertical = 16.dp)) {
            SearchTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.trim() },
                placeholder = "Search by customer or location",
                leadingIcon = Icons.Default.Search
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "All",
                    onClick = { selectedFilter = "All" },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = selectedFilter == "Completed",
                    onClick = { selectedFilter = "Completed" },
                    label = { Text("Completed") }
                )
                FilterChip(
                    selected = selectedFilter == "Cancelled",
                    onClick = { selectedFilter = "Cancelled" },
                    label = { Text("Cancelled") }
                )
            }
        }
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (filteredTrips.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.History, null, Modifier.size(64.dp), tint = TextDisabled)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isEmpty()) "No trip history yet" else "No trips found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Card
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(SecondaryLight)) {
                        Row(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            SummaryItem("Total", trips.size.toString())
                            SummaryItem("Completed", trips.count { it.status == TripStatus.COMPLETED }.toString())
                            SummaryItem("Earned", "₹${trips.filter { it.status == TripStatus.COMPLETED }.sumOf { it.fare }.toInt()}")
                        }
                    }
                }
                
                // OPTIMIZATION: Add keys to prevent unnecessary recompositions
                items(
                    items = filteredTrips,
                    key = { it.id }
                ) { trip ->
                    TripHistoryCard(trip, onClick = { onNavigateToTripDetails(trip.id) })
                }
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Secondary
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
fun TripHistoryCard(trip: Trip, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(2.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(White)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        trip.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatDate(trip.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                StatusChip(
                    text = when (trip.status) {
                        TripStatus.COMPLETED -> "Completed"
                        TripStatus.CANCELLED -> "Cancelled"
                        else -> "In Progress"
                    },
                    status = when (trip.status) {
                        TripStatus.COMPLETED -> ChipStatus.COMPLETED
                        TripStatus.CANCELLED -> ChipStatus.CANCELLED
                        else -> ChipStatus.IN_PROGRESS
                    }
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp), tint = Success)
                Spacer(Modifier.width(4.dp))
                Text(
                    trip.pickupLocation.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp), tint = Error)
                Spacer(Modifier.width(4.dp))
                Text(
                    trip.dropLocation.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${trip.distance} km • ${trip.estimatedDuration} mins",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    "₹${String.format("%.0f", trip.fare)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (trip.status == TripStatus.COMPLETED) Success else TextSecondary
                )
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
