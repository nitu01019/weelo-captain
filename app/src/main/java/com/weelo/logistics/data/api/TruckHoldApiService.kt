package com.weelo.logistics.data.api

import com.google.gson.annotations.SerializedName
import com.weelo.logistics.data.model.HoldPhase
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
 * 1. holdTrucks() → Trucks held for server-configured hold window
 * 2. confirmHold() → Trucks assigned permanently  
 * 3. releaseHold() → Trucks released back to pool
 * 
 * =============================================================================
 */
interface TruckHoldApiService {
    
    /**
     * Hold trucks for selection (server-configured hold window)
     * POST /api/v1/truck-hold/hold
     */
    @POST("truck-hold/hold")
    suspend fun holdTrucks(
        @Body request: HoldTrucksRequest,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<HoldTrucksResponse>
    
    /**
     * Confirm held trucks (simple - assign permanently without vehicle/driver)
     * POST /api/v1/truck-hold/confirm
     */
    @POST("truck-hold/confirm")
    suspend fun confirmHold(
        @Body request: ConfirmHoldRequest
    ): Response<ConfirmHoldResponse>
    
    /**
     * Confirm held trucks WITH vehicle and driver assignments
     * POST /api/v1/truck-hold/confirm-with-assignments
     * 
     * This is the PRODUCTION endpoint that:
     * 1. Validates vehicle availability (not in another trip)
     * 2. Validates driver availability (not on another trip)
     * 3. Creates assignment records
     * 4. Updates vehicle status to 'in_transit'
     * 5. Notifies drivers and customer
     * 
     * CORE INVARIANTS ENFORCED:
     * - One truck can be assigned to only one active order
     * - One driver can be on only one active trip
     * - Atomic: all assignments succeed or none
     */
    @POST("truck-hold/confirm-with-assignments")
    suspend fun confirmHoldWithAssignments(
        @Body request: ConfirmHoldWithAssignmentsRequest,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<ConfirmHoldWithAssignmentsResponse>
    
    /**
     * Release/reject held trucks
     * POST /api/v1/truck-hold/release
     */
    @POST("truck-hold/release")
    suspend fun releaseHold(
        @Body request: ReleaseHoldRequest,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<ReleaseHoldResponse>

    /**
     * Get active hold for current transporter and vehicle key.
     * Used for timeout/uncertain-response reconciliation.
     * GET /api/v1/truck-hold/my-active
     */
    @GET("truck-hold/my-active")
    suspend fun getMyActiveHold(
        @Query("orderId") orderId: String,
        @Query("vehicleType") vehicleType: String,
        @Query("vehicleSubtype") vehicleSubtype: String
    ): Response<MyActiveHoldResponse>
    
    /**
     * Get real-time truck availability for an order
     * GET /api/v1/truck-hold/availability/{orderId}
     */
    @GET("truck-hold/availability/{orderId}")
    suspend fun getOrderAvailability(
        @Path("orderId") orderId: String
    ): Response<OrderAvailabilityResponse>

    // =========================================================================
    // PRD-7777 Phase 2: Flex Hold + Confirmed Hold + Driver Accept/Decline
    // =========================================================================

    /** A1: Create a flex hold (Phase 1 timed hold, 90s default) */
    @POST("truck-hold/flex-hold")
    suspend fun createFlexHold(
        @Body request: CreateFlexHoldRequest,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<FlexHoldResponse>

    /** A2: Extend flex hold (+30s, max 130s) */
    @POST("truck-hold/flex-hold/extend")
    suspend fun extendFlexHold(
        @Body request: ExtendFlexHoldRequest
    ): Response<FlexHoldResponse>

    /** A3: Initialize confirmed hold (Phase 2, exclusive lock, 180s max) */
    @POST("truck-hold/confirmed-hold/initialize")
    suspend fun initializeConfirmedHold(
        @Body request: InitializeConfirmedHoldRequest
    ): Response<ConfirmedHoldResponse>

    /** A4: Driver accepts assignment (45s window) */
    @PUT("truck-hold/driver/{assignmentId}/accept")
    suspend fun driverAcceptAssignment(
        @Path("assignmentId") assignmentId: String
    ): Response<DriverDecisionResponse>

    /** A5: Driver declines assignment */
    @PUT("truck-hold/driver/{assignmentId}/decline")
    suspend fun driverDeclineAssignment(
        @Path("assignmentId") assignmentId: String,
        @Body request: DriverDeclineRequest? = null
    ): Response<DriverDecisionResponse>

    /** A6: Get order progress (trucks filled/total) */
    @GET("truck-hold/order-progress/{orderId}")
    suspend fun getOrderProgress(
        @Path("orderId") orderId: String
    ): Response<OrderProgressResponse>

    /** A7: Get flex hold details */
    @GET("truck-hold/flex-hold/{holdId}")
    suspend fun getFlexHoldDetails(
        @Path("holdId") holdId: String
    ): Response<FlexHoldResponse>

    // =========================================================================
    // PRD-7777 Phase 2: Order Timeout + Order Assignments
    // =========================================================================

    /** A9: Initialize order timeout (120s base, auto-extend on driver accept) */
    @POST("truck-hold/order-timeout/initialize")
    suspend fun initializeOrderTimeout(
        @Body request: InitOrderTimeoutRequest
    ): Response<OrderTimeoutInitResponse>

    /** A10: Get order timeout state (remaining time, extensions) */
    @GET("truck-hold/order-timeout/{orderId}")
    suspend fun getOrderTimeout(
        @Path("orderId") orderId: String
    ): Response<OrderTimeoutStateResponse>

    /** A11: Get all assignments for an order (truck + driver details) */
    @GET("truck-hold/order-assignments/{orderId}")
    suspend fun getOrderAssignments(
        @Path("orderId") orderId: String
    ): Response<OrderAssignmentsResponse>
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

/**
 * Request for confirming hold WITH vehicle and driver assignments
 * 
 * Each assignment maps a truck request to a specific vehicle and driver.
 * The number of assignments MUST match the number of trucks held.
 */
data class ConfirmHoldWithAssignmentsRequest(
    @SerializedName("holdId")
    val holdId: String,
    
    @SerializedName("assignments")
    val assignments: List<VehicleDriverAssignment>
)

/**
 * Single vehicle + driver assignment
 */
data class VehicleDriverAssignment(
    @SerializedName("vehicleId")
    val vehicleId: String,
    
    @SerializedName("driverId")
    val driverId: String
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
    val heldQuantity: Int,

    @SerializedName("holdState")
    val holdState: String? = null,

    @SerializedName("eventId")
    val eventId: String? = null,

    @SerializedName("eventVersion")
    val eventVersion: Int? = null,

    @SerializedName("serverTimeMs")
    val serverTimeMs: Long? = null
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

/**
 * Response for confirm with assignments
 */
data class ConfirmHoldWithAssignmentsResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: ConfirmWithAssignmentsData?,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("error")
    val error: ConfirmWithAssignmentsError?
)

data class ConfirmWithAssignmentsData(
    @SerializedName("assignmentIds")
    val assignmentIds: List<String>,
    
    @SerializedName("tripIds")
    val tripIds: List<String>
)

data class ConfirmWithAssignmentsError(
    @SerializedName("code")
    val code: String?,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("failedAssignments")
    val failedAssignments: List<FailedAssignment>?
)

data class FailedAssignment(
    @SerializedName("vehicleId")
    val vehicleId: String,
    
    @SerializedName("reason")
    val reason: String
)

data class ReleaseHoldResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String?,

    @SerializedName("error")
    val error: ApiError? = null
)

data class MyActiveHoldResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("data")
    val data: MyActiveHoldData?,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: ApiError? = null
)

data class MyActiveHoldData(
    @SerializedName("holdId")
    val holdId: String,

    @SerializedName("orderId")
    val orderId: String,

    @SerializedName("vehicleType")
    val vehicleType: String,

    @SerializedName("vehicleSubtype")
    val vehicleSubtype: String,

    @SerializedName("quantity")
    val quantity: Int,

    @SerializedName("expiresAt")
    val expiresAt: String,

    @SerializedName("status")
    val status: String,

    // F-C-34 forward-compat: phase is populated by the backend F-C-25
    // extension. Nullable so old backends (pre-extension) do not fail
    // deserialization — `null` is treated as HoldPhase.UNKNOWN at the UI
    // layer and falls back to LegacyCountdownBlock.
    @SerializedName("phase")
    val phase: com.weelo.logistics.data.model.HoldPhase? = null,

    // F-C-34: `canExtend` mirrors the FLEX-phase extension availability.
    // Default false so pre-extension backends never show the Extend button.
    @SerializedName("canExtend")
    val canExtend: Boolean = false
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

// =============================================================================
// PRD-7777 REQUEST MODELS
// =============================================================================

data class CreateFlexHoldRequest(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("vehicleType") val vehicleType: String,
    @SerializedName("vehicleSubtype") val vehicleSubtype: String,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("truckRequestIds") val truckRequestIds: List<String>? = null
)

data class ExtendFlexHoldRequest(
    @SerializedName("holdId") val holdId: String
)

data class InitializeConfirmedHoldRequest(
    @SerializedName("holdId") val holdId: String,
    @SerializedName("assignments") val assignments: List<VehicleDriverAssignment>
)

data class DriverDeclineRequest(
    @SerializedName("reason") val reason: String? = null
)

// =============================================================================
// PRD-7777 RESPONSE MODELS
// =============================================================================

data class FlexHoldResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: FlexHoldData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: ApiError? = null
)

data class FlexHoldData(
    @SerializedName("holdId") val holdId: String,
    @SerializedName("phase") val phase: HoldPhase,
    @SerializedName("expiresAt") val expiresAt: String,
    @SerializedName("remainingSeconds") val remainingSeconds: Int,
    @SerializedName("canExtend") val canExtend: Boolean = false,
    @SerializedName("totalDurationSeconds") val totalDurationSeconds: Int? = null
)

data class ConfirmedHoldResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: ConfirmedHoldData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: ApiError? = null
)

data class ConfirmedHoldData(
    @SerializedName("holdId") val holdId: String,
    @SerializedName("phase") val phase: HoldPhase,
    @SerializedName("expiresAt") val expiresAt: String,
    @SerializedName("assignmentIds") val assignmentIds: List<String>,
    @SerializedName("driverAcceptTimeoutSeconds") val driverAcceptTimeoutSeconds: Int
)

data class DriverDecisionResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: DriverDecisionData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: ApiError? = null
)

data class DriverDecisionData(
    @SerializedName("assignmentId") val assignmentId: String,
    @SerializedName("decision") val decision: String,
    @SerializedName("tripId") val tripId: String? = null
)

data class OrderProgressResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: OrderProgressData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: ApiError? = null
)

