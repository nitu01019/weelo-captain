package com.weelo.logistics.data.remote

import com.weelo.logistics.data.model.TripAssignedNotification
import com.weelo.logistics.data.remote.socket.SocketConnectionManager
import com.weelo.logistics.data.remote.socket.SocketConnectionState as RealSocketConnectionState
import com.weelo.logistics.data.remote.socket.SocketConstants
import com.weelo.logistics.data.remote.socket.SocketEventRouter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

// =============================================================================
// RE-EXPORT all data classes so existing imports keep compiling.
// The canonical definitions now live in data.remote.socket.SocketEventModels.
// =============================================================================
// Wildcard-style re-export via typealiases
// =============================================================================
typealias SocketConnectionState = com.weelo.logistics.data.remote.socket.SocketConnectionState
typealias IncomingBroadcastEnvelope = com.weelo.logistics.data.remote.socket.IncomingBroadcastEnvelope
typealias BroadcastNotification = com.weelo.logistics.data.remote.socket.BroadcastNotification
typealias RequestedVehicleNotification = com.weelo.logistics.data.remote.socket.RequestedVehicleNotification
typealias TruckAssignedNotification = com.weelo.logistics.data.remote.socket.TruckAssignedNotification
typealias AssignmentStatusNotification = com.weelo.logistics.data.remote.socket.AssignmentStatusNotification
typealias BookingUpdatedNotification = com.weelo.logistics.data.remote.socket.BookingUpdatedNotification
typealias TrucksRemainingNotification = com.weelo.logistics.data.remote.socket.TrucksRemainingNotification
typealias SocketError = com.weelo.logistics.data.remote.socket.SocketError
typealias VehicleRegisteredNotification = com.weelo.logistics.data.remote.socket.VehicleRegisteredNotification
typealias FleetUpdatedNotification = com.weelo.logistics.data.remote.socket.FleetUpdatedNotification
typealias DriverAddedNotification = com.weelo.logistics.data.remote.socket.DriverAddedNotification
typealias DriverStatusChangedNotification = com.weelo.logistics.data.remote.socket.DriverStatusChangedNotification
typealias DriversUpdatedNotification = com.weelo.logistics.data.remote.socket.DriversUpdatedNotification
typealias DriverTimeoutNotification = com.weelo.logistics.data.remote.socket.DriverTimeoutNotification
typealias TripCancelledNotification = com.weelo.logistics.data.remote.socket.TripCancelledNotification
typealias OrderCancelledNotification = com.weelo.logistics.data.remote.socket.OrderCancelledNotification
typealias BroadcastDismissedNotification = com.weelo.logistics.data.remote.socket.BroadcastDismissedNotification
typealias DriverSOSAlertNotification = com.weelo.logistics.data.remote.socket.DriverSOSAlertNotification
typealias FlexHoldStartedNotification = com.weelo.logistics.data.remote.socket.FlexHoldStartedNotification
typealias FlexHoldExtendedNotification = com.weelo.logistics.data.remote.socket.FlexHoldExtendedNotification
typealias CascadeReassignedNotification = com.weelo.logistics.data.remote.socket.CascadeReassignedNotification
typealias DriverDeclinedNotification = com.weelo.logistics.data.remote.socket.DriverDeclinedNotification
typealias DriverRatingUpdatedNotification = com.weelo.logistics.data.remote.socket.DriverRatingUpdatedNotification
typealias DriverAcceptedNotification = com.weelo.logistics.data.remote.socket.DriverAcceptedNotification
typealias HoldExpiredNotification = com.weelo.logistics.data.remote.socket.HoldExpiredNotification
typealias BookingPartiallyFilledNotification = com.weelo.logistics.data.remote.socket.BookingPartiallyFilledNotification
typealias RequestNoLongerAvailableNotification = com.weelo.logistics.data.remote.socket.RequestNoLongerAvailableNotification
typealias OrderStatusUpdateNotification = com.weelo.logistics.data.remote.socket.OrderStatusUpdateNotification
typealias DriverMayBeOfflineNotification = com.weelo.logistics.data.remote.socket.DriverMayBeOfflineNotification
typealias AssignmentStaleNotification = com.weelo.logistics.data.remote.socket.AssignmentStaleNotification

