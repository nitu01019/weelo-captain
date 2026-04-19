package com.weelo.logistics.ui.transporter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-47 DriverAssignmentScreen — proactive driver eviction tests
 * =============================================================================
 *
 * Source-level file-scan guard. Before this fix:
 *
 *   DriverAssignmentScreen.kt:263 registered a socket listener on
 *   `SocketIOService.driverStatusChanged` that updated the in-row chip colour
 *   when a driver flipped to offline — BUT it did NOT remove the driver from
 *   `driverAssignments`. The transporter could still press "Send to Drivers"
 *   with an offline driver in the payload; the backend would reject the
 *   assignment, and the UI showed no recovery path because `showDriverPicker`
 *   was never re-opened.
 *
 * The fix:
 *   1. On `!isOnline` for a driver that is assigned to a vehicle in the
 *      current map, remove that entry: `driverAssignments - vehicleId`.
 *
 *   2. Surface a `DriverEjectionBanner` composable (inline or extracted)
 *      that tells the transporter which driver went offline and prompts them
 *      to re-open the picker for the affected vehicle.
 *
 *   3. Emit a `driverEjectedMidAssignment` analytics event so we can track
 *      how often drivers go offline during the critical assignment window.
 *
 *   4. All behind `BuildConfig.FF_PROACTIVE_DRIVER_EVICTION` (default OFF).
 * =============================================================================
 */
class DriverEvictionTest {

    private val screenFile = File(
        "src/main/java/com/weelo/logistics/ui/transporter/DriverAssignmentScreen.kt"
    )

    private val screenSource: String by lazy {
        require(screenFile.exists()) {
            "Screen file not found at ${screenFile.absolutePath}. Test must run with cwd=app/."
        }
        screenFile.readText()
    }

    @Test
    fun `screen references FF_PROACTIVE_DRIVER_EVICTION feature flag`() {
        assertTrue(
            "F-C-47: Screen must branch on BuildConfig.FF_PROACTIVE_DRIVER_EVICTION",
            screenSource.contains("FF_PROACTIVE_DRIVER_EVICTION")
        )
    }

    @Test
    fun `offline driver is evicted from driverAssignments map`() {
        // Look for a pattern like `driverAssignments = driverAssignments - vehicleId`
        // or `driverAssignments.filterKeys { ... }` — the semantic is the
        // same: remove the vehicle->driver entry when the driver goes offline.
        val hasEviction = screenSource.contains(Regex("""driverAssignments\s*=\s*driverAssignments\s*-\s*\S+""")) ||
            screenSource.contains(Regex("""driverAssignments\s*=\s*driverAssignments\.filterKeys""")) ||
            screenSource.contains(Regex("""driverAssignments\s*-\s*vehicleId"""))
        assertTrue(
            "F-C-47: Screen must evict the vehicle->driver entry when the " +
                "assigned driver goes offline. Look for `driverAssignments - vehicleId`.",
            hasEviction
        )
    }

    @Test
    fun `DriverEjectionBanner is rendered when eviction occurs`() {
        assertTrue(
            "F-C-47: Screen must surface a DriverEjectionBanner composable " +
                "to tell the transporter which driver went offline.",
            screenSource.contains("DriverEjectionBanner")
        )
    }

    @Test
    fun `driverEjectedMidAssignment analytics event is emitted`() {
        // Grep-friendly event name — the analytics layer filters on this
        // string. It must appear verbatim so dashboards can count it.
        assertTrue(
            "F-C-47: Screen must emit analytics event `driverEjectedMidAssignment` " +
                "so we can track driver-offline frequency during the assignment window.",
            screenSource.contains("driverEjectedMidAssignment")
        )
    }

    @Test
    fun `eviction only triggers on isOnline false for assigned drivers`() {
        // We must NOT evict when the driver flips to online, and we must NOT
        // evict when the driver is not currently in driverAssignments. The
        // guard pattern is `!event.isOnline` + membership check on the map.
        assertTrue(
            "F-C-47: Eviction must be guarded by `!event.isOnline`",
            screenSource.contains(Regex("""!event\.isOnline"""))
        )
        assertTrue(
            "F-C-47: Eviction must check `driverAssignments.values.contains` membership",
            screenSource.contains("driverAssignments.values.contains")
        )
    }
}
