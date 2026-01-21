package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * =============================================================================
 * TRANSPORTER API SERVICE - Transporter-specific API calls
 * =============================================================================
 * 
 * ENDPOINTS:
 * - PUT  /transporter/availability   - Update online/offline status
 * - GET  /transporter/availability   - Get current availability status
 * - GET  /transporter/profile        - Get transporter profile
 * - PUT  /transporter/profile        - Update transporter profile
 * - GET  /transporter/stats          - Get transporter statistics
 * 
 * AVAILABILITY FEATURE:
 * - Toggle to go online/offline
 * - When offline, no broadcasts received
 * 
 * =============================================================================
 */
interface TransporterApiService {
    
    // ===========================================================================
    // AVAILABILITY
    // ===========================================================================
    
    /**
     * Update transporter's online/offline status
     */
    @PUT("transporter/availability")
    suspend fun updateAvailability(
        @Header("Authorization") auth: String,
        @Body request: Map<String, Boolean>
    ): Response<TransporterAvailabilityResponse>
    
    /**
     * Get current availability status
     */
    @GET("transporter/availability")
    suspend fun getAvailability(
        @Header("Authorization") auth: String
    ): Response<TransporterAvailabilityResponse>
    
    // ===========================================================================
    // PROFILE
    // ===========================================================================
    
    /**
     * Get transporter profile with stats
     */
    @GET("transporter/profile")
    suspend fun getProfile(
        @Header("Authorization") auth: String
    ): Response<TransporterProfileResponse>
    
    /**
     * Update transporter profile
     */
    @PUT("transporter/profile")
    suspend fun updateProfile(
        @Header("Authorization") auth: String,
        @Body request: UpdateProfileRequest
    ): Response<TransporterProfileResponse>
    
    // ===========================================================================
    // STATS
    // ===========================================================================
    
    /**
     * Get transporter statistics (earnings, trips, etc.)
     */
    @GET("transporter/stats")
    suspend fun getStats(
        @Header("Authorization") auth: String
    ): Response<TransporterStatsResponse>
}

// =============================================================================
// RESPONSE MODELS
// =============================================================================

data class TransporterAvailabilityResponse(
    val success: Boolean,
    val data: TransporterAvailabilityData? = null,
    val error: ApiError? = null
)

data class TransporterAvailabilityData(
    val isAvailable: Boolean,
    val updatedAt: String? = null
)

data class TransporterProfileResponse(
    val success: Boolean,
    val data: TransporterProfileData? = null,
    val error: ApiError? = null
)

data class TransporterProfileData(
    val profile: TransporterProfile,
    val stats: TransporterQuickStats? = null
)

data class TransporterProfile(
    val id: String,
    val name: String? = null,
    val businessName: String? = null,
    val phone: String,
    val email: String? = null,
    val gstNumber: String? = null,
    val isAvailable: Boolean = true,
    val createdAt: String? = null
)

data class TransporterQuickStats(
    val vehiclesCount: Int = 0,
    val driversCount: Int = 0,
    val availableVehicles: Int = 0,
    val activeTrips: Int = 0
)

data class UpdateProfileRequest(
    val name: String? = null,
    val businessName: String? = null,
    val email: String? = null,
    val gstNumber: String? = null
)

data class TransporterStatsResponse(
    val success: Boolean,
    val data: TransporterStats? = null,
    val error: ApiError? = null
)

data class TransporterStats(
    val totalTrips: Int = 0,
    val completedTrips: Int = 0,
    val activeTrips: Int = 0,
    val totalEarnings: Double = 0.0,
    val rating: Float = 0f,
    val acceptanceRate: Int = 100
)
