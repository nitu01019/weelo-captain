package com.weelo.logistics.broadcast

/**
 * Structured broadcast telemetry for production diagnostics and SLO tracking.
 */
enum class BroadcastStage {
    SOCKET_AUTH,
    BROADCAST_ACTIVE_FETCH,
    BROADCAST_WS_RECEIVED,
    FCM_RECEIVED,
    BROADCAST_PARSED,
    BROADCAST_GATED,
    STATE_APPLIED,
    RECONCILE_REQUESTED,
    RECONCILE_COALESCED,
    RECONCILE_FOLLOWUP_EXECUTED,
    RECONCILE_DONE,
    INGRESS_BACKPRESSURE_DROPPED,
    BROADCAST_OVERLAY_SHOWN,
    BROADCAST_OVERLAY_RENDER,
    DISMISSED,
    NOTIFICATION_RECOVERY,
    BATTERY_OPTIMIZATION_GUIDANCE,
    BROADCAST_DECLINE_REQUESTED,
    BROADCAST_DECLINE_SUCCESS,
    BROADCAST_DECLINE_FAIL,
    BROADCAST_ACCEPT_HOLD_REQUESTED,
    BROADCAST_HOLD_SUCCESS,
    BROADCAST_HOLD_FAIL,
    BROADCAST_CONFIRM_ASSIGN_REQUESTED,
    BROADCAST_CONFIRM_SUCCESS,
    BROADCAST_CONFIRM_FAIL,
    DRIVER_TRIP_ASSIGNED_EMIT_CONFIRMED
}

enum class BroadcastStatus {
    SUCCESS,
    BUFFERED,
    DROPPED,
    FAILED,
    SKIPPED
}

interface BroadcastTelemetrySink {
    fun record(
        stage: BroadcastStage,
        status: BroadcastStatus,
        reason: String? = null,
        attrs: Map<String, String> = emptyMap()
    )

    fun recordLatency(
        name: String,
        ms: Long,
        attrs: Map<String, String> = emptyMap()
    )
}

object BroadcastTelemetry {
    @Volatile
    private var sink: BroadcastTelemetrySink = TimberBroadcastTelemetrySink

    fun setSink(newSink: BroadcastTelemetrySink) {
        sink = newSink
    }

    fun record(
        stage: BroadcastStage,
        status: BroadcastStatus,
        reason: String? = null,
        attrs: Map<String, String> = emptyMap()
    ) {
        sink.record(stage, status, reason, attrs)
    }

    fun recordLatency(name: String, ms: Long, attrs: Map<String, String> = emptyMap()) {
        sink.recordLatency(name, ms, attrs)
    }
}

private object TimberBroadcastTelemetrySink : BroadcastTelemetrySink {
    override fun record(
        stage: BroadcastStage,
        status: BroadcastStatus,
        reason: String?,
        attrs: Map<String, String>
    ) {
        val payload = buildString {
            append("stage=")
            append(stage.name)
            append(" status=")
            append(status.name)
            if (!reason.isNullOrBlank()) {
                append(" reason=")
                append(reason)
            }
            if (attrs.isNotEmpty()) {
                append(" attrs=")
                append(attrs.entries.joinToString(separator = ",") { "${it.key}:${it.value}" })
            }
        }
        timber.log.Timber.i("📊 BroadcastTelemetry %s", payload)
    }

    override fun recordLatency(name: String, ms: Long, attrs: Map<String, String>) {
        val attrsString = if (attrs.isEmpty()) "" else attrs.entries.joinToString(separator = ",") { "${it.key}:${it.value}" }
        timber.log.Timber.i("📈 BroadcastLatency name=%s ms=%d attrs=%s", name, ms, attrsString)
    }
}
