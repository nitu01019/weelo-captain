package com.weelo.logistics.data.model

/**
 * Assignment-focused availability used by transporter assignment flows.
 *
 * This intentionally decouples assignment decisions from backend payload shape
 * so screens can sort and gate selection consistently.
 */
enum class DriverAssignmentAvailability {
    ACTIVE,
    OFFLINE,
    ON_TRIP
}

fun Driver.assignmentAvailability(): DriverAssignmentAvailability {
    return when {
        status == DriverStatus.ON_TRIP -> DriverAssignmentAvailability.ON_TRIP
        status == DriverStatus.ACTIVE && isAvailable -> DriverAssignmentAvailability.ACTIVE
        else -> DriverAssignmentAvailability.OFFLINE
    }
}

fun DriverAssignmentAvailability.sortOrder(): Int {
    return when (this) {
        DriverAssignmentAvailability.ACTIVE -> 0
        DriverAssignmentAvailability.ON_TRIP -> 1
        DriverAssignmentAvailability.OFFLINE -> 2
    }
}

fun DriverAssignmentAvailability.displayName(): String {
    return when (this) {
        DriverAssignmentAvailability.ACTIVE -> "Active"
        DriverAssignmentAvailability.OFFLINE -> "Offline"
        DriverAssignmentAvailability.ON_TRIP -> "On Trip"
    }
}

fun DriverAssignmentAvailability.isSelectableForAssignment(): Boolean {
    return this == DriverAssignmentAvailability.ACTIVE
}
