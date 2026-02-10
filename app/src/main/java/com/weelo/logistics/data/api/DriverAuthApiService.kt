package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * =============================================================================
 * DRIVER AUTH API SERVICE
 * =============================================================================
 * 
 * Separate authentication service for DRIVERS.
 * 
 * FLOW:
 * 1. Driver enters their phone number
 * 2. System finds which transporter this driver belongs to
 * 3. OTP is sent to TRANSPORTER's phone (not driver's)
 * 4. Driver gets OTP from transporter and enters it
 * 5. Driver gets authenticated and can access Driver Dashboard
 * 
 * ENDPOINTS:
 * POST /api/v1/driver-auth/send-otp    - Send OTP to transporter
 * POST /api/v1/driver-auth/verify-otp  - Verify OTP and get tokens
 * =============================================================================
 */
interface DriverAuthApiService {

    /**
     * Send OTP for driver login
     * OTP is sent to the TRANSPORTER's phone, not driver's
     * 
     * @param request Contains driver's phone number
     * @return Response with masked transporter phone for UI hint
     */
    @POST("driver-auth/send-otp")
    suspend fun sendOtp(
        @Body request: DriverSendOtpRequest
    ): Response<DriverSendOtpResponse>

    /**
     * Verify OTP and authenticate driver
     * 
     * @param request Contains driver's phone and OTP received from transporter
     * @return Response with JWT tokens and driver profile
     */
    @POST("driver-auth/verify-otp")
    suspend fun verifyOtp(
        @Body request: DriverVerifyOtpRequest
    ): Response<DriverVerifyOtpResponse>

    /**
     * Get pending OTP for testing (Development only)
     * 
     * @param driverPhone Driver's phone number
     * @return The pending OTP if exists
     */
    @GET("driver-auth/debug-otp")
    suspend fun getDebugOtp(
        @Query("driverPhone") driverPhone: String
    ): Response<DriverDebugOtpResponse>
}

// =============================================================================
// REQUEST MODELS
// =============================================================================

/**
 * Request to send OTP for driver login
 */
data class DriverSendOtpRequest(
    val driverPhone: String
)

/**
 * Request to verify OTP and login driver
 */
data class DriverVerifyOtpRequest(
    val driverPhone: String,
    val otp: String
)

// =============================================================================
// RESPONSE MODELS
// =============================================================================

/**
 * Response after requesting OTP
 */
data class DriverSendOtpResponse(
    val success: Boolean,
    val message: String?,
    val data: DriverOtpData?,
    val error: ApiError?
)

data class DriverOtpData(
    val transporterPhoneMasked: String,  // e.g., "78****631"
    val driverId: String,
    val driverName: String,
    val expiresInMinutes: Int
)

/**
 * Response after verifying OTP - contains tokens and driver info
 */
data class DriverVerifyOtpResponse(
    val success: Boolean,
    val message: String?,
    val data: DriverAuthData?,
    val error: ApiError?
)

data class DriverAuthData(
    val accessToken: String,
    val refreshToken: String,
    val driver: DriverProfile,
    val role: String  // "DRIVER"
)

data class DriverProfile(
    val id: String,
    val name: String,
    val phone: String,
    val transporterId: String,
    val transporterName: String,
    val licenseNumber: String?,
    val profilePhoto: String?,
    val preferredLanguage: String? = null,
    val isProfileCompleted: Boolean = false
)

/**
 * Debug OTP response (Development only)
 */
data class DriverDebugOtpResponse(
    val success: Boolean,
    val data: DebugOtpData?
)

data class DebugOtpData(
    val otp: String?,
    val message: String
)
