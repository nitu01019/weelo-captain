package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.R
import com.weelo.logistics.data.model.Vehicle
import com.weelo.logistics.data.model.VehicleStatus
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun VehicleDetailsScreen(
    vehicleId: String,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    var vehicle by remember { mutableStateOf<Vehicle?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(vehicleId) {
        scope.launch {
            repository.getVehicles("t1").onSuccess { vehicles ->
                vehicle = vehicles.find { it.id == vehicleId }
                isLoading = false
            }
        }
    }
    
    // Function to update vehicle status
    fun updateVehicleStatus(newStatus: VehicleStatus) {
        vehicle = vehicle?.copy(status = newStatus)
        showStatusDialog = false
        // BACKEND: Call API to update status
        // repository.updateVehicleStatus(vehicleId, newStatus)
    }
    
    Column(modifier = Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(
            title = "Vehicle Details",
            onBackClick = onNavigateBack,
            actions = {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Delete", tint = Error)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit")
                }
            }
        )
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else vehicle?.let { v ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Vehicle Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(PrimaryLight, White)
                                )
                            )
                            .padding(24.dp)
                    ) {
                        // Vehicle Image - Use new detail images based on category
                        val imageRes = when (v.category.id.lowercase()) {
                            "container" -> R.drawable.vehicle_container_detail
                            "tanker" -> R.drawable.vehicle_tanker_detail
                            "tipper" -> R.drawable.vehicle_tipper_detail
                            "bulker" -> R.drawable.vehicle_bulker_detail
                            "trailer" -> R.drawable.vehicle_trailer_detail
                            "mini" -> R.drawable.vehicle_mini_detail
                            "lcv" -> R.drawable.vehicle_lcv_detail
                            "dumper" -> R.drawable.vehicle_dumper_detail
                            "open" -> R.drawable.vehicle_open  // No new image provided
                            else -> null
                        }
                        
                        if (imageRes != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = White,
                                shadowElevation = 2.dp
                            ) {
                                Image(
                                    painter = painterResource(id = imageRes),
                                    contentDescription = v.subtype.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            
                            Spacer(Modifier.height(20.dp))
                        }
                        
                        // Vehicle Info
                        Text(
                            text = v.vehicleNumber,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryDark
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = v.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Details Section
                SectionCard(title = "Vehicle Information") {
                    DetailRow("Category", v.category.name)
                    Divider()
                    DetailRow("Type", v.subtype.name)
                    Divider()
                    DetailRow("Capacity", v.capacityText)
                    v.model?.let {
                        Divider()
                        DetailRow("Model", it)
                    }
                    v.year?.let {
                        Divider()
                        DetailRow("Year", it.toString())
                    }
                }
                
                // Status Section with Change Option
                SectionCard(title = "Vehicle Status") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Status", style = MaterialTheme.typography.bodyLarge)
                        StatusChip(
                            text = when (v.status) {
                                VehicleStatus.AVAILABLE -> "Available"
                                VehicleStatus.IN_TRANSIT -> "In Transit"
                                VehicleStatus.MAINTENANCE -> "Maintenance"
                                VehicleStatus.INACTIVE -> "Inactive"
                            },
                            status = when (v.status) {
                                VehicleStatus.AVAILABLE -> ChipStatus.AVAILABLE
                                VehicleStatus.IN_TRANSIT -> ChipStatus.IN_PROGRESS
                                VehicleStatus.MAINTENANCE -> ChipStatus.PENDING
                                VehicleStatus.INACTIVE -> ChipStatus.CANCELLED
                            }
                        )
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Status Change Buttons
                    if (v.status != VehicleStatus.IN_TRANSIT) {
                        OutlinedButton(
                            onClick = { showStatusDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors()
                        ) {
                            Icon(Icons.Default.ChangeCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Change Status")
                        }
                    }
                }
                
                // Actions
                PrimaryButton(
                    text = "Assign to Driver",
                    onClick = { /* TODO */ }
                )
                SecondaryButton(
                    text = "View Trip History",
                    onClick = { /* TODO */ }
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Vehicle?") },
            text = { Text("Are you sure you want to delete this vehicle? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteVehicle(vehicleId)
                            onNavigateBack()
                        }
                    }
                ) { Text("Delete", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Status Change Dialog
    if (showStatusDialog && vehicle != null) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            icon = { Icon(Icons.Default.ChangeCircle, null, tint = Primary) },
            title = { Text("Change Vehicle Status") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Select new status for ${vehicle!!.vehicleNumber}:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Available Button
                    if (vehicle!!.status != VehicleStatus.AVAILABLE) {
                        OutlinedButton(
                            onClick = { updateVehicleStatus(VehicleStatus.AVAILABLE) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Success
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Mark as Available")
                        }
                    }
                    
                    // Maintenance Button
                    if (vehicle!!.status != VehicleStatus.MAINTENANCE) {
                        OutlinedButton(
                            onClick = { updateVehicleStatus(VehicleStatus.MAINTENANCE) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Warning
                            )
                        ) {
                            Icon(Icons.Default.Build, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Mark as Under Maintenance")
                        }
                    }
                    
                    // Inactive Button
                    if (vehicle!!.status != VehicleStatus.INACTIVE) {
                        OutlinedButton(
                            onClick = { updateVehicleStatus(VehicleStatus.INACTIVE) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Error
                            )
                        ) {
                            Icon(Icons.Default.Cancel, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Mark as Inactive")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
