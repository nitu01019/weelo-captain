package com.weelo.logistics.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.utils.ClickDebouncer
import com.weelo.logistics.utils.GlobalRateLimiters
import com.weelo.logistics.utils.InputValidator
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.api.SendOTPRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * Modern Login Screen - Redesigned for speed and clarity
 * 
 * Key Features:
 * - NO toast delays - instant navigation
 * - Clean, minimal UI
 * - Fast animations
 * - Clear error states
 * - Backend ready
 * 
 * @param role "TRANSPORTER" or "DRIVER"
 * @param onNavigateToSignup Navigate to signup
 * @param onNavigateToOTP Navigate to OTP screen (instant, no delay)
 * @param onNavigateBack Back navigation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    role: String,
    onNavigateToSignup: () -> Unit,
    onNavigateToOTP: (String, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val clickDebouncer = remember { ClickDebouncer(500L) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Navigate after success - instant for speed ⚡
    LaunchedEffect(successMessage) {
        successMessage?.let {
            delay(100) // Minimal delay for visual feedback
            onNavigateToOTP(phoneNumber, role)
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
                            colors = listOf(Primary, Color.Transparent)
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
            LoginHeader(role = role, onNavigateBack = onNavigateBack)
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Phone Input Section
            PhoneInputCard(
                phoneNumber = phoneNumber,
                onPhoneChange = {
                    if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                        phoneNumber = it
                        errorMessage = ""
                    }
                },
                errorMessage = errorMessage,
                onSubmit = {
                    keyboardController?.hide()
                    // Trigger send OTP
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Send OTP Button
            ModernButton(
                text = if (isLoading) "Sending..." else "Send OTP",
                onClick = {
                    if (!clickDebouncer.canClick()) return@ModernButton
                    
                    val validation = InputValidator.validatePhoneNumber(phoneNumber)
                    if (!validation.isValid) {
                        errorMessage = validation.errorMessage!!
                        return@ModernButton
                    }
                    
                    keyboardController?.hide()
                    isLoading = true
                    errorMessage = ""
                    
                    scope.launch {
                        // Rate limiting check
                        if (!GlobalRateLimiters.otp.tryAcquire(phoneNumber)) {
                            isLoading = false
                            val retryAfter = GlobalRateLimiters.otp.getTimeUntilReset(phoneNumber) / 1000
                            errorMessage = "Too many attempts. Try again in ${retryAfter}s"
                            return@launch
                        }
                        
                        // Make actual API call
                        try {
                            // Backend expects 10-digit phone number without country code
                            val roleForApi = role.lowercase() // Backend expects lowercase: "driver" or "transporter"
                            
                            Log.d("LoginScreen", "Sending OTP to: $phoneNumber, role: $roleForApi")
                            
                            val response = withContext(Dispatchers.IO) {
                                RetrofitClient.authApi.sendOTP(
                                    SendOTPRequest(
                                        phone = phoneNumber,
                                        role = roleForApi
                                    )
                                )
                            }
                            
                            isLoading = false
                            
                            if (response.isSuccessful && response.body()?.success == true) {
                                // OTP sent successfully
                                Log.d("LoginScreen", "OTP sent successfully")
                                successMessage = "OTP sent to your phone"
                            } else {
                                val errorBody = response.errorBody()?.string()
                                Log.e("LoginScreen", "OTP failed: ${response.code()} - $errorBody")
                                errorMessage = response.body()?.error?.message ?: "Failed to send OTP"
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            Log.e("LoginScreen", "Send OTP error: ${e.message}", e)
                            errorMessage = "Network error. Please try again."
                        }
                    }
                },
                enabled = phoneNumber.length == 10 && !isLoading,
                isLoading = isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Success Message (brief, then navigates)
            AnimatedVisibility(
                visible = successMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SuccessCard(message = successMessage ?: "")
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Footer Section
            LoginFooter(
                role = role,
                onNavigateToSignup = onNavigateToSignup
            )
        }
    }
}

// =============================================================================
// HEADER COMPONENT
// =============================================================================

@Composable
private fun LoginHeader(
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
            color = Primary.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (role == "DRIVER") Icons.Default.DirectionsCar else Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = if (role == "DRIVER") "Driver Login" else "Transporter Login",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtitle
        Text(
            text = "Enter your phone number to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// =============================================================================
// PHONE INPUT COMPONENT
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneInputCard(
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    errorMessage: String,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Phone Number",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "10-digit number",
                        color = TextDisabled
                    )
                },
                leadingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "+91",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Divider(
                            modifier = Modifier
                                .height(24.dp)
                                .width(1.dp),
                            color = TextDisabled.copy(alpha = 0.3f)
                        )
                    }
                },
                trailingIcon = {
                    if (phoneNumber.length == 10) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Success
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSubmit() }
                ),
                singleLine = true,
                isError = errorMessage.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = TextDisabled.copy(alpha = 0.3f),
                    errorBorderColor = Error,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            
            // Error Message
            AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
            }
            
            // Character count
            if (phoneNumber.isNotEmpty()) {
                Text(
                    text = "${phoneNumber.length}/10",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDisabled,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

// =============================================================================
// MODERN BUTTON COMPONENT
// =============================================================================

@Composable
private fun ModernButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            disabledContainerColor = Primary.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp,
            disabledElevation = 0.dp
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
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
            verticalAlignment = Alignment.CenterVertically
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
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// =============================================================================
// FOOTER COMPONENT
// =============================================================================

@Composable
private fun LoginFooter(
    role: String,
    onNavigateToSignup: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Help Text
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (role == "DRIVER") {
                    "OTP will be sent to your registered transporter"
                } else {
                    "You'll receive OTP on this number"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Signup Link
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Don't have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = onNavigateToSignup) {
                Text(
                    text = "Sign Up",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        }
    }
}
