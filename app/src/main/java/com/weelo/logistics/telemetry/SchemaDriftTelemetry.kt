package com.weelo.logistics.telemetry

import android.util.Log
import com.weelo.logistics.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * F-C-84 — SchemaDriftTelemetry
 * -----------------------------
 * Single surface every "else -> UNKNOWN" enum branch calls when it can't map a
 * backend value onto a typed captain-side enum branch. The helper is the
 * observability contract declared in INDEX.md:
 *
 *   Counter: schema_drift_total{enum, value}    — see INDEX.md §F-C-84
 *
 * Goals (per SOL-8 §F-C-84):
 *   1. **Log + metric every drift event**. Today the UNKNOWN else-branch in
 *      `VehicleRepository.kt:521-528` and `Broadcast.kt:703-714` is silent —
 *      a backend rolling out `suspended` or `partial_delivery` goes invisible
 *      until a user-visible bug surfaces. This violates CLAUDE.md §"Status
 *      Changes Must Propagate" and the observability-first rule.
 *   2. **Dedupe within a 60s window per `{enum, rawValue}` key** so Sentry
 *      and the metrics endpoint aren't flooded by a single bad dispatch burst.
 *   3. **Graceful degradation on OFF**. When `BuildConfig
 *      .FF_CUSTOMER_ENUM_CONTRACT_STRICT` is `false` (the default — NO-DEPLOY
 *      constraint), record() is shadow-mode: metric + log only, zero
 *      behavioural change to the caller. When flipped to `true`, strict
 *      parsers can inspect [strictMode] and graduate (future — e.g. fail the
 *      parse or route through an alternative contract path) without changing
 *      any call site.
 *
 * Thread-safety: a `ConcurrentHashMap` backs the dedupe cache so producers on
 * the main thread (compose recomposition) and IO thread (Retrofit response
 * converter) can record() concurrently. Stale entries are purged opportunistically
 * on each record() call so we avoid a background GC timer.
 *
 * Depends on: F-C-78 meta (enum canonicalization), F-C-67 (BroadcastTelemetry
 * surface — once that ships we'll forward to Sentry + Prometheus here instead
 * of plain Log.w — tracked as a follow-up to keep this PR minimal-invasive).
 */
object SchemaDriftTelemetry {

    /** Metric name emitted on drift. Matches INDEX.md Counters table. */
    const val METRIC_NAME: String = "schema_drift_total"

    /** Dedupe window. SOL-8 §F-C-84 canonical value. */
    private val DEDUPE_WINDOW_MS: Long = TimeUnit.SECONDS.toMillis(60)

    private const val TAG = "SchemaDrift"

    /**
     * Strict mode is ON when the build has opted into the canonical enum
     * contract. Call sites currently only observe — future parsers can branch
     * on [strictMode] to graduate to hard failure without a new flag.
     *
     * NO-DEPLOY: default OFF via `BuildConfig.FF_CUSTOMER_ENUM_CONTRACT_STRICT`.
     */
    @JvmStatic
    val strictMode: Boolean
        get() = BuildConfig.FF_CUSTOMER_ENUM_CONTRACT_STRICT

    /**
     * Dedupe cache: `{enum}|{rawValue}` → last-emit epoch-ms. Replaced on
     * every emit, evicted when a stale key is re-observed past
     * [DEDUPE_WINDOW_MS]. ConcurrentHashMap so we don't need explicit locking.
     */
    private val dedupeCache: MutableMap<String, Long> = ConcurrentHashMap()

    /**
     * Record a drift event for `enumName` observed with `rawValue`.
     *
     * Call from every `else -> UNKNOWN` branch in the parse pipeline. Returns
     * `true` if the event was emitted (first observation in window) or
     * `false` if deduped (identical `{enum, rawValue}` already logged within
     * [DEDUPE_WINDOW_MS]).
     *
     * @param enumName name of the Kotlin enum that couldn't map (e.g.
     *                 `"VehicleStatus"`, `"AssignmentStatus"`, `"HoldPhase"`)
     * @param rawValue the raw backend value that failed to map, or `null` if
     *                 the JSON field was absent
     * @param source   optional call-site hint (e.g. `"VehicleRepository"`)
     *                 used for easier triage in Sentry breadcrumbs — defaults
     *                 to `null`
     */
    @JvmStatic
    @JvmOverloads
    fun record(
        enumName: String,
        rawValue: String?,
        source: String? = null,
        clockMs: Long = System.currentTimeMillis()
    ): Boolean {
        val normalized = rawValue ?: "<null>"
        val key = "$enumName|$normalized"
        val previous = dedupeCache[key]
        if (previous != null && clockMs - previous < DEDUPE_WINDOW_MS) {
            // Deduped — skip Log + metric emission this cycle.
            return false
        }
        dedupeCache[key] = clockMs
        // Opportunistic eviction — clear entries older than 5 windows to cap
        // memory at O(distinct drift keys within 5 min) without a timer.
        pruneStale(clockMs)

        val suffix = if (source != null) " source=$source" else ""
        Log.w(
            TAG,
            "$METRIC_NAME{enum=$enumName, value=$normalized}$suffix strict=$strictMode"
        )
        return true
    }

    private fun pruneStale(clockMs: Long) {
        val cutoff = clockMs - (DEDUPE_WINDOW_MS * 5)
        // Iterator.remove is supported on ConcurrentHashMap entrySet.
        val iterator = dedupeCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < cutoff) iterator.remove()
        }
    }

    /**
     * Test-only: reset the dedupe cache so assertions can verify dedupe
     * behaviour deterministically. `@VisibleForTesting` annotation intentionally
     * avoided — it pulls in a compile-time dep this module does not carry.
     */
    @JvmStatic
    fun resetForTest() {
        dedupeCache.clear()
    }
}
