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
     * Check if vehicle number is available for registration
     * GET /api/v1/vehicles/check/{vehicleNumber}
     * 
     * Response: { success, data: { available, exists, ownedByYou, vehicleId?, message } }
     * 
     * USE THIS BEFORE REGISTERING to check:
     * - If vehicle can be registered (available=true)
     * - If you already own it (ownedByYou=true) - use upsert instead
     * - If someone else owns it (exists=true, ownedByYou=false) - cannot register
     */
    @GET("vehicles/check/{vehicleNumber}")
    suspend fun checkVehicleAvailability(
        @Path("vehicleNumber") vehicleNumber: String
    ): Response<CheckVehicleResponse>
    
    /**
     * Register a new vehicle
     * POST /api/v1/vehicles
     * 
     * NOTE: Returns 409 error if vehicle already exists.
     * Use upsertVehicle() for create-or-update behavior.
     */
    @POST("vehicles")
    suspend fun registerVehicle(
        @Body request: RegisterVehicleRequest
    ): Response<RegisterVehicleResponse>
    
    /**
     * Register or Update vehicle (UPSERT)
     * PUT /api/v1/vehicles/upsert
     * 
     * RECOMMENDED for "Save" operations:
     * - If vehicle doesn't exist: creates new (returns 201)
     * - If vehicle exists and you own it: updates it (returns 200)
     * - If vehicle exists and someone else owns it: returns 409 error
     * 
     * Response includes `isNew` flag to indicate if created or updated.
     */
    @PUT("vehicles/upsert")
    suspend fun upsertVehicle(
        @Body request: RegisterVehicleRequest
    ): Response<UpsertVehicleResponse>
    
    /**
     * Get vehicle by ID
     * GET /api/v1/vehicles/{vehicleId}
     */
    @GET("vehicles/{vehicleId}")
    suspend fun getVehicleById(
        @Path("vehicleId") vehicleId: String
    ): Response<VehicleDetailResponse>
    
    /**
     * Update vehicle by ID
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
 * Check Vehicle Availability Response
 * GET /api/v1/vehicles/check/{vehicleNumber}
 */
data class CheckVehicleResponse(
    val success: Boolean,
    val data: CheckVehicleData? = null,
    val error: ApiError? = null
)

data class CheckVehicleData(
    val available: Boolean = false,      // true if can be registered as new
    val exists: Boolean = false,         // true if vehicle exists in system
    val ownedByYou: Boolean = false,     // true if you already own this vehicle
    val vehicleId: String? = null,       // ID of existing vehicle (if owned by you)
    val message: String = ""             // Human-readable message
)

/**
 * Register Vehicle Response
 * POST /api/v1/vehicles
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
 * Upsert Vehicle Response
 * PUT /api/v1/vehicles/upsert
 */
data class UpsertVehicleResponse(
    val success: Boolean,
    val data: UpsertVehicleData? = null,
    val message: String? = null,
    val error: ApiError? = null
)

data class UpsertVehicleData(
    val vehicle: VehicleData,
    val isNew: Boolean = true  // true if created, false if updated
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
