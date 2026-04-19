package com.weelo.logistics.data.remote.socket

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// =============================================================================
// SOCKET CONNECTION STATE
// =============================================================================

sealed class SocketConnectionState {
    object Disconnected : SocketConnectionState()
    object Connecting : SocketConnectionState()
    object Connected : SocketConnectionState()
    data class Error(val message: String) : SocketConnectionState()
}

// =============================================================================
// INCOMING BROADCAST ENVELOPE
// =============================================================================

data class IncomingBroadcastEnvelope(
    val rawEventName: String,
    val normalizedId: String,
    val receivedAtMs: Long,
    val payloadVersion: String?,
    val parseWarnings: List<String>,
    val broadcast: BroadcastNotification?
)

// =============================================================================
// BROADCAST EXPIRY PARSING
// =============================================================================

private val ISO_EXPIRY_WITH_MILLIS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
private val ISO_EXPIRY_NO_MILLIS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

internal fun parseBroadcastExpiryEpochMs(rawExpiry: String?): Long {
    val now = System.currentTimeMillis()
    val fallback = now + 60_000L
    val value = rawExpiry?.trim().orEmpty()
    if (value.isEmpty()) return fallback

    value.toLongOrNull()?.let { numeric ->
        return when {
            numeric > 1_000_000_000_000L -> numeric
            numeric > 1_000_000_000L -> numeric * 1_000L
            else -> fallback
        }.coerceAtLeast(now + 1_000L)
    }

    val instant = try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(value, ISO_EXPIRY_WITH_MILLIS).atOffset(ZoneOffset.UTC).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(value, ISO_EXPIRY_NO_MILLIS).atOffset(ZoneOffset.UTC).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    return (instant?.toEpochMilli() ?: fallback).coerceAtLeast(now + 1_000L)
}

// =============================================================================
// BROADCAST NOTIFICATION
// =============================================================================

