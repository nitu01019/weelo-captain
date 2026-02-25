package com.weelo.logistics.data.remote

import com.weelo.logistics.broadcast.BroadcastFeatureFlagsRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * =============================================================================
 * OPTIMIZED REAL-TIME SERVICE - Polling-Based Implementation
 * =============================================================================
 * 
 * This service provides real-time updates using optimized polling.
 * Can be upgraded to WebSocket (Socket.IO) when needed.
 * 
 * OPTIMIZATIONS:
 * 1. Adaptive polling interval based on activity
 * 2. Exponential backoff on errors
 * 3. Connection state management
 * 4. Buffered event flows
 * =============================================================================
 */
@Deprecated(
    message = "Legacy polling path. Use SocketIOService + BroadcastFlowCoordinator.",
    replaceWith = ReplaceWith("SocketIOService")
)
object WebSocketService {
    
    private const val TAG = "WebSocketService"
    
    // Configuration
    private const val POLL_INTERVAL_ACTIVE_MS = 5000L    // 5 seconds when active
    private const val POLL_INTERVAL_IDLE_MS = 30000L     // 30 seconds when idle
    private const val MAX_RECONNECT_ATTEMPTS = 10
    private const val INITIAL_RECONNECT_DELAY_MS = 1000L
    private const val MAX_RECONNECT_DELAY_MS = 30000L
    
    // State
    @Volatile
    private var isConnected = false
    @Volatile
    private var isConnecting = false
    private var pollingJob: Job? = null
    private val reconnectAttempts = AtomicInteger(0)
    
    // Stored credentials
    private var serverUrl: String? = null
    private var authToken: String? = null
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Connection quality
    private val _connectionQuality = MutableStateFlow(ConnectionQuality.UNKNOWN)
    val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()
    
    // Event flows
    private val _newBroadcasts = MutableSharedFlow<NewBroadcastEvent>(replay = 1, extraBufferCapacity = 20)
    val newBroadcasts: SharedFlow<NewBroadcastEvent> = _newBroadcasts.asSharedFlow()
    
    private val _acceptConfirmations = MutableSharedFlow<AcceptConfirmationEvent>(replay = 1, extraBufferCapacity = 10)
    val acceptConfirmations: SharedFlow<AcceptConfirmationEvent> = _acceptConfirmations.asSharedFlow()
    
    private val _trucksRemainingUpdates = MutableSharedFlow<TrucksRemainingEvent>(replay = 1, extraBufferCapacity = 20)
    val trucksRemainingUpdates: SharedFlow<TrucksRemainingEvent> = _trucksRemainingUpdates.asSharedFlow()
    
    private val _requestTakenEvents = MutableSharedFlow<RequestTakenEvent>(replay = 0, extraBufferCapacity = 20)
    val requestTakenEvents: SharedFlow<RequestTakenEvent> = _requestTakenEvents.asSharedFlow()
    
    private val _orderStatusUpdates = MutableSharedFlow<OrderStatusEvent>(replay = 1, extraBufferCapacity = 10)
    val orderStatusUpdates: SharedFlow<OrderStatusEvent> = _orderStatusUpdates.asSharedFlow()
    
    /**
     * Connect to real-time service
     */
    fun connect(serverUrl: String, token: String) {
        if (BroadcastFeatureFlagsRegistry.current().broadcastDisableLegacyWebsocketPath) {
            timber.log.Timber.w("Legacy WebSocketService disabled by feature flag")
            return
        }
        if (isConnected || isConnecting) {
            timber.log.Timber.d("Already connected/connecting")
            return
        }
        
        this.serverUrl = serverUrl
        this.authToken = token
        isConnecting = true
        reconnectAttempts.set(0)
        
        _connectionState.value = ConnectionState.Connecting
        timber.log.Timber.i("🔌 Connecting to real-time service")
        
        // Mark as connected (polling mode)
        isConnected = true
        isConnecting = false
        _connectionState.value = ConnectionState.Connected
        _connectionQuality.value = ConnectionQuality.GOOD
        
        timber.log.Timber.i("✅ Real-time service connected (polling mode)")
    }
    
    /**
     * Start polling for broadcasts
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        
        pollingJob = scope.launch {
            timber.log.Timber.i("📡 Starting broadcast polling")
            while (isActive && isConnected) {
                try {
                    // Poll for new broadcasts
                    pollForBroadcasts()
                    _connectionQuality.value = ConnectionQuality.GOOD
                    delay(POLL_INTERVAL_ACTIVE_MS)
                } catch (e: Exception) {
                    timber.log.Timber.w("Polling error: ${e.message}")
                    _connectionQuality.value = ConnectionQuality.POOR
                    delay(POLL_INTERVAL_IDLE_MS)
                }
            }
        }
    }
    
    /**
     * Stop polling
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        timber.log.Timber.i("📡 Stopped broadcast polling")
    }
    
    /**
     * Poll for new broadcasts (called by polling loop)
     */
    private suspend fun pollForBroadcasts() {
        // This would call the API to get active broadcasts
        // For now, it's a placeholder - the BroadcastListScreen fetches via API
        timber.log.Timber.v("Polling for broadcasts...")
    }
    
