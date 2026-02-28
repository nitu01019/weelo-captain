package com.weelo.logistics.broadcast

import android.content.Context
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.BroadcastDismissedNotification
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.remote.TrucksRemainingNotification
import com.weelo.logistics.data.remote.WeeloFirebaseService
import com.weelo.logistics.data.repository.BroadcastFetchQueryMode
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.offline.AvailabilityManager
import com.weelo.logistics.offline.AvailabilityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class BroadcastIngressSource {
    SOCKET,
    FCM,
    NOTIFICATION_OPEN,
    BUFFER
}

enum class BroadcastDropReason {
    MISSING_ID,
    PAYLOAD_INVALID,
    AVAILABILITY_OFFLINE,
    AVAILABILITY_UNKNOWN_BUFFER_EXPIRED,
    DUPLICATE_ID,
    CAPACITY_LIMIT,
    TOMBSTONE_SUPPRESSED,
    ROLE_NOT_TRANSPORTER
}

enum class BroadcastDecision {
    SHOW,
    BUFFER,
    DROP,
    RECONCILE
}

enum class BroadcastEventClass {
    NEW_BROADCAST,
    CANCEL,
    EXPIRE,
    TRUCKS_REMAINING,
    OTHER
}

data class BroadcastIngressEnvelope(
    val source: BroadcastIngressSource,
    val rawEventName: String,
    val normalizedId: String,
    val receivedAtMs: Long,
    val payloadVersion: String? = null,
    val parseWarnings: List<String> = emptyList(),
    val broadcast: BroadcastTrip? = null
)

data class BroadcastStateDelta(
    val added: List<BroadcastTrip> = emptyList(),
    val updated: List<BroadcastTrip> = emptyList(),
    val removedIds: List<String> = emptyList()
)

data class BroadcastFeedState(
    val broadcasts: List<BroadcastTrip> = emptyList(),
    val pendingCount: Int = 0,
    val availabilityState: AvailabilityState = AvailabilityState.UNKNOWN,
    val isReconciling: Boolean = false,
    val lastUpdatedMs: Long = 0L,
    val lastDelta: BroadcastStateDelta? = null
)

sealed interface BroadcastCoordinatorEvent {
    data class IngressHandled(
        val id: String,
        val source: BroadcastIngressSource,
        val decision: BroadcastDecision
    ) : BroadcastCoordinatorEvent

    data class Dropped(
        val id: String,
        val source: BroadcastIngressSource,
        val reason: BroadcastDropReason
    ) : BroadcastCoordinatorEvent

    data class ReconcileRequested(val force: Boolean) : BroadcastCoordinatorEvent
    data class ReconcileDone(val success: Boolean, val reason: String? = null) : BroadcastCoordinatorEvent
    data class OverlayShown(val id: String) : BroadcastCoordinatorEvent
    data class Dismissed(val id: String, val reason: String) : BroadcastCoordinatorEvent
}

/**
 * Single owner for broadcast ingress decisions and in-memory feed state.
 */
