package com.weelo.logistics.ui.shared

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.weelo.logistics.R
import com.weelo.logistics.data.api.TripTrackingData
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.*

/**
 * =============================================================================
 * LIVE TRACKING SCREEN — Real Google Maps + Dead Reckoning
 * =============================================================================
 *
 * Real-time GPS tracking of driver's location during trip.
 * ALL data comes from backend APIs — zero MockDataRepository references.
 *
 * DATA SOURCES:
 *   - GET /api/v1/tracking/{tripId}  → Driver's live location (polled every 5s)
 *   - GET /api/v1/trips/{tripId}      → Trip details (pickup, drop, fare)
 *
 * MAP:
 *   - GoogleMap composable via maps-compose:4.3.0
 *   - Truck marker moves every 100ms via dead reckoning interpolation
 *   - Marker rotates based on bearing
 *   - Camera auto-follows truck with smooth animation
 *
 * DEAD RECKONING:
 *   - Between 5s GPS poll intervals, truck position is predicted using:
 *     last known speed + bearing → advances position every 100ms
 *   - GPS outlier rejection prevents jumping on bad signals
 * =============================================================================
 */

// =============================================================================
// DEAD RECKONING — Position predictor between GPS polls
// =============================================================================

/**
 * Predicts truck position between GPS updates.
 * Called every 100ms for smooth 10fps animation.
 *
 * How it works:
 *   - Last known GPS: lat=12.9, lng=77.6, speed=8m/s, bearing=45°
 *   - 2 seconds have passed since last GPS
 *   - Predicted position = advance 16m from last GPS at bearing 45°
 *
 * OUTLIER REJECTION:
 *   - If new GPS point is impossibly far (>3x expected travel distance),
 *     it's likely a bad signal — ignored.
 */
class DeadReckoningCalculator {
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var speedMps = 0.0f    // meters per second from GPS
    private var bearingDeg = 0.0f  // degrees (0=north, 90=east, 180=south, 270=west)
    private var lastUpdateMs = 0L
    private var hasData = false

    /**
     * Called each time a new real GPS point arrives from the API poll.
     * Validates the point before accepting it.
     */
    fun onNewGpsPoint(lat: Double, lng: Double, speed: Float, bearing: Float) {
        if (hasData && lastUpdateMs > 0) {
            // Outlier rejection — if the new point is impossibly far away, skip it
            val results = FloatArray(1)
            Location.distanceBetween(lastLat, lastLng, lat, lng, results)
            val elapsedSec = (System.currentTimeMillis() - lastUpdateMs) / 1000.0
            val maxPossibleDistance = maxOf(speedMps * elapsedSec * 3.0, 200.0) // 3x buffer, min 200m
            if (results[0] > maxPossibleDistance) {
                Timber.w("🚛 Dead reckoning: GPS outlier rejected (${results[0].toInt()}m jump, max allowed ${maxPossibleDistance.toInt()}m)")
                return
            }
        }

        lastLat = lat
        lastLng = lng
        speedMps = speed
        bearingDeg = bearing
        lastUpdateMs = System.currentTimeMillis()
        hasData = true
    }

    /**
     * Returns the predicted current position (not just the last GPS point).
     * This is called every 100ms to animate the truck smoothly.
     *
     * Returns null if no GPS data received yet.
     */
    fun getInterpolatedPosition(): LatLng? {
        if (!hasData) return null

        // If stationary (< 0.5 m/s = slow walking), don't extrapolate
        if (speedMps < 0.5f) return LatLng(lastLat, lastLng)

        val elapsedSec = (System.currentTimeMillis() - lastUpdateMs) / 1000.0

        // Don't extrapolate more than 30s (GPS might be lost / driver stopped)
        if (elapsedSec > 30.0) return LatLng(lastLat, lastLng)

        // Spherical geometry: advance position by distance in direction of bearing
        val distanceM = speedMps * elapsedSec
        val earthRadiusM = 6_371_000.0
        val angularDistance = distanceM / earthRadiusM

        val latRad = Math.toRadians(lastLat)
        val lngRad = Math.toRadians(lastLng)
        val bearingRad = Math.toRadians(bearingDeg.toDouble())

        val newLatRad = asin(
            sin(latRad) * cos(angularDistance) +
            cos(latRad) * sin(angularDistance) * cos(bearingRad)
        )
        val newLngRad = lngRad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(newLatRad)
        )

