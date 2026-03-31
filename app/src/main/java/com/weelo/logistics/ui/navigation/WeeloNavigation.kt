package com.weelo.logistics.ui.navigation

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.weelo.logistics.ui.auth.*
import com.weelo.logistics.ui.driver.DriverProfileViewModel
import com.weelo.logistics.ui.driver.DriverProfileScreenWithPhotos
import com.weelo.logistics.ui.driver.DriverTripRequestOverlay
import com.weelo.logistics.ui.driver.DriverTripRequestManager
import com.weelo.logistics.ui.driver.ActionState
import com.weelo.logistics.ui.driver.DriverTripNavigationScreen
import com.weelo.logistics.ui.viewmodel.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.remote.WeeloFirebaseService
import com.weelo.logistics.data.model.TripAssignedNotification
import com.weelo.logistics.data.model.TripLocationInfo
import com.weelo.logistics.ui.utils.SocketUiEventDeduper

private const val FF_STRICT_LOGOUT_OFFLINE_ENFORCEMENT = true

/**
 * Find MainActivity from any Context by walking up the ContextWrapper chain.
 *
 * WHY: LocalContext.current inside the NavHost returns a locale-wrapped context
 * from CompositionLocalProvider(LocalContext provides localizedContext).
 * This context is created by createConfigurationContext() which does NOT
 * wrap the original Activity — it creates a standalone ContextWrapper.
 * So (context as? MainActivity) returns null.
 *
 * LocalView.current.context always returns the real Activity context,
 * but navController.context may also be wrapped. This utility handles all cases.
 */
private fun Context.findMainActivity(): com.weelo.logistics.MainActivity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is com.weelo.logistics.MainActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * ============================================================================
 * NAVIGATION HELPERS - Smooth & Scalable Navigation
 * ============================================================================
 *
 * SCALABILITY:
 * - launchSingleTop prevents duplicate destinations
 * - restoreState keeps screen state across back/forward
 * - saveState avoids reloading screens (smooth like Instagram)
 *
 * MODULARITY:
 * - Centralized navigation options
 * - Easy to reuse across driver + transporter flows
 *
 * CODING STANDARDS:
 * - Clear naming
 * - Documented behavior
 * ============================================================================
 */
private fun NavHostController.navigateSmooth(
    route: String,
    popUpToRoute: String? = null,
    inclusive: Boolean = false,
    restoreState: Boolean = true,
    saveStateOnPopUpTo: Boolean = restoreState && !inclusive
) {
    try {
        navigate(route) {
            launchSingleTop = true
            this.restoreState = restoreState
            if (popUpToRoute != null) {
                popUpTo(popUpToRoute) {
                    saveState = saveStateOnPopUpTo
                    this.inclusive = inclusive
                }
            }
        }
    } catch (e: IllegalArgumentException) {
        // Guard: Prevents crash when navigation destination is not found
        // (e.g., empty route segment like otp_verification//DRIVER/login)
        Timber.e(e, "❌ Navigation failed for route: $route")
    }
}

private fun isAuthPublicRoute(route: String?): Boolean {
    if (route.isNullOrBlank()) return false
    return route == Screen.RoleSelection.route ||
        route == Screen.Login.route ||
        route == Screen.Signup.route ||
        route == Screen.Onboarding.route ||
        route.startsWith("login") ||
        route.startsWith("signup") ||
        route.startsWith("otp_verification")
}

/**
 * Perform full logout cleanup — clears tokens, preferences, locale, and navigates to role selection.
 *
 * EXTRACTED: Single source of truth for logout. Called from Dashboard logout AND Settings logout.
 * Prevents divergence where one path is updated and the other is not.
 *
 * @param navController Navigation controller for redirect
 * @param coroutineScope Scoped coroutine for async DataStore clear (prevents MainScope leak)
 */
private fun performLogout(navController: NavHostController, coroutineScope: CoroutineScope) {
    val context = navController.context
    val mainActivity = context.findMainActivity()
    val driverPrefs = com.weelo.logistics.data.preferences.DriverPreferences.getInstance(context)
    val appInstance = com.weelo.logistics.WeeloApp.getInstance()

    coroutineScope.launch {
        // 1. Unified strict logout contract: unregister token -> stop heartbeat/offline -> socket disconnect -> clear auth data.
        var strictLogoutFailed = false
        if (FF_STRICT_LOGOUT_OFFLINE_ENFORCEMENT) {
            runCatching { appInstance?.logout() }
                .onFailure {
                    strictLogoutFailed = true
                    Timber.w(it, "⚠️ App logout pipeline failed, falling back to local clear")
                }
        } else {
            com.weelo.logistics.data.remote.RetrofitClient.clearAllData()
        }

        if (appInstance == null || strictLogoutFailed) {
            com.weelo.logistics.data.remote.RetrofitClient.clearAllData()
        }

        // 2. Clear driver prefs and language/profile flags before redirecting
        runCatching { driverPrefs.clearAll() }
            .onFailure { Timber.w(it, "⚠️ DriverPreferences clear failed during logout") }

        withContext(Dispatchers.Main) {
            // 3. Reset locale for auth screens
            mainActivity?.updateLocale("en")

            // 4. Clear current nav graph stack immediately (no state restore)
            try {
                navController.navigate(Screen.RoleSelection.route) {
                    popUpTo(navController.graph.id) {
                        inclusive = true
                        saveState = false
                    }
                    launchSingleTop = true
                    restoreState = false
                }
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "❌ Logout navigation failed")
            }

            // 5. Hard-reset task so Android back/recents cannot reopen protected screens
            mainActivity?.restartAsLoggedOutTask()
        }
    }
}

