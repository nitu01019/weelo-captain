package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.api.VehicleListData
import com.weelo.logistics.data.api.DriverListData
import com.weelo.logistics.data.api.UserProfile
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.remote.SocketConnectionState
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.components.rememberScreenConfig
import com.weelo.logistics.ui.components.responsiveHorizontalPadding
import com.weelo.logistics.ui.components.OfflineBanner
import com.weelo.logistics.offline.NetworkMonitor
import com.weelo.logistics.offline.OfflineCache
import com.weelo.logistics.ui.theme.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import com.weelo.logistics.R
import com.weelo.logistics.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transporter Dashboard Screen - Connected to Backend
 * 
 * Fetches real data from weelo-backend:
 * - User profile from GET /api/v1/profile
 * - Vehicle stats from GET /api/v1/vehicles/stats
 * - Driver stats from GET /api/v1/driver/list
 * 
 * Features:
 * - Navigation Drawer with real user profile
 * - Hamburger menu to open drawer
 * - Real-time stats from database
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun TransporterDashboardScreen(
    onNavigateToFleet: () -> Unit = {},
    onNavigateToDrivers: () -> Unit = {},
    onNavigateToTrips: () -> Unit = {},
    onNavigateToAddVehicle: () -> Unit = {},
    onNavigateToAddDriver: () -> Unit = {},
    onNavigateToCreateTrip: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToBroadcasts: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    // BACK BUTTON DISABLED - User must explicitly logout from drawer
    // Back press is consumed but does nothing
    androidx.activity.compose.BackHandler {
        // Do nothing - prevents going back to login/role selection
    }
    
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    // Network monitoring for offline banner
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    val isOnline by networkMonitor.isOnline.collectAsState()
    
    // Offline cache for instant loading
    val offlineCache = remember { OfflineCache.getInstance(context) }
    
    // Dashboard state - NO loading spinner, show data immediately
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var vehicleStats by remember { mutableStateOf<VehicleListData?>(null) }
    var driverStats by remember { mutableStateOf<DriverListData?>(null) }
    var isBackendConnected by remember { mutableStateOf(true) }
    
    // User profile state
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    
    // WebSocket connection state for real-time broadcasts
    val socketState by SocketIOService.connectionState.collectAsState()
    var isSocketConnected by remember { mutableStateOf(false) }
    
    // ==========================================================================
    // CACHE-FIRST LOADING: Show cached data INSTANTLY, then refresh in background
    // OPTIMIZATION: All network calls on IO dispatcher to prevent Main thread blocking
    // ==========================================================================
    LaunchedEffect(Unit) {
        // Step 1: Load cached data IMMEDIATELY (no loading spinner)
        val cachedData = withContext(Dispatchers.IO) {
            offlineCache.getDashboardCache()
        }
        
        if (cachedData.profile != null || cachedData.vehicleStats != null || cachedData.driverStats != null) {
            userProfile = cachedData.profile
            vehicleStats = cachedData.vehicleStats
            driverStats = cachedData.driverStats
            timber.log.Timber.i("📦 Loaded cached data instantly")
        }
        
        // Step 2: Refresh from API in background (only if cache is stale or empty)
        if (!cachedData.isFresh || cachedData.profile == null) {
            isRefreshing = true
            
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val profileDeferred = async {
                        try { RetrofitClient.profileApi.getProfile() } catch (e: Exception) { null }
                    }
                    val vehicleDeferred = async {
                        try { RetrofitClient.vehicleApi.getVehicles() } catch (e: Exception) { null }
                    }
                    val driverDeferred = async {
                        try { RetrofitClient.driverApi.getDriverList() } catch (e: Exception) { null }
                    }
                    
                    // Process responses
                    val profileResponse = profileDeferred.await()
                    val newProfile = if (profileResponse?.isSuccessful == true && profileResponse.body()?.success == true) {
                        profileResponse.body()?.data?.user
                    } else null
                    
                    val vehicleResponse = vehicleDeferred.await()
                    val newVehicleStats = if (vehicleResponse?.isSuccessful == true && vehicleResponse.body()?.success == true) {
                        vehicleResponse.body()?.data
                    } else null
                    
                    val driverResponse = driverDeferred.await()
                    val newDriverStats = if (driverResponse?.isSuccessful == true && driverResponse.body()?.success == true) {
                        driverResponse.body()?.data
                    } else null
                    
                    // Update UI state on Main thread
                    withContext(Dispatchers.Main) {
                        newProfile?.let { userProfile = it }
                        newVehicleStats?.let { vehicleStats = it }
                        newDriverStats?.let { driverStats = it }
                        
                        // Check if backend is reachable
                        isBackendConnected = profileResponse != null || vehicleResponse != null || driverResponse != null
                        
                        if (!isBackendConnected && cachedData.profile == null) {
                            errorMessage = context.getString(R.string.cannot_connect_backend)
                        }
                    }
                    
                    // Save to cache for next time
                    offlineCache.saveDashboardData(
                        profile = newProfile ?: userProfile,
                        vehicleStats = newVehicleStats ?: vehicleStats,
                        driverStats = newDriverStats ?: driverStats
                    )
                    
                    timber.log.Timber.i("🔄 Refreshed data from API")
                }
            }
            
            isRefreshing = false
        }
    }
    
    // ==========================================================================
    // WEBSOCKET CONNECTION - Critical for receiving broadcasts overlay
    // ==========================================================================
    LaunchedEffect(Unit) {
        val token = RetrofitClient.getAccessToken()
        if (token != null) {
            timber.log.Timber.i("🔌 Connecting WebSocket for broadcast overlay...")
            SocketIOService.connect(Constants.API.WS_URL, token)
        } else {
            timber.log.Timber.w("⚠️ No auth token - WebSocket not connected")
        }
    }
    
    // Track socket connection state
    LaunchedEffect(socketState) {
        isSocketConnected = socketState is SocketConnectionState.Connected
        
        when (socketState) {
            is SocketConnectionState.Connected -> {
                timber.log.Timber.i("✅ WebSocket connected - Ready for broadcasts")
            }
            is SocketConnectionState.Disconnected -> {
                timber.log.Timber.w("🔌 WebSocket disconnected")
            }
            is SocketConnectionState.Connecting -> {
                timber.log.Timber.d("🔄 WebSocket connecting...")
            }
            is SocketConnectionState.Error -> {
                val error = (socketState as SocketConnectionState.Error).message
                timber.log.Timber.e("❌ WebSocket error: $error")
            }
        }
    }
    
    // ==========================================================================
    // REAL-TIME DRIVER UPDATES - Listen for driver added/updated events
    // ==========================================================================
    LaunchedEffect(Unit) {
        SocketIOService.driverAdded.collect { notification ->
            timber.log.Timber.i("👤 Driver added: ${notification.driverName}")
            timber.log.Timber.i("📊 New driver count: ${notification.totalDrivers}")
            
            // Update driver stats immediately
            driverStats = DriverListData(
                drivers = emptyList(),
                total = notification.totalDrivers,
                online = notification.availableCount,
                offline = notification.onTripCount
            )
        }
    }
    
    LaunchedEffect(Unit) {
        SocketIOService.driversUpdated.collect { notification ->
            timber.log.Timber.i("👤 Drivers updated: ${notification.action}")
            timber.log.Timber.i("📊 Updated driver count: ${notification.totalDrivers}")
            
            // Update driver stats immediately
            driverStats = DriverListData(
                drivers = emptyList(),
                total = notification.totalDrivers,
                online = notification.availableCount,
                offline = notification.onTripCount
            )
        }
    }
    
    // =========================================================================
    // REAL-TIME DRIVER ONLINE/OFFLINE STATUS — Update online/offline counts instantly
    // =========================================================================
    LaunchedEffect(Unit) {
        SocketIOService.driverStatusChanged.collect { event ->
            timber.log.Timber.i("📡 [TransporterDashboard] Driver status: ${event.driverName} → ${if (event.isOnline) "ONLINE" else "OFFLINE"}")
            // Update online/offline counts based on status change event
            val current = driverStats
            if (current != null) {
                val delta = if (event.isOnline) 1 else -1
                driverStats = current.copy(
                    online = maxOf(0, current.online + delta),
                    offline = maxOf(0, current.offline - delta)
                )
            }
        }
    }

    // =========================================================================
    // ORDER CANCELLATION — Show snackbar with customer info + refresh
    // =========================================================================
    val cancelSnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var lastCancelCustomerPhone by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        SocketIOService.orderCancelled.collect { notification ->
            timber.log.Timber.w("🚫 Order cancelled on transporter dashboard: ${notification.orderId}")
            lastCancelCustomerPhone = notification.customerPhone
            
            // Build informative message with customer + route info
            val parts = mutableListOf<String>()
            parts.add("❌ Order cancelled")
            if (notification.customerName.isNotBlank()) parts.add("by ${notification.customerName}")
            parts.add("• ${notification.reason}")
            if (notification.assignmentsCancelled > 0) {
                parts.add("• ${notification.assignmentsCancelled} truck(s) released")
            }
            
            val result = cancelSnackbarHostState.showSnackbar(
                message = parts.joinToString(" "),
                actionLabel = if (notification.customerPhone.isNotBlank()) "📞 Call" else null,
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
            
            // If transporter tapped "Call" action
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed && 
                lastCancelCustomerPhone.isNotBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:$lastCancelCustomerPhone")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            
            // Refresh dashboard to update active orders + released vehicles
            isRefreshing = true
            scope.launch {
                try {
                    val api = RetrofitClient.vehicleApi
                    val driverApi = RetrofitClient.driverApi
                    
                    // MAJOR FIX: Wrap network calls in withContext(Dispatchers.IO).
                    // scope.launch{} uses the main dispatcher by default — running Retrofit
                    // calls on main thread risks ANR and UI jank.
                    // State mutations (vehicleStats, driverStats) must happen on Main thread —
                    // so fetch on IO, then assign back on Main (withContext returns to caller's dispatcher).
                    val (vehicleResponse, driverResponse) = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val v = try { api.getVehicles() } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            null
                        }
                        val d = try { driverApi.getDriverList() } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            null
                        }
                        v to d
                    }
                    // Back on Main thread — safe to mutate Compose state
                    if (vehicleResponse?.isSuccessful == true) vehicleStats = vehicleResponse.body()?.data
                    if (driverResponse?.isSuccessful == true) driverStats = driverResponse.body()?.data
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    timber.log.Timber.w("Failed to refresh after cancel: ${e.message}")
                } finally {
                    isRefreshing = false
                }
            }
        }
    }
    
    // OPTIMIZATION: Use derivedStateOf to prevent unnecessary recomposition when userProfile changes
    val drawerProfile by remember {
        derivedStateOf {
            userProfile?.let {
                DrawerUserProfile(
                    id = it.id,
                    phone = it.phone,
                    name = it.name ?: "",
                    role = it.role,
                    email = it.email,
                    businessName = it.getBusinessDisplayName(),
                    isVerified = it.isVerified
                )
            }
        }
    }
    
    // OPTIMIZATION: Remember menu items to prevent recreation on every recomposition
    // Extract strings outside remember {} since stringResource is @Composable
    val dashboardStr = stringResource(R.string.dashboard)
    val myFleetStr = stringResource(R.string.my_fleet)
    val myDriversStr = stringResource(R.string.my_drivers)
    val tripsStr = stringResource(R.string.trips_menu)
    val settingsStr = stringResource(R.string.settings)
    
    val menuItems = remember(dashboardStr, myFleetStr, myDriversStr, tripsStr, settingsStr) {
        createTransporterMenuItems(
            onDashboard = { scope.launch { drawerState.close() } },
            onFleet = { 
                scope.launch { drawerState.close() }
                onNavigateToFleet()
            },
            onDrivers = { 
                scope.launch { drawerState.close() }
                onNavigateToDrivers()
            },
            onTrips = { 
                scope.launch { drawerState.close() }
                onNavigateToTrips()
            },
            onBroadcasts = { 
                scope.launch { drawerState.close() }
                onNavigateToBroadcasts()
            },
            onSettings = { 
                scope.launch { drawerState.close() }
                onNavigateToSettings()
            },
            strings = mapOf(
                "dashboard" to dashboardStr,
                "my_fleet" to myFleetStr,
                "my_drivers" to myDriversStr,
                "trips" to tripsStr,
                "settings" to settingsStr
            )
        )
    }
    
    // =========================================================================
    // LAZY DRAWER CONTENT — Production-Grade Anti-Flicker
    // =========================================================================
    // Only compose DrawerContentInternal when the drawer is actually visible.
    // Prevents first-frame flicker caused by ModalNavigationDrawer composing
    // drawerContent before the swipeable offset anchors to "closed".
    //
    // Same pattern as DriverDashboardScreen for consistency.
    // =========================================================================
    val isDrawerVisible by remember {
        derivedStateOf {
            drawerState.isOpen || drawerState.isAnimationRunning
        }
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // When drawer is not visible: zero-width transparent sheet (invisible)
            // When drawer is visible: full 300dp sheet with content
            ModalDrawerSheet(
                modifier = if (isDrawerVisible) Modifier.width(300.dp) else Modifier.width(0.dp),
                drawerContainerColor = if (isDrawerVisible) androidx.compose.material3.MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent
            ) {
                if (isDrawerVisible) {
                    DrawerContentInternal(
                        userProfile = drawerProfile,
                        isLoading = false,
                        selectedItemId = "dashboard",
                        menuItems = menuItems,
                        onProfileClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToProfile()
                        },
                        onLogout = {
                            scope.launch { drawerState.close() }
                            RetrofitClient.clearAllData()
                            onLogout()
                        }
                    )
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Offline Banner - Shows when device is offline
            OfflineBanner(
                isOffline = !isOnline,
                onRetryClick = { /* Trigger data refresh */ }
            )
            
            // Top Bar with Hamburger Menu
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.dashboard),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.cd_menu),
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    // Availability Toggle (Compact) - Online/Offline Status
                    AvailabilityToggleCompact(
                        modifier = Modifier.padding(end = 4.dp),
                        onStatusChanged = { isOnline ->
                            timber.log.Timber.d("Transporter availability: ${if (isOnline) "ONLINE" else "OFFLINE"}")
                        }
                    )
                    
                    IconButton(onClick = { /* TODO: Navigate to notifications */ }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.cd_notifications),
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.White
                )
            )
            
            // Responsive layout configuration
            val screenConfig = rememberScreenConfig()
            val horizontalPadding = responsiveHorizontalPadding()
            
            // ALWAYS show content - no loading spinner
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Welcome Message with User Name (or default)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.welcome_format, userProfile?.name?.split(" ")?.firstOrNull() ?: "Transporter"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    // Subtle refresh indicator (not blocking)
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
                
                // Statistics - Responsive layout (4 cards in landscape, 2 in portrait)
                if (screenConfig.isLandscape) {
                    // Landscape - Show 4 cards in a row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total Vehicles Card
                        Card(
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToFleet,
                            elevation = CardDefaults.cardElevation(Elevation.low),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(BorderRadius.medium)
                        ) {
                            InfoCard(
                                icon = Icons.Default.LocalShipping,
                                title = stringResource(R.string.total_vehicles),
                                value = "${vehicleStats?.total ?: 0}",
                                modifier = Modifier.fillMaxWidth(),
                                animateValue = true,
                                targetCount = vehicleStats?.total ?: 0
                            )
                        }
                        
                        // Total Drivers Card
                        Card(
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToDrivers,
                            elevation = CardDefaults.cardElevation(Elevation.low),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(BorderRadius.medium)
                        ) {
                            InfoCard(
                                icon = Icons.Default.People,
                                title = stringResource(R.string.total_drivers),
                                value = "${driverStats?.total ?: 0}",
                                modifier = Modifier.fillMaxWidth(),
                                iconTint = Secondary,
                                animateValue = true,
                                targetCount = driverStats?.total ?: 0
                            )
                        }
                        
                        // Add Vehicle Quick Action
                        QuickActionCard(
                            icon = Icons.Default.AddCircle,
                            title = stringResource(R.string.add_vehicle),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToAddVehicle
                        )
                        
                        // Add Driver Quick Action
                        QuickActionCard(
                            icon = Icons.Default.PersonAdd,
                            title = stringResource(R.string.add_driver),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToAddDriver
                        )
                    }
                } else {
                    // Portrait - Stack vertically with 2 cards per row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total Vehicles Card - Animated counter (1,2,3...N)
                        Card(
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToFleet,
                            elevation = CardDefaults.cardElevation(Elevation.low),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(BorderRadius.medium)
                        ) {
                            InfoCard(
                                icon = Icons.Default.LocalShipping,
                                title = stringResource(R.string.total_vehicles),
                                value = "${vehicleStats?.total ?: 0}",
                                modifier = Modifier.fillMaxWidth(),
                                animateValue = true,
                                targetCount = vehicleStats?.total ?: 0
                            )
                        }
                        
                        // Total Drivers Card - Animated counter (1,2,3...N)
                        Card(
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToDrivers,
                            elevation = CardDefaults.cardElevation(Elevation.low),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(BorderRadius.medium)
                        ) {
                            InfoCard(
                                icon = Icons.Default.People,
                                title = stringResource(R.string.total_drivers),
                                value = "${driverStats?.total ?: 0}",
                                modifier = Modifier.fillMaxWidth(),
                                iconTint = Secondary,
                                animateValue = true,
                                targetCount = driverStats?.total ?: 0
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Quick Actions - Add Vehicle & Add Driver
                    Text(
                        text = stringResource(R.string.quick_actions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard(
                            icon = Icons.Default.AddCircle,
                            title = stringResource(R.string.add_vehicle),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToAddVehicle
                        )
                        QuickActionCard(
                            icon = Icons.Default.PersonAdd,
                            title = stringResource(R.string.add_driver),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToAddDriver
                        )
                    }
                }
            }
        } // end Column
        // SnackbarHost — renders cancellation snackbar at bottom of screen
        androidx.compose.material3.SnackbarHost(
            hostState = cancelSnackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        } // end Box
    }
}

