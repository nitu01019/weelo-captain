package com.weelo.logistics

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.weelo.logistics.broadcast.BroadcastOverlayManager
import com.weelo.logistics.broadcast.BroadcastOverlayScreen
import com.weelo.logistics.ui.navigation.Screen
import com.weelo.logistics.ui.navigation.WeeloNavigation
import com.weelo.logistics.ui.theme.WeeloTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * Main Activity - Hosts the main app navigation
 * 
 * RAPIDO-STYLE BROADCAST OVERLAY:
 * - BroadcastOverlayScreen is placed at root level
 * - When WebSocket receives broadcast, overlay shows over ANY screen
 * - Accept navigates to TruckSelectionScreen
 * - Reject dismisses and shows next in queue
 * 
 * Launched from SplashActivity with login info
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get login info from SplashActivity
        val isLoggedIn = intent.getBooleanExtra("IS_LOGGED_IN", false)
        val userRole = intent.getStringExtra("USER_ROLE")
        
        Log.i(TAG, "🚀 MainActivity started - isLoggedIn: $isLoggedIn, role: $userRole")
        
        setContent {
            WeeloTheme {
                // Remember navController at root level for overlay navigation
                val navController = rememberNavController()
                
                // Listen for broadcast events to handle navigation
                LaunchedEffect(Unit) {
                    BroadcastOverlayManager.broadcastEvents.collectLatest { event ->
                        when (event) {
                            is BroadcastOverlayManager.BroadcastEvent.Accepted -> {
                                Log.i(TAG, "✅ Broadcast accepted: ${event.broadcast.broadcastId}")
                                // Navigation will be handled by onAccept callback
                            }
                            is BroadcastOverlayManager.BroadcastEvent.Rejected -> {
                                Log.i(TAG, "❌ Broadcast rejected: ${event.broadcast.broadcastId}")
                            }
                            is BroadcastOverlayManager.BroadcastEvent.Expired -> {
                                Log.w(TAG, "⏰ Broadcast expired: ${event.broadcast.broadcastId}")
                            }
                            is BroadcastOverlayManager.BroadcastEvent.NewBroadcast -> {
                                Log.i(TAG, "📢 New broadcast queued: ${event.broadcast.broadcastId} (position: ${event.queuePosition})")
                            }
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Box to layer overlay on top of navigation
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Main Navigation
                        WeeloNavigation(
                            isLoggedIn = isLoggedIn,
                            userRole = userRole,
                            navController = navController
                        )
                        
                        // Broadcast Overlay (Rapido-style popup)
                        // This will show OVER any screen when a broadcast arrives
                        BroadcastOverlayScreen(
                            onAccept = { broadcast ->
                                Log.i(TAG, "🎯 Navigating to truck selection for broadcast: ${broadcast.broadcastId}")
                                // Navigate to truck selection screen
                                navController.navigate(Screen.TruckSelection.createRoute(broadcast.broadcastId))
                            },
                            onReject = { broadcast ->
                                Log.i(TAG, "❌ User rejected broadcast: ${broadcast.broadcastId}")
                                // Overlay manager will show next broadcast automatically
                            }
                        )
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clear broadcasts when activity is destroyed
        BroadcastOverlayManager.clearAll()
    }
}
