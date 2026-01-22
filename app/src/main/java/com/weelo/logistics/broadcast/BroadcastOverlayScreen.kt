package com.weelo.logistics.broadcast

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.weelo.logistics.core.notification.BroadcastSoundService
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.model.RequestedVehicle
import com.weelo.logistics.ui.theme.*

/**
 * =============================================================================
 * BROADCAST OVERLAY SCREEN - Multi-Truck Professional Design
 * =============================================================================
 * 
 * Full-screen overlay that appears when a new broadcast arrives.
 * 
 * FEATURES:
 * - Shows ALL truck types in one card (Option 2 design)
 * - Each truck type has quantity selector [-] count [+]
 * - Each truck type has REJECT / ACCEPT buttons
 * - 60-second countdown timer
 * - Sound notification on new broadcast
 * - Route visualization with directions
 * 
 * FLOW:
 * 1. New broadcast arrives → Overlay appears with sound
 * 2. Transporter selects quantity for any truck type
 * 3. Click ACCEPT → Goes to hold confirmation (15 sec)
 * 4. Click REJECT → Skips that truck type
 * 5. Timer expires → Overlay dismissed
 * 
 * =============================================================================
 */
@Composable
fun BroadcastOverlayScreen(
    onAccept: (BroadcastTrip) -> Unit,
    onReject: (BroadcastTrip) -> Unit
) {
    val context = LocalContext.current
    val soundService = remember { BroadcastSoundService.getInstance(context) }
    
    val isVisible by BroadcastOverlayManager.isOverlayVisible.collectAsState()
    val currentBroadcast by BroadcastOverlayManager.currentBroadcast.collectAsState()
    val remainingSeconds by BroadcastOverlayManager.remainingTimeSeconds.collectAsState()
    val queueSize by BroadcastOverlayManager.queueSize.collectAsState()
    
    // Play sound when new broadcast appears
    LaunchedEffect(currentBroadcast) {
        currentBroadcast?.let { broadcast ->
            if (broadcast.isUrgent) {
                soundService.playUrgentSound()
            } else {
                soundService.playBroadcastSound()
            }
        }
    }
    
    // Animate visibility
    AnimatedVisibility(
        visible = isVisible && currentBroadcast != null,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        )
    ) {
        currentBroadcast?.let { broadcast ->
            Dialog(
                onDismissRequest = { 
                    BroadcastOverlayManager.dismissOverlay()
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                BroadcastOverlayContent(
                    broadcast = broadcast,
                    remainingSeconds = remainingSeconds,
                    queueSize = queueSize,
                    onAcceptTruck = { vehicleType, vehicleSubtype, quantity ->
                        BroadcastOverlayManager.acceptCurrentBroadcast()
                        // Pass the selection info via broadcast object
                        onAccept(broadcast)
                    },
                    onRejectTruck = { vehicleType, vehicleSubtype ->
                        // Just reject this truck type, keep overlay if more types exist
                        // For now, reject entire broadcast
                        BroadcastOverlayManager.rejectCurrentBroadcast()
                        onReject(broadcast)
                    },
                    onDismiss = {
                        BroadcastOverlayManager.dismissOverlay()
                    }
                )
            }
        }
    }
}

