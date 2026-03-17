package com.weelo.logistics.ui.driver

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import timber.log.Timber

/**
 * =============================================================================
 * DRIVER TRIP REQUEST SOUND SERVICE - SINGLETON
 * =============================================================================
 *
 * Plays sound + vibration when trip request arrives.
 * Follows BroadcastSoundService pattern.
 *
 * RESPONSIBILITIES:
 *  - Play notification sound using system default notification tone
 *  - Vibrate device with specific pattern
 *  - Handle MediaPlayer lifecycle (prevent leaks)
 *  - Graceful fallback if sound fails (vibration still plays)
 *
 * THREAD SAFETY:
 *  - Uses synchronized(lock) for MediaPlayer access
 *  - Volatile modifier on mediaPlayer for visibility
 *
 * PLATFORM COMPATIBILITY:
 *  - Handles both old (Vibrator) and new (VibratorManager) APIs
 *  - Works on API 21+ (Material Design 3 requirement)
 */
object DriverTripRequestSoundService {

    private const val TAG = "DriverRequestSound"

    // =========================================================================
    // THREAD SAFETY - Singleton with synchronized access
    // =========================================================================
    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    private val lock = Any()

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Play trip request notification sound + vibration
     *
     * Thread-safe: Uses synchronized block
     * Auto-releases MediaPlayer on completion/error
     *
     * @param context Application context
     */
    fun playTripRequestSound(context: Context) {
        synchronized(lock) {
            try {
                // Release any existing MediaPlayer first
                releaseMediaPlayer()

                // Get system default notification sound URI
                val soundUri = RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
                )

                // Create and configure MediaPlayer
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    setDataSource(context, soundUri)
                    setVolume(1.0f, 1.0f)
                    isLooping = false
                    prepareAsync()
                    setOnCompletionListener {
                        Timber.tag(TAG).i("Trip request sound completed")
                        releaseMediaPlayer()
                    }
                    setOnErrorListener { _, what, extra ->
                        Timber.tag(TAG).e("Sound error: what=$what extra=$extra")
                        releaseMediaPlayer()
                        true
                    }
                    start()
                }

                Timber.tag(TAG).i("🔔 Playing trip request sound")
                playVibration(context)

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to play trip request sound")
                // Fallback: still play vibration even if sound fails
                playVibration(context)
            }
        }
    }

    /**
     * Stop currently playing sound
     */
    fun stop() {
        synchronized(lock) {
            releaseMediaPlayer()
        }
    }

    /**
     * Release all resources (call on app exit)
     */
    fun release() {
        synchronized(lock) {
            releaseMediaPlayer()
        }
    }

    // =========================================================================
    // PRIVATE HELPER METHODS
    // =========================================================================

    /**
     * Play vibration pattern
     *
     * Platform-safe: Handles both Vibrator (API 21+) and VibratorManager (API 31+)
     */
    private fun playVibration(context: Context) {
        try {
            // Get Vibrator instance (platform-specific)
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // Check if device supports vibration
            if (vibrator.hasVibrator()) {
                // Pattern: beep-pause-beep-pause-beep (200ms each, 100ms pauses)
                val pattern = longArrayOf(0, 200, 100, 200, 100)

                // Vibrate based on API level
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(pattern, -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
                Timber.tag(TAG).i("📳 Vibration played")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to play vibration")
        }
    }

    /**
     * Release MediaPlayer resources safely
     *
     * Called on:
     *  - Playing new sound (release old one first)
     *  - On completion callback
     *  - On error callback
     *  - Explicit stop() call
     *  - App exit (release())
     */
    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error releasing MediaPlayer")
        } finally {
            mediaPlayer = null
        }
    }
}
