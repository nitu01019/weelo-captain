package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.core.notification.BroadcastSoundService
import com.weelo.logistics.core.notification.BroadcastSoundService.SoundOption
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.theme.*

/**
 * =============================================================================
 * BROADCAST SOUND SETTINGS SCREEN
 * =============================================================================
 * 
 * Allows transporter to customize notification sounds for broadcasts.
 * 
 * FEATURES:
 * - Select from multiple sound options
 * - Preview sounds before selecting
 * - Enable/disable sound and vibration
 * - Volume control
 * 
 * =============================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastSoundSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val soundService = remember { BroadcastSoundService.getInstance(context) }
    
    val selectedSound by soundService.selectedSound.collectAsState()
    val soundEnabled by soundService.soundEnabled.collectAsState()
    val vibrationEnabled by soundService.vibrationEnabled.collectAsState()
    val volume by soundService.volume.collectAsState()
    
    Scaffold(
        topBar = {
            PrimaryTopBar(
                title = stringResource(R.string.notification_sound),
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Surface),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sound Toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                null,
                                tint = if (soundEnabled) Primary else TextSecondary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Notification Sound",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    if (soundEnabled) "On" else "Off",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { soundService.setSoundEnabled(it) }
                        )
                    }
                }
            }
            
            // Vibration Toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Vibration,
                                null,
                                tint = if (vibrationEnabled) Primary else TextSecondary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Vibration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    if (vibrationEnabled) "On" else "Off",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { soundService.setVibrationEnabled(it) }
                        )
                    }
                }
            }
            
            // Volume Slider
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, null, tint = Primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Volume",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${(volume * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = volume,
                            onValueChange = { soundService.setVolume(it) },
                            enabled = soundEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = Primary,
                                activeTrackColor = Primary
                            )
                        )
                    }
                }
            }
            
            // Sound Options Header
            item {
                Text(
                    "SELECT NOTIFICATION SOUND",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            // Sound Options
            items(SoundOption.getAll()) { option ->
                SoundOptionCard(
                    option = option,
                    isSelected = selectedSound == option,
                    enabled = soundEnabled,
                    onSelect = {
                        soundService.setSelectedSound(option)
                    },
                    onPreview = {
                        soundService.previewSound(option)
                    }
                )
            }
            
            // Test Sound Button
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { soundService.playBroadcastSound() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = soundEnabled,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Test Current Sound", fontWeight = FontWeight.Bold)
                }
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/**
 * Sound Option Card
 */
@Composable
private fun SoundOptionCard(
    option: SoundOption,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.1f) else White
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, Primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Primary else Divider
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Option info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    option.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (enabled) TextPrimary else TextDisabled
                )
                Text(
                    option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) TextSecondary else TextDisabled
                )
            }
            
            // Preview button
            IconButton(
                onClick = onPreview,
                enabled = enabled
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    "Preview",
                    tint = if (enabled) Primary else TextDisabled
                )
            }
        }
    }
}
