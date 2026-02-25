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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.ui.components.CardArtwork
import com.weelo.logistics.ui.components.CardArtworkPlacement
import com.weelo.logistics.ui.components.CardMediaSpec
import com.weelo.logistics.ui.components.InlineInfoBannerCard
import com.weelo.logistics.ui.components.MediaHeaderCard
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.ui.components.rememberScreenConfig
import com.weelo.logistics.utils.InputValidator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.weelo.logistics.utils.AuthOtpAutofillCoordinator
import com.weelo.logistics.utils.AuthOtpAutofillCoordinator.OtpAutofillClearReason
import com.weelo.logistics.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class OtpEntrySource { NONE, MANUAL, SMS_AUTOFILL }
private enum class OtpAsyncAction { NONE, VERIFY, RESEND }

/**
 * Modern OTP Verification Screen - Redesigned for speed and clarity
 * 
 * Key Features:
 * - NO delays - instant verification
 * - Manual entry via Next button
 * - SMS autofill triggers fast-path auto-verify
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
    var resendTimer by remember { mutableStateOf(30) }
    var canResend by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var verifyRequestNonce by remember { mutableIntStateOf(0) }
    var otpEntrySource by remember { mutableStateOf(OtpEntrySource.NONE) }
    var lastAutoVerifiedOtpKey by remember { mutableStateOf<String?>(null) }
    var lastAsyncAction by remember { mutableStateOf(OtpAsyncAction.NONE) }
    var pendingAutoReadOtp by remember { mutableStateOf<String?>(null) }
    var pendingAutoReadSessionId by remember { mutableStateOf<Long?>(null) }
    
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val isLoading = authState is AuthState.Loading
    val latestAuthState by rememberUpdatedState(authState)
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
    // SHARED SMS AUTO-READ COORDINATOR (prewarmed on LoginScreen)
    // =========================================================================
    val otpAutofillState by authViewModel.otpAutofillUiState.collectAsStateWithLifecycle()
    val autoReadOtp = otpAutofillState.otpCode
    val isSmsListening = otpAutofillState.isListening
    val otpAutofillSessionId = otpAutofillState.sessionId

    LaunchedEffect(phoneNumber, role) {
        authViewModel.attachOtpAutofill(
            context = context.applicationContext,
            phone = phoneNumber,
            role = role
        )
    }

    LaunchedEffect(autoReadOtp, otpAutofillSessionId, isLoading) {
        val latestOtp = autoReadOtp?.takeIf { it.length == 6 }
        if (latestOtp != null) {
            if (isLoading) {
                val pendingKey = "$phoneNumber|${role.lowercase()}|$latestOtp|${otpAutofillSessionId ?: -1}"
                val existingPendingKey = pendingAutoReadOtp?.let { existing ->
                    "$phoneNumber|${role.lowercase()}|$existing|${pendingAutoReadSessionId ?: -1}"
                }
                if (existingPendingKey != pendingKey) {
                    pendingAutoReadOtp = latestOtp
                    pendingAutoReadSessionId = otpAutofillSessionId
                    timber.log.Timber.d(
                        "📱 OTP autofill buffered while loading (session=%s)",
                        otpAutofillSessionId ?: -1
                    )
                }
                return@LaunchedEffect
            }
        }

        val otpToProcess = when {
            latestOtp != null -> latestOtp
            !isLoading -> pendingAutoReadOtp
            else -> null
        } ?: return@LaunchedEffect

        val sessionIdToProcess = if (latestOtp != null) otpAutofillSessionId else pendingAutoReadSessionId
        val autoKey = "$phoneNumber|${role.lowercase()}|$otpToProcess|${sessionIdToProcess ?: -1}"
        if (lastAutoVerifiedOtpKey == autoKey) return@LaunchedEffect

        timber.log.Timber.i(
            "📱 OTP autofill consumed in UI (session=%s, otp=%s****)",
            sessionIdToProcess ?: -1,
            otpToProcess.take(2)
        )
        otpEntrySource = OtpEntrySource.SMS_AUTOFILL
        otpValue = otpToProcess
        errorMessage = ""
        lastAutoVerifiedOtpKey = autoKey
        pendingAutoReadOtp = null
        pendingAutoReadSessionId = null
        verifyRequestNonce++
    }

    // Countdown timer (single source; resets when resendTimer/canResend changes)
    LaunchedEffect(resendTimer, canResend) {
        if (canResend || resendTimer <= 0) return@LaunchedEffect
        delay(1000)
        if (resendTimer > 1) {
            resendTimer--
        } else {
            resendTimer = 0
            canResend = true
        }
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Idle -> {
                // no-op
            }
            is AuthState.Loading -> {
                errorMessage = ""
            }
            is AuthState.RateLimited -> {
                errorMessage = context.getString(
                    R.string.auth_otp_rate_limited_retry_format,
                    state.retryAfterSeconds
                )
                if (lastAsyncAction == OtpAsyncAction.RESEND) {
                    canResend = true
                    resendTimer = 0
                }
            }
            is AuthState.Error -> {
                errorMessage = state.message
                if (lastAsyncAction == OtpAsyncAction.RESEND) {
                    canResend = true
                    resendTimer = 0
                }
                if (lastAsyncAction == OtpAsyncAction.VERIFY) {
                    val verifySource = otpEntrySource
                    otpValue = ""
                    if (verifySource != OtpEntrySource.SMS_AUTOFILL) {
                        otpEntrySource = OtpEntrySource.NONE
                    }
                }
            }
            is AuthState.OTPSent -> {
                val responseRole = state.role.lowercase()
                if (state.phone == phoneNumber && responseRole == role.lowercase()) {
                    successMessage = state.message
                    errorMessage = ""
                    if (lastAsyncAction == OtpAsyncAction.RESEND) {
                        resendTimer = 30
                        canResend = false
                    }
                }
            }
            is AuthState.Authenticated -> {
                errorMessage = ""
            }
            is AuthState.LoggedOut -> Unit
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.uiEffects.collectLatest { effect ->
            when (effect) {
                AuthUiEffect.OtpVerificationSucceeded -> {
                    val authenticated = latestAuthState as? AuthState.Authenticated
                    val backendLang = authenticated?.preferredLanguage
                    val authenticatedRole = authenticated?.role.orEmpty()
                    if (authenticatedRole.equals("DRIVER", ignoreCase = true)) {
                        if (!backendLang.isNullOrEmpty()) {
                            otpMainActivity?.updateLocale(backendLang)
                        }
                    } else if (authenticatedRole.equals("TRANSPORTER", ignoreCase = true)) {
                        // Transporter language selection is not implemented yet.
                        // Force English to avoid leaking driver language on shared devices.
                        otpMainActivity?.updateLocale("en")
                    }
                    pendingAutoReadOtp = null
                    pendingAutoReadSessionId = null
                    authViewModel.clearOtpAutofill(OtpAutofillClearReason.SUCCESS)
                    onVerifySuccess()
                }
            }
        }
    }

    // Hybrid verify trigger:
    // - SMS autofill can increment verifyRequestNonce (fast path auto-verify)
    // - Manual entry requires Next button (which also increments verifyRequestNonce)
    LaunchedEffect(verifyRequestNonce) {
        if (verifyRequestNonce <= 0) return@LaunchedEffect

        if (otpValue.length != 6) {
            errorMessage = context.getString(R.string.auth_otp_enter_six_digit)
            return@LaunchedEffect
        }

        keyboardController?.hide()
        val validation = InputValidator.validateOTP(otpValue)
        if (!validation.isValid) {
            errorMessage = validation.errorMessage!!
            otpValue = ""
            otpEntrySource = OtpEntrySource.NONE
            return@LaunchedEffect
        }

        lastAsyncAction = OtpAsyncAction.VERIFY
        successMessage = null
        errorMessage = ""
        if (otpEntrySource == OtpEntrySource.SMS_AUTOFILL) {
            timber.log.Timber.d("📱 OTP auto-verify triggered (session=%s)", otpAutofillSessionId ?: -1)
        }
        authViewModel.verifyOtpForRole(phoneNumber, role, otpValue)
    }
    
    // Responsive layout support
    val screenConfig = rememberScreenConfig()
    val otpContentWidthModifier = Modifier
        .fillMaxWidth()
        .widthIn(max = if (screenConfig.isLandscape) 560.dp else 520.dp)
    
    Scaffold(
        containerColor = Background,
        bottomBar = {
            Surface(
                color = Surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = if (screenConfig.isLandscape) 48.dp else 24.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            otpEntrySource = OtpEntrySource.MANUAL
                            verifyRequestNonce++
                        },
                        enabled = otpValue.length == 6 && !isLoading,
                        modifier = otpContentWidthModifier.height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = Color.Black,
                            disabledContainerColor = SurfaceVariant,
                            disabledContentColor = TextSecondary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(stringResource(R.string.next), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = if (screenConfig.isLandscape) 48.dp else 24.dp)
                .padding(top = if (screenConfig.isLandscape) 16.dp else 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = otpContentWidthModifier) {
                OTPHeader(
                    phoneNumber = phoneNumber,
                    role = role,
                    onNavigateBack = {
                        scope.launch {
                            pendingAutoReadOtp = null
                            pendingAutoReadSessionId = null
                            authViewModel.clearOtpAutofill(OtpAutofillClearReason.BACK_NAVIGATION)
                            authViewModel.resetState()
                            onNavigateBack()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(if (screenConfig.isLandscape) 18.dp else 24.dp))

            Box(modifier = otpContentWidthModifier) {
                OTPInputCard(
                    otpValue = otpValue,
                    onOtpChange = { newValue ->
                        if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                            otpEntrySource = OtpEntrySource.MANUAL
                            otpValue = newValue
                            errorMessage = ""
                        }
                    },
                    errorMessage = errorMessage,
                    isLoading = isLoading
                )
            }

            AnimatedVisibility(
                visible = isSmsListening && otpValue.isEmpty() && !isLoading,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                SmsListeningIndicator()
            }

            AnimatedVisibility(
                visible = successMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = otpContentWidthModifier.padding(top = 12.dp)) {
                    SuccessCard(message = successMessage ?: "")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = otpContentWidthModifier) {
                ResendSection(
                    canResend = canResend,
                    resendTimer = resendTimer,
                    onResend = {
                        if (!canResend) return@ResendSection
                        canResend = false
                        resendTimer = 30
                        otpValue = ""
                        errorMessage = ""
                        successMessage = null
                        otpEntrySource = OtpEntrySource.NONE
                        lastAutoVerifiedOtpKey = null
                        pendingAutoReadOtp = null
                        pendingAutoReadSessionId = null
                        lastAsyncAction = OtpAsyncAction.RESEND
                        authViewModel.resendOtp(context.applicationContext, phoneNumber, role)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = otpContentWidthModifier) {
                OTPFooter()
            }
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(16.dp),
                color = Surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.auth_otp_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.help), style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = stringResource(R.string.auth_otp_enter_verification_code),
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (role.equals("driver", ignoreCase = true)) {
                stringResource(R.string.auth_otp_sent_to_transporter_format, phoneNumber)
            } else {
                stringResource(R.string.auth_otp_sent_to_format, phoneNumber)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // OTP Input Boxes - Clickable to show keyboard
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
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
                    .height(60.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    autoCorrect = false
                ),
                enabled = !isLoading,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        innerTextField()
                    }
                },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent)
            )
        }

        AnimatedVisibility(visible = isLoading) {
            Row(
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Primary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.auth_otp_verifying),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Primary
                )
            }
        }

        AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                InlineInfoBannerCard(
                    title = stringResource(R.string.auth_otp_verification_failed_title),
                    subtitle = errorMessage,
                    icon = Icons.Default.ErrorOutline,
                    iconTint = Error,
                    containerColor = ErrorLight
                )
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
        isFilled -> Divider
        else -> TextDisabled.copy(alpha = 0.3f)
    }
    
    val backgroundColor = when {
        isFilled -> Primary.copy(alpha = 0.03f)
        else -> Color.Transparent
    }
    
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isActive) 2.dp else 1.dp,
        animationSpec = tween(300),
        label = "border"
    )
    
    Box(
        modifier = Modifier
            .size(50.dp)
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
    Surface(
        onClick = { if (canResend) onResend() },
        shape = RoundedCornerShape(24.dp),
        color = Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (canResend) Icons.Default.Refresh else Icons.Default.Message,
                contentDescription = null,
                tint = if (canResend) Primary else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (canResend) {
                    stringResource(R.string.auth_otp_resend)
                } else {
                    stringResource(R.string.auth_otp_resend_in_format, resendTimer)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (canResend) TextPrimary else TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// =============================================================================
// SUCCESS CARD COMPONENT
// =============================================================================

@Composable
private fun SuccessCard(message: String) {
    InlineInfoBannerCard(
        title = stringResource(R.string.auth_otp_verified_title),
        subtitle = message,
        icon = Icons.Default.CheckCircle,
        iconTint = Success,
        containerColor = SuccessLight
    )
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
            text = stringResource(R.string.auth_otp_waiting_sms),
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
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.auth_otp_footer_sms_consent),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}
