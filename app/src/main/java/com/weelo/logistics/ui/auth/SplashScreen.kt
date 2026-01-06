package com.weelo.logistics.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.theme.Primary
import kotlinx.coroutines.delay

/**
 * Splash Screen - First screen shown when app opens
 * PRD-01 Compliant: Shows "Hello Weelo Captains" greeting
 */
@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToDashboard: (String) -> Unit
) {
    // Animations
    val scale = remember { Animatable(0f) }
    val greetingAlpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Logo animation
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing
            )
        )
        
        // Greeting fade in after logo
        delay(100)
        greetingAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
        
        delay(500) // Brief delay to show splash
        
        // TODO: Check user session
        // For now, navigate to onboarding (or role selection if already completed)
        onNavigateToOnboarding()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            // Logo
            Text(
                text = "🚛",
                fontSize = 120.sp,
                modifier = Modifier.scale(scale.value)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Greeting - PRD Compliant
            Text(
                text = "Hello Weelo Captains ⚓",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.alpha(greetingAlpha.value)
            )
        }
        
        // Loading indicator at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}
