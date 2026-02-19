package com.weelo.logistics.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * =============================================================================
 * SMS RETRIEVER HELPER — Zero-Permission OTP Auto-Read
 * =============================================================================
 *
 * Uses Google Play Services SMS Retriever API to automatically read OTP
 * from incoming SMS without requiring RECEIVE_SMS or READ_SMS permissions.
 *
 * INDUSTRY STANDARD (Rapido/WhatsApp/GPay pattern):
 * 1. App calls SmsRetriever.getClient(context).startSmsRetriever()
 * 2. Google Play Services registers an OS-level SMS listener
 * 3. When matching SMS arrives, it's intercepted within ~200ms
 *    (often appears to fill "before" the SMS notification shows)
 * 4. BroadcastReceiver extracts OTP using regex
 * 5. OTP emitted via StateFlow → UI auto-fills and auto-verifies
 *
 * SMS FORMAT REQUIRED (backend already sends this):
 * <#> Your Weelo verification code is: 123456. Valid for 5 minutes.
 * {APP_HASH}
 *
 * WORKS FOR:
 * - TRANSPORTER login: OTP sent to their own phone → auto-read works ✅
 * - DRIVER login: OTP sent to transporter's phone → auto-read won't trigger
 *   on driver's device (different phone), but enabling it is harmless.
 *   If driver & transporter share a device (testing), it works.
 *
 * TIMEOUT: Google SMS Retriever has a 5-minute listening window.
 * After 5 minutes, it stops listening and broadcasts TIMEOUT.
 * restart() resets this 5-minute window (used on Resend OTP).
 *
 * SCALABILITY: Zero battery/CPU overhead — Play Services handles SMS listening
 * SECURITY: Zero-permission — no access to user's SMS inbox
 * MODULARITY: Standalone helper, lifecycle-managed via start()/stop()/restart()
 * =============================================================================
 */
class SmsRetrieverHelper(private val context: Context) {

    companion object {
        /** SMS Retriever API timeout — 5 minutes per Google's spec */
        private const val RETRIEVER_TIMEOUT_MS = 5 * 60 * 1000L
    }

    private val _otpCode = MutableStateFlow<String?>(null)
    /** Emits the auto-read OTP code (6 digits). Null when not yet received. */
    val otpCode: StateFlow<String?> = _otpCode.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    /**
     * True when SMS Retriever is actively listening for OTP SMS.
     * UI shows "Listening for SMS..." indicator when true.
     * Auto-resets to false after:
     *   - OTP received (success)
     *   - 5-minute timeout (Google's limit)
     *   - stop() called (screen disposed)
     *   - SmsRetriever fails to start
     */
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var receiver: SmsBroadcastReceiver? = null
    private var isStarted = false
    private var timeoutJob: Job? = null
    // SupervisorJob: child failure (e.g. timeout) doesn't cancel siblings
    // Recreated on each start() to ensure clean state after stop()
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Start listening for SMS with OTP.
     * Call this when OTP screen opens.
     *
     * Safe to call multiple times — skips if already started.
     */
    fun start() {
        if (isStarted) {
            Timber.d("📱 SmsRetriever already started, skipping")
            return
        }

        // Reset previous OTP
        _otpCode.value = null
        
        // Recreate scope (previous may have been cancelled by stop())
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Start SMS Retriever client
        val client = SmsRetriever.getClient(context)
        client.startSmsRetriever()
            .addOnSuccessListener {
                Timber.i("📱 SmsRetriever started — listening for OTP SMS (5-min window)")
                isStarted = true
                _isListening.value = true
                registerReceiver()
                startTimeoutTimer()
            }
            .addOnFailureListener { e ->
                Timber.e(e, "📱 SmsRetriever failed to start — manual OTP entry required")
                _isListening.value = false
                // Graceful degradation: user can still type OTP manually
            }
    }

    /**
     * Stop listening and clean up all resources.
     * Call this when OTP screen is disposed.
     *
     * Cancels the coroutine scope to prevent leaked coroutines
     * (e.g. timeout timer running after screen is gone).
     */
    fun stop() {
        timeoutJob?.cancel()
        timeoutJob = null
        unregisterReceiver()
        isStarted = false
        _isListening.value = false
        // Cancel all coroutines in scope to prevent leaks
        scope.cancel()
        Timber.d("📱 SmsRetriever stopped")
    }

