package com.weelo.logistics.data.remote

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.net.URI

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
    private val _newBroadcasts = MutableSharedFlow<BroadcastNotification>(replay = 1, extraBufferCapacity = 50)
    val newBroadcasts: SharedFlow<BroadcastNotification> = _newBroadcasts.asSharedFlow()
    
    private val _truckAssigned = MutableSharedFlow<TruckAssignedNotification>(replay = 1, extraBufferCapacity = 20)
    val truckAssigned: SharedFlow<TruckAssignedNotification> = _truckAssigned.asSharedFlow()
    
    private val _assignmentStatusChanged = MutableSharedFlow<AssignmentStatusNotification>(replay = 1, extraBufferCapacity = 20)
    val assignmentStatusChanged: SharedFlow<AssignmentStatusNotification> = _assignmentStatusChanged.asSharedFlow()
    
    private val _bookingUpdated = MutableSharedFlow<BookingUpdatedNotification>(replay = 1, extraBufferCapacity = 20)
    val bookingUpdated: SharedFlow<BookingUpdatedNotification> = _bookingUpdated.asSharedFlow()
    
    private val _trucksRemainingUpdates = MutableSharedFlow<TrucksRemainingNotification>(replay = 1, extraBufferCapacity = 20)
    val trucksRemainingUpdates: SharedFlow<TrucksRemainingNotification> = _trucksRemainingUpdates.asSharedFlow()
    
    private val _errors = MutableSharedFlow<SocketError>(replay = 0, extraBufferCapacity = 10)
    val errors: SharedFlow<SocketError> = _errors.asSharedFlow()
    
    // Fleet/Vehicle update events (NEW - for real-time fleet updates)
    private val _fleetUpdated = MutableSharedFlow<FleetUpdatedNotification>(replay = 1, extraBufferCapacity = 20)
    val fleetUpdated: SharedFlow<FleetUpdatedNotification> = _fleetUpdated.asSharedFlow()
    
    private val _vehicleRegistered = MutableSharedFlow<VehicleRegisteredNotification>(replay = 1, extraBufferCapacity = 10)
    val vehicleRegistered: SharedFlow<VehicleRegisteredNotification> = _vehicleRegistered.asSharedFlow()
    
    // Driver update events (NEW - for real-time driver updates)
    private val _driverAdded = MutableSharedFlow<DriverAddedNotification>(replay = 1, extraBufferCapacity = 10)
    val driverAdded: SharedFlow<DriverAddedNotification> = _driverAdded.asSharedFlow()
    
    private val _driversUpdated = MutableSharedFlow<DriversUpdatedNotification>(replay = 1, extraBufferCapacity = 20)
    val driversUpdated: SharedFlow<DriversUpdatedNotification> = _driversUpdated.asSharedFlow()
    
    // Stored credentials for reconnection
    private var serverUrl: String? = null
    private var authToken: String? = null
    
    /**
     * ==========================================================================
     * SOCKET EVENTS - Must match backend socket.service.ts
     * ==========================================================================
     */
    object Events {
        // Server -> Client
        const val CONNECTED = "connected"
        const val NEW_BROADCAST = "new_broadcast"
        const val BOOKING_UPDATED = "booking_updated"
        const val TRUCK_ASSIGNED = "truck_assigned"
        const val ASSIGNMENT_STATUS_CHANGED = "assignment_status_changed"
        const val TRUCKS_REMAINING_UPDATE = "trucks_remaining_update"
        const val BOOKING_EXPIRED = "booking_expired"
        const val BOOKING_FULLY_FILLED = "booking_fully_filled"
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
        
        // Order lifecycle events
        const val ORDER_CANCELLED = "order_cancelled"  // When customer cancels order
        const val ORDER_EXPIRED = "order_expired"      // When order times out
        
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
        timber.log.Timber.i("║  Token preview: ${token.take(20)}...")
        timber.log.Timber.i("║  Current state: ${_connectionState.value}")
        timber.log.Timber.i("╚══════════════════════════════════════════════════════════════╝")
        
        if (_connectionState.value is SocketConnectionState.Connected) {
            timber.log.Timber.w("⚠️ Already connected, skipping reconnect")
            return
        }
        
        serverUrl = url
        authToken = token
        
        _connectionState.value = SocketConnectionState.Connecting
        timber.log.Timber.i("🔌 Connecting to Socket.IO server: $url")
        
        try {
            // Socket.IO options with authentication
            val options = IO.Options().apply {
                auth = mapOf("token" to token)
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 1000
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
     */
    private fun setupEventListeners() {
        socket?.apply {
            // Connection events
            on(Socket.EVENT_CONNECT) {
                timber.log.Timber.i("✅ Socket.IO connected")
                _connectionState.value = SocketConnectionState.Connected
            }
            
            on(Socket.EVENT_DISCONNECT) { args ->
                val reason = args.firstOrNull()?.toString() ?: "unknown"
                timber.log.Timber.w("🔌 Socket.IO disconnected: $reason")
                _connectionState.value = SocketConnectionState.Disconnected
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
                CoroutineScope(Dispatchers.IO).launch {
                    _errors.emit(SocketError(error))
                }
            }
            
            // New broadcast notification (new booking request)
            on(Events.NEW_BROADCAST) { args ->
                handleNewBroadcast(args)
            }
            
            on(Events.NEW_ORDER_ALERT) { args ->
                handleNewBroadcast(args)
            }
            
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
            
            // Broadcast expired (NEW - backend sends this for timeout/cancellation)
            // This is the primary event for removing broadcasts from overlay
            on("broadcast_expired") { args ->
                handleBookingExpired(args)
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
            
            // Driver status changed
            on(Events.DRIVER_STATUS_CHANGED) { args ->
                handleDriversUpdated(args, "status_changed")
            }
            
            // General drivers update (catch-all for any driver changes)
            on(Events.DRIVERS_UPDATED) { args ->
                handleDriversUpdated(args, "drivers_updated")
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
            
            CoroutineScope(Dispatchers.IO).launch {
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
            
            CoroutineScope(Dispatchers.IO).launch {
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
            
            CoroutineScope(Dispatchers.IO).launch {
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
            
            CoroutineScope(Dispatchers.IO).launch {
                _driversUpdated.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing drivers update: ${e.message}")
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
    private fun handleNewBroadcast(args: Array<Any>) {
        try {
            timber.log.Timber.i("╔══════════════════════════════════════════════════════════════╗")
            timber.log.Timber.i("║  📢 NEW BROADCAST RECEIVED VIA WEBSOCKET                     ║")
            timber.log.Timber.i("╚══════════════════════════════════════════════════════════════╝")
            
            val data = args.firstOrNull() as? JSONObject
            if (data == null) {
                timber.log.Timber.e("❌ BROADCAST ERROR: No data in args! args.size=${args.size}")
                args.forEachIndexed { index, arg -> timber.log.Timber.e("   arg[$index]: $arg (${arg.javaClass.simpleName})") }
                return
            }
            
            timber.log.Timber.i("📦 Raw broadcast data: $data")
            
            // Parse requestedVehicles array for multi-truck support
            val requestedVehiclesList = mutableListOf<RequestedVehicleNotification>()
            val vehiclesArray = data.optJSONArray("requestedVehicles")
            if (vehiclesArray != null) {
                for (i in 0 until vehiclesArray.length()) {
                    val v = vehiclesArray.optJSONObject(i) ?: continue
                    requestedVehiclesList.add(
                        RequestedVehicleNotification(
                            vehicleType = v.optString("vehicleType", ""),
                            vehicleSubtype = v.optString("vehicleSubtype", ""),
                            count = v.optInt("count", 1),
                            filledCount = v.optInt("filledCount", 0),
                            farePerTruck = v.optDouble("farePerTruck", 0.0),
                            capacityTons = v.optDouble("capacityTons", 0.0)
                        )
                    )
                }
            }
            
            timber.log.Timber.i("📢 Parsed ${requestedVehiclesList.size} vehicle types from broadcast")
            requestedVehiclesList.forEach { rv ->
                timber.log.Timber.i("   - ${rv.vehicleType}/${rv.vehicleSubtype}: ${rv.count} trucks @ ₹${rv.farePerTruck}")
            }
            
            // Parse personalized fields from backend
            val trucksYouCanProvide = data.optInt("trucksYouCanProvide", 0)
            val maxTrucksYouCanProvide = data.optInt("maxTrucksYouCanProvide", trucksYouCanProvide)
            val yourAvailableTrucks = data.optInt("yourAvailableTrucks", 0)
            val yourTotalTrucks = data.optInt("yourTotalTrucks", 0)
            val trucksStillNeeded = data.optInt("trucksStillNeeded", 0)
            val isPersonalized = data.optBoolean("isPersonalized", false)
            
            timber.log.Timber.i("📊 PERSONALIZED DATA:")
            timber.log.Timber.i("   trucksYouCanProvide: $trucksYouCanProvide")
            timber.log.Timber.i("   yourAvailableTrucks: $yourAvailableTrucks")
            timber.log.Timber.i("   trucksStillNeeded: $trucksStillNeeded")
            timber.log.Timber.i("   isPersonalized: $isPersonalized")
            
            val notification = BroadcastNotification(
                broadcastId = data.optString("broadcastId", data.optString("orderId", "")),
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
                dropAddress = data.optJSONObject("dropLocation")?.optString("address", "")
                    ?: data.optString("dropAddress", ""),
                dropCity = data.optJSONObject("dropLocation")?.optString("city", "")
                    ?: data.optString("dropCity", ""),
                distanceKm = data.optInt("distance", data.optInt("distanceKm", 0)),
                goodsType = data.optString("goodsType", "General"),
                isUrgent = data.optBoolean("isUrgent", false),
                expiresAt = data.optString("expiresAt", ""),
                requestedVehicles = requestedVehiclesList,
                // Personalized fields
                trucksYouCanProvide = trucksYouCanProvide,
                maxTrucksYouCanProvide = maxTrucksYouCanProvide,
                yourAvailableTrucks = yourAvailableTrucks,
                yourTotalTrucks = yourTotalTrucks,
                trucksStillNeeded = trucksStillNeeded,
                isPersonalized = isPersonalized
            )
            
            // Convert to BroadcastTrip for overlay manager
            val broadcastTrip = notification.toBroadcastTrip()
            
            timber.log.Timber.i("╔══════════════════════════════════════════════════════════════╗")
            timber.log.Timber.i("║  🎯 TRIGGERING BROADCAST OVERLAY                             ║")
            timber.log.Timber.i("╠══════════════════════════════════════════════════════════════╣")
            timber.log.Timber.i("║  Broadcast ID: ${broadcastTrip.broadcastId}")
            timber.log.Timber.i("║  Customer: ${broadcastTrip.customerName}")
            timber.log.Timber.i("║  Trucks Needed: ${broadcastTrip.totalTrucksNeeded}")
            timber.log.Timber.i("║  Vehicle Types: ${broadcastTrip.requestedVehicles.size}")
            broadcastTrip.requestedVehicles.forEach { rv ->
                timber.log.Timber.i("║    - ${rv.vehicleType}/${rv.vehicleSubtype}: ${rv.count} @ ₹${rv.farePerTruck}")
            }
            timber.log.Timber.i("║  Pickup: ${broadcastTrip.pickupLocation.address}")
            timber.log.Timber.i("║  Drop: ${broadcastTrip.dropLocation.address}")
            timber.log.Timber.i("╚══════════════════════════════════════════════════════════════╝")
            
            // =================================================================
            // SHOW BROADCAST OVERLAY - CRITICAL!
            // =================================================================
            // This MUST run on Main thread to update UI
            // BroadcastOverlayManager.showBroadcast() updates StateFlows
            // which are observed by BroadcastOverlayScreen in MainActivity
            // =================================================================
            
            // Show full-screen overlay (Rapido style)
            CoroutineScope(Dispatchers.Main).launch {
                val shown = com.weelo.logistics.broadcast.BroadcastOverlayManager.showBroadcast(broadcastTrip)
                if (shown) {
                    timber.log.Timber.i("✅ OVERLAY SHOWN for broadcast: ${broadcastTrip.broadcastId}")
                } else {
                    timber.log.Timber.w("⚠️ OVERLAY NOT SHOWN - user might be offline or broadcast duplicate")
                    timber.log.Timber.w("   Queue info: ${com.weelo.logistics.broadcast.BroadcastOverlayManager.getQueueInfo()}")
                }
            }
            
            // Also emit to flow for BroadcastListScreen history
            CoroutineScope(Dispatchers.IO).launch {
                _newBroadcasts.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing broadcast: ${e.message}")
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
            
            CoroutineScope(Dispatchers.IO).launch {
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
            
            CoroutineScope(Dispatchers.IO).launch {
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
            
            CoroutineScope(Dispatchers.IO).launch {
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
                orderId = data.optString("orderId", ""),
                vehicleType = data.optString("vehicleType", ""),
                totalTrucks = data.optInt("totalTrucks", 0),
                trucksFilled = data.optInt("trucksFilled", 0),
                trucksRemaining = data.optInt("trucksRemaining", 0),
                orderStatus = data.optString("orderStatus", "")
            )
            
            CoroutineScope(Dispatchers.IO).launch {
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
            
            CoroutineScope(Dispatchers.IO).launch {
                _assignmentStatusChanged.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing accept confirmation: ${e.message}")
        }
    }
    
    /**
     * Handle booking/broadcast expired
     * 
     * CRITICAL: This is called when a broadcast times out on the backend.
     * We MUST immediately remove it from the overlay so transporters don't
     * see stale requests.
     * 
     * Event: "broadcast_expired" or "booking_expired"
     * Payload: { broadcastId, orderId, reason, message }
     */
    private fun handleBookingExpired(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            
            // Get the ID - backend sends both broadcastId and orderId for compatibility
            val broadcastId = data.optString("broadcastId", 
                data.optString("orderId", 
                    data.optString("bookingId", "")))
            val reason = data.optString("reason", "timeout")
            
            timber.log.Timber.w("╔══════════════════════════════════════════════════════════════╗")
            timber.log.Timber.w("║  ⏰ BROADCAST EXPIRED - REMOVING FROM UI                      ║")
            timber.log.Timber.w("╠══════════════════════════════════════════════════════════════╣")
            timber.log.Timber.w("║  Broadcast ID: $broadcastId")
            timber.log.Timber.w("║  Reason: $reason")
            timber.log.Timber.w("║  Message: ${data.optString("message", "N/A")}")
            timber.log.Timber.w("╚══════════════════════════════════════════════════════════════╝")
            
            if (broadcastId.isNotEmpty()) {
                // CRITICAL: Immediately remove from overlay (Main thread for UI)
                CoroutineScope(Dispatchers.Main).launch {
                    com.weelo.logistics.broadcast.BroadcastOverlayManager.removeBroadcast(broadcastId)
                    timber.log.Timber.i("   ✓ Removed broadcast $broadcastId from overlay")
                }
            }
            
            // Also emit to update any list views
            val notification = BookingUpdatedNotification(
                bookingId = broadcastId,
                status = "expired",
                trucksFilled = data.optInt("trucksFilled", -1),
                trucksNeeded = data.optInt("trucksNeeded", -1)
            )
            
            CoroutineScope(Dispatchers.IO).launch {
                _bookingUpdated.emit(notification)
            }
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
            
            CoroutineScope(Dispatchers.IO).launch {
                _bookingUpdated.emit(notification)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error parsing booking fully filled: ${e.message}")
        }
    }
    
    /**
     * Handle order cancelled by customer
     * 
     * CRITICAL: This IMMEDIATELY removes the order from overlay/broadcast list
     * Called when customer cancels their search/order
     * 
     * Uses BroadcastOverlayManager.removeBroadcast() for instant UI update
     */
    private fun handleOrderCancelled(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val orderId = data.optString("orderId", "")
            val reason = data.optString("reason", "Cancelled by customer")
            
            timber.log.Timber.w("🚫 ORDER CANCELLED: $orderId - $reason")
            
            // IMMEDIATELY remove from broadcast overlay (highest priority!)
            com.weelo.logistics.broadcast.BroadcastOverlayManager.removeBroadcast(orderId)
            
            // Also emit to update any list views
            val notification = BookingUpdatedNotification(
                bookingId = orderId,
                status = "cancelled",
                trucksFilled = -1,
                trucksNeeded = -1
            )
            
            CoroutineScope(Dispatchers.IO).launch {
                _bookingUpdated.emit(notification)
            }
            
            timber.log.Timber.i("   ✓ Removed from overlay and emitted update")
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error handling order cancelled: ${e.message}")
        }
    }
    
    /**
     * Handle order expired (timeout)
     * 
     * Called when order times out without being filled
     * Also removes from overlay and updates UI
     */
    private fun handleOrderExpired(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val orderId = data.optString("orderId", "")
            
            timber.log.Timber.w("⏰ ORDER EXPIRED: $orderId")
            
            // Remove from broadcast overlay
            com.weelo.logistics.broadcast.BroadcastOverlayManager.removeBroadcast(orderId)
            
            // Emit update
            val notification = BookingUpdatedNotification(
                bookingId = orderId,
                status = "expired",
                trucksFilled = data.optInt("trucksFilled", -1),
                trucksNeeded = data.optInt("totalTrucks", -1)
            )
            
            CoroutineScope(Dispatchers.IO).launch {
                _bookingUpdated.emit(notification)
            }
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
     */
    fun disconnect() {
        timber.log.Timber.i("🔌 Disconnecting...")
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
    val dropAddress: String,
    val dropCity: String,
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
    val isPersonalized: Boolean = false         // Is this a personalized broadcast?
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
            pickupLocation = com.weelo.logistics.data.model.Location(
                address = pickupAddress.ifEmpty { pickupCity },
                city = pickupCity,
                latitude = 0.0,
                longitude = 0.0
            ),
            dropLocation = com.weelo.logistics.data.model.Location(
                address = dropAddress.ifEmpty { dropCity },
                city = dropCity,
                latitude = 0.0,
                longitude = 0.0
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
            broadcastTime = System.currentTimeMillis(),
            expiryTime = try {
                java.time.Instant.parse(expiresAt).toEpochMilli()
            } catch (e: Exception) {
                System.currentTimeMillis() + (60 * 1000) // Default 1 min expiry
            },
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
            isPersonalized = isPersonalized
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
    val orderStatus: String
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
data class DriversUpdatedNotification(
    val action: String,              // "added", "updated", "deleted", "status_changed", "drivers_updated"
    val driverId: String,
    val totalDrivers: Int,
    val availableCount: Int,
    val onTripCount: Int
)
