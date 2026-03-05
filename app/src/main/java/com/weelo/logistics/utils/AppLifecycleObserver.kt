package com.weelo.logistics.utils

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import java.lang.ref.WeakReference

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
    private var appRef: WeakReference<Application>? = null

    /** True when at least one Activity is in STARTED state (visible to user). */
    val isAppInForeground: Boolean
        get() = _isForeground.get()

    /**
     * Initialize once from [Application.onCreate].
     * Safe to call multiple times — ProcessLifecycleOwner deduplicates observers.
     */
    fun init(app: Application) {
        appRef = WeakReference(app)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        timber.log.Timber.i("📱 AppLifecycleObserver initialized")
    }

    override fun onStart(owner: LifecycleOwner) {
        _isForeground.set(true)
        timber.log.Timber.d("📱 App → FOREGROUND")
        // ─────────────────────────────────────────────────────────────────
        // EDGE CASE: User opens app via launcher icon (not via notification).
        // The full-screen notification is stale — overlay is now visible.
        // Dismiss the notification so it doesn't linger.
        // ─────────────────────────────────────────────────────────────────
        appRef?.get()?.let { app ->
            com.weelo.logistics.broadcast.BroadcastFullScreenNotifier.dismiss(app)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        _isForeground.set(false)
        timber.log.Timber.d("📱 App → BACKGROUND")
    }
}
