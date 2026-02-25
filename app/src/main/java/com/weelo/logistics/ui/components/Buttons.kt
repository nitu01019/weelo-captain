package com.weelo.logistics.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.R
import com.weelo.logistics.ui.theme.*

// =============================================================================
// WEELO CAPTAIN - PREMIUM RAPIDO-STYLE BUTTONS
// =============================================================================
// Modern, premium button components with Saffron Yellow theme
// Features: Smooth animations, loading states, multiple variants
// =============================================================================

/**
 * Primary Button - Main action button (Saffron Yellow)
 * Premium Rapido-style with bold presence
 * Usage: Login, Sign Up, Submit forms, Primary CTAs
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    fullWidth: Boolean = true,
    loadingText: String? = null
) {
    val buttonModifier = modifier
        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = ComponentSize.touchTarget)
        .height(ComponentSize.buttonHeight)
    
    Button(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = OnPrimary,
            disabledContainerColor = Primary.copy(alpha = 0.4f),
            disabledContentColor = OnPrimary.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(BorderRadius.medium),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.card,
            pressedElevation = Elevation.cardPressed,
            disabledElevation = Elevation.none
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = OnPrimary,
                strokeWidth = 2.5.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = loadingText ?: stringResource(R.string.please_wait),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Secondary Button - Dark variant (Black/Dark Gray)
 * For secondary actions with premium feel
 * Usage: View Details, Learn More, Alternative primary actions
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    fullWidth: Boolean = true,
    loadingText: String? = null
) {
    val buttonModifier = modifier
        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = ComponentSize.touchTarget)
        .height(ComponentSize.buttonHeight)
    
    Button(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Secondary,
            contentColor = White,
            disabledContainerColor = Secondary.copy(alpha = 0.4f),
            disabledContentColor = White.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(BorderRadius.medium),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.card,
            pressedElevation = Elevation.cardPressed,
            disabledElevation = Elevation.none
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = White,
                    strokeWidth = 2.5.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = loadingText ?: stringResource(R.string.please_wait),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Outline Button - Yellow border, transparent background
 * Premium outlined style for tertiary actions
 * Usage: Cancel, Skip, Less prominent actions
 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fullWidth: Boolean = true,
    borderColor: Color = Primary
) {
    val buttonModifier = modifier
        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = ComponentSize.touchTarget)
        .height(ComponentSize.buttonHeight)
    
    OutlinedButton(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = if (borderColor == Primary) OnPrimary else borderColor,
            disabledContentColor = TextDisabled
        ),
        border = BorderStroke(
            width = 2.dp,
            color = if (enabled) borderColor else TextDisabled
        ),
        shape = RoundedCornerShape(BorderRadius.medium),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) borderColor else TextDisabled
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) borderColor else TextDisabled,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Text Button - Low emphasis action
 * Clean, minimal text-only button
 * Usage: Skip, Learn More, Links, Less important actions
 */
@Composable
fun WeeloTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Primary,
    icon: ImageVector? = null
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = color,
            disabledContentColor = TextDisabled
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Icon Button with background
 * Premium styled icon button with surface background
 * Usage: Action buttons, FAB-like buttons, Icon-only actions
 */
@Composable
fun IconButtonWithBackground(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = SurfaceVariant,
    enabled: Boolean = true,
    size: Dp = ComponentSize.touchTarget,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        color = backgroundColor,
        shape = RoundedCornerShape(BorderRadius.medium),
        tonalElevation = Elevation.low
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Premium Gradient Button - Eye-catching CTA
 * Yellow gradient background for important actions
 * Usage: Primary CTA, Featured actions, Premium feel
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    loadingText: String? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.buttonHeight),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = OnPrimary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = OnPrimary.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(BorderRadius.medium),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) {
                        Brush.horizontalGradient(PrimaryGradientColors)
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                Primary.copy(alpha = 0.4f),
                                PrimaryVariant.copy(alpha = 0.4f)
                            )
                        )
                    },
                    shape = RoundedCornerShape(BorderRadius.medium)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = OnPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = loadingText ?: stringResource(R.string.please_wait),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                } else {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Small Chip Button - For quick actions and filters
 * Compact button for tags, filters, quick selections
 */
@Composable
fun ChipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = ComponentSize.touchTarget),
        enabled = enabled,
        color = if (selected) Primary else SurfaceVariant,
        contentColor = if (selected) OnPrimary else TextPrimary,
        shape = RoundedCornerShape(BorderRadius.pill),
        border = if (!selected) BorderStroke(1.dp, Border) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

/**
 * Floating Action Button - Premium styled FAB
 * For primary floating actions
 */
@Composable
fun WeeloFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.ArrowForward,
    contentDescription: String? = null,
    extended: Boolean = false,
    text: String = ""
) {
    if (extended && text.isNotEmpty()) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = Primary,
            contentColor = OnPrimary,
            shape = RoundedCornerShape(BorderRadius.large),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = Elevation.fab,
                pressedElevation = Elevation.high
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = Primary,
            contentColor = OnPrimary,
            shape = RoundedCornerShape(BorderRadius.large),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = Elevation.fab,
                pressedElevation = Elevation.high
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
