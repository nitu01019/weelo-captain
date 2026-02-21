package com.weelo.logistics.ui.transporter

import android.content.Intent
import android.net.Uri
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.weelo.logistics.core.notification.BroadcastSoundService
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.remote.SocketConnectionState
import com.weelo.logistics.data.remote.BroadcastDismissedNotification
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.alpha

// =============================================================================
// RAPIDO STYLE COLORS
// =============================================================================
private val RapidoYellow = Color(0xFFFDD835)
private val RapidoBlack = Color(0xFF1A1A1A)
private val RapidoGray = Color(0xFF616161)
private val RapidoLightGray = Color(0xFFF5F5F5)
private val RapidoWhite = Color(0xFFFFFFFF)

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
    
    // ========== GRACEFUL DISMISS STATE ==========
    // Tracks which cards are being dismissed (blur + message overlay)
    // Key = broadcastId, Value = DismissInfo(reason, message)
    var dismissedCards by remember { mutableStateOf<Map<String, BroadcastDismissedNotification>>(emptyMap()) }
    val listState = rememberLazyListState()
    
    val socketState by SocketIOService.connectionState.collectAsState()
    var isSocketConnected by remember { mutableStateOf(false) }
    
    // OPTIMIZATION: Fetch broadcasts on IO dispatcher to avoid blocking Main thread
    fun fetchBroadcasts(forceRefresh: Boolean = false) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (forceRefresh) isRefreshing = true else isLoading = true
                errorMessage = null
            }
            
            when (val result = repository.fetchActiveBroadcasts(forceRefresh = forceRefresh)) {
                is BroadcastResult.Success -> {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        broadcasts = result.data.broadcasts
                    }
                    timber.log.Timber.d("Loaded ${broadcasts.size} broadcasts")
                }
                is BroadcastResult.Error -> {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        errorMessage = result.message
                    }
                    timber.log.Timber.e("Error: ${result.message}")
                }
                is BroadcastResult.Loading -> { }
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                isLoading = false
                isRefreshing = false
            }
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
            timber.log.Timber.d("Availability update: ${notification.orderId}")
            fetchBroadcasts(forceRefresh = true)
        }
    }
    
    // Auto-refresh every 30 seconds (optimized from 10s to reduce server load)
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            fetchBroadcasts(forceRefresh = true)
        }
    }
    
    // ========== ORDER CANCELLED: Wire orderCancelled → broadcastDismissed infrastructure ==========
    // PRD 4.3: When customer cancels, BroadcastListScreen must show dismiss overlay on that card
    LaunchedEffect(Unit) {
        SocketIOService.orderCancelled.collect { notification ->
            val broadcastId = notification.orderId
            if (broadcastId.isNotEmpty()) {
                // Reuse existing broadcastDismissed infrastructure — convert to BroadcastDismissedNotification
                val dismissNotification = BroadcastDismissedNotification(
                    broadcastId = broadcastId,
                    reason = "customer_cancelled",
                    message = "Sorry, the customer cancelled this order",
                    customerName = notification.customerName
                )
                val currentIndex = broadcasts.indexOfFirst { it.broadcastId == broadcastId }
                dismissedCards = dismissedCards + (broadcastId to dismissNotification)

                scope.launch {
                    delay(1_000L)
                    if (currentIndex >= 0 && broadcasts.size > 1) {
                        val nextIndex = if (currentIndex < broadcasts.size - 1) currentIndex + 1 else currentIndex - 1
                        listState.animateScrollToItem(nextIndex.coerceAtLeast(0))
                        delay(300L)
                    }
                    dismissedCards = dismissedCards - broadcastId
                    fetchBroadcasts(forceRefresh = true)
                    delay(300L)
                    if (broadcasts.isEmpty()) {
                        timber.log.Timber.i("🏠 No broadcasts left after customer cancel — navigating back to dashboard")
                        onNavigateBack()
                    }
                }
            }
        }
    }

    // ========== GRACEFUL DISMISS: Listen for broadcast dismissals ==========
    // Animated fade-in overlay → 1s read time → scroll to next → remove card → auto-nav if empty
    LaunchedEffect(Unit) {
        SocketIOService.broadcastDismissed.collect { dismissNotification ->
            val broadcastId = dismissNotification.broadcastId
            if (broadcastId.isNotEmpty()) {
                // 1. Add to dismissed map — triggers AnimatedVisibility(fadeIn) overlay on the card
                dismissedCards = dismissedCards + (broadcastId to dismissNotification)

                // 2. Find current card index for scroll-to-next logic
                val currentIndex = broadcasts.indexOfFirst { it.broadcastId == broadcastId }

                // 3. After 1s (user reads "Sorry" message), scroll to next card then remove
                scope.launch {
                    delay(1_000L) // 1s — user reads animated cancel message

                    // Scroll to next available card (if more exist)
                    if (currentIndex >= 0 && broadcasts.size > 1) {
                        val nextIndex = if (currentIndex < broadcasts.size - 1) currentIndex + 1 else currentIndex - 1
                        listState.animateScrollToItem(nextIndex.coerceAtLeast(0))
                        delay(300L) // Brief pause after scroll animation
                    }

                    // Remove dismissed card from map + refresh list
                    dismissedCards = dismissedCards - broadcastId
                    fetchBroadcasts(forceRefresh = true)

                    // 4. Auto-navigate to dashboard if no more broadcasts remain
                    // Give fetchBroadcasts 300ms to update state, then check
                    delay(300L)
                    if (broadcasts.isEmpty()) {
                        timber.log.Timber.i("🏠 No broadcasts left after cancel — navigating back to dashboard")
                        onNavigateBack()
                    }
                }
            }
        }
    }
    
    // OPTIMIZATION: Use derivedStateOf to prevent refiltering on every recomposition
    val filteredBroadcasts by remember {
        derivedStateOf {
            broadcasts.filter { broadcast ->
                when (selectedFilter) {
                    "Urgent" -> broadcast.isUrgent
                    else -> true
                }
            }
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
            items(
                items = filters,
                key = { it }
            ) { filter ->
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
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = filteredBroadcasts,
                        key = { it.broadcastId },
                        contentType = { "broadcast_order_card" }
                    ) { broadcast ->
                        val dismissInfo = dismissedCards[broadcast.broadcastId]
                        val isDismissed = dismissInfo != null
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // The actual card (fades to 20% when dismissed — still visible under overlay)
                            BroadcastOrderCard(
                                broadcast = broadcast,
                                modifier = Modifier.alpha(if (isDismissed) 0.20f else 1f),
                                onAcceptTruck = { vehicleType, vehicleSubtype, quantity ->
                                    if (!isDismissed) {
                                        onNavigateToBroadcastDetails(
                                            "${broadcast.broadcastId}|$vehicleType|$vehicleSubtype|$quantity"
                                        )
                                    }
                                },
                                onRejectTruck = { vehicleType, _ ->
                                    if (!isDismissed) {
                                        Toast.makeText(context, "Rejected $vehicleType", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            // Animated dismiss overlay — fades in smoothly over the card
                            // Use explicit androidx.compose.animation.AnimatedVisibility (not ColumnScope version)
                            // GPU-accelerated alpha animation — zero layout change, O(1) cost
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isDismissed,
                                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)),
                                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 150))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Color.Black.copy(alpha = 0.72f),
                                            RoundedCornerShape(16.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        // Emoji icon based on reason
                                        Text(
                                            text = when (dismissInfo!!.reason) {
                                                "customer_cancelled" -> "😔"
                                                "fully_filled" -> "✅"
                                                else -> "⏰"
                                            },
                                            fontSize = 48.sp
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        // Reason title
                                        Text(
                                            text = when (dismissInfo.reason) {
                                                "customer_cancelled" -> "ORDER CANCELLED"
                                                "fully_filled" -> "FULLY ASSIGNED"
                                                else -> "ORDER EXPIRED"
                                            },
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = when (dismissInfo.reason) {
                                                "customer_cancelled" -> Color(0xFFFF6B6B)
                                                "fully_filled" -> Color(0xFF4CAF50)
                                                else -> RapidoYellow
                                            },
                                            letterSpacing = 2.sp
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        // Empathetic message for cancel, original message for others
                                        Text(
                                            text = if (dismissInfo.reason == "customer_cancelled")
                                                "Sorry, the customer cancelled this order"
                                            else
                                                dismissInfo.message,
                                            fontSize = 14.sp,
                                            color = Color.White.copy(alpha = 0.92f),
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * =============================================================================
 * BROADCAST ORDER CARD - RAPIDO STYLE
 * Yellow accents, Bold Black text, Embedded Map
 * =============================================================================
 */
@Composable
@Suppress("UNUSED_VARIABLE")
fun BroadcastOrderCard(
    broadcast: BroadcastTrip,
    modifier: Modifier = Modifier,
    onAcceptTruck: (vehicleType: String, vehicleSubtype: String, quantity: Int) -> Unit,
    onRejectTruck: (vehicleType: String, vehicleSubtype: String) -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RapidoWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ========== URGENT BANNER ==========
            if (broadcast.isUrgent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RapidoYellow)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔥", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "URGENT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = RapidoBlack,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                // ========== HEADER: Order ID + Fare ==========
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Order #${broadcast.broadcastId.takeLast(8).uppercase()}",
                            fontSize = 12.sp,
                            color = RapidoGray,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            broadcast.customerName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RapidoBlack
                        )
                    }
                    
                    // Fare Badge - Yellow
                    Box(
                        modifier = Modifier
                            .background(RapidoYellow, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "₹${String.format("%,.0f", broadcast.totalFare)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = RapidoBlack
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // ========== EMBEDDED MAP ==========
                BroadcastCardMap(
                    broadcast = broadcast,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                
                Spacer(Modifier.height(12.dp))
                
                // ========== ROUTE SECTION ==========
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RapidoLightGray, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    // Pickup
                    Row(verticalAlignment = Alignment.Top) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(RapidoBlack, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(32.dp)
                                    .background(RapidoBlack.copy(alpha = 0.3f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(RapidoBlack, CircleShape)
                            )
                        }
                        
                        Spacer(Modifier.width(12.dp))
                        
                        // Locations - Rapido style
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "PICKUP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RapidoGray,
                                letterSpacing = 1.sp
                            )
                            Text(
                                broadcast.pickupLocation.address,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RapidoBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "DROP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RapidoGray,
                                letterSpacing = 1.sp
                            )
                            Text(
                                broadcast.dropLocation.address,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RapidoBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        // Distance - Rapido style
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                "${broadcast.distance.toInt()} km",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = RapidoBlack
                            )
                            Text(
                                "~${broadcast.estimatedDuration} min",
                                fontSize = 12.sp,
                                color = RapidoGray
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Goods Type
                Text(
                    "${broadcast.goodsType}${broadcast.weight?.let { " • $it" } ?: ""}",
                    fontSize = 12.sp,
                    color = RapidoGray
                )
                
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(RapidoLightGray))
                Spacer(Modifier.height(16.dp))
                
                // ========== TRUCKS REQUIRED SECTION ==========
                Text(
                    "TRUCKS REQUIRED",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RapidoBlack,
                    letterSpacing = 1.sp
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
                    @Suppress("DEPRECATION")
                    TruckTypeRow(
                        vehicleType = broadcast.vehicleType?.id ?: "truck",
                        vehicleSubtype = broadcast.vehicleType?.name ?: "",
                        available = broadcast.totalRemainingTrucks,
                        farePerTruck = broadcast.farePerTruck,
                        onAccept = { quantity ->
                            @Suppress("DEPRECATION")
                            onAcceptTruck(
                                broadcast.vehicleType?.id ?: "truck",
                                broadcast.vehicleType?.name ?: "",
                                quantity
                            )
                        },
                        onReject = {
                            @Suppress("DEPRECATION")
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
 * TRUCK TYPE ROW - RAPIDO STYLE
 * Yellow accents, Bold Black text
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
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RapidoLightGray, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Row 1: Truck info + Fare
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Truck Icon - Yellow background
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(RapidoYellow, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocalShipping,
                        null,
                        tint = RapidoBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(Modifier.width(12.dp))
                
                Column {
                    Text(
                        vehicleType.uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = RapidoBlack
                    )
                    if (vehicleSubtype.isNotBlank()) {
                        Text(
                            vehicleSubtype,
                            fontSize = 12.sp,
                            color = RapidoGray
                        )
                    }
                }
            }
            
            // Fare badge - Yellow
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$available available",
                    fontSize = 12.sp,
                    color = RapidoGray,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .background(RapidoYellow, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "₹${String.format("%,.0f", farePerTruck)}/truck",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RapidoBlack
                    )
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Row 2: Quantity selector + Actions - RAPIDO STYLE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quantity Selector - Rapido style
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(RapidoWhite, RoundedCornerShape(8.dp))
                    .border(1.dp, RapidoBlack.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                // Minus button - Yellow when active
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (selectedQuantity > 1) RapidoYellow else RapidoLightGray,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { if (selectedQuantity > 1) selectedQuantity-- },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RapidoBlack)
                }
                
                // Count
                Text(
                    text = selectedQuantity.toString(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = RapidoBlack,
                    textAlign = TextAlign.Center
                )
                
                // Plus button - Yellow when active
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (selectedQuantity < available) RapidoYellow else RapidoLightGray,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { if (selectedQuantity < available) selectedQuantity++ },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RapidoBlack)
                }
            }
            
            // Action Buttons - RAPIDO STYLE
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Reject Button - Black outline
                Box(
                    modifier = Modifier
                        .border(2.dp, RapidoBlack, RoundedCornerShape(8.dp))
                        .clickable { onReject() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "REJECT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RapidoBlack,
                        letterSpacing = 0.5.sp
                    )
                }
                
                // Accept Button - YELLOW background, BLACK text
                Box(
                    modifier = Modifier
                        .background(RapidoYellow, RoundedCornerShape(8.dp))
                        .clickable { onAccept(selectedQuantity) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "ACCEPT $selectedQuantity",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = RapidoBlack,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
        
        // Show total if selecting multiple
        if (selectedQuantity > 1) {
            Spacer(Modifier.height(8.dp))
            Text(
                "TOTAL: ₹${String.format("%,.0f", farePerTruck * selectedQuantity)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RapidoBlack,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * =============================================================================
 * EMBEDDED MAP FOR BROADCAST CARD - Shows route with markers
 * =============================================================================
 */
@Composable
private fun BroadcastCardMap(
    broadcast: BroadcastTrip,
    modifier: Modifier = Modifier
) {
    // Build list of LatLng points
    val points = remember(broadcast) {
        listOf(
            LatLng(broadcast.pickupLocation.latitude, broadcast.pickupLocation.longitude),
            LatLng(broadcast.dropLocation.latitude, broadcast.dropLocation.longitude)
        )
    }
    
    // Calculate bounds
    val boundsBuilder = remember(points) {
        LatLngBounds.builder().apply {
            points.forEach { include(it) }
        }
    }
    
    // Camera position
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points.first(), 10f)
    }
    
    // Auto-fit bounds
    LaunchedEffect(points) {
        try {
            val bounds = boundsBuilder.build()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, 60),
                durationMs = 300
            )
        } catch (e: Exception) { }
    }
    
    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.NORMAL),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false,
                myLocationButtonEnabled = false,
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = false
            )
        ) {
            // Pickup marker - Green
            Marker(
                state = MarkerState(position = points.first()),
                title = "Pickup",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            )
            
            // Drop marker - Red
            Marker(
                state = MarkerState(position = points.last()),
                title = "Drop",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
            
            // Polyline - Black
            Polyline(
                points = points,
                color = RapidoBlack,
                width = 6f
            )
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
