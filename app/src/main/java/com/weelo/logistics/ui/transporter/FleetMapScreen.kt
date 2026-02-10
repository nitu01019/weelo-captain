package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FleetMapScreen"

/**
 * =============================================================================
 * FLEET MAP SCREEN - Ola/Uber Style Fleet Tracking
 * =============================================================================
 * 
 * Shows ALL transporter's trucks/drivers on a single map view.
 * 
 * HOW IT WORKS (The Secret):
 * ──────────────────────────
 * 1. Backend sends fleet locations via GET /api/v1/tracking/fleet
 * 2. We poll every 5 seconds (or use WebSocket for real-time)
 * 3. Frontend INTERPOLATES between location updates
 * 4. Each truck marker moves SMOOTHLY (not jumpy!)
 * 5. Transporter sees "live" fleet movement
 * 
 * BACKEND ENDPOINT:
 * ─────────────────
 * GET /api/v1/tracking/fleet
 * Response: {
 *   transporterId: "...",
 *   activeDrivers: 5,
 *   drivers: [
 *     { driverId, vehicleNumber, latitude, longitude, speed, bearing, status, lastUpdated }
 *   ]
 * }
 * 
 * FOR PRODUCTION:
 * ───────────────
 * 1. Integrate Google Maps Compose: implementation 'com.google.maps.android:maps-compose:2.11.4'
 * 2. Add markers for each driver with custom truck icons
 * 3. Use MarkerAnimationHelper for smooth marker movement
 * 4. Color-code markers by status (green=available, orange=in_transit, red=offline)
 * 
 * @author Weelo Team
 * @version 1.0.0
 * =============================================================================
 */
