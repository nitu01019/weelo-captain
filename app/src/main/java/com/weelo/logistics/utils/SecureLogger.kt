package com.weelo.logistics.utils

import android.util.Log

/**
 * Secure Logger - Prevents sensitive data from appearing in logs
 * 
 * SECURITY: Never log PII (Personally Identifiable Information)
 * - Phone numbers
 * - OTPs
 * - Auth tokens
 * - Passwords
 * - Email addresses
 * - Location data
 * - Payment info
 * 
 * PRODUCTION: Disable debug logs in release builds
 * 
 * Usage:
 * ```
 * SecureLogger.d(TAG, "User logged in") // Safe
 * SecureLogger.d(TAG, "OTP: $otp") // DON'T DO THIS!
 * SecureLogger.d(TAG, "User phone: ${SecureLogger.mask(phone)}") // Safe
 * ```
 */
object SecureLogger {
    
    private const val TAG_PREFIX = "Weelo"
    
    // Set to false in production (use BuildConfig.DEBUG)
    var isDebugEnabled = true // TODO: Change to BuildConfig.DEBUG
    
    /**
     * Debug log - Only in development
     */
    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.d("$TAG_PREFIX:$tag", sanitizeMessage(message))
        }
    }
    
    /**
     * Info log - General information
     */
    fun i(tag: String, message: String) {
        Log.i("$TAG_PREFIX:$tag", sanitizeMessage(message))
    }
    
    /**
     * Warning log - Potential issues
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$TAG_PREFIX:$tag", sanitizeMessage(message), throwable)
        } else {
            Log.w("$TAG_PREFIX:$tag", sanitizeMessage(message))
        }
    }
    
    /**
     * Error log - Errors and exceptions
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG_PREFIX:$tag", sanitizeMessage(message), throwable)
        } else {
            Log.e("$TAG_PREFIX:$tag", sanitizeMessage(message))
        }
    }
    
    /**
     * Sanitize message - Remove sensitive patterns
     */
    private fun sanitizeMessage(message: String): String {
        var sanitized = message
        
        // Remove potential phone numbers (10 digits)
        sanitized = sanitized.replace(Regex("\\b\\d{10}\\b"), "[PHONE]")
        
        // Remove potential OTPs (6 digits)
        sanitized = sanitized.replace(Regex("\\b\\d{6}\\b"), "[OTP]")
        
        // Remove tokens (long alphanumeric strings)
        sanitized = sanitized.replace(Regex("\\b[A-Za-z0-9]{32,}\\b"), "[TOKEN]")
        
        // Remove email addresses
        sanitized = sanitized.replace(Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), "[EMAIL]")
        
        return sanitized
    }
    
    /**
     * Mask sensitive data for logging
     * Example: "9876543210" -> "987****210"
     */
    fun mask(data: String?, visibleChars: Int = 3): String {
        if (data == null || data.length <= visibleChars * 2) {
            return "[HIDDEN]"
        }
        val start = data.substring(0, visibleChars)
        val end = data.substring(data.length - visibleChars)
        return "$start****$end"
    }
    
    /**
     * Log API request (without sensitive data)
     */
    fun logApiRequest(endpoint: String, method: String) {
        d("API", "Request: $method $endpoint")
    }
    
    /**
     * Log API response (without sensitive data)
     */
    fun logApiResponse(endpoint: String, statusCode: Int, success: Boolean) {
        d("API", "Response: $endpoint - Status: $statusCode, Success: $success")
    }
    
    /**
     * Log user action (analytics-safe)
     */
    fun logUserAction(action: String, screen: String) {
        i("UserAction", "Action: $action on $screen")
    }
    
    /**
     * Log error with context (no PII)
     */
    fun logError(tag: String, operation: String, error: Throwable) {
        e(tag, "Error during $operation: ${error.javaClass.simpleName}", error)
    }
}

/**
 * Extension functions for easier usage
 */
fun String.maskForLog(): String = SecureLogger.mask(this)

/**
 * SECURITY CHECKLIST FOR LOGGING:
 * 
 * ✅ Never log:
 *    - Phone numbers
 *    - OTPs
 *    - Passwords
 *    - Auth tokens
 *    - Refresh tokens
 *    - Email addresses
 *    - User's full name
 *    - Location coordinates
 *    - Payment card info
 *    - Driver license numbers
 *    - Vehicle registration numbers
 * 
 * ✅ Safe to log:
 *    - User IDs (if not sequential)
 *    - Screen names
 *    - Button clicks
 *    - Navigation events
 *    - Error types
 *    - API endpoint names (not full URLs with params)
 *    - Success/failure status
 * 
 * ✅ If you must log sensitive data (debugging):
 *    - Use masking: SecureLogger.mask(phone)
 *    - Only in debug builds
 *    - Remove before production
 * 
 * ✅ Production Best Practices:
 *    - Disable all debug logs
 *    - Use analytics for user behavior (anonymized)
 *    - Use crash reporting (sanitized)
 *    - Log only errors for debugging
 */
