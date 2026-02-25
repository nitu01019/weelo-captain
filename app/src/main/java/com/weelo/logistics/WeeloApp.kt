package com.weelo.logistics

import android.app.Application
import android.os.StrictMode
import com.google.firebase.messaging.FirebaseMessaging
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import com.weelo.logistics.broadcast.BroadcastFeatureFlagsRegistry
import com.weelo.logistics.broadcast.BroadcastFlowCoordinator
import com.weelo.logistics.data.remote.NotificationTokenSync
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.remote.WeeloFirebaseService
import com.weelo.logistics.offline.AvailabilityManager
import com.weelo.logistics.offline.AvailabilityState
import com.weelo.logistics.utils.Constants
import com.weelo.logistics.utils.HeartbeatManager
import com.weelo.logistics.utils.RoleScopedLocalePolicy
import com.weelo.logistics.utils.TransporterOnlineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
            enableStrictMode()
        }
        
        // =====================================================================
        // P4 FIX: Apply saved locale to Application context on startup.
        // Without this, any code using applicationContext for string resources
        // gets the system default locale instead of user's selected language.
        // Uses synchronous SharedPreferences (same source as attachBaseContext).
        //
        // NOTE: LocaleHelper.setLocale() returns a NEW context via
        // createConfigurationContext() — it does NOT modify `this` Application.
        // To update the Application's own resources, we must directly update
        // the base resources configuration. This ensures getString(), etc.
        // on applicationContext return localized strings.
        //
        // SCALABILITY: O(1), synchronous, runs once per process creation.
        // ROLE ISOLATION: Reads SharedPreferences — only drivers save language
        // there. Transporters never write to "preferred_language", so this
        // is a no-op for transporters (savedLang = null → skip).
        // =====================================================================
        val prefs = getSharedPreferences("weelo_prefs", android.content.Context.MODE_PRIVATE)
        val startupLang = RoleScopedLocalePolicy.resolveStartupLocale(prefs)
        val locale = java.util.Locale(startupLang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        timber.log.Timber.i("🌐 Application locale initialized (role-scoped): $startupLang")
        
        // Initialize Coil ImageLoader with disk cache
        coil.Coil.setImageLoader(
            com.weelo.logistics.core.image.ImageLoaderConfig.getInstance(this)
        )
        
        // Initialize RetrofitClient with application context
        // This sets up secure token storage and API client
        RetrofitClient.init(this)

        // Broadcast feature flags + coordinator runtime owner (phased rollout; debug/internal enabled by default)
        BroadcastFeatureFlagsRegistry.initialize(this)
        val broadcastFlags = BroadcastFeatureFlagsRegistry.current()
        timber.log.Timber.i(
            "📡 Broadcast flags coordinator=%s localDelta=%s reconcileRateLimit=%s strictId=%s overlayInvariant=%s disableLegacySocket=%s",
            broadcastFlags.broadcastCoordinatorEnabled,
            broadcastFlags.broadcastLocalDeltaApplyEnabled,
            broadcastFlags.broadcastReconcileRateLimitEnabled,
            broadcastFlags.broadcastStrictIdValidationEnabled,
            broadcastFlags.broadcastOverlayInvariantEnforcementEnabled,
            broadcastFlags.broadcastDisableLegacyWebsocketPath
        )
        BroadcastFlowCoordinator.initialize(this)
        BroadcastFlowCoordinator.start()
        
        // Initialize HeartbeatManager for geolocation tracking
        HeartbeatManager.initialize(this)
        
        // Log API configuration
        Constants.API.logConfiguration()
        
        // Connect to WebSocket if user is logged in
        connectWebSocketIfLoggedIn()

        // Initialize FCM token cache early to improve closed-app push reliability
        initializeFcmToken()
        
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
            
            if (token.isNullOrEmpty() || (role != "transporter" && role != "driver")) {
                timber.log.Timber.w("⏭️ Skipping WebSocket connection:")
                timber.log.Timber.w("   - Token empty: ${token.isNullOrEmpty()}")
                timber.log.Timber.w("   - Role: $role (needs 'transporter' or 'driver')")
                return@launch
            }
            
            // Retry with exponential backoff (3 attempts: 2s, 4s, 8s)
            // CRITICAL: If WebSocket fails on startup (network not ready),
            // transporters won't receive broadcasts until app restart without this.
            val maxRetries = 3
            val baseDelayMs = 2000L
            
            for (attempt in 1..maxRetries) {
                try {
                    timber.log.Timber.i("🔌 WebSocket connect attempt $attempt/$maxRetries (role: $role, user: $userId)")
                    SocketIOService.connect(Constants.API.WS_URL, token)
                    
                    // Wait briefly and check if connection succeeded
                    delay(1500)
                    if (SocketIOService.isConnected()) {
                        timber.log.Timber.i("✅ WebSocket connected on attempt $attempt")
                        return@launch
                    }
                    
                    // Connection didn't establish — retry with backoff
                    if (attempt < maxRetries) {
                        val backoffMs = baseDelayMs * (1L shl (attempt - 1)) // 2s, 4s, 8s
                        timber.log.Timber.w("⏳ WebSocket not connected, retrying in ${backoffMs}ms...")
                        delay(backoffMs)
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "❌ WebSocket connect attempt $attempt failed: ${e.message}")
                    if (attempt < maxRetries) {
                        val backoffMs = baseDelayMs * (1L shl (attempt - 1))
                        delay(backoffMs)
                    }
                }
            }
            
            timber.log.Timber.e("❌ WebSocket failed after $maxRetries attempts — will retry on next app foreground")
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
                
                if (!token.isNullOrEmpty() && role == "transporter") {
                    val availabilityState = AvailabilityManager.getInstance(this@WeeloApp).availabilityState.value
                    if (availabilityState == AvailabilityState.ONLINE) {
                        timber.log.Timber.i("💓 Starting transporter online service (availability=ONLINE)")
                        TransporterOnlineService.start(this@WeeloApp)
                    } else {
                        timber.log.Timber.i("⏭️ Skipping heartbeat - transporter availability is $availabilityState")
                        TransporterOnlineService.stop(this@WeeloApp)
                    }
                } else {
                    timber.log.Timber.d("⏭️ Skipping heartbeat - not logged in as transporter")
                    TransporterOnlineService.stop(this@WeeloApp)
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
        TransporterOnlineService.stop(this)
        HeartbeatManager.stop()
    }
    
    /**
     * Full logout - disconnects WebSocket and stops heartbeat
     */
    fun logout() {
        applicationScope.launch {
            NotificationTokenSync.unregisterCurrentToken(reason = "app_logout")
        }
        stopHeartbeat()
        disconnectWebSocket()
        BroadcastFlowCoordinator.stop()
        RetrofitClient.clearAllData()
        timber.log.Timber.i("👋 User logged out, all services stopped")
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build()
        )
        timber.log.Timber.d("StrictMode enabled (debug)")
    }

    private fun initializeFcmToken() {
        WeeloFirebaseService.onTokenRefresh = { token ->
            WeeloFirebaseService.cacheToken(this, token)
            timber.log.Timber.i("🔑 FCM token refreshed and cached")
            applicationScope.launch {
                val isLoggedIn = RetrofitClient.isLoggedIn()
                val role = RetrofitClient.getUserRole()
                if (!isLoggedIn || (role != "transporter" && role != "driver")) {
                    return@launch
                }

                NotificationTokenSync.registerCurrentToken(reason = "fcm_token_refresh")

                if (SocketIOService.isConnected()) {
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.SOCKET_AUTH,
                        status = BroadcastStatus.SUCCESS,
                        reason = "fcm_refresh_triggered_socket_reconnect",
                        attrs = mapOf("role" to role)
                    )
                    timber.log.Timber.i("🔄 Reconnecting socket to propagate refreshed FCM token")
                    SocketIOService.reconnect()
                } else {
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.SOCKET_AUTH,
                        status = BroadcastStatus.SKIPPED,
                        reason = "socket_not_connected_on_fcm_refresh",
                        attrs = mapOf("role" to role)
                    )
                }
            }
        }

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    WeeloFirebaseService.cacheToken(this, token)
                    timber.log.Timber.i("🔑 FCM token initialized")
                    applicationScope.launch {
                        NotificationTokenSync.registerCurrentToken(reason = "fcm_token_init")
                    }
                }
            }
            .addOnFailureListener { error ->
                timber.log.Timber.w("⚠️ Failed to initialize FCM token: ${error.message}")
            }
    }
}