object BroadcastFlowCoordinator {
    private const val DEDUPE_LRU_SIZE = 10_000
    private const val PENDING_QUEUE_MAX = 50
    private const val STARTUP_BUFFER_TTL_MS = 90_000L
    private const val MAX_RENDERABLE_BROADCASTS = 250
    private const val INGRESS_CHANNEL_CAPACITY = 1_024
    private const val CANCELLATION_TOMBSTONE_MAX_SIZE = 5_000
    private const val CANCELLATION_TOMBSTONE_TTL_MS = 60_000L
    private const val DISMISS_LATENCY_TRACK_MAX_SIZE = 5_000
    private const val SNAPSHOT_RECONCILE_COOLDOWN_MS = 3_000L
    private const val RECONCILE_DEBOUNCE_MS = 350L
    private const val MIN_FETCH_INTERVAL_MS = 1_200L
    private val TERMINAL_ORDER_STATUSES = setOf(
        "cancelled",
        "canceled",
        "expired",
        "fully_filled",
        "completed",
        "closed"
    )

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dedupeIds = LruIdSet(DEDUPE_LRU_SIZE)
    private val pendingQueue = PendingBroadcastQueue(
        maxSize = PENDING_QUEUE_MAX,
        ttlMs = STARTUP_BUFFER_TTL_MS
    )
    private val stateStore = BroadcastStateStore(MAX_RENDERABLE_BROADCASTS)
    private val ingressChannel = Channel<BroadcastIngressEnvelope>(
        capacity = INGRESS_CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val ingressQueueDepth = AtomicInteger(0)
    private val cancellationTombstones = LinkedHashMap<String, Long>(CANCELLATION_TOMBSTONE_MAX_SIZE, 0.75f, true)
    private val tombstoneLock = Any()
    private val dismissLatencyById = LinkedHashMap<String, Long>(DISMISS_LATENCY_TRACK_MAX_SIZE, 0.75f, true)
    private val dismissLatencyLock = Any()
    private val snapshotReconcileLock = Any()
    private val snapshotReconcileAtMs = LinkedHashMap<String, Long>(1_024, 0.75f, true)

    private val _feedState = MutableStateFlow(BroadcastFeedState())
    val feedState: StateFlow<BroadcastFeedState> = _feedState.asStateFlow()

    private val _events = MutableSharedFlow<BroadcastCoordinatorEvent>(replay = 0, extraBufferCapacity = 128)
    val events: SharedFlow<BroadcastCoordinatorEvent> = _events.asSharedFlow()
    private val _dismissed = MutableSharedFlow<BroadcastDismissedNotification>(replay = 0, extraBufferCapacity = 128)
    val dismissed: SharedFlow<BroadcastDismissedNotification> = _dismissed.asSharedFlow()

    private var appContext: Context? = null
    private var availabilityJob: Job? = null
    private var trucksRemainingJob: Job? = null
    private var dismissEventsJob: Job? = null
    private var ingressWorkerJob: Job? = null
    private var scheduledReconcileJob: Job? = null
    private var activeReconcileJob: Job? = null
    private var pendingForceReconcile = false
    private var pendingReconcileAfterActive = false
    private var lastFetchStartedAtMs = 0L
    private var lastSyncCursor: String? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun start() {
        if (!isEnabled()) return
        if (started.getAndSet(true)) return

        val context = appContext
        if (context == null) {
            started.set(false)
            return
        }
        val availabilityManager = AvailabilityManager.getInstance(context)

        availabilityJob?.cancel()
        availabilityJob = scope.launch {
            availabilityManager.availabilityState.collect { state ->
                val previousState = _feedState.value.availabilityState
                publishState(
                    availabilityState = state,
                    isReconciling = _feedState.value.isReconciling
                )
                if (state == AvailabilityState.ONLINE && previousState != AvailabilityState.ONLINE) {
                    flushPendingBuffer()
                    requestReconcile(force = true)
                } else if (state == AvailabilityState.OFFLINE && previousState == AvailabilityState.UNKNOWN) {
                    dropPendingBufferForOffline()
                }
            }
        }

        trucksRemainingJob?.cancel()
        trucksRemainingJob = scope.launch {
            SocketIOService.trucksRemainingUpdates.collect { notification ->
                handleTrucksRemainingUpdate(notification)
            }
        }

        dismissEventsJob?.cancel()
        dismissEventsJob = scope.launch {
            SocketIOService.broadcastDismissed.collect { dismissed ->
                val id = dismissed.broadcastId.trim()
                if (id.isEmpty()) return@collect
                val reason = dismissed.reason.ifBlank { "dismissed" }
                val cancellationVersionToken = buildCancellationVersionToken(
                    eventVersion = dismissed.eventVersion,
                    serverTimeMs = dismissed.serverTimeMs
                )
                addCancellationTombstone(id, cancellationVersionToken)
                startDismissSequence(
                    id = id,
                    reason = reason,
                    message = dismissed.message,
                    customerName = dismissed.customerName
                )
            }
        }

        ingressWorkerJob?.cancel()
        ingressWorkerJob = scope.launch {
            while (started.get()) {
                val envelope = ingressChannel.receive()
                try {
                    handleIngress(envelope)
                } catch (t: Throwable) {
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.STATE_APPLIED,
                        status = BroadcastStatus.FAILED,
                        reason = "ingress_worker_error",
                        attrs = mapOf(
                            "event" to envelope.rawEventName,
                            "eventClass" to toEventClass(envelope.rawEventName).name,
                            "id" to envelope.normalizedId.ifBlank { "none" }
                        )
                    )
                    timber.log.Timber.e(t, "Ingress worker failed for event=%s id=%s", envelope.rawEventName, envelope.normalizedId)
                } finally {
                    ingressQueueDepth.updateAndGet { depth -> (depth - 1).coerceAtLeast(0) }
                }
            }
        }

        requestReconcile(force = true)
    }

    fun stop() {
        if (!started.getAndSet(false)) return
        availabilityJob?.cancel()
        trucksRemainingJob?.cancel()
        dismissEventsJob?.cancel()
        ingressWorkerJob?.cancel()
        scheduledReconcileJob?.cancel()
        activeReconcileJob?.cancel()
        availabilityJob = null
        trucksRemainingJob = null
        dismissEventsJob = null
        ingressWorkerJob = null
        scheduledReconcileJob = null
        activeReconcileJob = null
        pendingForceReconcile = false
        pendingReconcileAfterActive = false
        lastSyncCursor = null
        drainIngressQueue()
        pendingQueue.clear()
        dedupeIds.clear()
        clearCancellationTombstones()
        clearDismissLatencyTracking()
        stateStore.clear()
        _feedState.value = BroadcastFeedState(
            availabilityState = AvailabilityState.UNKNOWN,
            pendingCount = 0,
            broadcasts = emptyList(),
            isReconciling = false,
            lastUpdatedMs = System.currentTimeMillis(),
            lastDelta = BroadcastStateDelta(removedIds = emptyList())
        )
    }

    fun ingestSocketEnvelope(envelope: BroadcastIngressEnvelope) {
        if (!isEnabled()) return
        enqueueIngress(envelope)
    }

    fun ingestFcmEnvelope(envelope: BroadcastIngressEnvelope) {
        if (!isEnabled()) return
        enqueueIngress(envelope)
    }

    fun ingestNotificationOpen(broadcastId: String) {
        if (!isEnabled()) return
        val id = broadcastId.trim()
        if (id.isEmpty()) {
            emitDrop(
                id = "",
                source = BroadcastIngressSource.NOTIFICATION_OPEN,
                reason = BroadcastDropReason.MISSING_ID
            )
            return
        }

        val context = appContext ?: return
        val repository = BroadcastRepository.getInstance(context)
        scope.launch {
            when (val result = repository.getBroadcastById(id)) {
                is BroadcastResult.Success -> {
                    enqueueIngress(
                        BroadcastIngressEnvelope(
                            source = BroadcastIngressSource.NOTIFICATION_OPEN,
                            rawEventName = "notification_open",
                            normalizedId = id,
                            receivedAtMs = System.currentTimeMillis(),
                            broadcast = result.data
                        )
                    )
                }

                is BroadcastResult.Error -> {
                    requestReconcile(force = true)
                    _events.tryEmit(BroadcastCoordinatorEvent.ReconcileDone(false, result.message))
                }

                is BroadcastResult.Loading -> Unit
            }
        }
    }

