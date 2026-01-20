package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.api.DriverData
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.SearchDebouncer
import com.weelo.logistics.utils.DataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DriverListScreen - Shows all drivers from backend database
 * 
 * Fetches real data from: GET /api/v1/driver/list
 * No mock data - only real drivers saved in database
 */
@Composable
fun DriverListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddDriver: () -> Unit,
    onNavigateToDriverDetails: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var drivers by remember { mutableStateOf<List<DriverData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var totalDrivers by remember { mutableStateOf(0) }
    var availableDrivers by remember { mutableStateOf(0) }
    var onTripDrivers by remember { mutableStateOf(0) }
    
    val searchDebouncer = remember {
        SearchDebouncer<String>(500L, scope) { query ->
            debouncedSearchQuery = query
        }
    }
    
    // Fetch drivers from backend
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.driverApi.getDriverList()
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    drivers = data?.drivers ?: emptyList()
                    totalDrivers = data?.total ?: 0
                    availableDrivers = data?.online ?: 0
                    // onTrip count will be calculated from drivers list
                    onTripDrivers = drivers.count { it.isOnTrip }
                } else {
                    errorMessage = response.body()?.error?.message ?: "Failed to load drivers"
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.message}"
            } finally {
                isLoading = false
            }
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
            
            if (isLoading) {
                // OPTIMIZATION: Skeleton loading for better perceived performance
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SkeletonList(itemCount = 5)
                }
            } else if (errorMessage != null) {
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Person, null, Modifier.size(64.dp), tint = TextDisabled)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isEmpty() && drivers.isEmpty()) 
                                "No drivers yet\nTap + to add your first driver" 
                            else if (searchQuery.isEmpty()) 
                                "No drivers match filter"
                            else 
                                "No drivers found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDrivers, key = { it.id }) { driver ->
                        DriverCardFromApi(driver) { onNavigateToDriverDetails(driver.id) }
                    }
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
 * DriverCard for API data - displays driver from backend
 */
@Composable
fun DriverCardFromApi(driver: DriverData, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(2.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(White)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Driver Avatar
            Box(
                Modifier.size(56.dp).background(Surface, androidx.compose.foundation.shape.CircleShape),
                Alignment.Center
            ) {
                val initial = (driver.name?.firstOrNull() ?: driver.phone.lastOrNull() ?: 'D').uppercase()
                Text(
                    text = initial.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = driver.name ?: "Driver",
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "+91 ${driver.phone}",
                    style = MaterialTheme.typography.bodyMedium, 
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Show rating if available, else show 0
                    Text(
                        text = "⭐ ${String.format("%.1f", driver.rating ?: 0f)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(8.dp))
                    // Show total trips (0 for new drivers)
                    Text(
                        text = "${driver.totalTrips} trips",
                        style = MaterialTheme.typography.bodySmall, 
                        color = TextSecondary
                    )
                }
                // Show license number if available
                driver.licenseNumber?.let { license ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "License: $license",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Status chip based on online/trip status
            StatusChip(
                text = when {
                    driver.isOnTrip -> "On Trip"
                    driver.isOnline -> "Available"
                    else -> "Offline"
                },
                status = when {
                    driver.isOnTrip -> ChipStatus.IN_PROGRESS
                    driver.isOnline -> ChipStatus.AVAILABLE
                    else -> ChipStatus.COMPLETED
                }
            )
        }
    }
}
