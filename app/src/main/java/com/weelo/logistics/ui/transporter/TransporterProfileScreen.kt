package com.weelo.logistics.ui.transporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.PrimaryTopBar
import com.weelo.logistics.ui.components.SkeletonTransporterProfileLoading
import com.weelo.logistics.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest

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
    val profileViewModel: TransporterProfileViewModel = viewModel()
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        profileViewModel.bootstrapIfNeeded()
    }

    LaunchedEffect(profileViewModel) {
        profileViewModel.uiEvents.collectLatest { event ->
            when (event) {
                TransporterProfileUiEvent.ProfileSaved -> onProfileUpdated()
            }
        }
    }
    
    Scaffold(
        topBar = {
            PrimaryTopBar(
                title = stringResource(R.string.my_profile),
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Surface)
        ) {
            when (val state = uiState) {
                TransporterProfileUiState.Loading -> {
                    SkeletonTransporterProfileLoading(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }

                is TransporterProfileUiState.Content -> {
                    val form = state.form
                    val profile = form.profile
                    val completionChecks = remember(
                        form.name,
                        form.email,
                        form.businessName,
                        form.businessAddress
                    ) {
                        listOf(
                            form.name.trim().isNotEmpty(),
                            form.businessName.trim().isNotEmpty(),
                            form.businessAddress.trim().isNotEmpty(),
                            form.email.trim().isNotEmpty()
                        )
                    }
                    val completionPercent = (completionChecks.count { it } * 100) / completionChecks.size

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            item {
                                // Profile Avatar
                                ProfileAvatarSection(
                                    name = form.name,
                                    phone = profile?.phone ?: "",
                                    isVerified = profile?.isVerified ?: false
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            if (completionPercent < 100) {
                                item {
                                    ProfileReadinessCard(
                                        completionPercent = completionPercent,
                                        missingItems = buildList {
                                            if (form.name.trim().isEmpty()) add("Add your full name")
                                            if (form.businessName.trim().isEmpty()) add("Add business/company name")
                                            if (form.businessAddress.trim().isEmpty()) add("Add business address")
                                            if (form.email.trim().isEmpty()) add("Add email (recommended)")
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            item {
                                state.errorMessage?.let { error ->
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

                                state.successMessage?.let { success ->
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
                            }

                            item {
                                SectionHeader(title = "Personal Information", icon = Icons.Default.Person)
                            }
                            item {
                                ProfileTextField(
                                    value = form.name,
                                    onValueChange = {
                                        profileViewModel.clearMessages()
                                        profileViewModel.onNameChange(it)
                                    },
                                    label = "Full Name *",
                                    placeholder = "Enter your full name",
                                    leadingIcon = Icons.Default.Person
                                )
                            }
                            item {
                                ProfileTextField(
                                    value = form.email,
                                    onValueChange = {
                                        profileViewModel.clearMessages()
                                        profileViewModel.onEmailChange(it)
                                    },
                                    label = "Email Address",
                                    placeholder = "Enter your email",
                                    leadingIcon = Icons.Default.Email,
                                    keyboardType = KeyboardType.Email
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            item {
                                SectionHeader(title = "Business Information", icon = Icons.Default.Business)
                            }
                            item {
                                ProfileTextField(
                                    value = form.businessName,
                                    onValueChange = {
                                        profileViewModel.clearMessages()
                                        profileViewModel.onBusinessNameChange(it)
                                    },
                                    label = "Business/Company Name",
                                    placeholder = "Enter business name",
                                    leadingIcon = Icons.Default.Business
                                )
                            }
                            item {
                                ProfileTextField(
                                    value = form.businessAddress,
                                    onValueChange = {
                                        profileViewModel.clearMessages()
                                        profileViewModel.onBusinessAddressChange(it)
                                    },
                                    label = "Business Address",
                                    placeholder = "Enter business address",
                                    leadingIcon = Icons.Default.LocationOn,
                                    singleLine = false,
                                    maxLines = 3
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            item {
                                SectionHeader(title = "Tax & Legal", icon = Icons.Default.Description)
                            }
                            item {
                                ProfileTextField(
                                    value = form.panNumber,
                                    onValueChange = {
                                        profileViewModel.clearMessages()
                                        profileViewModel.onPanNumberChange(it)
                                    },
                                    label = "PAN Number",
                                    placeholder = "ABCDE1234F",
                                    leadingIcon = Icons.Default.CreditCard
                                )
                            }
                            item {
                                ProfileTextField(
                                    value = form.gstNumber,
                                    onValueChange = {
                                        profileViewModel.clearMessages()
                                        profileViewModel.onGstNumberChange(it)
                                    },
                                    label = "GST Number",
                                    placeholder = "22AAAAA0000A1Z5",
                                    leadingIcon = Icons.Default.Receipt
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                            }

                            item {
                                Button(
                                    onClick = { profileViewModel.saveProfile() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    enabled = !state.isSaving
                                ) {
                                    if (state.isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Text(
                                        text = if (state.isSaving) "Saving..." else "Save Profile",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            item {
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

                                        InfoRow(
                                            label = "Phone",
                                            value = profile?.phone?.takeIf { it.isNotBlank() }?.let { "+91 $it" }
                                                ?: "Not available"
                                        )
                                        InfoRow(
                                            label = "User ID",
                                            value = profile?.id?.takeIf { it.isNotBlank() }?.take(8) ?: "Pending"
                                        )
                                        InfoRow(label = "Role", value = "Transporter")
                                        InfoRow(
                                            label = "Account Status",
                                            value = if (profile?.isVerified == true) "Verified" else "Pending verification"
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }

                        if (state.isRefreshing) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                                color = Primary,
                                trackColor = SurfaceVariant
                            )
                        }
                    }
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
private fun ProfileReadinessCard(
    completionPercent: Int,
    missingItems: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.TaskAlt,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Profile readiness",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "$completionPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = completionPercent / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = Primary,
                trackColor = Primary.copy(alpha = 0.12f)
            )
            if (missingItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Complete these to improve account readiness:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                missingItems.take(3).forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            textAlign = TextAlign.End
        )
    }
}
