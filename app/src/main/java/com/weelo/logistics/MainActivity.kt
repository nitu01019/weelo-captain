package com.weelo.logistics

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.weelo.logistics.broadcast.BroadcastAcceptanceScreen
import com.weelo.logistics.broadcast.BroadcastOverlayManager
import com.weelo.logistics.broadcast.BroadcastOverlayScreen
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.navigation.Screen
import com.weelo.logistics.ui.navigation.WeeloNavigation
import com.weelo.logistics.ui.theme.WeeloTheme
import com.weelo.logistics.ui.viewmodel.MainViewModel
import com.weelo.logistics.utils.LocaleHelper
import kotlinx.coroutines.flow.collectLatest

/**
 * Main Activity - Hosts the main app navigation
 * 
 * RAPIDO-STYLE ARCHITECTURE:
 * - Single Activity with activity-scoped MainViewModel
 * - MainViewModel holds ALL app state (vehicles, drivers, trips)
 * - Screens only OBSERVE data - they never fetch
 * - Data is loaded ONCE at app startup
 * - Navigation NEVER waits for data
 * 
 * BROADCAST OVERLAY:
 * - BroadcastOverlayScreen is placed at root level
 * - When WebSocket receives broadcast, overlay shows over ANY screen
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // =========================================================================
    // RAPIDO-STYLE: Activity-scoped ViewModel (shared with all screens)
    // =========================================================================
    private val mainViewModel: MainViewModel by viewModels()
    
    /**
     * Apply saved language preference before activity is created
     * This ensures the app opens in the user's selected language
     * 
     * PERFORMANCE FIX: Using synchronous SharedPreferences instead of
     * runBlocking with DataStore to prevent blocking the main thread
     */
    override fun attachBaseContext(newBase: Context) {
        // PERFORMANCE: Use synchronous SharedPreferences read (non-blocking)
        // Previously used runBlocking which blocked the UI thread
        val prefs = newBase.getSharedPreferences("weelo_prefs", Context.MODE_PRIVATE)
        val savedLanguage = prefs.getString("preferred_language", null)
        
        // Apply language or default to English
        val languageCode = savedLanguage ?: "en"
        val context = LocaleHelper.setLocale(newBase, languageCode)
        super.attachBaseContext(context)
        
        timber.log.Timber.i("📱 Language applied: $languageCode")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // =========================================================================
        // RAPIDO-STYLE: Load ALL core data at app start (NOT on screen navigation)
        // =========================================================================
        mainViewModel.initializeAppData()
        
        // Check login status directly
        val isLoggedIn = RetrofitClient.isLoggedIn()
        val userRole = RetrofitClient.getUserRole()
        
        timber.log.Timber.i("🚀 MainActivity started - isLoggedIn: $isLoggedIn, role: $userRole")
        
        // Initialize BroadcastOverlayManager with context for availability checks
        BroadcastOverlayManager.initialize(this)
        
        setContent {
            WeeloTheme {
                // Remember navController at root level for overlay navigation
                val navController = rememberNavController()
                
                // ================================================================
                // STATE FOR ACCEPTANCE FLOW
                // ================================================================
                // When user accepts a broadcast, we show the acceptance screen
                // (truck selection + driver assignment) as an overlay
                // ================================================================
                var acceptedBroadcast by remember { mutableStateOf<BroadcastTrip?>(null) }
                var showAcceptanceScreen by remember { mutableStateOf(false) }
                
                // Listen for broadcast events to handle navigation
                LaunchedEffect(Unit) {
                    BroadcastOverlayManager.broadcastEvents.collectLatest { event ->
                        when (event) {
                            is BroadcastOverlayManager.BroadcastEvent.Accepted -> {
                                timber.log.Timber.i("✅ Broadcast accepted: ${event.broadcast.broadcastId}")
                                // Show acceptance screen overlay
                                acceptedBroadcast = event.broadcast
                                showAcceptanceScreen = true
                            }
                            is BroadcastOverlayManager.BroadcastEvent.Rejected -> {
                                timber.log.Timber.i("❌ Broadcast rejected: ${event.broadcast.broadcastId}")
                            }
                            is BroadcastOverlayManager.BroadcastEvent.Expired -> {
                                timber.log.Timber.w("⏰ Broadcast expired: ${event.broadcast.broadcastId}")
                            }
                            is BroadcastOverlayManager.BroadcastEvent.NewBroadcast -> {
                                timber.log.Timber.i("📢 New broadcast queued: ${event.broadcast.broadcastId} (position: ${event.queuePosition})")
                            }
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Box to layer overlays on top of navigation
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ============================================
                        // LAYER 1: Main Navigation
                        // ============================================
                        WeeloNavigation(
                            isLoggedIn = isLoggedIn,
                            userRole = userRole,
                            navController = navController
                        )
                        
                        // ============================================
                        // LAYER 2: Broadcast Overlay (Rapido-style)
                        // Shows when new broadcast arrives
                        // ============================================
                        BroadcastOverlayScreen(
                            onAccept = { broadcast ->
                                timber.log.Timber.i("🎯 User accepted broadcast: ${broadcast.broadcastId}")
                                // Store broadcast and show acceptance screen
                                acceptedBroadcast = broadcast
                                showAcceptanceScreen = true
                            },
                            onReject = { broadcast ->
                                timber.log.Timber.i("❌ User rejected broadcast: ${broadcast.broadcastId}")
                                // Overlay manager will show next broadcast automatically
                            }
                        )
                        
                        // ============================================
                        // LAYER 3: Acceptance Screen (Truck + Driver)
                        // Shows after user clicks "Accept" on broadcast
                        // ============================================
                        acceptedBroadcast?.let { broadcast ->
                            BroadcastAcceptanceScreen(
                                broadcast = broadcast,
                                isVisible = showAcceptanceScreen,
                                onDismiss = {
                                    timber.log.Timber.i("🔙 Acceptance screen dismissed")
                                    showAcceptanceScreen = false
                                    acceptedBroadcast = null
                                },
                                onSuccess = {
                                    timber.log.Timber.i("✅ Assignment successful!")
                                    showAcceptanceScreen = false
                                    acceptedBroadcast = null
                                    // Optionally navigate to trips or dashboard
                                }
                            )
                        }
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
