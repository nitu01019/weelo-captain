package com.weelo.logistics.ui.transporter

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.InputValidator
import com.weelo.logistics.utils.AddDriverRequest
import com.weelo.logistics.utils.ClickDebouncer
import com.weelo.logistics.utils.DataSanitizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AddDriverScreen - Futuristic Design for Driver Registration
 * 
 * Purpose: Transporter adds new driver to their fleet
 * 
 * Fields:
 * 1. Phone Number (10 digits, required)
 * 2. Full Name (3-100 chars, required)
 * 3. License Number (10-20 chars, required)
 * 4. Email (optional)
 * 5. Emergency Contact (optional)
 * 
 * Validation: Client-side validation before API call
 * Backend Endpoint: POST /api/v1/transporter/{transporterId}/drivers
 */
@Composable
fun AddDriverScreen(
    transporterId: String,
    onNavigateBack: () -> Unit,
    onDriverAdded: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clickDebouncer = remember { ClickDebouncer(500L) }
    
    // Fade-in animation
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "alpha *"
    )
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E27),
                        MaterialTheme.colorScheme.surface,
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        // Static background decoration
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.2f)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 50.dp, y = (-100).dp)
                    .size(300.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Primary.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )
            
            Box(
                modifier = Modifier
                    .offset(x = 250.dp, y = 500.dp)
                    .size(250.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Secondary.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )
        }
        
        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Top bar with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Text(
                    text = "Add Driver",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Box(modifier = Modifier.size(48.dp)) // Spacer for alignment
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Primary.copy(alpha = 0.5f)
                    )
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Primary.copy(alpha = 0.3f),
                                Primary.copy(alpha = 0.1f)
                            )
                        ),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add Driver",
                    modifier = Modifier.size(40.dp),
                    tint = Primary
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Add New Driver",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = "Enter driver details to add them to your fleet",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Phone Number
            FuturisticInput(
                value = phoneNumber,
                onValueChange = { 
                    if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                        phoneNumber = it.trim()
                        errorMessage = ""
                    }
                },
                label = "Phone Number *",
                icon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Full Name
            FuturisticInput(
                value = fullName,
                onValueChange = { 
                    fullName = it.trim()
                    errorMessage = ""
                },
                label = "Full Name *",
                icon = Icons.Default.Person,
                keyboardType = KeyboardType.Text
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // License Number
            FuturisticInput(
                value = licenseNumber,
                onValueChange = { 
                    licenseNumber = it.uppercase()
                    errorMessage = ""
                },
                label = "License Number *",
                icon = Icons.Default.Badge,
                keyboardType = KeyboardType.Text
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Email (Optional)
            FuturisticInput(
                value = email,
                onValueChange = { 
                    email = it.trim()
                    errorMessage = ""
                },
                label = "Email (Optional)",
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Emergency Contact (Optional)
            FuturisticInput(
                value = emergencyContact,
                onValueChange = { 
                    if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                        emergencyContact = it.trim()
                        errorMessage = ""
                    }
                },
                label = "Emergency Contact (Optional)",
                icon = Icons.Default.ContactPhone,
                keyboardType = KeyboardType.Phone
            )
            
            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = Error,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Add Driver Button
            FuturisticActionButton(
                text = "Add Driver",
                onClick = {
                    // Debounce to prevent rapid clicks
                    if (!clickDebouncer.canClick()) return@FuturisticActionButton
                    
                    // Build DTO with validation
                    val dtoResult = AddDriverRequest.build(
                        name = fullName,
                        phone = phoneNumber,
                        licenseNumber = licenseNumber,
                        email = email.takeIf { it.isNotEmpty() },
                        address = null,
                        city = null,
                        state = null,
                        pincode = null
                    )
                    
                    dtoResult.onSuccess { dto ->
                        // All valid, proceed
                        isLoading = true
                        scope.launch {
                            // TODO BACKEND: Call actual API with DTO
                            // val payload = dto.toMap()
                            // api.addDriver(transporterId, payload)
                        //     "name" to fullName,
                        //     "licenseNumber" to licenseNumber,
                        //     "email" to email,
                        //     "emergencyContact" to emergencyContact
                        // )
                        // val result = transporterViewModel.addDriver(transporterId, driverData)
                        
                            delay(1000) // Simulate API call
                            
                            isLoading = false
                            snackbarHostState.showSnackbar(
                                message = "Driver ${DataSanitizer.sanitizeForDisplay(fullName)} added successfully!",
                                duration = SnackbarDuration.Short
                            )
                            delay(500)
                            onDriverAdded()
                        }
                    }.onFailure { error ->
                        errorMessage = error.message ?: "Invalid data"
                    }
                },
                enabled = phoneNumber.length == 10 && fullName.isNotEmpty() && 
                          licenseNumber.isNotEmpty() && !isLoading,
                isLoading = isLoading
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Info box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "Driver will receive login credentials via SMS",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun FuturisticInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.6f)) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Primary.copy(alpha = 0.3f)
            ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Color(0xFF1A1F3A).copy(alpha = 0.6f),
            unfocusedContainerColor = Color(0xFF1A1F3A).copy(alpha = 0.6f),
            focusedBorderColor = Primary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            cursorColor = Primary,
            focusedLabelColor = Primary,
            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    )
}

@Composable
fun FuturisticActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled && !isLoading) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale *"
    )
    
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Primary.copy(alpha = 0.5f)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            disabledContainerColor = Primary.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Adding...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * BACKEND API CALL:
 * 
 * POST /api/v1/transporter/T001/drivers
 * Authorization: Bearer {jwt_token}
 * Content-Type: application/json
 * 
 * Body:
 * {
 *   "phone": "+919876543210",
 *   "name": "John Driver",
 *   "licenseNumber": "MH1234567890",
 *   "email": "john@example.com",
 *   "emergencyContact": "+919876543211"
 * }
 * 
 * Response:
 * {
 *   "success": true,
 *   "message": "Driver added successfully",
 *   "data": {
 *     "driverId": "D025",
 *     "phone": "+919876543210",
 *     "name": "John Driver",
 *     "status": "PENDING_VERIFICATION",
 *     "tempPassword": "TEMP123"
 *   }
 * }
 * 
 * Backend Logic:
 * 1. Validate inputs (phone format, name, license)
 * 2. Check if phone already exists
 * 3. Generate temporary password
 * 4. Create driver record with transporter_id
 * 5. Send SMS: "Welcome to Weelo! Your temp password: TEMP123"
 * 6. Return driver ID and credentials
 */
