package com.weelo.logistics.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Authentication ViewModel
 * 
 * Handles OTP-based login flow
 * 
 * FAKE OTP FOR TESTING: "123456"
 * 
 * TODO: Connect to backend
 * - Replace fake OTP with real API call
 * - Use AuthRepository.sendOTP()
 * - Use AuthRepository.verifyOTP()
 */
class AuthViewModel : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState
    
    // Fake OTP for testing (will be removed when backend connected)
    private val FAKE_OTP = "123456"
    private var sentOtpId: String? = null
    
    /**
     * Send OTP to mobile number
     * 
     * CURRENT: Sends fake OTP "123456"
     * TODO: Replace with real API call
     */
    fun sendOTP(mobileNumber: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            // Validate mobile number
            if (mobileNumber.length != 10) {
                _uiState.value = AuthUiState.Error("Please enter valid 10-digit mobile number")
                return@launch
            }
            
            // Simulate API call delay
            delay(1000)
            
            // TODO: Uncomment when backend ready
            // val repository = AuthRepository()
            // val result = repository.sendOTP(mobileNumber)
            // result.onSuccess { response ->
            //     sentOtpId = response.otpId
            //     _uiState.value = AuthUiState.OTPSent(response.otpId)
            // }.onFailure { error ->
            //     _uiState.value = AuthUiState.Error(error.message ?: "Failed to send OTP")
            // }
            
            // TEMPORARY: Fake OTP sent
            sentOtpId = "fake_otp_id_${System.currentTimeMillis()}"
            _uiState.value = AuthUiState.OTPSent(
                otpId = sentOtpId!!,
                message = "OTP sent! Use: $FAKE_OTP"
            )
        }
    }
    
    /**
     * Verify OTP
     * 
     * CURRENT: Accepts only "123456"
     * TODO: Replace with real API call
     */
    fun verifyOTP(
        mobileNumber: String,
        otp: String,
        role: String
    ) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            // Validate OTP
            if (otp.length != 6) {
                _uiState.value = AuthUiState.Error("Please enter 6-digit OTP")
                return@launch
            }
            
            // Simulate API call delay
            delay(1000)
            
            // TODO: Uncomment when backend ready
            // val repository = AuthRepository()
            // val result = repository.verifyOTP(
            //     mobileNumber = mobileNumber,
            //     otp = otp,
            //     otpId = sentOtpId ?: "",
            //     deviceId = getDeviceId(),
            //     fcmToken = getFCMToken()
            // )
            // result.onSuccess { authResponse ->
            //     // Save user session
            //     saveUserSession(authResponse.user, authResponse.token)
            //     _uiState.value = AuthUiState.Success(authResponse.user)
            // }.onFailure { error ->
            //     _uiState.value = AuthUiState.Error(error.message ?: "Invalid OTP")
            // }
            
            // TEMPORARY: Check fake OTP
            if (otp == FAKE_OTP) {
                _uiState.value = AuthUiState.Success(
                    userId = "fake_user_${System.currentTimeMillis()}",
                    role = role
                )
            } else {
                _uiState.value = AuthUiState.Error("Invalid OTP. Use: $FAKE_OTP")
            }
        }
    }
    
    /**
     * Reset state
     */
    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}

/**
 * Authentication UI States
 */
sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class OTPSent(val otpId: String, val message: String = "OTP sent successfully") : AuthUiState()
    data class Success(val userId: String, val role: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
