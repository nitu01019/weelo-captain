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
import com.weelo.logistics.ui.theme.*

// =============================================================================
// WEELO CAPTAIN - PREMIUM SKELETON LOADING COMPONENTS
// =============================================================================
// Smooth shimmer animations for loading states
// Matches the new Saffron Yellow theme
// =============================================================================

// Light theme shimmer colors (using theme colors)
private val ShimmerColorShades = listOf(
    SkeletonBase,
    SkeletonHighlight,
    SkeletonBase
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

// =============================================================================
// DARK THEME SKELETON COMPONENTS
// =============================================================================
// For broadcast overlay and other dark-themed screens
// Uses darker shimmer colors that look professional on dark backgrounds
// =============================================================================

// Dark theme shimmer colors (using theme colors)
private val DarkShimmerColorShades = listOf(
    SkeletonDarkBase,
    SkeletonDarkHighlight,
    SkeletonDarkBase
)

/**
 * Creates an animated shimmer brush for dark themes
 * 
 * USAGE: For dark background screens like broadcast overlay
 */
@Composable
fun darkShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "dark_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dark_shimmer_translate"
    )
    
    return Brush.linearGradient(
        colors = DarkShimmerColorShades,
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * Dark skeleton box with shimmer effect
 * 
 * @param modifier Modifier for the box
 * @param height Height of the skeleton
 * @param width Width (null = fill max width)
 * @param shape Corner shape
 */
@Composable
fun DarkSkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    width: Dp? = null,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp)
) {
    val brush = darkShimmerBrush()
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(shape)
            .background(brush)
    )
}

/**
 * Dark skeleton circle for avatars/icons
 */
@Composable
fun DarkSkeletonCircle(
    size: Dp = 48.dp
) {
    val brush = darkShimmerBrush()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(brush)
    )
}

// =============================================================================
// BROADCAST OVERLAY SKELETON
// =============================================================================
// Complete loading skeleton for the broadcast overlay screen
// Matches the exact layout of BroadcastOverlayContentNew
// =============================================================================

/**
 * Full skeleton for broadcast overlay while loading
 * 
 * DESIGN:
 * - Matches BroadcastOverlayContentNew layout exactly
 * - Dark theme with subtle shimmer
 * - Professional appearance during data fetch
 * 
 * USAGE:
 * ```kotlin
 * if (isLoading) {
 *     BroadcastOverlaySkeleton()
 * } else {
 *     BroadcastOverlayContentNew(...)
 * }
 * ```
 */
