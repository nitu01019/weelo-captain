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
 * - POST /transporter/heartbeat      - Live location heartbeat (every 5 sec)
 * - DELETE /transporter/heartbeat    - Mark offline
 * - GET  /transporter/profile        - Get transporter profile
 * - PUT  /transporter/profile        - Update transporter profile
 * - GET  /transporter/stats          - Get transporter statistics
 * 
 * AVAILABILITY FEATURE:
 * - Toggle to go online/offline
 * - When offline, no broadcasts received
 * 
 * HEARTBEAT FEATURE (CRITICAL for geolocation):
 * - Send location every 5 seconds when online
 * - Enables proximity-based matching (nearest drivers first)
 * - Uses geohash indexing for fast O(1) lookups
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
    // HEARTBEAT - Live Location Updates (for geolocation matching)
    // ===========================================================================
    
    /**
     * Send location heartbeat - CALL EVERY 5 SECONDS when online
     * 
     * This powers the LIVE AVAILABILITY TABLE:
     * - Geohash-indexed for fast nearby searches
     * - Top 10 nearby transporters get broadcasts first
     * - Stale entries (>60 sec) are auto-removed
     * 
     * @param request HeartbeatRequest with latitude, longitude
     */
    @POST("transporter/heartbeat")
    suspend fun sendHeartbeat(
        @Header("Authorization") auth: String,
        @Body request: HeartbeatRequest
    ): Response<HeartbeatResponse>
    
    /**
     * Mark offline - Call when app goes to background or user logs out
     */
    @DELETE("transporter/heartbeat")
    suspend fun markOffline(
        @Header("Authorization") auth: String
    ): Response<HeartbeatResponse>
    
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

// =============================================================================
// HEARTBEAT - Live Location for Geolocation Matching
// =============================================================================

/**
 * Heartbeat request - Send every 5 seconds when online
 * 
 * @param latitude Current latitude
 * @param longitude Current longitude
 * @param vehicleId Currently active vehicle (optional)
 * @param isOnTrip Whether currently on a trip
 */
data class HeartbeatRequest(
    val latitude: Double,
    val longitude: Double,
    val vehicleId: String? = null,
    val isOnTrip: Boolean = false
)

/**
 * Heartbeat response
 * 
 * @param registered Whether heartbeat was registered
 * @param vehicleKey Normalized vehicle key used for matching
 * @param nextHeartbeatMs Recommended interval for next heartbeat (5000ms)
 */
data class HeartbeatResponse(
    val success: Boolean,
    val data: HeartbeatData? = null,
    val error: ApiError? = null
)

data class HeartbeatData(
    val registered: Boolean = false,
    val vehicleKey: String? = null,
    val vehicleId: String? = null,
    val isOnTrip: Boolean = false,
    val nextHeartbeatMs: Int = 5000
)
