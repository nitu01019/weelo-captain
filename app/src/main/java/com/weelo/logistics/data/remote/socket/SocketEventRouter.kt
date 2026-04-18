package com.weelo.logistics.data.remote.socket

import com.weelo.logistics.BuildConfig
import com.weelo.logistics.broadcast.BroadcastDedupKey
import com.weelo.logistics.broadcast.BroadcastEventClass
import com.weelo.logistics.broadcast.BroadcastFeatureFlagsRegistry
import com.weelo.logistics.broadcast.BroadcastFlowCoordinator
import com.weelo.logistics.broadcast.BroadcastIngressEnvelope
import com.weelo.logistics.broadcast.BroadcastIngressSource
import com.weelo.logistics.broadcast.BroadcastOverlayManager
import com.weelo.logistics.broadcast.BroadcastRolePolicy
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import com.weelo.logistics.broadcast.BroadcastUiTiming
// P10 t1 — F-C-52 codegen consumers (stub imports until codegen lands).
// Gated behind BuildConfig.FF_* flags (default OFF) so legacy handlers keep
// handling traffic during backend-first rollout.
import com.weelo.logistics.contracts.AssignmentStatusChangedEvent
import com.weelo.logistics.contracts.BookingBroadcastV2Event
import com.weelo.logistics.contracts.DispatchAckEvent
import com.weelo.logistics.contracts.OrderProgressEvent
import com.weelo.logistics.data.model.RoutePoint
import com.weelo.logistics.data.model.RoutePointType
import com.weelo.logistics.data.model.TripAssignedNotification
import com.weelo.logistics.data.model.TripLocationInfo
import com.weelo.logistics.BuildConfig
import com.weelo.logistics.data.remote.BroadcastFallbackRoute
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.resolveBroadcastFallbackDecision
import com.weelo.logistics.utils.parseJsonSafe
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.Locale

