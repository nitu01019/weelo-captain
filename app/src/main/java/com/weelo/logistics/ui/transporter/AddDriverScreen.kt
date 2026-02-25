package com.weelo.logistics.ui.transporter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.data.api.InitiateDriverOnboardingRequest
import com.weelo.logistics.data.api.VerifyDriverOnboardingRequest
import com.weelo.logistics.data.api.ResendDriverOtpRequest
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * =============================================================================
 * ADD DRIVER SCREEN - Single Page with Inline OTP
 * =============================================================================
 * 
 * REDESIGNED FLOW (All on one page):
 * 1. Enter driver's phone number
 * 2. Click "Send OTP" → OTP section appears BELOW phone field
 * 3. Enter OTP (received on driver's phone)
 * 4. Click "Verify & Add" → Driver added
 * 
 * BACKEND ENDPOINTS:
 * - POST /api/v1/driver-onboarding/initiate (Send OTP to driver's phone)
 * - POST /api/v1/driver-onboarding/verify (Verify OTP and add driver)
 * - POST /api/v1/driver-onboarding/resend (Resend OTP)
 * 
 * KEY UX IMPROVEMENTS:
 * - No page navigation for OTP (inline experience)
 * - Clear indication that OTP goes to DRIVER's phone
 * - Proper input field widths for portrait mode
 * - Auto-verify when 6 digits entered
 * 
 * =============================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDriverScreen(
    @Suppress("UNUSED_PARAMETER") transporterId: String, // Reserved for future use
    onNavigateBack: () -> Unit,
    onDriverAdded: () -> Unit
) {
    // ==========================================================================
    // STATE
    // ==========================================================================
    
    // Form fields
    var phoneNumber by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    
    // OTP state
    var otpCode by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var isPhoneVerified by remember { mutableStateOf(false) }
    
    // UI state
    @Suppress("UNUSED_VARIABLE")
    var isLoading by remember { mutableStateOf(false) } // Reserved for future full-form loading
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Resend OTP timer
    var canResendOtp by remember { mutableStateOf(false) }
    var resendCountdown by remember { mutableStateOf(30) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // ==========================================================================
    // COUNTDOWN TIMER FOR RESEND OTP
    // ==========================================================================
    LaunchedEffect(isOtpSent) {
        if (isOtpSent && !isPhoneVerified) {
            canResendOtp = false
            resendCountdown = 30
            while (resendCountdown > 0) {
                delay(1000)
                resendCountdown--
            }
            canResendOtp = true
        }
    }
    
    // ==========================================================================
    // AUTO-VERIFY WHEN 6 DIGITS ENTERED
    // ==========================================================================
    LaunchedEffect(otpCode) {
        if (otpCode.length == 6 && isOtpSent && !isVerifyingOtp && !isPhoneVerified) {
            isVerifyingOtp = true
            errorMessage = null
            
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.driverApi.verifyDriverOnboarding(
                        VerifyDriverOnboardingRequest(phone = phoneNumber, otp = otpCode)
                    )
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    isPhoneVerified = true
                    successMessage = "Phone verified! Complete the form to add driver."
                    snackbarHostState.showSnackbar("Driver added successfully!")
                    delay(500)
                    onDriverAdded()
                } else {
                    errorMessage = response.body()?.error?.message ?: "Invalid OTP"
                    otpCode = ""
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.message}"
                otpCode = ""
            } finally {
                isVerifyingOtp = false
            }
        }
    }
    
    // Validation
    val isPhoneValid = phoneNumber.length == 10
    val isFormComplete = isPhoneValid && fullName.isNotBlank() && licenseNumber.isNotBlank()
    
    // ==========================================================================
    // SEND OTP FUNCTION
    // ==========================================================================
    fun sendOtp() {
        if (!isPhoneValid) {
            errorMessage = "Enter valid 10-digit phone number"
            return
        }
        if (fullName.isBlank()) {
            errorMessage = "Enter driver's name"
            return
        }
        if (licenseNumber.isBlank()) {
            errorMessage = "Enter license number"
            return
        }
        
        isSendingOtp = true
        errorMessage = null
        
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.driverApi.initiateDriverOnboarding(
                        InitiateDriverOnboardingRequest(
                            phone = phoneNumber,
                            name = fullName.trim(),
                            licenseNumber = licenseNumber.trim(),
                            licensePhoto = null
                        )
                    )
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    isOtpSent = true
                    successMessage = "OTP sent to driver's phone"
                    snackbarHostState.showSnackbar("OTP sent to driver's phone!")
                } else {
                    errorMessage = when (response.code()) {
                        401 -> "Session expired. Please login again."
                        403 -> "Permission denied"
                        409 -> response.body()?.error?.message ?: "Driver already exists"
                        else -> response.body()?.error?.message ?: "Failed to send OTP"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.message}"
            } finally {
                isSendingOtp = false
            }
        }
    }
    
    // ==========================================================================
    // RESEND OTP FUNCTION
    // ==========================================================================
    fun resendOtp() {
        if (!canResendOtp) return
        
        isSendingOtp = true
        errorMessage = null
        otpCode = ""
        
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.driverApi.resendDriverOnboardingOtp(
                        ResendDriverOtpRequest(phone = phoneNumber)
                    )
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    canResendOtp = false
                    resendCountdown = 30
                    snackbarHostState.showSnackbar("OTP resent!")
                    // Restart countdown
                    while (resendCountdown > 0) {
                        delay(1000)
                        resendCountdown--
                    }
                    canResendOtp = true
                } else {
                    errorMessage = response.body()?.error?.message ?: "Failed to resend"
                }
            } catch (e: Exception) {
                errorMessage = "Network error"
            } finally {
                isSendingOtp = false
            }
        }
    }
    
    // ==========================================================================
    // UI
    // ==========================================================================
    Scaffold(
        topBar = {
            PrimaryTopBar(
                title = stringResource(R.string.add_driver),
                onBackClick = onNavigateBack
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==================== HEADER ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Add New Driver",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    Text(
                        text = "OTP will be sent to driver's phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // ==================== FORM CARD ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Driver Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    
                    // ==================== PHONE NUMBER FIELD ====================
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormTextField(
                            value = phoneNumber,
                            onValueChange = { 
                                if (it.length <= 10 && it.all { c -> c.isDigit() }) {
                                    phoneNumber = it
                                    errorMessage = null
                                    // Reset OTP if phone changed
                                    if (isOtpSent) {
                                        isOtpSent = false
                                        otpCode = ""
                                        isPhoneVerified = false
                                    }
                                }
                            },
                            label = "Phone Number",
                            placeholder = "10-digit mobile number",
                            leadingIcon = Icons.Default.Phone,
                            keyboardType = KeyboardType.Phone,
                            isRequired = true,
                            enabled = !isOtpSent,
                            trailingContent = {
                                if (isPhoneVerified) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = Success,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        )
                        
                        // ==================== INLINE OTP SECTION ====================
                        AnimatedVisibility(
                            visible = isOtpSent && !isPhoneVerified,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Primary.copy(alpha = 0.05f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Sms,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Enter OTP sent to driver",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = Primary
                                        )
                                    }
                                    
                                    // 6-Digit OTP Input
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            repeat(6) { index ->
                                                OtpDigitBox(
                                                    digit = otpCode.getOrNull(index)?.toString() ?: "",
                                                    isFilled = index < otpCode.length,
                                                    isActive = index == otpCode.length,
                                                    hasError = errorMessage != null
                                                )
                                            }
                                        }
                                        
                                        // Invisible input field
                                        BasicTextField(
                                            value = otpCode,
                                            onValueChange = { newValue ->
                                                if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                                                    otpCode = newValue
                                                    errorMessage = null
                                                }
                                            },
                                            modifier = Modifier
                                                .matchParentSize()
                                                .padding(horizontal = 20.dp),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.NumberPassword
                                            ),
                                            enabled = !isVerifyingOtp,
                                            decorationBox = { Box(Modifier.fillMaxSize()) }
                                        )
                                    }
                                    
                                    // Verifying indicator
                                    if (isVerifyingOtp) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Primary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Verifying...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Primary
                                            )
                                        }
                                    }
                                    
                                    // Resend OTP
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Didn't receive? ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                        
                                        if (canResendOtp) {
                                            TextButton(
                                                onClick = { resendOtp() },
                                                contentPadding = PaddingValues(horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    "Resend OTP",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Primary
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Resend in ${resendCountdown}s",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextDisabled
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Send OTP Button (shown when OTP not sent)
                        AnimatedVisibility(
                            visible = !isOtpSent && isFormComplete,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Button(
                                onClick = { sendOtp() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                enabled = !isSendingOtp
                            ) {
                                if (isSendingOtp) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sending...", fontWeight = FontWeight.Medium)
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Send OTP to Driver", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    
                    Divider(color = TextDisabled.copy(alpha = 0.2f))
                    
                    // ==================== NAME FIELD ====================
                    FormTextField(
                        value = fullName,
                        onValueChange = { 
                            fullName = it
                            errorMessage = null
                        },
                        label = "Full Name",
                        placeholder = "Driver's full name",
                        leadingIcon = Icons.Default.Person,
                        isRequired = true,
                        enabled = !isOtpSent
                    )
                    
                    // ==================== LICENSE FIELD ====================
                    FormTextField(
                        value = licenseNumber,
                        onValueChange = { 
                            licenseNumber = it.uppercase()
                            errorMessage = null
                        },
                        label = "License Number",
                        placeholder = "e.g., DL1234567890",
                        leadingIcon = Icons.Default.Badge,
                        isRequired = true,
                        enabled = !isOtpSent
                    )
                    
                    // ==================== ERROR MESSAGE ====================
                    AnimatedVisibility(visible = errorMessage != null) {
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
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Error
                                )
                            }
                        }
                    }
                }
            }
            
            // ==================== INFO CARD ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Info.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Info,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "How it works:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Fill in driver details\n2. OTP will be sent to driver's phone\n3. Ask driver for the 6-digit code\n4. Enter OTP to verify and add driver",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// =============================================================================
// FORM TEXT FIELD COMPONENT
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isRequired: Boolean = false,
    enabled: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier) {
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
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(text = placeholder, color = TextDisabled)
            },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (enabled) TextSecondary else TextDisabled
                )
            },
            trailingIcon = trailingContent,
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = TextDisabled.copy(alpha = 0.3f),
                disabledBorderColor = TextDisabled.copy(alpha = 0.2f),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Surface
            )
        )
    }
}

// =============================================================================
// OTP DIGIT BOX COMPONENT
// =============================================================================
@Composable
private fun OtpDigitBox(
    digit: String,
    isFilled: Boolean,
    isActive: Boolean,
    hasError: Boolean
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            hasError -> Error
            isActive -> Primary
            isFilled -> Success
            else -> TextDisabled.copy(alpha = 0.3f)
        },
        animationSpec = tween(150),
        label = "borderColor"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            hasError -> Error.copy(alpha = 0.05f)
            isFilled -> Success.copy(alpha = 0.05f)
            isActive -> Primary.copy(alpha = 0.05f)
            else -> Color.White
        },
        animationSpec = tween(150),
        label = "backgroundColor"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isActive) 2.dp else 0.dp,
        animationSpec = tween(150),
        label = "elevation"
    )
    
    Card(
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive || isFilled || hasError) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    hasError -> Error
                    isFilled -> TextPrimary
                    else -> TextSecondary
                }
            )
        }
    }
}
