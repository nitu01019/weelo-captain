package com.weelo.logistics.ui.auth
import com.weelo.logistics.ui.components.FuturisticPhoneInput
import com.weelo.logistics.ui.components.FuturisticButton
import com.weelo.logistics.ui.components.CardArtwork
import com.weelo.logistics.ui.components.CardArtworkPlacement
import com.weelo.logistics.ui.components.CardMediaSpec
import com.weelo.logistics.ui.components.InlineInfoBannerCard
import com.weelo.logistics.ui.components.MediaHeaderCard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.ui.components.rememberScreenConfig
import com.weelo.logistics.utils.ClickDebouncer
import com.weelo.logistics.utils.InputValidator
import com.weelo.logistics.utils.DataSanitizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Dark Futuristic Signup Screen - Multi-Step Registration
 * 
 * Flow:
 * Step 1: Phone Number → OTP Verification
 * Step 2: Full Name
 * Step 3: Auto-fetch Location (GPS)
 * Step 4: Complete Registration
 * 
 * TODO BACKEND: 
 * - Real OTP service
 * - User registration API
 * - Location geocoding service
 */
@Composable
fun SignupScreen(
    role: String, // "TRANSPORTER" or "DRIVER"
    onNavigateToLogin: () -> Unit,
    onNavigateToOTP: (String, String, Boolean) -> Unit, // (phone, role, isSignup)
    onNavigateBack: () -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    var phoneNumber by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clickDebouncer = remember { ClickDebouncer(500L) }
    
    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                isLoadingLocation = true
                location = fetchCurrentLocation(context) ?: "Unable to fetch location"
                isLoadingLocation = false
            }
        } else {
            errorMessage = "Location permission denied"
        }
    }
    
    // Simplified - removed heavy animations for better performance
    
    // Fade in animation
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(1000),
        label = "alpha"
    )
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    // Responsive layout support
    val screenConfig = rememberScreenConfig()
    val signupContentWidthModifier = Modifier
        .fillMaxWidth()
        .widthIn(max = if (screenConfig.isLandscape) 620.dp else 560.dp)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E27),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        // Static background decoration (better performance)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.2f)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 50.dp, y = (-100).dp)
                    .size(300.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Primary.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )
            
            // Adjust second orb position for landscape
            Box(
                modifier = Modifier
                    .offset(
                        x = if (screenConfig.isLandscape) 400.dp else 200.dp, 
                        y = if (screenConfig.isLandscape) 100.dp else 500.dp
                    )
                    .size(250.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Secondary.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (screenConfig.isLandscape) 48.dp else 24.dp,
                    vertical = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Reduced top spacing in landscape
            Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 16.dp else 40.dp))
            
            // Back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 24.dp else 40.dp))
            
            // Progress indicator
            LinearProgressIndicator(
                progress = when (currentStep) {
                    1 -> 0.33f
                    2 -> 0.66f
                    else -> 1f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Primary,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            
            Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 24.dp else 40.dp))
            
            Box(modifier = signupContentWidthModifier) {
                when (currentStep) {
                    1 -> {
                        // Step 1: Phone Number
                        PhoneNumberStep(
                            phoneNumber = phoneNumber,
                            onPhoneChange = {
                                if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                                    phoneNumber = it.trim()
                                    errorMessage = ""
                                }
                            },
                            errorMessage = errorMessage,
                            role = role,
                            onNext = {
                                if (!clickDebouncer.canClick()) return@PhoneNumberStep
                                
                                val validation = InputValidator.validatePhoneNumber(phoneNumber)
                                if (!validation.isValid) {
                                    errorMessage = validation.errorMessage!!
                                    return@PhoneNumberStep
                                }
                                
                                onNavigateToOTP(phoneNumber, role, true)
                            }
                        )
                    }
                    
                    2 -> {
                        FullNameStep(
                            fullName = fullName,
                            onNameChange = {
                                fullName = it.trim()
                                errorMessage = ""
                            },
                            errorMessage = errorMessage,
                            onNext = {
                                if (!clickDebouncer.canClick()) return@FullNameStep
                                
                                val validation = InputValidator.validateName(fullName)
                                if (!validation.isValid) {
                                    errorMessage = validation.errorMessage!!
                                    return@FullNameStep
                                }
                                
                                fullName = DataSanitizer.sanitizeForDisplay(fullName)
                                currentStep = 3
                            }
                        )
                    }
                    
                    3 -> {
                        LocationStep(
                            location = location,
                            isLoadingLocation = isLoadingLocation,
                            errorMessage = errorMessage,
                            onFetchLocation = {
                                when (PackageManager.PERMISSION_GRANTED) {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) -> {
                                        scope.launch {
                                            isLoadingLocation = true
                                            location = fetchCurrentLocation(context) ?: "Unable to fetch location"
                                            isLoadingLocation = false
                                        }
                                    }
                                    else -> {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                }
                            },
                            onComplete = {
                                if (location.isEmpty()) {
                                    errorMessage = "Please fetch your location"
                                } else {
                                    isLoading = true
                                    scope.launch {
                                        onNavigateToLogin()
                                    }
                                }
                            },
                            isLoading = isLoading
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Already have account
            Row(
                modifier = signupContentWidthModifier,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Already have an account? ",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                
                Text(
                    text = "Login",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun PhoneNumberStep(
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    errorMessage: String,
    role: String,
    onNext: () -> Unit
) {
    val screenConfig = rememberScreenConfig()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        MediaHeaderCard(
            title = "What's your phone number?",
            subtitle = if (role == "DRIVER") {
                "We'll send a verification code to your registered transporter contact flow."
            } else {
                "We'll send you a verification code to continue."
            },
            mediaSpec = CardMediaSpec(
                artwork = CardArtwork.AUTH_SIGNUP_PHONE,
                headerHeight = if (screenConfig.isLandscape) 102.dp else 118.dp,
                placement = CardArtworkPlacement.TOP_INSET,
                contentScale = ContentScale.Fit,
                containerColor = White,
                showInsetFrame = true,
                insetPadding = 8.dp,
                enableImageFadeIn = true,
                imageFadeDurationMs = 170
            ),
            trailingHeaderContent = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.95f)
                ) {
                    Text(
                        text = if (role == "DRIVER") "Driver" else "Transporter",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 24.dp else 40.dp))
        
        FuturisticPhoneInput(
            value = phoneNumber,
            onValueChange = onPhoneChange,
            label = "Phone Number",
            error = errorMessage
        )
        
        Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 20.dp else 32.dp))
        
        FuturisticButton(
            text = "Send OTP",
            onClick = onNext,
            enabled = phoneNumber.length == 10
        )
    }
}

@Composable
fun FullNameStep(
    fullName: String,
    onNameChange: (String) -> Unit,
    errorMessage: String,
    onNext: () -> Unit
) {
    val screenConfig = rememberScreenConfig()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        MediaHeaderCard(
            title = "What's your name?",
            subtitle = "Enter your full name as it appears on official documents.",
            mediaSpec = CardMediaSpec(
                artwork = CardArtwork.AUTH_SIGNUP_NAME,
                headerHeight = if (screenConfig.isLandscape) 102.dp else 118.dp,
                placement = CardArtworkPlacement.TOP_INSET,
                contentScale = ContentScale.Fit,
                containerColor = White,
                showInsetFrame = true,
                insetPadding = 8.dp,
                enableImageFadeIn = true,
                imageFadeDurationMs = 170
            )
        )
        
        Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 24.dp else 40.dp))
        
        FuturisticTextInput(
            value = fullName,
            onValueChange = onNameChange,
            label = "Full Name",
            icon = Icons.Default.Person,
            error = errorMessage
        )
        
        Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 20.dp else 32.dp))
        
        FuturisticButton(
            text = "Next",
            onClick = onNext,
            enabled = fullName.isNotEmpty()
        )
    }
}

