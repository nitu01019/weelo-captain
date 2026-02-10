package com.weelo.logistics.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.theme.*

// =============================================================================
// WEELO CAPTAIN - PREMIUM CARD COMPONENTS
// =============================================================================
// Modern, clean card designs with Saffron Yellow accents
// Features: Subtle shadows, smooth animations, professional feel
// =============================================================================

// Static cache to track animated cards across recompositions/navigation
private object AnimationCache {
    private val animatedCounts = mutableMapOf<String, Int>()
    
    fun hasAnimated(key: String): Boolean = animatedCounts.containsKey(key)
    fun getLastCount(key: String): Int = animatedCounts[key] ?: 0
    fun setAnimated(key: String, count: Int) { animatedCounts[key] = count }
    fun reset() { animatedCounts.clear() }
}

/**
 * Premium Info Card - Display stats/metrics with animated counting
 * Clean white card with subtle yellow accents
 * Usage: Dashboard cards showing count, revenue, etc.
 */
@Composable
fun InfoCard(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    iconTint: Color = Primary,
    animateValue: Boolean = false,
    targetCount: Int = 0,
    cardKey: String = title,
    showAccent: Boolean = true
) {
    var displayCount by remember { mutableIntStateOf(
        if (AnimationCache.hasAnimated(cardKey)) AnimationCache.getLastCount(cardKey) else 0
    )}
    
    val lastAnimatedCount = AnimationCache.getLastCount(cardKey)
    val hasAnimatedBefore = AnimationCache.hasAnimated(cardKey)
    val shouldAnimate = animateValue && targetCount > 0 && 
                        (!hasAnimatedBefore || targetCount > lastAnimatedCount)
    
    LaunchedEffect(targetCount, shouldAnimate) {
        if (shouldAnimate && targetCount > 0) {
            val startFrom = if (!hasAnimatedBefore) 0 else lastAnimatedCount
            val duration = 800L
            val startTime = System.currentTimeMillis()
            val diff = targetCount - startFrom
            
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                val easedProgress = 1f - (1f - progress) * (1f - progress) * (1f - progress)
                displayCount = (startFrom + (diff * easedProgress)).toInt()
                if (progress >= 1f) break
                kotlinx.coroutines.delay(16)
            }
            
            displayCount = targetCount
            AnimationCache.setAnimated(cardKey, targetCount)
        } else if (targetCount > 0) {
            displayCount = targetCount
        }
    }
    
    val displayValue = if (animateValue && targetCount > 0) displayCount.toString() else value
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon with accent background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(BorderRadius.medium))
                    .background(if (showAccent) iconTint.copy(alpha = 0.12f) else SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(Spacing.medium))
            
            // Value
            Text(
                text = displayValue,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(Spacing.xs))
            
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Premium Status Chip - Display status with modern styling
 * Clean, professional status indicators
 * Usage: Trip status, driver availability, etc.
 */
@Composable
fun StatusChip(
    text: String,
    status: ChipStatus,
    modifier: Modifier = Modifier,
    size: ChipSize = ChipSize.MEDIUM
) {
    val (backgroundColor, textColor, dotColor) = when (status) {
        ChipStatus.AVAILABLE -> Triple(SuccessLight, Success, Success)
        ChipStatus.IN_PROGRESS -> Triple(PrimaryLight, Primary, Primary)
        ChipStatus.COMPLETED -> Triple(SurfaceVariant, TextSecondary, TextSecondary)
        ChipStatus.PENDING -> Triple(WarningLight, Warning, Warning)
        ChipStatus.CANCELLED -> Triple(ErrorLight, Error, Error)
        ChipStatus.ONLINE -> Triple(SuccessLight, Success, Success)
        ChipStatus.OFFLINE -> Triple(SurfaceVariant, TextSecondary, TextSecondary)
    }
    
    val (horizontalPadding, verticalPadding, fontSize) = when (size) {
        ChipSize.SMALL -> Triple(8.dp, 4.dp, 10.sp)
        ChipSize.MEDIUM -> Triple(12.dp, 6.dp, 12.sp)
        ChipSize.LARGE -> Triple(16.dp, 8.dp, 14.sp)
    }

    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(BorderRadius.pill)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

enum class ChipStatus {
    AVAILABLE,
    IN_PROGRESS,
    COMPLETED,
    PENDING,
    CANCELLED,
    ONLINE,
    OFFLINE
}

enum class ChipSize {
    SMALL, MEDIUM, LARGE
}

/**
 * Premium List Item Card - Modern list item with subtle hover effect
 * Clean design with proper spacing and typography
 * Usage: Vehicle list, driver list, trip list
 */
@Composable
fun ListItemCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        onClick = { onClick?.invoke() }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.cardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(Spacing.medium))
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (trailingContent != null) {
                    Spacer(modifier = Modifier.width(Spacing.medium))
                    trailingContent()
                }
            }
            
            if (showDivider) {
                Divider(
                    color = Divider,
                    thickness = 1.dp
                )
            }
        }
    }
}

/**
 * Premium Section Card - Card with styled header
 * Modern sectioned content grouping
 * Usage: Grouping related information
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPaddingLarge)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                if (action != null) {
                    action()
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.medium))
            
            // Content
            content()
        }
    }
}

/**
 * Premium Highlight Card - Card with yellow accent border
 * For featured or important content
 */
@Composable
fun HighlightCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(2.dp, accentColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPaddingLarge)
        ) {
            content()
        }
    }
}

/**
 * Premium Gradient Card - Card with gradient background
 * Eye-catching card for CTAs and featured content
 */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = PrimaryGradientColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(Spacing.cardPaddingLarge)
        ) {
            content()
        }
    }
}

/**
 * Premium Quick Action Card - For dashboard quick actions
 * Compact card with icon and label
 */
@Composable
fun QuickActionCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = Primary,
    showBadge: Boolean = false,
    badgeCount: Int = 0
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        shape = RoundedCornerShape(BorderRadius.large),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(BorderRadius.medium))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
                
                // Badge
                if (showBadge && badgeCount > 0) {
                    Badge(
                        containerColor = Error,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.small))
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
