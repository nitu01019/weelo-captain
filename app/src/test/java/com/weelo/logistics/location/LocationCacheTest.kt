package com.weelo.logistics.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-16 / W5 — LocationCache + async enrichment tests
 * =============================================================================
 *
 * Source-scan assertions (same pattern as HoldFallbackRemovedTest,
 * BroadcastListScreenTimerTest). Pre-existing Wave-0 compiler errors block the
 * Robolectric flavor of this test (SOL-7 §S-16 sketches it), so we lock in
 * the contract via source scans — captain-app verification approach per
 * CLAUDE.md.
 *
 * What these tests lock in (RED until the fix lands):
 *   - New file: location/LocationCache.kt
 *   - Is an app-scoped singleton (object) with suspend `getFreshLocation(...)`
 *   - Uses `FusedLocationProviderClient` (NOT `LocationManager.getLastKnownLocation`)
 *   - Has a 30-second TTL via AtomicReference and SystemClock.elapsedRealtime()
 *   - Checks ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION at runtime
 *   - Catches SecurityException distinctly from generic Exception
 *   - BroadcastBoundedStores.enrichWithLocalPickupDistance is suspend OR
 *     gated behind BuildConfig.FF_BROADCAST_FLP_LOCATION so legacy path
 *     (LocationManager) still runs when flag is OFF
 *   - BroadcastBoundedStores references LocationCache (when flag ON)
 *   - BuildConfig field FF_BROADCAST_FLP_LOCATION (default false)
 *   - kotlinx-coroutines-play-services dep present (verify it isn't removed)
 * =============================================================================
 */
class LocationCacheTest {

    private val cacheFile = File(
        "src/main/java/com/weelo/logistics/location/LocationCache.kt"
    )
    private val storesFile = File(
        "src/main/java/com/weelo/logistics/broadcast/BroadcastBoundedStores.kt"
    )
    private val buildGradleFile = File("build.gradle.kts")

    private val cacheSource: String by lazy {
        require(cacheFile.exists()) {
            "LocationCache.kt not found at ${cacheFile.absolutePath}. Test must run with cwd=app/."
        }
        cacheFile.readText()
    }

    private val storesSource: String by lazy {
        require(storesFile.exists()) {
            "BroadcastBoundedStores.kt not found at ${storesFile.absolutePath}. Test must run with cwd=app/."
        }
        storesFile.readText()
    }

    private val buildGradleSource: String by lazy {
        require(buildGradleFile.exists()) {
            "build.gradle.kts not found at ${buildGradleFile.absolutePath}. Test must run with cwd=app/."
        }
        buildGradleFile.readText()
    }

    // ------------------------------------------------------------------
    // (1) LocationCache file must exist and be in the right package
    // ------------------------------------------------------------------

    @Test
    fun `LocationCache kt file exists in location package`() {
        assertTrue(
            "LocationCache.kt must exist at location/LocationCache.kt (F-C-16)",
            cacheFile.exists()
        )
    }

    @Test
    fun `LocationCache declares the location package`() {
        assertTrue(
            "LocationCache must be in package com.weelo.logistics.location",
            cacheSource.contains("package com.weelo.logistics.location")
        )
    }

    // ------------------------------------------------------------------
    // (2) Must be a singleton with suspend getFreshLocation
    // ------------------------------------------------------------------

    @Test
    fun `LocationCache is a kotlin object singleton`() {
        val isObject = Regex("""object\s+LocationCache\b""").containsMatchIn(cacheSource)
        assertTrue(
            "LocationCache must be declared as `object LocationCache` (app-scoped singleton)",
            isObject
        )
    }

    @Test
    fun `LocationCache exposes suspend getFreshLocation`() {
        // Must be suspend — callers (enrichWithLocalPickupDistance) become
        // suspend so the socket thread never blocks on GPS IPC.
        val suspendFun = Regex(
            """suspend\s+fun\s+getFreshLocation\s*\("""
        ).containsMatchIn(cacheSource)
        assertTrue(
            "LocationCache must expose `suspend fun getFreshLocation(...)` (non-blocking FLP call)",
            suspendFun
        )
    }

    // ------------------------------------------------------------------
    // (3) Must use FusedLocationProviderClient (NOT LocationManager)
    // ------------------------------------------------------------------

    @Test
    fun `LocationCache uses FusedLocationProviderClient`() {
        // The whole point of F-C-16: replace blocking LocationManager with
        // FusedLocationProviderClient.lastLocation.await().
        assertTrue(
            "LocationCache must import/use FusedLocationProviderClient (SOL-7 §S-16)",
            cacheSource.contains("FusedLocationProviderClient") ||
                cacheSource.contains("getFusedLocationProviderClient")
        )
        assertTrue(
            "LocationCache must call FusedLocationProviderClient.lastLocation",
            cacheSource.contains(".lastLocation")
        )
    }

    @Test
    fun `LocationCache calls await on the FLP task`() {
        // `.await()` (kotlinx-coroutines-play-services) is what makes the
        // call non-blocking + cancellation-safe.
        assertTrue(
            "LocationCache must use Task.await() from kotlinx-coroutines-play-services",
            cacheSource.contains(".await(")  || cacheSource.contains(".await()")
        )
    }

    @Test
    fun `LocationCache does not use LocationManager getLastKnownLocation`() {
        // The blocking IPC path must not be used inside the cache. Any live
        // line calling `getLastKnownLocation(` inside LocationCache.kt is a
        // regression (it'd defeat the purpose of the fix).
        val live = cacheSource.lines().any { line ->
            val t = line.trim()
            val isComment = t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
            !isComment && line.contains("getLastKnownLocation(")
        }
        assertFalse(
            "LocationCache must not call LocationManager.getLastKnownLocation (blocking)",
            live
        )
    }

    // ------------------------------------------------------------------
    // (4) 30-second TTL memoization via AtomicReference + elapsedRealtime
    // ------------------------------------------------------------------

    @Test
    fun `LocationCache uses AtomicReference for the memo cell`() {
        // The spec calls for an `AtomicReference<LastKnown?>` — lock-free memo
        // so concurrent socket-thread upserts don't contend.
        assertTrue(
            "LocationCache must use AtomicReference for thread-safe memoization",
            cacheSource.contains("AtomicReference")
        )
    }

    @Test
    fun `LocationCache uses SystemClock elapsedRealtime for TTL checks`() {
        // Must use elapsedRealtime — monotonic clock that continues ticking
        // during doze. NTP/wall-clock jumps would otherwise corrupt the TTL.
        assertTrue(
            "LocationCache must use SystemClock.elapsedRealtime() for TTL reads",
            cacheSource.contains("SystemClock.elapsedRealtime()")
        )
    }

    @Test
    fun `LocationCache encodes a 30-second TTL literal`() {
        // The exact value is part of the contract (cache hits within the first
        // 30 seconds, misses after). Any of `30_000`, `30000`, or `30L * 1000`
        // is acceptable so long as it resolves to 30s.
        val ttlPattern = Regex(
            """(?:\b30_000\b|\b30000\b|30L?\s*\*\s*1_?000)"""
        )
        assertTrue(
            "LocationCache must encode a 30-second TTL literal (30_000ms)",
            ttlPattern.containsMatchIn(cacheSource)
        )
    }

    // ------------------------------------------------------------------
    // (5) Runtime permission check + distinct SecurityException handling
    // ------------------------------------------------------------------

    @Test
    fun `LocationCache explicitly checks ACCESS_FINE or ACCESS_COARSE permission`() {
        val hasFine = cacheSource.contains("ACCESS_FINE_LOCATION")
        val hasCoarse = cacheSource.contains("ACCESS_COARSE_LOCATION")
        assertTrue(
            "LocationCache must explicitly check ACCESS_FINE_LOCATION AND ACCESS_COARSE_LOCATION " +
                "(no @Suppress MissingPermission bypass)",
            hasFine && hasCoarse
        )
        val hasCheck = cacheSource.contains("checkSelfPermission") ||
            cacheSource.contains("PermissionChecker")
        assertTrue(
            "LocationCache must call checkSelfPermission / PermissionChecker",
            hasCheck
        )
    }

    @Test
    fun `LocationCache catches SecurityException distinctly from generic Exception`() {
        // Generic catch silently swallows SecurityException. The spec requires
        // the two catches to be separate so telemetry can attribute permission
        // revocation as its own failure mode.
        val catchSecurity = Regex("""catch\s*\(\s*\w+\s*:\s*SecurityException\s*\)""")
            .containsMatchIn(cacheSource)
        assertTrue(
            "LocationCache must have a dedicated catch (x: SecurityException) branch",
            catchSecurity
        )
    }

    // ------------------------------------------------------------------
    // (6) BroadcastBoundedStores integration
    // ------------------------------------------------------------------

    @Test
    fun `BroadcastBoundedStores references LocationCache`() {
        assertTrue(
            "BroadcastBoundedStores must reference LocationCache (when FF_BROADCAST_FLP_LOCATION is ON)",
            storesSource.contains("LocationCache") ||
                storesSource.contains("com.weelo.logistics.location.LocationCache")
        )
    }

    @Test
    fun `BroadcastBoundedStores wraps enrichment behind FF_BROADCAST_FLP_LOCATION`() {
        // Legacy LocationManager path must stay reachable under the `else`
        // branch so rollback is a flag flip, not a revert.
        assertTrue(
            "BroadcastBoundedStores must reference BuildConfig.FF_BROADCAST_FLP_LOCATION to gate the FLP path",
            storesSource.contains("FF_BROADCAST_FLP_LOCATION")
        )
    }

    @Test
    fun `enrichWithLocalPickupDistance becomes suspend when FLP path is wired`() {
        // When the FLP path is taken, the enrichment must become suspend so
        // await() on the FLP Task doesn't block the caller. Accept either a
        // suspend `enrichWithLocalPickupDistance` OR a helper that wraps the
        // suspend call behind a non-suspend facade.
        val suspendEnrich = Regex(
            """suspend\s+fun\s+enrichWithLocalPickupDistance\s*\("""
        ).containsMatchIn(storesSource)
        val suspendHelper = Regex(
            """suspend\s+fun\s+enrichWithLocalPickupDistance\w*\s*\("""
        ).containsMatchIn(storesSource)
        val runBlockingBridge = storesSource.contains("runBlocking") // acceptable escape hatch
        assertTrue(
            "BroadcastBoundedStores must surface a suspend enrichment path or a runBlocking bridge " +
                "when FF_BROADCAST_FLP_LOCATION is ON",
            suspendEnrich || suspendHelper || runBlockingBridge
        )
    }

    // ------------------------------------------------------------------
    // (7) Feature flag + dependency registration in build gradle
    // ------------------------------------------------------------------

    @Test
    fun `build gradle registers FF_BROADCAST_FLP_LOCATION BuildConfig field`() {
        val fieldDecl = Regex(
            """buildConfigField\s*\(\s*"boolean"\s*,\s*"FF_BROADCAST_FLP_LOCATION"\s*,\s*"(true|false)"\s*\)"""
        )
        assertTrue(
            "app/build.gradle.kts must declare a boolean BuildConfig field named FF_BROADCAST_FLP_LOCATION",
            fieldDecl.containsMatchIn(buildGradleSource)
        )
    }

    @Test
    fun `FF_BROADCAST_FLP_LOCATION defaults to false in build gradle`() {
        val defaultFalse = Regex(
            """buildConfigField\s*\(\s*"boolean"\s*,\s*"FF_BROADCAST_FLP_LOCATION"\s*,\s*"false"\s*\)"""
        )
        assertTrue(
            "FF_BROADCAST_FLP_LOCATION must default to false (Phase-9 flag policy)",
            defaultFalse.containsMatchIn(buildGradleSource)
        )
    }

    @Test
    fun `kotlinx-coroutines-play-services dependency is present`() {
        // Required for .await() on FLP's Task<Location>.
        assertTrue(
            "build.gradle.kts must keep org.jetbrains.kotlinx:kotlinx-coroutines-play-services",
            buildGradleSource.contains("kotlinx-coroutines-play-services")
        )
    }
}
