package com.weelo.logistics.data.remote.socket

import com.weelo.logistics.WeeloApp
import com.weelo.logistics.broadcast.BroadcastFeatureFlagsRegistry
import com.weelo.logistics.broadcast.BroadcastFlowCoordinator
import com.weelo.logistics.broadcast.BroadcastOverlayManager
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import com.weelo.logistics.data.remote.WeeloFirebaseService
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.net.URI

/**
 * =============================================================================
 * SOCKET CONNECTION MANAGER - Connection lifecycle, heartbeat, RAMEN replay
 * =============================================================================
 *
 * Owns the Socket.IO Socket instance and manages:
 *   - connect / disconnect / reconnect with exponential backoff
 *   - JWT auth token injection
 *   - Adaptive heartbeat (Uber-style, based on network quality)
 *   - Offline location queue + flush on reconnect
 *   - RAMEN replay sequence tracking (lastAckedSeq)
 *   - Broadcast dedup cache (LRU 2048 entries, 1-hour TTL)
 *
 * This class does NOT register individual event handlers.
 * After connection, it calls [onSetupEventListeners] so the router can wire
 * all on(...) handlers onto the live Socket instance.
 * =============================================================================
 */
class SocketConnectionManager(
    private val serviceScope: CoroutineScope,
    private val connectionState: MutableStateFlow<SocketConnectionState>,
    private val onSetupEventListeners: (Socket) -> Unit
) {
    // Socket instance
    internal var socket: Socket? = null
        private set

    // Stored credentials for reconnection
    private var serverUrl: String? = null
    private var authToken: String? = null

    // Heartbeat
    private var heartbeatJob: Job? = null

    @Volatile
    private var _isOnlineLocally = false

    // RAMEN replay sequence tracking
    @Volatile
    internal var lastAckedSeq: Long = 0L

    // Broadcast dedup cache (LRU, 2048 entries)
    internal val seenBroadcastIds = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Long>): Boolean = size > 2048
    }

    // FIX-25: Track joined rooms for re-join on reconnect
    private val activeRooms = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Offline location queue
    private val offlineLocationQueue = java.util.ArrayDeque<Pair<Double, Double>>(50)

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    fun isOnlineLocally(): Boolean = _isOnlineLocally

    fun setOnlineLocally(online: Boolean) {
        _isOnlineLocally = online
        if (online) startHeartbeat() else stopHeartbeat()
    }

    fun isConnected(): Boolean = connectionState.value is SocketConnectionState.Connected

    /**
     * Connect to Socket.IO server.
     *
     * @param url Server URL (e.g., "http://10.0.2.2:3000" for emulator)
     * @param token JWT authentication token
     */
    fun connect(url: String, token: String) {
        timber.log.Timber.i("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557")
        timber.log.Timber.i("\u2551  \uD83D\uDD0C SOCKET.IO CONNECT CALLED                                 \u2551")
        timber.log.Timber.i("\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563")
        timber.log.Timber.i("\u2551  URL: $url")
        timber.log.Timber.i("\u2551  Token length: ${token.length}")
        timber.log.Timber.i("\u2551  Current state: ${connectionState.value}")
        timber.log.Timber.i("\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D")

        val currentState = connectionState.value
        val isSameConnectionRequest = serverUrl == url && authToken == token
        if ((currentState is SocketConnectionState.Connected || currentState is SocketConnectionState.Connecting) &&
            isSameConnectionRequest
        ) {
            timber.log.Timber.w("\u26A0\uFE0F Socket already %s for same credentials, skipping reconnect", currentState::class.simpleName)
            return
        }

        if (socket != null && connectionState.value !is SocketConnectionState.Connected) {
            timber.log.Timber.w("\u26A0\uFE0F Cleaning up stale socket before creating a new connection")
            socket?.off()
            socket?.disconnect()
            socket = null
        }

        serverUrl = url
        authToken = token
        connectionState.value = SocketConnectionState.Connecting
        timber.log.Timber.i("\uD83D\uDD0C Connecting to Socket.IO server: $url")

        try {
            val options = IO.Options().apply {
                val authPayload = mutableMapOf<String, String>("token" to token)
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
                    timber.log.Timber.w("\u26A0\uFE0F Socket auth started without FCM token; push fallback may be delayed")
                }
                val currentLastSeq = lastAckedSeq
                if (currentLastSeq > 0L) {
                    authPayload["lastSeq"] = currentLastSeq.toString()
                    timber.log.Timber.i("\uD83D\uDD04 Reconnecting with lastSeq=$currentLastSeq \u2014 server will replay missed messages")
                }
                auth = authPayload
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 1000L + kotlin.random.Random.nextLong(2000L)
                reconnectionDelayMax = 30000
                timeout = 20000
                forceNew = true
                transports = arrayOf("websocket")
            }

            socket = IO.socket(URI.create(url), options)
            setupCoreListeners()
            onSetupEventListeners(socket!!)
            socket?.connect()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Connection error: ${e.message}")
            connectionState.value = SocketConnectionState.Error(e.message ?: "Connection failed")
        }
    }

    fun disconnect() {
        timber.log.Timber.i("\uD83D\uDD0C Disconnecting...")
        _isOnlineLocally = false
        stopHeartbeat()
        socket?.disconnect()
        socket?.off()
        socket = null
        connectionState.value = SocketConnectionState.Disconnected
    }

    fun reconnect() {
        timber.log.Timber.i("\uD83D\uDD04 Reconnecting...")
        disconnect()
        val url = serverUrl
        val token = authToken
        if (url != null && token != null) {
            connect(url, token)
        }
    }

    // =========================================================================
    // CORE SOCKET LISTENERS (connect/disconnect/error)
    // =========================================================================

    private fun setupCoreListeners() {
        val s = socket ?: return
        // Remove all previous listeners to prevent duplicates
        s.off()

        s.on(Socket.EVENT_CONNECT) {
            timber.log.Timber.i("\u2705 Socket.IO connected")
            connectionState.value = SocketConnectionState.Connected
            BroadcastOverlayManager.onSocketReconnected()
            if (BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                BroadcastFlowCoordinator.requestReconcile(force = true)
            }
            if (_isOnlineLocally) {
                timber.log.Timber.i("\uD83D\uDC93 Auto-restarting heartbeat on reconnect (driver was online)")
                startHeartbeat()
                flushOfflineLocationQueue()
            }
            // FIX-25 + FIX-8: Re-join all tracked rooms on reconnect using
            // type-specific events that the backend actually handles.
            val roomsSnapshot = activeRooms.toList()
            if (roomsSnapshot.isNotEmpty()) {
                timber.log.Timber.i("Re-joining ${roomsSnapshot.size} active room(s) on reconnect")
                for (room in roomsSnapshot) {
                    emitJoinRoom(room)
                }
            }

            val latestFcmToken = WeeloFirebaseService.fcmToken
            if (!latestFcmToken.isNullOrBlank()) {
                socket?.emit("fcm_token_refresh", JSONObject().apply {
                    put("fcmToken", latestFcmToken)
                })
                timber.log.Timber.i("\uD83D\uDD04 FCM token relayed to backend on connect")
            }
        }

        s.on(Socket.EVENT_DISCONNECT) { args ->
            val reason = args.firstOrNull()?.toString() ?: "unknown"
            timber.log.Timber.w("\uD83D\uDD0C Socket.IO disconnected: $reason")
            connectionState.value = SocketConnectionState.Disconnected
            stopHeartbeat()
        }

        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = args.firstOrNull()?.toString() ?: "unknown"
            timber.log.Timber.e("\u274C Connection error: $error")
            connectionState.value = SocketConnectionState.Error(error)
        }
    }

    // =========================================================================
    // HEARTBEAT (Uber-style adaptive interval)
    // =========================================================================

    fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            timber.log.Timber.i("\uD83D\uDC93 Adaptive heartbeat started")
            while (isActive) {
                try {
                    val heartbeatData = JSONObject().apply { put("t", "hb") }
                    try {
                        val ctx = WeeloApp.getInstance()?.applicationContext
                        if (ctx != null) {
                            val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE)
                                as? android.location.LocationManager
                            @Suppress("MissingPermission")
                            val loc = lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                ?: lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                            val MAX_LOC_AGE_MS = 5 * 60 * 1000L
                            if (loc != null && (System.currentTimeMillis() - loc.time) < MAX_LOC_AGE_MS) {
                                heartbeatData.put("lat", loc.latitude)
                                heartbeatData.put("lng", loc.longitude)
                                heartbeatData.put("speed", loc.speed.toDouble())
                                heartbeatData.put("bearing", loc.bearing.toDouble())
                                heartbeatData.put("battery", getDeviceBatteryLevel(ctx))
                            }
                        }
                    } catch (_: Exception) { /* best effort */ }

                    if (socket?.connected() == true) {
                        socket?.emit(SocketConstants.HEARTBEAT, heartbeatData)
                    } else {
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
                                val MAX_LOC_AGE_MS = 5 * 60 * 1000L
                                if (loc != null && (System.currentTimeMillis() - loc.time) < MAX_LOC_AGE_MS) {
                                    queueOfflineLocation(loc.latitude, loc.longitude)
                                }
                            }
                        } catch (_: Exception) { /* best effort */ }
                    }
                } catch (e: Exception) {
                    timber.log.Timber.w("\uD83D\uDC93 Heartbeat emit failed: ${e.message}")
                }
                val intervalMs = getAdaptiveHeartbeatInterval()
                delay(intervalMs)
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        timber.log.Timber.i("\uD83D\uDC93 Heartbeat stopped")
    }

    // =========================================================================
    // OFFLINE LOCATION QUEUE
    // =========================================================================

    private fun queueOfflineLocation(lat: Double, lng: Double) {
        synchronized(offlineLocationQueue) {
            if (offlineLocationQueue.size >= 50) offlineLocationQueue.removeFirst()
            offlineLocationQueue.addLast(Pair(lat, lng))
        }
    }

    internal fun flushOfflineLocationQueue() {
        val batch = synchronized(offlineLocationQueue) {
            val items = offlineLocationQueue.toList()
            offlineLocationQueue.clear()
            items
        }
        if (batch.isEmpty()) return
        timber.log.Timber.i("\uD83D\uDCE4 Flushing ${batch.size} offline location(s)")
        val last = batch.last()
        val locationData = JSONObject().apply {
            put("type", "heartbeat")
            put("lat", last.first)
            put("lng", last.second)
            put("_offlineFlush", true)
            put("_queueSize", batch.size)
        }
        socket?.emit(SocketConstants.HEARTBEAT, locationData)
    }

    // =========================================================================
    // CLIENT -> SERVER EMITTERS
    // =========================================================================

    // FIX-25: Join a room and track it for re-join on reconnect
    // FIX-8: Use type-specific join events that backend actually handles
    fun joinRoom(roomId: String) {
        if (roomId.isBlank()) return
        activeRooms.add(roomId)
        emitJoinRoom(roomId)
        timber.log.Timber.i("Joined room: $roomId (tracked for reconnect)")
    }

    // FIX-25: Leave a room and stop tracking it
    // FIX-8: Use type-specific leave events that backend actually handles
    fun leaveRoom(roomId: String) {
        activeRooms.remove(roomId)
        emitLeaveRoom(roomId)
        timber.log.Timber.i("Left room: $roomId")
    }

    /**
     * FIX-8: Emit the correct backend join event based on room prefix.
     *
     * Backend handlers:
     *   - "join_booking"     expects plain bookingId string
     *   - "join_order"       expects plain orderId string
     *   - "join_transporter" expects JSONObject { transporterId }
     *
     * Generic "join" has NO handler on the backend, so rooms joined with it
     * are silently ignored and the client misses all room-scoped events.
     */
    private fun emitJoinRoom(room: String) {
        when {
            room.startsWith("booking:") -> {
                val bookingId = room.removePrefix("booking:")
                socket?.emit(SocketConstants.JOIN_BOOKING, bookingId)
            }
            room.startsWith("order:") -> {
                val orderId = room.removePrefix("order:")
                socket?.emit(SocketConstants.JOIN_ORDER, orderId)
            }
            room.startsWith("transporter:") -> {
                val transporterId = room.removePrefix("transporter:")
                val payload = JSONObject().apply { put("transporterId", transporterId) }
                socket?.emit(SocketConstants.JOIN_TRANSPORTER, payload)
            }
            else -> {
                timber.log.Timber.w("Unknown room type: $room — emitting generic join (may be ignored by backend)")
                socket?.emit("join", room)
            }
        }
    }

    /**
     * FIX-8: Emit the correct backend leave event based on room prefix.
     *
     * Backend handlers:
     *   - "leave_booking" expects plain bookingId string
     *   - "leave_order"   expects plain orderId string
     */
    private fun emitLeaveRoom(room: String) {
        when {
            room.startsWith("booking:") -> {
                val bookingId = room.removePrefix("booking:")
                socket?.emit(SocketConstants.LEAVE_BOOKING, bookingId)
            }
            room.startsWith("order:") -> {
                val orderId = room.removePrefix("order:")
                socket?.emit(SocketConstants.LEAVE_ORDER, orderId)
            }
            else -> {
                timber.log.Timber.w("Unknown room type for leave: $room — emitting generic leave")
                socket?.emit("leave", room)
            }
        }
    }

    fun emitEvent(event: String, data: Any) {
        socket?.emit(event, data)
    }

    fun emitEvent(event: String, data: String) {
        socket?.emit(event, data)
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private fun getAdaptiveHeartbeatInterval(): Long {
        return try {
            val ctx = WeeloApp.getInstance()?.applicationContext ?: return 10_000L
            val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return 10_000L
            val nc = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                nc == null -> 15_000L
                nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> 3_000L
                nc.linkDownstreamBandwidthKbps > 5000 -> 3_000L
                nc.linkDownstreamBandwidthKbps > 1000 -> 5_000L
                nc.linkDownstreamBandwidthKbps > 200 -> 10_000L
                else -> 15_000L
            }
        } catch (_: Exception) {
            10_000L
        }
    }

    private fun getDeviceBatteryLevel(ctx: android.content.Context): Int {
        return try {
            val bm = ctx.getSystemService(android.content.Context.BATTERY_SERVICE)
                as? android.os.BatteryManager
            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (_: Exception) { -1 }
    }
}
