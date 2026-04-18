package com.weelo.logistics.ui.driver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.weelo.logistics.BuildConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.weelo.logistics.R
import timber.log.Timber
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import com.weelo.logistics.data.model.TripAssignedNotification
import com.weelo.logistics.data.model.TripLocationInfo
import com.weelo.logistics.ui.ServerDeadlineTimer
import com.weelo.logistics.ui.driver.components.CustomerRatingBadge
import com.weelo.logistics.ui.driver.components.TripETADisplay

// =============================================================================
// CONSTANTS
// =============================================================================
private const val SWIPE_THRESHOLD_DP = 200f
private const val OVERLAY_ENTER_MS = 300
private const val OVERLAY_EXIT_MS = 200
private const val COUNTDOWN_INTERVAL = 1000L

/**
 * =============================================================================
 * DRIVER TRIP REQUEST OVERLAY
 * =============================================================================
 *
 * Full-screen overlay following Uber/Ola/Rapido standards for driver acceptance.
 */
@Composable
fun DriverTripRequestOverlay(
    notification: TripAssignedNotification,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    actionState: ActionState = ActionState.IDLE,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // F-C-27: pin a monotonic deadline from the server-sent expiresAt, then
    // recompute remainingSeconds every tick. Doze-safe (SystemClock.elapsedRealtime
    // keeps ticking in sleep) and immune to wall-clock jumps.
    // If expiresAt is missing/unparseable, fall back to the DTO's remainingSeconds
    // anchored at screen-entry elapsed-realtime (same semantics as before).
    //
    // F-C-31: the progress ring denominator must be STABLE for the whole
    // assignment. Before this fix we read `notification.remainingSeconds` —
    // a live getter that re-derives its value from System.currentTimeMillis()
    // every call — inside `remember { ... }` with NO key. The remember block
    // captured the getter at entry time, but the getter's VALUE decayed as
    // real time ticked, producing an inverted progress ring. The fix is to
    // `remember(assignmentId)` and prefer the stable DTO field
    // `driverAcceptTimeoutSeconds` (server-sourced, never decays) with
    // `BuildConfig.DRIVER_ACCEPT_TIMEOUT_SECONDS` as the last-resort fallback.
    //
    // The new path is gated by `BuildConfig.FF_DRIVER_TOTAL_SECONDS_FROM_DTO`
    // so a rollback is a single flag flip.
    val totalSeconds = if (BuildConfig.FF_DRIVER_TOTAL_SECONDS_FROM_DTO) {
        remember(notification.assignmentId) {
            notification.driverAcceptTimeoutSeconds
                ?: BuildConfig.DRIVER_ACCEPT_TIMEOUT_SECONDS
        }
    } else {
        remember(notification.assignmentId) { notification.remainingSeconds }
    }
    val deadlineElapsedMs = remember(notification.assignmentId) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val expiresAtWall = notification.expiresAt?.let {
            runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
        }
        if (expiresAtWall != null) {
            ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
                expiresAtWallMs = expiresAtWall,
                nowWallMs = System.currentTimeMillis(),
                nowElapsedMs = nowElapsed
            )
        } else {
            nowElapsed + (notification.remainingSeconds * 1000L)
        }
    }
    var remainingSeconds by remember(notification.assignmentId) {
        mutableStateOf(
            ServerDeadlineTimer.remainingSecondsFromDeadline(
                deadlineElapsedMs = deadlineElapsedMs,
                nowElapsedMs = SystemClock.elapsedRealtime()
            )
        )
    }
    var swipeOffset by remember { mutableStateOf(0f) }
    var isAccepting by remember { mutableStateOf(false) }
    var isDeclining by remember { mutableStateOf(false) }

    // Sync with external actionState (from manager)
    val isProcessing = actionState != ActionState.IDLE

    // Industry Standard: Grab Mobile/Google - Block timer immediately on swipe
    // When driver swipes accept/decline, set this flag BEFORE API call
    // LaunchedEffect key includes it -> effect restarts -> early return blocks timer
    var isSwipeComplete by remember { mutableStateOf(false) }

    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

    // Countdown timer (F-C-27 — server-deadline recompute every tick, doze-safe)
    //
    // F-C-32: the prior implementation keyed the effect on
    // `(assignmentId, isProcessing, isSwipeComplete)`. Every gate flip
    // restarted the coroutine, which caused:
    //   - The time-up auto-decline firing twice when isProcessing flipped
    //     between "about to accept" and "accepting" within the same tick.
    //   - A dropped tick each time the user partially dragged then released
    //     (isSwipeComplete ticked in/out).
    //
    // The fix is a single-key effect (assignmentId) plus `rememberUpdatedState`
    // for the gates — reads see the latest value each loop iteration without
    // restarting. The decline on timeout runs inside `withContext(NonCancellable)`
    // so an eager composable dispose cannot cancel the API call.
    //
    // Gated behind `BuildConfig.FF_DRIVER_TIMER_STABLE_KEY` (default OFF).
    val processingRef = rememberUpdatedState(isProcessing)
    val swipeCompleteRef = rememberUpdatedState(isSwipeComplete)
    if (BuildConfig.FF_DRIVER_TIMER_STABLE_KEY) {
        LaunchedEffect(notification.assignmentId) {
            while (remainingSeconds > 0) {
                // Latest gate values, read without restarting the effect.
                if (processingRef.value || swipeCompleteRef.value) return@LaunchedEffect
                delay(COUNTDOWN_INTERVAL)
                remainingSeconds = ServerDeadlineTimer.remainingSecondsFromDeadline(
                    deadlineElapsedMs = deadlineElapsedMs,
                    nowElapsedMs = SystemClock.elapsedRealtime()
                )
                Timber.tag("Overlay").i("⏰ Countdown: ${remainingSeconds}s")
            }
            if (remainingSeconds <= 0) {
                Timber.tag("Overlay").i("⏰ Timeout, declining: ${notification.assignmentId}")
                withContext(NonCancellable) {
                    onDecline(notification.assignmentId)
                }
            }
        }
    } else {
        // Legacy path — retained for flag-off rollback. Same behavior as before
        // the F-C-32 fix (3-key effect, no NonCancellable wrapper).
        LaunchedEffect(notification.assignmentId, isProcessing, isSwipeComplete) {
            if (isProcessing || isSwipeComplete) return@LaunchedEffect

            while (remainingSeconds > 0) {
                delay(COUNTDOWN_INTERVAL)
                remainingSeconds = ServerDeadlineTimer.remainingSecondsFromDeadline(
                    deadlineElapsedMs = deadlineElapsedMs,
                    nowElapsedMs = SystemClock.elapsedRealtime()
                )
                Timber.tag("Overlay").i("⏰ Countdown: ${remainingSeconds}s")
            }

            if (remainingSeconds <= 0) {
                Timber.tag("Overlay").i("⏰ Timeout, declining: ${notification.assignmentId}")
                onDecline(notification.assignmentId)
            }
        }
    }

    // Play sound on appearance
    LaunchedEffect(Unit) {
        DriverTripRequestSoundService.playTripRequestSound(context)
    }

    // F-C-33: looping alarm requires deterministic teardown. When the
    // overlay leaves the composition (dismissal, navigation, accept/decline
    // complete), stop the alarm so it does not continue playing silently.
    // Before this effect landed the one-shot alarm self-terminated, so a
    // DisposableEffect was unnecessary; with the new loop-enabled service
    // it is mandatory to avoid runaway audio after the overlay closes.
    DisposableEffect(Unit) {
        onDispose {
            DriverTripRequestSoundService.stop()
        }
    }

    // Handle swipe completion - only if not already processing
    LaunchedEffect(swipeOffset) {
        if (isProcessing) return@LaunchedEffect
        when {
            swipeOffset > swipeThresholdPx -> {
                if (!isAccepting) {
                    isAccepting = true
                    // Industry Standard: Set flag IMMEDIATELY to block timer (before API)
                    isSwipeComplete = true
                    onAccept(notification.assignmentId)
                }
            }
            swipeOffset < -swipeThresholdPx -> {
                if (!isDeclining) {
                    isDeclining = true
                    // Industry Standard: Set flag IMMEDIATELY to block timer (before API)
                    isSwipeComplete = true
                    onDecline(notification.assignmentId)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { /* No-op */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = { if (!isProcessing) onDismiss() })
        ) {
            SwipeableCard(
                notification = notification,
                totalSeconds = totalSeconds,
                remainingSeconds = remainingSeconds,
                swipeOffset = swipeOffset,
                onSwipeChange = { newOffset -> if (!isProcessing) swipeOffset = newOffset },
                isAccepting = isAccepting,
                isDeclining = isDeclining,
                isProcessing = isProcessing,
                actionState = actionState,
                onNavigate = onNavigate,
                cardModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Swipeable card with swipe gestures
 */
@Composable
private fun SwipeableCard(
    notification: TripAssignedNotification,
    totalSeconds: Int,
    remainingSeconds: Int,
    swipeOffset: Float,
    onSwipeChange: (Float) -> Unit,
    isAccepting: Boolean,
    isDeclining: Boolean,
    isProcessing: Boolean = false,
    actionState: ActionState = ActionState.IDLE,
    onNavigate: () -> Unit,
    cardModifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

    val offsetAnimation by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 300f
        ),
        label = "swipeOffset"
    )

    val isUrgent = remainingSeconds > 0 && remainingSeconds < 15

    val scale by animateFloatAsState(
        targetValue = if (isUrgent && remainingSeconds % 2 == 0) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "countdownPulse"
    )

    val countdownColor = when {
        remainingSeconds <= 10 -> Color.Red
        remainingSeconds <= 20 -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }

    Card(
        modifier = cardModifier
            .fillMaxWidth()
            .offset { IntOffset(offsetAnimation.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffset.absoluteValue < swipeThresholdPx) {
                            onSwipeChange(0f)
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val maxSwipe = swipeThresholdPx * 1.5f
                        onSwipeChange(
                            (swipeOffset + dragAmount).coerceIn(-maxSwipe, maxSwipe)
                        )
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Card content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .then(if (isProcessing) Modifier.alpha(0.3f) else Modifier)
            ) {
                HeaderRow(totalSeconds, remainingSeconds, countdownColor, scale)

                Spacer(modifier = Modifier.height(16.dp))

                EarningsCard(notification.farePerTruck)

                Spacer(modifier = Modifier.height(16.dp))

                RouteCard(
                    pickup = notification.pickup,
                    drop = notification.drop,
                    distanceKm = notification.distanceKm,
                    onNavigate = onNavigate
                )

                Spacer(modifier = Modifier.height(16.dp))

                DetailsCard(notification)

                // Customer rating + Trip ETA badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomerRatingBadge(rating = notification.customerRating)
                    TripETADisplay(distanceKm = notification.distanceKm)
                }

                Spacer(modifier = Modifier.height(24.dp))

                SwipeIndicator(
                    swipeOffset = swipeOffset,
                    isAccepting = isAccepting,
                    isDeclining = isDeclining,
                    swipeThresholdPx = swipeThresholdPx
                )
            }

            // Loading overlay (centered over card)
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(vertical = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (actionState) {
                                ActionState.ACCEPTING -> stringResource(R.string.accepting_trip)
                                ActionState.DECLINING -> stringResource(R.string.declining_trip)
                                else -> stringResource(R.string.processing)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Header row with countdown timer
 */
@Composable
private fun HeaderRow(
    totalSeconds: Int,
    remainingSeconds: Int,
    countdownColor: Color,
    scale: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocalShipping,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.new_trip_request),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    this.scaleX = scale
                    this.scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            val progress = (remainingSeconds.toFloat() / totalSeconds.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
                color = countdownColor,
                strokeWidth = 5.dp
            )
            Text(
                "${remainingSeconds}s",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = countdownColor
            )
        }
    }
}

/**
 * Earnings card
 */
@Composable
private fun EarningsCard(fare: Double) {
    val fareFormatted = java.text.DecimalFormat("#,##0").format(fare)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.trip_earnings),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "₹",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    fareFormatted,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Route card with pickup, drop, distance
 */
@Composable
private fun RouteCard(
    pickup: TripLocationInfo,
    drop: TripLocationInfo,
    distanceKm: Double,
    onNavigate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.route_details),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LocationRow(
                icon = Icons.Default.TripOrigin,
                iconColor = Color(0xFF4CAF50),
                label = stringResource(R.string.pickup_label).uppercase(),
                address = pickup.address,
                city = pickup.city
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.padding(start = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "${distanceKm.toInt()} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            LocationRow(
                icon = Icons.Default.LocationOn,
                iconColor = Color(0xFFF44336),
                label = stringResource(R.string.drop_label).uppercase(),
                address = drop.address,
                city = drop.city
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.navigate_to_pickup), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Location row with icon and address
 */
@Composable
private fun LocationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    address: String,
    city: String?
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconColor.copy(alpha = 0.1f), CircleShape)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                address,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            city?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

/**
 * Details card with customer and vehicle info
 */
@Composable
private fun DetailsCard(
    notification: TripAssignedNotification
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            notification.customerName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.customer_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }

                val showCallDialog = remember { mutableStateOf(false) }

                if (showCallDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showCallDialog.value = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = { Text(stringResource(R.string.call_customer_dialog_title)) },
                        text = {
                            Text(
                                stringResource(R.string.call_customer_dialog_message, notification.customerName, notification.customerPhone)
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showCallDialog.value = false
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${notification.customerPhone}")
                                }
                                context.startActivity(intent)
                            }) {
                                Text(stringResource(R.string.call_button))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCallDialog.value = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                IconButton(onClick = { showCallDialog.value = true }) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call customer",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            notification.vehicleNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.assigned_vehicle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Swipe indicator
 */
@Composable
private fun SwipeIndicator(
    swipeOffset: Float,
    isAccepting: Boolean,
    isDeclining: Boolean,
    swipeThresholdPx: Float
) {
    val progress = (swipeOffset.absoluteValue / swipeThresholdPx).coerceIn(0f, 1f)
    val acceptProgress = if (swipeOffset > 0) progress else 0f
    val declineProgress = if (swipeOffset < 0) progress else 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (swipeOffset < -50f || declineProgress > 0.1f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.decline_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (swipeOffset > 50f || acceptProgress > 0.1f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.accept_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (swipeOffset == 0f && !isAccepting && !isDeclining) {
        Text(
            stringResource(R.string.swipe_instructions),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
