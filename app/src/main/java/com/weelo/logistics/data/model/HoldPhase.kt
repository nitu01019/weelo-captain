package com.weelo.logistics.data.model

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
 */
enum class HoldPhase {
    FLEX, CONFIRMED, EXPIRED, RELEASED, UNKNOWN;

    companion object {
        @JvmStatic
        fun fromBackendString(s: String?): HoldPhase = try {
            if (s.isNullOrBlank()) UNKNOWN else valueOf(s.uppercase())
        } catch (e: IllegalArgumentException) {
            UNKNOWN
        }
    }
}
