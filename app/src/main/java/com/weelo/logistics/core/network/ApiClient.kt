package com.weelo.logistics.core.network

/**
 * API Client configuration
 * Centralized network configuration for scalability
 */
object ApiClient {
    
    object BaseUrls {
        const val PRODUCTION = "https://api.weelologistics.com/v1/"
        const val STAGING = "https://staging-api.weelologistics.com/v1/"
        const val DEVELOPMENT = "http://localhost:3000/api/v1/"
    }
    
    object Endpoints {
        // Driver Management
        const val DRIVERS = "transporters/{transporterId}/drivers"
        const val DRIVER_DETAILS = "transporters/{transporterId}/drivers/{driverId}"
        const val ASSIGN_VEHICLE = "transporters/{transporterId}/drivers/{driverId}/assign-vehicle"
        
        // Vehicle Management
        const val VEHICLES = "transporters/{transporterId}/vehicles"
        const val VEHICLE_DETAILS = "transporters/{transporterId}/vehicles/{vehicleId}"
        
        // Trip Management
        const val TRIPS = "transporters/{transporterId}/trips"
        const val TRIP_DETAILS = "transporters/{transporterId}/trips/{tripId}"
        
        // Broadcast Management
        const val BROADCASTS = "transporters/{transporterId}/broadcasts"
        const val BROADCAST_ACCEPT = "transporters/{transporterId}/broadcasts/{broadcastId}/accept"
        const val BROADCAST_DETAILS = "transporters/{transporterId}/broadcasts/{broadcastId}"
        
        // Driver Assignment
        const val ASSIGN_DRIVER = "transporters/{transporterId}/trips/{tripId}/assign-driver"
        
        // GPS Tracking
        const val LOCATION_UPDATES = "drivers/{driverId}/location"
        const val TRIP_TRACKING = "trips/{tripId}/tracking"
    }
    
    object Timeouts {
        const val CONNECT_TIMEOUT = 30L
        const val READ_TIMEOUT = 30L
        const val WRITE_TIMEOUT = 30L
    }
}