@Composable
fun BroadcastOverlaySkeleton(
    modifier: Modifier = Modifier
) {
    val darkBg = Color(0xFF1A1A1A)
    val cardBg = Color(0xFF2D2D2D)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // ============== HEADER SKELETON ==============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(darkBg)
                    .padding(16.dp)
            ) {
                // Top row: Close | Timer | Direction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DarkSkeletonCircle(size = 44.dp)
                    DarkSkeletonBox(height = 36.dp, width = 80.dp, shape = RoundedCornerShape(20.dp))
                    DarkSkeletonBox(height = 36.dp, width = 100.dp, shape = RoundedCornerShape(12.dp))
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Title skeleton
                DarkSkeletonBox(height = 28.dp, width = 160.dp, shape = RoundedCornerShape(4.dp))
            }
            
            // ============== MAIN CONTENT SKELETON ==============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(cardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Order info skeleton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        DarkSkeletonBox(height = 12.dp, width = 80.dp)
                        Spacer(Modifier.height(6.dp))
                        DarkSkeletonBox(height = 18.dp, width = 120.dp)
                    }
                    DarkSkeletonBox(height = 48.dp, width = 100.dp, shape = RoundedCornerShape(8.dp))
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Route skeleton
                BroadcastRouteSkeletonCard()
                
                Spacer(Modifier.height(8.dp))
                
                // Section title skeleton
                DarkSkeletonBox(height = 12.dp, width = 180.dp)
                
                // Truck cards skeleton
                repeat(2) {
                    BroadcastTruckCardSkeleton()
                }
            }
            
            // ============== SUBMIT BUTTON SKELETON ==============
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(darkBg)
                    .padding(16.dp)
            ) {
                DarkSkeletonBox(
                    height = 56.dp,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

/**
 * Skeleton for route card in broadcast overlay
 */
@Composable
fun BroadcastRouteSkeletonCard(
    modifier: Modifier = Modifier
) {
    val darkBg = Color(0xFF1A1A1A)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(darkBg, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Pickup row
        Row(verticalAlignment = Alignment.Top) {
            DarkSkeletonCircle(size = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                DarkSkeletonBox(height = 10.dp, width = 50.dp)
                Spacer(Modifier.height(6.dp))
                DarkSkeletonBox(height = 14.dp)
            }
        }
        
        // Connector line
        Box(
            modifier = Modifier
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                .width(2.dp)
                .height(16.dp)
                .background(Color(0xFF424242))
        )
        
        // Drop row
        Row(verticalAlignment = Alignment.Top) {
            DarkSkeletonCircle(size = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                DarkSkeletonBox(height = 10.dp, width = 40.dp)
                Spacer(Modifier.height(6.dp))
                DarkSkeletonBox(height = 14.dp)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Distance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            DarkSkeletonBox(height = 14.dp, width = 60.dp)
        }
    }
}

/**
 * Skeleton for truck type card in broadcast overlay
 */
@Composable
@Suppress("UNUSED_VARIABLE")
fun BroadcastTruckCardSkeleton(
    modifier: Modifier = Modifier
) {
    val darkBg = Color(0xFF1A1A1A)
    val borderColor = Color(0xFF424242)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(darkBg, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DarkSkeletonCircle(size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    DarkSkeletonBox(height = 16.dp, width = 100.dp)
                    Spacer(Modifier.height(4.dp))
                    DarkSkeletonBox(height = 12.dp, width = 60.dp)
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                DarkSkeletonBox(height = 18.dp, width = 70.dp)
                Spacer(Modifier.height(4.dp))
                DarkSkeletonBox(height = 10.dp, width = 40.dp)
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quantity selector skeleton
            DarkSkeletonBox(height = 48.dp, width = 130.dp, shape = RoundedCornerShape(8.dp))
            
            // Buttons skeleton
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkSkeletonBox(height = 40.dp, width = 60.dp, shape = RoundedCornerShape(8.dp))
                DarkSkeletonBox(height = 40.dp, width = 80.dp, shape = RoundedCornerShape(8.dp))
            }
        }
    }
}

/**
 * Skeleton for truck selection screen (after submit)
 */
@Composable
@Suppress("UNUSED_VARIABLE")
fun TruckSelectionSkeleton(
    modifier: Modifier = Modifier,
    truckCount: Int = 3
) {
    val darkBg = Color(0xFF1A1A1A)
    val cardBg = Color(0xFF2D2D2D)
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DarkSkeletonCircle(size = 40.dp)
            DarkSkeletonBox(height = 24.dp, width = 120.dp)
            DarkSkeletonBox(height = 32.dp, width = 60.dp, shape = RoundedCornerShape(20.dp))
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Broadcast summary card
        DarkSkeletonBox(height = 80.dp, shape = RoundedCornerShape(12.dp))
        
        Spacer(Modifier.height(8.dp))
        
        // Info text
        DarkSkeletonBox(height = 14.dp, width = 200.dp)
        
        // Truck list
        repeat(truckCount) {
            TruckSelectionCardSkeleton()
        }
    }
}

/**
 * Skeleton for individual truck selection card
 */
@Composable
fun TruckSelectionCardSkeleton(
    modifier: Modifier = Modifier
) {
    val darkBg = Color(0xFF1A1A1A)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(darkBg, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DarkSkeletonCircle(size = 48.dp)
            Column {
                DarkSkeletonBox(height = 16.dp, width = 100.dp)
                Spacer(Modifier.height(6.dp))
                DarkSkeletonBox(height = 13.dp, width = 80.dp)
                Spacer(Modifier.height(4.dp))
                DarkSkeletonBox(height = 12.dp, width = 70.dp)
            }
        }
        
        DarkSkeletonCircle(size = 28.dp)
    }
}

/**
 * Skeleton for driver assignment row
 */
@Composable
fun DriverAssignmentSkeleton(
    modifier: Modifier = Modifier
) {
    val darkBg = Color(0xFF2D2D2D)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(darkBg, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Vehicle info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DarkSkeletonCircle(size = 40.dp)
                Column {
                    DarkSkeletonBox(height = 15.dp, width = 90.dp)
                    Spacer(Modifier.height(4.dp))
                    DarkSkeletonBox(height = 12.dp, width = 70.dp)
                }
            }
            
            DarkSkeletonBox(height = 32.dp, width = 100.dp, shape = RoundedCornerShape(6.dp))
        }
    }
}

// =============================================================================
// DRIVER DASHBOARD SKELETON
// =============================================================================
// Full-page skeleton that mirrors the DriverDashboardScreen layout.
// Shown ONLY on the very first load when cache is empty.
// On subsequent visits, cached data renders instantly (0ms).
//
// SCALABILITY: Uses the same responsive padding system as the real dashboard.
// MODULARITY: Composed from primitive SkeletonBox / SkeletonCircle atoms.
// PERFORMANCE: Pure Box + Brush composables — no images, no text measurement.
// =============================================================================

/**
 * Skeleton for the Driver Dashboard — matches DashboardContent layout exactly.
 *
 * Layout mirrors:
 * 1. Online status toggle
 * 2. Earnings card
 * 3. Trip stats grid (2×2)
 * 4. Performance metrics card
 * 5. Recent trips list (3 items)
 *
 * USAGE:
 * ```kotlin
 * when (state) {
 *     is Loading -> DriverDashboardSkeleton()
 *     is Success -> DashboardContent(...)
 * }
 * ```
 */
