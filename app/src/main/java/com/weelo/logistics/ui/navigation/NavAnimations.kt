package com.weelo.logistics.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*

/**
 * =============================================================================
 * NAVIGATION ANIMATIONS - Optimized for Performance
 * =============================================================================
 * 
 * OPTIMIZATIONS FOR SMOOTH 60fps ANIMATIONS:
 * 1. Reduced animation duration (300ms → 200ms) for snappier feel
 * 2. Simplified easing curves for GPU efficiency
 * 3. Removed redundant fade animations on main transitions
 * 4. Uses hardware-accelerated slide animations
 * 
 * PERFORMANCE TIPS:
 * - Slide animations are GPU-accelerated (fast)
 * - Fade animations require alpha blending (slower)
 * - Scale animations can cause jank on complex screens
 * - Keep animations under 300ms for perceived responsiveness
 * 
 * USAGE:
 * ```kotlin
 * composable(
 *     route = "screen",
 *     enterTransition = { NavAnimations.slideInFromRight },
 *     exitTransition = { NavAnimations.slideOutToLeft },
 *     popEnterTransition = { NavAnimations.slideInFromLeft },
 *     popExitTransition = { NavAnimations.slideOutToRight }
 * )
 * ```
 * =============================================================================
 */
object NavAnimations {
    
    // =========================================================================
    // OPTIMIZED ANIMATION DURATIONS
    // =========================================================================
    // Reduced for snappier, more responsive feel
    // 200ms is the sweet spot for perceived instant response
    
    private const val DURATION_FAST = 150        // Quick transitions
    private const val DURATION_STANDARD = 200    // Normal navigation (was 300)
    private const val DURATION_EMPHASIZED = 250  // Important transitions (was 400)
    
    // =========================================================================
    // OPTIMIZED EASING CURVES
    // =========================================================================
    // Using simpler curves for better GPU performance
    
    private val EasingFastOutSlowIn = FastOutSlowInEasing  // Standard Material easing
    private val EasingLinearOutSlowIn = LinearOutSlowInEasing  // Decelerate (entering)
    private val EasingFastOutLinearIn = FastOutLinearInEasing  // Accelerate (exiting)
    
    // =========================================================================
    // SLIDE TRANSITIONS (Horizontal - for forward/back navigation)
    // =========================================================================
    // OPTIMIZED: Pure slide without fade for GPU acceleration
    