data class BroadcastNotification(
    val broadcastId: String,
    val customerId: String,
    val customerName: String,
    val vehicleType: String,
    val vehicleSubtype: String,
    val trucksNeeded: Int,
    val trucksFilled: Int,
    val farePerTruck: Int,
    val pickupAddress: String,
    val pickupCity: String,
    val pickupLatitude: Double = 0.0,
    val pickupLongitude: Double = 0.0,
    val dropAddress: String,
    val dropCity: String,
    val dropLatitude: Double = 0.0,
    val dropLongitude: Double = 0.0,
    val distanceKm: Int,
    val goodsType: String,
    val isUrgent: Boolean,
    val expiresAt: String,
    val requestedVehicles: List<RequestedVehicleNotification> = emptyList(),
    val trucksYouCanProvide: Int = 0,
    val maxTrucksYouCanProvide: Int = 0,
    val yourAvailableTrucks: Int = 0,
    val yourTotalTrucks: Int = 0,
    val trucksStillNeeded: Int = 0,
    val isPersonalized: Boolean = false,
    val pickupDistanceKm: Double = 0.0,
    val pickupEtaMinutes: Int = 0,
    val eventId: String? = null,
    val dispatchRevision: Long? = null,
    val orderLifecycleVersion: Long? = null,
    val eventVersion: Int? = null,
    val serverTimeMs: Long? = null
) {
    /**
     * Convert BroadcastNotification to BroadcastTrip for UI display.
     * Used by BroadcastOverlayManager to show full-screen overlay.
     */
    fun toBroadcastTrip(): com.weelo.logistics.data.model.BroadcastTrip {
        val modelRequestedVehicles = if (requestedVehicles.isNotEmpty()) {
            requestedVehicles.map { rv ->
                com.weelo.logistics.data.model.RequestedVehicle(
                    vehicleType = rv.vehicleType,
                    vehicleSubtype = rv.vehicleSubtype,
                    count = rv.count,
                    filledCount = rv.filledCount,
                    farePerTruck = rv.farePerTruck,
                    capacityTons = rv.capacityTons
                )
            }
        } else {
            listOf(
                com.weelo.logistics.data.model.RequestedVehicle(
                    vehicleType = vehicleType,
                    vehicleSubtype = vehicleSubtype,
                    count = trucksNeeded,
                    filledCount = trucksFilled,
                    farePerTruck = farePerTruck.toDouble(),
                    capacityTons = 0.0
                )
            )
        }

        return com.weelo.logistics.data.model.BroadcastTrip(
            broadcastId = broadcastId,
            customerId = customerId,
            customerName = customerName,
            customerMobile = "",
            pickupLocation = com.weelo.logistics.data.model.Location(
                address = pickupAddress.ifEmpty { pickupCity },
                city = pickupCity,
                latitude = pickupLatitude,
                longitude = pickupLongitude
            ),
            dropLocation = com.weelo.logistics.data.model.Location(
                address = dropAddress.ifEmpty { dropCity },
                city = dropCity,
                latitude = dropLatitude,
                longitude = dropLongitude
            ),
            distance = distanceKm.toDouble(),
            estimatedDuration = (distanceKm * 2).toLong(),
            totalTrucksNeeded = trucksNeeded,
            trucksFilledSoFar = trucksFilled,
            requestedVehicles = modelRequestedVehicles,
            vehicleType = com.weelo.logistics.data.model.TruckCategory(
                id = vehicleType.lowercase(),
                name = vehicleType.replaceFirstChar { it.uppercase() },
                icon = "\uD83D\uDE9B",
                description = vehicleSubtype.ifEmpty { "Standard" }
            ),
            goodsType = goodsType,
            farePerTruck = farePerTruck.toDouble(),
            totalFare = if (requestedVehicles.isNotEmpty()) {
                requestedVehicles.sumOf { it.farePerTruck * it.count }
            } else {
                (farePerTruck * trucksNeeded).toDouble()
            },
            status = com.weelo.logistics.data.model.BroadcastStatus.ACTIVE,
            broadcastTime = serverTimeMs?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            expiryTime = parseBroadcastExpiryEpochMs(expiresAt),
            isUrgent = isUrgent,
            trucksYouCanProvide = if (trucksYouCanProvide > 0) trucksYouCanProvide
                else maxTrucksYouCanProvide.takeIf { it > 0 } ?: trucksNeeded,
            maxTrucksYouCanProvide = maxTrucksYouCanProvide.takeIf { it > 0 }
                ?: trucksYouCanProvide.takeIf { it > 0 } ?: trucksNeeded,
            yourAvailableTrucks = yourAvailableTrucks,
            yourTotalTrucks = yourTotalTrucks,
            trucksStillNeeded = trucksStillNeeded.takeIf { it > 0 } ?: (trucksNeeded - trucksFilled),
            isPersonalized = isPersonalized,
            pickupDistanceKm = pickupDistanceKm,
            pickupEtaMinutes = pickupEtaMinutes,
            eventId = eventId,
            dispatchRevision = dispatchRevision,
            orderLifecycleVersion = orderLifecycleVersion,
            eventVersion = eventVersion,
            serverTimeMs = serverTimeMs
        )
    }
}

// =============================================================================
// SIMPLE NOTIFICATION DATA CLASSES
// =============================================================================

data class RequestedVehicleNotification(
    val vehicleType: String,
    val vehicleSubtype: String,
    val count: Int,
    val filledCount: Int,
    val farePerTruck: Double,
    val capacityTons: Double = 0.0
)

data class TruckAssignedNotification(
    val bookingId: String,
    val assignmentId: String,
    val vehicleNumber: String,
    val driverName: String,
    val status: String
)

data class AssignmentStatusNotification(
    val assignmentId: String,
    val tripId: String,
    val status: String,
    val vehicleNumber: String,
    val message: String = ""
)

data class BookingUpdatedNotification(
    val bookingId: String,
    val status: String,
    val trucksFilled: Int,
    val trucksNeeded: Int
)

data class TrucksRemainingNotification(
    val orderId: String,
    val vehicleType: String,
    val totalTrucks: Int,
    val trucksFilled: Int,
    val trucksRemaining: Int,
    val orderStatus: String,
    val eventId: String? = null,
    val eventVersion: Int? = null,
    val serverTimeMs: Long? = null
)

