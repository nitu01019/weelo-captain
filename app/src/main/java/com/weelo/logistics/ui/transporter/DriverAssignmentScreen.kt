package com.weelo.logistics.ui.transporter

import android.os.SystemClock
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
import com.weelo.logistics.broadcast.BroadcastMiniRouteMapCard
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.data.repository.DriverRepository
import com.weelo.logistics.data.repository.DriverResult
import com.weelo.logistics.data.repository.VehicleRepository
import com.weelo.logistics.data.repository.VehicleResult
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.ui.ServerDeadlineTimer
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.ui.viewmodel.DriverAssignmentViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onNavigateToTracking: (assignmentId: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assignmentViewModel: DriverAssignmentViewModel = viewModel()

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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val fleetDriversById = remember(fleetDrivers) { fleetDrivers.associateBy { it.id } }
    
    val resolvedHoldId = remember(holdId) { holdId.trim().takeUnless { it.isBlank() } }

    // ── Hold expiry countdown (Phase 2 timer) ──
    // Backend hold is 180s total. Phase 1 (VehicleHoldConfirmScreen) used some of that.
    // Here we show remaining time so transporter knows how long they have to assign drivers.
    var holdRemainingSeconds by remember { mutableStateOf(-1) } // -1 = not loaded yet
    var holdExpired by remember { mutableStateOf(false) }

    LaunchedEffect(resolvedHoldId) {
        if (resolvedHoldId == null) return@LaunchedEffect
        try {
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.weelo.logistics.data.remote.RetrofitClient.truckHoldApi.getMyActiveHold(
                    orderId = broadcastId,
                    vehicleType = requiredVehicleType,
                    vehicleSubtype = requiredVehicleSubtype
                )
            }
            val expiresAtStr = response.body()?.data?.expiresAt
            if (expiresAtStr != null) {
                val expiresAtMs = try {
                    java.time.Instant.parse(expiresAtStr).toEpochMilli()
                } catch (_: Exception) { 0L }
                if (expiresAtMs > 0) {
                    // F-C-27: pin a monotonic deadline and recompute every tick.
                    // Doze-safe because SystemClock.elapsedRealtime keeps ticking in sleep
                    // and isn't affected by wall-clock jumps.
                    val deadlineElapsedMs = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
                        expiresAtWallMs = expiresAtMs,
                        nowWallMs = System.currentTimeMillis(),
                        nowElapsedMs = SystemClock.elapsedRealtime()
                    )
                    holdRemainingSeconds = ServerDeadlineTimer.remainingSecondsFromDeadline(
                        deadlineElapsedMs = deadlineElapsedMs,
                        nowElapsedMs = SystemClock.elapsedRealtime()
                    )
                    while (holdRemainingSeconds > 0 && !isSubmitting) {
                        kotlinx.coroutines.delay(500)
                        holdRemainingSeconds = ServerDeadlineTimer.remainingSecondsFromDeadline(
                            deadlineElapsedMs = deadlineElapsedMs,
                            nowElapsedMs = SystemClock.elapsedRealtime()
                        )
                    }
                    if (holdRemainingSeconds <= 0 && !isSubmitting) {
                        holdExpired = true
                    }
                }
            }
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to fetch hold expiry — timer hidden")
        }
    }

    // Auto-navigate back when hold expires
    LaunchedEffect(holdExpired) {
        if (holdExpired && !isSubmitting) {
            android.widget.Toast.makeText(context, "Hold expired. Trucks released.", android.widget.Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }


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
                // Hold Expiry Timer (visible during Phase 2)
                if (holdRemainingSeconds >= 0) {
                    item {
                        val timerColor = when {
                            holdRemainingSeconds > 30 -> Success
                            holdRemainingSeconds > 10 -> Warning
                            else -> Error
                        }
                        val minutes = holdRemainingSeconds / 60
                        val seconds = holdRemainingSeconds % 60
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(timerColor.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = timerColor
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Hold expires in ${minutes}:${"%02d".format(seconds)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = timerColor
                                )
                            }
                        }
                    }
                }

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

                broadcast?.let { currentBroadcast ->
                    item {
                        BroadcastMiniRouteMapCard(
                            broadcast = currentBroadcast,
                            title = "Route map",
                            subtitle = "${currentBroadcast.distance.toInt()} km",
                            mapHeight = 128.dp,
                            renderMode = BroadcastCardMapRenderMode.STATIC_CARD
                        )
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
                                        Toast.makeText(
                                            context,
                                            "✅ ${result.assignmentIds.size} driver(s) assigned successfully",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        // Navigate directly to real-time driver response tracking
                                        val targetAssignmentId = result.assignmentIds.firstOrNull()
                                        if (targetAssignmentId != null) {
                                            onNavigateToTracking(targetAssignmentId)
                                        } else {
                                            timber.log.Timber.w("⚠️ No assignmentId returned — navigating back")
                                            onNavigateBack()
                                        }
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
                // Industry standard: show ONLY online + available drivers.
                // Offline and on-trip drivers are hidden — not selectable at backend level anyway.
                // The real-time driverStatusChanged listener (above) keeps this list fresh.
                !driverAssignments.values.contains(driver.id) &&
                driver.assignmentAvailability() == DriverAssignmentAvailability.ACTIVE
            },
            onDriverSelected = { driver ->
                driverAssignments = driverAssignments + (vehicleId to driver.id)
                showDriverPicker = null
            },
            onDismiss = { showDriverPicker = null }
        )
    }
    

}


// VehicleDriverAssignmentCard, DriverPickerBottomSheet, DriverSelectCard
// extracted to DriverAssignmentParts.kt for 800-line compliance.
