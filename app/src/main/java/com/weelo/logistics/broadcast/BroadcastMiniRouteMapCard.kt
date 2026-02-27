package com.weelo.logistics.broadcast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.ui.theme.BroadcastUiTokens
import com.weelo.logistics.ui.transporter.BroadcastCardMapRenderMode
import com.weelo.logistics.ui.transporter.BroadcastCardMapRenderer
import com.weelo.logistics.ui.transporter.RouteMapSnapshotCache
import com.weelo.logistics.ui.transporter.RoutePreviewKey

@Composable
fun BroadcastMiniRouteMapCard(
    broadcast: BroadcastTrip,
    modifier: Modifier = Modifier,
    title: String = "Route preview",
    subtitle: String = "",
    mapHeight: Dp = 132.dp,
    renderMode: BroadcastCardMapRenderMode = BroadcastCardMapRenderMode.SNAPSHOT
) {
    val routeKey = remember(
        broadcast.broadcastId,
        broadcast.pickupLocation.latitude,
        broadcast.pickupLocation.longitude,
        broadcast.dropLocation.latitude,
        broadcast.dropLocation.longitude
    ) {
        RoutePreviewKey(
            broadcastId = broadcast.broadcastId,
            pickupLat = broadcast.pickupLocation.latitude,
            pickupLng = broadcast.pickupLocation.longitude,
            dropLat = broadcast.dropLocation.latitude,
            dropLng = broadcast.dropLocation.longitude
        )
    }
    val snapshotCache = remember { RouteMapSnapshotCache(maxEntries = 96) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BroadcastUiTokens.CardMutedBackground,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = BroadcastUiTokens.SecondaryCtaText
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = BroadcastUiTokens.PrimaryText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = BroadcastUiTokens.SecondaryText
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                BroadcastCardMapRenderer(
                    routeKey = routeKey,
                    renderMode = renderMode,
                    snapshotCache = snapshotCache,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
