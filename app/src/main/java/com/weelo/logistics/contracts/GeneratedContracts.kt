package com.weelo.logistics.contracts

import org.json.JSONObject

/**
 * =============================================================================
 * F-C-52 — GENERATED EVENT CONTRACTS (STUB)
 * =============================================================================
 *
 * Phase 10 / t1-captain-contracts-consumer / NO-DEPLOY mode.
 *
 * The real F-C-52 codegen (TypeScript AsyncAPI/Protobuf -> Kotlin) is a Wave-1
 * backend deliverable. In the captain repo we have not yet pulled the generated
 * artefact, so this file provides hand-written stub sealed classes that mirror
 * the backend contract registered at `src/shared/contracts/event-names.ts`:
 *
 *   - ASSIGNMENT_STATUS_CHANGED (F-C-51, F-C-63)
 *   - DISPATCH_ACK              (F-C-53)
 *   - BOOKING_BROADCAST_V2      (F-C-55)
 *   - ORDER_PROGRESS            (F-C-65)
 *
 * Each class exposes:
 *
 *   1. An `EVENT_NAME` constant mirroring the backend string so the router can
 *      register a listener without importing [SocketConstants] twice.
 *   2. A `fromJson(JSONObject)` factory that returns a typed value or `null` on
 *      shape mismatch (NEVER throws — fail-closed for wire drift).
 *   3. Field defaults that match the legacy captain `optString(..., "")` idiom
 *      so partial/legacy payloads from the backend still parse.
 *
 * When F-C-52 codegen lands, this file gets deleted and replaced by the
 * generated package. The [EVENT_NAME] constants and data-class field names
 * must match the generated artefact so the migration is drop-in.
 *
 * ALL consumers of these types are gated behind BuildConfig feature flags
 * (default OFF) so legacy handlers keep handling traffic until backend flips
 * the v2 payload shape.
 * =============================================================================
 */

/**
 * F-C-51 + F-C-63 — Unified assignment status event.
 *
 * Backend: `assignment.service.ts:818/851/886` emits THREE variants of this
 * event — per INDEX F-C-63 analysis. The v2 contract canonicalizes all three
 * into a single 10-field payload with an `orderId`/`bookingId` oneof.
 *
 * Legacy captain handler `SocketEventRouter.handleAssignmentStatusChanged`
 * parses 5 fields. The v2 handler [AssignmentStatusChangedEvent.fromJson] adds
 * `orderId`, `bookingId`, `driverId`, `driverName`, `trucksAccepted`, and
 * `trucksPending`. Missing fields fall back to "" / 0 (backward-compatible).
 */
data class AssignmentStatusChangedEvent(
    val assignmentId: String,
    val tripId: String,
    val status: String,
    val vehicleNumber: String,
    val message: String,
    // V2 additions (F-C-63) — nullable so legacy-shape callers still parse.
    val orderId: String? = null,
    val bookingId: String? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val trucksAccepted: Int? = null,
    val trucksPending: Int? = null,
    val source: String? = null
) {
    companion object {
        const val EVENT_NAME = "assignment_status_changed"

        /** Parse backend payload into typed event, returning null on irrecoverable drift. */
        fun fromJson(data: JSONObject): AssignmentStatusChangedEvent? {
            return try {
                AssignmentStatusChangedEvent(
                    assignmentId = data.optString("assignmentId", ""),
                    tripId = data.optString("tripId", ""),
                    status = data.optString("status", ""),
                    vehicleNumber = data.optString("vehicleNumber", ""),
                    message = data.optString("message", ""),
                    // V2 oneof — both keys present, callers route on non-blank.
                    orderId = data.optString("orderId", "").takeIf { it.isNotBlank() },
                    bookingId = data.optString("bookingId", "").takeIf { it.isNotBlank() },
                    driverId = data.optString("driverId", "").takeIf { it.isNotBlank() },
                    driverName = data.optString("driverName", "").takeIf { it.isNotBlank() },
                    trucksAccepted = if (data.has("trucksAccepted")) data.optInt("trucksAccepted", 0) else null,
                    trucksPending = if (data.has("trucksPending")) data.optInt("trucksPending", 0) else null,
                    source = data.optString("source", "").takeIf { it.isNotBlank() }
                )
            } catch (_: Exception) { null }
        }
    }
}

/**
 * F-C-53 — Dispatch ACK event.
 *
 * Backend gains `FF_DISPATCH_ACK_HANDLER` (t5-backend-consumers) — once backend
 * registers `socket.on('dispatch_ack', ...)`, it can ALSO emit an ACK-RESPONSE
 * back to the captain for observability (e.g., revision-tracking echo).
 *
 * Captain currently FIRES dispatch_ack on broadcast ingest (SocketEventRouter
 * line 821). This contract represents an optional server->client receipt event
 * that acknowledges the ack was recorded (think TCP-like ACK-of-ACK).
 */
