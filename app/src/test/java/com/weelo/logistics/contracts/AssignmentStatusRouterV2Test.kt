package com.weelo.logistics.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-51 — assignment_status_changed router consumer guard
 * =============================================================================
 *
 * Source-level file-scan guard. Pre-existing Wave-0 captain baseline compile
 * errors (see P9 t3/t4 reports, SocketEventRouter references missing
 * BroadcastPayloadParser) block runtime tests, so these verify the source text
 * reflects the intended contract migration.
 *
 * Invariants being locked in (Phase 10 / t1-captain-contracts-consumer /
 * F-C-51):
 *
 *   1. `SocketEventRouter.kt` imports and consumes the typed
 *      `AssignmentStatusChangedEvent` from the F-C-52 codegen package (stub
 *      lives at `contracts/GeneratedContracts.kt` until F-C-52 ships).
 *
 *   2. The migration is gated behind `BuildConfig.FF_ASSIGNMENT_STATUS_ROUTER_V2`
 *      (default OFF). Legacy `handleAssignmentStatusChanged` parse path stays
 *      wired so backend-first rollout can flip the server shape while captain
 *      fleet is still mixed-version.
 *
 *   3. The stub event class defines the canonical 10+ field contract that
 *      `assignment.service.ts` emit factories (F-C-63) converge on.
 *
 *   4. `BroadcastPayloadParser` stays referenced as the legacy fallback path
 *      (do NOT delete) — must coexist with the v2 typed parser.
 * =============================================================================
 */
class AssignmentStatusRouterV2Test {

    private val routerFile = File(
        "src/main/java/com/weelo/logistics/data/remote/socket/SocketEventRouter.kt"
    )

    private val contractsFile = File(
        "src/main/java/com/weelo/logistics/contracts/GeneratedContracts.kt"
    )

    private val routerSource: String by lazy {
        require(routerFile.exists()) {
            "Router file not found at ${routerFile.absolutePath}. Test must run with cwd=app/."
        }
        routerFile.readText()
    }

    private val contractsSource: String by lazy {
        require(contractsFile.exists()) {
            "Contracts file not found at ${contractsFile.absolutePath}. Test must run with cwd=app/."
        }
        contractsFile.readText()
    }

    @Test
    fun `contracts stub file exists`() {
        assertTrue(
            "F-C-51/52: Contracts package must exist at com.weelo.logistics.contracts. " +
                "Stub lives at contracts/GeneratedContracts.kt until F-C-52 codegen lands.",
            contractsFile.exists()
        )
    }

    @Test
    fun `AssignmentStatusChangedEvent data class is declared`() {
        assertTrue(
            "F-C-51: data class AssignmentStatusChangedEvent must be declared",
            contractsSource.contains("data class AssignmentStatusChangedEvent")
        )
    }

    @Test
    fun `AssignmentStatusChangedEvent has v2 additive fields`() {
        // F-C-63 — the 3 backend emit variants unify on these 6 additive fields.
        val requiredFields = listOf(
            "orderId", "bookingId", "driverId", "driverName",
            "trucksAccepted", "trucksPending"
        )
        requiredFields.forEach { field ->
            assertTrue(
                "F-C-51/63: AssignmentStatusChangedEvent must declare `$field` (v2 additive)",
                contractsSource.contains(Regex("""val\s+$field\s*:"""))
            )
        }
    }

    @Test
    fun `AssignmentStatusChangedEvent exposes EVENT_NAME constant`() {
        assertTrue(
            "F-C-51: AssignmentStatusChangedEvent.EVENT_NAME must exist so router " +
                "can register listener without duplicating string literals",
            contractsSource.contains(Regex("""EVENT_NAME\s*=\s*"assignment_status_changed""""))
        )
    }

    @Test
    fun `AssignmentStatusChangedEvent has a fromJson factory that fails closed`() {
        // Contract: fromJson returns nullable, NEVER throws. Callers check
        // for null and fall back to legacy parse.
        assertTrue(
            "F-C-51: fromJson must be declared on companion",
            contractsSource.contains(Regex("""fun\s+fromJson\("""))
        )
        assertTrue(
            "F-C-51: fromJson must return nullable AssignmentStatusChangedEvent",
            contractsSource.contains(Regex("""fun\s+fromJson\([^)]+\)\s*:\s*AssignmentStatusChangedEvent\?"""))
        )
    }

    @Test
    fun `router imports AssignmentStatusChangedEvent`() {
        assertTrue(
            "F-C-51: SocketEventRouter must import the typed event from contracts package",
            routerSource.contains("import com.weelo.logistics.contracts.AssignmentStatusChangedEvent")
        )
    }

    @Test
    fun `router references FF_ASSIGNMENT_STATUS_ROUTER_V2 feature flag`() {
        assertTrue(
            "F-C-51: router must branch on BuildConfig.FF_ASSIGNMENT_STATUS_ROUTER_V2",
            routerSource.contains("FF_ASSIGNMENT_STATUS_ROUTER_V2")
        )
    }

    @Test
    fun `router invokes AssignmentStatusChangedEvent fromJson factory`() {
        assertTrue(
            "F-C-51: router must call AssignmentStatusChangedEvent.fromJson on the payload",
            routerSource.contains("AssignmentStatusChangedEvent.fromJson")
        )
    }

    @Test
    fun `router keeps legacy handleAssignmentStatusChanged path for fallback`() {
        // The legacy handler must stay wired so backend-first rollout works:
        // while the flag is OFF or fromJson returns null, captain keeps working.
        assertTrue(
            "F-C-51: legacy handleAssignmentStatusChanged must remain for fallback",
            routerSource.contains("handleAssignmentStatusChanged")
        )
    }

    @Test
    fun `fromJson parses minimal payload with nullable oneof fields unset`() {
        // Functional unit test: stub is simple enough to exercise as a plain
        // Kotlin class (no Android deps).
        val data = org.json.JSONObject().apply {
            put("assignmentId", "a1")
            put("status", "accepted")
        }
        val event = AssignmentStatusChangedEvent.fromJson(data)
        assertNotNull("F-C-51: fromJson must parse minimal payload", event)
        // Missing orderId/bookingId fall through to null (oneof contract).
        assertNull("F-C-51: missing orderId -> null", event?.orderId)
        assertNull("F-C-51: missing bookingId -> null", event?.bookingId)
        assertEquals("a1", event?.assignmentId)
        assertEquals("accepted", event?.status)
    }

    @Test
    fun `fromJson parses full v2 payload with all 10 fields`() {
        val data = org.json.JSONObject().apply {
            put("assignmentId", "a1")
            put("tripId", "t1")
            put("status", "accepted")
            put("vehicleNumber", "MH12AB3456")
            put("message", "Driver accepted")
            put("orderId", "o1")
            put("bookingId", "b1")
            put("driverId", "d1")
            put("driverName", "Alice")
            put("trucksAccepted", 1)
            put("trucksPending", 2)
            put("source", "customer_order")
        }
        val event = AssignmentStatusChangedEvent.fromJson(data)
        assertNotNull("F-C-51: fromJson must parse full payload", event)
        assertEquals("o1", event?.orderId)
        assertEquals("b1", event?.bookingId)
        assertEquals("d1", event?.driverId)
        assertEquals("Alice", event?.driverName)
        assertEquals(1, event?.trucksAccepted)
        assertEquals(2, event?.trucksPending)
        assertEquals("customer_order", event?.source)
    }
}
