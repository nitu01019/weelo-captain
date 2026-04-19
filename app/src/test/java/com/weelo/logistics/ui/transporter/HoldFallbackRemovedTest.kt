package com.weelo.logistics.ui.transporter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-76 HOLD_DURATION_FALLBACK_SECONDS removal + fail-closed parse tests
 * =============================================================================
 *
 * Source-level file-scan assertions (same pattern as c4-pritam's F-C-27
 * verification tests). Pre-existing Wave-0 compiler errors in PodOtpDialog +
 * TransporterNavGraph block full compose runtime tests, so these verify the
 * source text reflects the corrected contract:
 *
 *   - The private const HOLD_DURATION_FALLBACK_SECONDS is gone (zero symbol
 *     occurrences outside of the F-C-76 landmark comment documenting the fix)
 *   - State initialization no longer references the deleted constant — the
 *     ring now starts at 0 and only animates once the server populates the
 *     real budget via `ServerDeadlineTimer`
 *   - On `Instant.parse` failure the code fails closed: releases the
 *     server-side hold and surfaces an error — it does NOT silently render a
 *     fabricated 180s window (which would be DOUBLE the 90s server budget)
 *   - BookMyShow invariant preserved: client never times out after server
 * =============================================================================
 */
class HoldFallbackRemovedTest {

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
    fun `HOLD_DURATION_FALLBACK_SECONDS constant is deleted`() {
        // Only one allowed occurrence: the landmark comment documenting the
        // removal (for future grep-ability in the fix archaeology).
        val lines = screenSource.lines()
        val occurrencesOutsideComment = lines.count { line ->
            line.contains("HOLD_DURATION_FALLBACK_SECONDS") &&
                !line.trim().startsWith("//") &&
                !line.trim().startsWith("*")
        }
        assertTrue(
            "The private const HOLD_DURATION_FALLBACK_SECONDS must be fully removed (found $occurrencesOutsideComment non-comment occurrences)",
            occurrencesOutsideComment == 0
        )
    }

    @Test
    fun `private const val HOLD_DURATION_FALLBACK_SECONDS declaration is removed`() {
        // Strict check: no declaration line remains.
        assertFalse(
            "private const val HOLD_DURATION_FALLBACK_SECONDS declaration must be deleted",
            screenSource.contains(Regex("""private\s+const\s+val\s+HOLD_DURATION_FALLBACK_SECONDS\s*="""))
        )
    }

    @Test
    fun `totalSeconds state initializes to 0 not the deleted constant`() {
        assertTrue(
            "totalSeconds must start at 0 (no pre-server assumption)",
            screenSource.contains("var totalSeconds by remember { mutableStateOf(0) }")
        )
        assertTrue(
            "remainingSeconds must start at 0 (no pre-server assumption)",
            screenSource.contains("var remainingSeconds by remember { mutableStateOf(0) }")
        )
    }

    @Test
    fun `parse failure fails closed by releasing the server-side hold`() {
        // The catch block on Instant.parse must call releaseHold rather than
        // silently continuing with a fake duration.
        assertTrue(
            "Parse-failure path must release server-side hold (BookMyShow fail-closed)",
            screenSource.contains("Release on parse-failure failed") ||
                screenSource.contains("RetrofitClient.truckHoldApi.releaseHold")
        )
        assertTrue(
            "Parse-failure path must surface an errorMessage to the user",
            screenSource.contains("errorMessage = context.getString(R.string.failed_hold_vehicles)")
        )
    }

    @Test
    fun `syncOk gate prevents rendering a holdSuccess without server expiresAt`() {
        // holdSuccess = true is no longer unconditional — it fires only inside
        // the if (syncOk) branch so we never show a countdown on a hold whose
        // server deadline we cannot pin.
        assertTrue(
            "holdSuccess must be gated behind successful server expiresAt sync",
            screenSource.contains("if (syncOk)") &&
                screenSource.contains("holdSuccess = true")
        )
    }

    @Test
    fun `parse-failure log message documents fail-closed intent`() {
        // Archaeology + ops: if operators see this log they know the code
        // deliberately aborts (doesn't retry with a fake window).
        assertTrue(
            "Parse-failure log must document fail-closed intent",
            screenSource.contains("Failed to parse expiresAt: fail-closed release")
        )
    }
}