data class OrderProgressData(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("trucksNeeded") val trucksNeeded: Int,
    @SerializedName("trucksFilled") val trucksFilled: Int,
    @SerializedName("trucksRemaining") val trucksRemaining: Int,
    @SerializedName("isFullyFilled") val isFullyFilled: Boolean,
    @SerializedName("assignments") val assignments: List<OrderProgressAssignment>? = null
)

data class OrderProgressAssignment(
    @SerializedName("assignmentId") val assignmentId: String,
    @SerializedName("driverName") val driverName: String? = null,
    @SerializedName("vehicleNumber") val vehicleNumber: String? = null,
    @SerializedName("status") val status: String
)

// =============================================================================
// A9: ORDER TIMEOUT REQUEST/RESPONSE MODELS
// =============================================================================

data class InitOrderTimeoutRequest(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("totalTrucks") val totalTrucks: Int
)

data class OrderTimeoutInitResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: OrderTimeoutInitData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: ApiError? = null
)

data class OrderTimeoutInitData(
    @SerializedName("expiresAt") val expiresAt: String
)

// =============================================================================
// A10: ORDER TIMEOUT STATE RESPONSE MODELS
// =============================================================================

data class OrderTimeoutStateResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: OrderTimeoutStateData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: ApiError? = null
)

