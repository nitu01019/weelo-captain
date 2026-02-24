package com.weelo.logistics.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Shared OTP autofill coordinator for Login -> OTP screen transition.
 *
 * Fixes missed OTP autofill caused by starting SMS Retriever only after OTP screen opens.
 * This coordinator prewarms the listener before send and keeps the state across navigation.
 */
object AuthOtpAutofillCoordinator {

    enum class SessionStatus {
        PREPARED,
        LISTENING,
        OTP_RECEIVED,
        TIMEOUT,
        CLEARED
    }

    enum class OtpAutofillClearReason {
        SUCCESS,
        BACK_NAVIGATION,
        PHONE_CHANGED,
        RESEND,
        TIMEOUT,
        SCREEN_DISPOSED_NO_SESSION,
        SEND_FAILED
    }

    data class OtpAutofillSession(
        val sessionId: Long,
        val phone: String,
        val role: String,
        val createdAtMs: Long,
        val status: SessionStatus
    )

    data class OtpAutofillUiState(
        val otpCode: String? = null,
        val isListening: Boolean = false,
        val sessionId: Long? = null,
        val phone: String? = null,
        val role: String? = null,
        val status: SessionStatus = SessionStatus.CLEARED
    )

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var helper: SmsRetrieverHelper? = null
    private var helperBridgeJob: Job? = null
    private var helperOtpBridgeJob: Job? = null
    private var helperListeningBridgeJob: Job? = null

    private var currentSession: OtpAutofillSession? = null
    private var nextSessionId: Long = 1L
    private var lastDeliveredOtpKey: String? = null

    private val _uiState = MutableStateFlow(OtpAutofillUiState())
    val uiState: StateFlow<OtpAutofillUiState> = _uiState.asStateFlow()

    private fun normalizedRole(role: String): String = role.trim().lowercase()
    private fun maskPhone(phone: String): String =
        if (phone.length < 6) "****" else phone.take(2) + "****" + phone.takeLast(2)