data class SocketError(val message: String)

// =============================================================================
// FLEET / VEHICLE NOTIFICATION DATA CLASSES
// =============================================================================

data class VehicleRegisteredNotification(
    val vehicleId: String,
    val vehicleNumber: String,
    val vehicleType: String,
    val vehicleSubtype: String,
    val message: String,
    val totalVehicles: Int,
    val availableCount: Int
)

data class FleetUpdatedNotification(
    val action: String,
    val vehicleId: String,
    val totalVehicles: Int,
    val availableCount: Int,
    val inTransitCount: Int,
    val maintenanceCount: Int
)

// =============================================================================
// DRIVER NOTIFICATION DATA CLASSES
// =============================================================================

data class DriverAddedNotification(
    val driverId: String,
    val driverName: String,
    val driverPhone: String,
    val licenseNumber: String,
    val message: String,
    val totalDrivers: Int,
    val availableCount: Int,
    val onTripCount: Int
)

data class DriverStatusChangedNotification(
    val driverId: String,
    val driverName: String,
    val isOnline: Boolean,
    val action: String,
    val timestamp: String
)

data class DriversUpdatedNotification(
    val action: String,
    val driverId: String,
    val totalDrivers: Int,
    val availableCount: Int,
    val onTripCount: Int
)

// =============================================================================
// TRIP ASSIGNMENT NOTIFICATION DATA CLASSES
// =============================================================================

data class DriverTimeoutNotification(
    val assignmentId: String,
    val tripId: String,
    val driverId: String,
    val driverName: String,
    val vehicleNumber: String,
    val reason: String,
    val message: String
)

data class TripCancelledNotification(
    val orderId: String,
    val tripId: String,
    val reason: String,
    val message: String,
    val cancelledAt: String,
    val customerName: String = "",
    val customerPhone: String = "",
    val pickupAddress: String = "",
    val dropAddress: String = "",
    val compensationAmount: Double = 0.0,
    val settlementState: String = "none"
)

// =============================================================================
// ORDER CANCELLATION NOTIFICATION
// =============================================================================

data class OrderCancelledNotification(
    val orderId: String,
    val tripId: String = "",
    val reason: String,
    val message: String,
    val cancelledAt: String,
    val assignmentsCancelled: Int = 0,
    val eventId: String? = null,
    val dispatchRevision: Long? = null,
    val orderLifecycleVersion: Long? = null,
    val eventVersion: Int? = null,
    val serverTimeMs: Long? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val pickupAddress: String = "",
    val dropAddress: String = ""
)

// =============================================================================
// BROADCAST DISMISSED NOTIFICATION
// =============================================================================

data class BroadcastDismissedNotification(
    val broadcastId: String,
    val reason: String,
    val message: String,
    val customerName: String = "",
    val eventId: String? = null,
    val dispatchRevision: Long? = null,
    val orderLifecycleVersion: Long? = null,
    val eventVersion: Int? = null,
    val serverTimeMs: Long? = null
)

// =============================================================================
// DRIVER SOS ALERT NOTIFICATION (safety-critical)
// =============================================================================

data class DriverSOSAlertNotification(
    @SerializedName("driverId") val driverId: String,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double,
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: String
)

// =============================================================================
// HOLD LIFECYCLE NOTIFICATION DATA CLASSES
// =============================================================================

// F-C-50: `flex_hold_started` kick-off event — emitted by backend after
// `truckHoldLedger.create(...)` completes in `FlexHoldService.createFlexHold`.
// PRD-7777 defines this as the canonical Phase-1 hold start signal over socket
// (previously REST-only — a lost HTTP response would leave the captain UI stuck).
data class FlexHoldStartedNotification(
    @SerializedName("holdId") val holdId: String,
    @SerializedName("orderId") val orderId: String,
    @SerializedName("phase") val phase: String,
    @SerializedName("expiresAt") val expiresAt: String,
    @SerializedName("baseDurationSeconds") val baseDurationSeconds: Int,
    @SerializedName("canExtend") val canExtend: Boolean,
    @SerializedName("maxExtensions") val maxExtensions: Int
)