/**
 * =============================================================================
 * SOCKET.IO SERVICE - Facade for Real-time Communication
 * =============================================================================
 *
 * Singleton facade that delegates to:
 *   - [SocketConnectionManager] — connect/disconnect/heartbeat/RAMEN replay
 *   - [SocketEventRouter]       — event listeners, JSON parsing, SharedFlow emissions
 *   - [SocketConstants]         — event name string constants
 *
 * All public API remains unchanged. Existing callers continue to reference:
 *   SocketIOService.connect(url, token)
 *   SocketIOService.newBroadcasts
 *   SocketIOService.Events.NEW_BROADCAST
 *   SocketIOService.driverStatusChanged
 *
 * No caller changes required.
 * =============================================================================
 */
object SocketIOService {

    private const val TAG = "SocketIOService"

    // =========================================================================
    // SHARED FLOWS — owned here, passed to router for emission
    // =========================================================================

    private val _connectionState = MutableStateFlow<SocketConnectionState>(RealSocketConnectionState.Disconnected)
    val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    private val _newBroadcasts = MutableSharedFlow<BroadcastNotification>(replay = 0, extraBufferCapacity = 50)
    val newBroadcasts: SharedFlow<BroadcastNotification> = _newBroadcasts.asSharedFlow()

    private val _truckAssigned = MutableSharedFlow<com.weelo.logistics.data.remote.socket.TruckAssignedNotification>(replay = 0, extraBufferCapacity = 20)
    val truckAssigned: SharedFlow<com.weelo.logistics.data.remote.socket.TruckAssignedNotification> = _truckAssigned.asSharedFlow()

    private val _assignmentStatusChanged = MutableSharedFlow<AssignmentStatusNotification>(replay = 0, extraBufferCapacity = 20)
    val assignmentStatusChanged: SharedFlow<AssignmentStatusNotification> = _assignmentStatusChanged.asSharedFlow()

    private val _bookingUpdated = MutableSharedFlow<BookingUpdatedNotification>(replay = 0, extraBufferCapacity = 20)
    val bookingUpdated: SharedFlow<BookingUpdatedNotification> = _bookingUpdated.asSharedFlow()

    private val _trucksRemainingUpdates = MutableSharedFlow<TrucksRemainingNotification>(replay = 0, extraBufferCapacity = 20)
    val trucksRemainingUpdates: SharedFlow<TrucksRemainingNotification> = _trucksRemainingUpdates.asSharedFlow()

    private val _errors = MutableSharedFlow<SocketError>(replay = 0, extraBufferCapacity = 10)
    val errors: SharedFlow<SocketError> = _errors.asSharedFlow()

    private val _fleetUpdated = MutableSharedFlow<FleetUpdatedNotification>(replay = 0, extraBufferCapacity = 20)
    val fleetUpdated: SharedFlow<FleetUpdatedNotification> = _fleetUpdated.asSharedFlow()

    private val _vehicleRegistered = MutableSharedFlow<VehicleRegisteredNotification>(replay = 0, extraBufferCapacity = 10)
    val vehicleRegistered: SharedFlow<VehicleRegisteredNotification> = _vehicleRegistered.asSharedFlow()

    private val _driverAdded = MutableSharedFlow<DriverAddedNotification>(replay = 0, extraBufferCapacity = 10)
    val driverAdded: SharedFlow<DriverAddedNotification> = _driverAdded.asSharedFlow()

    private val _driversUpdated = MutableSharedFlow<DriversUpdatedNotification>(replay = 0, extraBufferCapacity = 20)
    val driversUpdated: SharedFlow<DriversUpdatedNotification> = _driversUpdated.asSharedFlow()

    private val _tripAssigned = MutableSharedFlow<TripAssignedNotification>(replay = 1, extraBufferCapacity = 10)
    val tripAssigned: SharedFlow<TripAssignedNotification> = _tripAssigned.asSharedFlow()

    private val _driverTimeout = MutableSharedFlow<DriverTimeoutNotification>(replay = 0, extraBufferCapacity = 10)
    val driverTimeout: SharedFlow<DriverTimeoutNotification> = _driverTimeout.asSharedFlow()

    private val _tripCancelled = MutableSharedFlow<TripCancelledNotification>(replay = 0, extraBufferCapacity = 10)
    val tripCancelled: SharedFlow<TripCancelledNotification> = _tripCancelled.asSharedFlow()

    private val _orderCancelled = MutableSharedFlow<OrderCancelledNotification>(replay = 0, extraBufferCapacity = 10)
    val orderCancelled: SharedFlow<OrderCancelledNotification> = _orderCancelled.asSharedFlow()

