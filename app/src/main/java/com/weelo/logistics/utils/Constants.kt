package com.weelo.logistics.utils

/**
 * App-wide constants for Weelo Logistics
 * Designed for scalability to millions of users
 */
object Constants {
    
    // ==========================================================================
    // API CONFIGURATION
    // ==========================================================================
    // Backend: weelo-backend (Node.js/Express) running on port 3000
    // For Android Emulator: 10.0.2.2 maps to host's localhost
    // For Physical Device: Use your computer's local IP (run: ipconfig getifaddr en0)
    //
    // SECURITY:
    // - Development: HTTP allowed for local testing only
    // - Production: HTTPS required with certificate pinning
    //
    // AWS MIGRATION:
    // - When moving to AWS, update PRODUCTION_URL and WS_PRODUCTION_URL
    // - Example: "https://api.weelo.in" → "https://api.weelologistics.com"
    // - Certificate pinning hashes will need to be updated for new domain
    // ==========================================================================
    object API {
        
        /**
         * =====================================================================
         * ENVIRONMENT SELECTOR
         * =====================================================================
         * Change this single value to switch between environments:
         * - EMULATOR: Testing on Android emulator (localhost via 10.0.2.2)
         * - DEVICE: Testing on physical phone over WiFi
         * - STAGING: Pre-production testing
         * - PRODUCTION: Live production (AWS/Cloud)
         * 
         * FOR PHYSICAL DEVICE TESTING:
         * 1. Find your laptop's WiFi IP: Run `ipconfig getifaddr en0` in terminal
         * 2. Update DEVICE_IP below with that IP
         * 3. Set currentEnvironment = Environment.DEVICE
         * 4. Ensure both phone and laptop are on same WiFi network
         */
        enum class Environment {
            EMULATOR,    // Android emulator → laptop localhost
            DEVICE,      // Physical phone → laptop over WiFi
            STAGING,     // Staging server (pre-production)
            PRODUCTION   // Live production server (AWS)
        }
        
        /**
         * CURRENT ACTIVE ENVIRONMENT
         * ===========================
         * Change this to switch environments easily!
         * 
         * For development: EMULATOR or DEVICE
         * For testing: STAGING
         * For release: PRODUCTION (auto-selected for release builds)
         * 
         * OPTIONS:
         * - Environment.EMULATOR   → Local backend via emulator (10.0.2.2:3000)
         * - Environment.DEVICE     → Local backend via WiFi IP (192.168.x.x:3000)
         * - Environment.PRODUCTION → AWS backend (weelo-alb-xxx.amazonaws.com)
         */
        private val currentEnvironment: Environment
            get() = if (com.weelo.logistics.BuildConfig.DEBUG) {
                Environment.PRODUCTION  // <-- Using AWS backend for testing
                // Change to EMULATOR or DEVICE for local backend testing
            } else {
                Environment.PRODUCTION
            }
        
        /**
         * YOUR LAPTOP'S WIFI IP ADDRESS
         * ==============================
         * Update this when testing on physical device!
         * 
         * To find your IP:
         * - Mac: Run `ipconfig getifaddr en0` in Terminal
         * - Windows: Run `ipconfig` and find IPv4 Address
         * - Linux: Run `hostname -I`
         * 
         * Make sure phone and laptop are on SAME WiFi network!
         */
        private const val DEVICE_IP = "192.168.1.10"  // <-- UPDATE THIS WITH YOUR IP!
        
        // Port where backend runs
        private const val PORT = "3000"
        
        // API version path
        private const val API_PATH = "/api/v1/"
        
        // =============================================================
        // URL DEFINITIONS (Don't change unless server locations change)
        // =============================================================
        
        // Development URLs (HTTP - local only)
        private const val EMULATOR_HOST = "10.0.2.2"  // Android's localhost alias
        private val EMULATOR_URL = "http://$EMULATOR_HOST:$PORT$API_PATH"
        private val DEVICE_URL = "http://$DEVICE_IP:$PORT$API_PATH"
        
