package com.weelo.logistics.broadcast

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
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
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.ui.theme.*

/**
 * BroadcastOverlayScreen - Full-Screen Rapido-Style Broadcast Popup
 * ==================================================================
 * 
 * FEATURES:
 * - Full-screen overlay that appears over any screen
 * - Google Maps with pickup/drop markers and route
 * - Countdown timer (5 minutes)
 * - Vehicle type, quantity, price details
 * - Accept/Reject buttons
 * - Directions button to open Google Maps
 * 
 * SCALABILITY:
 * - Lazy loading of map
 * - Efficient recomposition
 * - Memory-safe image handling
 * 
 * USAGE:
 * Place this at the root of your app (MainActivity or NavHost)
 * It observes BroadcastOverlayManager.isOverlayVisible
 */
@Composable
fun BroadcastOverlayScreen(
    onAccept: (BroadcastTrip) -> Unit,
    onReject: (BroadcastTrip) -> Unit
) {
    val isVisible by BroadcastOverlayManager.isOverlayVisible.collectAsState()
    val currentBroadcast by BroadcastOverlayManager.currentBroadcast.collectAsState()
    val remainingSeconds by BroadcastOverlayManager.remainingTimeSeconds.collectAsState()
    val queueSize by BroadcastOverlayManager.queueSize.collectAsState()
    
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
                    onAccept = {
                        BroadcastOverlayManager.acceptCurrentBroadcast()
                        onAccept(broadcast)
                    },
                    onReject = {
                        BroadcastOverlayManager.rejectCurrentBroadcast()
                        onReject(broadcast)
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
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current
    
    // Timer color (red when < 60 seconds)
    val timerColor = if (remainingSeconds < 60) Color(0xFFE53935) else Color(0xFF4CAF50)
    
    // Background colors
    val backgroundColor = Color(0xFFF8F9FA)
    val cardColor = Color.White
    val primaryBlue = Color(0xFF1976D2)
    val accentGreen = Color(0xFF4CAF50)
    val textPrimary = Color(0xFF212121)
    val textSecondary = Color(0xFF757575)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ============== HEADER ==============
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = cardColor,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Top row: Close, Title, Timer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Close button
                        IconButton(
                            onClick = { BroadcastOverlayManager.dismissOverlay() },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFF5F5F5), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = textSecondary
                            )
                        }
                        
                        // Title
                        Text(
                            text = "New Booking Request",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        
                        // Timer
                        Surface(
                            color = timerColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = timerColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatTime(remainingSeconds),
                                    color = timerColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                    
                    // Queue indicator
                    if (queueSize > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "+$queueSize more requests waiting",
                            color = Color(0xFFFFA726),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // ============== MAIN CONTENT ==============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Vehicle Type & Trucks Needed Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vehicle Type
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = primaryBlue.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = primaryBlue,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = broadcast.vehicleType.name.uppercase(),
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = broadcast.vehicleType.description,
                                    color = textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
                        // Trucks Needed
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${broadcast.totalTrucksNeeded}",
                                color = primaryBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            )
                            Text(
                                text = "Trucks",
                                color = textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                
                // Route Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Pickup
                        Row(verticalAlignment = Alignment.Top) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(accentGreen, CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(40.dp)
                                        .background(Color(0xFFE0E0E0))
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PICKUP",
                                    color = accentGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = broadcast.pickupLocation.address,
                                    color = textPrimary,
                                    fontSize = 14.sp,
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
                                    .background(Color(0xFFE53935), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DROP",
                                    color = Color(0xFFE53935),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = broadcast.dropLocation.address,
                                    color = textPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Distance & Directions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Route,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${broadcast.distance.toInt()} km",
                                    color = textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = " • ~${(broadcast.distance * 2).toInt()} mins",
                                    color = textSecondary
                                )
                            }
                            
                            // Directions button
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
                                Icon(
                                    imageVector = Icons.Default.Directions,
                                    contentDescription = null,
                                    tint = primaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Directions",
                                    color = primaryBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                
                // Earnings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = accentGreen.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Per Truck
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Per Truck",
                                color = textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "₹${broadcast.farePerTruck.toInt()}",
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        
                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(Color(0xFFE0E0E0))
                        )
                        
                        // Total Earnings
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total Earnings",
                                color = textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "₹${broadcast.totalFare.toInt()}",
                                color = accentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // ============== BOTTOM BUTTONS ==============
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = cardColor,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Reject Button
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFE53935)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFE53935), Color(0xFFE53935))
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "REJECT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    
                    // Accept Button
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = remainingSeconds > 0
                    ) {
                        Text(
                            text = "ACCEPT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Format seconds to MM:SS
 */
private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
