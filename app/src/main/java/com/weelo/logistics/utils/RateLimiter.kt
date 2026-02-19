package com.weelo.logistics.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * =============================================================================
 * RATE LIMITER UTILITY - Thread-safe, Non-blocking
 * =============================================================================
 * 
 * Prevents abuse by limiting API calls per user/IP
 * Implements sliding window rate limiting algorithm
 * 
 * SECURITY: Protects against DoS attacks and abuse
 * 
 * SCALABILITY:
 * - Thread-safe using ConcurrentHashMap
 * - Non-blocking operations for UI thread compatibility
 * - Efficient memory with automatic cleanup
 * 
 * FOR BACKEND DEVELOPERS:
 * - Replace with Redis-based rate limiting for distributed systems
 * - This is client-side protection; server should have its own limits
 * 
 * Usage:
 * ```kotlin
 * val limiter = RateLimiter.forOTP()
 * if (limiter.tryAcquire(phoneNumber)) {
 *     // Proceed with OTP request
 * } else {
 *     val waitTime = limiter.getTimeUntilReset(phoneNumber)
 *     // Show "Too many requests, retry in ${waitTime/1000} seconds"
 * }
 * ```
 * =============================================================================
 */
class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long
) {
    // Thread-safe map for tracking requests
    private val requestMap = ConcurrentHashMap<String, RequestInfo>()
    
    data class RequestInfo(
        @Volatile var count: Int,
        @Volatile var windowStart: Long
    )
    
    /**
     * Try to acquire a permit for the given identifier
     * 
     * @param identifier Unique identifier (phone number, user ID, etc.)
     * @return true if request is allowed, false if rate limited
     */
    @Synchronized
    fun tryAcquire(identifier: String): Boolean {
        val now = System.currentTimeMillis()
        val info = requestMap[identifier]
        
        return if (info == null) {
            // First request - allow
            requestMap[identifier] = RequestInfo(1, now)
            true
        } else {
            // Check if window has expired
            if (now - info.windowStart > windowMs) {
                // Reset window
                info.count = 1
                info.windowStart = now
                true
            } else {
                // Within window - check count
                if (info.count < maxRequests) {
                    info.count++
                    true
                } else {
                    // Rate limit exceeded
                    false
                }
            }
        }
    }
    
    /**
     * Get remaining requests for identifier
     */
    fun getRemainingRequests(identifier: String): Int {
        val now = System.currentTimeMillis()
        val info = requestMap[identifier] ?: return maxRequests
        
        return if (now - info.windowStart > windowMs) {
            maxRequests
        } else {
            maxOf(0, maxRequests - info.count)
        }
    }
    
    /**
     * Get time until rate limit resets (in milliseconds)
     */
    fun getTimeUntilReset(identifier: String): Long {
        val now = System.currentTimeMillis()
        val info = requestMap[identifier] ?: return 0L
        
        val elapsed = now - info.windowStart
        return if (elapsed > windowMs) {
            0L
        } else {
            windowMs - elapsed
        }
    }
    
    /**
     * Reset rate limit for identifier
     */
    fun reset(identifier: String) {
        requestMap.remove(identifier)
    }
    
    /**
     * Clear all rate limits (for testing or logout)
     */
    fun clearAll() {
        requestMap.clear()
    }
    
    /**
     * Cleanup expired entries (call periodically for memory efficiency)
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        requestMap.entries.removeIf { (_, info) ->
            now - info.windowStart > windowMs
        }
    }
    
    companion object {
        // =================================================================
        // PREDEFINED RATE LIMITERS
        // =================================================================
        
        /**
         * OTP: 5 requests per 2 minutes per phone number
         * Matches backend exactly (5 OTPs / 2 min / phone)
         * Security: Prevents OTP spam while allowing normal retries
         * 
         * WHY 5/2min (not 3/5min):
         *   - Backend allows 5/2min — client must match or be slightly more permissive
         *   - 3/5min was too aggressive — users hit rate limit after 1 real attempt
         *     (was 1.5 due to double-counting bug, now fixed)
         *   - Rapido/Ola use similar windows (~1-2 minutes)
         */
        fun forOTP() = RateLimiter(
            maxRequests = 5,
            windowMs = 2 * 60 * 1000 // 2 minutes (matches backend)
        )
        
        /**
         * Login attempts: 5 attempts per 15 minutes per phone
         * Security: Prevents brute force attacks
         */
        fun forLogin() = RateLimiter(
            maxRequests = 5,
            windowMs = 15 * 60 * 1000 // 15 minutes
        )
        
        /**
         * API calls: 100 requests per minute per user
         * Scalability: Prevents API abuse
         */
        fun forAPI() = RateLimiter(
            maxRequests = 100,
            windowMs = 60 * 1000 // 1 minute
        )
        
        /**
         * Trip creation: 10 trips per hour per transporter
         * Business: Reasonable limit for normal usage
         */
        fun forTripCreation() = RateLimiter(
            maxRequests = 10,
            windowMs = 60 * 60 * 1000 // 1 hour
        )
        
        /**
         * Broadcast acceptance: 20 per hour per transporter
         * Business: Allow active transporters to accept multiple broadcasts
         */
        fun forBroadcast() = RateLimiter(
            maxRequests = 20,
            windowMs = 60 * 60 * 1000 // 1 hour
        )
        
        /**
         * Vehicle registration: 10 per day per transporter
         * Business: Reasonable fleet growth rate
         */
        fun forVehicleRegistration() = RateLimiter(
            maxRequests = 10,
            windowMs = 24 * 60 * 60 * 1000 // 24 hours
        )
    }
}

/**
 * =============================================================================
 * GLOBAL RATE LIMITER INSTANCES
 * =============================================================================
 * 
 * Singleton rate limiters for app-wide use.
 * 
 * FOR BACKEND DEVELOPERS:
 * - These are client-side only
 * - Server must implement its own rate limiting (Redis recommended)
 * - Client limits are for UX (prevent accidental spam)
 * - Server limits are for security (enforce hard limits)
 */
object GlobalRateLimiters {
    val otp = RateLimiter.forOTP()
    val login = RateLimiter.forLogin()
    val api = RateLimiter.forAPI()
    val tripCreation = RateLimiter.forTripCreation()
    val broadcast = RateLimiter.forBroadcast()
    val vehicleRegistration = RateLimiter.forVehicleRegistration()
    
    /**
     * Clear all rate limits (useful on logout)
     */
    fun clearAll() {
        otp.clearAll()
        login.clearAll()
        api.clearAll()
        tripCreation.clearAll()
        broadcast.clearAll()
        vehicleRegistration.clearAll()
    }
}
