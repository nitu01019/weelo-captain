package com.weelo.logistics.ui.transporter

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import com.weelo.logistics.broadcast.BroadcastUiTiming
import com.weelo.logistics.core.notification.BroadcastSoundService
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.model.RequestedVehicle
import com.weelo.logistics.ui.components.EmptyStateArtwork
import com.weelo.logistics.ui.components.EmptyStateHost
import com.weelo.logistics.ui.components.ProvideShimmerBrush
import com.weelo.logistics.ui.components.SkeletonListCard
import com.weelo.logistics.ui.components.allCaughtUpEmptyStateSpec

private val BroadcastBase = Color(0xFFF7F7F7)
private val BroadcastCardSurface = Color.White
private val BroadcastAccent = Color(0xFFF2DD34)
private val BroadcastTextPrimary = Color(0xFF121212)
private val BroadcastTextSecondary = Color(0xFF6D6D6D)
private val BroadcastBorder = Color(0xFFE9E9E9)
private val BroadcastMapSurface = Color(0xFFF3F5FA)
private const val LIVE_CARD_MAP_BUDGET = 2

@Composable
fun BroadcastListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBroadcastDetails: (String) -> Unit,
    onNavigateToSoundSettings: () -> Unit = {}
) {
    val viewModel: BroadcastListViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val soundService = remember { BroadcastSoundService.getInstance(context) }
    val snapshotCache = remember { RouteMapSnapshotCache(maxEntries = 256) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            viewModel.onScreenStarted()
            try {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        is BroadcastListUiEvent.ShowToast -> {
                            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                        }

                        is BroadcastListUiEvent.PlaySound -> {
                            if (event.urgent) {
                                soundService.playUrgentSound()
                            } else {
                                soundService.playBroadcastSound()
                            }
                        }

                        BroadcastListUiEvent.NavigateBackWhenEmpty -> onNavigateBack()
                    }
                }
            } finally {
                viewModel.onScreenStopped()
            }
        }
    }

    val liveMapIds by remember(uiState.visibleBroadcasts, listState.layoutInfo.visibleItemsInfo) {
        derivedStateOf {
            val visibleKeys = listState.layoutInfo.visibleItemsInfo
                .mapNotNull { it.key as? String }
                .take(LIVE_CARD_MAP_BUDGET)
                .toSet()
            if (visibleKeys.isNotEmpty()) {
                visibleKeys
            } else {
                uiState.visibleBroadcasts.take(LIVE_CARD_MAP_BUDGET).map { it.broadcastId }.toSet()
            }
        }
    }

    val focusBroadcast by remember(uiState.visibleBroadcasts, listState.firstVisibleItemIndex) {
        derivedStateOf {
            uiState.visibleBroadcasts.getOrNull(listState.firstVisibleItemIndex)
                ?: uiState.visibleBroadcasts.firstOrNull()
        }
    }

    LaunchedEffect(liveMapIds) {
        BroadcastTelemetry.record(
            stage = BroadcastStage.STATE_APPLIED,
            status = BroadcastStatus.SUCCESS,
            reason = "card_map_budget_applied",
            attrs = mapOf(
                "liveMapCount" to liveMapIds.size.toString(),
                "liveMapBudget" to LIVE_CARD_MAP_BUDGET.toString()
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BroadcastBase)
    ) {
        BroadcastTopBar(
            isConnected = uiState.isSocketConnected,
            requestCount = uiState.broadcasts.size,
            isRefreshing = uiState.isRefreshing,
            onBack = onNavigateBack,
            onNavigate = {
                focusBroadcast?.let { openNavigation(context, it) }
            },
            onRefresh = { viewModel.onManualRefresh() },
            onSoundSettings = onNavigateToSoundSettings
        )

        when {
            uiState.isLoading && uiState.broadcasts.isEmpty() -> {
                BroadcastLoadingState()
            }

            uiState.errorMessage != null && uiState.broadcasts.isEmpty() -> {
                BroadcastErrorState(
                    errorMessage = uiState.errorMessage,
                    onRetry = { viewModel.onManualRefresh() }
                )
            }

            uiState.visibleBroadcasts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateHost(
                        spec = allCaughtUpEmptyStateSpec(
                            artwork = EmptyStateArtwork.REQUESTS_ALL_CAUGHT_UP,
                            title = "No active requests",
                            subtitle = "New customer broadcasts will appear here instantly"
                        )
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        BroadcastHeroPanel(
                            broadcast = focusBroadcast,
                            isConnected = uiState.isSocketConnected,
                            totalCount = uiState.broadcasts.size,
                            snapshotCache = snapshotCache
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Requests (${uiState.visibleBroadcasts.size})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = BroadcastTextPrimary
                            )
                            Surface(
                                color = BroadcastAccent.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Auto-accept Off",
                                    color = BroadcastTextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(BroadcastListFeedTab.entries) { tab ->
                                FilterChip(
                                    selected = uiState.selectedTab == tab,
                                    onClick = { viewModel.onTabSelected(tab) },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontWeight = if (uiState.selectedTab == tab) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Medium
                                            }
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BroadcastTextPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White,
                                        labelColor = BroadcastTextPrimary
                                    )
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = uiState.visibleBroadcasts,
                        key = { _, item -> item.broadcastId }
                    ) { _, broadcast ->
                        LaunchedEffect(broadcast.broadcastId) {
                            viewModel.onBroadcastCardRendered(broadcast.broadcastId)
                        }
                        val dismissInfo = uiState.dismissedCards[broadcast.broadcastId]
                        val isDismissed = dismissInfo != null

                        Box(modifier = Modifier.fillMaxWidth()) {
                            BroadcastRequestCard(
                                broadcast = broadcast,
                                isDismissed = isDismissed,
                                mapRenderMode = if (liveMapIds.contains(broadcast.broadcastId)) {
                                    BroadcastCardMapRenderMode.LIVE
                                } else {
                                    BroadcastCardMapRenderMode.SNAPSHOT
                                },
                                snapshotCache = snapshotCache,
                                onIgnore = {
                                    viewModel.onIgnoreBroadcast(broadcast.broadcastId)
                                },
                                onAccept = { vehicleType, vehicleSubtype, quantity ->
                                    onNavigateToBroadcastDetails(
                                        "${broadcast.broadcastId}|$vehicleType|$vehicleSubtype|$quantity"
                                    )
                                }
                            )

                            androidx.compose.animation.AnimatedVisibility(
                                visible = isDismissed,
                                enter = fadeIn(tween(BroadcastUiTiming.DISMISS_ENTER_MS)),
                                exit = fadeOut(tween(BroadcastUiTiming.DISMISS_EXIT_MS))
                            ) {
                                DismissOverlay(
                                    reasonTitle = when (dismissInfo?.reason) {
                                        "customer_cancelled" -> "Order Cancelled"
                                        "fully_filled" -> "Already Assigned"
                                        else -> "Request Expired"
                                    },
                                    message = dismissInfo?.message ?: "This request is no longer active"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastTopBar(
    isConnected: Boolean,
    requestCount: Int,
    isRefreshing: Boolean,
    onBack: () -> Unit,
    onNavigate: () -> Unit,
    onRefresh: () -> Unit,
    onSoundSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = BroadcastTextPrimary)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Broadcast",
                        style = MaterialTheme.typography.titleLarge,
                        color = BroadcastTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF2EBE67) else Color(0xFFB0B0B0))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) "Online" else "Offline",
                            color = BroadcastTextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$requestCount active",
                            color = BroadcastTextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Button(
                    onClick = onNavigate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BroadcastAccent,
                        contentColor = BroadcastTextPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Navigate", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onSoundSettings) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Sound settings",
                        tint = BroadcastTextPrimary
                    )
                }
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = BroadcastTextPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = BroadcastTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastHeroPanel(
    broadcast: BroadcastTrip?,
    isConnected: Boolean,
    totalCount: Int,
    snapshotCache: RouteMapSnapshotCache
) {
    val totalFare = remember(totalCount, broadcast?.broadcastId) {
        broadcast?.totalFare ?: 0.0
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = BroadcastCardSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            if (broadcast != null) {
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
                BroadcastCardMapRenderer(
                    routeKey = routeKey,
                    renderMode = BroadcastCardMapRenderMode.LIVE,
                    snapshotCache = snapshotCache,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .background(BroadcastMapSurface)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .background(BroadcastMapSurface)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip(
                modifier = Modifier.weight(1f),
                title = "Potential",
                value = if (totalFare > 0.0) {
                    "₹${String.format("%,.0f", totalFare)}"
                } else {
                    "₹0"
                }
            )
            StatChip(
                modifier = Modifier.weight(1f),
                title = "Requests",
                value = totalCount.toString()
            )
            StatChip(
                modifier = Modifier.weight(1f),
                title = "Status",
                value = if (isConnected) "Live" else "Offline"
            )
        }
    }
}

@Composable
private fun StatChip(
    modifier: Modifier,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = BroadcastTextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = BroadcastTextPrimary,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun BroadcastRequestCard(
    broadcast: BroadcastTrip,
    isDismissed: Boolean,
    mapRenderMode: BroadcastCardMapRenderMode,
    snapshotCache: RouteMapSnapshotCache,
    onIgnore: () -> Unit,
    onAccept: (vehicleType: String, vehicleSubtype: String, quantity: Int) -> Unit
) {
    val requestVehicle = remember(broadcast.requestedVehicles, broadcast.broadcastId) {
        broadcast.requestedVehicles
            .firstOrNull { it.remainingCount > 0 }
            ?: broadcast.requestedVehicles.firstOrNull()
            ?: RequestedVehicle(
                vehicleType = "truck",
                vehicleSubtype = "",
                count = broadcast.totalRemainingTrucks.coerceAtLeast(1),
                filledCount = 0,
                farePerTruck = broadcast.farePerTruck
            )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDismissed) 0.22f else 1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BroadcastCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(BroadcastAccent.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = BroadcastTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "₹${String.format("%,.0f", requestVehicle.farePerTruck)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = BroadcastTextPrimary
                    )
                    Text(
                        text = if (broadcast.customerName.isNotBlank()) broadcast.customerName else "Cash Payment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BroadcastTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${broadcast.distance.toInt()} km",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BroadcastTextPrimary
                    )
                    Text(
                        text = "Total dist.",
                        style = MaterialTheme.typography.labelMedium,
                        color = BroadcastTextSecondary
                    )
                }
            }

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

            BroadcastCardMapRenderer(
                routeKey = routeKey,
                renderMode = mapRenderMode,
                snapshotCache = snapshotCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp)
                    .background(BroadcastMapSurface)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "PICKUP",
                    style = MaterialTheme.typography.labelMedium,
                    color = BroadcastTextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = broadcast.pickupLocation.address,
                    style = MaterialTheme.typography.titleMedium,
                    color = BroadcastTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DROP",
                    style = MaterialTheme.typography.labelMedium,
                    color = BroadcastTextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = broadcast.dropLocation.address,
                    style = MaterialTheme.typography.titleMedium,
                    color = BroadcastTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0F0F0)
                ) {
                    Text(
                        text = "Ignore",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIgnore() }
                            .padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        color = BroadcastTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        onAccept(
                            requestVehicle.vehicleType,
                            requestVehicle.vehicleSubtype,
                            1
                        )
                    },
                    modifier = Modifier.weight(1.8f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BroadcastAccent,
                        contentColor = BroadcastTextPrimary
                    )
                ) {
                    Text(
                        text = "Accept ${requestVehicle.vehicleType.replaceFirstChar { it.uppercase() }}",
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DismissOverlay(
    reasonTitle: String,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .border(width = 1.dp, color = BroadcastBorder, shape = RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = reasonTitle,
                style = MaterialTheme.typography.titleLarge,
                color = BroadcastTextPrimary,
                fontWeight = FontWeight.Black
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = BroadcastTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BroadcastLoadingState() {
    ProvideShimmerBrush {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(5) {
                SkeletonListCard()
            }
        }
    }
}

@Composable
private fun BroadcastErrorState(
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = errorMessage ?: "Unable to load broadcast requests",
                color = BroadcastTextSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

private fun openNavigation(context: android.content.Context, broadcast: BroadcastTrip) {
    val pickup = broadcast.pickupLocation
    val drop = broadcast.dropLocation
    if (pickup.latitude == 0.0 || pickup.longitude == 0.0 || drop.latitude == 0.0 || drop.longitude == 0.0) {
        Toast.makeText(context, "Location unavailable for navigation", Toast.LENGTH_SHORT).show()
        return
    }

    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1&origin=${pickup.latitude},${pickup.longitude}" +
            "&destination=${drop.latitude},${drop.longitude}&travelmode=driving"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }

    runCatching {
        context.startActivity(intent)
    }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
