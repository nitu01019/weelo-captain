package com.weelo.logistics.data.remote

import com.weelo.logistics.broadcast.BroadcastOverlayManager
import com.weelo.logistics.broadcast.BroadcastFeatureFlagsRegistry
import com.weelo.logistics.broadcast.BroadcastFlowCoordinator
import com.weelo.logistics.broadcast.BroadcastIngressEnvelope
import com.weelo.logistics.broadcast.BroadcastIngressSource
import com.weelo.logistics.broadcast.BroadcastPayloadNormalizer
import com.weelo.logistics.broadcast.BroadcastRolePolicy
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import com.weelo.logistics.broadcast.BroadcastUiTiming
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * =============================================================================
 * SOCKET.IO SERVICE - Real-time Communication for Captain App
 * =============================================================================
 * 
 * Connects to weelo-backend via Socket.IO for real-time updates:
 * - New broadcast notifications (new booking requests)
 * - Truck assignment confirmations
 * - Order status updates
 * - Location tracking
 * 
 * SECURITY:
 * - JWT authentication required for connection
 * - Room-based isolation (transporters only see their broadcasts)
 * 
 * SCALABILITY:
 * - Automatic reconnection with exponential backoff
 * - Connection quality monitoring
 * - Efficient event batching
 * 
 * FOR BACKEND DEVELOPERS:
 * - Backend: weelo-backend/src/shared/services/socket.service.ts
 * - Events must match between client and server
 * =============================================================================
 */
object SocketIOService {
    
    private const val TAG = "SocketIOService"
    
    // Socket instance
    private var socket: Socket? = null
    
    // Connection state
    private val _connectionState = MutableStateFlow<SocketConnectionState>(SocketConnectionState.Disconnected)
    val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()
    
    // Event flows for UI consumption
    private val _newBroadcasts = MutableSharedFlow<BroadcastNotification>(replay = 0, extraBufferCapacity = 50)
    val newBroadcasts: SharedFlow<BroadcastNotification> = _newBroadcasts.asSharedFlow()
    
    private val _truckAssigned = MutableSharedFlow<TruckAssignedNotification>(replay = 0, extraBufferCapacity = 20)
    val truckAssigned: SharedFlow<TruckAssignedNotification> = _truckAssigned.asSharedFlow()
    
    private val _assignmentStatusChanged = MutableSharedFlow<AssignmentStatusNotification>(replay = 0, extraBufferCapacity = 20)
    val assignmentStatusChanged: SharedFlow<AssignmentStatusNotification> = _assignmentStatusChanged.asSharedFlow()
    
    private val _bookingUpdated = MutableSharedFlow<BookingUpdatedNotification>(replay = 0, extraBufferCapacity = 20)
    val bookingUpdated: SharedFlow<BookingUpdatedNotification> = _bookingUpdated.asSharedFlow()
    
    private val _trucksRemainingUpdates = MutableSharedFlow<TrucksRemainingNotification>(replay = 0, extraBufferCapacity = 20)
    val trucksRemainingUpdates: SharedFlow<TrucksRemainingNotification> = _trucksRemainingUpdates.asSharedFlow()
    
    private val _errors = MutableSharedFlow<SocketError>(replay = 0, extraBufferCapacity = 10)
    val errors: SharedFlow<SocketError> = _errors.asSharedFlow()
    
    // Fleet/Vehicle update events (NEW - for real-time fleet updates)
    private val _fleetUpdated = MutableSharedFlow<FleetUpdatedNotification>(replay = 0, extraBufferCapacity = 20)
    val fleetUpdated: SharedFlow<FleetUpdatedNotification> = _fleetUpdated.asSharedFlow()
    
    private val _vehicleRegistered = MutableSharedFlow<VehicleRegisteredNotification>(replay = 0, extraBufferCapacity = 10)
    val vehicleRegistered: SharedFlow<VehicleRegisteredNotification> = _vehicleRegistered.asSharedFlow()
    
    // Driver update events (NEW - for real-time driver updates)
    private val _driverAdded = MutableSharedFlow<DriverAddedNotification>(replay = 0, extraBufferCapacity = 10)
    val driverAdded: SharedFlow<DriverAddedNotification> = _driverAdded.asSharedFlow()
    
    private val _driversUpdated = MutableSharedFlow<DriversUpdatedNotification>(replay = 0, extraBufferCapacity = 20)
    val driversUpdated: SharedFlow<DriversUpdatedNotification> = _driversUpdated.asSharedFlow()
    
    // ==========================================================================
    // TRIP ASSIGNMENT EVENTS - Driver receives new trip from transporter
    // ==========================================================================
    // Backend emits 'trip_assigned' when transporter confirms hold with assignments.
    // This flow delivers the notification to the UI for showing Accept/Decline screen.
    // replay = 0 prevents stale one-shot events from re-triggering UI flows.
    // ==========================================================================
    private val _tripAssigned = MutableSharedFlow<TripAssignedNotification>(replay = 0, extraBufferCapacity = 10)
    val tripAssigned: SharedFlow<TripAssignedNotification> = _tripAssigned.asSharedFlow()
    
    private val _driverTimeout = MutableSharedFlow<DriverTimeoutNotification>(replay = 0, extraBufferCapacity = 10)
    val driverTimeout: SharedFlow<DriverTimeoutNotification> = _driverTimeout.asSharedFlow()
    
    private val _tripCancelled = MutableSharedFlow<TripCancelledNotification>(replay = 0, extraBufferCapacity = 10)
    val tripCancelled: SharedFlow<TripCancelledNotification> = _tripCancelled.asSharedFlow()
    
    // ==========================================================================
    // ORDER CANCELLATION EVENTS - Customer cancels order
    // ==========================================================================
    // Separate flow from bookingUpdated so driver/transporter screens can
    // specifically react to cancellations (e.g., auto-dismiss trip screen,
    // show reason in snackbar, navigate back to dashboard).
    // ==========================================================================
    private val _orderCancelled = MutableSharedFlow<OrderCancelledNotification>(replay = 0, extraBufferCapacity = 10)
    val orderCancelled: SharedFlow<OrderCancelledNotification> = _orderCancelled.asSharedFlow()
    
    // ==========================================================================
    // BROADCAST DISMISSED — Graceful fade+scroll before removal
    // ==========================================================================
    // Emitted when a broadcast should be dismissed with a message (not instant remove).
    // UI observes this to show blur overlay + reason message on the card,
    // then auto-scrolls to next card and removes after a delay.
    // ==========================================================================
    private val _broadcastDismissed = MutableSharedFlow<BroadcastDismissedNotification>(replay = 0, extraBufferCapacity = 20)
    val broadcastDismissed: SharedFlow<BroadcastDismissedNotification> = _broadcastDismissed.asSharedFlow()
    
    // ==========================================================================
    // DRIVER STATUS CHANGED — Transporter receives real-time online/offline
    // ==========================================================================
    private val _driverStatusChanged = MutableSharedFlow<DriverStatusChangedNotification>(replay = 0, extraBufferCapacity = 20)
    val driverStatusChanged: SharedFlow<DriverStatusChangedNotification> = _driverStatusChanged.asSharedFlow()
    
    // ==========================================================================
    // HEARTBEAT — Driver sends every 12s to keep presence alive
    // ==========================================================================
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var _isOnlineLocally = false  // Tracks driver's local online intent (accessed from Socket.IO + Main threads)

    /**
     * Returns the current local online state (heartbeat running or not).
     * Used by DriverDashboardViewModel as fallback when availability API fails.
     */
    fun isOnlineLocally(): Boolean = _isOnlineLocally
    
    /**
     * Set the local online flag and start/stop heartbeat accordingly.
     * Called by DriverDashboardViewModel after API call succeeds.
     *
     * @param online true = start heartbeat, false = stop heartbeat
     */
    fun setOnlineLocally(online: Boolean) {
        _isOnlineLocally = online
        if (online) {
            startHeartbeat()
        } else {
            stopHeartbeat()
        }
    }
    
    /**
     * Start heartbeat — sends {type: "heartbeat"} every 12 seconds
     * Backend extends Redis presence TTL to 35s on each heartbeat.
     * If heartbeat stops → TTL expires → driver auto-offline.
     *
     * TTL DESIGN:
     *   Heartbeat interval = 12 seconds
     *   Redis TTL = 35 seconds
     *   → 3 retry windows before auto-offline
     *   → Handles 4G instability / network jitter
     */
    fun startHeartbeat() {
        // Cancel existing heartbeat if any
        heartbeatJob?.cancel()
        
        heartbeatJob = serviceScope.launch {
            timber.log.Timber.i("💓 Heartbeat started (every 12s)")
            while (isActive) {
                try {
                    val heartbeatData = org.json.JSONObject().apply {
                        put("type", "heartbeat")
                        // Attach latest GPS so backend refreshes Redis GEO index
                        try {
                            val ctx = WeeloApp.getInstance()?.applicationContext
                            if (ctx != null) {
                                val locationManager = ctx.getSystemService(
                                    android.content.Context.LOCATION_SERVICE
                                ) as? android.location.LocationManager
                                @Suppress("MissingPermission")
                                val loc = locationManager?.getLastKnownLocation(
                                    android.location.LocationManager.GPS_PROVIDER
                                ) ?: locationManager?.getLastKnownLocation(
                                    android.location.LocationManager.NETWORK_PROVIDER
                                )
                                if (loc != null) {
                                    put("lat", loc.latitude)
                                    put("lng", loc.longitude)
                                    put("speed", loc.speed.toDouble())
                                    put("battery", getDeviceBatteryLevel(ctx))
                                }
                            }
                        } catch (locErr: Exception) {
                            // Non-critical — heartbeat still extends presence TTL
                            timber.log.Timber.w("GPS unavailable for heartbeat: ${locErr.message}")
                        }
                    }
                    socket?.emit(Events.HEARTBEAT, heartbeatData)
                } catch (e: Exception) {
                    timber.log.Timber.w("💓 Heartbeat emit failed: ${e.message}")
                }
                kotlinx.coroutines.delay(12_000) // 12 seconds
            }
        }
    }
    
