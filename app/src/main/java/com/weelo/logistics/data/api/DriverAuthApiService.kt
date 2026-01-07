package com.weelo.logistics.data.api

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Driver Authentication API Service
 * 
 * Backend-ready interface for driver authentication
 * Driver OTP is sent to their assigned transporter
 * 
 * BACKEND IMPLEMENTATION REQUIRED
 */
interface DriverAuthApiService {
    
    /**
     * Send OTP for Driver Login
     * 
     * Flow:
     * 1. Backend receives driver phone number
     * 2. Looks up driver in database
     * 3. Finds driver's assigned transporter
     * 4. Sends OTP to TRANSPORTER's phone
     * 5. Returns transporter info to show in UI
     * 
     * @param request Driver phone number
     * @return DriverOTPResponse with transporter info
     */
    @POST("api/v1/driver/send-otp")
    suspend fun sendDriverOTP(
        @Body request: DriverOTPRequest
    ): DriverOTPResponse
    
    /**
     * Verify Driver OTP
     * 
     * @param request Driver phone and OTP
     * @return Auth token and driver details
     */
    @POST("api/v1/driver/verify-otp")
    suspend fun verifyDriverOTP(
        @Body request: VerifyOTPRequest
    ): DriverAuthResponse
}

/**
 * Request: Driver sends phone number
 */
data class DriverOTPRequest(
    val driverPhone: String,
    val deviceId: String? = null
)

/**
 * Response: Backend returns transporter info
 */
data class DriverOTPResponse(
    val success: Boolean,
    val message: String,
    val transporterName: String,      // e.g., "ABC Logistics"
    val transporterPhone: String,     // Masked: "91234XXXXX"
    val otpSentTo: String,            // "transporter"
    val expiryMinutes: Int = 5
)

// VerifyOTPRequest is already defined in AuthApiService.kt - reusing that

/**
 * Response: Authentication success
 */
data class DriverAuthResponse(
    val success: Boolean,
    val message: String,
    val driver: DriverDTO? = null,
    val authToken: String? = null,
    val refreshToken: String? = null
)

/**
 * Driver Data Transfer Object
 */
data class DriverDTO(
    val id: String,
    val phone: String,
    val name: String,
    val transporterId: String,
    val transporterName: String,
    val status: String,              // "active", "inactive", "suspended"
    val licenseNumber: String?,
    val vehicleAssigned: String?
)

/**
 * BACKEND IMPLEMENTATION GUIDE:
 * 
 * POST /api/v1/driver/send-otp
 * {
 *   "driverPhone": "9876543210"
 * }
 * 
 * Backend Logic:
 * 1. Look up driver: SELECT * FROM drivers WHERE phone = '9876543210'
 * 2. If not found: return 404 "Driver not found. Contact your transporter."
 * 3. Get transporter: SELECT * FROM transporters WHERE id = driver.transporter_id
 * 4. Generate OTP: otp = random 6-digit
 * 5. Send SMS to transporter.phone with message:
 *    "Driver [driverName] ([driverPhone]) is logging in. OTP: [otp]"
 * 6. Store in cache: redis.set("otp:driver:9876543210", otp, EX=300)
 * 7. Return response with transporter name
 * 
 * Response:
 * {
 *   "success": true,
 *   "message": "OTP sent to your transporter",
 *   "transporterName": "ABC Logistics",
 *   "transporterPhone": "91234XXXXX",
 *   "otpSentTo": "transporter",
 *   "expiryMinutes": 5
 * }
 */
