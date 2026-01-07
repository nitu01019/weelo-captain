package com.weelo.logistics.ui.navigation

/**
 * Screen routes for navigation
 * Defines all screen destinations in the app
 */
sealed class Screen(val route: String) {
    // Welcome & Auth
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Signup : Screen("signup")
    object RoleSelection : Screen("role_selection")
    // Language selection removed - app is English only
    
    // Transporter Screens
    object TransporterDashboard : Screen("transporter_dashboard")
    
    // Fleet Management
    object FleetList : Screen("fleet_list")
    object AddVehicle : Screen("add_vehicle")
    object VehicleDetails : Screen("vehicle_details/{vehicleId}") {
        fun createRoute(vehicleId: String) = "vehicle_details/$vehicleId"
    }
    
    // Driver Management
    object DriverList : Screen("driver_list")
    object AddDriver : Screen("add_driver")
    object DriverDetails : Screen("driver_details/{driverId}") {
        fun createRoute(driverId: String) = "driver_details/$driverId"
    }
    
    // Trip Management
    object TripList : Screen("trip_list")
    object CreateTrip : Screen("create_trip")
    object TripDetails : Screen("trip_details/{tripId}") {
        fun createRoute(tripId: String) = "trip_details/$tripId"
    }
    
    // Broadcast System - NEW
    object BroadcastList : Screen("broadcast_list")
    object TruckSelection : Screen("truck_selection/{broadcastId}") {
        fun createRoute(broadcastId: String) = "truck_selection/$broadcastId"
    }
    object DriverAssignment : Screen("driver_assignment/{broadcastId}/{vehicleIds}") {
        fun createRoute(broadcastId: String, vehicleIds: String) = "driver_assignment/$broadcastId/$vehicleIds"
    }
    object TripStatusManagement : Screen("trip_status/{assignmentId}") {
        fun createRoute(assignmentId: String) = "trip_status/$assignmentId"
    }
    
    // Driver Screens
    object DriverDashboard : Screen("driver_dashboard")
    object DriverPerformance : Screen("driver_performance/{driverId}") {
        fun createRoute(driverId: String) = "driver_performance/$driverId"
    }
    object DriverEarnings : Screen("driver_earnings/{driverId}") {
        fun createRoute(driverId: String) = "driver_earnings/$driverId"
    }
    object DriverProfileEdit : Screen("driver_profile_edit/{driverId}") {
        fun createRoute(driverId: String) = "driver_profile_edit/$driverId"
    }
    object DriverDocuments : Screen("driver_documents/{driverId}") {
        fun createRoute(driverId: String) = "driver_documents/$driverId"
    }
    object DriverSettings : Screen("driver_settings")
    object DriverTripHistory : Screen("driver_trip_history/{driverId}") {
        fun createRoute(driverId: String) = "driver_trip_history/$driverId"
    }
    object DriverTripNavigation : Screen("driver_trip_navigation/{tripId}") {
        fun createRoute(tripId: String) = "driver_trip_navigation/$tripId"
    }
    object DriverNotifications : Screen("driver_notifications/{driverId}") {
        fun createRoute(driverId: String) = "driver_notifications/$driverId"
    }
    object TripTracking : Screen("trip_tracking/{tripId}") {
        fun createRoute(tripId: String) = "trip_tracking/$tripId"
    }
    
    // Driver Broadcast System - NEW
    object DriverTripNotifications : Screen("driver_trip_notifications/{driverId}") {
        fun createRoute(driverId: String) = "driver_trip_notifications/$driverId"
    }
    object TripAcceptDecline : Screen("trip_accept_decline/{notificationId}") {
        fun createRoute(notificationId: String) = "trip_accept_decline/$notificationId"
    }
    
    // Shared Tracking - NEW
    object LiveTracking : Screen("live_tracking/{tripId}/{driverId}") {
        fun createRoute(tripId: String, driverId: String) = "live_tracking/$tripId/$driverId"
    }
    
    // Shared Screens
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Notifications : Screen("notifications")
}
