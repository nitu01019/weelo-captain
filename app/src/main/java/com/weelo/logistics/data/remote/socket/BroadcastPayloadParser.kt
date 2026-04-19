package com.weelo.logistics.data.remote.socket

import com.weelo.logistics.broadcast.BroadcastPayloadNormalizer
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import org.json.JSONObject

/**
 * =============================================================================
 * BROADCAST PAYLOAD PARSER - JSON parsing for incoming broadcast envelopes
 * =============================================================================
 *
 * Extracted from SocketEventRouter to keep the router under 800 lines.
 * Contains:
 *   - parseIncomingBroadcastEnvelope() — full envelope parsing
 *   - JSON utility helpers (resolveEventId, resolveOptional*, resolvePayloadVersion)
 * =============================================================================
 */
internal object BroadcastPayloadParser {

    fun parseIncomingBroadcastEnvelope(
        rawEventName: String,
        args: Array<Any>,
        receivedAtMs: Long
    ): IncomingBroadcastEnvelope {
        val normalizedPayload = BroadcastPayloadNormalizer.normalizeFromArgs(args)
        val parseWarnings = normalizedPayload.warnings.map { it.name.lowercase() }.toMutableList()
        val data = normalizedPayload.payload ?: run {
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_PARSED, status = BroadcastStatus.FAILED,
                reason = "payload_invalid", attrs = mapOf("event" to rawEventName)
            )
            return IncomingBroadcastEnvelope(rawEventName, "", receivedAtMs, null, listOf("payload_not_json"), null)
        }

        return try {
            val normalizedId = normalizedPayload.normalizedId
            if (normalizedId.isEmpty()) {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_PARSED, status = BroadcastStatus.DROPPED,
                    reason = "missing_id", attrs = mapOf("event" to rawEventName)
                )
                return IncomingBroadcastEnvelope(
                    rawEventName, "", receivedAtMs,
                    resolvePayloadVersion(data, ""), listOf("missing_id"), null
                )
            }

            val requestedVehiclesList = parseRequestedVehicles(data)
            val trucksYouCanProvide = data.optInt("trucksYouCanProvide", 0)

