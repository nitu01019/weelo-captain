package com.weelo.logistics.ui.transporter

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.data.api.HoldTrucksRequest
import com.weelo.logistics.data.api.ReleaseHoldRequest
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.R
import com.weelo.logistics.ui.ServerDeadlineTimer
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.components.ProvideShimmerBrush
import com.weelo.logistics.ui.components.SectionSkeletonBlock
import com.weelo.logistics.ui.components.SkeletonBox
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.ui.viewmodel.TruckHoldConfirmViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "VehicleHoldConfirm"
// Fallback duration if server doesn't return expiresAt (should never happen)
private const val HOLD_DURATION_FALLBACK_SECONDS = 180

/**
 * =============================================================================
 * VEHICLE HOLD CONFIRMATION SCREEN
 * =============================================================================
 *
 * Shows countdown timer after transporter clicks ACCEPT.
 *
 * FLOW:
 * 1. Auto-calls holdTrucks API
 * 2. Shows countdown (server-synced duration)
 * 3. User clicks CONFIRM -> confirmHold API -> Navigate to driver assignment
 * 4. User clicks CANCEL or timeout -> releaseHold API -> Go back
 *
 * RENAME NOTE: Renamed from TruckHoldConfirmScreen to VehicleHoldConfirmScreen.
 * Route string preserved: "truck_hold_confirm/{orderId}/..." (zero navigation breakage).
 * =============================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleHoldConfirmScreen(
    orderId: String,
    vehicleType: String,
    vehicleSubtype: String,
    quantity: Int,
    onConfirmed: (
        holdId: String,
        vehicleType: String,
        vehicleSubtype: String,
        quantity: Int
    ) -> Unit,
    onCancelled: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holdViewModel: TruckHoldConfirmViewModel = viewModel()

    // State
    var holdId by remember { mutableStateOf<String?>(null) }
    // Total seconds for progress ring calculation (set from server expiresAt)
    var totalSeconds by remember { mutableStateOf(HOLD_DURATION_FALLBACK_SECONDS) }
    var remainingSeconds by remember { mutableStateOf(HOLD_DURATION_FALLBACK_SECONDS) }
    // F-C-27: monotonic elapsed-realtime deadline; -1 = not synced to server yet.
    // Recomputing `remainingSeconds` from this on every tick keeps the timer
    // correct across doze/sleep and wall-clock jumps (Uber + Android guidance).
    var deadlineElapsedMs by remember { mutableStateOf(-1L) }
    var isLoading by remember { mutableStateOf(true) }
    var isConfirming by remember { mutableStateOf(false) }
    var isFinalizing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var holdSuccess by remember { mutableStateOf(false) }
    
    // Circular progress for countdown — scales to actual server duration
    val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f
    
    // Dynamic color thresholds — scale with server duration
    // >20% green, >10% yellow, else red
    val timerColor = when {
        remainingSeconds > (totalSeconds * 0.20).toInt() -> Success
        remainingSeconds > (totalSeconds * 0.10).toInt() -> Warning
        else -> Error
    }
    
    // Hold trucks on screen load
    LaunchedEffect(Unit) {
        timber.log.Timber.d("Holding $quantity $vehicleType $vehicleSubtype for order $orderId")
        
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.truckHoldApi.holdTrucks(
                    HoldTrucksRequest(
                        orderId = orderId,
                        vehicleType = vehicleType,
                        vehicleSubtype = vehicleSubtype,
                        quantity = quantity
                    )
                )
            }
            
            if (response.isSuccessful && response.body()?.success == true) {
                holdId = response.body()?.data?.holdId
                // Use server-provided expiresAt for timer synchronization
                val expiresAtStr = response.body()?.data?.expiresAt
                if (expiresAtStr != null) {
                    try {
                        val expiresAtMs = java.time.Instant.parse(expiresAtStr).toEpochMilli()
                        val nowMs = System.currentTimeMillis()
                        val nowElapsed = SystemClock.elapsedRealtime()
                        // F-C-27: pin an absolute monotonic deadline; countdown loop recomputes from it.
                        deadlineElapsedMs = ServerDeadlineTimer.deadlineElapsedFromServerExpiry(
                            expiresAtWallMs = expiresAtMs,
                            nowWallMs = nowMs,
                            nowElapsedMs = nowElapsed
                        )
                        val serverRemaining = ServerDeadlineTimer.remainingSecondsFromDeadline(
                            deadlineElapsedMs = deadlineElapsedMs,
                            nowElapsedMs = nowElapsed
                        )
                        totalSeconds = serverRemaining
                        remainingSeconds = serverRemaining
                        timber.log.Timber.d("Timer synced from server expiresAt: ${serverRemaining}s (deadlineElapsed=$deadlineElapsedMs)")
                    } catch (e: Exception) {
                        timber.log.Timber.w(e, "Failed to parse expiresAt, using fallback")
                        // Keep fallback values
                    }
                }
                holdSuccess = true
                timber.log.Timber.d("Hold success: $holdId")
            } else {
                val msg = response.body()?.error?.message ?: response.body()?.message ?: context.getString(R.string.failed_hold_vehicles)
                errorMessage = msg
                timber.log.Timber.e("Hold failed: $msg")
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Hold exception")
            errorMessage = e.localizedMessage ?: context.getString(R.string.network_error_short)
        } finally {
            isLoading = false
        }
    }
    
    // Countdown timer (F-C-27 — server-deadline recompute every tick, doze-safe)
    LaunchedEffect(holdSuccess, deadlineElapsedMs) {
        if (holdSuccess && holdId != null && deadlineElapsedMs > 0L) {
            while (remainingSeconds > 0 && !isConfirming && !isFinalizing) {
                delay(500)
                remainingSeconds = ServerDeadlineTimer.remainingSecondsFromDeadline(
                    deadlineElapsedMs = deadlineElapsedMs,
                    nowElapsedMs = SystemClock.elapsedRealtime()
                )
            }

            // Timeout - release hold
            if (remainingSeconds <= 0 && holdId != null && !isConfirming && !isFinalizing) {
                timber.log.Timber.d("Hold timeout, releasing")
                isFinalizing = true
                try {
                    withContext(Dispatchers.IO) {
                        RetrofitClient.truckHoldApi.releaseHold(
                            ReleaseHoldRequest(holdId!!)
                        )
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Release failed")
                } finally {
                    isFinalizing = false
                }
                Toast.makeText(context, context.getString(R.string.time_expired_released), Toast.LENGTH_SHORT).show()
                onCancelled()
            }
        }
    }
    
    // Confirm function
    // F-C-27: matches button's `remainingSeconds > 2` gate — grace buffer against
    // client-side stale confirms when the server has already expired the hold.
    fun confirmHold() {
        if (holdId == null || isConfirming || isFinalizing || remainingSeconds <= 2) return

        scope.launch {
            isConfirming = true

            val currentHoldId = holdId ?: run {
                isConfirming = false
                return@launch
            }

            timber.log.Timber.d(
                "Proceeding to driver assignment with hold=%s, type=%s, subtype=%s, quantity=%d",
                currentHoldId,
                vehicleType,
                vehicleSubtype,
                quantity
            )
            Toast.makeText(context, context.getString(R.string.hold_confirmed_assign), Toast.LENGTH_SHORT).show()
            onConfirmed(
                currentHoldId,
                vehicleType,
                vehicleSubtype,
                quantity
            )
            isConfirming = false
        }
    }
    
    // Cancel/release function
    fun cancelHold() {
        if (isFinalizing) return
        if (holdId == null) {
            onCancelled()
            return
        }
        
        scope.launch {
            isFinalizing = true
            try {
                withContext(Dispatchers.IO) {
                    RetrofitClient.truckHoldApi.releaseHold(
                        ReleaseHoldRequest(holdId!!)
                    )
                }
                timber.log.Timber.d("Hold released")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Release failed")
            } finally {
                isFinalizing = false
            }
            onCancelled()
        }
    }
    
    // UI
    Scaffold(
        topBar = {
            PrimaryTopBar(
                title = stringResource(R.string.confirm_selection),
                onBackClick = { if (!isConfirming && !isFinalizing) cancelHold() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Surface),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    ProvideShimmerBrush {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SectionSkeletonBlock(titleLineWidthFraction = 0.52f, rowCount = 2)
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(),
                                height = 56.dp
                            )
                        }
                    }
                }
                
                errorMessage != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = Error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            errorMessage ?: stringResource(R.string.error),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onCancelled) {
                            Text(stringResource(R.string.go_back))
                        }
                    }
                }
                
                holdSuccess -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Success Icon
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Success.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Success
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Text(
                                stringResource(R.string.vehicles_reserved),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            
                            // Truck info
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Primary.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocalShipping,
                                        null,
                                        tint = Primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "$quantity × ${vehicleType.uppercase()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                    if (vehicleSubtype.isNotBlank()) {
                                        Text(
                                            " ($vehicleSubtype)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(32.dp))
                            
                            // Countdown Timer
                            Box(contentAlignment = Alignment.Center) {
                                // Background circle
                                CircularProgressIndicator(
                                    progress = 1f,
                                    modifier = Modifier.size(120.dp),
                                    strokeWidth = 8.dp,
                                    color = timerColor.copy(alpha = 0.2f)
                                )
                                
                                // Progress circle
                                CircularProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.size(120.dp),
                                    strokeWidth = 8.dp,
                                    color = timerColor
                                )
                                
                                // Timer text
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        remainingSeconds.toString(),
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = timerColor
                                    )
                                    Text(
                                        stringResource(R.string.seconds_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Text(
                                stringResource(R.string.confirm_within_time),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(Modifier.height(32.dp))
                            
                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Cancel Button
                                OutlinedButton(
                                    onClick = { cancelHold() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isConfirming && !isFinalizing
                                ) {
                                    Text("CANCEL")
                                }
                                
                                // Confirm Button
                                // F-C-27: BookMyShow grace-buffer — disable when <=2s left
                                // so the client NEVER confirms after the server budget has elapsed.
                                Button(
                                    onClick = { confirmHold() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                                    enabled = !isConfirming && !isFinalizing && remainingSeconds > 2
                                ) {
                                    if (isConfirming) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Check, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("CONFIRM", fontWeight = FontWeight.Bold)
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
