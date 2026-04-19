package com.weelo.logistics.data.model

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HoldPhaseTest — F-C-78
 *
 * Covers the forward-compatible enum mapping contract:
 *   - known values round-trip to their enum constant
 *   - case-insensitive mapping (the backend could emit lower-case accidentally)
 *   - unknown / blank / null all fall back to UNKNOWN (no throw)
 *   - the Gson TypeAdapter wired in RetrofitClient deserialises a real JSON
 *     payload via [HoldPhase.fromBackendString] with the same fallback.
 */
class HoldPhaseTest {

    @Test
    fun `fromBackendString maps canonical FLEX`() {
        assertEquals(HoldPhase.FLEX, HoldPhase.fromBackendString("FLEX"))
    }

    @Test
    fun `fromBackendString is case-insensitive`() {
        assertEquals(HoldPhase.FLEX, HoldPhase.fromBackendString("flex"))
        assertEquals(HoldPhase.CONFIRMED, HoldPhase.fromBackendString("Confirmed"))
        assertEquals(HoldPhase.EXPIRED, HoldPhase.fromBackendString("eXpIrEd"))
        assertEquals(HoldPhase.RELEASED, HoldPhase.fromBackendString("released"))
    }

    @Test
    fun `fromBackendString returns UNKNOWN for unrecognised value`() {
        assertEquals(HoldPhase.UNKNOWN, HoldPhase.fromBackendString("AUTO_RELEASED"))
    }

    @Test
    fun `fromBackendString returns UNKNOWN for null`() {
        assertEquals(HoldPhase.UNKNOWN, HoldPhase.fromBackendString(null))
    }

    @Test
    fun `fromBackendString returns UNKNOWN for empty string`() {
        assertEquals(HoldPhase.UNKNOWN, HoldPhase.fromBackendString(""))
        assertEquals(HoldPhase.UNKNOWN, HoldPhase.fromBackendString("   "))
    }

    @Test
    fun `Gson TypeAdapter deserialises known and unknown phases`() {
        // Mirrors the TypeAdapter registered in RetrofitClient. Using
        // TypeAdapter (not JsonDeserializer) is deliberate: JsonDeserializer
        // is NOT invoked by Gson for JSON `null`, which would violate the
        // non-null `phase: HoldPhase` DTO contract. TypeAdapter.read IS
        // invoked for null tokens, so we can always return UNKNOWN instead.
        val gson = GsonBuilder()
            .registerTypeAdapter(
                HoldPhase::class.java,
                object : TypeAdapter<HoldPhase>() {
                    override fun write(out: JsonWriter, value: HoldPhase?) {
                        if (value == null) out.nullValue() else out.value(value.name)
                    }

                    override fun read(reader: JsonReader): HoldPhase {
                        return if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                            HoldPhase.UNKNOWN
                        } else {
                            HoldPhase.fromBackendString(reader.nextString())
                        }
                    }
                }
            )
            .create()

        val flex = gson.fromJson("""{"phase":"FLEX"}""", PhaseHolder::class.java)
        assertEquals(HoldPhase.FLEX, flex.phase)

        val confirmed = gson.fromJson("""{"phase":"CONFIRMED"}""", PhaseHolder::class.java)
        assertEquals(HoldPhase.CONFIRMED, confirmed.phase)

        val unknown = gson.fromJson("""{"phase":"AUTO_RELEASED"}""", PhaseHolder::class.java)
        assertEquals(HoldPhase.UNKNOWN, unknown.phase)

        val nullPhase = gson.fromJson("""{"phase":null}""", PhaseHolder::class.java)
        assertEquals(HoldPhase.UNKNOWN, nullPhase.phase)
    }

    private data class PhaseHolder(
        @SerializedName("phase") val phase: HoldPhase
    )
}
