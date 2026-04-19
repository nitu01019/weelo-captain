package com.weelo.logistics.ui.transporter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-76 / W1-1 Dead-VM guard test
 * =============================================================================
 *
 * Source-level file-scan guard that mirrors the HoldFallbackRemovedTest style
 * used elsewhere in this package (see HoldFallbackRemovedTest.kt) and the
 * existing evidence pack at .planning/wave1-evidence-pack.md § Section 1.
 *
 * Invariant being locked in (Phase 3 Wave 1 — Agent W1-1):
 *
 *   TruckHoldConfirmViewModel is a "dead ViewModel" — VehicleHoldConfirmScreen
 *   instantiates it (line 83) but never calls any method or reads any field on
 *   it. The Screen migrated to inline ServerDeadlineTimer + direct retrofit
 *   calls during F-C-27 / F-C-76. Per wave1-evidence-pack.md Section 1:
 *
 *     grep -rn "holdViewModel\." app/src/main/  -> ZERO non-comment matches
 *
 *   Therefore: W1-1 (a) deletes TruckHoldConfirmViewModel.kt, (b) deletes the
 *   TruckHoldConfirmViewModelTest.kt that pinned its dead behavior, and (c)
 *   strips the `val holdViewModel = viewModel()` declaration + its import from
 *   VehicleHoldConfirmScreen.kt.
 *
 *   This guard ensures the deletion cannot be silently reverted by a rebase or
 *   a future copy-paste — it fails if either:
 *
 *     1. TruckHoldConfirmViewModel.kt reappears in src/main, OR
 *     2. VehicleHoldConfirmScreen.kt regains a reference to the dead VM, OR
 *     3. The HOLD_DURATION_FALLBACK_SECONDS constant (the 180s fallback that
 *        was 2x the 90s server budget — a BookMyShow anti-pattern) reappears
 *        anywhere in the VM file if the file does still exist.
 *
 * Test must run with cwd=app/ (same convention as HoldFallbackRemovedTest).
 * =============================================================================
 */
class VehicleHoldConfirmDeadVmGuardTest {

    private val vmFile = File(
        "src/main/java/com/weelo/logistics/ui/viewmodel/TruckHoldConfirmViewModel.kt"
    )

    private val screenFile = File(
        "src/main/java/com/weelo/logistics/ui/transporter/VehicleHoldConfirmScreen.kt"
    )

    private val screenSource: String by lazy {
        require(screenFile.exists()) {
            "Screen file not found at ${screenFile.absolutePath}. Test must run with cwd=app/."
        }
        screenFile.readText()
    }

    @Test
    fun `TruckHoldConfirmViewModel source file must not exist`() {
        // Primary invariant: the dead VM file has been deleted.
        assertFalse(
            "TruckHoldConfirmViewModel.kt must be deleted (W1-1 / F-C-76). " +
                "If you need a VM here, re-introduce one with a NEW name and wire " +
                "it to the screen — don't revive this one.",
            vmFile.exists()
        )
    }

    @Test
    fun `HOLD_DURATION_FALLBACK_SECONDS constant must not reappear in the VM location`() {
        // Defense in depth: if someone DOES re-create the file, at minimum the
        // 180s fallback constant must not return — it was the Uber/BookMyShow
        // anti-pattern that made the client time out AFTER the server's 90s
        // budget. See HoldFallbackRemovedTest for the sibling guard on the
        // Screen file.
        if (vmFile.exists()) {
            val source = vmFile.readText()
            assertFalse(
                "HOLD_DURATION_FALLBACK_SECONDS must NOT reappear in " +
                    "TruckHoldConfirmViewModel.kt — it was removed by F-C-76 " +
                    "(BookMyShow fail-closed invariant: client never times out after server).",
                source.contains("HOLD_DURATION_FALLBACK_SECONDS")
            )
        }
    }

    @Test
    fun `VehicleHoldConfirmScreen must not reference the dead ViewModel`() {
        // The screen must not import or instantiate TruckHoldConfirmViewModel.
        // If this test fails, the VM has been silently re-wired — escalate.
        assertFalse(
            "VehicleHoldConfirmScreen must NOT import TruckHoldConfirmViewModel (W1-1).",
            screenSource.contains(
                "import com.weelo.logistics.ui.viewmodel.TruckHoldConfirmViewModel"
            )
        )
        assertFalse(
            "VehicleHoldConfirmScreen must NOT declare `val holdViewModel: TruckHoldConfirmViewModel` (W1-1).",
            screenSource.contains(
                Regex("""val\s+holdViewModel\s*:\s*TruckHoldConfirmViewModel""")
            )
        )
        assertFalse(
            "VehicleHoldConfirmScreen must NOT reference `TruckHoldConfirmViewModel` at all (W1-1).",
            screenSource.contains("TruckHoldConfirmViewModel")
        )
    }

    @Test
    fun `VehicleHoldConfirmScreen must not call any method on a holdViewModel receiver`() {
        // Scan the screen source for any `holdViewModel.<method>(` call pattern.
        // Matches `holdViewModel.foo`, `holdViewModel.uiState`, etc. Used as a
        // secondary guard in case someone reintroduces a VM with the same
        // local-val name but a different class.
        val hits = Regex("""holdViewModel\s*\.""").findAll(screenSource).toList()
        assertTrue(
            "VehicleHoldConfirmScreen must not call any method on a `holdViewModel` " +
                "receiver (found ${hits.size} matches). W1-1 removed this dead plumbing.",
            hits.isEmpty()
        )
    }
}
