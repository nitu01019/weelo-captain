package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Vehicle API Service - Matches weelo-backend/src/modules/vehicle/
 * 
 * All endpoints require transporter authentication (except /vehicles/types)
 * Authorization header is added automatically by RetrofitClient interceptor
 */
interface VehicleApiService {
    
    // ============== PUBLIC ENDPOINTS ==============
    
    /**
     * Get available vehicle types (PUBLIC - no auth required)
     * GET /api/v1/vehicles/types
     */
    @GET("vehicles/types")
    suspend fun getVehicleTypes(): Response<VehicleTypesResponse>
    
    // ============== TRANSPORTER ENDPOINTS ==============
    
    /**
     * Register a new vehicle
     * POST /api/v1/vehicles
     */
    @POST("vehicles")
    suspend fun registerVehicle(
        @Body request: RegisterVehicleRequest
    ): Response<RegisterVehicleResponse>
    
    /**
     * Get transporter's vehicles with status counts
     * GET /api/v1/vehicles
     */
    @GET("vehicles")
    suspend fun getVehicles(
        @Query("status") status: String? = null,
        @Query("vehicleType") vehicleType: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<VehicleListResponse>
    
    /**
     * Get available vehicles (for assignment)
     * GET /api/v1/vehicles/available
     */
    @GET("vehicles/available")
    suspend fun getAvailableVehicles(
        @Query("vehicleType") vehicleType: String? = null
    ): Response<AvailableVehiclesResponse>
    
    /**
     * Get vehicle types summary
     * GET /api/v1/vehicles/summary
     */
    @GET("vehicles/summary")
    suspend fun getVehiclesSummary(): Response<VehicleSummaryResponse>
    
    /**
     * Get vehicle status stats
     * GET /api/v1/vehicles/stats
     */
    @GET("vehicles/stats")
    suspend fun getVehicleStats(): Response<VehicleStatsResponse>
    
    /**
     * Get vehicle details
     * GET /api/v1/vehicles/{vehicleId}
     */
    @GET("vehicles/{vehicleId}")
    suspend fun getVehicleById(
        @Path("vehicleId") vehicleId: String
    ): Response<VehicleDetailResponse>
    
    /**
     * Update vehicle details
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
    
    /**
     * Assign driver to vehicle
     * POST /api/v1/vehicles/{vehicleId}/assign-driver
     */
    @POST("vehicles/{vehicleId}/assign-driver")
    suspend fun assignDriver(
        @Path("vehicleId") vehicleId: String,
        @Body request: AssignDriverRequest
    ): Response<VehicleDetailResponse>
    
    /**
     * Unassign driver from vehicle
     * POST /api/v1/vehicles/{vehicleId}/unassign-driver
     */
    @POST("vehicles/{vehicleId}/unassign-driver")
    suspend fun unassignDriver(
        @Path("vehicleId") vehicleId: String
    ): Response<VehicleDetailResponse>
    
    /**
     * Update vehicle status
     * PUT /api/v1/vehicles/{vehicleId}/status
     */
    @PUT("vehicles/{vehicleId}/status")
    suspend fun updateVehicleStatus(
        @Path("vehicleId") vehicleId: String,
        @Body request: UpdateStatusRequest
    ): Response<VehicleDetailResponse>
    
    /**
     * Put vehicle in maintenance mode
     * PUT /api/v1/vehicles/{vehicleId}/maintenance
     */
    @PUT("vehicles/{vehicleId}/maintenance")
    suspend fun setMaintenance(
        @Path("vehicleId") vehicleId: String,
        @Body request: SetMaintenanceRequest
    ): Response<VehicleDetailResponse>
    
    /**
     * Mark vehicle as available
     * PUT /api/v1/vehicles/{vehicleId}/available
     */
    @PUT("vehicles/{vehicleId}/available")
    suspend fun setAvailable(
        @Path("vehicleId") vehicleId: String
    ): Response<VehicleDetailResponse>
}

// ============== REQUEST MODELS ==============

/**
 * Register Vehicle Request
 * Matches: weelo-backend/src/modules/vehicle/vehicle.schema.ts -> registerVehicleSchema
 */
data class RegisterVehicleRequest(
    val vehicleNumber: String,      // e.g. "MH12AB1234"
    val vehicleType: String,        // e.g. "container", "tipper", "trailer"
    val vehicleSubtype: String,     // e.g. "20 Feet", "32 Feet"
    val make: String? = null,       // e.g. "Tata", "Ashok Leyland"
    val model: String? = null,      // e.g. "Prima", "Signa"
    val year: Int? = null,          // e.g. 2022
    val capacity: String? = null,   // e.g. "20 MT"
    val bodyType: String? = null,   // e.g. "closed", "open"
    val fuelType: String? = null    // e.g. "diesel", "cng"
)

data class UpdateVehicleRequest(
    val vehicleSubtype: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val capacity: String? = null,
    val bodyType: String? = null,
    val fuelType: String? = null
)

data class AssignDriverRequest(
    val driverId: String
)

data class UpdateStatusRequest(
    val status: String,  // "available", "in_transit", "maintenance", "inactive"
    val tripId: String? = null,
    val maintenanceReason: String? = null,
    val maintenanceEndDate: String? = null
)

data class SetMaintenanceRequest(
    val reason: String,
    val expectedEndDate: String? = null  // ISO date string
)

// ============== RESPONSE MODELS ==============

/**
 * Vehicle Types Response (Public)
 */
data class VehicleTypesResponse(
    val success: Boolean,
    val data: VehicleTypesData? = null,
    val error: ApiError? = null
)

data class VehicleTypesData(
    val types: List<VehicleTypeInfo>
)

data class VehicleTypeInfo(
    val type: String,       // e.g. "container"
    val name: String,       // e.g. "Container"
    val subtypes: List<String>  // e.g. ["20 Feet", "24 Feet", "32 Feet"]
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
 * Vehicle List Response
 */
data class VehicleListResponse(
    val success: Boolean,
    val data: VehicleListData? = null,
    val error: ApiError? = null
)

data class VehicleListData(
    val vehicles: List<VehicleData>,
    val total: Int,
    val hasMore: Boolean,
    val page: Int,
    val limit: Int,
    val statusCounts: StatusCounts
)

data class StatusCounts(
    val total: Int = 0,
    val available: Int = 0,
    val inTransit: Int = 0,
    val maintenance: Int = 0,
    val inactive: Int = 0
)

/**
 * Available Vehicles Response
 */
data class AvailableVehiclesResponse(
    val success: Boolean,
    val data: AvailableVehiclesData? = null,
    val error: ApiError? = null
)

data class AvailableVehiclesData(
    val vehicles: List<VehicleData>
)

/**
 * Vehicle Summary Response
 */
data class VehicleSummaryResponse(
    val success: Boolean,
    val data: VehicleSummaryData? = null,
    val error: ApiError? = null
)

data class VehicleSummaryData(
    val summary: List<VehicleTypeSummary>
)

data class VehicleTypeSummary(
    val type: String,
    val count: Int
)

/**
 * Vehicle Stats Response
 */
data class VehicleStatsResponse(
    val success: Boolean,
    val data: VehicleStatsData? = null,
    val error: ApiError? = null
)

data class VehicleStatsData(
    val statusCounts: StatusCounts,
    val total: Int
)

/**
 * Single Vehicle Detail Response
 */
data class VehicleDetailResponse(
    val success: Boolean,
    val data: VehicleDetailData? = null,
    val message: String? = null,
    val error: ApiError? = null
)

data class VehicleDetailData(
    val vehicle: VehicleData
)

/**
 * Vehicle Data Model (from backend)
 */
data class VehicleData(
    val id: String,
    val transporterId: String,
    val vehicleNumber: String,
    val vehicleType: String,
    val vehicleSubtype: String,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val capacity: String? = null,
    val bodyType: String? = null,
    val fuelType: String? = null,
    val status: String = "available",  // available, in_transit, maintenance, inactive
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val currentTripId: String? = null,
    val maintenanceInfo: MaintenanceInfo? = null,
    val createdAt: String,
    val updatedAt: String
)

data class MaintenanceInfo(
    val reason: String? = null,
    val expectedEndDate: String? = null
)

/**
 * Generic Success Response
 */
data class GenericSuccessResponse(
    val success: Boolean,
    val message: String? = null,
    val error: ApiError? = null
)