    private val _broadcastDismissed = MutableSharedFlow<BroadcastDismissedNotification>(replay = 0, extraBufferCapacity = 20)
    val broadcastDismissed: SharedFlow<BroadcastDismissedNotification> = _broadcastDismissed.asSharedFlow()

    private val _driverStatusChanged = MutableSharedFlow<DriverStatusChangedNotification>(replay = 0, extraBufferCapacity = 20)
    val driverStatusChanged: SharedFlow<DriverStatusChangedNotification> = _driverStatusChanged.asSharedFlow()

    private val _driverSOSAlert = MutableSharedFlow<DriverSOSAlertNotification>(replay = 1, extraBufferCapacity = 5)
    val driverSOSAlert: SharedFlow<DriverSOSAlertNotification> = _driverSOSAlert.asSharedFlow()

    // F-C-50: Phase-1 hold kick-off signal. Backend emits on successful
    // `FlexHoldService.createFlexHold` ledger row creation.
    private val _flexHoldStarted = MutableSharedFlow<FlexHoldStartedNotification>(replay = 0, extraBufferCapacity = 10)
    val flexHoldStarted: SharedFlow<FlexHoldStartedNotification> = _flexHoldStarted.asSharedFlow()

    private val _flexHoldExtended = MutableSharedFlow<FlexHoldExtendedNotification>(replay = 0, extraBufferCapacity = 10)
    val flexHoldExtended: SharedFlow<FlexHoldExtendedNotification> = _flexHoldExtended.asSharedFlow()

    private val _cascadeReassigned = MutableSharedFlow<CascadeReassignedNotification>(replay = 0, extraBufferCapacity = 10)
    val cascadeReassigned: SharedFlow<CascadeReassignedNotification> = _cascadeReassigned.asSharedFlow()

    private val _driverDeclined = MutableSharedFlow<DriverDeclinedNotification>(replay = 0, extraBufferCapacity = 10)
    val driverDeclined: SharedFlow<DriverDeclinedNotification> = _driverDeclined.asSharedFlow()

    private val _driverRatingUpdated = MutableSharedFlow<DriverRatingUpdatedNotification>(replay = 0, extraBufferCapacity = 10)
    val driverRatingUpdated: SharedFlow<DriverRatingUpdatedNotification> = _driverRatingUpdated.asSharedFlow()

    private val _driverAccepted = MutableSharedFlow<DriverAcceptedNotification>(replay = 0, extraBufferCapacity = 10)
    val driverAccepted: SharedFlow<DriverAcceptedNotification> = _driverAccepted.asSharedFlow()

    private val _holdExpired = MutableSharedFlow<HoldExpiredNotification>(replay = 0, extraBufferCapacity = 10)
    val holdExpired: SharedFlow<HoldExpiredNotification> = _holdExpired.asSharedFlow()

    private val _bookingPartiallyFilled = MutableSharedFlow<BookingPartiallyFilledNotification>(replay = 0, extraBufferCapacity = 10)
    val bookingPartiallyFilled: SharedFlow<BookingPartiallyFilledNotification> = _bookingPartiallyFilled.asSharedFlow()

    private val _requestNoLongerAvailable = MutableSharedFlow<RequestNoLongerAvailableNotification>(replay = 0, extraBufferCapacity = 10)
    val requestNoLongerAvailable: SharedFlow<RequestNoLongerAvailableNotification> = _requestNoLongerAvailable.asSharedFlow()

    private val _orderStatusUpdate = MutableSharedFlow<OrderStatusUpdateNotification>(replay = 0, extraBufferCapacity = 10)
    val orderStatusUpdate: SharedFlow<OrderStatusUpdateNotification> = _orderStatusUpdate.asSharedFlow()

    private val _driverMayBeOffline = MutableSharedFlow<DriverMayBeOfflineNotification>(replay = 0, extraBufferCapacity = 10)
    val driverMayBeOffline: SharedFlow<DriverMayBeOfflineNotification> = _driverMayBeOffline.asSharedFlow()

    private val _assignmentStale = MutableSharedFlow<AssignmentStaleNotification>(replay = 0, extraBufferCapacity = 10)
    val assignmentStale: SharedFlow<AssignmentStaleNotification> = _assignmentStale.asSharedFlow()

    // =========================================================================
    // EVENT CONSTANTS — backward-compatible alias
    // =========================================================================

    /** Alias so `SocketIOService.Events.NEW_BROADCAST` still compiles. */
    val Events = SocketConstants

