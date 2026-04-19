package com.weelo.logistics.ui.driver.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.R
import com.weelo.logistics.ui.theme.Warning

/**
 * CustomerRatingBadge -- Shows customer rating as star icon + number.
 *
 * Gracefully hides if rating is null or <= 0.
 * Designed to be placed in trip request overlay or trip info cards.
 */
@Composable
fun CustomerRatingBadge(
    rating: Double?,
    modifier: Modifier = Modifier
) {
    // Gracefully hide if no valid rating
    if (rating == null || rating <= 0.0) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Warning.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.customer_rating),
                tint = Warning,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = String.format("%.1f", rating),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Warning
            )
        }
    }
}
