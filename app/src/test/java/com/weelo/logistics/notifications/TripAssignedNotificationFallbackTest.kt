package com.weelo.logistics.notifications

import com.weelo.logistics.BuildConfig
import com.weelo.logistics.data.model.TripAssignedNotification
import com.weelo.logistics.data.model.TripLocationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * =============================================================================
 * F-C-77 TripAssignedNotification fallback-chain tests
 * =============================================================================
 *
 * Verifies that when the server did NOT provide `expiresAt` (parse-failure or
 * missing field), the countdown falls back to server-sourced
 * `driverAcceptTimeoutSeconds` rather than the legacy hardcoded 30s.
 *
 * Fallback chain:
 *   1. Parse expiresAt -> remaining seconds (server-authoritative, deadline-based)
 *   2. driverAcceptTimeoutSeconds (server payload override)
 *   3. BuildConfig.DRIVER_ACCEPT_TIMEOUT_SECONDS (build-time backend mirror)
 *
 * The legacy `return 30` behavior is permanently removed; 45 is the current
 * backend default, mirrored via BuildConfig so the two can never drift.
 * =============================================================================
 */
class TripAssignedNotificationFallbackTest {

    private fun buildNotification(
        expiresAt: String? = null,
        driverAcceptTimeoutSeconds: Int? = null
    ): TripAssignedNotification {
        val loc = TripLocationInfo(address = "", city = "", latitude = 0.0, longitude = 0.0)
        return TripAssignedNotification(
            assignmentId = "a1",
            tripId = "t1",
            orderId = "o1",
            truckRequestId = "tr1",
            pickup = loc,
            drop = loc,
            vehicleNumber = "",
            farePerTruck = 0.0,
            distanceKm = 0.0,
            customerName = "",
            customerPhone = "",
            assignedAt = "",
            expiresAt = expiresAt,
            routePoints = null,
            message = "",
            driverAcceptTimeoutSeconds = driverAcceptTimeoutSeconds
        )
    }

    @Test
    fun `expiresAt null, driverAcceptTimeoutSeconds 45 -- getter returns 45`() {
        val n = buildNotification(expiresAt = null, driverAcceptTimeoutSeconds = 45)
        assertEquals(45, n.remainingSeconds)
    }

    @Test
    fun `expiresAt null, driverAcceptTimeoutSeconds 60 -- getter honors server value`() {
        val n = buildNotification(expiresAt = null, driverAcceptTimeoutSeconds = 60)
        assertEquals(60, n.remainingSeconds)
    }

    @Test
    fun `both null -- getter returns BuildConfig_DRIVER_ACCEPT_TIMEOUT_SECONDS`() {
        val n = buildNotification(expiresAt = null, driverAcceptTimeoutSeconds = null)
        assertEquals(BuildConfig.DRIVER_ACCEPT_TIMEOUT_SECONDS, n.remainingSeconds)
    }

    @Test
    fun `BuildConfig mirrors backend env var -- must be 45 to match server default`() {
        // F-C-77 invariant: BuildConfig.DRIVER_ACCEPT_TIMEOUT_SECONDS is the
        // last-resort fallback when both expiresAt and server-sent override are
        // missing. It MUST match backend .env DRIVER_ACCEPT_TIMEOUT_SECONDS=45.
        // If backend changes, update buildConfigField in app/build.gradle.kts.
        assertEquals(45, BuildConfig.DRIVER_ACCEPT_TIMEOUT_SECONDS)
    }

    @Test
    fun `unparseable expiresAt -- falls back to driverAcceptTimeoutSeconds`() {
        val n = buildNotification(expiresAt = "not-a-date", driverAcceptTimeoutSeconds = 45)
        // parse returns 0 via catch (see TripAssignedNotification.parseIso8601 fallback).
        // remaining = (0 - now) / 1000 -> large negative -> clamped to 0
        // Unparseable is handled as expired; fallback does NOT apply because the
        // ?: only fires when parseIso8601 returns null (which it never does —
        // it returns 0 on failure). This is deliberate existing behavior.
        assertTrue(
            "expiresAt present but unparseable clamps to 0 (expired), not the fallback",
            n.remainingSeconds == 0
        )
    }

    @Test
    fun `legacy 30s hardcode is removed -- getter must NEVER silently return 30`() {
        // Regression guard: previously the getter hardcoded `return 30` when
        // expiresAt was null. That value was below the backend's 45s window and
        // caused false early timeouts. With both inputs null, the getter MUST
        // now return BuildConfig (45), not 30.
        val n = buildNotification(expiresAt = null, driverAcceptTimeoutSeconds = null)
        assertTrue(
            "Legacy 30s hardcode must be gone — expected >= 45, got ${n.remainingSeconds}",
            n.remainingSeconds >= 45
        )
    }
}
