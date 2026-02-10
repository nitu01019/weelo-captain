package com.weelo.logistics.core.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * =============================================================================
 * CIRCUIT BREAKER PATTERN - Resilient Network Calls
 * =============================================================================
 * 
 * Implements the Circuit Breaker pattern to prevent cascading failures
 * when the backend is experiencing issues.
 * 
 * STATES:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Too many failures, requests fail fast without calling backend
 * - HALF_OPEN: Testing if backend has recovered
 * 
 * SCALABILITY BENEFITS:
 * - Prevents server overload during partial outages
 * - Reduces user wait time for known-failing endpoints
 * - Allows graceful degradation
 * - Automatic recovery when backend is healthy
 * 
 * FOR BACKEND DEVELOPERS:
 * - If circuit opens frequently, investigate server issues
 * - Circuit trips after 5 consecutive failures (configurable)
 * - Auto-resets after 30 seconds (configurable)
 * 
 * USAGE:
 * ```kotlin
 * val result = circuitBreaker.execute {
 *     apiService.makeCall()
 * }
 * when (result) {
 *     is CircuitBreakerResult.Success -> // handle success
 *     is CircuitBreakerResult.Failure -> // handle failure
 *     is CircuitBreakerResult.CircuitOpen -> // show "Service temporarily unavailable"
 * }
 * ```
 * =============================================================================
 */
class CircuitBreaker(
    private val name: String,
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 30_000,
    private val halfOpenMaxCalls: Int = 3
) {
    companion object {
        private const val TAG = "CircuitBreaker"
    }
    
    // Circuit states
    enum class State {
        CLOSED,     // Normal operation
        OPEN,       // Failing fast
        HALF_OPEN   // Testing recovery
    }
    
    // Current state
    @Volatile
    private var state: State = State.CLOSED
    
    // Failure tracking
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val halfOpenCallCount = AtomicInteger(0)
    
    // Mutex for state transitions
    private val stateMutex = Mutex()
    
    /**
     * Execute a call through the circuit breaker
     */
    suspend fun <T> execute(block: suspend () -> T): CircuitBreakerResult<T> {
        return when (state) {
            State.OPEN -> {
                // Check if reset timeout has passed
                if (shouldAttemptReset()) {
                    stateMutex.withLock {
                        if (state == State.OPEN && shouldAttemptReset()) {
                            transitionToHalfOpen()
                        }
                    }
                    if (state == State.HALF_OPEN) {
                        executeCall(block)
                    } else {
                        CircuitBreakerResult.CircuitOpen(
                            "Service temporarily unavailable. Please try again in ${getRemainingResetTime() / 1000} seconds."
                        )
                    }
                } else {
                    timber.log.Timber.d("[$name] Circuit OPEN - failing fast")
                    CircuitBreakerResult.CircuitOpen(
                        "Service temporarily unavailable. Please try again in ${getRemainingResetTime() / 1000} seconds."
                    )
                }
            }
            
            State.HALF_OPEN -> {
                if (halfOpenCallCount.get() < halfOpenMaxCalls) {
                    halfOpenCallCount.incrementAndGet()
                    executeCall(block)
                } else {
                    CircuitBreakerResult.CircuitOpen(
                        "Service recovering. Please wait..."
                    )
                }
            }
            
            State.CLOSED -> {
                executeCall(block)
            }
        }
    }
    
    /**
     * Execute the actual call and handle result
     */
    private suspend fun <T> executeCall(block: suspend () -> T): CircuitBreakerResult<T> {
        return try {
            val result = block()
            onSuccess()
            CircuitBreakerResult.Success(result)
        } catch (e: Exception) {
            onFailure(e)
            CircuitBreakerResult.Failure(e)
        }
    }
    
    /**
     * Handle successful call
     */
    private suspend fun onSuccess() {
        when (state) {
            State.HALF_OPEN -> {
                successCount.incrementAndGet()
                if (successCount.get() >= halfOpenMaxCalls) {
                    stateMutex.withLock {
                        if (state == State.HALF_OPEN) {
                            transitionToClosed()
                        }
                    }
                }
            }
            State.CLOSED -> {
                // Reset failure count on success
                failureCount.set(0)
            }
            else -> {}
        }
    }
    
    /**
     * Handle failed call
     */
    private suspend fun onFailure(e: Exception) {
        lastFailureTime.set(System.currentTimeMillis())
        
        when (state) {
            State.HALF_OPEN -> {
                stateMutex.withLock {
                    if (state == State.HALF_OPEN) {
                        transitionToOpen()
                    }
                }
            }
            State.CLOSED -> {
                val failures = failureCount.incrementAndGet()
                timber.log.Timber.w("[$name] Failure #$failures: ${e.message}")
                
                if (failures >= failureThreshold) {
                    stateMutex.withLock {
                        if (state == State.CLOSED && failureCount.get() >= failureThreshold) {
                            transitionToOpen()
                        }
                    }
                }
            }
            else -> {}
        }
    }
    
    /**
     * Check if enough time has passed to attempt reset
     */
    private fun shouldAttemptReset(): Boolean {
        return System.currentTimeMillis() - lastFailureTime.get() >= resetTimeoutMs
    }
    
    /**
     * Get remaining time until reset attempt
     */
    private fun getRemainingResetTime(): Long {
        val elapsed = System.currentTimeMillis() - lastFailureTime.get()
        return maxOf(0, resetTimeoutMs - elapsed)
    }
    
    // State transitions
    
    private fun transitionToOpen() {
        state = State.OPEN
        timber.log.Timber.w("[$name] Circuit OPENED after $failureThreshold failures")
    }
    
    private fun transitionToHalfOpen() {
        state = State.HALF_OPEN
        halfOpenCallCount.set(0)
        successCount.set(0)
        timber.log.Timber.i("[$name] Circuit HALF-OPEN - testing recovery")
    }
    
    private fun transitionToClosed() {
        state = State.CLOSED
        failureCount.set(0)
        successCount.set(0)
        halfOpenCallCount.set(0)
        timber.log.Timber.i("[$name] Circuit CLOSED - backend recovered")
    }
    
    /**
     * Force reset the circuit (for testing or manual intervention)
     */
    fun reset() {
        state = State.CLOSED
        failureCount.set(0)
        successCount.set(0)
        halfOpenCallCount.set(0)
        lastFailureTime.set(0)
        timber.log.Timber.i("[$name] Circuit manually reset")
    }
    
    /**
     * Get current circuit state
     */
    fun getState(): State = state
    
    /**
     * Get current failure count
     */
    fun getFailureCount(): Int = failureCount.get()
}

