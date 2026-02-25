package com.weelo.logistics.broadcast

import org.json.JSONObject

enum class PayloadWarning {
    PAYLOAD_NOT_JSON,
    MISSING_ID,
    FALLBACK_ORDER_ID,
    FALLBACK_BOOKING_ID,
    FALLBACK_ID
}

data class NormalizedBroadcastPayload(
    val normalizedId: String,
    val payloadVersion: String?,
    val warnings: List<PayloadWarning>,
    val payload: JSONObject?
)

/**
 * Canonical payload normalizer for all ingress sources.
 */
object BroadcastPayloadNormalizer {
    fun normalizeFromArgs(args: Array<Any>): NormalizedBroadcastPayload {
        return normalize(args.firstOrNull() as? JSONObject)
    }

    fun normalize(payload: JSONObject?): NormalizedBroadcastPayload {
        if (payload == null) {
            return NormalizedBroadcastPayload(
                normalizedId = "",
                payloadVersion = null,
                warnings = listOf(PayloadWarning.PAYLOAD_NOT_JSON),
                payload = null
            )
        }

        val rawBroadcastId = payload.optString("broadcastId", "").trim()
        val rawOrderId = payload.optString("orderId", "").trim()
        val rawBookingId = payload.optString("bookingId", "").trim()
        val rawId = payload.optString("id", "").trim()

        val warnings = mutableListOf<PayloadWarning>()
        val normalizedId = when {
            rawBroadcastId.isNotBlank() -> rawBroadcastId
            rawOrderId.isNotBlank() -> {
                warnings += PayloadWarning.FALLBACK_ORDER_ID
                rawOrderId
            }
            rawBookingId.isNotBlank() -> {
                warnings += PayloadWarning.FALLBACK_BOOKING_ID
                rawBookingId
            }
            rawId.isNotBlank() -> {
                warnings += PayloadWarning.FALLBACK_ID
                rawId
            }
            else -> {
                warnings += PayloadWarning.MISSING_ID
                ""
            }
        }

        return NormalizedBroadcastPayload(
            normalizedId = normalizedId,
            payloadVersion = payload.optString("payloadVersion").takeIf { it.isNotBlank() },
            warnings = warnings,
            payload = payload
        )
    }
}
