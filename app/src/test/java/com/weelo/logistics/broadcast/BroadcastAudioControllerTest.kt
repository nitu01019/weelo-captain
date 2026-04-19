package com.weelo.logistics.broadcast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-06 / W5 — BroadcastAudioController lifecycle tests
 * =============================================================================
 *
 * Source-scan assertions (same pattern as HoldFallbackRemovedTest,
 * BroadcastListScreenTimerTest). Pre-existing Wave-0 compiler errors block the
 * Robolectric/MockK flavor of this test (SOL-7 §S-06 sketches it), so we lock
 * in the contract via source scans — the accepted captain-app verification
 * approach documented in CLAUDE.md.
 *
 * What these tests lock in (RED until the fix lands):
 *   - New class BroadcastAudioController exists at broadcast/BroadcastAudioController.kt
 *   - It is an app-scoped singleton (object or init(application)) initialized
 *     from WeeloApp.onCreate
 *   - It subscribes to BroadcastFlowCoordinator.currentBroadcast AND
 *     BroadcastFlowCoordinator.events (filtered on Dismissed)
 *   - It stops audio synchronously (`stopImmediate`) on null/Dismissed so the
 *     150ms AnimatedVisibility exit animation no longer races the stop call
 *   - WeeloApp.onCreate initializes the controller (flag-gated)
 *   - BroadcastOverlayScreen no longer owns a DisposableEffect that calls
 *     soundService.playLoopingSound + onDispose { soundService.stopSound() }
 *     (OR the DisposableEffect is gated behind !BuildConfig.FF_BROADCAST_AUDIO_CONTROLLER)
 *   - BuildConfig field FF_BROADCAST_AUDIO_CONTROLLER (default false) in build.gradle.kts
 * =============================================================================
 */
class BroadcastAudioControllerTest {

    private val controllerFile = File(
        "src/main/java/com/weelo/logistics/broadcast/BroadcastAudioController.kt"
    )
    private val overlayScreenFile = File(
        "src/main/java/com/weelo/logistics/broadcast/BroadcastOverlayScreen.kt"
    )
    private val appFile = File("src/main/java/com/weelo/logistics/WeeloApp.kt")
    private val buildGradleFile = File("build.gradle.kts")

    private val controllerSource: String by lazy {
        require(controllerFile.exists()) {
            "BroadcastAudioController.kt not found at ${controllerFile.absolutePath}. " +
                "Test must run with cwd=app/."
        }
        controllerFile.readText()
    }

    private val overlaySource: String by lazy {
        require(overlayScreenFile.exists()) {
            "BroadcastOverlayScreen.kt not found at ${overlayScreenFile.absolutePath}. " +
                "Test must run with cwd=app/."
        }
        overlayScreenFile.readText()
    }

    private val appSource: String by lazy {
        require(appFile.exists()) {
            "WeeloApp.kt not found at ${appFile.absolutePath}. Test must run with cwd=app/."
        }
        appFile.readText()
    }

    private val buildGradleSource: String by lazy {
        require(buildGradleFile.exists()) {
            "build.gradle.kts not found at ${buildGradleFile.absolutePath}. Test must run with cwd=app/."
        }
        buildGradleFile.readText()
    }

    // ------------------------------------------------------------------
    // (1) Controller class must exist
    // ------------------------------------------------------------------

    @Test
    fun `BroadcastAudioController class file exists`() {
        assertTrue(
            "BroadcastAudioController.kt must exist at broadcast/BroadcastAudioController.kt (F-C-06)",
            controllerFile.exists()
        )
    }

    @Test
    fun `BroadcastAudioController lives in the broadcast package`() {
        assertTrue(
            "BroadcastAudioController must be in package com.weelo.logistics.broadcast",
            controllerSource.contains("package com.weelo.logistics.broadcast")
        )
    }

    @Test
    fun `BroadcastAudioController is an app-scoped singleton`() {
        // Accept either `object BroadcastAudioController` (Kotlin-native
        // singleton) or a `class BroadcastAudioController` with an
        // `init(Application)` entry point — both match SOL-7 §S-06.
        val isObject = Regex("""object\s+BroadcastAudioController\b""").containsMatchIn(controllerSource)
        val hasInitApp = Regex(
            """fun\s+init\s*\(\s*\w+\s*:\s*(?:android\.app\.)?Application\s*\)"""
        ).containsMatchIn(controllerSource)
        assertTrue(
            "BroadcastAudioController must be app-scoped: `object` or class with `init(Application)`",
            isObject || hasInitApp
        )
    }

    // ------------------------------------------------------------------
    // (2) Must subscribe to coordinator current broadcast + Dismissed events
    // ------------------------------------------------------------------

    @Test
    fun `controller observes BroadcastFlowCoordinator currentBroadcast`() {
        assertTrue(
            "Controller must observe BroadcastFlowCoordinator.currentBroadcast " +
                "(single-owner audio lifecycle)",
            controllerSource.contains("BroadcastFlowCoordinator") &&
                controllerSource.contains("currentBroadcast")
        )
    }

