package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Vehicle API Service - Matches weelo-backend/src/modules/vehicle/
 * 
 * For vehicle/truck management operations.
 * Authorization header is added automatically by RetrofitClient interceptor.
 */
interface VehicleApiService {
    
    /**
     * Get transporter's vehicles
     * GET /api/v1/vehicles/list
     * 
     * Response: { success, data: { vehicles, total, available, inTransit, maintenance } }
     */
    @GET("vehicles/list")
    suspend fun getVehicles(): Response<VehicleListResponse>
    
    /**
     * Register a new vehicle
     * POST /api/v1/vehicles
     */
    @POST("vehicles")
    suspend fun registerVehicle(
        @Body request: RegisterVehicleRequest
    ): Response<RegisterVehicleResponse>
    
    /**
     * Get vehicle by ID
     * GET /api/v1/vehicles/{vehicleId}
     */
    @GET("vehicles/{vehicleId}")
    suspend fun getVehicleById(
        @Path("vehicleId") vehicleId: String
    ): Response<VehicleDetailResponse>
    
    /**
     * Update vehicle
     * PUT /api/v1/vehicles/{vehicleId}
     */
    @PUT("vehicles/{vehicleId}")
    suspend fun updateVehicle(
        @Path("vehicleId") vehicleId: String,
        @Body request: UpdateVehicleRequest
    ): Response<VehicleDetailResponse>
    
    /**
     * Delete vehicle
     * DELETE /api/v1/vehicles/{vehicleId}
     */
    @DELETE("vehicles/{vehicleId}")
    suspend fun deleteVehicle(
        @Path("vehicleId") vehicleId: String
    ): Response<GenericSuccessResponse>
}

// =============================================================================
// REQUEST MODELS
// =============================================================================

/**
 * Register Vehicle Request
 * Matches: registerVehicleSchema in vehicle.schema.ts
 */
data class RegisterVehicleRequest(
    val vehicleNumber: String,       // e.g., "MH12AB1234"
    val vehicleType: String,         // e.g., "open", "container", "tipper"
    val vehicleSubtype: String,      // e.g., "17 Feet", "20-24 Ton"
    val capacity: String,            // e.g., "10 Ton"
    val model: String? = null,       // e.g., "Tata Prima"
    val year: Int? = null,           // e.g., 2022
    // Documents (optional)
    val rcNumber: String? = null,
    val rcExpiry: String? = null,
    val insuranceNumber: String? = null,
    val insuranceExpiry: String? = null,
    val permitNumber: String? = null,
    val permitExpiry: String? = null,
    val fitnessExpiry: String? = null
)

/**
 * Update Vehicle Request
 */
data class UpdateVehicleRequest(
    val vehicleNumber: String? = null,
    val vehicleType: String? = null,
    val vehicleSubtype: String? = null,
    val capacity: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val status: String? = null
)

// =============================================================================
// RESPONSE MODELS
// =============================================================================

// Note: ApiError is defined in AuthApiService.kt

/**
 * Vehicle List Response
 * Matches: GET /api/v1/vehicles/list
 */
data class VehicleListResponse(
    val success: Boolean,
    val data: VehicleListData? = null,
    val error: ApiError? = null
)

data class VehicleListData(
    val vehicles: List<VehicleData> = emptyList(),
    val total: Int = 0,
    val available: Int = 0,
    val inTransit: Int = 0,
    val maintenance: Int = 0
)

/**
 * Vehicle Data Model
 * Matches: VehicleRecord in db.ts
 */
data class VehicleData(
    val id: String = "",
    val transporterId: String = "",
    val assignedDriverId: String? = null,
    
    // Vehicle details
    val vehicleNumber: String = "",
    val vehicleType: String = "",
    val vehicleSubtype: String = "",
    val capacity: String = "",
    val model: String? = null,
    val make: String? = null,        // Manufacturer
    val year: Int? = null,
    val bodyType: String? = null,    // Body type
    val fuelType: String? = null,    // Fuel type
    
    // Status
    val status: String = "available",  // available, in_transit, maintenance, inactive
    val currentTripId: String? = null,
    val maintenanceReason: String? = null,
    val maintenanceEndDate: String? = null,
    val lastStatusChange: String? = null,
    
    // Documents
    val rcNumber: String? = null,
    val rcExpiry: String? = null,
    val insuranceNumber: String? = null,
    val insuranceExpiry: String? = null,
    val permitNumber: String? = null,
    val permitExpiry: String? = null,
    val fitnessExpiry: String? = null,
    
    // Photos
    val vehiclePhotos: List<String>? = null,
    val rcPhoto: String? = null,
    val insurancePhoto: String? = null,
    
    // Flags
    val isVerified: Boolean = false,
    val isActive: Boolean = true,
    
    // Timestamps
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * Register Vehicle Response
 */
data class RegisterVehicleResponse(
    val success: Boolean,
    val data: RegisterVehicleData? = null,
    val message: String? = null,
    val error: ApiError? = null
)

data class RegisterVehicleData(
    val vehicle: VehicleData
)

/**
 * Vehicle Detail Response
 */
data class VehicleDetailResponse(
    val success: Boolean,
    val data: VehicleDetailData? = null,
    val error: ApiError? = null
)

data class VehicleDetailData(
    val vehicle: VehicleData
)

/**
 * Generic Success Response (used by other API services)
 */
data class GenericSuccessResponse(
    val success: Boolean,
    val message: String? = null,
    val error: ApiError? = null
)
