package com.weelo.logistics.ui.transporter

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.api.VehicleData
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
 * Fetches from: GET /api/v1/vehicles/list
 * Clean rewrite with proper error handling
 */
@Composable
fun FleetListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToVehicleDetails: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // State
    var vehicles by remember { mutableStateOf<List<VehicleData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Stats
    var totalCount by remember { mutableStateOf(0) }
    var availableCount by remember { mutableStateOf(0) }
    var inTransitCount by remember { mutableStateOf(0) }
    var maintenanceCount by remember { mutableStateOf(0) }
    
    // Filter
    var selectedFilter by remember { mutableStateOf("All") }
    
    // Load vehicles
    fun loadVehicles() {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                Log.d(TAG, "Fetching vehicles from backend...")
                
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.vehicleApi.getVehicles()
                }
                
                Log.d(TAG, "Response code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d(TAG, "Response body: $body")
                    
                    if (body?.success == true && body.data != null) {
                        vehicles = body.data.vehicles
                        totalCount = body.data.total
                        availableCount = body.data.available
                        inTransitCount = body.data.inTransit
                        maintenanceCount = body.data.maintenance
                        
                        Log.d(TAG, "Loaded ${vehicles.size} vehicles")
                    } else {
                        // Success false or no data
                        val errMsg = body?.error?.message ?: "No vehicles data returned"
                        Log.e(TAG, "API returned error: $errMsg")
                        errorMessage = errMsg
                    }
                } else {
                    // HTTP error
                    val errBody = response.errorBody()?.string()
                    Log.e(TAG, "HTTP ${response.code()}: $errBody")
                    
                    errorMessage = when (response.code()) {
                        401 -> "Session expired. Please login again."
                        403 -> "Access denied. Transporter account required."
                        404 -> "Vehicle service not available."
                        500 -> "Server error. Please try again."
                        else -> "Error ${response.code()}: Unable to load vehicles"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading vehicles", e)
                errorMessage = when (e) {
                    is java.net.ConnectException -> "Cannot connect to server. Check your connection."
                    is java.net.SocketTimeoutException -> "Connection timed out. Try again."
                    is java.net.UnknownHostException -> "No internet connection."
                    else -> "Error: ${e.localizedMessage ?: "Unknown error"}"
                }
            } finally {
                isLoading = false
            }
        }
    }
    
    // Initial load
    LaunchedEffect(Unit) {
        loadVehicles()
    }
    
    // Filtered vehicles
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
                title = "My Fleet ($totalCount)",
                onBackClick = onNavigateBack
            )
            
            // Stats Row
            if (!isLoading && errorMessage == null && vehicles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(White)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatChip(
                        label = "Available",
                        count = availableCount,
                        color = Success
                    )
                    StatChip(
                        label = "In Transit",
                        count = inTransitCount,
                        color = Primary
                    )
                    StatChip(
                        label = "Maintenance",
                        count = maintenanceCount,
                        color = Warning
                    )
                }
            }
            
            // Filter Chips
            if (!isLoading && errorMessage == null) {
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
            
            // Content
            when {
                isLoading -> {
                    // Loading State
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
                
                errorMessage != null -> {
                    // Error State
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
                            OutlinedButton(onClick = { loadVehicles() }) {
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
                    // Vehicle List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = filteredVehicles,
                            key = { it.id }
                        ) { vehicle ->
                            FleetVehicleCard(
                                vehicle = vehicle,
                                onClick = { onNavigateToVehicleDetails(vehicle.id) }
                            )
                        }
                        
                        // Bottom spacing for FAB
                        item { Spacer(Modifier.height(72.dp)) }
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
 * Vehicle Card - Clean design
 */
@Composable
fun FleetVehicleCard(
    vehicle: VehicleData,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
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
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.vehicleNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${vehicle.vehicleType.replaceFirstChar { it.uppercase() }} • ${vehicle.vehicleSubtype}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = vehicle.capacity,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            // Status Badge
            val (statusText, statusColor) = when (vehicle.status) {
                "available" -> "Available" to Success
                "in_transit" -> "In Transit" to Primary
                "maintenance" -> "Maintenance" to Warning
                else -> "Inactive" to TextDisabled
            }
            
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = statusColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }
        }
    }
}
