package com.weelo.logistics.utils

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import org.json.JSONObject

/**
 * =============================================================================
 * F-C-67 — Safe JSON parsing with observability breadcrumbs
 * =============================================================================
 *
 * The captain app's current socket/broadcast parse sites swallow exceptions
 * with `catch (e: Exception) { Timber.e(e, ...) }`. That gives us no metric,
 * no Sentry breadcrumb, and no way to tell a malformed-payload storm apart
 * from a transient hiccup (see INDEX.md §F-C-67 — 19 such catches in
 * `SocketEventRouter.kt` alone).
 *
 * This helper replaces those silent swallows with a typed, observable API:
 *   - Callers get a `null` (for the `String -> type` shape) or
 *     `Result.failure` (for the `JSONObject -> T` shape) and can fall back
 *     gracefully via `?: default` / `?: return`.
 *   - Every failure is tagged `fix_id = F-C-67` in the breadcrumb so the
 *     observability pipeline can correlate parse-failure storms to this fix.
 *   - Breadcrumbs flow through the existing [BroadcastTelemetry] sink (the
 *     same surface F-C-84 / F-C-78 will use for schema-drift reporting). If
 *     a Sentry SDK is later added, only the sink implementation needs to
 *     change — call sites stay identical.
 *
 * The `FF_CUSTOMER_PARSE_JSON_SAFE` build flag lives in the consumer; this
 * helper is always-callable so tests can exercise it independently of the
 * app's runtime flag state.
 *
 * Spec: INDEX.md §F-C-67 + SOL-8 §F-C-67.
 * =============================================================================
 */
object JsonSafe {

    const val FIX_ID: String = "F-C-67"

    /**
     * Parse [raw] JSON string into [type] via Gson.
     *
     * @return parsed instance or `null` if [raw] is null/blank or malformed.
     *   Callers use `?: default` or `?: return` to handle the nullable path.
     */
    fun <T> parseJsonSafe(raw: String?, type: Class<T>): T? {
        if (raw.isNullOrBlank()) {
            emitParseFailure(
                event = type.simpleName ?: "unknown",
                reason = "blank_or_null_raw",
                rawSize = 0
            )
            return null
        }
        return try {
            Gson().fromJson(raw, type)
        } catch (e: JsonSyntaxException) {
            emitParseFailure(
                event = type.simpleName ?: "unknown",
                reason = "json_syntax",
                rawSize = raw.length,
                error = e
            )
            null
        } catch (e: Exception) {
            emitParseFailure(
                event = type.simpleName ?: "unknown",
                reason = "unexpected",
                rawSize = raw.length,
                error = e
            )
            null
        }
    }

    /**
     * Parse an arbitrary [raw] payload (typically a Socket.IO argument) into a
     * typed notification via [fn]. Matches the SOL-8 §F-C-67 Result<T> shape
     * so future consumers can chain/fold without an extra null-check hop.
     *
     * @return [Result.success] on parse OK, [Result.failure] otherwise. The
     *   failure path emits a breadcrumb tagged `fix_id=F-C-67` before returning.
     */
    inline fun <T> parseJsonSafe(
        event: String,
        raw: Any?,
        crossinline fn: (JSONObject) -> T
    ): Result<T> {
        val json = raw as? JSONObject
        if (json == null) {
            emitParseFailure(
                event = event,
                reason = "not_json_object",
                rawSize = raw?.toString()?.length ?: 0
            )
            return Result.failure(IllegalArgumentException("not JSONObject: $raw"))
        }
        return try {
            Result.success(fn(json))
        } catch (e: Exception) {
            emitParseFailure(
                event = event,
                reason = "parse_exception",
                rawSize = json.length(),
                error = e
            )
            Result.failure(e)
        }
    }

    /**
     * Emit a structured breadcrumb/telemetry entry tagged with [FIX_ID] so
     * failures are correlatable to the F-C-67 fix in observability dashboards.
     * Public so the inline overload above can reach it from call-site bytecode.
     */
    @PublishedApi
    internal fun emitParseFailure(
        event: String,
        reason: String,
        rawSize: Int,
        error: Throwable? = null
    ) {
        val attrs = mutableMapOf(
            "fix_id" to FIX_ID,
            "event" to event,
            "reason" to reason,
            "rawSize" to rawSize.toString()
        )
        error?.javaClass?.simpleName?.let { attrs["exception"] = it }
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_PARSED,
            status = BroadcastStatus.FAILED,
            reason = reason,
            attrs = attrs
        )
        if (error != null) {
            timber.log.Timber.w(error, "parseJsonSafe failure event=%s reason=%s fix_id=%s", event, reason, FIX_ID)
        } else {
            timber.log.Timber.w("parseJsonSafe failure event=%s reason=%s fix_id=%s", event, reason, FIX_ID)
        }
    }
}

/**
 * Convenience top-level aliases — keep call sites concise and let Kotlin's
 * `import com.weelo.logistics.utils.parseJsonSafe` pattern work naturally.
 */

fun <T> parseJsonSafe(raw: String?, type: Class<T>): T? =
    JsonSafe.parseJsonSafe(raw, type)

inline fun <T> parseJsonSafe(
    event: String,
    raw: Any?,
    crossinline fn: (JSONObject) -> T
): Result<T> = JsonSafe.parseJsonSafe(event, raw, fn)
