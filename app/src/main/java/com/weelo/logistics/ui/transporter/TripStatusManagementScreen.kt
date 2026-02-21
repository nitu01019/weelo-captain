package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.repository.AssignmentRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * TRIP STATUS MANAGEMENT SCREEN - Transporter View
 * =================================================
 * Shows status of all driver assignments for a broadcast/trip.
 * Transporter can see who accepted, who declined, and reassign if needed.
 * 
 * FLOW:
 * 1. After sending assignments to drivers
 * 2. Shows real-time status of each driver's response
 * 3. Green = Accepted, Red = Declined, Yellow = Pending
 * 4. If declined → Shows "Reassign Driver" button
 * 5. Tracks trip progress once drivers accept
 * 
 * FOR BACKEND DEVELOPER:
 * - Real-time updates via WebSocket/polling
 * - Fetch TripAssignment with all DriverTruckAssignment statuses
 * - Show driver response status changes in real-time
 * - Allow reassignment for declined drivers
 * - Update UI when driver accepts/declines
 */
@Composable
fun TripStatusManagementScreen(
    assignmentId: String,
    onNavigateBack: () -> Unit,
    onNavigateToReassign: (String, String) -> Unit,  // assignmentId, vehicleId
    onNavigateToTracking: (String) -> Unit  // driverId
) {
    var assignment by remember { mutableStateOf<TripAssignment?>(null) }
    var broadcast by remember { mutableStateOf<BroadcastTrip?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Shared refresh function — used by initial load AND refresh button
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val doRefresh: () -> Unit = remember(assignmentId) {
        {
            scope.launch {
                isRefreshing = true
                try {
                    val token = RetrofitClient.getAccessToken() ?: run {
                        Timber.w("TripStatus: No auth token — skipping fetch")
                        return@launch
                    }
                    val response = RetrofitClient.assignmentApi.getAssignmentById(
                        "Bearer $token", assignmentId
                    )
                    if (response.isSuccessful && response.body()?.success == true) {
                        val data = response.body()?.data
                        if (data != null) {
                            // Map AssignmentData → TripAssignment for UI
                            val pickup = com.weelo.logistics.data.model.Location(
                                address = data.pickupAddress ?: "",
                                latitude = data.pickupLat ?: 0.0,
                                longitude = data.pickupLng ?: 0.0,
                                city = null
                            )
                            val drop = com.weelo.logistics.data.model.Location(
                                address = data.dropAddress ?: "",
                                latitude = data.dropLat ?: 0.0,
                                longitude = data.dropLng ?: 0.0,
                                city = null
                            )
                            val driverAssignment = com.weelo.logistics.data.model.DriverTruckAssignment(
                                driverId = data.driverId,
                                driverName = data.driverName,
                                vehicleId = data.vehicleId ?: "",
                                vehicleNumber = data.vehicleNumber,
                                status = com.weelo.logistics.data.model.DriverResponseStatus.PENDING
                            )
                            val statusEnum = when (data.status.lowercase()) {
                                "driver_accepted", "accepted" -> com.weelo.logistics.data.model.AssignmentStatus.DRIVER_ACCEPTED
                                "driver_declined", "declined" -> com.weelo.logistics.data.model.AssignmentStatus.DRIVER_DECLINED
                                "in_transit", "in_progress", "trip_started" -> com.weelo.logistics.data.model.AssignmentStatus.TRIP_STARTED
                                "completed", "trip_completed" -> com.weelo.logistics.data.model.AssignmentStatus.TRIP_COMPLETED
                                "cancelled" -> com.weelo.logistics.data.model.AssignmentStatus.CANCELLED
                                else -> com.weelo.logistics.data.model.AssignmentStatus.PENDING_DRIVER_RESPONSE
                            }
                            assignment = com.weelo.logistics.data.model.TripAssignment(
                                assignmentId = data.id,
                                broadcastId = data.bookingId,
                                transporterId = data.transporterId,
                                trucksTaken = 1,
                                assignments = listOf(driverAssignment),
                                pickupLocation = pickup,
                                dropLocation = drop,
                                distance = data.distanceKm ?: 0.0,
                                farePerTruck = data.pricePerTruck ?: 0.0,
                                goodsType = data.goodsType ?: "General",
                                currentRouteIndex = data.currentRouteIndex ?: 0,
                                status = statusEnum
                            )
                            Timber.d("TripStatus: Assignment mapped for $assignmentId — status=${data.status}")
                        } else {
                            Timber.w("TripStatus: Response success but data is null for $assignmentId")
                        }
                    } else {
                        Timber.w("TripStatus: Fetch failed ${response.code()}")
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "TripStatus: Fetch error")
                } finally {
                    isRefreshing = false
                    isLoading = false
                }
            }
            Unit
        }
    }

    // Initial load
    LaunchedEffect(assignmentId) { doRefresh() }

    // Auto-poll every 5 seconds for real-time driver responses
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            doRefresh()
        }
    }
    
    // =========================================================================
    // REAL-TIME WEBSOCKET UPDATES
    // =========================================================================
    // Listen for driver accept/decline/timeout events via WebSocket
    // Updates assignment cards immediately without waiting for polling
    // SCALABILITY: WebSocket events arrive in real-time across ECS instances
    // =========================================================================
    
    // Listen for assignment status changes (accept/decline)
    LaunchedEffect(Unit) {
        SocketIOService.assignmentStatusChanged.collect { notification ->
            Timber.i("📋 Real-time status update: ${notification.assignmentId} → ${notification.status}")
            
            // Update matching assignment — match by driverId OR vehicleNumber
            // NOT notification.assignmentId (that's the parent assignment, not the driver)
            assignment?.let { currentAssignment ->
                val updatedAssignments = currentAssignment.assignments.map { driverAssignment ->
                    // Match by vehicleNumber — most reliable key available in both models
                    if (driverAssignment.vehicleNumber == notification.vehicleNumber) {
                        driverAssignment.copy(
                            status = when (notification.status) {
                                "driver_accepted" -> DriverResponseStatus.ACCEPTED
                                "driver_declined" -> DriverResponseStatus.DECLINED
                                "expired" -> DriverResponseStatus.EXPIRED
                                "cancelled" -> DriverResponseStatus.DECLINED
                                else -> driverAssignment.status
                            }
                        )
                    } else driverAssignment
                }
                assignment = currentAssignment.copy(assignments = updatedAssignments)
            }
        }
    }
    
    // Listen for driver timeout events (driver didn't respond in 60s)
    LaunchedEffect(Unit) {
        SocketIOService.driverTimeout.collect { notification ->
            Timber.w("⏰ Driver timeout: ${notification.driverName} (${notification.vehicleNumber})")
            
            // Update matching assignment to EXPIRED status
            assignment?.let { currentAssignment ->
                val updatedAssignments = currentAssignment.assignments.map { driverAssignment ->
                    if (driverAssignment.driverId == notification.driverId ||
                        driverAssignment.vehicleNumber == notification.vehicleNumber) {
                        driverAssignment.copy(status = DriverResponseStatus.EXPIRED)
                    } else driverAssignment
                }
                assignment = currentAssignment.copy(assignments = updatedAssignments)
            }
        }
    }
    
    val acceptedCount = assignment?.assignments?.count { it.status == DriverResponseStatus.ACCEPTED } ?: 0
    val pendingCount = assignment?.assignments?.count { it.status == DriverResponseStatus.PENDING } ?: 0
    val declinedCount = assignment?.assignments?.count { it.status == DriverResponseStatus.DECLINED } ?: 0
    val timedOutCount = assignment?.assignments?.count { it.status == DriverResponseStatus.EXPIRED } ?: 0
    val totalCount = assignment?.assignments?.size ?: 0
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // Top Bar
        PrimaryTopBar(
            title = "Trip Status",
            onBackClick = onNavigateBack,
            actions = {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = White,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = { doRefresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = White)
                    }
                }
            }
        )
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(Primary.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                "Assignment Summary",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Progress Bar — zero-guard prevents NaN/Infinity when totalCount=0
                            val safeProgress = if (totalCount > 0) acceptedCount.toFloat() / totalCount else 0f
                            LinearProgressIndicator(
                                progress = safeProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                                color = Success,
                                trackColor = Surface
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatusCountChip(
                                    count = acceptedCount,
                                    label = "Accepted",
                                    color = Success
                                )
                                StatusCountChip(
                                    count = pendingCount,
                                    label = "Pending",
                                    color = Warning
                                )
                                StatusCountChip(
                                    count = declinedCount,
                                    label = "Declined",
                                    color = Error
                                )
                                if (timedOutCount > 0) {
                                    StatusCountChip(
                                        count = timedOutCount,
                                        label = "Timed Out",
                                        color = Warning
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Broadcast Details
                broadcast?.let { bc ->
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "Trip Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Customer", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text(bc.customerName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Fare per truck", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("₹${String.format("%.0f", bc.farePerTruck)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Success)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${bc.pickupLocation.city} → ${bc.dropLocation.city} • ${bc.distance} km",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                
                // Driver Assignments Header
                item {
                    Text(
                        "Driver Assignments ($totalCount)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Driver Assignment Cards
                assignment?.assignments?.let { assignments ->
                    items(
                        items = assignments,
                        key = { it.driverId }
                    ) { driverAssignment ->
                        DriverAssignmentStatusCard(
                            assignment = driverAssignment,
                            onReassignClick = {
                                onNavigateToReassign(assignmentId, driverAssignment.vehicleId)
                            },
                            onTrackClick = {
                                onNavigateToTracking(driverAssignment.driverId)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * STATUS COUNT CHIP - Shows count with colored badge
 */
@Composable
fun StatusCountChip(
    count: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * DRIVER ASSIGNMENT STATUS CARD
 * Shows individual driver with their response status
 * 
 * STATUSES:
 * - PENDING → Yellow, spinner, "Waiting for driver response..."
 * - ACCEPTED → Green, "Driver accepted, trip started" + Track button
 * - DECLINED → Red, "Driver rejected" + Reassign button
 * - EXPIRED → Orange, "No action from driver" + Reassign/Retry buttons
 * - REASSIGNED → Gray, "Reassigned to another driver"
 * 
 * SCALABILITY: Updates in real-time via WebSocket events from backend
 * EASY UNDERSTANDING: Color-coded cards with clear action buttons
 * MODULARITY: Self-contained composable, works with any DriverTruckAssignment
 */
@Composable
fun DriverAssignmentStatusCard(
    assignment: DriverTruckAssignment,
    onReassignClick: () -> Unit,
    onTrackClick: () -> Unit,
    onRetryClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (assignment.status) {
                DriverResponseStatus.ACCEPTED -> Success.copy(alpha = 0.05f)
                DriverResponseStatus.DECLINED -> Error.copy(alpha = 0.05f)
                DriverResponseStatus.PENDING -> Warning.copy(alpha = 0.05f)
                DriverResponseStatus.EXPIRED -> Warning.copy(alpha = 0.08f)
                DriverResponseStatus.REASSIGNED -> Surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            when (assignment.status) {
                                DriverResponseStatus.ACCEPTED -> Success.copy(alpha = 0.2f)
                                DriverResponseStatus.DECLINED -> Error.copy(alpha = 0.2f)
                                DriverResponseStatus.PENDING -> Warning.copy(alpha = 0.2f)
                                DriverResponseStatus.EXPIRED -> Warning.copy(alpha = 0.3f)
                                DriverResponseStatus.REASSIGNED -> TextDisabled.copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (assignment.status) {
                            DriverResponseStatus.ACCEPTED -> Icons.Default.CheckCircle
                            DriverResponseStatus.DECLINED -> Icons.Default.Cancel
                            DriverResponseStatus.PENDING -> Icons.Default.HourglassEmpty
                            DriverResponseStatus.EXPIRED -> Icons.Default.TimerOff
                            DriverResponseStatus.REASSIGNED -> Icons.Default.SwapHoriz
                        },
                        null,
                        tint = when (assignment.status) {
                            DriverResponseStatus.ACCEPTED -> Success
                            DriverResponseStatus.DECLINED -> Error
                            DriverResponseStatus.PENDING -> Warning
                            DriverResponseStatus.EXPIRED -> Warning
                            DriverResponseStatus.REASSIGNED -> TextDisabled
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        assignment.driverName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        assignment.vehicleNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = when (assignment.status) {
                            DriverResponseStatus.ACCEPTED -> Success
                            DriverResponseStatus.DECLINED -> Error
                            DriverResponseStatus.PENDING -> Warning
                            DriverResponseStatus.EXPIRED -> Warning
                            DriverResponseStatus.REASSIGNED -> TextDisabled
                        }
                    ) {
                        Text(
                            when (assignment.status) {
                                DriverResponseStatus.ACCEPTED -> "ACCEPTED"
                                DriverResponseStatus.DECLINED -> "DECLINED"
                                DriverResponseStatus.PENDING -> "WAITING..."
                                DriverResponseStatus.EXPIRED -> "NO RESPONSE"
                                DriverResponseStatus.REASSIGNED -> "REASSIGNED"
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                }
            }
            
            // Status Description Text
            when (assignment.status) {
                DriverResponseStatus.ACCEPTED -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "✅ Driver accepted — trip started",
                        style = MaterialTheme.typography.bodySmall,
                        color = Success,
                        fontWeight = FontWeight.Medium
                    )
                }
                DriverResponseStatus.DECLINED -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "❌ Driver rejected this trip",
                        style = MaterialTheme.typography.bodySmall,
                        color = Error,
                        fontWeight = FontWeight.Medium
                    )
                }
                DriverResponseStatus.EXPIRED -> {
                    Spacer(Modifier.height(8.dp))
                    // Yellow warning banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(Warning.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = Warning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "No action from driver — choose another driver or retry",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
                else -> {}
            }
            
            // Action Buttons
            when (assignment.status) {
                DriverResponseStatus.DECLINED -> {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onReassignClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Default.SwapHoriz, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reassign to Another Driver")
                    }
                }
                DriverResponseStatus.ACCEPTED -> {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onTrackClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MyLocation, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Track Driver Location")
                    }
                }
                DriverResponseStatus.PENDING -> {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Warning,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Waiting for driver response...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                DriverResponseStatus.EXPIRED -> {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Reassign to another driver
                        Button(
                            onClick = onReassignClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Another Driver", style = MaterialTheme.typography.labelMedium)
                        }
                        // Retry same driver
                        OutlinedButton(
                            onClick = { onRetryClick?.invoke() ?: onReassignClick() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry Driver", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
