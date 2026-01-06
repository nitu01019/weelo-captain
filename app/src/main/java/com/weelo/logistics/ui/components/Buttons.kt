package com.weelo.logistics.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.ui.theme.*

/**
 * Primary Button - Main action button (Orange)
 * Usage: Login, Sign Up, Submit forms
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.buttonHeight),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = White,
            disabledContainerColor = TextDisabled,
            disabledContentColor = White
        ),
        shape = RoundedCornerShape(BorderRadius.medium),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.medium,
            pressedElevation = Elevation.high
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(24.dp),
                color = White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Secondary Button - Alternative action (Blue outline)
 * Usage: Cancel, Skip, Alternative actions
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.buttonHeight),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Primary,
            disabledContentColor = TextDisabled
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = 2.dp,
            brush = androidx.compose.ui.graphics.SolidColor(if (enabled) Primary else TextDisabled)
        ),
        shape = RoundedCornerShape(BorderRadius.medium)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Text Button - Low emphasis action
 * Usage: Skip, Learn More, Less important actions
 */
@Composable
fun WeeloTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = Primary,
            disabledContentColor = TextDisabled
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Icon Button with background
 * Usage: Action buttons with icons
 */
@Composable
fun IconButtonWithBackground(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Surface,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        color = backgroundColor,
        shape = RoundedCornerShape(BorderRadius.medium),
        tonalElevation = Elevation.low
    ) {
        content()
    }
}