    /**
     * Get device battery level (0-100) for heartbeat telemetry.
     * Returns -1 if unavailable.
     */
    private fun getDeviceBatteryLevel(ctx: android.content.Context): Int {
        return try {
            val bm = ctx.getSystemService(android.content.Context.BATTERY_SERVICE)
                as? android.os.BatteryManager
            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (_: Exception) { -1 }
    }
    
    /**
     * Stop heartbeat — called when driver goes offline or disconnects.
     * Redis TTL will handle auto-offline after 35s.
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        timber.log.Timber.i("💓 Heartbeat stopped")
    }
    
    // Stored credentials for reconnection
    private var serverUrl: String? = null
    private var authToken: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val BROADCAST_OVERLAY_WATCHDOG_MS = 2_500L
    private val directBroadcastSocketEvents = setOf("new_broadcast", "new_order_alert", "new_truck_request")
    private val directCancellationSocketEvents = setOf(
        "order_cancelled",
        "booking_cancelled",
        "broadcast_dismissed",
        "broadcast_expired",
        "booking_expired",
        "order_expired"
    )
    private val fallbackBroadcastSocketEvents = setOf("message", "new_booking_request", "broadcast_request", "broadcast_available")
    private val broadcastPayloadTypes = setOf("new_broadcast", "new_truck_request")
    private val cancellationPayloadTypes = setOf(
        "order_cancelled",
        "booking_cancelled",
        "broadcast_dismissed",
        "broadcast_expired",
        "booking_expired",
        "order_expired"
    )

    private fun <T> emitHot(flow: MutableSharedFlow<T>, value: T) {
        if (!flow.tryEmit(value)) {
            serviceScope.launch {
                flow.emit(value)
            }
        }
    }
    
    /**
     * ==========================================================================
     * SOCKET EVENTS - Must match backend socket.service.ts
     * ==========================================================================
     */
    object Events {
        // Server -> Client
        const val CONNECTED = "connected"
        const val NEW_BROADCAST = "new_broadcast"
        const val NEW_TRUCK_REQUEST = "new_truck_request"
        const val BOOKING_UPDATED = "booking_updated"
        const val TRUCK_ASSIGNED = "truck_assigned"
        const val ASSIGNMENT_STATUS_CHANGED = "assignment_status_changed"
        const val TRUCKS_REMAINING_UPDATE = "trucks_remaining_update"
        const val BOOKING_EXPIRED = "booking_expired"
        const val BOOKING_CANCELLED = "booking_cancelled"
        const val BOOKING_FULLY_FILLED = "booking_fully_filled"
        const val BROADCAST_DISMISSED = "broadcast_dismissed"
        const val NEW_ORDER_ALERT = "new_order_alert"
        const val ACCEPT_CONFIRMATION = "accept_confirmation"
        const val ERROR = "error"
        
        // Fleet/Vehicle events (NEW - for real-time fleet updates)
        const val VEHICLE_REGISTERED = "vehicle_registered"
        const val VEHICLE_UPDATED = "vehicle_updated"
        const val VEHICLE_DELETED = "vehicle_deleted"
        const val VEHICLE_STATUS_CHANGED = "vehicle_status_changed"
        const val FLEET_UPDATED = "fleet_updated"
        
        // Driver events (NEW - for real-time driver updates)
        const val DRIVER_ADDED = "driver_added"
        const val DRIVER_UPDATED = "driver_updated"
        const val DRIVER_DELETED = "driver_deleted"
        const val DRIVER_STATUS_CHANGED = "driver_status_changed"
        const val DRIVERS_UPDATED = "drivers_updated"
        
        // =============================================================
        // TRIP ASSIGNMENT EVENTS - Driver receives trip from transporter
        // =============================================================
        // Backend emits from truck-hold.service.ts → confirmHoldWithAssignments()
        // This is how drivers learn about new trips assigned to them
        const val TRIP_ASSIGNED = "trip_assigned"
        const val DRIVER_TIMEOUT = "driver_timeout"       // Driver didn't respond in time
        const val TRIP_CANCELLED = "trip_cancelled"       // Driver trip cancelled (new primary contract)
        
        // Order lifecycle events
        const val ORDER_CANCELLED = "order_cancelled"  // When customer cancels order
        const val ORDER_EXPIRED = "order_expired"      // When order times out
        
        // Driver presence events
        const val HEARTBEAT = "heartbeat"              // Driver → Server every 12s
        const val DRIVER_ONLINE = "driver_online"      // Server → Transporter
        const val DRIVER_OFFLINE = "driver_offline"    // Server → Transporter
        
        // Client -> Server
        const val JOIN_BOOKING = "join_booking"
        const val LEAVE_BOOKING = "leave_booking"
        const val JOIN_ORDER = "join_order"
        const val LEAVE_ORDER = "leave_order"
        const val UPDATE_LOCATION = "update_location"
    }
    
    /**
     * Connect to Socket.IO server
     * 
     * CRITICAL: This establishes the WebSocket connection for receiving broadcasts!
     * If connection fails, transporters won't receive booking notifications.
     * 
     * @param url Server URL (e.g., "http://10.0.2.2:3000" for emulator)
     * @param token JWT authentication token
     */
    fun connect(url: String, token: String) {
        timber.log.Timber.i("╔══════════════════════════════════════════════════════════════╗")
        timber.log.Timber.i("║  🔌 SOCKET.IO CONNECT CALLED                                 ║")
        timber.log.Timber.i("╠══════════════════════════════════════════════════════════════╣")
        timber.log.Timber.i("║  URL: $url")
        timber.log.Timber.i("║  Token length: ${token.length}")
        timber.log.Timber.i("║  Current state: ${_connectionState.value}")
        timber.log.Timber.i("╚══════════════════════════════════════════════════════════════╝")
        
        val currentState = _connectionState.value
        val isSameConnectionRequest = serverUrl == url && authToken == token
        if ((currentState is SocketConnectionState.Connected || currentState is SocketConnectionState.Connecting) &&
            isSameConnectionRequest
        ) {
            timber.log.Timber.w("⚠️ Socket already %s for same credentials, skipping reconnect", currentState::class.simpleName)
            return
        }
        
        // CRITICAL FIX: Disconnect stale socket before creating a new one.
        // The guard above only catches Connected state — if we're still in Connecting
        // (e.g., previous attempt timed out), we'd create a second socket instance
        // with duplicate listeners and events. Always clean up first.
        if (socket != null && _connectionState.value !is SocketConnectionState.Connected) {
            timber.log.Timber.w("⚠️ Cleaning up stale socket before creating a new connection")
            socket?.off()
            socket?.disconnect()
            socket = null
        }
        
        serverUrl = url
        authToken = token
        
        _connectionState.value = SocketConnectionState.Connecting
        timber.log.Timber.i("🔌 Connecting to Socket.IO server: $url")
        
        try {
            // Socket.IO options with authentication
            val options = IO.Options().apply {
                val authPayload = mutableMapOf<String, String>(
                    "token" to token
                )
                val cachedFcmToken = WeeloFirebaseService.fcmToken?.takeIf { it.isNotBlank() }
                if (cachedFcmToken != null) {
                    authPayload["fcmToken"] = cachedFcmToken
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.SOCKET_AUTH,
                        status = BroadcastStatus.SUCCESS,
                        attrs = mapOf("hasFcmToken" to "true")
                    )
                } else {
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.SOCKET_AUTH,
                        status = BroadcastStatus.SKIPPED,
                        reason = "missing_fcm_token",
                        attrs = mapOf("hasFcmToken" to "false")
                    )
                    timber.log.Timber.w("⚠️ Socket auth started without FCM token; push fallback may be delayed")
                }
                auth = authPayload
                reconnection = true
                reconnectionAttempts = 10
                // JITTERED BACKOFF: Add 0-2s random jitter to prevent thundering herd
                // when thousands of transporters reconnect simultaneously after network outage.
                // Without jitter, all reconnect at T+1s → Redis/ALB overwhelmed.
                reconnectionDelay = 1000L + kotlin.random.Random.nextLong(2000L)
                reconnectionDelayMax = 30000
                timeout = 20000
                forceNew = true
                transports = arrayOf("websocket", "polling")
            }
            
            socket = IO.socket(URI.create(url), options)
            
            setupEventListeners()
            
            socket?.connect()
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Connection error: ${e.message}")
            _connectionState.value = SocketConnectionState.Error(e.message ?: "Connection failed")
        }
    }
    
    /**
     * Setup all event listeners
     * OPTIMIZATION: Remove old listeners before adding new ones to prevent memory leaks
     */
    private fun setupEventListeners() {
        socket?.apply {
            // CRITICAL: Remove all existing listeners to prevent duplicates and memory leaks
            off()
            
            // Connection events
            on(Socket.EVENT_CONNECT) {
                timber.log.Timber.i("✅ Socket.IO connected")
                _connectionState.value = SocketConnectionState.Connected
                BroadcastOverlayManager.onSocketReconnected()
                if (BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                    BroadcastFlowCoordinator.requestReconcile(force = true)
                }
                
                // AUTO-RECONNECT HEARTBEAT:
                // If driver was ONLINE before disconnect, auto-restart heartbeat.
                // Backend will restore Redis presence via restorePresence().
                // Driver does NOT need to press button again.
                if (_isOnlineLocally) {
                    timber.log.Timber.i("💓 Auto-restarting heartbeat on reconnect (driver was online)")
                    startHeartbeat()
                }
                
                // Relay latest FCM token to backend on every connect/reconnect
                // Handles: app reinstall, cache clear, token refresh while offline
                val latestFcmToken = WeeloFirebaseService.fcmToken
                if (!latestFcmToken.isNullOrBlank()) {
                    socket?.emit("fcm_token_refresh", org.json.JSONObject().apply {
                        put("fcmToken", latestFcmToken)
                    })
                    timber.log.Timber.i("🔄 FCM token relayed to backend on connect")
                }
            }
            
            on(Socket.EVENT_DISCONNECT) { args ->
                val reason = args.firstOrNull()?.toString() ?: "unknown"
                timber.log.Timber.w("🔌 Socket.IO disconnected: $reason")
                _connectionState.value = SocketConnectionState.Disconnected
                
                // Stop heartbeat on disconnect — TTL (35s) handles auto-offline
                stopHeartbeat()
            }
            
            on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown"
                timber.log.Timber.e("❌ Connection error: $error")
                _connectionState.value = SocketConnectionState.Error(error)
            }
            
            // Server confirmation
            on(Events.CONNECTED) { args ->
                val data = args.firstOrNull()
                timber.log.Timber.i("✅ Server confirmed connection: $data")
            }
            
            // Error events
            on(Events.ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown"
                timber.log.Timber.e("❌ Server error: $error")
                emitHot(_errors, SocketError(error))
            }
            
            // New broadcast notification (new booking request)
            on(Events.NEW_BROADCAST) { args ->
                handleNewBroadcast(args, Events.NEW_BROADCAST)
            }

            // Legacy alias from older backend/app payload contracts.
            on(Events.NEW_TRUCK_REQUEST) { args ->
                handleNewBroadcast(args, Events.NEW_TRUCK_REQUEST)
            }

            on(Events.NEW_ORDER_ALERT) { args ->
                handleNewBroadcast(args, Events.NEW_ORDER_ALERT)
            }

            // Fallback ingress to protect against event-name drift while keeping
            // existing direct listeners as primary.
            onAnyIncoming(Emitter.Listener { incomingArgs ->
                if (incomingArgs.isEmpty()) return@Listener
                val rawIncomingEvent = incomingArgs.firstOrNull()?.toString()?.trim().orEmpty()
                if (rawIncomingEvent.isBlank()) return@Listener
                val payloadArgs = incomingArgs
                    .drop(1)
                    .filterNotNull()
                    .toTypedArray()
                maybeRouteIncomingBroadcastFallback(rawIncomingEvent, payloadArgs)
            })
            
            // Truck assigned notification
            on(Events.TRUCK_ASSIGNED) { args ->
                handleTruckAssigned(args)
            }
            
            // Assignment status changed
            on(Events.ASSIGNMENT_STATUS_CHANGED) { args ->
                handleAssignmentStatusChanged(args)
            }
            
            // Booking updated
            on(Events.BOOKING_UPDATED) { args ->
                handleBookingUpdated(args)
            }
            
            // Trucks remaining update
            on(Events.TRUCKS_REMAINING_UPDATE) { args ->
                handleTrucksRemainingUpdate(args)
            }
            
            // Accept confirmation
            on(Events.ACCEPT_CONFIRMATION) { args ->
                handleAcceptConfirmation(args)
            }
            
            // Booking expired
            on(Events.BOOKING_EXPIRED) { args ->
                handleBookingExpired(args)
            }

            // Booking cancelled alias (legacy + FCM/socket parity)
            on(Events.BOOKING_CANCELLED) { args ->
                if (BroadcastFeatureFlagsRegistry.current().captainCanonicalCancelAliasesEnabled) {
                    handleOrderCancelled(args)
                }
            }
            
            // Broadcast expired (NEW - backend sends this for timeout/cancellation)
            // This is the primary event for removing broadcasts from overlay
            on("broadcast_expired") { args ->
                handleBookingExpired(args)
            }

            // Broadcast dismissed alias from canonical cancel flow.
            on(Events.BROADCAST_DISMISSED) { args ->
                if (BroadcastFeatureFlagsRegistry.current().captainCanonicalCancelAliasesEnabled) {
                    handleBookingExpired(args)
                }
            }
            
            // Booking fully filled
            on(Events.BOOKING_FULLY_FILLED) { args ->
                handleBookingFullyFilled(args)
            }
            
            // =================================================================
            // ORDER LIFECYCLE EVENTS - Critical for real-time UI updates
            // =================================================================
            
            // Order cancelled by customer - IMMEDIATELY remove from overlay/list
            on(Events.ORDER_CANCELLED) { args ->
                handleOrderCancelled(args)
            }
            
            // Order expired (timeout) - Remove from UI
            on(Events.ORDER_EXPIRED) { args ->
                handleOrderExpired(args)
            }
            
            // Driver trip cancelled (timeout/cancel compatibility path)
            on(Events.TRIP_CANCELLED) { args ->
                handleTripCancelled(args)
            }
            
            // =================================================================
            // FLEET/VEHICLE EVENTS - Real-time fleet updates
            // =================================================================
            
            // Vehicle registered (new vehicle added to fleet)
            on(Events.VEHICLE_REGISTERED) { args ->
                handleVehicleRegistered(args)
            }
            
            // Vehicle updated
            on(Events.VEHICLE_UPDATED) { args ->
                handleFleetUpdated(args, "updated")
            }
            
            // Vehicle deleted
            on(Events.VEHICLE_DELETED) { args ->
                handleFleetUpdated(args, "deleted")
            }
            
            // Vehicle status changed
            on(Events.VEHICLE_STATUS_CHANGED) { args ->
                handleFleetUpdated(args, "status_changed")
            }
            
            // General fleet update (catch-all for any fleet changes)
            on(Events.FLEET_UPDATED) { args ->
                handleFleetUpdated(args, "fleet_updated")
            }
            
            // =================================================================
            // DRIVER EVENTS - Real-time driver updates
            // =================================================================
            
            // Driver added (new driver added to fleet)
            on(Events.DRIVER_ADDED) { args ->
                handleDriverAdded(args)
            }
            
            // Driver updated
            on(Events.DRIVER_UPDATED) { args ->
                handleDriversUpdated(args, "updated")
            }
            
            // Driver deleted
            on(Events.DRIVER_DELETED) { args ->
                handleDriversUpdated(args, "deleted")
            }
            
            // Driver status changed (general)
            on(Events.DRIVER_STATUS_CHANGED) { args ->
                handleDriversUpdated(args, "status_changed")
                // Also handle as driver online/offline status change
                handleDriverStatusChanged(args)
            }
            
            // General drivers update (catch-all for any driver changes)
            on(Events.DRIVERS_UPDATED) { args ->
                handleDriversUpdated(args, "drivers_updated")
            }
            
            // =================================================================
            // TRIP ASSIGNMENT EVENTS - Critical for driver flow
            // =================================================================
            // Backend (truck-hold.service.ts) emits 'trip_assigned' to driver
            // when transporter confirms hold with vehicle + driver assignment.
            // This is THE entry point for the entire driver trip flow.
            // =================================================================
            
            // Trip assigned to driver - show Accept/Decline screen
            on(Events.TRIP_ASSIGNED) { args ->
                handleTripAssigned(args)
            }
            
            // Driver didn't respond in time - notify transporter
            on(Events.DRIVER_TIMEOUT) { args ->
                handleDriverTimeout(args)
            }
        }
    }
    
    /**
     * Handle vehicle registered event
     */
    private fun handleVehicleRegistered(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("🚛 Vehicle registered: $data")
            
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
                // Also emit fleet updated for general refresh
                _fleetUpdated.emit(FleetUpdatedNotification(
                    action = "added",
                    vehicleId = notification.vehicleId,
                    totalVehicles = notification.totalVehicles,
                    availableCount = notification.availableCount,
                    inTransitCount = fleetStats?.optInt("in_transit", 0) ?: 0,
                    maintenanceCount = fleetStats?.optInt("maintenance", 0) ?: 0
                ))
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing vehicle registered: ${e.message}")
        }
    }
    
    /**
     * Handle fleet updated events (update, delete, status change)
     */
    private fun handleFleetUpdated(args: Array<Any>, action: String) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("🚛 Fleet updated ($action): $data")
            
            val fleetStats = data.optJSONObject("fleetStats")
            
            val notification = FleetUpdatedNotification(
                action = data.optString("action", action),
                vehicleId = data.optString("vehicleId", ""),
                totalVehicles = fleetStats?.optInt("total", 0) ?: 0,
                availableCount = fleetStats?.optInt("available", 0) ?: 0,
                inTransitCount = fleetStats?.optInt("in_transit", 0) ?: 0,
                maintenanceCount = fleetStats?.optInt("maintenance", 0) ?: 0
            )
            
            serviceScope.launch {
                _fleetUpdated.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing fleet update: ${e.message}")
        }
    }
    
    /**
     * Handle driver added event
     */
    private fun handleDriverAdded(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("👤 Driver added: $data")
            
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
                // Also emit drivers updated for general refresh
                _driversUpdated.emit(DriversUpdatedNotification(
                    action = "added",
                    driverId = notification.driverId,
                    totalDrivers = notification.totalDrivers,
                    availableCount = notification.availableCount,
                    onTripCount = notification.onTripCount
                ))
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing driver added: ${e.message}")
        }
    }
    
    /**
     * Handle driver online/offline status change (for transporter UI)
     *
     * Payload from backend:
     * { driverId, driverName, isOnline, action: "online"|"offline", timestamp }
     */
    private fun handleDriverStatusChanged(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            
            val driverId = data.optString("driverId", "")
            val driverName = data.optString("driverName", "")
            val isOnline = data.optBoolean("isOnline", false)
            val action = data.optString("action", "")
            
            timber.log.Timber.i("👤 Driver status changed: $driverName → ${if (isOnline) "ONLINE" else "OFFLINE"}")
            
            val notification = DriverStatusChangedNotification(
                driverId = driverId,
                driverName = driverName,
                isOnline = isOnline,
                action = action,
                timestamp = data.optString("timestamp", "")
            )
            
            serviceScope.launch {
                _driverStatusChanged.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing driver_status_changed: ${e.message}")
        }
    }
    
    /**
     * Handle drivers updated events (update, delete, status change)
     */
    private fun handleDriversUpdated(args: Array<Any>, action: String) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("👤 Drivers updated ($action): $data")
            
            val driverStats = data.optJSONObject("driverStats")
            
            val notification = DriversUpdatedNotification(
                action = data.optString("action", action),
                driverId = data.optString("driverId", ""),
                totalDrivers = driverStats?.optInt("total", 0) ?: 0,
                availableCount = driverStats?.optInt("available", 0) ?: 0,
                onTripCount = driverStats?.optInt("onTrip", 0) ?: 0
            )
            
            serviceScope.launch {
                _driversUpdated.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing drivers update: ${e.message}")
        }
    }
    
    // ==========================================================================
    // TRIP ASSIGNMENT HANDLERS
    // ==========================================================================
    
    /**
     * Handle trip_assigned event from backend
     * 
     * CRITICAL: This is the entry point for the entire driver trip flow!
     * Backend emits this from truck-hold.service.ts → confirmHoldWithAssignments()
     * when a transporter assigns a specific vehicle + driver to a trip.
     * 
     * Payload matches backend driverNotification object:
     * {
     *   type: "trip_assigned",
     *   assignmentId, tripId, orderId, truckRequestId,
     *   pickup: { address, city, lat, lng },
     *   drop: { address, city, lat, lng },
     *   routePoints: [...],
     *   vehicleNumber, farePerTruck, distanceKm,
     *   customerName, customerPhone,
     *   assignedAt, message
     * }
     * 
     * SCALABILITY: Uses SharedFlow with replay=1 so late collectors still get
     * the event (e.g., after screen rotation or activity recreation).
     */
    private fun handleTripAssigned(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            
            timber.log.Timber.i("╔══════════════════════════════════════════════════════════════╗")
            timber.log.Timber.i("║  🚛 NEW TRIP ASSIGNED TO DRIVER                              ║")
            timber.log.Timber.i("╠══════════════════════════════════════════════════════════════╣")
            timber.log.Timber.i("║  Assignment ID: ${data.optString("assignmentId", "")}")
            timber.log.Timber.i("║  Trip ID: ${data.optString("tripId", "")}")
            timber.log.Timber.i("║  Vehicle: ${data.optString("vehicleNumber", "")}")
            timber.log.Timber.i("║  Message: ${data.optString("message", "")}")
            timber.log.Timber.i("╚══════════════════════════════════════════════════════════════╝")
            
            // Parse pickup location
            val pickupObj = data.optJSONObject("pickup")
            val pickup = TripLocationInfo(
                address = pickupObj?.optString("address", "") ?: "",
                city = pickupObj?.optString("city", "") ?: "",
                latitude = pickupObj?.optDouble("lat", 0.0) ?: 0.0,
                longitude = pickupObj?.optDouble("lng", 0.0) ?: 0.0
            )
            
            // Parse drop location
            val dropObj = data.optJSONObject("drop")
            val drop = TripLocationInfo(
                address = dropObj?.optString("address", "") ?: "",
                city = dropObj?.optString("city", "") ?: "",
                latitude = dropObj?.optDouble("lat", 0.0) ?: 0.0,
                longitude = dropObj?.optDouble("lng", 0.0) ?: 0.0
            )
            
            val notification = TripAssignedNotification(
                assignmentId = data.optString("assignmentId", ""),
                tripId = data.optString("tripId", ""),
                orderId = data.optString("orderId", ""),
                truckRequestId = data.optString("truckRequestId", ""),
                pickup = pickup,
                drop = drop,
                vehicleNumber = data.optString("vehicleNumber", ""),
                farePerTruck = data.optDouble("farePerTruck", 0.0),
                distanceKm = data.optDouble("distanceKm", 0.0),
                customerName = data.optString("customerName", ""),
                customerPhone = data.optString("customerPhone", ""),
                assignedAt = data.optString("assignedAt", ""),
                message = data.optString("message", "New trip assigned!")
            )
            
            // Emit to flow — UI collects this and navigates to TripAcceptDeclineScreen
            serviceScope.launch {
                _tripAssigned.emit(notification)
            }
            
            timber.log.Timber.i("✅ Trip assignment emitted to flow: ${notification.assignmentId}")
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing trip_assigned: ${e.message}")
        }
    }
    
    /**
     * Handle driver_timeout event from backend
     * 
     * Called when a driver doesn't respond to a trip assignment within the
     * timeout period (e.g., 60 seconds). Backend cancels the assignment
     * and notifies the transporter to reassign.
     * 
     * For TRANSPORTER view: Shows "Driver X didn't respond — [Reassign]" banner
     * For DRIVER view: Shows "Trip expired — you didn't respond in time"
     * 
     * Payload: { assignmentId, tripId, driverId, driverName, reason, message }
     */
    private fun handleDriverTimeout(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            
            timber.log.Timber.w("╔══════════════════════════════════════════════════════════════╗")
            timber.log.Timber.w("║  ⏰ DRIVER TIMEOUT - Assignment expired                      ║")
            timber.log.Timber.w("╠══════════════════════════════════════════════════════════════╣")
            timber.log.Timber.w("║  Assignment ID: ${data.optString("assignmentId", "")}")
            timber.log.Timber.w("║  Driver: ${data.optString("driverName", "")}")
            timber.log.Timber.w("║  Reason: ${data.optString("reason", "timeout")}")
            timber.log.Timber.w("╚══════════════════════════════════════════════════════════════╝")
            
            val notification = DriverTimeoutNotification(
                assignmentId = data.optString("assignmentId", ""),
                tripId = data.optString("tripId", ""),
                driverId = data.optString("driverId", ""),
                driverName = data.optString("driverName", ""),
                vehicleNumber = data.optString("vehicleNumber", ""),
                reason = data.optString("reason", "timeout"),
                message = data.optString("message", "Driver didn't respond in time")
            )
            
            serviceScope.launch {
                _driverTimeout.emit(notification)
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing driver_timeout: ${e.message}")
        }
    }
    
    /**
     * Handle trip_cancelled event from backend.
     *
     * Driver-specific cancel/expiry contract for in-flight or pending trip decisions.
     */
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

            serviceScope.launch {
                _tripCancelled.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing trip_cancelled: ${e.message}")
        }
    }
    
    /**
     * Handle new broadcast event
     * Shows full-screen overlay via BroadcastOverlayManager
     * 
     * CRITICAL: This is the entry point for ALL broadcast notifications!
     * When customer creates a booking, this gets called via WebSocket.
     * Must trigger BroadcastOverlayManager.showBroadcast() for overlay to appear.
     */
    private fun handleNewBroadcast(args: Array<Any>, rawEventName: String) {
        val receivedAtMs = System.currentTimeMillis()
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_WS_RECEIVED,
            status = BroadcastStatus.SUCCESS,
            attrs = mapOf(
                "event" to rawEventName,
                "eventName" to rawEventName,
                "ingressSource" to "socket"
            )
        )

        val userRole = RetrofitClient.getUserRole()?.lowercase()
        if (!BroadcastRolePolicy.canHandleBroadcastIngress(userRole)) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_GATED,
                status = BroadcastStatus.SKIPPED,
                reason = "role_not_transporter",
                attrs = mapOf(
                    "event" to rawEventName,
                    "eventName" to rawEventName,
                    "ingressSource" to "socket",
                    "role" to (userRole ?: "unknown")
                )
            )
            timber.log.Timber.w(
                "⏭️ Ignoring new broadcast event for non-transporter role=%s",
                userRole ?: "unknown"
            )
            return
        }
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_GATED,
            status = BroadcastStatus.SUCCESS,
            reason = "role_transporter",
            attrs = mapOf(
                "event" to rawEventName,
                "eventName" to rawEventName,
                "ingressSource" to "socket",
                "role" to (userRole ?: "unknown")
            )
        )

        val envelope = parseIncomingBroadcastEnvelope(
            rawEventName = rawEventName,
            args = args,
            receivedAtMs = receivedAtMs
        )
        val notification = envelope.broadcast ?: return
        val broadcastTrip = notification.toBroadcastTrip()

        // ACK delivery back to server (Uber RAMEN-style at-least-once guarantee)
        // Server tracks pending ACKs in Redis SET → removes on ACK → retries unACKed via FCM
        val ackOrderId = broadcastTrip.broadcastId.ifBlank {
            broadcastTrip.orderId
        }
        if (ackOrderId.isNotBlank()) {
            socket?.emit("broadcast_ack", org.json.JSONObject().apply {
                put("orderId", ackOrderId)
                put("receivedAt", receivedAtMs)
                put("source", "socket")
            })
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
            scheduleBroadcastOverlayWatchdog(
                broadcastId = broadcastTrip.broadcastId,
                rawEventName = rawEventName,
                ingressMode = "coordinator"
            )
            emitHot(_newBroadcasts, notification)
            return
        }

        serviceScope.launch {
            val ingestResult = BroadcastOverlayManager.showBroadcast(broadcastTrip)
            when (ingestResult.action) {
                BroadcastOverlayManager.BroadcastIngressAction.SHOWN -> {
                    timber.log.Timber.i("✅ OVERLAY SHOWN for broadcast: ${broadcastTrip.broadcastId}")
                    BroadcastTelemetry.recordLatency(
                        name = "overlay_open_latency_ms",
                        ms = System.currentTimeMillis() - receivedAtMs,
                        attrs = mapOf(
                            "broadcastId" to broadcastTrip.broadcastId,
                            "event" to rawEventName
                        )
                    )
                    scheduleBroadcastOverlayWatchdog(
                        broadcastId = broadcastTrip.broadcastId,
                        rawEventName = rawEventName,
                        ingressMode = "legacy_overlay_manager"
                    )
                }

                BroadcastOverlayManager.BroadcastIngressAction.BUFFERED -> {
                    timber.log.Timber.i("📥 Broadcast buffered: ${broadcastTrip.broadcastId} (${ingestResult.reason})")
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.BROADCAST_GATED,
                        status = BroadcastStatus.BUFFERED,
                        reason = ingestResult.reason ?: "overlay_buffered",
                        attrs = mapOf(
                            "broadcastId" to broadcastTrip.broadcastId,
                            "event" to rawEventName
                        )
                    )
                }

                BroadcastOverlayManager.BroadcastIngressAction.DROPPED -> {
                    timber.log.Timber.w(
                        "⚠️ Broadcast dropped: %s reason=%s queue=%s",
                        broadcastTrip.broadcastId,
                        ingestResult.reason,
                        BroadcastOverlayManager.getQueueInfo()
                    )
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.BROADCAST_GATED,
                        status = BroadcastStatus.DROPPED,
                        reason = ingestResult.reason ?: "overlay_dropped",
                        attrs = mapOf(
                            "broadcastId" to broadcastTrip.broadcastId,
                            "event" to rawEventName
                        )
                    )
                }
            }
        }

        emitHot(_newBroadcasts, notification)
    }

    private fun maybeRouteIncomingBroadcastFallback(
        rawIncomingEvent: String,
        payloadArgs: Array<Any>
    ) {
        val normalizedEvent = rawIncomingEvent.trim().lowercase(Locale.US)
        val payload = payloadArgs.firstOrNull() as? JSONObject
        val payloadType = payload?.optString("type", "")?.trim()?.lowercase(Locale.US).orEmpty()
        val legacyType = payload?.optString("legacyType", "")?.trim()?.lowercase(Locale.US).orEmpty()

        val resolution = resolveBroadcastFallbackDecision(
            rawIncomingEvent = rawIncomingEvent,
            payloadType = payloadType,
            legacyType = legacyType,
            directBroadcastSocketEvents = directBroadcastSocketEvents,
            directCancellationSocketEvents = directCancellationSocketEvents,
            fallbackBroadcastSocketEvents = fallbackBroadcastSocketEvents,
            broadcastPayloadTypes = broadcastPayloadTypes,
            cancellationPayloadTypes = cancellationPayloadTypes
        )
        val effectiveEvent = resolution.effectiveEvent ?: return

        when (resolution.route) {
            BroadcastFallbackRoute.NONE -> return
            BroadcastFallbackRoute.CANCELLATION -> {
                if (!BroadcastFeatureFlagsRegistry.current().captainCanonicalCancelAliasesEnabled) return

                timber.log.Timber.w(
                    "↪️ Fallback routing incoming cancellation event: incoming=%s effective=%s payloadType=%s legacyType=%s",
                    normalizedEvent,
                    effectiveEvent,
                    payloadType.ifBlank { "none" },
                    legacyType.ifBlank { "none" }
                )
                val normalizedId = payload?.let { resolveEventId(it, "orderId", "broadcastId", "bookingId", "id") }.orEmpty()
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_GATED,
                    status = BroadcastStatus.SUCCESS,
                    reason = "fallback_cancellation_alias",
                    attrs = mapOf(
                        "ingressSource" to "socket_fallback",
                        "eventName" to effectiveEvent,
                        "normalizedId" to normalizedId.ifBlank { "none" }
                    )
                )
                when (effectiveEvent) {
                    Events.ORDER_CANCELLED.lowercase(Locale.US),
                    Events.BOOKING_CANCELLED.lowercase(Locale.US) -> handleOrderCancelled(payloadArgs)
                    Events.ORDER_EXPIRED.lowercase(Locale.US) -> handleOrderExpired(payloadArgs)
                    Events.BROADCAST_DISMISSED.lowercase(Locale.US),
                    Events.BOOKING_EXPIRED.lowercase(Locale.US),
                    "broadcast_expired" -> handleBookingExpired(payloadArgs)
                }
            }
            BroadcastFallbackRoute.BROADCAST -> {
                timber.log.Timber.w(
                    "↪️ Fallback routing incoming broadcast event: incoming=%s effective=%s payloadType=%s legacyType=%s",
                    normalizedEvent,
                    effectiveEvent,
                    payloadType.ifBlank { "none" },
                    legacyType.ifBlank { "none" }
                )
                handleNewBroadcast(payloadArgs, effectiveEvent)
            }
        }
    }

    private fun scheduleBroadcastOverlayWatchdog(
        broadcastId: String,
        rawEventName: String,
        ingressMode: String
    ) {
        if (broadcastId.isBlank()) return
        if (!BroadcastFeatureFlagsRegistry.current().broadcastOverlayWatchdogEnabled) return

        serviceScope.launch {
            delay(BROADCAST_OVERLAY_WATCHDOG_MS)
            val overlayVisible = BroadcastOverlayManager.isOverlayVisible.value
            val currentOverlayId = BroadcastOverlayManager.currentBroadcast.value?.broadcastId.orEmpty()
            if (overlayVisible || currentOverlayId == broadcastId) return@launch

            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_OVERLAY_SHOWN,
                status = BroadcastStatus.FAILED,
                reason = "watchdog_overlay_not_visible",
                attrs = mapOf(
                    "broadcastId" to broadcastId,
                    "event" to rawEventName,
                    "ingressMode" to ingressMode,
                    "queueInfo" to BroadcastOverlayManager.getQueueInfo()
                )
            )
            timber.log.Timber.w(
                "⚠️ Overlay watchdog: broadcast=%s not visible after %dms mode=%s queue=%s",
                broadcastId,
                BROADCAST_OVERLAY_WATCHDOG_MS,
                ingressMode,
                BroadcastOverlayManager.getQueueInfo()
            )
            if (BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                BroadcastFlowCoordinator.requestReconcile(force = true)
            }
        }
    }

    private fun parseIncomingBroadcastEnvelope(
        rawEventName: String,
        args: Array<Any>,
        receivedAtMs: Long
    ): IncomingBroadcastEnvelope {
        val normalizedPayload = BroadcastPayloadNormalizer.normalizeFromArgs(args)
        val parseWarnings = normalizedPayload.warnings.map { warning ->
            warning.name.lowercase()
        }.toMutableList()
        val data = normalizedPayload.payload
        if (data == null) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_PARSED,
                status = BroadcastStatus.FAILED,
                reason = "payload_invalid",
                attrs = mapOf("event" to rawEventName)
            )
            return IncomingBroadcastEnvelope(
                rawEventName = rawEventName,
                normalizedId = "",
                receivedAtMs = receivedAtMs,
                payloadVersion = null,
                parseWarnings = listOf("payload_not_json"),
                broadcast = null
            )
        }

        return try {
            val normalizedId = normalizedPayload.normalizedId

            if (normalizedId.isEmpty()) {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_PARSED,
                    status = BroadcastStatus.DROPPED,
                    reason = "missing_id",
                    attrs = mapOf("event" to rawEventName)
                )
                return IncomingBroadcastEnvelope(
                    rawEventName = rawEventName,
                    normalizedId = "",
                    receivedAtMs = receivedAtMs,
                    payloadVersion = resolvePayloadVersion(data, ""),
                    parseWarnings = listOf("missing_id"),
                    broadcast = null
                )
            }

            val requestedVehiclesList = mutableListOf<RequestedVehicleNotification>()
            val vehiclesArray = data.optJSONArray("requestedVehicles")
            if (vehiclesArray != null) {
                for (i in 0 until vehiclesArray.length()) {
                    val vehicle = vehiclesArray.optJSONObject(i) ?: continue
                    requestedVehiclesList.add(
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
            }

            val trucksYouCanProvide = data.optInt("trucksYouCanProvide", 0)
            val maxTrucksYouCanProvide = data.optInt("maxTrucksYouCanProvide", trucksYouCanProvide)
            val yourAvailableTrucks = data.optInt("yourAvailableTrucks", 0)
            val yourTotalTrucks = data.optInt("yourTotalTrucks", 0)
            val trucksStillNeeded = data.optInt("trucksStillNeeded", 0)
            val isPersonalized = data.optBoolean("isPersonalized", false)

            val notification = BroadcastNotification(
                broadcastId = normalizedId,
                customerId = data.optString("customerId", ""),
                customerName = data.optString("customerName", "Customer"),
                vehicleType = data.optString("vehicleType", ""),
                vehicleSubtype = data.optString("vehicleSubtype", ""),
                trucksNeeded = data.optInt("trucksNeeded", data.optInt("totalTrucksNeeded", 1)),
                trucksFilled = data.optInt("trucksFilled", data.optInt("trucksFilledSoFar", 0)),
                farePerTruck = data.optInt("farePerTruck", data.optInt("pricePerTruck", 0)),
                pickupAddress = data.optJSONObject("pickupLocation")?.optString("address", "")
                    ?: data.optString("pickupAddress", ""),
                pickupCity = data.optJSONObject("pickupLocation")?.optString("city", "")
                    ?: data.optString("pickupCity", ""),
                pickupLatitude = data.optJSONObject("pickupLocation")?.let {
                    it.optDouble("latitude", Double.NaN).takeIf { v -> !v.isNaN() }
                        ?: it.optDouble("lat", Double.NaN).takeIf { v -> !v.isNaN() }
                } ?: data.optJSONObject("pickup")?.let {
                    it.optDouble("latitude", Double.NaN).takeIf { v -> !v.isNaN() }
                        ?: it.optDouble("lat", Double.NaN).takeIf { v -> !v.isNaN() }
                } ?: 0.0,
                pickupLongitude = data.optJSONObject("pickupLocation")?.let {
                    it.optDouble("longitude", Double.NaN).takeIf { v -> !v.isNaN() }
                        ?: it.optDouble("lng", Double.NaN).takeIf { v -> !v.isNaN() }
                } ?: data.optJSONObject("pickup")?.let {
                    it.optDouble("longitude", Double.NaN).takeIf { v -> !v.isNaN() }
                        ?: it.optDouble("lng", Double.NaN).takeIf { v -> !v.isNaN() }
                } ?: 0.0,
                dropAddress = data.optJSONObject("dropLocation")?.optString("address", "")
                    ?: data.optString("dropAddress", ""),
                dropCity = data.optJSONObject("dropLocation")?.optString("city", "")
                    ?: data.optString("dropCity", ""),
                dropLatitude = data.optJSONObject("dropLocation")?.let {
                    it.optDouble("latitude", Double.NaN).takeIf { v -> !v.isNaN() }
                        ?: it.optDouble("lat", Double.NaN).takeIf { v -> !v.isNaN() }
                } ?: data.optJSONObject("drop")?.let {
                    it.optDouble("latitude", Double.NaN).takeIf { v -> !v.isNaN() }
                        ?: it.optDouble("lat", Double.NaN).takeIf { v -> !v.isNaN() }
                } ?: 0.0,
                dropLongitude = data.optJSONObject("dropLocation")?.let {
                    it.optDouble("longitude", Double.NaN).takeIf { v -> !v.isNaN() }
                        ?: it.optDouble("lng", Double.NaN).takeIf { v -> !v.isNaN() }
                } ?: data.optJSONObject("drop")?.let {
                    it.optDouble("longitude", Double.NaN).takeIf { v -> !v.isNaN() }
                        ?: it.optDouble("lng", Double.NaN).takeIf { v -> !v.isNaN() }
                } ?: 0.0,
                distanceKm = data.optInt("distance", data.optInt("distanceKm", 0)),
                goodsType = data.optString("goodsType", "General"),
                isUrgent = data.optBoolean("isUrgent", false),
                expiresAt = data.optString("expiresAt", ""),
                requestedVehicles = requestedVehiclesList,
                trucksYouCanProvide = trucksYouCanProvide,
                maxTrucksYouCanProvide = maxTrucksYouCanProvide,
                yourAvailableTrucks = yourAvailableTrucks,
                yourTotalTrucks = yourTotalTrucks,
                trucksStillNeeded = trucksStillNeeded,
                isPersonalized = isPersonalized,
                eventId = resolveOptionalString(data, "eventId"),
                eventVersion = resolveOptionalInt(data, "eventVersion"),
                serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
            )

            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_PARSED,
                status = BroadcastStatus.SUCCESS,
                reason = if (parseWarnings.isEmpty()) null else "parse_warnings",
                attrs = mutableMapOf(
                    "event" to rawEventName,
                    "normalizedId" to normalizedId
                ).apply {
                    if (parseWarnings.isNotEmpty()) {
                        this["warnings"] = parseWarnings.joinToString("|")
                    }
                }
            )

            IncomingBroadcastEnvelope(
                rawEventName = rawEventName,
                normalizedId = normalizedId,
                receivedAtMs = receivedAtMs,
                payloadVersion = resolvePayloadVersion(data, normalizedId),
                parseWarnings = parseWarnings,
                broadcast = notification
            )
        } catch (e: Exception) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.BROADCAST_PARSED,
                status = BroadcastStatus.FAILED,
                reason = "payload_invalid",
                attrs = mapOf(
                    "event" to rawEventName,
                    "error" to (e.message ?: "unknown")
                )
            )
            timber.log.Timber.e(e, "Error parsing broadcast payload: ${e.message}")
            IncomingBroadcastEnvelope(
                rawEventName = rawEventName,
                normalizedId = "",
                receivedAtMs = receivedAtMs,
                payloadVersion = null,
                parseWarnings = listOf("exception"),
                broadcast = null
            )
        }
    }

    private fun resolveEventId(data: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = data.optString(key, "").trim()
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    private fun resolveOptionalString(data: JSONObject, key: String): String? {
        if (!data.has(key)) return null
        val value = data.optString(key, "").trim()
        return value.takeIf { it.isNotEmpty() }
    }

    private fun resolveOptionalInt(data: JSONObject, key: String): Int? {
        if (!data.has(key)) return null
        return when (val raw = data.opt(key)) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    private fun resolveOptionalLong(data: JSONObject, key: String): Long? {
        if (!data.has(key)) return null
        return when (val raw = data.opt(key)) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
    }

    private fun resolvePayloadVersion(data: JSONObject, normalizedId: String): String? {
        val eventId = resolveOptionalString(data, "eventId")
        if (!eventId.isNullOrBlank()) return "event:$eventId"

        val eventVersion = resolveOptionalInt(data, "eventVersion")
        val serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
        if (eventVersion != null && serverTimeMs != null && serverTimeMs > 0L) {
            return "v$eventVersion@$serverTimeMs|$normalizedId"
        }

        return data.optString("payloadVersion").takeIf { it.isNotBlank() }
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
            timber.log.Timber.i("🧹 Removed broadcast %s after dismiss window", broadcastId)
        }
    }
    
    /**
     * Handle truck assigned event
     */
    private fun handleTruckAssigned(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("🚛 Truck assigned: $data")
            
            val assignment = data.optJSONObject("assignment")
            val notification = TruckAssignedNotification(
                bookingId = data.optString("bookingId", ""),
                assignmentId = assignment?.optString("id", "") ?: "",
                vehicleNumber = assignment?.optString("vehicleNumber", "") ?: "",
                driverName = assignment?.optString("driverName", "") ?: "",
                status = assignment?.optString("status", "") ?: ""
            )
            
            serviceScope.launch {
                _truckAssigned.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing truck assigned: ${e.message}")
        }
    }
    
    /**
     * Handle assignment status changed
     */
    private fun handleAssignmentStatusChanged(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("📋 Assignment status changed: $data")
            
            val notification = AssignmentStatusNotification(
                assignmentId = data.optString("assignmentId", ""),
                tripId = data.optString("tripId", ""),
                status = data.optString("status", ""),
                vehicleNumber = data.optString("vehicleNumber", ""),
                message = data.optString("message", "")
            )
            
            serviceScope.launch {
                _assignmentStatusChanged.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing assignment status: ${e.message}")
        }
    }
    
    /**
     * Handle booking updated
     */
    private fun handleBookingUpdated(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("📝 Booking updated: $data")
            
            val notification = BookingUpdatedNotification(
                bookingId = data.optString("bookingId", ""),
                status = data.optString("status", ""),
                trucksFilled = data.optInt("trucksFilled", -1),
                trucksNeeded = data.optInt("trucksNeeded", -1)
            )
            
            serviceScope.launch {
                _bookingUpdated.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing booking update: ${e.message}")
        }
    }
    
    /**
     * Handle trucks remaining update
     */
    private fun handleTrucksRemainingUpdate(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("📊 Trucks remaining update: $data")
            
            val notification = TrucksRemainingNotification(
                orderId = resolveEventId(data, "orderId", "broadcastId", "bookingId", "id"),
                vehicleType = data.optString("vehicleType", ""),
                totalTrucks = data.optInt("totalTrucks", 0),
                trucksFilled = data.optInt("trucksFilled", 0),
                trucksRemaining = data.optInt("trucksRemaining", 0),
                orderStatus = data.optString("orderStatus", ""),
                eventId = resolveOptionalString(data, "eventId"),
                eventVersion = resolveOptionalInt(data, "eventVersion"),
                serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
            )
            
            serviceScope.launch {
                _trucksRemainingUpdates.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing trucks remaining: ${e.message}")
        }
    }
    
    /**
     * Handle accept confirmation
     */
    private fun handleAcceptConfirmation(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("✅ Accept confirmation: $data")
            
            val notification = AssignmentStatusNotification(
                assignmentId = data.optString("requestId", ""),
                tripId = data.optString("tripId", ""),
                status = if (data.optBoolean("success", false)) "accepted" else "failed",
                vehicleNumber = data.optString("vehicleNumber", ""),
                message = data.optString("message", "")
            )
            
            serviceScope.launch {
                _assignmentStatusChanged.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing accept confirmation: ${e.message}")
        }
    }
    
    /**
     * Handle booking/broadcast expired
     * 
     * ENHANCED: Instead of instantly removing, emits a "dismissed" notification
     * so the UI can show a blur+message overlay for 2 seconds before removal.
     * 
     * Event: "broadcast_expired" or "booking_expired"
     * Payload: { broadcastId, orderId, reason, message, customerName }
     */
    private fun handleBookingExpired(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            
            // Normalize ID across backend variants.
            val broadcastId = resolveEventId(data, "broadcastId", "orderId", "bookingId", "id")
            val reason = data.optString("reason", "timeout")
            val message = data.optString("message", when (reason) {
                "customer_cancelled" -> "Sorry, this order was cancelled by the customer"
                "fully_filled" -> "All trucks have been assigned for this booking"
                else -> "This booking request has expired"
            })
            val customerName = data.optString("customerName", "")
            
            timber.log.Timber.w("╔══════════════════════════════════════════════════════════════╗")
            timber.log.Timber.w("║  ⏰ BROADCAST DISMISSED — GRACEFUL FADE                      ║")
            timber.log.Timber.w("╠══════════════════════════════════════════════════════════════╣")
            timber.log.Timber.w("║  Broadcast ID: $broadcastId")
            timber.log.Timber.w("║  Reason: $reason")
            timber.log.Timber.w("║  Message: $message")
            timber.log.Timber.w("╚══════════════════════════════════════════════════════════════╝")
            
            if (broadcastId.isNotEmpty()) {
                // Emit dismiss notification for graceful fade (instead of instant remove)
                emitHot(
                    _broadcastDismissed,
                    BroadcastDismissedNotification(
                        broadcastId = broadcastId,
                        reason = reason,
                        message = message,
                        customerName = customerName,
                        eventId = resolveOptionalString(data, "eventId"),
                        eventVersion = resolveOptionalInt(data, "eventVersion"),
                        serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
                    )
                )

                if (!BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                    scheduleOverlayRemovalAfterDismiss(broadcastId)
                }
            }
            
            // Also emit to update any list views (backward compat)
            val notification = BookingUpdatedNotification(
                bookingId = broadcastId,
                status = if (reason == "customer_cancelled") "cancelled" else "expired",
                trucksFilled = data.optInt("trucksFilled", -1),
                trucksNeeded = data.optInt("trucksNeeded", -1)
            )
            
            emitHot(_bookingUpdated, notification)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing booking expired: ${e.message}")
        }
    }
    
    /**
     * Handle booking fully filled
     */
    private fun handleBookingFullyFilled(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("✅ Booking fully filled: $data")
            
            val notification = BookingUpdatedNotification(
                bookingId = data.optString("bookingId", data.optString("orderId", "")),
                status = "fully_filled",
                trucksFilled = data.optInt("trucksFilled", -1),
                trucksNeeded = data.optInt("trucksNeeded", -1)
            )
            
            serviceScope.launch {
                _bookingUpdated.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing booking fully filled: ${e.message}")
        }
    }
    
    /**
     * Handle order cancelled by customer
     * 
     * ENHANCED: Instead of instant removal, emits dismiss notification for
     * graceful blur+message UX. Delayed removal happens after 2s.
     * 
     * Uses broadcastDismissed flow for UI blur + BroadcastOverlayManager scheduled removal
     */
    /**
     * Handle order_cancelled event from backend.
     *
     * THREE surfaces must be cleaned up simultaneously:
     * 1. Full-screen overlay (BroadcastOverlayManager) — instant removal via removeBroadcast()
     * 2. BroadcastListScreen card — animated blur overlay via _broadcastDismissed
     * 3. Driver/Transporter dashboards — snackbar via _orderCancelled
     *
     * SCALABILITY: All operations are O(1). removeBroadcast() uses mutex-protected
     * ConcurrentLinkedQueue. Flows use SharedFlow with extraBufferCapacity=20 — never blocks.
     */
    private fun handleOrderCancelled(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val orderId = resolveEventId(data, "orderId", "broadcastId", "bookingId", "id")
            val tripId = data.optString("tripId", "")
            val reason = data.optString("reason", "Cancelled by customer")
            val cancelledAt = data.optString("cancelledAt", "")
            val message = data.optString("message", "Sorry, this order was cancelled by the customer")
            val assignmentsCancelled = data.optInt("assignmentsCancelled", 0)
            
            timber.log.Timber.w("╔══════════════════════════════════════════════════════════════╗")
            timber.log.Timber.w("║  🚫 ORDER CANCELLED BY CUSTOMER — GRACEFUL DISMISS           ║")
            timber.log.Timber.w("╠══════════════════════════════════════════════════════════════╣")
            timber.log.Timber.w("║  Order ID: $orderId")
            timber.log.Timber.w("║  Reason: $reason")
            timber.log.Timber.w("║  Assignments Released: $assignmentsCancelled")
            timber.log.Timber.w("╚══════════════════════════════════════════════════════════════╝")
            
            // 1. Emit dismiss notification for graceful fade on BroadcastListScreen/overlay card.
            //    BroadcastListScreen observes this → shows animated "Sorry" overlay → auto-removes after 1s.
            emitHot(
                _broadcastDismissed,
                BroadcastDismissedNotification(
                    broadcastId = orderId,
                    reason = "customer_cancelled",
                    message = message,
                    customerName = data.optString("customerName", ""),
                    eventId = resolveOptionalString(data, "eventId"),
                    eventVersion = resolveOptionalInt(data, "eventVersion"),
                    serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
                )
            )

            // 2. Remove from overlay queue after shared dismiss window (legacy path only).
            if (!BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                scheduleOverlayRemovalAfterDismiss(orderId)
            }
            
            // 3. Emit to dedicated orderCancelled flow (for driver/transporter screens)
            val cancelNotification = OrderCancelledNotification(
                orderId = orderId,
                tripId = tripId,
                reason = reason,
                message = message,
                cancelledAt = cancelledAt,
                assignmentsCancelled = assignmentsCancelled,
                eventId = resolveOptionalString(data, "eventId"),
                eventVersion = resolveOptionalInt(data, "eventVersion"),
                serverTimeMs = resolveOptionalLong(data, "serverTimeMs"),
                customerName = data.optString("customerName", ""),
                customerPhone = data.optString("customerPhone", ""),
                pickupAddress = data.optString("pickupAddress", ""),
                dropAddress = data.optString("dropAddress", "")
            )
            
            emitHot(_orderCancelled, cancelNotification)
            
            // 4. Also emit to bookingUpdated for backward compatibility (list views)
            val bookingNotification = BookingUpdatedNotification(
                bookingId = orderId,
                status = "cancelled",
                trucksFilled = -1,
                trucksNeeded = -1
            )
            
            emitHot(_bookingUpdated, bookingNotification)
            
            timber.log.Timber.i("   ✓ Emitted dismiss + orderCancelled + bookingUpdated flows")
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error handling order cancelled: ${e.message}")
        }
    }
    
    /**
     * Handle order expired (timeout)
     * 
     * ENHANCED: Graceful dismiss instead of instant removal
     * Shows "Order expired" blur overlay for 2s before removing
     */
    private fun handleOrderExpired(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val orderId = resolveEventId(data, "orderId", "broadcastId", "bookingId", "id")
            
            timber.log.Timber.w("⏰ ORDER EXPIRED — GRACEFUL DISMISS: $orderId")
            
            // Emit dismiss notification for graceful fade
            emitHot(
                _broadcastDismissed,
                BroadcastDismissedNotification(
                    broadcastId = orderId,
                    reason = "timeout",
                    message = "This booking request has expired",
                    customerName = data.optString("customerName", ""),
                    eventId = resolveOptionalString(data, "eventId"),
                    eventVersion = resolveOptionalInt(data, "eventVersion"),
                    serverTimeMs = resolveOptionalLong(data, "serverTimeMs")
                )
            )

            if (!BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                scheduleOverlayRemovalAfterDismiss(orderId)
            }
            
            // Emit update for list views
            val notification = BookingUpdatedNotification(
                bookingId = orderId,
                status = "expired",
                trucksFilled = data.optInt("trucksFilled", -1),
                trucksNeeded = data.optInt("totalTrucks", -1)
            )
            
            emitHot(_bookingUpdated, notification)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error handling order expired: ${e.message}")
        }
    }
    
    // ==========================================================================
    // CLIENT -> SERVER EVENTS
    // ==========================================================================
    
    /**
     * Join a booking room to receive updates
     */
    fun joinBookingRoom(bookingId: String) {
        if (_connectionState.value !is SocketConnectionState.Connected) {
            timber.log.Timber.w("Cannot join room: Not connected")
            return
        }
        socket?.emit(Events.JOIN_BOOKING, bookingId)
        timber.log.Timber.d("Joined booking room: $bookingId")
    }
    
    /**
     * Leave a booking room
     */
    fun leaveBookingRoom(bookingId: String) {
        socket?.emit(Events.LEAVE_BOOKING, bookingId)
        timber.log.Timber.d("Left booking room: $bookingId")
    }
    
    /**
     * Join an order room for multi-truck updates
     */
    fun joinOrderRoom(orderId: String) {
        if (_connectionState.value !is SocketConnectionState.Connected) {
            timber.log.Timber.w("Cannot join order room: Not connected")
            return
        }
        socket?.emit(Events.JOIN_ORDER, orderId)
        timber.log.Timber.d("Joined order room: $orderId")
    }
    
    /**
     * Leave an order room
     */
    fun leaveOrderRoom(orderId: String) {
        socket?.emit(Events.LEAVE_ORDER, orderId)
        timber.log.Timber.d("Left order room: $orderId")
    }
    
    /**
     * Update driver location (for drivers)
     */
    fun updateLocation(tripId: String, latitude: Double, longitude: Double, speed: Float = 0f, bearing: Float = 0f) {
        if (_connectionState.value !is SocketConnectionState.Connected) {
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
        
        socket?.emit(Events.UPDATE_LOCATION, data)
    }
    
    /**
     * Disconnect from server
     * 
     * CRITICAL: Must reset _isOnlineLocally to false!
     * Without this, the next login would auto-start heartbeat on EVENT_CONNECT
     * because _isOnlineLocally is still true from the PREVIOUS session.
     * This would make the NEW user appear online before they press the toggle.
     * 
     * EDGE CASES:
     *   - Logout → disconnect → _isOnlineLocally = false → no ghost heartbeat ✅
     *   - Login again → EVENT_CONNECT → _isOnlineLocally is false → no auto-heartbeat ✅
     *   - App killed → SocketIOService is singleton → _isOnlineLocally persists →
     *     but loadDashboardData() will sync it from backend anyway ✅
     */
    fun disconnect() {
        timber.log.Timber.i("🔌 Disconnecting...")
        // Reset online intent FIRST — prevents EVENT_CONNECT from restarting
        // heartbeat on next login with stale state from previous session
        _isOnlineLocally = false
        // Stop heartbeat — socket?.off() removes all listeners,
        // so the disconnect handler (which also stops heartbeat) may never fire.
        stopHeartbeat()
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionState.value = SocketConnectionState.Disconnected
    }
    
    /**
     * Force reconnect
     */
    fun reconnect() {
        timber.log.Timber.i("🔄 Reconnecting...")
        disconnect()
        val url = serverUrl
        val token = authToken
        if (url != null && token != null) {
            connect(url, token)
        }
    }
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean = _connectionState.value is SocketConnectionState.Connected
}

