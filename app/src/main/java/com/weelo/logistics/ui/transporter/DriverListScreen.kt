package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.model.Driver
import com.weelo.logistics.data.model.DriverStatus
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DriverListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddDriver: () -> Unit,
    onNavigateToDriverDetails: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    var drivers by remember { mutableStateOf<List<Driver>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    
    LaunchedEffect(Unit) {
        scope.launch {
            repository.getDrivers("t1").onSuccess { driverList ->
                drivers = driverList
                isLoading = false
            }
        }
    }
    
    val filteredDrivers = drivers.filter { driver ->
        val matchesSearch = driver.name.contains(searchQuery, ignoreCase = true) ||
                driver.mobileNumber.contains(searchQuery)
        val matchesFilter = when (selectedFilter) {
            "Available" -> driver.status == DriverStatus.ACTIVE && driver.isAvailable
            "On Trip" -> driver.status == DriverStatus.ON_TRIP
            "Inactive" -> driver.status == DriverStatus.INACTIVE
            else -> true
        }
        matchesSearch && matchesFilter
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Surface)) {
            PrimaryTopBar(
                title = "Drivers (${drivers.size})",
                onBackClick = onNavigateBack
            )
            
            Column(Modifier.fillMaxWidth().background(White).padding(16.dp)) {
                SearchTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search by name or mobile",
                    leadingIcon = Icons.Default.Search
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = selectedFilter == "Available",
                        onClick = { selectedFilter = "Available" },
                        label = { Text("Available") }
                    )
                    FilterChip(
                        selected = selectedFilter == "On Trip",
                        onClick = { selectedFilter = "On Trip" },
                        label = { Text("On Trip") }
                    )
                }
            }
            
            if (isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (filteredDrivers.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Person, null, Modifier.size(64.dp), tint = TextDisabled)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isEmpty()) "No drivers yet" else "No drivers found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDrivers) { driver ->
                        DriverCard(driver) { onNavigateToDriverDetails(driver.id) }
                    }
                }
            }
        }
        
        FloatingActionButton(
            onClick = onNavigateToAddDriver,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Primary
        ) {
            Icon(Icons.Default.Add, "Add Driver", tint = White)
        }
    }
}

@Composable
fun DriverCard(driver: Driver, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(2.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(White)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).background(Surface, androidx.compose.foundation.shape.CircleShape),
                Alignment.Center
            ) {
                Text("👤", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(driver.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(driver.mobileNumber, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐ ${String.format("%.1f", driver.rating)}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Text("${driver.totalTrips} trips", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Spacer(Modifier.width(12.dp))
            StatusChip(
                text = when (driver.status) {
                    DriverStatus.ACTIVE -> if (driver.isAvailable) "Available" else "Offline"
                    DriverStatus.ON_TRIP -> "On Trip"
                    DriverStatus.INACTIVE -> "Inactive"
                    DriverStatus.SUSPENDED -> "Suspended"
                },
                status = when (driver.status) {
                    DriverStatus.ACTIVE -> if (driver.isAvailable) ChipStatus.AVAILABLE else ChipStatus.COMPLETED
                    DriverStatus.ON_TRIP -> ChipStatus.IN_PROGRESS
                    else -> ChipStatus.CANCELLED
                }
            )
        }
    }
}