        // Production URLs (HTTPS - AWS/Cloud)
        // Update these when migrating to AWS
        private const val STAGING_HOST = "staging-api.weelo.in"
        private const val PRODUCTION_HOST = "weelo-alb-380596483.ap-south-1.elb.amazonaws.com"
        private const val STAGING_URL = "https://$STAGING_HOST$API_PATH"
        // TODO: CRITICAL — Change to HTTPS when SSL certificate is configured on ALB
        // Currently HTTP because ALB does not have SSL configured yet.
        private const val PRODUCTION_URL = "http://$PRODUCTION_HOST$API_PATH"
        
        // WebSocket URLs (for Socket.IO real-time communication)
        private val WS_EMULATOR_URL = "http://$EMULATOR_HOST:$PORT"
        private val WS_DEVICE_URL = "http://$DEVICE_IP:$PORT"
        private const val WS_STAGING_URL = "wss://$STAGING_HOST"
        // TODO: CRITICAL — Change to WSS when SSL certificate is configured on ALB
        private const val WS_PRODUCTION_URL = "ws://$PRODUCTION_HOST"
        
        // =============================================================
        // ACTIVE URLS (Auto-selected based on currentEnvironment)
        // =============================================================
        
        /**
         * Active Base URL - Used for all REST API calls
         */
        val BASE_URL: String
            get() = when (currentEnvironment) {
                Environment.EMULATOR -> EMULATOR_URL
                Environment.DEVICE -> DEVICE_URL
                Environment.STAGING -> STAGING_URL
                Environment.PRODUCTION -> PRODUCTION_URL
            }
        
        /**
         * Active WebSocket URL - Used for real-time communication
         */
        val WS_URL: String
            get() = when (currentEnvironment) {
                Environment.EMULATOR -> WS_EMULATOR_URL
                Environment.DEVICE -> WS_DEVICE_URL
                Environment.STAGING -> WS_STAGING_URL
                Environment.PRODUCTION -> WS_PRODUCTION_URL
            }
        
        /**
         * Check if currently in development mode
         */
        val isDevelopment: Boolean
            get() = currentEnvironment == Environment.EMULATOR || 
                    currentEnvironment == Environment.DEVICE
        
        /**
         * Get human-readable environment name (for logging/debugging)
         */
        val environmentName: String
            get() = currentEnvironment.name
        
        const val TIMEOUT_SECONDS = 30L
        const val MAX_RETRIES = 3
        const val CACHE_SIZE = 10 * 1024 * 1024 // 10 MB
        
        /**
         * SSL/TLS Configuration
         * 
         * SECURITY:
         * - Certificate pinning is ENABLED for release builds
         * - This prevents MITM attacks even if device CA is compromised
         * 
         * AWS MIGRATION NOTE:
         * When changing production domain, update certificate pin hashes!
         */
        val ENABLE_CERTIFICATE_PINNING: Boolean
            get() = !isDevelopment  // Enabled in staging/production
        
        const val TLS_VERSION = "TLSv1.3"
        
        /**
         * Log current configuration (call at app startup for debugging)
         */
        fun logConfiguration() {
            timber.log.Timber.i("""
                |╔══════════════════════════════════════════════════════════════╗
                |║  WEELO CAPTAIN APP - API CONFIGURATION                       ║
                |╠══════════════════════════════════════════════════════════════╣
                |║  Environment: $environmentName
                |║  Base URL: $BASE_URL
                |║  WebSocket URL: $WS_URL
                |║  Certificate Pinning: ${if (ENABLE_CERTIFICATE_PINNING) "ENABLED" else "DISABLED"}
                |╚══════════════════════════════════════════════════════════════╝
            """.trimMargin())
        }
    }
    
    // ==========================================================================
    // DEVELOPMENT MODE HELPERS
    // ==========================================================================
    // OTPs are logged to backend console - check terminal where server is running
    // SECURITY: These helpers are only useful in debug builds
    object DevMode {
        val isEnabled: Boolean
            get() = com.weelo.logistics.BuildConfig.DEBUG
        
