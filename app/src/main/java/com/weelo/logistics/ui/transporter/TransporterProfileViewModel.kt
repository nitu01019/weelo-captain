package com.weelo.logistics.ui.transporter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.data.api.TransporterProfileRequest
import com.weelo.logistics.data.api.UserProfile
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.offline.OfflineCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TransporterProfileUiState {
    data object Loading : TransporterProfileUiState

    data class Content(
        val form: TransporterProfileFormState,
        val isRefreshing: Boolean,
        val isSaving: Boolean,
        val errorMessage: String?,
        val successMessage: String?
    ) : TransporterProfileUiState
}

data class TransporterProfileFormState(
    val profile: UserProfile?,
    val name: String,
    val email: String,
    val businessName: String,
    val businessAddress: String,
    val panNumber: String,
    val gstNumber: String
) {
    companion object {
        fun empty() = TransporterProfileFormState(
            profile = null,
            name = "",
            email = "",
            businessName = "",
            businessAddress = "",
            panNumber = "",
            gstNumber = ""
        )

        fun fromProfile(profile: UserProfile?) = TransporterProfileFormState(
            profile = profile,
            name = profile?.name.orEmpty(),
            email = profile?.email.orEmpty(),
            businessName = profile?.getBusinessDisplayName().orEmpty(),
            businessAddress = profile?.businessAddress ?: profile?.address.orEmpty(),
            panNumber = profile?.panNumber.orEmpty(),
            gstNumber = profile?.gstNumber.orEmpty()
        )
    }
}

sealed interface TransporterProfileUiEvent {
    data object ProfileSaved : TransporterProfileUiEvent
}

class TransporterProfileViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val offlineCache = OfflineCache.getInstance(appContext)

    private val _uiState = MutableStateFlow<TransporterProfileUiState>(TransporterProfileUiState.Loading)
    val uiState: StateFlow<TransporterProfileUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<TransporterProfileUiEvent>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<TransporterProfileUiEvent> = _uiEvents.asSharedFlow()

    private var bootstrapStarted = false

    fun bootstrapIfNeeded() {
        if (bootstrapStarted) return
        bootstrapStarted = true

        viewModelScope.launch {
            val cachedProfile = withContext(Dispatchers.IO) {
                offlineCache.getDashboardCache().profile
            }
            if (cachedProfile != null) {
                timber.log.Timber.d("👤 TransporterProfile bootstrap source=cache")
                _uiState.value = TransporterProfileUiState.Content(
                    form = TransporterProfileFormState.fromProfile(cachedProfile),
                    isRefreshing = true,
                    isSaving = false,
                    errorMessage = null,
                    successMessage = null
                )
            } else {
                _uiState.value = TransporterProfileUiState.Loading
            }
            refreshProfileInternal()
        }
    }

    fun retry() {
        viewModelScope.launch { refreshProfileInternal() }
    }

    fun onNameChange(value: String) = updateForm { copy(name = value) }
    fun onEmailChange(value: String) = updateForm { copy(email = value) }
    fun onBusinessNameChange(value: String) = updateForm { copy(businessName = value) }
    fun onBusinessAddressChange(value: String) = updateForm { copy(businessAddress = value) }
    fun onPanNumberChange(value: String) = updateForm { copy(panNumber = value.uppercase()) }
    fun onGstNumberChange(value: String) = updateForm { copy(gstNumber = value.uppercase()) }

    fun clearMessages() {
        val current = _uiState.value as? TransporterProfileUiState.Content ?: return
        _uiState.value = current.copy(errorMessage = null, successMessage = null)
    }

    fun saveProfile() {
        val current = _uiState.value as? TransporterProfileUiState.Content ?: return
        if (current.isSaving) return

        if (current.form.name.isBlank()) {
            _uiState.value = current.copy(
                errorMessage = "Name is required",
                successMessage = null
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isSaving = true, errorMessage = null, successMessage = null)

            try {
                val request = TransporterProfileRequest(
                    name = current.form.name.trim(),
                    email = current.form.email.trim().ifEmpty { null },
                    company = current.form.businessName.trim(),
                    gstNumber = current.form.gstNumber.trim().ifEmpty { null },
                    panNumber = current.form.panNumber.trim().ifEmpty { null },
                    address = current.form.businessAddress.trim().ifEmpty { null }
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.profileApi.updateTransporterProfile(request)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    val updatedProfile = response.body()?.data?.user
                    if (updatedProfile != null) {
                        withContext(Dispatchers.IO) {
                            offlineCache.saveDashboardData(
                                profile = updatedProfile,
                                vehicleStats = null,
                                driverStats = null
                            )
                        }
                    }
                    _uiState.value = TransporterProfileUiState.Content(
                        form = TransporterProfileFormState.fromProfile(updatedProfile ?: current.form.profile),
                        isRefreshing = false,
                        isSaving = false,
                        errorMessage = null,
                        successMessage = "Profile saved successfully!"
                    )
                    _uiEvents.tryEmit(TransporterProfileUiEvent.ProfileSaved)
                } else {
                    _uiState.value = currentStateOrFallback().copy(
                        isSaving = false,
                        errorMessage = response.body()?.error?.message ?: "Failed to save profile",
                        successMessage = null
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = currentStateOrFallback().copy(
                    isSaving = false,
                    errorMessage = "Network error: ${e.message}",
                    successMessage = null
                )
            }
        }
    }

    private suspend fun refreshProfileInternal() {
        val before = currentStateOrNull()
        if (before != null) {
            _uiState.value = before.copy(isRefreshing = true, errorMessage = null)
        }

        try {
            val response = withContext(Dispatchers.IO) { RetrofitClient.profileApi.getProfile() }
            if (response.isSuccessful && response.body()?.success == true) {
                val profile = response.body()?.data?.user
                timber.log.Timber.d("👤 TransporterProfile refresh source=network")
                withContext(Dispatchers.IO) {
                    offlineCache.saveDashboardData(
                        profile = profile,
                        vehicleStats = null,
                        driverStats = null
                    )
                }
                _uiState.value = TransporterProfileUiState.Content(
                    form = TransporterProfileFormState.fromProfile(profile),
                    isRefreshing = false,
                    isSaving = false,
                    errorMessage = null,
                    successMessage = before?.successMessage
                )
            } else {
                val error = response.body()?.error?.message ?: "Failed to load profile"
                if (before != null) {
                    _uiState.value = before.copy(isRefreshing = false, errorMessage = error)
                } else {
                    _uiState.value = TransporterProfileUiState.Content(
                        form = TransporterProfileFormState.empty(),
                        isRefreshing = false,
                        isSaving = false,
                        errorMessage = error,
                        successMessage = null
                    )
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val error = "Network error: ${e.message}"
            val current = currentStateOrNull()
            if (current != null) {
                _uiState.value = current.copy(isRefreshing = false, errorMessage = error)
            } else {
                _uiState.value = TransporterProfileUiState.Content(
                    form = TransporterProfileFormState.empty(),
                    isRefreshing = false,
                    isSaving = false,
                    errorMessage = error,
                    successMessage = null
                )
            }
        }
    }

    private fun updateForm(transform: TransporterProfileFormState.() -> TransporterProfileFormState) {
        val current = _uiState.value
        if (current is TransporterProfileUiState.Content) {
            _uiState.value = current.copy(
                form = current.form.transform(),
                successMessage = null
            )
        }
    }

    private fun currentStateOrNull(): TransporterProfileUiState.Content? {
        return _uiState.value as? TransporterProfileUiState.Content
    }

    private fun currentStateOrFallback(): TransporterProfileUiState.Content {
        return currentStateOrNull() ?: TransporterProfileUiState.Content(
            form = TransporterProfileFormState.empty(),
            isRefreshing = false,
            isSaving = false,
            errorMessage = null,
            successMessage = null
        )
    }
}
