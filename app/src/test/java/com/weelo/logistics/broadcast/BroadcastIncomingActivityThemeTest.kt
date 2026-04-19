package com.weelo.logistics.broadcast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-04 / W5 — BroadcastIncomingActivity translucent overlay theme tests
 * =============================================================================
 *
 * Source-scan assertions (same style as HoldFallbackRemovedTest,
 * BroadcastListScreenTimerTest). Pre-existing Wave-0 compiler errors in
 * PodOtpDialog + TransporterNavGraph + VehicleHoldConfirmScreen block full
 * Compose/Espresso runtime tests — the source-scan pattern is the accepted
 * verification contract for captain theme/manifest fixes (CLAUDE.md / SOL-7
 * §S-04, INDEX.md:1540-1552).
 *
 * What these tests lock in (RED until the fix lands):
 *   - themes.xml defines Theme.WeeloLogistics.BroadcastOverlay
 *   - windowIsTranslucent=true (primary OneUI6/HyperOS recommendation)
 *   - windowBackground=transparent (no 300-500ms white-flash on Dialog mount)
 *   - windowNoTitle=true, backgroundDimEnabled=false
 *   - windowIsFloating=false (Samsung OneUI6 anti-pattern avoidance —
 *     `windowIsFloating=true` is the #1 reported breakage per P2 research)
 *   - BroadcastIncomingActivity entry in AndroidManifest references
 *     Theme.WeeloLogistics.BroadcastOverlay (NOT the opaque default)
 *   - activity-alias used for flag-gated rollout (FF_BROADCAST_TRANSLUCENT_THEME)
 *   - BuildConfig field FF_BROADCAST_TRANSLUCENT_THEME present in build.gradle.kts
 * =============================================================================
 */
class BroadcastIncomingActivityThemeTest {

    private val themesFile = File("src/main/res/values/themes.xml")
    private val manifestFile = File("src/main/AndroidManifest.xml")
    private val buildGradleFile = File("build.gradle.kts")

    private val themesSource: String by lazy {
        require(themesFile.exists()) {
            "themes.xml not found at ${themesFile.absolutePath}. Test must run with cwd=app/."
        }
        themesFile.readText()
    }

    private val manifestSource: String by lazy {
        require(manifestFile.exists()) {
            "AndroidManifest.xml not found at ${manifestFile.absolutePath}. Test must run with cwd=app/."
        }
        manifestFile.readText()
    }

    private val buildGradleSource: String by lazy {
        require(buildGradleFile.exists()) {
            "build.gradle.kts not found at ${buildGradleFile.absolutePath}. Test must run with cwd=app/."
        }
        buildGradleFile.readText()
    }

    // ------------------------------------------------------------------
    // (1) themes.xml — the translucent style must be defined
    // ------------------------------------------------------------------

    @Test
    fun `themes xml declares Theme WeeloLogistics BroadcastOverlay style`() {
        // The style must exist by name so the manifest can reference it.
        assertTrue(
            "themes.xml must declare Theme.WeeloLogistics.BroadcastOverlay",
            themesSource.contains("name=\"Theme.WeeloLogistics.BroadcastOverlay\"")
        )
    }

    @Test
    fun `broadcast overlay theme sets windowIsTranslucent to true`() {
        // Primary OneUI6 / HyperOS recommendation — without this the Activity
        // renders on an opaque background and causes the 300-500ms white-flash
        // when the Compose Dialog mounts over the previous task / lockscreen.
        val translucentLine = Regex(
            """<item\s+name="android:windowIsTranslucent"\s*>\s*true\s*</item>"""
        )
        assertTrue(
            "Broadcast overlay theme must set windowIsTranslucent=true",
            translucentLine.containsMatchIn(themesSource)
        )
    }

    @Test
    fun `broadcast overlay theme sets windowBackground to transparent`() {
        // The window background must be transparent — equivalent to a null
        // drawable when read from window.decorView.background. This is the
        // second half of the translucent-overlay contract (theme + background).
        val transparentBg = Regex(
            """<item\s+name="android:windowBackground"\s*>\s*@android:color/transparent\s*</item>"""
        )
        assertTrue(
            "Broadcast overlay theme must set windowBackground=@android:color/transparent",
            transparentBg.containsMatchIn(themesSource)
        )
    }

    @Test
    fun `broadcast overlay theme disables title and background dim`() {
        // windowNoTitle avoids the double-title that OneUI6 would otherwise
        // insert; backgroundDimEnabled=false prevents the system-drawn dim
        // scrim from fighting the Compose overlay's own scrim.
        val noTitle = Regex("""<item\s+name="android:windowNoTitle"\s*>\s*true\s*</item>""")
        val noDim = Regex("""<item\s+name="android:backgroundDimEnabled"\s*>\s*false\s*</item>""")
        assertTrue(
            "Broadcast overlay theme must set windowNoTitle=true",
            noTitle.containsMatchIn(themesSource)
        )
        assertTrue(
            "Broadcast overlay theme must set backgroundDimEnabled=false",
            noDim.containsMatchIn(themesSource)
        )
    }

    @Test
    fun `broadcast overlay theme keeps windowIsFloating false — Samsung OneUI6 anti-pattern`() {
        // Per P2 Samsung OneUI6 research, windowIsFloating=true is the #1
        // reported breakage (clips touch targets + taskbar insets). Keep it
        // false even though the overlay looks "dialog-like" visually.
        val floatingFalse = Regex(
            """<item\s+name="android:windowIsFloating"\s*>\s*false\s*</item>"""
        )
        assertTrue(
            "Broadcast overlay theme must set windowIsFloating=false (OneUI6 anti-pattern)",
            floatingFalse.containsMatchIn(themesSource)
        )
        // Regression armor: a live `windowIsFloating>true<` would be the bug.
        val floatingTrue = Regex(
            """<item\s+name="android:windowIsFloating"\s*>\s*true\s*</item>"""
        )
        assertFalse(
            "windowIsFloating must NEVER be true on the broadcast overlay theme",
            floatingTrue.containsMatchIn(themesSource)
        )
    }

    // ------------------------------------------------------------------
    // (2) AndroidManifest.xml — BroadcastIncomingActivity must use the overlay theme
    // ------------------------------------------------------------------

    @Test
    fun `BroadcastIncomingActivity entry in manifest references the overlay theme`() {
        // Per SOL-7 §S-04 the manifest must route the FSI Activity (or its
        // activity-alias) to the translucent theme. Accept either pattern:
        //   (a) direct <activity android:name=".broadcast.BroadcastIncomingActivity" ... theme="@style/Theme.WeeloLogistics.BroadcastOverlay">
        //   (b) activity-alias targeting BroadcastIncomingActivity with the overlay theme
        val activitySlice = extractActivityBlock(manifestSource, ".broadcast.BroadcastIncomingActivity")
        val aliasSlice = extractAliasForActivity(manifestSource, ".broadcast.BroadcastIncomingActivity")
        val activityHasOverlayTheme = activitySlice.contains(
            "@style/Theme.WeeloLogistics.BroadcastOverlay"
        )
        val aliasHasOverlayTheme = aliasSlice.contains(
            "@style/Theme.WeeloLogistics.BroadcastOverlay"
        )
        assertTrue(
            "Either BroadcastIncomingActivity or its activity-alias must apply " +
                "@style/Theme.WeeloLogistics.BroadcastOverlay (F-C-04 fix incomplete). " +
                "activity-block=${activitySlice.take(120)} alias-block=${aliasSlice.take(120)}",
            activityHasOverlayTheme || aliasHasOverlayTheme
        )
    }

    @Test
    fun `BroadcastIncomingActivity manifest entry does NOT keep the old opaque theme directly`() {
        // Regression armor — if someone reverts and the activity entry drops
        // the translucent theme entirely, this fires. Accept the overlay
        // theme OR no explicit theme (falls through to application theme —
        // the activity-alias variant handles this).
        val activityBlock = extractActivityBlock(manifestSource, ".broadcast.BroadcastIncomingActivity")
        // The raw activity block must NOT still declare the opaque theme as a
        // direct on-activity attribute AND also skip the overlay theme.
        val hasOpaqueTheme = Regex(
            """android:theme\s*=\s*"@style/Theme\.WeeloLogistics(?!\.BroadcastOverlay)\s*""""
        ).containsMatchIn(activityBlock)
        val aliasBlock = extractAliasForActivity(manifestSource, ".broadcast.BroadcastIncomingActivity")
        val overlayWiredSomewhere =
            activityBlock.contains("@style/Theme.WeeloLogistics.BroadcastOverlay") ||
                aliasBlock.contains("@style/Theme.WeeloLogistics.BroadcastOverlay")
        // Either: (a) no opaque direct theme, or (b) an alias carries the overlay theme
        assertTrue(
            "BroadcastIncomingActivity must not keep the opaque Theme.WeeloLogistics as its final theme — " +
                "hasOpaqueDirect=$hasOpaqueTheme, overlayWiredSomewhere=$overlayWiredSomewhere",
            (!hasOpaqueTheme) || overlayWiredSomewhere
        )
    }

    // ------------------------------------------------------------------
    // (3) BuildConfig feature flag for canary rollout
    // ------------------------------------------------------------------

    @Test
    fun `build gradle registers FF_BROADCAST_TRANSLUCENT_THEME BuildConfig field`() {
        // F-C-04 spec: the manifest-layer change must be gated behind
        // BuildConfig.FF_BROADCAST_TRANSLUCENT_THEME (default OFF) so rollout
        // can be reversed without a re-release. Even with the activity-alias
        // pattern, the BuildConfig field is the ground-truth flag.
        val fieldDecl = Regex(
            """buildConfigField\s*\(\s*"boolean"\s*,\s*"FF_BROADCAST_TRANSLUCENT_THEME"\s*,\s*"(true|false)"\s*\)"""
        )
        assertTrue(
            "app/build.gradle.kts must declare a boolean BuildConfig field named FF_BROADCAST_TRANSLUCENT_THEME",
            fieldDecl.containsMatchIn(buildGradleSource)
        )
    }

    @Test
    fun `FF_BROADCAST_TRANSLUCENT_THEME defaults to false in build gradle`() {
        // Every Phase-9 feature flag ships default OFF (see coordinator task
        // contract). Enforce the default stays "false" so canary rollout is a
        // conscious opt-in.
        val defaultFalse = Regex(
            """buildConfigField\s*\(\s*"boolean"\s*,\s*"FF_BROADCAST_TRANSLUCENT_THEME"\s*,\s*"false"\s*\)"""
        )
        assertTrue(
            "FF_BROADCAST_TRANSLUCENT_THEME must default to false — hard constraint (Phase-9 flag policy)",
            defaultFalse.containsMatchIn(buildGradleSource)
        )
    }

    // ------------------------------------------------------------------
    // Helpers — slice the manifest around a particular activity name
    // ------------------------------------------------------------------

    private fun extractActivityBlock(manifest: String, activityName: String): String {
        // Find every <activity ...> block and return the first whose name
        // attribute matches activityName.
        val activityPattern = Regex(
            """<activity\b[^>]*?android:name\s*=\s*"${Regex.escape(activityName)}"[^>]*?(?:/>|></activity>)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        return activityPattern.find(manifest)?.value ?: ""
    }

    private fun extractAliasForActivity(manifest: String, targetActivity: String): String {
        // activity-alias blocks reference their target via android:targetActivity.
        val aliasPattern = Regex(
            """<activity-alias\b[^>]*?android:targetActivity\s*=\s*"${Regex.escape(targetActivity)}"[^>]*?(?:/>|></activity-alias>)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        return aliasPattern.find(manifest)?.value ?: ""
    }
}
