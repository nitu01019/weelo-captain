package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Driver API Service - Matches weelo-backend/src/modules/driver/
 * 
 * For driver-specific operations like dashboard, availability, and earnings.
 * Authorization header is added automatically by RetrofitClient interceptor.
 */
interface DriverApiService {
    
    // ============== DRIVER ONBOARDING WITH OTP VERIFICATION ==============
    
    /**
     * Step 1: Initiate driver onboarding - sends OTP to DRIVER's phone
     * POST /api/v1/driver/onboard/initiate
     * 
     * FLOW:
     * 1. Transporter enters driver's phone, name, license
     * 2. OTP is sent to DRIVER's phone (not transporter's!)
     * 3. Driver shares OTP with transporter
     * 4. Transporter enters OTP to verify
     */
    @POST("driver/onboard/initiate")
    suspend fun initiateDriverOnboarding(
        @Body request: InitiateDriverOnboardingRequest
    ): Response<InitiateDriverOnboardingResponse>
    
    /**
     * Step 2: Verify OTP and add driver to fleet
     * POST /api/v1/driver/onboard/verify
     */
    @POST("driver/onboard/verify")
    suspend fun verifyDriverOnboarding(
        @Body request: VerifyDriverOnboardingRequest
    ): Response<VerifyDriverOnboardingResponse>
    
    /**
     * Resend OTP to driver's phone
     * POST /api/v1/driver/onboard/resend
     */
    @POST("driver/onboard/resend")
    suspend fun resendDriverOnboardingOtp(
        @Body request: ResendDriverOtpRequest
    ): Response<ResendDriverOtpResponse>
    
    // ============== LEGACY: CREATE DRIVER WITHOUT OTP (DEPRECATED) ==============
    
    /**
     * Create a new driver WITHOUT OTP verification (Transporter only)
     * POST /api/v1/driver/create
     * 
     * @deprecated Use initiateDriverOnboarding + verifyDriverOnboarding instead
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use driver-onboarding endpoints with OTP verification")
    @POST("driver/create")
    suspend fun createDriver(
        @Body request: CreateDriverRequest
    ): Response<CreateDriverResponse>
    
    /**
     * Get all drivers for transporter
     * GET /api/v1/driver/list
     */
    @GET("driver/list")
    suspend fun getDriverList(): Response<DriverListResponse>
    
    // ============== DRIVER DASHBOARD ==============
    
    /**
     * Get driver dashboard with stats, recent trips, and earnings
     * GET /api/v1/driver/dashboard
     */
    @GET("driver/dashboard")
    suspend fun getDriverDashboard(): Response<DriverDashboardResponse>
    
    // ============== DRIVER AVAILABILITY ==============
    
    /**
     * Get current driver availability status
     * GET /api/v1/driver/availability
     */
    @GET("driver/availability")
    suspend fun getAvailability(): Response<AvailabilityResponse>
    
    /**
     * Update driver availability status
     * PUT /api/v1/driver/availability
     */
    @PUT("driver/availability")
    suspend fun updateAvailability(
        @Body request: UpdateAvailabilityRequest
    ): Response<AvailabilityResponse>
    
    // ============== DRIVER EARNINGS ==============
    
    /**
     * Get driver earnings summary
     * GET /api/v1/driver/earnings
     */
    @GET("driver/earnings")
    suspend fun getDriverEarnings(
        @Query("period") period: String = "month"  // today, week, month
    ): Response<DriverEarningsResponse>
    
    // ============== DRIVER TRIPS ==============
    
    /**
     * Get driver's trip history
     * GET /api/v1/driver/trips
     */
    @GET("driver/trips")
    suspend fun getDriverTrips(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<DriverTripsResponse>
    
    /**
     * Get driver's currently active trip
     * GET /api/v1/driver/trips/active
     */
    @GET("driver/trips/active")
    suspend fun getActiveTrip(): Response<ActiveTripResponse>
}

// ============== REQUEST MODELS ==============

/**
 * Create Driver Request (Transporter creates driver)
 * @deprecated Use driver-onboarding endpoints with OTP verification
 */
@Deprecated("Use InitiateDriverOnboardingRequest + VerifyDriverOnboardingRequest")
data class CreateDriverRequest(
    val phone: String,          // 10-digit phone number
    val name: String,
    val licenseNumber: String? = null
)

// =============================================================================
// DRIVER ONBOARDING WITH OTP VERIFICATION (NEW - RECOMMENDED)
// =============================================================================

/**
 * Step 1: Initiate Driver Onboarding Request
 * Sends OTP to driver's phone for verification
 */
data class InitiateDriverOnboardingRequest(
    val phone: String,           // Driver's phone number (10 digits)
    val name: String,            // Driver's full name
    val licenseNumber: String,   // Driving license number
    val licensePhoto: String? = null,  // DL photo (Base64 or URL)
    val email: String? = null    // Optional email
)

/**
 * Step 1: Initiate Driver Onboarding Response
 */
data class InitiateDriverOnboardingResponse(
    val success: Boolean,
    val data: InitiateOnboardingData? = null,
    val message: String? = null,
    val error: ApiError? = null
)

data class InitiateOnboardingData(
    val message: String = "",
    val driverPhoneMasked: String = "",   // e.g., "98****3210"
    val expiresInMinutes: Int = 10
)

/**
 * Step 2: Verify OTP and Add Driver Request
 */
data class VerifyDriverOnboardingRequest(
    val phone: String,   // Driver's phone number
    val otp: String      // OTP received by driver
)

/**
 * Step 2: Verify OTP and Add Driver Response
 */
data class VerifyDriverOnboardingResponse(
    val success: Boolean,
    val data: VerifyOnboardingData? = null,
    val message: String? = null,
    val error: ApiError? = null
)

data class VerifyOnboardingData(
    val driver: OnboardedDriverInfo? = null
)

data class OnboardedDriverInfo(
    val id: String,
    val name: String,
    val phone: String,
    val licenseNumber: String? = null,
    val isVerified: Boolean = true
)

/**
 * Resend OTP Request
 */
data class ResendDriverOtpRequest(
    val phone: String   // Driver's phone number
)

/**
 * Resend OTP Response
 */
data class ResendDriverOtpResponse(
    val success: Boolean,
    val data: ResendOtpData? = null,
    val message: String? = null,
    val error: ApiError? = null
)

data class ResendOtpData(
    val message: String = "",
    val expiresInMinutes: Int = 10
)

// =============================================================================
// OTHER REQUEST/RESPONSE MODELS
// =============================================================================

/**
 * Update Availability Request
 */
data class UpdateAvailabilityRequest(
    val isOnline: Boolean,
    val currentLocation: LocationData? = null
)

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)

