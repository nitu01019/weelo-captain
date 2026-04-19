package com.weelo.logistics.ui.transporter

import com.weelo.logistics.ui.ServerDeadlineTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-27 / W1-3 BroadcastListScreen server-deadline timer migration tests
 * =============================================================================
 *
 * BroadcastListScreen is the 4th timer-bearing screen in the captain app. The
 * other three (VehicleHoldConfirmScreen, DriverAssignmentScreen,
 * DriverTripRequestOverlay) were migrated to `ServerDeadlineTimer` in phase-3
 * commit `a38862c`; this screen still runs a local `remainingSeconds--`
 * decrement loop at line 263 that is NOT doze-safe.
 *
 * Tests split into two kinds — same split the other timer-fix guard tests use:
 *
 *   1. JVM-pure unit tests that drive `ServerDeadlineTimer` with a fake
 *      `elapsedRealtime` clock to prove the helper behaves as the migrated
 *      composable would (fresh sync, 30-second simulated doze, past-deadline
 *      clamp). These are deterministic and run without Robolectric.
 *
 *   2. Source-level file-scan assertions (same style as
 *      `HoldFallbackRemovedTest.kt` and `VehicleHoldConfirmScreenExtendTest.kt`)
 *      to verify the migration actually happened in the composable's source:
 *        - no `remainingSeconds--` decrement remains in the file
 *        - `ServerDeadlineTimer.deadlineElapsedFromServerExpiry(...)` is called
 *          with `broadcast.expiryTime` as the wall-clock expiry
 *        - `ServerDeadlineTimer.remainingSecondsFromDeadline(...)` is called on
 *          every tick (recompute-from-deadline, not local decrement)
 *        - the imports reference `android.os.SystemClock` and the
 *          `ServerDeadlineTimer` helper
 *
 * Pre-existing Wave-0 compiler errors in PodOtpDialog + TransporterNavGraph
 * (documented in CLAUDE.md) block full Compose UI tests, so the source-scan
 * approach is the accepted verification contract for these captain timer
 * migrations.
 * =============================================================================
 */
class BroadcastListScreenTimerTest {

    private val screenFile = File(
        "src/main/java/com/weelo/logistics/ui/transporter/BroadcastListScreen.kt"
    )

    private val screenSource: String by lazy {
        require(screenFile.exists()) {
            "Screen file not found at ${screenFile.absolutePath}. Test must run with cwd=app/."
        }
        screenFile.readText()
    }

    // ------------------------------------------------------------------
    // (1) JVM unit tests — drive ServerDeadlineTimer with a fake clock
    // ------------------------------------------------------------------