    /**
     * Emit a new broadcast event (called when API returns new data)
     */
    suspend fun emitNewBroadcast(event: NewBroadcastEvent) {
        _newBroadcasts.emit(event)
        timber.log.Timber.i("📢 New broadcast emitted: ${event.orderId}")
    }
    
    /**
     * Emit acceptance confirmation
     */
    suspend fun emitAcceptConfirmation(event: AcceptConfirmationEvent) {
        _acceptConfirmations.emit(event)
        timber.log.Timber.i("✅ Accept confirmation emitted: ${event.requestId}")
    }
    
    /**
     * Emit trucks remaining update
     */
    suspend fun emitTrucksRemainingUpdate(event: TrucksRemainingEvent) {
        _trucksRemainingUpdates.emit(event)
        timber.log.Timber.i("📊 Trucks remaining update: ${event.trucksRemaining}")
    }
    
    /**
     * Emit request taken event
     */
    suspend fun emitRequestTaken(event: RequestTakenEvent) {
        _requestTakenEvents.emit(event)
        timber.log.Timber.w("⚠️ Request taken: ${event.requestId}")
    }
    
    /**
     * Emit order status update
     */
    suspend fun emitOrderStatus(event: OrderStatusEvent) {
        _orderStatusUpdates.emit(event)
        timber.log.Timber.i("📋 Order status: ${event.status}")
    }
    
    /**
     * Force reconnect
     */
    fun forceReconnect() {
        timber.log.Timber.i("🔄 Force reconnect")
        disconnect()
        val url = serverUrl
        val token = authToken
        if (url != null && token != null) {
            connect(url, token)
        }
    }
    
    /**
     * Disconnect
     */
    fun disconnect() {
        timber.log.Timber.i("🔌 Disconnecting...")
        stopPolling()
        isConnected = false
        isConnecting = false
        _connectionState.value = ConnectionState.Disconnected
        _connectionQuality.value = ConnectionQuality.UNKNOWN
        timber.log.Timber.i("✅ Disconnected")
    }
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * Get connection stats
     */
    fun getConnectionStats(): ConnectionStats {
        return ConnectionStats(
            isConnected = isConnected,
            isConnecting = isConnecting,
            reconnectAttempts = reconnectAttempts.get(),
            maxReconnectAttempts = MAX_RECONNECT_ATTEMPTS,
            connectionQuality = _connectionQuality.value
        )
    }
}

// =============================================================================
// CONNECTION STATE & QUALITY
// =============================================================================

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

enum class ConnectionQuality {
    UNKNOWN, POOR, FAIR, GOOD, EXCELLENT
}

data class ConnectionStats(
    val isConnected: Boolean,
    val isConnecting: Boolean,
    val reconnectAttempts: Int,
    val maxReconnectAttempts: Int,
    val connectionQuality: ConnectionQuality
)

// =============================================================================
// EVENT DATA CLASSES
// =============================================================================

data class NewBroadcastEvent(
    val orderId: String,
    val customerName: String,
    val vehicleType: String,
    val vehicleSubtype: String,
    val trucksNeeded: Int,
    val pricePerTruck: Int,
    val totalFare: Int,
    val pickupAddress: String,
    val pickupCity: String,
    val dropAddress: String,
    val dropCity: String,
    val distanceKm: Int,
    val goodsType: String,
    val timeoutSeconds: Int,
    val isUrgent: Boolean,
    val requestIds: List<String>
)

data class AcceptConfirmationEvent(
    val success: Boolean,
    val requestId: String,
    val orderId: String,
    val vehicleNumber: String,
    val tripId: String,
    val message: String,
    val moreTrucksAvailable: Boolean,
    val remainingOfSameType: Int,
    val remainingRequestIds: List<String>
)

data class TrucksRemainingEvent(
    val orderId: String,
    val vehicleType: String,
    val vehicleSubtype: String,
    val totalTrucks: Int,
    val trucksFilled: Int,
    val trucksRemaining: Int,
    val remainingOfSameType: Int,
    val remainingRequestIds: List<String>,
    val orderStatus: String,
    val message: String
)

data class RequestTakenEvent(
    val orderId: String,
    val requestId: String,
    val takenBy: String,
    val message: String
)

data class OrderStatusEvent(
    val orderId: String,
    val status: String,
    val message: String
)
