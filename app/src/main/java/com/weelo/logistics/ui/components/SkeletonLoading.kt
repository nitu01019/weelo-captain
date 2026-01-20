package com.weelo.logistics.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeleton Loading Components for smooth loading states
 * Uses shimmer animation for better UX
 */

// Shimmer colors
private val ShimmerColorShades = listOf(
    Color(0xFFE0E0E0),
    Color(0xFFF5F5F5),
    Color(0xFFE0E0E0)
)

/**
 * Creates an animated shimmer brush
 */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    
    return Brush.linearGradient(
        colors = ShimmerColorShades,
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * Skeleton box with shimmer effect
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    width: Dp? = null,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp)
) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(shape)
            .background(brush)
    )
}

/**
 * Skeleton circle for avatars
 */
@Composable
fun SkeletonCircle(
    size: Dp = 48.dp
) {
    val brush = shimmerBrush()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(brush)
    )
}

/**
 * Skeleton card for list items (e.g., driver cards)
 */
@Composable
fun SkeletonListCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar skeleton
            SkeletonCircle(size = 56.dp)
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Name skeleton
                SkeletonBox(height = 20.dp, width = 120.dp)
                Spacer(Modifier.height(8.dp))
                // Phone skeleton
                SkeletonBox(height = 14.dp, width = 100.dp)
                Spacer(Modifier.height(8.dp))
                // Info skeleton
                SkeletonBox(height = 12.dp, width = 80.dp)
            }
            
            Spacer(Modifier.width(12.dp))
            
            // Status chip skeleton
            SkeletonBox(height = 24.dp, width = 70.dp, shape = RoundedCornerShape(12.dp))
        }
    }
}

/**
 * Skeleton for dashboard stat cards
 */
@Composable
fun SkeletonStatCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SkeletonCircle(size = 40.dp)
            Spacer(Modifier.height(12.dp))
            SkeletonBox(height = 14.dp, width = 80.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonBox(height = 24.dp, width = 40.dp)
        }
    }
}

/**
 * Multiple skeleton list items for loading state
 */
@Composable
fun SkeletonList(
    itemCount: Int = 5,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(itemCount) {
            SkeletonListCard()
        }
    }
}

/**
 * Skeleton for profile header in drawer
 */
@Composable
fun SkeletonProfileHeader(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(24.dp)) {
        SkeletonCircle(size = 72.dp)
        Spacer(Modifier.height(16.dp))
        SkeletonBox(height = 24.dp, width = 150.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBox(height = 16.dp, width = 120.dp)
    }
}