// =============================================================================
// DATA CLASSES
// =============================================================================

sealed class SocketConnectionState {
    object Disconnected : SocketConnectionState()
    object Connecting : SocketConnectionState()
    object Connected : SocketConnectionState()
    data class Error(val message: String) : SocketConnectionState()
}

data class IncomingBroadcastEnvelope(
    val rawEventName: String,
    val normalizedId: String,
    val receivedAtMs: Long,
    val payloadVersion: String?,
    val parseWarnings: List<String>,
    val broadcast: BroadcastNotification?
)

private val ISO_EXPIRY_WITH_MILLIS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
private val ISO_EXPIRY_NO_MILLIS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

private fun parseBroadcastExpiryEpochMs(rawExpiry: String?): Long {
    val now = System.currentTimeMillis()
    val fallback = now + 60_000L
    val value = rawExpiry?.trim().orEmpty()
    if (value.isEmpty()) return fallback

    value.toLongOrNull()?.let { numeric ->
        return when {
            numeric > 1_000_000_000_000L -> numeric
            numeric > 1_000_000_000L -> numeric * 1_000L
            else -> fallback
        }.coerceAtLeast(now + 1_000L)
    }

    val instant = try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(value, ISO_EXPIRY_WITH_MILLIS).atOffset(ZoneOffset.UTC).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(value, ISO_EXPIRY_NO_MILLIS).atOffset(ZoneOffset.UTC).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    return (instant?.toEpochMilli() ?: fallback).coerceAtLeast(now + 1_000L)
}

