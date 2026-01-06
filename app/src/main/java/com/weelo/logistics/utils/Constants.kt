package com.weelo.logistics.utils

/**
 * App-wide constants for Weelo Logistics
 * Designed for scalability to millions of users
 */
object Constants {
    
    // API Configuration (for future backend integration)
    object API {
        const val BASE_URL = "https://api.weelo.com/v1/"
        const val TIMEOUT_SECONDS = 30L
        const val MAX_RETRIES = 3
        const val CACHE_SIZE = 10 * 1024 * 1024 // 10 MB
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
