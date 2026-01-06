package com.weelo.logistics.ui.transporter

import androidx.compose.animation.animateContentSize
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
 * BROADCAST LIST SCREEN - Transporter View
 * ========================================
 * This screen shows all active customer broadcasts (trip requests) to the transporter.
 * 
 * FLOW:
 * 1. Customer creates booking → Broadcast appears here in real-time
 * 2. Transporter sees: location, trucks needed, pricing, distance
 * 3. Transporter clicks on broadcast → Goes to truck selection screen
 * 
 * FOR BACKEND DEVELOPER:
 * - Fetch active broadcasts via API/WebSocket
 * - Show only broadcasts where: status = ACTIVE or PARTIALLY_FILLED
 * - Real-time updates: Add WebSocket listener for new broadcasts
 * - Auto-refresh when new broadcast arrives
 * - Filter by location, vehicle type if needed
 */
@Composable
fun BroadcastListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBroadcastDetails: (String) -> Unit  // broadcastId
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    var broadcasts by remember { mutableStateOf<List<BroadcastTrip>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("All") }
    
    // BACKEND: Replace with real API call or WebSocket connection
    LaunchedEffect(Unit) {
        scope.launch {
            // Mock data - Replace with: repository.getActiveBroadcasts()
            broadcasts = repository.getMockBroadcasts()
            isLoading = false
        }
    }
    
    // Filter broadcasts
    val filteredBroadcasts = broadcasts.filter { broadcast ->
        when (selectedFilter) {
            "Active" -> broadcast.status == BroadcastStatus.ACTIVE
            "Urgent" -> broadcast.isUrgent
            "Nearby" -> true // BACKEND: Filter by distance from transporter location
            else -> true
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // Top Bar
        PrimaryTopBar(
            title = "Available Broadcasts",
            onBackClick = onNavigateBack,
            actions = {
                // Badge showing number of new broadcasts
                Box {
                    IconButton(onClick = { /* Refresh broadcasts */ }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = White)
                    }
                    if (broadcasts.any { it.isUrgent }) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 8.dp)
                        ) {
                            Text(broadcasts.count { it.isUrgent }.toString())
                        }
                    }
                }
            }
        )
        
        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "All",
                onClick = { selectedFilter = "All" },
                label = { Text("All (${broadcasts.size})") }
            )
            FilterChip(
                selected = selectedFilter == "Active",
                onClick = { selectedFilter = "Active" },
                label = { Text("Active") }
            )
            FilterChip(
                selected = selectedFilter == "Urgent",
                onClick = { selectedFilter = "Urgent" },
                label = { Text("Urgent") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ErrorLight
                )
            )
            FilterChip(
                selected = selectedFilter == "Nearby",
                onClick = { selectedFilter = "Nearby" },
                label = { Text("Nearby") }
            )
        }
        
        Divider()
        
        // Broadcast List
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (filteredBroadcasts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        null,
                        Modifier.size(80.dp),
                        tint = TextDisabled
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No broadcasts available",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "New customer requests will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDisabled
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredBroadcasts) { broadcast ->
                    BroadcastCard(
                        broadcast = broadcast,
                        onClick = { onNavigateToBroadcastDetails(broadcast.broadcastId) }
                    )
                }
            }
        }
    }
}

/**
 * BROADCAST CARD - Shows individual broadcast details
 * ===================================================
 * Displays key information about customer's trip request
 * 
 * Shows:
 * - Customer name and contact
 * - Pickup → Drop locations
 * - Trucks needed (e.g., "3/10 filled")
 * - Pricing per truck and total
 * - Distance and duration
 * - Urgent badge if priority
 */
@Composable
fun BroadcastCard(
    broadcast: BroadcastTrip,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(2.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (broadcast.isUrgent) ErrorLight else White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Customer name + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Customer Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = broadcast.customerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = broadcast.customerMobile,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                // Urgent Badge
                if (broadcast.isUrgent) {
                    AssistChip(
                        onClick = { },
                        label = { Text("URGENT", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Error,
                            labelColor = White
                        ),
                        border = null
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            
            // Route Information
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Pickup
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint = Success,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "PICKUP",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                broadcast.pickupLocation.address,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Drop
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint = Error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "DROP",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                broadcast.dropLocation.address,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            
            // Trip Details Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Trucks Needed
                InfoColumn(
                    label = "TRUCKS NEEDED",
                    value = "${broadcast.totalTrucksNeeded - broadcast.trucksFilledSoFar}/${broadcast.totalTrucksNeeded}",
                    icon = Icons.Default.LocalShipping,
                    iconTint = Primary
                )
                
                // Distance
                InfoColumn(
                    label = "DISTANCE",
                    value = "${broadcast.distance} km",
                    icon = Icons.Default.Route,
                    iconTint = Info
                )
                
                // Duration
                InfoColumn(
                    label = "TIME",
                    value = "${broadcast.estimatedDuration} min",
                    icon = Icons.Default.Schedule,
                    iconTint = Warning
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Goods Type
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory, null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                Spacer(Modifier.width(4.dp))
                Text(
                    "Goods: ${broadcast.goodsType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                broadcast.weight?.let {
                    Text(" • Weight: $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            
            // Pricing Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "₹${String.format("%.0f", broadcast.farePerTruck)} per truck",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Success
                    )
                    Text(
                        "Total: ₹${String.format("%.0f", broadcast.totalFare)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                // View Details Button
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Select Trucks")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Info Column Component - Small info display
 */
@Composable
fun InfoColumn(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = iconTint)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}