    @Test
    fun `broadcast expiry 120s in future yields 120s remaining at sync`() {
        // Mirrors the composable's initial sync: convert broadcast.expiryTime
        // (epoch-ms) into a monotonic elapsed-realtime deadline, then compute
        // remainingSeconds.
        val nowWall = 1_700_000_000_000L
        val expiryMs = nowWall + 120_000L
        val startElapsed = 50_000L

        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiryMs,
            nowWallMs = nowWall,
            nowElapsedMs = startElapsed
        )
        val remaining = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = startElapsed
        )

        assertEquals(
            "Fresh sync: remaining must equal the server-advertised window",
            120,
            remaining
        )
    }

    @Test
    fun `doze simulation — 30s elapsed realtime gap drops remaining by 30s not by 1s`() {
        // Core F-C-27 guarantee: the old `remainingSeconds--` ticked only on
        // every resumed coroutine iteration. During Android doze, the coroutine
        // is suspended, so the old loop would show (N - 1) when the actual
        // remaining was (N - 30). Recomputing from a monotonic deadline fixes
        // this because `SystemClock.elapsedRealtime()` keeps ticking in doze.
        val nowWall = 1_700_000_000_000L
        val expiryMs = nowWall + 120_000L
        val startElapsed = 50_000L

        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiryMs,
            nowWallMs = nowWall,
            nowElapsedMs = startElapsed
        )

        // Simulate 30 seconds of doze: elapsed-realtime advances by 30,000ms
        // while the coroutine was suspended.
        val afterDoze = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = startElapsed + 30_000L
        )

        assertEquals(
            "Doze-safe: 30s wall of sleep must drop remaining by 30s",
            90,
            afterDoze
        )
        // Regression armor: the old decrement would yield 119 here.
        assertFalse(
            "Doze regression: remaining must NOT be off by 29s (local-decrement bug)",
            afterDoze == 119
        )
    }

    @Test
    fun `past broadcast expiry clamps to 0 never negative`() {
        // If a stale broadcast arrives whose expiryTime is already in the past
        // (late socket delivery / replay), remaining must clamp to 0 — matches
        // the existing coerceAtLeast(0) guard that will be preserved by the
        // ServerDeadlineTimer helper's own clamp.
        val nowWall = 1_700_000_000_000L
        val expiryMs = nowWall - 5_000L // expired 5s ago
        val nowElapsed = 50_000L

        val deadline = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiryMs,
            nowWallMs = nowWall,
            nowElapsedMs = nowElapsed
        )
        val remaining = ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadline,
            nowElapsedMs = nowElapsed
        )

        assertEquals(0, remaining)
    }

    // ------------------------------------------------------------------
    // (2) Source-scan assertions — same pattern as HoldFallbackRemovedTest
    // ------------------------------------------------------------------

    @Test
    fun `no local remainingSeconds-- decrement remains in the file`() {
        // The old tick loop at line 263 decremented `remainingSeconds` once per
        // `delay(1000L)` suspend. That loop MUST be fully replaced by a
        // recompute-from-deadline call. Same comment-filter pattern as
        // `HoldFallbackRemovedTest`: occurrences inside `//` or `*` comments
        // are allowed (for landmark/archaeology comments documenting the fix)
        // but any live code line containing the decrement is a regression.
        val lines = screenSource.lines()
        val liveDecrementHits = lines.count { line ->
            val trimmed = line.trim()
            val isComment = trimmed.startsWith("//") ||
                trimmed.startsWith("*") ||
                trimmed.startsWith("/*")
            !isComment && (
                line.contains("remainingSeconds--") ||
                    line.contains(Regex("""remainingSeconds\s*-=\s*1""")) ||
                    line.contains(Regex("""remainingSeconds\s*=\s*remainingSeconds\s*-\s*1"""))
                )
        }
        assertTrue(
            "BroadcastListScreen must not contain a live remainingSeconds decrement (F-C-27 fix incomplete) — found $liveDecrementHits live hit(s)",
            liveDecrementHits == 0
        )
    }

    @Test
    fun `deadline is pinned from broadcast expiryTime via ServerDeadlineTimer`() {
        assertTrue(
            "Composable must call ServerDeadlineTimer.deadlineElapsedFromServerExpiry(...)",
            screenSource.contains("ServerDeadlineTimer.deadlineElapsedFromServerExpiry")
        )
        assertTrue(
            "Deadline sync must use broadcast.expiryTime as the server expiry source",
            screenSource.contains("broadcast.expiryTime")
        )
    }

    @Test
    fun `tick loop recomputes remaining from deadline every frame`() {
        assertTrue(
            "Composable must call ServerDeadlineTimer.remainingSecondsFromDeadline(...) each tick",
            screenSource.contains("ServerDeadlineTimer.remainingSecondsFromDeadline")
        )
    }

    @Test
    fun `source imports the ServerDeadlineTimer helper and SystemClock`() {
        assertTrue(
            "Must import com.weelo.logistics.ui.ServerDeadlineTimer",
            screenSource.contains("import com.weelo.logistics.ui.ServerDeadlineTimer")
        )
        assertTrue(
            "Must import android.os.SystemClock for monotonic elapsed-realtime reads",
            screenSource.contains("import android.os.SystemClock")
        )
    }

    @Test
    fun `tick loop uses SystemClock elapsedRealtime as the monotonic clock source`() {
        // The per-tick recompute must read from elapsedRealtime() — NOT from
        // System.currentTimeMillis() — so a wall-clock jump or NTP correction
        // cannot corrupt the countdown. Same contract as the other 3 screens.
        assertTrue(
            "tick loop must read SystemClock.elapsedRealtime() per frame",
            screenSource.contains("SystemClock.elapsedRealtime()")
        )
    }
}
