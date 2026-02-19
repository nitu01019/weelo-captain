package com.weelo.logistics.ui.transporter

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.components.SectionCard
import com.weelo.logistics.ui.driver.SettingRow
import com.weelo.logistics.ui.theme.*

/**
 * Transporter Settings Screen
 * App settings and preferences for Transporter role
 */
@Composable
fun TransporterSettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    // val context = LocalContext.current  // Reserved for future use
    var notificationsEnabled by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    // Language selection removed - app is English only
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = stringResource(R.string.settings), onBackClick = onNavigateBack)
        
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(stringResource(R.string.notifications)) {
                SettingRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.push_notifications),
                    subtitle = stringResource(R.string.receive_driver_notifications),
                    trailing = {
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Primary)
                        )
                    }
                )
            }
            
            SectionCard(stringResource(R.string.preferences)) {
                // Language selection removed - app is English only
                SettingRow(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.theme),
                    subtitle = stringResource(R.string.theme_light),
                    onClick = { /* TODO */ }
                )
            }
            
            SectionCard(stringResource(R.string.account)) {
                SettingRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.privacy_policy),
                    subtitle = stringResource(R.string.privacy_policy_desc),
                    onClick = { /* TODO */ }
                )
                Divider()
                SettingRow(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.terms_conditions),
                    subtitle = stringResource(R.string.terms_desc),
                    onClick = { /* TODO */ }
                )
            }
            
            SectionCard(stringResource(R.string.support)) {
                SettingRow(
                    icon = Icons.Default.Help,
                    title = stringResource(R.string.help_support),
                    subtitle = stringResource(R.string.help_support_desc),
                    onClick = { /* TODO */ }
                )
                Divider()
                SettingRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.about_version_format, "1.0.0"),
                    onClick = { /* TODO */ }
                )
            }
            
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(Error.copy(alpha = 0.1f))
            ) {
                SettingRow(
                    icon = Icons.Default.Logout,
                    title = stringResource(R.string.logout),
                    subtitle = stringResource(R.string.logout_subtitle),
                    iconTint = Error,
                    titleColor = Error,
                    onClick = { showLogoutDialog = true }
                )
            }
        }
    }
    
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, stringResource(R.string.cd_logout), tint = Error) },
            title = { Text(stringResource(R.string.logout_confirmation_title)) },
            text = { Text(stringResource(R.string.logout_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text(stringResource(R.string.logout), color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Language dialog removed - app is English only
}

// SettingRow is imported from DriverSettingsScreen.kt — shared component
