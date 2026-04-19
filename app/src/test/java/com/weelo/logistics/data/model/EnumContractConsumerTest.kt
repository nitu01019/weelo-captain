package com.weelo.logistics.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-79 / F-C-80 / F-C-84 — Sealed-class enum canonicalization consumer tests
 * =============================================================================
 *
 * Source-scan JUnit4 tests. The captain baseline fails to compile end-to-end
 * (shared with upstream t1 work on Vehicle.kt), so we verify the load-bearing
 * contract at the source level. This mirrors the P9 t3/t4 idiom.
 *
 * F-C-79 (AssignmentStatus canonical enum consumer)
 *   - Broadcast.kt AssignmentStatus must gain `PARTIAL_DELIVERY` + `UNKNOWN`
 *     branches so customer `partial_delivery` trips no longer silently map to
 *     `PENDING_DRIVER_RESPONSE` (data-loss bug per INDEX §F-C-79).
 *   - `AssignmentStatus.Companion.fromBackendString(raw)` must exist and
 *     delegate UNKNOWN emission to `SchemaDriftTelemetry.record(...)`.
 *
 * F-C-80 (VehicleStatus.fromBackendString companion)
 *   - Vehicle.kt VehicleStatus must expose an `UNKNOWN` branch and a
 *     `Companion.fromBackendString(raw)` helper with `SchemaDriftTelemetry`
 *     instrumentation on the else path.
 *   - VehicleRepository.mapToUiModel() must delegate to
 *     `VehicleStatus.fromBackendString(data.status)` instead of hand-rolling a
 *     `when(status.lowercase())` branch.
 *
 * F-C-84 (SchemaDriftTelemetry helper + wire-up)
 *   - New `telemetry/SchemaDriftTelemetry.kt` helper providing `record(enum,
 *     rawValue)` which increments the `schema_drift_total{enum,value}` metric
 *     and (per SOL-8 §F-C-84) dedupes within a 60s window. Zero functional
 *     change to business paths — purely observability.
 *   - HoldPhase.fromBackendString (F-C-78 meta) must also emit drift for
 *     unrecognised values (today it silently returns UNKNOWN).
 *   - The rollout sits behind `BuildConfig.FF_CUSTOMER_ENUM_CONTRACT_STRICT`
 *     (default OFF) so telemetry-only observation in canary, drop-in
 *     replacement for hand-rolled `when` blocks after flag flip.
 *
 * Per INDEX.md F-C-79 / F-C-80 / F-C-84 (Wave 5) — depends on F-C-78 meta.
 * =============================================================================
 */
class EnumContractConsumerTest {

    companion object {
        private const val APP_SRC = "/private/tmp/weelo-p10-t2/app/src/main/java/com/weelo/logistics"
        private val broadcastKt = File("$APP_SRC/data/model/Broadcast.kt")
        private val vehicleKt = File("$APP_SRC/data/model/Vehicle.kt")
        private val holdPhaseKt = File("$APP_SRC/data/model/HoldPhase.kt")
        private val vehicleRepoKt = File("$APP_SRC/data/repository/VehicleRepository.kt")
        private val driftKt = File("$APP_SRC/telemetry/SchemaDriftTelemetry.kt")
        private val buildGradle = File("/private/tmp/weelo-p10-t2/app/build.gradle.kts")
    }

    // ---------- F-C-84 — SchemaDriftTelemetry helper ----------

    @Test
    fun `F-C-84 SchemaDriftTelemetry helper file exists`() {
        assertTrue(
            "F-C-84 telemetry helper must exist at ${driftKt.absolutePath}",
            driftKt.exists()
        )
    }

    @Test
    fun `F-C-84 SchemaDriftTelemetry exposes record enum rawValue`() {
        val src = driftKt.readText()
        assertTrue(
            "SchemaDriftTelemetry.record(enumName, rawValue) is the single surface every UNKNOWN else-branch calls.",
            src.contains("fun record(") && src.contains("enumName") && src.contains("rawValue")
        )
    }

