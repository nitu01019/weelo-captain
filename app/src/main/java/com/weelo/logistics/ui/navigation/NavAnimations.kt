package com.weelo.logistics.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*

/**
 * =============================================================================
 * NAVIGATION ANIMATIONS - Smooth Screen Transitions
 * =============================================================================
 * 
 * Provides smooth, polished animations for navigation transitions.
 * Inspired by Material Design motion guidelines.
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
    
    // Animation duration constants
    private const val DURATION_STANDARD = 300
    private const val DURATION_FAST = 200
    private const val DURATION_EMPHASIZED = 400
    
    // Easing curves (Material Design 3)
    private val EasingEmphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    private val EasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    private val EasingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    private val EasingStandard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    
    // =========================================================================
    // SLIDE TRANSITIONS (Horizontal - for forward/back navigation)
    // =========================================================================
    
    /**
     * Slide in from right - for navigating forward
     */
    val slideInFromRight: EnterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingEmphasizedDecelerate
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = LinearEasing
        )
    )
    
    /**
     * Slide out to left - for navigating forward (current screen exits)
     */
    val slideOutToLeft: ExitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth / 4 },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingEmphasizedAccelerate
        )
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = LinearEasing
        )
    )
    
    /**
     * Slide in from left - for navigating back
     */
    val slideInFromLeft: EnterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth / 4 },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingEmphasizedDecelerate
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = LinearEasing
        )
    )
    
    /**
     * Slide out to right - for navigating back (current screen exits)
     */
    val slideOutToRight: ExitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingEmphasizedAccelerate
        )
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = LinearEasing
        )
    )
    
    // =========================================================================
    // FADE TRANSITIONS (For modals, dialogs, overlays)
    // =========================================================================
    
    /**
     * Fade in with slight scale - for modal screens
     */
    val fadeInWithScale: EnterTransition = fadeIn(
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingEmphasizedDecelerate
        )
    ) + scaleIn(
        initialScale = 0.92f,
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingEmphasizedDecelerate
        )
    )
    
    /**
     * Fade out with slight scale - for modal screens
     */
    val fadeOutWithScale: ExitTransition = fadeOut(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = EasingEmphasizedAccelerate
        )
    ) + scaleOut(
        targetScale = 0.92f,
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = EasingEmphasizedAccelerate
        )
    )
    
    /**
     * Simple fade in
     */
    val fadeIn: EnterTransition = androidx.compose.animation.fadeIn(
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingStandard
        )
    )
    
    /**
     * Simple fade out
     */
    val fadeOut: ExitTransition = androidx.compose.animation.fadeOut(
        animationSpec = tween(
            durationMillis = DURATION_FAST,
            easing = EasingStandard
        )
    )
    
    // =========================================================================
    // VERTICAL TRANSITIONS (For bottom sheets, notifications)
    // =========================================================================
    
    /**
     * Slide up from bottom - for bottom sheets
     */
    val slideInFromBottom: EnterTransition = slideInVertically(
        initialOffsetY = { fullHeight -> fullHeight },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingEmphasizedDecelerate
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = DURATION_FAST
        )
    )
    
    /**
     * Slide down to bottom - for dismissing bottom sheets
     */
    val slideOutToBottom: ExitTransition = slideOutVertically(
        targetOffsetY = { fullHeight -> fullHeight },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingEmphasizedAccelerate
        )
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = DURATION_FAST
        )
    )
    
    /**
     * Slide down from top - for notifications/toasts
     */
    val slideInFromTop: EnterTransition = slideInVertically(
        initialOffsetY = { fullHeight -> -fullHeight },
        animationSpec = tween(
            durationMillis = DURATION_STANDARD,
            easing = EasingEmphasizedDecelerate
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = DURATION_FAST
        )
    )
    
    // =========================================================================
    // SHARED ELEMENT TRANSITIONS (Container transform)
    // =========================================================================
    
    /**
     * Expand from center - for FAB to screen transitions
     */
    val expandFromCenter: EnterTransition = scaleIn(
        initialScale = 0.0f,
        animationSpec = tween(
            durationMillis = DURATION_EMPHASIZED,
            easing = EasingEmphasized
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = DURATION_EMPHASIZED / 2,
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
            easing = EasingEmphasized
        )
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = DURATION_EMPHASIZED / 2,
            easing = LinearEasing
        )
    )
    
    // =========================================================================
    // NO ANIMATION (For instant transitions)
    // =========================================================================
    
    val none: EnterTransition = EnterTransition.None
    val noneExit: ExitTransition = ExitTransition.None
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
