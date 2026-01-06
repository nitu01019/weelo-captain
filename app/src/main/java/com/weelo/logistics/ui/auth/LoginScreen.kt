package com.weelo.logistics.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.theme.*

/**
 * Login Screen - OTP Based Authentication
 * 
 * FAKE OTP FOR TESTING: "123456"
 * 
 * Flow:
 * 1. Enter mobile number
 * 2. Click "Send OTP"
 * 3. Navigate to OTP screen
 * 4. Enter OTP: 123456
 * 5. Login successful
 * 
 * TODO: Connect to backend (already prepared in AuthViewModel)
 */
@Composable
fun LoginScreen(
    role: String, // "TRANSPORTER" or "DRIVER"
    onNavigateToSignup: () -> Unit,
    onNavigateToOTP: (String, String) -> Unit, // (mobile, role)
    onNavigateBack: () -> Unit
) {
    var mobileNumber by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val roleTitle = if (role == "TRANSPORTER") "Transporter Login" else "Driver Login"
    val roleGreeting = if (role == "TRANSPORTER") "Welcome back, Captain! ⚓" else "Ready to drive, Captain! 🚗"
    val roleColor = if (role == "TRANSPORTER") Primary else Secondary
    val signupText = if (role == "TRANSPORTER") "New here? Sign Up as Transporter" else "New here? Sign Up as Driver"
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar with back button
        com.weelo.logistics.ui.components.PrimaryTopBar(
            title = roleTitle,
            onBackClick = onNavigateBack
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Greeting - PRD Compliant
            Text(
                text = roleGreeting,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Mobile Number Input - PRD Compliant (56dp height, 12dp radius)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Mobile Number",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .border(
                            width = if (errorMessage.isNotEmpty()) 2.dp else 1.dp,
                            color = if (errorMessage.isNotEmpty()) Error else Divider,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(Background, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+91",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "|",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Divider
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    BasicTextField(
                        value = mobileNumber,
                        onValueChange = { 
                            if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                                mobileNumber = it
                                errorMessage = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (mobileNumber.isEmpty()) {
                                Text(
                                    text = "Enter mobile number",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextDisabled
                                )
                            }
                            innerTextField()
                        }
                    )
                }
                
                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Continue with OTP Button - PRD Compliant
            Button(
                onClick = {
                    when {
                        mobileNumber.isEmpty() -> errorMessage = "Please enter mobile number"
                        mobileNumber.length != 10 -> errorMessage = "Please enter valid 10-digit number"
                        else -> onNavigateToOTP("+91$mobileNumber", role)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = roleColor,
                    contentColor = White
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(4.dp)
            ) {
                Text(
                    text = "Continue with OTP",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Divider with "or"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f), color = Divider)
                Text(
                    text = "  or  ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Divider(modifier = Modifier.weight(1f), color = Divider)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Sign Up Link - PRD Compliant
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "New here?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onNavigateToSignup,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = roleColor
                    )
                ) {
                    Text(
                        text = signupText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
