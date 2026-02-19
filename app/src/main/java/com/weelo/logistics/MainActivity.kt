package com.weelo.logistics

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    
    // =========================================================================
    // REACTIVE LOCALE STATE
    //
    // WHY: stringResource() reads from LocalContext.current.resources.
    // By wrapping the Compose tree with CompositionLocalProvider(LocalContext
    // provides localizedContext), changing localeCode triggers recomposition →
    // ALL stringResource() calls resolve to the new language INSTANTLY.
    //
    // No Activity.recreate(). No screen flash. No WebSocket reconnect.
    // Pure Compose state change → recomposition → done.
    //
    // ROLE ISOLATION: Only drivers call updateLocale(). Transporters never
    // write preferred_language, so localeCode stays "en" (system default).
    //
    // SCALABILITY: O(1) state change. No network call. Compiled resource lookup.
    // =========================================================================
    internal var localeCode by mutableStateOf("en")
        private set
    
    /**
     * Update the app locale INSTANTLY without Activity restart.
     *
     * Called from:
     * - LanguageSelectionScreen (first-time onboarding)
     * - DriverSettingsScreen (language change bottom sheet)
     * - OTPVerificationScreen (re-login with backend language)
     *
     * THREAD SAFETY: Must be called on Main thread (Compose state).
     * PERFORMANCE: O(1) — single state assignment triggers recomposition.
     */
    fun updateLocale(langCode: String) {
        // 1. Update Compose state → triggers recomposition of entire tree
        localeCode = langCode
        
        // 2. Update JVM default locale (for non-Compose code: Timber, services, etc.)
        val locale = java.util.Locale(langCode)
        java.util.Locale.setDefault(locale)
        
        // 3. Update Activity resources (for any code using activity context directly)
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        
        timber.log.Timber.i("🌐 Locale updated instantly: $langCode (no recreate)")
    }
    
    /**
     * Apply saved language preference before activity is created.
     * This ensures the app opens in the user's selected language on COLD START.
     * 
     * PERFORMANCE: Synchronous SharedPreferences read (non-blocking, ~1ms).
     */
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("weelo_prefs", Context.MODE_PRIVATE)
        val savedLanguage = prefs.getString("preferred_language", null)
        
        val languageCode = savedLanguage ?: "en"
        val context = LocaleHelper.setLocale(newBase, languageCode)
        super.attachBaseContext(context)
        
        timber.log.Timber.i("📱 attachBaseContext language: $languageCode")
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
        
        // =====================================================================
        // Initialize localeCode from SharedPreferences (same source as
        // attachBaseContext). This ensures the reactive Compose state matches
        // the locale that attachBaseContext already applied.
        //
        // MUST happen BEFORE setContent so the first Compose frame uses
        // the correct locale context.
        // =====================================================================
        val langPrefs = getSharedPreferences("weelo_prefs", Context.MODE_PRIVATE)
        val savedLang = langPrefs.getString("preferred_language", null)
        if (!savedLang.isNullOrEmpty()) {
            localeCode = savedLang
        }
        
        setContent {
            WeeloTheme {
                // =============================================================
                // REACTIVE LOCALE CONTEXT
                //
                // Creates a locale-aware Context from the current localeCode.
                // When localeCode changes (via updateLocale()), this
                // recomputes → CompositionLocalProvider re-provides it →
                // ALL stringResource() and context.getString() calls
                // beneath this point resolve to the new language INSTANTLY.
                //
                // KEY: remember(localeCode) ensures a new Context is created
                // only when the language actually changes (not on every frame).
                //
                // PERFORMANCE: createConfigurationContext() is ~0.5ms.
                // Called at most once per language change (not per frame).
                // =============================================================
                val localizedContext = remember(localeCode) {
                    LocaleHelper.setLocale(this@MainActivity, localeCode)
                }
                
                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalActivityResultRegistryOwner provides this@MainActivity
                ) {
                // Remember navController at root level for overlay navigation
                val navController = rememberNavController()
                
                // ================================================================
                // STATE FOR ACCEPTANCE FLOW
                // ================================================================
                var acceptedBroadcast by remember { mutableStateOf<BroadcastTrip?>(null) }
                var showAcceptanceScreen by remember { mutableStateOf(false) }
                
                // Listen for broadcast events to handle navigation
                LaunchedEffect(Unit) {
                    BroadcastOverlayManager.broadcastEvents.collectLatest { event ->
                        when (event) {
                            is BroadcastOverlayManager.BroadcastEvent.Accepted -> {
                                timber.log.Timber.i("✅ Broadcast accepted: ${event.broadcast.broadcastId}")
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
                        // ============================================
                        BroadcastOverlayScreen(
                            onAccept = { broadcast ->
                                timber.log.Timber.i("🎯 User accepted broadcast: ${broadcast.broadcastId}")
                                acceptedBroadcast = broadcast
                                showAcceptanceScreen = true
                            },
                            onReject = { broadcast ->
                                timber.log.Timber.i("❌ User rejected broadcast: ${broadcast.broadcastId}")
                            }
                        )
                        
                        // ============================================
                        // LAYER 3: Acceptance Screen (Truck + Driver)
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
                                }
                            )
                        }
                    }
                }
                } // End CompositionLocalProvider
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clear broadcasts when activity is destroyed
        BroadcastOverlayManager.clearAll()
    }
}
