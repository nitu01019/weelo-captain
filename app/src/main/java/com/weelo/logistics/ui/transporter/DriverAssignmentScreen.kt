package com.weelo.logistics.ui.transporter

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.data.repository.DriverRepository
import com.weelo.logistics.data.repository.DriverResult
import com.weelo.logistics.data.repository.VehicleRepository
import com.weelo.logistics.data.repository.VehicleResult
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

/**
 * DRIVER ASSIGNMENT SCREEN
 * ========================
 * After truck selection, transporter assigns a specific driver to each truck.
 * 
 * FLOW:
 * 1. Shows list of selected trucks (e.g., 3 trucks)
 * 2. For each truck, transporter picks an available driver
 * 3. Shows driver details: name, rating, availability
 * 4. Confirms all assignments → Calls acceptBroadcast API for each vehicle
 * 5. Sends notifications to all assigned drivers (handled by backend)
 * 
 * CONNECTED TO REAL BACKEND:
 * - Fetches broadcast details via BroadcastRepository
 * - Fetches vehicles via VehicleRepository
 * - Fetches available drivers via DriverRepository
 * - Calls acceptBroadcast API to assign each vehicle+driver
 */
@Composable
fun DriverAssignmentScreen(
    broadcastId: String,
    selectedVehicleIds: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToTracking: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Real repositories connected to backend
    val broadcastRepository = remember { BroadcastRepository.getInstance(context) }
    val vehicleRepository = remember { VehicleRepository.getInstance(context) }
    val driverRepository = remember { DriverRepository.getInstance(context) }
    
    var broadcast by remember { mutableStateOf<BroadcastTrip?>(null) }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var availableDrivers by remember { mutableStateOf<List<Driver>>(emptyList()) }
    var driverAssignments by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // vehicleId -> driverId
    var showDriverPicker by remember { mutableStateOf<String?>(null) } // vehicleId being assigned
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successCount by remember { mutableStateOf(0) }
    
    // Fetch data from real backend
    LaunchedEffect(broadcastId, selectedVehicleIds) {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            // Fetch broadcast details
            when (val broadcastResult = broadcastRepository.getBroadcastById(broadcastId)) {
                is BroadcastResult.Success -> {
                    broadcast = broadcastResult.data
                }
                is BroadcastResult.Error -> {
                    errorMessage = broadcastResult.message
                    Toast.makeText(context, broadcastResult.message, Toast.LENGTH_SHORT).show()
                }
                is BroadcastResult.Loading -> {}
            }
            
            // Fetch selected vehicles
            when (val vehicleResult = vehicleRepository.fetchVehicles(forceRefresh = false)) {
                is VehicleResult.Success -> {
                    vehicles = vehicleRepository.mapToUiModels(
                        vehicleResult.data.vehicles.filter { it.id in selectedVehicleIds }
                    )
                }
                is VehicleResult.Error -> {
                    errorMessage = vehicleResult.message
                }
                is VehicleResult.Loading -> {}
            }
            
            // Fetch available drivers
            when (val driverResult = driverRepository.fetchDrivers(forceRefresh = true)) {
                is DriverResult.Success -> {
                    // Filter only available drivers (ACTIVE and not on trip)
                    availableDrivers = driverResult.data.drivers.filter { 
                        it.status == DriverStatus.ACTIVE && it.isAvailable
                    }
                    android.util.Log.i("DriverAssignmentScreen", 
                        "✅ Found ${availableDrivers.size} available drivers out of ${driverResult.data.total}")
                }
                is DriverResult.Error -> {
                    errorMessage = driverResult.message
                    Toast.makeText(context, "Failed to load drivers: ${driverResult.message}", Toast.LENGTH_SHORT).show()
                }
                is DriverResult.Loading -> {}
            }
            
            isLoading = false
        }
    }
    
    val allAssigned = driverAssignments.size == vehicles.size
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // Top Bar
        PrimaryTopBar(
            title = "Assign Drivers",
            onBackClick = onNavigateBack
        )
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Instructions Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(InfoLight)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                modifier = Modifier.size(32.dp),
                                tint = Info
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Assign one driver per truck",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Drivers will receive notifications once assigned",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                
                // Progress Indicator
                item {
                    LinearProgressIndicator(
                        progress = driverAssignments.size.toFloat() / vehicles.size,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Primary,
                        trackColor = Surface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${driverAssignments.size}/${vehicles.size} drivers assigned",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                // Truck List with Driver Assignment
                item {
                    Text(
                        "Your Trucks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                itemsIndexed(vehicles) { index, vehicle ->
                    VehicleDriverAssignmentCard(
                        vehicle = vehicle,
                        assignedDriver = driverAssignments[vehicle.id]?.let { driverId ->
                            availableDrivers.find { it.id == driverId }
                        },
                        onAssignDriver = { showDriverPicker = vehicle.id },
                        onRemoveDriver = {
                            driverAssignments = driverAssignments - vehicle.id
                        },
                        index = index + 1
                    )
                }
                
                // Available Drivers Info
                if (availableDrivers.isNotEmpty()) {
                    item {
                        Text(
                            "Available Drivers (${availableDrivers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
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
                    if (!allAssigned) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(WarningLight)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, null, tint = Warning, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${vehicles.size - driverAssignments.size} trucks still need drivers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    
                    // Confirm Button - Calls acceptBroadcast API for each vehicle+driver pair
                    Button(
                        onClick = {
                            isSubmitting = true
                            successCount = 0
                            
                            scope.launch {
                                var hasError = false
                                
                                // Call acceptBroadcast for each vehicle+driver assignment
                                for ((vehicleId, driverId) in driverAssignments) {
                                    android.util.Log.i("DriverAssignmentScreen", 
                                        "📤 Accepting broadcast: $broadcastId with vehicle: $vehicleId, driver: $driverId")
                                    
                                    when (val result = broadcastRepository.acceptBroadcast(
                                        broadcastId = broadcastId,
                                        vehicleId = vehicleId,
                                        driverId = driverId
                                    )) {
                                        is BroadcastResult.Success -> {
                                            successCount++
                                            android.util.Log.i("DriverAssignmentScreen", 
                                                "✅ Assignment successful: ${result.data.assignmentId}")
                                        }
                                        is BroadcastResult.Error -> {
                                            hasError = true
                                            android.util.Log.e("DriverAssignmentScreen", 
                                                "❌ Assignment failed: ${result.message}")
                                            Toast.makeText(context, 
                                                "Failed to assign vehicle: ${result.message}", 
                                                Toast.LENGTH_SHORT).show()
                                        }
                                        is BroadcastResult.Loading -> {}
                                    }
                                }
                                
                                isSubmitting = false
                                
                                if (successCount > 0) {
                                    showSuccessDialog = true
                                } else if (hasError) {
                                    Toast.makeText(context, 
                                        "Failed to assign vehicles. Please try again.", 
                                        Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = allAssigned && !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Assigning...",
                                style = MaterialTheme.typography.titleMedium
                            )
                        } else {
                            Icon(Icons.Default.Send, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Send to Drivers (${driverAssignments.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Driver Picker Bottom Sheet
    showDriverPicker?.let { vehicleId ->
        DriverPickerBottomSheet(
            availableDrivers = availableDrivers.filter { driver ->
                !driverAssignments.values.contains(driver.id) // Don't show already assigned drivers
            },
            onDriverSelected = { driver ->
                driverAssignments = driverAssignments + (vehicleId to driver.id)
                showDriverPicker = null
            },
            onDismiss = { showDriverPicker = null }
        )
    }
    
    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = { 
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = Success
                )
            },
            title = { 
                Text(
                    "Assignments Sent!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("${driverAssignments.size} drivers have been notified")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Drivers will receive notifications on their devices and can accept or decline the trip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You can track their responses in the Trip Details screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onNavigateToTracking() },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("View Trip Status")
                }
            }
        )
    }
}

/**
 * VEHICLE DRIVER ASSIGNMENT CARD
 * Shows truck with assigned driver or assignment button
 */
@Composable
fun VehicleDriverAssignmentCard(
    vehicle: Vehicle,
    assignedDriver: Driver?,
    onAssignDriver: () -> Unit,
    onRemoveDriver: () -> Unit,
    index: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (assignedDriver != null)
                    Modifier.border(2.dp, Success, RoundedCornerShape(12.dp))
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (assignedDriver != null) Success.copy(alpha = 0.05f) else White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Truck Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Number Badge
                Surface(
                    shape = CircleShape,
                    color = Primary
                ) {
                    Text(
                        "$index",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
                
                Spacer(Modifier.width(12.dp))
                
                Icon(
                    Icons.Default.LocalShipping,
                    null,
                    modifier = Modifier.size(32.dp),
                    tint = Primary
                )
                
                Spacer(Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        vehicle.vehicleNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        vehicle.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                // Status Badge
                if (assignedDriver != null) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = Success,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            
            // Assigned Driver or Assignment Button
            if (assignedDriver != null) {
                // Show assigned driver
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Driver Avatar
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Success.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            tint = Success,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            assignedDriver.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = Warning
                            )
                            Text(
                                " ${assignedDriver.rating} • ${assignedDriver.totalTrips} trips",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    
                    IconButton(onClick = onRemoveDriver) {
                        Icon(Icons.Default.Close, "Remove", tint = Error)
                    }
                }
            } else {
                // Assign Driver Button
                OutlinedButton(
                    onClick = onAssignDriver,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Primary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Assign Driver",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * DRIVER PICKER BOTTOM SHEET
 * Modal to select driver from available list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverPickerBottomSheet(
    availableDrivers: List<Driver>,
    onDriverSelected: (Driver) -> Unit,
    onDismiss: () -> Unit
) {
    var showSheet by remember { mutableStateOf(true) }
    
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                onDismiss()
            },
            containerColor = White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Select Driver",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    "${availableDrivers.size} drivers available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                Spacer(Modifier.height(16.dp))
                
                if (availableDrivers.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(WarningLight)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Warning, null, tint = Warning, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No available drivers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "All drivers are assigned or unavailable",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // OPTIMIZATION: Add keys to prevent unnecessary recompositions
                        items(
                            items = availableDrivers,
                            key = { it.id }
                        ) { driver ->
                            DriverSelectCard(
                                driver = driver,
                                onClick = {
                                    onDriverSelected(driver)
                                    showSheet = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/**
 * DRIVER SELECT CARD - Driver option in bottom sheet
 */
@Composable
fun DriverSelectCard(
    driver: Driver,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = Primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    driver.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    driver.mobileNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp), tint = Warning)
                    Text(
                        " ${driver.rating} • ${driver.totalTrips} trips",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            Icon(
                Icons.Default.ArrowForward,
                null,
                tint = Primary
            )
        }
    }
}
