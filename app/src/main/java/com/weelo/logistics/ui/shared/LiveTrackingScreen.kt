package com.weelo.logistics.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * LIVE TRACKING SCREEN
 * ====================
 * Real-time GPS tracking of driver's location during trip.
 * Shows map with driver's current position, route, and trip details.
 * 
 * FLOW:
 * 1. Driver accepts trip → Location tracking begins
 * 2. Driver's GPS coordinates sent to backend every few seconds
 * 3. This screen shows driver's real-time location on map
 * 4. Updates speed, heading, ETA automatically
 * 5. Shows trip progress (pickup → in transit → delivered)
 * 
 * FOR BACKEND DEVELOPER:
 * - Continuously send driver's GPS location via API
 * - Update LiveTripTracking model every 5-10 seconds
 * - Calculate ETA based on current location and traffic
 * - Use Google Maps SDK or Mapbox for map display
 * - Show route from current location to destination
 * - Handle location permissions (request if not granted)
 * - Stop tracking when trip is completed
 * 
 * IMPORTANT: 
 * - Map integration requires Google Maps API key
 * - This is a placeholder UI - replace Box with actual Map composable
 * - Use: implementation 'com.google.maps.android:maps-compose:2.11.4'
 */
@Composable
fun LiveTrackingScreen(
    tripId: String,
    driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    
    var trackingData by remember { mutableStateOf<LiveTripTracking?>(null) }
    var tripDetails by remember { mutableStateOf<Trip?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showTripDetails by remember { mutableStateOf(false) }
    
    // BACKEND: Fetch initial trip and tracking data
    LaunchedEffect(tripId) {
        scope.launch {
            // Mock - Replace with: repository.getTripDetails(tripId)
            tripDetails = repository.getMockTripById(tripId)
            isLoading = false
        }
    }
    
    // BACKEND: Real-time location updates (every 5 seconds)
    LaunchedEffect(driverId) {
        while (true) {
            scope.launch {
                // Mock - Replace with: repository.getDriverLiveLocation(driverId)
                trackingData = repository.getMockLiveTracking(driverId)
            }
            delay(5000) // Update every 5 seconds
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Map Placeholder
                // BACKEND: Replace this Box with actual Map composable
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Map,
                            null,
                            modifier = Modifier.size(80.dp),
                            tint = Primary.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "MAP VIEW",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Integrate Google Maps here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextDisabled
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Show driver's real-time location\nwith route from pickup to drop",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDisabled
                        )
                    }
                    
                    // Location indicator (for demo)
                    trackingData?.let { tracking ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Success,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    null,
                                    tint = White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "LIVE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                            }
                        }
                    }
                }
                
                // Bottom Sheet with Trip Info
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TextDisabled)
                                .align(Alignment.CenterHorizontally)
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Trip Status
                        tripDetails?.let { trip ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        when (trip.status) {
                                            TripStatus.IN_PROGRESS -> Icons.Default.LocalShipping
                                            TripStatus.COMPLETED -> Icons.Default.CheckCircle
                                            else -> Icons.Default.Schedule
                                        },
                                        null,
                                        tint = Primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                
                                Spacer(Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        when (trip.status) {
                                            TripStatus.IN_PROGRESS -> "Trip In Progress"
                                            TripStatus.COMPLETED -> "Trip Completed"
                                            else -> "Trip Pending"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    trackingData?.let {
                                        Text(
                                            "${it.currentSpeed.toInt()} km/h • Last updated just now",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                
                                IconButton(onClick = { showTripDetails = !showTripDetails }) {
                                    Icon(
                                        if (showTripDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        null,
                                        tint = Primary
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Route Info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "FROM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    Text(
                                        trip.pickupLocation.city ?: "Pickup",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Icon(
                                    Icons.Default.ArrowForward,
                                    null,
                                    modifier = Modifier.padding(top = 12.dp),
                                    tint = Primary
                                )
                                
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        "TO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    Text(
                                        trip.dropLocation.city ?: "Drop",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            // Expanded Details
                            if (showTripDetails) {
                                Spacer(Modifier.height(16.dp))
                                Divider()
                                Spacer(Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TrackingInfoItem(
                                        icon = Icons.Default.Route,
                                        label = "Distance",
                                        value = "${trip.distance} km"
                                    )
                                    TrackingInfoItem(
                                        icon = Icons.Default.Schedule,
                                        label = "ETA",
                                        value = "${trip.estimatedDuration} min"
                                    )
                                    TrackingInfoItem(
                                        icon = Icons.Default.AttachMoney,
                                        label = "Fare",
                                        value = "₹${String.format("%.0f", trip.fare)}"
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ArrowBack, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Back")
                                }
                                
                                if (trip.status == TripStatus.IN_PROGRESS) {
                                    Button(
                                        onClick = onNavigateToComplete,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Success)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Complete Trip")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tracking Info Item - Small metric display
 */
@Composable
fun TrackingInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = Primary)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
