package com.weelo.logistics.data.remote

import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

/**
 * =============================================================================
 * RETRY WITH EXPONENTIAL BACKOFF — Industry Standard (Uber/Google)
 * =============================================================================
 *
 * Retries a suspend block on transient network failures with exponential
 * backoff + jitter (prevents thundering herd at scale).
 *
 * WHEN TO USE:
 *  - Critical API calls (accept/decline assignment)
 *  - Idempotent operations only (safe to retry)
 *
 * WHEN NOT TO USE:
 *  - Non-idempotent writes (e.g., creating duplicate records)
 *  - 4xx client errors (retrying won't help)
 *
 * SCALABILITY:
 *  - Jitter prevents all retrying clients from hitting server simultaneously
 *  - Capped max delay prevents infinite waits
 *  - Only retries IOException by default (network errors)
 *
 * PARAMETERS:
 *  @param maxRetries     Max number of retry attempts (default: 3)
 *  @param initialDelayMs First retry delay in ms (default: 1000)
 *  @param maxDelayMs     Cap on delay to prevent excessive waits (default: 8000)
 *  @param factor         Multiplication factor per retry (default: 2.0)
 *  @param retryOn        Predicate — only retry if this returns true for the exception
 *  @param block          The suspend function to execute and potentially retry
 *
 * EXAMPLE:
 *  val result = retryWithBackoff(maxRetries = 3) {
 *      repository.acceptAssignment(assignmentId)
 *  }
 * =============================================================================
 */
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000,
    maxDelayMs: Long = 8000,
    factor: Double = 2.0,
    retryOn: (Exception) -> Boolean = { it is IOException },
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    var lastException: Exception? = null

    repeat(maxRetries + 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            // Never retry cancellation
            if (e is kotlinx.coroutines.CancellationException) throw e

            lastException = e

            if (attempt >= maxRetries || !retryOn(e)) {
                // Max retries exceeded or non-retryable error — propagate
                throw e
            }

            // Add jitter: ±25% of current delay
            val jitter = (currentDelay * 0.25 * (Random.nextDouble() * 2 - 1)).toLong()
            val delayWithJitter = (currentDelay + jitter).coerceAtLeast(0)

            Timber.tag("RetryUtils").w(
                "⚠️ Attempt ${attempt + 1}/$maxRetries failed: ${e.message}. " +
                "Retrying in ${delayWithJitter}ms..."
            )

            delay(delayWithJitter)

            // Exponential increase, capped at maxDelayMs
            currentDelay = min((currentDelay * factor).toLong(), maxDelayMs)
        }
    }

    // Should never reach here, but safety net
    throw lastException ?: IllegalStateException("Retry exhausted without exception")
}
