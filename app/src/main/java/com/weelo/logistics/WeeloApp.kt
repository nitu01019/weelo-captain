package com.weelo.logistics

import android.app.Application
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.utils.Constants
import com.weelo.logistics.utils.HeartbeatManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
// import dagger.hilt.android.HiltAndroidApp

/**
 * Weelo Application Class
 * Entry point for the application
 * 
 * Initializes:
 * - RetrofitClient for API calls
 * - Secure token storage
 * - WebSocket connection for real-time broadcasts
 */
// @HiltAndroidApp
class WeeloApp : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val TAG = "WeeloApp"
        
        @Volatile
        private var instance: WeeloApp? = null
        
        fun getInstance(): WeeloApp? = instance
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize Timber for production-safe logging
        // Debug builds: logs appear in logcat
        // Release builds: no logs (Timber plants no tree)
        if (BuildConfig.DEBUG) {
            timber.log.Timber.plant(timber.log.Timber.DebugTree())
        }
        
        // Initialize Coil ImageLoader with disk cache
        coil.Coil.setImageLoader(
            com.weelo.logistics.core.image.ImageLoaderConfig.getInstance(this)
        )
        
        // Initialize RetrofitClient with application context
        // This sets up secure token storage and API client
        RetrofitClient.init(this)
        
        // Initialize HeartbeatManager for geolocation tracking
        HeartbeatManager.initialize(this)
        
        // Log API configuration
        Constants.API.logConfiguration()
        
        // Connect to WebSocket if user is logged in
        connectWebSocketIfLoggedIn()
        
        // Start heartbeat if user is logged in (for geolocation matching)
        startHeartbeatIfLoggedIn()
    }
    
    /**
     * Connect to WebSocket if user is already logged in
     * This ensures broadcasts are received even if BroadcastListScreen isn't open
     * 
     * CRITICAL: WebSocket connection is REQUIRED for receiving broadcast overlays!
     * If this doesn't connect, transporters won't see new booking requests.
     */
    fun connectWebSocketIfLoggedIn() {
        applicationScope.launch {
            try {
                val token = RetrofitClient.getAccessToken()
                val role = RetrofitClient.getUserRole()
                val userId = RetrofitClient.getUserId()
                
                timber.log.Timber.i("╔══════════════════════════════════════════════════════════════╗")
                timber.log.Timber.i("║  🔌 WEBSOCKET CONNECTION CHECK                               ║")
                timber.log.Timber.i("╠══════════════════════════════════════════════════════════════╣")
                timber.log.Timber.i("║  Token present: ${!token.isNullOrEmpty()}")
                timber.log.Timber.i("║  Token length: ${token?.length ?: 0}")
                timber.log.Timber.i("║  User ID: ${userId ?: "NULL"}")
                timber.log.Timber.i("║  User Role: ${role ?: "NULL"}")
                timber.log.Timber.i("║  WebSocket URL: ${Constants.API.WS_URL}")
                timber.log.Timber.i("╚══════════════════════════════════════════════════════════════╝")
                
                if (!token.isNullOrEmpty() && (role == "transporter" || role == "driver")) {
                    timber.log.Timber.i("🔌 Auto-connecting to WebSocket (role: $role, user: $userId)")
                    timber.log.Timber.i("   URL: ${Constants.API.WS_URL}")
                    SocketIOService.connect(Constants.API.WS_URL, token)
                    timber.log.Timber.i("✅ WebSocket connect() called - check SocketIOService logs for connection status")
                } else {
                    timber.log.Timber.w("⏭️ Skipping WebSocket connection:")
                    timber.log.Timber.w("   - Token empty: ${token.isNullOrEmpty()}")
                    timber.log.Timber.w("   - Role: $role (needs 'transporter' or 'driver')")
                }
            } catch (e: Exception) {
                timber.log.Timber.e("❌ Failed to auto-connect WebSocket: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Disconnect WebSocket (call on logout)
     */
    fun disconnectWebSocket() {
        timber.log.Timber.i("🔌 Disconnecting WebSocket")
        SocketIOService.disconnect()
    }
    
    /**
     * Start heartbeat service if user is logged in
     * This enables proximity-based matching for broadcasts
     */
    fun startHeartbeatIfLoggedIn() {
        applicationScope.launch {
            try {
                val token = RetrofitClient.getAccessToken()
                val role = RetrofitClient.getUserRole()
                
                if (!token.isNullOrEmpty() && (role == "transporter" || role == "driver")) {
                    if (HeartbeatManager.hasLocationPermission(this@WeeloApp)) {
                        timber.log.Timber.i("💓 Starting HeartbeatManager (role: $role)")
                        HeartbeatManager.start()
                    } else {
                        timber.log.Timber.w("⚠️ Location permission not granted, heartbeat not started")
                    }
                } else {
                    timber.log.Timber.d("⏭️ Skipping heartbeat - not logged in or not transporter/driver")
                }
            } catch (e: Exception) {
                timber.log.Timber.e("❌ Failed to start heartbeat: ${e.message}")
            }
        }
    }
    
    /**
     * Stop heartbeat service (call on logout)
     */
    fun stopHeartbeat() {
        timber.log.Timber.i("💔 Stopping HeartbeatManager")
        HeartbeatManager.stop()
    }
    
    /**
     * Full logout - disconnects WebSocket and stops heartbeat
     */
    fun logout() {
        stopHeartbeat()
        disconnectWebSocket()
        RetrofitClient.clearAllData()
        timber.log.Timber.i("👋 User logged out, all services stopped")
    }
}
