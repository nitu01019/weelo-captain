package com.weelo.logistics.broadcast

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.weelo.logistics.core.notification.BroadcastSoundService
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.model.RequestedVehicle
import com.weelo.logistics.data.model.RoutePointType
import com.weelo.logistics.data.remote.BroadcastDismissedNotification
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.ui.transporter.BroadcastCardMapRenderMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "BroadcastOverlay"

// =============================================================================
// Shared broadcast palette aligned with reference yellow theme.
// =============================================================================
private val RapidoYellow = BroadcastUiTokens.PrimaryCta
private val RapidoYellowDark = BroadcastUiTokens.PrimaryCtaPressed
private val RapidoBlack = BroadcastUiTokens.OnPrimaryCta
private val RapidoDarkGray = BroadcastUiTokens.ScreenBackground
private val RapidoGray = BroadcastUiTokens.SecondaryText
private val RapidoMediumGray = BroadcastUiTokens.TertiaryText
private val RapidoLightGray = BroadcastUiTokens.SecondaryText
private val RapidoWhite = BroadcastUiTokens.PrimaryText
private val RapidoGreen = BroadcastUiTokens.PrimaryCta
private val RapidoRed = BroadcastUiTokens.Error
private val RapidoBlue = BroadcastUiTokens.AccentInfo

/**
 * =============================================================================
 * TRUCK HOLD STATE - Tracks accepted/rejected trucks
 * =============================================================================
 */
data class TruckHoldState(
    val vehicleType: String,
    val vehicleSubtype: String,
    val quantity: Int,
    val holdId: String? = null,
    val status: TruckHoldStatus = TruckHoldStatus.PENDING,
    val isHolding: Boolean = false  // Loading state for API call
)

enum class TruckHoldStatus {
    PENDING,    // Not yet decided
    ACCEPTED,   // Accepted and held (Redis lock acquired)
    REJECTED,   // Rejected by transporter (blurred)
    FAILED      // Hold failed (e.g., already taken)
}

/**
 * =============================================================================
 * BROADCAST OVERLAY SCREEN - Per-Truck Accept/Reject with Hold System
 * =============================================================================
 * 
 * Full-screen overlay that appears when a new broadcast arrives.
 * 
 * NEW FLOW (Per-Truck Accept = Hold):
 * ─────────────────────────────────────────────────────────────────────────────
 * 1. Transporter sees personalized broadcast with trucks they can provide
 * 2. Each truck type has:
 *    - [+] / [-] quantity selector
 *    - [ACCEPT] button → Calls holdTrucks() API (Redis 60-sec lock)
 *    - [REJECT] button → Blurs that truck (won't give it)
 * 3. Compact in-app route map + direction actions for context
 * 4. [SUBMIT] button at bottom:
 *    - DISABLED (gray) when no trucks accepted
 *    - ENABLED (yellow/bold) when at least 1 truck accepted
 * 5. Click SUBMIT → Opens truck selection page with ONLY held trucks
 * 6. Non-accepted trucks are auto-released/rejected
 * 
 * CORE INVARIANTS:
 * ─────────────────────────────────────────────────────────────────────────────
 * ✓ Accept = HOLD (Redis atomic lock, 60-sec TTL)
 * ✓ Other transporters see reduced availability in real-time
 * ✓ Reject = Local blur only (truck stays in pool for others)
 * ✓ Submit opens next screen with held trucks only
 * 
 * =============================================================================
 */
