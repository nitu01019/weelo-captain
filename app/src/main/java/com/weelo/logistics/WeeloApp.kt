package com.weelo.logistics

import android.app.Application
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.utils.Constants
import com.weelo.logistics.utils.HeartbeatManager
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
        val savedLang = prefs.getString("preferred_language", null)
        if (!savedLang.isNullOrEmpty()) {
            val locale = java.util.Locale(savedLang)
            java.util.Locale.setDefault(locale)
            val config = android.content.res.Configuration(resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
            timber.log.Timber.i("🌐 Application locale initialized: $savedLang")
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
