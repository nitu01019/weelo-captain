package com.weelo.logistics.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

/**
 * =============================================================================
 * TRUCK HOLD API SERVICE
 * =============================================================================
 * 
 * API endpoints for the BookMyShow-style truck holding system.
 * 
 * FLOW:
 * 1. holdTrucks() → Trucks held for 15 seconds
 * 2. confirmHold() → Trucks assigned permanently  
 * 3. releaseHold() → Trucks released back to pool
 * 
 * =============================================================================
 */
interface TruckHoldApiService {
    
    /**
     * Hold trucks for selection
     * POST /api/v1/truck-hold/hold
     */
    @POST("truck-hold/hold")
    suspend fun holdTrucks(
        @Body request: HoldTrucksRequest
    ): Response<HoldTrucksResponse>
    
    /**
     * Confirm held trucks (assign permanently)
     * POST /api/v1/truck-hold/confirm
     */
    @POST("truck-hold/confirm")
    suspend fun confirmHold(
        @Body request: ConfirmHoldRequest
    ): Response<ConfirmHoldResponse>
    
    /**
     * Release/reject held trucks
     * POST /api/v1/truck-hold/release
     */
    @POST("truck-hold/release")
    suspend fun releaseHold(
        @Body request: ReleaseHoldRequest
    ): Response<ReleaseHoldResponse>
    
    /**
     * Get real-time truck availability for an order
     * GET /api/v1/truck-hold/availability/{orderId}
     */
    @GET("truck-hold/availability/{orderId}")
    suspend fun getOrderAvailability(
        @Path("orderId") orderId: String
    ): Response<OrderAvailabilityResponse>
}

// =============================================================================
// REQUEST MODELS
// =============================================================================

data class HoldTrucksRequest(
    @SerializedName("orderId")
    val orderId: String,
    
    @SerializedName("vehicleType")
    val vehicleType: String,
    
    @SerializedName("vehicleSubtype")
    val vehicleSubtype: String,
    
    @SerializedName("quantity")
    val quantity: Int
)

data class ConfirmHoldRequest(
    @SerializedName("holdId")
    val holdId: String
)

data class ReleaseHoldRequest(
    @SerializedName("holdId")
    val holdId: String
)

// =============================================================================
// RESPONSE MODELS
// =============================================================================

data class HoldTrucksResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: HoldData?,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("error")
    val error: ApiError?
)

data class HoldData(
    @SerializedName("holdId")
    val holdId: String,
    
    @SerializedName("expiresAt")
    val expiresAt: String,
    
    @SerializedName("heldQuantity")
    val heldQuantity: Int
)

data class ConfirmHoldResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: ConfirmData?,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("error")
    val error: ApiError?
)

data class ConfirmData(
    @SerializedName("assignedTrucks")
    val assignedTrucks: List<String>
)

data class ReleaseHoldResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String?
)

data class OrderAvailabilityResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: OrderAvailability?,
    
    @SerializedName("error")
    val error: ApiError?
)

data class OrderAvailability(
    @SerializedName("orderId")
    val orderId: String,
    
    @SerializedName("customerName")
    val customerName: String,
    
    @SerializedName("customerPhone")
    val customerPhone: String,
    
    @SerializedName("pickup")
    val pickup: HoldLocationData,
    
    @SerializedName("drop")
    val drop: HoldLocationData,
    
    @SerializedName("distanceKm")
    val distanceKm: Double,
    
    @SerializedName("goodsType")
    val goodsType: String,
    
    @SerializedName("trucks")
    val trucks: List<TruckAvailability>,
    
    @SerializedName("totalValue")
    val totalValue: Double,
    
    @SerializedName("isFullyAssigned")
    val isFullyAssigned: Boolean
)

data class HoldLocationData(
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("address")
    val address: String,
    
    @SerializedName("city")
    val city: String?,
    
    @SerializedName("state")
    val state: String?
)

data class TruckAvailability(
    @SerializedName("vehicleType")
    val vehicleType: String,
    
    @SerializedName("vehicleSubtype")
    val vehicleSubtype: String,
    
    @SerializedName("totalNeeded")
    val totalNeeded: Int,
    
    @SerializedName("available")
    val available: Int,
    
    @SerializedName("held")
    val held: Int,
    
    @SerializedName("assigned")
    val assigned: Int,
    
    @SerializedName("farePerTruck")
    val farePerTruck: Double
)
