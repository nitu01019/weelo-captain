package com.weelo.logistics.ui.driver

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.components.SectionCard
import com.weelo.logistics.ui.theme.*

/**
 * Driver Settings Screen - PRD-03 Compliant
 * App settings and preferences
 */
@Composable
fun DriverSettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Settings", onBackClick = onNavigateBack)
        
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard("Notifications") {
                SettingRow(
                    icon = Icons.Default.Notifications,
                    title = "Push Notifications",
                    subtitle = "Receive trip and payment notifications",
                    trailing = {
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Primary)
                        )
                    }
                )
            }
            
            SectionCard("Preferences") {
                SettingRow(
                    icon = Icons.Default.Language,
                    title = "Language",
                    subtitle = "English",
                    onClick = { /* TODO */ }
                )
                Divider()
                SettingRow(
                    icon = Icons.Default.DarkMode,
                    title = "Theme",
                    subtitle = "Light",
                    onClick = { /* TODO */ }
                )
            }
            
            SectionCard("Account") {
                SettingRow(
                    icon = Icons.Default.Lock,
                    title = "Privacy Policy",
                    subtitle = "View our privacy policy",
                    onClick = { /* TODO */ }
                )
                Divider()
                SettingRow(
                    icon = Icons.Default.Description,
                    title = "Terms & Conditions",
                    subtitle = "View terms of service",
                    onClick = { /* TODO */ }
                )
            }
            
            SectionCard("Support") {
                SettingRow(
                    icon = Icons.Default.Help,
                    title = "Help & Support",
                    subtitle = "Get help or contact us",
                    onClick = { /* TODO */ }
                )
                Divider()
                SettingRow(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Version 1.0.0",
                    onClick = { /* TODO */ }
                )
            }
            
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(Error.copy(alpha = 0.1f))
            ) {
                SettingRow(
                    icon = Icons.Default.Logout,
                    title = "Logout",
                    subtitle = "Sign out from your account",
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
            icon = { Icon(Icons.Default.Logout, null, tint = Error) },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Logout", color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color = Primary,
    titleColor: androidx.compose.ui.graphics.Color = TextPrimary,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
        }
    }
}
