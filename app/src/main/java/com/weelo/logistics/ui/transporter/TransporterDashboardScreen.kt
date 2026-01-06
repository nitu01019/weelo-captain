package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.TripStatus
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Transporter Dashboard Screen - Backend Ready
 * 
 * CURRENT STATE: Shows zero/empty data until backend is connected
 * 
 * TODO: Connect to backend
 * - Uncomment repository call
 * - Fetch real dashboard data
 * - Handle loading and error states
 */
@Composable
fun TransporterDashboardScreen(
    onNavigateToFleet: () -> Unit = {},
    onNavigateToDrivers: () -> Unit = {},
    onNavigateToTrips: () -> Unit = {},
    onNavigateToAddVehicle: () -> Unit = {},
    onNavigateToAddDriver: () -> Unit = {},
    onNavigateToCreateTrip: () -> Unit = {}
) {
    // Empty dashboard - no fake data
    var dashboardData by remember { 
        mutableStateOf<com.weelo.logistics.data.model.TransporterDashboard?>(null) 
    }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // TODO: Uncomment when backend is ready
    // LaunchedEffect(Unit) {
    //     isLoading = true
    //     val repository = TransporterRepository()
    //     val result = repository.getTransporterDashboard(transporterId)
    //     result.onSuccess { data ->
    //         dashboardData = data
    //         isLoading = false
    //     }.onFailure { error ->
    //         errorMessage = error.message
    //         isLoading = false
    //     }
    // }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // Top Bar with Role Switcher placeholder
        SimpleTopBar(
            title = "Dashboard",
            actions = {
                IconButton(onClick = { /* TODO: Navigate to notifications */ }) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = TextPrimary
                    )
                }
            }
        )
        
        // Show backend not connected message or empty dashboard
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // Statistics Cards
                item {
                    Text(
                        text = "Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.width(150.dp),
                                onClick = onNavigateToFleet,
                                elevation = CardDefaults.cardElevation(Elevation.low),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(BorderRadius.medium)
                            ) {
                                InfoCard(
                                    icon = Icons.Default.DirectionsCar,
                                    title = "Total Vehicles",
                                    value = "0",
                                    modifier = Modifier.width(150.dp)
                                )
                            }
                        }
                        item {
                            InfoCard(
                                icon = Icons.Default.Person,
                                title = "Active Drivers",
                                value = "0",
                                modifier = Modifier.width(150.dp),
                                iconTint = Secondary
                            )
                        }
                        item {
                            InfoCard(
                                icon = Icons.Default.LocalShipping,
                                title = "Active Trips",
                                value = "0",
                                modifier = Modifier.width(150.dp),
                                iconTint = Info
                            )
                        }
                        item {
                            InfoCard(
                                icon = Icons.Default.AccountBalance,
                                title = "Today's Revenue",
                                value = "₹0",
                                modifier = Modifier.width(150.dp),
                                iconTint = Success
                            )
                        }
                    }
                }
                
                // Quick Actions
                item {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard(
                            icon = Icons.Default.AddCircle,
                            title = "Add Vehicle",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToAddVehicle
                        )
                        QuickActionCard(
                            icon = Icons.Default.PersonAdd,
                            title = "Add Driver",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToAddDriver
                        )
                    }
                }
                
                // Recent Trips
                item {
                    Text(
                        text = "Recent Trips",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                // Always show empty state until backend is connected
                item {
                    EmptyStateCard(
                        icon = Icons.Default.CloudOff,
                        message = "Backend not connected. Connect backend to see real data."
                    )
                }
        }
    }
}

@Composable
fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(BorderRadius.medium),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun TripListItem(trip: com.weelo.logistics.data.model.Trip) {
    ListItemCard(
        title = trip.customerName,
        subtitle = "${trip.pickupLocation.address} → ${trip.dropLocation.address}",
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.LocalShipping,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(40.dp)
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(
                    text = when (trip.status) {
                        TripStatus.PENDING -> "Pending"
                        TripStatus.ASSIGNED -> "Assigned"
                        TripStatus.ACCEPTED -> "Accepted"
                        TripStatus.IN_PROGRESS -> "In Progress"
                        TripStatus.COMPLETED -> "Completed"
                        TripStatus.REJECTED -> "Rejected"
                        TripStatus.CANCELLED -> "Cancelled"
                    },
                    status = when (trip.status) {
                        TripStatus.PENDING -> ChipStatus.PENDING
                        TripStatus.IN_PROGRESS -> ChipStatus.IN_PROGRESS
                        TripStatus.COMPLETED -> ChipStatus.COMPLETED
                        TripStatus.CANCELLED -> ChipStatus.CANCELLED
                        else -> ChipStatus.AVAILABLE
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₹${String.format("%.0f", trip.fare)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        },
        onClick = { /* TODO: Navigate to trip details */ }
    )
}

@Composable
fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(BorderRadius.medium),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}