data class DispatchAckEvent(
    val orderId: String,
    val dispatchRevision: Long?,
    val acknowledgedAtMs: Long?,
    val source: String
) {
    companion object {
        const val EVENT_NAME = "dispatch_ack"
        const val EVENT_NAME_RESPONSE = "dispatch_ack_response"

        fun fromJson(data: JSONObject): DispatchAckEvent? {
            return try {
                val orderId = data.optString("orderId", "")
                if (orderId.isBlank()) return null
                DispatchAckEvent(
                    orderId = orderId,
                    dispatchRevision = if (data.has("dispatchRevision")) data.optLong("dispatchRevision", -1L).takeIf { it >= 0L } else null,
                    acknowledgedAtMs = if (data.has("acknowledgedAtMs")) data.optLong("acknowledgedAtMs", -1L).takeIf { it >= 0L } else null,
                    source = data.optString("source", "socket")
                )
            } catch (_: Exception) { null }
        }
    }
}

/**
 * F-C-55 — Legacy booking broadcast v2 payload.
 *
 * Backend (`booking-payload.helper.ts`) emits a LEGACY shape with zero
 * personalized fields. The v2 contract adds 6 transporter-context fields:
 *
 *   - trucksYouCanProvide
 *   - maxTrucksYouCanProvide
 *   - yourAvailableTrucks
 *   - yourTotalTrucks
 *   - trucksStillNeeded
 *   - isPersonalized
 *
 * Legacy captain `BroadcastPayloadParser` defaults to `optInt(..., 0)` on these
 * keys -> "0 of 0" misleading UI. The v2 consumer reads all 6 fields and sets
 * `isPersonalized=true` only when the v2 builder ran on the backend.
 */
data class BookingBroadcastV2Event(
    val broadcastId: String,
    val trucksYouCanProvide: Int,
    val maxTrucksYouCanProvide: Int,
    val yourAvailableTrucks: Int,
    val yourTotalTrucks: Int,
    val trucksStillNeeded: Int,
    val isPersonalized: Boolean
) {
    companion object {
        const val EVENT_NAME = "booking_broadcast_v2"

        fun fromJson(data: JSONObject): BookingBroadcastV2Event? {
            return try {
                BookingBroadcastV2Event(
                    broadcastId = data.optString("broadcastId", ""),
                    trucksYouCanProvide = data.optInt("trucksYouCanProvide", 0),
                    maxTrucksYouCanProvide = data.optInt("maxTrucksYouCanProvide", 0),
                    yourAvailableTrucks = data.optInt("yourAvailableTrucks", 0),
                    yourTotalTrucks = data.optInt("yourTotalTrucks", 0),
                    trucksStillNeeded = data.optInt("trucksStillNeeded", 0),
                    // true ONLY when backend explicitly sets isPersonalized=true. Default false
                    // keeps the legacy "0 of 0" fallback visible so the UI doesn't overclaim.
                    isPersonalized = data.optBoolean("isPersonalized", false)
                )
            } catch (_: Exception) { null }
        }
    }
}

/**
 * F-C-65 — Order progress event.
 *
 * Backend registers `order_progress` emission in the order lifecycle service.
 * The captain transporter UI consumes this to render per-order truck progress
 * (N of M trucks accepted, N decline, etc). Distinct from `order_status_update`
 * which only carries coarse status transitions.
 */
data class OrderProgressEvent(
    val orderId: String,
    val status: String,
    val trucksNeeded: Int,
    val trucksAccepted: Int,
    val trucksPending: Int,
    val trucksDeclined: Int,
    val updatedAtMs: Long?
) {
    companion object {
        const val EVENT_NAME = "order_progress"

        fun fromJson(data: JSONObject): OrderProgressEvent? {
            return try {
                val orderId = data.optString("orderId", "")
                if (orderId.isBlank()) return null
                OrderProgressEvent(
                    orderId = orderId,
                    status = data.optString("status", ""),
                    trucksNeeded = data.optInt("trucksNeeded", 0),
                    trucksAccepted = data.optInt("trucksAccepted", 0),
                    trucksPending = data.optInt("trucksPending", 0),
                    trucksDeclined = data.optInt("trucksDeclined", 0),
                    updatedAtMs = if (data.has("updatedAtMs")) data.optLong("updatedAtMs", -1L).takeIf { it >= 0L } else null
                )
            } catch (_: Exception) { null }
        }
    }
}
