package com.weelo.logistics.data.model

/**
 * BROADCAST SYSTEM DATA MODELS
 * ============================
 * These models handle the broadcast-based trip assignment flow:
 * 1. Customer creates booking → Broadcast sent to transporters
 * 2. Transporter views broadcast → Selects trucks → Assigns drivers
 * 3. Driver receives notification → Accepts/Declines
 * 4. Trip tracking begins on acceptance
 * 
 * FOR BACKEND DEVELOPER:
 * - Use BroadcastTrip for customer broadcasts to transporters
 * - Use TripAssignment for transporter → driver assignments
 * - Use DriverNotification for push notifications to drivers
 * - Status flow: BROADCAST → TRANSPORTER_PARTIAL_FILLED → DRIVER_ASSIGNED → DRIVER_ACCEPTED → IN_PROGRESS
 */

/**
 * RequestedVehicle - A single vehicle type request with count and pricing
 * Used for multi-truck broadcast requests
 */
data class RequestedVehicle(
    val vehicleType: String,                    // e.g., "open", "container", "tipper"
    val vehicleSubtype: String,                 // e.g., "20 Feet", "32 Feet Multi"
    val count: Int,                             // Number of this truck type needed
    val filledCount: Int = 0,                   // How many of this type already filled
    val farePerTruck: Double,                   // Fare per truck for this type
    val capacityTons: Double = 0.0              // Capacity in tons
) {
    val remainingCount: Int get() = count - filledCount
    val totalFare: Double get() = farePerTruck * count
}

/**
 * BroadcastTrip - Customer broadcast message to transporters
 * This is what transporters receive when a customer needs trucks
 * 
 * SUPPORTS MULTIPLE TRUCK TYPES:
 * - Customer can request 2x Open Trucks + 3x Containers + 1x Tipper
 * - Each type has its own count and pricing
 * - Transporter only sees trucks they have registered
 * 
 * BACKEND: Send this via push notification/websocket to all nearby transporters
 */
data class BroadcastTrip(
    val broadcastId: String,                    // Unique broadcast ID
    val customerId: String,                     // Customer who created the broadcast
    val customerName: String,                   // Customer display name
    val customerMobile: String,                 // Customer contact
    
    // Trip Details
    val pickupLocation: Location,               // Where to pick up goods
    val dropLocation: Location,                 // Where to deliver
    val distance: Double,                       // Distance in km
    val estimatedDuration: Long,                // Estimated duration in minutes
    
    // === MULTI-TRUCK REQUIREMENTS ===
    val requestedVehicles: List<RequestedVehicle> = emptyList(),  // Multiple truck types with counts
    val totalTrucksNeeded: Int,                 // Total trucks across all types
    val trucksFilledSoFar: Int = 0,            // Total filled across all types
    
    // Legacy single vehicle type (for backward compatibility)
    @Deprecated("Use requestedVehicles instead")
    val vehicleType: TruckCategory? = null,     // Type of vehicle required (DEPRECATED)
    
    val goodsType: String,                      // Type of goods to transport
    val weight: String? = null,                 // Weight of goods
    
    // Pricing (Algorithm-based)
    val farePerTruck: Double,                   // Average fare per truck (for display)
    val totalFare: Double,                      // Total fare for all trucks
    
    // Broadcast Status
    val status: BroadcastStatus = BroadcastStatus.ACTIVE,
    val broadcastTime: Long = System.currentTimeMillis(),
    val expiryTime: Long? = null,              // When broadcast expires
    
    // Additional Info
    val notes: String? = null,                  // Special instructions
    val isUrgent: Boolean = false               // Priority flag
) {
    /**
     * Get remaining trucks needed across all types
     */
    val totalRemainingTrucks: Int 
        get() = if (requestedVehicles.isNotEmpty()) {
            requestedVehicles.sumOf { it.remainingCount }
        } else {
            totalTrucksNeeded - trucksFilledSoFar
        }
    
    /**
     * Check if this broadcast has multiple truck types
     */
    val hasMultipleTruckTypes: Boolean 
        get() = requestedVehicles.size > 1
    
    /**
     * Get vehicle types as display string
     */
    val vehicleTypesDisplay: String
        get() = if (requestedVehicles.isNotEmpty()) {
            requestedVehicles.joinToString(", ") { 
                "${it.count}x ${it.vehicleType.replaceFirstChar { c -> c.uppercase() }}"
            }
        } else {
            vehicleType?.name ?: "Truck"
        }
}

/**
 * TripAssignment - When transporter assigns drivers to selected trucks
 * This represents the assignment from transporter to specific driver
 * 
 * BACKEND: Create this when transporter selects trucks and assigns drivers
 */
data class TripAssignment(
    val assignmentId: String,
    val broadcastId: String,                    // Original broadcast ID
    val transporterId: String,                  // Who is assigning
    val trucksTaken: Int,                       // Number of trucks transporter is taking (e.g., 3 out of 10)
    
    // Driver Assignment Details
    val assignments: List<DriverTruckAssignment>, // Each truck gets one driver
    
    // Trip Details (copied from broadcast)
    val pickupLocation: Location,
    val dropLocation: Location,
    val distance: Double,
    val farePerTruck: Double,
    val goodsType: String,
    
    val assignedAt: Long = System.currentTimeMillis(),
    val status: AssignmentStatus = AssignmentStatus.PENDING_DRIVER_RESPONSE
)

