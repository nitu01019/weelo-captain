package com.weelo.logistics.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.theme.*

// Static cache to track animated cards across recompositions/navigation
private object AnimationCache {
    // Stores: cardKey -> lastAnimatedCount
    private val animatedCounts = mutableMapOf<String, Int>()
    
    fun hasAnimated(key: String): Boolean = animatedCounts.containsKey(key)
    
    fun getLastCount(key: String): Int = animatedCounts[key] ?: 0
    
    fun setAnimated(key: String, count: Int) {
        animatedCounts[key] = count
    }
    
    // Call this when app starts fresh (optional)
    fun reset() {
        animatedCounts.clear()
    }
}

/**
 * Info Card - Display stats/metrics with animated counting
 * Usage: Dashboard cards showing count, revenue, etc.
 * 
 * Animation triggers:
 * - ONCE when app opens and data first loads
 * - Again ONLY when new data is added (count increases)
 * - Static otherwise (no re-animation on navigation)
 * 
 * @param animateValue If true, animates number from 0 to target (1,2,3...N)
 * @param targetCount The target number to animate to (used when animateValue=true)
 * @param cardKey Unique key for this card to track animation state
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
    cardKey: String = title // Use title as default key
) {
    // Current display value
    var displayCount by remember { mutableIntStateOf(
        if (AnimationCache.hasAnimated(cardKey)) AnimationCache.getLastCount(cardKey) else 0
    )}
    
    // Check if should animate
    val lastAnimatedCount = AnimationCache.getLastCount(cardKey)
    val hasAnimatedBefore = AnimationCache.hasAnimated(cardKey)
    val shouldAnimate = animateValue && targetCount > 0 && 
                        (!hasAnimatedBefore || targetCount > lastAnimatedCount)
    
    // Animate only when needed
    LaunchedEffect(targetCount, shouldAnimate) {
        if (shouldAnimate && targetCount > 0) {
            val startFrom = if (!hasAnimatedBefore) 0 else lastAnimatedCount
            
            // Smooth counting animation
            val duration = 800L // 800ms for smooth effect
            val startTime = System.currentTimeMillis()
            val diff = targetCount - startFrom
            
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                
                // Ease out cubic for smooth deceleration
                val easedProgress = 1f - (1f - progress) * (1f - progress) * (1f - progress)
                
                displayCount = (startFrom + (diff * easedProgress)).toInt()
                
                if (progress >= 1f) break
                kotlinx.coroutines.delay(16) // ~60fps
            }
            
            displayCount = targetCount
            AnimationCache.setAnimated(cardKey, targetCount)
        } else if (targetCount > 0) {
            // No animation needed - show value instantly
            displayCount = targetCount
        }
    }
    
    // Display value
    val displayValue = if (animateValue && targetCount > 0) displayCount.toString() else value
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.low
        ),
        shape = RoundedCornerShape(BorderRadius.medium),
        colors = CardDefaults.cardColors(
            containerColor = White
        )
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.medium)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                text = displayValue,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

/**
 * Status Chip - Display status with colored background
 * Usage: Trip status, driver availability, etc.
 */
@Composable
fun StatusChip(
    text: String,
    status: ChipStatus,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (status) {
        ChipStatus.AVAILABLE -> Success
        ChipStatus.IN_PROGRESS -> Info
        ChipStatus.COMPLETED -> TextSecondary
        ChipStatus.PENDING -> Warning
        ChipStatus.CANCELLED -> Error
    }

    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(BorderRadius.medium)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(White, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

enum class ChipStatus {
    AVAILABLE,
    IN_PROGRESS,
    COMPLETED,
    PENDING,
    CANCELLED
}

/**
 * List Item Card - Generic list item with title, subtitle, and optional trailing content
 * Usage: Vehicle list, driver list, trip list
 */
@Composable
fun ListItemCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.low
        ),
        shape = RoundedCornerShape(BorderRadius.medium),
        colors = CardDefaults.cardColors(
            containerColor = White
        ),
        onClick = { onClick?.invoke() }
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.medium)
                .fillMaxWidth(),
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
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(Spacing.medium))
                trailingContent()
            }
        }
    }
}

/**
 * Section Card - Card with a header section
 * Usage: Grouping related information
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.low
        ),
        shape = RoundedCornerShape(BorderRadius.medium),
        colors = CardDefaults.cardColors(
            containerColor = White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(Spacing.medium))
            content()
        }
    }
}