// ============== RESPONSE MODELS ==============

/**
 * Create Driver Response
 */
data class CreateDriverResponse(
    val success: Boolean,
    val data: CreateDriverData? = null,
    val message: String? = null,
    val error: ApiError? = null
)

data class CreateDriverData(
    val driver: DriverData
)

/**
 * Driver List Response
 */
data class DriverListResponse(
    val success: Boolean,
    val data: DriverListData? = null,
    val error: ApiError? = null
)

data class DriverListData(
    val drivers: List<DriverData>,
    val total: Int,
    val online: Int,
    val offline: Int
)

/**
 * Driver Data Model
 */
data class DriverData(
    val id: String,
    val transporterId: String,
    val phone: String,
    val name: String? = null,
    val email: String? = null,
    val profilePhotoUrl: String? = null, // Driver profile photo (visible to transporter)
    val licenseNumber: String? = null,
    val licenseExpiry: String? = null,
    val isOnline: Boolean = false,
    val isOnTrip: Boolean = false,
    val assignedVehicleId: String? = null,
    val assignedVehicleNumber: String? = null,
    val currentTripId: String? = null,
    val lastLocation: LocationData? = null,
    val rating: Float? = null,
    val totalTrips: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * Driver Dashboard Response
 */
data class DriverDashboardResponse(
    val success: Boolean,
    val data: DashboardData? = null,
    val error: ApiError? = null
)

data class DashboardData(
    val driver: DriverData,
    val stats: DashboardStats,
    val activeTrip: TripData? = null,
    val recentTrips: List<TripData> = emptyList()
)

data class DashboardStats(
    val todayTrips: Int = 0,
    val todayEarnings: Double = 0.0,
    val todayDistance: Double = 0.0,
    val weekEarnings: Double = 0.0,
    val monthEarnings: Double = 0.0,
    val totalTrips: Int = 0,
    val rating: Float = 0f
)

/**
 * Availability Response
 */
data class AvailabilityResponse(
    val success: Boolean,
    val data: AvailabilityData? = null,
    val message: String? = null,
    val error: ApiError? = null
)

data class AvailabilityData(
    val isOnline: Boolean,
    val lastLocation: LocationData? = null,
    val updatedAt: String? = null
)

/**
 * Driver Earnings Response
 */
data class DriverEarningsResponse(
    val success: Boolean,
    val data: EarningsResponseData? = null,
    val error: ApiError? = null
)

data class EarningsResponseData(
    val period: String,
    val totalEarnings: Double,
    val totalTrips: Int,
    val averagePerTrip: Double,
    val breakdown: List<EarningsBreakdown>
)

data class EarningsBreakdown(
    val date: String,
    val earnings: Double,
    val trips: Int,
    val distance: Double = 0.0
)

/**
 * Driver Trips Response
 */
data class DriverTripsResponse(
    val success: Boolean,
    val data: TripsResponseData? = null,
    val error: ApiError? = null
)

data class TripsResponseData(
    val trips: List<TripData>,
    val total: Int,
    val hasMore: Boolean
)

/**
 * Active Trip Response
 */
data class ActiveTripResponse(
    val success: Boolean,
    val data: ActiveTripData? = null,
    val error: ApiError? = null
)

data class ActiveTripData(
    val trip: TripData? = null,
    val hasActiveTrip: Boolean = false
)

/**
 * Trip Data Model
 */
data class TripData(
    val id: String,
    val bookingId: String? = null,
    val customerId: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val vehicleId: String? = null,
    val vehicleNumber: String? = null,
    val vehicleType: String? = null,
    val pickup: TripLocation,
    val drop: TripLocation,
    val distanceKm: Double = 0.0,
    val fare: Double = 0.0,
    val status: String = "pending",  // pending, assigned, in_progress, completed, cancelled
    val startedAt: String? = null,
    val completedAt: String? = null,
    val createdAt: String? = null
)

data class TripLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String
)
