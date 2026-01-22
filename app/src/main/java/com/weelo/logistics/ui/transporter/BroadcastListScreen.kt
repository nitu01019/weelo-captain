package com.weelo.logistics.ui.transporter

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.core.notification.BroadcastSoundService
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.remote.SocketConnectionState
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "BroadcastListScreen"
private const val HOLD_DURATION_SECONDS = 15

/**
 * =============================================================================
 * BROADCAST LIST SCREEN - Professional Multi-Truck UI
 * =============================================================================
 * 
 * Shows booking requests as single cards with multiple truck types inside.
 * Each truck type has:
 * - Quantity selector ([-] count [+])
 * - REJECT button
 * - ACCEPT button
 * 
 * FLOW:
 * 1. Select quantity for a truck type
 * 2. Click ACCEPT → Trucks are HELD for 15 seconds
 * 3. Confirm within 15 seconds → Assigned permanently
 * 4. Timeout or REJECT → Released back to pool
 * 
 * =============================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBroadcastDetails: (String) -> Unit,
    onNavigateToSoundSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val repository = remember { BroadcastRepository.getInstance(context) }
    val soundService = remember { BroadcastSoundService.getInstance(context) }
    
    var broadcasts by remember { mutableStateOf<List<BroadcastTrip>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }
    
    val socketState by SocketIOService.connectionState.collectAsState()
    var isSocketConnected by remember { mutableStateOf(false) }
    
    // Fetch broadcasts
    fun fetchBroadcasts(forceRefresh: Boolean = false) {
        scope.launch {
            if (forceRefresh) isRefreshing = true else isLoading = true
            errorMessage = null
            
            when (val result = repository.fetchActiveBroadcasts(forceRefresh = forceRefresh)) {
                is BroadcastResult.Success -> {
                    broadcasts = result.data.broadcasts
                    Log.d(TAG, "Loaded ${broadcasts.size} broadcasts")
                }
                is BroadcastResult.Error -> {
                    errorMessage = result.message
                    Log.e(TAG, "Error: ${result.message}")
                }
                is BroadcastResult.Loading -> { }
            }
            
            isLoading = false
            isRefreshing = false
        }
    }
    
    // Initial fetch
    LaunchedEffect(Unit) {
        fetchBroadcasts()
    }
    
    // WebSocket connection
    LaunchedEffect(Unit) {
        val token = RetrofitClient.getAccessToken()
        if (token != null) {
            SocketIOService.connect(Constants.API.WS_URL, token)
        }
    }
    
    // Track socket connection
    LaunchedEffect(socketState) {
        val wasConnected = isSocketConnected
        isSocketConnected = socketState is SocketConnectionState.Connected
        if (socketState is SocketConnectionState.Connected && !wasConnected) {
            fetchBroadcasts(forceRefresh = true)
        }
    }
    
    // Listen for new broadcasts - PLAY SOUND
    LaunchedEffect(Unit) {
        SocketIOService.newBroadcasts.collect { notification ->
            // Play notification sound for new broadcast
            if (notification.isUrgent == true) {
                soundService.playUrgentSound()
            } else {
                soundService.playBroadcastSound()
            }
            
            Toast.makeText(context, "🔔 New request: ${notification.pickupCity} → ${notification.dropCity}", Toast.LENGTH_SHORT).show()
            fetchBroadcasts(forceRefresh = true)
        }
    }
    
    // Listen for availability updates
    LaunchedEffect(Unit) {
        SocketIOService.trucksRemainingUpdates.collect { notification ->
            Log.d(TAG, "Availability update: ${notification.orderId}")
            fetchBroadcasts(forceRefresh = true)
        }
    }
    
    // Auto-refresh every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L)
            fetchBroadcasts(forceRefresh = true)
        }
    }
    
    // Filter broadcasts
    val filteredBroadcasts = broadcasts.filter { broadcast ->
        when (selectedFilter) {
            "Urgent" -> broadcast.isUrgent
            else -> true
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // ========== TOP BAR ==========
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 4.dp,
            color = Primary
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = White)
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Booking Requests",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSocketConnected) Success else Error)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${broadcasts.size} active",
                                style = MaterialTheme.typography.bodySmall,
                                color = White.copy(alpha = 0.9f)
                            )
                        }
                    }
                    
                    // Sound settings
                    IconButton(onClick = onNavigateToSoundSettings) {
                        Icon(Icons.Default.NotificationsActive, "Sound Settings", tint = White)
                    }
                    
                    IconButton(
                        onClick = { fetchBroadcasts(forceRefresh = true) },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Refresh", tint = White)
                        }
                    }
                }
            }
        }
        
        // ========== FILTER CHIPS ==========
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("All", "Urgent")
            items(filters) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { 
                        Text(
                            when (filter) {
                                "All" -> "All (${broadcasts.size})"
                                "Urgent" -> "🔥 Urgent"
                                else -> filter
                            }
                        )
                    }
                )
            }
        }
        
        Divider(color = Divider, thickness = 1.dp)
        
        // ========== CONTENT ==========
        when {
            isLoading && broadcasts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Loading requests...", color = TextSecondary)
                    }
                }
            }
            
            errorMessage != null && broadcasts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(Icons.Default.CloudOff, null, Modifier.size(64.dp), tint = Error)
                        Spacer(Modifier.height(16.dp))
                        Text(errorMessage ?: "Error", color = TextSecondary)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { fetchBroadcasts(forceRefresh = true) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            
            filteredBroadcasts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(Icons.Outlined.Inbox, null, Modifier.size(64.dp), tint = TextDisabled)
                        Spacer(Modifier.height(16.dp))
                        Text("No requests available", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Pull to refresh or wait for new requests", color = TextSecondary)
                    }
                }
            }
            
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = filteredBroadcasts,
                        key = { it.broadcastId }
                    ) { broadcast ->
                        BroadcastOrderCard(
                            broadcast = broadcast,
                            onAcceptTruck = { vehicleType, vehicleSubtype, quantity ->
                                // Navigate to confirmation/driver assignment
                                onNavigateToBroadcastDetails(
                                    "${broadcast.broadcastId}|$vehicleType|$vehicleSubtype|$quantity"
                                )
                            },
                            onRejectTruck = { vehicleType, vehicleSubtype ->
                                Toast.makeText(context, "Rejected $vehicleType", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * =============================================================================
 * BROADCAST ORDER CARD - Single card with multiple truck types
 * =============================================================================
 */