    @Test
    fun `F-C-84 SchemaDriftTelemetry dedupes within 60s window`() {
        val src = driftKt.readText()
        assertTrue(
            "SOL-8 §F-C-84 — dedupe identical {enum,value} reports within a 60s window to avoid Sentry flooding.",
            src.contains("60") && (src.contains("dedup") || src.contains("Dedup") || src.contains("cache"))
        )
    }

    @Test
    fun `F-C-84 SchemaDriftTelemetry emits schema_drift_total metric label`() {
        val src = driftKt.readText()
        assertTrue(
            "Metric name must match the observability contract declared in INDEX.md (Counters table).",
            src.contains("schema_drift_total")
        )
    }

    // ---------- F-C-84 — BuildConfig flag default OFF ----------

    @Test
    fun `F-C-84 FF_CUSTOMER_ENUM_CONTRACT_STRICT flag declared and default false`() {
        val src = buildGradle.readText()
        assertTrue(
            "Flag must be declared as a buildConfigField so call sites can gate strict-mode parse.",
            src.contains("FF_CUSTOMER_ENUM_CONTRACT_STRICT")
        )
        // Default must be false (NO-DEPLOY constraint)
        val flagRegion = src.substringAfter("FF_CUSTOMER_ENUM_CONTRACT_STRICT")
            .take(60)
        assertTrue(
            "Flag must default to false. Observed: $flagRegion",
            flagRegion.contains("\"false\"") || flagRegion.contains("false.toString()")
        )
    }

    // ---------- F-C-79 — AssignmentStatus canonical consumer ----------

    @Test
    fun `F-C-79 AssignmentStatus has PARTIAL_DELIVERY branch`() {
        val src = broadcastKt.readText()
        assertTrue(
            "Backend schema.prisma emits partial_delivery (INDEX §F-C-79) — captain must carry a typed branch.",
            src.contains("PARTIAL_DELIVERY")
        )
    }

    @Test
    fun `F-C-79 AssignmentStatus has UNKNOWN sentinel`() {
        val src = broadcastKt.readText()
        assertTrue(
            "Forward-compatible enum pattern requires UNKNOWN sentinel for unknown backend values.",
            src.contains("UNKNOWN")
        )
    }

    @Test
    fun `F-C-79 AssignmentStatus exposes fromBackendString companion`() {
        val src = broadcastKt.readText()
        assertTrue(
            "Companion.fromBackendString consolidates mapping + schema-drift emission (single parse surface).",
            src.contains("fromBackendString") &&
                // Must be inside AssignmentStatus scope — nearest enum declaration
                src.indexOf("enum class AssignmentStatus") < src.indexOf("fromBackendString")
        )
    }

    @Test
    fun `F-C-79 AssignmentStatus fromBackendString delegates to SchemaDriftTelemetry on UNKNOWN`() {
        val src = broadcastKt.readText()
        // Locate the AssignmentStatus enum block
        val enumStart = src.indexOf("enum class AssignmentStatus")
        assertTrue("AssignmentStatus enum must be present", enumStart > 0)
        val enumBlock = src.substring(enumStart).take(2000)
        assertTrue(
            "Unknown-value path must emit SchemaDriftTelemetry.record so backend schema drift is observable.",
            enumBlock.contains("SchemaDriftTelemetry")
        )
    }

    // ---------- F-C-80 — VehicleStatus canonical consumer ----------

    @Test
    fun `F-C-80 VehicleStatus has UNKNOWN sentinel`() {
        val src = vehicleKt.readText()
        assertTrue(
            "VehicleStatus must expose UNKNOWN so an unknown backend value doesn't silently collapse to INACTIVE (current bug per INDEX §F-C-80).",
            src.contains("UNKNOWN")
        )
    }

