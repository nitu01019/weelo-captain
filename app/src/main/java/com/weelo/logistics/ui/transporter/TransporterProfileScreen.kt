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
import com.weelo.logistics.data.api.TransporterProfileRequest
import com.weelo.logistics.data.api.UserProfile
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.ui.components.rememberScreenConfig
import com.weelo.logistics.ui.components.responsiveHorizontalPadding
import kotlinx.coroutines.launch

/**
 * Transporter Profile Screen - View and Edit Profile
 * 
 * Connected to backend:
 * - GET /api/v1/profile - Fetch current profile
 * - PUT /api/v1/profile/transporter - Update profile
 * 
 * All data is saved to the database on your Mac
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransporterProfileScreen(
    onNavigateBack: () -> Unit,
    onProfileUpdated: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // Profile state
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    
    // Form fields
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var businessAddress by remember { mutableStateOf("") }
    var panNumber by remember { mutableStateOf("") }
    var gstNumber by remember { mutableStateOf("") }
    
    // Load profile on screen open
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                val response = RetrofitClient.profileApi.getProfile()
                if (response.isSuccessful && response.body()?.success == true) {
                    profile = response.body()?.data?.user
                    profile?.let { p ->
                        name = p.name ?: ""
                        email = p.email ?: ""
                        businessName = p.getBusinessDisplayName() ?: ""
                        businessAddress = p.businessAddress ?: p.address ?: ""
                        panNumber = p.panNumber ?: ""
                        gstNumber = p.gstNumber ?: ""
                    }
                } else {
                    errorMessage = response.body()?.error?.message ?: "Failed to load profile"
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    // Save profile function
    fun saveProfile() {
        if (name.isBlank()) {
            errorMessage = "Name is required"
            return
        }
        
        scope.launch {
            isSaving = true
            errorMessage = null
            successMessage = null
            
            try {
                val request = TransporterProfileRequest(
                    name = name.trim(),
                    email = email.trim().ifEmpty { null },
                    company = businessName.trim(),
                    gstNumber = gstNumber.trim().ifEmpty { null },
                    panNumber = panNumber.trim().ifEmpty { null },
                    address = businessAddress.trim().ifEmpty { null }
                )
                
                val response = RetrofitClient.profileApi.updateTransporterProfile(request)
                
                if (response.isSuccessful && response.body()?.success == true) {
                    successMessage = "Profile saved successfully!"
                    profile = response.body()?.data?.user
                    onProfileUpdated()
                } else {
                    errorMessage = response.body()?.error?.message ?: "Failed to save profile"
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.message}"
            } finally {
                isSaving = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Surface)
        ) {
            if (isLoading) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    // Profile Avatar
                    ProfileAvatarSection(
                        name = name,
                        phone = profile?.phone ?: "",
                        isVerified = profile?.isVerified ?: false
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Error/Success Messages
                    errorMessage?.let { error ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, null, tint = Error)
                                Spacer(Modifier.width(12.dp))
                                Text(error, color = Error)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    successMessage?.let { success ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = Success)
                                Spacer(Modifier.width(12.dp))
                                Text(success, color = Success)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // Personal Information Section
                    SectionHeader(title = "Personal Information", icon = Icons.Default.Person)
                    
                    ProfileTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Full Name *",
                        placeholder = "Enter your full name",
                        leadingIcon = Icons.Default.Person
                    )
                    
                    ProfileTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Address",
                        placeholder = "Enter your email",
                        leadingIcon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Business Information Section
                    SectionHeader(title = "Business Information", icon = Icons.Default.Business)
                    
                    ProfileTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = "Business/Company Name",
                        placeholder = "Enter business name",
                        leadingIcon = Icons.Default.Business
                    )
                    
                    ProfileTextField(
                        value = businessAddress,
                        onValueChange = { businessAddress = it },
                        label = "Business Address",
                        placeholder = "Enter business address",
                        leadingIcon = Icons.Default.LocationOn,
                        singleLine = false,
                        maxLines = 3
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Tax Information Section
                    SectionHeader(title = "Tax & Legal", icon = Icons.Default.Description)
                    
                    ProfileTextField(
                        value = panNumber,
                        onValueChange = { panNumber = it.uppercase() },
                        label = "PAN Number",
                        placeholder = "ABCDE1234F",
                        leadingIcon = Icons.Default.CreditCard
                    )
                    
                    ProfileTextField(
                        value = gstNumber,
                        onValueChange = { gstNumber = it.uppercase() },
                        label = "GST Number",
                        placeholder = "22AAAAA0000A1Z5",
                        leadingIcon = Icons.Default.Receipt
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Save Button
                    Button(
                        onClick = { saveProfile() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = if (isSaving) "Saving..." else "Save Profile",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Account Info (Read-only)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Account Information",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            InfoRow(label = "Phone", value = "+91 ${profile?.phone ?: ""}")
                            InfoRow(label = "User ID", value = profile?.id?.take(8) ?: "...")
                            InfoRow(label = "Role", value = "Transporter")
                            InfoRow(label = "Account Status", value = if (profile?.isVerified == true) "Verified ✓" else "Pending")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatarSection(
    name: String,
    phone: String,
    isVerified: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                val initial = name.firstOrNull()?.uppercase() ?: phone.lastOrNull()?.toString() ?: "T"
                Text(
                    text = initial,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Name
            Text(
                text = name.ifEmpty { "Set your name" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            // Phone
            Text(
                text = "+91 $phone",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            
            // Verification Badge
            if (isVerified) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Success.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = Success,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Verified Account",
                            style = MaterialTheme.typography.labelMedium,
                            color = Success,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextDisabled) },
        leadingIcon = {
            Icon(leadingIcon, contentDescription = null, tint = TextSecondary)
        },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = TextDisabled.copy(alpha = 0.3f),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}
