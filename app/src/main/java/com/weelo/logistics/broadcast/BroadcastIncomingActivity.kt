package com.weelo.logistics.broadcast

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.weelo.logistics.ui.theme.WeeloTheme

/**
 * Full-screen Activity for incoming broadcast offers when app is in background.
 *
 * INDUSTRY STANDARD: Same pattern as incoming phone call UI (WhatsApp, Uber, Rapido).
 *
 * LIFECYCLE:
 * 1. Android launches this Activity via [setFullScreenIntent] (lock screen or heads-up)
 * 2. Activity reads broadcast data from [BroadcastOverlayManager] (single source of truth)
 * 3. User accepts → finish() + navigate to MainActivity for acceptance flow
 * 4. User rejects → finish() + dismiss notification
 * 5. Broadcast expires → auto finish() + auto dismiss
 *
 * DESIGN DECISIONS:
 * - Reads from BroadcastOverlayManager (no data duplication, no serialization)
 * - Reuses BroadcastOverlayScreen composable (exact same UI as in-app overlay)
 * - singleInstance launch mode → only one incoming screen at a time
 * - Separate taskAffinity → back button dismisses without affecting main app
 * - turnScreenOn + showOnLockScreen → wakes device and shows over lock
 *
 * THREAD SAFETY: All Compose state reads are on main thread (standard).
 * PERFORMANCE: Lightweight Activity — no network calls, no database queries.
 */
class BroadcastIncomingActivity : ComponentActivity() {

    companion object {
        private const val TAG = "BroadcastIncoming"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // =====================================================================
        // WAKE DEVICE + SHOW OVER LOCK SCREEN
        // Standard API for incoming-call-style screens.
        // =====================================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Keep screen on while broadcast is displayed
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val broadcastId = intent?.getStringExtra("broadcast_id")
        timber.log.Timber.i("📲 BroadcastIncomingActivity opened: broadcastId=%s", broadcastId)

        setContent {
            WeeloTheme {
                // Read current broadcast from BroadcastOverlayManager (single source of truth)
                val currentBroadcast by BroadcastOverlayManager.currentBroadcast.collectAsState()
                val isVisible by BroadcastOverlayManager.isOverlayVisible.collectAsState()

                // If no broadcast available (expired or consumed), auto-close
                LaunchedEffect(currentBroadcast, isVisible) {
                    if (currentBroadcast == null || !isVisible) {
                        timber.log.Timber.i("📲 No active broadcast — closing incoming screen")
                        BroadcastFullScreenNotifier.dismiss(this@BroadcastIncomingActivity)
                        finish()
                    }
                }

                // Reuse the SAME overlay composable for consistent UI
                BroadcastOverlayScreen(
                    onAccept = { broadcast ->
                        timber.log.Timber.i("🎯 Broadcast accepted from incoming screen: ${broadcast.broadcastId}")
                        // Store broadcast BEFORE finish() so MainActivity can open acceptance screen
                        // even after BroadcastOverlayManager.clearAll() runs in onDestroy().
                        BroadcastStateSync.setPendingAccept(broadcast)
                        BroadcastFullScreenNotifier.dismiss(this@BroadcastIncomingActivity)
                        // Open main app for the acceptance flow (truck + driver selection)
                        navigateToMainApp(broadcast.broadcastId)
                        finish()
                    },
                    onReject = { broadcast ->
                        timber.log.Timber.i("❌ Broadcast rejected from incoming screen: ${broadcast.broadcastId}")
                        BroadcastFullScreenNotifier.dismiss(this@BroadcastIncomingActivity)
                        finish()
                    }
                )
            }
        }
    }

    // =========================================================================
    // EDGE CASE: Second broadcast arrives while this Activity is already open.
    // singleInstance reuses the same Activity — onNewIntent fires instead of
    // a new onCreate. We just update the intent so the composable re-reads.
    // =========================================================================
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newBroadcastId = intent.getStringExtra("broadcast_id")
        timber.log.Timber.i("📲 BroadcastIncomingActivity onNewIntent: broadcastId=%s", newBroadcastId)
    }

    /**
     * Navigate to MainActivity for the acceptance flow (truck + driver selection).
     *
     * Uses a dedicated intent type "accept_broadcast" so MainActivity knows to open
     * the BroadcastAcceptanceScreen directly — NOT re-show the overlay.
     */
    private fun navigateToMainApp(broadcastId: String) {
        val intent = Intent(this, com.weelo.logistics.MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("notification_type", "accept_broadcast")
            putExtra("broadcast_id", broadcastId)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure notification is dismissed if Activity is destroyed (e.g., by system)
        BroadcastFullScreenNotifier.dismiss(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        timber.log.Timber.d("📲 BroadcastIncomingActivity destroyed")
    }
}
