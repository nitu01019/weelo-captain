package com.weelo.logistics.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.weelo.logistics.data.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * User Preferences Repository - Store user preferences and session data
 * Uses DataStore for persistent storage
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {
    
    private object PreferenceKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_MOBILE = stringPreferencesKey("user_mobile")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val ACTIVE_ROLE = stringPreferencesKey("active_role")
        val HAS_TRANSPORTER_ROLE = booleanPreferencesKey("has_transporter_role")
        val HAS_DRIVER_ROLE = booleanPreferencesKey("has_driver_role")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
    }
    
    /**
     * Save user session
     */
    suspend fun saveUserSession(
        userId: String,
        name: String,
        mobile: String,
        email: String?,
        roles: List<UserRole>
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.USER_ID] = userId
            preferences[PreferenceKeys.USER_NAME] = name
            preferences[PreferenceKeys.USER_MOBILE] = mobile
            preferences[PreferenceKeys.USER_EMAIL] = email ?: ""
            preferences[PreferenceKeys.HAS_TRANSPORTER_ROLE] = roles.contains(UserRole.TRANSPORTER)
            preferences[PreferenceKeys.HAS_DRIVER_ROLE] = roles.contains(UserRole.DRIVER)
            preferences[PreferenceKeys.ACTIVE_ROLE] = roles.first().name
            preferences[PreferenceKeys.IS_LOGGED_IN] = true
        }
    }
    
    /**
     * Get user ID
     */
    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.USER_ID]
    }
    
    /**
     * Get active role
     */
    val activeRole: Flow<UserRole?> = context.dataStore.data.map { preferences ->
        val roleName = preferences[PreferenceKeys.ACTIVE_ROLE]
        roleName?.let { UserRole.valueOf(it) }
    }
    
    /**
     * Switch active role
     */
    suspend fun switchRole(role: UserRole) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ACTIVE_ROLE] = role.name
        }
    }
    
    /**
     * Check if user has both roles
     */
    val hasBothRoles: Flow<Boolean> = context.dataStore.data.map { preferences ->
        (preferences[PreferenceKeys.HAS_TRANSPORTER_ROLE] == true) &&
        (preferences[PreferenceKeys.HAS_DRIVER_ROLE] == true)
    }
    
    /**
     * Check if user is logged in
     */
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.IS_LOGGED_IN] ?: false
    }
    
    /**
     * Check if onboarding is completed
     */
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ONBOARDING_COMPLETED] ?: false
    }
    
    /**
     * Set onboarding completed
     */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ONBOARDING_COMPLETED] = completed
        }
    }
    
    /**
     * Get preferred language
     */
    val preferredLanguage: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.PREFERRED_LANGUAGE]
    }
    
    /**
     * Save preferred language
     * 
     * IMPORTANT: Also saves to SharedPreferences for synchronous read
     * during app startup (attachBaseContext cannot use coroutines)
     */
    suspend fun savePreferredLanguage(languageCode: String) {
        // Save to DataStore (for coroutine-based access)
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.PREFERRED_LANGUAGE] = languageCode
        }
        
        // Also save to SharedPreferences for synchronous startup access
        // This enables fast, non-blocking language loading in attachBaseContext()
        //
        // MUST USE commit() (synchronous), NOT apply() (async).
        // WHY: Activity.recreate() may be called immediately after this write.
        // commit() guarantees the write is flushed before recreate() reads it.
        context.getSharedPreferences("weelo_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("preferred_language", languageCode)
            .commit()
    }
    
    /**
     * Clear all preferences (logout)
     */
    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
