package com.weelo.logistics.ui.driver

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
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    // Optimize: Use derivedStateOf to prevent unnecessary recompositions
    val driverProfile by remember {
        derivedStateOf {
            when (val state = dashboardState) {
                is DriverDashboardState.Success -> DrawerUserProfile(
                    id = state.data.driverId,
                    name = "Driver",
                    phone = "",
                    role = "driver"
                )
                else -> null
            }
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
    
    // Main Navigation Drawer - Optimized for smooth performance
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp) // Slightly narrower for faster animation
            ) {
                DrawerContentInternal(
                    userProfile = driverProfile,
                    isLoading = dashboardState is DriverDashboardState.Loading,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = dashboardState) {
                is DriverDashboardState.Loading -> {
                    LoadingState()
                }
                
                is DriverDashboardState.Success -> {
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
                
                is DriverDashboardState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.loadDashboardData() }
                    )
                }
            }
        }
        } // End Scaffold
    } // End ModalNavigationDrawer
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
            containerColor = White
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
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
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
            
            // Earnings Card
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
            
            // Trip Stats Grid
            item {
                TripStatsGrid(
                    earnings = data.earnings,
                    performance = data.performance
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
private fun EarningsCard(earnings: EarningsSummary) {
    val hasEarnings = earnings.today > 0 || earnings.weekly > 0 || earnings.monthly > 0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasEarnings) Primary else Primary.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                    color = White,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (!hasEarnings) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = White.copy(alpha = 0.6f),
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
                            color = White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Start accepting trips to earn!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = White.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Your earnings will be calculated automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.6f),
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
                            color = White.copy(alpha = 0.8f)
                        )
                        AnimatedCounter(
                            targetValue = earnings.today,
                            prefix = "₹",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                color = White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "${earnings.todayTrips} trips",
                            style = MaterialTheme.typography.bodySmall,
                            color = White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            if (hasEarnings) {
                Divider(color = White.copy(alpha = 0.3f))
                
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
                        containerColor = White.copy(alpha = 0.2f)
                    )
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
                                tint = White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Pending Payment",
                                style = MaterialTheme.typography.bodyMedium,
                                color = White
                            )
                        }
                        
                        Text(
                            text = "₹${earnings.pendingPayment.roundToInt()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = White
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
    onOpenFullMap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun TripStatsGrid(
    earnings: EarningsSummary,
    performance: PerformanceMetrics
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Quick Stats",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
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
// Loading & Error States
// =============================================================================

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(5) {
            ShimmerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
        }
    }
}

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
// Helper Functions
// =============================================================================

private fun getCurrentGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }
}

private fun getStatusText(status: TripProgressStatus): String {
    return when (status) {
        TripProgressStatus.EN_ROUTE_TO_PICKUP -> "Heading to Pickup"
        TripProgressStatus.AT_PICKUP -> "At Pickup Location"
        TripProgressStatus.IN_TRANSIT -> "In Transit"
        TripProgressStatus.AT_DROP -> "At Drop Location"
        TripProgressStatus.COMPLETED -> "Completed"
    }
}

private fun getNotificationIcon(type: DashNotificationType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        DashNotificationType.NEW_TRIP_REQUEST -> Icons.Default.DirectionsCar
        DashNotificationType.TRIP_ASSIGNED -> Icons.Default.Assignment
        DashNotificationType.TRIP_CANCELLED -> Icons.Default.Cancel
        DashNotificationType.PAYMENT_RECEIVED -> Icons.Default.Payment
        DashNotificationType.RATING_RECEIVED -> Icons.Default.Star
        DashNotificationType.SYSTEM_ALERT -> Icons.Default.Warning
        DashNotificationType.PROMOTIONAL -> Icons.Default.CardGiftcard
    }
}

private fun getNotificationColor(type: DashNotificationType): androidx.compose.ui.graphics.Color {
    return when (type) {
        DashNotificationType.NEW_TRIP_REQUEST -> Primary
        DashNotificationType.TRIP_ASSIGNED -> Success
        DashNotificationType.TRIP_CANCELLED -> Error
        DashNotificationType.PAYMENT_RECEIVED -> Success
        DashNotificationType.RATING_RECEIVED -> Warning
        DashNotificationType.SYSTEM_ALERT -> Error
        DashNotificationType.PROMOTIONAL -> Secondary
    }
}

private fun formatTripDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
        days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
        else -> formatTripDate(timestamp)
    }
}