@Composable
fun DriverDashboardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Online status toggle skeleton
        SkeletonBox(
            height = 48.dp,
            shape = RoundedCornerShape(24.dp)
        )
        
        // 2. Earnings card skeleton
        SkeletonEarningsCard()
        
        // 3. Trip stats grid (2×2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonStatCard(modifier = Modifier.weight(1f))
            SkeletonStatCard(modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonStatCard(modifier = Modifier.weight(1f))
            SkeletonStatCard(modifier = Modifier.weight(1f))
        }
        
        // 4. Performance metrics skeleton
        SkeletonPerformanceCard()
        
        // 5. Recent trips header + items
        SkeletonBox(height = 20.dp, width = 140.dp)
        repeat(3) {
            SkeletonTripItem()
        }
    }
}

/**
 * Skeleton for the Earnings card — mirrors EarningsCard layout.
 */
@Composable
private fun SkeletonEarningsCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Title
            SkeletonBox(height = 18.dp, width = 120.dp)
            Spacer(Modifier.height(16.dp))
            
            // Amount
            SkeletonBox(height = 32.dp, width = 160.dp)
            Spacer(Modifier.height(8.dp))
            
            // Sub-text
            SkeletonBox(height = 14.dp, width = 100.dp)
            Spacer(Modifier.height(20.dp))
            
            // Period tabs row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SkeletonBox(height = 36.dp, width = 80.dp, shape = RoundedCornerShape(18.dp))
                SkeletonBox(height = 36.dp, width = 80.dp, shape = RoundedCornerShape(18.dp))
                SkeletonBox(height = 36.dp, width = 80.dp, shape = RoundedCornerShape(18.dp))
            }
        }
    }
}

/**
 * Skeleton for the Performance metrics card — mirrors PerformanceMetricsCard.
 */
@Composable
private fun SkeletonPerformanceCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            SkeletonBox(height = 18.dp, width = 160.dp)
            Spacer(Modifier.height(16.dp))
            
            // Rating row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonCircle(size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    SkeletonBox(height = 20.dp, width = 60.dp)
                    Spacer(Modifier.height(4.dp))
                    SkeletonBox(height = 12.dp, width = 90.dp)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Progress bars
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBox(height = 14.dp, width = 100.dp)
                    Spacer(Modifier.width(12.dp))
                    SkeletonBox(
                        height = 8.dp,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    SkeletonBox(height = 14.dp, width = 40.dp)
                }
                if (it < 2) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Skeleton for a single trip history item — mirrors TripHistoryItem.
 */
@Composable
private fun SkeletonTripItem(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trip icon placeholder
            SkeletonCircle(size = 40.dp)
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                SkeletonBox(height = 16.dp, width = 140.dp)
                Spacer(Modifier.height(6.dp))
                SkeletonBox(height = 12.dp, width = 100.dp)
            }
            
            Spacer(Modifier.width(8.dp))
            
            // Earnings amount
            Column(horizontalAlignment = Alignment.End) {
                SkeletonBox(height = 16.dp, width = 60.dp)
                Spacer(Modifier.height(4.dp))
                SkeletonBox(height = 10.dp, width = 40.dp)
            }
        }
    }
}

// =============================================================================
// ONBOARDING CHECK SKELETON
// =============================================================================
// Shown during the driver_onboarding_check route (~50-200ms) while DataStore
// preferences are read. Replaces the bare CircularProgressIndicator.
//
// DESIGN: Simple top-bar + content placeholder shimmer.
// PURPOSE: Eliminates the "blank screen with spinner" feeling after OTP.
// PERFORMANCE: Trivial composable — no state, no network.
// =============================================================================

/**
 * Skeleton placeholder for the driver onboarding check screen.
 *
 * Displays a shimmer top bar and content area so the user sees a polished
 * loading state instead of a bare spinner during preference checks.
 *
 * USAGE (in WeeloNavigation.kt):
 * ```kotlin
 * composable("driver_onboarding_check") {
 *     OnboardingCheckSkeleton()    // ← replaces CircularProgressIndicator
 *     LaunchedEffect(Unit) { ... } // preference check + navigation
 * }
 * ```
 */
@Composable
fun OnboardingCheckSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Fake top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonCircle(size = 40.dp)
            Spacer(Modifier.width(16.dp))
            SkeletonBox(height = 22.dp, width = 140.dp)
            Spacer(Modifier.weight(1f))
            SkeletonCircle(size = 32.dp)
        }
        
        // Divider shimmer
        SkeletonBox(
            height = 1.dp,
            shape = RoundedCornerShape(0.dp)
        )
        
        // Content area — mimics dashboard skeleton but lighter
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status toggle placeholder
            SkeletonBox(
                height = 48.dp,
                shape = RoundedCornerShape(24.dp)
            )
            
            // Card placeholder
            SkeletonBox(
                height = 140.dp,
                shape = RoundedCornerShape(16.dp)
            )
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonBox(
                    height = 90.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                SkeletonBox(
                    height = 90.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // List placeholder
            SkeletonBox(
                height = 100.dp,
                shape = RoundedCornerShape(12.dp)
            )
            
            SkeletonBox(
                height = 100.dp,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}
