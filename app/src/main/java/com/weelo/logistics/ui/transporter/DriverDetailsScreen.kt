package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
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
import com.weelo.logistics.data.model.Driver
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DriverDetailsScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPerformance: (String) -> Unit = {},
    onNavigateToEarnings: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    var driver by remember { mutableStateOf<Driver?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    LaunchedEffect(driverId) {
        scope.launch {
            repository.getDrivers("t1").onSuccess { drivers ->
                driver = drivers.find { it.id == driverId }
                isLoading = false
            }
        }
    }
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Driver Details", onBackClick = onNavigateBack)
        
        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else driver?.let { d ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(SecondaryLight)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👤", style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(d.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(d.mobileNumber, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    }
                }
                
                SectionCard("Contact Information") {
                    DetailRow("Mobile", d.mobileNumber)
                    Divider()
                    DetailRow("License", d.licenseNumber)
                }
                
                SectionCard("Performance") {
                    DetailRow("Rating", "⭐ ${String.format("%.1f", d.rating)}")
                    Divider()
                    DetailRow("Total Trips", d.totalTrips.toString())
                    Divider()
                    DetailRow("Completed", "${d.totalTrips - 5}") // Mock
                    Divider()
                    DetailRow("On-Time Rate", "92%") // Mock
                    Divider()
                    DetailRow("Status", if (d.isAvailable) "Available" else "Not Available")
                }
                
                SectionCard("Earnings Summary") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("This Month", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text("₹45,000", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Success)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Last Month", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text("₹38,500", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    DetailRow("Pending Payment", "₹5,200")
                }
                
                SectionCard("Documents") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("License", style = MaterialTheme.typography.bodyMedium)
                            Text(d.licenseNumber, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        StatusChip("Verified", ChipStatus.AVAILABLE)
                    }
                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Aadhar Card", style = MaterialTheme.typography.bodyMedium)
                        StatusChip("Pending", ChipStatus.PENDING)
                    }
                }
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("Performance", onClick = { onNavigateToPerformance(driverId) }, modifier = Modifier.weight(1f))
                    SecondaryButton("Earnings", onClick = { onNavigateToEarnings(driverId) }, modifier = Modifier.weight(1f))
                }
                
                PrimaryButton("Assign Vehicle", onClick = { /* TODO */ })
                SecondaryButton("View Trip History", onClick = { /* TODO */ })
            }
        }
    }
}
