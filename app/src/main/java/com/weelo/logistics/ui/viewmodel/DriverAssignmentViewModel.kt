package com.weelo.logistics.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.weelo.logistics.broadcast.assignment.DriverAssignmentUiState
import com.weelo.logistics.broadcast.assignment.DriverStatusSummary
import com.weelo.logistics.data.model.Driver
import com.weelo.logistics.data.model.DriverAssignmentAvailability
import com.weelo.logistics.data.model.Vehicle
import com.weelo.logistics.data.model.assignmentAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * =============================================================================
 * F-C-29 — DriverAssignmentViewModel
 * =============================================================================
 *
 * Phase 9 / t2-driver-assignment MVVM migration.
 *
 * Before this file existed, DriverAssignmentScreen.kt owned every piece of
 * state inline via `remember { mutableStateOf(...) }` — vehicles, drivers,
 * hold countdown, and the vehicle -> driver assignment map. That worked for
 * the initial PRD-7777 Phase 2 shipment but blocks:
 *
 *   - Configuration changes (screen rotation drops the in-flight assignment)
 *   - Process-death recovery via SavedStateHandle
 *   - Snapshot-based unit testing of the assignment invariants
 *
 * This VM is the landing zone for that state. The Screen reads `uiState` via
 * `collectAsStateWithLifecycle` behind `BuildConfig.FF_DRIVER_ASSIGNMENT_VM_MIGRATION`
 * so a rollback is a single FF flip.
 *
 * STATE SHAPE
 *   uiState           — Ready/Loading/Error/Empty per DriverAssignmentUiState
 *   holdRemainingSeconds — Phase-2 countdown, recomputed from ServerDeadlineTimer
 *   resolvedHoldId       — sanitized holdId (null if blank)
 *   driverAssignments    — vehicleId -> driverId (the assignment working set)
 *   fleetDrivers         — last-known driver roster, sorted by availability
 *   vehicles             — selected vehicles the transporter is assigning
 *
 * INVARIANTS
 *   - All emits are on the StateFlow; the Screen never mutates directly.
 *   - Backwards-compatible: while the FF is OFF the Screen keeps using its
 *     inline state and this VM is inert.
 * =============================================================================
 */
class DriverAssignmentViewModel : ViewModel() {

    // ------------------------------------------------------------------
    // uiState — canonical public surface
    // ------------------------------------------------------------------
    private val _uiState = MutableStateFlow<DriverAssignmentUiState>(
        DriverAssignmentUiState.Loading
    )
    val uiState: StateFlow<DriverAssignmentUiState> = _uiState.asStateFlow()

    // ------------------------------------------------------------------
    // Operational fields migrated from the Screen. Public read-only flows
    // so the Screen can observe them via collectAsStateWithLifecycle.
    // ------------------------------------------------------------------
    private val _holdRemainingSeconds = MutableStateFlow(-1)
    val holdRemainingSeconds: StateFlow<Int> = _holdRemainingSeconds.asStateFlow()

    private val _resolvedHoldId = MutableStateFlow<String?>(null)
    val resolvedHoldId: StateFlow<String?> = _resolvedHoldId.asStateFlow()

    private val _driverAssignments = MutableStateFlow<Map<String, String>>(emptyMap())
    val driverAssignments: StateFlow<Map<String, String>> = _driverAssignments.asStateFlow()

    private val _fleetDrivers = MutableStateFlow<List<Driver>>(emptyList())
    val fleetDrivers: StateFlow<List<Driver>> = _fleetDrivers.asStateFlow()

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    // ------------------------------------------------------------------
    // Mutation API — used by the Screen when FF is ON.
    // ------------------------------------------------------------------

    fun setUiState(state: DriverAssignmentUiState) {
        _uiState.value = state
    }

    fun setResolvedHoldId(holdId: String?) {
        _resolvedHoldId.value = holdId?.trim()?.takeUnless { it.isBlank() }
    }

    fun setHoldRemainingSeconds(seconds: Int) {
        _holdRemainingSeconds.value = seconds
    }

    fun setVehicles(items: List<Vehicle>) {
        _vehicles.value = items
    }

    fun setFleetDrivers(drivers: List<Driver>) {
        _fleetDrivers.value = drivers
    }

    fun assignDriver(vehicleId: String, driverId: String) {
        _driverAssignments.value = _driverAssignments.value + (vehicleId to driverId)
    }

    fun removeDriver(vehicleId: String) {
        _driverAssignments.value = _driverAssignments.value - vehicleId
    }

    fun evictDriver(driverId: String) {
        // F-C-47: remove any vehicle->driver entry where the driver matches.
        // Separate mutator from removeDriver(vehicleId) for clarity.
        _driverAssignments.value = _driverAssignments.value.filterValues { it != driverId }
    }

    /**
     * Build a [DriverStatusSummary] from the current fleet roster. Useful for
     * the Ready state of uiState — the Screen is free to compose it on its own
     * as well, but centralising here avoids drift.
     */
    fun currentDriverSummary(): DriverStatusSummary {
        val drivers = _fleetDrivers.value
        var active = 0
        var offline = 0
        var onTrip = 0
        drivers.forEach { driver ->
            when (driver.assignmentAvailability()) {
                DriverAssignmentAvailability.ACTIVE -> active++
                DriverAssignmentAvailability.OFFLINE -> offline++
                DriverAssignmentAvailability.ON_TRIP -> onTrip++
            }
        }
        return DriverStatusSummary(active = active, offline = offline, onTrip = onTrip)
    }
}
