package com.weelo.logistics.broadcast

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.weelo.logistics.broadcast.assignment.AssignmentSubmissionResult
import com.weelo.logistics.broadcast.assignment.BroadcastDriverAssignmentContent
import com.weelo.logistics.broadcast.assignment.DriverAssignmentUiState
import com.weelo.logistics.broadcast.assignment.DriverSelectionPolicy
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.DriverRepository
import com.weelo.logistics.data.repository.VehicleRepository
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.TruckSelectionSkeleton
import com.weelo.logistics.ui.components.DriverAssignmentSkeleton
import com.weelo.logistics.ui.components.DarkSkeletonBox
import com.weelo.logistics.ui.components.DarkSkeletonCircle
import com.weelo.logistics.ui.components.EmptyStateArtwork
import com.weelo.logistics.ui.components.EmptyStateLayoutStyle
import com.weelo.logistics.ui.components.IllustratedEmptyState
import com.weelo.logistics.ui.theme.BroadcastUiTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.weelo.logistics.data.remote.SocketIOService

private const val TAG = "BroadcastAcceptance"

// =============================================================================
// Broadcast palette mapped to shared tokens (reference yellow + clean surfaces).
// =============================================================================
private val AccentYellow = BroadcastUiTokens.PrimaryCta
private val AccentYellowDark = BroadcastUiTokens.PrimaryCtaPressed
private val BoldBlack = BroadcastUiTokens.ScreenBackground
private val DarkGray = BroadcastUiTokens.CardBackground
private val MediumGray = BroadcastUiTokens.SecondaryText
private val LightGray = BroadcastUiTokens.TertiaryText
private val PureWhite = BroadcastUiTokens.PrimaryText
private val SuccessGreen = BroadcastUiTokens.Success
private val ErrorRed = BroadcastUiTokens.Error
private val OnSuccess = Color.White

/**
 * Data class for truck + driver assignment
 */
data class TruckDriverAssignment(
    val vehicle: Vehicle,
    val driver: Driver? = null
)

/**
 * State for the acceptance flow
 */
enum class AcceptanceStep {
    LOADING,
    SELECT_TRUCKS,
    ASSIGN_DRIVERS,
    SUBMITTING,
    SUCCESS,
    ERROR,
    CANCELLED  // Customer cancelled while transporter was mid-selection
}

private data class HeldTruckSelection(
    val vehicleType: String,
    val vehicleSubtype: String,
    val quantity: Int,
    val holdId: String
) {
    val normalizedType: String = normalizeTruckToken(vehicleType)
    val normalizedSubtype: String = normalizeTruckToken(vehicleSubtype)
}

private fun normalizeTruckToken(value: String): String {
    return value.trim().lowercase().replace(Regex("\\s+"), " ")
}

private fun parseHeldTruckSelections(notes: String?): List<HeldTruckSelection> {
    if (notes.isNullOrBlank()) return emptyList()

    return notes.split(";")
        .mapNotNull { rawEntry ->
            val parts = rawEntry.split("|")
            if (parts.size < 4) return@mapNotNull null

            val quantity = parts[2].trim().toIntOrNull() ?: return@mapNotNull null
            val holdId = parts[3].trim()
            if (quantity <= 0 || holdId.isBlank()) return@mapNotNull null

            HeldTruckSelection(
                vehicleType = parts[0].trim(),
                vehicleSubtype = parts[1].trim(),
                quantity = quantity,
                holdId = holdId
            )
        }
}

private fun resolveHoldSelectionForVehicle(
    vehicle: Vehicle,
    holds: List<HeldTruckSelection>
): HeldTruckSelection? {
    if (holds.isEmpty()) return null

    val subtype = normalizeTruckToken(vehicle.subtype.name)
    val categoryAliases = listOf(
        normalizeTruckToken(vehicle.category.id),
        normalizeTruckToken(vehicle.category.name),
        normalizeTruckToken(vehicle.category.name.removeSuffix(" Truck")),
        normalizeTruckToken(vehicle.category.name.removeSuffix(" truck"))
    ).filter { it.isNotBlank() }

    return holds.firstOrNull { hold ->
        val subtypeMatches = hold.normalizedSubtype.isBlank() || hold.normalizedSubtype == subtype
        subtypeMatches && categoryAliases.contains(hold.normalizedType)
    }
}

private fun DriverAssignmentAvailability.color(): Color {
    return when (this) {
        DriverAssignmentAvailability.ACTIVE -> SuccessGreen
        DriverAssignmentAvailability.OFFLINE -> LightGray
        DriverAssignmentAvailability.ON_TRIP -> MediumGray
    }
}

private fun DriverAssignmentAvailability.upperLabel(): String {
    return displayName().uppercase()
}

/**
 * =============================================================================
 * BROADCAST ACCEPTANCE SCREEN
 * =============================================================================
 * 
 * Full-screen overlay that appears when transporter clicks "Accept" on a broadcast.
 * 
 * FLOW:
 * 1. Accept clicked → Broadcast is put on HOLD (backend notified)
 * 2. Show matching trucks (filtered by booking's vehicle type)
 * 3. Transporter selects trucks (multi-select up to trucks needed)
 * 4. For each selected truck, assign a driver
 * 5. Submit all assignments at once
 * 6. Success → Return to dashboard
 * 
 * DESIGN:
 * - Same Rapido-style dark theme as broadcast overlay
 * - Bold black background with yellow accents
 * - Smooth animations throughout
 * 
 * SCALABILITY:
 * - Batch API calls for efficiency
 * - Optimistic UI updates
 * - Error handling with retry
 * 
 * =============================================================================
 */
