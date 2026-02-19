package com.weelo.logistics.broadcast.assignment

import com.weelo.logistics.data.model.Driver

data class DriverStatusSummary(
    val active: Int,
    val offline: Int,
    val onTrip: Int
) {
    val total: Int
        get() = active + offline + onTrip
}

sealed interface DriverAssignmentUiState {
    data object Loading : DriverAssignmentUiState

    data class Ready(
        val allDrivers: List<Driver>,
        val visibleDrivers: List<Driver>,
        val summary: DriverStatusSummary
    ) : DriverAssignmentUiState

    data class Empty(
        val allDrivers: List<Driver>,
        val summary: DriverStatusSummary,
        val message: String
    ) : DriverAssignmentUiState

    data class Error(
        val message: String,
        val retryable: Boolean
    ) : DriverAssignmentUiState
}

sealed interface AssignmentSubmissionResult {
    data object Success : AssignmentSubmissionResult

    data class Failed(
        val message: String,
        val code: String? = null
    ) : AssignmentSubmissionResult
}
