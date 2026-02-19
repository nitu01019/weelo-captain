package com.weelo.logistics.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.weelo.logistics.R
import com.weelo.logistics.ui.theme.*
import kotlin.math.roundToInt

/**
 * Animated Counter - Smooth number animation for earnings display
 * 
 * @param targetValue The value to animate to
 * @param prefix Optional prefix (e.g., "₹")
 * @param suffix Optional suffix (e.g., "km")
 * @param decimals Number of decimal places
 * @param style Text style
 */
@Composable
fun AnimatedCounter(
    targetValue: Double,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    decimals: Int = 0,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium
) {
    var animatedValue by remember { mutableStateOf(0.0) }
    
    LaunchedEffect(targetValue) {
        val animationSpec = tween<Float>(
            durationMillis = 1000,
            easing = FastOutSlowInEasing
        )
        
        animate(
            initialValue = animatedValue.toFloat(),
            targetValue = targetValue.toFloat(),
            animationSpec = animationSpec
        ) { value, _ ->
            animatedValue = value.toDouble()
        }
    }
    
    val displayValue = if (decimals == 0) {
        animatedValue.roundToInt().toString()
    } else {
        String.format("%.${decimals}f", animatedValue)
    }
    
    Text(
        text = "$prefix$displayValue$suffix",
        style = style,
        modifier = modifier
    )
}

/**
 * Performance Indicator - Circular progress with percentage
 * 
 * @param percentage Value from 0 to 100
 * @param label Label text
 * @param color Indicator color
 */
@Composable
fun PerformanceIndicator(
    percentage: Double,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Primary
) {
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage.toFloat(),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "percentage"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp)
        ) {
            CircularProgressIndicator(
                progress = animatedPercentage / 100f,
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = 6.dp,
                trackColor = color.copy(alpha = 0.1f)
            )
            
            Text(
                text = "${animatedPercentage.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Stat Card - Compact stat display with icon
 * 
 * @param icon Icon to display
 * @param value Main value to show
 * @param label Description label
 * @param iconColor Icon background color
 */
@Composable
fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    iconColor: Color = Primary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Map Preview Card - Placeholder for Google Maps integration
 * 
 * BACKEND/MAPS TODO:
 * 1. Add Google Maps dependency to build.gradle
 * 2. Get Google Maps API key
 * 3. Replace this with actual MapView composable
 * 4. Show route from pickup to drop with live location
 */
@Composable
fun MapPreviewCard(
    pickupAddress: String,
    dropAddress: String,
    modifier: Modifier = Modifier,
    onOpenFullMap: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onOpenFullMap
    ) {
        Column {
            // Map placeholder - Replace with actual Google MapView
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(SecondaryLight),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = stringResource(R.string.cd_map),
                        modifier = Modifier.size(48.dp),
                        tint = Secondary
                    )
                    Text(
                        text = stringResource(R.string.map_view),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = stringResource(R.string.tap_to_open_map),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                // TODO: Replace above with:
                // AndroidView(factory = { context ->
                //     MapView(context).apply {
                //         // Setup map with route
                //     }
                // })
            }
            
            // Address info
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MapAddressRow(
                    icon = Icons.Default.LocationOn,
                    label = stringResource(R.string.pickup_label),
                    address = pickupAddress,
                    color = Success
                )
                
                Divider()
                
                MapAddressRow(
                    icon = Icons.Default.Place,
                    label = stringResource(R.string.drop_label),
                    address = dropAddress,
                    color = Error
                )
            }
        }
    }
}

@Composable
private fun MapAddressRow(
    icon: ImageVector,
    label: String,
    address: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Online Status Toggle - Driver availability switch
 * 
 * SPAM PROTECTION:
 *   - `isToggling` disables switch during 2s UI cooldown (ViewModel controls)
 *   - Backend enforces 5s cooldown + 10/5min rate limit + distributed lock
 *   - Switch is visually dimmed + shows "Switching..." during cooldown
 *   - Prevents visual spam even if backend handles it
 * 
 * @param isOnline Current online status
 * @param onToggle Callback when status is toggled
 * @param isToggling Whether a toggle is currently in progress (2s cooldown)
 */
@Composable
fun OnlineStatusToggle(
    isOnline: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isToggling: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) Success.copy(alpha = 0.1f) else Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isToggling) Warning
                            else if (isOnline) Success 
                            else TextDisabled
                        )
                )
                
                Column {
                    Text(
                        text = when {
                            isToggling -> stringResource(R.string.switching_status)
                            isOnline -> stringResource(R.string.you_are_online)
                            else -> stringResource(R.string.you_are_offline)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when {
                            isToggling -> stringResource(R.string.please_wait)
                            isOnline -> stringResource(R.string.accepting_trip_requests)
                            else -> stringResource(R.string.not_accepting_trips)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            Switch(
                checked = isOnline,
                onCheckedChange = { if (!isToggling) onToggle() },
                enabled = !isToggling,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Success,
                    checkedTrackColor = Success.copy(alpha = 0.5f)
                )
            )
        }
    }
}

/**
 * Notification Badge - Shows unread count
 * 
 * @param count Number of unread notifications
 */
@Composable
fun NotificationBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count > 0) {
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Error),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 9) "9+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Shimmer Loading Effect - For loading states
 */
@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Surface.copy(alpha = alpha)
        )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            // Content placeholder
        }
    }
}

/**
 * Empty State - When no data is available
 * 
 * @param icon Icon to display
 * @param title Title text
 * @param message Description message
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = TextDisabled
        )
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
