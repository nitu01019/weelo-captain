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
 * 
 * ROUTE POINTS (Intermediate Stops):
 * - routePoints array contains: PICKUP → STOP → STOP → DROP
 * - Stops are defined BEFORE booking (immutable after)
 * - currentRouteIndex tracks driver progress (0, 1, 2, 3...)
 * - Driver sees full route before accepting
 */

// =============================================================================
// ROUTE POINT - For intermediate stops
// =============================================================================

/**
 * Type of route point
 */
enum class RoutePointType {
    PICKUP,  // Starting point (always index 0)
    STOP,    // Intermediate stop (index 1 to N-1)
    DROP     // Final destination (always last index)
}

/**
 * Single point in a route
 * 
 * IMPORTANT: Route is IMMUTABLE after booking!
 * - Max 4 points: 1 pickup + 2 stops + 1 drop
 * - Progress tracked via currentRouteIndex
 * 
 * BACKEND: Send this in routePoints array of BroadcastTrip
 */
data class RoutePoint(
    val type: RoutePointType,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String? = null,
    val stopIndex: Int                      // 0=pickup, 1,2=stops, N=drop
) {
    /**
     * Get display name for this point
     */
    val displayName: String
        get() = when (type) {
            RoutePointType.PICKUP -> "Pickup"
            RoutePointType.STOP -> "Stop ${stopIndex}"
            RoutePointType.DROP -> "Drop"
        }
    
    /**
     * Get short address (first part before comma)
     */
    val shortAddress: String
        get() = address.split(",").firstOrNull()?.trim() ?: address
}

// =============================================================================
// ROUTE LEG - For ETA per leg visualization
// =============================================================================

/**
 * Single leg of a route (segment between two consecutive points)
 * 
 * EXAMPLE:
 * Leg 0: Delhi → Jaipur
 *   distanceKm: 270
 *   durationMinutes: 405 (6.75 hours)
 *   durationFormatted: "6 hrs 45 mins"
 *   etaMinutes: 405 (cumulative from start)
 * 
 * USE IN UI:
 * - Show distance and ETA for each leg
 * - Highlight current leg driver is on
 * - Calculate remaining time from current position
 */
data class RouteLeg(
    val fromIndex: Int,
    val toIndex: Int,
    val fromType: String,                   // "PICKUP", "STOP", "DROP"
    val toType: String,
    val fromAddress: String,
    val toAddress: String,
    val fromCity: String? = null,
    val toCity: String? = null,
    val distanceKm: Int,
    val durationMinutes: Int,
    val durationFormatted: String,          // "6 hrs 45 mins"
    val etaMinutes: Int                     // Cumulative ETA from trip start
) {
    /**
     * Get leg display string (e.g., "Delhi → Jaipur • 270 km • 6 hrs 45 mins")
     */
    val displayString: String
        get() {
            val from = fromCity ?: fromAddress.split(",").firstOrNull() ?: "Start"
            val to = toCity ?: toAddress.split(",").firstOrNull() ?: "End"
            return "$from → $to • $distanceKm km • $durationFormatted"
        }
    
    /**
     * Get short display (e.g., "270 km • 6h 45m")
     */
    val shortDisplay: String
        get() {
            val hours = durationMinutes / 60
            val mins = durationMinutes % 60
            return if (hours > 0) "$distanceKm km • ${hours}h ${mins}m" else "$distanceKm km • ${mins}m"
        }
}

/**
 * Complete route breakdown with all legs and totals
 * 
 * INCLUDED IN:
 * - Broadcast to transporters (see full route before accepting)
 * - Trip details for driver (know each leg)
 * - Tracking for customer (see progress)
 * 
 * EXAMPLE:
 * {
 *   legs: [Delhi→Jaipur: 270km 6.75hrs, Jaipur→Mumbai: 640km 16hrs],
 *   totalDistanceKm: 910,
 *   totalDurationFormatted: "22 hrs 45 mins",
 *   estimatedArrival: "2024-01-24T06:30:00Z"
 * }
 */
