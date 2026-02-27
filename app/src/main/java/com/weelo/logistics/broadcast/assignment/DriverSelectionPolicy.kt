package com.weelo.logistics.broadcast.assignment

import com.weelo.logistics.data.model.Driver
import com.weelo.logistics.data.model.DriverAssignmentAvailability
import com.weelo.logistics.data.model.assignmentAvailability

enum class DriverCandidateStatus {
    ACTIVE,
    OFFLINE,
    ON_TRIP
}

object DriverSelectionPolicy {
    fun classify(driver: Driver): DriverCandidateStatus {
        return when (driver.assignmentAvailability()) {
            DriverAssignmentAvailability.ACTIVE -> DriverCandidateStatus.ACTIVE
            DriverAssignmentAvailability.ON_TRIP -> DriverCandidateStatus.ON_TRIP
            DriverAssignmentAvailability.OFFLINE -> DriverCandidateStatus.OFFLINE
        }
    }

    fun isSelectable(driver: Driver): Boolean {
        return classify(driver) == DriverCandidateStatus.ACTIVE
    }

    fun buildUiState(drivers: List<Driver>): DriverAssignmentUiState {
        val activeDrivers = drivers
            .filter { classify(it) == DriverCandidateStatus.ACTIVE }
            .sortedBy { it.name.lowercase() }
        val onTripDrivers = drivers
            .filter { classify(it) == DriverCandidateStatus.ON_TRIP }
            .sortedBy { it.name.lowercase() }
        val offlineDrivers = drivers.filter { classify(it) == DriverCandidateStatus.OFFLINE }

        val summary = DriverStatusSummary(
            active = activeDrivers.size,
            offline = offlineDrivers.size,
            onTrip = onTripDrivers.size
        )

        if (drivers.isEmpty()) {
            return DriverAssignmentUiState.Empty(
                allDrivers = drivers,
                summary = summary,
                message = "No drivers found in your fleet."
            )
        }

        // Active-only visible policy: on-trip and offline are non-assignable and hidden from picker.
        val visibleDrivers = activeDrivers
        if (visibleDrivers.isEmpty()) {
            return DriverAssignmentUiState.Empty(
                allDrivers = drivers,
                summary = summary,
                message = "No active drivers available right now."
            )
        }

        return DriverAssignmentUiState.Ready(
            allDrivers = drivers,
            visibleDrivers = visibleDrivers,
            summary = summary
        )
    }
}
