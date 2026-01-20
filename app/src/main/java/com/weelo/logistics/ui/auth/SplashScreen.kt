package com.weelo.logistics.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

// Splash Colors
private val SplashBackground = Color(0xFF3D2400)  // Dark brown
private val CircleOrange = Color(0xFFFF8C00)      // Orange
private val TextColor = Color(0xFFFFFFFF)         // White text

/**
 * SplashScreen - INSTANT LOAD
 * 
 * - Shows immediately when app opens
 * - "Weelo Captain" visible instantly
 * - 2 seconds display time total
 */
@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigateToLogin: () -> Unit,
    onNavigateToDashboard: (String) -> Unit
) {
    // Track when splash started
    val startTime = remember { System.currentTimeMillis() }
    
    LaunchedEffect(key1 = Unit) {
        // ⚡ Check login in background while showing splash
        val loginCheckJob = async(Dispatchers.IO) {
            Pair(RetrofitClient.isLoggedIn(), RetrofitClient.getUserRole())
        }
        
        // Wait for login check
        val (isLoggedIn, userRole) = loginCheckJob.await()
        
        // Calculate remaining time to make total 2 seconds
        val elapsed = System.currentTimeMillis() - startTime
        val remainingTime = (2000 - elapsed).coerceAtLeast(0)
        
        // Wait remaining time
        delay(remainingTime)
        
        // Navigate
        if (isLoggedIn && userRole != null) {
            onNavigateToDashboard(userRole.uppercase())
        } else {
            onNavigateToOnboarding()
        }
    }
    
    // Dark brown background - instant render
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground),
        contentAlignment = Alignment.Center
    ) {
        // Glassmorphic outer ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = CircleShape
                )
        )
        
        // Main orange circle with text
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(CircleOrange, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Weelo",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = TextColor,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Captain",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
