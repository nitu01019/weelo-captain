package com.weelo.logistics.data.api

import com.weelo.logistics.data.model.Driver
import retrofit2.Response
import retrofit2.http.*

/**
 * Driver Management API Service
 * 
 * BACKEND INTEGRATION NOTES:
 * ==========================
 * Base URL: https://api.weelo.in/v1/
 * Headers: Authorization: Bearer {accessToken}
 * 
 * For transporter to manage their drivers
 */
interface DriverManagementApiService {
    
    /**
     * Get all drivers for transporter
     * 
     * ENDPOINT: GET /drivers
     * Query Params:
     * - transporterId: string (required)
     * - status: string (optional) - ACTIVE, INACTIVE, ON_TRIP
     * - page: number (default: 1)
     * - limit: number (default: 20)
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "drivers": [Driver array],
     *   "total": 25,
     *   "pagination": {PaginationData}
     * }
     */
    @GET("drivers")
    suspend fun getDrivers(
        @Header("Authorization") token: String,
        @Query("transporterId") transporterId: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<DriverListResponse>
    
    /**
     * Add new driver
     * 
     * ENDPOINT: POST /drivers/add
     * 
     * Request Body:
     * {
     *   "transporterId": "transporter_123",
     *   "name": "Rajesh Kumar",
     *   "mobileNumber": "9876543210",
     *   "licenseNumber": "DL1420110012345",
     *   "licenseExpiry": "2025-12-31",
     *   "emergencyContact": "9876543211",
     *   "address": "123 Street, City"
     * }
     * 
     * Response (201 Created):
     * {
     *   "success": true,
     *   "driver": {Driver object},
     *   "message": "Driver invitation sent via SMS",
     *   "invitationId": "inv_123"
     * }
     * 
     * NOTE: Backend sends SMS invitation to driver's mobile
     */
    @POST("drivers/add")
    suspend fun addDriver(
        @Header("Authorization") token: String,
        @Body request: AddDriverRequest
    ): Response<AddDriverResponse>
    
    /**
     * Update driver details
     * 
     * ENDPOINT: PUT /drivers/{driverId}
     */
    @PUT("drivers/{driverId}")
    suspend fun updateDriver(
        @Header("Authorization") token: String,
        @Path("driverId") driverId: String,
        @Body request: UpdateDriverRequest
    ): Response<Driver>
    
    /**
     * Delete/Remove driver
     * 
     * ENDPOINT: DELETE /drivers/{driverId}
     */
    @DELETE("drivers/{driverId}")
    suspend fun deleteDriver(
        @Header("Authorization") token: String,
        @Path("driverId") driverId: String
    ): Response<DriverGenericResponse>
    
    data class DriverGenericResponse(
        val success: Boolean,
        val message: String
    )
    
    /**
     * Get driver performance
     * 
     * ENDPOINT: GET /drivers/{driverId}/performance
     */
    @GET("drivers/{driverId}/performance")
    suspend fun getDriverPerformance(
        @Header("Authorization") token: String,
        @Path("driverId") driverId: String
    ): Response<DriverPerformanceResponse>
}

// Request/Response Models
data class DriverListResponse(
    val success: Boolean,
    val drivers: List<Driver>,
    val total: Int,
    val pagination: DriverPaginationData
)

data class DriverPaginationData(
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int
)

data class AddDriverRequest(
    val transporterId: String,
    val name: String,
    val mobileNumber: String,
    val licenseNumber: String,
    val licenseExpiry: String? = null,
    val emergencyContact: String? = null,
    val address: String? = null
)

data class AddDriverResponse(
    val success: Boolean,
    val driver: Driver,
    val message: String,
    val invitationId: String
)

data class UpdateDriverRequest(
    val name: String?,
    val status: String?,
    val licenseExpiry: String?
)

data class DriverPerformanceResponse(
    val success: Boolean,
    val performance: DriverPerformance
)

data class DriverPerformance(
    val totalTrips: Int,
    val completedTrips: Int,
    val averageRating: Float,
    val totalEarnings: Double,
    val totalDistance: Double
)
