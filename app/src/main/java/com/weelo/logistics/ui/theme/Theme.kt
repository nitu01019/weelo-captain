package com.weelo.logistics.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =============================================================================
// WEELO CAPTAIN - PREMIUM RAPIDO-STYLE THEME
// =============================================================================
// A professional, premium theme with Saffron Yellow as the primary color
// Designed for a clean, modern logistics app experience
// =============================================================================

private val LightColorScheme = lightColorScheme(
    // Primary - Saffron Yellow
    primary = Primary,
    onPrimary = OnPrimary,  // Black text on yellow
    primaryContainer = PrimaryLight,
    onPrimaryContainer = Secondary,
    
    // Secondary - Dark/Professional
    secondary = Secondary,
    onSecondary = White,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = White,
    
    // Tertiary - Amber accent
    tertiary = Tertiary,
    onTertiary = Black,
    tertiaryContainer = TertiaryLight,
    onTertiaryContainer = Secondary,
    
    // Error
    error = Error,
    onError = White,
    errorContainer = ErrorLight,
    onErrorContainer = ErrorDark,
    
    // Background - Slightly off-white for premium feel
    background = Background,
    onBackground = TextPrimary,
    
    // Surface - Pure white
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    
    // Outline
    outline = Border,
    outlineVariant = Divider,
    
    // Inverse (for snackbars etc.)
    inverseSurface = Secondary,
    inverseOnSurface = White,
    inversePrimary = PrimaryVariant,
    
    // Scrim
    scrim = Scrim
)

private val DarkColorScheme = darkColorScheme(
    // Primary - Saffron Yellow (same in dark mode for brand consistency)
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = PrimaryLight,
    
    // Secondary
    secondary = PrimaryVariant,
    onSecondary = Black,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = White,
    
    // Tertiary
    tertiary = Tertiary,
    onTertiary = Black,
    
    // Error
    error = Error,
    onError = White,
    errorContainer = ErrorDark,
    onErrorContainer = ErrorLight,
    
    // Background - Near black
    background = Secondary,
    onBackground = White,
    
    // Surface - Dark gray
    surface = SecondaryLight,
    onSurface = White,
    surfaceVariant = CardBackgroundDark,
    onSurfaceVariant = TextTertiary,
    
    // Outline
    outline = Color(0xFF424242),
    outlineVariant = Color(0xFF616161),
    
    // Inverse
    inverseSurface = White,
    inverseOnSurface = Secondary,
    inversePrimary = PrimaryDark,
    
    // Scrim
    scrim = Scrim
)

/**
 * Weelo Theme - Premium Rapido-style theme
 * 
 * @param darkTheme Whether to use dark theme (default: false for light theme)
 * @param content The content to be themed
 */
@Composable
fun WeeloTheme(
    darkTheme: Boolean = false, // Default to light theme for premium feel
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use white/light status bar for premium look
            window.statusBarColor = if (darkTheme) Secondary.toArgb() else White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Extended color accessors for convenience
 */
object WeeloColors {
    val primary = Primary
    val primaryDark = PrimaryDark
    val primaryLight = PrimaryLight
    val onPrimary = OnPrimary
    
    val secondary = Secondary
    val secondaryLight = SecondaryLight
    
    val success = Success
    val successLight = SuccessLight
    val warning = Warning
    val warningLight = WarningLight
    val error = Error
    val errorLight = ErrorLight
    val info = Info
    val infoLight = InfoLight
    
    val textPrimary = TextPrimary
    val textSecondary = TextSecondary
    val textTertiary = TextTertiary
    val textDisabled = TextDisabled
    
    val background = Background
    val surface = Surface
    val divider = Divider
    
    val white = White
    val black = Black
}
