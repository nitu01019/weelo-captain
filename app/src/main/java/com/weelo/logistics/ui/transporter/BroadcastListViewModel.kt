package com.weelo.logistics.ui.transporter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.broadcast.BroadcastCoordinatorEvent
import com.weelo.logistics.broadcast.BroadcastFeatureFlagsRegistry
import com.weelo.logistics.broadcast.BroadcastFlowCoordinator
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus as BroadcastTelemetryStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import com.weelo.logistics.broadcast.BroadcastUiTiming
import com.weelo.logistics.data.model.BroadcastStatus
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.remote.BroadcastDismissedNotification
import com.weelo.logistics.data.remote.BroadcastNotification
import com.weelo.logistics.data.remote.SocketConnectionState
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.remote.TrucksRemainingNotification
import com.weelo.logistics.data.repository.BroadcastFetchQueryMode
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.ui.utils.SocketUiEventDeduper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BroadcastListFeedTab(
    val title: String
) {
    ALL("All"),
    NEARBY("Nearby"),
    HIGH_VALUE("High Value"),
    LONG_DISTANCE("Long Distance")
}

data class BroadcastListUiState(
    val broadcasts: List<BroadcastTrip> = emptyList(),
    val visibleBroadcasts: List<BroadcastTrip> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val selectedTab: BroadcastListFeedTab = BroadcastListFeedTab.ALL,
    val isSocketConnected: Boolean = false,
    val dismissedCards: Map<String, BroadcastDismissedNotification> = emptyMap()
)

sealed interface BroadcastListUiEvent {
    data class ShowToast(val message: String) : BroadcastListUiEvent
    data class PlaySound(val urgent: Boolean) : BroadcastListUiEvent
    data object NavigateBackWhenEmpty : BroadcastListUiEvent
}

class BroadcastListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BroadcastRepository.getInstance(application.applicationContext)
    private val socketEventDeduper = SocketUiEventDeduper(maxEntries = 4_096)

    private val _uiState = MutableStateFlow(BroadcastListUiState())
    val uiState: StateFlow<BroadcastListUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<BroadcastListUiEvent>(replay = 0, extraBufferCapacity = 32)
    val uiEvents: SharedFlow<BroadcastListUiEvent> = _uiEvents.asSharedFlow()

    private var activeFetchJob: Job? = null
    private var queuedForceRefresh = false
    private var isScreenActive = false
    private var socketStateJob: Job? = null
    private var newBroadcastEventsJob: Job? = null
    private var trucksRemainingEventsJob: Job? = null
    private var dismissedEventsJob: Job? = null
    private var periodicRefreshJob: Job? = null
    private var scheduledRefreshJob: Job? = null
    private var coordinatorFeedJob: Job? = null
    private var coordinatorEventsJob: Job? = null
    private var coordinatorDismissedJob: Job? = null
    private val ignoreInFlightIds = mutableSetOf<String>()
    private val ignoredRecentlyIds = mutableSetOf<String>()
    private var pendingRefreshForce = false
    private var lastFetchStartedAtMs = 0L
    private var lastToastAtMs = 0L
    private var lastSoundAtMs = 0L
    private var latestSocketState: SocketConnectionState = SocketConnectionState.Disconnected
    private var consecutiveFetchErrors = 0
    private val periodicRefreshJitterMs = (((System.identityHashCode(this) and Int.MAX_VALUE) % 3000) + 500).toLong()

    companion object {
        private const val PERIODIC_REFRESH_CONNECTED_MS = 60_000L
        private const val PERIODIC_REFRESH_RECONNECTING_MS = 20_000L
        private const val PERIODIC_REFRESH_ERROR_MAX_MS = 120_000L
        private const val SOCKET_REFRESH_DEBOUNCE_MS = 350L
        private const val MIN_FETCH_INTERVAL_MS = 1_200L
        private const val EVENT_TOAST_COOLDOWN_MS = 2_500L
        private const val EVENT_SOUND_COOLDOWN_MS = 1_200L
        private const val MAX_RENDER_BROADCASTS = 250
        private val TERMINAL_ORDER_STATUSES = setOf(
            "cancelled",
            "canceled",
            "expired",
            "fully_filled",
            "completed",
            "closed"
        )
    }

    fun onScreenStarted() {
        if (isScreenActive) return
        isScreenActive = true

        if (isCoordinatorEnabled()) {
            BroadcastFlowCoordinator.start()
            observeCoordinatorFeed()
            observeCoordinatorEvents()
            observeCoordinatorDismissed()
            BroadcastFlowCoordinator.requestReconcile(force = true)
            return
        }

        observeSocketState()
        observeSocketBroadcastEvents()
        observeDismissedEvents()
        startPeriodicRefresh()
        requestRefresh(
            forceRefresh = _uiState.value.broadcasts.isNotEmpty(),
            debounceMs = 0L
        )
    }

    fun onScreenStopped() {
        if (!isScreenActive) return
        isScreenActive = false

        socketStateJob?.cancel()
        newBroadcastEventsJob?.cancel()
        trucksRemainingEventsJob?.cancel()
        dismissedEventsJob?.cancel()
        periodicRefreshJob?.cancel()
        scheduledRefreshJob?.cancel()
        coordinatorFeedJob?.cancel()
        coordinatorEventsJob?.cancel()
        coordinatorDismissedJob?.cancel()
        socketStateJob = null
        newBroadcastEventsJob = null
        trucksRemainingEventsJob = null
        dismissedEventsJob = null
        periodicRefreshJob = null
        scheduledRefreshJob = null
        coordinatorFeedJob = null
        coordinatorEventsJob = null
        coordinatorDismissedJob = null
        activeFetchJob?.cancel()
        activeFetchJob = null
        queuedForceRefresh = false
        pendingRefreshForce = false
        ignoreInFlightIds.clear()
        ignoredRecentlyIds.clear()
        _uiState.update { it.copy(isRefreshing = false) }
    }

    override fun onCleared() {
        onScreenStopped()
        super.onCleared()
    }

    fun onTabSelected(tab: BroadcastListFeedTab) {
        _uiState.update { current ->
            val visible = filterBroadcasts(current.broadcasts, tab)
            current.copy(
                selectedTab = tab,
                visibleBroadcasts = visible
            )
        }
    }

    fun onManualRefresh() {
        if (isCoordinatorEnabled()) {
            BroadcastFlowCoordinator.requestReconcile(force = true)
            return
        }
        requestRefresh(forceRefresh = true, debounceMs = 0L)
    }

    fun onIgnoreBroadcast(broadcastId: String) {
        val id = broadcastId.trim()
        if (id.isEmpty()) return
        if (!ignoreInFlightIds.add(id)) return

        val stateSnapshot = _uiState.value
        val ignoredTrip = stateSnapshot.broadcasts.firstOrNull { it.broadcastId == id }
        if (ignoredTrip == null) {
            ignoreInFlightIds.remove(id)
            return
        }

        val dismissNotification = BroadcastDismissedNotification(
            broadcastId = id,
            reason = "ignored",
            message = "Request ignored",
            customerName = ignoredTrip.customerName
        )
        _uiState.update { current ->
            current.copy(
                dismissedCards = current.dismissedCards + (id to dismissNotification)
            )
        }

        viewModelScope.launch {
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_DECLINE_REQUESTED,
                status = BroadcastTelemetryStatus.SUCCESS,
                attrs = mapOf("id" to id, "reason" to "NOT_AVAILABLE")
            )

            try {
                when (val result = repository.declineBroadcast(
                    broadcastId = id,
                    reason = "NOT_AVAILABLE",
                    notes = "Ignored from transporter broadcast list"
                )) {
                    is BroadcastResult.Success -> {
                        delay(BroadcastUiTiming.DISMISS_HOLD_MS + BroadcastUiTiming.DISMISS_EXIT_MS)
                        ignoredRecentlyIds.add(id)
                        _uiState.update { current ->
                            val updatedBroadcasts = current.broadcasts.filterNot { it.broadcastId == id }
                            current.copy(
                                broadcasts = updatedBroadcasts,
                                visibleBroadcasts = filterBroadcasts(updatedBroadcasts, current.selectedTab),
                                dismissedCards = current.dismissedCards - id
                            )
                        }

                        BroadcastTelemetry.record(
                            stage = BroadcastStage.BROADCAST_DECLINE_SUCCESS,
                            status = BroadcastTelemetryStatus.SUCCESS,
                            attrs = mapOf("id" to id)
                        )
                        if (isCoordinatorEnabled()) {
                            BroadcastFlowCoordinator.requestReconcile(force = true)
                        } else {
                            requestRefresh(forceRefresh = true, debounceMs = 0L)
                        }
                        if (_uiState.value.broadcasts.isEmpty()) {
                            _uiEvents.tryEmit(BroadcastListUiEvent.NavigateBackWhenEmpty)
                        }
                        delay(MIN_FETCH_INTERVAL_MS)
                        ignoredRecentlyIds.remove(id)
                        ignoreInFlightIds.remove(id)
                    }

                    is BroadcastResult.Error -> {
                        rollbackIgnoredBroadcast(
                            id = id,
                            message = result.message,
                            code = result.code,
                            apiCode = result.apiCode
                        )
                    }

                    is BroadcastResult.Loading -> {
                        rollbackIgnoredBroadcast(
                            id = id,
                            message = "Decline request did not complete",
                            code = null,
                            apiCode = null
                        )
                    }
                }
            } catch (t: Throwable) {
                rollbackIgnoredBroadcast(
                    id = id,
                    message = t.message ?: "Failed to ignore request",
                    code = null,
                    apiCode = null
                )
            }
        }
    }

    private fun observeCoordinatorFeed() {
        coordinatorFeedJob?.cancel()
        coordinatorFeedJob = viewModelScope.launch {
            BroadcastFlowCoordinator.feedState.collect { feed ->
                val sorted = applyIgnoredRecentlyFilter(sortBroadcasts(feed.broadcasts))
                _uiState.update { current ->
                    current.copy(
                        broadcasts = sorted,
                        visibleBroadcasts = filterBroadcasts(sorted, current.selectedTab),
                        isLoading = sorted.isEmpty() && feed.isReconciling,
                        isRefreshing = feed.isReconciling && sorted.isNotEmpty(),
                        errorMessage = null,
                        isSocketConnected = feed.availabilityState != com.weelo.logistics.offline.AvailabilityState.OFFLINE
                    )
                }
            }
        }
    }

    private fun observeCoordinatorEvents() {
        coordinatorEventsJob?.cancel()
        coordinatorEventsJob = viewModelScope.launch {
            BroadcastFlowCoordinator.events.collect { event ->
                when (event) {
                    is BroadcastCoordinatorEvent.IngressHandled -> {
                        if (event.decision == com.weelo.logistics.broadcast.BroadcastDecision.SHOW) {
                            val now = System.currentTimeMillis()
                            if (now - lastSoundAtMs >= EVENT_SOUND_COOLDOWN_MS) {
                                _uiEvents.tryEmit(BroadcastListUiEvent.PlaySound(urgent = true))
                                lastSoundAtMs = now
                            }
                        }
                    }

                    is BroadcastCoordinatorEvent.ReconcileDone -> {
                        if (!event.success && !event.reason.isNullOrBlank()) {
                            _uiState.update { it.copy(errorMessage = event.reason) }
                        }
                    }

                    is BroadcastCoordinatorEvent.Dismissed -> Unit

                    is BroadcastCoordinatorEvent.Dropped -> Unit
                    is BroadcastCoordinatorEvent.OverlayShown -> Unit
                    is BroadcastCoordinatorEvent.ReconcileRequested -> Unit
                }
            }
        }
    }

    private fun observeCoordinatorDismissed() {
        coordinatorDismissedJob?.cancel()
        coordinatorDismissedJob = viewModelScope.launch {
            BroadcastFlowCoordinator.dismissed.collect { notification ->
                handleDismissedCard(notification)
            }
        }
    }

    private fun observeSocketState() {
        socketStateJob?.cancel()
        socketStateJob = viewModelScope.launch {
            var wasConnected = false
            var isFirstEmission = true
            SocketIOService.connectionState.collect { state ->
                latestSocketState = state
                val connected = state is SocketConnectionState.Connected
                _uiState.update { it.copy(isSocketConnected = connected) }
                if (!isFirstEmission && connected && !wasConnected) {
                    requestRefresh(forceRefresh = true, debounceMs = 0L)
                }
                isFirstEmission = false
                wasConnected = connected
            }
        }
    }

    private fun observeSocketBroadcastEvents() {
        newBroadcastEventsJob?.cancel()
        newBroadcastEventsJob = viewModelScope.launch {
            SocketIOService.newBroadcasts.collect { notification ->
                val dedupeKey = buildNewBroadcastDedupeKey(notification)
                if (!socketEventDeduper.shouldHandle(dedupeKey)) return@collect

                applyIncomingBroadcast(notification)
                emitEventFeedback(notification)
                requestRefresh(forceRefresh = true, debounceMs = SOCKET_REFRESH_DEBOUNCE_MS)
            }
        }

        trucksRemainingEventsJob?.cancel()
        trucksRemainingEventsJob = viewModelScope.launch {
            SocketIOService.trucksRemainingUpdates.collect { notification ->
                val dedupeKey = buildTrucksRemainingDedupeKey(notification)
                if (!socketEventDeduper.shouldHandle(dedupeKey)) return@collect
                applyTrucksRemainingUpdate(notification)
                requestRefresh(forceRefresh = false, debounceMs = SOCKET_REFRESH_DEBOUNCE_MS)
            }
        }
    }

    private fun observeDismissedEvents() {
        dismissedEventsJob?.cancel()
        dismissedEventsJob = viewModelScope.launch {
            SocketIOService.broadcastDismissed.collect { notification ->
                val dedupeKey = buildDismissedDedupeKey(notification)
                if (!socketEventDeduper.shouldHandle(dedupeKey)) return@collect
                handleDismissedCard(notification)
            }
        }
    }

    private fun resolveSocketMetaDedupeKey(
        eventId: String?,
        eventVersion: Int?,
        serverTimeMs: Long?
    ): String? {
        val normalizedEventId = eventId?.trim().orEmpty()
        if (normalizedEventId.isNotEmpty()) {
            return "id:$normalizedEventId"
        }
        if (eventVersion != null && serverTimeMs != null && serverTimeMs > 0L) {
            return "v$eventVersion@$serverTimeMs"
        }
        return null
    }

    private fun buildNewBroadcastDedupeKey(notification: BroadcastNotification): String {
        val socketMetaKey = resolveSocketMetaDedupeKey(
            eventId = notification.eventId,
            eventVersion = notification.eventVersion,
            serverTimeMs = notification.serverTimeMs
        )
        return if (socketMetaKey != null) {
            "new_broadcast|${notification.broadcastId}|$socketMetaKey"
        } else {
            "new_broadcast|${notification.broadcastId}|${notification.expiresAt}"
        }
    }

    private fun buildTrucksRemainingDedupeKey(notification: TrucksRemainingNotification): String {
        val socketMetaKey = resolveSocketMetaDedupeKey(
            eventId = notification.eventId,
            eventVersion = notification.eventVersion,
            serverTimeMs = notification.serverTimeMs
        )
        return if (socketMetaKey != null) {
            "trucks_remaining|${notification.orderId}|${notification.vehicleType}|$socketMetaKey"
        } else {
            "trucks_remaining|${notification.orderId}|${notification.vehicleType}|${notification.trucksRemaining}|${notification.orderStatus}"
        }
    }

    private fun buildDismissedDedupeKey(notification: BroadcastDismissedNotification): String {
        val socketMetaKey = resolveSocketMetaDedupeKey(
            eventId = notification.eventId,
            eventVersion = notification.eventVersion,
            serverTimeMs = notification.serverTimeMs
        )
        return if (socketMetaKey != null) {
            "dismissed|${notification.broadcastId}|$socketMetaKey"
        } else {
            "dismissed|${notification.broadcastId}|${notification.reason}|${notification.message}"
        }
    }

    private fun handleDismissedCard(notification: BroadcastDismissedNotification) {
        val broadcastId = notification.broadcastId.takeIf { it.isNotBlank() } ?: return
        val stateSnapshot = _uiState.value
        val exists = stateSnapshot.broadcasts.any { it.broadcastId == broadcastId }
        if (!exists) return

        val wasLastCard = stateSnapshot.broadcasts.size == 1
        _uiState.update {
            it.copy(dismissedCards = it.dismissedCards + (broadcastId to notification))
        }

        viewModelScope.launch {
            delay(BroadcastUiTiming.DISMISS_HOLD_MS + BroadcastUiTiming.DISMISS_EXIT_MS)
            _uiState.update {
                it.copy(dismissedCards = it.dismissedCards - broadcastId)
            }
            if (!isScreenActive) return@launch
            requestRefresh(forceRefresh = true, debounceMs = 0L)
            if (wasLastCard) {
                _uiEvents.tryEmit(BroadcastListUiEvent.NavigateBackWhenEmpty)
            }
        }
    }

    private fun startPeriodicRefresh() {
        periodicRefreshJob?.cancel()
        periodicRefreshJob = viewModelScope.launch {
            while (isScreenActive) {
                delay(resolvePeriodicRefreshIntervalMs())
                requestRefresh(forceRefresh = true, debounceMs = 0L)
            }
        }
    }

    private fun resolvePeriodicRefreshIntervalMs(): Long {
        val baseInterval = when (latestSocketState) {
            is SocketConnectionState.Connected -> PERIODIC_REFRESH_CONNECTED_MS
            is SocketConnectionState.Connecting -> PERIODIC_REFRESH_RECONNECTING_MS
            is SocketConnectionState.Disconnected -> PERIODIC_REFRESH_RECONNECTING_MS
            is SocketConnectionState.Error -> PERIODIC_REFRESH_RECONNECTING_MS
        }
        val adjustedBase = if (consecutiveFetchErrors <= 0) {
            baseInterval
        } else {
            val multiplier = 1L shl consecutiveFetchErrors.coerceAtMost(3)
            (baseInterval * multiplier).coerceAtMost(PERIODIC_REFRESH_ERROR_MAX_MS)
        }
        return (adjustedBase + periodicRefreshJitterMs).coerceAtMost(PERIODIC_REFRESH_ERROR_MAX_MS)
    }

    private fun requestRefresh(
        forceRefresh: Boolean,
        debounceMs: Long = SOCKET_REFRESH_DEBOUNCE_MS
    ) {
        if (isCoordinatorEnabled()) {
            BroadcastFlowCoordinator.requestReconcile(force = forceRefresh)
            return
        }
        if (!isScreenActive) return

        pendingRefreshForce = pendingRefreshForce || forceRefresh
        if (scheduledRefreshJob?.isActive == true) return

        scheduledRefreshJob = viewModelScope.launch {
            val elapsedSinceLastFetch = System.currentTimeMillis() - lastFetchStartedAtMs
            val rateLimitDelayMs = (MIN_FETCH_INTERVAL_MS - elapsedSinceLastFetch).coerceAtLeast(0L)
            val effectiveDelayMs = maxOf(debounceMs, rateLimitDelayMs)
            if (effectiveDelayMs > 0L) {
                delay(effectiveDelayMs)
            }

            scheduledRefreshJob = null
            val forceNow = pendingRefreshForce
            pendingRefreshForce = false
            fetchBroadcasts(forceRefresh = forceNow)
        }
    }

    private fun fetchBroadcasts(forceRefresh: Boolean) {
        if (!isScreenActive) return
        if (activeFetchJob?.isActive == true) {
            if (forceRefresh) {
                queuedForceRefresh = true
            }
            return
        }

        activeFetchJob = viewModelScope.launch {
            try {
                lastFetchStartedAtMs = System.currentTimeMillis()
                _uiState.update {
                    val hasExistingData = it.broadcasts.isNotEmpty()
                    it.copy(
                        isLoading = !hasExistingData,
                        isRefreshing = forceRefresh && hasExistingData,
                        errorMessage = null
                    )
                }

                val result = repository.fetchActiveBroadcasts(
                    forceRefresh = forceRefresh,
                    queryMode = BroadcastFetchQueryMode.BOOKINGS_REQUESTS_PRIMARY_WITH_BROADCASTS_FALLBACK
                )

                when (result) {
                    is BroadcastResult.Success -> {
                        consecutiveFetchErrors = 0
                        val sorted = applyIgnoredRecentlyFilter(sortBroadcasts(result.data.broadcasts))
                        _uiState.update { current ->
                            current.copy(
                                broadcasts = sorted,
                                visibleBroadcasts = filterBroadcasts(sorted, current.selectedTab),
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = null
                            )
                        }
                    }

                    is BroadcastResult.Error -> {
                        consecutiveFetchErrors = (consecutiveFetchErrors + 1).coerceAtMost(6)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = result.message
                            )
                        }
                    }

                    is BroadcastResult.Loading -> Unit
                }
            } finally {
                activeFetchJob = null
                if (queuedForceRefresh && isScreenActive) {
                    queuedForceRefresh = false
                    requestRefresh(forceRefresh = true, debounceMs = 0L)
                } else {
                    queuedForceRefresh = false
                }
            }
        }
    }

    private fun emitEventFeedback(notification: BroadcastNotification) {
        val now = System.currentTimeMillis()
        if (now - lastSoundAtMs >= EVENT_SOUND_COOLDOWN_MS) {
            _uiEvents.tryEmit(BroadcastListUiEvent.PlaySound(urgent = notification.isUrgent))
            lastSoundAtMs = now
        }
        if (now - lastToastAtMs >= EVENT_TOAST_COOLDOWN_MS) {
            _uiEvents.tryEmit(
                BroadcastListUiEvent.ShowToast(
                    message = "New request: ${notification.pickupCity} → ${notification.dropCity}"
                )
            )
            lastToastAtMs = now
        }
    }

    private fun applyIncomingBroadcast(notification: BroadcastNotification) {
        val incomingTrip = notification.toBroadcastTrip()
        _uiState.update { current ->
            val merged = applyIgnoredRecentlyFilter(mergeIncomingTrip(current.broadcasts, incomingTrip))
            current.copy(
                broadcasts = merged,
                visibleBroadcasts = filterBroadcasts(merged, current.selectedTab),
                isLoading = false,
                errorMessage = null
            )
        }
    }

    private fun mergeIncomingTrip(
        current: List<BroadcastTrip>,
        incoming: BroadcastTrip
    ): List<BroadcastTrip> {
        val withoutDuplicate = current.filterNot { it.broadcastId == incoming.broadcastId }
        return sortBroadcasts(listOf(incoming) + withoutDuplicate)
    }

    private fun applyTrucksRemainingUpdate(notification: TrucksRemainingNotification) {
        val normalizedOrderId = notification.orderId.trim()
        if (normalizedOrderId.isEmpty()) return

        val normalizedStatus = notification.orderStatus.trim().lowercase()
        val shouldRemove = notification.trucksRemaining <= 0 || normalizedStatus in TERMINAL_ORDER_STATUSES

        _uiState.update { current ->
            val updatedBroadcasts = if (shouldRemove) {
                current.broadcasts.filterNot { it.broadcastId == normalizedOrderId }
            } else {
                current.broadcasts.map { broadcast ->
                    if (broadcast.broadcastId != normalizedOrderId) {
                        broadcast
                    } else {
                        val resolvedTotalNeeded = notification.totalTrucks.takeIf { it > 0 } ?: broadcast.totalTrucksNeeded
                        val resolvedFilled = notification.trucksFilled
                            .coerceAtLeast(0)
                            .coerceAtMost(resolvedTotalNeeded.coerceAtLeast(0))
                        val resolvedRemaining = notification.trucksRemaining
                            .coerceAtLeast(0)
                            .coerceAtMost(resolvedTotalNeeded.coerceAtLeast(0))

                        val resolvedStatus = when {
                            resolvedRemaining <= 0 -> BroadcastStatus.FULLY_FILLED
                            resolvedFilled > 0 -> BroadcastStatus.PARTIALLY_FILLED
                            else -> BroadcastStatus.ACTIVE
                        }

                        broadcast.copy(
                            totalTrucksNeeded = resolvedTotalNeeded,
                            trucksFilledSoFar = resolvedFilled,
                            trucksStillNeeded = resolvedRemaining,
                            status = resolvedStatus
                        )
                    }
                }
            }

            current.copy(
                broadcasts = applyIgnoredRecentlyFilter(updatedBroadcasts),
                visibleBroadcasts = filterBroadcasts(
                    applyIgnoredRecentlyFilter(updatedBroadcasts),
                    current.selectedTab
                )
            )
        }
    }

    private fun filterBroadcasts(
        broadcasts: List<BroadcastTrip>,
        tab: BroadcastListFeedTab
    ): List<BroadcastTrip> {
        return broadcasts.filter { broadcast ->
            when (tab) {
                BroadcastListFeedTab.ALL -> true
                BroadcastListFeedTab.NEARBY -> broadcast.distance <= 8.0
                BroadcastListFeedTab.HIGH_VALUE -> broadcast.totalFare >= 15_000.0
                BroadcastListFeedTab.LONG_DISTANCE -> broadcast.distance >= 12.0
            }
        }
    }

    private fun sortBroadcasts(broadcasts: List<BroadcastTrip>): List<BroadcastTrip> {
        return broadcasts
            .sortedWith(
                compareByDescending<BroadcastTrip> { it.isUrgent }
                    .thenByDescending { it.broadcastTime }
            )
            .take(MAX_RENDER_BROADCASTS)
    }

    private fun applyIgnoredRecentlyFilter(broadcasts: List<BroadcastTrip>): List<BroadcastTrip> {
        if (ignoredRecentlyIds.isEmpty()) return broadcasts
        return broadcasts.filterNot { ignoredRecentlyIds.contains(it.broadcastId) }
    }

    private fun rollbackIgnoredBroadcast(
        id: String,
        message: String,
        code: Int?,
        apiCode: String?
    ) {
        ignoreInFlightIds.remove(id)
        ignoredRecentlyIds.remove(id)
        _uiState.update { current ->
            current.copy(
                dismissedCards = current.dismissedCards - id
            )
        }
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_DECLINE_FAIL,
            status = BroadcastTelemetryStatus.FAILED,
            reason = message,
            attrs = buildMap {
                put("id", id)
                code?.let { put("code", it.toString()) }
                apiCode?.takeIf { it.isNotBlank() }?.let { put("apiCode", it) }
            }
        )
        _uiEvents.tryEmit(BroadcastListUiEvent.ShowToast(message.ifBlank { "Failed to ignore request" }))
    }

    private fun isCoordinatorEnabled(): Boolean {
        return BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled
    }
}
