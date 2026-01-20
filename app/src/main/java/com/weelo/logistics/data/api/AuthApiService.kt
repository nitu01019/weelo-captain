package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Authentication API Service - Matches weelo-backend
 * 
 * Backend: weelo-backend/src/modules/auth/
 * Base URL: http://10.0.2.2:3000/api/v1/ (emulator) or your IP for device
 * 
 * Authentication Flow:
 * 1. POST /auth/send-otp - Send OTP to phone
 * 2. POST /auth/verify-otp - Verify OTP, get tokens
 * 3. Store accessToken & refreshToken securely
 * 4. Include "Authorization: Bearer {accessToken}" in all requests
 * 
 * Development: OTPs are logged to backend console - check terminal
 */
interface AuthApiService {
    
    /**
     * Send OTP to phone number
     * POST /api/v1/auth/send-otp
     */
    @POST("auth/send-otp")
    suspend fun sendOTP(
        @Body request: SendOTPRequest
    ): Response<SendOTPResponse>
    
    /**
     * Verify OTP and get tokens
     * POST /api/v1/auth/verify-otp
     */
    @POST("auth/verify-otp")
    suspend fun verifyOTP(
        @Body request: VerifyOTPRequest
    ): Response<VerifyOTPResponse>
    
    /**
     * Refresh access token
     * POST /api/v1/auth/refresh
     */
    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<RefreshTokenResponse>
    
    /**
     * Logout and invalidate tokens
     * POST /api/v1/auth/logout
     */
    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): Response<LogoutResponse>
    
    /**
     * Get current user info
     * GET /api/v1/auth/me
     */
    @GET("auth/me")
    suspend fun getCurrentUser(
        @Header("Authorization") token: String
    ): Response<GetCurrentUserResponse>
}

// ============== Request Data Classes ==============

/**
 * Send OTP Request
 * Matches: weelo-backend/src/modules/auth/auth.schema.ts -> sendOtpSchema
 */
data class SendOTPRequest(
    val phone: String,  // 10-digit phone number e.g. "9876543210"
    val role: String = "transporter"  // "customer", "transporter", or "driver"
)

/**
 * Verify OTP Request
 * Matches: weelo-backend/src/modules/auth/auth.schema.ts -> verifyOtpSchema
 */
data class VerifyOTPRequest(
    val phone: String,      // 10-digit phone number
    val otp: String,        // 6-digit OTP from backend console
    val role: String = "transporter",
    val deviceId: String? = null,
    val deviceName: String? = null
)

/**
 * Refresh Token Request
 * Matches: weelo-backend/src/modules/auth/auth.schema.ts -> refreshTokenSchema
 */
data class RefreshTokenRequest(
    val refreshToken: String
)

// ============== Response Data Classes ==============

/**
 * Standard API Error format from backend
 */
data class ApiError(
    val code: String? = null,
    val message: String? = null
)

/**
 * Send OTP Response
 */
data class SendOTPResponse(
    val success: Boolean,
    val data: SendOTPData? = null,
    val error: ApiError? = null
)

data class SendOTPData(
    val message: String,
    val expiresIn: Int
)

/**
 * Verify OTP Response
 * Returns user info and JWT tokens
 */
data class VerifyOTPResponse(
    val success: Boolean,
    val data: VerifyOTPData? = null,
    val error: ApiError? = null
)

data class VerifyOTPData(
    val user: AuthUser,
    val tokens: TokenData,
    val isNewUser: Boolean
)

data class AuthUser(
    val id: String,
    val phone: String,
    val role: String,
    val name: String? = null,
    val email: String? = null,
    val company: String? = null,
    val isProfileComplete: Boolean = false
)

data class TokenData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long  // seconds until expiry
)

/**
 * Refresh Token Response
 */
data class RefreshTokenResponse(
    val success: Boolean,
    val data: RefreshTokenData? = null,
    val error: ApiError? = null
)

data class RefreshTokenData(
    val accessToken: String,
    val expiresIn: Long
)

/**
 * Logout Response
 */
data class LogoutResponse(
    val success: Boolean,
    val message: String? = null,
    val error: ApiError? = null
)

/**
 * Get Current User Response
 */
data class GetCurrentUserResponse(
    val success: Boolean,
    val data: CurrentUserData? = null,
    val error: ApiError? = null
)

data class CurrentUserData(
    val user: AuthUser
)
