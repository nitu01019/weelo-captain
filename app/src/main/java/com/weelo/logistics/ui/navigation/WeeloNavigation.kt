package com.weelo.logistics.ui.navigation

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.weelo.logistics.ui.auth.*

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
    userRole: String? = null
) {
    // Determine start destination based on login status
    val startDestination = if (isLoggedIn && userRole != null) {
        if (userRole.uppercase() == "TRANSPORTER") {
            Screen.TransporterDashboard.route
        } else {
            Screen.DriverDashboard.route
        }
    } else {
        Screen.RoleSelection.route
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // Default smooth animations for all screens
        enterTransition = { NavAnimations.slideInFromRight },
        exitTransition = { NavAnimations.slideOutToLeft },
        popEnterTransition = { NavAnimations.slideInFromLeft },
        popExitTransition = { NavAnimations.slideOutToRight }
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
                    navController.navigate("${Screen.Login.route}/$role")
                }
            )
        }
        
        composable("${Screen.Login.route}/{role}") { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "TRANSPORTER"
            LoginScreen(
                role = role,
                onNavigateToSignup = {
                    navController.navigate("${Screen.Signup.route}/$role")
                },
                onNavigateToOTP = { mobile, selectedRole ->
                    navController.navigate("otp_verification/$mobile/$selectedRole/login")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("otp_verification/{mobile}/{role}/{type}") { backStackEntry ->
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
                        navController.navigate("${Screen.Signup.route}/$role/step2") {
                            popUpTo("otp_verification/{mobile}/{role}/{type}") { inclusive = true }
                        }
                    } else {
                        // Login - go to dashboard
                        val destination = if (role == "TRANSPORTER") {
                            Screen.TransporterDashboard.route
                        } else {
                            Screen.DriverDashboard.route
                        }
                        navController.navigate(destination) {
                            // Clear auth stack, but keep RoleSelection as back target
                            popUpTo(Screen.RoleSelection.route) { inclusive = false }
                        }
                    }
                }
            )
        }
        
        composable("${Screen.Signup.route}/{role}") { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "TRANSPORTER"
            SignupScreen(
                role = role,
                onNavigateToLogin = {
                    navController.navigate("${Screen.Login.route}/$role") {
                        popUpTo(Screen.RoleSelection.route) { inclusive = false }
                    }
                },
                onNavigateToOTP = { mobile, selectedRole, isSignup ->
                    val type = if (isSignup) "signup" else "login"
                    navController.navigate("otp_verification/$mobile/$selectedRole/$type")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Transporter Dashboard
        composable(Screen.TransporterDashboard.route) {
            com.weelo.logistics.ui.transporter.TransporterDashboardScreen(
                onNavigateToFleet = { navController.navigate(Screen.FleetList.route) },
                onNavigateToDrivers = { navController.navigate(Screen.DriverList.route) },
                onNavigateToTrips = { navController.navigate(Screen.TripList.route) },
                onNavigateToAddVehicle = { navController.navigate(Screen.AddVehicle.route) },
                onNavigateToAddDriver = { navController.navigate(Screen.AddDriver.route) },
                onNavigateToCreateTrip = { navController.navigate(Screen.CreateTrip.route) },
                onNavigateToProfile = { navController.navigate("transporter_profile") },
                onNavigateToBroadcasts = { navController.navigate(Screen.BroadcastList.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onLogout = {
                    // Back button or logout goes to role selection
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
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
        
        // Broadcast List Screen - Shows customer booking requests
        composable(Screen.BroadcastList.route) {
            com.weelo.logistics.ui.transporter.BroadcastListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBroadcastDetails = { broadcastId ->
                    navController.navigate(Screen.TruckSelection.createRoute(broadcastId))
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
                    navController.navigate(Screen.DriverAssignment.createRoute(bId, vehicleIdsParam))
                }
            )
        }
        
        // Driver Assignment Screen - Assign drivers to selected trucks
        composable(Screen.DriverAssignment.route) { backStackEntry ->
            val broadcastId = backStackEntry.arguments?.getString("broadcastId") ?: ""
            val vehicleIdsParam = backStackEntry.arguments?.getString("vehicleIds") ?: ""
            val vehicleIds = vehicleIdsParam.split(",").filter { it.isNotEmpty() }
            com.weelo.logistics.ui.transporter.DriverAssignmentScreen(
                broadcastId = broadcastId,
                selectedVehicleIds = vehicleIds,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTracking = {
                    // Navigate back to dashboard after successful assignment
                    navController.popBackStack(Screen.TransporterDashboard.route, inclusive = false)
                }
            )
        }
        
        // Fleet Management
        composable(Screen.FleetList.route) {
            com.weelo.logistics.ui.transporter.FleetListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddVehicle = { navController.navigate(Screen.AddVehicle.route) },
                onNavigateToVehicleDetails = { vehicleId ->
                    navController.navigate(Screen.VehicleDetails.createRoute(vehicleId))
                }
            )
        }
        
        composable(Screen.AddVehicle.route) {
            com.weelo.logistics.ui.transporter.AddVehicleScreen(
                onNavigateBack = { navController.popBackStack() },
                onVehicleAdded = {
                    navController.popBackStack(Screen.FleetList.route, inclusive = false)
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
                onNavigateToAddDriver = { navController.navigate(Screen.AddDriver.route) },
                onNavigateToDriverDetails = { driverId ->
                    navController.navigate(Screen.DriverDetails.createRoute(driverId))
                }
            )
        }
        
        composable(Screen.AddDriver.route) {
            com.weelo.logistics.ui.transporter.AddDriverScreen(
                transporterId = "TRP-001", // TODO: Get from user session
                onNavigateBack = { navController.popBackStack() },
                onDriverAdded = {
                    navController.popBackStack(Screen.DriverList.route, inclusive = false)
                }
            )
        }
        
        composable(Screen.DriverDetails.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
            com.weelo.logistics.ui.transporter.DriverDetailsScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPerformance = { id ->
                    navController.navigate(Screen.DriverPerformance.createRoute(id))
                },
                onNavigateToEarnings = { id ->
                    navController.navigate(Screen.DriverEarnings.createRoute(id))
                }
            )
        }
        
        // Trip Management
        composable(Screen.TripList.route) {
            com.weelo.logistics.ui.transporter.TripListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateTrip = { navController.navigate(Screen.CreateTrip.route) },
                onNavigateToTripDetails = { tripId ->
                    navController.navigate(Screen.TripDetails.createRoute(tripId))
                }
            )
        }
        
        composable(Screen.CreateTrip.route) {
            com.weelo.logistics.ui.transporter.CreateTripScreen(
                onNavigateBack = { navController.popBackStack() },
                onTripCreated = {
                    navController.popBackStack(Screen.TripList.route, inclusive = false)
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
        
        // Driver Dashboard
        composable(Screen.DriverDashboard.route) {
            com.weelo.logistics.ui.driver.DriverDashboardScreen(
                onNavigateToNotifications = { /* TODO: Implement */ },
                onNavigateToTripHistory = { /* TODO: Implement */ },
                onNavigateToProfile = { navController.navigate("driver_profile") },
                onOpenFullMap = { tripId -> /* TODO: Implement */ },
                onLogout = {
                    // Clear tokens and logout
                    com.weelo.logistics.data.remote.RetrofitClient.clearAllData()
                    // Back button or logout goes to role selection
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Driver Profile Screen
        composable("driver_profile") {
            com.weelo.logistics.ui.driver.DriverProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onProfileUpdated = { /* Refresh will happen automatically */ }
            )
        }
        
        // Driver Performance
        composable(Screen.DriverPerformance.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: "d1"
            com.weelo.logistics.ui.driver.DriverPerformanceScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Driver Earnings
        composable(Screen.DriverEarnings.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: "d1"
            com.weelo.logistics.ui.driver.DriverEarningsScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Driver Profile Edit
        composable(Screen.DriverProfileEdit.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: "d1"
            com.weelo.logistics.ui.driver.DriverProfileEditScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        
        // Driver Documents
        composable(Screen.DriverDocuments.route) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: "d1"
            com.weelo.logistics.ui.driver.DriverDocumentsScreen(
                driverId = driverId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Driver Settings
        composable(Screen.DriverSettings.route) {
            com.weelo.logistics.ui.driver.DriverSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
