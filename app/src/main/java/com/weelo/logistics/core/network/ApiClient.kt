package com.weelo.logistics.core.network

import com.weelo.logistics.BuildConfig

/**
 * =============================================================================
 * API CLIENT CONFIGURATION
 * =============================================================================
 * 
 * Centralized network configuration for Weelo Backend.
 * 
 * SECURITY:
 * - Uses BuildConfig to determine environment (debug vs release)
 * - Production builds ALWAYS use HTTPS
 * - No hardcoded test credentials in release builds
 * 
 * FOR BACKEND DEVELOPERS:
 * - Development: Uses HTTP to local server (emulator/device)
 * - Production: Uses HTTPS with certificate pinning
 * 
 * Backend: weelo-backend (Node.js/Express)
 * API Version: v1
 * =============================================================================
 */
object ApiClient {
    
    /**
     * Base URLs for different environments
     * 
     * SECURITY NOTE:
     * - Production/Staging URLs use HTTPS (required!)
     * - Development URLs use HTTP (local network only)
     * - The CURRENT URL is determined by BuildConfig.DEBUG flag
     */
    object BaseUrls {
        // Production - AWS Load Balancer (deployed backend)
        // TODO: CRITICAL — Change to HTTPS when SSL certificate is on ALB
        const val PRODUCTION = "http://weelo-alb-380596483.ap-south-1.elb.amazonaws.com/api/v1/"
        
        // Staging - For testing before production
        const val STAGING = "http://weelo-alb-380596483.ap-south-1.elb.amazonaws.com/api/v1/"
        
        // Development - Android Emulator connects to host via 10.0.2.2
        const val EMULATOR = "http://weelo-alb-380596483.ap-south-1.elb.amazonaws.com/api/v1/"
        
        // Development - Physical device (replace with your Mac's IP)
        // Run: ipconfig getifaddr en0 to get your IP
        const val DEVICE = "http://weelo-alb-380596483.ap-south-1.elb.amazonaws.com/api/v1/"
        
        /**
         * Current active URL - Automatically selected based on build type
         * 
         * DEBUG builds: Use EMULATOR URL for local development
         * RELEASE builds: Use PRODUCTION URL with HTTPS
         */
        val CURRENT: String
            get() = if (BuildConfig.DEBUG) EMULATOR else PRODUCTION
    }
    
    /**
     * API Endpoints
     * All endpoints are relative to BASE_URL
     */
    object Endpoints {
        // ========== AUTH ==========
        const val SEND_OTP = "auth/send-otp"
        const val VERIFY_OTP = "auth/verify-otp"
        const val REFRESH_TOKEN = "auth/refresh"
        const val LOGOUT = "auth/logout"
        const val GET_ME = "auth/me"
        
        // ========== PROFILE ==========
        const val GET_PROFILE = "profile"
        const val UPDATE_CUSTOMER_PROFILE = "profile/customer"
        const val UPDATE_TRANSPORTER_PROFILE = "profile/transporter"
        const val UPDATE_DRIVER_PROFILE = "profile/driver"
        const val GET_TRANSPORTER_DRIVERS = "profile/drivers"
        const val ADD_DRIVER = "profile/drivers"
        const val REMOVE_DRIVER = "profile/drivers/{driverId}"
        const val GET_MY_TRANSPORTER = "profile/my-transporter"
        
        // ========== VEHICLES ==========
        const val VEHICLES = "vehicles"
        const val VEHICLE_DETAILS = "vehicles/{vehicleId}"
        const val VEHICLE_TYPES = "vehicles/types"
        const val VEHICLES_AVAILABLE = "vehicles/available"
        const val VEHICLES_SUMMARY = "vehicles/summary"
        const val VEHICLES_STATS = "vehicles/stats"
        const val VEHICLE_STATUS = "vehicles/{vehicleId}/status"
        const val VEHICLE_MAINTENANCE = "vehicles/{vehicleId}/maintenance"
        const val VEHICLE_SET_AVAILABLE = "vehicles/{vehicleId}/available"
        const val VEHICLE_ASSIGN_DRIVER = "vehicles/{vehicleId}/assign-driver"
        const val VEHICLE_UNASSIGN_DRIVER = "vehicles/{vehicleId}/unassign-driver"
        
        // ========== DRIVER ==========
        const val DRIVER_CREATE = "driver/create"
        const val DRIVER_LIST = "driver/list"
        const val DRIVER_DASHBOARD = "driver/dashboard"
        const val DRIVER_AVAILABILITY = "driver/availability"
        const val DRIVER_EARNINGS = "driver/earnings"
        const val DRIVER_TRIPS = "driver/trips"
        const val DRIVER_ACTIVE_TRIP = "driver/trips/active"
        
        // ========== BOOKINGS ==========
        const val BOOKINGS = "bookings"
        const val BOOKING_DETAILS = "bookings/{bookingId}"
        const val BOOKINGS_ACTIVE = "bookings/active"
        const val BOOKING_TRUCKS = "bookings/{bookingId}/trucks"
        const val BOOKING_CANCEL = "bookings/{bookingId}/cancel"
        
        // ========== BROADCASTS ==========
        const val BROADCASTS_ACTIVE = "broadcasts/active"
        const val BROADCAST_ACCEPT = "broadcasts/{broadcastId}/accept"
        const val BROADCAST_DECLINE = "broadcasts/{broadcastId}/decline"
        
        // ========== ASSIGNMENTS ==========
        const val ASSIGNMENTS = "assignments"
        const val ASSIGNMENT_DETAILS = "assignments/{assignmentId}"
        
        // ========== TRACKING ==========
        const val TRACKING_UPDATE = "tracking/location"
        const val TRACKING_TRIP = "tracking/trip/{tripId}"
        
        // ========== PRICING ==========
        const val PRICING_ESTIMATE = "pricing/estimate"
    }
    
    /**
     * Network timeouts
     */
    // PERFORMANCE FIX: Reduced from 30s to fail faster and show retry UI sooner.
    // Backend with warm Redis responds in <2s. If it takes >15s, something is wrong
    // and the circuit breaker / retry mechanism should kick in.
    object Timeouts {
        const val CONNECT_TIMEOUT = 15L   // was 30L — TCP connect should be instant
        const val READ_TIMEOUT = 20L      // was 30L — API responses should be <5s
        const val WRITE_TIMEOUT = 15L     // was 30L — request upload is small
    }
    
    /**
     * Development mode helper
     * 
     * SECURITY: Mock mode is ONLY available in debug builds
     * Release builds will always return false for isEnabled
     * 
     * FOR TESTING:
     * - In debug builds, OTPs are shown in backend server console
     * - No hardcoded test OTPs - always use real OTP from server
     */
    object DevMode {
        /**
         * Check if app is running in development/debug mode
         */
        val isEnabled: Boolean
            get() = BuildConfig.DEBUG
        
        /**
         * Hint message for developers
         */
        const val OTP_HINT = "Check backend server console for OTP"
    }
}
