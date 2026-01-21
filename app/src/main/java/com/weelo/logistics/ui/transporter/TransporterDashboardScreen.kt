package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
    
    // Dashboard state
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var vehicleStats by remember { mutableStateOf<VehicleListData?>(null) }
    var driverStats by remember { mutableStateOf<DriverListData?>(null) }
    var isBackendConnected by remember { mutableStateOf(false) }
    
    // User profile state
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isProfileLoading by remember { mutableStateOf(true) }
    
    // OPTIMIZATION: Fetch all data in parallel for faster loading
    LaunchedEffect(Unit) {
        isLoading = true
        isProfileLoading = true
        errorMessage = null
        
        // Launch all API calls in parallel using async
        coroutineScope {
            val profileDeferred = async(Dispatchers.IO) {
                try { RetrofitClient.profileApi.getProfile() } catch (e: Exception) { null }
            }
            val vehicleDeferred = async(Dispatchers.IO) {
                try { RetrofitClient.vehicleApi.getVehicles() } catch (e: Exception) { null }
            }
            val driverDeferred = async(Dispatchers.IO) {
                try { RetrofitClient.driverApi.getDriverList() } catch (e: Exception) { null }
            }
            
            // Process profile immediately when ready
            val profileResponse = profileDeferred.await()
            if (profileResponse?.isSuccessful == true && profileResponse.body()?.success == true) {
                userProfile = profileResponse.body()?.data?.user
            }
            isProfileLoading = false
            
            // Process other responses
            val vehicleResponse = vehicleDeferred.await()
            android.util.Log.d("Dashboard", "Vehicle API response: ${vehicleResponse?.code()} - ${vehicleResponse?.body()}")
            if (vehicleResponse?.isSuccessful == true && vehicleResponse.body()?.success == true) {
                vehicleStats = vehicleResponse.body()?.data
                android.util.Log.d("Dashboard", "Vehicle stats loaded: total=${vehicleStats?.total}")
                isBackendConnected = true
            } else {
                android.util.Log.e("Dashboard", "Vehicle API failed: ${vehicleResponse?.errorBody()?.string()}")
            }
            
            val driverResponse = driverDeferred.await()
            if (driverResponse?.isSuccessful == true && driverResponse.body()?.success == true) {
                driverStats = driverResponse.body()?.data
                isBackendConnected = true
            }
            
            if (profileResponse == null && vehicleResponse == null && driverResponse == null) {
                errorMessage = "Cannot connect to backend"
                isBackendConnected = false
            }
        }
        
        isLoading = false
    }
    
    // Convert UserProfile to DrawerUserProfile
    val drawerProfile = userProfile?.let {
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
    
    // Navigation drawer menu items
    val menuItems = createTransporterMenuItems(
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
        }
    )
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                // Use the NavigationDrawer components
                DrawerContentInternal(
                    userProfile = drawerProfile,
                    isLoading = isProfileLoading,
                    selectedItemId = "dashboard",
                    menuItems = menuItems,
                    onProfileClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToProfile()
                    },
                    onLogout = {
                        scope.launch { drawerState.close() }
                        // Clear tokens and logout
                        RetrofitClient.clearAllData()
                        onLogout()
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
        ) {
            // Top Bar with Hamburger Menu
            TopAppBar(
                title = {
                    Text(
                        text = "Dashboard",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    // Availability Toggle (Compact) - Online/Offline Status
                    AvailabilityToggleCompact(
                        modifier = Modifier.padding(end = 4.dp),
                        onStatusChanged = { isOnline ->
                            android.util.Log.d("Dashboard", "Transporter availability: ${if (isOnline) "ONLINE" else "OFFLINE"}")
                        }
                    )
                    
                    IconButton(onClick = { /* TODO: Navigate to notifications */ }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.White
                )
            )
            
            // Show loading indicator
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Welcome Message with User Name
                    userProfile?.let { profile ->
                        Text(
                            text = "Welcome, ${profile.name?.split(" ")?.firstOrNull() ?: "Transporter"}!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    
                    // Statistics - Total Vehicles & Total Drivers
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
                                title = "Total Vehicles",
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
                                title = "Total Drivers",
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
                        text = "Quick Actions",
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
                            title = "Add Vehicle",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToAddVehicle
                        )
                        QuickActionCard(
                            icon = Icons.Default.PersonAdd,
                            title = "Add Driver",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToAddDriver
                        )
                    }
                }
            }
        }
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
                        com.weelo.logistics.data.model.TripStatus.PENDING -> "Pending"
                        com.weelo.logistics.data.model.TripStatus.ASSIGNED -> "Assigned"
                        com.weelo.logistics.data.model.TripStatus.ACCEPTED -> "Accepted"
                        com.weelo.logistics.data.model.TripStatus.IN_PROGRESS -> "In Progress"
                        com.weelo.logistics.data.model.TripStatus.COMPLETED -> "Completed"
                        com.weelo.logistics.data.model.TripStatus.REJECTED -> "Rejected"
                        com.weelo.logistics.data.model.TripStatus.CANCELLED -> "Cancelled"
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
