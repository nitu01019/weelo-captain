package com.weelo.logistics.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.components.PrimaryButton
import com.weelo.logistics.ui.theme.*

/**
 * Role Selection Screen - PRD-01 Compliant
 * Simple 2-card selection: Transporter or Driver
 * Removed "Both" option as per new PRD
 */
@Composable
fun RoleSelectionScreen(
    onRoleSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        
        // PRD Compliant Header
        Text(
            text = "You are a:",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            fontSize = 28.sp
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Transporter Card - Tappable with instant navigation
        RoleCard(
            emoji = "🚛",
            title = "Transporter",
            description = "I own and manage vehicles",
            iconColor = Primary,
            onClick = { onRoleSelected("TRANSPORTER") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Driver Card - Tappable with instant navigation
        RoleCard(
            emoji = "👤",
            title = "Driver",
            description = "I drive vehicles for trips",
            iconColor = Secondary,
            onClick = { onRoleSelected("DRIVER") }
        )
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * PRD-01 Compliant Role Card
 * Specs: 140dp height, 16dp border radius, 2dp elevation
 * Instant tap to navigate (no selection state needed)
 */
@Composable
fun RoleCard(
    emoji: String,
    title: String,
    description: String,
    iconColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = androidx.compose.foundation.BorderStroke(2.dp, Divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon on left (48dp as per PRD)
            Text(
                text = emoji,
                fontSize = 48.sp,
                color = iconColor
            )
            
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
