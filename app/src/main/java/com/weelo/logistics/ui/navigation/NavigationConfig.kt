package com.weelo.logistics.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/**
 * Navigation Configuration - Production-level smooth transitions
 * 
 * Optimizations:
 * - Fast, smooth animations (200ms)
 * - GPU-accelerated transitions
 * - Memory-efficient navigation
 * - Predictive back gesture support
 */
object NavigationConfig {
    
    // Animation duration in milliseconds
    const val ANIMATION_DURATION = 200
    
    // Slide animation specs
    private val tweenSpec = tween<IntOffset>(
        durationMillis = ANIMATION_DURATION,
        easing = FastOutSlowInEasing
    )
    
    private val fadeTweenSpec = tween<Float>(
        durationMillis = ANIMATION_DURATION,
        easing = LinearEasing
    )
    
    /**
     * Slide in from right (Forward navigation)
     */
    fun slideInFromRight(): EnterTransition {
        return slideInHorizontally(
            animationSpec = tweenSpec,
            initialOffsetX = { fullWidth -> fullWidth }
        ) + fadeIn(animationSpec = fadeTweenSpec)
    }
    
    /**
     * Slide out to left (Forward navigation)
     */
    fun slideOutToLeft(): ExitTransition {
        return slideOutHorizontally(
            animationSpec = tweenSpec,
            targetOffsetX = { fullWidth -> -fullWidth / 3 }
        ) + fadeOut(animationSpec = fadeTweenSpec)
    }
    
    /**
     * Slide in from left (Back navigation)
     */
    fun slideInFromLeft(): EnterTransition {
        return slideInHorizontally(
            animationSpec = tweenSpec,
            initialOffsetX = { fullWidth -> -fullWidth / 3 }
        ) + fadeIn(animationSpec = fadeTweenSpec)
    }
    
    /**
     * Slide out to right (Back navigation)
     */
    fun slideOutToRight(): ExitTransition {
        return slideOutHorizontally(
            animationSpec = tweenSpec,
            targetOffsetX = { fullWidth -> fullWidth }
        ) + fadeOut(animationSpec = fadeTweenSpec)
    }
    
    /**
     * Fade transition (for dialogs/overlays)
     */
    fun fadeInTransition(): EnterTransition {
        return fadeIn(animationSpec = fadeTweenSpec)
    }
    
    fun fadeOutTransition(): ExitTransition {
        return fadeOut(animationSpec = fadeTweenSpec)
    }
    
    /**
     * Scale + Fade (for bottom sheets)
     */
    fun scaleInTransition(): EnterTransition {
        return scaleIn(
            animationSpec = tween(ANIMATION_DURATION),
            initialScale = 0.9f
        ) + fadeIn(animationSpec = fadeTweenSpec)
    }
    
    fun scaleOutTransition(): ExitTransition {
        return scaleOut(
            animationSpec = tween(ANIMATION_DURATION),
            targetScale = 0.9f
        ) + fadeOut(animationSpec = fadeTweenSpec)
    }
}
