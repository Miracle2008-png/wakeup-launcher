package com.wakeup.dna

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTest {
    private fun ctx(min: Int = 600, day: Int = 3, charging: Boolean = false, pct: Int = 80, wx: String = "clear", sun: Boolean? = true) =
        AutoContext(min, day, charging, pct, wx, sun)

    private val night = Rule("r1", "Night", trigger = Trigger.TimeWindow(22 * 60, 6 * 60), action = Action.SetMode("focus"))

    @Test fun timeWindowCrossingMidnight() {
        val e = AutomationEngine()
        assertTrue(e.active(night.trigger, ctx(min = 23 * 60)))
        assertTrue(e.active(night.trigger, ctx(min = 3 * 60)))
        assertFalse(e.active(night.trigger, ctx(min = 12 * 60)))
    }

    @Test fun dayMaskHonouredIncludingAcrossMidnight() {
        val e = AutomationEngine()
        val weekdaysOnly = Trigger.TimeWindow(22 * 60, 6 * 60, daysMask = 0b0011111) // Mon..Fri start days
        assertTrue(e.active(weekdaysOnly, ctx(min = 23 * 60, day = 5)))   // Friday night
        assertTrue(e.active(weekdaysOnly, ctx(min = 3 * 60, day = 6)))    // Saturday 03:00 belongs to Friday's window
        assertFalse(e.active(weekdaysOnly, ctx(min = 23 * 60, day = 6)))  // Saturday night
        assertFalse(e.active(weekdaysOnly, ctx(min = 3 * 60, day = 1)))   // Monday 03:00 belongs to Sunday's window
    }

    @Test fun firesOnEdgeNotOnEveryPoll() {
        val e = AutomationEngine()
        assertTrue(e.evaluate(listOf(night), ctx(min = 12 * 60)).isEmpty())
        assertEquals(1, e.evaluate(listOf(night), ctx(min = 22 * 60 + 1)).size)
        assertTrue(e.evaluate(listOf(night), ctx(min = 22 * 60 + 2)).isEmpty())
        assertTrue(e.evaluate(listOf(night), ctx(min = 23 * 60)).isEmpty())
        assertTrue(e.evaluate(listOf(night), ctx(min = 8 * 60)).isEmpty())
        assertEquals(1, e.evaluate(listOf(night), ctx(min = 22 * 60)).size)
    }

    @Test fun enablingARuleNeverFiresRetroactively() {
        val e = AutomationEngine()
        // first evaluation while the condition is already true only records state
        assertTrue(e.evaluate(listOf(night), ctx(min = 23 * 60), firstRun = true).isEmpty())
        assertTrue(e.evaluate(listOf(night), ctx(min = 23 * 60 + 1)).isEmpty())
    }

    @Test fun disabledRulesNeverFire() {
        val e = AutomationEngine()
        val off = night.copy(enabled = false)
        e.evaluate(listOf(off), ctx(min = 12 * 60))
        assertTrue(e.evaluate(listOf(off), ctx(min = 23 * 60)).isEmpty())
    }

    @Test fun deviceTriggers() {
        val e = AutomationEngine()
        assertTrue(e.active(Trigger.Charging(true), ctx(charging = true)))
        assertTrue(e.active(Trigger.BatteryBelow(20), ctx(pct = 15)))
        assertFalse(e.active(Trigger.BatteryBelow(20), ctx(pct = 15, charging = true)))
        assertTrue(e.active(Trigger.WeatherIs("rain"), ctx(wx = "rain")))
        assertFalse(e.active(Trigger.SunIs(false), ctx(sun = null)))
        assertTrue(e.active(Trigger.SunIs(false), ctx(sun = false)))
    }

    @Test fun serializationRoundTrip() {
        val s = AutomationState(listOf(night, Rule("r2", "Rain", trigger = Trigger.WeatherIs("rain"), action = Action.ApplySetup("s1"))))
        val j = Json { classDiscriminator = "type" }
        assertEquals(s, j.decodeFromString(AutomationState.serializer(), j.encodeToString(AutomationState.serializer(), s)))
    }
}
