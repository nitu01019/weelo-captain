package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Profile API Service - Matches weelo-backend/src/modules/profile/
 * 
 * All endpoints require authentication
 * Authorization header is added automatically by RetrofitClient interceptor
 */
interface ProfileApiService {
    
    /**
     * Get current user's profile
     * GET /api/v1/profile
     */
    @GET("profile")
    suspend fun getProfile(): Response<GetProfileResponse>
    
    /**
     * Update customer profile
     * PUT /api/v1/profile/customer
     */
    @PUT("profile/customer")
    suspend fun updateCustomerProfile(
        @Body request: CustomerProfileRequest
    ): Response<UpdateProfileResponse>
    
    /**
     * Update transporter profile
     * PUT /api/v1/profile/transporter
     */
    @PUT("profile/transporter")
    suspend fun updateTransporterProfile(
        @Body request: TransporterProfileRequest
    ): Response<UpdateProfileResponse>
    
    /**
     * Update driver profile
     * PUT /api/v1/profile/driver
     */
    @PUT("profile/driver")
    suspend fun updateDriverProfile(
        @Body request: DriverProfileRequest
    ): Response<UpdateProfileResponse>
    
    /**
     * Get transporter's drivers
     * GET /api/v1/profile/drivers
     */
    @GET("profile/drivers")
    suspend fun getTransporterDrivers(): Response<DriversListResponse>
    
    /**
     * Add driver to transporter's fleet
     * POST /api/v1/profile/drivers
     */
    @POST("profile/drivers")
    suspend fun addDriver(
        @Body request: AddDriverProfileRequest
    ): Response<AddDriverProfileResponse>
    
    /**
     * Remove driver from fleet
     * DELETE /api/v1/profile/drivers/{driverId}
     */
    @DELETE("profile/drivers/{driverId}")
    suspend fun removeDriver(
        @Path("driverId") driverId: String
    ): Response<GenericSuccessResponse>
    
    /**
     * Get driver's transporter info
     * GET /api/v1/profile/my-transporter
     */
    @GET("profile/my-transporter")
    suspend fun getMyTransporter(): Response<MyTransporterResponse>
}

// ============== REQUEST MODELS ==============

data class CustomerProfileRequest(
    val name: String,
    val email: String? = null,
    val company: String? = null,
    val address: String? = null
)

data class TransporterProfileRequest(
    val name: String,
    val email: String? = null,
    val company: String? = null,
    val gstNumber: String? = null,
    val panNumber: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null
)

data class DriverProfileRequest(
    val name: String,
    val email: String? = null,
    val licenseNumber: String? = null,
    val licenseExpiry: String? = null,  // ISO date string
    val address: String? = null,
    val emergencyContact: String? = null
)

data class AddDriverProfileRequest(
    val phone: String,      // 10-digit phone number
    val name: String,
    val licenseNumber: String? = null
)

// ============== RESPONSE MODELS ==============

/**
 * Get Profile Response
 */
data class GetProfileResponse(
    val success: Boolean,
    val data: ProfileData? = null,
    val error: ApiError? = null
)

data class ProfileData(
    val user: UserProfile
)

data class UserProfile(
    val id: String,
    val phone: String,
    val role: String,
    val name: String? = null,
    val email: String? = null,
    val company: String? = null,
    val businessName: String? = null,
    val businessAddress: String? = null,
    val gstNumber: String? = null,
    val panNumber: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val licenseNumber: String? = null,
    val licenseExpiry: String? = null,
    val emergencyContact: String? = null,
    val profilePhoto: String? = null,
    val isVerified: Boolean = false,
    val isActive: Boolean = true,
    val transporterId: String? = null,  // For drivers
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    // Helper to get business name (handles both field names)
    fun getBusinessDisplayName(): String? = businessName ?: company
}

/**
 * Update Profile Response
 */
data class UpdateProfileResponse(
    val success: Boolean,
    val data: ProfileData? = null,
    val error: ApiError? = null
)

/**
 * Drivers List Response (for transporter)
 */
data class DriversListResponse(
    val success: Boolean,
    val data: DriversListData? = null,
    val error: ApiError? = null
)

data class DriversListData(
    val drivers: List<DriverInfo>
)

data class DriverInfo(
    val id: String,
    val phone: String,
    val name: String? = null,
    val email: String? = null,
    val licenseNumber: String? = null,
    val licenseExpiry: String? = null,
    val isOnline: Boolean = false,
    val assignedVehicleId: String? = null,
    val assignedVehicleNumber: String? = null,
    val currentTripId: String? = null,
    val createdAt: String? = null
)

/**
 * Add Driver Response
 */
data class AddDriverProfileResponse(
    val success: Boolean,
    val data: AddDriverProfileData? = null,
    val error: ApiError? = null
)

data class AddDriverProfileData(
    val driver: DriverInfo
)

/**
 * My Transporter Response (for driver)
 */
data class MyTransporterResponse(
    val success: Boolean,
    val data: MyTransporterData? = null,
    val error: ApiError? = null
)

data class MyTransporterData(
    val transporter: TransporterInfo?
)

data class TransporterInfo(
    val id: String,
    val phone: String,
    val name: String? = null,
    val company: String? = null,
    val email: String? = null
)