/**
 * Main Navigation component
 * Sets up the navigation graph for the entire app
 * 
 * @param isLoggedIn - from SplashActivity, true if user has valid token
 * @param userRole - from SplashActivity, the user's role (TRANSPORTER/DRIVER)
 */
@Composable
fun WeeloNavigation(
    navController: NavHostController = rememberNavController(),
    isLoggedIn: Boolean = false,
    userRole: String? = null,
    mainViewModel: MainViewModel,
    onAcceptFromList: (com.weelo.logistics.data.model.BroadcastTrip) -> Unit = {}
) {
    // Scoped coroutine for logout cleanup (prevents MainScope leak)
    val logoutScope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // =========================================================================
    // DRIVER TRIP REQUEST OVERLAY — Show when driver receives trip assignment
    // =========================================================================
    val isDriver = userRole?.uppercase() == "DRIVER"
    val isOverlayVisible by DriverTripRequestManager.isOverlayVisible.collectAsState()
    val currentRequest by DriverTripRequestManager.currentRequest.collectAsState()
    val actionState by DriverTripRequestManager.actionState.collectAsState()

    // =========================================================================
    // APP-LEVEL TRIP REQUEST COLLECTION (Industry Standard: Uber/Ola Pattern)
    // =========================================================================
    // WHY HERE: WeeloNavigation is composed for the ENTIRE driver session.
    // Unlike DriverDashboardScreen which is destroyed on navigation away,
    // these collectors survive across all screens (Settings, Profile, etc.)
    //
    // DEDUP: DriverTripRequestManager.addRequest() rejects duplicate assignmentIds.
    // SocketUiEventDeduper provides a second layer. Zero risk of double overlay.
    //
    // RESEARCH: Google recommends app-level collection for mission-critical
    // real-time events. Screen-level collection is for screen-specific UI.
    // =========================================================================
    if (isDriver) {
        val driverLifecycleOwner = LocalLifecycleOwner.current
        val appEventDeduper = remember { SocketUiEventDeduper(maxEntries = 64) }

        // Socket.IO path: Primary delivery channel
        LaunchedEffect(driverLifecycleOwner) {
            driverLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SocketIOService.tripAssigned.collect { notification ->
                    val dedupeKey = "${notification.assignmentId}|${notification.assignedAt}"
                    if (!appEventDeduper.shouldHandle(dedupeKey)) {
                        Timber.d("⏭️ [NAV] Skipping replayed trip_assigned: $dedupeKey")
                        return@collect
                    }
                    Timber.i("🚛 [NAV] Trip assigned: ${notification.assignmentId} (${notification.vehicleNumber})")
                    DriverTripRequestManager.addRequest(notification)
                }
            }
        }

        // FCM path: Backup delivery when Socket.IO missed the event
        LaunchedEffect(driverLifecycleOwner) {
            driverLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WeeloFirebaseService.foregroundNotifications.collect { fcmNotification ->
                    if (fcmNotification.type == WeeloFirebaseService.TYPE_TRIP_ASSIGNED) {
                        val assignmentId = fcmNotification.assignmentId ?: return@collect
                        if (assignmentId.isBlank()) return@collect

                        val assignedAt = fcmNotification.data["assignedAt"] ?: ""
                        val dedupeKey = "$assignmentId|$assignedAt"
                        if (!appEventDeduper.shouldHandle(dedupeKey)) {
                            Timber.d("⏭️ [NAV] Skipping duplicate FCM trip_assigned: $dedupeKey")
                            return@collect
                        }

                        Timber.i("📱 [NAV] FCM Trip assigned: $assignmentId")

                        val tripAssigned = TripAssignedNotification(
                            assignmentId = assignmentId,
                            tripId = fcmNotification.tripId ?: fcmNotification.data["tripId"] ?: "",
                            orderId = fcmNotification.data["orderId"] ?: "",
                            truckRequestId = fcmNotification.data["truckRequestId"] ?: "",
                            pickup = TripLocationInfo(
                                address = fcmNotification.data["pickupAddress"] ?: "",
                                city = fcmNotification.data["pickupCity"] ?: "",
                                latitude = fcmNotification.data["pickupLat"]?.toDoubleOrNull() ?: 0.0,
                                longitude = fcmNotification.data["pickupLng"]?.toDoubleOrNull() ?: 0.0
                            ),
                            drop = TripLocationInfo(
                                address = fcmNotification.data["dropAddress"] ?: "",
                                city = fcmNotification.data["dropCity"] ?: "",
                                latitude = fcmNotification.data["dropLat"]?.toDoubleOrNull() ?: 0.0,
                                longitude = fcmNotification.data["dropLng"]?.toDoubleOrNull() ?: 0.0
                            ),
                            vehicleNumber = fcmNotification.data["vehicleNumber"] ?: "",
                            farePerTruck = fcmNotification.data["fare"]?.toDoubleOrNull()
                                ?: fcmNotification.amount ?: 0.0,
                            distanceKm = fcmNotification.data["distanceKm"]?.toDoubleOrNull() ?: 0.0,
                            customerName = fcmNotification.data["customerName"] ?: "",
                            customerPhone = fcmNotification.data["customerPhone"] ?: "",
                            assignedAt = assignedAt,
                            expiresAt = fcmNotification.data["expiresAt"],
                            routePoints = null,
                            message = fcmNotification.body
                        )
                        DriverTripRequestManager.addRequest(tripAssigned)
                    }
                }
            }
        }
    }

    // Determine start destination based on login status
    // CRITICAL: Drivers ALWAYS go through onboarding check first
    // to ensure language is selected. Never skip to dashboard directly.
    val startDestination = if (isLoggedIn && userRole != null) {
        if (userRole.uppercase() == "TRANSPORTER") {
            Screen.TransporterDashboard.route
        } else {
            // DRIVER: Always check language/profile status first
            // driver_onboarding_check will redirect to dashboard if complete,
            // or force language selection if not set
            "driver_onboarding_check"
        }
    } else {
        Screen.RoleSelection.route
    }

    // Reactive auth guard: if token is cleared/expired while the app is open,
    // force redirect to auth and clear protected back stack.
    LaunchedEffect(isLoggedIn, currentRoute) {
        if (!isLoggedIn && currentRoute != null && !isAuthPublicRoute(currentRoute)) {
            Timber.w("🔒 Auth guard redirect from protected route: $currentRoute")
            try {
                navController.navigate(Screen.RoleSelection.route) {
                    popUpTo(navController.graph.id) {
                        inclusive = true
                        saveState = false
                    }
                    launchSingleTop = true
                    restoreState = false
                }
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "❌ Auth guard navigation failed")
            }
        }
    }

    // =========================================================================
    // LAYERED CONTENT — Overlay sits on top of all navigation
    // =========================================================================
    Box(modifier = Modifier.fillMaxSize()) {
        // Main navigation (bottom layer)
        NavHost(
        navController = navController,
        startDestination = startDestination,
        // Route-aware transitions reduce jank on heavy realtime/map screens
        enterTransition = {
            NavAnimations.enterForRoute(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route
            )
        },
        exitTransition = {
            NavAnimations.exitForRoute(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route
            )
        },
        popEnterTransition = {
            NavAnimations.popEnterForRoute(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route
            )
        },
        popExitTransition = {
            NavAnimations.popExitForRoute(
                initialRoute = initialState.destination.route,
                targetRoute = targetState.destination.route
            )
        }
    ) {

        // Onboarding screen removed - users go directly to role selection after splash
        
        composable(
            route = Screen.RoleSelection.route,
            enterTransition = { NavAnimations.fadeInWithScale },
            exitTransition = { NavAnimations.fadeOut }
        ) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    // Navigate to login screen for selected role
                    navController.navigateSmooth("${Screen.Login.route}/$role")
                }
            )
        }
        
        composable(
            "${Screen.Login.route}/{role}",
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "TRANSPORTER"
            LoginScreen(
                role = role,
                onNavigateToSignup = {
                    navController.navigateSmooth("${Screen.Signup.route}/$role")
                },
                onNavigateToOTP = { mobile, selectedRole ->
                    navController.navigateSmooth("otp_verification/$mobile/$selectedRole/login")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            "otp_verification/{mobile}/{role}/{type}",
            arguments = listOf(
                navArgument("mobile") { type = NavType.StringType },
                navArgument("role") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mobile = backStackEntry.arguments?.getString("mobile") ?: ""
            val role = backStackEntry.arguments?.getString("role") ?: "TRANSPORTER"
            val type = backStackEntry.arguments?.getString("type") ?: "login"
            OTPVerificationScreen(
                phoneNumber = mobile,
                role = role,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVerifySuccess = {
                    if (type == "signup") {
                        // Continue to name/location steps (handled in SignupScreen)
                        navController.navigateSmooth(
                            route = "${Screen.Signup.route}/$role/step2",
                            popUpToRoute = "otp_verification/{mobile}/{role}/{type}",
                            inclusive = true
                        )
                    } else {
                        // Login - check driver onboarding status
                        if (role == "TRANSPORTER") {
                            navController.navigateSmooth(
                                route = Screen.TransporterDashboard.route,
                                popUpToRoute = Screen.RoleSelection.route,
                                inclusive = false
                            )
                        } else {
                            // Driver: Check if language/profile setup needed
                            navController.navigateSmooth(
                                route = "driver_onboarding_check",
                                popUpToRoute = Screen.RoleSelection.route,
                                inclusive = false
                            )
                        }
                    }
                }
            )
        }
        
        composable(
            "${Screen.Signup.route}/{role}",
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "TRANSPORTER"
            SignupScreen(
                role = role,
                onNavigateToLogin = {
                    navController.navigateSmooth(
                        route = "${Screen.Login.route}/$role",
                        popUpToRoute = Screen.RoleSelection.route,
                        inclusive = false
                    )
                },
                onNavigateToOTP = { mobile, selectedRole, isSignup ->
                    val type = if (isSignup) "signup" else "login"
                    navController.navigateSmooth("otp_verification/$mobile/$selectedRole/$type")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Transporter Dashboard
        composable(Screen.TransporterDashboard.route) {
            com.weelo.logistics.ui.transporter.TransporterDashboardScreen(
                mainViewModel = mainViewModel,
                onNavigateToFleet = { navController.navigateSmooth(Screen.FleetList.route) },
                onNavigateToDrivers = { navController.navigateSmooth(Screen.DriverList.route) },
                onNavigateToTrips = { navController.navigateSmooth(Screen.TripList.route) },
                onNavigateToAddVehicle = { navController.navigateSmooth(Screen.AddVehicle.route) },
                onNavigateToAddDriver = { navController.navigateSmooth(Screen.AddDriver.route) },
                onNavigateToCreateTrip = { navController.navigateSmooth(Screen.CreateTrip.route) },
                onNavigateToProfile = { navController.navigateSmooth("transporter_profile") },
                onNavigateToBroadcasts = { navController.navigateSmooth(Screen.BroadcastList.route) },
                onNavigateToSettings = { navController.navigateSmooth(Screen.Settings.route) },
                onLogout = {
                    performLogout(navController, logoutScope)
                }
            )
        }
        
        // Transporter Profile Screen
        composable("transporter_profile") {
            com.weelo.logistics.ui.transporter.TransporterProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onProfileUpdated = { /* Refresh will happen automatically */ }
            )
        }

        composable(Screen.Settings.route) {
            com.weelo.logistics.ui.transporter.TransporterSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    performLogout(navController, logoutScope)
                }
            )
        }

        composable(Screen.BroadcastList.route) {
            com.weelo.logistics.ui.transporter.BroadcastListScreen(
                onNavigateBack = { navController.popBackStack() },
                onAccept = { broadcast ->
                    timber.log.Timber.i("🎯 [List] User accepted broadcast: ${broadcast.broadcastId}")
                    onAcceptFromList(broadcast)
                },
                onNavigateToSoundSettings = { navController.navigateSmooth(Screen.BroadcastSoundSettings.route) }
            )
        }
        
        // Broadcast Sound Settings
        composable(Screen.BroadcastSoundSettings.route) {
            com.weelo.logistics.ui.transporter.BroadcastSoundSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Truck Hold Confirmation Screen (15 second countdown)
        composable(Screen.TruckHoldConfirm.route) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
            val vehicleType = backStackEntry.arguments?.getString("vehicleType") ?: ""
            val vehicleSubtype = backStackEntry.arguments?.getString("vehicleSubtype")?.let { if (it == "_") "" else it } ?: ""
            val quantity = backStackEntry.arguments?.getString("quantity")?.toIntOrNull() ?: 1
            
            com.weelo.logistics.ui.transporter.TruckHoldConfirmScreen(
                orderId = orderId,
                vehicleType = vehicleType,
                vehicleSubtype = vehicleSubtype,
                quantity = quantity,
                onConfirmed = { holdId, resolvedVehicleType, resolvedVehicleSubtype, resolvedQuantity ->
                    // Navigate to driver assignment
                    navController.navigateSmooth(
                        route = Screen.DriverAssignment.createRoute(
                            broadcastId = orderId,
                            holdId = holdId,
                            vehicleType = resolvedVehicleType,
                            vehicleSubtype = resolvedVehicleSubtype,
                            quantity = resolvedQuantity
                        ),
                        popUpToRoute = Screen.TransporterDashboard.route
                    )
                },
                onCancelled = {
                    navController.popBackStack()
                }
            )
        }
        
        // Truck Selection Screen - Select trucks to assign to a broadcast
        composable(Screen.TruckSelection.route) { backStackEntry ->
            val broadcastId = backStackEntry.arguments?.getString("broadcastId") ?: ""
            com.weelo.logistics.ui.transporter.TruckSelectionScreen(
                broadcastId = broadcastId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDriverAssignment = { bId, vehicleIds ->
                    // Encode vehicle IDs as comma-separated string
                    val vehicleIdsParam = vehicleIds.joinToString(",")
                    navController.navigateSmooth(Screen.DriverAssignment.createLegacyRoute(bId, vehicleIdsParam))
                }
            )
        }
        
        // Driver Assignment Screen - Assign drivers to selected trucks
        composable(Screen.DriverAssignment.route) { backStackEntry ->
            val broadcastId = backStackEntry.arguments?.getString("broadcastId") ?: ""
            val holdId = backStackEntry.arguments?.getString("holdId")?.let { if (it == "_") "" else it } ?: ""
            val vehicleType = backStackEntry.arguments?.getString("vehicleType")?.let { if (it == "_") "" else it } ?: ""
            val vehicleSubtype = backStackEntry.arguments?.getString("vehicleSubtype")?.let { if (it == "_") "" else it } ?: ""
            val quantity = backStackEntry.arguments?.getString("quantity")?.toIntOrNull() ?: 1
            val vehicleIdsParam = backStackEntry.arguments?.getString("vehicleIds").orEmpty()
            val vehicleIds = vehicleIdsParam.split(",").filter { it.isNotEmpty() }
            com.weelo.logistics.ui.transporter.DriverAssignmentScreen(
                broadcastId = broadcastId,
                holdId = holdId,
                requiredVehicleType = vehicleType,
                requiredVehicleSubtype = vehicleSubtype,
                requiredQuantity = quantity,
                preselectedVehicleIds = vehicleIds,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTracking = { assignmentId ->
                    // Navigate to real-time driver response tracking screen
                    navController.navigateSmooth(
                        route = Screen.TripStatusManagement.createRoute(assignmentId),
                        popUpToRoute = Screen.TransporterDashboard.route,
                        inclusive = false
                    )
                }
            )
        }
        
        // Trip Status Management - Shows driver acceptance status in real-time
        composable(
            route = Screen.TripStatusManagement.route,
            arguments = listOf(navArgument("assignmentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val assignmentId = backStackEntry.arguments?.getString("assignmentId") ?: ""
            com.weelo.logistics.ui.transporter.TripStatusManagementScreen(
                assignmentId = assignmentId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReassign = { _, _ ->
                    navController.popBackStack()
                },
                onNavigateToTracking = { _ ->
                    navController.navigateSmooth(Screen.TransporterDashboard.route)
                }
            )
        }

        // Fleet Management
        composable(Screen.FleetList.route) {
            com.weelo.logistics.ui.transporter.FleetListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddVehicle = { navController.navigateSmooth(Screen.AddVehicle.route) },
                onNavigateToVehicleDetails = { vehicleId ->
                    navController.navigateSmooth(Screen.VehicleDetails.createRoute(vehicleId))
                }
            )
        }
        
        composable(Screen.AddVehicle.route) {
            com.weelo.logistics.ui.transporter.AddVehicleScreen(
                onNavigateBack = { navController.popBackStack() },
                onVehicleAdded = {
                    navController.navigateSmooth(
                        route = Screen.FleetList.route,
                        popUpToRoute = Screen.FleetList.route,
                        inclusive = false
                    )
                }
            )
        }
        
        composable(Screen.VehicleDetails.route) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: ""
            com.weelo.logistics.ui.transporter.VehicleDetailsScreen(
                vehicleId = vehicleId,
                onNavigateBack = { navController.popBackStack() },
                onEdit = { /* TODO: Navigate to edit */ }
            )
        }
        
        // Driver Management
        composable(Screen.DriverList.route) {
            com.weelo.logistics.ui.transporter.DriverListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddDriver = { navController.navigateSmooth(Screen.AddDriver.route) },
                onNavigateToDriverDetails = { driverId ->
                    navController.navigateSmooth(Screen.DriverDetails.createRoute(driverId))
                }
            )
        }
        
        composable(Screen.AddDriver.route) {
            com.weelo.logistics.ui.transporter.AddDriverScreen(
                transporterId = com.weelo.logistics.data.remote.RetrofitClient.getUserId() ?: "",
                onNavigateBack = { navController.popBackStack() },
                onDriverAdded = {
                    navController.navigateSmooth(
                        route = Screen.DriverList.route,
                        popUpToRoute = Screen.DriverList.route,
                        inclusive = false
                    )
                }
            )
        }
        
        composable(Screen.DriverDetails.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            com.weelo.logistics.ui.transporter.DriverDetailsScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPerformance = { id ->
                    navController.navigateSmooth(Screen.DriverPerformance.createRoute(id))
                },
                onNavigateToEarnings = { id ->
                    navController.navigateSmooth(Screen.DriverEarnings.createRoute(id))
                }
            )
        }
        
        // Trip Management
        composable(Screen.TripList.route) {
            com.weelo.logistics.ui.transporter.TripListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateTrip = { navController.navigateSmooth(Screen.CreateTrip.route) },
                onNavigateToTripDetails = { tripId ->
                    navController.navigateSmooth(Screen.TripDetails.createRoute(tripId))
                }
            )
        }
        
        composable(Screen.CreateTrip.route) {
            com.weelo.logistics.ui.transporter.CreateTripScreen(
                onNavigateBack = { navController.popBackStack() },
                onTripCreated = {
                    navController.navigateSmooth(
                        route = Screen.TripList.route,
                        popUpToRoute = Screen.TripList.route,
                        inclusive = false
                    )
                }
            )
        }
        
        composable(Screen.TripDetails.route) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            com.weelo.logistics.ui.transporter.TripDetailsScreen(
                tripId = tripId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // =====================================================================
        // DRIVER ONBOARDING FLOW (Language → Profile → Dashboard)
        // =====================================================================
        
        // =====================================================================
        // STRICT SECURITY: Driver Onboarding Check
        // BLOCKS dashboard access without language selection
        //
        // CRITICAL FIX: Uses suspend getLanguageSync() + isProfileCompletedSync()
        // instead of collectAsState(initial=...) to avoid race conditions.
        //
        // WHY: collectAsState emits `initial` value BEFORE DataStore loads,
        // which can trigger navigation to wrong screen. By using
        // withContext(IO) + first(), we wait for actual persisted values.
        //
        // SCALABILITY: O(1) read, no network call, sub-10ms on any device.
        // =====================================================================
        composable("driver_onboarding_check") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val driverPrefs = remember { 
                com.weelo.logistics.data.preferences.DriverPreferences.getInstance(context) 
            }
            
            // STRICT: Read actual persisted values (not initial/default)
            // LaunchedEffect(Unit) runs ONCE, waits for DataStore to load
            LaunchedEffect(Unit) {
                val savedLanguage = withContext(Dispatchers.IO) {
                    driverPrefs.getLanguageSync()
                }
                val profileDone = withContext(Dispatchers.IO) {
                    driverPrefs.isProfileCompletedSync()
                }
                
                Timber.i(
                    "🔒 Onboarding check: savedLanguage='$savedLanguage', profileDone=$profileDone"
                )
                
                when {
                    savedLanguage.isEmpty() -> {
                        // No language saved locally (backend had null + never selected)
                        // → MUST select language before proceeding
                        Timber.i("🔒 No language saved → showing language selection screen")
                        navController.navigateSmooth(
                            route = "driver_language_selection",
                            popUpToRoute = "driver_onboarding_check",
                            inclusive = true
                        )
                    }
                    !profileDone -> {
                        // Language done, but profile not complete
                        Timber.i("🔒 Profile incomplete → forcing profile screen")
                        navController.navigateSmooth(
                            route = "driver_profile_completion",
                            popUpToRoute = "driver_onboarding_check",
                            inclusive = true
                        )
                    }
                    else -> {
                        // Everything done: go to dashboard
                        Timber.i("✅ Onboarding complete → dashboard")
                        navController.navigateSmooth(
                            route = Screen.DriverDashboard.route,
                            popUpToRoute = "driver_onboarding_check",
                            inclusive = true
                        )
                    }
                }
            }
            
            // =========================================================
            // POLISHED SKELETON instead of bare CircularProgressIndicator
            // =========================================================
            // Shows shimmer placeholder (~50-200ms) while DataStore prefs
            // load. Makes the OTP → dashboard transition feel seamless.
            //
            // SCALABILITY: Pure composable, no state, no network calls.
            // MODULARITY: Uses OnboardingCheckSkeleton from SkeletonLoading.kt
            // =========================================================
            com.weelo.logistics.ui.components.OnboardingCheckSkeleton()
        }
        
        // Language Selection Screen (first-time onboarding)
        composable("driver_language_selection") {
            val langContext = androidx.compose.ui.platform.LocalContext.current
            val driverPrefs = remember { 
                com.weelo.logistics.data.preferences.DriverPreferences.getInstance(langContext) 
            }
            
            // Get real MainActivity via LocalView (not affected by locale wrapping)
            val langView = androidx.compose.ui.platform.LocalView.current
            val langMainActivity = remember { langView.context.findMainActivity() }
            
            val coroutineScope = rememberCoroutineScope()
            
            // STRICT: Cannot navigate back from language selection
            BackHandler(enabled = true) {
                // Block back button - must select language
            }
            
            com.weelo.logistics.ui.driver.LanguageSelectionScreen(
                onLanguageSelected = { languageCode ->
                    coroutineScope.launch {
                        // 1. Save language locally (commit = synchronous)
                        driverPrefs.saveLanguage(languageCode)
                        
                        // 2. Update locale INSTANTLY via reactive state
                        //    No recreate(). No screen flash. No WebSocket disruption.
                        //    CompositionLocalProvider re-provides localized Context →
                        //    all stringResource() calls resolve to new language.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            timber.log.Timber.i("🌐 First-time language selected: $languageCode → instant locale update")
                            langMainActivity?.updateLocale(languageCode)
                            
                            // 3. Navigate to profile completion (language is done)
                            navController.navigateSmooth(
                                route = "driver_profile_completion",
                                popUpToRoute = "driver_language_selection",
                                inclusive = true
                            )
                        }
                    }
                }
            )
        }
        
        // Profile Completion Screen
        composable("driver_profile_completion") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val driverPrefs = remember { 
                com.weelo.logistics.data.preferences.DriverPreferences.getInstance(context) 
            }
            
            val coroutineScope = rememberCoroutineScope()
            
            com.weelo.logistics.ui.driver.DriverProfileCompletionScreen(
                onProfileComplete = { _ ->
                    // TODO: Upload profile data to backend
                    // For now, just mark as complete and navigate
                    coroutineScope.launch {
                        driverPrefs.markProfileCompleted()
                        // Navigate to dashboard (on main thread)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            navController.navigateSmooth(
                                route = Screen.DriverDashboard.route,
                                popUpToRoute = "driver_profile_completion",
                                inclusive = true
                            )
                        }
                    }
                }
            )
        }
        
        // Driver Dashboard
        // SMOOTH TRANSITION: Uses fade instead of slide when entering from
        // onboarding check — both screens have similar skeleton layouts,
        // so a fade creates a seamless visual bridge. Other navigation
        // (from profile, settings, etc.) also benefits from the smooth fade.
        composable(
            route = Screen.DriverDashboard.route,
            enterTransition = { NavAnimations.fadeIn },
            exitTransition = { NavAnimations.fadeOut },
            popEnterTransition = { NavAnimations.fadeIn },
            popExitTransition = { NavAnimations.fadeOut }
        ) {
            val currentDriverId = com.weelo.logistics.data.remote.RetrofitClient.getUserId() ?: ""
            com.weelo.logistics.ui.driver.DriverDashboardScreen(
                onNavigateToNotifications = { navController.navigateSmooth(Screen.DriverNotifications.createRoute(currentDriverId)) },
                onNavigateToTripHistory = { navController.navigateSmooth(Screen.DriverTripHistory.createRoute(currentDriverId)) },
                onNavigateToProfile = { navController.navigateSmooth("driver_profile") },
                onNavigateToEarnings = { navController.navigateSmooth(Screen.DriverEarnings.createRoute(currentDriverId)) },
                onNavigateToDocuments = { navController.navigateSmooth(Screen.DriverDocuments.createRoute(currentDriverId)) },
                onNavigateToSettings = { navController.navigateSmooth(Screen.DriverSettings.route) },
                onOpenFullMap = { _ -> /* TODO: Implement */ },
                onLogout = {
                    performLogout(navController, logoutScope)
                }
            )
        }

        // Driver Trip Navigation (after accepting trip)
        // Shows live tracking with Google Map
        composable(
            route = Screen.DriverTripNavigation.route,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            DriverTripNavigationScreen(
                tripId = tripId,
                onNavigateBack = { navController.popBackStack() },
                onTripCompleted = {
                    // Navigate back to dashboard after trip completion
                    navController.navigateSmooth(
                        route = Screen.DriverDashboard.route,
                        popUpToRoute = Screen.DriverDashboard.route,
                        inclusive = true
                    )
                }
            )
        }

        // Driver Profile Screen (with photo display and update)
        // Uses viewModel() factory for proper lifecycle management (survives config changes)
        composable("driver_profile") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val viewModel: DriverProfileViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return DriverProfileViewModel(context.applicationContext) as T
                    }
                }
            )
            
            com.weelo.logistics.ui.driver.DriverProfileScreenWithPhotos(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Driver Performance
        composable(Screen.DriverPerformance.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            com.weelo.logistics.ui.driver.DriverPerformanceScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Driver Earnings
        composable(Screen.DriverEarnings.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            com.weelo.logistics.ui.driver.DriverEarningsScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Driver Profile Edit
        composable(Screen.DriverProfileEdit.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            com.weelo.logistics.ui.driver.DriverProfileEditScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        
        // Driver Documents
        composable(Screen.DriverDocuments.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            com.weelo.logistics.ui.driver.DriverDocumentsScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Driver Settings
        // NOTE: Language change is now handled by an inline ModalBottomSheet
        // inside DriverSettingsScreen — no onChangeLanguage callback needed.
        composable(Screen.DriverSettings.route) {
            com.weelo.logistics.ui.driver.DriverSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    performLogout(navController, logoutScope)
                }
            )
        }
        
        // Driver Trip History
        composable(Screen.DriverTripHistory.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            com.weelo.logistics.ui.driver.DriverTripHistoryScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTripDetails = { tripId ->
                    navController.navigateSmooth(Screen.TripDetails.createRoute(tripId))
                }
            )
        }
        
        // Driver Notifications
        composable(Screen.DriverNotifications.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            com.weelo.logistics.ui.driver.DriverNotificationsScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTrip = { tripId ->
                    navController.navigateSmooth(Screen.TripDetails.createRoute(tripId))
                }
            )
        }
        
        // =====================================================================
        // LANGUAGE CHANGE route REMOVED.
        //
        // Language change from Settings is now handled by an inline
        // ModalBottomSheet inside DriverSettingsScreen.kt — no separate
        // screen, no navigation, no Activity.recreate().
        //
        // Uses MainActivity.updateLocale() for instant locale switch.
        // =====================================================================
        }

        // =========================================================================
        // DRIVER TRIP REQUEST OVERLAY (Top Layer)
        // =========================================================================
        if (isDriver && isOverlayVisible && currentRequest != null) {
            val overlayContext = LocalContext.current
            DriverTripRequestOverlay(
                notification = currentRequest!!,
                onAccept = { assignmentId ->
                    logoutScope.launch {
                        // Set loading state BEFORE API call — spinner shows immediately
                        DriverTripRequestManager.setActionState(ActionState.ACCEPTING)
                        try {
                            val result = com.weelo.logistics.data.remote.retryWithBackoff(
                                maxRetries = 3,
                                initialDelayMs = 1000
                            ) {
                                com.weelo.logistics.data.repository.AssignmentRepository
                                    .getInstance(overlayContext).acceptAssignment(assignmentId)
                            }
                            when (result) {
                                is com.weelo.logistics.data.repository.BroadcastResult.Success -> {
                                    DriverTripRequestManager.onAccept(assignmentId)
                                    // Navigate to trip tracking screen
                                    navController.navigate("driver/trip_navigation/${result.data.tripId}")
                                }
                                is com.weelo.logistics.data.repository.BroadcastResult.Error -> {
                                    DriverTripRequestManager.setActionState(ActionState.IDLE)
                                    android.widget.Toast.makeText(
                                        overlayContext,
                                        "Failed to accept: ${result.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                                is com.weelo.logistics.data.repository.BroadcastResult.Loading -> {}
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            DriverTripRequestManager.setActionState(ActionState.IDLE)
                            android.widget.Toast.makeText(
                                overlayContext,
                                "Network error. Please try again.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onDecline = { assignmentId ->
                    logoutScope.launch {
                        // Set loading state BEFORE API call
                        DriverTripRequestManager.setActionState(ActionState.DECLINING)
                        try {
                            val result = com.weelo.logistics.data.remote.retryWithBackoff(
                                maxRetries = 3,
                                initialDelayMs = 1000
                            ) {
                                com.weelo.logistics.data.repository.AssignmentRepository
                                    .getInstance(overlayContext).declineAssignment(assignmentId, "")
                            }
                            when (result) {
                                is com.weelo.logistics.data.repository.BroadcastResult.Success -> {
                                    DriverTripRequestManager.onDecline(assignmentId)
                                }
                                is com.weelo.logistics.data.repository.BroadcastResult.Error -> {
                                    DriverTripRequestManager.setActionState(ActionState.IDLE)
                                    android.widget.Toast.makeText(
                                        overlayContext,
                                        "Failed to decline: ${result.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                                is com.weelo.logistics.data.repository.BroadcastResult.Loading -> {}
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            DriverTripRequestManager.setActionState(ActionState.IDLE)
                            android.widget.Toast.makeText(
                                overlayContext,
                                "Network error. Please try again.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onDismiss = {
                    logoutScope.launch {
                        DriverTripRequestManager.onDismiss()
                    }
                },
                onNavigate = {
                    // Open Google Maps to navigate to pickup
                    val pickupLat = currentRequest!!.pickup.latitude
                    val pickupLng = currentRequest!!.pickup.longitude
                    if (pickupLat != 0.0 && pickupLng != 0.0) {
                        val uri = android.net.Uri.parse("google.navigation:q=$pickupLat,$pickupLng")
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW, uri
                        ).apply {
                            setPackage("com.google.android.apps.maps")
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            overlayContext.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to web Google Maps
                            val webIntent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://maps.google.com/?q=$pickupLat,$pickupLng")
                            )
                            overlayContext.startActivity(webIntent)
                        }
                    }
                },
                actionState = actionState
            )
        }
    }
}
