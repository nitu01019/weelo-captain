package com.weelo.logistics.data.remote.socket

/**
 * =============================================================================
 * SOCKET EVENT CONSTANTS - Must match backend socket.service.ts
 * =============================================================================
 *
 * Canonical source of all Socket.IO event name strings.
 * Both SocketConnectionManager and SocketEventRouter reference these constants.
 *
 * SocketIOService.Events is a typealias to this object so that every caller
 * that currently writes `SocketIOService.Events.NEW_BROADCAST` keeps compiling.
 * =============================================================================
 */
object SocketConstants {
    // Server -> Client (broadcast lifecycle)
    const val CONNECTED = "connected"
    const val NEW_BROADCAST = "new_broadcast"
    const val NEW_TRUCK_REQUEST = "new_truck_request"
    const val BOOKING_UPDATED = "booking_updated"
    const val TRUCK_ASSIGNED = "truck_assigned"
    const val ASSIGNMENT_STATUS_CHANGED = "assignment_status_changed"
    const val TRUCKS_REMAINING_UPDATE = "trucks_remaining_update"
    const val BOOKING_EXPIRED = "booking_expired"
    const val BOOKING_CANCELLED = "booking_cancelled"
    const val BOOKING_FULLY_FILLED = "booking_fully_filled"
    const val BROADCAST_DISMISSED = "broadcast_dismissed"
    const val BROADCAST_EXPIRED = "broadcast_expired"
    const val NEW_ORDER_ALERT = "new_order_alert"
    const val ACCEPT_CONFIRMATION = "accept_confirmation"
    const val ERROR = "error"

    // Fleet/Vehicle events
    const val VEHICLE_REGISTERED = "vehicle_registered"
    const val VEHICLE_UPDATED = "vehicle_updated"
    const val VEHICLE_DELETED = "vehicle_deleted"
    const val VEHICLE_STATUS_CHANGED = "vehicle_status_changed"
    const val FLEET_UPDATED = "fleet_updated"

    // Driver events
    const val DRIVER_ADDED = "driver_added"
    const val DRIVER_UPDATED = "driver_updated"
    const val DRIVER_DELETED = "driver_deleted"
    const val DRIVER_STATUS_CHANGED = "driver_status_changed"
    const val DRIVERS_UPDATED = "drivers_updated"

    // Trip assignment events
    const val TRIP_ASSIGNED = "trip_assigned"
    const val DRIVER_TIMEOUT = "driver_timeout"
    const val TRIP_CANCELLED = "trip_cancelled"

    // Order lifecycle events
    const val ORDER_CANCELLED = "order_cancelled"
    const val ORDER_EXPIRED = "order_expired"

    // Driver presence events
    const val HEARTBEAT = "heartbeat"
    const val DRIVER_ONLINE = "driver_online"
    const val DRIVER_OFFLINE = "driver_offline"

    // Safety-critical events
    const val DRIVER_SOS_ALERT = "driver_sos_alert"

    // Hold lifecycle events
    const val FLEX_HOLD_STARTED = "flex_hold_started"
    const val FLEX_HOLD_EXTENDED = "flex_hold_extended"
    const val CASCADE_REASSIGNED = "cascade_reassigned"
    const val DRIVER_DECLINED = "driver_declined"
    const val DRIVER_ACCEPTED = "driver_accepted"
    const val HOLD_EXPIRED = "hold_expired"
    const val FLEX_HOLD_EXPIRED = "flex_hold_expired"
    const val CONFIRMED_HOLD_EXPIRED = "confirmed_hold_expired"

    // Booking progress events
    const val BOOKING_PARTIALLY_FILLED = "booking_partially_filled"

    // Request availability events
    const val REQUEST_NO_LONGER_AVAILABLE = "request_no_longer_available"

    // Order status events
    const val ORDER_STATUS_UPDATE = "order_status_update"

    // Driver health events
    const val DRIVER_MAY_BE_OFFLINE = "driver_may_be_offline"

    // Assignment health events
    const val ASSIGNMENT_STALE = "assignment_stale"

    // Driver rating events
    const val DRIVER_RATING_UPDATED = "driver_rating_updated"

    // Client -> Server
    const val JOIN_BOOKING = "join_booking"
    const val LEAVE_BOOKING = "leave_booking"
    const val JOIN_ORDER = "join_order"
    const val LEAVE_ORDER = "leave_order"
    const val JOIN_TRANSPORTER = "join_transporter"
    const val UPDATE_LOCATION = "update_location"
}
