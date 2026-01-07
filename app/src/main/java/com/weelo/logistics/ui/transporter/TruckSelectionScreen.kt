package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

/**
 * TRUCK SELECTION SCREEN
 * ======================
 * After transporter views broadcast, they select how many trucks to commit.
 * 
 * FLOW:
 * 1. Show broadcast details (customer needs 10 trucks)
 * 2. Show transporter's available vehicles
 * 3. Transporter selects vehicles (e.g., picks 3 trucks)
 * 4. Confirms selection → Goes to driver assignment screen
 * 
 * FOR BACKEND DEVELOPER:
 * - Fetch transporter's available vehicles (status = AVAILABLE, not on trip)
 * - Filter by vehicle type matching broadcast requirement
 * - Allow selection up to (totalTrucksNeeded - trucksFilledSoFar)
 * - Create TripAssignment when confirmed
 */
@Composable
fun TruckSelectionScreen(
    broadcastId: String,
    onNavigateBack: () -> Unit,
    onNavigateToDriverAssignment: (String, List<String>) -> Unit  // broadcastId, selectedVehicleIds
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    // TODO: Connect to real repository from backend
    
    var broadcast by remember { mutableStateOf<BroadcastTrip?>(null) }
    var availableVehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var selectedVehicleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    // BACKEND: Fetch broadcast details and available vehicles
    LaunchedEffect(broadcastId) {
        scope.launch {
            // Mock - Replace with: repository.getBroadcastById(broadcastId)
            broadcast = repository.getMockBroadcastById(broadcastId)
            
            // Mock - Replace with: repository.getAvailableVehicles(transporterId, vehicleType)
            availableVehicles = repository.getMockAvailableVehicles("t1")
            isLoading = false
        }
    }
    
    val trucksRemaining = (broadcast?.totalTrucksNeeded ?: 0) - (broadcast?.trucksFilledSoFar ?: 0)
    val canSelectMore = selectedVehicleIds.size < trucksRemaining
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // Top Bar
        PrimaryTopBar(
            title = "Select Trucks",
            onBackClick = onNavigateBack
        )
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            broadcast?.let { bc ->
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Broadcast Summary Card
                    item {
                        BroadcastSummaryCard(broadcast = bc)
                    }
                    
                    // Selection Info
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(InfoLight)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Select Your Trucks",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${selectedVehicleIds.size} selected • $trucksRemaining available",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                
                                // Selection counter
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Primary
                                ) {
                                    Text(
                                        "${selectedVehicleIds.size}/$trucksRemaining",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = White
                                    )
                                }
                            }
                        }
                    }
                    
                    // Available Vehicles Header
                    item {
                        Text(
                            "Your Available Vehicles (${availableVehicles.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Vehicle List
                    if (availableVehicles.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(WarningLight)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        null,
                                        modifier = Modifier.size(48.dp),
                                        tint = Warning
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "No available vehicles",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "All your vehicles are currently on trips",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        items(availableVehicles) { vehicle ->
                            VehicleSelectionCard(
                                vehicle = vehicle,
                                isSelected = selectedVehicleIds.contains(vehicle.id),
                                canSelect = canSelectMore || selectedVehicleIds.contains(vehicle.id),
                                onToggle = {
                                    selectedVehicleIds = if (selectedVehicleIds.contains(vehicle.id)) {
                                        selectedVehicleIds - vehicle.id
                                    } else {
                                        if (canSelectMore) selectedVehicleIds + vehicle.id
                                        else selectedVehicleIds
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Bottom Action Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Selection Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "${selectedVehicleIds.size} trucks selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Earnings: ₹${String.format("%.0f", bc.farePerTruck * selectedVehicleIds.size)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Success
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Confirm Button
                        Button(
                            onClick = { showConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = selectedVehicleIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Default.AssignmentTurnedIn, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Assign Drivers (${selectedVehicleIds.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Confirmation Dialog
    if (showConfirmDialog && broadcast != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { Icon(Icons.Default.Info, null, tint = Primary) },
            title = { Text("Confirm Selection") },
            text = {
                Column {
                    Text("You are committing:")
                    Spacer(Modifier.height(8.dp))
                    Text("• ${selectedVehicleIds.size} trucks", fontWeight = FontWeight.Bold)
                    Text("• Route: ${broadcast!!.pickupLocation.city} → ${broadcast!!.dropLocation.city}")
                    Text("• Earnings: ₹${String.format("%.0f", broadcast!!.farePerTruck * selectedVehicleIds.size)}", color = Success)
                    Spacer(Modifier.height(8.dp))
                    Text("Next step: Assign drivers to each truck", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onNavigateToDriverAssignment(broadcastId, selectedVehicleIds.toList())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * BROADCAST SUMMARY CARD - Quick view of broadcast details
 */
@Composable
fun BroadcastSummaryCard(broadcast: BroadcastTrip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Customer Request",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                broadcast.customerName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(
                    icon = Icons.Default.LocationOn,
                    label = "Route",
                    value = "${broadcast.pickupLocation.city} → ${broadcast.dropLocation.city}"
                )
                DetailItem(
                    icon = Icons.Default.LocalShipping,
                    label = "Needed",
                    value = "${broadcast.totalTrucksNeeded - broadcast.trucksFilledSoFar} trucks"
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(
                    icon = Icons.Default.AttachMoney,
                    label = "Per Truck",
                    value = "₹${String.format("%.0f", broadcast.farePerTruck)}"
                )
                DetailItem(
                    icon = Icons.Default.Route,
                    label = "Distance",
                    value = "${broadcast.distance} km"
                )
            }
        }
    }
}

/**
 * VEHICLE SELECTION CARD - Individual vehicle with checkbox
 */
@Composable
fun VehicleSelectionCard(
    vehicle: Vehicle,
    isSelected: Boolean,
    canSelect: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, Primary, RoundedCornerShape(12.dp))
                else Modifier
            ),
        onClick = { if (canSelect) onToggle() },
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.05f) else White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = { if (canSelect) onToggle() },
                enabled = canSelect,
                colors = CheckboxDefaults.colors(checkedColor = Primary)
            )
            
            Spacer(Modifier.width(12.dp))
            
            // Vehicle Icon
            Icon(
                Icons.Default.LocalShipping,
                null,
                modifier = Modifier.size(40.dp),
                tint = if (isSelected) Primary else TextSecondary
            )
            
            Spacer(Modifier.width(12.dp))
            
            // Vehicle Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    vehicle.vehicleNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Primary else TextPrimary
                )
                Text(
                    vehicle.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            // Status Badge
            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Success
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = White
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detail Item - Small label-value display
 */
@Composable
fun DetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = TextSecondary)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
