package com.weelo.logistics.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.WeeloApp
import com.weelo.logistics.data.api.SendOTPRequest
import com.weelo.logistics.data.api.VerifyOTPRequest
import com.weelo.logistics.data.api.DriverSendOtpRequest
import com.weelo.logistics.data.api.DriverVerifyOtpRequest
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
 * =============================================================================
 * AUTH VIEW MODEL - Connected to weelo-backend
 * =============================================================================
 * 
 * Handles authentication for both TRANSPORTER and DRIVER roles.
 * 
 * IMPORTANT: Different OTP flows for different roles!
 * 
 * TRANSPORTER LOGIN:
 * - Uses: /api/v1/auth/send-otp and /api/v1/auth/verify-otp
 * - OTP sent to: Transporter's own phone
 * 
 * DRIVER LOGIN:
 * - Uses: /api/v1/driver-auth/send-otp and /api/v1/driver-auth/verify-otp
 * - OTP sent to: TRANSPORTER's phone (NOT driver's!)
 * - Driver must ask transporter for the OTP
 * 
 * Development: OTPs are logged to backend console - check terminal
 * =============================================================================
 */
class AuthViewModel : ViewModel() {
    
    // UI State - Reactive state management with StateFlow
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    // Rate limiters
    private val otpRateLimiter = GlobalRateLimiters.otp
    
