package com.weelo.logistics.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.launch

/**
 * SplashScreen - App Entry Point (Optimized for Scale)
 * 
 * Performance:
 * - Reduced animation time: 500ms (was 800ms)
 * - Total duration: 1.5s (was 2.5s)
 * - Memory efficient: Single composable, minimal state
 * 
 * Scalability:
 * - No network calls (fast startup)
 * - No database queries on main thread
 * - Async auth check ready (TODO)
 * 
 * Clear naming: SplashScreen = App entry, shows branding
 */
@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigateToLogin: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigateToDashboard: (String) -> Unit
) {
    // Optimized animations - faster for better UX
    val scale = remember { Animatable(0f) }
    val greetingAlpha = remember { Animatable(0f) }
    
    LaunchedEffect(key1 = Unit) {
        // Parallel animations for faster load
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 500, // Optimized: 500ms (was 800ms)
                    easing = FastOutSlowInEasing
                )
            )
        }
        
        launch {
            delay(200) // Small delay for stagger effect
            greetingAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
        }
        
        delay(1500) // Total: 1.5s (optimized from 2.5s)
        
        // TODO Production: Check auth status asynchronously
        // val isLoggedIn = withContext(Dispatchers.IO) { authRepository.isUserLoggedIn() }
        // if (isLoggedIn) onNavigateToDashboard(role) else onNavigateToOnboarding()
        
        // Skip onboarding - go directly to role selection (not onboarding)
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
            // Logo - Using Material Icon instead of emoji
            Icon(
                imageVector = Icons.Default.LocalShipping,
                contentDescription = "Weelo Logo",
                tint = Color.White,
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale.value)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Greeting - Always in English (before language selection)
            Text(
                text = "Hello Weelo Captains",
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
