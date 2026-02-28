package com.weelo.logistics.broadcast

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Lightweight persistence for broadcast reliability signals.
 *
 * Tracks recently seen broadcast IDs, estimates reconnect/killed-app misses,
 * and gates battery optimization guidance prompts.
 */
object BroadcastRecoveryTracker {
    private const val PREFS_NAME = "weelo_prefs"
    private const val KEY_SEEN_CACHE_JSON = "broadcast_seen_cache_json"
    private const val KEY_MISSED_STREAK = "broadcast_missed_streak"
    private const val KEY_LAST_GUIDANCE_PROMPT_MS = "broadcast_last_guidance_prompt_ms"
    private const val MAX_SEEN_ENTRIES = 250
    private const val SEEN_TTL_MS = 30 * 60 * 1000L
    private const val GUIDANCE_STREAK_THRESHOLD = 3
    private const val GUIDANCE_PROMPT_COOLDOWN_MS = 24 * 60 * 60 * 1000L

    fun markSeen(context: Context, broadcastId: String, nowMs: Long = System.currentTimeMillis()) {
        val normalized = broadcastId.trim().lowercase(Locale.US)
        if (normalized.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val map = readSeenMap(prefs.getString(KEY_SEEN_CACHE_JSON, null), nowMs)
        map[normalized] = nowMs
        if (map.size > MAX_SEEN_ENTRIES) {
            map.entries
                .sortedBy { it.value }
                .take(map.size - MAX_SEEN_ENTRIES)
                .forEach { (id, _) -> map.remove(id) }
        }
        prefs.edit().putString(KEY_SEEN_CACHE_JSON, writeSeenMap(map)).apply()
    }

    fun countUnseenActive(
        context: Context,
        activeBroadcastIds: List<String>,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        if (activeBroadcastIds.isEmpty()) return 0
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val map = readSeenMap(prefs.getString(KEY_SEEN_CACHE_JSON, null), nowMs)
        return activeBroadcastIds.count { id ->
            val normalized = id.trim().lowercase(Locale.US)
            normalized.isNotEmpty() && !map.containsKey(normalized)
        }
    }

    fun recordRecoveryResult(context: Context, unseenCount: Int) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_MISSED_STREAK, 0)
        val next = when {
            unseenCount > 0 -> (current + 1).coerceAtMost(50)
            current > 0 -> current - 1
            else -> 0
        }
        prefs.edit().putInt(KEY_MISSED_STREAK, next).apply()
    }

    fun shouldShowBatteryGuidance(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val streak = prefs.getInt(KEY_MISSED_STREAK, 0)
        if (streak < GUIDANCE_STREAK_THRESHOLD) return false
        val lastPromptAt = prefs.getLong(KEY_LAST_GUIDANCE_PROMPT_MS, 0L)
        return nowMs - lastPromptAt >= GUIDANCE_PROMPT_COOLDOWN_MS
    }

    fun markBatteryGuidanceShown(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_GUIDANCE_PROMPT_MS, nowMs)
            .putInt(KEY_MISSED_STREAK, 0)
            .apply()
    }

    private fun readSeenMap(raw: String?, nowMs: Long): MutableMap<String, Long> {
        val map = mutableMapOf<String, Long>()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val json = JSONObject(raw)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val ts = json.optLong(key, 0L)
                    if (ts > 0L && nowMs - ts <= SEEN_TTL_MS) {
                        map[key] = ts
                    }
                }
            }
        }
        return map
    }

    private fun writeSeenMap(map: Map<String, Long>): String {
        val json = JSONObject()
        map.entries
            .sortedByDescending { it.value }
            .forEach { (id, ts) ->
                json.put(id.lowercase(Locale.US), ts)
            }
        return json.toString()
    }
}
