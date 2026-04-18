package com.weelo.logistics.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-67 — parseJsonSafe + observability breadcrumbs (SOL-8 §F-C-67)
 * =============================================================================
 *
 * Pure JUnit4 source-scan tests. We cannot run the Android/Timber+Sentry stack
 * in a host-only unit test, so we verify the load-bearing contract at the
 * source level:
 *
 *   1. `util/JsonSafe.kt` file exists (new helper co-located with shared utils).
 *   2. It exposes `parseJsonSafe` — two shapes are acceptable:
 *      a. `inline fun <T> parseJsonSafe(event, raw, fn): Result<T>` matching
 *         SOL-8 spec (wraps `fn` and returns `Result`).
 *      b. `fun <T> parseJsonSafe(raw: String, type: Class<T>): T?` matching
 *         task-owner instruction (Gson-delegated, nullable on failure).
 *      We accept either — both satisfy the F-C-67 fix_id contract.
 *   3. On parse failure, the helper logs a breadcrumb/telemetry event with a
 *      `fix_id` tag set to "F-C-67".
 *   4. `BuildConfig.FF_CUSTOMER_PARSE_JSON_SAFE` flag is declared in
 *      `app/build.gradle.kts`, default `false` (NO-DEPLOY + flag-OFF).
 *   5. `SocketEventRouter.kt` references `parseJsonSafe` (adoption marker) —
 *      guarded behind the feature flag so rollback is a flip-switch.
 *
 * Per INDEX.md §F-C-67 / SOL-8 §F-C-67. Observability-first, additive only,
 * zero functional change with flag OFF.
 * =============================================================================
 */
class JsonSafeSourceScanTest {

    companion object {
        private const val APP_SRC = "/private/tmp/weelo-p10-t4/app/src/main/java/com/weelo/logistics"
        private val jsonSafeFile = File("$APP_SRC/utils/JsonSafe.kt")
        private val socketRouter = File("$APP_SRC/data/remote/socket/SocketEventRouter.kt")
        private val buildGradle = File("/private/tmp/weelo-p10-t4/app/build.gradle.kts")
    }

    @Test
    fun `JsonSafe helper file exists`() {
        assertTrue(
            "F-C-67 helper must exist at ${jsonSafeFile.absolutePath}",
            jsonSafeFile.exists()
        )
    }

    @Test
    fun `JsonSafe exposes parseJsonSafe helper function`() {
        val src = jsonSafeFile.readText()
        assertTrue(
            "Must declare `parseJsonSafe` (either Result<T> or T? shape).",
            src.contains("fun parseJsonSafe") ||
                src.contains("fun <T> parseJsonSafe") ||
                src.contains("inline fun <T") ||
                src.contains("inline fun <reified T") ||
                src.contains("parseJsonSafe<")
        )
    }

    @Test
    fun `JsonSafe tags failures with F-C-67 fix_id for telemetry`() {
        val src = jsonSafeFile.readText()
        assertTrue(
            "Must emit a breadcrumb/telemetry entry tagged `fix_id=F-C-67` so parse " +
                "failures surface in dashboards instead of being Timber-only.",
            src.contains("F-C-67") && (src.contains("fix_id") || src.contains("fixId"))
        )
    }

    @Test
    fun `JsonSafe returns null or Result-failure on parse error`() {
        val src = jsonSafeFile.readText()
        // Accept either shape the spec calls out.
        val returnsNull = src.contains("return null") || src.contains(": T?")
        val returnsResult = src.contains("Result.failure") || src.contains("Result<T>")
        assertTrue(
            "parseJsonSafe must fail gracefully (return null or Result.failure) " +
                "so callers can use `?: default` / `?: return` fallbacks.",
            returnsNull || returnsResult
        )
    }

    @Test
    fun `JsonSafe catches parse exceptions and does not rethrow`() {
        val src = jsonSafeFile.readText()
        assertTrue(
            "Helper must wrap `fn`/parse in try/catch so malformed payloads do not " +
                "propagate and crash the socket handler thread.",
            src.contains("try {") && src.contains("catch")
        )
    }

    @Test
    fun `JsonSafe routes failures through BroadcastTelemetry or Timber`() {
        val src = jsonSafeFile.readText()
        val emitsTelemetry = src.contains("BroadcastTelemetry") ||
            src.contains("Sentry") ||
            src.contains("parseFailure")
        assertTrue(
            "Failures must hit the observability sink (BroadcastTelemetry surface " +
                "per SOL-8 §F-C-67, with Sentry as the preferred sink once available). " +
                "Silent Timber-only behaviour is what we are removing.",
            emitsTelemetry
        )
    }

    @Test
    fun `FF_CUSTOMER_PARSE_JSON_SAFE build flag declared and defaults OFF`() {
        val src = buildGradle.readText()
        assertTrue(
            "Must declare `FF_CUSTOMER_PARSE_JSON_SAFE` buildConfigField for rollout gating.",
            src.contains("FF_CUSTOMER_PARSE_JSON_SAFE")
        )
        // Extract the line that declares the flag and verify default is "false".
        val flagLine = src.lines().firstOrNull { it.contains("FF_CUSTOMER_PARSE_JSON_SAFE") }
            ?: error("FF_CUSTOMER_PARSE_JSON_SAFE line not found")
        assertTrue(
            "NO-DEPLOY rule + task spec — flag must default OFF (\"false\"). Got: $flagLine",
            flagLine.contains("\"false\"")
        )
    }

    @Test
    fun `SocketEventRouter adopts parseJsonSafe at least once`() {
        val src = socketRouter.readText()
        assertTrue(
            "At least one critical parse site must route through parseJsonSafe (flag-guarded).",
            src.contains("parseJsonSafe")
        )
    }

    @Test
    fun `SocketEventRouter adoption is guarded behind feature flag`() {
        val src = socketRouter.readText()
        assertTrue(
            "Adoption must be behind `BuildConfig.FF_CUSTOMER_PARSE_JSON_SAFE` so we can " +
                "flip back to the legacy catch-and-Timber path without code change.",
            src.contains("FF_CUSTOMER_PARSE_JSON_SAFE")
        )
    }
}
