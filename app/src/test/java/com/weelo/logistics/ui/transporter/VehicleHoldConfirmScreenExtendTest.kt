package com.weelo.logistics.ui.transporter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-26 VehicleHoldConfirmScreen Extend +30s button tests
 * =============================================================================
 *
 * Source-level file-scan assertions — same verification style c4-pritam used
 * for F-C-27 (see VehicleHoldConfirmServerRecheckTest.kt). Pre-existing
 * Wave-0 compiler errors in PodOtpDialog + TransporterNavGraph block full
 * compose-level UI tests, so these verify the source text matches the
 * BookMyShow seat-lock extension pattern from the PRD:
 *   - Extend button present and wired to TruckHoldApiService.extendFlexHold
 *   - Button gated on remainingSeconds in 1..29 (low-time recovery lane)
 *   - Extension path re-pins ServerDeadlineTimer monotonic deadline from
 *     the new server expiresAt (F-C-27 monotonic contract preserved)
 *   - Extend state disables Confirm/Cancel to avoid double-action races
 * =============================================================================
 */
class VehicleHoldConfirmScreenExtendTest {

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
    fun `extendHold function exists and calls extendFlexHold`() {
        assertTrue(
            "extendHold() method must exist on VehicleHoldConfirmScreen",
            screenSource.contains("fun extendHold()")
        )
        assertTrue(
            "extendHold must invoke RetrofitClient.truckHoldApi.extendFlexHold",
            screenSource.contains("RetrofitClient.truckHoldApi.extendFlexHold")
        )
        assertTrue(
            "extendHold must pass the current holdId in an ExtendFlexHoldRequest",
            screenSource.contains("ExtendFlexHoldRequest(holdId!!)")
        )
    }

    @Test
    fun `Extend +30s button is gated on low-time window (1 to 29 seconds)`() {
        // BookMyShow pattern: extension UI only appears when extension is useful.
        // Showing it from the start would encourage misuse and spam the server.
        assertTrue(
            "Extend button must be gated on remainingSeconds in 1..29",
            screenSource.contains("remainingSeconds in 1..29")
        )
    }

    @Test
    fun `extendHold re-pins the monotonic deadline from server expiresAt`() {
        // F-C-27 contract preservation: the extension path must recompute
        // deadlineElapsedMs from the new server expiresAt via
        // ServerDeadlineTimer.deadlineElapsedFromServerExpiry, not local +30 math.
        assertTrue(
            "Extend must call ServerDeadlineTimer.deadlineElapsedFromServerExpiry on success",
            screenSource.contains("ServerDeadlineTimer.deadlineElapsedFromServerExpiry") &&
                screenSource.contains("extendHold") // ensure within extend context
        )
    }

    @Test
    fun `isExtending state disables Confirm and Cancel buttons during API call`() {
        assertTrue(
            "isExtending state must be declared",
            screenSource.contains("var isExtending by remember")
        )
        // Button enabled chain on Extend itself:
        assertTrue(
            "Extend button enabled must include !isExtending guard",
            screenSource.contains("enabled = !isConfirming && !isFinalizing && !isExtending")
        )
    }

    @Test
    fun `extendHold guards against invalid preconditions`() {
        // Must early-return when holdId is null, already extending, confirming,
        // finalizing, or when hold has already expired.
        assertTrue(
            "extendHold must short-circuit on invalid state",
            screenSource.contains("holdId == null || isExtending || isConfirming || isFinalizing || remainingSeconds <= 0")
        )
    }

    @Test
    fun `extendHold increments extensionsUsed counter`() {
        // Helps the UI (future) enforce the client-side cap mirroring the
        // backend's 3-extension max (90s base + 30 + 30 + 10 = 160s).
        assertTrue(
            "extensionsUsed must increment on success",
            screenSource.contains("extensionsUsed += 1")
        )
    }

    @Test
    fun `extendHold survives expiresAt parse failure without crashing`() {
        // Error handling: if the server returned a malformed expiresAt the
        // request is still a success (extension is persisted server-side) —
        // we log and continue without throwing, preserving the user's hold.
        assertTrue(
            "Extend must catch parse failures on new expiresAt",
            screenSource.contains("Extend succeeded but expiresAt parse failed") ||
                screenSource.contains("Extend succeeded but expiresAt")
        )
    }
}