data class FlexHoldExtendedNotification(
    @SerializedName("holdId") val holdId: String,
    @SerializedName("orderId") val orderId: String,
    @SerializedName("newExpiresAt") val newExpiresAt: String,
    @SerializedName("addedSeconds") val addedSeconds: Int,
    @SerializedName("totalDurationSeconds") val totalDurationSeconds: Int
)

data class CascadeReassignedNotification(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("assignmentId") val assignmentId: String,
    @SerializedName("previousDriverId") val previousDriverId: String,
    @SerializedName("newDriverId") val newDriverId: String,
    @SerializedName("newDriverName") val newDriverName: String,
    @SerializedName("vehicleNumber") val vehicleNumber: String,
    @SerializedName("message") val message: String
)

data class DriverDeclinedNotification(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("assignmentId") val assignmentId: String,
    @SerializedName("driverId") val driverId: String,
    @SerializedName("driverName") val driverName: String,
    @SerializedName("vehicleNumber") val vehicleNumber: String,
    @SerializedName("reason") val reason: String
)

// =============================================================================
// DRIVER ACCEPTED NOTIFICATION DATA CLASS (hold lifecycle)
// =============================================================================

data class DriverAcceptedNotification(
    val holdId: String = "",
    val assignmentId: String,
    val driverId: String = "",
    val driverName: String = "",
    val vehicleNumber: String = "",
    val tripId: String = "",
    val bookingId: String = "",
    val trucksAccepted: Int = 0,
    val trucksPending: Int = 0,
    val message: String = ""
)

// =============================================================================
// HOLD EXPIRED NOTIFICATION DATA CLASS
// =============================================================================

data class HoldExpiredNotification(
    val holdId: String = "",
    val orderId: String,
    val transporterId: String = "",
    val phase: String = "",
    val status: String = "expired",
    val reason: String = "",
    val vehicleType: String = "",
    val vehicleSubtype: String = "",
    val quantity: Int = 0,
    val expiredAt: String = "",
    val message: String = ""
)

// =============================================================================
// BOOKING PARTIALLY FILLED NOTIFICATION DATA CLASS
// =============================================================================

data class BookingPartiallyFilledNotification(
    val bookingId: String,
    val filled: Int,
    val total: Int
)

// =============================================================================
// REQUEST NO LONGER AVAILABLE NOTIFICATION DATA CLASS
// =============================================================================

data class RequestNoLongerAvailableNotification(
    val orderId: String
)

// =============================================================================
// ORDER STATUS UPDATE NOTIFICATION DATA CLASS
// =============================================================================

data class OrderStatusUpdateNotification(
    val orderId: String,
    val status: String,
    val totalAssignments: Int = 0,
    val message: String = ""
)

// =============================================================================
// DRIVER MAY BE OFFLINE NOTIFICATION DATA CLASS
// =============================================================================

data class DriverMayBeOfflineNotification(
    val driverId: String,
    val driverName: String = "",
    val vehicleNumber: String = "",
    val tripId: String = "",
    val assignmentId: String = "",
    val lastSeenSeconds: Int = 0,
    val lastLatitude: Double = 0.0,
    val lastLongitude: Double = 0.0,
    val durationHours: Int = 0,
    val lastLocationAgeHours: Int = 0,
    val status: String = "",
    val message: String = ""
)

// =============================================================================
// ASSIGNMENT STALE NOTIFICATION DATA CLASS
// =============================================================================

data class AssignmentStaleNotification(
    val assignmentId: String,
    val driverId: String = "",
    val tripId: String = "",
    val message: String = ""
)

// =============================================================================
// DRIVER RATING NOTIFICATION DATA CLASS
// =============================================================================

data class DriverRatingUpdatedNotification(
    @SerializedName("driverId") val driverId: String,
    @SerializedName("newRating") val newRating: Double,
    @SerializedName("totalRatings") val totalRatings: Int,
    @SerializedName("ratingDelta") val ratingDelta: Double
)
