package com.weelo.logistics.broadcast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastRolePolicyTest {

    @Test
    fun `transporter can handle broadcast ingress`() {
        assertTrue(BroadcastRolePolicy.canHandleBroadcastIngress("transporter"))
        assertTrue(BroadcastRolePolicy.canHandleBroadcastIngress("TRANSPORTER"))
    }

    @Test
    fun `non transporter cannot handle broadcast ingress`() {
        assertFalse(BroadcastRolePolicy.canHandleBroadcastIngress("driver"))
        assertFalse(BroadcastRolePolicy.canHandleBroadcastIngress(null))
    }

    @Test
    fun `broadcast notification types are transporter only`() {
        assertTrue(BroadcastRolePolicy.canDisplayNotification("transporter", "new_broadcast"))
        assertFalse(BroadcastRolePolicy.canDisplayNotification("driver", "new_broadcast"))
        assertFalse(BroadcastRolePolicy.canDisplayNotification(null, "booking_cancelled"))
        assertTrue(BroadcastRolePolicy.canDisplayNotification("driver", "trip_assigned"))
    }
}
