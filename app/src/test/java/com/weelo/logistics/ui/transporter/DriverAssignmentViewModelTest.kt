package com.weelo.logistics.ui.transporter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-29 DriverAssignmentViewModel MVVM migration guard
 * =============================================================================
 *
 * Source-level file-scan guard (same style as HoldFallbackRemovedTest +
 * VehicleHoldConfirmDeadVmGuardTest). Pre-existing Wave-0 compiler errors in
 * sibling files prevent running the Compose runtime here, so these verify the
 * source text matches the intended MVVM contract.
 *
 * Invariants being locked in (Phase 9 / t2-driver-assignment / F-C-29):
 *
 *   1. `DriverAssignmentViewModel.kt` must EXIST under ui/viewmodel/ — the
 *      Screen references it but before this task the file was missing, so the
 *      screen compiled only via ambient androidx `viewModel<T>()` extensions.
 *
 *   2. The ViewModel must expose a single `uiState: StateFlow<...>` — the
 *      Ready/Loading/Error/Empty shape introduced in
 *      broadcast/assignment/DriverAssignmentUiState.kt — AND the operational
 *      fields the Screen currently owns inline: holdRemainingSeconds,
 *      resolvedHoldId, driverAssignments map, fleetDrivers, vehicles.
 *
 *   3. The migration is gated by `BuildConfig.FF_DRIVER_ASSIGNMENT_VM_MIGRATION`
 *      so a rollback flips the flag without re-deleting the VM.
 *
 *   4. `TruckSelectionViewModel` (if present) must carry the
 *      `@file:Suppress("unused")` marker per the user's orphan-retention rule.
 *      If it is absent that is also acceptable (no-op branch).
 * =============================================================================
 */
class DriverAssignmentViewModelTest {

    private val vmFile = File(
        "src/main/java/com/weelo/logistics/ui/viewmodel/DriverAssignmentViewModel.kt"
    )

    private val screenFile = File(
        "src/main/java/com/weelo/logistics/ui/transporter/DriverAssignmentScreen.kt"
    )

    private val truckSelectionVmFile = File(
        "src/main/java/com/weelo/logistics/ui/viewmodel/TruckSelectionViewModel.kt"
    )

    private val vmSource: String by lazy {
        require(vmFile.exists()) {
            "DriverAssignmentViewModel not found at ${vmFile.absolutePath}. " +
                "F-C-29 requires the ViewModel to exist and back the Screen. " +
                "Test must run with cwd=app/."
        }
        vmFile.readText()
    }

    private val screenSource: String by lazy {
        require(screenFile.exists()) {
            "Screen file not found at ${screenFile.absolutePath}. Test must run with cwd=app/."
        }
        screenFile.readText()
    }

    @Test
    fun `DriverAssignmentViewModel file exists`() {
        assertTrue(
            "F-C-29: DriverAssignmentViewModel.kt must exist under ui/viewmodel/ " +
                "so DriverAssignmentScreen can delegate state ownership to it.",
            vmFile.exists()
        )
    }

    @Test
    fun `DriverAssignmentViewModel is wired as MVVM with uiState flow`() {
        assertTrue(
            "VM must extend AndroidX ViewModel",
            vmSource.contains(Regex("""class\s+DriverAssignmentViewModel\s*[^{]*:\s*ViewModel"""))
        )
        assertTrue(
            "VM must expose an observable uiState StateFlow",
            vmSource.contains("uiState") &&
                vmSource.contains(Regex("""StateFlow<[^>]+>"""))
        )
    }

    @Test
    fun `DriverAssignmentViewModel exposes screen state fields`() {
        // The Screen today owns these as local remembered state. Migration
        // moves each into the VM so a recomposition or config change preserves
        // the in-flight assignment.
        val required = listOf(
            "holdRemainingSeconds",
            "resolvedHoldId",
            "driverAssignments",
            "fleetDrivers",
            "vehicles"
        )
        required.forEach { field ->
            assertTrue(
                "F-C-29: DriverAssignmentViewModel must expose `$field` — " +
                    "migrated from DriverAssignmentScreen inline state.",
                vmSource.contains(field)
            )
        }
    }

    @Test
    fun `DriverAssignmentScreen collects uiState from the VM behind FF`() {
        // The Screen must import collectAsStateWithLifecycle (added by the
        // migration) and read `uiState` from the VM when the FF is ON.
        assertTrue(
            "Screen must import androidx lifecycle collectAsStateWithLifecycle",
            screenSource.contains(
                "import androidx.lifecycle.compose.collectAsStateWithLifecycle"
            )
        )
        assertTrue(
            "F-C-29: Screen must guard VM uiState read with " +
                "BuildConfig.FF_DRIVER_ASSIGNMENT_VM_MIGRATION (default OFF)",
            screenSource.contains("FF_DRIVER_ASSIGNMENT_VM_MIGRATION")
        )
        assertTrue(
            "F-C-29: Screen must call collectAsStateWithLifecycle on uiState",
            screenSource.contains("assignmentViewModel.uiState.collectAsStateWithLifecycle") ||
                screenSource.contains(".uiState.collectAsStateWithLifecycle")
        )
    }

    @Test
    fun `orphan TruckSelectionViewModel is retained with unused suppression`() {
        // Per CLAUDE.md orphan-retention rule: do NOT delete dead Screen/VM
        // files, mark them with @file:Suppress("unused") so the linter does
        // not flag them but they remain available for future wiring.
        if (truckSelectionVmFile.exists()) {
            val src = truckSelectionVmFile.readText()
            assertTrue(
                "If TruckSelectionViewModel.kt exists it MUST carry " +
                    "@file:Suppress(\"unused\") per orphan-retention rule.",
                src.contains("@file:Suppress(\"unused\")") ||
                    src.contains("@file:Suppress(\"unused\", ")
            )
        }
    }

    @Test
    fun `viewmodel belongs to the ui_viewmodel package`() {
        assertTrue(
            "Package line must be com.weelo.logistics.ui.viewmodel",
            vmSource.contains(Regex("""package\s+com\.weelo\.logistics\.ui\.viewmodel"""))
        )
    }
}
