package com.weelo.logistics.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weelo.logistics.R
import com.weelo.logistics.ui.components.InlineInfoBannerCard
import com.weelo.logistics.ui.components.IllustrationCanvas
import com.weelo.logistics.ui.theme.*
import com.weelo.logistics.ui.components.rememberScreenConfig
import com.weelo.logistics.utils.InputValidator
import com.weelo.logistics.utils.AuthOtpAutofillCoordinator
import com.weelo.logistics.utils.AuthOtpAutofillCoordinator.OtpAutofillClearReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class OtpSendTrigger { AUTO, MANUAL_RETRY, IME_DONE }

/**
 * Modern Login Screen - Redesigned for speed and clarity
 * 
 * Key Features:
 * - NO toast delays - instant navigation
 * - Clean, minimal UI
 * - Fast animations
 * - Clear error states
 * - Backend ready
 * 
 * @param role "TRANSPORTER" or "DRIVER"
 * @param onNavigateToSignup Navigate to signup
 * @param onNavigateToOTP Navigate to OTP screen (instant, no delay)
 * @param onNavigateBack Back navigation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class, FlowPreview::class)
@Composable
fun LoginScreen(
    role: String,
    onNavigateToSignup: () -> Unit,
    onNavigateToOTP: (String, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var lastAutoSentPhone by remember { mutableStateOf<String?>(null) }
    var inFlightOtpRequestPhone by remember { mutableStateOf<String?>(null) }
    var expectedOtpSuccessPhone by remember { mutableStateOf<String?>(null) }
    var handledOtpSuccessKey by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    // Use AuthViewModel so DRIVER uses /driver-auth/* and TRANSPORTER uses /auth/*
    val authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val normalizedRole = remember(role) { role.lowercase() }
    val latestPhoneNumber by rememberUpdatedState(phoneNumber)

    fun sendOtpNow(trigger: OtpSendTrigger, targetPhone: String) {
        val phone = targetPhone.trim()
        if (phone.length != 10) return

        val validation = InputValidator.validatePhoneNumber(phone)
        if (!validation.isValid) {
            errorMessage = validation.errorMessage ?: context.getString(R.string.auth_login_invalid_phone)
            return
        }

        if (isLoading || inFlightOtpRequestPhone == phone) return
        if (trigger == OtpSendTrigger.AUTO && lastAutoSentPhone == phone) return

        keyboardController?.hide()
        errorMessage = ""
        statusMessage = context.getString(R.string.auth_login_status_sending_otp)
        isLoading = true
        inFlightOtpRequestPhone = phone
        expectedOtpSuccessPhone = phone
        handledOtpSuccessKey = null

        scope.launch {
            try {
                authViewModel.prepareOtpAutofillForSend(
                    context = context.applicationContext,
                    phone = phone,
                    role = role
                )
                if (normalizedRole == "driver") {
                    authViewModel.sendDriverOTP(phone)
                } else {
                    authViewModel.sendTransporterOTP(phone)
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Send OTP error")
                authViewModel.clearOtpAutofill(OtpAutofillClearReason.SEND_FAILED)
                isLoading = false
                inFlightOtpRequestPhone = null
                statusMessage = null
                errorMessage = context.getString(R.string.auth_error_network_try_again)
            }
        }
    }

    // Reflect backend state instantly in UI
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Idle -> {
                isLoading = false
                if (inFlightOtpRequestPhone == null) statusMessage = null
            }
            is AuthState.Loading -> {
                isLoading = true
                errorMessage = ""
                if (inFlightOtpRequestPhone != null) {
                    statusMessage = context.getString(R.string.auth_login_status_sending_otp)
                }
            }
            is AuthState.RateLimited -> {
                isLoading = false
                inFlightOtpRequestPhone = null
                authViewModel.clearOtpAutofill(OtpAutofillClearReason.SEND_FAILED)
                val retryAfter = state.retryAfterSeconds
                errorMessage = context.getString(R.string.auth_otp_rate_limited_retry_format, retryAfter)
                statusMessage = null
            }
            is AuthState.Error -> {
                isLoading = false
                inFlightOtpRequestPhone = null
                authViewModel.clearOtpAutofill(OtpAutofillClearReason.SEND_FAILED)
                errorMessage = state.message
                statusMessage = null
            }
            is AuthState.OTPSent -> {
                isLoading = false
                val responsePhone = state.phone
                val responseRole = state.role.lowercase()
                val stateKey = "$responsePhone|$responseRole|${state.message}"

                val isExpectedPhone = expectedOtpSuccessPhone == responsePhone
                val isExpectedRole = responseRole == normalizedRole

                if (isExpectedPhone && isExpectedRole && handledOtpSuccessKey != stateKey) {
                    handledOtpSuccessKey = stateKey
                    inFlightOtpRequestPhone = null
                    lastAutoSentPhone = responsePhone
                    statusMessage = state.message

                    if (responsePhone.isNotEmpty()) {
                        onNavigateToOTP(responsePhone, role)
                    } else {
                        errorMessage = context.getString(R.string.auth_login_enter_phone_number)
                    }
                } else {
                    timber.log.Timber.w("Ignoring stale OTP sent state: phone=${state.phone} role=${state.role}")
                }
            }
            is AuthState.Authenticated -> {
                isLoading = false
                inFlightOtpRequestPhone = null
            }
            is AuthState.LoggedOut -> {
                isLoading = false
                inFlightOtpRequestPhone = null
            }
        }
    }

    // Auto-send OTP only on LoginScreen, guarded with debounce + distinct value.
    LaunchedEffect(role) {
        snapshotFlow { latestPhoneNumber }
            .debounce(350)
            .distinctUntilChanged()
            .collect { value ->
                if (value.length < 10) {
                    if (value.length < 10) {
                        lastAutoSentPhone = null
                    }
                    return@collect
                }
                if (value.length == 10 && value.all(Char::isDigit)) {
                    sendOtpNow(OtpSendTrigger.AUTO, value)
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IllustrationCanvas)
    ) {
        val screenConfig = rememberScreenConfig()
        val authContentWidthModifier = Modifier
            .fillMaxWidth()
            .widthIn(max = if (screenConfig.isLandscape) 620.dp else 560.dp)
        val bannerHeight = if (screenConfig.isLandscape) 184.dp else 242.dp

        LoginHeroBanner(
            role = role,
            onNavigateBack = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Surface,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (screenConfig.isLandscape) 20.dp else 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = authContentWidthModifier) {
                    Column {
                        Text(
                            text = stringResource(R.string.auth_login_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (normalizedRole == "driver") {
                                stringResource(R.string.auth_login_subtitle_driver)
                            } else {
                                stringResource(R.string.auth_login_subtitle_transporter)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                PhoneInputCard(
                    phoneNumber = phoneNumber,
                    onPhoneChange = {
                        if (it.length <= 10 && it.all(Char::isDigit)) {
                            val wasTenDigits = phoneNumber.length == 10
                            phoneNumber = it
                            errorMessage = ""
                            if (wasTenDigits && it.length < 10) {
                                statusMessage = null
                                inFlightOtpRequestPhone = null
                                expectedOtpSuccessPhone = null
                                lastAutoSentPhone = null
                                scope.launch {
                                    authViewModel.clearOtpAutofill(OtpAutofillClearReason.PHONE_CHANGED)
                                }
                            }
                        }
                    },
                    errorMessage = errorMessage,
                    onSubmit = {
                        if (phoneNumber.length == 10) {
                            sendOtpNow(OtpSendTrigger.IME_DONE, phoneNumber)
                        }
                    },
                    onRetry = {
                        sendOtpNow(OtpSendTrigger.MANUAL_RETRY, phoneNumber)
                    },
                    modifier = authContentWidthModifier
                )

                AnimatedVisibility(
                    visible = isLoading || (!statusMessage.isNullOrBlank() && errorMessage.isBlank()),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = authContentWidthModifier
                            .padding(top = 14.dp)
                    ) {
                        InlineInfoBannerCard(
                            title = if (isLoading) {
                                stringResource(R.string.auth_login_status_sending_otp)
                            } else {
                                stringResource(R.string.auth_otp_sent_title)
                            },
                            subtitle = statusMessage ?: stringResource(R.string.please_wait),
                            icon = if (isLoading) Icons.Default.Schedule else Icons.Default.CheckCircle,
                            iconTint = if (isLoading) Primary else Success,
                            containerColor = if (isLoading) SurfaceVariant else SuccessLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Box(modifier = authContentWidthModifier) {
                    LoginFooter(
                        role = role,
                        onNavigateToSignup = onNavigateToSignup
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// =============================================================================
// HEADER COMPONENT
// =============================================================================

@Composable
private fun LoginHeroBanner(
    role: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenConfig = rememberScreenConfig()
    val isDriver = role.equals("driver", ignoreCase = true)
    val artworkRes = if (isDriver) {
        com.weelo.logistics.R.drawable.card_auth_role_driver_soft
    } else {
        com.weelo.logistics.R.drawable.card_auth_role_transporter_soft
    }
    Box(
        modifier = modifier
            .background(IllustrationCanvas)
    ) {
        Image(
            painter = painterResource(id = artworkRes),
            contentDescription = if (isDriver) {
                stringResource(R.string.auth_login_cd_driver_banner)
            } else {
                stringResource(R.string.auth_login_cd_transporter_banner)
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = if (screenConfig.isLandscape) 20.dp else 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.94f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.back), style = MaterialTheme.typography.labelLarge)
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.94f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDriver) Icons.Default.DirectionsCar else Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isDriver) {
                            stringResource(R.string.auth_role_driver)
                        } else {
                            stringResource(R.string.auth_role_transporter)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

// =============================================================================
// PHONE INPUT COMPONENT
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneInputCard(
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    errorMessage: String,
    onSubmit: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_login_phone_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.auth_login_phone_placeholder),
                        color = TextDisabled
                    )
                },
                leadingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "+91",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Divider(
                            modifier = Modifier
                                .height(24.dp)
                                .width(1.dp),
                            color = TextDisabled.copy(alpha = 0.3f)
                        )
                    }
                },
                trailingIcon = {
                    if (phoneNumber.length == 10) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Success
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSubmit() }
                ),
                singleLine = true,
                isError = errorMessage.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = TextDisabled.copy(alpha = 0.3f),
                    errorBorderColor = Error,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            
            // Error Message (structured banner + input red state)
            AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    InlineInfoBannerCard(
                        title = stringResource(R.string.auth_login_send_otp_failed_title),
                        subtitle = errorMessage,
                        icon = Icons.Default.ErrorOutline,
                        iconTint = Error,
                        containerColor = ErrorLight,
                        action = if (onRetry != null) {
                            {
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            }
                        } else null
                    )
                }
            }
            
            // Character count
            if (phoneNumber.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.auth_login_phone_count_format, phoneNumber.length),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDisabled,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

// =============================================================================
// FOOTER COMPONENT
// =============================================================================

@Composable
private fun LoginFooter(
    role: String,
    onNavigateToSignup: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Help Text
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (role.equals("driver", ignoreCase = true)) {
                    stringResource(R.string.auth_login_footer_driver_help)
                } else {
                    stringResource(R.string.auth_login_footer_transporter_help)
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Signup Link with premium styling
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.auth_login_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = onNavigateToSignup) {
                Text(
                    text = stringResource(R.string.auth_sign_up),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black  // Black for premium Rapido look
                )
            }
        }
    }
}
