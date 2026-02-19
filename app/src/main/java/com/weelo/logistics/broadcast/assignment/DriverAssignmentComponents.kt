package com.weelo.logistics.broadcast.assignment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.data.model.Driver
import com.weelo.logistics.data.model.Vehicle
import com.weelo.logistics.ui.components.DriverAssignmentSkeleton

private val AccentYellow = Color(0xFFFDD835)
private val BoldBlack = Color(0xFF1A1A1A)
private val DarkGray = Color(0xFF2D2D2D)
private val MediumGray = Color(0xFF424242)
private val LightGray = Color(0xFFE0E0E0)
private val PureWhite = Color(0xFFFFFFFF)
private val SuccessGreen = Color(0xFF2E7D32)
private val ErrorRed = Color(0xFFD32F2F)

@Composable
fun BroadcastDriverAssignmentContent(
    vehicles: List<Vehicle>,
    driverState: DriverAssignmentUiState,
    assignments: Map<String, String>,
    submissionResults: Map<String, AssignmentSubmissionResult>,
    hasInvalidAssignments: Boolean,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onAssignDriver: (vehicleId: String, driverId: String) -> Unit,
    onRetryLoadDrivers: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    var expandedVehicleId by remember { mutableStateOf<String?>(null) }
    val allDrivers = when (driverState) {
        is DriverAssignmentUiState.Ready -> driverState.allDrivers
        is DriverAssignmentUiState.Empty -> driverState.allDrivers
        else -> emptyList()
    }
    val allDriversById = remember(allDrivers) { allDrivers.associateBy { it.id } }
    val assignmentProgress = assignments.size.toFloat() / vehicles.size.coerceAtLeast(1)
    val statusSummary = when (driverState) {
        is DriverAssignmentUiState.Ready -> driverState.summary
        is DriverAssignmentUiState.Empty -> driverState.summary
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoldBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(DarkGray, CircleShape)
                        .clickable(enabled = !isSubmitting) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = PureWhite
                    )
                }
                Text(
                    text = "ASSIGN DRIVERS",
                    color = PureWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
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
                        color = if (canSubmit) PureWhite else BoldBlack,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = assignmentProgress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = if (canSubmit) SuccessGreen else AccentYellow,
                trackColor = DarkGray
            )

            statusSummary?.let { summary ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Active ${summary.active} • Offline ${summary.offline} (hidden) • On Trip ${summary.onTrip}",
                    color = LightGray,
                    fontSize = 12.sp
                )
            }
        }

        when (driverState) {
            DriverAssignmentUiState.Loading -> {
                DriverAssignmentSkeleton()
            }

            is DriverAssignmentUiState.Error -> {
                BlockedStateCard(
                    title = "Could not load drivers",
                    message = driverState.message,
                    retryable = driverState.retryable,
                    onRetry = onRetryLoadDrivers
                )
            }

            is DriverAssignmentUiState.Empty -> {
                BlockedStateCard(
                    title = "No assignable drivers",
                    message = driverState.message,
                    retryable = true,
                    onRetry = onRetryLoadDrivers
                )
            }

            is DriverAssignmentUiState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(vehicles, key = { "driver_assign_${it.id}" }) { vehicle ->
                        val assignedDriverId = assignments[vehicle.id]
                        val assignedDriver = assignedDriverId?.let { allDriversById[it] }
                        val usedDriverIds = assignments.values.toSet() - setOfNotNull(assignedDriverId)
                        val availableDrivers = driverState.visibleDrivers.filter { it.id !in usedDriverIds }
                        val isExpanded = expandedVehicleId == vehicle.id
                        val submissionResult = submissionResults[vehicle.id]
                        val isLocked = submissionResult is AssignmentSubmissionResult.Success
                        val invalidSelection = assignedDriver?.let { !DriverSelectionPolicy.isSelectable(it) } == true

                        BroadcastVehicleDriverAssignmentCard(
                            vehicle = vehicle,
                            assignedDriver = assignedDriver,
                            availableDrivers = availableDrivers,
                            isExpanded = isExpanded,
                            isLocked = isLocked,
                            invalidSelection = invalidSelection,
                            isSubmitting = isSubmitting,
                            submissionResult = submissionResult,
                            onToggleExpand = {
                                if (!isLocked && !isSubmitting) {
                                    expandedVehicleId = if (isExpanded) null else vehicle.id
                                }
                            },
                            onSelectDriver = { driverId ->
                                onAssignDriver(vehicle.id, driverId)
                                expandedVehicleId = null
                            }
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = BoldBlack
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (hasInvalidAssignments) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "One assigned driver is no longer assignable. Reassign to continue.",
                            color = ErrorRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MediumGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("BACK", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }

                    Button(
                        onClick = onSubmit,
                        enabled = canSubmit && !isSubmitting,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessGreen,
                            contentColor = PureWhite,
                            disabledContainerColor = DarkGray,
                            disabledContentColor = MediumGray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isSubmitting) "SUBMITTING..." else "SUBMIT",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastVehicleDriverAssignmentCard(
    vehicle: Vehicle,
    assignedDriver: Driver?,
    availableDrivers: List<Driver>,
    isExpanded: Boolean,
    isLocked: Boolean,
    invalidSelection: Boolean,
    isSubmitting: Boolean,
    submissionResult: AssignmentSubmissionResult?,
    onToggleExpand: () -> Unit,
    onSelectDriver: (String) -> Unit
) {
    val status = assignedDriver?.let { DriverSelectionPolicy.classify(it) }
    val statusColor = when (status) {
        DriverCandidateStatus.ACTIVE -> SuccessGreen
        DriverCandidateStatus.ON_TRIP -> AccentYellow
        DriverCandidateStatus.OFFLINE -> LightGray
        null -> LightGray
    }
    val statusLabel = when (status) {
        DriverCandidateStatus.ACTIVE -> "ACTIVE"
        DriverCandidateStatus.ON_TRIP -> "ON TRIP"
        DriverCandidateStatus.OFFLINE -> "OFFLINE"
        null -> ""
    }
    val containerColor = when (submissionResult) {
        is AssignmentSubmissionResult.Success -> SuccessGreen.copy(alpha = 0.12f)
        is AssignmentSubmissionResult.Failed -> ErrorRed.copy(alpha = 0.12f)
        null -> DarkGray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isLocked && !isSubmitting) { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(BoldBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = AccentYellow
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

                when {
                    assignedDriver != null -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = assignedDriver.name,
                                    color = statusColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = LightGray
                            )
                        }
                    }

                    else -> {
                        TextButton(
                            onClick = onToggleExpand,
                            enabled = !isSubmitting
                        ) {
                            Text("Select Driver", color = AccentYellow, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            when (submissionResult) {
                is AssignmentSubmissionResult.Success -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Assignment sent",
                            color = SuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                is AssignmentSubmissionResult.Failed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = submissionResult.message,
                            color = ErrorRed,
                            fontSize = 12.sp
                        )
                    }
                }

                null -> Unit
            }

            if (invalidSelection) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Assigned driver is no longer eligible. Reassign this truck.",
                    color = ErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isExpanded && !isLocked) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = MediumGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                if (availableDrivers.isEmpty()) {
                    Text(
                        text = "No drivers available for this truck",
                        color = LightGray,
                        fontSize = 12.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableDrivers.forEach { driver ->
                            BroadcastDriverSelectRow(
                                driver = driver,
                                isSelected = assignedDriver?.id == driver.id,
                                isSubmitting = isSubmitting,
                                onSelect = { onSelectDriver(driver.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastDriverSelectRow(
    driver: Driver,
    isSelected: Boolean,
    isSubmitting: Boolean,
    onSelect: () -> Unit
) {
    val status = DriverSelectionPolicy.classify(driver)
    val isSelectable = DriverSelectionPolicy.isSelectable(driver)
    val statusColor = when (status) {
        DriverCandidateStatus.ACTIVE -> SuccessGreen
        DriverCandidateStatus.ON_TRIP -> AccentYellow
        DriverCandidateStatus.OFFLINE -> LightGray
    }
    val statusLabel = when (status) {
        DriverCandidateStatus.ACTIVE -> "ACTIVE"
        DriverCandidateStatus.ON_TRIP -> "ON TRIP"
        DriverCandidateStatus.OFFLINE -> "OFFLINE"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isSelected -> SuccessGreen.copy(alpha = 0.18f)
                    isSelectable -> BoldBlack
                    else -> MediumGray.copy(alpha = 0.35f)
                },
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = isSelectable && !isSubmitting) { onSelect() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MediumGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = PureWhite
                )
            }
            Column {
                Text(
                    text = driver.name,
                    color = if (isSelectable) PureWhite else LightGray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "⭐ ${"%.1f".format(driver.rating)} • ${driver.totalTrips} trips",
                    color = LightGray,
                    fontSize = 11.sp
                )
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!isSelectable) {
                    val blockedReason = when (status) {
                        DriverCandidateStatus.ON_TRIP -> "On trip, cannot assign right now"
                        DriverCandidateStatus.OFFLINE -> "Offline, cannot assign right now"
                        DriverCandidateStatus.ACTIVE -> "Unavailable for assignment"
                    }
                    Text(
                        text = blockedReason,
                        color = AccentYellow,
                        fontSize = 10.sp
                    )
                }
            }
        }

        if (isSelectable) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        width = 2.dp,
                        color = if (isSelected) SuccessGreen else MediumGray,
                        shape = CircleShape
                    )
                    .background(
                        if (isSelected) SuccessGreen else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = AccentYellow
            )
        }
    }
}

@Composable
private fun BlockedStateCard(
    title: String,
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkGray),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = LightGray,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
                if (retryable) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentYellow, contentColor = BoldBlack)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        }
    }
}
