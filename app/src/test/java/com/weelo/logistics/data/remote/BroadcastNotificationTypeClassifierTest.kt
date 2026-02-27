package com.weelo.logistics.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastNotificationTypeClassifierTest {

    @Test
    fun `recognizes canonical and legacy cancel or expiry types`() {
        assertTrue(isBroadcastCancelOrExpiryType("order_cancelled"))
        assertTrue(isBroadcastCancelOrExpiryType("booking_cancelled"))
        assertTrue(isBroadcastCancelOrExpiryType("broadcast_dismissed"))
        assertTrue(isBroadcastCancelOrExpiryType("order_expired"))
        assertTrue(isBroadcastCancelOrExpiryType("booking_expired"))
        assertTrue(isBroadcastCancelOrExpiryType("broadcast_expired"))
    }

    @Test
    fun `rejects non cancellation types`() {
        assertFalse(isBroadcastCancelOrExpiryType("new_broadcast"))
        assertFalse(isBroadcastCancelOrExpiryType("trip_assigned"))
        assertFalse(isBroadcastCancelOrExpiryType("payment"))
    }
}