data class RouteBreakdown(
    val legs: List<RouteLeg> = emptyList(),
    val totalDistanceKm: Int = 0,
    val totalDurationMinutes: Int = 0,
    val totalDurationFormatted: String = "",
    val totalStops: Int = 0,
    val estimatedArrival: String? = null
) {
    /**
     * Check if route has multiple legs
     */
    val hasMultipleLegs: Boolean
        get() = legs.size > 1
    
    /**
     * Get summary string (e.g., "910 km • 22 hrs 45 mins • 2 stops")
     */
    val summaryString: String
        get() {
            val stopsText = when (totalStops) {
                0 -> "Direct"
                1 -> "1 stop"
                else -> "$totalStops stops"
            }
            return "$totalDistanceKm km • $totalDurationFormatted • $stopsText"
        }
    
    /**
     * Get leg at index (safe)
     */
    fun getLegAt(index: Int): RouteLeg? = legs.getOrNull(index)
    
    /**
     * Get remaining duration from a specific leg
     */
    fun getRemainingDuration(fromLegIndex: Int): Int {
        return legs.drop(fromLegIndex).sumOf { it.durationMinutes }
    }
    
    /**
     * Get remaining distance from a specific leg
     */
    fun getRemainingDistance(fromLegIndex: Int): Int {
        return legs.drop(fromLegIndex).sumOf { it.distanceKm }
    }
}

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
 * ROUTE POINTS (Intermediate Stops):
 * - routePoints array shows full route: PICKUP → STOP → STOP → DROP
 * - Transporter/Driver can see all stops before accepting
 * - totalStops = number of intermediate stops (0, 1, or 2)
 * 
 * BACKEND: Send this via push notification/websocket to all nearby transporters
 */