@Composable
fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(BorderRadius.medium),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun TripListItem(trip: com.weelo.logistics.data.model.Trip) {
    ListItemCard(
        title = trip.customerName,
        subtitle = "${trip.pickupLocation.address} → ${trip.dropLocation.address}",
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.LocalShipping,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(40.dp)
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(
                    text = when (trip.status) {
                        com.weelo.logistics.data.model.TripStatus.PENDING -> stringResource(R.string.status_pending)
                        com.weelo.logistics.data.model.TripStatus.ASSIGNED -> stringResource(R.string.status_assigned)
                        com.weelo.logistics.data.model.TripStatus.ACCEPTED -> stringResource(R.string.status_accepted)
                        com.weelo.logistics.data.model.TripStatus.AT_PICKUP -> stringResource(R.string.status_at_pickup)
                        com.weelo.logistics.data.model.TripStatus.LOADING_COMPLETE -> stringResource(R.string.status_loaded)
                        com.weelo.logistics.data.model.TripStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
                        com.weelo.logistics.data.model.TripStatus.COMPLETED -> stringResource(R.string.status_completed)
                        com.weelo.logistics.data.model.TripStatus.REJECTED -> stringResource(R.string.status_rejected)
                        com.weelo.logistics.data.model.TripStatus.CANCELLED -> stringResource(R.string.status_cancelled)
                    },
                    status = when (trip.status) {
                        com.weelo.logistics.data.model.TripStatus.PENDING -> ChipStatus.PENDING
                        com.weelo.logistics.data.model.TripStatus.IN_PROGRESS -> ChipStatus.IN_PROGRESS
                        com.weelo.logistics.data.model.TripStatus.COMPLETED -> ChipStatus.COMPLETED
                        com.weelo.logistics.data.model.TripStatus.CANCELLED -> ChipStatus.CANCELLED
                        else -> ChipStatus.AVAILABLE
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₹${String.format("%.0f", trip.fare)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        },
        onClick = { /* TODO: Navigate to trip details */ }
    )
}

@Composable
fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(BorderRadius.medium),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}
