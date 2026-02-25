package com.weelo.logistics.broadcast

/**
 * Shared broadcast UI motion timings.
 *
 * Keep these centralized so overlay/list dismiss behavior stays consistent.
 */
object BroadcastUiTiming {
    const val DISMISS_ENTER_MS = 180
    const val DISMISS_HOLD_MS = 1_000L
    const val DISMISS_EXIT_MS = 150
}
