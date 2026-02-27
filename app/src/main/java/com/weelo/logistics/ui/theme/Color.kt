package com.weelo.logistics.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// WEELO CAPTAIN - PREMIUM RAPIDO-STYLE COLOR PALETTE
// =============================================================================
// Brand Colors: Saffron Yellow (#F9C935), White (#FFFFFF), Black (#000000)
// Designed for a premium, professional logistics app experience
// =============================================================================

// Primary Colors (Saffron Yellow - Rapido Style)
val Primary = Color(0xFFF9C935)           // Saffron - Main brand color
val PrimaryDark = Color(0xFFE5B82E)       // Darker saffron for pressed states
val PrimaryLight = Color(0xFFFFF8E1)      // Light saffron for backgrounds
val PrimaryVariant = Color(0xFFFFD54F)    // Brighter variant for accents

// On Primary (Text/Icons on Primary color)
val OnPrimary = Color(0xFF000000)         // Black text on yellow background

// Secondary Colors (Dark/Professional accent)
val Secondary = Color(0xFF1A1A1A)         // Near black for premium feel
val SecondaryDark = Color(0xFF000000)     // Pure black
val SecondaryLight = Color(0xFF2D2D2D)    // Dark gray for cards

// Tertiary (Accent for special elements)
val Tertiary = Color(0xFFFFB300)          // Amber for highlights
val TertiaryLight = Color(0xFFFFE082)     // Light amber

// Status Colors (Vibrant and clear)
val Success = Color(0xFF00C853)           // Vibrant green
val SuccessLight = Color(0xFFE8F5E9)      // Light green background
val SuccessDark = Color(0xFF00A844)       // Dark green

val Warning = Color(0xFFFF9800)           // Orange warning
val WarningLight = Color(0xFFFFF3E0)      // Light orange background
val WarningDark = Color(0xFFE65100)       // Dark orange

val Error = Color(0xFFE53935)             // Red error
val ErrorLight = Color(0xFFFFEBEE)        // Light red background
val ErrorDark = Color(0xFFB71C1C)         // Dark red

val Info = Color(0xFF2196F3)              // Blue info
val InfoLight = Color(0xFFE3F2FD)         // Light blue background
val InfoDark = Color(0xFF1565C0)          // Dark blue

// Neutral Colors (Clean and professional)
val TextPrimary = Color(0xFF1A1A1A)       // Near black for main text
val TextSecondary = Color(0xFF666666)     // Medium gray for secondary text
val TextTertiary = Color(0xFF999999)      // Light gray for hints
val TextDisabled = Color(0xFFBDBDBD)      // Disabled text
val TextOnDark = Color(0xFFFFFFFF)        // White text on dark backgrounds

// Background Colors
val Background = Color(0xFFFAFAFA)        // Slightly off-white for premium feel
val BackgroundElevated = Color(0xFFFFFFFF) // Pure white for elevated surfaces
val Surface = Color(0xFFFFFFFF)           // White surface
val SurfaceVariant = Color(0xFFF5F5F5)    // Light gray surface

// Dividers and Borders
val Divider = Color(0xFFEEEEEE)           // Light divider
val DividerDark = Color(0xFFE0E0E0)       // Slightly darker divider
val Border = Color(0xFFE0E0E0)            // Border color
val BorderFocused = Color(0xFFF9C935)     // Yellow border when focused

// Card Colors
val CardBackground = Color(0xFFFFFFFF)    // White cards
val CardBackgroundDark = Color(0xFF1A1A1A) // Dark cards for contrast
val CardShadow = Color(0x1A000000)        // Subtle shadow (10% black)

// Skeleton Loading Colors
val SkeletonBase = Color(0xFFE0E0E0)      // Base skeleton color
val SkeletonHighlight = Color(0xFFF5F5F5) // Shimmer highlight
val SkeletonDarkBase = Color(0xFF2D2D2D)  // Dark theme skeleton
val SkeletonDarkHighlight = Color(0xFF3D3D3D) // Dark theme shimmer

// Special UI Elements
val Overlay = Color(0x80000000)           // 50% black overlay
val Scrim = Color(0x52000000)             // 32% black scrim
val Ripple = Color(0x1F000000)            // Ripple effect

// Common
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val Transparent = Color(0x00000000)

// =============================================================================
// GRADIENT DEFINITIONS
// =============================================================================

val PrimaryGradientColors = listOf(
    Color(0xFFF9C935),  // Saffron
    Color(0xFFFFD54F)   // Light saffron
)

val DarkGradientColors = listOf(
    Color(0xFF1A1A1A),  // Near black
    Color(0xFF2D2D2D)   // Dark gray
)

val PremiumGradientColors = listOf(
    Color(0xFFF9C935),  // Saffron
    Color(0xFFFFB300)   // Amber
)

// =============================================================================
// SEMANTIC ALIASES (UI Excellence v2)
// =============================================================================
// Use these aliases in screens/components instead of raw brand/status colors.
// This keeps UI modular and allows safe visual tuning without screen-level edits.

val SurfaceElevated = BackgroundElevated
val SurfaceMuted = SurfaceVariant

val CriticalAction = Error
val CriticalActionContainer = ErrorLight

val SuccessContainer = SuccessLight
val WarningContainer = WarningLight

val FocusRing = Color(0xFF1E88E5)

// =============================================================================
// BROADCAST UI TOKENS
// =============================================================================
// Shared visual tokens for transporter broadcast surfaces so overlay, acceptance,
// and assignment flows keep the same hierarchy and contrast.
object BroadcastUiTokens {
    val ScreenBackground = BroadcastDesignTokens.ScreenBackground
    val CardBackground = BroadcastDesignTokens.CardBackground
    val CardMutedBackground = BroadcastDesignTokens.CardMutedBackground
    val Border = BroadcastDesignTokens.Border
    val PrimaryCta = BroadcastDesignTokens.PrimaryAction
    val PrimaryCtaPressed = BroadcastDesignTokens.PrimaryActionPressed
    val OnPrimaryCta = BroadcastDesignTokens.OnPrimaryAction
    val SecondaryCtaText = BroadcastDesignTokens.SecondaryActionText
    val SecondaryCtaBorder = BroadcastDesignTokens.SecondaryActionBorder
    val PrimaryText = BroadcastDesignTokens.TextPrimary
    val SecondaryText = BroadcastDesignTokens.TextSecondary
    val TertiaryText = BroadcastDesignTokens.TextTertiary
    val Success = BroadcastDesignTokens.RoutePickup
    val Error = BroadcastDesignTokens.StatusError
    val Warning = BroadcastDesignTokens.StatusWarning
    val AccentInfo = BroadcastDesignTokens.AccentInfo
}
