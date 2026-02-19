package com.weelo.logistics.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.ui.components.rememberScreenConfig
import com.weelo.logistics.utils.InputValidator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.api.VerifyOTPRequest
import com.weelo.logistics.data.api.VerifyOTPResponse
import com.weelo.logistics.data.api.SendOTPRequest
import com.weelo.logistics.WeeloApp
import com.weelo.logistics.utils.SmsRetrieverHelper
import com.weelo.logistics.utils.AppSignatureHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modern OTP Verification Screen - Redesigned for speed and clarity
 * 
 * Key Features:
 * - NO delays - instant verification
 * - Auto-submit when 6 digits entered
 * - Individual digit boxes (modern UI)
 * - SMS auto-read enabled (requires backend SMS format)
 * - Clear countdown timer
 * - Instant navigation on success
 * 
 * SMS Auto-Read Setup (Backend TODO):
 * - SMS must contain app hash code
 * - Format: "<#> Your OTP is: 123456 ABC123xyz..."
 * - Uses Google Play Services SMS Retriever API
 * - No user permission needed!
 * 
 * @param phoneNumber The phone number OTP was sent to
 * @param role "TRANSPORTER" or "DRIVER"
 * @param onVerifySuccess Navigate on successful verification (instant)
 * @param onNavigateBack Back navigation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OTPVerificationScreen(
    phoneNumber: String,
    role: String,
    onVerifySuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var otpValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var resendTimer by remember { mutableStateOf(30) }
    var canResend by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // Get real MainActivity via LocalView (LocalContext is locale-wrapped, can't cast directly)
    val otpView = androidx.compose.ui.platform.LocalView.current
    val otpMainActivity = remember {
        var ctx: android.content.Context = otpView.context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is com.weelo.logistics.MainActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
    
    // =========================================================================
    // SMS AUTO-READ (Google SMS Retriever API — Zero Permission)
    // =========================================================================
    // Industry standard (Rapido/WhatsApp/GPay pattern):
    // - TRANSPORTER login: OTP sent to own phone → auto-read works ✅
    // - DRIVER login: OTP sent to transporter's phone → auto-read won't
    //   trigger on driver's device (different phone), but enabling it is
    //   harmless. If driver & transporter share a device, it works.
    // - Graceful degradation: if auto-read fails, manual entry still works
    // =========================================================================
    val smsRetrieverHelper = remember {
        SmsRetrieverHelper(context.applicationContext)
    }
    
    // Start SMS Retriever & log app hash (for backend SMS format setup)
    DisposableEffect(Unit) {
        smsRetrieverHelper.start()
        
        // Log app hash once (for backend SMS format configuration)
        // Only in debug builds — not needed in production
        if (timber.log.Timber.treeCount > 0) {
            AppSignatureHelper.getAppSignatures(context.applicationContext)
        }
        
        onDispose {
            smsRetrieverHelper.stop()
        }
    }
    
    // Auto-fill OTP when SMS Retriever reads it
    val autoReadOtp by smsRetrieverHelper.otpCode.collectAsState()
    val isSmsListening by smsRetrieverHelper.isListening.collectAsState()
    LaunchedEffect(autoReadOtp) {
        autoReadOtp?.let { otp ->
            if (otp.length == 6 && otpValue.length < 6 && !isLoading) {
                timber.log.Timber.i("📱 Auto-filling OTP from SMS: ${otp.take(2)}****")
                otpValue = otp // This triggers the auto-verify LaunchedEffect below
            }
        }
    }
    
    // Countdown timer
    LaunchedEffect(Unit) {
        while (resendTimer > 0) {
            delay(1000)
            resendTimer--
        }
        canResend = true
    }
    
    // Auto-verify when 6 digits entered - INSTANT ⚡
    LaunchedEffect(otpValue) {
        if (otpValue.length == 6) {
            keyboardController?.hide()
            // No delay — keyboard hides async, verification starts immediately
            
            val validation = InputValidator.validateOTP(otpValue)
            if (!validation.isValid) {
                errorMessage = validation.errorMessage!!
                otpValue = ""
                return@LaunchedEffect
            }
            
            isLoading = true
            errorMessage = ""
            
            // Make actual API call to verify OTP
            try {
                val response = withContext(Dispatchers.IO) {
                    if (role == "DRIVER") {
                        // Driver: use /driver-auth/verify-otp (OTP was sent to transporter)
                        RetrofitClient.driverAuthApi.verifyOtp(
                            com.weelo.logistics.data.api.DriverVerifyOtpRequest(
                                driverPhone = phoneNumber,
                                otp = otpValue
                            )
                        )
                    } else {
                        // Transporter: use /auth/verify-otp
                        RetrofitClient.authApi.verifyOTP(
                            VerifyOTPRequest(
                                phone = phoneNumber,
                                otp = otpValue,
                                role = role.lowercase()
                            )
                        )
                    }
                }
                
                isLoading = false
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    
                    // Check success based on role-specific response
                    val isSuccess = if (role == "DRIVER") {
                        (responseBody as? com.weelo.logistics.data.api.DriverVerifyOtpResponse)?.success == true
                    } else {
                        (responseBody as? VerifyOTPResponse)?.success == true
                    }
                    
                    if (isSuccess) {
                        if (role == "DRIVER") {
                            // Driver response structure
                            val driverResponse = responseBody as com.weelo.logistics.data.api.DriverVerifyOtpResponse
                            driverResponse.data?.let {
                                RetrofitClient.saveTokens(it.accessToken, it.refreshToken)
                                RetrofitClient.saveUserInfo(it.driver.id, it.role)
                                
                                // ============================================================
                                // CRITICAL: Restore ALL preferences from backend BEFORE
                                // navigation fires. Must use withContext to ensure writes
                                // complete before onVerifySuccess() triggers onboarding check.
                                //
                                // WHY: driver_onboarding_check reads from DriverPreferences
                                // immediately. If we navigate before saving, the check sees
                                // empty language → forces language screen every login.
                                //
                                // SCALABILITY: O(1) writes, no extra network call.
                                // ============================================================
                                val driverPrefs = com.weelo.logistics.data.preferences.DriverPreferences
                                    .getInstance(context.applicationContext)
                                
                                // 1. Restore language from backend (if exists)
                                // If backend has language → full save locally →
                                // onboarding check will SKIP language screen.
                                // If backend has no language → don't save →
                                // onboarding check will SHOW language screen.
                                val backendLang = it.driver.preferredLanguage
                                if (!backendLang.isNullOrEmpty()) {
                                    timber.log.Timber.i("🔑 Restoring language from backend: $backendLang → skipping language screen")
                                    driverPrefs.saveLanguage(backendLang)
                                } else {
                                    timber.log.Timber.i("🔑 No language in backend → will show language selection screen")
                                }
                                
                                // 2. Restore profile completion status from backend
                                if (it.driver.isProfileCompleted == true) {
                                    timber.log.Timber.i("🔑 Restoring profile completed from backend")
                                    driverPrefs.markProfileCompleted()
                                }
                                
                                // =========================================================
                                // INSTANT LOCALE on Re-Login
                                //
                                // Apply backend language to locale IMMEDIATELY via
                                // MainActivity.updateLocale(). No recreate(). No screen flash.
                                // No WebSocket disruption.
                                //
                                // CompositionLocalProvider re-provides localized Context →
                                // all stringResource() calls on dashboard resolve to
                                // the correct language from the first frame.
                                //
                                // Normal flow continues: WebSocket + onVerifySuccess()
                                // execute normally (no early return, no skipped code).
                                // =========================================================
                                if (!backendLang.isNullOrEmpty()) {
                                    timber.log.Timber.i("🌐 Re-login with backend language '$backendLang' → instant locale update")
                                    otpMainActivity?.updateLocale(backendLang)
                                }
                            }
                        } else {
                            // Transporter response structure
                            val transporterResponse = responseBody as VerifyOTPResponse
                            transporterResponse.data?.tokens?.let { tokens ->
                                RetrofitClient.saveTokens(tokens.accessToken, tokens.refreshToken)
                            }
                            transporterResponse.data?.user?.let { user ->
                                RetrofitClient.saveUserInfo(user.id, user.role)
                            }
                        }
                        
                        // ================================================================
                        // CRITICAL: Connect WebSocket for real-time broadcast reception!
                        // Without this, transporters won't receive booking notifications
                        // ================================================================
                        timber.log.Timber.i("🔌 Connecting WebSocket after successful login...")
                        WeeloApp.getInstance()?.connectWebSocketIfLoggedIn()
                        
                        successMessage = "Verified successfully!"
                        // Zero delay — navigate instantly to dashboard
                        onVerifySuccess()
                    } else {
                        val errorMsg = if (role == "DRIVER") {
                            (responseBody as? com.weelo.logistics.data.api.DriverVerifyOtpResponse)?.error?.message
                        } else {
                            (responseBody as? VerifyOTPResponse)?.error?.message
                        }
                        errorMessage = errorMsg ?: "Invalid OTP. Please try again."
                        otpValue = ""
                    }
                } else {
                    errorMessage = "Invalid OTP. Please try again."
                    otpValue = ""
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Network error. Please try again."
                otpValue = ""
            }
        }
    }
    
    // Responsive layout support
    val screenConfig = rememberScreenConfig()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Premium background decoration with green accents
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.08f)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 80.dp, y = (-40).dp)
                    .size(200.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Success, Color.Transparent)
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )
            // Bottom accent for landscape
            if (screenConfig.isLandscape) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-40).dp, y = 40.dp)
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Primary, Color.Transparent)
                            ),
                            shape = RoundedCornerShape(50)
                        )
                )
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (screenConfig.isLandscape) 48.dp else 24.dp,
                    vertical = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Reduced spacing in landscape
            Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 16.dp else 40.dp))
            
            // Header Section
            OTPHeader(
                phoneNumber = phoneNumber,
                role = role,
                onNavigateBack = onNavigateBack
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // OTP Input Section
            OTPInputCard(
                otpValue = otpValue,
                onOtpChange = { newValue ->
                    if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                        otpValue = newValue
                        errorMessage = ""
                    }
                },
                errorMessage = errorMessage,
                isLoading = isLoading
            )
            
            // SMS Auto-Read Indicator (Rapido/GPay-style)
            // Shows "Listening for SMS..." with animated dot while SMS Retriever is active
            AnimatedVisibility(
                visible = isSmsListening && otpValue.isEmpty() && !isLoading,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                SmsListeningIndicator()
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Success Message
            AnimatedVisibility(
                visible = successMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SuccessCard(message = successMessage ?: "")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Resend OTP Section
            ResendSection(
                canResend = canResend,
                resendTimer = resendTimer,
                onResend = {
                    canResend = false
                    resendTimer = 30
                    otpValue = ""
                    errorMessage = ""
                    
                    scope.launch {
                        // Restart SMS Retriever — fresh 5-min listening window
                        // Without this, if 4+ min passed since first OTP request,
                        // the resent SMS arrives but SmsRetriever has timed out
                        smsRetrieverHelper.restart()
                        
                        // Call resend OTP API
                        try {
                            withContext(Dispatchers.IO) {
                                if (role == "DRIVER") {
                                    // Driver: use /driver-auth/send-otp (sends to transporter)
                                    RetrofitClient.driverAuthApi.sendOtp(
                                        com.weelo.logistics.data.api.DriverSendOtpRequest(
                                            driverPhone = phoneNumber
                                        )
                                    )
                                } else {
                                    // Transporter: use /auth/send-otp
                                    RetrofitClient.authApi.sendOTP(
                                        SendOTPRequest(
                                            phone = phoneNumber,
                                            role = role.lowercase()
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = "Failed to resend OTP"
                        }
                        
                        // Restart timer
                        while (resendTimer > 0) {
                            delay(1000)
                            resendTimer--
                        }
                        canResend = true
                    }
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Footer Section
            OTPFooter()
        }
    }
}

// =============================================================================
// HEADER COMPONENT
// =============================================================================

@Composable
private fun OTPHeader(
    phoneNumber: String,
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
            color = Success.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Message,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = "Verify OTP",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtitle
        Text(
            text = if (role == "DRIVER") {
                "Enter OTP sent to your transporter"
            } else {
                "Enter OTP sent to +91 $phoneNumber"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Change number link
        TextButton(onClick = onNavigateBack) {
            Text(
                text = "Change Number",
                style = MaterialTheme.typography.bodySmall,
                color = Primary
            )
        }
    }
}

// =============================================================================
// OTP INPUT COMPONENT - Individual Boxes
// =============================================================================

@Composable
private fun OTPInputCard(
    otpValue: String,
    onOtpChange: (String) -> Unit,
    errorMessage: String,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter 6-Digit OTP",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // OTP Input Boxes - Clickable to show keyboard
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(6) { index ->
                        OTPDigitBox(
                            digit = otpValue.getOrNull(index)?.toString() ?: "",
                            isFilled = index < otpValue.length,
                            isActive = index == otpValue.length,
                            hasError = errorMessage.isNotEmpty()
                        )
                    }
                }
                
                // Invisible overlay that captures clicks and shows keyboard
                BasicTextField(
                    value = otpValue,
                    onValueChange = onOtpChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp), // Match digit box height
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        autoCorrect = false
                    ),
                    enabled = !isLoading,
                    decorationBox = { innerTextField ->
                        // Transparent box that captures touch
                        Box(modifier = Modifier.fillMaxSize()) {
                            innerTextField()
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Loading Indicator
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Verifying...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Primary
                    )
                }
            }
            
            // Error Message
            AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// =============================================================================
// SINGLE OTP DIGIT BOX
// =============================================================================

@Composable
private fun OTPDigitBox(
    digit: String,
    isFilled: Boolean,
    isActive: Boolean,
    hasError: Boolean
) {
    val borderColor = when {
        hasError -> Error
        isActive -> Primary
        isFilled -> Success
        else -> TextDisabled.copy(alpha = 0.3f)
    }
    
    val backgroundColor = when {
        isFilled -> Primary.copy(alpha = 0.05f)
        else -> Color.Transparent
    }
    
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isActive) 2.dp else 1.dp,
        animationSpec = tween(300),
        label = "border"
    )
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(animatedBorderWidth, borderColor, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (isFilled) TextPrimary else TextDisabled
        )
    }
}

// =============================================================================
// RESEND SECTION COMPONENT
// =============================================================================

@Composable
private fun ResendSection(
    canResend: Boolean,
    resendTimer: Int,
    onResend: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!canResend) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Resend OTP in ${resendTimer}s",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        } else {
            Text(
                text = "Didn't receive OTP?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            TextButton(onClick = onResend) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Resend",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
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
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// =============================================================================
// SMS LISTENING INDICATOR — Rapido/GPay-style animated indicator
// =============================================================================

@Composable
private fun SmsListeningIndicator() {
    // Pulsing animation for the dot (industry standard — Rapido, GPay, PhonePe)
    val infiniteTransition = rememberInfiniteTransition(label = "sms_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sms_dot_alpha"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing green dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(alpha)
                .background(
                    color = Success,
                    shape = RoundedCornerShape(50)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Waiting for OTP SMS...",
            style = MaterialTheme.typography.bodySmall,
            color = Success,
            fontWeight = FontWeight.Medium
        )
    }
}

// =============================================================================
// FOOTER COMPONENT
// =============================================================================

@Composable
private fun OTPFooter() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Your data is secure and encrypted",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
