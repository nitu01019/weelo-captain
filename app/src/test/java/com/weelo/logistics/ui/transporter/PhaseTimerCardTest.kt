package com.weelo.logistics.ui.transporter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-34 PhaseTimerCard — FLEX/CONFIRMED/EXPIRED/UNKNOWN phase branching
 * =============================================================================
 *
 * Source-level file-scan guard. Before this fix, DriverAssignmentScreen had a
 * single countdown block (lines 103-153 and 340-376) that rendered the same
 * UI regardless of backend hold phase — so a transporter in Phase 2
 * (CONFIRMED) saw the same "extend +30s" chip as Phase 1 (FLEX), even though
 * the extension API only applies to FLEX. No phase chip, no Extend button
 * gated on `canExtend`.
 *
 * The fix introduces `PhaseTimerCard(phase, remaining, total, canExtend, onExtend)`
 * that branches on the HoldPhase enum:
 *   FLEX      -> green pill + Extend +30s button (gated on canExtend)
 *   CONFIRMED -> blue pill + "Drivers responding" subtitle (NO extend)
 *   EXPIRED   -> red pill + "Hold expired" (terminal)
 *   UNKNOWN   -> graceful fallback to LegacyCountdownBlock (prior behavior)
 *              so a backend rollout of a new phase value never crashes the UI
 *              (F-C-78 UNKNOWN-sentinel contract is preserved).
 *
 * The path is behind `BuildConfig.FF_PHASE_AWARE_TIMER` (default OFF). While
 * OFF the Screen must still render the LegacyCountdownBlock so this is a
 * non-breaking diff.
 * =============================================================================
 */
class PhaseTimerCardTest {

    private val screenFile = File(
        "src/main/java/com/weelo/logistics/ui/transporter/DriverAssignmentScreen.kt"
    )

    private val screenSource: String by lazy {
        require(screenFile.exists()) {
            "Screen file not found at ${screenFile.absolutePath}. Test must run with cwd=app/."
        }
        screenFile.readText()
    }

    // Either the card is inlined in the Screen file OR extracted to a sibling
    // file. We look for it in both places.
    private val siblingCardFile = File(
        "src/main/java/com/weelo/logistics/ui/transporter/PhaseTimerCard.kt"
    )

    private val cardSource: String by lazy {
        if (siblingCardFile.exists()) siblingCardFile.readText() else screenSource
    }

    @Test
    fun `PhaseTimerCard composable is declared with required signature`() {
        // The composable signature defined in the task spec:
        //   PhaseTimerCard(phase, remaining, total, canExtend, onExtend)
        //
        // We accept `HoldPhase` as the phase type. Signature order is
        // documented in INDEX.md; we regex on the presence of the named
        // parameters.
        assertTrue(
            "F-C-34: PhaseTimerCard composable must exist",
            cardSource.contains(Regex("""fun\s+PhaseTimerCard\s*\("""))
        )
        val requiredParams = listOf("phase", "remaining", "total", "canExtend", "onExtend")
        requiredParams.forEach { param ->
            assertTrue(
                "F-C-34: PhaseTimerCard must accept parameter `$param`",
                cardSource.contains(Regex("""\b$param\s*:"""))
            )
        }
    }

    @Test
    fun `PhaseTimerCard branches across all four HoldPhase values`() {
        // Verify that the card handles each phase — FLEX, CONFIRMED, EXPIRED,
        // UNKNOWN — via either when/switch branches on HoldPhase.
        val phases = listOf("FLEX", "CONFIRMED", "EXPIRED", "UNKNOWN")
        phases.forEach { phase ->
            assertTrue(
                "F-C-34: PhaseTimerCard must handle HoldPhase.$phase",
                cardSource.contains("HoldPhase.$phase")
            )
        }
    }

    @Test
    fun `Extend plus30s button is gated on canExtend AND FLEX phase`() {
        // Industry invariant: the Extend API only applies to FLEX. The button
        // must not render (or must be disabled) in CONFIRMED/EXPIRED/UNKNOWN.
        assertTrue(
            "F-C-34: Extend button must be gated on canExtend",
            cardSource.contains("canExtend")
        )
        // The button is only reachable when the phase is FLEX. We accept any
        // of the standard Kotlin conjunction shapes:
        //   if (phase == HoldPhase.FLEX && canExtend) { ... }
        //   HoldPhase.FLEX -> if (canExtend) ...
        //   when (phase) { HoldPhase.FLEX -> { ... canExtend ... } }
        val hasFlexGateWithAnd = cardSource.contains(
            Regex("""phase\s*==\s*HoldPhase\.FLEX\s*&&\s*canExtend""")
        )
        val hasFlexArrowWithExtend = cardSource.contains(
            Regex("""(?s)HoldPhase\.FLEX\s*->.{0,400}?canExtend""")
        )
        assertTrue(
            "F-C-34: Extend button must live in a guard that requires FLEX + canExtend. " +
                "Matched patterns: and-expr=$hasFlexGateWithAnd, arrow-branch=$hasFlexArrowWithExtend",
            hasFlexGateWithAnd || hasFlexArrowWithExtend
        )
    }

    @Test
    fun `UNKNOWN phase falls back to LegacyCountdownBlock`() {
        // F-C-78 UNKNOWN-sentinel contract: a backend rollout of a new phase
        // (e.g. AUTO_RELEASED) must not crash the UI. We render the legacy
        // countdown instead so the user still has feedback.
        assertTrue(
            "F-C-34: UNKNOWN phase must fall back to LegacyCountdownBlock",
            cardSource.contains("LegacyCountdownBlock")
        )
    }

    @Test
    fun `phase-aware timer path gated behind FF_PHASE_AWARE_TIMER`() {
        assertTrue(
            "F-C-34: phase-aware timer must reference BuildConfig.FF_PHASE_AWARE_TIMER",
            screenSource.contains("FF_PHASE_AWARE_TIMER")
        )
    }

    @Test
    fun `HoldPhase enum import is present`() {
        assertTrue(
            "F-C-34: Screen must import com.weelo.logistics.data.model.HoldPhase",
            screenSource.contains("com.weelo.logistics.data.model.HoldPhase") ||
                cardSource.contains("com.weelo.logistics.data.model.HoldPhase")
        )
    }
}
