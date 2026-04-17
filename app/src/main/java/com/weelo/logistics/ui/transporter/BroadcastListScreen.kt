package com.weelo.logistics.ui.transporter

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weelo.logistics.broadcast.BroadcastOverlayContentNew
import com.weelo.logistics.broadcast.BroadcastOverlayManager
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import com.weelo.logistics.broadcast.BroadcastStateSync
import com.weelo.logistics.broadcast.BroadcastUiTiming
import com.weelo.logistics.broadcast.TruckHoldState
import com.weelo.logistics.broadcast.TruckHoldStatus
import com.weelo.logistics.core.notification.BroadcastSoundService
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.ui.ServerDeadlineTimer
import com.weelo.logistics.ui.components.EmptyStateArtwork
import com.weelo.logistics.ui.components.EmptyStateHost
import com.weelo.logistics.ui.components.ProvideShimmerBrush
import com.weelo.logistics.ui.components.SkeletonListCard
import com.weelo.logistics.ui.components.allCaughtUpEmptyStateSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Colors used in this screen
private val BroadcastBase = Color(0xFF1A1A1A)           // Match overlay dark bg

/**
 * =============================================================================
 * BROADCAST LIST SCREEN — UNIFIED WITH OVERLAY
 * =============================================================================
 *
 * Each card = BroadcastOverlayContentNew (EXACT same as overlay).
 * Card gets full screen height so fillMaxSize inside the composable works.
 * Newest broadcast auto-scrolls to top.
 * Ignore on this screen ALSO removes from Overlay (cross-screen sync).
 * Fully accepted broadcasts are auto-removed via BroadcastStateSync.
 * Only ONE navigation button exists — inside each card (DIRECTIONS).
 * =============================================================================
 */
