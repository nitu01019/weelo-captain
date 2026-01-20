package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Booking API Service - Matches weelo-backend/src/modules/booking/
 * 
 * Handles booking operations for transporters
 * Authorization header is added automatically by RetrofitClient interceptor
 */
interface BookingApiService {
    
    /**
     * Get all bookings for transporter
     * GET /api/v1/bookings
     */
    @GET("bookings")
    suspend fun getBookings(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<BookingsResponse>
    
    /**
     * Get booking details
     * GET /api/v1/bookings/{bookingId}
     */
    @GET("bookings/{bookingId}")
    suspend fun getBookingById(
        @Path("bookingId") bookingId: String
    ): Response<BookingDetailResponse>
    
    /**
     * Get active bookings
     * GET /api/v1/bookings/active
     */
    @GET("bookings/active")
    suspend fun getActiveBookings(): Response<BookingsResponse>
    
    /**
     * Get available trucks for a booking
     * GET /api/v1/bookings/{bookingId}/trucks
     */
    @GET("bookings/{bookingId}/trucks")
    suspend fun getAvailableTrucks(
        @Path("bookingId") bookingId: String
    ): Response<AvailableTrucksResponse>
    
    /**
     * Cancel a booking
     * POST /api/v1/bookings/{bookingId}/cancel
     */
    @POST("bookings/{bookingId}/cancel")
    suspend fun cancelBooking(
        @Path("bookingId") bookingId: String,
        @Body request: CancelBookingRequest
    ): Response<GenericSuccessResponse>
}

// ============== REQUEST MODELS ==============

data class CancelBookingRequest(
    val reason: String
)

// ============== RESPONSE MODELS ==============

data class BookingsResponse(
    val success: Boolean,
    val data: BookingsData? = null,
    val error: ApiError? = null
)

data class BookingsData(
    val bookings: List<BookingData>,
    val total: Int,
    val hasMore: Boolean
)

data class BookingDetailResponse(
    val success: Boolean,
    val data: BookingDetailData? = null,
    val error: ApiError? = null
)

data class BookingDetailData(
    val booking: BookingData
)

data class BookingData(
    val id: String,
    val customerId: String,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val transporterId: String? = null,
    val vehicleType: String,
    val vehicleSubtype: String? = null,
    val pickup: BookingLocation,
    val drop: BookingLocation,
    val scheduledDate: String? = null,
    val estimatedFare: Double? = null,
    val finalFare: Double? = null,
    val distanceKm: Double? = null,
    val status: String = "pending", // pending, accepted, assigned, in_progress, completed, cancelled
    val assignedVehicleId: String? = null,
    val assignedDriverId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class BookingLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val landmark: String? = null
)

data class AvailableTrucksResponse(
    val success: Boolean,
    val data: AvailableTrucksData? = null,
    val error: ApiError? = null
)

data class AvailableTrucksData(
    val trucks: List<AvailableTruck>
)

data class AvailableTruck(
    val vehicleId: String,
    val vehicleNumber: String,
    val vehicleType: String,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val distanceFromPickup: Double? = null,
    val estimatedArrival: Int? = null // minutes
)
