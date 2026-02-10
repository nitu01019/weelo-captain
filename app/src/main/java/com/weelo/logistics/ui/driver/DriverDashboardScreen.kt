package com.weelo.logistics.ui.driver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weelo.logistics.data.model.*
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.components.rememberScreenConfig
import com.weelo.logistics.ui.components.responsiveGridColumns
import com.weelo.logistics.ui.components.responsiveHorizontalPadding
import com.weelo.logistics.ui.components.OfflineBanner
import com.weelo.logistics.offline.NetworkMonitor
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * DriverDashboardScreen - Dynamic driver dashboard with real-time features
 * 
 * Features:
 * - Real-time earnings display with animations
 * - Active trip tracking with map preview
 * - Performance metrics and statistics
 * - Recent trip history
 * - Notifications with badge count
 * - Online/Offline status toggle
 * - Pull-to-refresh
 * 
 * Backend Integration:
 * - All API endpoints documented in ViewModel and DashboardModels.kt
 * - Mock data can be replaced without UI changes
 * - Repository pattern ready for Retrofit integration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun DriverDashboardScreen(
    viewModel: DriverDashboardViewModel = viewModel(),
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToTripHistory: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToEarnings: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenFullMap: (String) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val dashboardState by viewModel.dashboardState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    // NOTE: viewModel.isInitialLoad is available for future use (e.g., showing
    // different UI on first launch vs returning user). Currently, the conditional
    // drawer + AnimatedContent handles state transitions automatically.
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    // Network monitoring for offline banner
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    val isOnline by networkMonitor.isOnline.collectAsState()
    
    // =========================================================================
    // CACHED DRAWER PROFILE — prevents flickering
    // =========================================================================
    // Uses remember + mutableStateOf to HOLD the last known profile.
    // On Loading state, we keep showing the cached profile instead of null,
    // which prevents the drawer header from flickering between states.
    //
    // PERFORMANCE: O(1) — just a reference swap, no allocation on recompose.
    // =========================================================================
    var cachedProfile by remember { mutableStateOf<DrawerUserProfile?>(null) }
    val driverProfile = remember(dashboardState) {
        when (val state = dashboardState) {
            is DriverDashboardState.Success -> {
                val profile = DrawerUserProfile(
                    id = state.data.driverId,
                    name = "Driver",
                    phone = "",
                    role = "driver"
                )
                cachedProfile = profile
                profile
            }
            // Keep showing cached profile during loading/error — no flicker
            else -> cachedProfile
        }
    }
    
    // Optimize: Use derivedStateOf for notification count
    val notificationCount by remember {
        derivedStateOf {
            when (val state = dashboardState) {
                is DriverDashboardState.Success -> state.data.notifications.count { !it.isRead }
                else -> 0
            }
        }
    }
    
    // Optimize: Remember callbacks to prevent recreation
    val closeDrawerAndNavigate: (() -> Unit) -> Unit = remember(scope, drawerState) {
        { action ->
            scope.launch { 
                drawerState.close()
                action()
            }
        }
    }
    
    // Optimize: Create menu items only when notificationCount changes
    val menuItems = remember(notificationCount) {
        createDriverMenuItems(
            onDashboard = { },
            onTripHistory = { },
            onEarnings = { },
            onDocuments = { },
            onSettings = { },
            notificationCount = notificationCount
        )
    }
    
    // Load data when screen first opens
    LaunchedEffect(Unit) {
        viewModel.loadDashboardData()
    }
    
    // BACK BUTTON DISABLED - User must explicitly logout from profile
    // Back press is consumed but does nothing
    androidx.activity.compose.BackHandler {
        // Do nothing - prevents going back to login/role selection
    }
    
    // =========================================================================
    // CONDITIONAL DRAWER — Production-Grade Anti-Flicker
    // =========================================================================
    // PROBLEM: ModalNavigationDrawer composes its drawer sheet on EVERY frame,
    // even when closed. On the first frame, the internal swipeable offset
    // hasn't settled → drawer briefly flickers visible.
    //
    // SOLUTION: During Loading state, don't render ModalNavigationDrawer AT ALL.
    // Show only the skeleton + top bar. Once data arrives (Success), wrap in
    // the full drawer layout. This eliminates all drawer-related flicker.
    //
    // PERFORMANCE: Zero drawer overhead during loading phase.
    // SCALABILITY: Works on all devices regardless of speed.
    // =========================================================================
    
    // Map state to a simple content key for AnimatedContent.
    // This prevents re-animation when only data INSIDE Success changes
    // (e.g., toggling online status, marking notification as read).
    val contentKey = when (dashboardState) {
        is DriverDashboardState.Idle -> "loading"
        is DriverDashboardState.Loading -> "loading"
        is DriverDashboardState.Success -> "content"
        is DriverDashboardState.Error -> "error"
    }
    
    // Drawer visibility — only compose when actually opening/open
    val isDrawerVisible by remember {
        derivedStateOf {
            drawerState.isOpen || drawerState.isAnimationRunning
        }
    }
    
    when {
        // =====================================================================
        // IDLE / LOADING STATE — No drawer, just skeleton + top bar
        // =====================================================================
        // Idle = initial state before data fetch starts
        // Loading = data fetch took > 150ms, showing skeleton
        // Both show the same skeleton — no drawer overhead
        // =====================================================================
        dashboardState is DriverDashboardState.Idle || dashboardState is DriverDashboardState.Loading -> {
            Scaffold(
                topBar = {
                    DriverDashboardTopBar(
                        driverName = "Driver",
                        unreadCount = 0,
                        onMenuClick = { /* Drawer disabled during loading */ },
                        onNotificationsClick = { },
                        onProfileClick = { }
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    DriverDashboardSkeleton()
                }
            }
        }
        
        // =====================================================================
        // SUCCESS / ERROR — Full drawer + content with smooth transitions
        // =====================================================================
        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = if (isDrawerVisible) Modifier.width(280.dp) else Modifier.width(0.dp),
                        drawerContainerColor = if (isDrawerVisible) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        }
                    ) {
                        if (isDrawerVisible) {
                            DrawerContentInternal(
                                userProfile = driverProfile,
                                isLoading = false,
                                selectedItemId = "dashboard",
                                menuItems = menuItems,
                                onProfileClick = {
                                    closeDrawerAndNavigate(onNavigateToProfile)
                                },
                                onLogout = {
                                    closeDrawerAndNavigate(onLogout)
                                }
                            )
                        }
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        DriverDashboardTopBar(
                            driverName = "Driver",
                            unreadCount = notificationCount,
                            onMenuClick = remember(scope, drawerState) { { scope.launch { drawerState.open() } } },
                            onNotificationsClick = onNavigateToNotifications,
                            onProfileClick = onNavigateToProfile
                        )
                    }
                ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Offline Banner
                    OfflineBanner(
                        isOffline = !isOnline,
                        onRetryClick = { viewModel.loadDashboardData() }
                    )
                    
                    // =============================================================
                    // ANIMATED CONTENT — Smooth fade between states
                    // =============================================================
                    // Uses contentKey (string) instead of full state object to
                    // prevent re-animation when only data inside Success changes.
                    //
                    // Asymmetric timing: fadeIn(300ms) + fadeOut(150ms)
                    // Content appears smoothly, old state vanishes quickly.
                    // This is the Material 3 recommended pattern.
                    // =============================================================
                    Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = contentKey,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(150))
                        },
                        label = "dashboard_animated_content"
                    ) { key ->
                        when (key) {
                            "content" -> {
                                val state = dashboardState
                                if (state is DriverDashboardState.Success) {
                                    DashboardContent(
                                        data = state.data,
                                        onToggleOnlineStatus = { viewModel.toggleOnlineStatus() },
                                        onRefresh = { viewModel.refresh() },
                                        onOpenFullMap = onOpenFullMap,
                                        onNavigateToTripHistory = onNavigateToTripHistory,
                                        onMarkNotificationAsRead = { viewModel.markNotificationAsRead(it) },
                                        isRefreshing = isRefreshing
                                    )
                                }
                            }
                            
                            "error" -> {
                                val state = dashboardState
                                if (state is DriverDashboardState.Error) {
                                    ErrorState(
                                        message = state.message,
                                        onRetry = { viewModel.loadDashboardData() }
                                    )
                                }
                            }
                            
                            else -> {
                                // "loading" — shouldn't reach here (handled above)
                                // but safety fallback
                                DriverDashboardSkeleton()
                            }
                        }
                    }
                    } // End Box
                } // End Column
                } // End Scaffold
            } // End ModalNavigationDrawer
        }
    } // End when
}

