package com.weelo.logistics.ui.driver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-31 + F-C-32 DriverTripRequestOverlay timer stability tests
 * =============================================================================
 *
 * Source-level file-scan guards matching the style used by
 * HoldFallbackRemovedTest / VehicleHoldConfirmDeadVmGuardTest. Pre-existing
 * Wave-0 compiler errors in peer files prevent a Compose runtime test here.
 *
 * Invariants being locked in (Phase 9 / t2-driver-assignment):
 *
 *   F-C-31 — `totalSeconds` (the denominator of the progress ring) must come
 *   from the STABLE DTO field `notification.driverAcceptTimeoutSeconds`
 *   (falling back to `BuildConfig.DRIVER_ACCEPT_TIMEOUT_SECONDS`) and must be
 *   remembered keyed on `assignmentId`, not `Unit`. Before this fix the code
 *   used `remember { notification.remainingSeconds }` which captured the
 *   LIVE getter — meaning the denominator drifted as time passed, producing
 *   an inverted progress ring that moved the wrong direction.
 *
 *   F-C-32 — The countdown `LaunchedEffect` used a 3-key trigger
 *   (`assignmentId, isProcessing, isSwipeComplete`) which caused churn: every
 *   time a gate flipped the coroutine restarted, sometimes races the
 *   time-up auto-decline firing twice. The fix is a single-key effect
 *   (`assignmentId`) plus `rememberUpdatedState` for gates and a
 *   `NonCancellable` block around the decline call so cancellation during
 *   swipe cannot abort the network request.
 * =============================================================================
 */
class DriverTripRequestOverlayTimerTest {

    private val overlayFile = File(
        "src/main/java/com/weelo/logistics/ui/driver/DriverTripRequestOverlay.kt"
    )

    private val overlaySource: String by lazy {
        require(overlayFile.exists()) {
            "Overlay file not found at ${overlayFile.absolutePath}. Test must run with cwd=app/."
        }
        overlayFile.readText()
    }

    // -----------------------------------------------------------------------
    // F-C-31 totalSeconds from stable DTO
    // -----------------------------------------------------------------------

    @Test
    fun `totalSeconds must be remembered keyed on assignmentId not Unit`() {
        // The old code was `remember { notification.remainingSeconds }` — no
        // key at all. Remembering by assignmentId makes the denominator stable
        // per-assignment (invalidates when a new trip arrives).
        assertTrue(
            "F-C-31: totalSeconds must use `remember(assignmentId)` keyed on " +
                "the DTO assignmentId so it is stable per-request.",
            overlaySource.contains(Regex("""remember\s*\(\s*[^\)]*assignmentId[^\)]*\)"""))
        )
    }

    @Test
    fun `totalSeconds must prefer DTO driverAcceptTimeoutSeconds then BuildConfig`() {
        // F-C-31 fix: denominator must be the stable `driverAcceptTimeoutSeconds`
        // payload field (server-authoritative). Fallback is BuildConfig
        // (the backend-mirrored default, not a local hardcode).
        assertTrue(
            "F-C-31: Overlay must read notification.driverAcceptTimeoutSeconds",
            overlaySource.contains("notification.driverAcceptTimeoutSeconds")
        )
        assertTrue(
            "F-C-31: Overlay must fall back to BuildConfig.DRIVER_ACCEPT_TIMEOUT_SECONDS",
            overlaySource.contains("BuildConfig.DRIVER_ACCEPT_TIMEOUT_SECONDS")
        )
    }

    @Test
    fun `overlay is gated behind FF_DRIVER_TOTAL_SECONDS_FROM_DTO`() {
        // The new stable-DTO path is behind a default-OFF feature flag so the
        // legacy getter-based denominator remains until the flag is flipped.
        assertTrue(
            "F-C-31: Overlay must reference BuildConfig.FF_DRIVER_TOTAL_SECONDS_FROM_DTO",
            overlaySource.contains("FF_DRIVER_TOTAL_SECONDS_FROM_DTO")
        )
    }

    // -----------------------------------------------------------------------
    // F-C-32 single-key LaunchedEffect
    // -----------------------------------------------------------------------

    @Test
    fun `countdown LaunchedEffect must key on assignmentId only when FF enabled`() {
        // The fix collapses the 3-key trigger to a single key. We allow either
        // the old 3-key effect OR the new 1-key effect to appear in source
        // because the implementation picks between them based on the FF.
        //
        // What we MUST see is the single-key variant introduced by the fix —
        // `LaunchedEffect(notification.assignmentId)` without trailing commas.
        val hasSingleKey = overlaySource.contains(
            Regex("""LaunchedEffect\s*\(\s*notification\.assignmentId\s*\)""")
        )
        assertTrue(
            "F-C-32: Overlay must contain a single-key LaunchedEffect keyed on " +
                "notification.assignmentId (introduced by the stable-key fix).",
            hasSingleKey
        )
    }

    @Test
    fun `gate state must use rememberUpdatedState for churn-free reads`() {
        // With the 1-key effect, we cannot restart on isProcessing changes —
        // but we still need to see the latest value inside the loop. The
        // industry-standard pattern is rememberUpdatedState.
        assertTrue(
            "F-C-32: Overlay must use rememberUpdatedState to read churn-free gates",
            overlaySource.contains("rememberUpdatedState")
        )
    }

    @Test
    fun `decline call on timeout must run under NonCancellable`() {
        // When the coroutine is cancelled mid-decline (e.g., user swipes), the
        // in-flight decline must still complete. Wrapping in NonCancellable is
        // the Google/JetBrains-recommended pattern for network teardown.
        assertTrue(
            "F-C-32: timeout onDecline path must run inside NonCancellable",
            overlaySource.contains("NonCancellable")
        )
    }

    @Test
    fun `timer stable key path gated behind FF_DRIVER_TIMER_STABLE_KEY`() {
        assertTrue(
            "F-C-32: Overlay must reference BuildConfig.FF_DRIVER_TIMER_STABLE_KEY",
            overlaySource.contains("FF_DRIVER_TIMER_STABLE_KEY")
        )
    }
}
