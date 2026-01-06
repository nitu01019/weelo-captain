package com.weelo.logistics.data.api

import com.weelo.logistics.data.model.Vehicle
import retrofit2.Response
import retrofit2.http.*

/**
 * Vehicle API Service - Fleet Management
 * 
 * BACKEND INTEGRATION NOTES:
 * ==========================
 * Base URL: https://api.weelo.in/v1/
 * Headers: Authorization: Bearer {accessToken}
 * 
 * All endpoints require transporter authentication
 */
interface VehicleApiService {
    
    /**
     * Get all vehicles for transporter
     * 
     * ENDPOINT: GET /vehicles
     * Query Params:
     * - transporterId: string (required)
     * - status: string (optional) - AVAILABLE, IN_TRANSIT, MAINTENANCE
     * - page: number (default: 1)
     * - limit: number (default: 20)
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "vehicles": [Vehicle array],
     *   "total": 50,
     *   "pagination": {PaginationData}
     * }
     */
    @GET("vehicles")
    suspend fun getVehicles(
        @Header("Authorization") token: String,
        @Query("transporterId") transporterId: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<VehicleListResponse>
    
    /**
     * Add new vehicle
     * 
     * ENDPOINT: POST /vehicles/add
     * 
     * Request Body:
     * {
     *   "transporterId": "transporter_123",
     *   "category": "CONTAINER",
     *   "subtype": "32 Feet Single Axle",
     *   "vehicleNumber": "GJ-01-AB-1234",
     *   "model": "Tata Prima",
     *   "year": 2022,
     *   "capacity": "20 tons",
     *   "documents": {
     *     "registrationCert": "url",
     *     "insurance": "url",
     *     "permit": "url"
     *   }
     * }
     * 
     * Response (201 Created):
     * {
     *   "success": true,
     *   "vehicle": {Vehicle object},
     *   "message": "Vehicle added successfully"
     * }
     */
    @POST("vehicles/add")
    suspend fun addVehicle(
        @Header("Authorization") token: String,
        @Body request: AddVehicleRequest
    ): Response<AddVehicleResponse>
    
    /**
     * Update vehicle details
     * 
     * ENDPOINT: PUT /vehicles/{vehicleId}
     */
    @PUT("vehicles/{vehicleId}")
    suspend fun updateVehicle(
        @Header("Authorization") token: String,
        @Path("vehicleId") vehicleId: String,
        @Body request: UpdateVehicleRequest
    ): Response<Vehicle>
    
    /**
     * Delete vehicle
     * 
     * ENDPOINT: DELETE /vehicles/{vehicleId}
     */
    @DELETE("vehicles/{vehicleId}")
    suspend fun deleteVehicle(
        @Header("Authorization") token: String,
        @Path("vehicleId") vehicleId: String
    ): Response<VehicleGenericResponse>
    
    /**
     * Assign driver to vehicle
     * 
     * ENDPOINT: POST /vehicles/{vehicleId}/assign-driver
     */
    @POST("vehicles/{vehicleId}/assign-driver")
    suspend fun assignDriver(
        @Header("Authorization") token: String,
        @Path("vehicleId") vehicleId: String,
        @Body request: AssignDriverRequest
    ): Response<VehicleGenericResponse>
}

// Request/Response Models
data class VehicleListResponse(
    val success: Boolean,
    val vehicles: List<Vehicle>,
    val total: Int,
    val pagination: VehiclePaginationData
)

data class VehiclePaginationData(
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int
)

data class AddVehicleRequest(
    val transporterId: String,
    val category: String,
    val subtype: String,
    val vehicleNumber: String,
    val model: String,
    val year: Int,
    val capacity: String,
    val documents: VehicleDocuments? = null
)

data class VehicleDocuments(
    val registrationCert: String?,
    val insurance: String?,
    val permit: String?
)

data class AddVehicleResponse(
    val success: Boolean,
    val vehicle: Vehicle,
    val message: String
)

data class UpdateVehicleRequest(
    val status: String?,
    val model: String?,
    val year: Int?
)

data class AssignDriverRequest(
    val driverId: String
)

data class VehicleGenericResponse(
    val success: Boolean,
    val message: String
)