    private fun ensureInitialized(appContext: Context) {
        if (helper != null) return

        val context = appContext.applicationContext
        helper = SmsRetrieverHelper(context)

        // Bridge helper state into coordinator state so screens can survive navigation handoff.
        helperBridgeJob?.cancel()
        helperBridgeJob = scope.launch {
            helperOtpBridgeJob?.cancel()
            helperListeningBridgeJob?.cancel()

            helper?.let { smsHelper ->
                helperOtpBridgeJob = launch {
                    smsHelper.otpCode.collectLatest { otp ->
                        mutex.withLock {
                            if (otp == null) {
                                _uiState.value = _uiState.value.copy(otpCode = null)
                                return@withLock
                            }

                            val session = currentSession ?: run {
                                Timber.w("OTP received with no active autofill session")
                                return@withLock
                            }
                            val key =
                                "${session.phone}|${normalizedRole(session.role)}|$otp|${session.sessionId}"
                            if (lastDeliveredOtpKey == key) {
                                Timber.d("Ignoring duplicate autofill OTP for session=${session.sessionId}")
                                return@withLock
                            }

                            lastDeliveredOtpKey = key
                            currentSession = session.copy(status = SessionStatus.OTP_RECEIVED)
                            _uiState.value = _uiState.value.copy(
                                otpCode = otp,
                                sessionId = session.sessionId,
                                phone = session.phone,
                                role = session.role,
                                status = SessionStatus.OTP_RECEIVED
                            )
                            Timber.i("OTP captured in coordinator session=${session.sessionId}")
                        }
                    }
                }

                helperListeningBridgeJob = launch {
                    smsHelper.isListening.collectLatest { listening ->
                        mutex.withLock {
                            val session = currentSession
                            val newStatus = when {
                                session == null -> SessionStatus.CLEARED
                                listening -> SessionStatus.LISTENING
                                session.status == SessionStatus.OTP_RECEIVED -> SessionStatus.OTP_RECEIVED
                                session.status == SessionStatus.CLEARED -> SessionStatus.CLEARED
                                else -> SessionStatus.TIMEOUT
                            }
                            if (session != null && session.status != newStatus) {
                                currentSession = session.copy(status = newStatus)
                            }
                            _uiState.value = _uiState.value.copy(
                                isListening = listening,
                                status = newStatus
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun prepareForOtpSend(context: Context, phone: String, role: String): Long = mutex.withLock {
        ensureInitialized(context)
        val normalizedRole = normalizedRole(role)
        val existing = currentSession
        val sameSession = existing?.phone == phone && normalizedRole(existing.role) == normalizedRole
        val smsHelper = helper ?: error("SmsRetrieverHelper not initialized")

        if (sameSession && _uiState.value.isListening) {
            Timber.d(
                "Reusing active OTP autofill session=${existing?.sessionId} for " +
                    "${maskPhone(phone)}/$normalizedRole"
            )
            return@withLock existing!!.sessionId
        }

        val sessionId = if (sameSession && existing != null) existing.sessionId else nextSessionId++
        currentSession = OtpAutofillSession(
            sessionId = sessionId,
            phone = phone,
            role = normalizedRole,
            createdAtMs = System.currentTimeMillis(),
            status = SessionStatus.PREPARED
        )
        lastDeliveredOtpKey = null
        _uiState.value = OtpAutofillUiState(
            otpCode = null,
            isListening = false,
            sessionId = sessionId,
            phone = phone,
            role = normalizedRole,
            status = SessionStatus.PREPARED
        )

        if (sameSession) {
            smsHelper.start()
        } else {
            smsHelper.restart()
        }
        Timber.i(
            "Prepared OTP autofill session=$sessionId for " +
                "${maskPhone(phone)}/$normalizedRole"
        )
        return@withLock sessionId
    }

    suspend fun attachOtpScreen(context: Context, phone: String, role: String): Long? = mutex.withLock {
        ensureInitialized(context)
        val normalizedRole = normalizedRole(role)
        val existing = currentSession
        val smsHelper = helper ?: error("SmsRetrieverHelper not initialized")

        if (existing == null ||
            existing.phone != phone ||
            normalizedRole(existing.role) != normalizedRole
        ) {
            Timber.w(
                "No matching prewarmed OTP session for ${maskPhone(phone)}/$normalizedRole; " +
                    "creating screen-attached session"
            )
            val newId = nextSessionId++
            currentSession = OtpAutofillSession(
                sessionId = newId,
                phone = phone,
                role = normalizedRole,
                createdAtMs = System.currentTimeMillis(),
                status = SessionStatus.PREPARED
            )
            lastDeliveredOtpKey = null
            _uiState.value = OtpAutofillUiState(
                otpCode = null,
                isListening = false,
                sessionId = newId,
                phone = phone,
                role = normalizedRole,
                status = SessionStatus.PREPARED
            )
            smsHelper.start()
            return@withLock newId
        }

        if (!_uiState.value.isListening && _uiState.value.otpCode == null) {
            smsHelper.start()
        }
        return@withLock existing.sessionId
    }

    suspend fun restartForResend(context: Context, phone: String, role: String): Long = mutex.withLock {
        ensureInitialized(context)
        val normalizedRole = normalizedRole(role)
        val smsHelper = helper ?: error("SmsRetrieverHelper not initialized")
        val sessionId = nextSessionId++
        currentSession = OtpAutofillSession(
            sessionId = sessionId,
            phone = phone,
            role = normalizedRole,
            createdAtMs = System.currentTimeMillis(),
            status = SessionStatus.PREPARED
        )
        lastDeliveredOtpKey = null
        _uiState.value = OtpAutofillUiState(
            otpCode = null,
            isListening = false,
            sessionId = sessionId,
            phone = phone,
            role = normalizedRole,
            status = SessionStatus.PREPARED
        )
        smsHelper.restart()
        Timber.i(
            "Restarted OTP autofill session=$sessionId for resend " +
                "(${maskPhone(phone)}/$normalizedRole)"
        )
        return@withLock sessionId
    }

    suspend fun clearSession(reason: OtpAutofillClearReason) = mutex.withLock {
        currentSession = currentSession?.copy(status = SessionStatus.CLEARED)
        helper?.stop()
        lastDeliveredOtpKey = null
        _uiState.value = OtpAutofillUiState(status = SessionStatus.CLEARED)
        Timber.d("Cleared OTP autofill session (reason=$reason)")
        currentSession = null
    }
}