    fun requestReconcile(force: Boolean) {
        if (!isEnabled()) return
        pendingForceReconcile = pendingForceReconcile || force
        if (scheduledReconcileJob?.isActive == true) return

        scheduledReconcileJob = scope.launch {
            delay(RECONCILE_DEBOUNCE_MS)
            val flags = BroadcastFeatureFlagsRegistry.current()
            if (flags.broadcastReconcileRateLimitEnabled) {
                val elapsed = System.currentTimeMillis() - lastFetchStartedAtMs
                val delayMs = (MIN_FETCH_INTERVAL_MS - elapsed).coerceAtLeast(0L)
                if (delayMs > 0L) {
                    delay(delayMs)
                }
            }
            val forceRefresh = pendingForceReconcile
            pendingForceReconcile = false
            scheduledReconcileJob = null
            runReconcile(forceRefresh = forceRefresh)
        }
    }

    private fun enqueueIngress(envelope: BroadcastIngressEnvelope) {
        val queueDepthBefore = ingressQueueDepth.get()
        val isAtCapacity = queueDepthBefore >= INGRESS_CHANNEL_CAPACITY
        val sent = ingressChannel.trySend(envelope).isSuccess

        if (sent) {
            if (isAtCapacity) {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.INGRESS_BACKPRESSURE_DROPPED,
                    status = BroadcastStatus.DROPPED,
                    reason = "drop_oldest",
                    attrs = mapOf(
                        "event" to envelope.rawEventName,
                        "eventClass" to toEventClass(envelope.rawEventName).name,
                        "source" to envelope.source.name.lowercase(Locale.US),
                        "id" to envelope.normalizedId.ifBlank { "none" },
                        "ingressQueueDepth" to queueDepthBefore.toString()
                    )
                )
                _events.tryEmit(
                    BroadcastCoordinatorEvent.Dropped(
                        id = envelope.normalizedId,
                        source = envelope.source,
                        reason = BroadcastDropReason.CAPACITY_LIMIT
                    )
                )
                requestReconcile(force = true)
                return
            }

            ingressQueueDepth.incrementAndGet()
            return
        }