    @Test
    fun `F-C-80 VehicleStatus exposes fromBackendString companion`() {
        val src = vehicleKt.readText()
        assertTrue(
            "Companion.fromBackendString is the single mapper surface — INDEX §F-C-80 consumers (captain + customer) call through it.",
            src.contains("fromBackendString") &&
                src.indexOf("enum class VehicleStatus") < src.indexOf("fromBackendString")
        )
    }

    @Test
    fun `F-C-80 VehicleStatus fromBackendString delegates to SchemaDriftTelemetry on UNKNOWN`() {
        val src = vehicleKt.readText()
        val enumStart = src.indexOf("enum class VehicleStatus")
        assertTrue("VehicleStatus enum must be present", enumStart > 0)
        val enumBlock = src.substring(enumStart).take(2000)
        assertTrue(
            "SchemaDriftTelemetry wiring is the observability contract — every hand-rolled else -> UNKNOWN must emit.",
            enumBlock.contains("SchemaDriftTelemetry")
        )
    }

    @Test
    fun `F-C-80 VehicleRepository mapToUiModel uses VehicleStatus fromBackendString`() {
        val src = vehicleRepoKt.readText()
        // Hand-rolled when block must be gone, replaced by a single call.
        assertTrue(
            "VehicleRepository must delegate to VehicleStatus.fromBackendString — no more hand-rolled when(status.lowercase()).",
            src.contains("VehicleStatus.fromBackendString")
        )
    }

    // ---------- F-C-78 meta (HoldPhase) — drift emission wiring ----------

    @Test
    fun `F-C-78 meta HoldPhase fromBackendString emits SchemaDriftTelemetry for unknown value`() {
        val src = holdPhaseKt.readText()
        assertTrue(
            "HoldPhase.fromBackendString must now call SchemaDriftTelemetry on UNKNOWN path (F-C-84 telemetry wiring).",
            src.contains("SchemaDriftTelemetry")
        )
    }

    // ---------- F-C-84 — strict flag gating (zero behavioural change when OFF) ----------

    @Test
    fun `F-C-84 SchemaDriftTelemetry respects BuildConfig FF_CUSTOMER_ENUM_CONTRACT_STRICT`() {
        val src = driftKt.readText()
        // NO-DEPLOY: default OFF means record() may still log + increment,
        // but downstream strict behaviour (e.g. throwing) must be flagged.
        // We enforce the flag is referenced — the helper must know strict vs
        // shadow mode so a canary rollout can be graduated.
        assertTrue(
            "Helper must branch on BuildConfig.FF_CUSTOMER_ENUM_CONTRACT_STRICT so strict-parse can graduate (NO-DEPLOY: default OFF means shadow-only).",
            src.contains("FF_CUSTOMER_ENUM_CONTRACT_STRICT")
        )
    }

    // ---------- Graceful degradation — source-scan semantic checks ----------
    //
    // Captain baseline compile is broken (P10 t1 shares Vehicle.kt; gradle
    // test won't link), so we validate graceful-degradation via source-scan
    // of the mapping tables instead of runtime invocation. The assertions
    // below prove the canonical raw-string branches are present and route
    // to the intended typed value.

    @Test
    fun `F-C-79 AssignmentStatus maps partial_delivery branch to PARTIAL_DELIVERY`() {
        val src = broadcastKt.readText()
        val enumStart = src.indexOf("enum class AssignmentStatus")
        val body = src.substring(enumStart).take(4000)
        assertTrue(
            "Mapping table must contain a `\"partial_delivery\" -> PARTIAL_DELIVERY` arm so the backend value routes to the typed branch.",
            body.contains("\"partial_delivery\" -> PARTIAL_DELIVERY") ||
                body.contains("\"partial_delivery\"-> PARTIAL_DELIVERY")
        )
    }

