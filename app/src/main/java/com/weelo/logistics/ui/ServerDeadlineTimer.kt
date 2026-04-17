package com.weelo.logistics.ui

/**
 * =============================================================================
 * SERVER-DEADLINE COUNTDOWN HELPER (F-C-27)
 * =============================================================================
 *
 * Pattern: server-authoritative absolute deadline + client recompute-every-tick,
 * using `SystemClock.elapsedRealtime()` for doze/sleep resilience.
 *
 * Why `elapsedRealtime` (not `currentTimeMillis`):
 *   - `System.currentTimeMillis()` can jump backward/forward if the device user
 *     or NTP changes the wall clock, causing the countdown to reset or race.
 *   - `SystemClock.elapsedRealtime()` is a monotonic "since boot" clock that
 *     keeps ticking during deep sleep/doze. Android's CountDownTimer is built on it.
 *
 * References:
 *   - Uber ride-offer pattern: server sends `deadline_epoch_ms`; client recomputes
 *     every second — https://medium.com/@narengowda/uber-system-design-8b2bc95e2cfe
 *   - BookMyShow seat-lock: client timer ≤ server hold (grace buffer) —
 *     https://medium.com/@narengowda/bookmyshow-system-design-e268fefb56f5
 *   - Android platform guidance: `SystemClock.elapsedRealtime` for doze-safe
 *     countdowns — https://developer.android.com/reference/android/os/SystemClock
 *
 * USAGE:
 *   val deadline = deadlineElapsedFromServerExpiry(expiresAtWallMs, nowWallMs, nowElapsedMs)
 *   val remaining = remainingSecondsFromDeadline(deadline, nowElapsedMs)
 * =============================================================================
 */
object ServerDeadlineTimer {

    /**
     * Convert a server-sent absolute wall-clock expiry (`expiresAt` epoch-ms)
     * into a monotonic "elapsed-realtime" deadline. The offset between wall
     * clock and elapsed-realtime is captured at this exact moment — subsequent
     * ticks are immune to wall-clock jumps.
     *
     * @param expiresAtWallMs server expiry as epoch-ms (from `Instant.parse(expiresAtStr).toEpochMilli()`)
     * @param nowWallMs       current `System.currentTimeMillis()`
     * @param nowElapsedMs    current `SystemClock.elapsedRealtime()`
     * @return deadline expressed on the monotonic elapsed-realtime axis
     */
    fun deadlineElapsedFromServerExpiry(
        expiresAtWallMs: Long,
        nowWallMs: Long,
        nowElapsedMs: Long
    ): Long {
        val remainingMs = expiresAtWallMs - nowWallMs
        return nowElapsedMs + remainingMs
    }

    /**
     * Recompute remaining seconds from the monotonic deadline. Never negative.
     *
     * @param deadlineElapsedMs value previously returned by [deadlineElapsedFromServerExpiry]
     * @param nowElapsedMs      current `SystemClock.elapsedRealtime()`
     * @return floor seconds remaining, clamped to 0
     */
    fun remainingSecondsFromDeadline(
        deadlineElapsedMs: Long,
        nowElapsedMs: Long
    ): Int {
        val remainingMs = deadlineElapsedMs - nowElapsedMs
        if (remainingMs <= 0L) return 0
        return (remainingMs / 1000L).toInt().coerceAtLeast(0)
    }
}
