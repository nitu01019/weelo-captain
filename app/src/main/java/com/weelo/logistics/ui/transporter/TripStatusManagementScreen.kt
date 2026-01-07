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
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    // TODO: Connect to real repository from backend
    
    var assignment by remember { mutableStateOf<TripAssignment?>(null) }
    var broadcast by remember { mutableStateOf<BroadcastTrip?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // BACKEND: Fetch assignment details with real-time updates
    LaunchedEffect(assignmentId) {
        scope.launch {
            // Mock - Replace with: repository.getTripAssignment(assignmentId)
            assignment = repository.getMockAssignmentDetails(assignmentId)
            broadcast = assignment?.let { repository.getMockBroadcastById(it.broadcastId) }
            isLoading = false
        }
    }
    
    // Auto-refresh every 5 seconds to get latest driver responses
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            // BACKEND: Poll for updates or use WebSocket
            isRefreshing = true
            kotlinx.coroutines.delay(500)
            isRefreshing = false
        }
    }
    
    val acceptedCount = assignment?.assignments?.count { it.status == DriverResponseStatus.ACCEPTED } ?: 0
    val pendingCount = assignment?.assignments?.count { it.status == DriverResponseStatus.PENDING } ?: 0
    val declinedCount = assignment?.assignments?.count { it.status == DriverResponseStatus.DECLINED } ?: 0
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
                    IconButton(onClick = { 
                        isRefreshing = true
                        // Refresh data
                    }) {
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
                            
                            // Progress Bar
                            LinearProgressIndicator(
                                progress = acceptedCount.toFloat() / totalCount,
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
                    items(assignments) { driverAssignment ->
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
 */
@Composable
fun DriverAssignmentStatusCard(
    assignment: DriverTruckAssignment,
    onReassignClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (assignment.status) {
                DriverResponseStatus.ACCEPTED -> Success.copy(alpha = 0.05f)
                DriverResponseStatus.DECLINED -> Error.copy(alpha = 0.05f)
                DriverResponseStatus.PENDING -> Warning.copy(alpha = 0.05f)
                else -> White
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
                                else -> Surface
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (assignment.status) {
                            DriverResponseStatus.ACCEPTED -> Icons.Default.CheckCircle
                            DriverResponseStatus.DECLINED -> Icons.Default.Cancel
                            DriverResponseStatus.PENDING -> Icons.Default.HourglassEmpty
                            else -> Icons.Default.Person
                        },
                        null,
                        tint = when (assignment.status) {
                            DriverResponseStatus.ACCEPTED -> Success
                            DriverResponseStatus.DECLINED -> Error
                            DriverResponseStatus.PENDING -> Warning
                            else -> TextDisabled
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
                            else -> TextDisabled
                        }
                    ) {
                        Text(
                            when (assignment.status) {
                                DriverResponseStatus.ACCEPTED -> "ACCEPTED"
                                DriverResponseStatus.DECLINED -> "DECLINED"
                                DriverResponseStatus.PENDING -> "WAITING..."
                                else -> "UNKNOWN"
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                }
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
                else -> {}
            }
        }
    }
}
