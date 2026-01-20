package com.weelo.logistics.data.remote

import android.util.Log
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
     * @param url Server URL (e.g., "http://10.0.2.2:3000" for emulator)
     * @param token JWT authentication token
     */
    fun connect(url: String, token: String) {
        if (_connectionState.value is SocketConnectionState.Connected) {
            Log.d(TAG, "Already connected")
            return
        }
        
        serverUrl = url
        authToken = token
        
        _connectionState.value = SocketConnectionState.Connecting
        Log.i(TAG, "🔌 Connecting to Socket.IO: $url")
        
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
            Log.e(TAG, "Connection error: ${e.message}", e)
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
                Log.i(TAG, "✅ Socket.IO connected")
                _connectionState.value = SocketConnectionState.Connected
            }
            
            on(Socket.EVENT_DISCONNECT) { args ->
                val reason = args.firstOrNull()?.toString() ?: "unknown"
                Log.w(TAG, "🔌 Socket.IO disconnected: $reason")
                _connectionState.value = SocketConnectionState.Disconnected
            }
            
            on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown"
                Log.e(TAG, "❌ Connection error: $error")
                _connectionState.value = SocketConnectionState.Error(error)
            }
            
            // Server confirmation
            on(Events.CONNECTED) { args ->
                val data = args.firstOrNull()
                Log.i(TAG, "✅ Server confirmed connection: $data")
            }
            
            // Error events
            on(Events.ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown"
                Log.e(TAG, "❌ Server error: $error")
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
            
            // Booking fully filled
            on(Events.BOOKING_FULLY_FILLED) { args ->
                handleBookingFullyFilled(args)
            }
        }
    }
    
    /**
     * Handle new broadcast event
     */
    private fun handleNewBroadcast(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            Log.i(TAG, "📢 New broadcast received: $data")
            
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
                expiresAt = data.optString("expiresAt", "")
            )
            
            CoroutineScope(Dispatchers.IO).launch {
                _newBroadcasts.emit(notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing broadcast: ${e.message}", e)
        }
    }
    
    /**
     * Handle truck assigned event
     */
    private fun handleTruckAssigned(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            Log.i(TAG, "🚛 Truck assigned: $data")
            
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
            Log.e(TAG, "Error parsing truck assigned: ${e.message}", e)
        }
    }
    
    /**
     * Handle assignment status changed
     */
    private fun handleAssignmentStatusChanged(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            Log.i(TAG, "📋 Assignment status changed: $data")
            
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
            Log.e(TAG, "Error parsing assignment status: ${e.message}", e)
        }
    }
    
    /**
     * Handle booking updated
     */
    private fun handleBookingUpdated(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            Log.i(TAG, "📝 Booking updated: $data")
            
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
            Log.e(TAG, "Error parsing booking update: ${e.message}", e)
        }
    }
    
    /**
     * Handle trucks remaining update
     */
    private fun handleTrucksRemainingUpdate(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            Log.i(TAG, "📊 Trucks remaining update: $data")
            
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
            Log.e(TAG, "Error parsing trucks remaining: ${e.message}", e)
        }
    }
    
    /**
     * Handle accept confirmation
     */
    private fun handleAcceptConfirmation(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            Log.i(TAG, "✅ Accept confirmation: $data")
            
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
            Log.e(TAG, "Error parsing accept confirmation: ${e.message}", e)
        }
    }
    
    /**
     * Handle booking expired
     */
    private fun handleBookingExpired(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            Log.w(TAG, "⏰ Booking expired: $data")
            
            val notification = BookingUpdatedNotification(
                bookingId = data.optString("bookingId", data.optString("orderId", "")),
                status = "expired",
                trucksFilled = -1,
                trucksNeeded = -1
            )
            
            CoroutineScope(Dispatchers.IO).launch {
                _bookingUpdated.emit(notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing booking expired: ${e.message}", e)
        }
    }
    
    /**
     * Handle booking fully filled
     */
    private fun handleBookingFullyFilled(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            Log.i(TAG, "✅ Booking fully filled: $data")
            
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
            Log.e(TAG, "Error parsing booking fully filled: ${e.message}", e)
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
            Log.w(TAG, "Cannot join room: Not connected")
            return
        }
        socket?.emit(Events.JOIN_BOOKING, bookingId)
        Log.d(TAG, "Joined booking room: $bookingId")
    }
    
    /**
     * Leave a booking room
     */
    fun leaveBookingRoom(bookingId: String) {
        socket?.emit(Events.LEAVE_BOOKING, bookingId)
        Log.d(TAG, "Left booking room: $bookingId")
    }
    
    /**
     * Join an order room for multi-truck updates
     */
    fun joinOrderRoom(orderId: String) {
        if (_connectionState.value !is SocketConnectionState.Connected) {
            Log.w(TAG, "Cannot join order room: Not connected")
            return
        }
        socket?.emit(Events.JOIN_ORDER, orderId)
        Log.d(TAG, "Joined order room: $orderId")
    }
    
    /**
     * Leave an order room
     */
    fun leaveOrderRoom(orderId: String) {
        socket?.emit(Events.LEAVE_ORDER, orderId)
        Log.d(TAG, "Left order room: $orderId")
    }
    
    /**
     * Update driver location (for drivers)
     */
    fun updateLocation(tripId: String, latitude: Double, longitude: Double, speed: Float = 0f, bearing: Float = 0f) {
        if (_connectionState.value !is SocketConnectionState.Connected) {
            Log.w(TAG, "Cannot update location: Not connected")
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
        Log.i(TAG, "🔌 Disconnecting...")
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionState.value = SocketConnectionState.Disconnected
    }
    
    /**
     * Force reconnect
     */
    fun reconnect() {
        Log.i(TAG, "🔄 Reconnecting...")
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
    val expiresAt: String
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
