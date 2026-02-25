package com.weelo.logistics.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.WeeloApp
import com.weelo.logistics.data.api.SendOTPRequest
import com.weelo.logistics.data.api.VerifyOTPRequest
import com.weelo.logistics.data.api.DriverSendOtpRequest
import com.weelo.logistics.data.api.DriverVerifyOtpRequest
import com.weelo.logistics.data.remote.NotificationTokenSync
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.utils.AppSignatureHelper
import com.weelo.logistics.utils.AuthOtpAutofillCoordinator
import com.weelo.logistics.utils.AuthOtpAutofillCoordinator.OtpAutofillClearReason
import com.weelo.logistics.utils.GlobalRateLimiters
import com.weelo.logistics.utils.InputValidator
import com.weelo.logistics.utils.RoleScopedLocalePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

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
    private var appHashLoggedForSession = false

    private val _uiEffects = MutableSharedFlow<AuthUiEffect>(extraBufferCapacity = 16)
    val uiEffects: SharedFlow<AuthUiEffect> = _uiEffects.asSharedFlow()

    val otpAutofillUiState: StateFlow<AuthOtpAutofillCoordinator.OtpAutofillUiState> =
        AuthOtpAutofillCoordinator.uiState

    suspend fun prepareOtpAutofillForSend(context: Context, phone: String, role: String): Long? {
        return try {
            withTimeoutOrNull(1_500) {
                AuthOtpAutofillCoordinator.prepareForOtpSend(
                    context = context.applicationContext,
                    phone = phone,
                    role = role
                )
            }.also { sessionId ->
                if (sessionId == null) {
                    timber.log.Timber.w(
                        "OTP autofill prewarm timed out; proceeding with OTP send (phone=%s, role=%s)",
                        phone.takeLast(4),
                        role
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.w(e, "OTP autofill prewarm failed; proceeding with manual OTP fallback")
            null
        }
    }

    suspend fun attachOtpAutofill(context: Context, phone: String, role: String) {
        AuthOtpAutofillCoordinator.attachOtpScreen(
            context = context.applicationContext,
            phone = phone,
            role = role
        )
        if (!appHashLoggedForSession && timber.log.Timber.treeCount > 0) {
            AppSignatureHelper.getAppSignatures(context.applicationContext)
            appHashLoggedForSession = true
        }
    }

    suspend fun clearOtpAutofill(reason: OtpAutofillClearReason) {
        AuthOtpAutofillCoordinator.clearSession(reason)
    }

    suspend fun restartOtpAutofill(context: Context, phone: String, role: String) {
        AuthOtpAutofillCoordinator.restartForResend(
            context = context.applicationContext,
            phone = phone,
            role = role
        )
    }

    fun resendOtp(context: Context, phone: String, role: String) {
        viewModelScope.launch {
            restartOtpAutofill(context, phone, role)
            if (role.equals("driver", ignoreCase = true)) {
                sendDriverOTP(phone, skipLocalRateLimit = true)
            } else {
                sendTransporterOTP(phone, skipLocalRateLimit = true)
            }
        }
    }

    fun verifyOtpForRole(phone: String, role: String, otp: String) {
        if (role.equals("driver", ignoreCase = true)) {
            verifyDriverOTP(phone, otp)
        } else {
            verifyTransporterOTP(phone, otp)
        }
    }
    
    /**
     * Send OTP for Driver Login
     * 
     * IMPORTANT: Uses driver-auth endpoint!
     * OTP is sent to TRANSPORTER's phone, NOT driver's phone.
     * Driver must ask their transporter for the OTP.
     * 
     * Calls: POST /api/v1/driver-auth/send-otp
     */
    fun sendDriverOTP(driverPhone: String, skipLocalRateLimit: Boolean = false) {
        viewModelScope.launch {
            // Step 1: Validate input
            val validation = InputValidator.validatePhoneNumber(driverPhone)
            if (!validation.isValid) {
                _authState.value = AuthState.Error(validation.errorMessage!!)
                return@launch
            }
            
            // Step 2: Check rate limiting
            if (!skipLocalRateLimit) {
                val rateLimit = otpRateLimiter.tryAcquire(driverPhone)
                if (!rateLimit) {
                    val retryAfter = otpRateLimiter.getTimeUntilReset(driverPhone) / 1000
                    _authState.value = AuthState.RateLimited(retryAfter)
                    return@launch
                }
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
                            ?.takeIf { !it.startsWith("{") }
                            ?: extractFriendlyError(response, "Failed to send OTP. Driver not found or not registered.")
                        _authState.value = AuthState.Error(errorMsg)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _authState.value = AuthState.Error(
                        when {
                            e.message?.contains("timeout", true) == true -> "Connection timed out. Please check your internet and try again."
                            e.message?.contains("Unable to resolve", true) == true -> "No internet connection. Please check your network."
                            e.message?.contains("Connection refused", true) == true -> "Server is not reachable. Please try again later."
                            else -> "Something went wrong. Please check your internet connection and try again."
                        }
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
        sendTransporterOTP(transporterPhone, skipLocalRateLimit = false)
    }

    fun sendTransporterOTP(transporterPhone: String, skipLocalRateLimit: Boolean = false) {
        sendOTP(transporterPhone, "transporter", skipLocalRateLimit)
    }
    
    /**
     * Generic Send OTP function (for Customer/Transporter)
     * NOT used for Driver - drivers use sendDriverOTP()
     */
    private fun sendOTP(phone: String, role: String, skipLocalRateLimit: Boolean = false) {
        viewModelScope.launch {
            // Step 1: Validate input
            val validation = InputValidator.validatePhoneNumber(phone)
            if (!validation.isValid) {
                _authState.value = AuthState.Error(validation.errorMessage!!)
                return@launch
            }
            
            // Step 2: Check rate limiting
            if (!skipLocalRateLimit) {
                val rateLimit = otpRateLimiter.tryAcquire(phone)
                if (!rateLimit) {
                    val retryAfter = otpRateLimiter.getTimeUntilReset(phone) / 1000
                    _authState.value = AuthState.RateLimited(retryAfter)
                    return@launch
                }
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
                            ?.takeIf { !it.startsWith("{") }
                            ?: extractFriendlyError(response, "Failed to send OTP. Please try again.")
                        _authState.value = AuthState.Error(errorMsg)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _authState.value = AuthState.Error(
                        when {
                            e.message?.contains("timeout", true) == true -> "Connection timed out. Please check your internet and try again."
                            e.message?.contains("Unable to resolve", true) == true -> "No internet connection. Please check your network."
                            e.message?.contains("Connection refused", true) == true -> "Server is not reachable. Please try again later."
                            else -> "Something went wrong. Please check your internet connection and try again."
                        }
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
                            val driverPrefs = com.weelo.logistics.data.preferences.DriverPreferences.getInstance(ctx)
                            driverPrefs.restoreLanguageIfNeeded(data?.driver?.preferredLanguage)
                            if (data?.driver?.isProfileCompleted == true) {
                                driverPrefs.markProfileCompleted()
                            }
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
                            transporterName = data?.driver?.transporterName,
                            preferredLanguage = data?.driver?.preferredLanguage,
                            isProfileCompleted = data?.driver?.isProfileCompleted ?: false
                        )
                        _uiEffects.tryEmit(AuthUiEffect.OtpVerificationSucceeded)
                    } else {
                        val errorMsg = response.body()?.error?.message
                            ?.takeIf { !it.startsWith("{") }
                            ?: extractFriendlyError(response, "Invalid OTP. Please check and try again.")
                        _authState.value = AuthState.Error(errorMsg)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _authState.value = AuthState.Error(
                        when {
                            e.message?.contains("timeout", true) == true -> "Connection timed out. Please check your internet and try again."
                            e.message?.contains("Unable to resolve", true) == true -> "No internet connection. Please check your network."
                            e.message?.contains("Connection refused", true) == true -> "Server is not reachable. Please try again later."
                            else -> "Something went wrong. Please check your internet connection and try again."
                        }
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
                        
                        // ROLE-ISOLATED LOCALE: Driver language restore must never
                        // leak into transporter sessions.
                        WeeloApp.getInstance()?.applicationContext?.let { ctx ->
                            val sharedPrefs = ctx.getSharedPreferences("weelo_prefs", Context.MODE_PRIVATE)
                            if (role.equals("driver", ignoreCase = true)) {
                                val driverPrefs = com.weelo.logistics.data.preferences.DriverPreferences.getInstance(ctx)
                                driverPrefs.restoreLanguageIfNeeded(data?.preferredLanguage)
                            } else if (role.equals("transporter", ignoreCase = true)) {
                                RoleScopedLocalePolicy.markTransporterNoLocale(
                                    prefs = sharedPrefs,
                                    userId = data?.user?.id
                                )
                            }
                        }
                        
                        // Connect WebSocket for real-time broadcasts
                        WeeloApp.getInstance()?.connectWebSocketIfLoggedIn()
                        
                        _authState.value = AuthState.Authenticated(
                            userId = data?.user?.id ?: "",
                            userName = data?.user?.name ?: "",
                            role = role.uppercase(),
                            authToken = data?.tokens?.accessToken ?: "",
                            refreshToken = data?.tokens?.refreshToken ?: "",
                            isNewUser = data?.isNewUser ?: false,
                            preferredLanguage = data?.preferredLanguage
                        )
                        _uiEffects.tryEmit(AuthUiEffect.OtpVerificationSucceeded)
                    } else {
                        val errorMsg = response.body()?.error?.message
                            ?.takeIf { !it.startsWith("{") }
                            ?: extractFriendlyError(response, "Invalid OTP. Please check and try again.")
                        _authState.value = AuthState.Error(errorMsg)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _authState.value = AuthState.Error(
                        when {
                            e.message?.contains("timeout", true) == true -> "Connection timed out. Please check your internet and try again."
                            e.message?.contains("Unable to resolve", true) == true -> "No internet connection. Please check your network."
                            e.message?.contains("Connection refused", true) == true -> "Server is not reachable. Please try again later."
                            else -> "Something went wrong. Please check your internet connection and try again."
                        }
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
            // Step 1: Go offline FIRST — removes driver from Redis presence + transporter set
            // This ensures the driver is IMMEDIATELY invisible to transporters.
            // If this fails (network issue), Redis TTL (35s) will auto-expire anyway.
            try {
                RetrofitClient.driverApi.updateAvailability(
                    com.weelo.logistics.data.api.UpdateAvailabilityRequest(isOnline = false)
                )
                timber.log.Timber.i("✅ Driver went offline before logout")
            } catch (_: Exception) {
                timber.log.Timber.w("⚠️ goOffline failed during logout (TTL will handle cleanup)")
            }

            NotificationTokenSync.unregisterCurrentToken(reason = "auth_logout")

            // Step 2: Call logout API
            try {
                RetrofitClient.authApi.logout(RetrofitClient.getAuthHeader())
            } catch (_: Exception) {
                // Ignore errors, still clear local data
            }
            
            // Step 3: Disconnect WebSocket (heartbeat stops here too)
            WeeloApp.getInstance()?.disconnectWebSocket()
            
            // Clear stored tokens and user data
            RetrofitClient.clearAllData()
            
            // Clear DriverPreferences (language, etc.)
            // On next login, language will be restored from backend response
            WeeloApp.getInstance()?.applicationContext?.let { ctx ->
                val driverPrefs = com.weelo.logistics.data.preferences.DriverPreferences.getInstance(ctx)
                driverPrefs.clearAll()
            }
            
            _authState.value = AuthState.LoggedOut
        }
    }
    
    // =========================================================================
    // FRIENDLY ERROR MESSAGE PARSER
    // =========================================================================
    // 
    // Converts raw API error responses into plain English messages.
    // Handles three cases:
    //   1. response.body()?.error?.message — server returned a message field
    //   2. response.errorBody() — non-2xx response with JSON body
    //   3. Fallback default message
    //
    // Error codes are mapped to user-friendly messages so users never see
    // raw JSON or technical error codes on screen.
    // =========================================================================
    
    /**
     * Extracts a clean, plain English error message from an API response.
     * Never returns raw JSON — always a human-readable string.
     */
    private fun extractFriendlyError(response: retrofit2.Response<*>, defaultMsg: String): String {
        // Parse error body JSON
        try {
            val errorBodyStr = response.errorBody()?.string()
            if (!errorBodyStr.isNullOrBlank()) {
                val json = JSONObject(errorBodyStr)
                
                // Extract error code and message
                val errorObj = json.optJSONObject("error")
                val code = errorObj?.optString("code", "") ?: ""
                val message = errorObj?.optString("message", "") ?: ""
                
                // Map error codes to friendly messages
                return mapErrorCodeToFriendlyMessage(code, message, defaultMsg)
            }
        } catch (_: Exception) {}
        
        return defaultMsg
    }
    
    /**
     * Maps backend error codes to plain English messages.
     * Users should never see codes like "OTP_RATE_LIMIT_EXCEEDED" —
     * instead they see "Too many attempts. Please try again in 10 minutes."
     */
    private fun mapErrorCodeToFriendlyMessage(code: String, serverMessage: String, defaultMsg: String): String {
        return when (code) {
            // OTP Rate Limiting
            "OTP_RATE_LIMIT_EXCEEDED" -> "Too many OTP attempts. Please try again in 2 minutes."
            "RATE_LIMIT_EXCEEDED" -> "Too many requests. Please wait a moment and try again."
            "TOO_MANY_REQUESTS" -> "Too many requests. Please wait a moment and try again."
            
            // OTP Errors
            "OTP_EXPIRED" -> "OTP has expired. Please request a new one."
            "INVALID_OTP" -> "Incorrect OTP. Please check and try again."
            "OTP_INVALID" -> "Incorrect OTP. Please check and try again."
            "OTP_NOT_FOUND" -> "No OTP request found. Please request a new OTP."
            "OTP_ALREADY_VERIFIED" -> "This OTP has already been used. Please request a new one."
            "OTP_MAX_ATTEMPTS" -> "Too many incorrect attempts. Please request a new OTP."
            "MAX_ATTEMPTS" -> "Too many incorrect attempts. Please request a new OTP."
            "OTP_SEND_FAILED" -> "Could not send OTP. Please check your number and try again."
            "OTP_VERIFY_IN_PROGRESS" -> "OTP verification is already in progress. Please try again."
            
            // User/Account Errors
            "USER_NOT_FOUND" -> "No account found with this phone number."
            "DRIVER_NOT_FOUND" -> "Driver not found. Please check your phone number or contact your transporter."
            "TRANSPORTER_NOT_FOUND" -> "Transporter account not found."
            "ACCOUNT_DISABLED" -> "Your account has been disabled. Please contact support."
            "ACCOUNT_SUSPENDED" -> "Your account is suspended. Please contact support."
            "UNAUTHORIZED" -> "Session expired. Please log in again."
            "FORBIDDEN" -> "You don't have permission for this action."
            
            // Network/Server Errors
            "SERVICE_UNAVAILABLE" -> "Service is temporarily unavailable. Please try again shortly."
            "INTERNAL_ERROR" -> "Something went wrong. Please try again."
            "SERVER_ERROR" -> "Something went wrong on our end. Please try again."
            "TIMEOUT" -> "Request timed out. Please check your connection and try again."
            
            // Validation Errors
            "INVALID_PHONE" -> "Please enter a valid 10-digit phone number."
            "INVALID_REQUEST" -> "Something went wrong. Please try again."
            "VALIDATION_ERROR" -> serverMessage.ifBlank { "Please check your input and try again." }
            
            // If code is unknown but server sent a message, use that message
            // (but clean it — remove any JSON-like syntax)
            else -> {
                if (serverMessage.isNotBlank() && !serverMessage.startsWith("{")) {
                    serverMessage
                } else {
                    defaultMsg
                }
            }
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
        val transporterName: String? = null,
        val preferredLanguage: String? = null,
        val isProfileCompleted: Boolean? = null
    ) : AuthState()
    
    // Error occurred - show error message
    data class Error(val message: String) : AuthState()
    
    // Rate limit exceeded - show countdown
    data class RateLimited(val retryAfterSeconds: Long) : AuthState()
    
    // User logged out - navigate to login
    object LoggedOut : AuthState()
}

sealed class AuthUiEffect {
    object OtpVerificationSucceeded : AuthUiEffect()
}
