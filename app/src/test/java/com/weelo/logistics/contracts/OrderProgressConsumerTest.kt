package com.weelo.logistics.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-65 — order_progress event consumer guard
 * =============================================================================
 *
 * Task prompt: "Wire backend's order_progress event emission into captain
 * transporter UI. New OrderProgressScreen consumer or existing dashboard."
 *
 * Captain already handles `order_status_update` (coarse status) and
 * `trucks_remaining_update` (count deltas). The new `order_progress` event
 * provides a unified per-order progress payload with accepted/pending/declined
 * truck counts — useful for the transporter dashboard + future
 * OrderProgressScreen.
 *
 * Invariants:
 *
 *   1. `OrderProgressEvent` data class exists in contracts package.
 *   2. EVENT_NAME mirrors backend string `order_progress`.
 *   3. Router references `FF_ORDER_PROGRESS_V1` flag (default OFF).
 *   4. SocketConstants has ORDER_PROGRESS constant.
 *   5. Router registers a listener for `order_progress` under the flag.
 *   6. fromJson fails-closed on blank orderId.
 * =============================================================================
 */
class OrderProgressConsumerTest {

    private val routerFile = File(
        "src/main/java/com/weelo/logistics/data/remote/socket/SocketEventRouter.kt"
    )

    private val contractsFile = File(
        "src/main/java/com/weelo/logistics/contracts/GeneratedContracts.kt"
    )

    private val constantsFile = File(
        "src/main/java/com/weelo/logistics/data/remote/socket/SocketConstants.kt"
    )

    private val routerSource: String by lazy { routerFile.readText() }
    private val contractsSource: String by lazy { contractsFile.readText() }
    private val constantsSource: String by lazy { constantsFile.readText() }

    @Test
    fun `OrderProgressEvent is declared`() {
        assertTrue(
            "F-C-65: data class OrderProgressEvent must be declared",
            contractsSource.contains("data class OrderProgressEvent")
        )
    }

    @Test
    fun `OrderProgressEvent has per-order truck progress fields`() {
        val required = listOf(
            "orderId", "status",
            "trucksNeeded", "trucksAccepted", "trucksPending", "trucksDeclined"
        )
        required.forEach { field ->
            assertTrue(
                "F-C-65: OrderProgressEvent must declare `$field`",
                contractsSource.contains(Regex("""val\s+$field\s*:"""))
            )
        }
    }

    @Test
    fun `OrderProgressEvent EVENT_NAME mirrors backend string`() {
        assertTrue(
            "F-C-65: EVENT_NAME must be `order_progress`",
            contractsSource.contains(Regex("""EVENT_NAME\s*=\s*"order_progress""""))
        )
    }

    @Test
    fun `fromJson fails closed on blank orderId`() {
        val empty = org.json.JSONObject()
        assertNull(
            "F-C-65: fromJson must return null when orderId absent",
            OrderProgressEvent.fromJson(empty)
        )
    }

    @Test
    fun `fromJson parses full progress payload`() {
        val data = org.json.JSONObject().apply {
            put("orderId", "order-1")
            put("status", "in_progress")
            put("trucksNeeded", 5)
            put("trucksAccepted", 2)
            put("trucksPending", 2)
            put("trucksDeclined", 1)
            put("updatedAtMs", 1700000000000L)
        }
        val event = OrderProgressEvent.fromJson(data)
        assertNotNull(event)
        assertEquals("order-1", event?.orderId)
        assertEquals("in_progress", event?.status)
        assertEquals(5, event?.trucksNeeded)
        assertEquals(2, event?.trucksAccepted)
        assertEquals(2, event?.trucksPending)
        assertEquals(1, event?.trucksDeclined)
        assertEquals(1700000000000L, event?.updatedAtMs)
    }

    @Test
    fun `SocketConstants declares ORDER_PROGRESS`() {
        assertTrue(
            "F-C-65: SocketConstants must declare ORDER_PROGRESS constant",
            constantsSource.contains(Regex("""const\s+val\s+ORDER_PROGRESS\s*=\s*"order_progress""""))
        )
    }

    @Test
    fun `router references FF_ORDER_PROGRESS_V1 flag`() {
        assertTrue(
            "F-C-65: router must reference BuildConfig.FF_ORDER_PROGRESS_V1",
            routerSource.contains("FF_ORDER_PROGRESS_V1")
        )
    }

    @Test
    fun `router imports OrderProgressEvent`() {
        assertTrue(
            "F-C-65: router must import OrderProgressEvent from contracts",
            routerSource.contains("import com.weelo.logistics.contracts.OrderProgressEvent")
        )
    }

    @Test
    fun `router registers order_progress listener under the flag`() {
        // Look for the SocketConstants.ORDER_PROGRESS registration call.
        assertTrue(
            "F-C-65: router must register socket.on(SocketConstants.ORDER_PROGRESS, ...)",
            routerSource.contains(Regex("""on\(SocketConstants\.ORDER_PROGRESS\)"""))
        )
    }
}