@Composable
fun BroadcastAcceptanceScreen(
    broadcast: BroadcastTrip,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Repositories
    val vehicleRepository = remember { VehicleRepository.getInstance(context) }
    val driverRepository = remember { DriverRepository.getInstance(context) }
    val broadcastRepository = remember { BroadcastRepository.getInstance(context) }
    val assignmentCoordinator = remember { BroadcastAssignmentCoordinator(broadcastRepository) }
    
    // State
    var currentStep by remember { mutableStateOf(AcceptanceStep.LOADING) }
    var availableVehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var allDrivers by remember { mutableStateOf<List<Driver>>(emptyList()) }
    var driverAssignmentState by remember { mutableStateOf<DriverAssignmentUiState>(DriverAssignmentUiState.Loading) }
    var selectedVehicles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var assignments by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // vehicleId -> driverId
    var submissionResults by remember { mutableStateOf<Map<String, AssignmentSubmissionResult>>(emptyMap()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submissionProgress by remember { mutableStateOf(0f) }
    var pendingSubmissionCount by remember { mutableStateOf(0) }
    var isSubmittingAssignments by remember { mutableStateOf(false) }
    var confirmedHoldIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var submissionJob by remember { mutableStateOf<Job?>(null) }

    val heldSelections = remember(broadcast.notes) { parseHeldTruckSelections(broadcast.notes) }
    val hasHeldSelections = heldSelections.isNotEmpty()
    val heldTruckTarget = heldSelections.sumOf { it.quantity }

    val allDriversById = remember(allDrivers) { allDrivers.associateBy { it.id } }
    val availableVehiclesById = remember(availableVehicles) { availableVehicles.associateBy { it.id } }
    val selectedAssignments = remember(selectedVehicles, assignments) {
        assignments.filterKeys { it in selectedVehicles }
    }

    // Calculate trucks needed
    val broadcastTrucksNeeded = (broadcast.totalTrucksNeeded - broadcast.trucksFilledSoFar).coerceAtLeast(0)
    val trucksNeeded = if (hasHeldSelections) heldTruckTarget else broadcastTrucksNeeded

    val selectedCountByHoldId = remember(selectedVehicles, availableVehicles, heldSelections) {
        val counts = mutableMapOf<String, Int>()
        selectedVehicles.forEach { vehicleId ->
            val vehicle = availableVehicles.find { it.id == vehicleId } ?: return@forEach
            val hold = resolveHoldSelectionForVehicle(vehicle, heldSelections) ?: return@forEach
            counts[hold.holdId] = (counts[hold.holdId] ?: 0) + 1
        }
        counts
    }

    val selectedVehiclesOutsideHeldTypes = remember(selectedVehicles, availableVehicles, heldSelections) {
        if (!hasHeldSelections) {
            emptyList()
        } else {
            selectedVehicles.filter { vehicleId ->
                val vehicle = availableVehicles.find { it.id == vehicleId } ?: return@filter true
                resolveHoldSelectionForVehicle(vehicle, heldSelections) == null
            }
        }
    }

    val holdSelectionCountsValid = !hasHeldSelections || heldSelections.all { hold ->
        selectedCountByHoldId[hold.holdId] == hold.quantity
    }

    val canProceedToDrivers = if (hasHeldSelections) {
        selectedVehicles.isNotEmpty() &&
            selectedVehicles.size == trucksNeeded &&
            selectedVehiclesOutsideHeldTypes.isEmpty() &&
            holdSelectionCountsValid
    } else {
        selectedVehicles.isNotEmpty() && selectedVehicles.size <= trucksNeeded
    }

    val hasInvalidAssignments = selectedVehicles.any { vehicleId ->
        val assignedDriverId = selectedAssignments[vehicleId] ?: return@any false
        val assignedDriver = allDriversById[assignedDriverId] ?: return@any true
        !DriverSelectionPolicy.isSelectable(assignedDriver)
    }
    val allDriversAssigned = driverAssignmentState is DriverAssignmentUiState.Ready &&
        selectedVehicles.isNotEmpty() &&
        (!hasHeldSelections || selectedVehicles.size == trucksNeeded) &&
        selectedVehicles.all { vehicleId -> selectedAssignments.containsKey(vehicleId) } &&
        !hasInvalidAssignments

    fun releaseUnconfirmedHolds() {
        if (!hasHeldSelections) return
        val pendingHoldIds = heldSelections
            .map { it.holdId }
            .filter { it !in confirmedHoldIds }
            .distinct()
        if (pendingHoldIds.isEmpty()) return

        scope.launch {
            pendingHoldIds.forEach { holdId ->
                when (val releaseResult = broadcastRepository.releaseHold(holdId)) {
                    is com.weelo.logistics.data.repository.BroadcastResult.Success -> {
                        timber.log.Timber.i("🔓 Released pending hold: $holdId")
                    }
                    is com.weelo.logistics.data.repository.BroadcastResult.Error -> {
                        timber.log.Timber.w("⚠️ Failed to release hold $holdId: ${releaseResult.message}")
                    }
                    is com.weelo.logistics.data.repository.BroadcastResult.Loading -> Unit
                }
            }
        }
    }

    suspend fun refreshDriverState(forceRefresh: Boolean) {
        val startedAt = System.currentTimeMillis()
        driverAssignmentState = DriverAssignmentUiState.Loading
        when (val driverResult = driverRepository.fetchDrivers(forceRefresh = forceRefresh)) {
            is com.weelo.logistics.data.repository.DriverResult.Success -> {
                allDrivers = driverResult.data.drivers
                driverAssignmentState = DriverSelectionPolicy.buildUiState(driverResult.data.drivers)
                BroadcastTelemetry.recordLatency(
                    name = "driver_assignment_fetch_latency_ms",
                    ms = System.currentTimeMillis() - startedAt,
                    attrs = mapOf("broadcastId" to broadcast.broadcastId)
                )
            }
            is com.weelo.logistics.data.repository.DriverResult.Error -> {
                driverAssignmentState = DriverAssignmentUiState.Error(
                    message = driverResult.message,
                    retryable = true
                )
                BroadcastTelemetry.record(
                    stage = BroadcastStage.BROADCAST_ACTIVE_FETCH,
                    status = BroadcastStatus.FAILED,
                    reason = "driver_fetch_failed",
                    attrs = mapOf(
                        "broadcastId" to broadcast.broadcastId,
                        "message" to driverResult.message
                    )
                )
            }
            is com.weelo.logistics.data.repository.DriverResult.Loading -> {
                driverAssignmentState = DriverAssignmentUiState.Loading
            }
        }
    }

    // ========== ORDER CANCELLED: Auto-dismiss with overlay (PRD 4.4) ==========
    // If customer cancels while transporter is mid-selection, abort and show overlay
    LaunchedEffect(Unit) {
        // Use LaunchedEffect coroutine scope (NOT rememberCoroutineScope) so delay is cancelled safely
        // and we don't leak/race multiple dismiss jobs.
        SocketIOService.orderCancelled.collectLatest { notification: com.weelo.logistics.data.remote.OrderCancelledNotification ->
            if (notification.orderId == broadcast.broadcastId) {
                submissionJob?.cancel()
                submissionJob = null
                isSubmittingAssignments = false  // Abort any in-flight submission
                releaseUnconfirmedHolds()
                currentStep = AcceptanceStep.CANCELLED
                delay(1_500L)
                onDismiss()
            }
        }
    }

    // Keep assignment state fresh when drivers go online/offline in real-time.
    LaunchedEffect(Unit) {
        SocketIOService.driverStatusChanged.collectLatest { statusEvent ->
            if (allDrivers.none { it.id == statusEvent.driverId }) return@collectLatest
            allDrivers = allDrivers.map { driver ->
                if (driver.id == statusEvent.driverId) {
                    driver.copy(isAvailable = statusEvent.isOnline)
                } else {
                    driver
                }
            }
            driverAssignmentState = DriverSelectionPolicy.buildUiState(allDrivers)
            val staleAssignmentDriverIds = assignments.values.filter { driverId ->
                val updatedDriver = allDrivers.firstOrNull { it.id == driverId } ?: return@filter true
                !DriverSelectionPolicy.isSelectable(updatedDriver)
            }.toSet()
            if (staleAssignmentDriverIds.isNotEmpty()) {
                assignments = assignments.filterValues { it !in staleAssignmentDriverIds }
                errorMessage = "Some assigned drivers went offline. Please reassign."
            }
        }
    }

    // Load vehicles and drivers when visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            currentStep = AcceptanceStep.LOADING
            selectedVehicles = emptySet()
            assignments = emptyMap()
            submissionResults = emptyMap()
            errorMessage = null
            submissionProgress = 0f
            pendingSubmissionCount = 0
            isSubmittingAssignments = false
            confirmedHoldIds = emptySet()

            try {
                // Fetch vehicles matching the broadcast's vehicle type
                val vehicleResult = vehicleRepository.fetchVehicles(forceRefresh = true)
                if (vehicleResult is com.weelo.logistics.data.repository.VehicleResult.Success) {
                    val requestedTypes = broadcast.requestedVehicles.map { it.vehicleType.lowercase() }.toSet()
                    val requestedSubtypes = broadcast.requestedVehicles
                        .filter { it.vehicleSubtype.isNotEmpty() }
                        .map { "${it.vehicleType.lowercase()}_${it.vehicleSubtype.lowercase()}" }
                        .toSet()

                    val filtered = vehicleResult.data.vehicles.filter { vehicle ->
                        val isAvailable = vehicle.status.lowercase() == "available"
                        val typeMatch = requestedTypes.contains(vehicle.vehicleType.lowercase())
                        val subtypeKey = "${vehicle.vehicleType.lowercase()}_${vehicle.vehicleSubtype.lowercase()}"
                        val subtypeMatch = requestedSubtypes.isEmpty() || requestedSubtypes.contains(subtypeKey)
                        val holdTypeMatch = if (!hasHeldSelections) {
                            true
                        } else {
                            val type = normalizeTruckToken(vehicle.vehicleType)
                            val subtype = normalizeTruckToken(vehicle.vehicleSubtype)
                            heldSelections.any { hold ->
                                val subtypeMatches = hold.normalizedSubtype.isBlank() || hold.normalizedSubtype == subtype
                                subtypeMatches && hold.normalizedType == type
                            }
                        }

                        isAvailable && typeMatch && (requestedSubtypes.isEmpty() || subtypeMatch) && holdTypeMatch
                    }

                    availableVehicles = vehicleRepository.mapToUiModels(filtered)
                    timber.log.Timber.i("✅ Found ${availableVehicles.size} matching vehicles for types: $requestedTypes")
                } else if (vehicleResult is com.weelo.logistics.data.repository.VehicleResult.Error) {
                    errorMessage = vehicleResult.message
                    currentStep = AcceptanceStep.ERROR
                    return@LaunchedEffect
                }

                refreshDriverState(forceRefresh = true)
                currentStep = AcceptanceStep.SELECT_TRUCKS
            } catch (e: Exception) {
                timber.log.Timber.e("❌ Error loading data: ${e.message}")
                errorMessage = e.message ?: "Failed to load data"
                currentStep = AcceptanceStep.ERROR
            }
        }
    }

    // Submit all assignments
    fun submitAssignments() {
        if (isSubmittingAssignments) return

        submissionJob?.cancel()
        submissionJob = scope.launch {
            if (driverAssignmentState !is DriverAssignmentUiState.Ready) {
                errorMessage = "Driver list is not ready. Retry loading drivers."
                currentStep = AcceptanceStep.ASSIGN_DRIVERS
                return@launch
            }

            val assignmentsToValidate = selectedAssignments.entries.toList()
            if (assignmentsToValidate.isEmpty()) {
                errorMessage = "Assign drivers to all selected trucks before submitting."
                currentStep = AcceptanceStep.ASSIGN_DRIVERS
                return@launch
            }

            if (hasHeldSelections && assignmentsToValidate.size != trucksNeeded) {
                errorMessage = "Select and assign exactly $trucksNeeded held truck(s) before submitting."
                currentStep = AcceptanceStep.ASSIGN_DRIVERS
                return@launch
            }

            if (hasHeldSelections && selectedVehiclesOutsideHeldTypes.isNotEmpty()) {
                errorMessage = "One selected vehicle does not match held truck types. Re-select and retry."
                currentStep = AcceptanceStep.SELECT_TRUCKS
                return@launch
            }

            if (hasHeldSelections && !holdSelectionCountsValid) {
                errorMessage = "Selected trucks do not match held quantities. Fix selection and retry."
                currentStep = AcceptanceStep.SELECT_TRUCKS
                return@launch
            }

            val invalidEntry = assignmentsToValidate.firstOrNull { (_, driverId) ->
                val driver = allDriversById[driverId] ?: return@firstOrNull true
                !DriverSelectionPolicy.isSelectable(driver)
            }
            if (invalidEntry != null) {
                errorMessage = "One selected driver is no longer assignable. Reassign and retry."
                currentStep = AcceptanceStep.ASSIGN_DRIVERS
                return@launch
            }

            currentStep = AcceptanceStep.SUBMITTING
            isSubmittingAssignments = true

            val updatedSubmissionResults = submissionResults.toMutableMap()
            var successCount = 0
            var failedCount = 0

            try {
                if (hasHeldSelections) {
                    val assignmentsByHoldId = mutableMapOf<String, MutableList<Pair<String, String>>>()

                    assignmentsToValidate.forEach { (vehicleId, driverId) ->
                        val vehicle = availableVehiclesById[vehicleId]
                        val hold = vehicle?.let { resolveHoldSelectionForVehicle(it, heldSelections) }
                        if (vehicle == null || hold == null) {
                            failedCount++
                            updatedSubmissionResults[vehicleId] = AssignmentSubmissionResult.Failed(
                                message = "Vehicle does not match held truck selection.",
                                code = null
                            )
                        } else {
                            assignmentsByHoldId.getOrPut(hold.holdId) { mutableListOf() }.add(vehicleId to driverId)
                        }
                    }

                    val pendingHolds = heldSelections.filter { it.holdId !in confirmedHoldIds }
                    if (pendingHolds.isEmpty()) {
                        currentStep = AcceptanceStep.SUCCESS
                        kotlinx.coroutines.delay(1200)
                        onSuccess()
                        return@launch
                    }

                    pendingSubmissionCount = pendingHolds.sumOf { it.quantity }.coerceAtLeast(1)
                    submissionProgress = 0f
                    var processedCount = 0
                    val newlyConfirmed = confirmedHoldIds.toMutableSet()

                    pendingHolds.forEach { hold ->
                        val holdAssignments = assignmentsByHoldId[hold.holdId].orEmpty()
                        if (holdAssignments.size != hold.quantity) {
                            failedCount += holdAssignments.size
                            holdAssignments.forEach { (vehicleId, _) ->
                                updatedSubmissionResults[vehicleId] = AssignmentSubmissionResult.Failed(
                                    message = "Expected ${hold.quantity} assignments for held ${hold.vehicleType} ${hold.vehicleSubtype}.",
                                    code = null
                                )
                            }
                            processedCount += holdAssignments.size
                            submissionProgress = processedCount.toFloat() / pendingSubmissionCount
                            return@forEach
                        }

                        timber.log.Timber.i(
                            "📤 Confirming hold ${hold.holdId} with ${holdAssignments.size} assignment(s)"
                        )
                        BroadcastTelemetry.record(
                            stage = BroadcastStage.BROADCAST_CONFIRM_ASSIGN_REQUESTED,
                            status = BroadcastStatus.SUCCESS,
                            attrs = mapOf(
                                "broadcastId" to broadcast.broadcastId,
                                "holdId" to hold.holdId,
                                "assignmentCount" to holdAssignments.size.toString()
                            )
                        )

                        when (
                            val result = assignmentCoordinator.confirmHoldAssignments(
                                broadcastId = broadcast.broadcastId,
                                holdId = hold.holdId,
                                assignments = holdAssignments
                            )
                        ) {
                            is BroadcastAssignmentResult.Success -> {
                                newlyConfirmed.add(hold.holdId)
                                holdAssignments.forEach { (vehicleId, _) ->
                                    successCount++
                                    updatedSubmissionResults[vehicleId] = AssignmentSubmissionResult.Success
                                }
                            }
                            is BroadcastAssignmentResult.Error -> {
                                failedCount += holdAssignments.size
                                holdAssignments.forEach { (vehicleId, _) ->
                                    updatedSubmissionResults[vehicleId] = AssignmentSubmissionResult.Failed(
                                        message = result.message,
                                        code = result.apiCode
                                    )
                                }
                                timber.log.Timber.w(
                                    "⚠️ Hold confirm failed: holdId=${hold.holdId} msg=${result.message}"
                                )
                            }
                        }

                        processedCount += holdAssignments.size
                        submissionProgress = processedCount.toFloat() / pendingSubmissionCount
                    }

                    confirmedHoldIds = newlyConfirmed
                } else {
                    failedCount = assignmentsToValidate.size
                    errorMessage = "Legacy assignment path disabled. Reopen request and hold trucks before assigning drivers."
                    currentStep = AcceptanceStep.ERROR
                    return@launch
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                timber.log.Timber.e("❌ Submission error: ${e.message}")
                val pendingVehicleIds = selectedAssignments.keys
                failedCount = pendingVehicleIds.count { updatedSubmissionResults[it] !is AssignmentSubmissionResult.Success }
                pendingVehicleIds.forEach { vehicleId ->
                    if (updatedSubmissionResults[vehicleId] !is AssignmentSubmissionResult.Success) {
                        updatedSubmissionResults[vehicleId] = AssignmentSubmissionResult.Failed(
                            message = "Submission failed. Please retry.",
                            code = null
                        )
                    }
                }
            } finally {
                submissionResults = updatedSubmissionResults
                isSubmittingAssignments = false
                submissionJob = null
            }

            if (failedCount == 0) {
                timber.log.Timber.i("✅ Successfully submitted $successCount assignment(s)")
                currentStep = AcceptanceStep.SUCCESS
                kotlinx.coroutines.delay(1500)
                onSuccess()
            } else {
                currentStep = AcceptanceStep.ASSIGN_DRIVERS
                val retryMessage = if (successCount > 0) {
                    "$successCount assignment(s) sent, $failedCount failed. Retry failed trucks."
                } else {
                    "Failed to submit assignments. Fix errors and retry."
                }
                errorMessage = retryMessage
                Toast.makeText(context, retryMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Animated visibility
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(250)) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut(tween(150)) + slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(200)
        )
    ) {
        Dialog(
            onDismissRequest = { 
                if (!isSubmittingAssignments && currentStep != AcceptanceStep.SUBMITTING) {
                    releaseUnconfirmedHolds()
                    onDismiss()
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = !isSubmittingAssignments && currentStep != AcceptanceStep.SUBMITTING,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BoldBlack)
            ) {
                when (currentStep) {
                    AcceptanceStep.LOADING -> LoadingContent()
                    
                    AcceptanceStep.SELECT_TRUCKS -> TruckSelectionContent(
                        broadcast = broadcast,
                        vehicles = availableVehicles,
                        selectedVehicles = selectedVehicles,
                        trucksNeeded = trucksNeeded,
                        onVehicleToggle = onToggle@{ vehicleId ->
                            if (selectedVehicles.contains(vehicleId)) {
                                selectedVehicles = selectedVehicles - vehicleId
                                assignments = assignments - vehicleId
                                submissionResults = submissionResults - vehicleId
                            } else if (selectedVehicles.size < trucksNeeded) {
                                if (hasHeldSelections) {
                                    val vehicle = availableVehiclesById[vehicleId]
                                    val hold = vehicle?.let { resolveHoldSelectionForVehicle(it, heldSelections) }
                                    if (vehicle == null || hold == null) {
                                        errorMessage = "This vehicle is not part of held truck slots."
                                        return@onToggle
                                    }

                                    val currentCountForHold = selectedVehicles.count { selectedVehicleId ->
                                        val selectedVehicle = availableVehiclesById[selectedVehicleId] ?: return@count false
                                        resolveHoldSelectionForVehicle(selectedVehicle, heldSelections)?.holdId == hold.holdId
                                    }

                                    if (currentCountForHold >= hold.quantity) {
                                        errorMessage = "You can select only ${hold.quantity} ${hold.vehicleType} ${hold.vehicleSubtype} truck(s)."
                                        return@onToggle
                                    }
                                }

                                selectedVehicles = selectedVehicles + vehicleId
                            }
                        },
                        onProceed = {
                            assignments = selectedAssignments
                            submissionResults = submissionResults.filterKeys { it in selectedVehicles }
                            errorMessage = null
                            currentStep = AcceptanceStep.ASSIGN_DRIVERS
                        },
                        onCancel = {
                            releaseUnconfirmedHolds()
                            onDismiss()
                        },
                        canProceed = canProceedToDrivers
                    )
                    
                    AcceptanceStep.ASSIGN_DRIVERS -> BroadcastDriverAssignmentContent(
                        vehicles = availableVehicles.filter { it.id in selectedVehicles },
                        driverState = driverAssignmentState,
                        assignments = selectedAssignments,
                        submissionResults = submissionResults.filterKeys { it in selectedVehicles },
                        hasInvalidAssignments = hasInvalidAssignments,
                        canSubmit = allDriversAssigned,
                        isSubmitting = isSubmittingAssignments,
                        onAssignDriver = { vehicleId, driverId ->
                            assignments = selectedAssignments + (vehicleId to driverId)
                            submissionResults = submissionResults - vehicleId
                            errorMessage = null
                        },
                        onRetryLoadDrivers = {
                            scope.launch { refreshDriverState(forceRefresh = true) }
                        },
                        onBack = { currentStep = AcceptanceStep.SELECT_TRUCKS },
                        onSubmit = { submitAssignments() },
                    )
                    
                    AcceptanceStep.SUBMITTING -> SubmittingContent(
                        progress = submissionProgress,
                        totalCount = pendingSubmissionCount.coerceAtLeast(1)
                    )
                    
                    AcceptanceStep.SUCCESS -> SuccessContent(
                        assignmentCount = selectedAssignments.size
                    )
                    
                    AcceptanceStep.ERROR -> ErrorContent(
                        message = errorMessage ?: "Unknown error",
                        onRetry = { currentStep = AcceptanceStep.SELECT_TRUCKS },
                        onDismiss = {
                            releaseUnconfirmedHolds()
                            onDismiss()
                        }
                    )

                    AcceptanceStep.CANCELLED -> CancelledContent()
                }
            }
        }
    }
}

// =============================================================================
// LOADING CONTENT - Professional Skeleton Loading
// =============================================================================
// 
// DESIGN PRINCIPLES:
// - Uses shimmer animation for smooth loading UX
// - Matches exact layout of TruckSelectionContent
// - Dark theme consistent with overlay
// - Professional, not childish
// 
// SCALABILITY:
// - Lightweight animation (no heavy computations)
// - Reusable skeleton components
// =============================================================================
@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DarkSkeletonCircle(size = 40.dp)
            DarkSkeletonBox(height = 24.dp, width = 120.dp)
            DarkSkeletonBox(height = 36.dp, width = 70.dp, shape = RoundedCornerShape(20.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Broadcast summary skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkGray),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DarkSkeletonCircle(size = 16.dp)
                    DarkSkeletonBox(height = 14.dp, width = 200.dp)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DarkSkeletonCircle(size = 16.dp)
                    DarkSkeletonBox(height = 14.dp, width = 180.dp)
                }
                Spacer(Modifier.height(12.dp))
                Divider(color = MediumGray, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DarkSkeletonBox(height = 12.dp, width = 100.dp)
                    DarkSkeletonBox(height = 14.dp, width = 80.dp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Info text skeleton
        DarkSkeletonBox(height = 14.dp, width = 180.dp)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Truck cards skeleton (3 items)
        repeat(3) {
            TruckCardSkeleton()
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Bottom buttons skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DarkSkeletonBox(
                modifier = Modifier.weight(1f),
                height = 48.dp,
                shape = RoundedCornerShape(8.dp)
            )
            DarkSkeletonBox(
                modifier = Modifier.weight(1f),
                height = 48.dp,
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

/**
 * Skeleton for individual truck card in selection screen
 */
@Composable
private fun TruckCardSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BoldBlack),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Truck icon skeleton
                DarkSkeletonCircle(size = 48.dp)
                
                Column {
                    DarkSkeletonBox(height = 16.dp, width = 100.dp)
                    Spacer(Modifier.height(6.dp))
                    DarkSkeletonBox(height = 13.dp, width = 80.dp)
                    Spacer(Modifier.height(4.dp))
                    DarkSkeletonBox(height = 12.dp, width = 70.dp)
                }
            }
            
            // Checkbox skeleton
            DarkSkeletonCircle(size = 28.dp)
        }
    }
}

// =============================================================================
// TRUCK SELECTION CONTENT - Enhanced with Sorting and Grouping
// =============================================================================
// 
// FEATURES:
// - Trucks sorted by requested vehicle type (matching types first)
// - Grouped by vehicle type with section headers
// - Smooth scrolling with fling behavior
// - Professional dark theme
// - Selection counter with visual feedback
// 
// SORTING LOGIC:
// 1. First: Trucks matching the requested vehicle type (exact match)
// 2. Second: Trucks matching the requested subtype
// 3. Third: All other available trucks
// 
// SCALABILITY:
// - Efficient grouping with remember/derivedStateOf
// - LazyColumn for large lists
// - Key-based recomposition for performance
// =============================================================================
@Composable
private fun TruckSelectionContent(
    broadcast: BroadcastTrip,
    vehicles: List<Vehicle>,
    selectedVehicles: Set<String>,
    trucksNeeded: Int,
    onVehicleToggle: (String) -> Unit,
    onProceed: () -> Unit,
    onCancel: () -> Unit,
    canProceed: Boolean
) {
    // Get requested vehicle types from broadcast
    val requestedTypes = remember(broadcast) {
        broadcast.requestedVehicles
            .map { it.vehicleType.lowercase() to it.vehicleSubtype.lowercase() }
            .toSet()
    }
    
    // Sort and group vehicles by type (matching types first)
    val sortedAndGroupedVehicles = remember(vehicles, requestedTypes) {
        // Sort: matching types first, then by type name
        val sorted = vehicles.sortedWith(compareBy(
            // Primary sort: matching requested type comes first (0), others later (1)
            { vehicle -> 
                val typeMatch = requestedTypes.any { (type, _) -> 
                    vehicle.category.name.lowercase() == type 
                }
                if (typeMatch) 0 else 1
            },
            // Secondary sort: matching subtype comes first
            { vehicle ->
                val subtypeMatch = requestedTypes.any { (type, subtype) ->
                    vehicle.category.name.lowercase() == type && 
                    vehicle.subtype.name.lowercase() == subtype
                }
                if (subtypeMatch) 0 else 1
            },
            // Tertiary sort: alphabetically by type name
            { it.category.name },
            // Quaternary sort: alphabetically by vehicle number
            { it.vehicleNumber }
        ))
        
        // Group by vehicle type
        sorted.groupBy { "${it.category.name} - ${it.subtype.name}" }
    }
    
    // Calculate selection progress
    val selectionProgress = selectedVehicles.size.toFloat() / trucksNeeded.coerceAtLeast(1)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoldBlack)
    ) {
        // ============== HEADER ==============
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BoldBlack)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back/Close button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(DarkGray, CircleShape)
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = PureWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SELECT TRUCKS",
                        color = PureWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Choose which trucks to assign",
                        color = MediumGray,
                        fontSize = 12.sp
                    )
                }
                
                // Selection counter with progress
                Box(
                    modifier = Modifier
                        .background(
                            if (canProceed) SuccessGreen else AccentYellow,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${selectedVehicles.size}/$trucksNeeded",
                        color = if (canProceed) OnSuccess else BroadcastUiTokens.OnPrimaryCta,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            // Progress bar
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = selectionProgress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (canProceed) SuccessGreen else AccentYellow,
                trackColor = DarkGray
            )
        }
        
        // ============== BROADCAST SUMMARY (Compact) ==============
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            BroadcastSummaryCard(broadcast)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // ============== TRUCK LIST ==============
        if (vehicles.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    @Suppress("DEPRECATION")
                    val vehicleTypeName = broadcast.vehicleType?.name ?: "matching"
                    IllustratedEmptyState(
                        illustrationRes = EmptyStateArtwork.MATCHING_TRUCKS.drawableRes,
                        title = stringResource(R.string.empty_title_no_matching_trucks),
                        subtitle = stringResource(R.string.empty_subtitle_no_matching_trucks_format, vehicleTypeName),
                        maxIllustrationWidthDp = 190,
                        maxTextWidthDp = 260,
                        showFramedIllustration = EmptyStateLayoutStyle.MODAL_COMPACT.showFramedIllustration
                    )
                }
            }
        } else {
            // Truck list with grouping
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // Smooth scrolling with fling
                flingBehavior = ScrollableDefaults.flingBehavior()
            ) {
                sortedAndGroupedVehicles.forEach { (groupName, groupVehicles) ->
                    // Section header
                    item(key = "header_$groupName") {
                        TruckGroupHeader(
                            title = groupName,
                            count = groupVehicles.size,
                            selectedCount = groupVehicles.count { selectedVehicles.contains(it.id) },
                            isRequestedType = requestedTypes.any { (type, subtype) ->
                                groupName.lowercase().contains(type) &&
                                (subtype.isEmpty() || groupName.lowercase().contains(subtype))
                            }
                        )
                    }
                    
                    // Trucks in this group
                    items(
                        items = groupVehicles,
                        key = { "vehicle_${it.id}" }
                    ) { vehicle ->
                        VehicleSelectCard(
                            vehicle = vehicle,
                            isSelected = selectedVehicles.contains(vehicle.id),
                            onToggle = { onVehicleToggle(vehicle.id) },
                            canSelect = selectedVehicles.size < trucksNeeded || selectedVehicles.contains(vehicle.id)
                        )
                    }
                    
                    // Spacer between groups
                    item(key = "spacer_$groupName") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        
        // ============== BOTTOM ACTION BAR ==============
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PureWhite,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Selection summary
                if (selectedVehicles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected: ${selectedVehicles.size} truck${if (selectedVehicles.size > 1) "s" else ""}",
                            color = AccentYellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (selectedVehicles.size < trucksNeeded) {
                            Text(
                                text = "${trucksNeeded - selectedVehicles.size} more needed",
                                color = MediumGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BroadcastUiTokens.SecondaryCtaText
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MediumGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "CANCEL",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    // Proceed button
                    Button(
                        onClick = onProceed,
                        enabled = canProceed,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canProceed) SuccessGreen else AccentYellow,
                            contentColor = if (canProceed) OnSuccess else BroadcastUiTokens.OnPrimaryCta,
                            disabledContainerColor = DarkGray,
                            disabledContentColor = MediumGray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (canProceed) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (canProceed) "ASSIGN DRIVERS" else "SELECT TRUCKS",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * =============================================================================
 * TRUCK GROUP HEADER - Section header for grouped trucks
 * =============================================================================
 */
@Composable
private fun TruckGroupHeader(
    title: String,
    count: Int,
    selectedCount: Int,
    isRequestedType: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Requested type badge
            if (isRequestedType) {
                Box(
                    modifier = Modifier
                        .background(AccentYellow, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "REQUESTED",
                            color = PureWhite,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            
            Text(
                text = title.uppercase(),
                color = if (isRequestedType) AccentYellow else LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        
        // Count badge
        Text(
            text = if (selectedCount > 0) "$selectedCount/$count" else "$count available",
            color = if (selectedCount > 0) SuccessGreen else MediumGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// =============================================================================
// BROADCAST SUMMARY CARD (Compact)
// =============================================================================
@Composable
private fun BroadcastSummaryCard(broadcast: BroadcastTrip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BroadcastMiniRouteMapCard(
                broadcast = broadcast,
                title = "Route map",
                subtitle = "${broadcast.distance.toInt()} km",
                mapHeight = 120.dp
            )

            // Route
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = broadcast.pickupLocation.address.take(40) + if (broadcast.pickupLocation.address.length > 40) "..." else "",
                    color = PureWhite,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = broadcast.dropLocation.address.take(40) + if (broadcast.dropLocation.address.length > 40) "..." else "",
                    color = PureWhite,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Divider(color = MediumGray, thickness = 0.5.dp)
            
            // Details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Vehicle type
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = AccentYellow,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = broadcast.requestedVehicles.firstOrNull()?.let { 
                            "${it.vehicleType}${if (it.vehicleSubtype.isNotEmpty()) " - ${it.vehicleSubtype}" else ""}"
                        } ?: (@Suppress("DEPRECATION") broadcast.vehicleType?.name) ?: "Truck",
                        color = LightGray,
                        fontSize = 12.sp
                    )
                }
                
                // Fare
                Text(
                    text = "₹${broadcast.farePerTruck.toInt()}/truck",
                    color = AccentYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =============================================================================
// VEHICLE SELECT CARD
// =============================================================================
@Composable
private fun VehicleSelectCard(
    vehicle: Vehicle,
    isSelected: Boolean,
    onToggle: () -> Unit,
    canSelect: Boolean
) {
    val borderColor = animateColorAsState(
        targetValue = if (isSelected) AccentYellow else MediumGray,
        animationSpec = tween(200),
        label = "border"
    )
    
    val backgroundColor = animateColorAsState(
        targetValue = if (isSelected) DarkGray else BoldBlack,
        animationSpec = tween(200),
        label = "background"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor.value,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = canSelect) { onToggle() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor.value),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Truck icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isSelected) AccentYellow.copy(alpha = 0.2f) else MediumGray.copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = if (isSelected) AccentYellow else LightGray,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Column {
                    Text(
                        text = vehicle.vehicleNumber,
                        color = PureWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${vehicle.category.name} - ${vehicle.subtype.name}",
                        color = LightGray,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${vehicle.subtype.capacityTons} Ton capacity",
                        color = MediumGray,
                        fontSize = 12.sp
                    )
                }
            }
            
            // Selection indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (isSelected) AccentYellow else Color.Transparent,
                        CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) AccentYellow else MediumGray,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// DRIVER ASSIGNMENT CONTENT - Enhanced with 7-second Debounce
// =============================================================================
// 
// FEATURES:
// - Professional skeleton loading while drivers load
// - 7-second debounce before sending to backend/driver
// - Visual countdown timer for pending assignments
// - Grouped by vehicle with clear status indicators
// - Smooth animations and transitions
// 
// DEBOUNCE LOGIC:
// - When driver is assigned to a truck, 7-second countdown starts
// - User can change driver during countdown (resets timer)
// - After 7 seconds, assignment is "locked in"
// - Prevents accidental rapid clicks and allows driver to prepare
// 
// SCALABILITY:
// - Efficient state management with remember/derivedStateOf
// - LazyColumn for large driver lists
// - Debounce prevents backend spam
// =============================================================================

/**
 * State for tracking debounced driver assignments
 */
data class DebounceAssignment(
    val vehicleId: String,
    val driverId: String,
    val assignedAt: Long,
    val isLocked: Boolean = false  // True after 7 seconds
)

private const val DEBOUNCE_SECONDS = 7

@Composable
@Suppress("UNUSED_PARAMETER")
private fun DriverAssignmentContent(
    broadcast: BroadcastTrip,
    vehicles: List<Vehicle>,
    drivers: List<Driver>,
    assignments: Map<String, String>,
    hasInvalidAssignments: Boolean,
    onAssignDriver: (vehicleId: String, driverId: String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean
) {
    var expandedVehicleId by remember { mutableStateOf<String?>(null) }
    var isLoadingDrivers by remember { mutableStateOf(drivers.isEmpty()) }
    val driversById = remember(drivers) { drivers.associateBy { it.id } }
    
    // Debounce state - tracks countdown for each assignment
    var debounceTimers by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var lockedAssignments by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Current time for countdown calculations (updates every second)
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Update current time every second for countdowns
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            currentTimeMillis = System.currentTimeMillis()
            
            // Check for newly locked assignments (7 seconds passed)
            debounceTimers.forEach { (vehicleId, assignedAt) ->
                val elapsedSeconds = (currentTimeMillis - assignedAt) / 1000
                if (elapsedSeconds >= DEBOUNCE_SECONDS && vehicleId !in lockedAssignments) {
                    lockedAssignments = lockedAssignments + vehicleId
                }
            }
        }
    }
    
    // Update loading state when drivers arrive
    LaunchedEffect(drivers) {
        if (drivers.isNotEmpty()) {
            kotlinx.coroutines.delay(300) // Small delay for smooth transition
            isLoadingDrivers = false
        }
    }
    
    // Calculate progress
    val assignmentProgress = assignments.size.toFloat() / vehicles.size.coerceAtLeast(1)
    val lockedCount = lockedAssignments.intersect(assignments.keys).size
    
    // Handle driver assignment with debounce
    fun handleAssignDriver(vehicleId: String, driverId: String) {
        // Start/reset debounce timer
        debounceTimers = debounceTimers + (vehicleId to System.currentTimeMillis())
        // Remove from locked if changing
        lockedAssignments = lockedAssignments - vehicleId
        // Call actual assignment
        onAssignDriver(vehicleId, driverId)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoldBlack)
    ) {
        // ============== HEADER ==============
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BoldBlack)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(DarkGray, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = PureWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ASSIGN DRIVERS",
                        color = PureWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Select driver for each truck",
                        color = MediumGray,
                        fontSize = 12.sp
                    )
                }
                
                // Progress counter
                Box(
                    modifier = Modifier
                        .background(
                            if (canSubmit) SuccessGreen else AccentYellow,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${assignments.size}/${vehicles.size}",
                        color = if (canSubmit) OnSuccess else BroadcastUiTokens.OnPrimaryCta,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            // Progress bar
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = assignmentProgress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (canSubmit) SuccessGreen else AccentYellow,
                trackColor = DarkGray
            )
            
            // Locked assignments info
            if (lockedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$lockedCount locked",
                        color = SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        
        // ============== DRIVER LIST OR SKELETON ==============
        if (isLoadingDrivers) {
            // Skeleton loading
            DriverAssignmentSkeleton()
        } else if (drivers.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    IllustratedEmptyState(
                        illustrationRes = EmptyStateArtwork.MATCHING_DRIVERS.drawableRes,
                        title = stringResource(R.string.empty_title_no_matching_drivers),
                        subtitle = stringResource(R.string.empty_subtitle_no_matching_drivers),
                        maxIllustrationWidthDp = 190,
                        maxTextWidthDp = 260,
                        showFramedIllustration = EmptyStateLayoutStyle.MODAL_COMPACT.showFramedIllustration
                    )
                }
            }
        } else {
            // Vehicle + Driver assignment list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                flingBehavior = ScrollableDefaults.flingBehavior()
            ) {
                items(vehicles, key = { "driver_assign_${it.id}" }) { vehicle ->
                    val assignedDriverId = assignments[vehicle.id]
                    val assignedDriver = assignedDriverId?.let { driversById[it] }
                    val isExpanded = expandedVehicleId == vehicle.id
                    val isLocked = vehicle.id in lockedAssignments && assignedDriverId != null
                    
                    // Calculate remaining debounce time
                    val debounceStartTime = debounceTimers[vehicle.id]
                    val remainingSeconds = if (debounceStartTime != null && !isLocked) {
                        val elapsed = (currentTimeMillis - debounceStartTime) / 1000
                        (DEBOUNCE_SECONDS - elapsed).coerceAtLeast(0)
                    } else 0L
                    
                    // Get available drivers (not already assigned to other vehicles)
                    val usedDriverIds = assignments.values.toSet() - setOfNotNull(assignedDriverId)
                    val availableForThisVehicle = drivers.filter { it.id !in usedDriverIds }
                    
                    VehicleDriverAssignmentCardEnhanced(
                        vehicle = vehicle,
                        assignedDriver = assignedDriver,
                        isExpanded = isExpanded,
                        isLocked = isLocked,
                        remainingDebounceSeconds = remainingSeconds.toInt(),
                        availableDrivers = availableForThisVehicle,
                        onToggleExpand = { 
                            if (!isLocked) {
                                expandedVehicleId = if (isExpanded) null else vehicle.id
                            }
                        },
                        onSelectDriver = { driverId ->
                            handleAssignDriver(vehicle.id, driverId)
                            expandedVehicleId = null
                        }
                    )
                }
            }
        }
        
        // ============== BOTTOM ACTION BAR ==============
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = BroadcastUiTokens.CardBackground,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Assignment summary
                if (assignments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Assigned: ${assignments.size} driver${if (assignments.size > 1) "s" else ""}",
                            color = AccentYellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (lockedCount < assignments.size) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = AccentYellow,
                                    strokeWidth = 1.5.dp
                                )
                                Text(
                                    text = "Locking...",
                                    color = MediumGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (hasInvalidAssignments) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentYellow.copy(alpha = 0.14f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "One assigned driver is on a trip. Reassign to continue.",
                            color = AccentYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Back button
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BroadcastUiTokens.SecondaryCtaText
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MediumGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "BACK",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    // Submit button
                    Button(
                        onClick = onSubmit,
                        enabled = canSubmit,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessGreen,
                            contentColor = OnSuccess,
                            disabledContainerColor = DarkGray,
                            disabledContentColor = MediumGray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SUBMIT ALL",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton loading for driver assignment screen
 */
@Composable
private fun DriverAssignmentSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkGray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Vehicle info row skeleton
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DarkSkeletonCircle(size = 40.dp)
                            Column {
                                DarkSkeletonBox(height = 15.dp, width = 90.dp)
                                Spacer(Modifier.height(4.dp))
                                DarkSkeletonBox(height = 12.dp, width = 70.dp)
                            }
                        }
                        DarkSkeletonBox(height = 36.dp, width = 100.dp, shape = RoundedCornerShape(8.dp))
                    }
                }
            }
        }
    }
}

// =============================================================================
// VEHICLE-DRIVER ASSIGNMENT CARD - Enhanced with Debounce Timer
// =============================================================================
@Composable
private fun VehicleDriverAssignmentCardEnhanced(
    vehicle: Vehicle,
    assignedDriver: Driver?,
    isExpanded: Boolean,
    isLocked: Boolean,
    remainingDebounceSeconds: Int,
    availableDrivers: List<Driver>,
    onToggleExpand: () -> Unit,
    onSelectDriver: (String) -> Unit
) {
    val showCountdown = remainingDebounceSeconds > 0 && !isLocked && assignedDriver != null
    val assignedDriverStatus = assignedDriver?.assignmentAvailability()
    val assignedDriverStatusColor = assignedDriverStatus?.color() ?: LightGray
    val assignedDriverStatusText = assignedDriverStatus?.upperLabel() ?: ""
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .then(
                if (isLocked) Modifier.border(2.dp, SuccessGreen, RoundedCornerShape(12.dp))
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) SuccessGreen.copy(alpha = 0.1f) else DarkGray
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Vehicle info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isLocked) { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Truck icon with status
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                when {
                                    isLocked -> SuccessGreen.copy(alpha = 0.2f)
                                    assignedDriver != null -> AccentYellow.copy(alpha = 0.2f)
                                    else -> BoldBlack
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when {
                                isLocked -> Icons.Default.Lock
                                else -> Icons.Default.LocalShipping
                            },
                            contentDescription = null,
                            tint = when {
                                isLocked -> SuccessGreen
                                assignedDriver != null -> AccentYellow
                                else -> MediumGray
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column {
                        Text(
                            text = vehicle.vehicleNumber,
                            color = PureWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${vehicle.category.name} - ${vehicle.subtype.capacityTons}T",
                            color = LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
                
                // Right side: Assigned driver or countdown or select button
                when {
                    isLocked && assignedDriver != null -> {
                        // Locked state - show driver with lock icon
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = assignedDriver.name,
                                    color = assignedDriverStatusColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "LOCKED • $assignedDriverStatusText",
                                    color = SuccessGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    showCountdown && assignedDriver != null -> {
                        // Countdown state - show driver with timer
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = assignedDriver.name,
                                    color = assignedDriverStatusColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                // Countdown badge
                                Box(
                                    modifier = Modifier
                                        .background(AccentYellow, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${remainingDebounceSeconds}s",
                                        color = PureWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            // Circular countdown
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = remainingDebounceSeconds.toFloat() / DEBOUNCE_SECONDS,
                                    color = AccentYellow,
                                    trackColor = DarkGray,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    assignedDriver != null -> {
                        // Assigned but editable
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = assignedDriver.name,
                                    color = assignedDriverStatusColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$assignedDriverStatusText • ⭐ ${"%.1f".format(assignedDriver.rating)}",
                                    color = LightGray,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = LightGray
                            )
                        }
                    }
                    else -> {
                        // Not assigned - show select button
                        TextButton(
                            onClick = onToggleExpand,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = AccentYellow
                            )
                        ) {
                            Text("Select Driver", fontWeight = FontWeight.SemiBold)
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            // Expandable driver list (only if not locked)
            if (isExpanded && !isLocked) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MediumGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))
                
                if (availableDrivers.isEmpty()) {
                    IllustratedEmptyState(
                        illustrationRes = EmptyStateArtwork.ASSIGNMENT_BUSY_DRIVERS.drawableRes,
                        title = stringResource(R.string.empty_title_no_drivers_left),
                        subtitle = stringResource(R.string.empty_subtitle_no_drivers_left),
                        maxIllustrationWidthDp = 150,
                        maxTextWidthDp = 240,
                        showFramedIllustration = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableDrivers.forEach { driver ->
                            DriverSelectRowEnhanced(
                                driver = driver,
                                isSelected = assignedDriver?.id == driver.id,
                                onSelect = { onSelectDriver(driver.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Enhanced driver select row with better visuals
 */
@Composable
private fun DriverSelectRowEnhanced(
    driver: Driver,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val driverStatus = driver.assignmentAvailability()
    val canAssign = driverStatus.isSelectableForAssignment()
    val statusColor = driverStatus.color()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> SuccessGreen.copy(alpha = 0.15f)
                    canAssign -> BoldBlack
                    else -> MediumGray.copy(alpha = 0.35f)
                }
            )
            .clickable(enabled = canAssign) { onSelect() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Driver avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        when {
                            isSelected -> SuccessGreen.copy(alpha = 0.3f)
                            canAssign -> MediumGray
                            else -> DarkGray
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = driver.name.take(1).uppercase(),
                    color = when {
                        isSelected -> SuccessGreen
                        canAssign -> PureWhite
                        else -> LightGray
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Column {
                Text(
                    text = driver.name,
                    color = when {
                        isSelected -> SuccessGreen
                        canAssign -> PureWhite
                        else -> LightGray
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⭐ ${"%.1f".format(driver.rating)}",
                        color = AccentYellow,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "•",
                        color = MediumGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${driver.totalTrips} trips",
                        color = LightGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "•",
                        color = MediumGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = driverStatus.upperLabel(),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!canAssign) {
                    val blockedReason = when (driverStatus) {
                        DriverAssignmentAvailability.ON_TRIP -> "On trip, cannot assign right now"
                        DriverAssignmentAvailability.OFFLINE -> "Offline, cannot assign right now"
                        DriverAssignmentAvailability.ACTIVE -> "Unavailable for assignment"
                    }
                    Text(
                        text = blockedReason,
                        color = AccentYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // Selection indicator
        if (canAssign) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (isSelected) SuccessGreen else Color.Transparent,
                        CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) SuccessGreen else MediumGray,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = PureWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(AccentYellow.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Not assignable",
                    tint = AccentYellow,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// =============================================================================
// LEGACY VEHICLE-DRIVER ASSIGNMENT CARD (Kept for compatibility)
// =============================================================================
@Composable
private fun VehicleDriverAssignmentCard(
    vehicle: Vehicle,
    assignedDriver: Driver?,
    isExpanded: Boolean,
    availableDrivers: List<Driver>,
    onToggleExpand: () -> Unit,
    onSelectDriver: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        colors = CardDefaults.cardColors(containerColor = DarkGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Vehicle info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Truck icon with status
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (assignedDriver != null) SuccessGreen.copy(alpha = 0.2f) 
                                else AccentYellow.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = if (assignedDriver != null) SuccessGreen else AccentYellow,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column {
                        Text(
                            text = vehicle.vehicleNumber,
                            color = PureWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${vehicle.category.name} - ${vehicle.subtype.capacityTons}T",
                            color = LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
                
                // Assigned driver or "Select" button
                if (assignedDriver != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = assignedDriver.name,
                                color = SuccessGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "⭐ ${"%.1f".format(assignedDriver.rating)}",
                                color = LightGray,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = LightGray
                        )
                    }
                } else {
                    TextButton(
                        onClick = onToggleExpand,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = AccentYellow
                        )
                    ) {
                        Text("Select Driver", fontWeight = FontWeight.SemiBold)
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // Expandable driver list
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MediumGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))
                
                if (availableDrivers.isEmpty()) {
                    IllustratedEmptyState(
                        illustrationRes = EmptyStateArtwork.ASSIGNMENT_BUSY_DRIVERS.drawableRes,
                        title = stringResource(R.string.empty_title_no_drivers_left),
                        subtitle = stringResource(R.string.empty_subtitle_no_drivers_left),
                        maxIllustrationWidthDp = 150,
                        maxTextWidthDp = 240,
                        showFramedIllustration = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableDrivers.forEach { driver ->
                            DriverSelectRow(
                                driver = driver,
                                isSelected = assignedDriver?.id == driver.id,
                                onSelect = { onSelectDriver(driver.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// DRIVER SELECT ROW
// =============================================================================
@Composable
private fun DriverSelectRow(
    driver: Driver,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SuccessGreen.copy(alpha = 0.15f) else BoldBlack)
            .clickable { onSelect() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Driver avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MediumGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = driver.name.take(1).uppercase(),
                    color = PureWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Column {
                Text(
                    text = driver.name,
                    color = PureWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⭐ ${"%.1f".format(driver.rating)}",
                        color = AccentYellow,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "•",
                        color = MediumGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${driver.totalTrips} trips",
                        color = LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = SuccessGreen,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// =============================================================================
// SUBMITTING CONTENT - Professional Progress UI
// =============================================================================
// 
// DESIGN:
// - Circular progress with percentage
// - Animated pulse effect
// - Clear status messages
// - Professional dark theme
// =============================================================================
@Composable
private fun SubmittingContent(
    progress: Float,
    totalCount: Int
) {
    // Pulse animation for the progress ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated progress ring with percentage
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background track
                CircularProgressIndicator(
                    progress = 1f,
                    color = DarkGray,
                    strokeWidth = 8.dp,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Progress indicator
                CircularProgressIndicator(
                    progress = progress,
                    color = AccentYellow.copy(alpha = pulseAlpha),
                    strokeWidth = 8.dp,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Percentage text in center
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = AccentYellow,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status text
            Text(
                text = "Submitting Assignments",
                color = PureWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Progress details
            Text(
                text = "${(progress * totalCount).toInt()} of $totalCount trucks assigned",
                color = LightGray,
                fontSize = 14.sp
            )
            
            // Processing indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AccentYellow,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Notifying drivers...",
                    color = MediumGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// =============================================================================
// SUCCESS CONTENT
// =============================================================================
@Composable
private fun SuccessContent(assignmentCount: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success icon with animation
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(SuccessGreen.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = SuccessGreen,
                    modifier = Modifier.size(56.dp)
                )
            }
            
            Text(
                text = "Success!",
                color = SuccessGreen,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "$assignmentCount truck${if (assignmentCount > 1) "s" else ""} assigned successfully",
                color = LightGray,
                fontSize = 16.sp
            )
            
            Text(
                text = "Drivers have been notified",
                color = MediumGray,
                fontSize = 14.sp
            )
        }
    }
}

// =============================================================================
// ERROR CONTENT
// =============================================================================
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Error icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(ErrorRed.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Error",
                    tint = ErrorRed,
                    modifier = Modifier.size(56.dp)
                )
            }
            
            Text(
                text = "Something went wrong",
                color = ErrorRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = message,
                color = LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PureWhite
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MediumGray)
                ) {
                    Text("Close")
                }
                
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentYellow,
                        contentColor = BoldBlack
                    )
                ) {
                    Text("Try Again", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =============================================================================
// CANCELLED CONTENT — Customer cancelled while transporter was mid-selection
// PRD 4.4: Full-screen dark overlay, fade-in 250ms, auto-dismiss after 1.5s
// =============================================================================
@Composable
private fun CancelledContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BoldBlack.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = "ORDER CANCELLED",
                color = ErrorRed,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Sorry, the customer cancelled this order.",
                color = LightGray,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "(closing automatically...)",
                color = MediumGray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
