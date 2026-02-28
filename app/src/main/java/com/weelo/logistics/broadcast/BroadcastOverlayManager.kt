package com.weelo.logistics.broadcast

import android.content.Context
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.offline.AvailabilityManager
import com.weelo.logistics.offline.AvailabilityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap
import kotlin.math.abs

/**
 * Global manager for transporter broadcast overlay lifecycle.
 *
 * Pipeline:
 * WS receive -> availability gate -> buffer/drop/show -> overlay queue -> accept/reject/expire cleanup.
 */
object BroadcastOverlayManager {

    private const val MAX_ACTIVE_BROADCASTS = 50
    private const val MAX_STARTUP_BUFFER_SIZE = 50
    private const val STARTUP_BUFFER_TTL_MS = 90_000L
    private const val DEFAULT_BROADCAST_TIMEOUT_MS = 60_000L
    private const val DEDUPE_LRU_SIZE = 2_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()

    private var appContext: Context? = null
    private var availabilityObserverJob: Job? = null

    private val broadcastQueue = mutableListOf<QueuedBroadcast>()
    private val startupBufferQueue = ArrayDeque<BufferedBroadcast>()

    private val dedupeLock = Any()
    private val seenBroadcastIds = object : LinkedHashMap<String, Long>(DEDUPE_LRU_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > DEDUPE_LRU_SIZE
        }
    }

    private val _currentBroadcast = MutableStateFlow<BroadcastTrip?>(null)
    val currentBroadcast: StateFlow<BroadcastTrip?> = _currentBroadcast.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _remainingTimeSeconds = MutableStateFlow(0)
    val remainingTimeSeconds: StateFlow<Int> = _remainingTimeSeconds.asStateFlow()

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _totalBroadcastCount = MutableStateFlow(0)
    val totalBroadcastCount: StateFlow<Int> = _totalBroadcastCount.asStateFlow()

    private val _allBroadcasts = mutableListOf<BroadcastTrip>()

    private val _broadcastEvents = MutableSharedFlow<BroadcastEvent>()
    val broadcastEvents: SharedFlow<BroadcastEvent> = _broadcastEvents.asSharedFlow()
    private var countdownTimerJob: Job? = null

    data class BufferedBroadcast(
        val trip: BroadcastTrip,
        val receivedAtMs: Long
    )

    private data class QueuedBroadcast(
        val broadcast: BroadcastTrip,
        val receivedAtMs: Long,
        val expiresAtMs: Long
    ) {
        fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs >= expiresAtMs
        fun remainingTimeMs(nowMs: Long = System.currentTimeMillis()): Long = (expiresAtMs - nowMs).coerceAtLeast(0L)
    }

    enum class BroadcastIngressAction {
        SHOWN,
        BUFFERED,
        DROPPED
    }

    data class BroadcastIngressResult(
        val action: BroadcastIngressAction,
        val reason: String? = null
    )

    private val queuePriorityComparator = Comparator<QueuedBroadcast> { a, b ->
        when {
            a.expiresAtMs != b.expiresAtMs -> a.expiresAtMs.compareTo(b.expiresAtMs)
            a.broadcast.farePerTruck != b.broadcast.farePerTruck -> b.broadcast.farePerTruck.compareTo(a.broadcast.farePerTruck)
            (a.broadcast.eventVersion ?: 0) != (b.broadcast.eventVersion ?: 0) ->
                (b.broadcast.eventVersion ?: 0).compareTo(a.broadcast.eventVersion ?: 0)
            else -> b.broadcast.broadcastTime.compareTo(a.broadcast.broadcastTime)
        }
    }

    sealed class BroadcastEvent {
        data class Accepted(val broadcast: BroadcastTrip) : BroadcastEvent()
        data class Rejected(val broadcast: BroadcastTrip) : BroadcastEvent()
        data class Expired(val broadcast: BroadcastTrip) : BroadcastEvent()
        data class NewBroadcast(val broadcast: BroadcastTrip, val queuePosition: Int) : BroadcastEvent()
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        observeAvailabilityChanges()
        timber.log.Timber.i("✅ BroadcastOverlayManager initialized")
    }

    /**
     * Replay pending startup buffer after socket reconnect.
     */
    fun onSocketReconnected() {
        val state = getAvailabilityState()
        if (state == AvailabilityState.ONLINE) {
            flushBufferedBroadcasts()
        }
    }

    fun showBroadcast(broadcast: BroadcastTrip): BroadcastIngressResult {
        val broadcastId = broadcast.broadcastId.trim()
        val receivedAt = System.currentTimeMillis()

        if (broadcastId.isEmpty()) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_GATED,
                status = BroadcastStatus.DROPPED,
                reason = "missing_id"
            )
            return BroadcastIngressResult(BroadcastIngressAction.DROPPED, reason = "missing_id")
        }

        if (!registerBroadcastIdIfNew(broadcastId)) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_GATED,
                status = BroadcastStatus.DROPPED,
                reason = "duplicate_id",
                attrs = mapOf("broadcastId" to broadcastId)
            )
            return BroadcastIngressResult(BroadcastIngressAction.DROPPED, reason = "duplicate_id")
        }

        val availabilityState = getAvailabilityState()
        return when (availabilityState) {
            AvailabilityState.UNKNOWN -> {
                scope.launch {
                    mutex.withLock {
                        bufferBroadcastLocked(BufferedBroadcast(broadcast, receivedAt))
                    }
                }
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_GATED,
                    status = BroadcastStatus.BUFFERED,
                    reason = "availability_unknown",
                    attrs = mapOf("broadcastId" to broadcastId)
                )
                BroadcastIngressResult(BroadcastIngressAction.BUFFERED, reason = "availability_unknown")
            }

            AvailabilityState.OFFLINE -> {
                unregisterBroadcastId(broadcastId)
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_GATED,
                    status = BroadcastStatus.DROPPED,
                    reason = "availability_offline",
                    attrs = mapOf("broadcastId" to broadcastId)
                )
                BroadcastIngressResult(BroadcastIngressAction.DROPPED, reason = "availability_offline")
            }

            AvailabilityState.ONLINE -> {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_GATED,
                    status = BroadcastStatus.SUCCESS,
                    attrs = mapOf("broadcastId" to broadcastId, "availability" to "online")
                )
                scope.launch {
                    mutex.withLock {
                        showBroadcastOnlineLocked(
                            broadcast = broadcast,
                            receivedAtMs = receivedAt,
                            forceImmediate = true
                        )
                    }
                }
                BroadcastIngressResult(BroadcastIngressAction.SHOWN)
            }
        }
    }

    fun acceptCurrentBroadcast() {
        scope.launch {
            mutex.withLock {
                val current = _currentBroadcast.value ?: return@withLock
                _broadcastEvents.emit(BroadcastEvent.Accepted(current))
                removeEverywhereInternal(current.broadcastId)
            }
        }
    }

    fun rejectCurrentBroadcast() {
        scope.launch {
            mutex.withLock {
                val current = _currentBroadcast.value ?: return@withLock
                _broadcastEvents.emit(BroadcastEvent.Rejected(current))
                removeEverywhereInternal(current.broadcastId)
            }
        }
    }

    fun dismissOverlay() {
        scope.launch {
            mutex.withLock {
                val current = _currentBroadcast.value ?: return@withLock
                val queued = createQueuedBroadcast(current)
                if (!queued.isExpired()) {
                    enqueueQueuedBroadcastLocked(queued)
                }
                hideOverlayInternal()
                updateQueueSizeLocked()
            }
        }
    }

    fun acknowledgeDisplayed(broadcastId: String) {
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_OVERLAY_SHOWN,
            status = BroadcastStatus.SUCCESS,
            attrs = mapOf(
                "broadcastId" to broadcastId,
                "queueDepth" to queueSize.value.toString()
            )
        )
    }

    fun clearAll() {
        scope.launch {
            mutex.withLock {
                broadcastQueue.clear()
                startupBufferQueue.clear()
                _allBroadcasts.clear()
                _currentBroadcast.value = null
                _isOverlayVisible.value = false
                _remainingTimeSeconds.value = 0
                _queueSize.value = 0
                _currentIndex.value = 0
                _totalBroadcastCount.value = 0
                synchronized(dedupeLock) {
                    seenBroadcastIds.clear()
                }
                timber.log.Timber.i("🗑️ All broadcasts cleared")
            }
        }
    }

    fun removeBroadcast(orderId: String) {
        removeEverywhere(orderId)
    }

    fun removeEverywhere(broadcastId: String) {
        if (broadcastId.isBlank()) return
        scope.launch {
            mutex.withLock {
                removeEverywhereInternal(broadcastId)
            }
        }
    }

    /**
     * Patch current/queued broadcast payload with newer canonical snapshot.
     */
    fun upsertBroadcastData(broadcast: BroadcastTrip) {
        val id = broadcast.broadcastId.trim()
        if (id.isEmpty()) return
        scope.launch {
            mutex.withLock {
                val current = _currentBroadcast.value
                if (current?.broadcastId == id) {
                    _currentBroadcast.value = broadcast
                    val queued = createQueuedBroadcast(broadcast, receivedAtMs = current.broadcastTime)
                    _remainingTimeSeconds.value = (queued.remainingTimeMs() / 1000).toInt()
                    startCountdownTimer(queued)
                }

                val allIndex = _allBroadcasts.indexOfFirst { it.broadcastId == id }
                if (allIndex >= 0) {
                    _allBroadcasts[allIndex] = broadcast
                }

                for (index in broadcastQueue.indices) {
                    val queued = broadcastQueue[index]
                    if (queued.broadcast.broadcastId == id) {
                        broadcastQueue[index] = createQueuedBroadcast(
                            broadcast = broadcast,
                            receivedAtMs = queued.receivedAtMs
                        )
                    }
                }
                broadcastQueue.sortWith(queuePriorityComparator)
                updateCurrentIndexLocked()
                updateQueueSizeLocked()
            }
        }
    }

    fun showPreviousBroadcast() {
        scope.launch {
            mutex.withLock {
                if (_allBroadcasts.size <= 1) return@withLock
                val newIndex = if (_currentIndex.value > 0) _currentIndex.value - 1 else _allBroadcasts.lastIndex
                _currentIndex.value = newIndex
                    val broadcast = _allBroadcasts.getOrNull(newIndex)
                    if (broadcast != null) {
                        _currentBroadcast.value = broadcast
                        _isOverlayVisible.value = true
                        _remainingTimeSeconds.value = (createQueuedBroadcast(broadcast).remainingTimeMs() / 1000).toInt()
                        startCountdownTimer(createQueuedBroadcast(broadcast))
                    }
                }
            }
        }

    fun showNextBroadcast() {
        scope.launch {
            mutex.withLock {
                if (_allBroadcasts.size <= 1) return@withLock
                val newIndex = if (_currentIndex.value < _allBroadcasts.lastIndex) _currentIndex.value + 1 else 0
                _currentIndex.value = newIndex
                    val broadcast = _allBroadcasts.getOrNull(newIndex)
                    if (broadcast != null) {
                        _currentBroadcast.value = broadcast
                        _isOverlayVisible.value = true
                        _remainingTimeSeconds.value = (createQueuedBroadcast(broadcast).remainingTimeMs() / 1000).toInt()
                        startCountdownTimer(createQueuedBroadcast(broadcast))
                    }
                }
            }
        }

    fun getQueueInfo(): String {
        return "Queue=${broadcastQueue.size}, StartupBuffer=${startupBufferQueue.size}, Current=${_currentBroadcast.value?.broadcastId}, Visible=${_isOverlayVisible.value}, Active=${_allBroadcasts.size}, Availability=${getAvailabilityState()}"
    }

    private fun observeAvailabilityChanges() {
        val context = appContext ?: return
        availabilityObserverJob?.cancel()
        availabilityObserverJob = scope.launch {
            AvailabilityManager.getInstance(context).availabilityState.collect { state ->
                when (state) {
                    AvailabilityState.UNKNOWN -> {
                        // hold incoming broadcasts in startup buffer
                    }

                    AvailabilityState.OFFLINE -> {
                        dropBufferedBroadcasts(reason = "availability_offline")
                    }

                    AvailabilityState.ONLINE -> {
                        flushBufferedBroadcasts()
                    }
                }
            }
        }
    }

    private fun getAvailabilityState(): AvailabilityState {
        val context = appContext ?: return AvailabilityState.UNKNOWN
        return AvailabilityManager.getInstance(context).availabilityState.value
    }

    private fun registerBroadcastIdIfNew(broadcastId: String): Boolean {
        synchronized(dedupeLock) {
            if (seenBroadcastIds.containsKey(broadcastId)) return false
            seenBroadcastIds[broadcastId] = System.currentTimeMillis()
            return true
        }
    }

    private fun unregisterBroadcastId(broadcastId: String) {
        synchronized(dedupeLock) {
            seenBroadcastIds.remove(broadcastId)
        }
    }

    private fun createQueuedBroadcast(broadcast: BroadcastTrip, receivedAtMs: Long = System.currentTimeMillis()): QueuedBroadcast {
        val now = System.currentTimeMillis()
        val fallbackExpiry = receivedAtMs + DEFAULT_BROADCAST_TIMEOUT_MS
        val broadcastExpiry = broadcast.expiryTime?.takeIf { it > 0L } ?: fallbackExpiry
        val sourceNow = broadcast.serverTimeMs?.takeIf { it > 0L }
            ?: broadcast.broadcastTime.takeIf { it > 0L }
            ?: receivedAtMs
        val canApplyClockOffset = abs(now - sourceNow) <= 30_000L
        val effectiveExpiry = if (canApplyClockOffset) {
            // Align expiry with local clock using server time offset when sourceNow is fresh.
            (broadcastExpiry + (now - sourceNow)).coerceAtLeast(now + 1_000L)
        } else {
            // For stale source timestamps (e.g. createdAt from feed), trust absolute expiry.
            broadcastExpiry.coerceAtLeast(now + 1_000L)
        }
        return QueuedBroadcast(
            broadcast = broadcast,
            receivedAtMs = receivedAtMs,
            expiresAtMs = effectiveExpiry
        )
    }

    private fun isStartupBufferExpired(item: BufferedBroadcast, nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs - item.receivedAtMs > STARTUP_BUFFER_TTL_MS
    }

    private suspend fun bufferBroadcastLocked(item: BufferedBroadcast) {
        val now = System.currentTimeMillis()

        // purge expired entries first
        val expiredIds = mutableListOf<String>()
        val startupExpired = startupBufferQueue.filter {
            val expired = isStartupBufferExpired(it, now)
            if (expired) {
                expiredIds += it.trip.broadcastId
            }
            expired
        }
        startupExpired.forEach { startupBufferQueue.remove(it) }
        expiredIds.forEach { id ->
            unregisterBroadcastId(id)
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_GATED,
                status = BroadcastStatus.DROPPED,
                reason = "availability_unknown_buffer_expired",
                attrs = mapOf("broadcastId" to id)
            )
        }

        if (startupBufferQueue.size >= MAX_STARTUP_BUFFER_SIZE) {
            val evicted = startupBufferQueue.removeFirstOrNull()
            if (evicted != null) {
                unregisterBroadcastId(evicted.trip.broadcastId)
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_GATED,
                    status = BroadcastStatus.DROPPED,
                    reason = "overlay_capacity_limit",
                    attrs = mapOf("broadcastId" to evicted.trip.broadcastId)
                )
            }
        }

        startupBufferQueue.addLast(item)
        updateQueueSizeLocked()
    }

    private fun flushBufferedBroadcasts() {
        scope.launch {
            mutex.withLock {
                var flushed = 0
                while (true) {
                    val item = startupBufferQueue.removeFirstOrNull() ?: break
                    if (isStartupBufferExpired(item)) {
                        unregisterBroadcastId(item.trip.broadcastId)
                        BroadcastTelemetry.record(
                            stage = BroadcastStage.BROADCAST_GATED,
                            status = BroadcastStatus.DROPPED,
                            reason = "availability_unknown_buffer_expired",
                            attrs = mapOf("broadcastId" to item.trip.broadcastId)
                        )
                        continue
                    }
                    val shouldShowImmediately = _currentBroadcast.value == null && flushed == 0
                    showBroadcastOnlineLocked(
                        broadcast = item.trip,
                        receivedAtMs = item.receivedAtMs,
                        forceImmediate = shouldShowImmediately
                    )
                    flushed++
                }
                updateQueueSizeLocked()
                if (flushed > 0) {
                    timber.log.Timber.i("📤 Flushed buffered broadcasts: $flushed")
                }
            }
        }
    }

    private fun dropBufferedBroadcasts(reason: String) {
        scope.launch {
            mutex.withLock {
                while (true) {
                    val item = startupBufferQueue.removeFirstOrNull() ?: break
                    unregisterBroadcastId(item.trip.broadcastId)
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.BROADCAST_GATED,
                        status = BroadcastStatus.DROPPED,
                        reason = reason,
                        attrs = mapOf("broadcastId" to item.trip.broadcastId)
                    )
                }
                updateQueueSizeLocked()
            }
        }
    }

    private suspend fun showBroadcastOnlineLocked(
        broadcast: BroadcastTrip,
        receivedAtMs: Long,
        forceImmediate: Boolean
    ) {
        cleanExpiredQueueLocked()

        val broadcastId = broadcast.broadcastId
        if (_allBroadcasts.any { it.broadcastId == broadcastId } ||
            broadcastQueue.any { it.broadcast.broadcastId == broadcastId }
        ) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_GATED,
                status = BroadcastStatus.DROPPED,
                reason = "duplicate_id",
                attrs = mapOf("broadcastId" to broadcastId)
            )
            return
        }

        if (_allBroadcasts.size >= MAX_ACTIVE_BROADCASTS) {
            val evicted = _allBroadcasts.removeAt(_allBroadcasts.lastIndex)
            broadcastQueue
                .filter { it.broadcast.broadcastId == evicted.broadcastId }
                .forEach { broadcastQueue.remove(it) }
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_GATED,
                status = BroadcastStatus.DROPPED,
                reason = "overlay_capacity_limit",
                attrs = mapOf("broadcastId" to evicted.broadcastId)
            )
        }

        val queued = createQueuedBroadcast(broadcast, receivedAtMs)

        if (!forceImmediate && _currentBroadcast.value != null && _isOverlayVisible.value) {
            _allBroadcasts.add(broadcast)
            _totalBroadcastCount.value = _allBroadcasts.size
            enqueueQueuedBroadcastLocked(queued)
            updateQueueSizeLocked()
            _broadcastEvents.emit(
                BroadcastEvent.NewBroadcast(
                    broadcast = broadcast,
                    queuePosition = queuePositionForLocked(broadcast.broadcastId)
                )
            )
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_GATED,
                status = BroadcastStatus.BUFFERED,
                reason = "active_overlay_queue",
                attrs = mapOf("broadcastId" to broadcast.broadcastId)
            )
            return
        }

        _allBroadcasts.add(0, broadcast)
        _totalBroadcastCount.value = _allBroadcasts.size
        _currentIndex.value = 0

        showBroadcastInternal(queued)

        _broadcastEvents.emit(BroadcastEvent.NewBroadcast(broadcast, 0))

        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_OVERLAY_SHOWN,
            status = BroadcastStatus.SUCCESS,
            attrs = mapOf(
                "broadcastId" to broadcast.broadcastId,
                "queueDepth" to (_allBroadcasts.size - 1).coerceAtLeast(0).toString()
            )
        )
        BroadcastTelemetry.recordLatency(
            name = "broadcast_receive_to_overlay_ms",
            ms = (System.currentTimeMillis() - receivedAtMs).coerceAtLeast(0L),
            attrs = mapOf("broadcastId" to broadcast.broadcastId)
        )

        updateQueueSizeLocked()
    }

    private suspend fun removeEverywhereInternal(broadcastId: String) {
        val currentId = _currentBroadcast.value?.broadcastId

        broadcastQueue.removeAll { it.broadcast.broadcastId == broadcastId }
        startupBufferQueue.removeAll { it.trip.broadcastId == broadcastId }
        _allBroadcasts.removeAll { it.broadcastId == broadcastId }

        _totalBroadcastCount.value = _allBroadcasts.size

        if (currentId == broadcastId) {
            val nextFromAll = _allBroadcasts.firstOrNull()
            if (nextFromAll != null) {
                _currentIndex.value = 0
                showBroadcastInternal(createQueuedBroadcast(nextFromAll))
            } else {
                val nextFromQueue = pollNextQueueBroadcastLocked()
                if (nextFromQueue != null) {
                    if (_allBroadcasts.none { it.broadcastId == nextFromQueue.broadcast.broadcastId }) {
                        _allBroadcasts.add(0, nextFromQueue.broadcast)
                        _totalBroadcastCount.value = _allBroadcasts.size
                    }
                    _currentIndex.value = 0
                    showBroadcastInternal(nextFromQueue)
                } else {
                    hideOverlayInternal()
                }
            }
        } else {
            updateCurrentIndexLocked()
            if (_allBroadcasts.isEmpty() && _currentBroadcast.value == null) {
                hideOverlayInternal()
            }
        }

        updateQueueSizeLocked()
    }

    private suspend fun showBroadcastInternal(queuedBroadcast: QueuedBroadcast) {
        if (queuedBroadcast.isExpired()) {
            _broadcastEvents.emit(BroadcastEvent.Expired(queuedBroadcast.broadcast))
            removeEverywhereInternal(queuedBroadcast.broadcast.broadcastId)
            return
        }

        _currentBroadcast.value = queuedBroadcast.broadcast
        _isOverlayVisible.value = true
        _remainingTimeSeconds.value = (queuedBroadcast.remainingTimeMs() / 1000).toInt()

        startCountdownTimer(queuedBroadcast)
    }

    private fun startCountdownTimer(queuedBroadcast: QueuedBroadcast) {
        countdownTimerJob?.cancel()
        countdownTimerJob = scope.launch {
            while (_currentBroadcast.value?.broadcastId == queuedBroadcast.broadcast.broadcastId) {
                val remainingMs = queuedBroadcast.remainingTimeMs()
                _remainingTimeSeconds.value = (remainingMs / 1000).toInt()

                if (remainingMs <= 0L) {
                    mutex.withLock {
                        _broadcastEvents.emit(BroadcastEvent.Expired(queuedBroadcast.broadcast))
                        removeEverywhereInternal(queuedBroadcast.broadcast.broadcastId)
                    }
                    break
                }
                delay(1000L)
            }
        }
    }

    private fun hideOverlayInternal() {
        countdownTimerJob?.cancel()
        countdownTimerJob = null
        _currentBroadcast.value = null
        _isOverlayVisible.value = false
        _remainingTimeSeconds.value = 0
    }

    private fun cleanExpiredQueueLocked() {
        val now = System.currentTimeMillis()
        val expiredIds = mutableListOf<String>()

        val queueExpired = broadcastQueue.filter {
            val expired = it.isExpired(now)
            if (expired) {
                expiredIds += it.broadcast.broadcastId
            }
            expired
        }
        if (queueExpired.isNotEmpty()) {
            broadcastQueue.removeAll(queueExpired.toSet())
        }

        if (expiredIds.isNotEmpty()) {
            _allBroadcasts.removeAll { broadcast -> expiredIds.contains(broadcast.broadcastId) }
            _totalBroadcastCount.value = _allBroadcasts.size
            expiredIds.forEach { id ->
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_GATED,
                    status = BroadcastStatus.DROPPED,
                    reason = "broadcast_expired",
                    attrs = mapOf("broadcastId" to id)
                )
            }
        }

        val startupExpiredIds = mutableListOf<String>()
        val startupExpired = startupBufferQueue.filter {
            val expired = isStartupBufferExpired(it, now)
            if (expired) {
                startupExpiredIds += it.trip.broadcastId
            }
            expired
        }
        if (startupExpired.isNotEmpty()) {
            startupBufferQueue.removeAll(startupExpired.toSet())
        }
        startupExpiredIds.forEach { id ->
            unregisterBroadcastId(id)
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_GATED,
                status = BroadcastStatus.DROPPED,
                reason = "availability_unknown_buffer_expired",
                attrs = mapOf("broadcastId" to id)
            )
        }

        updateQueueSizeLocked()
    }

    private fun pollNextQueueBroadcastLocked(): QueuedBroadcast? {
        cleanExpiredQueueLocked()
        while (broadcastQueue.isNotEmpty()) {
            val next = broadcastQueue.removeAt(0)
            if (!next.isExpired()) {
                return next
            }
            _allBroadcasts.removeAll { it.broadcastId == next.broadcast.broadcastId }
            _totalBroadcastCount.value = _allBroadcasts.size
        }
        return null
    }

    private fun updateCurrentIndexLocked() {
        val current = _currentBroadcast.value ?: run {
            _currentIndex.value = 0
            return
        }
        val index = _allBroadcasts.indexOfFirst { it.broadcastId == current.broadcastId }
        _currentIndex.value = if (index >= 0) index else 0
    }

    private fun updateQueueSizeLocked() {
        _queueSize.value = broadcastQueue.size + startupBufferQueue.size
    }

    private fun enqueueQueuedBroadcastLocked(queued: QueuedBroadcast) {
        broadcastQueue.removeAll { it.broadcast.broadcastId == queued.broadcast.broadcastId }
        broadcastQueue.add(queued)
        if (BroadcastFeatureFlagsRegistry.current().captainBurstQueueModeEnabled) {
            broadcastQueue.sortWith(queuePriorityComparator)
        }
    }

    private fun queuePositionForLocked(broadcastId: String): Int {
        val idx = broadcastQueue.indexOfFirst { it.broadcast.broadcastId == broadcastId }
        return if (idx >= 0) idx + 1 else broadcastQueue.size
    }
}
