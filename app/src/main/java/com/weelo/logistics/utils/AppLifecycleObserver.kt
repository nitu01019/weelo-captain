package com.weelo.logistics.utils

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-level foreground/background detection using ProcessLifecycleOwner.
 *
 * INDUSTRY STANDARD: ProcessLifecycleOwner is the official Android Jetpack way
 * to detect if the app has ANY visible Activity. It correctly handles:
 * - Config changes (rotation)
 * - Multi-window mode
 * - Activity transitions (brief overlap between onStop/onStart)
 *
 * USAGE:
 *   AppLifecycleObserver.init(application)  // once in Application.onCreate()
 *   AppLifecycleObserver.isAppInForeground  // check anywhere
 *
 * THREAD SAFETY: AtomicBoolean — safe to read from any thread (socket, FCM, main).
 * PERFORMANCE: Zero overhead — lifecycle callbacks fire naturally.
 */
object AppLifecycleObserver : DefaultLifecycleObserver {

    private val _isForeground = AtomicBoolean(false)

    /** True when at least one Activity is in STARTED state (visible to user). */
    val isAppInForeground: Boolean
        get() = _isForeground.get()

    /**
     * Initialize once from [Application.onCreate].
     * Safe to call multiple times — ProcessLifecycleOwner deduplicates observers.
     */
    fun init(app: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        timber.log.Timber.i("📱 AppLifecycleObserver initialized")
    }

    override fun onStart(owner: LifecycleOwner) {
        _isForeground.set(true)
        timber.log.Timber.d("📱 App → FOREGROUND")
    }

    override fun onStop(owner: LifecycleOwner) {
        _isForeground.set(false)
        timber.log.Timber.d("📱 App → BACKGROUND")
    }
}