@Composable
private fun BroadcastOverlayContent(
    broadcast: BroadcastTrip,
    remainingSeconds: Int,
    queueSize: Int,
    onAcceptTruck: (vehicleType: String, vehicleSubtype: String, quantity: Int) -> Unit,
    onRejectTruck: (vehicleType: String, vehicleSubtype: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // Timer color (red when < 30 seconds)
    val timerColor = when {
        remainingSeconds < 15 -> Error
        remainingSeconds < 30 -> Warning
        else -> Success
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ============== HEADER ==============
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Primary,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .background(White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, "Close", tint = White)
                        }
                        
                        // Title
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "New Booking Request",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                            if (broadcast.isUrgent) {
                                Text(
                                    "🔥 URGENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Warning,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // Timer
                        Surface(
                            color = timerColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Timer,
                                    null,
                                    tint = White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    formatTime(remainingSeconds),
                                    color = White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                    
                    // Queue indicator
                    if (queueSize > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "+$queueSize more requests waiting",
                            color = Warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // ============== MAIN CONTENT (Scrollable) ==============
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Order Info Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Order #${broadcast.broadcastId.takeLast(8).uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, Modifier.size(14.dp), tint = TextSecondary)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    broadcast.customerName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        // Total Value
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Success.copy(alpha = 0.1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Total Value",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    "₹${String.format("%,.0f", broadcast.totalFare)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Success
                                )
                            }
                        }
                    }
                }
                
                // Route Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Pickup
                            Row(verticalAlignment = Alignment.Top) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Success, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(32.dp)
                                            .background(Divider)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("PICKUP", style = MaterialTheme.typography.labelSmall, color = Success, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        broadcast.pickupLocation.address,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            // Drop
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Error, CircleShape)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("DROP", style = MaterialTheme.typography.labelSmall, color = Error, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        broadcast.dropLocation.address,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            // Distance & Directions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Route, null, Modifier.size(18.dp), tint = TextSecondary)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${broadcast.distance.toInt()} km",
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                    Text(" • ~${broadcast.estimatedDuration} min", color = TextSecondary)
                                }
                                
                                TextButton(
                                    onClick = {
                                        val uri = Uri.parse(
                                            "https://www.google.com/maps/dir/?api=1" +
                                            "&origin=${Uri.encode(broadcast.pickupLocation.address)}" +
                                            "&destination=${Uri.encode(broadcast.dropLocation.address)}" +
                                            "&travelmode=driving"
                                        )
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                ) {
                                    Icon(Icons.Default.Directions, null, Modifier.size(18.dp), tint = Primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Directions", color = Primary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                
                // Goods Type
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Inventory2, null, Modifier.size(16.dp), tint = TextSecondary)
                        Spacer(Modifier.width(6.dp))
                        Text("Goods: ${broadcast.goodsType}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        broadcast.weight?.let {
                            Text(" • $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
                
                // Section Header
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "SELECT TRUCKS TO ACCEPT",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
                
                // ============== TRUCK TYPES (Multi-Truck UI) ==============
                if (broadcast.requestedVehicles.isNotEmpty()) {
                    items(broadcast.requestedVehicles.filter { it.remainingCount > 0 }) { vehicle ->
                        TruckTypeCard(
                            vehicle = vehicle,
                            onAccept = { quantity ->
                                onAcceptTruck(vehicle.vehicleType, vehicle.vehicleSubtype, quantity)
                            },
                            onReject = {
                                onRejectTruck(vehicle.vehicleType, vehicle.vehicleSubtype)
                            }
                        )
                    }
                } else {
                    // Legacy single truck type
                    item {
                        LegacySingleTruckCard(
                            broadcast = broadcast,
                            onAccept = { quantity ->
                                onAcceptTruck(
                                    broadcast.vehicleType?.id ?: "truck",
                                    broadcast.vehicleType?.name ?: "",
                                    quantity
                                )
                            },
                            onReject = {
                                onRejectTruck(
                                    broadcast.vehicleType?.id ?: "truck",
                                    broadcast.vehicleType?.name ?: ""
                                )
                            }
                        )
                    }
                }
                
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

/**
 * TRUCK TYPE CARD - Individual truck type with quantity selector
 */
@Composable
private fun TruckTypeCard(
    vehicle: RequestedVehicle,
    onAccept: (quantity: Int) -> Unit,
    onReject: () -> Unit
) {
    var selectedQuantity by remember { mutableStateOf(1) }
    
    LaunchedEffect(vehicle.remainingCount) {
        if (selectedQuantity > vehicle.remainingCount) {
            selectedQuantity = vehicle.remainingCount.coerceAtLeast(1)
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Truck type + Available
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocalShipping, null, tint = Primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            vehicle.vehicleType.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (vehicle.vehicleSubtype.isNotBlank()) {
                            Text(
                                vehicle.vehicleSubtype,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Success.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "${vehicle.remainingCount} available",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Success
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "₹${String.format("%,.0f", vehicle.farePerTruck)}/truck",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Quantity Selector + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantity Selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Surface, RoundedCornerShape(8.dp))
                        .border(1.dp, Divider, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { if (selectedQuantity > 1) selectedQuantity-- },
                        modifier = Modifier.size(36.dp),
                        enabled = selectedQuantity > 1
                    ) {
                        Icon(Icons.Default.Remove, "Decrease", tint = if (selectedQuantity > 1) Primary else TextDisabled)
                    }
                    
                    Text(
                        selectedQuantity.toString(),
                        modifier = Modifier.widthIn(min = 40.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    IconButton(
                        onClick = { if (selectedQuantity < vehicle.remainingCount) selectedQuantity++ },
                        modifier = Modifier.size(36.dp),
                        enabled = selectedQuantity < vehicle.remainingCount
                    ) {
                        Icon(Icons.Default.Add, "Increase", tint = if (selectedQuantity < vehicle.remainingCount) Primary else TextDisabled)
                    }
                }
                
                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Error.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("REJECT", fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { onAccept(selectedQuantity) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Success),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("ACCEPT $selectedQuantity", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            // Total if multiple
            if (selectedQuantity > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Total: ₹${String.format("%,.0f", vehicle.farePerTruck * selectedQuantity)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Success,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * Legacy Single Truck Card (for backward compatibility)
 */
@Composable
private fun LegacySingleTruckCard(
    broadcast: BroadcastTrip,
    onAccept: (quantity: Int) -> Unit,
    onReject: () -> Unit
) {
    var selectedQuantity by remember { mutableStateOf(1) }
    val available = broadcast.totalRemainingTrucks
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocalShipping, null, tint = Primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            broadcast.vehicleTypesDisplay.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$available trucks needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "₹${String.format("%,.0f", broadcast.farePerTruck)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Success
                    )
                    Text("/truck", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Surface, RoundedCornerShape(8.dp))
                        .border(1.dp, Divider, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { if (selectedQuantity > 1) selectedQuantity-- },
                        modifier = Modifier.size(36.dp),
                        enabled = selectedQuantity > 1
                    ) {
                        Icon(Icons.Default.Remove, "Decrease")
                    }
                    Text(
                        selectedQuantity.toString(),
                        modifier = Modifier.widthIn(min = 40.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = { if (selectedQuantity < available) selectedQuantity++ },
                        modifier = Modifier.size(36.dp),
                        enabled = selectedQuantity < available
                    ) {
                        Icon(Icons.Default.Add, "Increase")
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                    ) {
                        Text("REJECT", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onAccept(selectedQuantity) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Success)
                    ) {
                        Text("ACCEPT $selectedQuantity", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Format seconds to M:SS
 */
private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