data class BroadcastNotification(
    val broadcastId: String,
    val customerId: String,
    val customerName: String,
    val vehicleType: String,
    val vehicleSubtype: String,
    val trucksNeeded: Int,
    val trucksFilled: Int,
    val farePerTruck: Int,
    val pickupAddress: String,
    val pickupCity: String,
    // =========================================================================
    // CRITICAL FIX: Coordinates for Navigate button (was hardcoded 0.0)
    // Backend sends these in pickupLocation/dropLocation JSON objects
    // =========================================================================
    val pickupLatitude: Double = 0.0,
    val pickupLongitude: Double = 0.0,
    val dropAddress: String,
    val dropCity: String,
    val dropLatitude: Double = 0.0,
    val dropLongitude: Double = 0.0,
    val distanceKm: Int,
    val goodsType: String,
    val isUrgent: Boolean,
    val expiresAt: String,
    val requestedVehicles: List<RequestedVehicleNotification> = emptyList(),  // Multi-truck support
    
    // =========================================================================
    // PERSONALIZED FIELDS - Each transporter sees their own capacity
    // =========================================================================
    val trucksYouCanProvide: Int = 0,          // How many THIS transporter can accept
    val maxTrucksYouCanProvide: Int = 0,       // Alias
    val yourAvailableTrucks: Int = 0,          // How many they have available
    val yourTotalTrucks: Int = 0,              // How many they own total
    val trucksStillNeeded: Int = 0,            // How many order still needs
    val isPersonalized: Boolean = false,         // Is this a personalized broadcast?
    val eventId: String? = null,
    val eventVersion: Int? = null,
    val serverTimeMs: Long? = null
) {
    /**
     * Convert BroadcastNotification to BroadcastTrip for UI display
     * Used by BroadcastOverlayManager to show full-screen overlay
     */
    fun toBroadcastTrip(): com.weelo.logistics.data.model.BroadcastTrip {
        // Convert requestedVehicles to model format
        val modelRequestedVehicles = if (requestedVehicles.isNotEmpty()) {
            requestedVehicles.map { rv ->
                com.weelo.logistics.data.model.RequestedVehicle(
                    vehicleType = rv.vehicleType,
                    vehicleSubtype = rv.vehicleSubtype,
                    count = rv.count,
                    filledCount = rv.filledCount,
                    farePerTruck = rv.farePerTruck,
                    capacityTons = rv.capacityTons
                )
            }
        } else {
            // Fallback: Create single entry from legacy fields
            listOf(
                com.weelo.logistics.data.model.RequestedVehicle(
                    vehicleType = vehicleType,
                    vehicleSubtype = vehicleSubtype,
                    count = trucksNeeded,
                    filledCount = trucksFilled,
                    farePerTruck = farePerTruck.toDouble(),
                    capacityTons = 0.0
                )
            )
        }
        
        return com.weelo.logistics.data.model.BroadcastTrip(
            broadcastId = broadcastId,
            customerId = customerId,
            customerName = customerName,
            customerMobile = "", // Not available from WebSocket
            // CRITICAL FIX: Use actual coordinates from backend (was hardcoded 0.0)
            // These are parsed from pickupLocation/dropLocation JSON objects
            // Navigate button uses these for Google Maps directions
            pickupLocation = com.weelo.logistics.data.model.Location(
                address = pickupAddress.ifEmpty { pickupCity },
                city = pickupCity,
                latitude = pickupLatitude,
                longitude = pickupLongitude
            ),
            dropLocation = com.weelo.logistics.data.model.Location(
                address = dropAddress.ifEmpty { dropCity },
                city = dropCity,
                latitude = dropLatitude,
                longitude = dropLongitude
            ),
            distance = distanceKm.toDouble(),
            estimatedDuration = (distanceKm * 2).toLong(), // Rough estimate: 2 min per km
            totalTrucksNeeded = trucksNeeded,
            trucksFilledSoFar = trucksFilled,
            requestedVehicles = modelRequestedVehicles,  // Multi-truck support!
            vehicleType = com.weelo.logistics.data.model.TruckCategory(
                id = vehicleType.lowercase(),
                name = vehicleType.replaceFirstChar { it.uppercase() },
                icon = "🚛",
                description = vehicleSubtype.ifEmpty { "Standard" }
            ),
            goodsType = goodsType,
            farePerTruck = farePerTruck.toDouble(),
            totalFare = if (requestedVehicles.isNotEmpty()) {
                requestedVehicles.sumOf { it.farePerTruck * it.count }
            } else {
                (farePerTruck * trucksNeeded).toDouble()
            },
            status = com.weelo.logistics.data.model.BroadcastStatus.ACTIVE,
            broadcastTime = serverTimeMs?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            expiryTime = parseBroadcastExpiryEpochMs(expiresAt),
            isUrgent = isUrgent,
            
            // =========================================================================
            // PERSONALIZED FIELDS
            // =========================================================================
            trucksYouCanProvide = if (trucksYouCanProvide > 0) trucksYouCanProvide 
                else maxTrucksYouCanProvide.takeIf { it > 0 } ?: trucksNeeded,
            maxTrucksYouCanProvide = maxTrucksYouCanProvide.takeIf { it > 0 } 
                ?: trucksYouCanProvide.takeIf { it > 0 } ?: trucksNeeded,
            yourAvailableTrucks = yourAvailableTrucks,
            yourTotalTrucks = yourTotalTrucks,
            trucksStillNeeded = trucksStillNeeded.takeIf { it > 0 } ?: (trucksNeeded - trucksFilled),
            isPersonalized = isPersonalized,
            eventId = eventId,
            eventVersion = eventVersion,
            serverTimeMs = serverTimeMs
        )
    }
}

