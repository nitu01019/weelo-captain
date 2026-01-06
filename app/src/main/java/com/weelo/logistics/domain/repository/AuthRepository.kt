package com.weelo.logistics.domain.repository

import com.weelo.logistics.data.api.*
import com.weelo.logistics.data.model.User
import com.weelo.logistics.data.remote.RetrofitClient

/**
 * Authentication Repository
 * 
 * This repository handles all authentication-related operations.
 * It acts as a clean abstraction layer between UI and API.
 * 
 * BACKEND INTEGRATION:
 * ====================
 * Replace mock implementations with actual API calls using RetrofitClient
 * 
 * USAGE IN VIEWMODEL:
 * ===================
 * val authRepository = AuthRepository()
 * val result = authRepository.sendOTP("9876543210")
 * 
 * result.onSuccess { otpResponse ->
 *     // OTP sent successfully
 *     navigateToOTPScreen(otpResponse.otpId)
 * }.onFailure { error ->
 *     // Show error message
 *     showError(error.message)
 * }
 */
class AuthRepository {
    
    private val authApi = RetrofitClient.authApiService
    
    /**
     * Send OTP to mobile number
     * 
     * @param mobileNumber Mobile number (10 digits)
     * @param countryCode Country code (default: +91)
     * @return Result with SendOTPResponse or error
     */
    suspend fun sendOTP(
        mobileNumber: String,
        countryCode: String = "+91"
    ): Result<SendOTPResponse> {
        return try {
            val response = authApi.sendOTP(
                SendOTPRequest(
                    mobileNumber = mobileNumber,
                    countryCode = countryCode
                )
            )
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to send OTP: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Verify OTP and login/signup
     * 
     * @param mobileNumber Mobile number
     * @param otp OTP code (6 digits)
     * @param otpId OTP ID from sendOTP response
     * @param deviceId Unique device identifier
     * @param fcmToken FCM token for push notifications
     * @return Result with AuthResponse (user + token) or error
     */
    suspend fun verifyOTP(
        mobileNumber: String,
        otp: String,
        otpId: String,
        deviceId: String,
        fcmToken: String?
    ): Result<AuthResponse> {
        return try {
            val response = authApi.verifyOTP(
                VerifyOTPRequest(
                    mobileNumber = mobileNumber,
                    otp = otp,
                    otpId = otpId,
                    deviceId = deviceId,
                    fcmToken = fcmToken
                )
            )
            
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                
                // Save token to secure storage
                RetrofitClient.saveAccessToken(authResponse.token.accessToken)
                
                Result.success(authResponse)
            } else {
                Result.failure(Exception("Invalid OTP: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Complete user profile (for new users)
     * 
     * @param name User name
     * @param email Email (optional)
     * @param role User role (DRIVER or TRANSPORTER)
     * @return Result with User or error
     */
    suspend fun completeProfile(
        name: String,
        email: String?,
        role: String
    ): Result<User> {
        return try {
            val response = authApi.completeProfile(
                token = "Bearer ${getAccessToken()}",
                request = CompleteProfileRequest(
                    name = name,
                    email = email,
                    role = role
                )
            )
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to complete profile: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Logout user
     * 
     * @param deviceId Device identifier
     * @param fcmToken FCM token
     * @return Result with success or error
     */
    suspend fun logout(
        deviceId: String,
        fcmToken: String?
    ): Result<Unit> {
        return try {
            val response = authApi.logout(
                token = "Bearer ${getAccessToken()}",
                request = LogoutRequest(
                    deviceId = deviceId,
                    fcmToken = fcmToken
                )
            )
            
            if (response.isSuccessful) {
                // Clear tokens from secure storage
                RetrofitClient.clearTokens()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Logout failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get current user profile
     * 
     * @return Result with User or error
     */
    suspend fun getCurrentUser(): Result<User> {
        return try {
            val response = authApi.getCurrentUser(
                token = "Bearer ${getAccessToken()}"
            )
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get user: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get access token from secure storage
     * TODO: Implement actual token retrieval
     */
    private fun getAccessToken(): String {
        // TODO: Get from EncryptedSharedPreferences
        return ""
    }
}