@Composable
fun BroadcastOrderCard(
    broadcast: BroadcastTrip,
    onAcceptTruck: (vehicleType: String, vehicleSubtype: String, quantity: Int) -> Unit,
    onRejectTruck: (vehicleType: String, vehicleSubtype: String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ========== URGENT BANNER ==========
            if (broadcast.isUrgent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Error)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔥", fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "URGENT REQUEST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                // ========== HEADER: Order ID + Customer ==========
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Order #${broadcast.broadcastId.takeLast(8).uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = TextSecondary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                broadcast.customerName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // Total Value Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Success.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "₹${String.format("%,.0f", broadcast.totalFare)}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Success
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // ========== ROUTE SECTION ==========
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Route visualization
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Success, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(32.dp)
                                    .background(Divider)
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Error, CircleShape)
                            )
                        }
                        
                        Spacer(Modifier.width(12.dp))
                        
                        // Locations
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                broadcast.pickupLocation.address,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                broadcast.dropLocation.address,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        // Distance
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                "${broadcast.distance.toInt()} km",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                            Text(
                                "~${broadcast.estimatedDuration} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Goods Type
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Inventory2,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = TextSecondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Goods: ${broadcast.goodsType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    broadcast.weight?.let { weight ->
                        Text(
                            " • $weight",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Divider(color = Divider)
                Spacer(Modifier.height(16.dp))
                
                // ========== TRUCKS REQUIRED SECTION ==========
                Text(
                    "TRUCKS REQUIRED",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                
                Spacer(Modifier.height(12.dp))
                
                // Show each truck type
                if (broadcast.requestedVehicles.isNotEmpty()) {
                    broadcast.requestedVehicles.forEach { vehicle ->
                        if (vehicle.remainingCount > 0) {
                            TruckTypeRow(
                                vehicleType = vehicle.vehicleType,
                                vehicleSubtype = vehicle.vehicleSubtype,
                                available = vehicle.remainingCount,
                                farePerTruck = vehicle.farePerTruck,
                                onAccept = { quantity ->
                                    onAcceptTruck(vehicle.vehicleType, vehicle.vehicleSubtype, quantity)
                                },
                                onReject = {
                                    onRejectTruck(vehicle.vehicleType, vehicle.vehicleSubtype)
                                }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                } else {
                    // Legacy single type
                    TruckTypeRow(
                        vehicleType = broadcast.vehicleType?.id ?: "truck",
                        vehicleSubtype = broadcast.vehicleType?.name ?: "",
                        available = broadcast.totalRemainingTrucks,
                        farePerTruck = broadcast.farePerTruck,
                        onAccept = { quantity ->
                            onAcceptTruck(
                                broadcast.vehicleType?.id ?: "truck",
                                broadcast.vehicleType?.name ?: "",
                                quantity
                            )
                        },
                        onReject = {
                            onRejectTruck(
                                broadcast.vehicleType?.id ?: "truck",
                                broadcast.vehicleType?.name ?: ""
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * =============================================================================
 * TRUCK TYPE ROW - Single truck type with quantity selector
 * =============================================================================
 */
@Composable
fun TruckTypeRow(
    vehicleType: String,
    vehicleSubtype: String,
    available: Int,
    farePerTruck: Double,
    onAccept: (quantity: Int) -> Unit,
    onReject: () -> Unit
) {
    var selectedQuantity by remember { mutableStateOf(1) }
    
    // Reset quantity if available changes
    LaunchedEffect(available) {
        if (selectedQuantity > available) {
            selectedQuantity = available.coerceAtLeast(1)
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Primary.copy(alpha = 0.04f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Truck info + Available count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Truck Icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            vehicleType.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (vehicleSubtype.isNotBlank()) {
                            Text(
                                vehicleSubtype,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                // Available badge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$available available",
                        style = MaterialTheme.typography.labelMedium,
                        color = Success,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "₹${String.format("%,.0f", farePerTruck)}/truck",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Row 2: Quantity selector + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantity Selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(White, RoundedCornerShape(8.dp))
                        .border(1.dp, Divider, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    // Minus button
                    IconButton(
                        onClick = { if (selectedQuantity > 1) selectedQuantity-- },
                        modifier = Modifier.size(32.dp),
                        enabled = selectedQuantity > 1
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            "Decrease",
                            tint = if (selectedQuantity > 1) Primary else TextDisabled
                        )
                    }
                    
                    // Count
                    Text(
                        text = selectedQuantity.toString(),
                        modifier = Modifier.widthIn(min = 32.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    // Plus button
                    IconButton(
                        onClick = { if (selectedQuantity < available) selectedQuantity++ },
                        modifier = Modifier.size(32.dp),
                        enabled = selectedQuantity < available
                    ) {
                        Icon(
                            Icons.Default.Add,
                            "Increase",
                            tint = if (selectedQuantity < available) Primary else TextDisabled
                        )
                    }
                }
                
                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Reject Button
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Error
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Error.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "REJECT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Accept Button
                    Button(
                        onClick = { onAccept(selectedQuantity) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Success),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "ACCEPT $selectedQuantity",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Show total if selecting multiple
            if (selectedQuantity > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Total: ₹${String.format("%,.0f", farePerTruck * selectedQuantity)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Success,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * Backward compatibility exports
 */
@Composable
fun InfoColumn(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = iconTint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TripInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
