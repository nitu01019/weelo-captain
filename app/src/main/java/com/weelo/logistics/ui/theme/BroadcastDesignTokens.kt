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

    val RoutePickup = Color(0xFFF2D22E)
    val RouteDrop = Color(0xFFE05858)
    val StatusWarning = Color(0xFFF0A345)
    val StatusError = Color(0xFFE05858)
    val AccentInfo = Color(0xFF2B7DE9)
}