    /**
     * Send OTP for Driver Login
     * 
     * IMPORTANT: Uses driver-auth endpoint!
     * OTP is sent to TRANSPORTER's phone, NOT driver's phone.
     * Driver must ask their transporter for the OTP.
     * 
     * Calls: POST /api/v1/driver-auth/send-otp
     */
    fun sendDriverOTP(driverPhone: String) {
        viewModelScope.launch {
            // Step 1: Validate input
            val validation = InputValidator.validatePhoneNumber(driverPhone)
            if (!validation.isValid) {
                _authState.value = AuthState.Error(validation.errorMessage!!)
                return@launch
            }
            
            // Step 2: Check rate limiting
            val rateLimit = otpRateLimiter.tryAcquire(driverPhone)
            if (!rateLimit) {
                val retryAfter = otpRateLimiter.getTimeUntilReset(driverPhone) / 1000
                _authState.value = AuthState.RateLimited(retryAfter)
                return@launch
            }
            
            // Step 3: Show loading state
            _authState.value = AuthState.Loading
            
            // Step 4: Make API call to DRIVER-AUTH endpoint
            withContext(Dispatchers.IO) {
                try {
                    val response = RetrofitClient.driverAuthApi.sendOtp(
                        DriverSendOtpRequest(driverPhone = driverPhone)
                    )
                    
                    if (response.isSuccessful && response.body()?.success == true) {
                        val data = response.body()?.data
                        _authState.value = AuthState.OTPSent(
                            phone = driverPhone,
                            role = "driver",
                            message = response.body()?.message 
                                ?: "OTP sent to your transporter (${data?.transporterPhoneMasked}). Please ask them for the code.",
                            transporterPhoneMasked = data?.transporterPhoneMasked,
                            driverName = data?.driverName
                        )
                    } else {
                        val errorMsg = response.body()?.error?.message 
                            ?: response.body()?.message
                            ?: response.errorBody()?.string() 
                            ?: "Failed to send OTP. Driver not found or not registered."
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
     * Send OTP for Transporter Login
     * 
     * OTP is sent to transporter's own phone.
     * 
     * Calls: POST /api/v1/auth/send-otp with role="transporter"
     */
    fun sendTransporterOTP(transporterPhone: String) {
        sendOTP(transporterPhone, "transporter")
    }
    
    /**
     * Generic Send OTP function (for Customer/Transporter)
     * NOT used for Driver - drivers use sendDriverOTP()
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
     * 
     * IMPORTANT: Uses driver-auth endpoint!
     * 
     * Calls: POST /api/v1/driver-auth/verify-otp
     */
    fun verifyDriverOTP(driverPhone: String, otp: String) {
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
                    val response = RetrofitClient.driverAuthApi.verifyOtp(
                        DriverVerifyOtpRequest(driverPhone = driverPhone, otp = otp)
                    )
                    
                    if (response.isSuccessful && response.body()?.success == true) {
                        val data = response.body()?.data
                        
                        // Save tokens securely
                        data?.let {
                            RetrofitClient.saveTokens(it.accessToken, it.refreshToken)
                        }
                        
                        // Save user info (driver)
                        data?.driver?.let { driver ->
                            RetrofitClient.saveUserInfo(driver.id, "DRIVER")
                        }
                        
                        // SCALABILITY: Restore language preference from backend
                        // in one shot — no extra API call needed
                        WeeloApp.getInstance()?.applicationContext?.let { ctx ->
                            val driverPrefs = com.weelo.logistics.data.preferences.DriverPreferences(ctx)
                            driverPrefs.restoreLanguageIfNeeded(data?.driver?.preferredLanguage)
                        }
                        
                        // Connect WebSocket for real-time broadcasts
                        WeeloApp.getInstance()?.connectWebSocketIfLoggedIn()
                        
                        _authState.value = AuthState.Authenticated(
                            userId = data?.driver?.id ?: "",
                            userName = data?.driver?.name ?: "",
                            role = "DRIVER",
                            authToken = data?.accessToken ?: "",
                            refreshToken = data?.refreshToken ?: "",
                            isNewUser = false,
                            transporterId = data?.driver?.transporterId,
                            transporterName = data?.driver?.transporterName
                        )
                    } else {
                        val errorMsg = response.body()?.error?.message
                            ?: response.body()?.message
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
     * Verify Transporter OTP
     * Calls: POST /api/v1/auth/verify-otp with role="transporter"
     */
    fun verifyTransporterOTP(transporterPhone: String, otp: String) {
        verifyOTP(transporterPhone, otp, "transporter")
    }
    
    /**
     * Generic Verify OTP function (for Customer/Transporter)
     * NOT used for Driver - drivers use verifyDriverOTP()
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
                        
                        // SCALABILITY: Restore language from backend on login
                        WeeloApp.getInstance()?.applicationContext?.let { ctx ->
                            val driverPrefs = com.weelo.logistics.data.preferences.DriverPreferences(ctx)
                            driverPrefs.restoreLanguageIfNeeded(data?.preferredLanguage)
                        }
                        
                        // Connect WebSocket for real-time broadcasts
                        WeeloApp.getInstance()?.connectWebSocketIfLoggedIn()
                        
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
    /**
     * Logout user — clears ALL local data including language preference.
     *
     * MODULARITY: DriverPreferences.clearAll() resets language to "" (empty),
     *             so next login will either restore from backend or show
     *             language selection screen.
     * SCALABILITY: Backend language is preserved — only local data cleared.
     */
    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Call logout API
                RetrofitClient.authApi.logout(RetrofitClient.getAuthHeader())
            } catch (_: Exception) {
                // Ignore errors, still clear local data
            }
            
            // Disconnect WebSocket
            WeeloApp.getInstance()?.disconnectWebSocket()
            
            // Clear stored tokens and user data
            RetrofitClient.clearAllData()
            
            // Clear DriverPreferences (language, etc.)
            // On next login, language will be restored from backend response
            WeeloApp.getInstance()?.applicationContext?.let { ctx ->
                val driverPrefs = com.weelo.logistics.data.preferences.DriverPreferences(ctx)
                driverPrefs.clearAll()
            }
            
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
 * =============================================================================
 * AUTH STATE - Type-safe state management
 * =============================================================================
 * 
 * Represents all possible states during authentication flow.
 * Used by UI to show appropriate screens/messages.
 */
sealed class AuthState {
    // Initial state - show login form
    object Idle : AuthState()
    
    // Loading state - show progress indicator
    object Loading : AuthState()
    
    /**
     * OTP sent successfully
     * 
     * For TRANSPORTER: OTP sent to their own phone
     * For DRIVER: OTP sent to transporter's phone (transporterPhoneMasked shows hint)
     */
    data class OTPSent(
        val phone: String,
        val role: String,
        val message: String,
        // Driver-specific: masked transporter phone for UI hint
        val transporterPhoneMasked: String? = null,
        val driverName: String? = null
    ) : AuthState()
    
    /**
     * User authenticated successfully
     * 
     * For DRIVER: includes transporterId and transporterName
     */
    data class Authenticated(
        val userId: String,
        val userName: String,
        val role: String, // "DRIVER" or "TRANSPORTER"
        val authToken: String,
        val refreshToken: String,
        val isNewUser: Boolean = false,
        // Driver-specific: which transporter this driver belongs to
        val transporterId: String? = null,
        val transporterName: String? = null
    ) : AuthState()
    
    // Error occurred - show error message
    data class Error(val message: String) : AuthState()
    
    // Rate limit exceeded - show countdown
    data class RateLimited(val retryAfterSeconds: Long) : AuthState()
    
    // User logged out - navigate to login
    object LoggedOut : AuthState()
}
