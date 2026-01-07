package com.weelo.logistics.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API Key Manager
 * 
 * Secure storage and retrieval of API keys and secrets
 * Uses Android Keystore for encryption
 * 
 * SECURITY: NEVER hardcode API keys in source code
 * 
 * TODO BACKEND DEVELOPER:
 * 1. Store API keys in environment variables on server
 * 2. Use BuildConfig for client-side keys (if needed)
 * 3. Rotate keys regularly
 * 4. Never commit .env files to git
 * 
 * Example .env file (DO NOT COMMIT):
 * ```
 * API_BASE_URL=https://api.weelo.com
 * OTP_SERVICE_KEY=your_otp_service_key_here
 * MAPS_API_KEY=your_maps_api_key_here
 * FCM_SERVER_KEY=your_fcm_key_here
 * ```
 */
object ApiKeyManager {
    
    private const val PREFS_NAME = "weelo_secure_prefs"
    
    // TODO: Move these to BuildConfig or environment variables
    // For now, these are placeholders
    const val API_BASE_URL = "https://api.weelo.com" // TODO: Replace with actual URL
    const val TIMEOUT_SECONDS = 30L
    
    /**
     * Get encrypted shared preferences
     * Uses Android Keystore for secure storage
     */
    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Store API key securely
     * BACKEND: Call this only from secure initialization, never hardcode keys
     */
    fun storeApiKey(context: Context, keyName: String, keyValue: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().putString(keyName, keyValue).apply()
    }
    
    /**
     * Retrieve API key securely
     */
    fun getApiKey(context: Context, keyName: String): String? {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(keyName, null)
    }
    
    /**
     * Remove API key (for key rotation)
     */
    fun removeApiKey(context: Context, keyName: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().remove(keyName).apply()
    }
    
    /**
     * Check if API key exists
     */
    fun hasApiKey(context: Context, keyName: String): Boolean {
        return getApiKey(context, keyName) != null
    }
    
    /**
     * Key names (use these as constants)
     */
    object Keys {
        const val OTP_SERVICE = "otp_service_key"
        const val MAPS_API = "maps_api_key"
        const val FCM_SERVER = "fcm_server_key"
        const val AUTH_TOKEN = "auth_token"
        const val REFRESH_TOKEN = "refresh_token"
    }
}

/**
 * Security Best Practices Checklist:
 * 
 * ✅ 1. API Keys Storage
 *    - Use environment variables on server
 *    - Use BuildConfig for client (obfuscated)
 *    - Never commit keys to git
 *    - Add .env to .gitignore
 * 
 * ✅ 2. API Keys in Transit
 *    - Always use HTTPS/TLS
 *    - Use certificate pinning for critical APIs
 *    - Implement token expiration
 * 
 * ✅ 3. API Keys Rotation
 *    - Rotate keys every 90 days
 *    - Have key rotation mechanism
 *    - Monitor for compromised keys
 * 
 * ✅ 4. Access Control
 *    - Implement rate limiting
 *    - Use IP whitelisting when possible
 *    - Monitor unusual API usage patterns
 * 
 * ✅ 5. Secrets Management
 *    - Use Android Keystore
 *    - Encrypt sensitive data at rest
 *    - Clear secrets from memory after use
 */
