package com.weelo.logistics.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.weelo.logistics.ui.theme.*

// =============================================================================
// WEELO CAPTAIN - OFFLINE HANDLING COMPONENTS
// =============================================================================
// Premium offline banners and indicators
// Smooth animations for network state changes
// =============================================================================

/**
 * Offline Banner - Shows when device is offline
 * Animated entrance/exit with premium styling
 * Usage: Place at top of screen content
 */
@Composable
fun OfflineBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier,
    onRetryClick: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = isOffline,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Secondary,
            shadowElevation = Elevation.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.small))
                    Text(
                        text = "You're offline",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = White
                    )
                }
                
                if (onRetryClick != null) {
                    TextButton(
                        onClick = onRetryClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Primary
                        )
                    ) {
                        Text(
                            text = "Retry",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Offline Indicator - Small dot/chip indicator
 * For inline status display
 */
@Composable
fun OfflineIndicator(
    isOffline: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOffline,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(BorderRadius.pill),
            color = Error.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing dot
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_alpha"
                )
                
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Error.copy(alpha = alpha))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Error
                )
            }
        }
    }
}

/**
 * Network Status Banner - Shows current network status
 * Premium styled with smooth transitions
 */
@Composable
fun NetworkStatusBanner(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    showWhenOnline: Boolean = false
) {
    val showBanner = !isOnline || showWhenOnline
    
    AnimatedVisibility(
        visible = showBanner,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val backgroundColor = if (isOnline) Success else Warning
        val icon = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff
        val message = if (isOnline) "Back online" else "No internet connection"
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = backgroundColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isOnline) White else OnPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.small))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isOnline) White else OnPrimary
                )
            }
        }
    }
}

/**
 * Syncing Indicator - Shows when data is being synced
 * With animated sync icon
 */
@Composable
fun SyncingIndicator(
    isSyncing: Boolean,
    modifier: Modifier = Modifier,
    message: String = "Syncing..."
) {
    AnimatedVisibility(
        visible = isSyncing,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PrimaryLight
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rotating sync icon
                val infiniteTransition = rememberInfiniteTransition(label = "sync")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "sync_rotation"
                )
                
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
                Spacer(Modifier.width(Spacing.small))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * Connection Lost Dialog - Full screen dialog when connection is lost
 * For critical operations that need network
 */
@Composable
fun ConnectionLostDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Warning,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Connection Lost",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Please check your internet connection and try again.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary
                    )
                ) {
                    Text("Retry", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = TextSecondary)
                }
            },
            containerColor = Surface,
            shape = RoundedCornerShape(BorderRadius.large)
        )
    }
}

/**
 * Offline Mode Card - Information card about offline mode
 * Shows what features are available offline
 */
@Composable
fun OfflineModeCard(
    modifier: Modifier = Modifier,
    onGoOnlineClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = WarningLight),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.none)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPaddingLarge)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Warning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(Spacing.small))
                Text(
                    text = "Offline Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            
            Spacer(Modifier.height(Spacing.small))
            
            Text(
                text = "You can still view cached data. Some features may be limited until you're back online.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            
            if (onGoOnlineClick != null) {
                Spacer(Modifier.height(Spacing.medium))
                
                OutlinedButton(
                    onClick = onGoOnlineClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Warning
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Warning),
                    shape = RoundedCornerShape(BorderRadius.medium)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = "Check Connection",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
