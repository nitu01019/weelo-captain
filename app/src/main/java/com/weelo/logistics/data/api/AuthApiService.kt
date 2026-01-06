package com.weelo.logistics.data.api

import com.weelo.logistics.data.model.User
import retrofit2.Response
import retrofit2.http.*

/**
 * Authentication API Service
 * 
 * BACKEND INTEGRATION NOTES:
 * ==========================
 * Base URL: https://api.weelo.in/v1/
 * 
 * All endpoints require proper error handling:
 * - 200: Success
 * - 400: Bad Request (validation errors)
 * - 401: Unauthorized (invalid credentials)
 * - 404: Not Found
 * - 500: Server Error
 * 
 * Authentication Flow:
 * 1. User enters mobile number -> sendOTP()
 * 2. User enters OTP code -> verifyOTP()
 * 3. Backend returns JWT token
 * 4. Store token securely (use EncryptedSharedPreferences)
 * 5. Include token in all subsequent requests via Interceptor
 */
interface AuthApiService {
    
    /**
     * Send OTP to mobile number
     * 
     * ENDPOINT: POST /auth/send-otp
     * 
     * Request Body:
     * {
     *   "mobileNumber": "9876543210",
     *   "countryCode": "+91"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "message": "OTP sent successfully",
     *   "otpId": "otp_123456",
     *   "expiresIn": 300
     * }
     * 
     * Response (400 Bad Request):
     * {
     *   "success": false,
     *   "error": "Invalid mobile number"
     * }
     */
    @POST("auth/send-otp")
    suspend fun sendOTP(
        @Body request: SendOTPRequest
    ): Response<SendOTPResponse>
    
    /**
     * Verify OTP and login/signup
     * 
     * ENDPOINT: POST /auth/verify-otp
     * 
     * Request Body:
     * {
     *   "mobileNumber": "9876543210",
     *   "otp": "123456",
     *   "otpId": "otp_123456",
     *   "deviceId": "device_unique_id",
     *   "fcmToken": "fcm_token_for_push_notifications"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "user": {
     *     "id": "user_123",
     *     "name": "John Doe",
     *     "mobileNumber": "9876543210",
     *     "email": "john@example.com",
     *     "roles": ["DRIVER", "TRANSPORTER"],
     *     "isNewUser": false
     *   },
     *   "token": {
     *     "accessToken": "jwt_access_token",
     *     "refreshToken": "jwt_refresh_token",
     *     "expiresIn": 3600
     *   }
     * }
     * 
     * Response (401 Unauthorized):
     * {
     *   "success": false,
     *   "error": "Invalid OTP"
     * }
     */
    @POST("auth/verify-otp")
    suspend fun verifyOTP(
        @Body request: VerifyOTPRequest
    ): Response<AuthResponse>
    
    /**
     * Complete user profile (for new users after OTP verification)
     * 
     * ENDPOINT: POST /auth/complete-profile
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "name": "John Doe",
     *   "email": "john@example.com",
     *   "role": "DRIVER" // or "TRANSPORTER"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "user": {User object with updated details}
     * }
     */
    @POST("auth/complete-profile")
    suspend fun completeProfile(
        @Header("Authorization") token: String,
        @Body request: CompleteProfileRequest
    ): Response<User>
    
    /**
     * Refresh access token using refresh token
     * 
     * ENDPOINT: POST /auth/refresh-token
     * 
     * Request Body:
     * {
     *   "refreshToken": "jwt_refresh_token"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "accessToken": "new_jwt_access_token",
     *   "expiresIn": 3600
     * }
     */
    @POST("auth/refresh-token")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<RefreshTokenResponse>
    
    /**
     * Logout user
     * 
     * ENDPOINT: POST /auth/logout
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "deviceId": "device_unique_id",
     *   "fcmToken": "fcm_token"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "message": "Logged out successfully"
     * }
     */
    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String,
        @Body request: LogoutRequest
    ): Response<LogoutResponse>
    
    /**
     * Get current user profile
     * 
     * ENDPOINT: GET /auth/me
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "user": {User object}
     * }
     */
    @GET("auth/me")
    suspend fun getCurrentUser(
        @Header("Authorization") token: String
    ): Response<User>
}

// ============== Request/Response Data Classes ==============

data class SendOTPRequest(
    val mobileNumber: String,
    val countryCode: String = "+91"
)

data class SendOTPResponse(
    val success: Boolean,
    val message: String,
    val otpId: String,
    val expiresIn: Int
)

data class VerifyOTPRequest(
    val mobileNumber: String,
    val otp: String,
    val otpId: String,
    val deviceId: String,
    val fcmToken: String?
)

data class AuthResponse(
    val success: Boolean,
    val user: User,
    val token: TokenData,
    val error: String? = null
)

data class TokenData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

data class CompleteProfileRequest(
    val name: String,
    val email: String?,
    val role: String // "DRIVER" or "TRANSPORTER"
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class RefreshTokenResponse(
    val success: Boolean,
    val accessToken: String,
    val expiresIn: Int
)

data class LogoutRequest(
    val deviceId: String,
    val fcmToken: String?
)

data class LogoutResponse(
    val success: Boolean,
    val message: String
)
