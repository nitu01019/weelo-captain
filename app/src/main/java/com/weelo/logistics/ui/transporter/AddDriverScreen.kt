package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weelo.logistics.data.api.CreateDriverRequest
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AddDriverScreen - Clean Modern UI for Driver Registration
 * 
 * Purpose: Transporter adds new driver to their fleet
 * 
 * Required Fields:
 * 1. Phone Number (10 digits)
 * 2. Full Name
 * 3. License Number
 * 
 * Backend Endpoint: POST /api/v1/driver/create
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDriverScreen(
    transporterId: String,
    onNavigateBack: () -> Unit,
    onDriverAdded: () -> Unit
) {
    // Form state
    var phoneNumber by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    
    // UI state
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Validation
    val isFormValid = phoneNumber.length == 10 && fullName.isNotBlank() && licenseNumber.isNotBlank()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Add Driver",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Surface)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar Icon
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Add New Driver",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    Text(
                        text = "Fill in the details to add a driver to your fleet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // Form Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section Header
                    Text(
                        text = "Driver Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    
                    // Phone Number
                    CleanTextField(
                        value = phoneNumber,
                        onValueChange = { 
                            if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                                phoneNumber = it
                                errorMessage = null
                            }
                        },
                        label = "Phone Number",
                        placeholder = "Enter 10-digit phone number",
                        leadingIcon = Icons.Default.Phone,
                        keyboardType = KeyboardType.Phone,
                        isRequired = true,
                        isError = errorMessage?.contains("Phone") == true
                    )
                    
                    // Full Name
                    CleanTextField(
                        value = fullName,
                        onValueChange = { 
                            fullName = it
                            errorMessage = null
                        },
                        label = "Full Name",
                        placeholder = "Enter driver's full name",
                        leadingIcon = Icons.Default.Person,
                        isRequired = true,
                        isError = errorMessage?.contains("Name") == true
                    )
                    
                    // License Number
                    CleanTextField(
                        value = licenseNumber,
                        onValueChange = { 
                            licenseNumber = it.uppercase()
                            errorMessage = null
                        },
                        label = "License Number",
                        placeholder = "e.g., DL1234567890",
                        leadingIcon = Icons.Default.Badge,
                        isRequired = true,
                        isError = errorMessage?.contains("License") == true
                    )
                    
                    // Error Message
                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Error
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Info.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Info,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "The driver can login using their phone number after you add them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Add Driver Button
            Button(
                onClick = {
                    // Validate
                    when {
                        phoneNumber.length != 10 -> {
                            errorMessage = "Phone number must be 10 digits"
                        }
                        fullName.isBlank() -> {
                            errorMessage = "Name is required"
                        }
                        licenseNumber.isBlank() -> {
                            errorMessage = "License number is required"
                        }
                        else -> {
                            // Make API call
                            isLoading = true
                            errorMessage = null
                            
                            scope.launch {
                                try {
                                    val request = CreateDriverRequest(
                                        phone = phoneNumber,
                                        name = fullName.trim(),
                                        licenseNumber = licenseNumber.trim()
                                    )
                                    
                                    val response = withContext(Dispatchers.IO) {
                                        RetrofitClient.driverApi.createDriver(request)
                                    }
                                    
                                    isLoading = false
                                    
                                    if (response.isSuccessful && response.body()?.success == true) {
                                        snackbarHostState.showSnackbar(
                                            message = "Driver added successfully!",
                                            duration = SnackbarDuration.Short
                                        )
                                        delay(500)
                                        onDriverAdded()
                                    } else {
                                        errorMessage = response.body()?.message 
                                            ?: response.body()?.error?.message 
                                            ?: "Failed to add driver"
                                    }
                                } catch (e: Exception) {
                                    isLoading = false
                                    errorMessage = "Network error. Please try again."
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = isFormValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Adding Driver...",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Driver",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Clean TextField component for forms
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isRequired: Boolean = false,
    isError: Boolean = false
) {
    Column {
        // Label
        Row {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            if (isRequired) {
                Text(
                    text = " *",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Error
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Text Field
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    color = TextDisabled
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isError) Error else TextSecondary
                )
            },
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = TextDisabled.copy(alpha = 0.3f),
                errorBorderColor = Error,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Surface,
                errorContainerColor = Error.copy(alpha = 0.05f)
            )
        )
    }
}
