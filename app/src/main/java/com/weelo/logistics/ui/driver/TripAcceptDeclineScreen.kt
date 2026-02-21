package com.weelo.logistics.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.weelo.logistics.data.model.*
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.repository.AssignmentRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.ClickDebouncer
import com.weelo.logistics.utils.DataSanitizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TRIP ACCEPT/DECLINE SCREEN
 * ===========================
 * Full trip details screen where driver decides to accept or decline.
 * 
 * FLOW:
 * 1. Driver clicks on notification → Opens this screen
 * 2. Shows complete trip information
 * 3. Driver clicks "Accept" or "Decline"
 * 4. If Accept → Trip starts, location tracking begins
 * 5. If Decline → Notification sent to transporter for reassignment
 * 
 * FOR BACKEND DEVELOPER:
 * - Fetch full notification/assignment details
 * - On Accept: Update assignment status to DRIVER_ACCEPTED
 *             Update driver status to ON_TRIP
 *             Start GPS location tracking
 *             Notify transporter
 * - On Decline: Update assignment status to DRIVER_DECLINED
 *              Create TripReassignment record
 *              Notify transporter
 *              Allow driver to optionally provide decline reason
 */
@Composable
fun TripAcceptDeclineScreen(
    notificationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTracking: (String) -> Unit  // assignmentId
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repository = remember { AssignmentRepository.getInstance(context) }
    val clickDebouncer = remember { ClickDebouncer(500L) }
    
    var notification by remember { mutableStateOf<DriverTripNotification?>(null) }
    var assignmentDetails by remember { mutableStateOf<TripAssignment?>(null) }
    var currentTripId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var showDeclineDialog by remember { mutableStateOf(false) }
    var showAcceptDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // =========================================================================
    // ORDER CANCELLATION — Hybrid banner + action + auto-dismiss (2s)
    // =========================================================================
    var showCancelledBanner by remember { mutableStateOf(false) }
    var cancellationActionTaken by remember { mutableStateOf(false) }
    var cancellationAutoDismissToken by remember { mutableStateOf(0) }
    var cancelReason by remember { mutableStateOf("") }
    var cancelCustomerName by remember { mutableStateOf("") }
    var cancelCustomerPhone by remember { mutableStateOf("") }
    var cancelPickupAddress by remember { mutableStateOf("") }
    var cancelDropAddress by remember { mutableStateOf("") }
    var pendingCancellation by remember {
        mutableStateOf<com.weelo.logistics.data.remote.OrderCancelledNotification?>(null)
    }
    var lastCancellationKey by remember { mutableStateOf("") }

    fun cancellationKey(notification: com.weelo.logistics.data.remote.OrderCancelledNotification): String {
        return "${notification.orderId}:${notification.tripId}:${notification.cancelledAt}:${notification.reason}"
    }

    fun matchesCurrentTrip(notification: com.weelo.logistics.data.remote.OrderCancelledNotification): Boolean {
        val currentOrderId = assignmentDetails?.broadcastId.orEmpty()
        val orderMatches = currentOrderId.isNotBlank() && notification.orderId == currentOrderId
        val tripMatches = currentTripId.isNotBlank() &&
            notification.tripId.isNotBlank() &&
            notification.tripId == currentTripId
        return orderMatches || tripMatches
    }

    fun applyCancellation(notification: com.weelo.logistics.data.remote.OrderCancelledNotification) {
        val eventKey = cancellationKey(notification)
        if (eventKey == lastCancellationKey || showCancelledBanner) return
        lastCancellationKey = eventKey

        cancelReason = notification.reason
        cancelCustomerName = notification.customerName
        cancelCustomerPhone = notification.customerPhone
        cancelPickupAddress = notification.pickupAddress
        cancelDropAddress = notification.dropAddress
        isProcessing = false
        showDeclineDialog = false
        showAcceptDialog = false
        showSuccessDialog = false
        showCancelledBanner = true
        cancellationActionTaken = false
        cancellationAutoDismissToken += 1
        pendingCancellation = null
        timber.log.Timber.w("🚫 Order cancelled while on accept/decline screen: ${notification.reason}")
    }

    LaunchedEffect(Unit) {
        SocketIOService.orderCancelled.collect { notification ->
            if (matchesCurrentTrip(notification)) {
                applyCancellation(notification)
            } else {
                val hasResolvedContext = assignmentDetails != null
                if (!hasResolvedContext) {
                    // Details not loaded yet; hold event and resolve once assignment details arrive.
                    pendingCancellation = notification
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        SocketIOService.tripCancelled.collect { notification ->
            val compatibilityNotification = com.weelo.logistics.data.remote.OrderCancelledNotification(
                orderId = notification.orderId,
                tripId = notification.tripId,
                reason = notification.reason,
                message = notification.message,
                cancelledAt = notification.cancelledAt,
                customerName = notification.customerName,
                customerPhone = notification.customerPhone,
                pickupAddress = notification.pickupAddress,
                dropAddress = notification.dropAddress
            )

            if (matchesCurrentTrip(compatibilityNotification)) {
                applyCancellation(compatibilityNotification)
            } else {
                val hasResolvedContext = assignmentDetails != null
                if (!hasResolvedContext) {
                    pendingCancellation = compatibilityNotification
                }
            }
        }
    }

    LaunchedEffect(assignmentDetails?.broadcastId, currentTripId, pendingCancellation, isLoading) {
        val pending = pendingCancellation ?: return@LaunchedEffect
        val hasResolvedContext = assignmentDetails != null
        if (!hasResolvedContext) return@LaunchedEffect

        if (matchesCurrentTrip(pending)) {
            applyCancellation(pending)
        } else if (!isLoading) {
            // Drop stale replay event once details are known and do not match.
            pendingCancellation = null
        }
    }

    LaunchedEffect(cancellationAutoDismissToken, showCancelledBanner) {
        if (!showCancelledBanner) return@LaunchedEffect
        delay(2_000L)
        if (showCancelledBanner && !cancellationActionTaken) {
            cancellationActionTaken = true
            showCancelledBanner = false
            onNavigateBack()
        }
    }
    
    // Fetch assignment details from backend
    LaunchedEffect(notificationId) {
        scope.launch {
            when (val result = repository.getAssignmentById(notificationId)) {
                is BroadcastResult.Success -> {
                    notification = result.data.notification
                    assignmentDetails = result.data.assignment
                    currentTripId = result.data.tripId
                    isLoading = false
                }
                is BroadcastResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                is BroadcastResult.Loading -> { /* Already showing loading */ }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        // Top Bar
        PrimaryTopBar(
            title = "Trip Details",
            onBackClick = onNavigateBack
        )

        androidx.compose.animation.AnimatedVisibility(visible = showCancelledBanner) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color(0xFFFFEBEE)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Order cancelled by customer",
                        color = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold
                    )
                    if (cancelReason.isNotBlank()) {
                        Text(
                            "Reason: $cancelReason",
                            color = androidx.compose.ui.graphics.Color(0xFF424242),
                            fontSize = 13.sp
                        )
                    }
                    if (cancelCustomerName.isNotBlank()) {
                        Text(
                            "Customer: $cancelCustomerName",
                            color = androidx.compose.ui.graphics.Color(0xFF424242),
                            fontSize = 13.sp
                        )
                    }
                    if (cancelPickupAddress.isNotBlank()) {
                        Text(
                            "Pickup: $cancelPickupAddress",
                            color = androidx.compose.ui.graphics.Color(0xFF616161),
                            fontSize = 12.sp
                        )
                    }
                    if (cancelDropAddress.isNotBlank()) {
                        Text(
                            "Drop: $cancelDropAddress",
                            color = androidx.compose.ui.graphics.Color(0xFF616161),
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                cancellationActionTaken = true
                                showCancelledBanner = false
                                onNavigateBack()
                            }
                        ) {
                            Text("Go to Dashboard")
                        }

                        if (cancelCustomerPhone.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    cancellationActionTaken = true
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                        data = android.net.Uri.parse("tel:$cancelCustomerPhone")
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Call Customer", color = androidx.compose.ui.graphics.Color(0xFF1976D2))
                            }
                        }
                    }
                }
            }
        }
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            notification?.let { notif ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Urgent Banner (if applicable)
                    if (notif.expiryTime != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Error
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    null,
                                    tint = White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Respond within ${getRemainingTime(notif.expiryTime)} minutes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                            }
                        }
                    }
                    
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Earnings Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(Success.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Trip Earnings",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "₹${String.format("%.0f", notif.fare)}",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Success
                                )
                            }
                        }
                        
                        // Route Information Card
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "Trip Route",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(Modifier.height(16.dp))
                                
                                // Pickup Location
                                LocationDetailRow(
                                    icon = Icons.Default.TripOrigin,
                                    iconColor = Success,
                                    label = "PICKUP LOCATION",
                                    address = notif.pickupAddress
                                )
                                
                                // Route Line
                                Box(
                                    modifier = Modifier
                                        .padding(start = 20.dp)
                                        .width(2.dp)
                                        .height(40.dp)
                                        .background(TextDisabled)
                                )
                                
                                // Drop Location
                                LocationDetailRow(
                                    icon = Icons.Default.LocationOn,
                                    iconColor = Error,
                                    label = "DROP LOCATION",
                                    address = notif.dropAddress
                                )
                            }
                        }
                        
                        // Trip Details Card
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "Trip Information",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TripDetailColumn(
                                        icon = Icons.Default.Route,
                                        label = "Distance",
                                        value = "${notif.distance} km",
                                        iconColor = Primary
                                    )
                                    TripDetailColumn(
                                        icon = Icons.Default.Schedule,
                                        label = "Duration",
                                        value = "${notif.estimatedDuration} min",
                                        iconColor = Info
                                    )
                                    TripDetailColumn(
                                        icon = Icons.Default.Inventory,
                                        label = "Goods Type",
                                        value = notif.goodsType,
                                        iconColor = Warning
                                    )
                                }
                            }
                        }
                        
                        // Vehicle Assignment Card
                        assignmentDetails?.assignments?.firstOrNull()?.let { assignment ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        "Assigned Vehicle",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Spacer(Modifier.height(16.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(Primary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.LocalShipping,
                                                null,
                                                modifier = Modifier.size(32.dp),
                                                tint = Primary
                                            )
                                        }
                                        
                                        Spacer(Modifier.width(16.dp))
                                        
                                        Column {
                                            Text(
                                                assignment.vehicleNumber,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Your assigned vehicle",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Important Notes
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(InfoLight)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Default.Info,
                                        null,
                                        tint = Info,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Important Information",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "• Your location will be tracked during the trip\n" +
                                            "• Contact customer on arrival\n" +
                                            "• Ensure safe delivery of goods\n" +
                                            "• Mark trip complete after delivery",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Bottom Action Buttons
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Decline Button
                        OutlinedButton(
                            onClick = { 
                                if (!clickDebouncer.canClick()) return@OutlinedButton
                                showDeclineDialog = true 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            enabled = !isProcessing && !showCancelledBanner,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Error
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Error)
                            )
                        ) {
                            Icon(Icons.Default.Close, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Decline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Accept Button
                        Button(
                            onClick = { 
                                if (!clickDebouncer.canClick()) return@Button
                                showAcceptDialog = true 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            enabled = !isProcessing && !showCancelledBanner,
                            colors = ButtonDefaults.buttonColors(containerColor = Success)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = White
                                )
                            } else {
                                Icon(Icons.Default.CheckCircle, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Accept Trip",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Accept Confirmation Dialog
    if (showAcceptDialog && notification != null) {
        AlertDialog(
            onDismissRequest = { showAcceptDialog = false },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(48.dp)) },
            title = { 
                Text(
                    "Accept Trip?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "You are accepting this trip for ₹${String.format("%.0f", notification!!.fare)}",
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your location will be tracked during the trip",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAcceptDialog = false
                        isProcessing = true
                        scope.launch {
                            when (val result = repository.acceptAssignment(notificationId)) {
                                is BroadcastResult.Success -> {
                                    isProcessing = false
                                    showSuccessDialog = true
                                }
                                is BroadcastResult.Error -> {
                                    isProcessing = false
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                                is BroadcastResult.Loading -> { /* Still processing */ }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Success)
                ) {
                    Text("Confirm Accept")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAcceptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Decline Dialog
    if (showDeclineDialog) {
        var declineReason by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showDeclineDialog = false },
            icon = { Icon(Icons.Default.Cancel, null, tint = Error) },
            title = { Text("Decline Trip?") },
            text = {
                Column {
                    Text("Please provide a reason (optional):")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = declineReason,
                        onValueChange = { 
                            declineReason = DataSanitizer.sanitizeForApi(it) ?: ""
                        },
                        placeholder = { Text("e.g., Not available, Too far...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeclineDialog = false
                        isProcessing = true
                        scope.launch {
                            // Provide default reason if empty
                            val reason = declineReason.ifBlank { "Not available" }
                            when (val result = repository.declineAssignment(notificationId, reason)) {
                                is BroadcastResult.Success -> {
                                    isProcessing = false
                                    Toast.makeText(context, "Trip declined", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                                is BroadcastResult.Error -> {
                                    isProcessing = false
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                                is BroadcastResult.Loading -> { /* Still processing */ }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Error)
                ) {
                    Text("Confirm Decline")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeclineDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = { 
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    modifier = Modifier.size(72.dp),
                    tint = Success
                )
            },
            title = { 
                Text(
                    "Trip Accepted!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Your transporter has been notified. Start your trip when ready.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { onNavigateToTracking(assignmentDetails?.assignmentId ?: "") },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Start Trip")
                }
            }
        )
    }
}

/**
 * Location Detail Row Component
 */
@Composable
fun LocationDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    label: String,
    address: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                address,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Trip Detail Column Component
 */
@Composable
fun TripDetailColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconColor: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(32.dp),
            tint = iconColor
        )
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Get remaining time in minutes
 */
fun getRemainingTime(expiryTime: Long): Long {
    val diff = expiryTime - System.currentTimeMillis()
    return (diff / 60000).coerceAtLeast(0)
}