        return LatLng(Math.toDegrees(newLatRad), Math.toDegrees(newLngRad))
    }

    /** Returns current bearing for rotating the truck marker icon */
    fun getBearing(): Float = bearingDeg

    /** Returns true once first GPS point has been received */
    fun hasLocation(): Boolean = hasData
}

// =============================================================================
// LIVE TRACKING SCREEN
// =============================================================================

@Composable
fun LiveTrackingScreen(
    tripId: String,
    @Suppress("UNUSED_PARAMETER") driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToComplete: () -> Unit
) {
    val context = LocalContext.current

    // Trip metadata
    var tripPickup by remember { mutableStateOf("") }
    var tripDrop by remember { mutableStateOf("") }
    var tripFare by remember { mutableStateOf(0.0) }
    var tripDistance by remember { mutableStateOf(0.0) }
    var tripStatus by remember { mutableStateOf("pending") }
    var isLoading by remember { mutableStateOf(true) }
    var showTripDetails by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Live tracking state (raw from API)
    var latestTrackingData by remember { mutableStateOf<TripTrackingData?>(null) }

    // Dead reckoning — smooth position updated every 100ms
    val deadReckoning = remember { DeadReckoningCalculator() }
    var smoothPosition by remember { mutableStateOf<LatLng?>(null) }
    var truckBearing by remember { mutableStateOf(0f) }

    // Google Maps camera state
    val cameraPositionState = rememberCameraPositionState()
    var hasMovedCameraOnce by remember { mutableStateOf(false) }

    // ── Fetch trip details on first load ────────────────────────────────────
    LaunchedEffect(tripId) {
        try {
            val activeResponse = RetrofitClient.tripApi.getTripDetails(
                token = RetrofitClient.getAuthHeader(),
                tripId = tripId
            )
            if (activeResponse.isSuccessful && activeResponse.body()?.success == true) {
                val tripData = activeResponse.body()?.trip
                if (tripData != null) {
                    tripPickup = tripData.pickupLocation.address
                    tripDrop = tripData.dropLocation.address
                    tripFare = tripData.fare
                    tripDistance = tripData.distance
                    tripStatus = tripData.status.name.lowercase()
                } else {
                    errorMessage = "Trip data not found"
                }
            } else {
                errorMessage = "Failed to load trip (${activeResponse.code()})"
                Timber.w("LiveTracking: API error ${activeResponse.code()}")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "LiveTracking: Failed to load trip details")
            errorMessage = e.message ?: "Network error"
        } finally {
            isLoading = false
        }
    }

    // ── Poll tracking API every 5s for real GPS ──────────────────────────────
    LaunchedEffect(tripId) {
        val terminalStatuses = setOf("completed", "cancelled", "failed")
        while (tripStatus !in terminalStatuses) {
            delay(5_000)
            if (tripStatus in terminalStatuses) break
            try {
                val response = RetrofitClient.trackingApi.getTripTracking(tripId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        latestTrackingData = data
                        tripStatus = data.status

                        // Feed new GPS point into dead reckoning
                        deadReckoning.onNewGpsPoint(
                            lat = data.latitude,
                            lng = data.longitude,
                            speed = data.speed / 3.6f,  // km/h → m/s
                            bearing = data.bearing.toFloat()
                        )

                        Timber.d("🚛 LiveTracking: GPS updated ${data.latitude}, ${data.longitude}")

                        if (data.status in terminalStatuses) {
                            Timber.i("LiveTracking: Trip ${data.status} — stopping poll")
                            break
                        }
                    }
                } else {
                    Timber.w("LiveTracking: Poll error ${response.code()}")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w("LiveTracking: Location poll failed: ${e.message}")
            }
        }
        Timber.i("LiveTracking: Poll loop exited (tripStatus=$tripStatus)")
    }

    // ── Dead reckoning animation loop — runs every 100ms ────────────────────
    // Updates truck marker 10x per second — fills the gaps between 5s GPS polls
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            val predicted = deadReckoning.getInterpolatedPosition()
            if (predicted != null) {
                smoothPosition = predicted
                truckBearing = deadReckoning.getBearing()
            }
        }
    }

    // ── Smooth camera follow — animate to truck on first lock, then follow ───
    LaunchedEffect(smoothPosition) {
        val pos = smoothPosition ?: return@LaunchedEffect
        if (!hasMovedCameraOnce) {
            // First fix: jump camera directly to truck
            cameraPositionState.position = CameraPosition.fromLatLngZoom(pos, 16f)
            hasMovedCameraOnce = true
        } else {
            // Subsequent: smooth pan (don't change zoom)
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(pos),
                durationMs = 800
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════════════════════
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            errorMessage != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage ?: "Error", color = TextSecondary)
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onNavigateBack) { Text("Go Back") }
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ── Google Map ───────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(
                                isMyLocationEnabled = false, // We show truck marker instead
                                mapType = MapType.NORMAL
                            ),
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                myLocationButtonEnabled = false,
                                compassEnabled = true
                            )
                        ) {
                            // Truck marker — smoothly animated via dead reckoning
                            smoothPosition?.let { pos ->
                                Marker(
                                    state = MarkerState(position = pos),
                                    title = "Driver",
                                    rotation = truckBearing,
                                    flat = true,          // Flat=true rotates with bearing correctly
                                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                    icon = BitmapDescriptorFactory.defaultMarker(
                                        BitmapDescriptorFactory.HUE_BLUE
                                    )
                                )
                            }
                        }

                        // LIVE badge (top-right corner)
                        if (deadReckoning.hasLocation()) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = Success,
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(White)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "LIVE",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = White
                                    )
                                }
                            }
                        } else {
                            // Waiting for first GPS fix
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFFF9800),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.GpsNotFixed,
                                        null,
                                        tint = White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Locating...",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = White
                                    )
                                }
                            }
                        }

                        // Recenter button (bottom-left of map)
                        SmallFloatingActionButton(
                            onClick = {
                                smoothPosition?.let { pos ->
                                    cameraPositionState.position =
                                        CameraPosition.fromLatLngZoom(pos, 16f)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                            containerColor = White
                        ) {
                            Icon(Icons.Default.MyLocation, null, tint = Primary)
                        }
                    }

                    // ── Bottom Sheet with Trip Info ──────────────────────────
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 8.dp,
                        color = White,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            // Drag Handle
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(TextDisabled)
                                    .align(Alignment.CenterHorizontally)
                            )

                            Spacer(Modifier.height(16.dp))

                            // Trip Status Row
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
                                        when {
                                            tripStatus == "completed" -> Icons.Default.CheckCircle
                                            tripStatus in listOf("in_transit", "heading_to_pickup") ->
                                                Icons.Default.LocalShipping
                                            else -> Icons.Default.Schedule
                                        },
                                        null, tint = Primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        when (tripStatus) {
                                            "in_transit" -> "Trip In Progress"
                                            "completed" -> "Trip Completed"
                                            "heading_to_pickup" -> "Heading to Pickup"
                                            "at_pickup" -> "At Pickup Point"
                                            "loading_complete" -> "Loading Complete"
                                            "arrived_at_drop" -> "Arrived at Drop"
                                            else -> "Trip Active"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    latestTrackingData?.let {
                                        Text(
                                            "${it.speed.toInt()} km/h • Tracking live",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                IconButton(onClick = { showTripDetails = !showTripDetails }) {
                                    Icon(
                                        if (showTripDetails) Icons.Default.ExpandLess
                                        else Icons.Default.ExpandMore,
                                        null, tint = Primary
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Route Info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("FROM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(
                                        tripPickup.take(30),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    Icons.Default.ArrowForward, null,
                                    modifier = Modifier.padding(top = 12.dp),
                                    tint = Primary
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text("TO", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(
                                        tripDrop.take(30),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Expanded Details
                            if (showTripDetails) {
                                Spacer(Modifier.height(16.dp))
                                Divider()
                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TrackingInfoItem(
                                        icon = Icons.Default.Route,
                                        label = stringResource(R.string.distance),
                                        value = stringResource(R.string.distance_km_format, tripDistance)
                                    )
                                    TrackingInfoItem(
                                        icon = Icons.Default.Speed,
                                        label = stringResource(R.string.speed),
                                        value = stringResource(R.string.speed_kmh_format, latestTrackingData?.speed?.toInt() ?: 0)
                                    )
                                    TrackingInfoItem(
                                        icon = Icons.Default.AttachMoney,
                                        label = stringResource(R.string.fare),
                                        value = stringResource(R.string.fare_amount_format, tripFare)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ArrowBack, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Back")
                                }

                                if (tripStatus in listOf("in_transit", "arrived_at_drop")) {
                                    Button(
                                        onClick = onNavigateToComplete,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Success)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Complete Trip")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tracking Info Item - Small metric display
 */
@Composable
fun TrackingInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = Primary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