        BroadcastTelemetry.record(
            stage = BroadcastStage.INGRESS_BACKPRESSURE_DROPPED,
            status = BroadcastStatus.FAILED,
            reason = "enqueue_failed",
            attrs = mapOf(
                "event" to envelope.rawEventName,
                "eventClass" to toEventClass(envelope.rawEventName).name,
                "source" to envelope.source.name.lowercase(Locale.US),
                "id" to envelope.normalizedId.ifBlank { "none" },
                "ingressQueueDepth" to queueDepthBefore.toString()
            )
        )
        emitDrop(
            id = envelope.normalizedId,
            source = envelope.source,
            reason = BroadcastDropReason.CAPACITY_LIMIT,
            additionalAttrs = mapOf("ingressQueueDepth" to queueDepthBefore.toString())
        )
        requestReconcile(force = true)
    }

    private fun drainIngressQueue() {
        while (true) {
            val drained = ingressChannel.tryReceive().getOrNull() ?: break
            if (drained.normalizedId.isNotBlank()) {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.INGRESS_BACKPRESSURE_DROPPED,
                    status = BroadcastStatus.SKIPPED,
                    reason = "stop_drained",
                    attrs = mapOf(
                        "event" to drained.rawEventName,
                        "eventClass" to toEventClass(drained.rawEventName).name,
                        "id" to drained.normalizedId
                    )
                )
            }
        }
        ingressQueueDepth.set(0)
    }

    private fun isEnabled(): Boolean {
        return BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled
    }

    private suspend fun handleIngress(envelope: BroadcastIngressEnvelope) {
        val flags = BroadcastFeatureFlagsRegistry.current()
        val eventClass = toEventClass(envelope.rawEventName)
        val role = RetrofitClient.getUserRole()?.lowercase(Locale.US)
        if (!BroadcastRolePolicy.canHandleBroadcastIngress(role)) {
            emitDrop(
                id = envelope.normalizedId,
                source = envelope.source,
                reason = BroadcastDropReason.ROLE_NOT_TRANSPORTER,
                additionalAttrs = mapOf(
                    "eventClass" to eventClass.name,
                    "event" to envelope.rawEventName
                )
            )
            return
        }

        val normalizedId = envelope.normalizedId.trim().ifEmpty {
            envelope.broadcast?.broadcastId?.trim().orEmpty()
        }
        if (normalizedId.isEmpty()) {
            if (!flags.broadcastStrictIdValidationEnabled) {
                requestReconcile(force = true)
                _events.tryEmit(
                    BroadcastCoordinatorEvent.IngressHandled(
                        id = "",
                        source = envelope.source,
                        decision = BroadcastDecision.RECONCILE
                    )
                )
                return
            }
            emitDrop(
                id = "",
                source = envelope.source,
                reason = BroadcastDropReason.MISSING_ID,
                additionalAttrs = mapOf(
                    "eventClass" to eventClass.name,
                    "event" to envelope.rawEventName
                )
            )
            return
        }

        if (eventClass == BroadcastEventClass.NEW_BROADCAST && hasCancellationTombstone(normalizedId, envelope.payloadVersion)) {
            emitDrop(
                id = normalizedId,
                source = envelope.source,
                reason = BroadcastDropReason.TOMBSTONE_SUPPRESSED,
                additionalAttrs = mapOf(
                    "eventClass" to eventClass.name,
                    "event" to envelope.rawEventName
                )
            )
            return
        }

        val dedupeKey = dedupeKey(eventClass, envelope, normalizedId)
        if (!dedupeIds.add(dedupeKey)) {
            emitDrop(
                id = normalizedId,
                source = envelope.source,
                reason = BroadcastDropReason.DUPLICATE_ID,
                additionalAttrs = mapOf(
                    "eventClass" to eventClass.name,
                    "event" to envelope.rawEventName
                )
            )
            return
        }

        if (eventClass == BroadcastEventClass.CANCEL || eventClass == BroadcastEventClass.EXPIRE) {
            addCancellationTombstone(normalizedId, envelope.payloadVersion)
            startDismissSequence(
                id = normalizedId,
                reason = envelope.rawEventName,
                message = "Request no longer active",
                customerName = ""
            )
            return
        }

        val trip = envelope.broadcast
        if (trip == null) {
            _events.tryEmit(
                BroadcastCoordinatorEvent.IngressHandled(
                    id = normalizedId,
                    source = envelope.source,
                    decision = BroadcastDecision.RECONCILE
                )
            )
            requestReconcile(force = true)
            BroadcastTelemetry.record(
                stage = BroadcastStage.RECONCILE_REQUESTED,
                status = BroadcastStatus.SKIPPED,
                reason = "ingress_payload_missing_fetching_from_api",
                attrs = mapOf(
                    "id" to normalizedId,
                    "source" to envelope.source.name.lowercase(Locale.US),
                    "eventClass" to eventClass.name,
                    "ingressQueueDepth" to ingressQueueDepth.get().toString()
                )
            )
            return
        }

        when (_feedState.value.availabilityState) {
            AvailabilityState.UNKNOWN -> {
                pendingQueue.enqueue(PendingBroadcast(trip = trip, receivedAtMs = envelope.receivedAtMs))
                publishState(
                    pendingCount = pendingQueue.size(),
                    availabilityState = _feedState.value.availabilityState,
                    isReconciling = _feedState.value.isReconciling
                )
                _events.tryEmit(
                    BroadcastCoordinatorEvent.IngressHandled(
                        id = normalizedId,
                        source = envelope.source,
                        decision = BroadcastDecision.BUFFER
                    )
                )
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_GATED,
                    status = BroadcastStatus.BUFFERED,
                    reason = "availability_unknown",
                    attrs = mapOf(
                        "id" to normalizedId,
                        "eventClass" to eventClass.name,
                        "source" to envelope.source.name.lowercase(Locale.US),
                        "queueDepth" to pendingQueue.size().toString(),
                        "ingressQueueDepth" to ingressQueueDepth.get().toString()
                    )
                )
            }

            AvailabilityState.OFFLINE -> {
                emitDrop(
                    id = normalizedId,
                    source = envelope.source,
                    reason = BroadcastDropReason.AVAILABILITY_OFFLINE,
                    additionalAttrs = mapOf(
                        "eventClass" to eventClass.name,
                        "event" to envelope.rawEventName
                    )
                )
            }

            AvailabilityState.ONLINE -> {
                applyTripAndOverlay(
                    trip = trip,
                    envelope = envelope,
                    normalizedId = normalizedId,
                    eventClass = eventClass
                )
            }
        }
    }

    private fun handleTrucksRemainingUpdate(notification: TrucksRemainingNotification) {
        if (!isEnabled()) return
        val id = notification.orderId.trim()
        if (id.isEmpty()) return
        val updated = stateStore.patchTrucksRemaining(
            broadcastId = id,
            totalTrucks = notification.totalTrucks,
            trucksFilled = notification.trucksFilled,
            trucksRemaining = notification.trucksRemaining,
            terminalStatuses = TERMINAL_ORDER_STATUSES,
            rawStatus = notification.orderStatus
        )
        if (updated != null) {
            val delta = if (notification.trucksRemaining <= 0 ||
                notification.orderStatus.lowercase(Locale.US) in TERMINAL_ORDER_STATUSES
            ) {
                BroadcastStateDelta(removedIds = listOf(id))
            } else {
                BroadcastStateDelta(updated = listOf(updated))
            }
            publishState(
                delta = delta,
                pendingCount = pendingQueue.size(),
                availabilityState = _feedState.value.availabilityState,
                isReconciling = _feedState.value.isReconciling
            )
        }
    }

    private fun flushPendingBuffer() {
        val (valid, expired) = pendingQueue.drainValid()
        expired.forEach {
            emitDrop(
                id = it.trip.broadcastId,
                source = BroadcastIngressSource.BUFFER,
                reason = BroadcastDropReason.AVAILABILITY_UNKNOWN_BUFFER_EXPIRED
            )
        }

        valid.sortedBy { it.receivedAtMs }.forEach { pending ->
            enqueueIngress(
                BroadcastIngressEnvelope(
                    source = BroadcastIngressSource.BUFFER,
                    rawEventName = "buffer_flush",
                    normalizedId = pending.trip.broadcastId,
                    receivedAtMs = pending.receivedAtMs,
                    broadcast = pending.trip
                )
            )
        }
        publishState(
            pendingCount = pendingQueue.size(),
            availabilityState = _feedState.value.availabilityState,
            isReconciling = _feedState.value.isReconciling
        )
    }

    private fun dropPendingBufferForOffline() {
        val (valid, expired) = pendingQueue.drainValid()
        valid.forEach {
            emitDrop(
                id = it.trip.broadcastId,
                source = BroadcastIngressSource.BUFFER,
                reason = BroadcastDropReason.AVAILABILITY_OFFLINE,
                additionalAttrs = mapOf("eventClass" to BroadcastEventClass.NEW_BROADCAST.name)
            )
        }
        expired.forEach {
            emitDrop(
                id = it.trip.broadcastId,
                source = BroadcastIngressSource.BUFFER,
                reason = BroadcastDropReason.AVAILABILITY_UNKNOWN_BUFFER_EXPIRED,
                additionalAttrs = mapOf("eventClass" to BroadcastEventClass.NEW_BROADCAST.name)
            )
        }
        publishState(
            pendingCount = pendingQueue.size(),
            availabilityState = _feedState.value.availabilityState,
            isReconciling = _feedState.value.isReconciling
        )
    }

    private fun applyTripAndOverlay(
        trip: BroadcastTrip,
        envelope: BroadcastIngressEnvelope,
        normalizedId: String,
        eventClass: BroadcastEventClass
    ) {
        val flags = BroadcastFeatureFlagsRegistry.current()
        var delta: BroadcastStateDelta? = null
        if (flags.broadcastLocalDeltaApplyEnabled) {
            val previous = stateStore.upsert(trip)
            delta = if (previous == null) {
                BroadcastStateDelta(added = listOf(trip))
            } else {
                BroadcastStateDelta(updated = listOf(trip))
            }
        }

        publishState(
            delta = delta,
            pendingCount = pendingQueue.size(),
            availabilityState = _feedState.value.availabilityState,
            isReconciling = _feedState.value.isReconciling
        )

        val ingress = BroadcastOverlayManager.showBroadcast(trip)
        when (ingress.action) {
            BroadcastOverlayManager.BroadcastIngressAction.SHOWN -> {
                _events.tryEmit(BroadcastCoordinatorEvent.OverlayShown(normalizedId))
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_OVERLAY_SHOWN,
                    status = BroadcastStatus.SUCCESS,
                    attrs = mapOf(
                        "id" to normalizedId,
                        "source" to envelope.source.name.lowercase(Locale.US),
                        "eventClass" to eventClass.name,
                        "queueDepth" to pendingQueue.size().toString()
                    )
                )
            }

            BroadcastOverlayManager.BroadcastIngressAction.BUFFERED -> {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_OVERLAY_SHOWN,
                    status = BroadcastStatus.BUFFERED,
                    reason = ingress.reason,
                    attrs = mapOf(
                        "id" to normalizedId,
                        "source" to envelope.source.name.lowercase(Locale.US),
                        "eventClass" to eventClass.name,
                        "queueDepth" to pendingQueue.size().toString()
                    )
                )
            }

            BroadcastOverlayManager.BroadcastIngressAction.DROPPED -> {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_OVERLAY_SHOWN,
                    status = BroadcastStatus.DROPPED,
                    reason = ingress.reason,
                    attrs = mapOf(
                        "id" to normalizedId,
                        "source" to envelope.source.name.lowercase(Locale.US),
                        "eventClass" to eventClass.name,
                        "queueDepth" to pendingQueue.size().toString()
                    )
                )
            }
        }

        _events.tryEmit(
            BroadcastCoordinatorEvent.IngressHandled(
                id = normalizedId,
                source = envelope.source,
                decision = BroadcastDecision.SHOW
            )
        )
        BroadcastTelemetry.record(
            stage = BroadcastStage.STATE_APPLIED,
            status = BroadcastStatus.SUCCESS,
            attrs = mapOf(
                "id" to normalizedId,
                "source" to envelope.source.name.lowercase(Locale.US),
                "eventClass" to eventClass.name,
                "queueDepth" to pendingQueue.size().toString(),
                "ingressQueueDepth" to ingressQueueDepth.get().toString()
            )
        )
        requestReconcile(force = false)
        markSeenInRecoveryStore(normalizedId)
        if (flags.captainReconcileSnapshotEnabled) {
            triggerSnapshotReconcile(normalizedId)
        }
    }

    private fun runReconcile(forceRefresh: Boolean) {
        if (!isEnabled()) return
        val context = appContext ?: return
        if (activeReconcileJob?.isActive == true) {
            pendingForceReconcile = pendingForceReconcile || forceRefresh
            pendingReconcileAfterActive = true
            BroadcastTelemetry.record(
                stage = BroadcastStage.RECONCILE_COALESCED,
                status = BroadcastStatus.BUFFERED,
                reason = "reconcile_active",
                attrs = mapOf(
                    "force" to forceRefresh.toString(),
                    "coalesced" to "true",
                    "ingressQueueDepth" to ingressQueueDepth.get().toString()
                )
            )
            return
        }

        val repository = BroadcastRepository.getInstance(context)
        activeReconcileJob = scope.launch {
            try {
                lastFetchStartedAtMs = System.currentTimeMillis()
                publishState(
                    pendingCount = pendingQueue.size(),
                    availabilityState = _feedState.value.availabilityState,
                    isReconciling = true
                )
                _events.tryEmit(BroadcastCoordinatorEvent.ReconcileRequested(forceRefresh))
                BroadcastTelemetry.record(
                    stage = BroadcastStage.RECONCILE_REQUESTED,
                    status = BroadcastStatus.SUCCESS,
                    attrs = mapOf(
                        "force" to forceRefresh.toString(),
                        "coalesced" to pendingReconcileAfterActive.toString(),
                        "ingressQueueDepth" to ingressQueueDepth.get().toString()
                    )
                )

                when (val result = repository.fetchActiveBroadcasts(
                    forceRefresh = forceRefresh,
                    syncCursor = if (forceRefresh) null else lastSyncCursor,
                    queryMode = BroadcastFetchQueryMode.BOOKINGS_REQUESTS_PRIMARY_WITH_BROADCASTS_FALLBACK
                )) {
                    is BroadcastResult.Success -> {
                        lastSyncCursor = result.data.syncCursor ?: lastSyncCursor
                        val before = stateStore.snapshotSorted()
                        val beforeById = before.associateBy { it.broadcastId }
                        val incoming = result.data.broadcasts
                        val incomingIds = incoming.map { it.broadcastId }.toSet()

                        incoming.forEach { trip ->
                            stateStore.upsert(trip)
                        }
                        beforeById.keys
                            .filterNot { it in incomingIds }
                            .forEach { stateStore.removeById(it) }

                        val after = stateStore.snapshotSorted()
                        after.forEach { markSeenInRecoveryStore(it.broadcastId) }
                        val afterById = after.associateBy { it.broadcastId }
                        val added = after.filter { it.broadcastId !in beforeById }
                        val updated = after.filter { current ->
                            val previous = beforeById[current.broadcastId]
                            previous != null && previous != current
                        }
                        val removedIds = beforeById.keys.filterNot { it in afterById }
                        val delta = BroadcastStateDelta(
                            added = added,
                            updated = updated,
                            removedIds = removedIds
                        )

                        publishState(
                            delta = delta,
                            pendingCount = pendingQueue.size(),
                            availabilityState = _feedState.value.availabilityState,
                            isReconciling = false
                        )
                        _events.tryEmit(BroadcastCoordinatorEvent.ReconcileDone(true))
                        BroadcastTelemetry.record(
                            stage = BroadcastStage.RECONCILE_DONE,
                            status = BroadcastStatus.SUCCESS,
                            attrs = mapOf(
                                "count" to after.size.toString(),
                                "syncCursor" to (lastSyncCursor ?: "none")
                            )
                        )
                    }

                    is BroadcastResult.Error -> {
                        publishState(
                            pendingCount = pendingQueue.size(),
                            availabilityState = _feedState.value.availabilityState,
                            isReconciling = false
                        )
                        _events.tryEmit(BroadcastCoordinatorEvent.ReconcileDone(false, result.message))
                        BroadcastTelemetry.record(
                            stage = BroadcastStage.RECONCILE_DONE,
                            status = BroadcastStatus.FAILED,
                            reason = result.message
                        )
                    }

                    is BroadcastResult.Loading -> Unit
                }
            } finally {
                activeReconcileJob = null
                if (pendingReconcileAfterActive) {
                    pendingReconcileAfterActive = false
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.RECONCILE_FOLLOWUP_EXECUTED,
                        status = BroadcastStatus.SUCCESS,
                        attrs = mapOf(
                            "force" to "true",
                            "ingressQueueDepth" to ingressQueueDepth.get().toString()
                        )
                    )
                    requestReconcile(force = true)
                }
            }
        }
    }

    private fun removeEverywhere(id: String, reason: String, emitDismissEvent: Boolean = true) {
        if (id.isBlank()) return
        pendingQueue.removeById(id)
        stateStore.removeById(id)
        val flags = BroadcastFeatureFlagsRegistry.current()
        if (flags.broadcastOverlayInvariantEnforcementEnabled) {
            BroadcastOverlayManager.removeEverywhere(id)
        } else {
            BroadcastOverlayManager.removeBroadcast(id)
        }
        publishState(
            delta = BroadcastStateDelta(removedIds = listOf(id)),
            pendingCount = pendingQueue.size(),
            availabilityState = _feedState.value.availabilityState,
            isReconciling = _feedState.value.isReconciling
        )
        if (emitDismissEvent) {
            _events.tryEmit(BroadcastCoordinatorEvent.Dismissed(id, reason))
        }
        consumeDismissStart(id)?.let { startedAtMs ->
            BroadcastTelemetry.recordLatency(
                name = "cancel_to_dismiss_ms",
                ms = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L),
                attrs = mapOf(
                    "normalizedId" to id,
                    "removeReason" to reason
                )
            )
        }
        BroadcastTelemetry.record(
            stage = BroadcastStage.DISMISSED,
            status = BroadcastStatus.SUCCESS,
            reason = reason,
            attrs = mapOf(
                "id" to id,
                "normalizedId" to id,
                "removeReason" to reason
            )
        )
        requestReconcile(force = true)
    }

    private fun emitDrop(
        id: String,
        source: BroadcastIngressSource,
        reason: BroadcastDropReason,
        additionalAttrs: Map<String, String> = emptyMap()
    ) {
        _events.tryEmit(BroadcastCoordinatorEvent.Dropped(id = id, source = source, reason = reason))
        val attrs = mutableMapOf(
            "id" to id.ifBlank { "none" },
            "normalizedId" to id.ifBlank { "none" },
            "source" to source.name.lowercase(Locale.US),
            "ingressSource" to source.name.lowercase(Locale.US),
            "dropReason" to reason.name.lowercase(Locale.US),
            "ingressQueueDepth" to ingressQueueDepth.get().toString()
        )
        attrs.putAll(additionalAttrs)
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_GATED,
            status = BroadcastStatus.DROPPED,
            reason = reason.name.lowercase(Locale.US),
            attrs = attrs
        )
        timber.log.Timber.w(
            "⚠️ Broadcast dropped id=%s source=%s reason=%s attrs=%s",
            id.ifBlank { "none" },
            source.name.lowercase(Locale.US),
            reason.name.lowercase(Locale.US),
            attrs
        )
    }

    private fun publishState(
        delta: BroadcastStateDelta? = null,
        pendingCount: Int = _feedState.value.pendingCount,
        availabilityState: AvailabilityState = _feedState.value.availabilityState,
        isReconciling: Boolean = _feedState.value.isReconciling
    ) {
        _feedState.value = BroadcastFeedState(
            broadcasts = stateStore.snapshotSorted(),
            pendingCount = pendingCount,
            availabilityState = availabilityState,
            isReconciling = isReconciling,
            lastUpdatedMs = System.currentTimeMillis(),
            lastDelta = delta
        )
    }

    private fun dedupeKey(
        eventClass: BroadcastEventClass,
        envelope: BroadcastIngressEnvelope,
        normalizedId: String
    ): String {
        val semanticVersion = envelope.payloadVersion?.takeIf { it.isNotBlank() } ?: "v0"
        return "${eventClass.name}|$normalizedId|$semanticVersion"
    }

    private fun markSeenInRecoveryStore(broadcastId: String) {
        val context = appContext ?: return
        BroadcastRecoveryTracker.markSeen(context, broadcastId)
    }

    private fun shouldRunSnapshotReconcile(broadcastId: String, nowMs: Long): Boolean {
        synchronized(snapshotReconcileLock) {
            val lastRun = snapshotReconcileAtMs[broadcastId] ?: 0L
            if (nowMs - lastRun < SNAPSHOT_RECONCILE_COOLDOWN_MS) {
                return false
            }
            snapshotReconcileAtMs[broadcastId] = nowMs
            if (snapshotReconcileAtMs.size > 1_024) {
                val iterator = snapshotReconcileAtMs.entries.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            return true
        }
    }

    private fun isSnapshotTerminalState(state: String): Boolean {
        return when (state.lowercase(Locale.US)) {
            "cancelled", "canceled", "expired", "accepted", "completed", "fully_filled", "closed" -> true
            else -> false
        }
    }

    private fun triggerSnapshotReconcile(broadcastId: String) {
        if (broadcastId.isBlank()) return
        val context = appContext ?: return
        val nowMs = System.currentTimeMillis()
        if (!shouldRunSnapshotReconcile(broadcastId, nowMs)) return

        scope.launch {
            val repository = BroadcastRepository.getInstance(context)
            when (val snapshotResult = repository.getBroadcastSnapshot(broadcastId)) {
                is BroadcastResult.Success -> {
                    val snapshot = snapshotResult.data
                    lastSyncCursor = snapshot.syncCursor ?: lastSyncCursor
                    val state = snapshot.state.lowercase(Locale.US)
                    if (isSnapshotTerminalState(state)) {
                        addCancellationTombstone(broadcastId, "v${snapshot.eventVersion}")
                        removeEverywhere(
                            id = broadcastId,
                            reason = "snapshot_$state",
                            emitDismissEvent = true
                        )
                        return@launch
                    }

                    val snapshotTrip = snapshot.broadcast ?: run {
                        requestReconcile(force = true)
                        return@launch
                    }
                    val previous = stateStore.upsert(snapshotTrip)
                    val delta = if (previous == null) {
                        BroadcastStateDelta(added = listOf(snapshotTrip))
                    } else if (previous != snapshotTrip) {
                        BroadcastStateDelta(updated = listOf(snapshotTrip))
                    } else {
                        null
                    }
                    publishState(
                        delta = delta,
                        pendingCount = pendingQueue.size(),
                        availabilityState = _feedState.value.availabilityState,
                        isReconciling = _feedState.value.isReconciling
                    )
                    BroadcastOverlayManager.upsertBroadcastData(snapshotTrip)
                    markSeenInRecoveryStore(snapshotTrip.broadcastId)
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.RECONCILE_DONE,
                        status = BroadcastStatus.SUCCESS,
                        reason = "snapshot_reconciled",
                        attrs = mapOf(
                            "id" to snapshotTrip.broadcastId,
                            "syncCursor" to (snapshot.syncCursor ?: "none"),
                            "dispatchState" to snapshot.dispatchState
                        )
                    )
                }

                is BroadcastResult.Error -> {
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.RECONCILE_DONE,
                        status = BroadcastStatus.FAILED,
                        reason = "snapshot_failed",
                        attrs = mapOf(
                            "id" to broadcastId,
                            "error" to snapshotResult.message
                        )
                    )
                }

                is BroadcastResult.Loading -> Unit
            }
        }
    }

    private fun startDismissSequence(
        id: String,
        reason: String,
        message: String,
        customerName: String
    ) {
        if (id.isBlank()) return
        markDismissStart(id)
        _dismissed.tryEmit(
            BroadcastDismissedNotification(
                broadcastId = id,
                reason = reason,
                message = message,
                customerName = customerName
            )
        )
        _events.tryEmit(BroadcastCoordinatorEvent.Dismissed(id, reason))
        scope.launch {
            delay(dismissWindowMs())
            removeEverywhere(id = id, reason = reason, emitDismissEvent = false)
        }
    }

    private fun toEventClass(rawEventName: String): BroadcastEventClass {
        val normalized = rawEventName.lowercase(Locale.US)
        return when {
            normalized == SocketIOService.Events.NEW_BROADCAST.lowercase(Locale.US) ||
                normalized == SocketIOService.Events.NEW_ORDER_ALERT.lowercase(Locale.US) ||
                normalized == WeeloFirebaseService.TYPE_NEW_TRUCK_REQUEST ||
                normalized == WeeloEventNames.NOTIFICATION_OPEN ||
                normalized == WeeloEventNames.BUFFER_FLUSH -> BroadcastEventClass.NEW_BROADCAST

            normalized == SocketIOService.Events.ORDER_CANCELLED.lowercase(Locale.US) ||
                normalized == WeeloEventNames.BOOKING_CANCELLED ||
                normalized == WeeloEventNames.BROADCAST_DISMISSED -> BroadcastEventClass.CANCEL

            normalized == SocketIOService.Events.ORDER_EXPIRED.lowercase(Locale.US) ||
                normalized == WeeloEventNames.ORDER_EXPIRED ||
                normalized == WeeloEventNames.BOOKING_EXPIRED ||
                normalized == WeeloEventNames.BROADCAST_EXPIRED -> BroadcastEventClass.EXPIRE

            normalized == SocketIOService.Events.TRUCKS_REMAINING_UPDATE.lowercase(Locale.US) -> BroadcastEventClass.TRUCKS_REMAINING
            else -> BroadcastEventClass.OTHER
        }
    }

    private fun tombstoneKey(id: String, payloadVersion: String?): String {
        val normalizedId = id.trim()
        val normalizedVersion = payloadVersion?.trim().orEmpty()
        return if (normalizedVersion.isBlank()) normalizedId else "$normalizedId|$normalizedVersion"
    }

    private fun buildCancellationVersionToken(eventVersion: Int?, serverTimeMs: Long?): String? {
        if (eventVersion == null || serverTimeMs == null || serverTimeMs <= 0L) return null
        return "v${eventVersion}@${serverTimeMs}"
    }

    private fun addCancellationTombstone(
        id: String,
        payloadVersion: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (id.isBlank()) return
        synchronized(tombstoneLock) {
            pruneExpiredTombstonesLocked(nowMs)
            val key = tombstoneKey(id, payloadVersion)
            cancellationTombstones[key] = nowMs
            // Keep base-key tombstone for backward compatibility payloads that
            // do not include version metadata.
            cancellationTombstones[id] = nowMs
            while (cancellationTombstones.size > CANCELLATION_TOMBSTONE_MAX_SIZE) {
                val iterator = cancellationTombstones.entries.iterator()
                if (!iterator.hasNext()) break
                iterator.next()
                iterator.remove()
            }
        }
    }

    private fun hasCancellationTombstone(
        id: String,
        payloadVersion: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (id.isBlank()) return false
        synchronized(tombstoneLock) {
            pruneExpiredTombstonesLocked(nowMs)
            val exactKey = tombstoneKey(id, payloadVersion)
            val exactTs = cancellationTombstones[exactKey]
            if (exactTs != null && nowMs - exactTs <= CANCELLATION_TOMBSTONE_TTL_MS) {
                return true
            }
            val fallbackTs = cancellationTombstones[id] ?: return false
            return nowMs - fallbackTs <= CANCELLATION_TOMBSTONE_TTL_MS
        }
    }

    private fun clearCancellationTombstones() {
        synchronized(tombstoneLock) {
            cancellationTombstones.clear()
        }
    }

    private fun markDismissStart(id: String, nowMs: Long = System.currentTimeMillis()) {
        if (id.isBlank()) return
        synchronized(dismissLatencyLock) {
            dismissLatencyById[id] = nowMs
            while (dismissLatencyById.size > DISMISS_LATENCY_TRACK_MAX_SIZE) {
                val iterator = dismissLatencyById.entries.iterator()
                if (!iterator.hasNext()) break
                iterator.next()
                iterator.remove()
            }
        }
    }

    private fun consumeDismissStart(id: String): Long? {
        if (id.isBlank()) return null
        synchronized(dismissLatencyLock) {
            return dismissLatencyById.remove(id)
        }
    }

    private fun clearDismissLatencyTracking() {
        synchronized(dismissLatencyLock) {
            dismissLatencyById.clear()
        }
    }

    private fun pruneExpiredTombstonesLocked(nowMs: Long) {
        val iterator = cancellationTombstones.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMs - entry.value > CANCELLATION_TOMBSTONE_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun dismissWindowMs(): Long {
        return BroadcastUiTiming.DISMISS_ENTER_MS.toLong() +
            BroadcastUiTiming.DISMISS_HOLD_MS +
            BroadcastUiTiming.DISMISS_EXIT_MS.toLong()
    }

    private object WeeloEventNames {
        const val BOOKING_CANCELLED = "booking_cancelled"
        const val ORDER_EXPIRED = "order_expired"
        const val BOOKING_EXPIRED = "booking_expired"
        const val BROADCAST_DISMISSED = "broadcast_dismissed"
        const val BROADCAST_EXPIRED = "broadcast_expired"
        const val NOTIFICATION_OPEN = "notification_open"
        const val BUFFER_FLUSH = "buffer_flush"
    }
}
