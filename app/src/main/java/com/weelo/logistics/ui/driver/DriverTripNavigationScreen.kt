package com.weelo.logistics.ui.driver

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.weelo.logistics.data.api.TripTrackingData
import com.weelo.logistics.data.model.TripStatus
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.GPSTrackingService
import kotlinx.coroutines.launch

/**
 * Driver Trip Navigation Screen — PRD Phase 3
 * 
 * Shows live trip tracking with Google Map, navigation, and status updates.
 * 
 * DATA FLOW:
 * ──────────
 * TripAcceptDeclineScreen → (accept) → navigates here with assignment data
 * 
 * The assignment data (pickup, drop, customer, fare) is passed as parameters
 * from the navigation graph. This avoids an extra API call — the data was
 * already fetched when the driver was viewing the Accept/Decline screen.
 * 
 * The tracking data (driver's current location) is fetched from
 * GET /tracking/:tripId and refreshed via WebSocket updates.
 * 
 * @param tripId         Trip ID for tracking
 * @param assignmentId   Assignment ID (for future reference)
 * @param pickupAddr     Pickup address text
 * @param dropAddr       Drop address text
 * @param pickupLat      Pickup latitude
 * @param pickupLng      Pickup longitude
 * @param dropLat        Drop latitude
 * @param dropLng        Drop longitude
 * @param custName       Customer name for display
 * @param custPhone      Customer phone for calling
 * @param tripDistance    Trip distance in km
 * @param tripFare       Trip fare in ₹
 * @param tripGoodsType  Type of goods being transported
 * @param driverId       Driver ID (for GPS tracking service)
 */
