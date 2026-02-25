package com.weelo.logistics.ui.driver

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.weelo.logistics.R
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.repository.DriverProfileRepository
import com.weelo.logistics.ui.components.CameraCaptureScreen
import com.weelo.logistics.ui.components.OptimizedUriImage
import com.weelo.logistics.ui.components.SimpleTopBar
import kotlinx.coroutines.launch

/**
 * Driver Profile Completion Screen
 * 
 * Collects essential driver information after language selection:
 * - License number and photos (front/back)
 * - Driver selfie photo
 * - Vehicle type preference
 * - Optional address
 * 
 * Optimized for performance with lazy loading and minimal recompositions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverProfileCompletionScreen(
    onProfileComplete: (DriverProfileData) -> Unit,
    modifier: Modifier = Modifier
) {
    var licenseNumber by remember { mutableStateOf("") }
    var licenseFrontUri by remember { mutableStateOf<Uri?>(null) }
    var licenseBackUri by remember { mutableStateOf<Uri?>(null) }
    var driverPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVehicleType by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Camera state
    var showCamera by remember { mutableStateOf(false) }
    var cameraPhotoType by remember { mutableStateOf("") } // "driver", "licenseFront", "licenseBack"
    
    // Gallery picker for license photos only
    var showLicenseOptions by remember { mutableStateOf(false) }
    var licensePhotoType by remember { mutableStateOf("") } // "licenseFront" or "licenseBack"
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            when (licensePhotoType) {
                "licenseFront" -> licenseFrontUri = it
                "licenseBack" -> licenseBackUri = it
            }
        }
    }
    
    // API integration
    val context = LocalContext.current
    // Read actual selected language from DriverPreferences (not hardcoded)
    val driverPrefs = remember { com.weelo.logistics.data.preferences.DriverPreferences.getInstance(context) }
    val savedLanguage by driverPrefs.selectedLanguage.collectAsState(initial = "en")
    val coroutineScope = rememberCoroutineScope()
    val repository = remember {
        DriverProfileRepository(
            profileApiService = RetrofitClient.profileApi,
            context = context
        )
    }
    
    val vehicleTypes = listOf(
        "Tata Ace", "Mahindra Pickup", "Eicher Truck",
        "Ashok Leyland", "Tata 407", "Other"
    )
    
    // Show license photo options (Camera or Gallery)
    if (showLicenseOptions) {
        LicensePhotoOptionsDialog(
            title = when (licensePhotoType) {
                "licenseFront" -> "License Front Photo"
                "licenseBack" -> "License Back Photo"
                else -> "License Photo"
            },
            onCameraClick = {
                showLicenseOptions = false
                cameraPhotoType = licensePhotoType
                showCamera = true
            },
            onGalleryClick = {
                showLicenseOptions = false
                galleryLauncher.launch("image/*")
            },
            onDismiss = {
                showLicenseOptions = false
            }
        )
    }
    
    // Show camera if requested
    if (showCamera) {
        CameraCaptureScreen(
            title = when (cameraPhotoType) {
                "driver" -> "Driver Photo"
                "licenseFront" -> "License Front"
                "licenseBack" -> "License Back"
                else -> "Take Photo"
            },
            onImageCaptured = { uri ->
                when (cameraPhotoType) {
                    "driver" -> driverPhotoUri = uri
                    "licenseFront" -> licenseFrontUri = uri
                    "licenseBack" -> licenseBackUri = uri
                }
                showCamera = false
            },
            onClose = {
                showCamera = false
            }
        )
        return
    }
    
    Scaffold(
        topBar = {
            SimpleTopBar(
                title = stringResource(R.string.complete_your_profile)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress Indicator
            item {
                LinearProgressIndicator(
                    progress = 0.5f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF4CAF50),
                )
            }
            
            // Driver Photo Section
            item {
                ProfilePhotoSection(
                    photoUri = driverPhotoUri,
                    onPhotoClick = { 
                        cameraPhotoType = "driver"
                        showCamera = true
                    }
                )
            }
            
            // License Number Input
            item {
                ProfileSectionCard(title = "Driving License") {
                    OutlinedTextField(
                        value = licenseNumber,
                        onValueChange = { licenseNumber = it.uppercase() },
                        label = { Text("License Number") },
                        placeholder = { Text("e.g., DL1234567890") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1A3A6B),
                            focusedLabelColor = Color(0xFF1A3A6B)
                        )
                    )
                }
            }
            
            // License Photos Section
            item {
                ProfileSectionCard(title = "License Photos") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Front side
                        PhotoUploadCard(
                            title = "Front Side",
                            photoUri = licenseFrontUri,
                            onPhotoClick = { 
                                licensePhotoType = "licenseFront"
                                showLicenseOptions = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Back side
                        PhotoUploadCard(
                            title = "Back Side",
                            photoUri = licenseBackUri,
                            onPhotoClick = { 
                                licensePhotoType = "licenseBack"
                                showLicenseOptions = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Vehicle Type Selection
            item {
                ProfileSectionCard(title = "Preferred Vehicle Type") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        vehicleTypes.forEach { vehicleType ->
                            VehicleTypeChip(
                                vehicleType = vehicleType,
                                isSelected = selectedVehicleType == vehicleType,
                                onClick = { selectedVehicleType = vehicleType }
                            )
                        }
                    }
                }
            }
            
            // Address (Optional)
            item {
                ProfileSectionCard(title = "Address (Optional)") {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Your Address") },
                        placeholder = { Text("Street, City, State") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )
                }
            }
            
            // Error Message
            if (errorMessage.isNotEmpty()) {
                item {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }
            }
            
            // Submit Button
            item {
                Button(
                    onClick = {
                        if (validateProfile(licenseNumber, licenseFrontUri, licenseBackUri, driverPhotoUri, selectedVehicleType)) {
                            isLoading = true
                            errorMessage = ""
                            
                            // Upload to backend
                            coroutineScope.launch {
                                try {
                                    timber.log.Timber.d("Starting profile upload...")
                                    timber.log.Timber.d("License: $licenseNumber, Vehicle: $selectedVehicleType")
                                    timber.log.Timber.d("Photos - Driver: ${driverPhotoUri != null}, Front: ${licenseFrontUri != null}, Back: ${licenseBackUri != null}")
                                    
                                    val response = repository.completeDriverProfile(
                                        licenseNumber = licenseNumber,
                                        vehicleType = selectedVehicleType,
                                        address = address.ifBlank { null },
                                        language = savedLanguage.ifEmpty { "en" },
                                        driverPhotoUri = driverPhotoUri!!,
                                        licenseFrontUri = licenseFrontUri!!,
                                        licenseBackUri = licenseBackUri!!
                                    )
                                    
                                    isLoading = false
                                    
                                    timber.log.Timber.d("Response received - Success: ${response.isSuccessful}, Code: ${response.code()}")
                                    
                                    if (response.isSuccessful && response.body()?.success == true) {
                                        timber.log.Timber.d("Profile completed successfully!")
                                        // Success - pass data to callback
                                        val profileData = DriverProfileData(
                                            licenseNumber = licenseNumber,
                                            licenseFrontUri = licenseFrontUri,
                                            licenseBackUri = licenseBackUri,
                                            driverPhotoUri = driverPhotoUri,
                                            vehicleType = selectedVehicleType,
                                            address = address
                                        )
                                        onProfileComplete(profileData)
                                    } else {
                                        val errorBody = response.errorBody()?.string()
                                        timber.log.Timber.e("API Error: ${response.code()}")
                                        timber.log.Timber.e("Error body: $errorBody")
                                        timber.log.Timber.e("Response body: ${response.body()}")
                                        errorMessage = response.body()?.message 
                                            ?: errorBody
                                            ?: "Failed to complete profile. Error code: ${response.code()}"
                                    }
                                } catch (e: Exception) {
                                    isLoading = false
                                    timber.log.Timber.e(e, "Exception: ${e.javaClass.simpleName} - ${e.message}")
                                    errorMessage = "Error: ${e.message ?: "Unknown error"}"
                                }
                            }
                        } else {
                            errorMessage = "Please complete all required fields"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A3A6B)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Complete Profile",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Profile Photo Section (Large circular photo for driver)
 */
