package com.weelo.logistics.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.weelo.logistics.ui.components.PrimaryButton
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.ui.components.rememberScreenConfig
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * Role Selection Screen - PRD-01 Compliant
 * Simple 2-card selection: Transporter or Driver
 * Added guide icon with dialog to explain roles
 */
@Composable
fun RoleSelectionScreen(
    onRoleSelected: (String) -> Unit
) {
    var showGuideDialog by remember { mutableStateOf(false) }
    
    // Responsive layout support
    val screenConfig = rememberScreenConfig()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (screenConfig.isLandscape) 48.dp else 24.dp,
                vertical = 24.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Reduced top spacing in landscape
        Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 16.dp else 60.dp))
        
        // Header with Guide Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "You are a:",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 28.sp,
                modifier = Modifier.weight(1f)
            )
            
            // Guide/Help Icon Button
            IconButton(
                onClick = { showGuideDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .shadow(8.dp, CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Primary.copy(alpha = 0.15f), Color.White)
                        ),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = "Role Guide",
                    tint = Primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 24.dp else 48.dp))
        
        // Role Cards - Side by side in landscape, stacked in portrait
        if (screenConfig.isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Transporter Card
                Box(modifier = Modifier.weight(1f)) {
                    RoleCard(
                        icon = Icons.Default.Business,
                        title = "Transporter",
                        description = "I own and manage vehicles",
                        iconColor = Primary,
                        onClick = { onRoleSelected("TRANSPORTER") }
                    )
                }
                
                // Driver Card
                Box(modifier = Modifier.weight(1f)) {
                    RoleCard(
                        icon = Icons.Default.AccountCircle,
                        title = "Driver",
                        description = "I drive vehicles for trips",
                        iconColor = Secondary,
                        onClick = { onRoleSelected("DRIVER") }
                    )
                }
            }
        } else {
            // Portrait - Stacked vertically
            RoleCard(
                icon = Icons.Default.Business,
                title = "Transporter",
                description = "I own and manage vehicles",
                iconColor = Primary,
                onClick = { onRoleSelected("TRANSPORTER") }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            RoleCard(
                icon = Icons.Default.AccountCircle,
                title = "Driver",
                description = "I drive vehicles for trips",
                iconColor = Secondary,
                onClick = { onRoleSelected("DRIVER") }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
    
    // Guide Dialog
    if (showGuideDialog) {
        RoleGuideDialog(onDismiss = { showGuideDialog = false })
    }
}

/**
 * PRD-01 Compliant Role Card with Glassmorphic Design
 * Specs: 140dp height, 16dp border radius, elevated with glass effect
 * Instant tap to navigate (no selection state needed)
 */
@Composable
fun RoleCard(
    icon: ImageVector,
    title: String,
    description: String,
    iconColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = iconColor.copy(alpha = 0.3f),
                spotColor = iconColor.copy(alpha = 0.3f)
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        iconColor.copy(alpha = 0.4f),
                        iconColor.copy(alpha = 0.1f)
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                iconColor.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon container with glow effect
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(16.dp),
                                ambientColor = iconColor.copy(alpha = 0.5f)
                            )
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        iconColor.copy(alpha = 0.2f),
                                        iconColor.copy(alpha = 0.05f)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            modifier = Modifier.size(40.dp),
                            tint = iconColor
                        )
                    }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text on right
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 20.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
            }
        }
    }
}

/**
 * Role Guide Dialog - Explains the difference between roles
 * Modern card-based design with icons and clear descriptions
 */
@Composable
fun RoleGuideDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose Your Role",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Transporter Section
                GuideRoleItem(
                    icon = Icons.Default.Business,
                    iconColor = Primary,
                    title = "Transporter",
                    points = listOf(
                        "Own and manage fleet of vehicles",
                        "Post trip requirements and broadcast to drivers",
                        "Track vehicles and manage drivers in real-time",
                        "Monitor earnings and performance analytics"
                    )
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Divider(color = Divider, thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Driver Section
                GuideRoleItem(
                    icon = Icons.Default.AccountCircle,
                    iconColor = Secondary,
                    title = "Driver",
                    points = listOf(
                        "Drive vehicles for transporters",
                        "Receive trip requests and accept instantly",
                        "Navigate with built-in GPS tracking",
                        "Earn money per trip with transparent payments"
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Got It Button
                PrimaryButton(
                    text = "Got It!",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Individual role item in guide dialog with icon and bullet points
 */
@Composable
fun GuideRoleItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    points: List<String>
) {
    Column {
        // Title with Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                iconColor.copy(alpha = 0.2f),
                                iconColor.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Bullet Points
        points.forEach { point ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = 6.dp)
                        .background(iconColor, CircleShape)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
