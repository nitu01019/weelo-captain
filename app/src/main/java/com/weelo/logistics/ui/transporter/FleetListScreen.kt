package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.Vehicle
import com.weelo.logistics.data.model.VehicleStatus
import com.weelo.logistics.data.repository.VehicleRepository
import com.weelo.logistics.data.repository.VehicleResult
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.SearchDebouncer
import com.weelo.logistics.utils.DataSanitizer
import kotlinx.coroutines.launch

// =============================================================================
// FLEET LIST SCREEN - Total Vehicles Management
// =============================================================================
// 
// This screen displays all vehicles in the transporter's fleet.
// 
// BACKEND INTEGRATION:
// - GET /api/v1/vehicles - Fetch all vehicles
// - GET /api/v1/vehicles?status={status} - Filter by status
// - GET /api/v1/vehicles?search={query} - Search vehicles
// 
// Response Model Expected:
// {
//   "success": true,
//   "data": {
//     "vehicles": [...],
//     "totalCount": 10,
//     "statusCounts": { "available": 5, "inTransit": 3, "maintenance": 2 }
//   }
// }
// =============================================================================

/**
 * Fleet List Screen - Shows all vehicles with search and filter
 * 
 * @param onNavigateBack Callback to navigate back
 * @param onNavigateToAddVehicle Callback to navigate to add vehicle screen
 * @param onNavigateToVehicleDetails Callback to navigate to vehicle details (vehicleId)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToVehicleDetails: (String) -> Unit
) {
    // ==========================================================================
    // STATE MANAGEMENT
    // ==========================================================================
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VehicleRepository.getInstance(context) }
    
    // Data states
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isStaleData by remember { mutableStateOf(false) }
    
    // Search & Filter states
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(VehicleFilterType.ALL) }
    
    // Search debouncer for performance
    val searchDebouncer = remember {
        SearchDebouncer<String>(500L, scope) { query ->
            debouncedSearchQuery = query
        }
    }
    
    // ==========================================================================
    // DATA LOADING WITH CACHING
    // ==========================================================================
    // Uses VehicleRepository with:
    // - In-memory caching (5 min validity)
    // - Stale-while-revalidate pattern
    // - Background preloading support
    
    // Load function that can be called for initial load and retry
    val loadVehicles: (Boolean) -> Unit = { forceRefresh ->
        scope.launch {
            if (forceRefresh) {
                isRefreshing = true
            } else {
                isLoading = true
            }
            errorMessage = null
            
            val result = repository.fetchVehicles(forceRefresh = forceRefresh)
            
            when (result) {
                is VehicleResult.Success -> {
                    // Map backend data to UI models
                    vehicles = repository.mapToUiModels(result.data.vehicles)
                    isStaleData = result.data.isStale
                    errorMessage = null
                }
                is VehicleResult.Error -> {
                    // Check if we have cached data to show
                    val cached = repository.getCachedVehicles()
                    if (cached != null) {
                        vehicles = repository.mapToUiModels(cached.vehicles)
                        isStaleData = true
                        // Show error but keep displaying cached data
                        errorMessage = if (vehicles.isEmpty()) result.message else null
                    } else {
                        errorMessage = result.message
                    }
                }
                else -> {
                    errorMessage = "Unexpected error"
                }
            }
            
            isLoading = false
            isRefreshing = false
        }
    }
    
    // Initial load - try cache first, then refresh
    LaunchedEffect(Unit) {
        // Check for cached data first for instant display
        val cached = repository.getCachedVehicles()
        if (cached != null) {
            vehicles = repository.mapToUiModels(cached.vehicles)
            isStaleData = cached.isStale
            isLoading = false
            
            // Refresh in background if stale
            if (cached.isStale) {
                loadVehicles(true)
            }
        } else {
            // No cache, fetch from backend
            loadVehicles(false)
        }
    }
    
    // ==========================================================================
    // DERIVED STATE - Filtered vehicles
    // ==========================================================================
    val filteredVehicles by remember(vehicles, debouncedSearchQuery, selectedFilter) {
        derivedStateOf {
            vehicles.filter { vehicle ->
                val matchesSearch = debouncedSearchQuery.isEmpty() ||
                        vehicle.vehicleNumber.contains(debouncedSearchQuery, ignoreCase = true) ||
                        vehicle.displayName.contains(debouncedSearchQuery, ignoreCase = true)
                val matchesFilter = when (selectedFilter) {
                    VehicleFilterType.ALL -> true
                    VehicleFilterType.AVAILABLE -> vehicle.status == VehicleStatus.AVAILABLE
                    VehicleFilterType.IN_TRANSIT -> vehicle.status == VehicleStatus.IN_TRANSIT
                    VehicleFilterType.MAINTENANCE -> vehicle.status == VehicleStatus.MAINTENANCE
                }
                matchesSearch && matchesFilter
            }
        }
    }
    
    // Status counts for filter badges
    val statusCounts by remember(vehicles) {
        derivedStateOf {
            VehicleStatusCounts(
                total = vehicles.size,
                available = vehicles.count { it.status == VehicleStatus.AVAILABLE },
                inTransit = vehicles.count { it.status == VehicleStatus.IN_TRANSIT },
                maintenance = vehicles.count { it.status == VehicleStatus.MAINTENANCE }
            )
        }
    }
    
    // ==========================================================================
    // UI LAYOUT
    // ==========================================================================
    Scaffold(
        topBar = {
            FleetTopBar(
                totalVehicles = vehicles.size,
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddVehicle,
                containerColor = Primary,
                contentColor = White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Vehicle"
                )
            }
        },
        containerColor = Surface
    ) { paddingValues ->
        // Content wrapper
        // Primary: Auto-loads on screen open (automatic)
        // Secondary: Refresh button available when data is stale
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search & Filter Section
            FleetSearchFilterSection(
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                    searchDebouncer.search(query.trim())
                },
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                statusCounts = statusCounts
            )
            
            // Refreshing indicator at top
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary
                )
            }
            
            // Stale data indicator with refresh button (backup option)
            if (isStaleData && vehicles.isNotEmpty() && !isRefreshing) {
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
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Showing cached data",
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { loadVehicles(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Refresh", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            // Content Section
            FleetContentSection(
                isLoading = isLoading,
                errorMessage = errorMessage,
                filteredVehicles = filteredVehicles,
                searchQuery = searchQuery,
                onVehicleClick = onNavigateToVehicleDetails,
                onRetry = { loadVehicles(false) },
                onAddVehicle = onNavigateToAddVehicle
            )
        }
    }
}

// =============================================================================
// SECTION COMPONENTS - Modular UI Building Blocks
// =============================================================================

/**
 * Top App Bar for Fleet Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FleetTopBar(
    totalVehicles: Int,
    onNavigateBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "My Vehicles",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (totalVehicles > 0) {
                    Text(
                        text = "$totalVehicles vehicles in fleet",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = White
        )
    )
}

/**
 * Search and Filter Section - Contains search bar and filter chips
 */
