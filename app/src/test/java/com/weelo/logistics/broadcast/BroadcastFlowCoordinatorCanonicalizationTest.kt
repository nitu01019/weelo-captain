package com.weelo.logistics.broadcast

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastFlowCoordinatorCanonicalizationTest {

    @After
    fun tearDown() {
        clearTombstones()
    }

    @Test
    fun `new broadcast aliases map to same event class`() {
        val newBroadcast = eventClassFor("new_broadcast")
        val newOrderAlert = eventClassFor("new_order_alert")

        assertEquals(BroadcastEventClass.NEW_BROADCAST, newBroadcast)
        assertEquals(BroadcastEventClass.NEW_BROADCAST, newOrderAlert)
    }

    @Test
    fun `new broadcast aliases generate same dedupe key`() {
        val broadcastKey = dedupeKeyFor(rawEvent = "new_broadcast", id = "b_123", version = "v1")
        val alertKey = dedupeKeyFor(rawEvent = "new_order_alert", id = "b_123", version = "v1")

        assertEquals(broadcastKey, alertKey)
    }

    @Test
    fun `cancellation tombstone suppresses delayed new event within ttl`() {
        addTombstone(id = "broadcast_1", nowMs = 1_000L)

        assertTrue(hasTombstone(id = "broadcast_1", nowMs = 60_000L))
        assertFalse(hasTombstone(id = "broadcast_1", nowMs = 62_000L))
    }

    private fun eventClassFor(rawEvent: String): BroadcastEventClass {
        val method = BroadcastFlowCoordinator::class.java.getDeclaredMethod("toEventClass", String::class.java)
        method.isAccessible = true
        return method.invoke(BroadcastFlowCoordinator, rawEvent) as BroadcastEventClass
    }

    private fun dedupeKeyFor(rawEvent: String, id: String, version: String): String {
        val method = BroadcastFlowCoordinator::class.java.getDeclaredMethod(
            "dedupeKey",
            BroadcastEventClass::class.java,
            BroadcastIngressEnvelope::class.java,
            String::class.java
        )
        method.isAccessible = true

        val envelope = BroadcastIngressEnvelope(
            source = BroadcastIngressSource.SOCKET,
            rawEventName = rawEvent,
            normalizedId = id,
            receivedAtMs = 1L,
            payloadVersion = version,
            broadcast = null
        )

        return method.invoke(
            BroadcastFlowCoordinator,
            eventClassFor(rawEvent),
            envelope,
            id
        ) as String
    }

    private fun addTombstone(id: String, nowMs: Long) {
        val method = BroadcastFlowCoordinator::class.java.getDeclaredMethod(
            "addCancellationTombstone",
            String::class.java,
            Long::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(BroadcastFlowCoordinator, id, nowMs)
    }

    private fun hasTombstone(id: String, nowMs: Long): Boolean {
        val method = BroadcastFlowCoordinator::class.java.getDeclaredMethod(
            "hasCancellationTombstone",
            String::class.java,
            Long::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(BroadcastFlowCoordinator, id, nowMs) as Boolean
    }

    private fun clearTombstones() {
        val method = BroadcastFlowCoordinator::class.java.getDeclaredMethod("clearCancellationTombstones")
        method.isAccessible = true
        method.invoke(BroadcastFlowCoordinator)
    }
}
