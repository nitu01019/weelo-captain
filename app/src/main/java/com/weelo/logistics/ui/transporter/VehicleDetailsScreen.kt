package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.components.SkeletonVehicleDetailsLoading
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "VehicleDetails"

/**
 * VehicleDetailsScreen - Show vehicle details from backend
 * 
 * Fetches from: GET /api/v1/vehicles/{vehicleId}
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun VehicleDetailsScreen(
    vehicleId: String,
    onNavigateBack: () -> Unit,
    onEdit: (String) -> Unit = {} // Optional edit callback
) {
    val scope = rememberCoroutineScope()
    
    // State
    var vehicle by remember { mutableStateOf<VehicleData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    
    // Load vehicle details
    fun loadVehicle() {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                timber.log.Timber.d("Fetching vehicle: $vehicleId")
                
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.vehicleApi.getVehicleById(vehicleId)
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    vehicle = response.body()?.data?.vehicle
                    timber.log.Timber.d("Loaded vehicle: ${vehicle?.vehicleNumber}")
                } else {
                    errorMessage = response.body()?.error?.message ?: "Failed to load vehicle"
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error loading vehicle")
                errorMessage = e.localizedMessage ?: "Error loading vehicle"
            } finally {
                isLoading = false
            }
        }
    }
    
    // Delete vehicle
    fun deleteVehicle() {
        scope.launch {
            isDeleting = true
            
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.vehicleApi.deleteVehicle(vehicleId)
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    timber.log.Timber.d("Vehicle deleted successfully")
                    onNavigateBack()
                } else {
                    errorMessage = response.body()?.error?.message ?: "Failed to delete vehicle"
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error deleting vehicle")
                errorMessage = e.localizedMessage ?: "Error deleting vehicle"
            } finally {
                isDeleting = false
                showDeleteDialog = false
            }
        }
    }
    
    // Initial load
    LaunchedEffect(vehicleId) {
        loadVehicle()
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = Error) },
            title = { Text("Delete Vehicle?") },
            text = { 
                Text("Are you sure you want to delete ${vehicle?.vehicleNumber}? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = { deleteVehicle() },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Delete", color = Error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // Top Bar
        PrimaryTopBar(
            title = vehicle?.vehicleNumber ?: "Vehicle Details",
            onBackClick = onNavigateBack
        )
        
        when {
            isLoading -> {
                SkeletonVehicleDetailsLoading(
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = Error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(errorMessage ?: "Error", color = TextSecondary)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { loadVehicle() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            
            vehicle != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Vehicle Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Icon
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(40.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalShipping,
                                    null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Primary
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Vehicle Number
                            Text(
                                text = vehicle!!.vehicleNumber,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            
                            // Type & Subtype
                            Text(
                                text = "${vehicle!!.vehicleType.replaceFirstChar { it.uppercase() }} • ${vehicle!!.vehicleSubtype}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            // Status Badge
                            val (statusText, statusColor) = when (vehicle!!.status) {
                                "available" -> "Available" to Success
                                "in_transit" -> "In Transit" to Primary
                                "maintenance" -> "Maintenance" to Warning
                                else -> "Inactive" to TextDisabled
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = statusColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = statusText,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = statusColor
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Details Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                "Vehicle Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            
                            DetailRow("Capacity", vehicle!!.capacity)
                            DetailRow("Model", vehicle!!.model ?: "Not specified")
                            DetailRow("Year", vehicle!!.year?.toString() ?: "Not specified")
                            
                            if (vehicle!!.rcNumber != null) {
                                DetailRow("RC Number", vehicle!!.rcNumber!!)
                            }
                            if (vehicle!!.insuranceNumber != null) {
                                DetailRow("Insurance", vehicle!!.insuranceNumber!!)
                            }
                            
                            DetailRow(
                                "Verified",
                                if (vehicle!!.isVerified) "Yes" else "Pending"
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // Delete Button
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Vehicle")
                    }
                    
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}