/**
 * Result wrapper for circuit breaker calls
 */
sealed class CircuitBreakerResult<out T> {
    data class Success<T>(val data: T) : CircuitBreakerResult<T>()
    data class Failure(val exception: Exception) : CircuitBreakerResult<Nothing>()
    data class CircuitOpen(val message: String) : CircuitBreakerResult<Nothing>()
    
    /**
     * Map success value
     */
    inline fun <R> map(transform: (T) -> R): CircuitBreakerResult<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Failure -> this
            is CircuitOpen -> this
        }
    }
    
    /**
     * Get value or null
     */
    fun getOrNull(): T? = (this as? Success)?.data
    
    /**
     * Get value or throw
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw exception
        is CircuitOpen -> throw IllegalStateException(message)
    }
}

/**
 * =============================================================================
 * GLOBAL CIRCUIT BREAKERS
 * =============================================================================
 * 
 * Pre-configured circuit breakers for different API endpoints.
 * Group related endpoints to prevent one failing endpoint from
 * affecting others.
 */
object CircuitBreakers {
    // Auth endpoints - separate circuit
    val auth = CircuitBreaker(
        name = "auth",
        failureThreshold = 3,
        resetTimeoutMs = 60_000  // 1 minute (auth failures are serious)
    )
    
    // Vehicle endpoints
    val vehicles = CircuitBreaker(
        name = "vehicles",
        failureThreshold = 5,
        resetTimeoutMs = 30_000
    )
    
    // Driver endpoints
    val drivers = CircuitBreaker(
        name = "drivers",
        failureThreshold = 5,
        resetTimeoutMs = 30_000
    )
    
    // Booking/Broadcast endpoints (critical)
    val bookings = CircuitBreaker(
        name = "bookings",
        failureThreshold = 3,
        resetTimeoutMs = 15_000  // Faster reset for critical path
    )
    
    // Trip/Tracking endpoints
    val trips = CircuitBreaker(
        name = "trips",
        failureThreshold = 5,
        resetTimeoutMs = 30_000
    )
    
    /**
     * Reset all circuit breakers
     */
    fun resetAll() {
        auth.reset()
        vehicles.reset()
        drivers.reset()
        bookings.reset()
        trips.reset()
    }
}
