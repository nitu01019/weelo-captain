package com.weelo.logistics.data.remote

import java.util.Locale

internal fun isBroadcastCancelOrExpiryType(type: String): Boolean {
    val normalized = type.trim().lowercase(Locale.US)
    return normalized == WeeloFirebaseService.TYPE_BOOKING_CANCELLED ||
        normalized == WeeloFirebaseService.TYPE_ORDER_CANCELLED ||
        normalized == WeeloFirebaseService.TYPE_BROADCAST_DISMISSED ||
        normalized == WeeloFirebaseService.TYPE_BOOKING_EXPIRED ||
        normalized == WeeloFirebaseService.TYPE_ORDER_EXPIRED ||
        normalized == WeeloFirebaseService.TYPE_BROADCAST_EXPIRED
}