@Composable
fun BroadcastOverlayScreen(
    onAccept: (BroadcastTrip) -> Unit,
    onReject: (BroadcastTrip) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soundService = remember { BroadcastSoundService.getInstance(context) }
    val broadcastRepository = remember { BroadcastRepository.getInstance(context) }
    
    val isVisible by BroadcastOverlayManager.isOverlayVisible.collectAsState()
    val currentBroadcast by BroadcastOverlayManager.currentBroadcast.collectAsState()
    val remainingSeconds by BroadcastOverlayManager.remainingTimeSeconds.collectAsState()
    val queueSize by BroadcastOverlayManager.queueSize.collectAsState()
    val currentIndex by BroadcastOverlayManager.currentIndex.collectAsState()
    val totalCount by BroadcastOverlayManager.totalBroadcastCount.collectAsState()
    
    // =========================================================================
    // TRUCK HOLD STATE - Tracks accepted/rejected/pending trucks
    // =========================================================================
    var truckHoldStates by remember { mutableStateOf<Map<String, TruckHoldState>>(emptyMap()) }
    
    // Reset when broadcast changes
    LaunchedEffect(currentBroadcast?.broadcastId) {
        truckHoldStates = emptyMap()
        currentBroadcast?.broadcastId?.let(BroadcastOverlayManager::acknowledgeDisplayed)
    }

    // Auto-dismiss overlay when broadcast is removed (cancelled/expired via socket event)
    val feedState by BroadcastFlowCoordinator.feedState.collectAsState()
    val previousBroadcastCount = remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    LaunchedEffect(feedState.broadcasts.size, currentBroadcast?.broadcastId) {
        val bid = currentBroadcast?.broadcastId ?: return@LaunchedEffect
        val currentCount = feedState.broadcasts.size
        val prevCount = previousBroadcastCount.intValue
        previousBroadcastCount.intValue = currentCount
        // Only dismiss if we previously had broadcasts and ours was removed
        if (prevCount > 0) {
            val stillExists = feedState.broadcasts.any { it.broadcastId == bid }
            if (!stillExists) {
                android.widget.Toast.makeText(context, "This request was cancelled", android.widget.Toast.LENGTH_SHORT).show()
                BroadcastOverlayManager.dismissOverlay()
            }
        }
    }
    
    // Calculate accepted trucks for Submit button
    val acceptedTrucks = truckHoldStates.values.filter { it.status == TruckHoldStatus.ACCEPTED }
    val totalAcceptedQuantity = acceptedTrucks.sumOf { it.quantity }
    val isSubmitEnabled = acceptedTrucks.isNotEmpty()
    val hasAnyHolding = truckHoldStates.values.any { it.isHolding }
    
    // Play sound when new broadcast appears
    LaunchedEffect(currentBroadcast) {
        currentBroadcast?.let { broadcast ->
            if (broadcast.isUrgent) {
                soundService.playUrgentSound()
            } else {
                soundService.playBroadcastSound()
            }
        }
    }

    LaunchedEffect(currentBroadcast?.broadcastId) {
        val broadcast = currentBroadcast ?: return@LaunchedEffect
        val safeMapEnabled = BroadcastFeatureFlagsRegistry.current().captainOverlaySafeRenderEnabled
        val hasCoords = broadcast.pickupLocation.latitude != 0.0 &&
            broadcast.pickupLocation.longitude != 0.0 &&
            broadcast.dropLocation.latitude != 0.0 &&
            broadcast.dropLocation.longitude != 0.0
        val renderResult = when {
            safeMapEnabled && hasCoords -> "success"
            !hasCoords -> "map_failed_fallback"
            else -> "compose_failed_fallback"
        }
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_OVERLAY_RENDER,
            status = if (renderResult == "success") BroadcastStatus.SUCCESS else BroadcastStatus.BUFFERED,
            reason = renderResult,
            attrs = mapOf(
                "broadcastId" to broadcast.broadcastId,
                "overlayRenderResult" to renderResult
            )
        )
    }
    
    // =========================================================================
    // GRACEFUL DISMISS STATE — Tracks if current broadcast is being dismissed
    // =========================================================================
    var dismissInfo by remember { mutableStateOf<BroadcastDismissedNotification?>(null) }
    
    // Listen for broadcast dismissals
    // CRITICAL FIX: Use collectLatest so a new dismissal event cancels the
    // in-flight 2s delay job from the previous dismissal. Without this, rapid
    // successive dismissals each launch independent jobs that all call
    // showNextBroadcast(), causing the carousel to skip broadcasts.
    LaunchedEffect(Unit) {
        val dismissFlow = if (BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
            BroadcastFlowCoordinator.dismissed
        } else {
            SocketIOService.broadcastDismissed
        }
        dismissFlow.collectLatest { notification ->
            val current = BroadcastOverlayManager.currentBroadcast.value
            if (current == null || current.broadcastId != notification.broadcastId) return@collectLatest

            // Current broadcast is being dismissed — show overlay
            dismissInfo = notification

            // Keep dismiss overlay visible during the shared timing window.
            // collectLatest cancels this delay if a new dismissal arrives first.
            delay(
                BroadcastUiTiming.DISMISS_ENTER_MS.toLong() +
                    BroadcastUiTiming.DISMISS_HOLD_MS +
                    BroadcastUiTiming.DISMISS_EXIT_MS.toLong()
            )
            dismissInfo = null
        }
    }
    
    // Clear dismiss info when broadcast changes
    LaunchedEffect(currentBroadcast?.broadcastId) {
        dismissInfo = null
    }
    
    // =========================================================================
    // ACCEPT TRUCK - Call holdTrucks() API (Redis atomic lock)
    // =========================================================================
    fun handleAcceptTruck(vehicleType: String, vehicleSubtype: String, quantity: Int) {
        val key = "$vehicleType|$vehicleSubtype"
        val broadcast = currentBroadcast ?: return
        
        // Set loading state
        truckHoldStates = truckHoldStates + (key to TruckHoldState(
            vehicleType = vehicleType,
            vehicleSubtype = vehicleSubtype,
            quantity = quantity,
            status = TruckHoldStatus.PENDING,
            isHolding = true
        ))
        
        timber.log.Timber.i("🔒 Holding $quantity trucks: $vehicleType $vehicleSubtype")
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_ACCEPT_HOLD_REQUESTED,
            status = BroadcastStatus.SUCCESS,
            attrs = mapOf(
                "broadcastId" to broadcast.broadcastId,
                "vehicleType" to vehicleType,
                "vehicleSubtype" to vehicleSubtype,
                "quantity" to quantity.toString()
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
                    timber.log.Timber.i("✅ Hold acquired: ${result.data.holdId}")
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.BROADCAST_HOLD_SUCCESS,
                        status = BroadcastStatus.SUCCESS,
                        attrs = mapOf(
                            "broadcastId" to broadcast.broadcastId,
                            "holdId" to result.data.holdId
                        )
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
                    timber.log.Timber.e("❌ Hold failed: ${result.message}")
                    BroadcastTelemetry.record(
                        stage = BroadcastStage.BROADCAST_HOLD_FAIL,
                        status = BroadcastStatus.FAILED,
                        reason = result.message,
                        attrs = mapOf(
                            "broadcastId" to broadcast.broadcastId,
                            "vehicleType" to vehicleType,
                            "vehicleSubtype" to vehicleSubtype
                        )
                    )
                    // Detect cancelled/expired order — dismiss overlay instead of retry
                    val isCancelled = result.message.contains("cancelled", ignoreCase = true)
                        || result.message.contains("no longer exists", ignoreCase = true)
                        || result.message.contains("expired", ignoreCase = true)
                    if (isCancelled) {
                        Toast.makeText(context, "This request was cancelled", Toast.LENGTH_SHORT).show()
                        BroadcastOverlayManager.removeEverywhere(broadcast.broadcastId)
                        BroadcastOverlayManager.dismissOverlay()
                    } else {
                        truckHoldStates = truckHoldStates + (key to TruckHoldState(
                            vehicleType = vehicleType,
                            vehicleSubtype = vehicleSubtype,
                            quantity = quantity,
                            status = TruckHoldStatus.FAILED,
                            isHolding = false
                        ))
                        Toast.makeText(context, "Failed: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
                is BroadcastResult.Loading -> {
                    // Loading state - already handled by isHolding flag
                }
            }
        }
    }
    
    // =========================================================================
    // REJECT TRUCK - Local blur only (truck stays in pool for others)
    // =========================================================================
    fun handleRejectTruck(vehicleType: String, vehicleSubtype: String) {
        val key = "$vehicleType|$vehicleSubtype"
        timber.log.Timber.i("❌ Rejected: $vehicleType $vehicleSubtype")
        
        truckHoldStates = truckHoldStates + (key to TruckHoldState(
            vehicleType = vehicleType,
            vehicleSubtype = vehicleSubtype,
            quantity = 0,
            status = TruckHoldStatus.REJECTED,
            isHolding = false
        ))
    }
    
    // =========================================================================
    // SUBMIT - Proceed with accepted trucks only
    // =========================================================================
    fun handleSubmit() {
        currentBroadcast?.let { broadcast ->
            timber.log.Timber.i("📤 Submit: ${acceptedTrucks.size} types, $totalAcceptedQuantity trucks")
            
            BroadcastOverlayManager.acceptCurrentBroadcast()
            
            // Pass hold info to next screen via notes field
            val holdInfo = acceptedTrucks.joinToString(";") { 
                "${it.vehicleType}|${it.vehicleSubtype}|${it.quantity}|${it.holdId ?: ""}" 
            }
            
            onAccept(broadcast.copy(notes = holdInfo))
        }
    }
    
    // =========================================================================
    // DISMISS - Release all holds
    // =========================================================================
    fun handleDismiss() {
        scope.launch {
            truckHoldStates.values
                .filter { it.holdId != null && it.status == TruckHoldStatus.ACCEPTED }
                .forEach { state ->
                    timber.log.Timber.i("🔓 Releasing: ${state.holdId}")
                    broadcastRepository.releaseHold(state.holdId!!)
                }
        }
        
        currentBroadcast?.let { broadcast ->
            BroadcastOverlayManager.rejectCurrentBroadcast()
            onReject(broadcast)
        }
    }
    
    // Animate visibility
    AnimatedVisibility(
        visible = isVisible && currentBroadcast != null,
        enter = fadeIn(tween(BroadcastUiTiming.DISMISS_ENTER_MS)) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut(tween(BroadcastUiTiming.DISMISS_EXIT_MS)) + slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(BroadcastUiTiming.DISMISS_EXIT_MS)
        )
    ) {
        currentBroadcast?.let { broadcast ->
            Dialog(
                onDismissRequest = { handleDismiss() },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Main content (blurred when dismissed)
                    BroadcastOverlayContentNew(
                        broadcast = broadcast,
                        remainingSeconds = remainingSeconds,
                        currentIndex = currentIndex,
                        totalCount = totalCount,
                        pendingQueueCount = queueSize,
                        truckHoldStates = truckHoldStates,
                        isSubmitEnabled = isSubmitEnabled && dismissInfo == null,
                        totalAcceptedQuantity = totalAcceptedQuantity,
                        hasAnyHolding = hasAnyHolding,
                        onAcceptTruck = ::handleAcceptTruck,
                        onRejectTruck = ::handleRejectTruck,
                        onSubmit = ::handleSubmit,
                        onDismiss = ::handleDismiss,
                        onPrevious = { BroadcastOverlayManager.showPreviousBroadcast() },
                        onNext = { BroadcastOverlayManager.showNextBroadcast() }
                    )
                    
                    // ======== DISMISS OVERLAY (blur + message) ========
                    AnimatedVisibility(
                        visible = dismissInfo != null,
                        enter = fadeIn(tween(BroadcastUiTiming.DISMISS_ENTER_MS)),
                        exit = fadeOut(tween(BroadcastUiTiming.DISMISS_EXIT_MS))
                    ) {
                        val activeDismissInfo = dismissInfo
                        if (activeDismissInfo != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.94f))
                                    // CRITICAL FIX: Consume all pointer input so taps don't fall
                                    // through to accept/reject buttons behind this overlay.
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { /* consume taps — intentional no-op */ },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = when (activeDismissInfo.reason) {
                                            "customer_cancelled" -> Icons.Default.Cancel
                                            "fully_filled" -> Icons.Default.CheckCircle
                                            else -> Icons.Default.Schedule
                                        },
                                        contentDescription = null,
                                        tint = when (activeDismissInfo.reason) {
                                            "customer_cancelled" -> RapidoBlack
                                            "fully_filled" -> RapidoBlack
                                            else -> RapidoYellow
                                        },
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // Title
                                    Text(
                                        text = when (activeDismissInfo.reason) {
                                            "customer_cancelled" -> "ORDER CANCELLED"
                                            "fully_filled" -> "FULLY ASSIGNED"
                                            else -> "ORDER EXPIRED"
                                        },
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = when (activeDismissInfo.reason) {
                                            "customer_cancelled" -> RapidoBlack
                                            "fully_filled" -> RapidoBlack
                                            else -> RapidoYellow
                                        },
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    // Message
                                    Text(
                                        text = activeDismissInfo.message,
                                        fontSize = 16.sp,
                                        color = Color.Black,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Medium
                                    )
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
 * =============================================================================
 * RAPIDO STYLE BROADCAST OVERLAY
 * =============================================================================
 * 
 * DESIGN PRINCIPLES:
 * - Yellow background with Bold Black text
 * - Simple, clean, professional
 * - No childish colors
 * - Clear hierarchy
 * - Easy to read at a glance
 * =============================================================================
 */
@Composable
private fun BroadcastOverlayContent(
    broadcast: BroadcastTrip,
    remainingSeconds: Int,
    currentIndex: Int,
    totalCount: Int,
    onAcceptTruck: (vehicleType: String, vehicleSubtype: String, quantity: Int) -> Unit,
    onRejectTruck: (vehicleType: String, vehicleSubtype: String) -> Unit,
    onDismiss: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val showNavigation = totalCount > 1
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RapidoYellow)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // ============== HEADER - Yellow with Black text ==============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Top row: Close | Timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(RapidoBlack.copy(alpha = 0.1f), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = RapidoBlack, modifier = Modifier.size(24.dp))
                    }
                    
                    // Timer - Bold and prominent
                    Row(
                        modifier = Modifier
                            .background(RapidoBlack, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatTime(remainingSeconds),
                            color = RapidoYellow,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Title - Bold Black
                Text(
                    "NEW RIDE",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = RapidoBlack,
                    letterSpacing = 1.sp
                )
                
                // Navigation (if multiple broadcasts)
                if (showNavigation) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(RapidoBlack.copy(alpha = 0.1f), CircleShape)
                                .clickable { onPrevious() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ChevronLeft, "Previous", tint = RapidoBlack)
                        }
                        
                        Text(
                            "  ${currentIndex + 1} / $totalCount  ",
                            fontWeight = FontWeight.Bold,
                            color = RapidoBlack,
                            fontSize = 14.sp
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(RapidoBlack.copy(alpha = 0.1f), CircleShape)
                                .clickable { onNext() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ChevronRight, "Next", tint = RapidoBlack)
                        }
                    }
                }
            }
            
            // ============== MAIN CONTENT ==============
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ========== FARE - Big and Bold ==========
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "#${broadcast.broadcastId.takeLast(6).uppercase()}",
                                fontSize = 12.sp,
                                color = RapidoGray,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                broadcast.customerName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = RapidoBlack
                            )
                        }
                        
                        // Fare - Yellow background
                        Box(
                            modifier = Modifier
                                .background(RapidoYellow, RoundedCornerShape(12.dp))
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                "₹${String.format("%,.0f", broadcast.totalFare)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = RapidoBlack
                            )
                        }
                    }
                }
                
                // ========== ROUTE - Clean and Simple ==========
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RapidoLightGray, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        val hasRoutePoints = broadcast.routePoints.isNotEmpty() && broadcast.routePoints.size > 2
                        val hasRouteBreakdown = broadcast.hasRouteBreakdown
                        
                        if (hasRoutePoints) {
                            // Multiple stops
                            broadcast.routePoints.forEachIndexed { index, point ->
                                val isLast = index == broadcast.routePoints.size - 1
                                
                                Row(verticalAlignment = Alignment.Top) {
                                    // Dot
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(20.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(RapidoBlack, CircleShape)
                                        )
                                        if (!isLast) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(40.dp)
                                                    .background(RapidoBlack.copy(alpha = 0.3f))
                                            )
                                        }
                                    }
                                    
                                    Spacer(Modifier.width(12.dp))
                                    
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            point.displayName.uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RapidoGray,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            point.address,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RapidoBlack,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        // Leg info
                                        if (!isLast && hasRouteBreakdown) {
                                            val leg = broadcast.routeBreakdown.getLegAt(index)
                                            if (leg != null) {
                                                Text(
                                                    "${leg.distanceKm} km • ${leg.durationFormatted}",
                                                    fontSize = 12.sp,
                                                    color = RapidoGray
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                if (!isLast) Spacer(Modifier.height(4.dp))
                            }
                        } else {
                            // Simple pickup → drop
                            // Pickup
                            Row(verticalAlignment = Alignment.Top) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(20.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(RapidoBlack, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(40.dp)
                                            .background(RapidoBlack.copy(alpha = 0.3f))
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("PICKUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RapidoGray, letterSpacing = 1.sp)
                                    Text(
                                        broadcast.pickupLocation.address,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RapidoBlack,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(4.dp))
                            
                            // Drop
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(RapidoBlack, CircleShape)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("DROP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RapidoGray, letterSpacing = 1.sp)
                                    Text(
                                        broadcast.dropLocation.address,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RapidoBlack,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                
                // ========== DISTANCE & TIME - Bold Summary ==========
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RapidoYellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DISTANCE", fontSize = 10.sp, color = RapidoGray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(
                                if (broadcast.hasRouteBreakdown) "${broadcast.routeBreakdown.totalDistanceKm} km" 
                                else "${broadcast.distance.toInt()} km",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = RapidoBlack
                            )
                        }
                        
                        Box(Modifier.width(1.dp).height(40.dp).background(RapidoBlack.copy(alpha = 0.2f)))
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TIME", fontSize = 10.sp, color = RapidoGray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(
                                if (broadcast.hasRouteBreakdown) broadcast.routeBreakdown.totalDurationFormatted 
                                else "${broadcast.estimatedDuration} min",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = RapidoBlack
                            )
                        }
                        
                        if (broadcast.totalStops > 0) {
                            Box(Modifier.width(1.dp).height(40.dp).background(RapidoBlack.copy(alpha = 0.2f)))
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("STOPS", fontSize = 10.sp, color = RapidoGray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(
                                    "${broadcast.totalStops}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RapidoBlack
                                )
                            }
                        }
                    }
                }
                
                // ========== EMBEDDED MAP (toggle-able via feature flag) ==========
                if (BroadcastFeatureFlagsRegistry.current().broadcastOverlayMapEnabled) {
                    item {
                        BroadcastMiniRouteMapCard(
                            broadcast = broadcast,
                            modifier = Modifier.fillMaxWidth(),
                            renderMode = com.weelo.logistics.ui.transporter.BroadcastCardMapRenderMode.STATIC_OVERLAY,
                            mapHeight = 160.dp
                        )
                    }
                }
                
                // ========== DIRECTIONS BUTTONS - PROMINENT ==========
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Primary Navigate Button - Large and Yellow
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Open Google Maps with turn-by-turn navigation
                                    val pickupLat = broadcast.pickupLocation.latitude
                                    val pickupLng = broadcast.pickupLocation.longitude
                                    val dropLat = broadcast.dropLocation.latitude
                                    val dropLng = broadcast.dropLocation.longitude
                                    
                                    // Build waypoints string for intermediate stops
                                    val waypointsStr = if (broadcast.hasIntermediateStops) {
                                        broadcast.intermediateStops.joinToString("|") { 
                                            "${it.latitude},${it.longitude}"
                                        }
                                    } else ""
                                    
                                    val uri = if (waypointsStr.isNotEmpty()) {
                                        Uri.parse("google.navigation:q=$dropLat,$dropLng&waypoints=$waypointsStr&mode=d")
                                    } else {
                                        Uri.parse("google.navigation:q=$dropLat,$dropLng&mode=d")
                                    }
                                    
                                    val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                        setPackage("com.google.android.apps.maps")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (e: Exception) {
                                        // Fallback to browser if Google Maps not installed
                                        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$pickupLat,$pickupLng&destination=$dropLat,$dropLng&travelmode=driving")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    }
                                }
                                .background(RapidoYellow, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Navigation, 
                                contentDescription = "Navigate",
                                tint = RapidoBlack, 
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "NAVIGATE",
                                color = RapidoBlack,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        // Secondary View Route Button - Outlined
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, RapidoBlack, RoundedCornerShape(12.dp))
                                .clickable {
                                    val origin = Uri.encode(broadcast.pickupLocation.address)
                                    val destination = Uri.encode(broadcast.dropLocation.address)
                                    val waypoints = if (broadcast.hasIntermediateStops) {
                                        broadcast.intermediateStops.map { Uri.encode(it.address) }.joinToString("|")
                                    } else ""
                                    
                                    val uri = if (waypoints.isNotEmpty()) {
                                        Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination&waypoints=$waypoints&travelmode=driving")
                                    } else {
                                        Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination&travelmode=driving")
                                    }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Directions, 
                                contentDescription = "View Route",
                                tint = RapidoBlack, 
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "VIEW FULL ROUTE",
                                color = RapidoBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                
                // ========== GOODS INFO ==========
                item {
                    Text(
                        "${broadcast.goodsType}${broadcast.weight?.let { " • $it" } ?: ""}",
                        fontSize = 12.sp,
                        color = RapidoGray
                    )
                }
                
                // ========== TRUCKS SECTION ==========
                item {
                    Text(
                        "SELECT TRUCKS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RapidoBlack,
                        letterSpacing = 1.sp
                    )
                }
                
                // ============== TRUCK TYPES (Multi-Truck UI) ==============
                // PERSONALIZED: Each transporter sees their capacity (trucksYouCanProvide)
                if (broadcast.requestedVehicles.isNotEmpty()) {
                    items(broadcast.requestedVehicles.filter { it.remainingCount > 0 }) { vehicle ->
                        TruckTypeCard(
                            vehicle = vehicle,
                            // Pass personalized max from backend
                            trucksYouCanProvide = broadcast.trucksYouCanProvide,
                            yourAvailableTrucks = broadcast.yourAvailableTrucks,
                            onAccept = { quantity ->
                                onAcceptTruck(vehicle.vehicleType, vehicle.vehicleSubtype, quantity)
                            },
                            onReject = {
                                onRejectTruck(vehicle.vehicleType, vehicle.vehicleSubtype)
                            }
                        )
                    }
                } else {
                    // Legacy single truck type - use vehicleTypesDisplay as fallback
                    item {
                        LegacySingleTruckCard(
                            broadcast = broadcast,
                            onAccept = { quantity ->
                                onAcceptTruck(
                                    broadcast.vehicleTypesDisplay,
                                    "",
                                    quantity
                                )
                            },
                            onReject = {
                                onRejectTruck(
                                    broadcast.vehicleTypesDisplay,
                                    ""
                                )
                            }
                        )
                    }
                }
                
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

/**
 * =============================================================================
 * NEW BROADCAST OVERLAY CONTENT - Professional Dark Theme with Hold System
 * =============================================================================
 */
@Composable
private fun BroadcastOverlayContentNew(
    broadcast: BroadcastTrip,
    remainingSeconds: Int,
    currentIndex: Int,
    totalCount: Int,
    pendingQueueCount: Int,
    truckHoldStates: Map<String, TruckHoldState>,
    isSubmitEnabled: Boolean,
    totalAcceptedQuantity: Int,
    hasAnyHolding: Boolean,
    onAcceptTruck: (vehicleType: String, vehicleSubtype: String, quantity: Int) -> Unit,
    onRejectTruck: (vehicleType: String, vehicleSubtype: String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val showNavigation = totalCount > 1
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RapidoDarkGray)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // ============== HEADER - Dark with Yellow accents ==============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RapidoDarkGray)
                    .padding(16.dp)
            ) {
                // Top row: Close | Timer | Direction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(RapidoDarkGray, CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = RapidoWhite, modifier = Modifier.size(24.dp))
                    }
                    
                    // Timer - Yellow text on dark
                    Row(
                        modifier = Modifier
                            .background(RapidoYellow, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = RapidoBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatTime(remainingSeconds),
                            color = RapidoBlack,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }
                    
                    // Directions button - Opens Google Maps
                    Box(
                        modifier = Modifier
                            .background(RapidoBlue, RoundedCornerShape(12.dp))
                            .clickable {
                                val pickup = broadcast.pickupLocation
                                val drop = broadcast.dropLocation
                                val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${pickup.latitude},${pickup.longitude}&destination=${drop.latitude},${drop.longitude}&travelmode=driving")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Navigation, null, tint = RapidoWhite, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("DIRECTIONS", color = RapidoWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Title
                Text(
                    "NEW BOOKING",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = RapidoYellow,
                    letterSpacing = 2.sp
                )

                if (pendingQueueCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "+$pendingQueueCount waiting",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RapidoLightGray
                    )
                }
                
                // Navigation (if multiple broadcasts) - Smooth animated carousel
                if (showNavigation) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(RapidoDarkGray.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // Previous button with ripple effect
                        IconButton(
                            onClick = onPrevious,
                            modifier = Modifier
                                .size(36.dp)
                                .background(RapidoGray.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                "Previous",
                                tint = RapidoYellow,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Spacer(Modifier.width(8.dp))
                        
                        // Page indicator dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(minOf(totalCount, 5)) { index ->
                                val actualIndex = if (totalCount <= 5) index else {
                                    // Show dots around current index for large counts
                                    when {
                                        currentIndex < 2 -> index
                                        currentIndex > totalCount - 3 -> totalCount - 5 + index
                                        else -> currentIndex - 2 + index
                                    }
                                }
                                val isActive = actualIndex == currentIndex
                                
                                // Animated dot
                                val dotSize by animateDpAsState(
                                    targetValue = if (isActive) 10.dp else 6.dp,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "dotSize"
                                )
                                val dotColor by animateColorAsState(
                                    targetValue = if (isActive) RapidoYellow else RapidoMediumGray,
                                    animationSpec = tween(200),
                                    label = "dotColor"
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .size(dotSize)
                                        .background(dotColor, CircleShape)
                                )
                            }
                        }
                        
                        Spacer(Modifier.width(8.dp))
                        
                        // Counter text
                        Text(
                            "${currentIndex + 1}/$totalCount",
                            color = RapidoLightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(Modifier.width(8.dp))
                        
                        // Next button with ripple effect
                        IconButton(
                            onClick = onNext,
                            modifier = Modifier
                                .size(36.dp)
                                .background(RapidoGray.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                "Next",
                                tint = RapidoYellow,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            
            // ============== MAIN CONTENT - SMOOTH SCROLLING ==============
            // Industry-style smooth scrolling like Rapido/Uber
            val listState = rememberLazyListState()
            
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(RapidoDarkGray, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Smooth fling behavior for industry-style scrolling
                flingBehavior = ScrollableDefaults.flingBehavior()
            ) {
                // ========== ORDER INFO ==========
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "#${broadcast.broadcastId.takeLast(6).uppercase()}",
                                fontSize = 11.sp,
                                color = RapidoMediumGray,
                                letterSpacing = 1.sp
                            )
                            Text(
                                broadcast.customerName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = RapidoWhite
                            )
                        }
                        
                        // Total Fare
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TOTAL FARE", fontSize = 10.sp, color = RapidoMediumGray)
                            Text(
                                "₹${"%,.0f".format(broadcast.totalFare)}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = RapidoYellow
                            )
                        }
                    }
                }

                item {
                    val hasCoords = broadcast.pickupLocation.latitude != 0.0 &&
                        broadcast.pickupLocation.longitude != 0.0 &&
                        broadcast.dropLocation.latitude != 0.0 &&
                        broadcast.dropLocation.longitude != 0.0
                    val safeMapEnabled = BroadcastFeatureFlagsRegistry.current().captainOverlaySafeRenderEnabled

                    if (safeMapEnabled && hasCoords) {
                        BroadcastMiniRouteMapCard(
                            broadcast = broadcast,
                            title = "Route map",
                            subtitle = "${broadcast.distance.toInt()} km",
                            mapHeight = 136.dp,
                            renderMode = BroadcastCardMapRenderMode.STATIC_OVERLAY
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = BroadcastUiTokens.CardMutedBackground,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Route map unavailable",
                                    color = BroadcastUiTokens.SecondaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${broadcast.pickupLocation.address} → ${broadcast.dropLocation.address}",
                                    color = BroadcastUiTokens.TertiaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                
                // ========== ROUTE (Compact) ==========
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        // Pickup
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(RapidoGreen, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("PICKUP", fontSize = 9.sp, color = RapidoGreen, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(
                                    broadcast.pickupLocation.address,
                                    fontSize = 13.sp,
                                    color = RapidoWhite,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        // Connector line
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .width(2.dp)
                                .height(20.dp)
                                .background(RapidoGray)
                        )
                        
                        // Drop
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(RapidoRed, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("DROP", fontSize = 9.sp, color = RapidoRed, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(
                                    broadcast.dropLocation.address,
                                    fontSize = 13.sp,
                                    color = RapidoWhite,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        // Distance
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                "${broadcast.distance} KM",
                                fontSize = 12.sp,
                                color = RapidoYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // ========== TRUCK TYPES - Per-Truck Accept/Reject ==========
                item {
                    Text(
                        "SELECT TRUCKS TO PROVIDE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RapidoMediumGray,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                
                // Truck cards
                val vehicles = if (broadcast.requestedVehicles.isNotEmpty()) {
                    broadcast.requestedVehicles
                } else {
                    @Suppress("DEPRECATION")
                    listOf(RequestedVehicle(
                        vehicleType = broadcast.vehicleType?.name ?: "Truck",
                        vehicleSubtype = "",
                        count = broadcast.totalTrucksNeeded,
                        filledCount = broadcast.trucksFilledSoFar,
                        farePerTruck = broadcast.farePerTruck,
                        capacityTons = 0.0
                    ))
                }
                
                items(vehicles) { vehicle ->
                    val key = "${vehicle.vehicleType}|${vehicle.vehicleSubtype}"
                    val holdState = truckHoldStates[key]
                    val maxQty = minOf(
                        broadcast.trucksYouCanProvide.takeIf { it > 0 } ?: vehicle.remainingCount,
                        vehicle.remainingCount
                    ).coerceAtLeast(1)
                    
                    TruckTypeCardNew(
                        vehicle = vehicle,
                        maxQuantity = maxQty,
                        holdState = holdState,
                        onAccept = { qty -> onAcceptTruck(vehicle.vehicleType, vehicle.vehicleSubtype, qty) },
                        onReject = { onRejectTruck(vehicle.vehicleType, vehicle.vehicleSubtype) }
                    )
                }
            }
            
            // ============== SUBMIT BUTTON - Fixed at bottom ==============
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BroadcastUiTokens.CardBackground)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = isSubmitEnabled && !hasAnyHolding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSubmitEnabled) RapidoYellow else RapidoGray,
                        contentColor = RapidoBlack,
                        disabledContainerColor = RapidoGray,
                        disabledContentColor = RapidoMediumGray
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (hasAnyHolding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = RapidoBlack,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("HOLDING...", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    } else if (isSubmitEnabled) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SUBMIT ($totalAcceptedQuantity TRUCK${if (totalAcceptedQuantity > 1) "S" else ""})",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Text(
                            "SELECT TRUCKS TO CONTINUE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = RapidoMediumGray
                        )
                    }
                }
            }
        }
    }
}

/**
 * =============================================================================
 * NEW TRUCK TYPE CARD - Dark Theme with Hold States
 * =============================================================================
 */
@Composable
private fun TruckTypeCardNew(
    vehicle: RequestedVehicle,
    maxQuantity: Int,
    holdState: TruckHoldState?,
    onAccept: (quantity: Int) -> Unit,
    onReject: () -> Unit
) {
    var selectedQuantity by remember { mutableStateOf(1) }
    
    val isRejected = holdState?.status == TruckHoldStatus.REJECTED
    val isAccepted = holdState?.status == TruckHoldStatus.ACCEPTED
    val isHolding = holdState?.isHolding == true
    val isFailed = holdState?.status == TruckHoldStatus.FAILED
    @Suppress("UNUSED_VARIABLE")
    val isPending = holdState == null || holdState.status == TruckHoldStatus.PENDING && !isHolding
    
    // Card opacity for rejected state
    val cardAlpha = if (isRejected) 0.4f else 1f
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .background(
                when {
                    isAccepted -> RapidoGreen.copy(alpha = 0.15f)
                    isFailed -> RapidoRed.copy(alpha = 0.15f)
                    else -> RapidoBlack
                },
                RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isAccepted) 2.dp else 1.dp,
                color = when {
                    isAccepted -> RapidoGreen
                    isFailed -> RapidoRed
                    else -> RapidoGray
                },
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        // Header: Vehicle Type & Fare
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalShipping,
                        null,
                        tint = if (isAccepted) RapidoGreen else RapidoYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        vehicle.vehicleType.uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = RapidoWhite
                    )
                }
                if (vehicle.vehicleSubtype.isNotBlank()) {
                    Text(
                        vehicle.vehicleSubtype,
                        fontSize = 12.sp,
                        color = RapidoLightGray,
                        modifier = Modifier.padding(start = 28.dp)
                    )
                }
            }
            
            // Fare per truck
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "₹${"%,.0f".format(vehicle.farePerTruck)}",
                    fontWeight = FontWeight.Black,
                    color = RapidoYellow,
                    fontSize = 18.sp
                )
                Text("/truck", fontSize = 10.sp, color = RapidoMediumGray)
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Status badge or controls
        if (isRejected) {
            // Rejected state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RapidoRed.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("SKIPPED", color = RapidoRed, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        } else if (isAccepted) {
            // Accepted state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RapidoGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = RapidoGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${holdState?.quantity ?: 1} TRUCK${if ((holdState?.quantity ?: 1) > 1) "S" else ""} HELD",
                        color = RapidoGreen,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        } else if (isFailed) {
            // Failed state - allow retry
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RapidoRed.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Not available \u2013 try another request", color = RapidoRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                // Show controls again for retry
                TruckControlsRow(
                    selectedQuantity = selectedQuantity,
                    maxQuantity = maxQuantity,
                    isHolding = false,
                    onQuantityChange = { selectedQuantity = it },
                    onAccept = { onAccept(selectedQuantity) },
                    onReject = onReject
                )
            }
        } else {
            // Pending state - show controls
            TruckControlsRow(
                selectedQuantity = selectedQuantity,
                maxQuantity = maxQuantity,
                isHolding = isHolding,
                onQuantityChange = { selectedQuantity = it },
                onAccept = { onAccept(selectedQuantity) },
                onReject = onReject
            )
        }
        
        // Total fare if multiple
        if (!isRejected && !isAccepted && selectedQuantity > 1) {
            Spacer(Modifier.height(8.dp))
            Text(
                "TOTAL: ₹${"%,.0f".format(vehicle.farePerTruck * selectedQuantity)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RapidoYellow,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * Truck Controls Row - Quantity selector + Accept/Reject buttons
 */
@Composable
private fun TruckControlsRow(
    selectedQuantity: Int,
    maxQuantity: Int,
    isHolding: Boolean,
    onQuantityChange: (Int) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quantity Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(RapidoDarkGray, RoundedCornerShape(8.dp))
                .border(1.dp, RapidoGray, RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            // Minus button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (selectedQuantity > 1) RapidoYellow else RapidoGray,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(enabled = selectedQuantity > 1 && !isHolding) { 
                        onQuantityChange(selectedQuantity - 1) 
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "−",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedQuantity > 1) RapidoBlack else RapidoMediumGray
                )
            }
            
            // Quantity display
            Text(
                "$selectedQuantity",
                modifier = Modifier.padding(horizontal = 20.dp),
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = RapidoWhite
            )
            
            // Plus button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (selectedQuantity < maxQuantity) RapidoYellow else RapidoGray,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(enabled = selectedQuantity < maxQuantity && !isHolding) { 
                        onQuantityChange(selectedQuantity + 1) 
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedQuantity < maxQuantity) RapidoBlack else RapidoMediumGray
                )
            }
        }
        
        // Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Reject/Skip button
            Box(
                modifier = Modifier
                    .border(2.dp, RapidoRed, RoundedCornerShape(8.dp))
                    .clickable(enabled = !isHolding) { onReject() }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    "SKIP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = RapidoRed,
                    letterSpacing = 1.sp
                )
            }
            
            // Accept button
            Box(
                modifier = Modifier
                    .background(
                        if (isHolding) RapidoGray else RapidoYellow,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = !isHolding) { onAccept() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isHolding) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = RapidoWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("HOLD", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RapidoWhite)
                    }
                } else {
                    Text(
                        "ACCEPT",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = RapidoBlack,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * TRUCK TYPE CARD - Rapido Style with Personalized Quantity (Legacy)
 * Yellow & Black, Clean and Bold
 * 
 * PERSONALIZED:
 * - maxQuantity is MIN(transporterAvailable, trucksStillNeeded)
 * - Each transporter sees their capacity, not the total order
 */
@Composable
@Suppress("UNUSED_PARAMETER")
private fun TruckTypeCard(
    vehicle: RequestedVehicle,
    trucksYouCanProvide: Int = 0,  // Personalized max from backend
    yourAvailableTrucks: Int = 0,  // How many trucks transporter has
    onAccept: (quantity: Int) -> Unit,
    onReject: () -> Unit
) {
    var selectedQuantity by remember { mutableStateOf(1) }
    
    // Use personalized max if available, otherwise fall back to remainingCount
    val maxQuantity = if (trucksYouCanProvide > 0) {
        trucksYouCanProvide
    } else {
        vehicle.remainingCount
    }
    
    LaunchedEffect(maxQuantity) {
        if (selectedQuantity > maxQuantity) {
            selectedQuantity = maxQuantity.coerceAtLeast(1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RapidoLightGray, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Header: Vehicle Type & Fare
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    vehicle.vehicleType.uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = RapidoBlack
                )
                if (vehicle.vehicleSubtype.isNotBlank()) {
                    Text(
                        vehicle.vehicleSubtype,
                        fontSize = 12.sp,
                        color = RapidoGray
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .background(RapidoYellow, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    "₹${String.format("%,.0f", vehicle.farePerTruck)}",
                    fontWeight = FontWeight.Black,
                    color = RapidoBlack,
                    fontSize = 16.sp
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Available badge
        Text(
            "$maxQuantity AVAILABLE",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = RapidoGray,
            letterSpacing = 1.sp
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Bottom Row: Quantity | Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quantity Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, RapidoBlack.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (selectedQuantity > 1) RapidoYellow else RapidoLightGray,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { if (selectedQuantity > 1) selectedQuantity-- },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RapidoBlack)
                }
                
                Text(
                    "$selectedQuantity",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = RapidoBlack
                )
                
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (selectedQuantity < maxQuantity) RapidoYellow else RapidoLightGray,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { if (selectedQuantity < maxQuantity) selectedQuantity++ },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RapidoBlack)
                }
            }
            
            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Skip - Outlined
                Box(
                    modifier = Modifier
                        .border(2.dp, RapidoBlack, RoundedCornerShape(8.dp))
                        .clickable { onReject() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "SKIP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = RapidoBlack,
                        letterSpacing = 1.sp
                    )
                }
                
                // Accept - Filled Yellow
                Box(
                    modifier = Modifier
                        .background(RapidoYellow, RoundedCornerShape(8.dp))
                        .clickable { onAccept(selectedQuantity) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        "ACCEPT",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = RapidoBlack,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        
        // Total if multiple selected
        if (selectedQuantity > 1) {
            Spacer(Modifier.height(8.dp))
            Text(
                "TOTAL: ₹${String.format("%,.0f", vehicle.farePerTruck * selectedQuantity)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RapidoBlack,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * Legacy Single Truck Card (for backward compatibility)
 */
@Composable
private fun LegacySingleTruckCard(
    broadcast: BroadcastTrip,
    onAccept: (quantity: Int) -> Unit,
    onReject: () -> Unit
) {
    var selectedQuantity by remember { mutableStateOf(1) }
    val available = broadcast.totalRemainingTrucks
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(RapidoBlack.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocalShipping, null, tint = RapidoBlack, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            broadcast.vehicleTypesDisplay.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$available trucks needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = RapidoGray
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "₹${String.format("%,.0f", broadcast.farePerTruck)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RapidoBlack
                    )
                    Text("/truck", style = MaterialTheme.typography.labelSmall, color = RapidoGray)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Surface, RoundedCornerShape(8.dp))
                        .border(1.dp, Divider, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { if (selectedQuantity > 1) selectedQuantity-- },
                        modifier = Modifier.size(36.dp),
                        enabled = selectedQuantity > 1
                    ) {
                        Icon(Icons.Default.Remove, "Decrease")
                    }
                    Text(
                        selectedQuantity.toString(),
                        modifier = Modifier.widthIn(min = 40.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = { if (selectedQuantity < available) selectedQuantity++ },
                        modifier = Modifier.size(36.dp),
                        enabled = selectedQuantity < available
                    ) {
                        Icon(Icons.Default.Add, "Increase")
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RapidoBlack)
                    ) {
                        Text("REJECT", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onAccept(selectedQuantity) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RapidoYellow,
                            contentColor = RapidoBlack
                        )
                    ) {
                        Text("ACCEPT $selectedQuantity", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Format seconds to M:SS
 */
private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
