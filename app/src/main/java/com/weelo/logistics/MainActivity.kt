package com.weelo.logistics

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.weelo.logistics.broadcast.BroadcastAcceptanceScreen
import com.weelo.logistics.broadcast.BroadcastFeatureFlagsRegistry
import com.weelo.logistics.broadcast.BroadcastFlowCoordinator
import com.weelo.logistics.broadcast.BroadcastOverlayManager
import com.weelo.logistics.broadcast.BroadcastOverlayScreen
import com.weelo.logistics.broadcast.BroadcastRecoveryScheduler
import com.weelo.logistics.broadcast.BroadcastRecoveryTracker
import com.weelo.logistics.broadcast.BroadcastRolePolicy
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastStateSync
import com.weelo.logistics.broadcast.BroadcastTelemetry
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.remote.BroadcastNotification
import com.weelo.logistics.data.remote.NotificationTokenSync
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.WeeloFirebaseService
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.ui.navigation.Screen
import com.weelo.logistics.ui.navigation.WeeloNavigation
import com.weelo.logistics.ui.theme.WeeloTheme
import com.weelo.logistics.ui.viewmodel.MainViewModel
import com.weelo.logistics.utils.LocaleHelper
import com.weelo.logistics.utils.RoleScopedLocalePolicy
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        private const val REQ_POST_NOTIFICATIONS = 2101
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
    private var lastHandledNotificationKey: String? = null
    private var batteryGuidanceDialogVisible = false

    /**
     * Hard reset task after logout so system back / recents cannot reopen a
     * protected dashboard that belonged to the previous authenticated task.
     */
    fun restartAsLoggedOutTask() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
    
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
        val languageCode = RoleScopedLocalePolicy.resolveStartupLocale(prefs)
        val context = LocaleHelper.setLocale(newBase, languageCode)
        super.attachBaseContext(context)
        
        timber.log.Timber.i("📱 attachBaseContext language: $languageCode")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        
        // =========================================================================
        // RAPIDO-STYLE: Load ALL core data at app start (NOT on screen navigation)
        // =========================================================================
        mainViewModel.initializeAppData()
        
        // Initial snapshot only — reactive auth state is collected in Compose below.
        val initialIsLoggedIn = RetrofitClient.isLoggedIn()
        val initialUserRole = RetrofitClient.getUserRole()
        
        timber.log.Timber.i("🚀 MainActivity started - isLoggedIn: $initialIsLoggedIn, role: $initialUserRole")
        
        // Initialize BroadcastOverlayManager with context for availability checks
        BroadcastOverlayManager.initialize(this)
        scheduleBroadcastRecovery(trigger = "main_activity_on_create")
        handleNotificationIntent(intent)
        
        // =====================================================================
        // Initialize localeCode from SharedPreferences (same source as
        // attachBaseContext). This ensures the reactive Compose state matches
        // the locale that attachBaseContext already applied.
        //
        // MUST happen BEFORE setContent so the first Compose frame uses
        // the correct locale context.
        // =====================================================================
        val langPrefs = getSharedPreferences("weelo_prefs", Context.MODE_PRIVATE)
        localeCode = RoleScopedLocalePolicy.resolveStartupLocale(langPrefs)
        
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
                val lifecycleOwner = LocalLifecycleOwner.current
                // Remember navController at root level for overlay navigation
                val navController = rememberNavController()
                val isLoggedIn by RetrofitClient.authState.collectAsStateWithLifecycle(initialValue = initialIsLoggedIn)
                val userRole = if (isLoggedIn) RetrofitClient.getUserRole() else null

                LaunchedEffect(isLoggedIn, userRole) {
                    if (isLoggedIn && (userRole == "transporter" || userRole == "driver")) {
                        NotificationTokenSync.registerCurrentToken(reason = "auth_state_logged_in")
                    }
                }
                
                // ================================================================
                // STATE FOR ACCEPTANCE FLOW
                // ================================================================
                var acceptedBroadcast by remember { mutableStateOf<BroadcastTrip?>(null) }
                var showAcceptanceScreen by remember { mutableStateOf(false) }

                // Listen for broadcast events to handle navigation
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
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
                }

                // ================================================================
                // CROSS-ACTIVITY ACCEPT HANDOFF (BroadcastIncomingActivity → here)
                //
                // When the transporter accepts from the full-screen incoming overlay,
                // BroadcastIncomingActivity stores the broadcast in BroadcastStateSync
                // BEFORE calling finish(). We observe that here and immediately open
                // the acceptance screen — without going through the overlay/event path
                // that gets cleared by BroadcastOverlayManager.clearAll().
                // ================================================================
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        BroadcastStateSync.pendingAccept.collect { pending ->
                            if (pending != null && !showAcceptanceScreen) {
                                timber.log.Timber.i("🎯 [PendingAccept] Opening acceptance screen for: ${pending.broadcastId}")
                                val consumed = BroadcastStateSync.consumePendingAccept()
                                if (consumed != null) {
                                    acceptedBroadcast = consumed
                                    showAcceptanceScreen = true
                                }
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
                            navController = navController,
                            mainViewModel = mainViewModel,
                            onAcceptFromList = { broadcast ->
                                timber.log.Timber.i("🎯 [List→Accept] broadcast: ${broadcast.broadcastId}")
                                acceptedBroadcast = broadcast
                                showAcceptanceScreen = true
                            }
                        )
                        
                        // ============================================
                        // LAYER 2: Broadcast Overlay (Rapido-style)
                        // Suppressed when user is on Requests screen
                        // (BroadcastListScreen handles display directly)
                        // ============================================
                        val currentEntry by navController.currentBackStackEntryAsState()
                        val isOnRequestsScreen = currentEntry?.destination?.route == Screen.BroadcastList.route
                        if (!isOnRequestsScreen) {
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
                        }
                        
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
                                onSuccess = { assignmentId ->
                                    timber.log.Timber.i("✅ Assignment successful! assignmentId=$assignmentId")
                                    // Cross-screen sync: mark as fully accepted so
                                    // Request Screen removes it immediately
                                    broadcast.broadcastId.let { id ->
                                        BroadcastStateSync.markFullyAccepted(id)
                                        BroadcastOverlayManager.removeEverywhere(id)
                                    }
                                    showAcceptanceScreen = false
                                    acceptedBroadcast = null
                                    
                                    // FIX: Navigate to TripStatusManagement ("Waiting for driver" screen)
                                    // BEFORE: Just closed dialog → user had no visibility into driver response
                                    // NOW: Shows real-time driver acceptance status
                                    if (assignmentId != null) {
                                        navController.navigate(
                                            Screen.TripStatusManagement.createRoute(assignmentId)
                                        ) {
                                            popUpTo(Screen.TransporterDashboard.route) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                } // End CompositionLocalProvider
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scheduleBroadcastRecovery(trigger = "main_activity_on_resume")
        maybeShowBatteryOptimizationGuidance()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_POST_NOTIFICATIONS
            )
        }
    }

    private fun handleNotificationIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val rawType = incomingIntent.getStringExtra("notification_type") ?: return
        val broadcastId = incomingIntent.getStringExtra("broadcast_id") ?: return
        if (broadcastId.isBlank()) return
        if (!BroadcastRolePolicy.canHandleBroadcastIngress(RetrofitClient.getUserRole())) return

        val type = when (rawType.trim().lowercase()) {
            WeeloFirebaseService.TYPE_NEW_TRUCK_REQUEST -> WeeloFirebaseService.TYPE_NEW_BROADCAST
            else -> rawType.trim().lowercase()
        }

        // =====================================================================
        // ACCEPT_BROADCAST: User already accepted from BroadcastIncomingActivity.
        // The broadcast is stored in BroadcastStateSync.pendingAccept.
        // The Compose LaunchedEffect above observes it and opens the acceptance
        // screen. No need to fetch from API or re-show the overlay.
        // =====================================================================
        if (type == "accept_broadcast") {
            timber.log.Timber.i("🎯 [MainActivity] accept_broadcast intent received: id=%s", broadcastId)
            // Compose observer on BroadcastStateSync.pendingAccept handles the rest.
            // Just log — no action needed here.
            return
        }

        if (type != WeeloFirebaseService.TYPE_NEW_BROADCAST) return

        val key = "$type|$broadcastId"
        if (lastHandledNotificationKey == key) return
        lastHandledNotificationKey = key

        if (BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
            BroadcastFlowCoordinator.ingestNotificationOpen(broadcastId)
            return
        }

        lifecycleScope.launch {
            when (val result = BroadcastRepository.getInstance(this@MainActivity).getBroadcastById(broadcastId)) {
                is BroadcastResult.Success -> {
                    val ingress = BroadcastOverlayManager.showBroadcast(result.data)
                    timber.log.Timber.i(
                        "📲 Notification-open broadcast handled: id=%s action=%s reason=%s",
                        broadcastId,
                        ingress.action.name,
                        ingress.reason
                    )
                }

                is BroadcastResult.Error -> {
                    timber.log.Timber.w(
                        "⚠️ Failed to fetch broadcast from notification: id=%s reason=%s",
                        broadcastId,
                        result.message
                    )
                }

                is BroadcastResult.Loading -> Unit
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Do not clear on config change/process recreation; only clear when task is finishing.
        if (isFinishing) {
            BroadcastOverlayManager.clearAll()
        }
    }

    private fun scheduleBroadcastRecovery(trigger: String) {
        if (!RetrofitClient.isLoggedIn()) return
        if (!BroadcastRolePolicy.canHandleBroadcastIngress(RetrofitClient.getUserRole())) return
        BroadcastRecoveryScheduler.schedule(this, trigger)
    }

    private fun maybeShowBatteryOptimizationGuidance() {
        if (batteryGuidanceDialogVisible) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (!RetrofitClient.isLoggedIn()) return
        if (!BroadcastRolePolicy.canHandleBroadcastIngress(RetrofitClient.getUserRole())) return
        if (!BroadcastRecoveryTracker.shouldShowBatteryGuidance(this)) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val alreadyWhitelisted = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        if (alreadyWhitelisted) {
            BroadcastRecoveryTracker.markBatteryGuidanceShown(this)
            return
        }

        batteryGuidanceDialogVisible = true
        AlertDialog.Builder(this)
            .setTitle("Improve Broadcast Reliability")
            .setMessage("Battery optimization may delay new broadcast alerts. Allow background optimization exemption for more reliable notifications.")
            .setPositiveButton("Open Settings") { _, _ ->
                BroadcastRecoveryTracker.markBatteryGuidanceShown(this)
                batteryGuidanceDialogVisible = false
                runCatching {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BATTERY_OPTIMIZATION_GUIDANCE,
                    status = BroadcastStatus.SUCCESS,
                    reason = "settings_opened"
                )
            }
            .setNegativeButton("Not Now") { _, _ ->
                BroadcastRecoveryTracker.markBatteryGuidanceShown(this)
                batteryGuidanceDialogVisible = false
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BATTERY_OPTIMIZATION_GUIDANCE,
                    status = BroadcastStatus.SKIPPED,
                    reason = "user_deferred"
                )
            }
            .setOnDismissListener {
                batteryGuidanceDialogVisible = false
            }
            .show()
    }
}
