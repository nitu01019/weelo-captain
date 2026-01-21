package com.weelo.logistics.ui.transporter

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.remote.SocketConnectionState
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.Constants
import kotlinx.coroutines.delay
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
 * BACKEND INTEGRATION:
 * - Fetches active broadcasts via GET /broadcasts/active
 * - Real-time updates via WebSocket (new_broadcast event)
 * - Auto-refresh every 30 seconds
 * - Pull-to-refresh support
 * 
 * SCALABILITY:
 * - Pagination ready (can add infinite scroll)
 * - Efficient caching via BroadcastRepository
 * - Optimistic UI updates for accept/decline
 */
@Composable
fun BroadcastListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBroadcastDetails: (String) -> Unit  // broadcastId
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Use real BroadcastRepository instead of MockDataRepository
    val repository = remember { BroadcastRepository.getInstance(context) }
    
    var broadcasts by remember { mutableStateOf<List<BroadcastTrip>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }
    
    // Auto-refresh interval (10 seconds as fallback when WebSocket not connected)
    // Reduced from 30s to 10s for faster broadcast detection
    val autoRefreshIntervalMs = 10_000L
    
    // WebSocket connection state
    val socketState by SocketIOService.connectionState.collectAsState()
    var isSocketConnected by remember { mutableStateOf(false) }
    
    /**
     * Fetch broadcasts from backend
     */
    fun fetchBroadcasts(forceRefresh: Boolean = false) {
        scope.launch {
            if (forceRefresh) isRefreshing = true else isLoading = true
            errorMessage = null
            
            when (val result = repository.fetchActiveBroadcasts(forceRefresh = forceRefresh)) {
                is BroadcastResult.Success -> {
                    broadcasts = result.data.broadcasts
                    if (result.data.isStale) {
                        // Show subtle indicator that data might be outdated
                        Toast.makeText(context, "Showing cached data", Toast.LENGTH_SHORT).show()
                    }
                }
                is BroadcastResult.Error -> {
                    errorMessage = result.message
                    if (broadcasts.isEmpty()) {
                        // Only show error if we have no data to show
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
                is BroadcastResult.Loading -> {
                    // Already handled by isLoading state
                }
            }
            
            isLoading = false
            isRefreshing = false
        }
    }
    
    // Initial fetch
    LaunchedEffect(Unit) {
        fetchBroadcasts()
    }
    
    // =========================================================================
    // WEBSOCKET CONNECTION - Real-time broadcast updates
    // =========================================================================
    
    // Connect to WebSocket on screen load
    LaunchedEffect(Unit) {
        val token = RetrofitClient.getAccessToken()
        if (token != null) {
            SocketIOService.connect(Constants.API.WS_URL, token)
            android.util.Log.i("BroadcastListScreen", "🔌 Connecting to WebSocket: ${Constants.API.WS_URL}")
        }
    }
    
    // Track connection state - IMPORTANT: Fetch broadcasts when reconnecting!
    LaunchedEffect(socketState) {
        val wasConnected = isSocketConnected
        isSocketConnected = socketState is SocketConnectionState.Connected
        
        when (socketState) {
            is SocketConnectionState.Connected -> {
                android.util.Log.i("BroadcastListScreen", "✅ WebSocket connected - Real-time updates active")
                
                // IMPORTANT: Fetch broadcasts immediately when reconnecting
                // This catches any broadcasts missed while disconnected
                if (!wasConnected) {
                    android.util.Log.i("BroadcastListScreen", "🔄 Reconnected - fetching any missed broadcasts...")
                    fetchBroadcasts(forceRefresh = true)
                }
            }
            is SocketConnectionState.Disconnected -> {
                android.util.Log.w("BroadcastListScreen", "🔌 WebSocket disconnected")
            }
            is SocketConnectionState.Error -> {
                android.util.Log.e("BroadcastListScreen", "❌ WebSocket error: ${(socketState as SocketConnectionState.Error).message}")
            }
            else -> {}
        }
    }
    
    // Listen for new broadcasts from WebSocket
    LaunchedEffect(Unit) {
        SocketIOService.newBroadcasts.collect { notification ->
            android.util.Log.i("BroadcastListScreen", "📢 NEW BROADCAST via WebSocket: ${notification.broadcastId}")
            
            // Show notification toast
            Toast.makeText(
                context, 
                "New request: ${notification.vehicleType} - ${notification.pickupCity} to ${notification.dropCity}", 
                Toast.LENGTH_LONG
            ).show()
            
            // Refresh the list to show new broadcast
            fetchBroadcasts(forceRefresh = true)
        }
    }
    
    // Listen for booking updates (status changes, trucks filled, etc.)
    LaunchedEffect(Unit) {
        SocketIOService.bookingUpdated.collect { notification ->
            android.util.Log.i("BroadcastListScreen", "📝 Booking updated: ${notification.bookingId} - Status: ${notification.status}")
            
            // Update local list without full refresh
            broadcasts = broadcasts.map { broadcast ->
                if (broadcast.broadcastId == notification.bookingId) {
                    broadcast.copy(
                        trucksFilledSoFar = if (notification.trucksFilled >= 0) notification.trucksFilled else broadcast.trucksFilledSoFar,
                        status = when (notification.status.lowercase()) {
                            "fully_filled" -> BroadcastStatus.FULLY_FILLED
                            "partially_filled" -> BroadcastStatus.PARTIALLY_FILLED
                            "expired" -> BroadcastStatus.EXPIRED
                            "cancelled" -> BroadcastStatus.CANCELLED
                            else -> broadcast.status
                        }
                    )
                } else {
                    broadcast
                }
            }.filter { it.status == BroadcastStatus.ACTIVE || it.status == BroadcastStatus.PARTIALLY_FILLED }
        }
    }
    
    // Listen for trucks remaining updates
    LaunchedEffect(Unit) {
        SocketIOService.trucksRemainingUpdates.collect { notification ->
            android.util.Log.i("BroadcastListScreen", "📊 Trucks update: ${notification.orderId} - ${notification.trucksRemaining} remaining")
            
            // Update UI optimistically
            broadcasts = broadcasts.map { broadcast ->
                if (broadcast.broadcastId == notification.orderId) {
                    broadcast.copy(
                        trucksFilledSoFar = notification.trucksFilled,
                        totalTrucksNeeded = notification.totalTrucks
                    )
                } else {
                    broadcast
                }
            }
        }
    }
    
    // Auto-refresh every 10 seconds - ALWAYS runs as backup for unreliable WebSocket
    LaunchedEffect(Unit) {
        while (true) {
            delay(autoRefreshIntervalMs)
            // Always refresh - WebSocket is unreliable due to Android battery optimization
            android.util.Log.d("BroadcastListScreen", "⏰ Auto-refreshing broadcasts (interval: ${autoRefreshIntervalMs/1000}s, WS connected: $isSocketConnected)")
            fetchBroadcasts(forceRefresh = true)
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
                // WebSocket connection indicator
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSocketConnected -> Success  // Green when connected
                                    socketState is SocketConnectionState.Connecting -> Warning  // Yellow when connecting
                                    else -> Error  // Red when disconnected
                                }
                            )
                    )
                }
                
                // Refresh button with loading indicator
                Box {
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
                    // Badge showing urgent broadcasts count
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
        
        // WebSocket status banner (shown when disconnected)
        if (!isSocketConnected && socketState !is SocketConnectionState.Connecting) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Warning.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Real-time updates unavailable. Auto-refreshing every 30s.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
        
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
        if (isLoading && broadcasts.isEmpty()) {
            // Initial loading state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Loading broadcasts...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else if (errorMessage != null && broadcasts.isEmpty()) {
            // Error state (only when no cached data)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        Modifier.size(80.dp),
                        tint = Error
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Failed to load broadcasts",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorMessage ?: "Please check your connection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { fetchBroadcasts(forceRefresh = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }
        } else if (filteredBroadcasts.isEmpty()) {
            // Empty state
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
                        "New customer requests will appear here.\nWe'll check automatically every 30 seconds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDisabled
                    )
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { fetchBroadcasts(forceRefresh = true) }
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Check Now")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // OPTIMIZATION: Add keys to prevent unnecessary recompositions
                items(
                    items = filteredBroadcasts,
                    key = { it.broadcastId }
                ) { broadcast ->
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