@Composable
fun DriverTripNavigationScreen(
    tripId: String,
    @Suppress("UNUSED_PARAMETER") assignmentId: String = "",
    pickupAddr: String = "",
    dropAddr: String = "",
    pickupLat: Double = 0.0,
    pickupLng: Double = 0.0,
    dropLat: Double = 0.0,
    dropLng: Double = 0.0,
    custName: String = "",
    custPhone: String = "",
    tripDistance: Double = 0.0,
    tripFare: Double = 0.0,
    tripGoodsType: String = "",
    driverId: String = "",
    onNavigateBack: () -> Unit,
    onTripCompleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val trackingApi = remember { RetrofitClient.trackingApi }
    
    // Trip tracking data from backend (GET /tracking/:tripId) — driver's live location
    var trackingData by remember { mutableStateOf<TripTrackingData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCompleteDialog by remember { mutableStateOf(false) }
    var tripStatus by remember { mutableStateOf(TripStatus.ACCEPTED) }
    
    // Assignment data — passed from TripAcceptDeclineScreen via navigation args
    // These are displayed in the trip info card (addresses, fare, customer info)
    val pickupAddress = pickupAddr.ifBlank { "Pickup location" }
    val dropAddress = dropAddr.ifBlank { "Drop location" }
    val pickupLatLng = remember { LatLng(pickupLat, pickupLng) }
    val dropLatLng = remember { LatLng(dropLat, dropLng) }
    val customerName = custName.ifBlank { "Customer" }
    val customerPhone = custPhone
    val distance = tripDistance
    val fare = tripFare
    val goodsType = tripGoodsType
    
    // =========================================================================
    // ORDER CANCELLATION — If active trip is cancelled, show dialog → navigate back
    // =========================================================================
    var showTripCancelledDialog by remember { mutableStateOf(false) }
    var tripCancelReason by remember { mutableStateOf("") }
    var tripCancelCustomerName by remember { mutableStateOf("") }
    var tripCancelCustomerPhone by remember { mutableStateOf("") }
    var tripCancelPickupAddress by remember { mutableStateOf("") }
    var tripCancelDropAddress by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        SocketIOService.orderCancelled.collect { notification ->
            tripCancelReason = notification.reason
            tripCancelCustomerName = notification.customerName
            tripCancelCustomerPhone = notification.customerPhone
            tripCancelPickupAddress = notification.pickupAddress
            tripCancelDropAddress = notification.dropAddress
            showTripCancelledDialog = true
            timber.log.Timber.w("🚫 Active trip cancelled by customer: ${notification.orderId} — ${notification.reason}")
            
            // Stop GPS tracking immediately
            try {
                GPSTrackingService.stopTracking(context)
            } catch (_: Exception) {}
        }
    }
    
    if (showTripCancelledDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            title = { androidx.compose.material3.Text("🚫 Trip Cancelled", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFD32F2F)) },
            text = { 
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text("Customer has cancelled this trip.", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                    androidx.compose.material3.Text("Reason: $tripCancelReason")
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                    androidx.compose.material3.Text("Your vehicle has been released.", color = androidx.compose.ui.graphics.Color.Gray)
                    if (tripCancelCustomerName.isNotBlank()) {
                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                        androidx.compose.material3.Text("Customer: $tripCancelCustomerName", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                    if (tripCancelCustomerPhone.isNotBlank()) {
                        androidx.compose.material3.Text("📞 $tripCancelCustomerPhone", color = androidx.compose.ui.graphics.Color(0xFF1976D2))
                    }
                    if (tripCancelPickupAddress.isNotBlank()) {
                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                        androidx.compose.material3.Text("📍 $tripCancelPickupAddress", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Gray)
                    }
                    if (tripCancelDropAddress.isNotBlank()) {
                        androidx.compose.material3.Text("📌 $tripCancelDropAddress", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Gray)
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { 
                    showTripCancelledDialog = false
                    onNavigateBack()
                }) {
                    androidx.compose.material3.Text("Go to Dashboard")
                }
            },
            dismissButton = {
                if (tripCancelCustomerPhone.isNotBlank()) {
                    androidx.compose.material3.TextButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                            data = android.net.Uri.parse("tel:$tripCancelCustomerPhone")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }) {
                        androidx.compose.material3.Text("📞 Call Customer", color = androidx.compose.ui.graphics.Color(0xFF1976D2))
                    }
                }
            }
        )
    }
    
    // Fetch trip tracking data (driver's current location) from backend
    LaunchedEffect(tripId) {
        scope.launch {
            try {
                val response = trackingApi.getTripTracking(tripId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        trackingData = data
                        tripStatus = when (data.status.lowercase()) {
                            "heading_to_pickup" -> TripStatus.ACCEPTED
                            "at_pickup" -> TripStatus.AT_PICKUP
                            "loading_complete" -> TripStatus.LOADING_COMPLETE
                            "in_transit" -> TripStatus.IN_PROGRESS
                            "completed" -> TripStatus.COMPLETED
                            else -> TripStatus.ACCEPTED
                        }
                        isLoading = false
                        timber.log.Timber.i("✅ Trip tracking loaded: ${data.tripId} — Status: ${data.status}")
                    }
                } else {
                    // Trip tracking not initialized yet — normal for newly accepted trips
                    isLoading = false
                    timber.log.Timber.w("⚠️ No tracking data yet for trip $tripId — will start when GPS begins")
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Failed to load trip: ${e.message}"
                timber.log.Timber.e(e, "❌ Error fetching trip tracking: ${e.message}")
            }
        }
    }
    
    // Camera position — starts at pickup, auto-fits to show both markers
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(pickupLatLng, 12f)
    }
    
    // Auto-fit camera to show pickup + drop when coordinates are available
    LaunchedEffect(pickupLatLng, dropLatLng) {
        if (pickupLatLng.latitude != 0.0 && dropLatLng.latitude != 0.0) {
            try {
                val bounds = LatLngBounds.builder()
                    .include(pickupLatLng)
                    .include(dropLatLng)
                    .build()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 80),
                    durationMs = 500
                )
            } catch (e: Exception) {
                timber.log.Timber.w("Camera bounds error: ${e.message}")
            }
        }
    }
    
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ================================================================
            // REAL GOOGLE MAP — shows pickup, drop, route polyline
            // Follows exact pattern from BroadcastCardMap (BroadcastListScreen.kt)
            // ================================================================
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(mapType = MapType.NORMAL),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        mapToolbarEnabled = false,
                        compassEnabled = true,
                        myLocationButtonEnabled = false,
                        scrollGesturesEnabled = true,
                        zoomGesturesEnabled = true,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = true
                    )
                ) {
                    // Pickup marker — Green
                    if (pickupLatLng.latitude != 0.0) {
                        Marker(
                            state = MarkerState(position = pickupLatLng),
                            title = "Pickup",
                            snippet = pickupAddress,
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                        )
                    }
                    
                    // Drop marker — Red
                    if (dropLatLng.latitude != 0.0) {
                        Marker(
                            state = MarkerState(position = dropLatLng),
                            title = "Drop",
                            snippet = dropAddress,
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        )
                    }
                    
                    // Route polyline — Black line from pickup to drop
                    if (pickupLatLng.latitude != 0.0 && dropLatLng.latitude != 0.0) {
                        Polyline(
                            points = listOf(pickupLatLng, dropLatLng),
                            color = TextPrimary,
                            width = 6f
                        )
                    }
                    
                    // Driver's current location marker — Orange (if tracking active)
                    trackingData?.let { data ->
                        if (data.latitude != 0.0) {
                            Marker(
                                state = MarkerState(position = LatLng(data.latitude, data.longitude)),
                                title = "Your Location",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                                rotation = data.bearing
                            )
                        }
                    }
                }
            }
            
            // Trip Info Card — shows customer, pickup/drop, stats, action buttons
            Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        // Customer Info + Call Button
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    customerName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (customerPhone.isNotBlank()) {
                                    Text(
                                        customerPhone,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                            // Call Customer — opens phone dialer
                            if (customerPhone.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Success, CircleShape)
                                ) {
                                    Icon(Icons.Default.Call, "Call Customer", tint = White)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Divider()
                        Spacer(Modifier.height(16.dp))
                        
                        // Locations (Pickup + Drop)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = Success
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Pickup", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    Text(pickupAddress, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = Error
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Drop", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    Text(dropAddress, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Trip Stats
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBox("Distance", "${String.format("%.1f", distance)} km", Icons.Default.Timeline)
                            StatBox("Goods", goodsType.ifBlank { "General" }, Icons.Default.Category)
                            StatBox("Fare", "₹${String.format("%.0f", fare)}", Icons.Default.CurrencyRupee)
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // =============================================================
                        // ACTION BUTTONS — Full Trip Status Flow
                        // =============================================================
                        // STATUS FLOW (driver clicks buttons in order):
                        //   ACCEPTED → heading_to_pickup (auto on accept)
                        //   → "Reached Pickup" button → at_pickup
                        //   → "Loading Complete" button → loading_complete
                        //   → "Start Trip" button → in_transit (starts GPS)
                        //   → "Complete Trip" button → completed (stops GPS)
                        //
                        // Each status change:
                        //   1. Calls PUT /tracking/trip/{tripId}/status
                        //   2. Backend broadcasts to customer via WebSocket
                        //   3. Backend sends FCM push to customer (even if app closed)
                        //   4. Customer sees banner + truck card update + marker color change
                        //
                        // SCALABILITY: Single API call per status, O(1) backend processing
                        // MODULARITY: updateTripStatusOnBackend() handles all status changes
                        // =============================================================
                        
                        // Navigate button (always visible)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SecondaryButton(
                                text = "🗺 Navigate",
                                onClick = {
                                    val targetLat = if (tripStatus == TripStatus.ACCEPTED) pickupLatLng.latitude else dropLatLng.latitude
                                    val targetLng = if (tripStatus == TripStatus.ACCEPTED) pickupLatLng.longitude else dropLatLng.longitude
                                    if (targetLat != 0.0 && targetLng != 0.0) {
                                        val uri = Uri.parse("google.navigation:q=$targetLat,$targetLng&mode=d")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            setPackage("com.google.android.apps.maps")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        try {
                                            context.startActivity(mapIntent)
                                        } catch (e: Exception) {
                                            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$targetLat,$targetLng&travelmode=driving")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            })
                                        }
                                    } else {
                                        Toast.makeText(context, "Location not available yet", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Status-based action button
                            PrimaryButton(
                                text = when (tripStatus) {
                                    TripStatus.ACCEPTED -> "📍 Reached Pickup"
                                    TripStatus.AT_PICKUP -> "📦 Loading Complete"
                                    TripStatus.LOADING_COMPLETE -> "🚀 Start Trip"
                                    TripStatus.IN_PROGRESS -> "✅ Complete Trip"
                                    TripStatus.COMPLETED -> "Trip Completed ✓"
                                    else -> "Start"
                                },
                                onClick = {
                                    when (tripStatus) {
                                        TripStatus.ACCEPTED -> {
                                            // Driver reached pickup location
                                            scope.launch {
                                                updateTripStatusOnBackend(
                                                    trackingApi, tripId, "at_pickup",
                                                    onSuccess = {
                                                        tripStatus = TripStatus.AT_PICKUP
                                                        Toast.makeText(context, "📍 Marked as reached pickup!", Toast.LENGTH_SHORT).show()
                                                        timber.log.Timber.i("📍 Trip $tripId → at_pickup")
                                                    },
                                                    onError = { msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        }
                                        TripStatus.AT_PICKUP -> {
                                            // Loading complete
                                            scope.launch {
                                                updateTripStatusOnBackend(
                                                    trackingApi, tripId, "loading_complete",
                                                    onSuccess = {
                                                        tripStatus = TripStatus.LOADING_COMPLETE
                                                        Toast.makeText(context, "📦 Loading complete!", Toast.LENGTH_SHORT).show()
                                                        timber.log.Timber.i("📦 Trip $tripId → loading_complete")
                                                    },
                                                    onError = { msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        }
                                        TripStatus.LOADING_COMPLETE -> {
                                            // Start trip — begins GPS tracking
                                            scope.launch {
                                                updateTripStatusOnBackend(
                                                    trackingApi, tripId, "in_transit",
                                                    onSuccess = {
                                                        tripStatus = TripStatus.IN_PROGRESS
                                                        GPSTrackingService.startTracking(context, tripId, driverId)
                                                        Toast.makeText(context, "🚀 Trip started! GPS tracking active.", Toast.LENGTH_SHORT).show()
                                                        timber.log.Timber.i("🚀 Trip $tripId → in_transit, GPS started")
                                                    },
                                                    onError = { msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        }
                                        TripStatus.IN_PROGRESS -> {
                                            showCompleteDialog = true
                                        }
                                        else -> { /* No action for COMPLETED */ }
                                    }
                                },
                                enabled = tripStatus != TripStatus.COMPLETED,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        // Status progress indicator
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val steps = listOf("Heading", "At Pickup", "Loaded", "In Transit", "Done")
                            val currentStep = when (tripStatus) {
                                TripStatus.ACCEPTED -> 0
                                TripStatus.AT_PICKUP -> 1
                                TripStatus.LOADING_COMPLETE -> 2
                                TripStatus.IN_PROGRESS -> 3
                                TripStatus.COMPLETED -> 4
                                else -> 0
                            }
                            steps.forEachIndexed { index, label ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        if (index <= currentStep) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = if (index <= currentStep) Success else TextSecondary
                                    )
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (index <= currentStep) Success else TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Back Button Overlay
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
                .background(White, CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, "Back")
        }
        
        // Loading indicator
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }
        
        // Error message
        errorMessage?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(msg, color = Error, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Complete Trip Dialog — calls backend to mark trip as completed
        if (showCompleteDialog) {
            AlertDialog(
                onDismissRequest = { showCompleteDialog = false },
                icon = { Icon(Icons.Default.CheckCircle, null, tint = Success) },
                title = { Text("Complete Trip?") },
                text = { Text("Mark this trip as completed? Customer will be notified.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val response = trackingApi.updateTripStatus(
                                        tripId = tripId,
                                        request = com.weelo.logistics.data.api.TripStatusRequest(
                                            status = "completed"
                                        )
                                    )

                                    if (response.isSuccessful) {
                                        GPSTrackingService.stopTracking(context)
                                        tripStatus = TripStatus.COMPLETED
                                        showCompleteDialog = false
                                        Toast.makeText(context, "Trip completed! 🎉", Toast.LENGTH_SHORT).show()
                                        timber.log.Timber.i("✅ Trip completed: $tripId")
                                        onTripCompleted()
                                    } else {
                                        showCompleteDialog = false
                                        Toast.makeText(context, "Failed to complete trip", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    showCompleteDialog = false
                                    Toast.makeText(context, "Failed to complete: ${e.message}", Toast.LENGTH_LONG).show()
                                    timber.log.Timber.e(e, "❌ Complete trip failed")
                                }
                            }
                        }
                    ) {
                        Text("Complete", color = Success)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCompleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

/**
 * =============================================================================
 * UPDATE TRIP STATUS — Reusable Backend Call
 * =============================================================================
 *
 * Calls PUT /tracking/trip/{tripId}/status with the new status.
 * Backend then:
 *   1. Updates Redis tracking data
 *   2. Broadcasts ASSIGNMENT_STATUS_CHANGED via WebSocket to booking room
 *   3. Sends FCM push notification to customer (even if app closed)
 *
 * SCALABILITY: Single API call, O(1) backend processing
 * MODULARITY: All status transitions use this one function
 * EASY UNDERSTANDING: onSuccess/onError callbacks keep UI logic in composable
 *
 * @param trackingApi  Retrofit API interface
 * @param tripId       Trip to update
 * @param newStatus    One of: heading_to_pickup, at_pickup, loading_complete, in_transit, completed
 * @param onSuccess    Called on main thread after successful update
 * @param onError      Called on main thread with error message
 */
private suspend fun updateTripStatusOnBackend(
    trackingApi: com.weelo.logistics.data.api.TrackingApiService,
    tripId: String,
    newStatus: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        val response = trackingApi.updateTripStatus(
            tripId = tripId,
            request = com.weelo.logistics.data.api.TripStatusRequest(status = newStatus)
        )
        if (response.isSuccessful) {
            onSuccess()
        } else {
            onError("Failed to update status: ${response.code()}")
        }
    } catch (e: Exception) {
        timber.log.Timber.e(e, "❌ Status update failed: $tripId → $newStatus")
        onError("Failed: ${e.message}")
    }
}

@Composable
fun StatBox(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(20.dp), tint = Primary)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
