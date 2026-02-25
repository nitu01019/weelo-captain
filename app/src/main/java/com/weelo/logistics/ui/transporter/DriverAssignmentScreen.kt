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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.R
import com.weelo.logistics.broadcast.BroadcastAssignmentCoordinator
import com.weelo.logistics.broadcast.BroadcastAssignmentResult
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.data.repository.DriverRepository
import com.weelo.logistics.data.repository.DriverResult
import com.weelo.logistics.data.repository.VehicleRepository
import com.weelo.logistics.data.repository.VehicleResult
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

private fun DriverAssignmentAvailability.chipStatus(): ChipStatus {
    return when (this) {
        DriverAssignmentAvailability.ACTIVE -> ChipStatus.AVAILABLE
        DriverAssignmentAvailability.OFFLINE -> ChipStatus.OFFLINE
        DriverAssignmentAvailability.ON_TRIP -> ChipStatus.IN_PROGRESS
    }
}

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
 * - Fetches all drivers via DriverRepository
 * - Calls acceptBroadcast API to assign each vehicle+driver
 */
@Composable
fun DriverAssignmentScreen(
    broadcastId: String,
    holdId: String,
    requiredVehicleType: String,
    requiredVehicleSubtype: String,
    requiredQuantity: Int,
    preselectedVehicleIds: List<String> = emptyList(),
    onNavigateBack: () -> Unit,
    onNavigateToTracking: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Real repositories connected to backend
    val broadcastRepository = remember { BroadcastRepository.getInstance(context) }
    val assignmentCoordinator = remember { BroadcastAssignmentCoordinator(broadcastRepository) }
    val vehicleRepository = remember { VehicleRepository.getInstance(context) }
    val driverRepository = remember { DriverRepository.getInstance(context) }
    
    var broadcast by remember { mutableStateOf<BroadcastTrip?>(null) }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var fleetDrivers by remember { mutableStateOf<List<Driver>>(emptyList()) }
    var driverAssignments by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // vehicleId -> driverId
    var showDriverPicker by remember { mutableStateOf<String?>(null) } // vehicleId being assigned
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successCount by remember { mutableStateOf(0) }
    val fleetDriversById = remember(fleetDrivers) { fleetDrivers.associateBy { it.id } }
    
    val resolvedHoldId = remember(holdId) { holdId.trim().takeUnless { it.isBlank() } }

    // Fetch data from real backend
    LaunchedEffect(broadcastId, holdId, requiredVehicleType, requiredVehicleSubtype, requiredQuantity, preselectedVehicleIds) {
        scope.launch {
            isLoading = true
            errorMessage = null

            if (preselectedVehicleIds.isEmpty() && resolvedHoldId == null) {
                errorMessage = "Missing hold context. Reopen the request from broadcast list."
                isLoading = false
                return@launch
            }
            
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
            
            // Fetch selected/matching vehicles
            when (val vehicleResult = vehicleRepository.fetchVehicles(forceRefresh = false)) {
                is VehicleResult.Success -> {
                    val mappedVehicles = vehicleRepository.mapToUiModels(vehicleResult.data.vehicles)
                    val availableVehicles = mappedVehicles.filter { it.status == VehicleStatus.AVAILABLE }

                    vehicles = if (preselectedVehicleIds.isNotEmpty()) {
                        availableVehicles.filter { it.id in preselectedVehicleIds }
                    } else {
                        val normalizedType = requiredVehicleType.trim().lowercase()
                        val normalizedSubtype = requiredVehicleSubtype.trim().lowercase()
                        val targetCount = requiredQuantity.coerceAtLeast(1)

                        availableVehicles
                            .filter { vehicle ->
                                val vehicleTypeAliases = setOf(
                                    vehicle.category.id.trim().lowercase(),
                                    vehicle.category.name.trim().lowercase(),
                                    vehicle.category.name.removeSuffix(" Truck").trim().lowercase()
                                )
                                val typeMatches = normalizedType.isBlank() ||
                                    normalizedType == "_" ||
                                    vehicleTypeAliases.contains(normalizedType)
                                val subtypeMatches = normalizedSubtype.isBlank() ||
                                    normalizedSubtype == "_" ||
                                    vehicle.subtype.name.trim().lowercase() == normalizedSubtype
                                typeMatches && subtypeMatches
                            }
                            .take(targetCount)
                    }

                    if (vehicles.isEmpty()) {
                        errorMessage = "No available vehicles matched this hold."
                    } else if (preselectedVehicleIds.isEmpty() && vehicles.size < requiredQuantity.coerceAtLeast(1)) {
                        errorMessage = "Only ${vehicles.size} matching vehicle(s) available for required quantity ${requiredQuantity.coerceAtLeast(1)}."
                    }
                }
                is VehicleResult.Error -> {
                    errorMessage = vehicleResult.message
                }
                is VehicleResult.Loading -> {}
            }
            
            // Fetch drivers for assignment
            when (val driverResult = driverRepository.fetchDrivers(forceRefresh = true)) {
                is DriverResult.Success -> {
                    fleetDrivers = driverResult.data.drivers.sortedWith(compareBy<Driver>(
                        { it.assignmentAvailability().sortOrder() },
                        { it.name.lowercase() }
                    ))
                    val activeCount = fleetDrivers.count { it.assignmentAvailability() == DriverAssignmentAvailability.ACTIVE }
                    val offlineCount = fleetDrivers.count { it.assignmentAvailability() == DriverAssignmentAvailability.OFFLINE }
                    val onTripCount = fleetDrivers.count { it.assignmentAvailability() == DriverAssignmentAvailability.ON_TRIP }
                    timber.log.Timber.i(
                        "✅ Loaded ${fleetDrivers.size} drivers (active=$activeCount, offline=$offlineCount, onTrip=$onTripCount)"
                    )
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
    
    // =========================================================================
    // REAL-TIME: Listen for driver online/offline changes during assignment
    // Same pattern as DriverListScreen — update in-place, no API call
    //
    // EDGE CASE: If an ASSIGNED driver goes offline, the assignment becomes
    // invalid (readyAssignments auto-excludes offline drivers). We show a
    // toast so the transporter knows why their progress dropped.
    // =========================================================================
    LaunchedEffect(Unit) {
        SocketIOService.driverStatusChanged.collect { event ->
            timber.log.Timber.i("📡 [DriverAssignment] Real-time status: ${event.driverName} → ${if (event.isOnline) "ONLINE" else "OFFLINE"}")
            
            // Check if this driver is currently assigned to a vehicle
            if (!event.isOnline) {
                val isAssigned = driverAssignments.values.contains(event.driverId)
                if (isAssigned) {
                    timber.log.Timber.w("⚠️ Assigned driver ${event.driverName} went OFFLINE — assignment invalidated")
                    Toast.makeText(
                        context,
                        "${event.driverName} went offline. Please reassign.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            
            fleetDrivers = fleetDrivers.map { driver ->
                if (driver.id == event.driverId) {
                    driver.copy(isAvailable = event.isOnline)
                } else {
                    driver
                }
            }.sortedWith(compareBy<Driver>(
                { it.assignmentAvailability().sortOrder() },
                { it.name.lowercase() }
            ))
        }
    }
    
    val readyAssignments = remember(vehicles, driverAssignments, fleetDrivers) {
        vehicles.mapNotNull { vehicle ->
            val driverId = driverAssignments[vehicle.id] ?: return@mapNotNull null
            val driver = fleetDriversById[driverId] ?: return@mapNotNull null
            if (!driver.assignmentAvailability().isSelectableForAssignment()) return@mapNotNull null
            vehicle.id to driverId
        }
    }
    val allAssigned = readyAssignments.size == vehicles.size
    val pendingAssignments = (vehicles.size - readyAssignments.size).coerceAtLeast(0)
    val hasOnTripSelections = driverAssignments.values.any { driverId ->
        val driver = fleetDriversById[driverId] ?: return@any false
        driver.assignmentAvailability() == DriverAssignmentAvailability.ON_TRIP
    }
    
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
            ProvideShimmerBrush {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionSkeletonBlock(titleLineWidthFraction = 0.44f, rowCount = 2)
                    SectionSkeletonBlock(titleLineWidthFraction = 0.36f, rowCount = 3, showLeadingAvatar = true)
                    SkeletonList(itemCount = 3)
                }
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
                        progress = readyAssignments.size.toFloat() / vehicles.size.coerceAtLeast(1),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Primary,
                        trackColor = Surface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${readyAssignments.size}/${vehicles.size} drivers ready",
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
                
                itemsIndexed(
                    items = vehicles,
                    key = { _, vehicle -> vehicle.id }
                ) { index, vehicle ->
                    VehicleDriverAssignmentCard(
                        vehicle = vehicle,
                        assignedDriver = driverAssignments[vehicle.id]?.let { driverId ->
                            fleetDriversById[driverId]
                        },
                        onAssignDriver = { showDriverPicker = vehicle.id },
                        onRemoveDriver = {
                            driverAssignments = driverAssignments - vehicle.id
                        },
                        index = index + 1
                    )
                }
                
                // Available Drivers Info
                if (fleetDrivers.isNotEmpty()) {
                    item {
                        Text(
                            "Drivers (${fleetDrivers.size})",
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
                                    if (hasOnTripSelections) {
                                        "Reassign on-trip drivers before sending"
                                    } else {
                                        "$pendingAssignments trucks still need valid drivers"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    
                    // Confirm Button - canonical confirm-with-assignments flow
                    Button(
                        onClick = {
                            if (isSubmitting) return@Button
                            val submissionAssignments = readyAssignments
                            if (submissionAssignments.isEmpty()) return@Button
                            val currentHoldId = resolvedHoldId
                            if (currentHoldId == null) {
                                Toast.makeText(
                                    context,
                                    "Missing hold context. Reopen request from broadcast list.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }

                            isSubmitting = true
                            successCount = submissionAssignments.size
                            
                            scope.launch {
                                when (
                                    val result = assignmentCoordinator.confirmHoldAssignments(
                                        broadcastId = broadcastId,
                                        holdId = currentHoldId,
                                        assignments = submissionAssignments
                                    )
                                ) {
                                    is BroadcastAssignmentResult.Success -> {
                                        timber.log.Timber.i(
                                            "✅ Assignment successful: assignments=%s trips=%s",
                                            result.assignmentIds.size,
                                            result.tripIds.size
                                        )
                                        showSuccessDialog = true
                                    }

                                    is BroadcastAssignmentResult.Error -> {
                                        timber.log.Timber.e("❌ Assignment failed: %s", result.message)
                                        Toast.makeText(
                                            context,
                                            "Failed to assign vehicles: ${result.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }

                                isSubmitting = false
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
                                "Send to Drivers (${readyAssignments.size})",
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
            drivers = fleetDrivers.filter { driver ->
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
                    Text("${readyAssignments.size} drivers have been notified")
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
                val assignedStatus = assignedDriver.assignmentAvailability()
                val assignedStatusColor = when (assignedStatus) {
                    DriverAssignmentAvailability.ACTIVE -> Success
                    DriverAssignmentAvailability.OFFLINE -> TextSecondary
                    DriverAssignmentAvailability.ON_TRIP -> Warning
                }

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
                            .background(assignedStatusColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            tint = assignedStatusColor,
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
                        Spacer(Modifier.height(4.dp))
                        StatusChip(
                            text = assignedStatus.displayName(),
                            status = assignedStatus.chipStatus(),
                            size = ChipSize.SMALL
                        )
                        Spacer(Modifier.height(4.dp))
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
    drivers: List<Driver>,
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
                    "${drivers.size} drivers shown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                Spacer(Modifier.height(16.dp))
                
                if (drivers.isEmpty()) {
                    IllustratedEmptyState(
                        illustrationRes = EmptyStateArtwork.ASSIGNMENT_BUSY_DRIVERS.drawableRes,
                        title = stringResource(R.string.empty_title_no_assignment_drivers),
                        subtitle = stringResource(R.string.empty_subtitle_no_assignment_drivers),
                        maxIllustrationWidthDp = EmptyStateLayoutStyle.MODAL_COMPACT.maxIllustrationWidthDp,
                        maxTextWidthDp = EmptyStateLayoutStyle.MODAL_COMPACT.maxTextWidthDp,
                        showFramedIllustration = EmptyStateLayoutStyle.MODAL_COMPACT.showFramedIllustration,
                        sectionBackgroundColor = EmptyStateArtwork.ASSIGNMENT_BUSY_DRIVERS.blendPalette().sectionBackground,
                        sectionBlendMode = SectionBlendMode.PANEL,
                        paletteHaloOverride = EmptyStateArtwork.ASSIGNMENT_BUSY_DRIVERS.blendPalette().haloColor,
                        paletteHaloAlphaOverride = EmptyStateArtwork.ASSIGNMENT_BUSY_DRIVERS.blendPalette().haloAlpha
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // OPTIMIZATION: Add keys to prevent unnecessary recompositions
                        items(
                            items = drivers,
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
    val driverStatus = driver.assignmentAvailability()
    val isSelectable = driverStatus.isSelectableForAssignment()

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (isSelectable) onClick() },
        enabled = isSelectable,
        colors = CardDefaults.cardColors(
            if (isSelectable) White else SurfaceVariant
        ),
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
                    .background(
                        when (driverStatus) {
                            DriverAssignmentAvailability.ACTIVE -> Success.copy(alpha = 0.12f)
                            DriverAssignmentAvailability.OFFLINE -> TextSecondary.copy(alpha = 0.12f)
                            DriverAssignmentAvailability.ON_TRIP -> Warning.copy(alpha = 0.12f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = when (driverStatus) {
                        DriverAssignmentAvailability.ACTIVE -> Success
                        DriverAssignmentAvailability.OFFLINE -> TextSecondary
                        DriverAssignmentAvailability.ON_TRIP -> Warning
                    },
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    driver.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelectable) TextPrimary else TextSecondary
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
                Spacer(Modifier.height(6.dp))
                StatusChip(
                    text = driverStatus.displayName(),
                    status = driverStatus.chipStatus(),
                    size = ChipSize.SMALL
                )
                if (!isSelectable) {
                    val blockedReason = when (driverStatus) {
                        DriverAssignmentAvailability.ON_TRIP -> "On trip, cannot assign right now"
                        DriverAssignmentAvailability.OFFLINE -> "Offline, cannot assign right now"
                        DriverAssignmentAvailability.ACTIVE -> "Unavailable for assignment"
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        blockedReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = Warning
                    )
                }
            }
            
            Icon(
                if (isSelectable) Icons.Default.ArrowForward else Icons.Default.Block,
                null,
                tint = if (isSelectable) Primary else Warning
            )
        }
    }
}
