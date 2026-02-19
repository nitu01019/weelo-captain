package com.weelo.logistics.ui.components

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

/**
 * =============================================================================
 * FULL-SCREEN CAMERA CAPTURE COMPONENT
 * =============================================================================
 * 
 * Scalable, modular camera component for driver profile photo capture.
 * 
 * Features:
 * - Full-screen camera preview with CameraX
 * - Front/Back camera switching
 * - Flash control
 * - Capture button with animation
 * - Permission handling
 * - High-quality photo output
 * 
 * Optimized for:
 * - Millions of concurrent users (efficient memory management)
 * - Modular design (reusable across features)
 * - Standard coding practices (well-documented, maintainable)
 * - Easy backend integration (clean API)
 * 
 * =============================================================================
 */

/**
 * Main camera capture screen
 * 
 * @param title Screen title (e.g., "Driver Photo", "License Front")
 * @param onImageCaptured Callback with captured image URI
 * @param onClose Callback when user closes camera
 */
@Composable
fun CameraCaptureScreen(
    title: String,
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    // Check permission on launch
    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                title = title,
                onImageCaptured = onImageCaptured,
                onClose = onClose
            )
        } else {
            // Permission denied UI
            PermissionDeniedContent(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onClose = onClose
            )
        }
    }
}

/**
 * Camera preview with controls
 */
@Composable
private fun CameraPreview(
    title: String,
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isCapturing by remember { mutableStateOf(false) }
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }
    
    // Setup camera
    LaunchedEffect(lensFacing, flashMode) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        
        try {
            // Unbind all use cases
            cameraProvider.unbindAll()
            
            // Preview use case
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            // Image capture use case
            val imageCaptureUseCase = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode)
                .build()
            
            imageCapture.value = imageCaptureUseCase
            
            // Camera selector
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            
            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCaptureUseCase
            )
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Camera initialization error")
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        
        // Overlay guide (for driver photo - face outline)
        if (title.contains("Driver", ignoreCase = true) || title.contains("Photo", ignoreCase = true)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(280.dp, 360.dp)
                        .border(3.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                )
            }
        }
        
        // Top bar with title and close button
        TopAppBar(
            title = title,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // Bottom controls
        BottomControls(
            lensFacing = lensFacing,
            flashMode = flashMode,
            isCapturing = isCapturing,
            onFlipCamera = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) 
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK 
            },
            onToggleFlash = { 
                flashMode = when (flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            },
            onCapture = {
                isCapturing = true
                capturePhoto(
                    context = context,
                    imageCapture = imageCapture.value,
                    onSuccess = { uri ->
                        isCapturing = false
                        onImageCaptured(uri)
                    },
                    onError = {
                        isCapturing = false
                    }
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Top app bar with title and close button
 */
@Composable
private fun TopAppBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
        
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}

/**
 * Bottom controls (capture button, flip camera, flash)
 */
@Composable
private fun BottomControls(
    @Suppress("UNUSED_PARAMETER") lensFacing: Int,
    flashMode: Int,
    isCapturing: Boolean,
    onFlipCamera: () -> Unit,
    onToggleFlash: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(vertical = 32.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash button
            IconButton(
                onClick = onToggleFlash,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Capture button
            Button(
                onClick = onCapture,
                enabled = !isCapturing,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    disabledContainerColor = Color.Gray
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.Black
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Capture",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            
            // Flip camera button
            IconButton(
                onClick = onFlipCamera,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Flip Camera",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Permission denied UI
 */
@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(80.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Camera Permission Required",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "We need camera access to capture your profile photo. This is required to complete your driver registration.",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text("Grant Permission")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onClose) {
            Text("Cancel", color = Color.White)
        }
    }
}

/**
 * Capture photo and save to file
 */
private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    onSuccess: (Uri) -> Unit,
    onError: () -> Unit
) {
    val captureInstance = imageCapture ?: run {
        onError()
        return
    }
    
    // Create output file
    val photoFile = File(
        context.cacheDir,
        "profile_photo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    )
    
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    captureInstance.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSuccess(Uri.fromFile(photoFile))
            }
            
            override fun onError(exception: ImageCaptureException) {
                timber.log.Timber.e(exception, "Image capture error")
                onError()
            }
        }
    )
}
