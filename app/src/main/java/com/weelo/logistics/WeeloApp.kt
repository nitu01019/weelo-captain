package com.weelo.logistics

import android.app.Application
import android.util.Log
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.utils.Constants
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
        
        // Initialize RetrofitClient with application context
        // This sets up secure token storage and API client
        RetrofitClient.init(this)
        
        // Log API configuration
        Constants.API.logConfiguration()
        
        // Connect to WebSocket if user is logged in
        connectWebSocketIfLoggedIn()
    }
    
    /**
     * Connect to WebSocket if user is already logged in
     * This ensures broadcasts are received even if BroadcastListScreen isn't open
     */
    fun connectWebSocketIfLoggedIn() {
        applicationScope.launch {
            try {
                val token = RetrofitClient.getAccessToken()
                val role = RetrofitClient.getUserRole()
                
                if (!token.isNullOrEmpty() && (role == "transporter" || role == "driver")) {
                    Log.i(TAG, "🔌 Auto-connecting to WebSocket (role: $role)")
                    SocketIOService.connect(Constants.API.WS_URL, token)
                } else {
                    Log.d(TAG, "⏭️ Skipping WebSocket - not logged in or not transporter/driver")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to auto-connect WebSocket: ${e.message}")
            }
        }
    }
    
    /**
     * Disconnect WebSocket (call on logout)
     */
    fun disconnectWebSocket() {
        Log.i(TAG, "🔌 Disconnecting WebSocket")
        SocketIOService.disconnect()
    }
}
