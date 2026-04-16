package com.weelo.logistics.broadcast

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BroadcastFullScreenNotifier] — F-C-01.
 *
 * Scope: verifies the pure decision function
 * [BroadcastFullScreenNotifier.shouldUseFullScreenIntent] that encapsulates
 * the Android 14+ canUseFullScreenIntent() runtime gate.
 *
 * Why a pure function?
 *   This project's unit test config (app/build.gradle.kts) does not enable
 *   Robolectric or `testOptions { unitTests.isReturnDefaultValues = true }`
 *   — building a real [android.app.Notification] in JVM-only tests throws
 *   "Method not mocked." Exercising OS-level full-screen-intent dispatch
 *   therefore requires an instrumented test on an API 34 device, which is
 *   deferred to manual QA per the F-C-01 fix plan.
 *
 *   The decision logic (sdkInt-based gate + canUseFsi supplier) is the
 *   only branch that can diverge in production code — verifying it in
 *   isolation gives full code-level coverage of the F-C-01 fix.
 *
 * See:
 *   https://developer.android.com/about/versions/14/behavior-changes-14
 *   https://source.android.com/docs/core/permissions/fsi-limits
 */
class BroadcastFullScreenNotifierTest {

    // =========================================================================
    // 1. Pre-Android-14 behaviour — gate is always open
    // =========================================================================

    @Test
    fun `returns true on API 33 (Android 13) regardless of canUseFsi supplier`() {
        val result = BroadcastFullScreenNotifier.shouldUseFullScreenIntent(
            sdkInt = Build.VERSION_CODES.TIRAMISU, // 33
            canUseFsiFn = { false } // supplier SHOULD NOT be called
        )
        assertTrue(
            "API 33 must keep pre-Android-14 behaviour (FSI always granted)",
            result
        )
    }

    @Test
    fun `returns true on API 24 (min supported) without invoking supplier`() {
        var supplierInvoked = false
        val result = BroadcastFullScreenNotifier.shouldUseFullScreenIntent(
            sdkInt = Build.VERSION_CODES.N, // 24, minSdk
            canUseFsiFn = {
                supplierInvoked = true
                false
            }
        )
        assertTrue("API 24 must default-grant FSI", result)
        assertFalse(
            "canUseFsi supplier MUST NOT be invoked on pre-Android-14",
            supplierInvoked
        )
    }

    // =========================================================================
    // 2. Android 14+ behaviour — gate honours the canUseFsi supplier
    // =========================================================================

    @Test
    fun `returns true on API 34 when canUseFsi supplier returns true (permission granted)`() {
        val result = BroadcastFullScreenNotifier.shouldUseFullScreenIntent(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, // 34
            canUseFsiFn = { true }
        )
        assertTrue(
            "API 34 with USE_FULL_SCREEN_INTENT granted must allow FSI",
            result
        )
    }

    @Test
    fun `returns false on API 34 when canUseFsi supplier returns false (permission denied)`() {
        val result = BroadcastFullScreenNotifier.shouldUseFullScreenIntent(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, // 34
            canUseFsiFn = { false }
        )
        assertFalse(
            "API 34 with USE_FULL_SCREEN_INTENT denied MUST deny FSI (Google Play mandate)",
            result
        )
    }

    @Test
    fun `returns false on API 35 (Android 15) when canUseFsi supplier returns false`() {
        // Covers future-OS case: Android 15 inherits the FSI restriction.
        val result = BroadcastFullScreenNotifier.shouldUseFullScreenIntent(
            sdkInt = 35,
            canUseFsiFn = { false }
        )
        assertFalse(
            "API 35+ must continue to honour runtime FSI gate",
            result
        )
    }

    // =========================================================================
    // 3. Supplier is invoked exactly once (no accidental double-check)
    // =========================================================================

    @Test
    fun `canUseFsi supplier is invoked exactly once on API 34`() {
        var invocationCount = 0
        BroadcastFullScreenNotifier.shouldUseFullScreenIntent(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            canUseFsiFn = {
                invocationCount++
                true
            }
        )
        assertEquals(
            "Supplier must be evaluated exactly once per check",
            1,
            invocationCount
        )
    }
}
