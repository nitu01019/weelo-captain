package com.weelo.logistics.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.data.api.SendOTPRequest
import com.weelo.logistics.data.api.VerifyOTPRequest
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.utils.GlobalRateLimiters
import com.weelo.logistics.utils.InputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AuthViewModel - Connected to weelo-backend
 * 
 * Uses real API calls to:
 * - POST /api/v1/auth/send-otp
 * - POST /api/v1/auth/verify-otp
 * 
 * Development: OTPs are logged to backend console - check terminal where server runs
 */
class AuthViewModel : ViewModel() {
    
    // UI State - Reactive state management with StateFlow
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    // Rate limiters
    private val otpRateLimiter = GlobalRateLimiters.otp
    
    /**
     * Send OTP for Driver Login
     * Calls: POST /api/v1/auth/send-otp with role="driver"
     */
    fun sendDriverOTP(driverPhone: String) {
        sendOTP(driverPhone, "driver")
    }
    
    /**
     * Send OTP for Transporter Login
     * Calls: POST /api/v1/auth/send-otp with role="transporter"
     */
    fun sendTransporterOTP(transporterPhone: String) {
        sendOTP(transporterPhone, "transporter")
    }
    
    /**
     * Generic Send OTP function
     */
    private fun sendOTP(phone: String, role: String) {
        viewModelScope.launch {
            // Step 1: Validate input
            val validation = InputValidator.validatePhoneNumber(phone)
            if (!validation.isValid) {
                _authState.value = AuthState.Error(validation.errorMessage!!)
                return@launch
            }
            
            // Step 2: Check rate limiting
            val rateLimit = otpRateLimiter.tryAcquire(phone)
            if (!rateLimit) {
                val retryAfter = otpRateLimiter.getTimeUntilReset(phone) / 1000
                _authState.value = AuthState.RateLimited(retryAfter)
                return@launch
            }
            
            // Step 3: Show loading state
            _authState.value = AuthState.Loading
            
            // Step 4: Make API call
            withContext(Dispatchers.IO) {
                try {
                    val response = RetrofitClient.authApi.sendOTP(
                        SendOTPRequest(phone = phone, role = role)
                    )
                    
                    if (response.isSuccessful && response.body()?.success == true) {
                        _authState.value = AuthState.OTPSent(
                            phone = phone,
                            role = role,
                            message = response.body()?.data?.message ?: "OTP sent successfully"
                        )
                    } else {
                        val errorMsg = response.body()?.error?.message 
                            ?: response.errorBody()?.string() 
                            ?: "Failed to send OTP"
                        _authState.value = AuthState.Error(errorMsg)
                    }
                } catch (e: Exception) {
                    _authState.value = AuthState.Error(
                        "Network error: ${e.message ?: "Cannot connect to server"}"
                    )
                }
            }
        }
    }
    
    /**
     * Verify Driver OTP
     * Calls: POST /api/v1/auth/verify-otp with role="driver"
     */
    fun verifyDriverOTP(driverPhone: String, otp: String) {
        verifyOTP(driverPhone, otp, "driver")
    }
    
    /**
     * Verify Transporter OTP
     * Calls: POST /api/v1/auth/verify-otp with role="transporter"
     */
    fun verifyTransporterOTP(transporterPhone: String, otp: String) {
        verifyOTP(transporterPhone, otp, "transporter")
    }
    
    /**
     * Generic Verify OTP function
     */
    private fun verifyOTP(phone: String, otp: String, role: String) {
        viewModelScope.launch {
            // Validate OTP format
            val validation = InputValidator.validateOTP(otp)
            if (!validation.isValid) {
                _authState.value = AuthState.Error(validation.errorMessage!!)
                return@launch
            }
            
            _authState.value = AuthState.Loading
            
            withContext(Dispatchers.IO) {
                try {
                    val response = RetrofitClient.authApi.verifyOTP(
                        VerifyOTPRequest(phone = phone, otp = otp, role = role)
                    )
                    
                    if (response.isSuccessful && response.body()?.success == true) {
                        val data = response.body()?.data
                        
                        // Save tokens securely
                        data?.tokens?.let { tokens ->
                            RetrofitClient.saveTokens(tokens.accessToken, tokens.refreshToken)
                        }
                        
                        // Save user info
                        data?.user?.let { user ->
                            RetrofitClient.saveUserInfo(user.id, user.role)
                        }
                        
                        _authState.value = AuthState.Authenticated(
                            userId = data?.user?.id ?: "",
                            userName = data?.user?.name ?: "",
                            role = role.uppercase(),
                            authToken = data?.tokens?.accessToken ?: "",
                            refreshToken = data?.tokens?.refreshToken ?: "",
                            isNewUser = data?.isNewUser ?: false
                        )
                    } else {
                        val errorMsg = response.body()?.error?.message
                            ?: "Invalid OTP. Please try again."
                        _authState.value = AuthState.Error(errorMsg)
                    }
                } catch (e: Exception) {
                    _authState.value = AuthState.Error(
                        "Network error: ${e.message ?: "Cannot connect to server"}"
                    )
                }
            }
        }
    }
    
    /**
     * Check if user is already logged in
     */
    fun checkLoginStatus() {
        viewModelScope.launch {
            if (RetrofitClient.isLoggedIn()) {
                val userId = RetrofitClient.getUserId() ?: ""
                val role = RetrofitClient.getUserRole() ?: "TRANSPORTER"
                _authState.value = AuthState.Authenticated(
                    userId = userId,
                    userName = "",
                    role = role,
                    authToken = RetrofitClient.getAccessToken() ?: "",
                    refreshToken = RetrofitClient.getRefreshToken() ?: "",
                    isNewUser = false
                )
            }
        }
    }
    
    /**
     * Logout user - Clears all auth data
     */
    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Call logout API
                RetrofitClient.authApi.logout(RetrofitClient.getAuthHeader())
            } catch (e: Exception) {
                // Ignore errors, still clear local data
            }
            
            // Clear stored tokens and user data
            RetrofitClient.clearAllData()
            _authState.value = AuthState.LoggedOut
        }
    }
    
    /**
     * Reset state to idle
     */
    fun resetState() {
        _authState.value = AuthState.Idle
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }
}

/**
 * AuthState - Sealed class for type-safe state management
 */
sealed class AuthState {
    // Initial state
    object Idle : AuthState()
    
    // Loading state (API call in progress)
    object Loading : AuthState()
    
    // OTP sent successfully
    data class OTPSent(
        val phone: String,
        val role: String,
        val message: String
    ) : AuthState()
    
    // User authenticated successfully
    data class Authenticated(
        val userId: String,
        val userName: String,
        val role: String, // "DRIVER" or "TRANSPORTER"
        val authToken: String,
        val refreshToken: String,
        val isNewUser: Boolean = false
    ) : AuthState()
    
    // Error occurred
    data class Error(val message: String) : AuthState()
    
    // Rate limit exceeded
    data class RateLimited(val retryAfterSeconds: Long) : AuthState()
    
    // User logged out
    object LoggedOut : AuthState()
}
