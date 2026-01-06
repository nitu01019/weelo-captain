package com.weelo.logistics.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.SimpleTopBar
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Driver Dashboard Screen
 * 
 * BACKEND INTEGRATION:
 * ====================
 * This screen fetches driver dashboard data from backend.
 * 
 * TODO BEFORE BACKEND IS READY:
 * - Pass driverId from UserPreferencesRepository (logged-in user)
 * - Handle loading states properly
 * - Handle error states with retry option
 * - Add pull-to-refresh functionality
 * 
 * CURRENT STATUS: UI ready, waiting for backend connection
 */
@Composable
fun DriverDashboardScreen(
    driverId: String = "DRIVER_ID_FROM_LOGIN" // TODO: Get from UserPreferencesRepository
) {
    val scope = rememberCoroutineScope()
    // TODO: Replace with actual DriverRepository when backend is ready
    // val repository = remember { DriverRepository() }
    
    var dashboardData by remember { mutableStateOf<com.weelo.logistics.data.model.DriverDashboard?>(null) }
    var isAvailable by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Function to load dashboard data
    fun loadDashboard() {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            // TODO: Uncomment when backend is ready
            // val result = repository.getDriverDashboard(driverId)
            // result.onSuccess { data ->
            //     dashboardData = data
            //     isAvailable = data.isAvailable
            //     isLoading = false
            // }.onFailure { error ->
            //     errorMessage = error.message ?: "Failed to load dashboard"
            //     isLoading = false
            // }
            
            // TEMPORARY: Show empty state until backend connected
            isLoading = false
            errorMessage = "Connect backend to load dashboard data"
        }
    }
    
    LaunchedEffect(Unit) {
        loadDashboard()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        SimpleTopBar(
            title = "Dashboard",
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Notifications, null, tint = TextPrimary)
                }
            }
        )
        
        // Loading State
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        }
        
        // Error State
        else if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(onClick = { loadDashboard() }) {
                        Text("Retry")
                    }
                    Text(
                        text = "Backend not connected yet.\nWaiting for API integration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        
        // Dashboard Data
        else if (dashboardData != null) {
            val data = dashboardData!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Hello Driver! 👋",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(if (isAvailable) SuccessLight else Surface)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isAvailable) "● AVAILABLE" else "● OFFLINE",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(checked = isAvailable, onCheckedChange = { isAvailable = it })
                        }
                    }
                }
                
                item {
                    Text("Today's Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(Modifier.weight(1f)) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${data.todayTrips}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Primary)
                                Text("Trips", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Card(Modifier.weight(1f)) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${data.todayDistance.toInt()}km", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Primary)
                                Text("Distance", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Card(Modifier.weight(1f)) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("₹${data.todayEarnings.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Success)
                                Text("Earnings", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
