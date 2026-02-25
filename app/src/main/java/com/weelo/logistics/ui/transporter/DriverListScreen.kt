package com.weelo.logistics.ui.transporter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.R
import com.weelo.logistics.data.api.DriverData
import com.weelo.logistics.data.cache.AppCache
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.SearchDebouncer
import com.weelo.logistics.utils.DataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DriverListScreen"

/**
 * DriverListScreen - Shows all drivers from backend database
 * 
 * RAPIDO-STYLE INSTANT NAVIGATION:
 * - Observes AppCache (shows cached data instantly)
 * - Background refresh (no blocking on navigation)
 * - Populates cache for DriverDetailsScreen
 */
@Composable
fun DriverListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddDriver: () -> Unit,
    onNavigateToDriverDetails: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // =========================================================================
    // RAPIDO-STYLE: Observe cached data (instant display)
    // =========================================================================
    val drivers by AppCache.drivers.collectAsState()
    val isLoading by AppCache.driversLoading.collectAsState()
    val errorMessage by AppCache.driversError.collectAsState()
    
    // Local UI state
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    
    val searchDebouncer = remember {
        SearchDebouncer<String>(500L, scope) { query ->
            debouncedSearchQuery = query
        }
    }
    
    // Computed stats from cache
    val totalDrivers = remember(drivers) { drivers.size }
    @Suppress("UNUSED_VARIABLE")
    val availableDrivers = remember(drivers) { drivers.count { it.isOnline && !it.isOnTrip } }
    @Suppress("UNUSED_VARIABLE")
    val onTripDrivers = remember(drivers) { drivers.count { it.isOnTrip } }
    
    // Background refresh function
    fun refreshDrivers(forceRefresh: Boolean = false) {
        if (!forceRefresh && AppCache.hasDriverData() && !AppCache.shouldRefreshDrivers()) {
            timber.log.Timber.d("📦 Using cached drivers (fresh)")
            return
        }
        
        scope.launch {
            AppCache.setDriversLoading(true)
            
            try {
                timber.log.Timber.d("🔄 Refreshing drivers in background...")
                
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.driverApi.getDriverList()
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    AppCache.setDrivers(data?.drivers ?: emptyList())
                    timber.log.Timber.d("✅ Cached ${data?.drivers?.size ?: 0} drivers")
                } else {
                    if (!AppCache.hasDriverData()) {
                        AppCache.setDriversError(response.body()?.error?.message ?: "Failed to load")
                    }
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "❌ Refresh failed")
                if (!AppCache.hasDriverData()) {
                    AppCache.setDriversError("Network error: ${e.message}")
                }
            } finally {
                AppCache.setDriversLoading(false)
            }
        }
    }
    
    // =========================================================================
    // RAPIDO-STYLE: Only refresh if stale or empty
    // =========================================================================
    LaunchedEffect(Unit) {
        if (AppCache.shouldRefreshDrivers()) {
            refreshDrivers()
        }
    }
    
    // =========================================================================
    // REAL-TIME: Listen for driver online/offline status changes via WebSocket
    // =========================================================================
    // When a driver goes online/offline, backend emits 'driver_status_changed'.
    // SocketIOService parses it → emits to driverStatusChanged flow.
    // We update AppCache in-place so the list updates INSTANTLY (no API call).
    // =========================================================================
    LaunchedEffect(Unit) {
        SocketIOService.driverStatusChanged.collect { event ->
            timber.log.Timber.i("📡 [DriverList] Real-time status: ${event.driverName} → ${if (event.isOnline) "ONLINE" else "OFFLINE"}")
            
            // Update the specific driver's isOnline field in AppCache
            val currentDrivers = AppCache.drivers.value
            val updatedDrivers = currentDrivers.map { driver ->
                if (driver.id == event.driverId) {
                    driver.copy(isOnline = event.isOnline)
                } else {
                    driver
                }
            }
            AppCache.setDrivers(updatedDrivers)
        }
    }
    
    // OPTIMIZATION: Use derivedStateOf to prevent unnecessary recomposition
    // Only recalculates when drivers, debouncedSearchQuery, or selectedFilter changes
    val filteredDrivers by remember(drivers, debouncedSearchQuery, selectedFilter) {
        derivedStateOf {
            drivers.filter { driver ->
                val matchesSearch = (driver.name ?: "").contains(debouncedSearchQuery, ignoreCase = true) ||
                        driver.phone.contains(debouncedSearchQuery)
                val matchesFilter = when (selectedFilter) {
                    "Available" -> driver.isOnline && !driver.isOnTrip
                    "On Trip" -> driver.isOnTrip
                    "Offline" -> !driver.isOnline
                    else -> true
                }
                matchesSearch && matchesFilter
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Surface)) {
            PrimaryTopBar(
                title = "Drivers ($totalDrivers)",
                onBackClick = onNavigateBack
            )
            
            Column(Modifier.fillMaxWidth().background(White).padding(16.dp)) {
                SearchTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it.trim()
                        searchDebouncer.search(it.trim())
                    },
                    placeholder = "Search by name or phone",
                    leadingIcon = Icons.Default.Search
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = selectedFilter == "Available",
                        onClick = { selectedFilter = "Available" },
                        label = { Text("Available") }
                    )
                    FilterChip(
                        selected = selectedFilter == "On Trip",
                        onClick = { selectedFilter = "On Trip" },
                        label = { Text("On Trip") }
                    )
                    FilterChip(
                        selected = selectedFilter == "Offline",
                        onClick = { selectedFilter = "Offline" },
                        label = { Text("Offline") }
                    )
                }
            }
            
            // RAPIDO-STYLE: Only show loading skeleton if loading AND no cached data
            if (isLoading && drivers.isEmpty()) {
                // First load - show skeleton for better perceived performance
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SkeletonList(itemCount = 5)
                }
            } else if (errorMessage != null && drivers.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.CloudOff, null, Modifier.size(64.dp), tint = TextDisabled)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            errorMessage ?: "Error loading drivers",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
            } else if (filteredDrivers.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    when {
                        searchQuery.isEmpty() && drivers.isEmpty() -> {
                            EmptyDrivers(
                                onAddDriver = onNavigateToAddDriver
                            )
                        }
                        searchQuery.isEmpty() -> {
                            EmptyStateHost(
                                spec = filterEmptyStateSpec(
                                    artwork = EmptyStateArtwork.FLEET_FILTER,
                                    title = stringResource(R.string.empty_title_driver_filter),
                                    subtitle = stringResource(R.string.empty_subtitle_driver_filter)
                                )
                            )
                        }
                        else -> {
                            EmptyStateHost(
                                spec = searchEmptyStateSpec(
                                    artwork = EmptyStateArtwork.DRIVER_SEARCH,
                                    title = stringResource(R.string.empty_title_driver_search),
                                    subtitle = stringResource(R.string.empty_subtitle_driver_search)
                                )
                            )
                        }
                    }
                }
            } else {
                // OPTIMIZED LazyColumn for smooth scrolling
                val listState = rememberLazyListState()
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = true
                ) {
                    items(
                        items = filteredDrivers, 
                        key = { it.id },
                        // Content type optimization for better recycling
                        contentType = { "driver_card" }
                    ) { driver ->
                        // Stable key wrapper prevents unnecessary recomposition
                        key(driver.id) {
                            DriverCardFromApi(driver) { onNavigateToDriverDetails(driver.id) }
                        }
                    }
                    
                    // Bottom spacing for FAB
                    item(key = "bottom_spacer") { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
        
        FloatingActionButton(
            onClick = onNavigateToAddDriver,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Primary
        ) {
            Icon(Icons.Default.Add, "Add Driver", tint = White)
        }
    }
}

