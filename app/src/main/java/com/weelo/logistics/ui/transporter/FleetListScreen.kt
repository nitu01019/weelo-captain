package com.weelo.logistics.ui.transporter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.api.VehicleData
import com.weelo.logistics.data.cache.AppCache
import com.weelo.logistics.data.cache.VehicleStats
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FleetListScreen"

/**
 * FleetListScreen - Shows all vehicles from backend
 * 
 * RAPIDO-STYLE INSTANT NAVIGATION:
 * - Observes AppCache (shows cached data instantly)
 * - Background refresh (no blocking on navigation)
 * - Back button shows cached data (no reload)
 * 
 * Fetches from: GET /api/v1/vehicles/list
 */
@Composable
fun FleetListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToVehicleDetails: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // =========================================================================
    // RAPIDO-STYLE: Observe cached data (instant display)
    // =========================================================================
    val vehicles by AppCache.vehicles.collectAsState()
    val isLoading by AppCache.vehiclesLoading.collectAsState()
    val errorMessage by AppCache.vehiclesError.collectAsState()
    val stats by AppCache.vehicleStats.collectAsState()
    
    // Filter state (local - doesn't need caching)
    var selectedFilter by remember { mutableStateOf("All") }
    
    // Background refresh function
    fun refreshVehicles(forceRefresh: Boolean = false) {
        // Skip if we have data and it's fresh (unless forced)
        if (!forceRefresh && AppCache.hasVehicleData() && !AppCache.shouldRefreshVehicles()) {
            timber.log.Timber.d("📦 Using cached data (fresh)")
            return
        }
        
        scope.launch {
            AppCache.setVehiclesLoading(true)
            
            try {
                timber.log.Timber.d("🔄 Refreshing vehicles in background...")
                
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.vehicleApi.getVehicles()
                }
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.data != null) {
                        AppCache.setVehicles(
                            vehicles = body.data.vehicles,
                            stats = VehicleStats(
                                total = body.data.total,
                                available = body.data.available,
                                inTransit = body.data.inTransit,
                                maintenance = body.data.maintenance
                            )
                        )
                        timber.log.Timber.d("✅ Cached ${body.data.vehicles.size} vehicles")
                    } else {
                        AppCache.setVehiclesError(body?.error?.message ?: "No data")
                    }
                } else {
                    AppCache.setVehiclesError("Error ${response.code()}")
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "❌ Refresh failed")
                // Only show error if no cached data
                if (!AppCache.hasVehicleData()) {
                    AppCache.setVehiclesError(e.message ?: "Network error")
                }
            } finally {
                AppCache.setVehiclesLoading(false)
            }
        }
    }
    
    // =========================================================================
    // RAPIDO-STYLE: Only refresh if stale or empty
    // NOT on every screen open!
    // =========================================================================
    LaunchedEffect(Unit) {
        if (AppCache.shouldRefreshVehicles()) {
            refreshVehicles()
        }
    }
    
    // Filtered vehicles (computed from cache)
    val filteredVehicles = remember(vehicles, selectedFilter) {
        when (selectedFilter) {
            "Available" -> vehicles.filter { it.status == "available" }
            "In Transit" -> vehicles.filter { it.status == "in_transit" }
            "Maintenance" -> vehicles.filter { it.status == "maintenance" }
            else -> vehicles
        }
    }
    
    // UI
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
        ) {
            // Top Bar
            PrimaryTopBar(
                title = "My Fleet (${stats.total})",
                onBackClick = onNavigateBack
            )
            
            // Stats Row - RAPIDO-STYLE: Always show if we have cached data
            if (vehicles.isNotEmpty() || stats.total > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(White)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatChip(
                        label = "Available",
                        count = stats.available,
                        color = Success
                    )
                    StatChip(
                        label = "In Transit",
                        count = stats.inTransit,
                        color = Primary
                    )
                    StatChip(
                        label = "Maintenance",
                        count = stats.maintenance,
                        color = Warning
                    )
                }
            }
            
            // Filter Chips - RAPIDO-STYLE: Always show if we have data
            if (vehicles.isNotEmpty() || stats.total > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(White)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Available", "In Transit", "Maintenance").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                }
                
                Divider(color = Divider)
            }
            
            // Content - RAPIDO-STYLE: Show cached data during refresh
            when {
                // Only show spinner if loading AND no cached data
                isLoading && vehicles.isEmpty() -> {
                    // First load - show spinner
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Primary)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Loading vehicles...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                // Only show error if no cached data available
                errorMessage != null && vehicles.isEmpty() -> {
                    // Error State - only show if we have NO cached data
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Error
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                errorMessage ?: "Error",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(onClick = { refreshVehicles(forceRefresh = true) }) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
                
                filteredVehicles.isEmpty() -> {
                    // Empty State
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.LocalShipping,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = TextDisabled
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (vehicles.isEmpty()) "No vehicles yet" else "No vehicles match filter",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (vehicles.isEmpty()) "Tap + to add your first vehicle" else "Try a different filter",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                else -> {
                    // Vehicle List - OPTIMIZED for smooth scrolling
                    val listState = rememberLazyListState()
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        // Prefetch more items for smoother scrolling
                        userScrollEnabled = true
                    ) {
                        items(
                            items = filteredVehicles,
                            key = { it.id },
                            // Content type optimization - all items same type
                            contentType = { "vehicle_card" }
                        ) { vehicle ->
                            // Stable composition - prevents recomposition during scroll
                            key(vehicle.id) {
                                FleetVehicleCard(
                                    vehicle = vehicle,
                                    onClick = { onNavigateToVehicleDetails(vehicle.id) }
                                )
                            }
                        }
                        
                        // Bottom spacing for FAB
                        item(key = "bottom_spacer") { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
        }
        
        // FAB - Add Vehicle
        FloatingActionButton(
            onClick = onNavigateToAddVehicle,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Primary
        ) {
            Icon(Icons.Default.Add, "Add Vehicle", tint = White)
        }
    }
}

/**
 * Stat Chip for showing counts
 */
@Composable
private fun StatChip(
    label: String,
    count: Int,
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
 * Vehicle Card - OPTIMIZED for smooth scrolling
 * Uses remember for computed values to prevent recomposition
 */
@Composable
fun FleetVehicleCard(
    vehicle: VehicleData,
    onClick: () -> Unit
) {
    // Pre-compute values to avoid recalculation during scroll
    val vehicleTypeDisplay = remember(vehicle.vehicleType, vehicle.vehicleSubtype) {
        "${vehicle.vehicleType.replaceFirstChar { it.uppercase() }} • ${vehicle.vehicleSubtype}"
    }
    
    val (statusText, statusColor) = remember(vehicle.status) {
        when (vehicle.status) {
            "available" -> "Available" to Success
            "in_transit" -> "In Transit" to Primary
            "maintenance" -> "Maintenance" to Warning
            else -> "Inactive" to TextDisabled
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Hardware acceleration for smoother rendering
                clip = true
            },
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon - simplified for performance
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Surface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Primary
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Info - use weight for flexible layout
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.vehicleNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = vehicleTypeDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = vehicle.capacity,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            
            // Status Badge - pre-computed colors
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = statusColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                    maxLines = 1
                )
            }
        }
    }
}