@Composable
fun LocationStep(
    location: String,
    isLoadingLocation: Boolean,
    errorMessage: String,
    onFetchLocation: () -> Unit,
    onComplete: () -> Unit,
    isLoading: Boolean
) {
    val screenConfig = rememberScreenConfig()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        MediaHeaderCard(
            title = "Your Location",
            subtitle = "This helps people find work near you.",
            mediaSpec = CardMediaSpec(
                artwork = CardArtwork.AUTH_SIGNUP_LOCATION,
                headerHeight = if (screenConfig.isLandscape) 102.dp else 118.dp,
                placement = CardArtworkPlacement.TOP_INSET,
                contentScale = ContentScale.Fit,
                containerColor = White,
                showInsetFrame = true,
                insetPadding = 8.dp,
                enableImageFadeIn = true,
                imageFadeDurationMs = 170
            )
        )
        
        Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 24.dp else 40.dp))
        
        // Location input with auto-fetch button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(1f)) {
                FuturisticTextInput(
                    value = location,
                    onValueChange = { /* Read-only for now */ },
                    label = "e.g. Mumbai, Delhi",
                    icon = Icons.Default.LocationOn,
                    error = "",
                    readOnly = true
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Auto-fetch location button
            IconButton(
                onClick = onFetchLocation,
                enabled = !isLoadingLocation,
                modifier = Modifier
                    .size(60.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Primary.copy(alpha = 0.5f)
                    )
                    .background(Primary, RoundedCornerShape(16.dp))
            ) {
                if (isLoadingLocation) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Fetch Location",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        if (isLoadingLocation) {
            Spacer(modifier = Modifier.height(12.dp))
            InlineInfoBannerCard(
                title = "Fetching location",
                subtitle = "Getting your current location from device GPS...",
                icon = Icons.Default.MyLocation,
                iconTint = Primary,
                containerColor = Color.White.copy(alpha = 0.08f)
            )
        } else if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            InlineInfoBannerCard(
                title = "Location required",
                subtitle = errorMessage,
                icon = Icons.Default.ErrorOutline,
                iconTint = Error,
                containerColor = Color.White.copy(alpha = 0.08f)
            )
        }
        
        Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 20.dp else 32.dp))
        
        FuturisticButton(
            text = "Complete Registration",
            onClick = onComplete,
            enabled = location.isNotEmpty() && !isLoading,
            isLoading = isLoading
        )
    }
}

@Composable
fun FuturisticTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    error: String = "",
    readOnly: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Primary.copy(alpha = 0.3f)
                )
                .background(
                    Color(0xFF1A1F3A).copy(alpha = 0.6f),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.5f),
                            Secondary.copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 16.sp
                        )
                    }
                    
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        readOnly = readOnly,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        if (error.isNotEmpty()) {
            Text(
                text = error,
                color = Error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

// Helper function to fetch current location
suspend fun fetchCurrentLocation(context: android.content.Context): String? {
    return try {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        @Suppress("MissingPermission")
        val location = fusedLocationClient.lastLocation.await()
        
        if (location != null) {
            // TODO BACKEND: Use geocoding service to get city name
            // For now, return coordinates as string
            "Lat: ${String.format("%.4f", location.latitude)}, Lng: ${String.format("%.4f", location.longitude)}"
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