@Composable
fun BroadcastListScreen(
    onNavigateBack: () -> Unit,
    onAccept: (BroadcastTrip) -> Unit,
    onNavigateToSoundSettings: () -> Unit = {}
) {
    val viewModel: BroadcastListViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val soundService = remember { BroadcastSoundService.getInstance(context) }

    // Screen height used to give each card a proper bounded height
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    // Top bar is ~72dp, give cards the remaining space
    val cardHeight = screenHeightDp - 72.dp

    // ── Lifecycle: listen for events ──
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onScreenStarted()
            try {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        is BroadcastListUiEvent.ShowToast ->
                            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                        is BroadcastListUiEvent.PlaySound ->
                            if (event.urgent) soundService.playUrgentSound()
                            else soundService.playBroadcastSound()
                        BroadcastListUiEvent.NavigateBackWhenEmpty -> onNavigateBack()
                    }
                }
            } finally {
                viewModel.onScreenStopped()
            }
        }
    }

    // ── Auto-scroll to top when a new broadcast arrives ──
    val prevCount = remember { mutableIntStateOf(0) }
    LaunchedEffect(uiState.visibleBroadcasts.size) {
        val currentCount = uiState.visibleBroadcasts.size
        if (currentCount > prevCount.intValue && currentCount > 0) {
            listState.animateScrollToItem(0)
        }
        prevCount.intValue = currentCount
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BroadcastBase)
    ) {
        // ── Top bar: back, title, online status, bell, refresh ──
        BroadcastTopBar(
            isConnected = uiState.isSocketConnected,
            requestCount = uiState.broadcasts.size,
            isRefreshing = uiState.isRefreshing,
            onBack = onNavigateBack,
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
                // ── Paged list: one card per screen, scroll to navigate ──
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(
                        items = uiState.visibleBroadcasts,
                        key = { _, item -> item.broadcastId }
                    ) { _, broadcast ->
                        LaunchedEffect(broadcast.broadcastId) {
                            viewModel.onBroadcastCardRendered(broadcast.broadcastId)
                        }

                        val dismissInfo = uiState.dismissedCards[broadcast.broadcastId]
                        val isDismissed = dismissInfo != null

                        // ── KEY FIX: Give each card an explicit height so
                        //    BroadcastOverlayContentNew's fillMaxSize has a real constraint ──
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeight)
                        ) {
                            BroadcastListCard(
                                broadcast = broadcast,
                                isDismissed = isDismissed,
                                dismissReason = dismissInfo?.reason,
                                dismissMessage = dismissInfo?.message,
                                onAccept = onAccept,
                                onIgnore = { viewModel.onIgnoreBroadcast(broadcast.broadcastId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * =============================================================================
 * BROADCAST LIST CARD — EXACT same overlay composable
 * =============================================================================
 *
 * Self-contained with own hold state + countdown.
 * Ignore from Request Screen ALSO removes from Overlay (cross-screen sync).
 * =============================================================================
 */
@Composable
private fun BroadcastListCard(
    broadcast: BroadcastTrip,
    isDismissed: Boolean,
    dismissReason: String?,
    dismissMessage: String?,
    onAccept: (BroadcastTrip) -> Unit,
    onIgnore: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val broadcastRepository = remember { BroadcastRepository.getInstance(context) }

    // ── Per-card hold state ──
    var truckHoldStates by remember(broadcast.broadcastId) {
        mutableStateOf<Map<String, TruckHoldState>>(emptyMap())
    }
    val acceptedTrucks = truckHoldStates.values.filter { it.status == TruckHoldStatus.ACCEPTED }
    val totalAcceptedQuantity = acceptedTrucks.sumOf { it.quantity }
    val isSubmitEnabled = acceptedTrucks.isNotEmpty()
    val hasAnyHolding = truckHoldStates.values.any { it.isHolding }

    // ── Per-card countdown (F-C-27 / W1-3 — server-deadline recompute every
    //    tick, doze-safe via SystemClock.elapsedRealtime). Pattern mirrors the
    //    other 3 captain timer screens (VehicleHoldConfirmScreen,
    //    DriverAssignmentScreen, DriverTripRequestOverlay) migrated in phase-3
    //    commit a38862c. Replaces the old `remainingSeconds--` local decrement
    //    loop that silently under-counted during Android doze. ──
    val deadlineElapsedMs = remember(broadcast.broadcastId, broadcast.expiryTime) {
        val expiryMs = broadcast.expiryTime ?: (System.currentTimeMillis() + 120_000L)
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
            expiresAtWallMs = expiryMs,
            nowWallMs = nowWall,
            nowElapsedMs = nowElapsed
        )
    }
    val initialRemaining = remember(broadcast.broadcastId, deadlineElapsedMs) {
        ServerDeadlineTimer.remainingSecondsFromDeadline(
            deadlineElapsedMs = deadlineElapsedMs,
            nowElapsedMs = SystemClock.elapsedRealtime()
        )
    }
    var remainingSeconds by remember(broadcast.broadcastId) { mutableIntStateOf(initialRemaining) }

    LaunchedEffect(broadcast.broadcastId, deadlineElapsedMs) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds = ServerDeadlineTimer.remainingSecondsFromDeadline(
                deadlineElapsedMs = deadlineElapsedMs,
                nowElapsedMs = SystemClock.elapsedRealtime()
            )
        }
    }

    // ── ACCEPT TRUCK (Redis hold — same logic as overlay) ──
    fun handleAcceptTruck(vehicleType: String, vehicleSubtype: String, quantity: Int) {
        val key = "$vehicleType|$vehicleSubtype"
        truckHoldStates = truckHoldStates + (key to TruckHoldState(
            vehicleType = vehicleType,
            vehicleSubtype = vehicleSubtype,
            quantity = quantity,
            status = TruckHoldStatus.PENDING,
            isHolding = true
        ))
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_ACCEPT_HOLD_REQUESTED,
            status = BroadcastStatus.SUCCESS,
            attrs = mapOf(
                "broadcastId" to broadcast.broadcastId,
                "vehicleType" to vehicleType,
                "vehicleSubtype" to vehicleSubtype,
                "quantity" to quantity.toString(),
                "source" to "request_screen"
            )
        )
        scope.launch {
            val result = broadcastRepository.holdTrucks(
                orderId = broadcast.broadcastId,
                vehicleType = vehicleType,
                vehicleSubtype = vehicleSubtype,
                quantity = quantity
            )
            when (result) {
                is BroadcastResult.Success -> {
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.BROADCAST_HOLD_SUCCESS,
                        status = BroadcastStatus.SUCCESS,
                        attrs = mapOf("broadcastId" to broadcast.broadcastId, "holdId" to result.data.holdId)
                    )
                    truckHoldStates = truckHoldStates + (key to TruckHoldState(
                        vehicleType = vehicleType,
                        vehicleSubtype = vehicleSubtype,
                        quantity = quantity,
                        holdId = result.data.holdId,
                        status = TruckHoldStatus.ACCEPTED,
                        isHolding = false
                    ))
                    Toast.makeText(context, "✓ $quantity truck(s) reserved. Assign drivers to finalize.", Toast.LENGTH_SHORT).show()
                }
                is BroadcastResult.Error -> {
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.BROADCAST_HOLD_FAIL,
                        status = BroadcastStatus.FAILED,
                        reason = result.message,
                        attrs = mapOf("broadcastId" to broadcast.broadcastId, "vehicleType" to vehicleType)
                    )
                    val isCancelled = result.message.contains("cancelled", ignoreCase = true)
                        || result.message.contains("no longer exists", ignoreCase = true)
                        || result.message.contains("expired", ignoreCase = true)
                    if (isCancelled) {
                        Toast.makeText(context, "This request was cancelled", Toast.LENGTH_SHORT).show()
                        onIgnore() // Remove from this screen's list only
                    } else {
                        truckHoldStates = truckHoldStates + (key to TruckHoldState(
                            vehicleType, vehicleSubtype, quantity,
                            status = TruckHoldStatus.FAILED, isHolding = false
                        ))
                        Toast.makeText(context, "Failed: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
                is BroadcastResult.Loading -> { /* handled by isHolding */ }
            }
        }
    }

    // ── REJECT TRUCK (local only, same as overlay) ──
    fun handleRejectTruck(vehicleType: String, vehicleSubtype: String) {
        truckHoldStates = truckHoldStates + ("$vehicleType|$vehicleSubtype" to TruckHoldState(
            vehicleType = vehicleType,
            vehicleSubtype = vehicleSubtype,
            quantity = 0,
            status = TruckHoldStatus.REJECTED,
            isHolding = false
        ))
    }

    // ── SUBMIT: same pass-through as overlay ──
    fun handleSubmit() {
        val holdInfo = acceptedTrucks.joinToString(";") {
            "${it.vehicleType}|${it.vehicleSubtype}|${it.quantity}|${it.holdId ?: ""}"
        }
        onAccept(broadcast.copy(notes = holdInfo))
    }

    // ── DISMISS: Removes from this screen's list AND from the Overlay.
    //    Request Screen ignore = "I don't want this order" → remove everywhere.
    fun handleDismiss() {
        // Release any Redis holds this card had
        scope.launch {
            truckHoldStates.values
                .filter { it.holdId != null && it.status == TruckHoldStatus.ACCEPTED }
                .forEach { broadcastRepository.releaseHold(it.holdId!!) }
        }

        // Propagate to Overlay — remove from overlay queue too
        BroadcastStateSync.markIgnoredByList(broadcast.broadcastId)
        BroadcastOverlayManager.removeEverywhere(broadcast.broadcastId)

        // Remove from this screen's list
        onIgnore()
    }

    // ── RENDER ──
    Box(modifier = Modifier.fillMaxSize()) {
        // Same overlay composable, full size within the card's bounded height
        BroadcastOverlayContentNew(
            broadcast = broadcast,
            remainingSeconds = remainingSeconds,
            currentIndex = 0,
            totalCount = 1,
            pendingQueueCount = 0,
            truckHoldStates = truckHoldStates,
            isSubmitEnabled = isSubmitEnabled && !isDismissed,
            totalAcceptedQuantity = totalAcceptedQuantity,
            hasAnyHolding = hasAnyHolding,
            onAcceptTruck = ::handleAcceptTruck,
            onRejectTruck = ::handleRejectTruck,
            onSubmit = ::handleSubmit,
            onDismiss = ::handleDismiss,
            onPrevious = {},
            onNext = {}
        )

        // Dismiss overlay when cancelled/expired server-side
        AnimatedVisibility(
            visible = isDismissed,
            enter = fadeIn(tween(BroadcastUiTiming.DISMISS_ENTER_MS)),
            exit = fadeOut(tween(BroadcastUiTiming.DISMISS_EXIT_MS))
        ) {
            DismissOverlay(
                reasonTitle = when (dismissReason) {
                    "customer_cancelled" -> "Order Cancelled"
                    "fully_filled" -> "Already Assigned"
                    else -> "Request Expired"
                },
                message = dismissMessage ?: "This request is no longer active"
            )
        }
    }
}

// =============================================================================
// TOP BAR — NO Navigate button (DIRECTIONS is inside each card)
// =============================================================================
@Composable
private fun BroadcastTopBar(
    isConnected: Boolean,
    requestCount: Int,
    isRefreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSoundSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1C1C1C),      // Dark to blend with overlay cards
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Broadcast",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFFF2DD34) else Color(0xFF666666))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "Online" else "Offline",
                        color = Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$requestCount active",
                        color = Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Bell icon
            IconButton(onClick = onSoundSettings) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Sound settings",
                    tint = Color.White
                )
            }

            // Refresh icon
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// =============================================================================
// DISMISS OVERLAY
// =============================================================================
@Composable
private fun DismissOverlay(reasonTitle: String, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = reasonTitle,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF121212),
                fontWeight = FontWeight.Black
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6D6D6D),
                textAlign = TextAlign.Center
            )
        }
    }
}

// =============================================================================
// LOADING STATE
// =============================================================================
@Composable
private fun BroadcastLoadingState() {
    ProvideShimmerBrush {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(3) { SkeletonListCard() }
        }
    }
}

// =============================================================================
// ERROR STATE
// =============================================================================
@Composable
private fun BroadcastErrorState(errorMessage: String?, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = errorMessage ?: "Unable to load broadcast requests",
                color = Color(0xFF6D6D6D),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