data class OrderTimeoutStateData(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("baseTimeoutMs") val baseTimeoutMs: Long,
    @SerializedName("extendedMs") val extendedMs: Long,
    @SerializedName("totalTimeoutMs") val totalTimeoutMs: Long,
    @SerializedName("expiresAt") val expiresAt: String,
    @SerializedName("remainingSeconds") val remainingSeconds: Int,
    @SerializedName("isExpired") val isExpired: Boolean
)

// =============================================================================
// A11: ORDER ASSIGNMENTS RESPONSE MODELS
// =============================================================================

data class OrderAssignmentsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: OrderAssignmentsData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: ApiError? = null
)

data class OrderAssignmentsData(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("assignments") val assignments: List<OrderAssignmentDetail> = emptyList()
)

data class OrderAssignmentDetail(
    @SerializedName("assignmentId") val assignmentId: String,
    @SerializedName("vehicleNumber") val vehicleNumber: String? = null,
    @SerializedName("vehicleType") val vehicleType: String? = null,
    @SerializedName("vehicleSubtype") val vehicleSubtype: String? = null,
    @SerializedName("driverName") val driverName: String? = null,
    @SerializedName("driverPhone") val driverPhone: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("tripId") val tripId: String? = null,
    @SerializedName("assignedAt") val assignedAt: String? = null
)
