package com.weelo.logistics.ui.transporter

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
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
import com.weelo.logistics.BuildConfig
import com.weelo.logistics.ui.theme.BroadcastUiTokens
import java.util.Locale
import kotlin.math.abs

enum class BroadcastCardMapRenderMode {
    LIVE_HERO,
    STATIC_CARD,
    STATIC_OVERLAY,
    FALLBACK_PLACEHOLDER
}

data class RoutePreviewKey(
    val broadcastId: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropLat: Double,
    val dropLng: Double
)

/**
 * Kept for call-site compatibility. Static map rendering is now URL/cache based.
 */
class RouteMapSnapshotCache(
    @Suppress("unused")
    private val maxEntries: Int = 128
)

private data class StaticMapRequest(
    val url: String,
    val cacheKey: String
)

@Composable
fun BroadcastCardMapRenderer(
    routeKey: RoutePreviewKey,
    renderMode: BroadcastCardMapRenderMode,
    @Suppress("UNUSED_PARAMETER")
    snapshotCache: RouteMapSnapshotCache,
    modifier: Modifier = Modifier
) {
    val hasValidCoords = remember(routeKey) {
        !(routeKey.pickupLat == 0.0 &&
            routeKey.pickupLng == 0.0 &&
            routeKey.dropLat == 0.0 &&
            routeKey.dropLng == 0.0)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BroadcastUiTokens.CardMutedBackground)
    ) {
        when {
            !hasValidCoords || renderMode == BroadcastCardMapRenderMode.FALLBACK_PLACEHOLDER -> {
                RoutePlaceholder(routeKey = routeKey, modifier = Modifier.fillMaxSize())
            }
            renderMode == BroadcastCardMapRenderMode.LIVE_HERO -> {
                LiveRoutePreview(routeKey = routeKey, modifier = Modifier.fillMaxSize())
            }
            else -> {
                StaticRoutePreview(
                    routeKey = routeKey,
                    renderMode = renderMode,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun StaticRoutePreview(
    routeKey: RoutePreviewKey,
    renderMode: BroadcastCardMapRenderMode,
    modifier: Modifier = Modifier
) {
    val apiKey = remember { BuildConfig.MAPS_API_KEY.trim() }
    val request = remember(routeKey, renderMode, apiKey) {
        buildStaticMapRequest(routeKey, renderMode, apiKey)
    }

    if (request == null) {
        RoutePlaceholder(routeKey = routeKey, modifier = modifier)
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(request.url)
            .diskCacheKey(request.cacheKey)
            .memoryCacheKey(request.cacheKey)
            .crossfade(true)
            .build(),
        contentDescription = "Route preview",
        modifier = modifier,
        loading = { RoutePlaceholder(routeKey = routeKey, modifier = Modifier.fillMaxSize()) },
        error = { RoutePlaceholder(routeKey = routeKey, modifier = Modifier.fillMaxSize()) }
    )
}

@Composable
private fun RoutePlaceholder(
    routeKey: RoutePreviewKey? = null,
    modifier: Modifier = Modifier
) {
    val routeDistanceLabel = remember(routeKey) {
        routeKey?.let { key ->
            val km = estimateDistanceKm(
                key.pickupLat,
                key.pickupLng,
                key.dropLat,
                key.dropLng
            )
            if (km > 0.1) String.format(Locale.US, "%.1f km route", km) else null
        }
    }

    Box(
        modifier = modifier.background(BroadcastUiTokens.CardMutedBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                tint = BroadcastUiTokens.TertiaryText
            )
            Text(
                text = "Route map unavailable",
                color = BroadcastUiTokens.SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (!routeDistanceLabel.isNullOrBlank()) {
                Text(
                    text = routeDistanceLabel,
                    color = BroadcastUiTokens.TertiaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun LiveRoutePreview(
    routeKey: RoutePreviewKey,
    modifier: Modifier = Modifier
) {
    val pickup = remember(routeKey.pickupLat, routeKey.pickupLng) {
        LatLng(routeKey.pickupLat, routeKey.pickupLng)
    }
    val drop = remember(routeKey.dropLat, routeKey.dropLng) {
        LatLng(routeKey.dropLat, routeKey.dropLng)
    }
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
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
        )
        Marker(
            state = MarkerState(position = drop),
            title = "Drop",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        )
        Polyline(
            points = listOf(pickup, drop),
            color = BroadcastUiTokens.PrimaryCta,
            width = 8f
        )
    }
}

private fun buildStaticMapRequest(
    routeKey: RoutePreviewKey,
    renderMode: BroadcastCardMapRenderMode,
    apiKey: String
): StaticMapRequest? {
    if (apiKey.isBlank()) return null

    val size = when (renderMode) {
        BroadcastCardMapRenderMode.STATIC_OVERLAY -> "960x420"
        BroadcastCardMapRenderMode.STATIC_CARD -> "720x360"
        BroadcastCardMapRenderMode.LIVE_HERO,
        BroadcastCardMapRenderMode.FALLBACK_PLACEHOLDER -> "720x360"
    }

    val zoom = calculateStaticMapZoom(routeKey)
    val pickup = formatLatLng(routeKey.pickupLat, routeKey.pickupLng)
    val drop = formatLatLng(routeKey.dropLat, routeKey.dropLng)
    val cacheKey = buildString {
        append("broadcast_static_map:")
        append(routeKey.broadcastId)
        append(':')
        append(pickup)
        append(':')
        append(drop)
        append(':')
        append(size)
        append(':')
        append(zoom)
        append(":light")
    }

    val url = Uri.parse("https://maps.googleapis.com/maps/api/staticmap")
        .buildUpon()
        .appendQueryParameter("size", size)
        .appendQueryParameter("scale", "2")
        .appendQueryParameter("maptype", "roadmap")
        .appendQueryParameter("zoom", zoom.toString())
        .appendQueryParameter("style", "feature:poi|visibility:off")
        .appendQueryParameter("style", "feature:transit|visibility:off")
        .appendQueryParameter("style", "feature:road|saturation:-15|lightness:10")
        .appendQueryParameter("markers", "color:0xF2D22E|label:P|$pickup")
        .appendQueryParameter("markers", "color:0xE05858|label:D|$drop")
        .appendQueryParameter("path", "color:0xF2D22E|weight:6|$pickup|$drop")
        .appendQueryParameter("key", apiKey)
        .build()
        .toString()

    return StaticMapRequest(url = url, cacheKey = cacheKey)
}

private fun calculateStaticMapZoom(routeKey: RoutePreviewKey): Int {
    val latDelta = abs(routeKey.pickupLat - routeKey.dropLat)
    val lngDelta = abs(routeKey.pickupLng - routeKey.dropLng)
    val maxDelta = maxOf(latDelta, lngDelta)
    return when {
        maxDelta > 3.0 -> 7
        maxDelta > 1.5 -> 8
        maxDelta > 0.7 -> 9
        maxDelta > 0.25 -> 10
        maxDelta > 0.08 -> 11
        else -> 12
    }
}

private fun formatLatLng(lat: Double, lng: Double): String {
    return String.format(Locale.US, "%.5f,%.5f", lat, lng)
}

private fun estimateDistanceKm(
    startLat: Double,
    startLng: Double,
    endLat: Double,
    endLng: Double
): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(endLat - startLat)
    val dLng = Math.toRadians(endLng - startLng)
    val lat1 = Math.toRadians(startLat)
    val lat2 = Math.toRadians(endLat)

    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2) *
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadiusKm * c
}
