package com.weelo.logistics.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class BroadcastIngressFallbackResolverTest {

    private val directBroadcastSocketEvents = setOf("new_broadcast", "new_order_alert", "new_truck_request")
    private val directCancellationSocketEvents = setOf(
        "order_cancelled",
        "booking_cancelled",
        "broadcast_dismissed",
        "broadcast_expired",
        "booking_expired",
        "order_expired"
    )
    private val fallbackBroadcastSocketEvents = setOf("message", "new_booking_request", "broadcast_request", "broadcast_available")
    private val broadcastPayloadTypes = setOf("new_broadcast", "new_truck_request")
    private val cancellationPayloadTypes = setOf(
        "order_cancelled",
        "booking_cancelled",
        "broadcast_dismissed",
        "broadcast_expired",
        "booking_expired",
        "order_expired"
    )

    @Test
    fun `routes cancellation before generic message broadcast fallback`() {
        val decision = resolveBroadcastFallbackDecision(
            rawIncomingEvent = "message",
            payloadType = "order_cancelled",
            legacyType = null,
            directBroadcastSocketEvents = directBroadcastSocketEvents,
            directCancellationSocketEvents = directCancellationSocketEvents,
            fallbackBroadcastSocketEvents = fallbackBroadcastSocketEvents,
            broadcastPayloadTypes = broadcastPayloadTypes,
            cancellationPayloadTypes = cancellationPayloadTypes
        )

        assertEquals(BroadcastFallbackRoute.CANCELLATION, decision.route)
        assertEquals("order_cancelled", decision.effectiveEvent)
    }

    @Test
    fun `routes broadcast fallback when payload type is broadcast`() {
        val decision = resolveBroadcastFallbackDecision(
            rawIncomingEvent = "message",
            payloadType = "new_broadcast",
            legacyType = null,
            directBroadcastSocketEvents = directBroadcastSocketEvents,
            directCancellationSocketEvents = directCancellationSocketEvents,
            fallbackBroadcastSocketEvents = fallbackBroadcastSocketEvents,
            broadcastPayloadTypes = broadcastPayloadTypes,
            cancellationPayloadTypes = cancellationPayloadTypes
        )

        assertEquals(BroadcastFallbackRoute.BROADCAST, decision.route)
        assertEquals("message", decision.effectiveEvent)
    }

    @Test
    fun `ignores direct event names because dedicated listeners handle them`() {
        val decision = resolveBroadcastFallbackDecision(
            rawIncomingEvent = "new_broadcast",
            payloadType = null,
            legacyType = null,
            directBroadcastSocketEvents = directBroadcastSocketEvents,
            directCancellationSocketEvents = directCancellationSocketEvents,
            fallbackBroadcastSocketEvents = fallbackBroadcastSocketEvents,
            broadcastPayloadTypes = broadcastPayloadTypes,
            cancellationPayloadTypes = cancellationPayloadTypes
        )

        assertEquals(BroadcastFallbackRoute.NONE, decision.route)
        assertEquals(null, decision.effectiveEvent)
    }
}

