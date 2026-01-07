package com.weelo.logistics.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.Trip
import com.weelo.logistics.data.model.TripStatus
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Driver Trip Navigation Screen - PRD-04 Compliant
 * Live trip tracking with map and navigation
 */
@Composable
fun DriverTripNavigationScreen(
    tripId: String,
    onNavigateBack: () -> Unit,
    onTripCompleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    // TODO: Connect to real repository from backend
    var trip by remember { mutableStateOf<Trip?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(tripId) {
        scope.launch {
            repository.getTrips("t1").onSuccess { trips ->
                trip = trips.find { it.id == tripId }
                isLoading = false
            }
        }
    }
    
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Map Placeholder
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Surface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Map,
                        null,
                        Modifier.size(64.dp),
                        tint = TextDisabled
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Map View",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Google Maps integration ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            // Trip Info Card
            trip?.let { t ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        // Customer Info
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.customerName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    t.customerMobile,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { /* TODO: Call customer */ },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Success, androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(Icons.Default.Call, null, tint = White)
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Divider()
                        Spacer(Modifier.height(16.dp))
                        
                        // Locations
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = Success
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Pickup",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary
                                    )
                                    Text(
                                        t.pickupLocation.address,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = Error
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Drop",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary
                                    )
                                    Text(
                                        t.dropLocation.address,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Trip Stats
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBox("Distance", "${t.distance} km", Icons.Default.Timeline)
                            StatBox("Goods", t.goodsType, Icons.Default.Category)
                            StatBox("Fare", "₹${String.format("%.0f", t.fare)}", Icons.Default.CurrencyRupee)
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Action Buttons
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SecondaryButton(
                                text = "Report Issue",
                                onClick = { /* TODO */ },
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryButton(
                                text = if (t.status == TripStatus.IN_PROGRESS) "Complete Trip" else "Start Trip",
                                onClick = {
                                    if (t.status == TripStatus.IN_PROGRESS) {
                                        showCompleteDialog = true
                                    } else {
                                        scope.launch {
                                            repository.updateTripStatus(tripId, TripStatus.IN_PROGRESS)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        
        // Back Button Overlay
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
                .background(White, androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, "Back")
        }
    }
    
    // Complete Trip Dialog
    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = Success) },
            title = { Text("Complete Trip?") },
            text = { Text("Mark this trip as completed? Customer will be notified.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.updateTripStatus(tripId, TripStatus.COMPLETED)
                            showCompleteDialog = false
                            onTripCompleted()
                        }
                    }
                ) {
                    Text("Complete", color = Success)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatBox(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(20.dp), tint = Primary)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
