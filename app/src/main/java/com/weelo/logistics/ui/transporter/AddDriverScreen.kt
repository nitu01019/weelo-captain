package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.data.model.Driver
import com.weelo.logistics.data.model.DriverStatus
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AddDriverScreen(
    onNavigateBack: () -> Unit,
    onDriverAdded: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("") }
    var licenseExpiry by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val repository = remember { MockDataRepository() }
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Add Driver", onBackClick = onNavigateBack)
        
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card with icon
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF4A90E2).copy(alpha = 0.1f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                androidx.compose.ui.graphics.Color(0xFF4A90E2),
                                androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "👤",
                            fontSize = 24.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Driver Information",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Add a new driver to your fleet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            PrimaryTextField(
                value = name,
                onValueChange = { name = it; errorMessage = "" },
                label = "Full Name *",
                placeholder = "Enter driver name",
                leadingIcon = Icons.Default.Person
            )
            
            PrimaryTextField(
                value = mobile,
                onValueChange = { if (it.length <= 10) mobile = it; errorMessage = "" },
                label = "Mobile Number *",
                placeholder = "10-digit mobile number",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = errorMessage.isNotEmpty(),
                errorMessage = errorMessage
            )
            
            Divider()
            Text(
                "License Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            PrimaryTextField(
                value = license,
                onValueChange = { license = it.uppercase() },
                label = "License Number *",
                placeholder = "DL1420110012345",
                leadingIcon = Icons.Default.Badge
            )
            
            PrimaryTextField(
                value = licenseExpiry,
                onValueChange = { licenseExpiry = it },
                label = "License Valid Till",
                placeholder = "DD/MM/YYYY",
                leadingIcon = Icons.Default.CalendarMonth
            )
            
            Divider()
            Text(
                "Emergency Contact",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            PrimaryTextField(
                value = emergencyContact,
                onValueChange = { if (it.length <= 10) emergencyContact = it },
                label = "Emergency Mobile",
                placeholder = "10-digit number",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            
            PrimaryTextField(
                value = address,
                onValueChange = { address = it },
                label = "Address (Optional)",
                placeholder = "Enter residential address",
                leadingIcon = Icons.Default.Home,
                maxLines = 2
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(SecondaryLight)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = Secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Driver will receive SMS invitation to download and login to the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            PrimaryButton(
                text = "Send Invitation",
                onClick = {
                    when {
                        name.isEmpty() -> errorMessage = "Please enter driver name"
                        mobile.isEmpty() -> errorMessage = "Please enter mobile number"
                        mobile.length != 10 -> errorMessage = "Mobile number must be 10 digits"
                        license.isEmpty() -> errorMessage = "Please enter license number"
                        else -> {
                            isLoading = true
                            scope.launch {
                                val driver = Driver(
                                    id = "d_${System.currentTimeMillis()}",
                                    name = name,
                                    mobileNumber = mobile,
                                    licenseNumber = license,
                                    transporterId = "t1",
                                    status = DriverStatus.ACTIVE
                                )
                                repository.addDriver(driver)
                                onDriverAdded()
                            }
                        }
                    }
                },
                isLoading = isLoading,
                enabled = !isLoading
            )
        }
    }
}
