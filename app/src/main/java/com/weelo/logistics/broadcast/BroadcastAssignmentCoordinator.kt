package com.weelo.logistics.broadcast

import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult

sealed interface BroadcastAssignmentResult {
    data class Success(
        val assignmentIds: List<String>,
        val tripIds: List<String>
    ) : BroadcastAssignmentResult

    data class Error(
        val message: String,
        val code: Int? = null,
        val apiCode: String? = null
    ) : BroadcastAssignmentResult
}

/**
 * Single owner for transporter assignment submission.
 * Uses confirmHoldWithAssignments only.
 */
class BroadcastAssignmentCoordinator(
    private val repository: BroadcastRepository
) {

    suspend fun confirmHoldAssignments(
        broadcastId: String,
        holdId: String,
        assignments: List<Pair<String, String>>
    ): BroadcastAssignmentResult {
        val normalizedHoldId = holdId.trim()
        if (normalizedHoldId.isEmpty()) {
            return BroadcastAssignmentResult.Error(
                message = "Missing hold context. Retry from broadcast request.",
                apiCode = "MISSING_HOLD_ID"
            )
        }
        if (assignments.isEmpty()) {
            return BroadcastAssignmentResult.Error(
                message = "Assign at least one driver before confirming.",
                apiCode = "EMPTY_ASSIGNMENTS"
            )
        }

        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_CONFIRM_ASSIGN_REQUESTED,
            status = BroadcastStatus.SUCCESS,
            attrs = mapOf(
                "broadcastId" to broadcastId,
                "holdId" to normalizedHoldId,
                "assignmentCount" to assignments.size.toString()
            )
        )

        return when (val result = repository.confirmHoldWithAssignments(normalizedHoldId, assignments)) {
            is BroadcastResult.Success -> {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_CONFIRM_SUCCESS,
                    status = BroadcastStatus.SUCCESS,
                    attrs = mapOf(
                        "broadcastId" to broadcastId,
                        "holdId" to normalizedHoldId,
                        "assignmentCount" to assignments.size.toString()
                    )
                )
                BroadcastAssignmentResult.Success(
                    assignmentIds = result.data.assignmentIds,
                    tripIds = result.data.tripIds
                )
            }

            is BroadcastResult.Error -> {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_CONFIRM_FAIL,
                    status = BroadcastStatus.FAILED,
                    reason = result.message,
                    attrs = mapOf(
                        "broadcastId" to broadcastId,
                        "holdId" to normalizedHoldId,
                        "assignmentCount" to assignments.size.toString(),
                        "httpCode" to (result.code?.toString() ?: "unknown"),
                        "apiCode" to (result.apiCode ?: "unknown")
                    )
                )
                BroadcastAssignmentResult.Error(
                    message = mapSubmissionError(result.apiCode, result.message),
                    code = result.code,
                    apiCode = result.apiCode
                )
            }

            is BroadcastResult.Loading -> {
                BroadcastAssignmentResult.Error(
                    message = "Assignment confirmation did not complete.",
                    apiCode = "CONFIRM_INCOMPLETE"
                )
            }
        }
    }

    private fun mapSubmissionError(apiCode: String?, fallbackMessage: String): String {
        return when (apiCode) {
            "DRIVER_BUSY" -> "Driver is already on another trip."
            "DRIVER_NOT_IN_FLEET" -> "This driver does not belong to your fleet."
            "VEHICLE_NOT_IN_FLEET" -> "This vehicle does not belong to your fleet."
            "BROADCAST_FILLED" -> "This booking has already been fully assigned."
            "BROADCAST_EXPIRED" -> "This booking request has expired."
            "INVALID_ASSIGNMENT_STATE" -> "Assignment state changed. Please retry."
            "AUTH_EXPIRED" -> "Session expired. Please login again."
            else -> fallbackMessage
        }
    }
}
