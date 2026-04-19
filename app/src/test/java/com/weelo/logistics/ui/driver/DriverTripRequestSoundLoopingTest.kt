package com.weelo.logistics.ui.driver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-33 DriverTripRequestSoundService looping-alarm tests
 * =============================================================================
 *
 * Source-level file-scan guard. Before this fix the trip-request alarm was a
 * one-shot: `isLooping = false` on MediaPlayer, and vibration `repeat = -1`
 * (which is Android's "play once" sentinel — counter-intuitive but
 * documented). The result: a 45-second driver-decision window with 1 second
 * of sound at the start. Drivers missed alerts from the kitchen or while
 * holding the phone inverted in the dock.
 *
 * The fix:
 *   - `isLooping = true` on the MediaPlayer
 *   - vibration `repeat = 0` (loop from the start of the pattern)
 *   - the Overlay ties lifecycle to a `DisposableEffect(Unit) { onDispose { stop() } }`
 *     so dismissal or a swipe terminates the alarm immediately.
 *
 * Both code paths remain behind `BuildConfig.FF_DRIVER_ALARM_LOOPING` (default
 * OFF) so the old one-shot behavior is still reachable via flag-flip.
 * =============================================================================
 */
class DriverTripRequestSoundLoopingTest {

    private val soundFile = File(
        "src/main/java/com/weelo/logistics/ui/driver/DriverTripRequestSoundService.kt"
    )

    private val overlayFile = File(
        "src/main/java/com/weelo/logistics/ui/driver/DriverTripRequestOverlay.kt"
    )

    private val soundSource: String by lazy {
        require(soundFile.exists()) {
            "Sound service file not found at ${soundFile.absolutePath}. Test must run with cwd=app/."
        }
        soundFile.readText()
    }

    private val overlaySource: String by lazy {
        require(overlayFile.exists()) { "Overlay file missing. Test must run with cwd=app/." }
        overlayFile.readText()
    }

    @Test
    fun `FF_DRIVER_ALARM_LOOPING feature flag is referenced`() {
        assertTrue(
            "F-C-33: sound service must branch on BuildConfig.FF_DRIVER_ALARM_LOOPING",
            soundSource.contains("FF_DRIVER_ALARM_LOOPING")
        )
    }

    @Test
    fun `looping-alarm path sets isLooping true`() {
        // Regex tolerates either direct assignment `isLooping = true` OR
        // conditional `isLooping = LOOP_ENABLED`; both must evaluate to true
        // when the FF is on. We assert the literal `isLooping = true` appears
        // inside the sound file — the conditional switch wraps it.
        assertTrue(
            "F-C-33: isLooping = true must be set under FF_DRIVER_ALARM_LOOPING",
            soundSource.contains(Regex("""isLooping\s*=\s*true"""))
        )
    }

    @Test
    fun `legacy non-looping path is still reachable for flag-off rollback`() {
        // The pre-existing `isLooping = false` line remains so a runtime
        // toggle can revert without reverting the whole file. The file should
        // contain both states (true under FF, false otherwise).
        assertTrue(
            "F-C-33: rollback path `isLooping = false` must still exist for flag-off",
            soundSource.contains(Regex("""isLooping\s*=\s*false"""))
        )
    }

    @Test
    fun `vibration repeat index switches from minus1 to 0 under FF`() {
        // Android VibrationEffect.createWaveform(pattern, repeat):
        //   repeat = -1 -> play pattern once
        //   repeat = 0  -> loop from index 0 (start of pattern)
        //
        // Under FF_DRIVER_ALARM_LOOPING we must use `0` so the pattern loops.
        // Accept either `createWaveform(pattern, 0)`, a named constant, or a
        // computed local variable switched on the FF.
        val hasLoopIndex = soundSource.contains(
            Regex("""createWaveform\s*\(\s*pattern\s*,\s*(0|VIBRATION_LOOP_INDEX|repeatIndex)\s*\)""")
        )
        // And the named loop constant must be defined so we do not have a
        // naked magic number wandering through the source.
        val hasNamedConstant = soundSource.contains(
            Regex("""VIBRATION_LOOP_INDEX\s*=\s*0""")
        )
        assertTrue(
            "F-C-33: createWaveform must pass a repeat index that evaluates to " +
                "0 under the looping FF (direct 0, VIBRATION_LOOP_INDEX, or " +
                "computed repeatIndex).",
            hasLoopIndex
        )
        assertTrue(
            "F-C-33: VIBRATION_LOOP_INDEX named constant must be defined = 0",
            hasNamedConstant
        )
    }

    @Test
    fun `overlay ties alarm lifecycle to a DisposableEffect`() {
        // The overlay must tear the alarm down deterministically when the
        // composable leaves the tree — otherwise a dismissed overlay with a
        // looping alarm would keep playing.
        assertTrue(
            "F-C-33: DriverTripRequestOverlay must register a DisposableEffect " +
                "that calls DriverTripRequestSoundService.stop() on dispose.",
            overlaySource.contains("DisposableEffect") &&
                overlaySource.contains("DriverTripRequestSoundService.stop")
        )
    }

    @Test
    fun `sound service stop releases media player`() {
        // Verifies the stop path exists and is wired to releaseMediaPlayer()
        // — the DisposableEffect contract depends on this.
        assertTrue(
            "F-C-33: sound service stop() must release the MediaPlayer",
            soundSource.contains(Regex("""fun\s+stop\s*\(\s*\)""")) &&
                soundSource.contains("releaseMediaPlayer")
        )
    }
}
