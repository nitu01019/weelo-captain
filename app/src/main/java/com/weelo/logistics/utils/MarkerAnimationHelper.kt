package com.weelo.logistics.utils

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import kotlin.math.*

/**
 * =============================================================================
 * MARKER ANIMATION HELPER - Ola/Uber Style Smooth Tracking
 * =============================================================================
 * 
 * Creates smooth "live" tracking illusion on Google Maps.
 * 
 * THE SECRET:
 * ───────────
 * Backend sends location every 5-10 seconds.
 * This helper INTERPOLATES between points to create smooth motion.
 * User thinks it's live tracking - it's actually clever animation!
 * 
 * HOW IT WORKS:
 * ─────────────
 * 1. Receive Point A (current position)
 * 2. Receive Point B (new position after 5 sec)
 * 3. Animate marker smoothly from A to B over 5 seconds
 * 4. Calculate ~60 fake positions per second between A and B
 * 5. User sees smooth movement!
 * 
 * WHAT UBER/OLA DO:
 * ─────────────────
 * - Same technique
 * - GPS updates every 3-5 seconds
 * - Frontend interpolates
 * - Users think it's real-time
 * 
 * USAGE:
 * ──────
 * ```kotlin
 * // When you receive new location from WebSocket/API:
 * MarkerAnimationHelper.animateMarker(
 *     marker = driverMarker,
 *     toPosition = newLatLng,
 *     duration = 5000  // 5 seconds
 * )
 * ```
 * 
 * @author Weelo Team
 * @version 1.0.0
 * =============================================================================
 */
object MarkerAnimationHelper {

    // Default animation duration (matches backend update interval)
    private const val DEFAULT_DURATION_MS = 5000L
    
    // Minimum duration to prevent too-fast animations
    private const val MIN_DURATION_MS = 1000L
    
    // Maximum duration to prevent too-slow animations
    private const val MAX_DURATION_MS = 10000L

    // Active animators (to cancel if new update comes)
    private val activeAnimators = mutableMapOf<Marker, ValueAnimator>()

    /**
     * ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
     * ┃  ANIMATE MARKER - The Core Function                                   ┃
     * ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
     * 
     * Smoothly animates a marker from its current position to a new position.
     * 
     * INTERPOLATION MATH:
     * newPosition = startPosition + (endPosition - startPosition) × progress
     * 
     * progress goes from 0.0 → 1.0 over the duration
     * 
     * @param marker The Google Maps marker to animate
     * @param toPosition The destination LatLng
     * @param duration Animation duration in milliseconds (default: 5000ms)
     * @param onComplete Callback when animation finishes
     */
    fun animateMarker(
        marker: Marker,
        toPosition: LatLng,
        duration: Long = DEFAULT_DURATION_MS,
        onComplete: (() -> Unit)? = null
    ) {
        val startPosition = marker.position
        
        // Cancel any existing animation for this marker
        cancelAnimation(marker)
        
        // Create animator from 0 to 1
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
            interpolator = LinearInterpolator()
            
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                
                // Linear interpolation: start + (end - start) * progress
                val lat = startPosition.latitude + 
                    (toPosition.latitude - startPosition.latitude) * progress
                val lng = startPosition.longitude + 
                    (toPosition.longitude - startPosition.longitude) * progress
                
                marker.position = LatLng(lat, lng)
            }
            
