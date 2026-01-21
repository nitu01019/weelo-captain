package com.weelo.logistics.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.offline.AvailabilityManager
import com.weelo.logistics.offline.NetworkMonitor
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

/**
 * =============================================================================
 * AVAILABILITY TOGGLE - Online/Offline Switch for Transporter Dashboard
 * =============================================================================
 * 
 * FEATURES:
 * - Large, prominent toggle button
 * - Shows online (green) / offline (red) status
 * - Shows network connectivity status
 * - Syncing indicator when updating backend
 * - Works offline - syncs when back online
 * 
 * USAGE:
 * AvailabilityToggle(
 *     modifier = Modifier.fillMaxWidth()
 * )
 * 
 * =============================================================================
 */

@Composable
fun AvailabilityToggle(
    modifier: Modifier = Modifier,
    onStatusChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val availabilityManager = remember { AvailabilityManager.getInstance(context) }
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    
    val isAvailable by availabilityManager.isAvailable.collectAsState()
    val isSyncing by availabilityManager.isSyncing.collectAsState()
    val isOnline by networkMonitor.isOnline.collectAsState()
    
    // Colors
    val backgroundColor by animateColorAsState(
        targetValue = if (isAvailable) Color(0xFF4CAF50) else Color(0xFFE53935),
        animationSpec = tween(300),
        label = "bgColor"
    )
    
    val toggleScale by animateFloatAsState(
        targetValue = if (isSyncing) 0.95f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )
    
    Card(
        modifier = modifier
            .scale(toggleScale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isSyncing) {
                scope.launch {
                    availabilityManager.toggleAvailability()
                    onStatusChanged?.invoke(!isAvailable)
                }
            },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon & Text
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status indicator dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.White, CircleShape)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = if (isAvailable) "You're Online" else "You're Offline",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (isAvailable) 
                            "Receiving booking requests" 
                        else 
                            "Not receiving requests",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
            
            // Toggle Switch
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(32.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = if (isAvailable) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = backgroundColor,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
    
    // Network status indicator (if offline)
    if (!isOnline) {
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "No internet connection",
                color = Color(0xFFFFA726),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Big Rapido-style toggle for app bar - Clean switch without text
 */
@Composable
fun AvailabilityToggleCompact(
    modifier: Modifier = Modifier,
    onStatusChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val availabilityManager = remember { AvailabilityManager.getInstance(context) }
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    
    val isAvailable by availabilityManager.isAvailable.collectAsState()
    val isSyncing by availabilityManager.isSyncing.collectAsState()
    val isOnline by networkMonitor.isOnline.collectAsState()
    
    // Track colors - Green when online, Red when offline
    val trackColor by animateColorAsState(
        targetValue = if (isAvailable) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
        animationSpec = tween(250),
        label = "trackColor"
    )
    
    // Thumb position animation
    val thumbOffset by animateFloatAsState(
        targetValue = if (isAvailable) 1f else 0f,
        animationSpec = tween(250),
        label = "thumbOffset"
    )
    
    // Thumb color - White normally, slightly gray when syncing
    val thumbColor by animateColorAsState(
        targetValue = if (isSyncing) Color(0xFFF5F5F5) else Color.White,
        animationSpec = tween(150),
        label = "thumbColor"
    )
    
    Box(
        modifier = modifier
            .width(56.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .clickable(enabled = !isSyncing) {
                scope.launch {
                    availabilityManager.toggleAvailability()
                    onStatusChanged?.invoke(!isAvailable)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Thumb (the circular button that moves)
        Box(
            modifier = Modifier
                .padding(3.dp)
                .offset(x = (thumbOffset * 24).dp)
                .size(26.dp)
                .background(thumbColor, CircleShape)
                .then(
                    if (isAvailable) {
                        Modifier.background(
                            color = Color.White,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isSyncing -> {
                    // Show spinner while syncing
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = trackColor,
                        strokeWidth = 2.dp
                    )
                }
                !isOnline -> {
                    // Show cloud-off icon when no network
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "No network",
                        tint = Color(0xFFFFA726),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        // Glow effect when online (optional subtle indicator)
        if (isAvailable && !isSyncing) {
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .offset(x = 24.dp)
                    .size(26.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Offline banner shown at top of screen
 */
@Composable
fun OfflineBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    val isOnline by networkMonitor.isOnline.collectAsState()
    
    if (!isOnline) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = Color(0xFFFFA726)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "You're offline. Some features may not work.",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}
