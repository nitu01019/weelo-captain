package com.weelo.logistics.ui.driver

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.CameraCaptureScreen
import com.weelo.logistics.ui.components.OptimizedNetworkImage
import com.weelo.logistics.ui.components.SkeletonProfileLoading

/**
 * =============================================================================
 * DRIVER PROFILE SCREEN WITH PHOTO DISPLAY AND UPDATE
 * =============================================================================
 * 
 * Displays driver profile with photos from S3:
 * - Profile photo (circular with edit button)
 * - License front photo (card with edit button)
 * - License back photo (card with edit button)
 * - Driver details (name, phone, license number, etc.)
 * 
 * Features:
 * - View all photos from S3
 * - Update any photo (camera or gallery)
 * - Real-time updates via WebSocket
 * - Loading states
 * - Error handling
 * 
 * Scalability:
 * - Efficient image loading with Coil
 * - Memory-efficient (no bitmap caching)
 * - Handles millions of users
 * 
 * Modularity:
 * - MVVM architecture
 * - Reusable components
 * - Clean separation
 * 
 * Coding Standards:
 * - Well-documented
 * - Consistent naming
 * - Type-safe
 * 
 * Backend Integration:
 * - GET /driver/profile - Fetch profile with photos
 * - PUT /driver/profile/photo - Update profile photo
 * - PUT /driver/profile/license - Update license photos
 * =============================================================================
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverProfileScreenWithPhotos(
    viewModel: DriverProfileViewModel,
    onNavigateBack: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsState()
    val photoUpdateState by viewModel.photoUpdateState.collectAsState()
    val showPhotoOptions by viewModel.showPhotoOptions.collectAsState()
    val photoTypeToUpdate by viewModel.photoTypeToUpdate.collectAsState()
    
    // Camera and gallery launchers
    var showCamera by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onPhotoSelected(it) }
    }
    
    // Show camera screen
    if (showCamera) {
        CameraCaptureScreen(
            title = when (photoTypeToUpdate) {
                DriverProfileViewModel.PhotoType.PROFILE -> stringResource(R.string.profile_photo_label)
                DriverProfileViewModel.PhotoType.LICENSE_FRONT -> stringResource(R.string.license_front)
                DriverProfileViewModel.PhotoType.LICENSE_BACK -> stringResource(R.string.license_back)
                else -> stringResource(R.string.take_photo)
            },
            onImageCaptured = { uri ->
                showCamera = false
                viewModel.onPhotoSelected(uri)
            },
            onClose = {
                showCamera = false
                viewModel.hidePhotoOptions()
            }
        )
        return
    }
    
    // Show photo options dialog (Camera or Gallery)
    if (showPhotoOptions) {
        PhotoOptionsDialog(
            onCameraClick = {
                showCamera = true
            },
            onGalleryClick = {
                viewModel.hidePhotoOptions()
                galleryLauncher.launch("image/*")
            },
            onDismiss = {
                viewModel.hidePhotoOptions()
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_profile)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A3A6B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = profileState) {
                is ProfileState.Loading -> {
                    SkeletonProfileLoading(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is ProfileState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = Color.Red,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadProfile() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                
                is ProfileState.Success -> {
                    ProfileContent(
                        profile = state.profile,
                        photoUpdateState = photoUpdateState,
                        onEditProfilePhoto = {
                            viewModel.showPhotoOptions(DriverProfileViewModel.PhotoType.PROFILE)
                        },
                        onEditLicenseFront = {
                            viewModel.showPhotoOptions(DriverProfileViewModel.PhotoType.LICENSE_FRONT)
                        },
                        onEditLicenseBack = {
                            viewModel.showPhotoOptions(DriverProfileViewModel.PhotoType.LICENSE_BACK)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    profile: com.weelo.logistics.data.api.DriverProfileWithPhotos,
    photoUpdateState: PhotoUpdateState,
    onEditProfilePhoto: () -> Unit,
    onEditLicenseFront: () -> Unit,
    onEditLicenseBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        
        // Upload status
        when (photoUpdateState) {
            is PhotoUpdateState.Uploading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3CD)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.uploading_photo))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            is PhotoUpdateState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFD4EDDA)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF155724))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(photoUpdateState.message, color = Color(0xFF155724))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            is PhotoUpdateState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF8D7DA)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, null, tint = Color(0xFF721C24))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(photoUpdateState.message, color = Color(0xFF721C24))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            else -> {}
        }
        
        // Profile Photo Section
        ProfilePhotoSection(
            photoUrl = profile.photos?.profilePhoto,
            onEditClick = onEditProfilePhoto
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Driver Details
        DriverDetailsSection(profile)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // License Photos Section (License details visible only to driver)
        LicensePhotosSection(
            licenseFrontUrl = profile.photos?.licenseFront,
            licenseBackUrl = profile.photos?.licenseBack,
            onEditFront = onEditLicenseFront,
            onEditBack = onEditLicenseBack
        )
    }
}

@Composable
private fun ProfilePhotoSection(
    photoUrl: String?,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                // Profile photo with optimized caching
                OptimizedNetworkImage(
                    imageUrl = photoUrl,
                    contentDescription = stringResource(R.string.profile_photo_label),
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
                
                // Edit button
                FloatingActionButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(40.dp),
                    containerColor = Color(0xFF4CAF50)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_edit),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.profile_photo_label),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = stringResource(R.string.visible_to_transporter),
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun DriverDetailsSection(profile: com.weelo.logistics.data.api.DriverProfileWithPhotos) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.personal_information),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            DetailRow(stringResource(R.string.name), profile.name)
            DetailRow(stringResource(R.string.phone), profile.phone)
            profile.email?.let { DetailRow(stringResource(R.string.email), it) }
            profile.licenseNumber?.let { DetailRow(stringResource(R.string.license_number), it) }
            profile.vehicleType?.let { DetailRow(stringResource(R.string.vehicle_type), it) }
            profile.address?.let { DetailRow(stringResource(R.string.address), it) }
        }
    }
}

@Composable
private fun LicensePhotosSection(
    licenseFrontUrl: String?,
    licenseBackUrl: String?,
    onEditFront: () -> Unit,
    onEditBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.license_photos),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = stringResource(R.string.only_visible_to_you),
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LicensePhotoCard(
                    title = stringResource(R.string.front_label),
                    photoUrl = licenseFrontUrl,
                    onEditClick = onEditFront,
                    modifier = Modifier.weight(1f)
                )
                
                LicensePhotoCard(
                    title = stringResource(R.string.back_label),
                    photoUrl = licenseBackUrl,
                    onEditClick = onEditBack,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LicensePhotoCard(
    title: String,
    photoUrl: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                // License photo with optimized caching
                OptimizedNetworkImage(
                    imageUrl = photoUrl,
                    contentDescription = stringResource(R.string.license_format, title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_edit),
                        tint = Color(0xFF1A3A6B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "$label:",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PhotoOptionsDialog(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.update_photo), fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(stringResource(R.string.choose_photo_method))
                Spacer(modifier = Modifier.height(16.dp))
                
                // Camera option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCameraClick() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.take_photo), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.use_camera), fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Gallery option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGalleryClick() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.choose_from_gallery), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.select_existing_photo), fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
