package com.weelo.logistics.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weelo.logistics.ui.components.*
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Driver Profile Edit Screen - PRD-03 Compliant
 * Edit personal information and upload photo
 */
@Composable
fun DriverProfileEditScreen(
    driverId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember { mutableStateOf("Rajesh Kumar") }
    var mobile by remember { mutableStateOf("9876543210") }
    var email by remember { mutableStateOf("rajesh@example.com") }
    var emergencyContact by remember { mutableStateOf("9876543211") }
    var address by remember { mutableStateOf("123 Main Street, Mumbai") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    Column(Modifier.fillMaxSize().background(Surface)) {
        PrimaryTopBar(title = "Edit Profile", onBackClick = onNavigateBack)
        
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Photo Section
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(100.dp)
                            .background(Surface, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👤", style = MaterialTheme.typography.displayLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { /* TODO: Upload photo */ }) {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Change Photo")
                    }
                }
            }
            
            Text(
                "Personal Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            PrimaryTextField(
                value = name,
                onValueChange = { name = it; errorMessage = "" },
                label = "Full Name *",
                placeholder = "Enter name",
                leadingIcon = Icons.Default.Person
            )
            
            PrimaryTextField(
                value = mobile,
                onValueChange = { if (it.length <= 10) mobile = it },
                label = "Mobile Number *",
                placeholder = "10-digit number",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = false // Usually not editable after registration
            )
            
            PrimaryTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email (Optional)",
                placeholder = "your@email.com",
                leadingIcon = Icons.Default.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
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
                label = "Address",
                placeholder = "Enter full address",
                leadingIcon = Icons.Default.Home,
                maxLines = 3
            )
            
            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = Error, style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Column(Modifier.padding(16.dp)) {
            PrimaryButton(
                text = "Save Changes",
                onClick = {
                    when {
                        name.isEmpty() -> errorMessage = "Name is required"
                        else -> {
                            isLoading = true
                            scope.launch {
                                onSaved()
                            }
                        }
                    }
                },
                isLoading = isLoading
            )
        }
    }
}
