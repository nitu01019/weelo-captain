package com.weelo.logistics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.weelo.logistics.ui.navigation.WeeloNavigation
import com.weelo.logistics.ui.theme.WeeloTheme

/**
 * Main Activity - Hosts the main app navigation
 * 
 * Launched from SplashActivity with login info
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get login info from SplashActivity
        val isLoggedIn = intent.getBooleanExtra("IS_LOGGED_IN", false)
        val userRole = intent.getStringExtra("USER_ROLE")
        
        setContent {
            WeeloTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                   WeeloNavigation(
                       isLoggedIn = isLoggedIn,
                       userRole = userRole
                   )
                }
            }
        }
    }
}
