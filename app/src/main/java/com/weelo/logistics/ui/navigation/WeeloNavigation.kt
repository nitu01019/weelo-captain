package com.weelo.logistics.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.weelo.logistics.ui.auth.*

/**
 * Main Navigation component
 * Sets up the navigation graph for the entire app
 */
@Composable
fun WeeloNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // Welcome & Auth Flow
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToDashboard = { role ->
                    val destination = if (role == "TRANSPORTER") {
                        Screen.TransporterDashboard.route
                    } else {
                        Screen.DriverDashboard.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    // TODO: Temporarily skip login/signup - go directly to dashboard
                    val destination = if (role == "TRANSPORTER") {
                        Screen.TransporterDashboard.route
                    } else {
                        Screen.DriverDashboard.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                    
                    // Original flow (commented out for now):
                    // navController.navigate("${Screen.Login.route}/$role")
                }
            )
        }
        
        composable("${Screen.Login.route}/{role}") { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "TRANSPORTER"
            LoginScreen(
                role = role,
                onNavigateToSignup = {
                    navController.navigate("${Screen.Signup.route}/$role/new")
                },
                onNavigateToOTP = { mobile, selectedRole ->
                    navController.navigate("otp_verification/$mobile/$selectedRole")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("otp_verification/{mobile}/{role}") { backStackEntry ->
            val mobile = backStackEntry.arguments?.getString("mobile") ?: ""
            val role = backStackEntry.arguments?.getString("role") ?: "TRANSPORTER"
            OTPVerificationScreen(
                mobileNumber = mobile,
                role = role,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVerifySuccess = { verifiedRole ->
                    // Check if user exists, if not go to signup
                    navController.navigate("${Screen.Signup.route}/$verifiedRole/$mobile") {
                        popUpTo("otp_verification/{mobile}/{role}") { inclusive = true }
                    }
                }
            )
        }
        
        composable("${Screen.Signup.route}/{role}/{mobile}") { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "TRANSPORTER"
            val mobile = backStackEntry.arguments?.getString("mobile") ?: ""
            SignupScreen(
                role = role,
                mobileNumber = mobile,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSignupSuccess = { signupRole ->
                    val destination = if (signupRole == "TRANSPORTER") {
                        Screen.TransporterDashboard.route
                    } else {
                        Screen.DriverDashboard.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
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
                onNavigateToCreateTrip = { navController.navigate(Screen.CreateTrip.route) }
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
            com.weelo.logistics.ui.driver.DriverDashboardScreen()
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
