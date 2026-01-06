package com.weelo.logistics.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weelo.logistics.ui.components.PrimaryTextField
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Signup Screen - PRD-01 Compliant
 * Separate forms for Transporter and Driver
 */
@Composable
fun SignupScreen(
    role: String, // "TRANSPORTER" or "DRIVER"
    mobileNumber: String, // Pre-filled from OTP verification
    onNavigateBack: () -> Unit,
    onSignupSuccess: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") } // For transporter only
    var city by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") } // For driver only
    var emergencyContact by remember { mutableStateOf("") } // For driver only
    var agreeToTerms by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val isTransporter = role == "TRANSPORTER"
    val title = if (isTransporter) "Create Transporter Account" else "Complete Your Profile"
    val roleColor = if (isTransporter) Primary else Secondary
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        PrimaryTopBar(
            title = title,
            onBackClick = onNavigateBack
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Full Name - Required for both
            PrimaryTextField(
                value = name,
                onValueChange = { 
                    name = it
                    errorMessage = ""
                },
                label = "Full Name *",
                placeholder = "Enter your full name",
                leadingIcon = Icons.Default.Person
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Company/Business Name - Transporter only
            if (isTransporter) {
                PrimaryTextField(
                    value = companyName,
                    onValueChange = { 
                        companyName = it
                        errorMessage = ""
                    },
                    label = "Company/Business Name *",
                    placeholder = "Enter company name",
                    leadingIcon = Icons.Default.Business
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Mobile Number - Pre-filled, non-editable
            PrimaryTextField(
                value = mobileNumber,
                onValueChange = { },
                label = "Mobile Number *",
                placeholder = "",
                leadingIcon = Icons.Default.Phone,
                enabled = false
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // License Number - Driver only
            if (!isTransporter) {
                PrimaryTextField(
                    value = licenseNumber,
                    onValueChange = { licenseNumber = it },
                    label = "License Number",
                    placeholder = "Enter your driving license number",
                    leadingIcon = Icons.Default.Badge
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Emergency Contact - Driver only
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Emergency Contact",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(1.dp, Divider, RoundedCornerShape(12.dp))
                            .background(Background, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "+91", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "|", color = Divider)
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = emergencyContact,
                            onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) emergencyContact = it },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (emergencyContact.isEmpty()) {
                                    Text("Enter emergency contact", style = MaterialTheme.typography.bodyLarge, color = TextDisabled)
                                }
                                innerTextField()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // City - Transporter only
            if (isTransporter) {
                PrimaryTextField(
                    value = city,
                    onValueChange = { 
                        city = it
                        errorMessage = ""
                    },
                    label = "City *",
                    placeholder = "Enter your city",
                    leadingIcon = Icons.Default.LocationCity
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Terms & Conditions Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = agreeToTerms,
                    onCheckedChange = { agreeToTerms = it },
                    colors = CheckboxDefaults.colors(checkedColor = roleColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I agree to Terms & Conditions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Error
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Create Account / Complete Profile Button
            Button(
                onClick = {
                    when {
                        name.isEmpty() -> errorMessage = "Please enter your name"
                        isTransporter && companyName.isEmpty() -> errorMessage = "Please enter company name"
                        isTransporter && city.isEmpty() -> errorMessage = "Please enter city"
                        !agreeToTerms -> errorMessage = "Please agree to Terms & Conditions"
                        else -> {
                            isLoading = true
                            scope.launch {
                                onSignupSuccess(role)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = roleColor),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (isTransporter) "Create Account" else "Complete Profile",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
