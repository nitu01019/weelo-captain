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
import com.weelo.logistics.data.model.Vehicle
import com.weelo.logistics.data.model.VehicleStatus
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Fleet List Screen - PRD-02 Compliant
 * Shows all vehicles with search and filter
 */
@Composable
fun FleetListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToVehicleDetails: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    
    LaunchedEffect(Unit) {
        scope.launch {
            val result = repository.getVehicles("t1")
            result.onSuccess { vehicleList ->
                vehicles = vehicleList
                isLoading = false
            }
        }
    }
    
    // Filter vehicles
    val filteredVehicles = vehicles.filter { vehicle ->
        val matchesSearch = vehicle.vehicleNumber.contains(searchQuery, ignoreCase = true) ||
                vehicle.displayName.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "Available" -> vehicle.status == VehicleStatus.AVAILABLE
            "In Transit" -> vehicle.status == VehicleStatus.IN_TRANSIT
            "Maintenance" -> vehicle.status == VehicleStatus.MAINTENANCE
            else -> true
        }
        matchesSearch && matchesFilter
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
        ) {
            // Top Bar
            PrimaryTopBar(
                title = "Fleet (${vehicles.size})",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(onClick = { /* TODO: Filter */ }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
            
            // Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(White)
                    .padding(16.dp)
            ) {
                SearchTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search by vehicle number or type",
                    leadingIcon = Icons.Default.Search
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = selectedFilter == "Available",
                        onClick = { selectedFilter = "Available" },
                        label = { Text("Available") }
                    )
                    FilterChip(
                        selected = selectedFilter == "In Transit",
                        onClick = { selectedFilter = "In Transit" },
                        label = { Text("In Transit") }
                    )
                    FilterChip(
                        selected = selectedFilter == "Maintenance",
                        onClick = { selectedFilter = "Maintenance" },
                        label = { Text("Maintenance") }
                    )
                }
            }
            
            // Vehicle List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (filteredVehicles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = TextDisabled,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "No vehicles yet" else "No vehicles found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Add your first vehicle to get started",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredVehicles) { vehicle ->
                        VehicleListCard(
                            vehicle = vehicle,
                            onClick = { onNavigateToVehicleDetails(vehicle.id) }
                        )
                    }
                }
            }
        }
        
        // FAB - Add Vehicle
        FloatingActionButton(
            onClick = onNavigateToAddVehicle,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Primary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Vehicle",
                tint = White
            )
        }
    }
}

@Composable
fun VehicleListCard(
    vehicle: Vehicle,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vehicle Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Surface, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = vehicle.category.icon,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Vehicle Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.vehicleNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = vehicle.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = vehicle.capacityText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Status Chip
            StatusChip(
                text = when (vehicle.status) {
                    VehicleStatus.AVAILABLE -> "Available"
                    VehicleStatus.IN_TRANSIT -> "In Transit"
                    VehicleStatus.MAINTENANCE -> "Maintenance"
                    VehicleStatus.INACTIVE -> "Inactive"
                },
                status = when (vehicle.status) {
                    VehicleStatus.AVAILABLE -> ChipStatus.AVAILABLE
                    VehicleStatus.IN_TRANSIT -> ChipStatus.IN_PROGRESS
                    VehicleStatus.MAINTENANCE -> ChipStatus.PENDING
                    VehicleStatus.INACTIVE -> ChipStatus.CANCELLED
                }
            )
        }
    }
}
