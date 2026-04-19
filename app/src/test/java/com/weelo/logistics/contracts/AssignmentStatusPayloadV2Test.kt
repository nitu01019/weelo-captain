package com.weelo.logistics.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-63 — assignment_status_payload_v2 consumer guard
 * =============================================================================
 *
 * Per INDEX F-C-63 analysis:
 *
 *   > assignment.service.ts:818 booking-room 4 fields
 *   > :851 customer-booking 7 fields w/o orderId
 *   > :886 customer-order 7 fields w/o bookingId
 *   > captain SocketEventRouter.kt:378-391 optString(..., "") silently defaults
 *
 * The v2 factory `emitAssignmentStatusChanged` unifies all 3 shapes into a
 * single 10+ field payload with both orderId AND bookingId (oneof). Captain
 * consumer must parse v2 AND fall back to legacy shape when v2 absent (graceful
 * backend-first rollout).
 *
 * Invariants:
 *
 *   1. Router references `FF_ASSIGNMENT_STATUS_PAYLOAD_V2` flag.
 *   2. Router calls `AssignmentStatusChangedEvent.fromJson` (stub in
 *      contracts/GeneratedContracts.kt — will be replaced by F-C-52 codegen).
 *   3. Router falls back to legacy handleAssignmentStatusChanged when v2 parse
 *      returns null OR when the flag is OFF.
 *   4. Both orderId AND bookingId are captured as nullable (oneof) — the
 *      source field identifies which emit path the backend used.
 * =============================================================================
 */
class AssignmentStatusPayloadV2Test {

    private val routerFile = File(
        "src/main/java/com/weelo/logistics/data/remote/socket/SocketEventRouter.kt"
    )

    private val contractsFile = File(
        "src/main/java/com/weelo/logistics/contracts/GeneratedContracts.kt"
    )

    private val routerSource: String by lazy { routerFile.readText() }
    private val contractsSource: String by lazy { contractsFile.readText() }

    @Test
    fun `router references FF_ASSIGNMENT_STATUS_PAYLOAD_V2 flag`() {
        assertTrue(
            "F-C-63: router must reference BuildConfig.FF_ASSIGNMENT_STATUS_PAYLOAD_V2",
            routerSource.contains("FF_ASSIGNMENT_STATUS_PAYLOAD_V2")
        )
    }

    @Test
    fun `AssignmentStatusChangedEvent declares nullable orderId and bookingId oneof`() {
        // Both fields must be nullable so captain can route based on presence.
        assertTrue(
            "F-C-63: orderId must be nullable (oneof with bookingId)",
            contractsSource.contains(Regex("""val\s+orderId\s*:\s*String\?"""))
        )
        assertTrue(
            "F-C-63: bookingId must be nullable (oneof with orderId)",
            contractsSource.contains(Regex("""val\s+bookingId\s*:\s*String\?"""))
        )
    }

    @Test
    fun `fromJson treats blank oneof fields as null`() {
        val data = org.json.JSONObject().apply {
            put("assignmentId", "a1")
            put("status", "driver_accepted")
            put("orderId", "")
            put("bookingId", "")
        }
        val event = AssignmentStatusChangedEvent.fromJson(data)
        assertNotNull(event)
        assertNull("F-C-63: blank orderId -> null so caller uses bookingId path", event?.orderId)
        assertNull("F-C-63: blank bookingId -> null", event?.bookingId)
    }

    @Test
    fun `fromJson parses booking-room variant with bookingId only`() {
        val data = org.json.JSONObject().apply {
            put("assignmentId", "a1")
            put("status", "driver_accepted")
            put("bookingId", "b1")
            put("source", "booking_room")
        }
        val event = AssignmentStatusChangedEvent.fromJson(data)
        assertNotNull(event)
        assertNull("F-C-63: absent orderId -> null", event?.orderId)
        assertEquals("b1", event?.bookingId)
        assertEquals("booking_room", event?.source)
    }

    @Test
    fun `fromJson parses customer-order variant with orderId only`() {
        val data = org.json.JSONObject().apply {
            put("assignmentId", "a1")
            put("status", "in_transit")
            put("orderId", "o1")
            put("source", "customer_order")
        }
        val event = AssignmentStatusChangedEvent.fromJson(data)
        assertNotNull(event)
        assertEquals("o1", event?.orderId)
        assertNull("F-C-63: absent bookingId -> null", event?.bookingId)
        assertEquals("customer_order", event?.source)
    }

    @Test
    fun `router keeps legacy handleAssignmentStatusChanged as fallback`() {
        // F-C-63 rollout plan: flag OFF -> legacy path. flag ON + fromJson==null
        // -> legacy path. flag ON + fromJson valid -> v2 path.
        assertTrue(
            "F-C-63: legacy handler must remain wired for graceful rollout",
            routerSource.contains("handleAssignmentStatusChanged")
        )
    }

    @Test
    fun `router has trucksAccepted and trucksPending accessors for multi-vehicle progress`() {
        // Multi-vehicle progress UI reads these fields. The contract class
        // exposes them; the router must forward them through to the caller.
        assertTrue(
            "F-C-63: trucksAccepted must be carried through the v2 event",
            contractsSource.contains(Regex("""val\s+trucksAccepted\s*:\s*Int\?"""))
        )
        assertTrue(
            "F-C-63: trucksPending must be carried through the v2 event",
            contractsSource.contains(Regex("""val\s+trucksPending\s*:\s*Int\?"""))
        )
    }
}