        const val OTP_INFO = "Check backend console for OTP"
    }
    
    // Security Configuration
    object Security {
        const val OTP_LENGTH = 6
        const val OTP_VALIDITY_MINUTES = 5
        const val MAX_LOGIN_ATTEMPTS = 5
        const val LOGIN_LOCKOUT_MINUTES = 15
        const val SESSION_TIMEOUT_MINUTES = 60
        const val TOKEN_REFRESH_MINUTES = 50
    }
    
    // Pagination (for scalability)
    object Pagination {
        const val PAGE_SIZE = 20
        const val INITIAL_LOAD_SIZE = 40
        const val PREFETCH_DISTANCE = 10
    }
    
    // Location Updates (GPS Tracking)
    object Location {
        const val UPDATE_INTERVAL_MS = 10000L // 10 seconds
        const val FASTEST_INTERVAL_MS = 5000L // 5 seconds
        const val MIN_DISTANCE_METERS = 10f
        const val ACCURACY_THRESHOLD_METERS = 50f
    }
    
    // Database Configuration
    object Database {
        const val NAME = "weelo_logistics.db"
        const val VERSION = 1
        const val CACHE_SIZE_MB = 50
    }
    
    // Cache Configuration
    object Cache {
        const val DASHBOARD_CACHE_MINUTES = 5
        const val VEHICLE_LIST_CACHE_MINUTES = 10
        const val DRIVER_LIST_CACHE_MINUTES = 10
        const val TRIP_LIST_CACHE_MINUTES = 2
    }
    
    // File Upload Limits
    object Upload {
        const val MAX_IMAGE_SIZE_MB = 5
        const val MAX_DOCUMENT_SIZE_MB = 10
        const val ALLOWED_IMAGE_TYPES = "image/jpeg,image/png,image/jpg"
        const val ALLOWED_DOCUMENT_TYPES = "application/pdf,image/jpeg,image/png"
    }
    
    // Validation Limits
    object Validation {
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 100
        const val MOBILE_NUMBER_LENGTH = 10
        const val MIN_COMPANY_NAME_LENGTH = 2
        const val MAX_COMPANY_NAME_LENGTH = 200
        const val MIN_CITY_NAME_LENGTH = 2
        const val LICENSE_NUMBER_LENGTH = 15
    }
    
    // App Features
    object Features {
        const val ENABLE_GPS_TRACKING = true
        const val ENABLE_PUSH_NOTIFICATIONS = true
        const val ENABLE_OFFLINE_MODE = true
        const val ENABLE_DARK_MODE = false // For future
        const val ENABLE_ANALYTICS = true
    }
    
    // Notification Channels
    object Notifications {
        const val CHANNEL_TRIPS = "trips"
        const val CHANNEL_PAYMENTS = "payments"
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_GPS = "gps_tracking"
    }
}

/**
 * Error codes for consistent error handling
 */
object ErrorCodes {
    const val NETWORK_ERROR = 1001
    const val AUTHENTICATION_ERROR = 1002
    const val VALIDATION_ERROR = 1003
    const val SERVER_ERROR = 1004
    const val TIMEOUT_ERROR = 1005
    const val PERMISSION_ERROR = 1006
    const val GPS_ERROR = 1007
    const val DATABASE_ERROR = 1008
}

/**
 * Preferences keys for DataStore
 */
object PreferenceKeys {
    const val USER_ID = "user_id"
    const val USER_TOKEN = "user_token"
    const val USER_ROLE = "user_role"
    const val HAS_BOTH_ROLES = "has_both_roles"
    const val ACTIVE_ROLE = "active_role"
    const val IS_LOGGED_IN = "is_logged_in"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val LAST_SYNC_TIME = "last_sync_time"
    const val GPS_ENABLED = "gps_enabled"
}
