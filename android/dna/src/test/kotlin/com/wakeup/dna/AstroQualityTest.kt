package com.wakeup.dna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AstroQualityTest {
    private fun ms(s: String) = Instant.parse(s).toEpochMilli()

    @Test fun londonMidsummerSunTimes() {
        // 2024-06-21 London: sunrise 03:43 UTC, sunset 20:21 UTC (published almanac values)
        val t = Astro.sunTimes(ms("2024-06-21T12:00:00Z"), 51.5074, -0.1278)
        assertEquals(ms("2024-06-21T03:43:00Z").toDouble(), t.sunriseMs!!.toDouble(), 4 * 60_000.0)
        assertEquals(ms("2024-06-21T20:21:00Z").toDouble(), t.sunsetMs!!.toDouble(), 4 * 60_000.0)
    }

    @Test fun lagosEquinoxDayIsAboutTwelveHours() {
        val t = Astro.sunTimes(ms("2024-03-20T12:00:00Z"), 6.5244, 3.3792)
        val hours = (t.sunsetMs!! - t.sunriseMs!!) / 3_600_000.0
        assertEquals(12.1, hours, .15)
    }

    @Test fun polarNightAndDay() {
        assertEquals(Astro.Polar.NIGHT, Astro.sunTimes(ms("2024-12-21T12:00:00Z"), 78.0, 15.0).polar)
        assertEquals(Astro.Polar.DAY, Astro.sunTimes(ms("2024-06-21T12:00:00Z"), 78.0, 15.0).polar)
    }

    @Test fun elevationPeaksAtSolarNoonAndIsNegativeAtMidnight() {
        val noon = Astro.sunTimes(ms("2024-06-21T12:00:00Z"), 51.5, -0.1).solarNoonMs
        val high = Astro.sunElevation(noon, 51.5, -0.1)
        assertEquals(61.9, high, 1.0) // 90 - 51.5 + 23.44
        assertTrue(Astro.sunElevation(noon + 12 * 3_600_000L, 51.5, -0.1) < -5)
    }

    @Test fun moonPhasesMatchAlmanac() {
        // new moon 2024-04-08 18:21 UTC, full moon 2024-04-24 23:49 UTC
        val newE = Astro.moonElongation(ms("2024-04-08T18:21:00Z")).let { if (it > 180) it - 360 else it }
        assertEquals(0.0, newE, 1.5)
        assertEquals(180.0, Astro.moonElongation(ms("2024-04-23T23:49:00Z")), 1.5)
        val m = Astro.moon(ms("2024-04-23T23:49:00Z"))
        assertTrue(m.illumination > .98)
        assertEquals("Full moon", m.name)
    }

    @Test fun nextFullMoonIsFoundWithinAlmanacTolerance() {
        val next = Astro.nextPhase(ms("2024-04-10T00:00:00Z"), 180.0)
        assertEquals(ms("2024-04-23T23:49:00Z").toDouble(), next.toDouble(), 2 * 3_600_000.0)
    }

    @Test fun placesLookup() {
        assertNotNull(Places.byTimeZone("Africa/Lagos"))
        assertEquals("Lagos", Places.byName("lagos")?.name)
    }

    // ---- quality policy ----
    private val cool = QualityInputs(thermal = 0, batteryPct = 80, charging = false)

    @Test fun modesMapToTiers() {
        assertEquals(1, QualityPolicy.decide(QualityMode.BATTERY_SAVER, cool).maxLevel)
        assertEquals(2, QualityPolicy.decide(QualityMode.BALANCED, cool).maxLevel)
        assertEquals(3, QualityPolicy.decide(QualityMode.HIGH, cool).maxLevel)
        assertEquals(60, QualityPolicy.decide(QualityMode.ULTRA, cool).worldFps)
    }

    @Test fun hotDeviceStepsEverythingDown() {
        val p = QualityPolicy.decide(QualityMode.ULTRA, cool.copy(thermal = 3))
        assertEquals(1, p.maxLevel); assertFalse(p.backdropBlur); assertTrue(p.reasons.contains("device is hot"))
        assertEquals(QualityPolicy.decide(QualityMode.ULTRA, cool.copy(thermal = 2)).maxLevel, 2)
    }

    @Test fun lowBatteryAndPowerSaveLimit() {
        assertEquals(1, QualityPolicy.decide(QualityMode.HIGH, cool.copy(batteryPct = 10)).maxLevel)
        assertEquals(1, QualityPolicy.decide(QualityMode.HIGH, cool.copy(powerSave = true)).maxLevel)
        // charging lifts the low-battery limit
        assertEquals(3, QualityPolicy.decide(QualityMode.HIGH, cool.copy(batteryPct = 10, charging = true)).maxLevel)
    }

    @Test fun slowFramesDegradeOneTier() {
        val p = QualityPolicy.decide(QualityMode.HIGH, cool.copy(frameMsEma = 30f, refreshHz = 60f))
        assertEquals(2, p.maxLevel)
        assertEquals(30, p.worldFps)
    }

    @Test fun reducedMotionForcesStill() {
        val p = QualityPolicy.decide(QualityMode.ULTRA, cool.copy(reducedMotion = true))
        assertEquals(0, p.maxLevel); assertFalse(p.widgetAnimation)
    }

    @Test fun cinemaBoostsOnlyWhenSafe() {
        assertEquals(4, QualityPolicy.decide(QualityMode.BALANCED, cool.copy(cinema = true)).maxLevel)
        assertEquals(1, QualityPolicy.decide(QualityMode.BALANCED, cool.copy(cinema = true, thermal = 3)).maxLevel)
        assertEquals(1, QualityPolicy.decide(QualityMode.BALANCED, cool.copy(cinema = true, batteryPct = 10)).maxLevel)
    }

    @Test fun invisibleLauncherRendersNothing() {
        assertTrue(QualityPolicy.decide(QualityMode.ULTRA, cool.copy(resumed = false)).suspended)
        assertTrue(QualityPolicy.decide(QualityMode.ULTRA, cool.copy(screenOn = false)).suspended)
    }
}