            // Cleanup on end
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    activeAnimators.remove(marker)
                    onComplete?.invoke()
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    activeAnimators.remove(marker)
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }
        
        // Track and start
        activeAnimators[marker] = animator
        animator.start()
    }

    /**
     * Animate marker with rotation (bearing)
     * 
     * Makes the truck icon face the direction of travel.
     * More realistic for vehicle tracking.
     * 
     * @param marker The marker to animate
     * @param toPosition Destination position
     * @param toBearing New bearing/rotation in degrees (0-360)
     * @param duration Animation duration in milliseconds
     */
    fun animateMarkerWithRotation(
        marker: Marker,
        toPosition: LatLng,
        toBearing: Float,
        duration: Long = DEFAULT_DURATION_MS,
        onComplete: (() -> Unit)? = null
    ) {
        val startPosition = marker.position
        val startBearing = marker.rotation
        
        // Cancel existing animation
        cancelAnimation(marker)
        
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
            interpolator = LinearInterpolator()
            
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                
                // Interpolate position
                val lat = startPosition.latitude + 
                    (toPosition.latitude - startPosition.latitude) * progress
                val lng = startPosition.longitude + 
                    (toPosition.longitude - startPosition.longitude) * progress
                
                // Interpolate bearing (handle 360° wraparound)
                val bearing = interpolateBearing(startBearing, toBearing, progress)
                
                marker.position = LatLng(lat, lng)
                marker.rotation = bearing
            }
            
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    activeAnimators.remove(marker)
                    onComplete?.invoke()
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    activeAnimators.remove(marker)
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }
        
        activeAnimators[marker] = animator
        animator.start()
    }

    /**
     * Animate camera to follow a position smoothly
     * 
     * Use this to keep the driver centered on screen.
     * 
     * @param map GoogleMap instance
     * @param position Position to move camera to
     * @param zoom Zoom level (default: 16)
     * @param duration Animation duration
     */
    fun animateCameraToFollow(
        map: GoogleMap,
        position: LatLng,
        zoom: Float = 16f,
        duration: Int = 1000
    ) {
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(position, zoom),
            duration,
            null
        )
    }

    /**
     * Calculate optimal animation duration based on speed and distance
     * 
     * SMART DURATION:
     * - Fast driver (highway) → Shorter animation
     * - Slow driver (traffic) → Longer animation
     * - Stationary → No animation needed
     * 
     * @param speedMetersPerSec Driver's speed in m/s
     * @param distanceMeters Distance between points in meters
     * @param updateIntervalMs Backend update interval (default: 5000ms)
     * @return Recommended animation duration in milliseconds
     */
    fun calculateOptimalDuration(
        speedMetersPerSec: Float,
        distanceMeters: Float,
        updateIntervalMs: Long = DEFAULT_DURATION_MS
    ): Long {
        // If stationary or very slow, use default
        if (speedMetersPerSec < 0.5f) {
            return DEFAULT_DURATION_MS
        }
        
        // Calculate time it should take to cover the distance at current speed
        val expectedTimeMs = (distanceMeters / speedMetersPerSec * 1000).toLong()
        
        // Clamp to reasonable bounds
        return expectedTimeMs.coerceIn(MIN_DURATION_MS, updateIntervalMs)
    }

    /**
     * Calculate distance between two LatLng points using Haversine formula
     * 
     * @param from Starting point
     * @param to Ending point
     * @return Distance in meters
     */
    fun calculateDistance(from: LatLng, to: LatLng): Float {
        val earthRadius = 6371000.0 // meters
        
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLng = Math.toRadians(to.longitude - from.longitude)
        
        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(deltaLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return (earthRadius * c).toFloat()
    }

    /**
     * Calculate bearing (direction) between two points
     * 
     * @param from Starting point
     * @param to Ending point
     * @return Bearing in degrees (0-360)
     */
    fun calculateBearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLng = Math.toRadians(to.longitude - from.longitude)
        
        val y = sin(deltaLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng)
        
        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        
        // Normalize to 0-360
        if (bearing < 0) {
            bearing += 360f
        }
        
        return bearing
    }

    /**
     * Interpolate between two bearing values
     * Handles the 360° wraparound correctly
     * 
     * Example: 350° to 10° should go through 0°, not backwards through 180°
     */
    private fun interpolateBearing(from: Float, to: Float, progress: Float): Float {
        var delta = to - from
        
        // Take the shorter path around the circle
        if (delta > 180) {
            delta -= 360
        } else if (delta < -180) {
            delta += 360
        }
        
        var result = from + delta * progress
        
        // Normalize to 0-360
        if (result < 0) {
            result += 360
        } else if (result >= 360) {
            result -= 360
        }
        
        return result
    }

    /**
     * Cancel any running animation for a marker
     * 
     * Call this when:
     * - A new location update arrives before previous animation finishes
     * - User navigates away from tracking screen
     */
    fun cancelAnimation(marker: Marker) {
        activeAnimators[marker]?.cancel()
        activeAnimators.remove(marker)
    }

    /**
     * Cancel all running animations
     * 
     * Call this when leaving the tracking screen
     */
    fun cancelAllAnimations() {
        activeAnimators.values.forEach { it.cancel() }
        activeAnimators.clear()
    }

    /**
     * Check if marker has an active animation
     */
    fun isAnimating(marker: Marker): Boolean {
        return activeAnimators[marker]?.isRunning == true
    }
}
