package com.weelo.logistics.data.api

import com.weelo.logistics.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Driver API Service - For driver-specific operations
 * 
 * BACKEND INTEGRATION NOTES:
 * ==========================
 * Base URL: https://api.weelo.in/v1/
 * Headers: Authorization: Bearer {accessToken}
 */
interface DriverApiService {
    
    /**
     * Get driver dashboard data
     * 
     * ENDPOINT: GET /driver/dashboard
     * Headers: Authorization: Bearer {accessToken}
     * Query Params:
     * - driverId: string (required)
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "dashboard": {
     *     "isAvailable": true,
     *     "activeTrip": {Trip object or null},
     *     "todayTrips": 5,
     *     "todayEarnings": 12500.0,
     *     "todayDistance": 150.5,
     *     "weekEarnings": 62500.0,
     *     "monthEarnings": 250000.0,
     *     "rating": 4.5,
     *     "totalTrips": 156,
     *     "pendingTrips": [Trip array]
     *   }
     * }
     */
    @GET("driver/dashboard")
    suspend fun getDriverDashboard(
        @Header("Authorization") token: String,
        @Query("driverId") driverId: String
    ): Response<DriverDashboardResponse>
    
    /**
     * Update driver availability status
     * 
     * ENDPOINT: PUT /driver/availability
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "driverId": "driver_123",
     *   "isAvailable": true
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "isAvailable": true,
     *   "message": "Status updated successfully"
     * }
     */
    @PUT("driver/availability")
    suspend fun updateAvailability(
        @Header("Authorization") token: String,
        @Body request: UpdateAvailabilityRequest
    ): Response<UpdateAvailabilityResponse>
    
    /**
     * Get driver notifications
     * 
     * ENDPOINT: GET /driver/notifications
     * Headers: Authorization: Bearer {accessToken}
     * Query Params:
     * - driverId: string (required)
     * - status: string (optional) - "PENDING_RESPONSE", "ACCEPTED", "DECLINED", "EXPIRED"
     * - page: number (default: 1)
     * - limit: number (default: 20)
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "notifications": [DriverNotification array],
     *   "unreadCount": 5,
     *   "pagination": {PaginationData}
     * }
     */
    @GET("driver/notifications")
    suspend fun getDriverNotifications(
        @Header("Authorization") token: String,
        @Query("driverId") driverId: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<DriverNotificationsResponse>
    
    /**
     * Mark notification as read
     * 
     * ENDPOINT: PUT /driver/notifications/{notificationId}/read
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "message": "Notification marked as read"
     * }
     */
    @PUT("driver/notifications/{notificationId}/read")
    suspend fun markNotificationAsRead(
        @Header("Authorization") token: String,
        @Path("notificationId") notificationId: String
    ): Response<GenericResponse>
    
    /**
     * Get driver trip history
     * 
     * ENDPOINT: GET /driver/trips/history
     * Headers: Authorization: Bearer {accessToken}
     * Query Params:
     * - driverId: string (required)
     * - status: string (optional) - "COMPLETED", "CANCELLED"
     * - startDate: string (optional) - ISO date
     * - endDate: string (optional) - ISO date
     * - page: number (default: 1)
     * - limit: number (default: 20)
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "trips": [Trip array],
     *   "statistics": {
     *     "totalTrips": 156,
     *     "totalEarnings": 850000.0,
     *     "totalDistance": 12500.5,
     *     "averageRating": 4.5
     *   },
     *   "pagination": {PaginationData}
     * }
     */
    @GET("driver/trips/history")
    suspend fun getDriverTripHistory(
        @Header("Authorization") token: String,
        @Query("driverId") driverId: String,
        @Query("status") status: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<DriverTripHistoryResponse>
    
    /**
     * Get driver earnings details
     * 
     * ENDPOINT: GET /driver/earnings
     * Headers: Authorization: Bearer {accessToken}
     * Query Params:
     * - driverId: string (required)
     * - period: string - "TODAY", "WEEK", "MONTH", "CUSTOM"
     * - startDate: string (optional for CUSTOM)
     * - endDate: string (optional for CUSTOM)
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "earnings": {
     *     "totalEarnings": 250000.0,
     *     "totalTrips": 25,
     *     "averagePerTrip": 10000.0,
     *     "breakdown": [
     *       {
     *         "date": "2026-01-05",
     *         "earnings": 12500.0,
     *         "trips": 5
     *       }
     *     ]
     *   }
     * }
     */
    @GET("driver/earnings")
    suspend fun getDriverEarnings(
        @Header("Authorization") token: String,
        @Query("driverId") driverId: String,
        @Query("period") period: String = "MONTH",
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<DriverEarningsResponse>
    
    /**
     * Update driver profile
     * 
     * ENDPOINT: PUT /driver/profile
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "driverId": "driver_123",
     *   "name": "John Doe",
     *   "email": "john@example.com",
     *   "address": "123 Street, City",
     *   "emergencyContact": "9876543210"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "driver": {Driver object}
     * }
     */
    @PUT("driver/profile")
    suspend fun updateDriverProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateDriverProfileRequest
    ): Response<UpdateDriverProfileResponse>
    
    /**
     * Update FCM token for push notifications
     * 
     * ENDPOINT: PUT /driver/fcm-token
     * Headers: Authorization: Bearer {accessToken}
     * 
     * Request Body:
     * {
     *   "driverId": "driver_123",
     *   "fcmToken": "fcm_token_string",
     *   "deviceId": "device_unique_id"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "message": "FCM token updated"
     * }
     */
    @PUT("driver/fcm-token")
    suspend fun updateFCMToken(
        @Header("Authorization") token: String,
        @Body request: UpdateFCMTokenRequest
    ): Response<GenericResponse>
}

// ============== Request/Response Data Classes ==============

data class DriverDashboardResponse(
    val success: Boolean,
    val dashboard: DriverDashboard
)

data class UpdateAvailabilityRequest(
    val driverId: String,
    val isAvailable: Boolean
)

data class UpdateAvailabilityResponse(
    val success: Boolean,
    val isAvailable: Boolean,
    val message: String
)

data class DriverNotificationsResponse(
    val success: Boolean,
    val notifications: List<DriverNotification>,
    val unreadCount: Int,
    val pagination: PaginationData
)

data class DriverTripHistoryResponse(
    val success: Boolean,
    val trips: List<Trip>,
    val statistics: TripStatistics,
    val pagination: PaginationData
)

data class TripStatistics(
    val totalTrips: Int,
    val totalEarnings: Double,
    val totalDistance: Double,
    val averageRating: Float
)

data class DriverEarningsResponse(
    val success: Boolean,
    val earnings: EarningsData
)

data class EarningsData(
    val totalEarnings: Double,
    val totalTrips: Int,
    val averagePerTrip: Double,
    val breakdown: List<EarningsBreakdown>
)

data class EarningsBreakdown(
    val date: String,
    val earnings: Double,
    val trips: Int
)

data class UpdateDriverProfileRequest(
    val driverId: String,
    val name: String,
    val email: String?,
    val address: String?,
    val emergencyContact: String?
)

data class UpdateDriverProfileResponse(
    val success: Boolean,
    val driver: Driver
)

data class UpdateFCMTokenRequest(
    val driverId: String,
    val fcmToken: String,
    val deviceId: String
)

data class GenericResponse(
    val success: Boolean,
    val message: String,
    val error: String? = null
)
