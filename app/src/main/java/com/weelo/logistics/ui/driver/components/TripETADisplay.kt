package com.weelo.logistics.ui.driver.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.R
import com.weelo.logistics.ui.theme.Info

/**
 * TripETADisplay -- Shows estimated trip duration.
 *
 * If etaMinutes is provided and > 0, uses it directly.
 * Otherwise, calculates from distanceKm / average city speed (30 km/h).
 * Displays as "~X min".
 *
 * Hides if no meaningful ETA can be determined.
 */
@Composable
fun TripETADisplay(
    etaMinutes: Int? = null,
    distanceKm: Double = 0.0,
    modifier: Modifier = Modifier
) {
    // Calculate ETA: use direct value if available, else estimate from distance
    val displayMinutes = when {
        etaMinutes != null && etaMinutes > 0 -> etaMinutes
        distanceKm > 0 -> ((distanceKm / 30.0) * 60).toInt().coerceAtLeast(1)
        else -> return // Nothing to show
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Info.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = stringResource(R.string.est_trip_duration),
                tint = Info,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.eta_approx_format, displayMinutes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Info
            )
        }
    }
}
