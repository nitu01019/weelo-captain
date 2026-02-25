package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.api.DriverData
import com.weelo.logistics.data.cache.AppCache
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DriverDetailsScreen"

/**
 * DriverDetailsScreen - Shows driver details
 * 
 * RAPIDO-STYLE INSTANT NAVIGATION:
 * - First checks AppCache for instant display (0ms)
 * - Background refresh only if not in cache
 * - Back button shows cached data immediately
 */
@Composable
fun DriverDetailsScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPerformance: (String) -> Unit = {},
    onNavigateToEarnings: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    
    // =========================================================================
    // RAPIDO-STYLE: Check cache FIRST for instant display
    // =========================================================================
    var driver by remember { mutableStateOf<DriverData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }
    
    // Phase 5: Real performance + earnings data (no more hardcoded 0s)
    var performanceData by remember { mutableStateOf<com.weelo.logistics.data.api.PerformanceResponseData?>(null) }
    var earningsThisMonth by remember { mutableStateOf(0.0) }
    var earningsLastMonth by remember { mutableStateOf<Double?>(null) }
    var pendingPayment by remember { mutableStateOf<Double?>(null) }
    
    // =========================================================================
    // REAL-TIME: Listen for this driver's online/offline status changes
    // =========================================================================
    LaunchedEffect(driverId) {
        SocketIOService.driverStatusChanged.collect { event ->
            if (event.driverId == driverId) {
                timber.log.Timber.i("📡 [DriverDetails] Real-time status: ${event.driverName} → ${if (event.isOnline) "ONLINE" else "OFFLINE"}")
                driver = driver?.copy(isOnline = event.isOnline)
            }
        }
    }
    
    // Try cache first (INSTANT)
    LaunchedEffect(driverId, reloadTrigger) {
        // Step 1: Check cache immediately (0ms)
        val cachedDriver = AppCache.getDriver(driverId)
        if (cachedDriver != null) {
            timber.log.Timber.d("📦 Showing cached driver: ${cachedDriver.name}")
            driver = cachedDriver
            // Don't refresh if data is fresh
            if (!AppCache.shouldRefreshDrivers()) {
                return@LaunchedEffect
            }
        }
        
        // Step 2: Background fetch (only if not cached or stale)
        if (driver == null) {
            isLoading = true
        }
        
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.driverApi.getDriverList()
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val drivers = response.body()?.data?.drivers ?: emptyList()
                    // Update cache
                    AppCache.setDrivers(drivers)
                    // Update local state
                    driver = drivers.find { it.id == driverId }
                    if (driver == null && cachedDriver == null) {
                        errorMessage = "Driver not found"
                    }
                } else if (driver == null) {
                    errorMessage = response.body()?.error?.message ?: "Failed to load driver"
                }
                
                // Phase 5: Fetch real performance + earnings for this driver
                try {
                    val perfResponse = withContext(Dispatchers.IO) {
                        RetrofitClient.driverApi.getDriverPerformance(driverId = driverId)
                    }
                    if (perfResponse.isSuccessful) {
                        performanceData = perfResponse.body()?.data
                    }
                } catch (e: Exception) {
                    timber.log.Timber.w("⚠️ Performance fetch failed: ${e.message}")
                }
                
                try {
                    val earningsResponse = withContext(Dispatchers.IO) {
                        RetrofitClient.driverApi.getDriverEarnings(period = "month", driverId = driverId)
                    }
                    if (earningsResponse.isSuccessful) {
                        earningsThisMonth = earningsResponse.body()?.data?.totalEarnings ?: 0.0
                        // Current API does not return last-month/pending-payment fields.
                        earningsLastMonth = null
                        pendingPayment = null
                    }
                } catch (e: Exception) {
                    timber.log.Timber.w("⚠️ Earnings fetch failed: ${e.message}")
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "❌ Fetch failed")
                if (driver == null) {
                    errorMessage = "Network error: ${e.message}"
                }
            } finally {
                isLoading = false
            }
        }
    }
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Driver Details", onBackClick = onNavigateBack)
        
        if (isLoading) {
            SkeletonProfileLoading(Modifier.fillMaxSize())
        } else if (errorMessage != null) {
            RetryErrorStatePanel(
                title = if (errorMessage == "Driver not found") "Driver not found" else "Could not load driver",
                message = errorMessage ?: "Failed to load driver details",
                onRetry = {
                    errorMessage = null
                    driver = null
                    isLoading = true
                    reloadTrigger += 1
                },
                illustrationRes = EmptyStateArtwork.DRIVER_DETAILS_NOT_FOUND.drawableRes,
                modifier = Modifier.fillMaxSize()
            )
        } else driver?.let { d ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroEntityCard(
                    title = d.name ?: "Driver",
                    subtitle = "+91 ${d.phone}",
                    mediaSpec = CardMediaSpec(artwork = CardArtwork.DETAIL_DRIVER),
                    leadingAvatar = {
                        Box(
                            Modifier.size(64.dp),
                            Alignment.Center
                        ) {
                            Box(
                                Modifier.size(56.dp).background(Primary.copy(alpha = 0.1f), CircleShape),
                                Alignment.Center
                            ) {
                                val initial = (d.name?.firstOrNull() ?: d.phone.lastOrNull() ?: 'D').uppercase()
                                Text(
                                    text = initial.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(
                                        color = White,
                                        shape = CircleShape
                                    )
                                    .padding(2.dp)
                                    .background(
                                        color = when {
                                            d.isOnTrip -> Warning   // Orange for on-trip
                                            d.isOnline -> Success   // Green for online
                                            else -> TextDisabled    // Grey for offline
                                        },
                                        shape = CircleShape
                                    )
                            )
                        }
                    },
                    statusContent = {
                        StatusChip(
                            text = when {
                                d.isOnTrip -> "On Trip"
                                d.isOnline -> "Available"
                                else -> "Offline"
                            },
                            status = when {
                                d.isOnTrip -> ChipStatus.IN_PROGRESS
                                d.isOnline -> ChipStatus.AVAILABLE
                                else -> ChipStatus.COMPLETED
                            }
                        )
                    },
                    metaContent = {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                color = SurfaceVariant
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Trips", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${performanceData?.totalTrips ?: d.totalTrips}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                color = SurfaceVariant
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Rating", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = String.format(
                                            "%.1f",
                                            performanceData?.rating ?: d.rating?.toDouble() ?: 4.5
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                )
                
                // Contact Information - Real data
                SectionCard("Contact Information") {
                    DetailRow("Phone", "+91 ${d.phone}")
                    if (d.licenseNumber != null) {
                        Divider()
                        DetailRow("License", d.licenseNumber)
                    }
                    if (d.email != null) {
                        Divider()
                        DetailRow("Email", d.email)
                    }
                }
                
                // Performance - Phase 5: Real data from GET /api/v1/driver/performance?driverId=X
                SectionCard("Performance") {
                    DetailRow("Rating", "⭐ ${String.format("%.1f", performanceData?.rating ?: d.rating?.toDouble() ?: 4.5)}")
                    Divider()
                    DetailRow("Total Trips", "${performanceData?.totalTrips ?: d.totalTrips}")
                    Divider()
                    DetailRow("Acceptance Rate", "${String.format("%.1f", performanceData?.acceptanceRate ?: 0.0)}%")
                    Divider()
                    DetailRow("Completion Rate", "${String.format("%.1f", performanceData?.completionRate ?: 0.0)}%")
                    Divider()
                    DetailRow("On-Time Rate", "${String.format("%.1f", performanceData?.onTimeDeliveryRate ?: 0.0)}%")
                    Divider()
                    DetailRow("Total Distance", "${String.format("%.1f", performanceData?.totalDistance ?: 0.0)} km")
                }
                
                // Earnings Summary - Phase 5: Real data from GET /api/v1/driver/earnings?driverId=X
                SectionCard("Earnings Summary") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("This Month", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                "₹${String.format("%,.0f", earningsThisMonth)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Success
                            )
                        }
                        earningsLastMonth?.let { lastMonth ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Last Month", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                Text(
                                    "₹${String.format("%,.0f", lastMonth)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    pendingPayment?.let { pending ->
                        Spacer(Modifier.height(8.dp))
                        Divider()
                        Spacer(Modifier.height(8.dp))
                        DetailRow("Pending Payment", "₹${String.format("%,.0f", pending)}")
                    }
                }
                
                // Documents
                SectionCard("Documents") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("License", style = MaterialTheme.typography.bodyMedium)
                            Text(d.licenseNumber ?: "Not provided", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        StatusChip(
                            text = if (d.licenseNumber != null) "Provided" else "Pending",
                            status = if (d.licenseNumber != null) ChipStatus.AVAILABLE else ChipStatus.PENDING
                        )
                    }
                }
                
                // Assigned Vehicle - if any
                d.assignedVehicleNumber?.let { vehicleNumber ->
                    SectionCard("Assigned Vehicle") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalShipping, null, tint = Primary)
                                Spacer(Modifier.width(12.dp))
                                Text(vehicleNumber, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            }
                            StatusChip("Assigned", ChipStatus.AVAILABLE)
                        }
                    }
                }
                
                // Action Buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("Performance", onClick = { onNavigateToPerformance(driverId) }, modifier = Modifier.weight(1f))
                    SecondaryButton("Earnings", onClick = { onNavigateToEarnings(driverId) }, modifier = Modifier.weight(1f))
                }
                
                PrimaryButton("Assign Vehicle", onClick = { /* TODO: Will connect to backend later */ })
                SecondaryButton("View Trip History", onClick = { /* TODO: Will connect to backend later */ })
            }
        }
    }
}