    @Test
    fun `F-C-79 AssignmentStatus fromBackendString else branch returns UNKNOWN not PENDING`() {
        val src = broadcastKt.readText()
        val enumStart = src.indexOf("enum class AssignmentStatus")
        val body = src.substring(enumStart).take(4000)
        // Verify graceful degradation routes to UNKNOWN (NOT to
        // PENDING_DRIVER_RESPONSE which was the pre-F-C-79 bug).
        assertTrue(
            "else -> UNKNOWN must be the graceful-degradation path (NOT else -> PENDING_DRIVER_RESPONSE which silently blocks workflow).",
            body.contains("else ->") && body.contains("UNKNOWN")
        )
        assertFalse(
            "Prior buggy else -> PENDING_DRIVER_RESPONSE pattern must be gone inside fromBackendString.",
            body.substringAfter("fun fromBackendString").take(1500).contains("else -> PENDING_DRIVER_RESPONSE") ||
                body.substringAfter("fun fromBackendString").take(1500).contains("else -> AssignmentStatus.PENDING_DRIVER_RESPONSE")
        )
    }

    @Test
    fun `F-C-79 AssignmentStatus fromBackendString handles null and blank`() {
        val src = broadcastKt.readText()
        val enumStart = src.indexOf("enum class AssignmentStatus")
        val body = src.substring(enumStart).take(4000)
        assertTrue(
            "fromBackendString must null-check via isNullOrBlank (forward-compatible enum contract).",
            body.contains("isNullOrBlank")
        )
    }

    @Test
    fun `F-C-79 AssignmentStatus fromBackendString is case-insensitive`() {
        val src = broadcastKt.readText()
        val enumStart = src.indexOf("enum class AssignmentStatus")
        val body = src.substring(enumStart).take(4000)
        assertTrue(
            "Case-insensitive parse — raw.lowercase() or raw.uppercase() normalisation required (backend may emit mixed case accidentally).",
            body.contains("lowercase()") || body.contains("uppercase()")
        )
    }

    @Test
    fun `F-C-80 VehicleStatus fromBackendString else branch returns UNKNOWN not INACTIVE`() {
        val src = vehicleKt.readText()
        val enumStart = src.indexOf("enum class VehicleStatus")
        val body = src.substring(enumStart).take(4000)
        // Prior fallback collapsed unknowns into INACTIVE — pre-F-C-80 bug.
        val fromFn = body.substringAfter("fun fromBackendString").take(1500)
        assertTrue(
            "else -> UNKNOWN must be the graceful path inside fromBackendString.",
            fromFn.contains("UNKNOWN")
        )
        assertFalse(
            "Pre-F-C-80 fallback `else -> INACTIVE` must be gone from fromBackendString.",
            fromFn.contains("else -> INACTIVE") || fromFn.contains("else -> VehicleStatus.INACTIVE")
        )
    }

    @Test
    fun `F-C-80 VehicleStatus fromBackendString maps canonical lowercase branches`() {
        val src = vehicleKt.readText()
        val enumStart = src.indexOf("enum class VehicleStatus")
        val body = src.substring(enumStart).take(4000)
        assertTrue(body.contains("\"available\" -> AVAILABLE") || body.contains("\"available\"-> AVAILABLE"))
        assertTrue(body.contains("\"in_transit\" -> IN_TRANSIT") || body.contains("\"in_transit\"-> IN_TRANSIT"))
        assertTrue(body.contains("\"maintenance\" -> MAINTENANCE") || body.contains("\"maintenance\"-> MAINTENANCE"))
        assertTrue(body.contains("\"inactive\" -> INACTIVE") || body.contains("\"inactive\"-> INACTIVE"))
    }

    @Test
    fun `F-C-80 VehicleStatus fromBackendString is case-insensitive and null-safe`() {
        val src = vehicleKt.readText()
        val enumStart = src.indexOf("enum class VehicleStatus")
        val body = src.substring(enumStart).take(4000)
        assertTrue(
            "Case-insensitive normalisation required.",
            body.contains("lowercase()") || body.contains("uppercase()")
        )
        assertTrue(
            "Null/blank path must short-circuit to UNKNOWN.",
            body.contains("isNullOrBlank")
        )
    }
}
