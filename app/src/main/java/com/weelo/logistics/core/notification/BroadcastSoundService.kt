package com.weelo.logistics.core.notification

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * =============================================================================
 * BROADCAST SOUND SERVICE
 * =============================================================================
 * 
 * Modular notification sound system for broadcast alerts.
 * 
 * FEATURES:
 * - Multiple sound options for transporters to choose
 * - Vibration patterns
 * - Volume control
 * - Easy to extend with new sounds
 * 
 * SCALABILITY:
 * - Singleton pattern for app-wide access
 * - Sound preferences stored locally
 * - Ready for remote sound library
 * 
 * USAGE:
 *   BroadcastSoundService.getInstance(context).playBroadcastSound()
 *   BroadcastSoundService.getInstance(context).setSelectedSound(SoundOption.ALERT_2)
 * 
 * =============================================================================
 */
class BroadcastSoundService private constructor(private val context: Context) {
    
    @SuppressLint("StaticFieldLeak")
    companion object {
        private const val TAG = "BroadcastSound"
        private const val PREFS_NAME = "broadcast_sound_prefs"
        private const val KEY_SELECTED_SOUND = "selected_sound"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_VOLUME = "volume"
        
        @Volatile
        private var instance: BroadcastSoundService? = null
        
        fun getInstance(context: Context): BroadcastSoundService {
            return instance ?: synchronized(this) {
                instance ?: BroadcastSoundService(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // ==========================================================================
    // SOUND OPTIONS - Easily extendable
    // ==========================================================================
    
    /**
     * Available sound options for broadcast notifications
     * Add new sounds here - they will automatically appear in settings
     */
    enum class SoundOption(
        val id: String,
        val displayName: String,
        val description: String,
        val isSystemSound: Boolean = false,
        val systemSoundType: Int? = null
    ) {
        // System sounds
        DEFAULT(
            id = "default",
            displayName = "Default",
            description = "System notification sound",
            isSystemSound = true,
            systemSoundType = RingtoneManager.TYPE_NOTIFICATION
        ),
        ALARM(
            id = "alarm",
            displayName = "Alarm",
            description = "Urgent alarm sound",
            isSystemSound = true,
            systemSoundType = RingtoneManager.TYPE_ALARM
        ),
        RINGTONE(
            id = "ringtone",
            displayName = "Ringtone",
            description = "Phone ringtone",
            isSystemSound = true,
            systemSoundType = RingtoneManager.TYPE_RINGTONE
        ),
        
        // Custom sounds (add raw resources)
        ALERT_1(
            id = "alert_1",
            displayName = "Alert Tone 1",
            description = "Quick alert beep"
        ),
        ALERT_2(
            id = "alert_2",
            displayName = "Alert Tone 2",
            description = "Double beep alert"
        ),
        URGENT(
            id = "urgent",
            displayName = "Urgent",
            description = "High priority alert"
        ),
        CASH_REGISTER(
            id = "cash_register",
            displayName = "Cash Register",
            description = "Money sound - new booking!"
        ),
        TRUCK_HORN(
            id = "truck_horn",
            displayName = "Truck Horn",
            description = "Truck horn sound"
        );
        
        @SuppressLint("StaticFieldLeak")
    companion object {
            fun fromId(id: String): SoundOption {
                return values().find { it.id == id } ?: DEFAULT
            }
            
            fun getAll(): List<SoundOption> = values().toList()
        }
    }
    
    // ==========================================================================
    // VIBRATION PATTERNS - Modular patterns
    // ==========================================================================
    
    /**
     * Vibration patterns for broadcast notifications
     */
    enum class VibrationPattern(
        val id: String,
        val displayName: String,
        val pattern: LongArray
    ) {
        NONE("none", "None", longArrayOf(0)),
        SHORT("short", "Short", longArrayOf(0, 200)),
        MEDIUM("medium", "Medium", longArrayOf(0, 400)),
        LONG("long", "Long", longArrayOf(0, 800)),
        DOUBLE("double", "Double Pulse", longArrayOf(0, 200, 100, 200)),
        URGENT("urgent", "Urgent", longArrayOf(0, 300, 100, 300, 100, 300)),
        SOS("sos", "SOS Pattern", longArrayOf(0, 100, 100, 100, 100, 100, 300, 100, 300, 100, 300, 100, 100, 100, 100, 100, 100));
        
        @SuppressLint("StaticFieldLeak")
    companion object {
            fun fromId(id: String): VibrationPattern {
                return values().find { it.id == id } ?: MEDIUM
            }
        }
    }
    
    // ==========================================================================
    // STATE
    // ==========================================================================
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var mediaPlayer: MediaPlayer? = null
    
    private val _selectedSound = MutableStateFlow(loadSelectedSound())
    val selectedSound: StateFlow<SoundOption> = _selectedSound
    
    private val _soundEnabled = MutableStateFlow(loadSoundEnabled())
    val soundEnabled: StateFlow<Boolean> = _soundEnabled
    
    private val _vibrationEnabled = MutableStateFlow(loadVibrationEnabled())
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled
    
    private val _volume = MutableStateFlow(loadVolume())
    val volume: StateFlow<Float> = _volume
    
    // ==========================================================================
    // PUBLIC METHODS
    // ==========================================================================
    
    /**
     * Play broadcast notification sound
     * Call this when a new broadcast is received
     */
    fun playBroadcastSound() {
        if (!_soundEnabled.value) {
            timber.log.Timber.d("Sound disabled, skipping")
            return
        }
        
        try {
            // Stop any currently playing sound
            stopSound()
            
            val soundOption = _selectedSound.value
            timber.log.Timber.d("Playing sound: ${soundOption.displayName}")
            
            val uri = getSoundUri(soundOption)
            
            if (uri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    setVolume(_volume.value, _volume.value)
                    setOnCompletionListener { release() }
                    setOnErrorListener { mp, _, _ -> 
                        mp.release()
                        true 
                    }
                    prepare()
                    start()
                }
            }
            
            // Also vibrate if enabled
            if (_vibrationEnabled.value) {
                vibrate(VibrationPattern.DOUBLE)
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error playing sound")
            // Fallback to system notification
            playFallbackSound()
        }
    }
    
    /**
     * Play urgent broadcast sound (for urgent requests)
     */
    fun playUrgentSound() {
        if (!_soundEnabled.value) return
        
        try {
            stopSound()
            
            val uri = getSoundUri(SoundOption.ALARM) ?: getSoundUri(SoundOption.DEFAULT)
            
            if (uri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    setVolume(1f, 1f)  // Max volume for urgent
                    setOnCompletionListener { release() }
                    prepare()
                    start()
                }
            }
            
            // Strong vibration for urgent
            if (_vibrationEnabled.value) {
                vibrate(VibrationPattern.URGENT)
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error playing urgent sound")
        }
    }
    
    /**
     * Stop currently playing sound
     */
    fun stopSound() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error stopping sound")
        }
    }
    
    /**
     * Preview a sound option (for settings)
     */
    fun previewSound(option: SoundOption) {
        try {
            stopSound()
            
            val uri = getSoundUri(option)
            if (uri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    setVolume(_volume.value, _volume.value)
                    setOnCompletionListener { release() }
                    prepare()
                    start()
                }
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error previewing sound")
        }
    }
    
    /**
     * Vibrate with a pattern
     */
    fun vibrate(pattern: VibrationPattern = VibrationPattern.MEDIUM) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern.pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern.pattern, -1)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error vibrating")
        }
    }
    
    // ==========================================================================
    // SETTINGS METHODS
    // ==========================================================================
    
    /**
     * Set selected sound option
     */
    fun setSelectedSound(option: SoundOption) {
        _selectedSound.value = option
        prefs.edit().putString(KEY_SELECTED_SOUND, option.id).apply()
        timber.log.Timber.d("Sound set to: ${option.displayName}")
    }
    
    /**
     * Enable/disable sound
     */
    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }
    
    /**
     * Enable/disable vibration
     */
    fun setVibrationEnabled(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }
    
    /**
     * Set volume (0.0 to 1.0)
     */
    fun setVolume(vol: Float) {
        val clampedVol = vol.coerceIn(0f, 1f)
        _volume.value = clampedVol
        prefs.edit().putFloat(KEY_VOLUME, clampedVol).apply()
    }
    
    // ==========================================================================
    // PRIVATE METHODS
    // ==========================================================================
    
    private fun getSoundUri(option: SoundOption): Uri? {
        return if (option.isSystemSound && option.systemSoundType != null) {
            RingtoneManager.getDefaultUri(option.systemSoundType)
        } else {
            // For custom sounds, use system sounds as fallback
            // TODO: Add custom sound files in res/raw/ folder later
            when (option) {
                SoundOption.ALERT_1 -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                SoundOption.ALERT_2 -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                SoundOption.URGENT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                SoundOption.CASH_REGISTER -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                SoundOption.TRUCK_HORN -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
        }
    }
    
    private fun getResourceUri(resId: Int): Uri {
        return Uri.parse("android.resource://${context.packageName}/$resId")
    }
    
    private fun playFallbackSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Fallback sound also failed")
        }
    }
    
    private fun loadSelectedSound(): SoundOption {
        val id = prefs.getString(KEY_SELECTED_SOUND, SoundOption.DEFAULT.id) ?: SoundOption.DEFAULT.id
        return SoundOption.fromId(id)
    }
    
    private fun loadSoundEnabled(): Boolean {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true)
    }
    
    private fun loadVibrationEnabled(): Boolean {
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    }
    
    private fun loadVolume(): Float {
        return prefs.getFloat(KEY_VOLUME, 0.8f)
    }
}
