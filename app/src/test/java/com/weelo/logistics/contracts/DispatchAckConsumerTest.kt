package com.weelo.logistics.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-53 — dispatch_ack consumer guard
 * =============================================================================
 *
 * Backend (t5-backend-consumers) gains `FF_DISPATCH_ACK_HANDLER` which
 * registers `socket.on('dispatch_ack', ...)` on the server side and MAY emit a
 * receipt-echo (`dispatch_ack_response`) back to the captain for
 * revision-tracking observability.
 *
 * Captain already fires `dispatch_ack` on ingest (SocketEventRouter.kt:821).
 * This task adds the CONSUMER side: typed parse of server->client receipt
 * events so captain telemetry can close the loop on dispatch-ack success rate.
 *
 * Invariants being locked in:
 *
 *   1. `DispatchAckEvent` data class exists in contracts package.
 *   2. EVENT_NAME mirrors backend string `dispatch_ack`.
 *   3. Router references `FF_DISPATCH_ACK_HANDLER` flag (default OFF).
 *   4. Router's existing `emitDispatchAck` emission path stays wired — the
 *      existing behavior must NOT change when flag is OFF.
 *   5. fromJson fails-closed on blank orderId (cannot correlate without it).
 * =============================================================================
 */
class DispatchAckConsumerTest {

    private val routerFile = File(
        "src/main/java/com/weelo/logistics/data/remote/socket/SocketEventRouter.kt"
    )

    private val contractsFile = File(
        "src/main/java/com/weelo/logistics/contracts/GeneratedContracts.kt"
    )

    private val routerSource: String by lazy { routerFile.readText() }
    private val contractsSource: String by lazy { contractsFile.readText() }

    @Test
    fun `DispatchAckEvent is declared in contracts`() {
        assertTrue(
            "F-C-53: data class DispatchAckEvent must be declared",
            contractsSource.contains("data class DispatchAckEvent")
        )
    }

    @Test
    fun `DispatchAckEvent exposes canonical event name constant`() {
        assertTrue(
            "F-C-53: DispatchAckEvent.EVENT_NAME must match backend string",
            contractsSource.contains(Regex("""EVENT_NAME\s*=\s*"dispatch_ack""""))
        )
    }

    @Test
    fun `DispatchAckEvent fromJson fails closed on blank orderId`() {
        val empty = org.json.JSONObject()
        assertNull(
            "F-C-53: fromJson must return null when orderId is missing — cannot correlate ack",
            DispatchAckEvent.fromJson(empty)
        )

        val blank = org.json.JSONObject().apply { put("orderId", "") }
        assertNull(
            "F-C-53: fromJson must treat empty orderId as failure",
            DispatchAckEvent.fromJson(blank)
        )
    }

    @Test
    fun `DispatchAckEvent fromJson parses full revision-tracking payload`() {
        val data = org.json.JSONObject().apply {
            put("orderId", "order-1")
            put("dispatchRevision", 42L)
            put("acknowledgedAtMs", 1700000000000L)
            put("source", "backend_handler")
        }
        val event = DispatchAckEvent.fromJson(data)
        assertNotNull("F-C-53: valid payload must parse", event)
        assertEquals("order-1", event?.orderId)
        assertEquals(42L, event?.dispatchRevision)
        assertEquals(1700000000000L, event?.acknowledgedAtMs)
        assertEquals("backend_handler", event?.source)
    }

    @Test
    fun `router references FF_DISPATCH_ACK_HANDLER flag`() {
        assertTrue(
            "F-C-53: router must reference BuildConfig.FF_DISPATCH_ACK_HANDLER",
            routerSource.contains("FF_DISPATCH_ACK_HANDLER")
        )
    }

    @Test
    fun `router keeps existing emitDispatchAck path wired`() {
        // DO NOT DELETE captain's existing emit — backend may still consume it
        // even with the new consumer path.
        assertTrue(
            "F-C-53: emitDispatchAck emission path must remain (captain->server)",
            routerSource.contains("fun emitDispatchAck(")
        )
    }

    @Test
    fun `router imports DispatchAckEvent for v2 consumer path`() {
        assertTrue(
            "F-C-53: router must import DispatchAckEvent from contracts package",
            routerSource.contains("import com.weelo.logistics.contracts.DispatchAckEvent")
        )
    }
}
