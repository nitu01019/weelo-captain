package com.weelo.logistics.broadcast

import java.util.Locale

/**
 * Central role policy for broadcast ingestion and display decisions.
 */
object BroadcastRolePolicy {

    private const val ROLE_TRANSPORTER = "transporter"

    private val transporterOnlyNotificationTypes = setOf(
        "new_broadcast",
        "new_truck_request",
        "new_order_alert",
        "booking_cancelled",
        "booking_expired",
        "broadcast_expired"
    )

    fun isTransporter(role: String?): Boolean {
        return role?.trim()?.lowercase(Locale.US) == ROLE_TRANSPORTER
    }

    fun canHandleBroadcastIngress(role: String?): Boolean {
        return isTransporter(role)
    }

    fun canDisplayNotification(role: String?, type: String): Boolean {
        val normalizedType = type.trim().lowercase(Locale.US)
        if (normalizedType in transporterOnlyNotificationTypes) {
            return isTransporter(role)
        }
        return true
    }
}