    @Test
    fun `controller observes coordinator Dismissed events`() {
        // Per SOL-7 §S-06: subscribe to `events.filterIsInstance<Dismissed>()`
        // so the stop fires synchronously at the logical-dismiss edge, not
        // after the 150ms AnimatedVisibility exit.
        val filtersDismissed =
            controllerSource.contains("filterIsInstance<BroadcastCoordinatorEvent.Dismissed>") ||
                controllerSource.contains("filterIsInstance<Dismissed>") ||
                controllerSource.contains("BroadcastCoordinatorEvent.Dismissed")
        assertTrue(
            "Controller must observe BroadcastCoordinatorEvent.Dismissed (synchronous stop on dismiss)",
            filtersDismissed
        )
    }

    @Test
    fun `controller exposes a synchronous stopImmediate path`() {
        // The fix guarantee is "stop within 10ms, not after 150ms animation
        // exit". The controller must expose a direct stop call — not a
        // DisposableEffect onDispose.
        val hasStopImmediate = Regex(
            """fun\s+stopImmediate\s*\("""
        ).containsMatchIn(controllerSource)
        assertTrue(
            "Controller must expose fun stopImmediate() for synchronous stop (SOL-7 §S-06)",
            hasStopImmediate
        )
    }

    // ------------------------------------------------------------------
    // (3) WeeloApp.onCreate must initialize the controller (flag-gated)
    // ------------------------------------------------------------------

    @Test
    fun `WeeloApp onCreate initializes BroadcastAudioController`() {
        assertTrue(
            "WeeloApp.onCreate must initialize BroadcastAudioController (app-scoped lifecycle)",
            appSource.contains("BroadcastAudioController")
        )
    }

    @Test
    fun `controller init is gated behind FF_BROADCAST_AUDIO_CONTROLLER flag`() {
        // Must be a conscious opt-in per the flag policy. Legacy DisposableEffect
        // path stays live under the negated branch so rollback is zero-risk.
        val gated = Regex(
            """BuildConfig\.FF_BROADCAST_AUDIO_CONTROLLER"""
        ).containsMatchIn(appSource)
        assertTrue(
            "WeeloApp must gate BroadcastAudioController init behind BuildConfig.FF_BROADCAST_AUDIO_CONTROLLER",
            gated
        )
    }

    // ------------------------------------------------------------------
    // (4) BroadcastOverlayScreen legacy DisposableEffect must be flag-gated off
    // ------------------------------------------------------------------

    @Test
    fun `BroadcastOverlayScreen DisposableEffect is guarded by FF_BROADCAST_AUDIO_CONTROLLER`() {
        // The legacy `DisposableEffect(currentBroadcast?.broadcastId) { onDispose { soundService.stopSound() } }`
        // must only run when the flag is OFF, so the controller can take over
        // without running audio twice when the flag is ON.
        val overlayReferencesFlag = overlaySource.contains("FF_BROADCAST_AUDIO_CONTROLLER")
        assertTrue(
            "BroadcastOverlayScreen must reference BuildConfig.FF_BROADCAST_AUDIO_CONTROLLER " +
                "to gate the legacy DisposableEffect audio path",
            overlayReferencesFlag
        )
    }

    @Test
    fun `BroadcastOverlayScreen does not run soundService unconditionally in DisposableEffect`() {
        // Regression armor: the specific racy pattern
        //   DisposableEffect(currentBroadcast?.broadcastId) {
        //       currentBroadcast?.let { soundService.playLoopingSound(...) }
        //       onDispose { soundService.stopSound() }
        //   }
        // must be inside a flag branch, not top-level.
        //
        // We detect the pattern by looking at the region around the first
        // DisposableEffect(currentBroadcast?.broadcastId) and asserting the
        // immediate surrounding control flow references the flag.
        val marker = "DisposableEffect(currentBroadcast?.broadcastId)"
        val idx = overlaySource.indexOf(marker)
        if (idx < 0) {
            // DisposableEffect fully removed → best-case; fix even stronger.
            return
        }
        val windowStart = (idx - 400).coerceAtLeast(0)
        val windowEnd = (idx + marker.length + 400).coerceAtMost(overlaySource.length)
        val window = overlaySource.substring(windowStart, windowEnd)
        assertTrue(
            "DisposableEffect(currentBroadcast?.broadcastId) must sit inside a " +
                "BuildConfig.FF_BROADCAST_AUDIO_CONTROLLER-gated branch",
            window.contains("FF_BROADCAST_AUDIO_CONTROLLER")
        )
    }

    // ------------------------------------------------------------------
    // (5) BuildConfig field registration
    // ------------------------------------------------------------------

    @Test
    fun `build gradle registers FF_BROADCAST_AUDIO_CONTROLLER BuildConfig field`() {
        val fieldDecl = Regex(
            """buildConfigField\s*\(\s*"boolean"\s*,\s*"FF_BROADCAST_AUDIO_CONTROLLER"\s*,\s*"(true|false)"\s*\)"""
        )
        assertTrue(
            "app/build.gradle.kts must declare a boolean BuildConfig field named FF_BROADCAST_AUDIO_CONTROLLER",
            fieldDecl.containsMatchIn(buildGradleSource)
        )
    }

    @Test
    fun `FF_BROADCAST_AUDIO_CONTROLLER defaults to false in build gradle`() {
        val defaultFalse = Regex(
            """buildConfigField\s*\(\s*"boolean"\s*,\s*"FF_BROADCAST_AUDIO_CONTROLLER"\s*,\s*"false"\s*\)"""
        )
        assertTrue(
            "FF_BROADCAST_AUDIO_CONTROLLER must default to false (Phase-9 flag policy)",
            defaultFalse.containsMatchIn(buildGradleSource)
        )
    }
}
