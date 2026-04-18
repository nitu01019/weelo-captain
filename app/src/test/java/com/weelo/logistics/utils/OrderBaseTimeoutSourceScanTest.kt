package com.weelo.logistics.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-81 — ORDER_BASE_TIMEOUT_SECONDS centralization (SOL-8 §F-C-81)
 * =============================================================================
 *
 * Captain has several hardcoded 120-second timeout values for order lifecycle.
 * Backend canonical env var is `ORDER_BASE_TIMEOUT_SECONDS=120`. We centralize
 * to a single `BuildConfig.ORDER_BASE_TIMEOUT_SECONDS` so the fallback never
 * drifts from server config (same pattern as `DRIVER_ACCEPT_TIMEOUT_SECONDS`
 * introduced under F-C-77).
 *
 * Verifies at source level:
 *   1. `app/build.gradle.kts` declares `buildConfigField("int", "ORDER_BASE_TIMEOUT_SECONDS", "120")`.
 *   2. Default matches backend env (`120`).
 *   3. The 3 production hardcode sites read from `BuildConfig.ORDER_BASE_TIMEOUT_SECONDS`:
 *      - `BroadcastOverlayScreen.kt` — `?: 120` fallback for total-time (timer ring).
 *      - `BroadcastListScreen.kt` — `(System.currentTimeMillis() + 120_000L)` expiry fallback.
 *      - `BroadcastSoundService.kt` — `handler.postDelayed(autoStopRunnable, 120_000L)` auto-stop.
 *
 * Per INDEX.md §F-C-81 / SOL-8 §F-C-81 (captain-only). Additive, zero
 * functional change — the numeric default still 120s; only source of truth changes.
 * =============================================================================
 */
class OrderBaseTimeoutSourceScanTest {

    companion object {
        private const val APP_SRC = "/private/tmp/weelo-p10-t4/app/src/main/java/com/weelo/logistics"
        private val buildGradle = File("/private/tmp/weelo-p10-t4/app/build.gradle.kts")
        private val overlayScreen = File("$APP_SRC/broadcast/BroadcastOverlayScreen.kt")
        private val listScreen = File("$APP_SRC/ui/transporter/BroadcastListScreen.kt")
        private val soundService = File("$APP_SRC/core/notification/BroadcastSoundService.kt")
    }

    @Test
    fun `ORDER_BASE_TIMEOUT_SECONDS buildConfigField declared in app build_gradle`() {
        val src = buildGradle.readText()
        assertTrue(
            "F-C-81 requires buildConfigField named ORDER_BASE_TIMEOUT_SECONDS.",
            src.contains("ORDER_BASE_TIMEOUT_SECONDS")
        )
        assertTrue(
            "Must be declared as `buildConfigField(\"int\", \"ORDER_BASE_TIMEOUT_SECONDS\", ...)`.",
            src.contains("buildConfigField(\"int\", \"ORDER_BASE_TIMEOUT_SECONDS\"")
        )
    }

    @Test
    fun `ORDER_BASE_TIMEOUT_SECONDS default matches backend env 120`() {
        val src = buildGradle.readText()
        val flagLine = src.lines().firstOrNull {
            it.contains("buildConfigField(\"int\", \"ORDER_BASE_TIMEOUT_SECONDS\"")
        } ?: error("ORDER_BASE_TIMEOUT_SECONDS buildConfigField not found")
        assertTrue(
            "Default must be \"120\" to match backend env var. Got: $flagLine",
            flagLine.contains("\"120\"")
        )
    }

    @Test
    fun `BroadcastOverlayScreen fallback reads from BuildConfig`() {
        val src = overlayScreen.readText()
        assertTrue(
            "F-C-81 — total-time fallback must reference BuildConfig.ORDER_BASE_TIMEOUT_SECONDS " +
                "instead of a magic literal 120.",
            src.contains("BuildConfig.ORDER_BASE_TIMEOUT_SECONDS")
        )
    }

    @Test
    fun `BroadcastListScreen expiry fallback reads from BuildConfig`() {
        val src = listScreen.readText()
        assertTrue(
            "F-C-81 — card deadline fallback must reference BuildConfig.ORDER_BASE_TIMEOUT_SECONDS " +
                "instead of the magic literal 120_000L.",
            src.contains("BuildConfig.ORDER_BASE_TIMEOUT_SECONDS")
        )
    }

    @Test
    fun `BroadcastSoundService auto-stop reads from BuildConfig`() {
        val src = soundService.readText()
        assertTrue(
            "F-C-81 — looping-sound auto-stop must reference BuildConfig.ORDER_BASE_TIMEOUT_SECONDS " +
                "instead of the magic literal 120_000L.",
            src.contains("BuildConfig.ORDER_BASE_TIMEOUT_SECONDS")
        )
    }
}