@Composable
private fun FleetSearchFilterSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: VehicleFilterType,
    onFilterSelected: (VehicleFilterType) -> Unit,
    statusCounts: VehicleStatusCounts
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Search Bar - Properly sized
            FleetSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Filter Chips - Horizontally scrollable
            FleetFilterChips(
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected,
                statusCounts = statusCounts
            )
        }
    }
}

/**
 * Compact Search Bar with proper sizing
 */
@Composable
private fun FleetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Search vehicles...",
                color = TextSecondary
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = TextSecondary
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = 56.dp),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            focusedBorderColor = Primary,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = Primary
        )
    )
}

/**
 * Filter Chips Row with status counts
 */
@Composable
private fun FleetFilterChips(
    selectedFilter: VehicleFilterType,
    onFilterSelected: (VehicleFilterType) -> Unit,
    statusCounts: VehicleStatusCounts
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VehicleFilterChip(
            label = "All",
            count = statusCounts.total,
            isSelected = selectedFilter == VehicleFilterType.ALL,
            onClick = { onFilterSelected(VehicleFilterType.ALL) }
        )
        VehicleFilterChip(
            label = "Available",
            count = statusCounts.available,
            isSelected = selectedFilter == VehicleFilterType.AVAILABLE,
            onClick = { onFilterSelected(VehicleFilterType.AVAILABLE) },
            selectedColor = Success
        )
        VehicleFilterChip(
            label = "In Transit",
            count = statusCounts.inTransit,
            isSelected = selectedFilter == VehicleFilterType.IN_TRANSIT,
            onClick = { onFilterSelected(VehicleFilterType.IN_TRANSIT) },
            selectedColor = Warning
        )
        VehicleFilterChip(
            label = "Maintenance",
            count = statusCounts.maintenance,
            isSelected = selectedFilter == VehicleFilterType.MAINTENANCE,
            onClick = { onFilterSelected(VehicleFilterType.MAINTENANCE) },
            selectedColor = Error
        )
    }
}