/**
 * DriverCard for API data - OPTIMIZED for smooth scrolling
 * Uses remember for computed values to prevent recomposition
 */
@Composable
fun DriverCardFromApi(driver: DriverData, onClick: () -> Unit) {
    // Pre-compute values to avoid recalculation during scroll
    val initial = remember(driver.name, driver.phone) {
        (driver.name?.firstOrNull() ?: driver.phone.lastOrNull() ?: 'D').uppercase().toString()
    }
    
    val displayName = remember(driver.name) { driver.name ?: "Driver" }
    val phoneDisplay = remember(driver.phone) { "+91 ${driver.phone}" }
    val ratingDisplay = remember(driver.rating) { "⭐ ${String.format("%.1f", driver.rating ?: 0f)}" }
    val tripsDisplay = remember(driver.totalTrips) { "${driver.totalTrips} trips" }
    
    val (statusText, chipStatus) = remember(driver.isOnTrip, driver.isOnline) {
        when {
            driver.isOnTrip -> "On Trip" to ChipStatus.IN_PROGRESS
            driver.isOnline -> "Available" to ChipStatus.AVAILABLE
            else -> "Offline" to ChipStatus.COMPLETED
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(White)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Driver Avatar with online/offline indicator dot
            Box(
                Modifier
                    .size(60.dp),  // Slightly larger to accommodate dot
                Alignment.Center
            ) {
                if (!driver.profilePhotoUrl.isNullOrBlank()) {
                    // Show profile photo using Coil
                    OptimizedNetworkImage(
                        imageUrl = driver.profilePhotoUrl,
                        contentDescription = "Driver profile photo",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Surface),
                        crossfade = false,
                        targetSizeDp = 56.dp
                    )
                } else {
                    // Fallback to initials if no photo
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(Surface, androidx.compose.foundation.shape.CircleShape),
                        Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                }
                
                // Online/Offline indicator dot (WhatsApp/Rapido style)
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .background(
                            color = White,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .padding(2.dp)
                        .background(
                            color = when {
                                driver.isOnTrip -> Warning   // Orange for on-trip
                                driver.isOnline -> Success   // Green for online
                                else -> TextDisabled         // Grey for offline
                            },
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = phoneDisplay,
                    style = MaterialTheme.typography.bodyMedium, 
                    color = TextSecondary,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ratingDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tripsDisplay,
                        style = MaterialTheme.typography.bodySmall, 
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                // Show license number if available - only render if present
                if (driver.licenseNumber != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "License: ${driver.licenseNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Status chip - pre-computed values
            StatusChip(
                text = statusText,
                status = chipStatus
            )
        }
    }
}
