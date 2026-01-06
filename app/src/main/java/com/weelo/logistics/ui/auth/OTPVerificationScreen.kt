package com.weelo.logistics.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.data.repository.MockDataRepository
import com.weelo.logistics.ui.components.PrimaryButton
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.components.WeeloTextButton
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * OTP Verification Screen
 * 
 * FAKE OTP FOR TESTING: "123456"
 * 
 * Features:
 * - Auto-verify when 6 digits entered
 * - Accepts only: 123456
 * - Countdown timer (30 seconds)
 * - Resend OTP option
 * 
 * TODO: Connect to backend
 * - Use AuthViewModel.verifyOTP()
 * - Real OTP validation
 * - Token management
 */
@Composable
fun OTPVerificationScreen(
    mobileNumber: String,
    role: String,
    onNavigateBack: () -> Unit,
    onVerifySuccess: (String) -> Unit
) {
    var otp by remember { mutableStateOf(List(6) { "" }) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(30) }
    var canResend by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val focusRequesters = remember { List(6) { FocusRequester() } }
    
    // Show hint about fake OTP
    LaunchedEffect(Unit) {
        successMessage = "💡 Test OTP: 123456"
    }
    
    // Countdown timer
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        canResend = true
    }
    
    // Auto-verify when all digits entered
    LaunchedEffect(otp) {
        if (otp.all { it.isNotEmpty() }) {
            val otpCode = otp.joinToString("")
            isLoading = true
            errorMessage = ""
            
            // Simulate API delay
            scope.launch {
                delay(800)
                
                // FAKE OTP: Only "123456" works
                if (otpCode == "123456") {
                    successMessage = "✓ OTP Verified! Logging in..."
                    delay(500)
                    onVerifySuccess(role)
                } else {
                    errorMessage = "❌ Invalid OTP. Use: 123456"
                    otp = List(6) { "" }
                    isLoading = false
                    focusRequesters[0].requestFocus()
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PrimaryTopBar(
            title = "Enter OTP",
            onBackClick = onNavigateBack
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Phone number display
            Text(
                text = "Code sent to $mobileNumber",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Hint for testing
            if (successMessage.isNotEmpty()) {
                Text(
                    text = successMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (successMessage.startsWith("✓")) Success else Info,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Edit number link
            WeeloTextButton(
                text = "Edit number",
                onClick = onNavigateBack
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // OTP Input Boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                otp.forEachIndexed { index, digit ->
                    OTPDigitBox(
                        digit = digit,
                        isError = errorMessage.isNotEmpty(),
                        focusRequester = focusRequesters[index],
                        onDigitChange = { newDigit ->
                            if (newDigit.length <= 1 && (newDigit.isEmpty() || newDigit.all { it.isDigit() })) {
                                val newOtp = otp.toMutableList()
                                newOtp[index] = newDigit
                                otp = newOtp
                                errorMessage = ""
                                
                                // Auto-move to next box
                                if (newDigit.isNotEmpty() && index < 5) {
                                    focusRequesters[index + 1].requestFocus()
                                }
                            }
                        },
                        onBackspace = {
                            if (digit.isEmpty() && index > 0) {
                                focusRequesters[index - 1].requestFocus()
                            }
                        }
                    )
                    
                    if (index < 5) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
            
            // Error message
            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Error,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Resend OTP
            if (canResend) {
                WeeloTextButton(
                    text = "Resend OTP",
                    onClick = {
                        countdown = 30
                        canResend = false
                        // TODO: Trigger resend OTP API
                    }
                )
            } else {
                Text(
                    text = "Resend in 00:${countdown.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Verify Button
            PrimaryButton(
                text = "Verify OTP",
                onClick = {
                    val otpCode = otp.joinToString("")
                    if (otpCode.length == 6) {
                        isLoading = true
                        scope.launch {
                            if (otpCode == "123456") {
                                onVerifySuccess(role)
                            } else {
                                errorMessage = "Invalid OTP"
                                isLoading = false
                            }
                        }
                    } else {
                        errorMessage = "Please enter complete OTP"
                    }
                },
                isLoading = isLoading,
                enabled = otp.all { it.isNotEmpty() } && !isLoading
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Demo hint
            Text(
                text = "Demo: Enter 123456",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Single OTP Digit Input Box - PRD Compliant
 * Size: 48dp x 56dp, Border radius: 12dp
 */
@Composable
fun OTPDigitBox(
    digit: String,
    isError: Boolean,
    focusRequester: FocusRequester,
    onDigitChange: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val borderColor = when {
        isError -> Error
        digit.isNotEmpty() -> Primary
        else -> Divider
    }
    
    val backgroundColor = if (digit.isNotEmpty()) PrimaryLight else Background
    
    Box(
        modifier = Modifier
            .size(48.dp, 56.dp)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = digit,
            onValueChange = { newValue ->
                if (newValue.isEmpty()) {
                    onBackspace()
                }
                onDigitChange(newValue)
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxSize(),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (digit.isEmpty()) {
                        Text(
                            text = "_",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextDisabled,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
