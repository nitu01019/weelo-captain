package com.weelo.logistics.ui.transporter

// =============================================================================
// DriverAssignmentParts.kt
// Extracted from DriverAssignmentScreen.kt for 800-line compliance.
//
// Contains the three composables referenced by DriverAssignmentScreen:
//   - VehicleDriverAssignmentCard   (per-truck row with assign / remove UI)
//   - DriverPickerBottomSheet       (modal bottom sheet listing online drivers)
//   - DriverSelectCard              (single driver row inside the picker)
//
// Visual treatment mirrors BroadcastAcceptanceScreen.VehicleDriverAssignmentCard
// (Material 3 Card + icons + saffron/green accents) but uses the light-theme
// palette that DriverAssignmentScreen itself uses (Primary, TextSecondary,
// Success, Warning, White, Surface).
// =============================================================================

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.Driver
import com.weelo.logistics.data.model.Vehicle
import com.weelo.logistics.ui.theme.Primary
import com.weelo.logistics.ui.theme.PrimaryLight
import com.weelo.logistics.ui.theme.Success
import com.weelo.logistics.ui.theme.SuccessLight
import com.weelo.logistics.ui.theme.TextPrimary
import com.weelo.logistics.ui.theme.TextSecondary
import com.weelo.logistics.ui.theme.Warning
import com.weelo.logistics.ui.theme.White

// ----------------------------------------------------------------------------
// VehicleDriverAssignmentCard
// ----------------------------------------------------------------------------
// Matches the call site in DriverAssignmentScreen.kt (line 518):
//   VehicleDriverAssignmentCard(
//       vehicle = vehicle,
//       assignedDriver = ...Driver?,
//       onAssignDriver = { showDriverPicker = vehicle.id },
//       onRemoveDriver = { driverAssignments = driverAssignments - vehicle.id },
//       index = index + 1
//   )
// ----------------------------------------------------------------------------
@Composable
internal fun VehicleDriverAssignmentCard(
    vehicle: Vehicle,
    assignedDriver: Driver?,
    onAssignDriver: () -> Unit,
    onRemoveDriver: () -> Unit,
    index: Int
) {
    val isAssigned = assignedDriver != null
    val accentColor = if (isAssigned) Success else Primary
    val accentBackground = if (isAssigned) SuccessLight else PrimaryLight

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: index badge + vehicle info + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Index badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(accentBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString(),
                        color = accentColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Truck icon circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accentBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vehicle.vehicleNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${vehicle.category.name} - ${vehicle.capacityText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (isAssigned) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Driver assigned",
                        tint = Success,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Driver row
            if (assignedDriver != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SuccessLight)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Driver avatar (initial)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Success.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = assignedDriver.name.take(1).uppercase(),
                            color = Success,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = assignedDriver.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = assignedDriver.mobileNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    IconButton(onClick = onRemoveDriver) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove driver",
                            tint = Warning,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onAssignDriver,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Assign driver",
                        color = Primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// DriverPickerBottomSheet
// ----------------------------------------------------------------------------
// Matches the call site in DriverAssignmentScreen.kt (line 670):
//   DriverPickerBottomSheet(
//       drivers = List<Driver>,
//       onDriverSelected = { driver -> ... },
//       onDismiss = { ... }
//   )
// ----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DriverPickerBottomSheet(
    drivers: List<Driver>,
    onDriverSelected: (Driver) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Select a driver",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${drivers.size} online and available",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))

            if (drivers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No drivers available right now",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = drivers,
                        key = { it.id }
                    ) { driver ->
                        DriverSelectCard(
                            driver = driver,
                            onClick = { onDriverSelected(driver) }
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// DriverSelectCard
// ----------------------------------------------------------------------------
// Single clickable row: avatar + name + phone + chevron-style cue.
// ----------------------------------------------------------------------------
@Composable
private fun DriverSelectCard(
    driver: Driver,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = PrimaryLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Primary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = driver.name.take(1).uppercase(),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = driver.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = driver.mobileNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (driver.rating > 0f) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "⭐ ${"%.1f".format(driver.rating)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Assign",
                tint = Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
