package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.HoldPhase
import com.weelo.logistics.ui.theme.Error
import com.weelo.logistics.ui.theme.Info
import com.weelo.logistics.ui.theme.Success
import com.weelo.logistics.ui.theme.Warning

// =============================================================================
// F-C-34 PHASE-AWARE TIMER (PhaseTimerCard + LegacyCountdownBlock)
// F-C-47 DriverEjectionBanner
// =============================================================================
// Extracted from DriverAssignmentScreen.kt for 800-line compliance. The
// Screen remains the state owner — these composables are pure render cards.
//
// BuildConfig.FF_PHASE_AWARE_TIMER (default OFF) switches the Screen between
// LegacyCountdownBlock (old behavior) and PhaseTimerCard (new HoldPhase-
// aware rendering with F-C-78 UNKNOWN fallback).
// =============================================================================

/**
 * Legacy countdown block — the pre-F-C-34 single-timer card. Kept so that
 * FF_PHASE_AWARE_TIMER-off is a perfect pixel match, and so HoldPhase.UNKNOWN
 * paths in the new card still have a graceful fallback.
 */
@Composable
internal fun LegacyCountdownBlock(remainingSeconds: Int) {
    val timerColor = when {
        remainingSeconds > 30 -> Success
        remainingSeconds > 10 -> Warning
        else -> Error
    }
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(timerColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = timerColor
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Hold expires in ${minutes}:${"%02d".format(seconds)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = timerColor
            )
        }
    }
}

/**
 * F-C-34 PhaseTimerCard — phase-aware countdown.
 *
 * BRANCHES:
 *   HoldPhase.FLEX       -> Green pill, "Assign drivers — Phase 1 (FLEX)",
 *                           Extend +30s button (gated on [canExtend]).
 *   HoldPhase.CONFIRMED  -> Blue pill, "Drivers responding — Phase 2",
 *                           no extend, tick-tock pulse at <10s.
 *   HoldPhase.EXPIRED    -> Red pill, "Hold expired" (terminal).
 *   HoldPhase.UNKNOWN    -> Fallback to [LegacyCountdownBlock] so an
 *                           unrecognised backend phase never crashes the UI.
 *                           (F-C-78 UNKNOWN-sentinel contract.)
 *   HoldPhase.RELEASED   -> Same as EXPIRED for UI purposes.
 *
 * The `total` parameter is the phase's full duration (90+40=130 for FLEX with
 * extensions, 180 for CONFIRMED) — used as the progress-ring denominator.
 */
@Composable
internal fun PhaseTimerCard(
    phase: HoldPhase,
    remaining: Int,
    total: Int,
    canExtend: Boolean,
    onExtend: () -> Unit
) {
    val (label, color) = when (phase) {
        HoldPhase.FLEX -> "Phase 1 — Assign drivers" to Success
        HoldPhase.CONFIRMED -> "Phase 2 — Drivers responding" to Info
        HoldPhase.EXPIRED -> "Hold expired" to Error
        HoldPhase.RELEASED -> "Hold released" to Error
        HoldPhase.UNKNOWN -> {
            // Should never reach — caller handles UNKNOWN via LegacyCountdownBlock.
            LegacyCountdownBlock(remainingSeconds = remaining)
            return
        }
    }
    val minutes = remaining / 60
    val secs = remaining % 60
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${minutes}:${"%02d".format(secs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            if (total > 0 && phase != HoldPhase.EXPIRED && phase != HoldPhase.RELEASED) {
                Spacer(Modifier.height(4.dp))
                val progress = (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = color
                )
            }
            // Extend +30s is FLEX-only and gated by canExtend.
            if (phase == HoldPhase.FLEX && canExtend) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onExtend,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Extend +30s")
                }
            }
        }
    }
}

/**
 * F-C-47 DriverEjectionBanner — shown when a driver goes offline mid-assignment.
 * Tapping dismiss clears the banner; the picker auto-opens for the affected
 * vehicle via the socket listener (see DriverAssignmentScreen.kt).
 */
@Composable
internal fun DriverEjectionBanner(
    driverName: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Error.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Error
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$driverName went offline — pick a replacement",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Error,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Error)
            }
        }
    }
}