@Composable
fun FleetMapScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDriverDetails: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // ==========================================================================
    // STATE
    // ==========================================================================
    
    var fleetData by remember { mutableStateOf<FleetTrackingData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_VARIABLE")
    var selectedDriver by remember { mutableStateOf<DriverLocation?>(null) }
    
    // Map state (for when Google Maps is integrated)
    @Suppress("UNUSED_VARIABLE")
    var isMapReady by remember { mutableStateOf(false) }
    
    // ==========================================================================
    // LOAD FLEET DATA - Polls every 5 seconds
    // ==========================================================================
    
    /**
     * Fetch fleet locations from backend
     * 
     * BACKEND INTEGRATION:
     * - Endpoint: GET /api/v1/tracking/fleet
     * - Auth: Bearer token (transporter only)
     * - Returns: All active driver locations
     */
    suspend fun loadFleetData() {
        try {
            timber.log.Timber.d("📍 Fetching fleet locations...")
            
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.trackingApi.getFleetTracking()
            }
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                fleetData = data
                timber.log.Timber.d("✅ Loaded ${data?.activeDrivers ?: 0} active drivers")
            } else {
                timber.log.Timber.e("❌ Failed to load fleet: ${response.code()}")
                if (fleetData == null) {
                    errorMessage = "Failed to load fleet data"
                }
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "❌ Exception loading fleet")
            if (fleetData == null) {
                errorMessage = e.localizedMessage ?: "Network error"
            }
        } finally {
            isLoading = false
        }
    }
    
    // Initial load
    LaunchedEffect(Unit) {
        loadFleetData()
    }
    
    // ==========================================================================
    // REAL-TIME POLLING - Every 5 seconds
    // ==========================================================================
    // 
    // WHY POLLING (not WebSocket here):
    // - Fleet overview doesn't need sub-second updates
    // - Polling is simpler and sufficient
    // - For individual trip tracking, use WebSocket
    // 
    // IN PRODUCTION:
    // - Consider WebSocket for larger fleets
    // - Or increase interval to 10s for >100 vehicles
    // ==========================================================================
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000) // Poll every 5 seconds
            loadFleetData()
        }
    }
    
    // ==========================================================================
    // UI
    // ==========================================================================
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
        ) {
            // ══════════════════════════════════════════════════════════════════
            // TOP BAR
            // ══════════════════════════════════════════════════════════════════
            
            PrimaryTopBar(
                title = "Fleet Map",
                onBackClick = onNavigateBack,
                actions = {
                    // Refresh button
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                loadFleetData()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = White
                        )
                    }
                }
            )
            
            // ══════════════════════════════════════════════════════════════════
            // STATS BAR
            // ══════════════════════════════════════════════════════════════════
            
            fleetData?.let { data ->
                FleetStatsBar(
                    totalDrivers = data.activeDrivers,
                    inTransit = data.drivers.count { it.status == "in_transit" },
                    available = data.drivers.count { it.status == "available" || it.status == "pending" },
                    offline = data.drivers.count { it.status == "offline" || it.status == "completed" }
                )
            }
            
            // ══════════════════════════════════════════════════════════════════
            // MAP VIEW
            // ══════════════════════════════════════════════════════════════════
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Surface),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading && fleetData == null -> {
                        // Loading state
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Primary)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Loading fleet locations...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    
                    errorMessage != null && fleetData == null -> {
                        // Error state
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = Error
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                errorMessage ?: "Error",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Error
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        errorMessage = null
                                        isLoading = true
                                        loadFleetData()
                                    }
                                }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                    
                    fleetData?.drivers?.isEmpty() == true -> {
                        // Empty state
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.LocalShipping,
                                null,
                                modifier = Modifier.size(80.dp),
                                tint = Primary.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No Active Drivers",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Your drivers will appear here\nwhen they start trips",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextDisabled
                            )
                        }
                    }
                    
                    else -> {
                        // ══════════════════════════════════════════════════════
                        // MAP PLACEHOLDER
                        // ══════════════════════════════════════════════════════
                        // 
                        // TODO: Replace with actual Google Maps integration
                        // 
                        // INTEGRATION STEPS:
                        // 1. Add dependency: implementation 'com.google.maps.android:maps-compose:2.11.4'
                        // 2. Add Google Maps API key in AndroidManifest.xml
                        // 3. Replace this Box with:
                        //    GoogleMap(
                        //        modifier = Modifier.fillMaxSize(),
                        //        cameraPositionState = cameraPositionState
                        //    ) {
                        //        fleetData?.drivers?.forEach { driver ->
                        //            Marker(
                        //                state = MarkerState(position = LatLng(driver.latitude, driver.longitude)),
                        //                title = driver.vehicleNumber,
                        //                icon = getMarkerIcon(driver.status)
                        //            )
                        //        }
                        //    }
                        // 
                        // 4. For smooth animation, use MarkerAnimationHelper
                        // ══════════════════════════════════════════════════════
                        
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Map,
                                null,
                                modifier = Modifier.size(80.dp),
                                tint = Primary.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "FLEET MAP VIEW",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Integrate Google Maps here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextDisabled
                            )
                            Spacer(Modifier.height(24.dp))
                            
                            // Show driver count
                            fleetData?.let { data ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Primary.copy(alpha = 0.1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.LocalShipping,
                                            null,
                                            tint = Primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "${data.activeDrivers} Active Drivers",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Primary
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Live indicator
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Success,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(White)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "LIVE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                            }
                        }
                    }
                }
            }
            
            // ══════════════════════════════════════════════════════════════════
            // DRIVER LIST (Bottom Panel)
            // ══════════════════════════════════════════════════════════════════
            
            fleetData?.drivers?.takeIf { it.isNotEmpty() }?.let { drivers ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TextDisabled)
                                .align(Alignment.CenterHorizontally)
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Text(
                            "Active Drivers (${drivers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Driver cards (horizontal scroll or first few)
                        drivers.take(3).forEach { driver ->
                            DriverLocationCard(
                                driver = driver,
                                onClick = { onNavigateToDriverDetails(driver.driverId) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        if (drivers.size > 3) {
                            TextButton(
                                onClick = { /* TODO: Show all drivers */ },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("View All ${drivers.size} Drivers")
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// COMPONENTS
// =============================================================================

/**
 * Fleet stats bar showing driver counts by status
 */
@Composable
private fun FleetStatsBar(
    totalDrivers: Int,
    inTransit: Int,
    available: Int,
    offline: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FleetStatItem(
                count = totalDrivers,
                label = "Total",
                color = Primary
            )
            FleetStatItem(
                count = inTransit,
                label = "In Transit",
                color = Warning
            )
            FleetStatItem(
                count = available,
                label = "Available",
                color = Success
            )
            FleetStatItem(
                count = offline,
                label = "Offline",
                color = TextDisabled
            )
        }
    }
}

@Composable
private fun FleetStatItem(
    count: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

/**
 * Driver location card showing current status
 */
@Composable
private fun DriverLocationCard(
    driver: DriverLocation,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (driver.status) {
                            "in_transit" -> Warning
                            "available", "pending" -> Success
                            else -> TextDisabled
                        }
                    )
            )
            
            Spacer(Modifier.width(12.dp))
            
            // Driver info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = driver.vehicleNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = driver.driverName ?: "Driver ${driver.driverId.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            // Speed
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${driver.speed.toInt()} km/h",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = driver.status.replace("_", " ").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (driver.status) {
                        "in_transit" -> Warning
                        "available", "pending" -> Success
                        else -> TextDisabled
                    }
                )
            }
            
            Spacer(Modifier.width(8.dp))
            
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = TextDisabled
            )
        }
    }
}

// =============================================================================
// DATA MODELS (Should match backend response)
// =============================================================================

/**
 * Fleet tracking response from backend
 * Endpoint: GET /api/v1/tracking/fleet
 */
data class FleetTrackingData(
    val transporterId: String,
    val activeDrivers: Int,
    val drivers: List<DriverLocation>
)

/**
 * Individual driver location
 */
data class DriverLocation(
    val driverId: String,
    val driverName: String? = null,
    val vehicleNumber: String,
    val tripId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val bearing: Float,
    val status: String,
    val lastUpdated: String
)
