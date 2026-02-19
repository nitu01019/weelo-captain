package com.weelo.logistics.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.weelo.logistics.ui.theme.Spacing

// =============================================================================
// WEELO CAPTAIN - RESPONSIVE LAYOUT UTILITIES
// =============================================================================
// Handles landscape/portrait mode and different screen sizes
// Ensures premium UI looks great on all device orientations
// =============================================================================

/**
 * Screen size categories for responsive design
 */
enum class WindowSize {
    COMPACT,    // Phone portrait (< 600dp)
    MEDIUM,     // Phone landscape or small tablet (600-840dp)
    EXPANDED    // Tablet or large screen (> 840dp)
}

/**
 * Orientation helper
 */
enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * Screen configuration data class
 */
data class ScreenConfig(
    val windowSize: WindowSize,
    val orientation: DeviceOrientation,
    val screenWidth: Dp,
    val screenHeight: Dp,
    val isLandscape: Boolean,
    val isTablet: Boolean
)

/**
 * Get current screen configuration
 * Use this in any composable to adapt UI to screen size/orientation
 */
@Composable
fun rememberScreenConfig(): ScreenConfig {
    val configuration = LocalConfiguration.current
    
    return remember(configuration) {
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        val windowSize = when {
            screenWidth < 600.dp -> WindowSize.COMPACT
            screenWidth < 840.dp -> WindowSize.MEDIUM
            else -> WindowSize.EXPANDED
        }
        
        val isTablet = screenWidth >= 600.dp && screenHeight >= 600.dp
        
        ScreenConfig(
            windowSize = windowSize,
            orientation = if (isLandscape) DeviceOrientation.LANDSCAPE else DeviceOrientation.PORTRAIT,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            isLandscape = isLandscape,
            isTablet = isTablet
        )
    }
}

/**
 * Responsive container that adapts padding and max width based on screen size
 */
@Composable
fun ResponsiveContainer(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val screenConfig = rememberScreenConfig()
    
    val horizontalPadding = when (screenConfig.windowSize) {
        WindowSize.COMPACT -> Spacing.screenHorizontal
        WindowSize.MEDIUM -> 32.dp
        WindowSize.EXPANDED -> 48.dp
    }
    
    val maxWidth = when (screenConfig.windowSize) {
        WindowSize.COMPACT -> Dp.Unspecified
        WindowSize.MEDIUM -> 600.dp
        WindowSize.EXPANDED -> 840.dp
    }
    
    val columnModifier = if (scrollable) {
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    } else {
        modifier.fillMaxSize()
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter
    ) {
        Column(
            modifier = columnModifier
                .then(
                    if (maxWidth != Dp.Unspecified) {
                        Modifier.widthIn(max = maxWidth)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                .padding(horizontal = horizontalPadding),
            content = content
        )
    }
}

/**
 * Responsive row that becomes column in portrait mode on small screens
 */
@Composable
fun ResponsiveRowColumn(
    modifier: Modifier = Modifier,
    forceColumn: Boolean = false,
    spacing: Dp = Spacing.medium,
    content: @Composable () -> Unit
) {
    val screenConfig = rememberScreenConfig()
    val useRow = !forceColumn && (screenConfig.isLandscape || screenConfig.windowSize != WindowSize.COMPACT)
    
    if (useRow) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            content()
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            content()
        }
    }
}

/**
 * Responsive grid that adapts columns based on screen size
 */
@Composable
fun responsiveGridColumns(): Int {
    val screenConfig = rememberScreenConfig()
    return when {
        screenConfig.windowSize == WindowSize.EXPANDED -> 4
        screenConfig.isLandscape -> 3
        screenConfig.windowSize == WindowSize.MEDIUM -> 3
        else -> 2
    }
}

/**
 * Get responsive horizontal padding
 */
@Composable
fun responsiveHorizontalPadding(): Dp {
    val screenConfig = rememberScreenConfig()
    return when (screenConfig.windowSize) {
        WindowSize.COMPACT -> Spacing.screenHorizontal
        WindowSize.MEDIUM -> 32.dp
        WindowSize.EXPANDED -> 48.dp
    }
}

/**
 * Get responsive card width for grid layouts
 */
@Composable
fun responsiveCardWidth(): Dp {
    val screenConfig = rememberScreenConfig()
    return when {
        screenConfig.windowSize == WindowSize.EXPANDED -> 200.dp
        screenConfig.isLandscape -> 180.dp
        else -> 160.dp
    }
}

/**
 * Modifier extension for landscape-specific padding
 */
fun Modifier.landscapePadding(): Modifier = composed {
    val screenConfig = rememberScreenConfig()
    if (screenConfig.isLandscape) {
        this.padding(horizontal = 24.dp)
    } else {
        this
    }
}

/**
 * Get content max width for centered layouts
 */
@Composable
fun contentMaxWidth(): Dp {
    val screenConfig = rememberScreenConfig()
    return when (screenConfig.windowSize) {
        WindowSize.COMPACT -> Dp.Unspecified
        WindowSize.MEDIUM -> 500.dp
        WindowSize.EXPANDED -> 600.dp
    }
}

/**
 * Auth screen specific - narrower max width for forms
 */
@Composable
fun authFormMaxWidth(): Dp {
    val screenConfig = rememberScreenConfig()
    return when {
        screenConfig.isLandscape || screenConfig.windowSize != WindowSize.COMPACT -> 400.dp
        else -> Dp.Unspecified
    }
}
