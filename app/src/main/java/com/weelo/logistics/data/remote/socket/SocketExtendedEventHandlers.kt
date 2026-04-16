package com.weelo.logistics.data.remote.socket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * =============================================================================
 * EXTENDED EVENT HANDLERS — Hold lifecycle, health, and progress events
 * =============================================================================
 *
 * Extracted from [SocketEventRouter] to keep files under 800 lines.
 * Each handler follows the same pattern: parse JSONObject -> data class -> emit.
 * =============================================================================
 */
internal class SocketExtendedEventHandlers(
    private val serviceScope: CoroutineScope,
    private val resolveEventId: (JSONObject, Array<out String>) -> String,
    private val emitHot: (MutableSharedFlow<BroadcastDismissedNotification>, BroadcastDismissedNotification) -> Unit,
    private val _flexHoldStarted: MutableSharedFlow<FlexHoldStartedNotification>,
    private val _flexHoldExtended: MutableSharedFlow<FlexHoldExtendedNotification>,
    private val _cascadeReassigned: MutableSharedFlow<CascadeReassignedNotification>,
    private val _driverDeclined: MutableSharedFlow<DriverDeclinedNotification>,
    private val _driverRatingUpdated: MutableSharedFlow<DriverRatingUpdatedNotification>,
    private val _driverAccepted: MutableSharedFlow<DriverAcceptedNotification>,
    private val _holdExpired: MutableSharedFlow<HoldExpiredNotification>,
    private val _bookingPartiallyFilled: MutableSharedFlow<BookingPartiallyFilledNotification>,
    private val _requestNoLongerAvailable: MutableSharedFlow<RequestNoLongerAvailableNotification>,
    private val _orderStatusUpdate: MutableSharedFlow<OrderStatusUpdateNotification>,
    private val _driverMayBeOffline: MutableSharedFlow<DriverMayBeOfflineNotification>,
    private val _assignmentStale: MutableSharedFlow<AssignmentStaleNotification>,
    private val _broadcastDismissed: MutableSharedFlow<BroadcastDismissedNotification>
) {

    // --- Hold lifecycle handlers ---

    // F-C-50: `flex_hold_started` — Phase-1 hold kick-off signal from backend.
    // Parses {holdId, orderId, phase, expiresAt, baseDurationSeconds, canExtend, maxExtensions}
    // into [FlexHoldStartedNotification] and emits on [SocketIOService.flexHoldStarted].
    fun handleFlexHoldStarted(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("Flex hold started: holdId=${data.optString("holdId", "")} orderId=${data.optString("orderId", "")}")
            val notification = FlexHoldStartedNotification(
                holdId = data.optString("holdId", ""),
                orderId = data.optString("orderId", ""),
                phase = data.optString("phase", "FLEX"),
                expiresAt = data.optString("expiresAt", ""),
                baseDurationSeconds = data.optInt("baseDurationSeconds", 0),
                canExtend = data.optBoolean("canExtend", true),
                maxExtensions = data.optInt("maxExtensions", 0)
            )
            serviceScope.launch { _flexHoldStarted.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing flex_hold_started: ${e.message}") }
    }

    fun handleFlexHoldExtended(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("Flex hold extended: holdId=${data.optString("holdId", "")}")
            val notification = FlexHoldExtendedNotification(
                holdId = data.optString("holdId", ""),
                orderId = data.optString("orderId", ""),
                newExpiresAt = data.optString("newExpiresAt", ""),
                addedSeconds = data.optInt("addedSeconds", 0),
                totalDurationSeconds = data.optInt("totalDurationSeconds", 0)
            )
            serviceScope.launch { _flexHoldExtended.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing flex_hold_extended: ${e.message}") }
    }

    fun handleCascadeReassigned(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("Cascade reassigned: orderId=${data.optString("orderId", "")} newDriver=${data.optString("newDriverName", "")}")
            val notification = CascadeReassignedNotification(
                orderId = data.optString("orderId", ""),
                assignmentId = data.optString("assignmentId", ""),
                previousDriverId = data.optString("previousDriverId", ""),
                newDriverId = data.optString("newDriverId", ""),
                newDriverName = data.optString("newDriverName", ""),
                vehicleNumber = data.optString("vehicleNumber", ""),
                message = data.optString("message", "")
            )
            serviceScope.launch { _cascadeReassigned.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing cascade_reassigned: ${e.message}") }
    }

    fun handleDriverDeclined(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.w("Driver declined: driverName=${data.optString("driverName", "")} orderId=${data.optString("orderId", "")}")
            val notification = DriverDeclinedNotification(
                orderId = data.optString("orderId", ""),
                assignmentId = data.optString("assignmentId", ""),
                driverId = data.optString("driverId", ""),
                driverName = data.optString("driverName", ""),
                vehicleNumber = data.optString("vehicleNumber", ""),
                reason = data.optString("reason", "")
            )
            serviceScope.launch { _driverDeclined.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing driver_declined: ${e.message}") }
    }

    fun handleDriverAccepted(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("Driver accepted: assignmentId=${data.optString("assignmentId", "")}")
            val notification = DriverAcceptedNotification(
                holdId = data.optString("holdId", ""),
                assignmentId = data.optString("assignmentId", ""),
                driverId = data.optString("driverId", ""),
                driverName = data.optString("driverName", ""),
                vehicleNumber = data.optString("vehicleNumber", ""),
                tripId = data.optString("tripId", ""),
                bookingId = data.optString("bookingId", ""),
                trucksAccepted = data.optInt("trucksAccepted", 0),
                trucksPending = data.optInt("trucksPending", 0),
                message = data.optString("message", "")
            )
            serviceScope.launch { _driverAccepted.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing driver_accepted: ${e.message}") }
    }

    fun handleHoldExpired(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.w("Hold expired: holdId=${data.optString("holdId", "")} orderId=${data.optString("orderId", "")}")
            val notification = HoldExpiredNotification(
                holdId = data.optString("holdId", ""),
                orderId = resolveEventId(data, arrayOf("orderId", "broadcastId", "bookingId", "id")),
                transporterId = data.optString("transporterId", ""),
                phase = data.optString("phase", ""),
                status = data.optString("status", "expired"),
                reason = data.optString("reason", ""),
                vehicleType = data.optString("vehicleType", ""),
                vehicleSubtype = data.optString("vehicleSubtype", ""),
                quantity = data.optInt("quantity", 0),
                expiredAt = data.optString("expiredAt", ""),
                message = data.optString("message", "")
            )
            serviceScope.launch { _holdExpired.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing hold_expired: ${e.message}") }
    }

    // --- Booking progress handlers ---

    fun handleBookingPartiallyFilled(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("Booking partially filled: bookingId=${data.optString("bookingId", "")} filled=${data.optInt("filled", 0)}")
            val notification = BookingPartiallyFilledNotification(
                bookingId = data.optString("bookingId", ""),
                filled = data.optInt("filled", 0),
                total = data.optInt("total", 0)
            )
            serviceScope.launch { _bookingPartiallyFilled.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing booking_partially_filled: ${e.message}") }
    }

    fun handleRequestNoLongerAvailable(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val orderId = resolveEventId(data, arrayOf("orderId", "broadcastId", "bookingId", "id"))
            timber.log.Timber.w("Request no longer available: orderId=$orderId")
            val notification = RequestNoLongerAvailableNotification(orderId = orderId)
            serviceScope.launch { _requestNoLongerAvailable.emit(notification) }
            // Also dismiss the broadcast overlay since this truck was taken
            if (orderId.isNotEmpty()) {
                emitHot(_broadcastDismissed, BroadcastDismissedNotification(
                    broadcastId = orderId, reason = "request_taken",
                    message = "This request is no longer available"
                ))
            }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing request_no_longer_available: ${e.message}") }
    }

    // --- Order status handlers ---

    fun handleOrderStatusUpdate(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("Order status update: orderId=${data.optString("orderId", "")} status=${data.optString("status", "")}")
            val notification = OrderStatusUpdateNotification(
                orderId = resolveEventId(data, arrayOf("orderId", "broadcastId", "bookingId", "id")),
                status = data.optString("status", ""),
                totalAssignments = data.optInt("totalAssignments", 0),
                message = data.optString("message", "")
            )
            serviceScope.launch { _orderStatusUpdate.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing order_status_update: ${e.message}") }
    }

    // --- Driver/assignment health handlers ---

    fun handleDriverMayBeOffline(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.w("Driver may be offline: driverId=${data.optString("driverId", "")} vehicleNumber=${data.optString("vehicleNumber", "")}")
            val notification = DriverMayBeOfflineNotification(
                driverId = data.optString("driverId", ""),
                driverName = data.optString("driverName", ""),
                vehicleNumber = data.optString("vehicleNumber", ""),
                tripId = data.optString("tripId", ""),
                assignmentId = data.optString("assignmentId", ""),
                lastSeenSeconds = data.optInt("lastSeenSeconds", 0),
                lastLatitude = data.optDouble("lastLatitude", 0.0),
                lastLongitude = data.optDouble("lastLongitude", 0.0),
                durationHours = data.optInt("durationHours", 0),
                lastLocationAgeHours = data.optInt("lastLocationAgeHours", 0),
                status = data.optString("status", ""),
                message = data.optString("message", "")
            )
            serviceScope.launch { _driverMayBeOffline.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing driver_may_be_offline: ${e.message}") }
    }

    fun handleAssignmentStale(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.w("Assignment stale: assignmentId=${data.optString("assignmentId", "")} driverId=${data.optString("driverId", "")}")
            val notification = AssignmentStaleNotification(
                assignmentId = data.optString("assignmentId", ""),
                driverId = data.optString("driverId", ""),
                tripId = data.optString("tripId", ""),
                message = data.optString("message", "")
            )
            serviceScope.launch { _assignmentStale.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing assignment_stale: ${e.message}") }
    }

    // --- Driver rating handler ---

    fun handleDriverRatingUpdated(args: Array<Any>) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            timber.log.Timber.i("Driver rating updated: driverId=${data.optString("driverId", "")} newRating=${data.optDouble("newRating", 0.0)}")
            val notification = DriverRatingUpdatedNotification(
                driverId = data.optString("driverId", ""),
                newRating = data.optDouble("newRating", 0.0),
                totalRatings = data.optInt("totalRatings", 0),
                ratingDelta = data.optDouble("ratingDelta", 0.0)
            )
            serviceScope.launch { _driverRatingUpdated.emit(notification) }
        } catch (e: Exception) { timber.log.Timber.e(e, "Error parsing driver_rating_updated: ${e.message}") }
    }
}
