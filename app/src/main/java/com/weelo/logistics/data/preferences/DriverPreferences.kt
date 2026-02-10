package com.weelo.logistics.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Driver Preferences Storage
 * 
 * Efficiently stores driver-specific preferences using DataStore:
 * - Selected language (persistent)
 * - Profile completion status
 * - First launch flag
 * 
 * Optimized for performance:
 * - Uses DataStore (better than SharedPreferences)
 * - Reactive Flow for real-time updates
 * - Type-safe keys
 */

// Extension property for DataStore
private val Context.driverDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "driver_preferences"
)

class DriverPreferences(private val context: Context) {
    
    companion object {
        // Keys (type-safe)
        private val LANGUAGE_KEY = stringPreferencesKey("selected_language")
        private val PROFILE_COMPLETED_KEY = booleanPreferencesKey("profile_completed")
        private val FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")
        
        // Singleton instance for better performance
        @Volatile
        private var INSTANCE: DriverPreferences? = null
        
        fun getInstance(context: Context): DriverPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DriverPreferences(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    // =========================================================================
    // LANGUAGE PREFERENCE
    // =========================================================================
    
    /**
     * Get selected language as Flow (reactive)
     * Default: "" (empty = not yet selected → shows language selection screen)
     * 
     * EASY UNDERSTANDING: Empty string means "never selected".
     * Navigation checks isEmpty() to decide whether to show language screen.
     * Once selected, this persists across app restarts.
     * On logout, clearAll() resets to empty so next login re-evaluates.
     */
    val selectedLanguage: Flow<String> = context.driverDataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: ""
    }
    
    /**
     * Save selected language
     * Called after language selection screen
     * CRITICAL: This enables dashboard access
     * 
     * DUAL WRITE: Saves to both DataStore (for reactive Flow) AND
     * SharedPreferences (for synchronous read in attachBaseContext).
     * 
     * WHY: MainActivity.attachBaseContext() runs BEFORE any coroutine
     * can complete, so it needs a synchronous SharedPreferences read.
     * DataStore is async-only, so we write to both.
     */
    suspend fun saveLanguage(languageCode: String) {
        // 1. Save to DataStore (reactive Flow for Compose UI)
        context.driverDataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = languageCode
            // Also mark first launch as completed
            preferences[FIRST_LAUNCH_KEY] = false
        }
        
        // 2. CRITICAL: Also save to SharedPreferences for synchronous read
        // in MainActivity.attachBaseContext() — this is what actually applies
        // the locale on app restart. Without this, language resets to English!
        context.getSharedPreferences("weelo_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("preferred_language", languageCode)
            .apply()
    }
    
    /**
     * Get language synchronously (for immediate use)
     * Returns "" if not yet selected.
     */
    suspend fun getLanguageSync(): String {
        val preferences = context.driverDataStore.data
        return preferences.map { it[LANGUAGE_KEY] ?: "" }.first()
    }
    
    /**
     * Restore language from backend response (after login).
     * Only restores if backend has a saved preference AND local is empty.
     * 
     * SCALABILITY: Called once per login, O(1), no network call.
     * MODULARITY: Isolated restore logic — caller doesn't need to know internals.
     * 
     * CRITICAL: Uses saveLanguage() which does DUAL WRITE to both
     * DataStore and SharedPreferences, ensuring attachBaseContext() reads it.
     */
    suspend fun restoreLanguageIfNeeded(backendLanguage: String?) {
        if (!backendLanguage.isNullOrEmpty()) {
            val current = getLanguageSync()
            if (current.isEmpty()) {
                saveLanguage(backendLanguage)
            }
        }
    }
    
    // =========================================================================
    // PROFILE COMPLETION STATUS
    // =========================================================================
    
    /**
     * Check if driver has completed profile setup
     * Returns Flow for reactive UI updates
     */
    val isProfileCompleted: Flow<Boolean> = context.driverDataStore.data.map { preferences ->
        preferences[PROFILE_COMPLETED_KEY] ?: false
    }
    
    /**
     * Mark profile as completed
     * Called after successful profile submission
     */
    suspend fun markProfileCompleted() {
        context.driverDataStore.edit { preferences ->
            preferences[PROFILE_COMPLETED_KEY] = true
        }
    }
    
    /**
     * Get profile completion status synchronously
     */
    suspend fun isProfileCompletedSync(): Boolean {
        val preferences = context.driverDataStore.data
        return preferences.map { it[PROFILE_COMPLETED_KEY] ?: false }.first()
    }
    
    // =========================================================================
    // FIRST LAUNCH FLAG
    // =========================================================================
    
    /**
     * Check if this is first time driver is launching after login
     * Used to show language selection only once
     */
    val isFirstLaunch: Flow<Boolean> = context.driverDataStore.data.map { preferences ->
        preferences[FIRST_LAUNCH_KEY] ?: true // Default: true (first launch)
    }
    
    /**
     * Mark first launch as completed
     * Called after language selection
     */
    suspend fun markFirstLaunchCompleted() {
        context.driverDataStore.edit { preferences ->
            preferences[FIRST_LAUNCH_KEY] = false
        }
    }
    
    // =========================================================================
    // RESET (for testing or logout)
    // =========================================================================
    
    /**
     * Clear all driver preferences
     * Called on driver logout
     * 
     * DUAL CLEAR: Clears both DataStore and SharedPreferences
     * to ensure language resets properly on logout.
     */
    suspend fun clearAll() {
        context.driverDataStore.edit { preferences ->
            preferences.clear()
        }
        // Also clear SharedPreferences (used by attachBaseContext)
        context.getSharedPreferences("weelo_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .remove("preferred_language")
            .apply()
    }
    
    /**
     * Reset only onboarding flags (keep language)
     * Useful for testing
     */
    suspend fun resetOnboarding() {
        context.driverDataStore.edit { preferences ->
            preferences[FIRST_LAUNCH_KEY] = true
            preferences[PROFILE_COMPLETED_KEY] = false
        }
    }
}

/**
 * Extension function for easy access from Composables
 */
fun Context.getDriverPreferences(): DriverPreferences {
    return DriverPreferences.getInstance(this)
}