            val notification = BroadcastNotification(
                broadcastId = normalizedId,
                customerId = data.optString("customerId", ""),
                customerName = data.optString("customerName", "Customer"),
                vehicleType = data.optString("vehicleType", ""),
                vehicleSubtype = data.optString("vehicleSubtype", ""),
                trucksNeeded = data.optInt("trucksNeeded", data.optInt("totalTrucksNeeded", 1)),
                trucksFilled = data.optInt("trucksFilled", data.optInt("trucksFilledSoFar", 0)),
                farePerTruck = data.optInt("farePerTruck", data.optInt("pricePerTruck", 0)),
                pickupAddress = resolveNestedString(data, "pickupLocation", "address")
                    ?: data.optString("pickupAddress", ""),
                pickupCity = resolveNestedString(data, "pickupLocation", "city")
                    ?: data.optString("pickupCity", ""),
                pickupLatitude = resolveNestedCoordinate(data, "pickupLocation", "pickup", "latitude", "lat"),
                pickupLongitude = resolveNestedCoordinate(data, "pickupLocation", "pickup", "longitude", "lng"),
                dropAddress = resolveNestedString(data, "dropLocation", "address")
                    ?: data.optString("dropAddress", ""),
                dropCity = resolveNestedString(data, "dropLocation", "city")
                    ?: data.optString("dropCity", ""),
                dropLatitude = resolveNestedCoordinate(data, "dropLocation", "drop", "latitude", "lat"),
                dropLongitude = resolveNestedCoordinate(data, "dropLocation", "drop", "longitude", "lng"),
                distanceKm = data.optInt("distance", data.optInt("distanceKm", 0)),
                goodsType = data.optString("goodsType", "General"),
                isUrgent = data.optBoolean("isUrgent", false),
                expiresAt = data.optString("expiresAt", ""),
                requestedVehicles = requestedVehiclesList,
                trucksYouCanProvide = trucksYouCanProvide,
                maxTrucksYouCanProvide = data.optInt("maxTrucksYouCanProvide", trucksYouCanProvide),
                yourAvailableTrucks = data.optInt("yourAvailableTrucks", 0),
                yourTotalTrucks = data.optInt("yourTotalTrucks", 0),
                trucksStillNeeded = data.optInt("trucksStillNeeded", 0),
                isPersonalized = data.optBoolean("isPersonalized", false),
                pickupDistanceKm = data.optDouble("pickupDistanceKm", 0.0),
                pickupEtaMinutes = data.optInt("pickupEtaMinutes", 0),
                eventId = resolveOptionalString(data, "eventId"),
                dispatchRevision = resolveOptionalLong(data, "dispatchRevision"),
                orderLifecycleVersion = resolveOptionalLong(data, "orderLifecycleVersion"),
                eventVersion = resolveOptionalInt(data, "eventVersion"),
                serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
            )

            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_PARSED, status = BroadcastStatus.SUCCESS,
                reason = if (parseWarnings.isEmpty()) null else "parse_warnings",
                attrs = mutableMapOf("event" to rawEventName, "normalizedId" to normalizedId).apply {
                    if (parseWarnings.isNotEmpty()) this["warnings"] = parseWarnings.joinToString("|")
                }
            )
            IncomingBroadcastEnvelope(
                rawEventName, normalizedId, receivedAtMs,
                resolvePayloadVersion(data, normalizedId), parseWarnings, notification
            )
        } catch (e: Exception) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_PARSED, status = BroadcastStatus.FAILED,
                reason = "payload_invalid",
                attrs = mapOf("event" to rawEventName, "error" to (e.message ?: "unknown"))
            )
            timber.log.Timber.e(e, "Error parsing broadcast payload: ${e.message}")
            IncomingBroadcastEnvelope(rawEventName, "", receivedAtMs, null, listOf("exception"), null)
        }
    }

    // =========================================================================
    // JSON FIELD RESOLVERS
    // =========================================================================

    fun resolveEventId(data: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = data.optString(key, "").trim()
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    fun resolveOptionalString(data: JSONObject, key: String): String? {
        if (!data.has(key)) return null
        return data.optString(key, "").trim().takeIf { it.isNotEmpty() }
    }

    fun resolveOptionalInt(data: JSONObject, key: String): Int? {
        if (!data.has(key)) return null
        return when (val raw = data.opt(key)) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    fun resolveOptionalLong(data: JSONObject, key: String): Long? {
        if (!data.has(key)) return null
        return when (val raw = data.opt(key)) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
    }

    fun resolvePayloadVersion(data: JSONObject, normalizedId: String): String? {
        val dispatchRevision = resolveOptionalLong(data, "dispatchRevision")
        val orderLifecycleVersion = resolveOptionalLong(data, "orderLifecycleVersion")
        if ((dispatchRevision != null && dispatchRevision >= 0L) ||
            (orderLifecycleVersion != null && orderLifecycleVersion >= 0L)
        ) {
            return buildString {
                append("order:"); append(normalizedId)
                dispatchRevision?.takeIf { it >= 0L }?.let { append("|dispatch:"); append(it) }
                orderLifecycleVersion?.takeIf { it >= 0L }?.let { append("|lifecycle:"); append(it) }
            }
        }
        val eventId = resolveOptionalString(data, "eventId")
        if (!eventId.isNullOrBlank()) return "event:$eventId"
        val eventVersion = resolveOptionalInt(data, "eventVersion")
        val serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
        if (eventVersion != null && serverTimeMs != null && serverTimeMs > 0L) {
            return "v$eventVersion@$serverTimeMs|$normalizedId"
        }
        return data.optString("payloadVersion").takeIf { it.isNotBlank() }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private fun parseRequestedVehicles(data: JSONObject): List<RequestedVehicleNotification> {
        val vehiclesArray = data.optJSONArray("requestedVehicles") ?: return emptyList()
        val result = mutableListOf<RequestedVehicleNotification>()
        for (i in 0 until vehiclesArray.length()) {
            val vehicle = vehiclesArray.optJSONObject(i) ?: continue
            result.add(
                RequestedVehicleNotification(
                    vehicleType = vehicle.optString("vehicleType", ""),
                    vehicleSubtype = vehicle.optString("vehicleSubtype", ""),
                    count = vehicle.optInt("count", 1),
                    filledCount = vehicle.optInt("filledCount", 0),
                    farePerTruck = vehicle.optDouble("farePerTruck", 0.0),
                    capacityTons = vehicle.optDouble("capacityTons", 0.0)
                )
            )
        }
        return result
    }

    private fun resolveNestedString(data: JSONObject, objectKey: String, fieldKey: String): String? {
        return data.optJSONObject(objectKey)?.optString(fieldKey, "")?.takeIf { it.isNotEmpty() }
    }

    /**
     * Resolves a coordinate from nested JSON objects.
     * Tries primaryObject.primaryKey, primaryObject.fallbackKey,
     * then fallbackObject.primaryKey, fallbackObject.fallbackKey.
     */
    private fun resolveNestedCoordinate(
        data: JSONObject,
        primaryObjectKey: String,
        fallbackObjectKey: String,
        primaryKey: String,
        fallbackKey: String
    ): Double {
        val primary = data.optJSONObject(primaryObjectKey)
        if (primary != null) {
            val v1 = primary.optDouble(primaryKey, Double.NaN)
            if (!v1.isNaN()) return v1
            val v2 = primary.optDouble(fallbackKey, Double.NaN)
            if (!v2.isNaN()) return v2
        }
        val fallback = data.optJSONObject(fallbackObjectKey)
        if (fallback != null) {
            val v1 = fallback.optDouble(primaryKey, Double.NaN)
            if (!v1.isNaN()) return v1
            val v2 = fallback.optDouble(fallbackKey, Double.NaN)
            if (!v2.isNaN()) return v2
        }
        return 0.0
    }
}
