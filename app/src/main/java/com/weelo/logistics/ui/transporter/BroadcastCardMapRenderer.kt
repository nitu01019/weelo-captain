package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.LinkedHashMap
import kotlin.math.abs

enum class BroadcastCardMapRenderMode {
    SNAPSHOT,
    LIVE
}

data class RoutePreviewKey(
    val broadcastId: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropLat: Double,
    val dropLng: Double
)

data class RoutePreviewSnapshot(
    val pickup: Offset,
    val drop: Offset,
    val bend: Offset
)

class RouteMapSnapshotCache(
    private val maxEntries: Int = 128
) {
    private val cache = object : LinkedHashMap<RoutePreviewKey, RoutePreviewSnapshot>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RoutePreviewKey, RoutePreviewSnapshot>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    private fun getOrPut(key: RoutePreviewKey): RoutePreviewSnapshot {
        cache[key]?.let { return it }
        val created = buildSnapshot(key)
        cache[key] = created
        return created
    }

    @Composable
    fun rememberSnapshot(key: RoutePreviewKey): RoutePreviewSnapshot {
        return remember(key) { getOrPut(key) }
    }

    private fun buildSnapshot(key: RoutePreviewKey): RoutePreviewSnapshot {
        val seed = key.hashCode()
        val lngDelta = key.dropLng - key.pickupLng
        val latDelta = key.dropLat - key.pickupLat
        val horizontalShift = if (lngDelta >= 0) 0.08f else -0.08f
        val verticalShift = if (latDelta >= 0) -0.05f else 0.05f

        val pickup = Offset(
            x = 0.23f + normalized(seed shr 3, -0.06f, 0.06f),
            y = 0.28f + normalized(seed shr 7, -0.07f, 0.07f)
        )
        val drop = Offset(
            x = 0.76f + normalized(seed shr 11, -0.06f, 0.06f),
            y = 0.70f + normalized(seed shr 15, -0.07f, 0.07f)
        )

        val midpoint = Offset((pickup.x + drop.x) / 2f, (pickup.y + drop.y) / 2f)
        val bend = Offset(
            x = midpoint.x + horizontalShift + normalized(seed shr 19, -0.08f, 0.08f),
            y = midpoint.y + verticalShift + normalized(seed shr 23, -0.09f, 0.09f)
        )

        return RoutePreviewSnapshot(
            pickup = pickup,
            drop = drop,
            bend = bend
        )
    }

    private fun normalized(seed: Int, min: Float, max: Float): Float {
        val range = max - min
        val ratio = (abs(seed) % 10_000) / 10_000f
        return min + (ratio * range)
    }
}

@Composable
fun BroadcastCardMapRenderer(
    routeKey: RoutePreviewKey,
    renderMode: BroadcastCardMapRenderMode,
    snapshotCache: RouteMapSnapshotCache,
    modifier: Modifier = Modifier
) {
    val pickup = remember(routeKey.pickupLat, routeKey.pickupLng) {
        LatLng(routeKey.pickupLat, routeKey.pickupLng)
    }
    val drop = remember(routeKey.dropLat, routeKey.dropLng) {
        LatLng(routeKey.dropLat, routeKey.dropLng)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF6F7FB))
    ) {
        when {
            routeKey.pickupLat == 0.0 && routeKey.pickupLng == 0.0 && routeKey.dropLat == 0.0 && routeKey.dropLng == 0.0 -> {
                SnapshotRoutePreview(
                    routeKey = routeKey,
                    snapshotCache = snapshotCache,
                    modifier = Modifier.fillMaxSize()
                )
            }

            renderMode == BroadcastCardMapRenderMode.LIVE -> {
                LiveRoutePreview(
                    routeKey = routeKey,
                    pickup = pickup,
                    drop = drop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                SnapshotRoutePreview(
                    routeKey = routeKey,
                    snapshotCache = snapshotCache,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SnapshotRoutePreview(
    routeKey: RoutePreviewKey,
    snapshotCache: RouteMapSnapshotCache,
    modifier: Modifier = Modifier
) {
    val snapshot = snapshotCache.rememberSnapshot(routeKey)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val gridColor = Color(0xFFE8EBF2)
        val routeColor = Color(0xFFF2D22E)
        val routeStroke = 7.dp.toPx()

        for (i in 1..4) {
            val y = height * (i / 5f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        for (i in 1..5) {
            val x = width * (i / 6f)
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1.dp.toPx()
            )
        }

        val pickupOffset = Offset(width * snapshot.pickup.x, height * snapshot.pickup.y)
        val dropOffset = Offset(width * snapshot.drop.x, height * snapshot.drop.y)
        val bendOffset = Offset(width * snapshot.bend.x, height * snapshot.bend.y)

        val routePath = Path().apply {
            moveTo(pickupOffset.x, pickupOffset.y)
            quadraticBezierTo(
                bendOffset.x,
                bendOffset.y,
                dropOffset.x,
                dropOffset.y
            )
        }

        drawPath(
            path = routePath,
            color = routeColor,
            style = Stroke(width = routeStroke, cap = StrokeCap.Round)
        )

        drawCircle(
            color = Color(0xFF1EB66A),
            radius = 6.dp.toPx(),
            center = pickupOffset
        )
        drawCircle(
            color = Color(0xFFFF5B5B),
            radius = 6.dp.toPx(),
            center = dropOffset
        )
    }
}

@Composable
private fun LiveRoutePreview(
    routeKey: RoutePreviewKey,
    pickup: LatLng,
    drop: LatLng,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(routeKey) {
        val bounds = LatLngBounds.builder()
            .include(pickup)
            .include(drop)
            .build()
        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 52))
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.NORMAL),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
            myLocationButtonEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false
        )
    ) {
        Marker(
            state = MarkerState(position = pickup),
            title = "Pickup",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        )
        Marker(
            state = MarkerState(position = drop),
            title = "Drop",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        )
        Polyline(
            points = listOf(pickup, drop),
            color = Color(0xFFF2D22E),
            width = 8f
        )
    }
}
