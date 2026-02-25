package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.weelo.logistics.ui.components.CardArtwork
import com.weelo.logistics.ui.components.CardMediaSpec
import com.weelo.logistics.ui.components.EmptyStateArtwork
import com.weelo.logistics.ui.components.HeroEntityCard
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.components.RetryErrorStatePanel
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
                RetryErrorStatePanel(
                    title = "Could not load vehicle",
                    message = errorMessage ?: "Failed to load vehicle details",
                    onRetry = { loadVehicle() },
                    illustrationRes = EmptyStateArtwork.VEHICLE_DETAILS_NOT_FOUND.drawableRes,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            vehicle != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    val currentVehicle = vehicle!!
                    val (statusText, statusColor) = when (currentVehicle.status) {
                        "available" -> "Available" to Success
                        "in_transit" -> "In Transit" to Primary
                        "maintenance" -> "Maintenance" to Warning
                        else -> "Inactive" to TextDisabled
                    }

                    HeroEntityCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = currentVehicle.vehicleNumber,
                        subtitle = "${currentVehicle.vehicleType.replaceFirstChar { it.uppercase() }} • ${currentVehicle.vehicleSubtype}",
                        mediaSpec = CardMediaSpec(artwork = CardArtwork.DETAIL_VEHICLE),
                        leadingAvatar = {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Primary.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = Primary
                                )
                            }
                        },
                        statusContent = {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = statusColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = statusText,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = statusColor
                                )
                            }
                        },
                        metaContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = SurfaceVariant
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Capacity",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = currentVehicle.capacity,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = SurfaceVariant
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Verified",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = if (currentVehicle.isVerified) "Yes" else "Pending",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (currentVehicle.isVerified) Success else Warning
                                        )
                                    }
                                }
                            }
                        }
                    )

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
                                .padding(20.dp),
                        ) {
                            Text(
                                "Vehicle Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            
                            DetailRow("Capacity", currentVehicle.capacity)
                            DetailRow("Model", currentVehicle.model ?: "Not specified")
                            DetailRow("Year", currentVehicle.year?.toString() ?: "Not specified")
                            
                            if (currentVehicle.rcNumber != null) {
                                DetailRow("RC Number", currentVehicle.rcNumber)
                            }
                            if (currentVehicle.insuranceNumber != null) {
                                DetailRow("Insurance", currentVehicle.insuranceNumber)
                            }
                            
                            DetailRow(
                                "Verified",
                                if (currentVehicle.isVerified) "Yes" else "Pending"
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(0.62f)
        )
    }
}