/**
 * Requested Vehicle from WebSocket broadcast - for multi-truck orders
 */
data class RequestedVehicleNotification(
    val vehicleType: String,
    val vehicleSubtype: String,
    val count: Int,
    val filledCount: Int,
    val farePerTruck: Double,
    val capacityTons: Double = 0.0
)

data class TruckAssignedNotification(
    val bookingId: String,
    val assignmentId: String,
    val vehicleNumber: String,
    val driverName: String,
    val status: String
)

data class AssignmentStatusNotification(
    val assignmentId: String,
    val tripId: String,
    val status: String,
    val vehicleNumber: String,
    val message: String = ""
)

data class BookingUpdatedNotification(
    val bookingId: String,
    val status: String,
    val trucksFilled: Int,
    val trucksNeeded: Int
)

data class TrucksRemainingNotification(
    val orderId: String,
    val vehicleType: String,
    val totalTrucks: Int,
    val trucksFilled: Int,
    val trucksRemaining: Int,
    val orderStatus: String,
    val eventId: String? = null,
    val eventVersion: Int? = null,
    val serverTimeMs: Long? = null
)

data class SocketError(val message: String)

// =============================================================================
// FLEET/VEHICLE NOTIFICATION DATA CLASSES
// =============================================================================

/**
 * Notification when a vehicle is registered
 */
