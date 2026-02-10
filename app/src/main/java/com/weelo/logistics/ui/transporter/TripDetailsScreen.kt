package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.weelo.logistics.ui.components.responsiveHorizontalPadding
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TripDetailsScreen(
    tripId: String,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    var trip by remember { mutableStateOf<Trip?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    LaunchedEffect(tripId) {
        scope.launch {
            repository.getTrips("t1").onSuccess { trips ->
                trip = trips.find { it.id == tripId }
                isLoading = false
            }
        }
    }
    
    // Responsive layout
    val horizontalPadding = responsiveHorizontalPadding()
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Trip Details", onBackClick = onNavigateBack)
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else trip?.let { t ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(if (t.status == TripStatus.IN_PROGRESS) PrimaryLight else White)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t.customerName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            StatusChip(
                                text = when (t.status) {
                                    TripStatus.PENDING -> "Pending"
                                    TripStatus.ASSIGNED -> "Assigned"
                                    TripStatus.IN_PROGRESS -> "In Progress"
                                    TripStatus.COMPLETED -> "Completed"
                                    else -> "Cancelled"
                                },
                                status = when (t.status) {
                                    TripStatus.PENDING -> ChipStatus.PENDING
                                    TripStatus.IN_PROGRESS -> ChipStatus.IN_PROGRESS
                                    TripStatus.COMPLETED -> ChipStatus.COMPLETED
                                    else -> ChipStatus.CANCELLED
                                }
                            )
                        }
                    }
                }
                
                SectionCard("Location Details") {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.LocationOn, null, tint = Success, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Pickup", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(t.pickupLocation.address, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.LocationOn, null, tint = Error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Drop", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(t.dropLocation.address, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                
                SectionCard("Trip Information") {
                    DetailRow("Customer Mobile", t.customerMobile)
                    Divider()
                    DetailRow("Goods Type", t.goodsType)
                    t.weight?.let {
                        Divider()
                        DetailRow("Weight", it)
                    }
                    Divider()
                    DetailRow("Distance", "${t.distance} km")
                    Divider()
                    DetailRow("Fare", "₹${String.format("%.0f", t.fare)}")
                }
                
                if (t.status == TripStatus.IN_PROGRESS) {
                    PrimaryButton("Track Live Location", onClick = { /* TODO */ })
                }
                
                SecondaryButton("Contact Driver", onClick = { /* TODO */ })
            }
        }
    }
}