/**
 * Custom Filter Chip with count badge
 */
@Composable
private fun VehicleFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = Primary
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = label)
                if (count > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) White.copy(alpha = 0.2f) else Surface
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedColor,
            selectedLabelColor = White
        )
    )
}

/**
 * Main Content Section - Shows loading, empty, error, or vehicle list
 */
@Composable
private fun FleetContentSection(
    isLoading: Boolean,
    errorMessage: String?,
    filteredVehicles: List<Vehicle>,
    searchQuery: String,
    onVehicleClick: (String) -> Unit,
    onRetry: () -> Unit,
    onAddVehicle: () -> Unit
) {
    when {
        isLoading -> {
            FleetLoadingState()
        }
        errorMessage != null -> {
            FleetErrorState(
                message = errorMessage,
                onRetry = onRetry
            )
        }
        filteredVehicles.isEmpty() -> {
            FleetEmptyState(
                isSearching = searchQuery.isNotEmpty(),
                onAddVehicle = onAddVehicle
            )
        }
        else -> {
            FleetVehicleList(
                vehicles = filteredVehicles,
                onVehicleClick = onVehicleClick
            )
        }
    }
}

/**
 * Loading State with skeleton
 */
@Composable
private fun FleetLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Primary)
    }
}

/**
 * Error State with retry button
 */
@Composable
private fun FleetErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * Empty State - No vehicles found
 */
@Composable
private fun FleetEmptyState(
    isSearching: Boolean,
    onAddVehicle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Default.SearchOff else Icons.Default.LocalShipping,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isSearching) "No vehicles found" else "No vehicles yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isSearching) 
                    "Try adjusting your search or filters" 
                else 
                    "Add your first vehicle to start managing your fleet",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            if (!isSearching) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onAddVehicle,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Vehicle")
                }
            }
        }
    }
}

/**
 * Vehicle List - LazyColumn with optimized rendering
 */
@Composable
private fun FleetVehicleList(
    vehicles: List<Vehicle>,
    onVehicleClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = vehicles,
            key = { it.id }
        ) { vehicle ->
            VehicleListCard(
                vehicle = vehicle,
                onClick = { onVehicleClick(vehicle.id) }
            )
        }
    }
}

// =============================================================================
// DATA MODELS - For Backend Integration
// =============================================================================

/**
 * Filter types for vehicle list
 */
enum class VehicleFilterType {
    ALL,
    AVAILABLE,
    IN_TRANSIT,
    MAINTENANCE
}

/**
 * Vehicle status counts for filter badges
 */
data class VehicleStatusCounts(
    val total: Int = 0,
    val available: Int = 0,
    val inTransit: Int = 0,
    val maintenance: Int = 0
)

@Composable
fun VehicleListCard(
    vehicle: Vehicle,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vehicle Icon - Use proper drawable image
            vehicle.category.imageResId?.let { imageRes ->
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = vehicle.category.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Surface),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } ?: Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Surface, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = vehicle.category.name,
                    modifier = Modifier.size(32.dp),
                    tint = Primary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Vehicle Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.vehicleNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = vehicle.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = vehicle.capacityText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Status Chip
            StatusChip(
                text = when (vehicle.status) {
                    VehicleStatus.AVAILABLE -> "Available"
                    VehicleStatus.IN_TRANSIT -> "In Transit"
                    VehicleStatus.MAINTENANCE -> "Maintenance"
                    VehicleStatus.INACTIVE -> "Inactive"
                },
                status = when (vehicle.status) {
                    VehicleStatus.AVAILABLE -> ChipStatus.AVAILABLE
                    VehicleStatus.IN_TRANSIT -> ChipStatus.IN_PROGRESS
                    VehicleStatus.MAINTENANCE -> ChipStatus.PENDING
                    VehicleStatus.INACTIVE -> ChipStatus.CANCELLED
                }
            )
        }
    }
}
