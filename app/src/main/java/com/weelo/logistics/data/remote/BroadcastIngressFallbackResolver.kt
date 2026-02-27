package com.weelo.logistics.data.remote

import java.util.Locale

internal enum class BroadcastFallbackRoute {
    NONE,
    CANCELLATION,
    BROADCAST
}

internal data class BroadcastFallbackDecision(
    val route: BroadcastFallbackRoute,
    val effectiveEvent: String? = null
)

internal fun resolveBroadcastFallbackDecision(
    rawIncomingEvent: String,
    payloadType: String?,
    legacyType: String?,
    directBroadcastSocketEvents: Set<String>,
    directCancellationSocketEvents: Set<String>,
    fallbackBroadcastSocketEvents: Set<String>,
    broadcastPayloadTypes: Set<String>,
    cancellationPayloadTypes: Set<String>
): BroadcastFallbackDecision {
    val normalizedEvent = rawIncomingEvent.trim().lowercase(Locale.US)
    if (normalizedEvent in directBroadcastSocketEvents || normalizedEvent in directCancellationSocketEvents) {
        return BroadcastFallbackDecision(route = BroadcastFallbackRoute.NONE)
    }

    val normalizedPayloadType = payloadType?.trim()?.lowercase(Locale.US).orEmpty()
    val normalizedLegacyType = legacyType?.trim()?.lowercase(Locale.US).orEmpty()

    val effectiveCancellationEvent = when {
        normalizedPayloadType in cancellationPayloadTypes -> normalizedPayloadType
        normalizedLegacyType in cancellationPayloadTypes -> normalizedLegacyType
        else -> null
    }
    if (effectiveCancellationEvent != null) {
        return BroadcastFallbackDecision(
            route = BroadcastFallbackRoute.CANCELLATION,
            effectiveEvent = effectiveCancellationEvent
        )
    }

    val effectiveBroadcastEvent = when {
        normalizedEvent in fallbackBroadcastSocketEvents -> normalizedEvent
        normalizedPayloadType in broadcastPayloadTypes -> normalizedPayloadType
        normalizedLegacyType in broadcastPayloadTypes -> normalizedLegacyType
        else -> null
    }
    return if (effectiveBroadcastEvent != null) {
        BroadcastFallbackDecision(
            route = BroadcastFallbackRoute.BROADCAST,
            effectiveEvent = effectiveBroadcastEvent
        )
    } else {
        BroadcastFallbackDecision(route = BroadcastFallbackRoute.NONE)
    }
}

