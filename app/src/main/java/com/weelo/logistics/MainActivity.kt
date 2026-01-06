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
 * Main Activity - Entry point of the app
 * Hosts the navigation graph and theme
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeeloTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WeeloNavigation()
                }
            }
        }
    }
}
