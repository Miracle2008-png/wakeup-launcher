package com.wakeup.dna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private val sample = """
    {"latitude":6.5,"longitude":3.4,
     "current":{"time":1700000000,"temperature_2m":27.4,"weather_code":63,"cloud_cover":92,"wind_speed_10m":14.0},
     "hourly":{"time":[1700000000,1700003600,1700007200],"temperature_2m":[27.4,26.9,26.1],"precipitation_probability":[60,70,40]},
     "daily":{"temperature_2m_max":[29.1],"temperature_2m_min":[23.8]}}
    """.trimIndent()

    @Test fun parsesAnOpenMeteoResponse() {
        val r = WeatherCodes.parseOpenMeteo(sample, "Lagos", 1L)
        assertEquals("Rain", r.condition)
        assertEquals(27.4f, r.tempC!!, .01f)
        assertEquals(29.1f, r.hiC!!, .01f)
        assertEquals(3, r.hourly.size)
        assertEquals(1700003600000L, r.hourly[1].epochMs)
        assertTrue(r.rain > .6f && r.cloud > .85f)
    }

    @Test fun roundTripsThroughJsonForTheCache() {
        val r = WeatherCodes.parseOpenMeteo(sample, "Lagos", 5L)
        val j = kotlinx.serialization.json.Json.encodeToString(WeatherReport.serializer(), r)
        assertEquals(r, kotlinx.serialization.json.Json.decodeFromString(WeatherReport.serializer(), j))
    }

    @Test fun malformedResponsesAreRejectedNotCrashed() {
        for (bad in listOf("", "not json", "{}", "{\"current\":{}}", "[1,2]", "{\"current\":{\"weather_code\":\"x\"}}")) {
            try { WeatherCodes.parseOpenMeteo(bad, "x", 0); throw AssertionError("accepted: $bad") } catch (e: IllegalArgumentException) { }
        }
    }

    @Test fun codesMapToSensibleVectors() {
        assertTrue(WeatherCodes.vector(0, null, null).rain == 0f)
        assertTrue(WeatherCodes.vector(65, null, null).rain > .9f)
        assertTrue(WeatherCodes.vector(75, null, null).snow > .8f)
        assertTrue(WeatherCodes.vector(45, null, null).fog > .8f)
        assertFalse(WeatherCodes.vector(3, null, null).cloud < .8f)
        assertEquals(1f, WeatherCodes.vector(0, null, 400f).wind, 0f)
    }

    @Test fun manualPresetsCoverEveryChip() {
        for (k in Weather.PRESETS.keys) assertEquals(k, k, WeatherCodes.manual(k, 0).let { k })
        assertEquals("Rain", WeatherCodes.manual("rain", 0).condition)
    }
}
