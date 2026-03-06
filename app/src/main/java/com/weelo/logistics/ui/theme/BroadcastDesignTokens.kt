package com.weelo.logistics.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Broadcast-specific visual contract.
 * Keep all high-churn broadcast surfaces/actions bound to these semantic tokens.
 */
object BroadcastDesignTokens {
    val ScreenBackground = Color(0xFFF6F7FB)
    val CardBackground = Color(0xFFFFFFFF)
    val CardMutedBackground = Color(0xFFF2F4F8)
    val Border = Color(0xFFDCE2EC)

    val PrimaryAction = Color(0xFFF2D22E)
    val PrimaryActionPressed = Color(0xFFE2C21F)
    val OnPrimaryAction = Color(0xFF111111)

    val SecondaryActionText = Color(0xFF1F2430)
    val SecondaryActionBorder = Color(0xFFD3DAE6)

    val TextPrimary = Color(0xFF111111)
    val TextSecondary = Color(0xFF5B6473)
    val TextTertiary = Color(0xFF7E8899)

    val RoutePickup = Color(0xFF2E8B57)        // Green dot for pickup
    val RouteDrop = Color(0xFFE05858)          // Red dot for drop
    val RouteLineGreen = Color(0xFF2E8B57)     // Green vertical route line
    val StatusWarning = Color(0xFFF0A345)
    val StatusError = Color(0xFFE05858)
    val AccentInfo = Color(0xFF2B7DE9)

    // Timer ring tokens
    val TimerRingActive = Color(0xFFF2D22E)    // Yellow timer ring (depleting)
    val TimerRingTrack = Color(0xFFE8E8E8)     // Grey background track
    val TimerRingUrgent = Color(0xFFE05858)    // Red when <15 seconds left

    // Truck card tokens (white background, not dark)
    val TruckCardBackground = Color(0xFFFFFFFF)
    val TruckCardBorder = Color(0xFFE0E0E0)

    // Pickup distance label
    val PickupDistanceLabel = Color(0xFF2E8B57)  // Green for "Pickup" label
    val DropDistanceLabel = Color(0xFFE05858)     // Red for "Drop" label
}
