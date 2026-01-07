package com.weelo.logistics.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rate Limiter Utility
 * 
 * Prevents abuse by limiting API calls per user/IP
 * Implements token bucket algorithm
 * 
 * SECURITY: Protects against DoS attacks and abuse
 * 
 * Usage:
 * ```
 * val limiter = RateLimiter.forOTP()
 * if (limiter.tryAcquire(phoneNumber)) {
 *     // Proceed with OTP request
 * } else {
 *     // Show "Too many requests" error
 * }
 * ```
 */
class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long
) {
    private val requestMap = mutableMapOf<String, RequestInfo>()
    private val mutex = Mutex()
    
    data class RequestInfo(
        var count: Int,
        var windowStart: Long
    )
    
    suspend fun tryAcquire(identifier: String): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        val info = requestMap[identifier]
        
        return if (info == null) {
            // First request
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
                // Within window
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
    
    suspend fun getRemainingRequests(identifier: String): Int = mutex.withLock {
        val now = System.currentTimeMillis()
        val info = requestMap[identifier] ?: return maxRequests
        
        return if (now - info.windowStart > windowMs) {
            maxRequests
        } else {
            maxOf(0, maxRequests - info.count)
        }
    }
    
    suspend fun getTimeUntilReset(identifier: String): Long = mutex.withLock {
        val now = System.currentTimeMillis()
        val info = requestMap[identifier] ?: return 0L
        
        val elapsed = now - info.windowStart
        return if (elapsed > windowMs) {
            0L
        } else {
            windowMs - elapsed
        }
    }
    
    suspend fun reset(identifier: String) = mutex.withLock {
        requestMap.remove(identifier)
    }
    
    companion object {
        // Predefined rate limiters for common use cases
        
        // OTP: 3 requests per 5 minutes per phone number
        fun forOTP() = RateLimiter(
            maxRequests = 3,
            windowMs = 5 * 60 * 1000 // 5 minutes
        )
        
        // Login attempts: 5 attempts per 15 minutes per phone
        fun forLogin() = RateLimiter(
            maxRequests = 5,
            windowMs = 15 * 60 * 1000 // 15 minutes
        )
        
        // API calls: 100 requests per minute per user
        fun forAPI() = RateLimiter(
            maxRequests = 100,
            windowMs = 60 * 1000 // 1 minute
        )
        
        // Trip creation: 10 trips per hour per transporter
        fun forTripCreation() = RateLimiter(
            maxRequests = 10,
            windowMs = 60 * 60 * 1000 // 1 hour
        )
        
        // Broadcast: 5 broadcasts per hour per transporter
        fun forBroadcast() = RateLimiter(
            maxRequests = 5,
            windowMs = 60 * 60 * 1000 // 1 hour
        )
    }
}

/**
 * Global rate limiter instances
 * BACKEND: Replace with Redis-based rate limiting for production
 */
object GlobalRateLimiters {
    val otp = RateLimiter.forOTP()
    val login = RateLimiter.forLogin()
    val api = RateLimiter.forAPI()
    val tripCreation = RateLimiter.forTripCreation()
    val broadcast = RateLimiter.forBroadcast()
}