@Composable
private fun ProfilePhotoSection(
    photoUri: Uri?,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(4.dp, Color(0xFF1A3A6B), CircleShape)
                .clickable(onClick = onPhotoClick),
            contentAlignment = Alignment.Center
        ) {
            if (photoUri != null) {
                OptimizedUriImage(
                    uri = photoUri,
                    contentDescription = "Driver Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    crossfade = false,
                    targetSizeDp = 120.dp
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Add Photo",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Text(
                        text = "Tap to add",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Upload Your Photo",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1A3A6B)
        )
    }
}

/**
 * Section Card Wrapper (Reusable)
 */
@Composable
private fun ProfileSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A3A6B),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

/**
 * Photo Upload Card (for license front/back)
 */
@Composable
private fun PhotoUploadCard(
    title: String,
    photoUri: Uri?,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(140.dp)
            .clickable(onClick = onPhotoClick),
        colors = CardDefaults.cardColors(
            containerColor = if (photoUri != null) Color.White else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (photoUri != null) {
                OptimizedUriImage(
                    uri = photoUri,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    crossfade = false,
                    targetSizeDp = 140.dp
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Upload",
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * Vehicle Type Selection Chip
 */
@Composable
private fun VehicleTypeChip(
    vehicleType: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1A3A6B) else Color(0xFFF5F5F5)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = vehicleType,
                fontSize = 14.sp,
                color = if (isSelected) Color.White else Color.Black,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Dialog to choose Camera or Gallery for license photos
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensePhotoOptionsDialog(
    title: String,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose how to upload your license photo:",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                // Camera Option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCameraClick() },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = Color(0xFF1A3A6B),
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Take Photo",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Use camera to capture",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                // Gallery Option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGalleryClick() },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Gallery",
                            tint = Color(0xFF1A3A6B),
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Choose from Gallery",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select existing photo",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF1A3A6B))
            }
        }
    )
}

/**
 * Validation Helper
 */
private fun validateProfile(
    licenseNumber: String,
    licenseFront: Uri?,
    licenseBack: Uri?,
    driverPhoto: Uri?,
    vehicleType: String
): Boolean {
    return licenseNumber.isNotBlank() &&
            licenseFront != null &&
            licenseBack != null &&
            driverPhoto != null &&
            vehicleType.isNotBlank()
}

/**
 * Driver Profile Data Class
 * Immutable for thread safety and better performance
 */
data class DriverProfileData(
    val licenseNumber: String,
    val licenseFrontUri: Uri?,
    val licenseBackUri: Uri?,
    val driverPhotoUri: Uri?,
    val vehicleType: String,
    val address: String
)
