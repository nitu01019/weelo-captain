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
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DriverDetailsScreen - Shows driver details from backend database
 * 
 * Fetches real data from: GET /api/v1/driver/list (filters by driverId)
 * Performance/Earnings show 0 - will be connected to backend later
 */
@Composable
fun DriverDetailsScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPerformance: (String) -> Unit = {},
    onNavigateToEarnings: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var driver by remember { mutableStateOf<DriverData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Fetch driver from backend
    LaunchedEffect(driverId) {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.driverApi.getDriverList()
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val drivers = response.body()?.data?.drivers ?: emptyList()
                    driver = drivers.find { it.id == driverId }
                    if (driver == null) {
                        errorMessage = "Driver not found"
                    }
                } else {
                    errorMessage = response.body()?.error?.message ?: "Failed to load driver"
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Driver Details", onBackClick = onNavigateBack)
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.Error, null, Modifier.size(64.dp), tint = TextDisabled)
                    Spacer(Modifier.height(16.dp))
                    Text(errorMessage ?: "Error", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
            }
        } else driver?.let { d ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Driver Profile Card
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(SecondaryLight)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // Avatar with initial
                        Box(
                            Modifier.size(80.dp).background(Primary.copy(alpha = 0.1f), CircleShape),
                            Alignment.Center
                        ) {
                            val initial = (d.name?.firstOrNull() ?: d.phone.lastOrNull() ?: 'D').uppercase()
                            Text(
                                text = initial.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = d.name ?: "Driver",
                            style = MaterialTheme.typography.headlineMedium, 
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "+91 ${d.phone}",
                            style = MaterialTheme.typography.bodyLarge, 
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        // Status chip
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
                    }
                }
                
                // Contact Information - Real data
                SectionCard("Contact Information") {
                    DetailRow("Phone", "+91 ${d.phone}")
                    if (d.licenseNumber != null) {
                        Divider()
                        DetailRow("License", d.licenseNumber!!)
                    }
                    if (d.email != null) {
                        Divider()
                        DetailRow("Email", d.email!!)
                    }
                }
                
                // Performance - Shows real data or 0
                SectionCard("Performance") {
                    DetailRow("Rating", "⭐ ${String.format("%.1f", d.rating ?: 0f)}")
                    Divider()
                    DetailRow("Total Trips", "${d.totalTrips}")
                    Divider()
                    DetailRow("Completed Trips", "${d.totalTrips}") // Same as total for now
                    Divider()
                    DetailRow("On-Time Rate", "0%") // Will connect to backend later
                }
                
                // Earnings Summary - Shows 0 for now
                SectionCard("Earnings Summary") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("This Month", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text("₹0", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Success)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Last Month", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text("₹0", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    DetailRow("Pending Payment", "₹0")
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