    /**
     * Slide in from right - for navigating forward
     * OPTIMIZED: No fade, pure hardware-accelerated slide
     */
    val slideInFromRight: EnterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingLinearOutSlowIn
        )
    )
    
    /**
     * Slide out to left - for navigating forward (current screen exits)
     * OPTIMIZED: Pure slide without fade — eliminates alpha blending GPU overhead
     */
    val slideOutToLeft: ExitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth / 5 },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingFastOutLinearIn
        )
    )
    
    /**
     * Slide in from left - for navigating back
     * OPTIMIZED: Pure slide without fade — eliminates alpha blending GPU overhead
     */
    val slideInFromLeft: EnterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth / 5 },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingLinearOutSlowIn
        )
    )
    
    /**
     * Slide out to right - for navigating back (current screen exits)
     * OPTIMIZED: Full slide out
     */
    val slideOutToRight: ExitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingFastOutLinearIn
        )
    )
    
    // =========================================================================
    // FADE TRANSITIONS (For modals, dialogs, overlays)
    // =========================================================================
    // OPTIMIZED: Reduced scale animation for better performance
    
    /**
     * Fade in with slight scale - for modal screens
     * OPTIMIZED: Minimal scale (0.95 instead of 0.92) for faster render
     */
    val fadeInWithScale: EnterTransition = fadeIn(
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingFastOutSlowIn
        )
    ) + scaleIn(
        initialScale = 0.95f,
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingFastOutSlowIn
        )
    )
    
    /**
     * Fade out with slight scale - for modal screens
     */
    val fadeOutWithScale: ExitTransition = fadeOut(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = EasingFastOutLinearIn
        )
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = EasingFastOutLinearIn
        )
    )
    
    /**
     * Simple fade in - OPTIMIZED
     */
    val fadeIn: EnterTransition = androidx.compose.animation.fadeIn(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = LinearEasing
        )
    )
    
    /**
     * Simple fade out - OPTIMIZED
     */
    val fadeOut: ExitTransition = androidx.compose.animation.fadeOut(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = LinearEasing
        )
    )
    
    // =========================================================================
    // VERTICAL TRANSITIONS (For bottom sheets, notifications)
    // =========================================================================
    // OPTIMIZED: Faster vertical transitions
    
    /**
     * Slide up from bottom - for bottom sheets
     */
    val slideInFromBottom: EnterTransition = slideInVertically(
        initialOffsetY = { fullHeight -> fullHeight },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingLinearOutSlowIn
        )
    )
    
    /**
     * Slide down to bottom - for dismissing bottom sheets
     */
    val slideOutToBottom: ExitTransition = slideOutVertically(
        targetOffsetY = { fullHeight -> fullHeight },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingFastOutLinearIn
        )
    )
    
    /**
     * Slide down from top - for notifications/toasts
     */
    val slideInFromTop: EnterTransition = slideInVertically(
        initialOffsetY = { fullHeight -> -fullHeight },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingLinearOutSlowIn
        )
    )
    
    // =========================================================================
    // SHARED ELEMENT TRANSITIONS (Container transform)
    // =========================================================================
    // OPTIMIZED: Faster scale transitions
    
    /**
     * Expand from center - for FAB to screen transitions
     */
    val expandFromCenter: EnterTransition = scaleIn(
        initialScale = 0.0f,
        animationSpec = tween(
            durationMillis = DURATION_EMPHASIZED,
            easing = EasingFastOutSlowIn
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = LinearEasing
        )
    )
    
    /**
     * Shrink to center - for screen to FAB transitions
     */
    val shrinkToCenter: ExitTransition = scaleOut(
        targetScale = 0.0f,
        animationSpec = tween(
            durationMillis = DURATION_EMPHASIZED,
            easing = EasingFastOutSlowIn
        )
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = LinearEasing
        )
    )
    
    // =========================================================================
    // NO ANIMATION (For instant transitions)
    // =========================================================================
    
    val none: EnterTransition = EnterTransition.None
    val noneExit: ExitTransition = ExitTransition.None

    private enum class RouteProfile {
        AUTH,
        MODAL_FLOW,
        HEAVY_REALTIME,
        STANDARD
    }

    private fun routeProfile(route: String?): RouteProfile {
        if (route.isNullOrBlank()) return RouteProfile.STANDARD
        return when {
            route == Screen.RoleSelection.route ||
                route == Screen.Login.route ||
                route == Screen.Signup.route ||
                route.startsWith("login") ||
                route.startsWith("signup") ||
                route.startsWith("otp_verification") -> RouteProfile.AUTH

            route.startsWith("truck_hold_confirm") -> RouteProfile.MODAL_FLOW

            route == "transporter_profile" -> RouteProfile.HEAVY_REALTIME

            route.startsWith("live_tracking") ||
                route.startsWith("trip_tracking") ||
                route.startsWith("driver_trip_navigation") ||
                route.startsWith("trip_accept_decline") -> RouteProfile.HEAVY_REALTIME

            else -> RouteProfile.STANDARD
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun enterForRoute(initialRoute: String?, targetRoute: String?): EnterTransition =
        when (routeProfile(targetRoute)) {
            RouteProfile.AUTH -> fadeIn
            RouteProfile.MODAL_FLOW -> fadeInWithScale
            RouteProfile.HEAVY_REALTIME -> fadeIn
            RouteProfile.STANDARD -> slideInFromRight
        }

    @Suppress("UNUSED_PARAMETER")
    fun exitForRoute(initialRoute: String?, targetRoute: String?): ExitTransition =
        when (routeProfile(targetRoute)) {
            RouteProfile.AUTH -> fadeOut
            RouteProfile.MODAL_FLOW -> fadeOutWithScale
            RouteProfile.HEAVY_REALTIME -> fadeOut
            RouteProfile.STANDARD -> slideOutToLeft
        }

    @Suppress("UNUSED_PARAMETER")
    fun popEnterForRoute(initialRoute: String?, targetRoute: String?): EnterTransition =
        when (routeProfile(targetRoute)) {
            RouteProfile.AUTH -> fadeIn
            RouteProfile.MODAL_FLOW -> fadeInWithScale
            RouteProfile.HEAVY_REALTIME -> fadeIn
            RouteProfile.STANDARD -> slideInFromLeft
        }

    @Suppress("UNUSED_PARAMETER")
    fun popExitForRoute(initialRoute: String?, targetRoute: String?): ExitTransition =
        when (routeProfile(initialRoute)) {
            RouteProfile.AUTH -> fadeOut
            RouteProfile.MODAL_FLOW -> fadeOutWithScale
            RouteProfile.HEAVY_REALTIME -> fadeOut
            RouteProfile.STANDARD -> slideOutToRight
        }
}

/**
 * Helper to create nav animations spec easily
 */
data class NavAnimationSpec(
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition,
    val popEnterTransition: EnterTransition,
    val popExitTransition: ExitTransition
)

/**
 * Pre-configured animation specs for common navigation patterns
 */
object NavAnimationSpecs {
    /**
     * Standard horizontal slide - for most screen navigations
     */
    val slideHorizontal = NavAnimationSpec(
        enterTransition = NavAnimations.slideInFromRight,
        exitTransition = NavAnimations.slideOutToLeft,
        popEnterTransition = NavAnimations.slideInFromLeft,
        popExitTransition = NavAnimations.slideOutToRight
    )
    
    /**
     * Fade with scale - for modal/dialog screens
     */
    val modal = NavAnimationSpec(
        enterTransition = NavAnimations.fadeInWithScale,
        exitTransition = NavAnimations.fadeOutWithScale,
        popEnterTransition = NavAnimations.fadeIn,
        popExitTransition = NavAnimations.fadeOutWithScale
    )
    
    /**
     * Bottom sheet style - slide from bottom
     */
    val bottomSheet = NavAnimationSpec(
        enterTransition = NavAnimations.slideInFromBottom,
        exitTransition = NavAnimations.slideOutToBottom,
        popEnterTransition = NavAnimations.fadeIn,
        popExitTransition = NavAnimations.slideOutToBottom
    )
    
    /**
     * Simple fade - for tab switches
     */
    val fade = NavAnimationSpec(
        enterTransition = NavAnimations.fadeIn,
        exitTransition = NavAnimations.fadeOut,
        popEnterTransition = NavAnimations.fadeIn,
        popExitTransition = NavAnimations.fadeOut
    )
}