/**
 * DriverTruckAssignment - Individual driver assigned to a specific truck
 */
data class DriverTruckAssignment(
    val driverId: String,
    val driverName: String,
    val vehicleId: String,                      // Which truck/vehicle
    val vehicleNumber: String,
    val status: DriverResponseStatus = DriverResponseStatus.PENDING
)

/**
 * DriverNotification - Push notification sent to driver's app
 * This is what appears on driver's screen with sound/vibration
 * 
 * BACKEND: Send this as push notification when transporter assigns a trip
 */
data class DriverTripNotification(
    val notificationId: String,
    val assignmentId: String,                   // Link to trip assignment
    val driverId: String,                       // Which driver to notify
    
    // Trip Preview (so driver can decide)
    val pickupAddress: String,
    val dropAddress: String,
    val distance: Double,
    val estimatedDuration: Long,
    val fare: Double,
    val goodsType: String,
    
    // Notification Metadata
    val receivedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val soundPlayed: Boolean = false,           // Track if alert sound was played
    val expiryTime: Long? = null,               // Auto-decline after expiry
    
    val status: NotificationStatus = NotificationStatus.PENDING_RESPONSE
)

/**
 * TripReassignment - When driver declines and transporter needs to reassign
 * 
 * BACKEND: Create this when driver declines, allow transporter to pick new driver
 */
data class TripReassignment(
    val reassignmentId: String,
    val originalAssignmentId: String,
    val broadcastId: String,
    val transporterId: String,
    val vehicleId: String,                      // Same vehicle, different driver
    
    val previousDriverId: String,               // Who declined
    val previousDriverName: String,
    val declinedAt: Long,
    val declineReason: String? = null,
    
    // Reassignment
    val newDriverId: String? = null,            // New driver (null until assigned)
    val newDriverName: String? = null,
    val reassignedAt: Long? = null,
    
    val status: ReassignmentStatus = ReassignmentStatus.WAITING_FOR_NEW_DRIVER
)

/**
 * LiveTripTracking - Real-time location tracking once driver accepts
 * This starts tracking driver's location throughout the trip
 * 
 * BACKEND: Update this every few seconds with driver's GPS coordinates
 */
data class LiveTripTracking(
    val trackingId: String,
    val assignmentId: String,
    val driverId: String,
    val vehicleId: String,
    
    // Current Location
    val currentLocation: Location,
    val currentLatitude: Double,
    val currentLongitude: Double,
    
    // Trip Progress
    val tripStatus: TripStatus,
    val startedAt: Long? = null,
    val currentSpeed: Float = 0f,               // km/h
    val heading: Float = 0f,                    // Direction in degrees
    
    // Tracking Metadata
    val lastUpdated: Long = System.currentTimeMillis(),
    val isLocationSharing: Boolean = true
)

// ============================================
// ENUMS - Status tracking for various stages
// ============================================

/**
 * BroadcastStatus - Lifecycle of customer broadcast
 */
enum class BroadcastStatus {
    ACTIVE,                 // Currently broadcasting to transporters
    PARTIALLY_FILLED,       // Some transporters took trucks, still need more
    FULLY_FILLED,          // All trucks assigned
    EXPIRED,               // Time limit exceeded
    CANCELLED              // Customer cancelled
}

/**
 * AssignmentStatus - Status of transporter's assignment
 */
enum class AssignmentStatus {
    PENDING_DRIVER_RESPONSE,    // Waiting for driver to accept/decline
    DRIVER_ACCEPTED,            // Driver accepted the trip
    DRIVER_DECLINED,            // Driver declined, needs reassignment
    TRIP_STARTED,               // Driver started the trip
    TRIP_COMPLETED,             // Trip finished successfully
    CANCELLED                   // Assignment cancelled
}

/**
 * DriverResponseStatus - Individual driver's response to assignment
 */
enum class DriverResponseStatus {
    PENDING,                // Notification sent, waiting for response
    ACCEPTED,               // Driver accepted
    DECLINED,               // Driver declined
    EXPIRED,                // Driver didn't respond in time
    REASSIGNED              // Assigned to different driver
}

/**
 * NotificationStatus - Status of notification sent to driver
 */
enum class NotificationStatus {
    PENDING_RESPONSE,       // Notification sent, waiting for action
    ACCEPTED,               // Driver accepted via notification
    DECLINED,               // Driver declined via notification
    EXPIRED,                // Notification expired (timeout)
    READ                    // Driver opened but didn't act
}

/**
 * ReassignmentStatus - Status of reassignment process
 */
enum class ReassignmentStatus {
    WAITING_FOR_NEW_DRIVER, // Waiting for transporter to pick new driver
    NEW_DRIVER_ASSIGNED,    // New driver assigned, waiting for response
    COMPLETED,              // New driver accepted
    FAILED                  // Unable to reassign
}