data class VehicleRegisteredNotification(
    val vehicleId: String,
    val vehicleNumber: String,
    val vehicleType: String,
    val vehicleSubtype: String,
    val message: String,
    val totalVehicles: Int,
    val availableCount: Int
)

/**
 * Notification when fleet is updated (add, update, delete, status change)
 * Used to trigger UI refresh in FleetListScreen
 */
data class FleetUpdatedNotification(
    val action: String,           // "added", "updated", "deleted", "status_changed"
    val vehicleId: String,
    val totalVehicles: Int,
    val availableCount: Int,
    val inTransitCount: Int,
    val maintenanceCount: Int
)

/**
 * Driver Added Notification
 */
data class DriverAddedNotification(
    val driverId: String,
    val driverName: String,
    val driverPhone: String,
    val licenseNumber: String,
    val message: String,
    val totalDrivers: Int,
    val availableCount: Int,
    val onTripCount: Int
)

/**
 * Drivers Updated Notification
 */
/**
 * Driver online/offline status change notification (for transporter UI)
 */
data class DriverStatusChangedNotification(
    val driverId: String,
    val driverName: String,
    val isOnline: Boolean,
    val action: String,      // "online" or "offline"
    val timestamp: String
)

data class DriversUpdatedNotification(
    val action: String,              // "added", "updated", "deleted", "status_changed", "drivers_updated"
    val driverId: String,
    val totalDrivers: Int,
    val availableCount: Int,
    val onTripCount: Int
)

