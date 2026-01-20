package com.weelo.logistics.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.InputValidator
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.api.VerifyOTPRequest
import com.weelo.logistics.data.api.SendOTPRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modern OTP Verification Screen - Redesigned for speed and clarity
 * 
 * Key Features:
 * - NO delays - instant verification
 * - Auto-submit when 6 digits entered
 * - Individual digit boxes (modern UI)
 * - SMS auto-read enabled (requires backend SMS format)
 * - Clear countdown timer
 * - Instant navigation on success
 * 
 * SMS Auto-Read Setup (Backend TODO):
 * - SMS must contain app hash code
 * - Format: "<#> Your OTP is: 123456 ABC123xyz..."
 * - Uses Google Play Services SMS Retriever API
 * - No user permission needed!
 * 
 * @param phoneNumber The phone number OTP was sent to
 * @param role "TRANSPORTER" or "DRIVER"
 * @param onVerifySuccess Navigate on successful verification (instant)
 * @param onNavigateBack Back navigation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OTPVerificationScreen(
    phoneNumber: String,
    role: String,
    onVerifySuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var otpValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var resendTimer by remember { mutableStateOf(30) }
    var canResend by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Countdown timer
    LaunchedEffect(Unit) {
        while (resendTimer > 0) {
            delay(1000)
            resendTimer--
        }
        canResend = true
    }
    
    // Auto-verify when 6 digits entered - INSTANT ⚡
    LaunchedEffect(otpValue) {
        if (otpValue.length == 6) {
            keyboardController?.hide()
            delay(50) // Minimal delay for keyboard to hide
            
            val validation = InputValidator.validateOTP(otpValue)
            if (!validation.isValid) {
                errorMessage = validation.errorMessage!!
                otpValue = ""
                return@LaunchedEffect
            }
            
            isLoading = true
            errorMessage = ""
            
            // Make actual API call to verify OTP
            try {
                val roleForApi = role.lowercase()
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.authApi.verifyOTP(
                        VerifyOTPRequest(
                            phone = phoneNumber,
                            otp = otpValue,
                            role = roleForApi
                        )
                    )
                }
                
                isLoading = false
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    
                    // Save tokens securely
                    data?.tokens?.let { tokens ->
                        RetrofitClient.saveTokens(tokens.accessToken, tokens.refreshToken)
                    }
                    
                    // Save user info
                    data?.user?.let { user ->
                        RetrofitClient.saveUserInfo(user.id, user.role)
                    }
                    
                    successMessage = "Verified successfully!"
                    delay(150) // Quick success feedback ⚡
                    onVerifySuccess()
                } else {
                    errorMessage = response.body()?.error?.message ?: "Invalid OTP. Please try again."
                    otpValue = ""
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Network error. Please try again."
                otpValue = ""
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFAFAFA),
                        Color.White
                    )
                )
            )
    ) {
        // Minimal background decoration
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.05f)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 100.dp, y = (-50).dp)
                    .size(200.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Success, Color.Transparent)
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Header Section
            OTPHeader(
                phoneNumber = phoneNumber,
                role = role,
                onNavigateBack = onNavigateBack
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // OTP Input Section
            OTPInputCard(
                otpValue = otpValue,
                onOtpChange = { newValue ->
                    if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                        otpValue = newValue
                        errorMessage = ""
                    }
                },
                errorMessage = errorMessage,
                isLoading = isLoading
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Success Message
            AnimatedVisibility(
                visible = successMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SuccessCard(message = successMessage ?: "")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Resend OTP Section
            ResendSection(
                canResend = canResend,
                resendTimer = resendTimer,
                onResend = {
                    canResend = false
                    resendTimer = 30
                    otpValue = ""
                    errorMessage = ""
                    
                    scope.launch {
                        // Call resend OTP API
                        try {
                            val roleForApi = role.lowercase()
                            withContext(Dispatchers.IO) {
                                RetrofitClient.authApi.sendOTP(
                                    SendOTPRequest(
                                        phone = phoneNumber,
                                        role = roleForApi
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            errorMessage = "Failed to resend OTP"
                        }
                        
                        // Restart timer
                        while (resendTimer > 0) {
                            delay(1000)
                            resendTimer--
                        }
                        canResend = true
                    }
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Footer Section
            OTPFooter()
        }
    }
}

// =============================================================================
// HEADER COMPONENT
// =============================================================================

@Composable
private fun OTPHeader(
    phoneNumber: String,
    role: String,
    onNavigateBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Logo/Icon
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(20.dp),
            color = Success.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Message,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = "Verify OTP",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtitle
        Text(
            text = if (role == "DRIVER") {
                "Enter OTP sent to your transporter"
            } else {
                "Enter OTP sent to +91 $phoneNumber"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Change number link
        TextButton(onClick = onNavigateBack) {
            Text(
                text = "Change Number",
                style = MaterialTheme.typography.bodySmall,
                color = Primary
            )
        }
    }
}

// =============================================================================
// OTP INPUT COMPONENT - Individual Boxes
// =============================================================================

@Composable
private fun OTPInputCard(
    otpValue: String,
    onOtpChange: (String) -> Unit,
    errorMessage: String,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter 6-Digit OTP",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // OTP Input Boxes - Clickable to show keyboard
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(6) { index ->
                        OTPDigitBox(
                            digit = otpValue.getOrNull(index)?.toString() ?: "",
                            isFilled = index < otpValue.length,
                            isActive = index == otpValue.length,
                            hasError = errorMessage.isNotEmpty()
                        )
                    }
                }
                
                // Invisible overlay that captures clicks and shows keyboard
                BasicTextField(
                    value = otpValue,
                    onValueChange = onOtpChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp), // Match digit box height
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        autoCorrect = false
                    ),
                    enabled = !isLoading,
                    decorationBox = { innerTextField ->
                        // Transparent box that captures touch
                        Box(modifier = Modifier.fillMaxSize()) {
                            innerTextField()
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Loading Indicator
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Verifying...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Primary
                    )
                }
            }
            
            // Error Message
            AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// =============================================================================
// SINGLE OTP DIGIT BOX
// =============================================================================

@Composable
private fun OTPDigitBox(
    digit: String,
    isFilled: Boolean,
    isActive: Boolean,
    hasError: Boolean
) {
    val borderColor = when {
        hasError -> Error
        isActive -> Primary
        isFilled -> Success
        else -> TextDisabled.copy(alpha = 0.3f)
    }
    
    val backgroundColor = when {
        isFilled -> Primary.copy(alpha = 0.05f)
        else -> Color.Transparent
    }
    
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isActive) 2.dp else 1.dp,
        animationSpec = tween(300),
        label = "border"
    )
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(animatedBorderWidth, borderColor, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (isFilled) TextPrimary else TextDisabled
        )
    }
}

// =============================================================================
// RESEND SECTION COMPONENT
// =============================================================================

@Composable
private fun ResendSection(
    canResend: Boolean,
    resendTimer: Int,
    onResend: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!canResend) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Resend OTP in ${resendTimer}s",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        } else {
            Text(
                text = "Didn't receive OTP?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            TextButton(onClick = onResend) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Resend",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =============================================================================
// SUCCESS CARD COMPONENT
// =============================================================================

@Composable
private fun SuccessCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SuccessLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Success,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Success,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// =============================================================================
// FOOTER COMPONENT
// =============================================================================

@Composable
private fun OTPFooter() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Your data is secure and encrypted",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
