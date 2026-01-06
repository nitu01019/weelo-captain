package com.weelo.logistics.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.weelo.logistics.ui.theme.Primary
import com.weelo.logistics.ui.theme.TextPrimary
import com.weelo.logistics.ui.theme.White

/**
 * Primary Top App Bar - Standard top bar with back button
 * Usage: Most screens with navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimaryTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
        },
        actions = { actions() },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = White
        )
    )
}

/**
 * Simple Top Bar - Top bar without back button
 * Usage: Main dashboard screens
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        actions = { actions() },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = White
        )
    )
}
