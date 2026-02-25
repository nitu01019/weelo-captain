package com.weelo.logistics.ui.utils

/**
 * Bounded de-duplication set for replayed socket/UI events.
 *
 * Socket flows in this app intentionally use replay=1 for late collectors,
 * which can cause screens to re-handle stale events when reopened. This helper
 * tracks a small rolling window of handled event keys (e.g. "orderId|timestamp")
 * so UI logic remains idempotent without changing backend payloads/contracts.
 */
class SocketUiEventDeduper(
    private val maxEntries: Int = 128
) {
    private val handled = object : LinkedHashMap<String, Unit>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun shouldHandle(key: String): Boolean {
        if (key.isBlank()) return false
        if (handled.containsKey(key)) return false
        handled[key] = Unit
        return true
    }

    @Synchronized
    fun clear() {
        handled.clear()
    }
}