    // =========================================================================
    // INTERNAL DELEGATES (lazy-initialized)
    // =========================================================================

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connectionManager: SocketConnectionManager by lazy {
        SocketConnectionManager(
            serviceScope = serviceScope,
            connectionState = _connectionState,
            onSetupEventListeners = { socket -> eventRouter.wireEventListeners(socket) }
        )
    }

    private val eventRouter: SocketEventRouter by lazy {
        SocketEventRouter(
            serviceScope = serviceScope,
            connectionManager = connectionManager,
            _newBroadcasts = _newBroadcasts,
            _truckAssigned = _truckAssigned,
            _assignmentStatusChanged = _assignmentStatusChanged,
            _bookingUpdated = _bookingUpdated,
            _trucksRemainingUpdates = _trucksRemainingUpdates,
            _errors = _errors,
            _fleetUpdated = _fleetUpdated,
            _vehicleRegistered = _vehicleRegistered,
            _driverAdded = _driverAdded,
            _driversUpdated = _driversUpdated,
            _tripAssigned = _tripAssigned,
            _driverTimeout = _driverTimeout,
            _tripCancelled = _tripCancelled,
            _orderCancelled = _orderCancelled,
            _broadcastDismissed = _broadcastDismissed,
            _driverStatusChanged = _driverStatusChanged,
            _driverSOSAlert = _driverSOSAlert,
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
            _assignmentStale = _assignmentStale
        )
    }

    // =========================================================================
    // PUBLIC CONNECTION API — delegates to SocketConnectionManager
    // =========================================================================

    fun connect(url: String, token: String) = connectionManager.connect(url, token)

    fun disconnect() = connectionManager.disconnect()

    fun reconnect() = connectionManager.reconnect()

    fun isConnected(): Boolean = connectionManager.isConnected()

    // =========================================================================
    // HEARTBEAT API — delegates to SocketConnectionManager
    // =========================================================================

    fun isOnlineLocally(): Boolean = connectionManager.isOnlineLocally()

    fun setOnlineLocally(online: Boolean) = connectionManager.setOnlineLocally(online)

    fun startHeartbeat() = connectionManager.startHeartbeat()

    fun stopHeartbeat() = connectionManager.stopHeartbeat()

    // =========================================================================
    // CLIENT -> SERVER EVENTS — delegates to SocketConnectionManager
    // =========================================================================

    fun joinBookingRoom(bookingId: String) {
        if (_connectionState.value !is RealSocketConnectionState.Connected) {
            timber.log.Timber.w("Cannot join room: Not connected")
            return
        }
        connectionManager.emitEvent(SocketConstants.JOIN_BOOKING, bookingId)
        timber.log.Timber.d("Joined booking room: $bookingId")
    }

    fun leaveBookingRoom(bookingId: String) {
        connectionManager.emitEvent(SocketConstants.LEAVE_BOOKING, bookingId)
        timber.log.Timber.d("Left booking room: $bookingId")
    }

    fun joinOrderRoom(orderId: String) {
        if (_connectionState.value !is RealSocketConnectionState.Connected) {
            timber.log.Timber.w("Cannot join order room: Not connected")
            return
        }
        connectionManager.emitEvent(SocketConstants.JOIN_ORDER, orderId)
        timber.log.Timber.d("Joined order room: $orderId")
    }

    fun leaveOrderRoom(orderId: String) {
        connectionManager.emitEvent(SocketConstants.LEAVE_ORDER, orderId)
        timber.log.Timber.d("Left order room: $orderId")
    }

    fun updateLocation(tripId: String, latitude: Double, longitude: Double, speed: Float = 0f, bearing: Float = 0f) {
        if (_connectionState.value !is RealSocketConnectionState.Connected) {
            timber.log.Timber.w("Cannot update location: Not connected")
            return
        }
        val data = JSONObject().apply {
            put("tripId", tripId)
            put("latitude", latitude)
            put("longitude", longitude)
            put("speed", speed)
            put("bearing", bearing)
        }
        connectionManager.emitEvent(SocketConstants.UPDATE_LOCATION, data)
    }

    // =========================================================================
    // DISPATCH ACK + SOS — delegates to SocketEventRouter
    // =========================================================================

    fun emitDispatchAck(
        orderId: String,
        seq: Long = 0L,
        dispatchRevision: Long? = null,
        source: String,
        receivedAtMs: Long = System.currentTimeMillis()
    ) = eventRouter.emitDispatchAck(orderId, seq, dispatchRevision, source, receivedAtMs)

    fun emitDriverSOS(tripId: String, driverId: String) = eventRouter.emitDriverSOS(tripId, driverId)
}
