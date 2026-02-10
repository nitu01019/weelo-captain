package com.weelo.logistics.ui.driver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.data.api.LanguagePreferenceRequest
import com.weelo.logistics.data.preferences.DriverPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Language Selection Screen
 *
 * MODULARITY:    Single responsibility — language preference persistence.
 * SCALABILITY:   Local-first save (instant UX), backend save async (non-blocking).
 *                Even if backend is slow under millions of users, UI is responsive.
 * CODING STD:    Uses DriverPreferences (same DataStore navigation reads from).
 *                Eliminates desync between UserPreferencesRepository and DriverPreferences.
 * EASY TO READ:  Two steps: local save → backend save. Always succeeds locally.
 */
class LanguageViewModel(application: Application) : AndroidViewModel(application) {

    // MODULARITY: Uses DriverPreferences — same store that navigation reads from.
    // Previously used UserPreferencesRepository which caused desync.
    private val driverPrefs = DriverPreferences(application)
    private val profileApi = com.weelo.logistics.data.remote.RetrofitClient.profileApi

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    /**
     * Save language preference to local storage + backend.
     *
     * SCALABILITY: Local save is instant (DataStore). Backend save is fire-and-forget.
     * Even under millions of concurrent requests, the user sees instant response.
     * Backend failure does NOT block the user — language is persisted locally.
     */
    fun saveLanguagePreference(languageCode: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                // Step 1: Save to DriverPreferences (instant, local)
                driverPrefs.saveLanguage(languageCode)

                // Step 2: Save to backend (async, for cross-device persistence)
                try {
                    profileApi.updateLanguagePreference(
                        LanguagePreferenceRequest(languageCode)
                    )
                } catch (_: Exception) {
                    // Backend failure is non-blocking — local save already succeeded
                }

                _saveSuccess.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save language preference"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Reactive Flow of selected language (for UI observation). */
    fun getSavedLanguage() = driverPrefs.selectedLanguage
}