// =============================================================================
// TRIP ASSIGNMENT NOTIFICATION DATA CLASSES
// =============================================================================

/**
 * Location info for pickup/drop in trip assignment
 * 
 * Matches backend payload structure:
 *   pickup: { address, city, lat, lng }
 *   drop: { address, city, lat, lng }
 */
data class TripLocationInfo(
    val address: String,
    val city: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Trip Assigned Notification — sent to driver when transporter assigns them
 * 
 * Backend emits from: truck-hold.service.ts → confirmHoldWithAssignments()
 * Event name: "trip_assigned"
 * Target: Driver's personal room (user:{driverId})
 * 
 * FLOW:
 *   Backend emits → SocketIOService receives → _tripAssigned flow →
 *   UI collects → Navigate to TripAcceptDeclineScreen
 * 
 * This data class matches the backend's driverNotification object exactly.
 * Any field changes in backend must be reflected here.
 */
data class TripAssignedNotification(
    val assignmentId: String,        // Unique assignment ID (UUID)
    val tripId: String,              // Trip ID for tracking (UUID)
    val orderId: String,             // Original customer order ID
    val truckRequestId: String,      // Which truck request this fulfills
    val pickup: TripLocationInfo,    // Pickup location with lat/lng
    val drop: TripLocationInfo,      // Drop location with lat/lng
    val vehicleNumber: String,       // e.g., "KA-01-AB-1234"
    val farePerTruck: Double,        // Price in ₹ for this trip
    val distanceKm: Double,          // Distance in km
    val customerName: String,        // Customer name for display
    val customerPhone: String,       // Customer phone for calling
    val assignedAt: String,          // ISO timestamp of assignment
    val message: String              // Human-readable message
)

/**
 * Driver Timeout Notification — sent when driver doesn't respond in time
 * 
 * Backend emits when assignment timeout expires (e.g., 60 seconds).
 * 
 * For TRANSPORTER: Shows "Driver X didn't respond — [Reassign]" banner
 * For DRIVER: Shows "Trip expired — you didn't respond in time" 
 * 
 * Event name: "driver_timeout"
 * Target: Both driver and transporter personal rooms
 */
data class DriverTimeoutNotification(
    val assignmentId: String,        // Which assignment timed out
    val tripId: String,              // Associated trip ID
    val driverId: String,            // Driver who didn't respond
    val driverName: String,          // Driver name for display
    val vehicleNumber: String,       // Vehicle that was assigned
    val reason: String,              // "timeout" | "cancelled" | etc.
    val message: String              // Human-readable message
)

/**
 * Trip Cancelled Notification — driver-focused cancellation contract.
 *
 * Backend emits: "trip_cancelled"
 * Target: Assigned drivers
 */
data class TripCancelledNotification(
    val orderId: String,
    val tripId: String,
    val reason: String,
    val message: String,
    val cancelledAt: String,
    val customerName: String = "",
    val customerPhone: String = "",
    val pickupAddress: String = "",
    val dropAddress: String = "",
    val compensationAmount: Double = 0.0,
    val settlementState: String = "none"
)

// =============================================================================
// ORDER CANCELLATION NOTIFICATION
// =============================================================================

/**
 * Order Cancelled Notification — sent when customer cancels their order
 * 
 * Backend emits from: order.service.ts → cancelOrder()
 * Event name: "order_cancelled"
 * Target: All notified transporters + all assigned drivers
 * 
 * FLOW:
 *   Customer cancels → Backend releases assignments/vehicles →
 *   SocketIOService receives → _orderCancelled flow →
 *   UI collects → Remove from overlay, show snackbar, auto-dismiss trip screen
 * 
 * Used by:
 * - TransporterDashboardScreen: Remove from active list, show snackbar
 * - DriverDashboardScreen: Show snackbar "Customer cancelled: {reason}"
 * - TripAcceptDeclineScreen: Auto-dismiss if order cancelled while deciding
 * - DriverTripNavigationScreen: Show dialog → navigate to dashboard
 */
data class OrderCancelledNotification(
    val orderId: String,                  // Order that was cancelled
    val tripId: String = "",              // Optional trip identifier for strict matching
    val reason: String,                   // Why customer cancelled (from CancellationBottomSheet)
    val message: String,                  // Human-readable message
    val cancelledAt: String,              // ISO timestamp
    val assignmentsCancelled: Int = 0,    // How many assignments were released
    val eventId: String? = null,
    val eventVersion: Int? = null,
    val serverTimeMs: Long? = null,
    // CUSTOMER CONTACT — Driver/transporter can call even after cancel
    val customerName: String = "",        // Customer name for display
    val customerPhone: String = "",       // Customer phone for calling
    val pickupAddress: String = "",       // Pickup location (for context)
    val dropAddress: String = ""          // Drop location (for context)
)

// =============================================================================
// BROADCAST DISMISSED NOTIFICATION — Graceful fade+scroll UX
// =============================================================================

/**
 * Notification emitted when a broadcast should be dismissed gracefully.
 * Instead of instant removal, UI shows blur+message overlay for 2s,
 * then auto-scrolls to next card and removes.
 *
 * Reasons:
 * - "customer_cancelled" → "Sorry, this order was cancelled by {customerName}"
 * - "timeout" / "expired" → "This booking request has expired"
 * - "fully_filled" → "All trucks have been assigned for this booking"
 *
 * Used by:
 * - BroadcastListScreen: Blur card + scroll to next
 * - BroadcastOverlayScreen: Blur current card + advance to next
 */
data class BroadcastDismissedNotification(
    val broadcastId: String,         // Which broadcast to dismiss
    val reason: String,              // "customer_cancelled" | "timeout" | "fully_filled"
    val message: String,             // Human-readable message for overlay
    val customerName: String = "",   // Customer name (if cancelled)
    val eventId: String? = null,
    val eventVersion: Int? = null,
    val serverTimeMs: Long? = null
)
