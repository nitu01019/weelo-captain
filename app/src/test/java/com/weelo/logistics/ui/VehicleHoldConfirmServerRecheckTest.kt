package com.weelo.logistics.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-27 VehicleHoldConfirmScreen source-level verification
 * =============================================================================
 *
 * Pure JUnit4 file-scan test following the existing RenamesDarkModeLocalizationTest
 * pattern. We cannot spin up the full Compose runtime in a unit test, so we verify
 * the load-bearing properties at the source level:
 *
 *  1. VehicleHoldConfirmScreen imports `ServerDeadlineTimer` and `SystemClock` —
 *     i.e., the doze-safe pattern is wired in.
 *  2. The Confirm button uses the tighter `remainingSeconds > 2` grace gate
 *     (not `> 0`). This is BookMyShow's grace-buffer pattern so the client
 *     cannot confirm inside the 0-2 second drift window where the server has
 *     already expired the hold.
 *  3. DriverAssignmentScreen and DriverTripRequestOverlay also wire the helper
 *     — all three sites must migrate together; partial adoption defeats the fix.
 *  4. No local-decrement pattern (`remainingSeconds--` inside a `while` loop)
 *     remains in any of the three files — that was the bug, it must be gone.
 * =============================================================================
 */
class VehicleHoldConfirmServerRecheckTest {

    companion object {
        private const val APP_SRC = "/Users/nitishbhardwaj/Desktop/weelo captain/app/src/main/java/com/weelo/logistics"
        private val vehicleHoldConfirm = File("$APP_SRC/ui/transporter/VehicleHoldConfirmScreen.kt")
        private val driverAssignment = File("$APP_SRC/ui/transporter/DriverAssignmentScreen.kt")
        private val driverOverlay = File("$APP_SRC/ui/driver/DriverTripRequestOverlay.kt")
        private val helper = File("$APP_SRC/ui/ServerDeadlineTimer.kt")
    }

    @Test
    fun `ServerDeadlineTimer helper file exists`() {
        assertTrue(
            "F-C-27 helper must exist at ${helper.absolutePath}",
            helper.exists()
        )
    }

    @Test
    fun `VehicleHoldConfirmScreen imports SystemClock`() {
        val src = vehicleHoldConfirm.readText()
        assertTrue(
            "Must import android.os.SystemClock for doze-safe countdown",
            src.contains("import android.os.SystemClock")
        )
    }

    @Test
    fun `VehicleHoldConfirmScreen imports ServerDeadlineTimer`() {
        val src = vehicleHoldConfirm.readText()
        assertTrue(
            "Must import the shared ServerDeadlineTimer helper",
            src.contains("com.weelo.logistics.ui.ServerDeadlineTimer")
        )
    }

    @Test
    fun `VehicleHoldConfirmScreen uses deadlineElapsedFromServerExpiry`() {
        val src = vehicleHoldConfirm.readText()
        assertTrue(
            "Must pin a monotonic deadline from server expiresAt",
            src.contains("deadlineElapsedFromServerExpiry")
        )
    }

    @Test
    fun `VehicleHoldConfirmScreen uses remainingSecondsFromDeadline on every tick`() {
        val src = vehicleHoldConfirm.readText()
        assertTrue(
            "Must recompute remaining from deadline each tick",
            src.contains("remainingSecondsFromDeadline")
        )
    }

    @Test
    fun `VehicleHoldConfirmScreen has no local-decrement tick`() {
        val src = vehicleHoldConfirm.readText()
        assertFalse(
            "Old local-decrement pattern `remainingSeconds--` must be gone (F-C-27)",
            src.contains("remainingSeconds--")
        )
    }

    @Test
    fun `VehicleHoldConfirmScreen Confirm button uses grace buffer gate`() {
        val src = vehicleHoldConfirm.readText()
        // BookMyShow grace-buffer: client cannot confirm inside the 0-2s drift window.
        assertTrue(
            "Confirm button must gate on remainingSeconds > 2 (not > 0)",
            src.contains("remainingSeconds > 2")
        )
        assertFalse(
            "Old `remainingSeconds > 0` gate on Confirm button must be removed",
            src.contains("enabled = !isConfirming && !isFinalizing && remainingSeconds > 0")
        )
    }

    @Test
    fun `VehicleHoldConfirmScreen confirmHold guard matches button gate`() {
        val src = vehicleHoldConfirm.readText()
        // Defensive guard inside confirmHold() must match the button's tighter gate
        // so a stale nav event or race can't bypass the UI gate.
        assertTrue(
            "confirmHold() must early-return when remainingSeconds <= 2",
            src.contains("remainingSeconds <= 2")
        )
    }

    @Test
    fun `DriverAssignmentScreen imports SystemClock`() {
        val src = driverAssignment.readText()
        assertTrue(src.contains("import android.os.SystemClock"))
    }

    @Test
    fun `DriverAssignmentScreen uses ServerDeadlineTimer`() {
        val src = driverAssignment.readText()
        assertTrue(src.contains("ServerDeadlineTimer"))
        assertTrue(src.contains("deadlineElapsedFromServerExpiry"))
        assertTrue(src.contains("remainingSecondsFromDeadline"))
    }

    @Test
    fun `DriverAssignmentScreen has no local-decrement tick`() {
        val src = driverAssignment.readText()
        assertFalse(
            "Old `remaining--` or `holdRemainingSeconds--` pattern must be gone (F-C-27)",
            src.contains("remaining--") || src.contains("holdRemainingSeconds--")
        )
    }

    @Test
    fun `DriverTripRequestOverlay imports SystemClock`() {
        val src = driverOverlay.readText()
        assertTrue(src.contains("import android.os.SystemClock"))
    }

    @Test
    fun `DriverTripRequestOverlay uses ServerDeadlineTimer`() {
        val src = driverOverlay.readText()
        assertTrue(src.contains("ServerDeadlineTimer"))
        assertTrue(src.contains("remainingSecondsFromDeadline"))
    }

    @Test
    fun `DriverTripRequestOverlay has no local-decrement tick`() {
        val src = driverOverlay.readText()
        assertFalse(
            "Old `remainingSeconds--` inside timer loop must be gone (F-C-27)",
            src.contains("remainingSeconds--")
        )
    }
}
