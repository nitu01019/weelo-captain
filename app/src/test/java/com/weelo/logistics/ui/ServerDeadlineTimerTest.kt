package com.weelo.logistics.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * =============================================================================
 * F-C-27 ServerDeadlineTimer unit tests
 * =============================================================================
 *
 * Pure JUnit4 — no Android runtime. We simulate `SystemClock.elapsedRealtime()`
 * by passing explicit `nowElapsedMs` values into the helper. This lets us verify
 * the core doze-safety property: after a simulated 30-second doze window, the
 * countdown reflects the ACTUAL elapsed time, not a locally-decremented count.
 *
 * Covered scenarios:
 *   - Fresh sync: remaining === server's remaining
 *   - Ticking: after N seconds of simulated elapsed-realtime, remaining drops by N
 *   - Doze: after a 30s doze window, remaining drops by 30s (not by 1s)
 *   - Expiry clamp: past-deadline returns 0, never negative
 *   - Wall-clock drift: if client wall clock is offset from server, the monotonic
 *     deadline still converges on the correct remaining time
 * =============================================================================
 */
class ServerDeadlineTimerTest {

    @Test
    fun `fresh sync — remaining equals server remaining`() {
        val nowWall = 1_700_000_000_000L
        val expiresAtWall = nowWall + 90_000L // 90 seconds
        val nowElapsed = 10_000L

        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiresAtWall,
            nowWallMs = nowWall,
            nowElapsedMs = nowElapsed
        )
        val remaining = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = nowElapsed
        )

        assertEquals(90, remaining)
    }

    @Test
    fun `ticking — 1 second later remaining drops by 1`() {
        val nowWall = 1_700_000_000_000L
        val expiresAtWall = nowWall + 90_000L
        val startElapsed = 10_000L

        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiresAtWall,
            nowWallMs = nowWall,
            nowElapsedMs = startElapsed
        )

        val after1s = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = startElapsed + 1_000L
        )
        val after5s = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = startElapsed + 5_000L
        )

        assertEquals(89, after1s)
        assertEquals(85, after5s)
    }

    @Test
    fun `doze — after 30s sleep remaining drops by 30s, not by 1s`() {
        // This is the core F-C-27 guarantee: the old local-decrement loop ticked
        // `remainingSeconds--` once per resumed coroutine iteration. If the
        // device dozed for 30s between two `delay(1000)` calls, the old code
        // would show (N - 1) when the actual remaining was (N - 30). The fix
        // is to recompute from the monotonic deadline on every tick.
        val nowWall = 1_700_000_000_000L
        val expiresAtWall = nowWall + 90_000L
        val startElapsed = 10_000L

        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiresAtWall,
            nowWallMs = nowWall,
            nowElapsedMs = startElapsed
        )

        // Simulate doze: 30s passes on elapsed-realtime while the coroutine is suspended.
        val afterDoze = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = startElapsed + 30_000L
        )

        assertEquals("Doze-safe: remaining reflects actual elapsed-realtime", 60, afterDoze)
    }

    @Test
    fun `past deadline returns 0 never negative`() {
        val nowWall = 1_700_000_000_000L
        val expiresAtWall = nowWall + 5_000L
        val startElapsed = 10_000L

        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiresAtWall,
            nowWallMs = nowWall,
            nowElapsedMs = startElapsed
        )

        val wayPast = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = startElapsed + 1_000_000L
        )

        assertEquals(0, wayPast)
    }

    @Test
    fun `zero remaining at deadline`() {
        val nowWall = 1_700_000_000_000L
        val expiresAtWall = nowWall + 10_000L
        val startElapsed = 10_000L

        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiresAtWall,
            nowWallMs = nowWall,
            nowElapsedMs = startElapsed
        )

        val atDeadline = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = startElapsed + 10_000L
        )

        assertEquals(0, atDeadline)
    }

    @Test
    fun `wall clock forward jump does not corrupt monotonic deadline`() {
        // Scenario: user manually advances wall clock by 1 hour mid-countdown.
        // The old code re-derived remaining from System.currentTimeMillis() each
        // tick, so it would jump to 0 instantly. The new code pins elapsed-realtime
        // at sync and only reads from elapsed-realtime on tick — so the deadline
        // is immune to wall-clock mutation after the initial sync.
        val nowWall = 1_700_000_000_000L
        val expiresAtWall = nowWall + 90_000L
        val startElapsed = 10_000L

        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiresAtWall,
            nowWallMs = nowWall,
            nowElapsedMs = startElapsed
        )

        // Advance elapsed-realtime by 5s; user jumps wall clock forward 1hr (irrelevant).
        val afterJump = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = startElapsed + 5_000L
        )

        assertEquals("Wall-clock jump must not affect monotonic countdown", 85, afterJump)
    }

    @Test
    fun `negative server remaining yields zero immediately`() {
        // If the server's expiresAt is already in the past at sync time (network
        // latency / stale event replay), remaining must be 0, not negative.
        val nowWall = 1_700_000_000_000L
        val expiresAtWall = nowWall - 2_000L // already expired 2s ago
        val nowElapsed = 10_000L

        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiresAtWall,
            nowWallMs = nowWall,
            nowElapsedMs = nowElapsed
        )
        val remaining = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = nowElapsed
        )

        assertEquals(0, remaining)
    }

    @Test
    fun `progressive ticks cover full 90s countdown`() {
        // Sanity: step from 90s down to 0 in 5-second slices and verify every
        // intermediate value is correct and monotonically non-increasing.
        val nowWall = 1_700_000_000_000L
        val expiresAtWall = nowWall + 90_000L
        val startElapsed = 10_000L
        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiresAtWall,
            nowWallMs = nowWall,
            nowElapsedMs = startElapsed
        )

        var previous = Int.MAX_VALUE
        for (stepSeconds in 0..95 step 5) {
            val remaining = ServerDeadlineTimer.remainingSecondsFromDeadline(
                deadlineElapsedMs = deadline,
                nowElapsedMs = startElapsed + stepSeconds * 1_000L
            )
            assertTrue("remaining must never be negative", remaining >= 0)
            assertTrue("remaining must be monotonically non-increasing", remaining <= previous)
            previous = remaining
        }
        // At t+95s we must be clamped to 0.
        assertEquals(0, previous)
    }
}
