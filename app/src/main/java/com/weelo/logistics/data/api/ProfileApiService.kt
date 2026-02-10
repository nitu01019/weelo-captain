package com.weelo.logistics.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
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
    
    /**
     * Complete driver profile with photos
     * POST /api/v1/driver/complete-profile
     * 
     * Uploads driver photo, license front, and license back photos to S3
     * and completes the driver profile registration.
     */
    @Multipart
    @POST("driver/complete-profile")
    suspend fun completeDriverProfile(
        @Part("licenseNumber") licenseNumber: RequestBody,
        @Part("vehicleType") vehicleType: RequestBody,
        @Part("address") address: RequestBody? = null,
        @Part("language") language: RequestBody? = null,
        @Part driverPhoto: MultipartBody.Part,
        @Part licenseFront: MultipartBody.Part,
        @Part licenseBack: MultipartBody.Part
    ): Response<CompleteDriverProfileResponse>
    
    /**
     * =============================================================================
     * PROFILE PHOTO MANAGEMENT ENDPOINTS
     * =============================================================================
     * Get and update driver profile photos with real-time updates
     * Scalable, modular, follows coding standards
     * =============================================================================
     */
    
    /**
     * Get driver profile with photos
     * GET /api/v1/driver/profile
     * 
     * Returns complete driver profile including all photo URLs
     */
    @GET("driver/profile")
    suspend fun getDriverProfile(): Response<GetDriverProfileResponse>
    
    /**
     * Update profile photo
     * PUT /api/v1/driver/profile/photo
     * 
     * Updates driver's profile photo and emits real-time update event
     */
    @Multipart
    @PUT("driver/profile/photo")
    suspend fun updateProfilePhoto(
        @Part photo: MultipartBody.Part
    ): Response<UpdatePhotoResponse>
    
    /**
     * Update license photos (front and/or back)
     * PUT /api/v1/driver/profile/license
     * 
     * Updates one or both license photos with real-time updates
     */
    @Multipart
    @PUT("driver/profile/license")
    suspend fun updateLicensePhotos(
        @Part licenseFront: MultipartBody.Part?,
        @Part licenseBack: MultipartBody.Part?
    ): Response<UpdateLicensePhotosResponse>
    
    /**
     * Update user's preferred language
     * PUT /api/v1/profile/language
     * 
     * Saves language preference to database so it persists across logins
     */
    @PUT("profile/language")
    suspend fun updateLanguagePreference(
        @Body request: LanguagePreferenceRequest
    ): Response<BasicResponse>
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
    val profilePhoto: String? = null,           // ✅ ADDED: Profile photo URL from S3
    val licenseFrontPhoto: String? = null,      // ✅ ADDED: License front photo URL
    val licenseBackPhoto: String? = null,       // ✅ ADDED: License back photo URL
    val isProfileCompleted: Boolean = false,    // ✅ ADDED: Profile completion status
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

/**
 * Complete Driver Profile Response
 */
data class CompleteDriverProfileResponse(
    val success: Boolean,
    val message: String,
    val data: CompleteDriverProfileData? = null
)

data class CompleteDriverProfileData(
    val driver: CompletedDriverProfile
)

data class CompletedDriverProfile(
    val id: String,
    val name: String,
    val phone: String,
    val licenseNumber: String,
    val vehicleType: String,
    val address: String?,
    val photoUrls: PhotoUrls,
    val isProfileCompleted: Boolean
)

data class PhotoUrls(
    val driverPhotoUrl: String,
    val licenseFrontUrl: String,
    val licenseBackUrl: String
)

/**
 * =============================================================================
 * DRIVER PROFILE MANAGEMENT - GET AND UPDATE PHOTOS
 * =============================================================================
 */

/**
 * Get Driver Profile Response (with photos)
 */
data class GetDriverProfileResponse(
    val success: Boolean,
    val data: GetDriverProfileData? = null,
    val error: ApiError? = null
)

data class GetDriverProfileData(
    val driver: DriverProfileWithPhotos
)

data class DriverProfileWithPhotos(
    val id: String,
    val name: String,
    val phone: String,
    val email: String?,
    val licenseNumber: String?,
    val vehicleType: String?,
    val address: String?,
    val language: String?,
    val photos: DriverPhotos?,  // Make nullable - backend might return null
    val isProfileCompleted: Boolean,
    val createdAt: String,
    val updatedAt: String
)

data class DriverPhotos(
    val profilePhoto: String?,
    val licenseFront: String?,
    val licenseBack: String?
)

/**
 * Update Photo Response (single photo)
 */
data class UpdatePhotoResponse(
    val success: Boolean,
    val message: String,
    val data: UpdatePhotoData? = null
)

data class UpdatePhotoData(
    val photoUrl: String,
    val driver: BasicDriverInfo
)

data class BasicDriverInfo(
    val id: String,
    val name: String,
    val profilePhoto: String? = null
)

/**
 * Update License Photos Response
 */
data class UpdateLicensePhotosResponse(
    val success: Boolean,
    val message: String,
    val data: UpdateLicensePhotosData? = null
)

data class UpdateLicensePhotosData(
    val photos: LicensePhotos,
    val driver: BasicDriverInfo
)

data class LicensePhotos(
    val licenseFront: String?,
    val licenseBack: String?
)

/**
 * Language Preference Request
 */
data class LanguagePreferenceRequest(
    val preferredLanguage: String  // ISO 639-1 code: en, hi, ta, etc.
)

/**
 * Basic Response (for simple operations)
 */
data class BasicResponse(
    val success: Boolean,
    val message: String
)