data class BroadcastTrip(
    val broadcastId: String,                    // Unique broadcast ID
    val customerId: String,                     // Customer who created the broadcast
    val customerName: String,                   // Customer display name
    val customerMobile: String,                 // Customer contact
    
    // =========================================================================
    // ROUTE POINTS (NEW - with intermediate stops)
    // =========================================================================
    val routePoints: List<RoutePoint> = emptyList(),  // Full route: PICKUP → STOP → DROP
    val totalStops: Int = 0,                    // Number of intermediate stops (0, 1, or 2)
    
    // =========================================================================
    // ROUTE BREAKDOWN (NEW - ETA per leg)
    // =========================================================================
    val routeBreakdown: RouteBreakdown = RouteBreakdown(),  // Full breakdown with legs
    
    // Trip Details (legacy, for backward compatibility)
    val pickupLocation: Location,               // Where to pick up goods (first routePoint)
    val dropLocation: Location,                 // Where to deliver (last routePoint)
    val distance: Double,                       // Total distance in km (all legs combined)
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
    val isUrgent: Boolean = false,              // Priority flag
    
    // =========================================================================
    // PERSONALIZED FIELDS - Unique per transporter
    // =========================================================================
    // 
    // These fields are sent in personalized broadcasts from the backend.
    // Each transporter sees their own capacity, not the total order.
    // 
    // EXAMPLE (Order needs 5 "Open 17ft" trucks):
    //   Transporter A (has 3 available) → trucksYouCanProvide = 3
    //   Transporter B (has 1 available) → trucksYouCanProvide = 1
    //   Transporter C (has 10 available) → trucksYouCanProvide = 5 (capped at order need)
    // =========================================================================
    
    /** How many trucks THIS transporter can provide (MIN of available and needed) */
    val trucksYouCanProvide: Int = 0,
    
    /** Maximum trucks this transporter can provide (alias for UI) */
    val maxTrucksYouCanProvide: Int = 0,
    
    /** How many trucks this transporter has available of this type */
    val yourAvailableTrucks: Int = 0,
    
    /** How many trucks this transporter owns of this type (total, including in-transit) */
    val yourTotalTrucks: Int = 0,
    
    /** How many trucks are still needed for the order (same for all transporters) */
    val trucksStillNeeded: Int = 0,
    
    /** Whether this is a personalized broadcast (true) or generic (false) */
    val isPersonalized: Boolean = false,

    /** Lifecycle metadata used for strict dedupe/order/reconcile. */
    val eventId: String? = null,
    val eventVersion: Int? = null,
    val serverTimeMs: Long? = null,
    val reasonCode: String? = null
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
            @Suppress("DEPRECATION")
            vehicleType?.name ?: "Truck"
        }
    
    // =========================================================================
    // ROUTE POINT HELPERS
    // =========================================================================
    
    /**
     * Check if route has intermediate stops
     */
    val hasIntermediateStops: Boolean
        get() = totalStops > 0 || routePoints.any { it.type == RoutePointType.STOP }
    
    /**
     * Get pickup point from routePoints (or fallback to pickupLocation)
     */
    val pickupPoint: RoutePoint?
        get() = routePoints.firstOrNull { it.type == RoutePointType.PICKUP }
    
    /**
     * Get drop point from routePoints (or fallback to dropLocation)
     */
    val dropPoint: RoutePoint?
        get() = routePoints.firstOrNull { it.type == RoutePointType.DROP }
    
    /**
     * Get intermediate stops only
     */
    val intermediateStops: List<RoutePoint>
        get() = routePoints.filter { it.type == RoutePointType.STOP }
    
    /**
     * Get route display string (e.g., "Delhi → Jaipur → Mumbai")
     */
    val routeDisplayString: String
        get() = if (routePoints.isNotEmpty()) {
            routePoints.joinToString(" → ") { it.city ?: it.shortAddress }
        } else {
            "${pickupLocation.city ?: pickupLocation.address} → ${dropLocation.city ?: dropLocation.address}"
        }
    
    /**
     * Get route summary (e.g., "3 stops • 450 km")
     */
    val routeSummary: String
        get() {
            val stopsText = when (totalStops) {
                0 -> "Direct"
                1 -> "1 stop"
                else -> "$totalStops stops"
            }
            return "$stopsText • ${distance.toInt()} km"
        }
    
    // =========================================================================
    // ROUTE BREAKDOWN HELPERS
    // =========================================================================
    
    /**
     * Check if route breakdown is available
     */
    val hasRouteBreakdown: Boolean
        get() = routeBreakdown.legs.isNotEmpty()
    
    /**
     * Get total estimated duration formatted
     */
    val totalDurationFormatted: String
        get() = if (hasRouteBreakdown) {
            routeBreakdown.totalDurationFormatted
        } else {
            val hours = estimatedDuration / 60
            val mins = estimatedDuration % 60
            if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        }
    
    /**
     * Get full route summary with ETA (e.g., "910 km • 22 hrs 45 mins • 2 stops")
     */
    val fullRouteSummary: String
        get() = if (hasRouteBreakdown) {
            routeBreakdown.summaryString
        } else {
            routeSummary
        }
    
    /**
     * Get leg info for a specific segment
     */
    fun getLegInfo(legIndex: Int): RouteLeg? = routeBreakdown.getLegAt(legIndex)
}

