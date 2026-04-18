package com.weelo.logistics.data.model

import com.weelo.logistics.telemetry.SchemaDriftTelemetry

/**
 * HoldPhase — forward-compatible mapping of the backend `HoldPhase` enum
 * (see `prisma/schema.prisma` → enum HoldPhase { FLEX CONFIRMED EXPIRED RELEASED }).
 *
 * Pattern: "Forward-compatible enum" — Bright Inventions + pbandk open-enum.
 * The `UNKNOWN` sentinel protects the app when the backend rolls out a new phase
 * value (e.g. a hypothetical `AUTO_RELEASED`) before the mobile client is updated.
 *
 * Wired from Gson via a `JsonDeserializer<HoldPhase>` registered in RetrofitClient
 * so every `phase` string coming off the wire is normalised through
 * [fromBackendString]. See F-C-78.
 *
 * F-C-84: every UNKNOWN mapping is routed through [SchemaDriftTelemetry.record]
 * so schema drift is observable via the `schema_drift_total{enum,value}`
 * metric. The telemetry surface is zero-functional-change — graceful
 * degradation to `UNKNOWN` still happens; we only added observability.
 */
enum class HoldPhase {
    FLEX, CONFIRMED, EXPIRED, RELEASED, UNKNOWN;

    companion object {
        private const val ENUM_NAME: String = "HoldPhase"

        @JvmStatic
        fun fromBackendString(s: String?): HoldPhase {
            if (s.isNullOrBlank()) {
                SchemaDriftTelemetry.record(ENUM_NAME, s, source = "HoldPhase.fromBackendString")
                return UNKNOWN
            }
            return try {
                valueOf(s.uppercase())
            } catch (e: IllegalArgumentException) {
                SchemaDriftTelemetry.record(ENUM_NAME, s, source = "HoldPhase.fromBackendString")
                UNKNOWN
            }
        }
    }
}
