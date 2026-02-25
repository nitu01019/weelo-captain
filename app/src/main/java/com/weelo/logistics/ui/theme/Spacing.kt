package com.weelo.logistics.ui.theme

import androidx.compose.ui.unit.dp

// =============================================================================
// WEELO CAPTAIN - DESIGN SYSTEM SPACING & SIZING
// =============================================================================
// Consistent spacing and sizing for a premium, polished UI
// =============================================================================

object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp
    
    // Screen padding
    val screenHorizontal = 20.dp
    val screenVertical = 16.dp
    
    // Card internal padding
    val cardPadding = 16.dp
    val cardPaddingLarge = 20.dp
    
    // List item spacing
    val listItemSpacing = 12.dp
    val listItemPadding = 16.dp
}

object ComponentSize {
    // Buttons
    val buttonHeight = 56.dp
    val buttonHeightSmall = 44.dp
    val buttonHeightLarge = 60.dp
    
    // Inputs
    val inputHeight = 56.dp
    val inputHeightSmall = 48.dp
    
    // Navigation
    val topBarHeight = 64.dp
    val bottomNavHeight = 64.dp
    
    // Icons
    val iconSmall = 20.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp
    val iconXL = 48.dp
    
    // Avatars
    val avatarSmall = 40.dp
    val avatarMedium = 56.dp
    val avatarLarge = 72.dp
    val avatarXL = 96.dp
    
    // Cards
    val cardMinHeight = 80.dp
    val cardImageHeight = 120.dp
    
    // Touch targets (minimum 48dp for accessibility)
    val touchTarget = 48.dp
}

object BorderRadius {
    val xs = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val pill = 100.dp  // For pill-shaped buttons
    val circle = 50    // Percentage for circles
}

object Elevation {
    val none = 0.dp
    val xs = 1.dp
    val low = 2.dp
    val medium = 4.dp
    val high = 8.dp
    val xl = 12.dp
    val xxl = 16.dp
    
    // Specific component elevations
    val card = 2.dp
    val cardHovered = 4.dp
    val cardPressed = 8.dp
    val bottomSheet = 16.dp
    val dialog = 24.dp
    val fab = 6.dp
}

object AnimationDuration {
    val instant = 0
    val fast = 150
    val normal = 220
    val slow = 450
    val slower = 600
    
    // Specific animations
    val shimmer = 1200
    val skeleton = 1000
    val pageTransition = 300
    val buttonPress = 100
}

object InteractionMotion {
    // Premium-balanced motion profile for operational UI.
    val micro = 150
    val standard = 220
    val emphasis = 300
}