/**
 * Driver Dashboard Top Bar with hamburger menu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverDashboardTopBar(
    driverName: String,
    unreadCount: Int,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Welcome, ${driverName.split(" ").firstOrNull() ?: "Driver"}!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = getCurrentGreeting(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = TextPrimary
                )
            }
        },
        actions = {
            // Notifications with badge
            IconButton(onClick = onNotificationsClick) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = TextPrimary
                    )
                    if (unreadCount > 0) {
                        Badge(
                            containerColor = Error,
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            // Profile
            IconButton(onClick = onProfileClick) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Profile",
                    tint = TextPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Surface
        )
    )
}

// Keep old DashboardTopBar for backward compatibility (can be removed later)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    unreadCount: Int,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = getCurrentGreeting(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        actions = {
            IconButton(onClick = onNotificationsClick) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications"
                    )
                    if (unreadCount > 0) {
                        NotificationBadge(
                            count = unreadCount,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
            }
            
            IconButton(onClick = onProfileClick) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Profile"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun DashboardContent(
    data: DashboardData,
    onToggleOnlineStatus: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFullMap: (String) -> Unit,
    onNavigateToTripHistory: () -> Unit,
    onMarkNotificationAsRead: (String) -> Unit,
    isRefreshing: Boolean
) {
    // Responsive layout configuration
    val screenConfig = rememberScreenConfig()
    val horizontalPadding = responsiveHorizontalPadding()
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = horizontalPadding,
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Online Status Toggle
            item {
                OnlineStatusToggle(
                    isOnline = data.isOnline,
                    onToggle = onToggleOnlineStatus,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // In landscape mode, show Earnings and Active Trip side by side
            if (screenConfig.isLandscape && data.activeTrip != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        EarningsCard(
                            earnings = data.earnings,
                            modifier = Modifier.weight(1f)
                        )
                        ActiveTripCard(
                            trip = data.activeTrip,
                            onOpenFullMap = { onOpenFullMap(data.activeTrip.tripId) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Portrait mode - stack vertically
                item {
                    EarningsCard(earnings = data.earnings)
                }
                
                // Active Trip (if exists)
                if (data.activeTrip != null) {
                    item {
                        ActiveTripCard(
                            trip = data.activeTrip,
                            onOpenFullMap = { onOpenFullMap(data.activeTrip.tripId) }
                        )
                    }
                }
            }
            
            // Trip Stats Grid - responsive columns
            item {
                TripStatsGrid(
                    earnings = data.earnings,
                    performance = data.performance,
                    isLandscape = screenConfig.isLandscape
                )
            }
            
            // Performance Metrics
            item {
                PerformanceMetricsCard(performance = data.performance)
            }
            
            // Recent Trips
            item {
                RecentTripsHeader(onViewAll = onNavigateToTripHistory)
            }
            
            if (data.recentTrips.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = "No Trips Yet",
                        message = "Your completed trips will appear here. Start accepting trips from transporters!",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // OPTIMIZATION: Add keys to prevent unnecessary recompositions
                items(
                    items = data.recentTrips.take(5),
                    key = { it.tripId }
                ) { trip ->
                    TripHistoryItem(trip = trip)
                }
            }
            
            // Notifications Preview
            item {
                Text(
                    text = "Recent Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            if (data.notifications.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.NotificationsNone,
                        title = "No Notifications",
                        message = "You're all caught up! New trip requests and updates will appear here.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // OPTIMIZATION: Add keys to prevent unnecessary recompositions
                items(
                    items = data.notifications.take(3),
                    key = { it.id }
                ) { notification ->
                    NotificationItem(
                        notification = notification,
                        onMarkAsRead = { onMarkNotificationAsRead(notification.id) }
                    )
                }
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// =============================================================================
// Dashboard Components
// =============================================================================

@Composable
private fun EarningsCard(
    earnings: EarningsSummary,
    modifier: Modifier = Modifier
) {
    val hasEarnings = earnings.today > 0 || earnings.weekly > 0 || earnings.monthly > 0
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Primary  // Saffron Yellow
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Earnings",
                    style = MaterialTheme.typography.titleMedium,
                    color = Secondary,  // Black text on yellow
                    fontWeight = FontWeight.Bold
                )
                
                if (!hasEarnings) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Secondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            if (!hasEarnings) {
                // Empty state for earnings
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "₹0",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = Secondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Start accepting trips to earn!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Secondary.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Your earnings will be calculated automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = Secondary.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // Today's Earnings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.bodySmall,
                            color = Secondary.copy(alpha = 0.7f)
                        )
                        AnimatedCounter(
                            targetValue = earnings.today,
                            prefix = "₹",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                color = Secondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "${earnings.todayTrips} trips",
                            style = MaterialTheme.typography.bodySmall,
                            color = Secondary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            if (hasEarnings) {
                Divider(color = Secondary.copy(alpha = 0.2f))
                
                // Weekly and Monthly
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    EarningsPeriod(
                        label = "This Week",
                        amount = earnings.weekly,
                        trips = earnings.weeklyTrips
                    )
                    
                    EarningsPeriod(
                        label = "This Month",
                        amount = earnings.monthly,
                        trips = earnings.monthlyTrips
                    )
                }
            }
            
            // Pending Payment
            if (earnings.pendingPayment > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Secondary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Pending Payment",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Text(
                            text = "₹${earnings.pendingPayment.roundToInt()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EarningsPeriod(
    label: String,
    amount: Double,
    trips: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = White.copy(alpha = 0.8f)
        )
        AnimatedCounter(
            targetValue = amount,
            prefix = "₹",
            style = MaterialTheme.typography.titleLarge.copy(
                color = White,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = "$trips trips",
            style = MaterialTheme.typography.bodySmall,
            color = White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ActiveTripCard(
    trip: ActiveTrip,
    onOpenFullMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SuccessLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Success)
                    )
                    Text(
                        text = "Active Trip",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Success
                    )
                }
                
                Text(
                    text = getStatusText(trip.currentStatus),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            // Trip Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.customerName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = trip.vehicleType,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${trip.estimatedEarning.roundToInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Success
                    )
                    Text(
                        text = "${trip.estimatedDistance.roundToInt()} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            // Map Preview
            MapPreviewCard(
                pickupAddress = trip.pickupAddress,
                dropAddress = trip.dropAddress,
                onOpenFullMap = onOpenFullMap
            )
            
            // Time Elapsed
            val elapsedMinutes = ((System.currentTimeMillis() - trip.startTime) / 60000).toInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Time Elapsed: ${elapsedMinutes} mins",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "ETA: ${trip.estimatedDuration} mins",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun TripStatsGrid(
    earnings: EarningsSummary,
    performance: PerformanceMetrics,
    isLandscape: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Quick Stats",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        // In landscape, show more stats in a row
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.CheckCircle,
                    value = "${performance.totalTrips}",
                    label = "Total Trips",
                    modifier = Modifier.weight(1f),
                    iconColor = Success
                )
                
                StatCard(
                    icon = Icons.Default.Route,
                    value = "${performance.totalDistance.roundToInt()} km",
                    label = "Distance",
                    modifier = Modifier.weight(1f),
                    iconColor = Secondary
                )
                
                StatCard(
                    icon = Icons.Default.Star,
                    value = String.format("%.1f", performance.rating),
                    label = "Rating",
                    modifier = Modifier.weight(1f),
                    iconColor = Warning
                )
                
                StatCard(
                    icon = Icons.Default.Timer,
                    value = "${performance.onTimeDeliveryRate.roundToInt()}%",
                    label = "On-Time",
                    modifier = Modifier.weight(1f),
                    iconColor = Info
                )
            }
        } else {
            // Portrait - 2 columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.CheckCircle,
                    value = "${performance.totalTrips}",
                    label = "Total Trips",
                    modifier = Modifier.weight(1f),
                    iconColor = Success
                )
                
                StatCard(
                    icon = Icons.Default.Route,
                    value = "${performance.totalDistance.roundToInt()} km",
                    label = "Distance",
                    modifier = Modifier.weight(1f),
                    iconColor = Secondary
                )
            }
        }
    }
}

@Composable
private fun PerformanceMetricsCard(performance: PerformanceMetrics) {
    val hasPerformance = performance.totalTrips > 0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Performance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            if (!hasPerformance) {
                // Empty state for performance
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = TextDisabled,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No Performance Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Complete trips to build your performance metrics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PerformanceIndicator(
                        percentage = (performance.rating / 5.0) * 100,
                        label = "Rating\n${String.format("%.1f", performance.rating)}⭐",
                        color = Warning
                    )
                    
                    PerformanceIndicator(
                        percentage = performance.acceptanceRate,
                        label = "Acceptance\nRate",
                        color = Success
                    )
                    
                    PerformanceIndicator(
                        percentage = performance.onTimeDeliveryRate,
                        label = "On-Time\nDelivery",
                        color = Secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTripsHeader(onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recent Trips",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        TextButton(onClick = onViewAll) {
            Text(text = "View All")
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TripHistoryItem(trip: CompletedTrip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = trip.customerName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = trip.vehicleType,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "${trip.distance.roundToInt()} km • ${trip.duration} mins",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = formatTripDate(trip.completedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDisabled
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "₹${trip.earnings.roundToInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Success
                )
                if (trip.rating != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Warning,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = String.format("%.1f", trip.rating),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: DriverNotification,
    onMarkAsRead: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isRead) InfoLight else Surface
        ),
        onClick = if (!notification.isRead) onMarkAsRead else ({})
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = getNotificationIcon(notification.type),
                contentDescription = null,
                tint = getNotificationColor(notification.type),
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTimeAgo(notification.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDisabled
                )
            }
            
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Primary)
                )
            }
        }
    }
}

// =============================================================================
// Error State
// =============================================================================
// NOTE: LoadingState() removed — replaced by DriverDashboardSkeleton()
// from SkeletonLoading.kt for a polished, content-shaped shimmer experience.
// =============================================================================

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Oops! Something went wrong",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRetry) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

// =============================================================================
// Helper Functions - Now in DriverDashboardUtils.kt
// Import from: com.weelo.logistics.ui.driver.*
// =============================================================================