/**
 * TripAssignment - When transporter assigns drivers to selected trucks
 * This represents the assignment from transporter to specific driver
 * 
 * ROUTE POINTS:
 * - Driver sees full route with all stops before accepting
 * - currentRouteIndex tracks progress (starts at 0)
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
    
    // Route Points (NEW - with intermediate stops)
    val routePoints: List<RoutePoint> = emptyList(),  // Full route for driver
    val totalStops: Int = 0,                    // Number of intermediate stops
    val currentRouteIndex: Int = 0,             // Driver progress (0 = at pickup)
    
    // Route Breakdown (NEW - ETA per leg)
    val routeBreakdown: RouteBreakdown = RouteBreakdown(),
    
    // Trip Details (legacy, for backward compatibility)
    val pickupLocation: Location,
    val dropLocation: Location,
    val distance: Double,
    val farePerTruck: Double,
    val goodsType: String,
    
    val assignedAt: Long = System.currentTimeMillis(),
    val status: AssignmentStatus = AssignmentStatus.PENDING_DRIVER_RESPONSE
) {
    /**
     * Get current route point based on index
     */
    val currentPoint: RoutePoint?
        get() = routePoints.getOrNull(currentRouteIndex)
    
    /**
     * Get next route point
     */
    val nextPoint: RoutePoint?
        get() = routePoints.getOrNull(currentRouteIndex + 1)
    
    /**
     * Check if trip is completed (reached final drop)
     */
    val isCompleted: Boolean
        get() = currentRouteIndex >= routePoints.size - 1
    
    /**
     * Get route display string
     */
    val routeDisplayString: String
        get() = if (routePoints.isNotEmpty()) {
            routePoints.joinToString(" → ") { it.city ?: it.shortAddress }
        } else {
            "${pickupLocation.city ?: pickupLocation.address} → ${dropLocation.city ?: dropLocation.address}"
        }
    
    /**
     * Get current leg (the segment driver is currently on)
     */
    val currentLeg: RouteLeg?
        get() = if (currentRouteIndex > 0 && routeBreakdown.legs.isNotEmpty()) {
            routeBreakdown.getLegAt(currentRouteIndex - 1)
        } else null
    
    /**
     * Get next leg (the segment driver will be on after current stop)
     */
    val nextLeg: RouteLeg?
        get() = routeBreakdown.getLegAt(currentRouteIndex)
    
    /**
     * Get remaining distance from current position
     */
    val remainingDistanceKm: Int
        get() = routeBreakdown.getRemainingDistance(currentRouteIndex)
    
    /**
     * Get remaining duration from current position (minutes)
     */
    val remainingDurationMinutes: Int
        get() = routeBreakdown.getRemainingDuration(currentRouteIndex)
    
    /**
     * Get ETA to next stop formatted
     */
    val etaToNextStop: String
        get() {
            val leg = nextLeg ?: return "N/A"
            return leg.durationFormatted
        }
}

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
 * ROUTE PROGRESS:
 * - currentRouteIndex shows which stop driver is heading to
 * - routePoints shows full route for drawing on map
 * - Customer sees driver progress through stops
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
    
    // Route Progress (NEW - with intermediate stops)
    val routePoints: List<RoutePoint> = emptyList(),  // Full route
    val currentRouteIndex: Int = 0,             // Which stop driver is at/heading to
    val totalStops: Int = 0,                    // Number of intermediate stops
    
    // Route Breakdown (NEW - ETA per leg)
    val routeBreakdown: RouteBreakdown = RouteBreakdown(),
    
    // Trip Progress
    val tripStatus: TripStatus,
    val startedAt: Long? = null,
    val currentSpeed: Float = 0f,               // km/h
    val heading: Float = 0f,                    // Direction in degrees
    
    // Tracking Metadata
    val lastUpdated: Long = System.currentTimeMillis(),
    val isLocationSharing: Boolean = true
) {
    /**
     * Get current destination point
     */
    val currentDestination: RoutePoint?
        get() = routePoints.getOrNull(currentRouteIndex)
    
    /**
     * Get next destination after current
     */
    val nextDestination: RoutePoint?
        get() = routePoints.getOrNull(currentRouteIndex + 1)
    
    /**
     * Check if trip is completed
     */
    val isCompleted: Boolean
        get() = currentRouteIndex >= routePoints.size - 1 && tripStatus == TripStatus.COMPLETED
    
    /**
     * Get progress as percentage (0-100)
     */
    val progressPercent: Int
        get() = if (routePoints.isEmpty()) 0 else ((currentRouteIndex.toFloat() / (routePoints.size - 1)) * 100).toInt()
}

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