    /**
     * Restart the SMS Retriever — resets the 5-minute listening window.
     *
     * MUST be called on Resend OTP. Without this:
     * - User requests OTP → SmsRetriever starts (5-min window)
     * - 4 minutes pass, no SMS received (delivery delay / wrong number)
     * - User taps "Resend OTP" → new SMS sent
     * - But SmsRetriever's 5-min window has only 1 min left
     * - If SMS arrives after 1 min → SmsRetriever already timed out → no auto-read
     *
     * restart() = stop() + start() → fresh 5-minute window.
     */
    fun restart() {
        Timber.d("📱 SmsRetriever restarting (fresh 5-min window)")
        stop()
        start()
    }

    /**
     * Start 5-minute timeout timer.
     * Google SMS Retriever automatically stops after 5 minutes.
     * This timer updates _isListening so UI can remove the indicator.
     */
    private fun startTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(RETRIEVER_TIMEOUT_MS)
            if (isStarted && _otpCode.value == null) {
                Timber.d("📱 SmsRetriever 5-min timeout — hiding listening indicator")
                _isListening.value = false
                // Note: don't call stop() here — Google Play Services handles
                // the actual timeout. We only update the UI state.
            }
        }
    }

    /**
     * Register the BroadcastReceiver for SMS_RETRIEVED_ACTION.
     */
    private fun registerReceiver() {
        if (receiver != null) return

        receiver = SmsBroadcastReceiver(
            onOtpReceived = { otp ->
                Timber.i("📱 OTP auto-read: ${otp.take(2)}****")
                _otpCode.value = otp
                _isListening.value = false
                timeoutJob?.cancel()
                // Auto-cleanup after receiving OTP (single use)
                unregisterReceiver()
            },
            onTimeout = {
                Timber.w("📱 SmsRetriever timeout — user must enter OTP manually")
                _isListening.value = false
                isStarted = false
                timeoutJob?.cancel()
            }
        )

        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
        Timber.d("📱 SMS BroadcastReceiver registered")
    }

    /**
     * Unregister the BroadcastReceiver safely.
     */
    private fun unregisterReceiver() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
                Timber.d("📱 SMS BroadcastReceiver unregistered")
            } catch (e: IllegalArgumentException) {
                // Already unregistered — safe to ignore
            }
        }
        receiver = null
    }
}

/**
 * BroadcastReceiver for SMS Retriever API.
 *
 * Receives the SMS_RETRIEVED_ACTION broadcast from Google Play Services,
 * extracts the 6-digit OTP from the SMS body, and invokes the callback.
 *
 * SECURITY: Only receives SMS that contain the app's hash — cannot read
 * arbitrary SMS messages.
 *
 * TIMEOUT HANDLING: When Google's 5-min window expires, it broadcasts
 * TIMEOUT status code. We invoke onTimeout so the UI can update.
 */
private class SmsBroadcastReceiver(
    private val onOtpReceived: (String) -> Unit,
    private val onTimeout: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return

        val extras = intent.extras ?: return
        @Suppress("DEPRECATION")
        val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return

        when (status.statusCode) {
            CommonStatusCodes.SUCCESS -> {
                val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return
                Timber.d("📱 SMS received by Retriever: ${message.take(30)}...")

                // Extract 6-digit OTP from SMS body
                val otp = extractOtp(message)
                if (otp != null) {
                    onOtpReceived(otp)
                } else {
                    Timber.w("📱 Could not extract OTP from SMS: ${message.take(50)}...")
                }
            }
            CommonStatusCodes.TIMEOUT -> {
                onTimeout()
            }
            else -> {
                Timber.w("📱 SmsRetriever error: statusCode=${status.statusCode}")
            }
        }
    }

    /**
     * Extract 6-digit OTP from SMS message body.
     *
     * Matches patterns like:
     * - "Your code is: 123456"
     * - "verification code is: 123456"
     * - "OTP is 123456"
     * - Just finds any 6-digit sequence as fallback
     */
    private fun extractOtp(message: String): String? {
        // Try specific pattern first: "code is: XXXXXX" or "code is XXXXXX"
        val specificPattern = Regex("""(?:code|otp)\s*(?:is)?[:\s]+(\d{6})""", RegexOption.IGNORE_CASE)
        specificPattern.find(message)?.groupValues?.get(1)?.let { return it }

        // Fallback: find any 6-digit sequence
        val genericPattern = Regex("""\b(\d{6})\b""")
        genericPattern.find(message)?.groupValues?.get(1)?.let { return it }

        return null
    }
}