/** Routes all Socket.IO events to typed SharedFlows. Delegates parsing to [BroadcastPayloadParser]. */
class SocketEventRouter(
    private val serviceScope: CoroutineScope,
    private val connectionManager: SocketConnectionManager,
    private val _newBroadcasts: MutableSharedFlow<BroadcastNotification>,
    private val _truckAssigned: MutableSharedFlow<TruckAssignedNotification>,
    private val _assignmentStatusChanged: MutableSharedFlow<AssignmentStatusNotification>,
    private val _bookingUpdated: MutableSharedFlow<BookingUpdatedNotification>,
    private val _trucksRemainingUpdates: MutableSharedFlow<TrucksRemainingNotification>,
    private val _errors: MutableSharedFlow<SocketError>,
    private val _fleetUpdated: MutableSharedFlow<FleetUpdatedNotification>,
    private val _vehicleRegistered: MutableSharedFlow<VehicleRegisteredNotification>,
    private val _driverAdded: MutableSharedFlow<DriverAddedNotification>,
    private val _driversUpdated: MutableSharedFlow<DriversUpdatedNotification>,
    private val _tripAssigned: MutableSharedFlow<TripAssignedNotification>,
    private val _driverTimeout: MutableSharedFlow<DriverTimeoutNotification>,
    private val _tripCancelled: MutableSharedFlow<TripCancelledNotification>,
    private val _orderCancelled: MutableSharedFlow<OrderCancelledNotification>,
    private val _broadcastDismissed: MutableSharedFlow<BroadcastDismissedNotification>,
    private val _driverStatusChanged: MutableSharedFlow<DriverStatusChangedNotification>,
    private val _driverSOSAlert: MutableSharedFlow<DriverSOSAlertNotification>,
    private val _flexHoldStarted: MutableSharedFlow<FlexHoldStartedNotification>,
    private val _flexHoldExtended: MutableSharedFlow<FlexHoldExtendedNotification>,
    private val _cascadeReassigned: MutableSharedFlow<CascadeReassignedNotification>,
    private val _driverDeclined: MutableSharedFlow<DriverDeclinedNotification>,
    private val _driverRatingUpdated: MutableSharedFlow<DriverRatingUpdatedNotification>,
    private val _driverAccepted: MutableSharedFlow<DriverAcceptedNotification>,
    private val _holdExpired: MutableSharedFlow<HoldExpiredNotification>,
    private val _bookingPartiallyFilled: MutableSharedFlow<BookingPartiallyFilledNotification>,
    private val _requestNoLongerAvailable: MutableSharedFlow<RequestNoLongerAvailableNotification>,
    private val _orderStatusUpdate: MutableSharedFlow<OrderStatusUpdateNotification>,
    private val _driverMayBeOffline: MutableSharedFlow<DriverMayBeOfflineNotification>,
    private val _assignmentStale: MutableSharedFlow<AssignmentStaleNotification>
) {
    /** Delegate for hold/health/progress handlers — extracted to keep this file under 800 lines. */
    private val ext: SocketExtendedEventHandlers by lazy {
        SocketExtendedEventHandlers(
            serviceScope = serviceScope,
            resolveEventId = { data, keys -> BroadcastPayloadParser.resolveEventId(data, *keys) },
            emitHot = { flow, value -> emitHot(flow, value) },
            _flexHoldStarted = _flexHoldStarted,
            _flexHoldExtended = _flexHoldExtended,
            _cascadeReassigned = _cascadeReassigned,
            _driverDeclined = _driverDeclined,
            _driverRatingUpdated = _driverRatingUpdated,
            _driverAccepted = _driverAccepted,
            _holdExpired = _holdExpired,
            _bookingPartiallyFilled = _bookingPartiallyFilled,
            _requestNoLongerAvailable = _requestNoLongerAvailable,
            _orderStatusUpdate = _orderStatusUpdate,
            _driverMayBeOffline = _driverMayBeOffline,
            _assignmentStale = _assignmentStale,
            _broadcastDismissed = _broadcastDismissed
        )
    }

    private val broadcastOverlayWatchdogMs = 2_500L
    private val directBroadcastSocketEvents = setOf("new_broadcast", "new_order_alert", "new_truck_request")
    private val directCancellationSocketEvents = setOf(
        SocketConstants.ORDER_CANCELLED, SocketConstants.BOOKING_CANCELLED,
        SocketConstants.BROADCAST_DISMISSED, SocketConstants.BROADCAST_EXPIRED,
        SocketConstants.BOOKING_EXPIRED, SocketConstants.ORDER_EXPIRED
    )
    private val fallbackBroadcastSocketEvents = setOf("message", "new_booking_request", "broadcast_request", "broadcast_available")
    private val broadcastPayloadTypes = setOf("new_broadcast", "new_truck_request")
    private val cancellationPayloadTypes = setOf(
        SocketConstants.ORDER_CANCELLED, SocketConstants.BOOKING_CANCELLED,
        SocketConstants.BROADCAST_DISMISSED, SocketConstants.BROADCAST_EXPIRED,
        SocketConstants.BOOKING_EXPIRED, SocketConstants.ORDER_EXPIRED
    )

    fun wireEventListeners(socket: Socket) {
        socket.apply {
            // Server confirmation
            on(SocketConstants.CONNECTED) { args ->
                timber.log.Timber.i("\u2705 Server confirmed connection: ${args.firstOrNull()}")
            }

            on(SocketConstants.ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown"
                timber.log.Timber.e("\u274C Server error: $error")
                emitHot(_errors, SocketError(error))
            }

            // Broadcast events
            on(SocketConstants.NEW_BROADCAST) { args -> handleNewBroadcast(args, SocketConstants.NEW_BROADCAST) }
            on(SocketConstants.NEW_TRUCK_REQUEST) { args -> handleNewBroadcast(args, SocketConstants.NEW_TRUCK_REQUEST) }
            on(SocketConstants.NEW_ORDER_ALERT) { args -> handleNewBroadcast(args, SocketConstants.NEW_ORDER_ALERT) }

            // Fallback ingress
            onAnyIncoming(Emitter.Listener { incomingArgs ->
                if (incomingArgs.isEmpty()) return@Listener
                val rawEvent = incomingArgs.firstOrNull()?.toString()?.trim().orEmpty()
                if (rawEvent.isBlank()) return@Listener
                val payloadArgs = incomingArgs.drop(1).filterNotNull().toTypedArray()
                maybeRouteIncomingBroadcastFallback(rawEvent, payloadArgs)
            })

            // Truck / assignment events
            on(SocketConstants.TRUCK_ASSIGNED) { args -> handleTruckAssigned(args) }
            on(SocketConstants.ASSIGNMENT_STATUS_CHANGED) { args -> handleAssignmentStatusChanged(args) }
            on(SocketConstants.BOOKING_UPDATED) { args -> handleBookingUpdated(args) }
            on(SocketConstants.TRUCKS_REMAINING_UPDATE) { args -> handleTrucksRemainingUpdate(args) }
            on(SocketConstants.ACCEPT_CONFIRMATION) { args -> handleAcceptConfirmation(args) }
            on(SocketConstants.BOOKING_EXPIRED) { args -> handleBookingExpired(args) }

            on(SocketConstants.BOOKING_CANCELLED) { args ->
                if (BroadcastFeatureFlagsRegistry.current().captainCanonicalCancelAliasesEnabled) handleOrderCancelled(args)
            }
            on(SocketConstants.BROADCAST_EXPIRED) { args -> handleBookingExpired(args) }
            on(SocketConstants.BROADCAST_DISMISSED) { args ->
                if (BroadcastFeatureFlagsRegistry.current().captainCanonicalCancelAliasesEnabled) handleBookingExpired(args)
            }
            on(SocketConstants.BOOKING_FULLY_FILLED) { args -> handleBookingFullyFilled(args) }

            // Order lifecycle
            on(SocketConstants.ORDER_CANCELLED) { args -> handleOrderCancelled(args) }
            on(SocketConstants.ORDER_EXPIRED) { args -> handleOrderExpired(args) }
            on(SocketConstants.TRIP_CANCELLED) { args -> handleTripCancelled(args) }

            // Fleet / vehicle events
            on(SocketConstants.VEHICLE_REGISTERED) { args -> handleVehicleRegistered(args) }
            on(SocketConstants.VEHICLE_UPDATED) { args -> handleFleetUpdated(args, "updated") }
            on(SocketConstants.VEHICLE_DELETED) { args -> handleFleetUpdated(args, "deleted") }
            on(SocketConstants.VEHICLE_STATUS_CHANGED) { args -> handleFleetUpdated(args, "status_changed") }
            on(SocketConstants.FLEET_UPDATED) { args -> handleFleetUpdated(args, "fleet_updated") }

            // Driver events
            on(SocketConstants.DRIVER_ADDED) { args -> handleDriverAdded(args) }
            on(SocketConstants.DRIVER_UPDATED) { args -> handleDriversUpdated(args, "updated") }
            on(SocketConstants.DRIVER_DELETED) { args -> handleDriversUpdated(args, "deleted") }
            on(SocketConstants.DRIVER_STATUS_CHANGED) { args ->
                handleDriversUpdated(args, "status_changed")
                handleDriverStatusChanged(args)
            }
            on(SocketConstants.DRIVERS_UPDATED) { args -> handleDriversUpdated(args, "drivers_updated") }

            // Trip assignment
            on(SocketConstants.TRIP_ASSIGNED) { args -> handleTripAssigned(args) }
            on(SocketConstants.DRIVER_TIMEOUT) { args -> handleDriverTimeout(args) }

            // Safety-critical: Driver SOS alert
            on(SocketConstants.DRIVER_SOS_ALERT) { args -> handleDriverSOSAlert(args) }

            // Hold lifecycle events (delegated to SocketExtendedEventHandlers)
            on(SocketConstants.FLEX_HOLD_STARTED) { args -> ext.handleFlexHoldStarted(args) }
            on(SocketConstants.FLEX_HOLD_EXTENDED) { args -> ext.handleFlexHoldExtended(args) }
            on(SocketConstants.CASCADE_REASSIGNED) { args -> ext.handleCascadeReassigned(args) }
            on(SocketConstants.DRIVER_DECLINED) { args -> ext.handleDriverDeclined(args) }
            on(SocketConstants.DRIVER_ACCEPTED) { args -> ext.handleDriverAccepted(args) }
            on(SocketConstants.HOLD_EXPIRED) { args -> ext.handleHoldExpired(args) }
            on(SocketConstants.FLEX_HOLD_EXPIRED) { args -> ext.handleHoldExpired(args) }
            on(SocketConstants.CONFIRMED_HOLD_EXPIRED) { args -> ext.handleHoldExpired(args) }

            // Booking progress events
            on(SocketConstants.BOOKING_PARTIALLY_FILLED) { args -> ext.handleBookingPartiallyFilled(args) }

            // Request availability events
            on(SocketConstants.REQUEST_NO_LONGER_AVAILABLE) { args -> ext.handleRequestNoLongerAvailable(args) }

            // Order status events
            on(SocketConstants.ORDER_STATUS_UPDATE) { args -> ext.handleOrderStatusUpdate(args) }

            // Driver health events
            on(SocketConstants.DRIVER_MAY_BE_OFFLINE) { args -> ext.handleDriverMayBeOffline(args) }

            // Assignment health events
            on(SocketConstants.ASSIGNMENT_STALE) { args -> ext.handleAssignmentStale(args) }

            // Driver rating events
            on(SocketConstants.DRIVER_RATING_UPDATED) { args -> ext.handleDriverRatingUpdated(args) }

            // ================================================================
            // P10 t1 — F-C-51/53/55/63/65 Captain contracts consumers
            // ================================================================
            // All four event registrations below are ADDITIVE — they coexist
            // with the existing legacy handlers above. Each handler short-
            // circuits when its BuildConfig flag is OFF (default), so the
            // socket listener overhead is a single flag read until we flip the
            // gradle property on a canary build.
            //
            // Backend-first rollout plan:
            //   1. Backend emits legacy + v2 shapes in parallel (t5 PRs).
            //   2. Captain ships these handlers flag-OFF (this commit).
            //   3. Canary gradle build flips the flag; captain consumes v2.
            //   4. After 90%+ DAU on v2, backend drops the legacy emit.
            //
            // See F-C-83 audit doc (docs/phase4/flag-registry-audit.md) for
            // the full captain+backend flag registry.
            on(SocketConstants.ORDER_PROGRESS) { args -> handleOrderProgress(args) }
            on(SocketConstants.DISPATCH_ACK_RESPONSE) { args -> handleDispatchAckResponse(args) }
            on(SocketConstants.BOOKING_BROADCAST_V2) { args -> handleBookingBroadcastV2(args) }
        }
    }

    // --- Broadcast handlers ---

    private fun handleNewBroadcast(args: Array<Any>, rawEventName: String) {
        val receivedAtMs = System.currentTimeMillis()
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_WS_RECEIVED,
            status = BroadcastStatus.SUCCESS,
            attrs = mapOf("event" to rawEventName, "eventName" to rawEventName, "ingressSource" to "socket")
        )

        val userRole = RetrofitClient.getUserRole()?.lowercase()
        if (!BroadcastRolePolicy.canHandleBroadcastIngress(userRole)) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_GATED, status = BroadcastStatus.SKIPPED,
                reason = "role_not_transporter",
                attrs = mapOf("event" to rawEventName, "eventName" to rawEventName, "ingressSource" to "socket", "role" to (userRole ?: "unknown"))
            )
            timber.log.Timber.w("\u23ED\uFE0F Ignoring new broadcast event for non-transporter role=%s", userRole ?: "unknown")
            return
        }
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_GATED, status = BroadcastStatus.SUCCESS,
            reason = "role_transporter",
            attrs = mapOf("event" to rawEventName, "eventName" to rawEventName, "ingressSource" to "socket", "role" to (userRole ?: "unknown"))
        )

        // Dedup — unified BroadcastDedupKey layer (cross-channel: Socket.IO + FCM).
        // W1-2 / F-C-02 — funnel through BroadcastDedupKey.admit so socket and FCM
        // share the same pre-ingress namespace + 1-release legacy dual-probe.
        val dedupPayload = try { args.firstOrNull() as? JSONObject } catch (_: Exception) { null }
        val dedupBroadcastId = try {
            dedupPayload?.optString("broadcastId", "")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        if (dedupBroadcastId != null) {
            val dedupPayloadVersion = try {
                dedupPayload?.optString("payloadVersion", "")?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
            val admitted = BroadcastDedupKey.admit(
                eventClass = BroadcastEventClass.NEW_BROADCAST,
                id = dedupBroadcastId,
                version = dedupPayloadVersion
            )
            if (!admitted) {
                timber.log.Timber.d("\uD83D\uDD01 Dedup: skipping already-seen broadcast %s (cross-channel)", dedupBroadcastId)
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_GATED, status = BroadcastStatus.DROPPED,
                    reason = "dedup_already_seen", attrs = mapOf("broadcastId" to dedupBroadcastId, "source" to "socket")
                )
                return
            }
            // Legacy per-connection dedup (retained for reconnect awareness)
            connectionManager.seenBroadcastIds[dedupBroadcastId] = System.currentTimeMillis()
        }

        val envelope = parseIncomingBroadcastEnvelope(rawEventName, args, receivedAtMs)
        val notification = envelope.broadcast ?: return
        val broadcastTrip = notification.toBroadcastTrip()

        // RAMEN ACK
        val rawSeq: Long = try { (args.firstOrNull() as? JSONObject)?.optLong("_seq", 0L) ?: 0L } catch (_: Exception) { 0L }
        if (rawSeq > 0L && rawSeq > connectionManager.lastAckedSeq) {
            connectionManager.lastAckedSeq = rawSeq
        }
        val ackOrderId = broadcastTrip.broadcastId
        if (ackOrderId.isNotBlank()) {
            emitDispatchAck(ackOrderId, rawSeq, broadcastTrip.dispatchRevision, "socket", receivedAtMs)
        }

        if (BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
            BroadcastFlowCoordinator.ingestSocketEnvelope(
                BroadcastIngressEnvelope(
                    source = BroadcastIngressSource.SOCKET,
                    rawEventName = envelope.rawEventName,
                    normalizedId = envelope.normalizedId,
                    receivedAtMs = envelope.receivedAtMs,
                    payloadVersion = envelope.payloadVersion,
                    parseWarnings = envelope.parseWarnings,
                    broadcast = broadcastTrip
                )
            )
            scheduleBroadcastOverlayWatchdog(broadcastTrip.broadcastId, rawEventName, "coordinator")
            emitHot(_newBroadcasts, notification)
            return
        }

        serviceScope.launch {
            val ingestResult = BroadcastOverlayManager.showBroadcast(broadcastTrip, trustedSource = true)
            when (ingestResult.action) {
                BroadcastOverlayManager.BroadcastIngressAction.SHOWN -> {
                    timber.log.Timber.i("\u2705 OVERLAY SHOWN for broadcast: ${broadcastTrip.broadcastId}")
                    BroadcastTelemetry.recordLatency(
                        name = "overlay_open_latency_ms",
                        ms = System.currentTimeMillis() - receivedAtMs,
                        attrs = mapOf("broadcastId" to broadcastTrip.broadcastId, "event" to rawEventName)
                    )
                    scheduleBroadcastOverlayWatchdog(broadcastTrip.broadcastId, rawEventName, "legacy_overlay_manager")
                    if (!com.weelo.logistics.utils.AppLifecycleObserver.isAppInForeground) {
                        com.weelo.logistics.WeeloApp.getInstance()?.let { app ->
                            com.weelo.logistics.broadcast.BroadcastFullScreenNotifier.showIncomingBroadcast(
                                context = app,
                                broadcastId = broadcastTrip.broadcastId,
                                title = "New Booking Request",
                                body = "${broadcastTrip.pickupLocation.city ?: broadcastTrip.pickupLocation.address} \u2192 ${broadcastTrip.dropLocation.city ?: broadcastTrip.dropLocation.address}"
                            )
                        }
                    }
                }
                BroadcastOverlayManager.BroadcastIngressAction.BUFFERED -> {
                    timber.log.Timber.i("\uD83D\uDCE5 Broadcast buffered: ${broadcastTrip.broadcastId} (${ingestResult.reason})")
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.BROADCAST_GATED, status = BroadcastStatus.BUFFERED,
                        reason = ingestResult.reason ?: "overlay_buffered",
                        attrs = mapOf("broadcastId" to broadcastTrip.broadcastId, "event" to rawEventName)
                    )
                }
                BroadcastOverlayManager.BroadcastIngressAction.DROPPED -> {
                    timber.log.Timber.w("\u26A0\uFE0F Broadcast dropped: %s reason=%s queue=%s",
                        broadcastTrip.broadcastId, ingestResult.reason, BroadcastOverlayManager.getQueueInfo()
                    )
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.BROADCAST_GATED, status = BroadcastStatus.DROPPED,
                        reason = ingestResult.reason ?: "overlay_dropped",
                        attrs = mapOf("broadcastId" to broadcastTrip.broadcastId, "event" to rawEventName)
                    )
                }
            }
        }
        emitHot(_newBroadcasts, notification)
    }

    private fun maybeRouteIncomingBroadcastFallback(rawIncomingEvent: String, payloadArgs: Array<Any>) {
        val normalizedEvent = rawIncomingEvent.trim().lowercase(Locale.US)
        val payload = payloadArgs.firstOrNull() as? JSONObject
        val payloadType = payload?.optString("type", "")?.trim()?.lowercase(Locale.US).orEmpty()
        val legacyType = payload?.optString("legacyType", "")?.trim()?.lowercase(Locale.US).orEmpty()

        val resolution = resolveBroadcastFallbackDecision(
            rawIncomingEvent, payloadType, legacyType,
            directBroadcastSocketEvents, directCancellationSocketEvents,
            fallbackBroadcastSocketEvents, broadcastPayloadTypes, cancellationPayloadTypes
        )
        val effectiveEvent = resolution.effectiveEvent ?: return

        when (resolution.route) {
            BroadcastFallbackRoute.NONE -> return
            BroadcastFallbackRoute.CANCELLATION -> {
                if (!BroadcastFeatureFlagsRegistry.current().captainCanonicalCancelAliasesEnabled) return
                timber.log.Timber.w("\u21AA\uFE0F Fallback routing incoming cancellation event: incoming=%s effective=%s payloadType=%s legacyType=%s",
                    normalizedEvent, effectiveEvent, payloadType.ifBlank { "none" }, legacyType.ifBlank { "none" })
                val normalizedId = payload?.let { resolveEventId(it, "orderId", "broadcastId", "bookingId", "id") }.orEmpty()
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_GATED, status = BroadcastStatus.SUCCESS,
                    reason = "fallback_cancellation_alias",
                    attrs = mapOf("ingressSource" to "socket_fallback", "eventName" to effectiveEvent, "normalizedId" to normalizedId.ifBlank { "none" })
                )
                when (effectiveEvent) {
                    SocketConstants.ORDER_CANCELLED.lowercase(Locale.US),
                    SocketConstants.BOOKING_CANCELLED.lowercase(Locale.US) -> handleOrderCancelled(payloadArgs)
                    SocketConstants.ORDER_EXPIRED.lowercase(Locale.US) -> handleOrderExpired(payloadArgs)
                    SocketConstants.BROADCAST_DISMISSED.lowercase(Locale.US),
                    SocketConstants.BROADCAST_EXPIRED.lowercase(Locale.US),
                    SocketConstants.BOOKING_EXPIRED.lowercase(Locale.US) -> handleBookingExpired(payloadArgs)
                }
            }
            BroadcastFallbackRoute.BROADCAST -> {
                timber.log.Timber.w("\u21AA\uFE0F Fallback routing incoming broadcast event: incoming=%s effective=%s payloadType=%s legacyType=%s",
                    normalizedEvent, effectiveEvent, payloadType.ifBlank { "none" }, legacyType.ifBlank { "none" })
                handleNewBroadcast(payloadArgs, effectiveEvent)
            }
        }
    }

    // --- Truck / assignment / booking handlers ---

    private fun handleTruckAssigned(args: Array<Any>) {
        // F-C-67 — flag-guarded swap from `catch { Timber.e }` silent-swallow to
        // `parseJsonSafe` which emits a `fix_id=F-C-67` telemetry breadcrumb on
        // failure. Legacy path preserved under `else` for instant rollback.
        if (BuildConfig.FF_CUSTOMER_PARSE_JSON_SAFE) {
            val notification = parseJsonSafe<TruckAssignedNotification>(
                event = SocketConstants.TRUCK_ASSIGNED,
                raw = args.firstOrNull()
            ) { data ->
                timber.log.Timber.i("\uD83D\uDE9B Truck assigned: $data")
                val assignment = data.optJSONObject("assignment")
                TruckAssignedNotification(
                    bookingId = data.optString("bookingId", ""),
                    assignmentId = assignment?.optString("id", "") ?: "",
                    vehicleNumber = assignment?.optString("vehicleNumber", "") ?: "",
                    driverName = assignment?.optString("driverName", "") ?: "",
                    status = assignment?.optString("status", "") ?: ""
                )
            }.getOrNull() ?: return
            serviceScope.launch { _truckAssigned.emit(notification) }
            return
        }
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\uD83D\uDE9B Truck assigned: $data")
            val assignment = data.optJSONObject("assignment")
            val notification = TruckAssignedNotification(
                bookingId = data.optString("bookingId", ""),
                assignmentId = assignment?.optString("id", "") ?: "",
                vehicleNumber = assignment?.optString("vehicleNumber", "") ?: "",
                driverName = assignment?.optString("driverName", "") ?: "",
                status = assignment?.optString("status", "") ?: ""
            )
            serviceScope.launch { _truckAssigned.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing truck assigned: ${e.message}") }
    }

    private fun handleAssignmentStatusChanged(args: Array<Any>) {
        // F-C-67 — flag-guarded adoption, see handleTruckAssigned for contract.
        if (BuildConfig.FF_CUSTOMER_PARSE_JSON_SAFE) {
            val notification = parseJsonSafe<AssignmentStatusNotification>(
                event = SocketConstants.ASSIGNMENT_STATUS_CHANGED,
                raw = args.firstOrNull()
            ) { data ->
                timber.log.Timber.i("\uD83D\uDCCB Assignment status changed: $data")
                AssignmentStatusNotification(
                    assignmentId = data.optString("assignmentId", ""),
                    tripId = data.optString("tripId", ""),
                    status = data.optString("status", ""),
                    vehicleNumber = data.optString("vehicleNumber", ""),
                    message = data.optString("message", "")
                )
            }.getOrNull() ?: return
            serviceScope.launch { _assignmentStatusChanged.emit(notification) }
            return
        }
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\uD83D\uDCCB Assignment status changed: $data")

            // P10 / F-C-51 + F-C-63 — typed v2 consumer under feature flag.
            // When FF_ASSIGNMENT_STATUS_ROUTER_V2 OR FF_ASSIGNMENT_STATUS_PAYLOAD_V2
            // is ON, delegate parse to the typed factory first. On null result or
            // flag OFF, fall through to the legacy parse path so we never drop
            // events during backend-first rollout.
            if (BuildConfig.FF_ASSIGNMENT_STATUS_ROUTER_V2 ||
                BuildConfig.FF_ASSIGNMENT_STATUS_PAYLOAD_V2) {
                val typed = AssignmentStatusChangedEvent.fromJson(data)
                if (typed != null) {
                    timber.log.Timber.d(
                        "\uD83D\uDCCB v2 parse ok: source=%s orderId=%s bookingId=%s accepted=%s pending=%s",
                        typed.source ?: "none",
                        typed.orderId ?: "none",
                        typed.bookingId ?: "none",
                        typed.trucksAccepted?.toString() ?: "none",
                        typed.trucksPending?.toString() ?: "none"
                    )
                    // Emit the legacy notification shape the rest of the app already
                    // consumes — v2 adds context but the downstream UI doesn't need
                    // a new flow yet. Multi-vehicle progress UI (future) reads
                    // trucksAccepted/trucksPending via a dedicated handler.
                    val notification = AssignmentStatusNotification(
                        assignmentId = typed.assignmentId,
                        tripId = typed.tripId,
                        status = typed.status,
                        vehicleNumber = typed.vehicleNumber,
                        message = typed.message
                    )
                    serviceScope.launch { _assignmentStatusChanged.emit(notification) }
                    return
                }
                timber.log.Timber.w("\u21A9\uFE0F v2 parse returned null, falling back to legacy path")
            }

            // Legacy 5-field path — stays wired for: (a) flag OFF, (b) backend
            // still emits legacy shape, (c) v2 parse fails closed on drift.
            val notification = AssignmentStatusNotification(
                assignmentId = data.optString("assignmentId", ""),
                tripId = data.optString("tripId", ""),
                status = data.optString("status", ""),
                vehicleNumber = data.optString("vehicleNumber", ""),
                message = data.optString("message", "")
            )
            serviceScope.launch { _assignmentStatusChanged.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing assignment status: ${e.message}") }
    }

    // F-C-65 — Order progress consumer (per-order truck accept/pending/decline counts).
    // Gated behind FF_ORDER_PROGRESS_V1. Feeds the existing
    // _trucksRemainingUpdates flow so the transporter dashboard can render
    // progress without a new UI wire-up — a future OrderProgressScreen can
    // consume the typed OrderProgressEvent directly.
    private fun handleOrderProgress(args: Array<Any>) {
        if (!BuildConfig.FF_ORDER_PROGRESS_V1) return
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val event = OrderProgressEvent.fromJson(data) ?: run {
                timber.log.Timber.w("order_progress: fromJson null, dropping event")
                return
            }
            timber.log.Timber.i(
                "\uD83D\uDCCA Order progress: orderId=%s status=%s accepted=%d pending=%d declined=%d needed=%d",
                event.orderId, event.status, event.trucksAccepted, event.trucksPending,
                event.trucksDeclined, event.trucksNeeded
            )
            // Reuse TrucksRemainingNotification as the compatibility emission —
            // the dashboard already listens on this flow.
            val trucksRemaining = (event.trucksNeeded - event.trucksAccepted).coerceAtLeast(0)
            val notification = TrucksRemainingNotification(
                orderId = event.orderId,
                vehicleType = "",
                totalTrucks = event.trucksNeeded,
                trucksFilled = event.trucksAccepted,
                trucksRemaining = trucksRemaining,
                orderStatus = event.status,
                eventId = null,
                eventVersion = null,
                serverTimeMs = event.updatedAtMs
            )
            serviceScope.launch { _trucksRemainingUpdates.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing order_progress: ${e.message}") }
    }

    // F-C-53 — Dispatch ACK receipt echo consumer (server -> client confirmation).
    // Captain fires dispatch_ack on broadcast ingest (emitDispatchAck below);
    // this handler consumes the optional echo backend emits when
    // FF_DISPATCH_ACK_HANDLER is ON. Telemetry only — no business-logic side
    // effect. Safe to enable before backend ships the matching emit.
    private fun handleDispatchAckResponse(args: Array<Any>) {
        if (!BuildConfig.FF_DISPATCH_ACK_HANDLER) return
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val event = DispatchAckEvent.fromJson(data) ?: run {
                timber.log.Timber.w("dispatch_ack_response: fromJson null (likely missing orderId), dropping")
                return
            }
            timber.log.Timber.d(
                "\u2705 Dispatch ack receipt: orderId=%s revision=%s ackAt=%s source=%s",
                event.orderId,
                event.dispatchRevision?.toString() ?: "none",
                event.acknowledgedAtMs?.toString() ?: "none",
                event.source
            )
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_WS_RECEIVED,
                status = BroadcastStatus.SUCCESS,
                reason = "dispatch_ack_response",
                attrs = mapOf(
                    "orderId" to event.orderId,
                    "dispatchRevision" to (event.dispatchRevision?.toString() ?: "none"),
                    "source" to event.source
                )
            )
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing dispatch_ack_response: ${e.message}") }
    }

    // F-C-55 — Booking broadcast v2 payload consumer. Backend ships a v2 emit
    // path that populates 6 transporter-context fields the legacy builder
    // zeroes out ("0 of 0 trucks available" mislead). Gated behind
    // FF_BOOKING_V2_PAYLOAD. Telemetry only — the real personalization UX
    // lands in a follow-up that wires the v2 event into BroadcastCoordinator.
    private fun handleBookingBroadcastV2(args: Array<Any>) {
        if (!BuildConfig.FF_BOOKING_V2_PAYLOAD) return
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val event = BookingBroadcastV2Event.fromJson(data) ?: run {
                timber.log.Timber.w("booking_broadcast_v2: fromJson null, dropping")
                return
            }
            timber.log.Timber.i(
                "\uD83D\uDCE6 Booking broadcast v2: id=%s personalized=%s youCanProvide=%d/%d needed=%d",
                event.broadcastId, event.isPersonalized,
                event.trucksYouCanProvide, event.maxTrucksYouCanProvide,
                event.trucksStillNeeded
            )
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_WS_RECEIVED,
                status = BroadcastStatus.SUCCESS,
                reason = "booking_broadcast_v2",
                attrs = mapOf(
                    "broadcastId" to event.broadcastId,
                    "isPersonalized" to event.isPersonalized.toString(),
                    "trucksYouCanProvide" to event.trucksYouCanProvide.toString()
                )
            )
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing booking_broadcast_v2: ${e.message}") }
    }

    private fun handleBookingUpdated(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\uD83D\uDCDD Booking updated: $data")
            val notification = BookingUpdatedNotification(
                bookingId = data.optString("bookingId", ""),
                status = data.optString("status", ""),
                trucksFilled = data.optInt("trucksFilled", -1),
                trucksNeeded = data.optInt("trucksNeeded", -1)
            )
            serviceScope.launch { _bookingUpdated.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing booking update: ${e.message}") }
    }

    private fun handleTrucksRemainingUpdate(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\uD83D\uDCCA Trucks remaining update: $data")
            val notification = TrucksRemainingNotification(
                orderId = resolveEventId(data, "orderId", "broadcastId", "bookingId", "id"),
                vehicleType = data.optString("vehicleType", ""),
                totalTrucks = data.optInt("trucksNeeded", data.optInt("totalTrucks", 0)),
                trucksFilled = data.optInt("trucksFilled", 0),
                trucksRemaining = data.optInt("trucksRemaining", 0),
                orderStatus = data.optString("orderStatus", ""),
                eventId = resolveOptionalString(data, "eventId"),
                eventVersion = resolveOptionalInt(data, "eventVersion"),
                serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
            )
            serviceScope.launch { _trucksRemainingUpdates.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing trucks remaining: ${e.message}") }
    }

    private fun handleAcceptConfirmation(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\u2705 Accept confirmation: $data")
            val notification = AssignmentStatusNotification(
                assignmentId = data.optString("requestId", ""),
                tripId = data.optString("tripId", ""),
                status = if (data.optBoolean("success", false)) "accepted" else "failed",
                vehicleNumber = data.optString("vehicleNumber", ""),
                message = data.optString("message", "")
            )
            serviceScope.launch { _assignmentStatusChanged.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing accept confirmation: ${e.message}") }
    }

    private fun handleBookingExpired(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val broadcastId = resolveEventId(data, "broadcastId", "orderId", "bookingId", "id")
            val reason = data.optString("reason", "timeout")
            val message = data.optString("message", when (reason) {
                "customer_cancelled" -> "Sorry, this order was cancelled by the customer"
                "fully_filled" -> "All trucks have been assigned for this booking"
                else -> "This booking request has expired"
            })
            val customerName = data.optString("customerName", "")
            timber.log.Timber.w("\u23F0 BROADCAST DISMISSED \u2014 Broadcast ID: $broadcastId Reason: $reason")

            if (broadcastId.isNotEmpty()) {
                emitHot(_broadcastDismissed, BroadcastDismissedNotification(
                    broadcastId = broadcastId, reason = reason, message = message, customerName = customerName,
                    eventId = resolveOptionalString(data, "eventId"),
                    dispatchRevision = resolveOptionalLong(data, "dispatchRevision"),
                    orderLifecycleVersion = resolveOptionalLong(data, "orderLifecycleVersion"),
                    eventVersion = resolveOptionalInt(data, "eventVersion"),
                    serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
                ))
                if (!BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                    scheduleOverlayRemovalAfterDismiss(broadcastId)
                }
            }
            val notification = BookingUpdatedNotification(
                bookingId = broadcastId,
                status = if (reason == "customer_cancelled") "cancelled" else "expired",
                trucksFilled = data.optInt("trucksFilled", -1),
                trucksNeeded = data.optInt("trucksNeeded", -1)
            )
            emitHot(_bookingUpdated, notification)
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing booking expired: ${e.message}") }
    }

    private fun handleBookingFullyFilled(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\u2705 Booking fully filled: $data")
            val notification = BookingUpdatedNotification(
                bookingId = data.optString("bookingId", data.optString("orderId", "")),
                status = "fully_filled",
                trucksFilled = data.optInt("trucksFilled", -1),
                trucksNeeded = data.optInt("trucksNeeded", -1)
            )
            serviceScope.launch { _bookingUpdated.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing booking fully filled: ${e.message}") }
    }

    // --- Order lifecycle handlers ---

    private fun handleOrderCancelled(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val orderId = resolveEventId(data, "orderId", "broadcastId", "bookingId", "id")
            val tripId = data.optString("tripId", "")
            val reason = data.optString("reason", "Cancelled by customer")
            val cancelledAt = data.optString("cancelledAt", "")
            val message = data.optString("message", "Sorry, this order was cancelled by the customer")
            val assignmentsCancelled = data.optInt("assignmentsCancelled", 0)
            timber.log.Timber.w("\uD83D\uDEAB ORDER CANCELLED BY CUSTOMER \u2014 Order ID: $orderId Reason: $reason Assignments Released: $assignmentsCancelled")

            emitHot(_broadcastDismissed, BroadcastDismissedNotification(
                broadcastId = orderId, reason = "customer_cancelled", message = message,
                customerName = data.optString("customerName", ""),
                eventId = resolveOptionalString(data, "eventId"),
                dispatchRevision = resolveOptionalLong(data, "dispatchRevision"),
                orderLifecycleVersion = resolveOptionalLong(data, "orderLifecycleVersion"),
                eventVersion = resolveOptionalInt(data, "eventVersion"),
                serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
            ))
            if (!BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                scheduleOverlayRemovalAfterDismiss(orderId)
            }

            emitHot(_orderCancelled, OrderCancelledNotification(
                orderId = orderId, tripId = tripId, reason = reason, message = message,
                cancelledAt = cancelledAt, assignmentsCancelled = assignmentsCancelled,
                eventId = resolveOptionalString(data, "eventId"),
                dispatchRevision = resolveOptionalLong(data, "dispatchRevision"),
                orderLifecycleVersion = resolveOptionalLong(data, "orderLifecycleVersion"),
                eventVersion = resolveOptionalInt(data, "eventVersion"),
                serverTimeMs = resolveOptionalLong(data, "serverTimeMs"),
                customerName = data.optString("customerName", ""),
                customerPhone = data.optString("customerPhone", ""),
                pickupAddress = data.optString("pickupAddress", ""),
                dropAddress = data.optString("dropAddress", "")
            ))

            emitHot(_bookingUpdated, BookingUpdatedNotification(
                bookingId = orderId, status = "cancelled", trucksFilled = -1, trucksNeeded = -1
            ))
            timber.log.Timber.i("   \u2713 Emitted dismiss + orderCancelled + bookingUpdated flows")
        } catch (e: Exception) { timber.log.Timber.e(e, "Error handling order cancelled: ${e.message}") }
    }

    private fun handleOrderExpired(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val orderId = resolveEventId(data, "orderId", "broadcastId", "bookingId", "id")
            timber.log.Timber.w("\u23F0 ORDER EXPIRED \u2014 GRACEFUL DISMISS: $orderId")

            emitHot(_broadcastDismissed, BroadcastDismissedNotification(
                broadcastId = orderId, reason = "timeout", message = "This booking request has expired",
                customerName = data.optString("customerName", ""),
                eventId = resolveOptionalString(data, "eventId"),
                dispatchRevision = resolveOptionalLong(data, "dispatchRevision"),
                orderLifecycleVersion = resolveOptionalLong(data, "orderLifecycleVersion"),
                eventVersion = resolveOptionalInt(data, "eventVersion"),
                serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
            ))
            if (!BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                scheduleOverlayRemovalAfterDismiss(orderId)
            }
            emitHot(_bookingUpdated, BookingUpdatedNotification(
                bookingId = orderId, status = "expired",
                trucksFilled = data.optInt("trucksFilled", -1),
                trucksNeeded = data.optInt("totalTrucks", -1)
            ))
        } catch (e: Exception) { timber.log.Timber.e(e, "Error handling order expired: ${e.message}") }
    }

    // --- Fleet / vehicle / driver handlers ---

    private fun handleVehicleRegistered(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\uD83D\uDE9B Vehicle registered: $data")
            val vehicle = data.optJSONObject("vehicle")
            val fleetStats = data.optJSONObject("fleetStats")
            val notification = VehicleRegisteredNotification(
                vehicleId = vehicle?.optString("id", "") ?: "",
                vehicleNumber = vehicle?.optString("vehicleNumber", "") ?: "",
                vehicleType = vehicle?.optString("vehicleType", "") ?: "",
                vehicleSubtype = vehicle?.optString("vehicleSubtype", "") ?: "",
                message = data.optString("message", "Vehicle registered successfully"),
                totalVehicles = fleetStats?.optInt("total", 0) ?: 0,
                availableCount = fleetStats?.optInt("available", 0) ?: 0
            )
            serviceScope.launch {
                _vehicleRegistered.emit(notification)
                _fleetUpdated.emit(FleetUpdatedNotification(
                    action = "added", vehicleId = notification.vehicleId,
                    totalVehicles = notification.totalVehicles, availableCount = notification.availableCount,
                    inTransitCount = fleetStats?.optInt("in_transit", 0) ?: 0,
                    maintenanceCount = fleetStats?.optInt("maintenance", 0) ?: 0
                ))
            }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing vehicle registered: ${e.message}") }
    }

    private fun handleFleetUpdated(args: Array<Any>, action: String) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\uD83D\uDE9B Fleet updated ($action): $data")
            val fleetStats = data.optJSONObject("fleetStats")
            val notification = FleetUpdatedNotification(
                action = data.optString("action", action),
                vehicleId = data.optString("vehicleId", ""),
                totalVehicles = fleetStats?.optInt("total", 0) ?: 0,
                availableCount = fleetStats?.optInt("available", 0) ?: 0,
                inTransitCount = fleetStats?.optInt("in_transit", 0) ?: 0,
                maintenanceCount = fleetStats?.optInt("maintenance", 0) ?: 0
            )
            serviceScope.launch { _fleetUpdated.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing fleet update: ${e.message}") }
    }

    private fun handleDriverAdded(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\uD83D\uDC64 Driver added: $data")
            val driver = data.optJSONObject("driver")
            val driverStats = data.optJSONObject("driverStats")
            val notification = DriverAddedNotification(
                driverId = driver?.optString("id", "") ?: "",
                driverName = driver?.optString("name", "") ?: "",
                driverPhone = driver?.optString("phone", "") ?: "",
                licenseNumber = driver?.optString("licenseNumber", "") ?: "",
                message = data.optString("message", "Driver added successfully"),
                totalDrivers = driverStats?.optInt("total", 0) ?: 0,
                availableCount = driverStats?.optInt("available", 0) ?: 0,
                onTripCount = driverStats?.optInt("onTrip", 0) ?: 0
            )
            serviceScope.launch {
                _driverAdded.emit(notification)
                _driversUpdated.emit(DriversUpdatedNotification(
                    action = "added", driverId = notification.driverId,
                    totalDrivers = notification.totalDrivers, availableCount = notification.availableCount,
                    onTripCount = notification.onTripCount
                ))
            }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing driver added: ${e.message}") }
    }

    private fun handleDriverStatusChanged(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val driverId = data.optString("driverId", "")
            val driverName = data.optString("driverName", "")
            val isOnline = data.optBoolean("isOnline", false)
            val action = data.optString("action", "")
            timber.log.Timber.i("\uD83D\uDC64 Driver status changed: $driverName \u2192 ${if (isOnline) "ONLINE" else "OFFLINE"}")
            val notification = DriverStatusChangedNotification(
                driverId = driverId, driverName = driverName, isOnline = isOnline,
                action = action, timestamp = data.optString("timestamp", "")
            )
            serviceScope.launch { _driverStatusChanged.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing driver_status_changed: ${e.message}") }
    }

    private fun handleDriversUpdated(args: Array<Any>, action: String) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\uD83D\uDC64 Drivers updated ($action): $data")
            val driverStats = data.optJSONObject("driverStats")
            val notification = DriversUpdatedNotification(
                action = data.optString("action", action),
                driverId = data.optString("driverId", ""),
                totalDrivers = driverStats?.optInt("total", 0) ?: 0,
                availableCount = driverStats?.optInt("available", 0) ?: 0,
                onTripCount = driverStats?.optInt("onTrip", 0) ?: 0
            )
            serviceScope.launch { _driversUpdated.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing drivers update: ${e.message}") }
    }

    // --- Trip assignment handlers ---

    private fun handleTripAssigned(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("\uD83D\uDE9B NEW TRIP ASSIGNED TO DRIVER: assignmentId=${data.optString("assignmentId", "")}")
            val pickupObj = data.optJSONObject("pickup")
            val pickup = TripLocationInfo(
                address = pickupObj?.optString("address", "") ?: "",
                city = pickupObj?.optString("city", "") ?: "",
                latitude = pickupObj?.optDouble("lat", 0.0) ?: 0.0,
                longitude = pickupObj?.optDouble("lng", 0.0) ?: 0.0
            )
            val dropObj = data.optJSONObject("drop")
            val drop = TripLocationInfo(
                address = dropObj?.optString("address", "") ?: "",
                city = dropObj?.optString("city", "") ?: "",
                latitude = dropObj?.optDouble("lat", 0.0) ?: 0.0,
                longitude = dropObj?.optDouble("lng", 0.0) ?: 0.0
            )
            val routePointsArray = data.optJSONArray("routePoints")
            val routePoints = mutableListOf<RoutePoint>()
            if (routePointsArray != null) {
                for (i in 0 until routePointsArray.length()) {
                    val pointObj = routePointsArray.optJSONObject(i)
                    val typeStr = pointObj?.optString("type", "PICKUP") ?: "PICKUP"
                    val pointType = when (typeStr.uppercase()) {
                        "PICKUP" -> RoutePointType.PICKUP
                        "STOP" -> RoutePointType.STOP
                        "DROP" -> RoutePointType.DROP
                        else -> RoutePointType.PICKUP
                    }
                    routePoints.add(RoutePoint(
                        type = pointType,
                        latitude = pointObj?.optDouble("latitude", 0.0) ?: 0.0,
                        longitude = pointObj?.optDouble("longitude", 0.0) ?: 0.0,
                        address = pointObj?.optString("address", "") ?: "",
                        city = pointObj?.optString("city", ""),
                        stopIndex = i
                    ))
                }
            }
            val expiresAt = data.optString("expiresAt", "").takeIf { it.isNotEmpty() }
            // F-C-77: thread server-provided driverAcceptTimeoutSeconds so the
            // DTO fallback uses server truth rather than a stale hardcode.
            val driverAcceptTimeoutSeconds = if (data.has("driverAcceptTimeoutSeconds") && !data.isNull("driverAcceptTimeoutSeconds")) {
                data.optInt("driverAcceptTimeoutSeconds").takeIf { it > 0 }
            } else null
            val notification = TripAssignedNotification(
                assignmentId = data.optString("assignmentId", ""),
                tripId = data.optString("tripId", ""),
                orderId = data.optString("orderId", ""),
                truckRequestId = data.optString("truckRequestId", ""),
                pickup = pickup, drop = drop,
                vehicleNumber = data.optString("vehicleNumber", ""),
                farePerTruck = data.optDouble("farePerTruck", 0.0),
                distanceKm = data.optDouble("distanceKm", 0.0),
                customerName = data.optString("customerName", ""),
                customerPhone = data.optString("customerPhone", ""),
                assignedAt = data.optString("assignedAt", ""),
                expiresAt = expiresAt,
                routePoints = routePoints.ifEmpty { null },
                message = data.optString("message", "New trip assigned!"),
                driverAcceptTimeoutSeconds = driverAcceptTimeoutSeconds
            )
            serviceScope.launch { _tripAssigned.emit(notification) }
            timber.log.Timber.i("\u2705 Trip assignment emitted to flow: ${notification.assignmentId}")
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing trip_assigned: ${e.message}") }
    }

    private fun handleDriverTimeout(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.w("\u23F0 DRIVER TIMEOUT - Assignment expired: assignmentId=${data.optString("assignmentId", "")}")
            val notification = DriverTimeoutNotification(
                assignmentId = data.optString("assignmentId", ""),
                tripId = data.optString("tripId", ""),
                driverId = data.optString("driverId", ""),
                driverName = data.optString("driverName", ""),
                vehicleNumber = data.optString("vehicleNumber", ""),
                reason = data.optString("reason", "timeout"),
                message = data.optString("message", "Driver didn't respond in time")
            )
            serviceScope.launch { _driverTimeout.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing driver_timeout: ${e.message}") }
    }

    private fun handleDriverSOSAlert(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.e("DRIVER SOS ALERT: driverId=${data.optString("driverId", "")}")
            val notification = DriverSOSAlertNotification(
                driverId = data.optString("driverId", ""),
                lat = data.optJSONObject("location")?.optDouble("lat", 0.0) ?: 0.0,
                lng = data.optJSONObject("location")?.optDouble("lng", 0.0) ?: 0.0,
                message = data.optString("message", "Emergency!"),
                timestamp = data.optString("timestamp", "")
            )
            serviceScope.launch { _driverSOSAlert.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing driver_sos_alert: ${e.message}") }
    }

    private fun handleTripCancelled(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val notification = TripCancelledNotification(
                orderId = resolveEventId(data, "orderId", "broadcastId", "bookingId", "id"),
                tripId = data.optString("tripId", ""),
                reason = data.optString("reason", "cancelled"),
                message = data.optString("message", "Trip cancelled by customer"),
                cancelledAt = data.optString("cancelledAt", ""),
                customerName = data.optString("customerName", ""),
                customerPhone = data.optString("customerPhone", ""),
                pickupAddress = data.optString("pickupAddress", ""),
                dropAddress = data.optString("dropAddress", ""),
                compensationAmount = data.optDouble("compensationAmount", 0.0),
                settlementState = data.optString("settlementState", "none")
            )
            serviceScope.launch { _tripCancelled.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing trip_cancelled: ${e.message}") }
    }

    // --- Dispatch ACK + helpers ---

    fun emitDispatchAck(
        orderId: String, seq: Long = 0L, dispatchRevision: Long? = null,
        source: String, receivedAtMs: Long = System.currentTimeMillis()
    ) {
        val normalizedOrderId = orderId.trim()
        if (normalizedOrderId.isEmpty()) return
        val ackPayload = JSONObject().apply {
            if (seq > 0L) put("seq", seq)
            put("orderId", normalizedOrderId)
            dispatchRevision?.let { put("dispatchRevision", it) }
            put("receivedAt", receivedAtMs)
            put("source", source)
        }
        connectionManager.socket?.emit("dispatch_ack", ackPayload)
        connectionManager.socket?.emit("broadcast_ack", ackPayload)
    }

    fun emitDriverSOS(tripId: String, driverId: String) {
        try {
            val sosPayload = JSONObject().apply {
                put("tripId", tripId)
                put("driverId", driverId)
                put("type", "sos_alert")
                put("timestamp", System.currentTimeMillis())
            }
            connectionManager.socket?.emit("driver_sos", sosPayload)
            timber.log.Timber.w("SOS alert emitted for trip=$tripId driver=$driverId")
        } catch (e: Exception) { timber.log.Timber.e(e, "Failed to emit SOS alert") }
    }

    // --- Private utilities ---

    private fun <T> emitHot(flow: MutableSharedFlow<T>, value: T) {
        if (!flow.tryEmit(value)) {
            serviceScope.launch { flow.emit(value) }
        }
    }

    private fun dismissWindowMs(): Long {
        return BroadcastUiTiming.DISMISS_ENTER_MS.toLong() +
            BroadcastUiTiming.DISMISS_HOLD_MS +
            BroadcastUiTiming.DISMISS_EXIT_MS.toLong()
    }

    private fun scheduleOverlayRemovalAfterDismiss(broadcastId: String) {
        if (broadcastId.isBlank()) return
        if (BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) return
        serviceScope.launch {
            delay(dismissWindowMs())
            BroadcastOverlayManager.removeEverywhere(broadcastId)
            timber.log.Timber.i("\uD83E\uDDF9 Removed broadcast %s after dismiss window", broadcastId)
        }
    }

    private fun scheduleBroadcastOverlayWatchdog(broadcastId: String, rawEventName: String, ingressMode: String) {
        if (broadcastId.isBlank()) return
        if (!BroadcastFeatureFlagsRegistry.current().broadcastOverlayWatchdogEnabled) return
        serviceScope.launch {
            delay(broadcastOverlayWatchdogMs)
            val overlayVisible = BroadcastOverlayManager.isOverlayVisible.value
            val currentOverlayId = BroadcastOverlayManager.currentBroadcast.value?.broadcastId.orEmpty()
            if (overlayVisible || currentOverlayId == broadcastId) return@launch
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_OVERLAY_SHOWN, status = BroadcastStatus.FAILED,
                reason = "watchdog_overlay_not_visible",
                attrs = mapOf("broadcastId" to broadcastId, "event" to rawEventName, "ingressMode" to ingressMode, "queueInfo" to BroadcastOverlayManager.getQueueInfo())
            )
            timber.log.Timber.w("\u26A0\uFE0F Overlay watchdog: broadcast=%s not visible after %dms mode=%s queue=%s",
                broadcastId, broadcastOverlayWatchdogMs, ingressMode, BroadcastOverlayManager.getQueueInfo())
            if (BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                BroadcastFlowCoordinator.requestReconcile(force = true)
            }
        }
    }

    private fun parseIncomingBroadcastEnvelope(rawEventName: String, args: Array<Any>, receivedAtMs: Long): IncomingBroadcastEnvelope =
        BroadcastPayloadParser.parseIncomingBroadcastEnvelope(rawEventName, args, receivedAtMs)

    internal fun resolveEventId(data: JSONObject, vararg keys: String): String =
        BroadcastPayloadParser.resolveEventId(data, *keys)

    private fun resolveOptionalString(data: JSONObject, key: String): String? =
        BroadcastPayloadParser.resolveOptionalString(data, key)

    private fun resolveOptionalInt(data: JSONObject, key: String): Int? =
        BroadcastPayloadParser.resolveOptionalInt(data, key)

    private fun resolveOptionalLong(data: JSONObject, key: String): Long? =
        BroadcastPayloadParser.resolveOptionalLong(data, key)
}
