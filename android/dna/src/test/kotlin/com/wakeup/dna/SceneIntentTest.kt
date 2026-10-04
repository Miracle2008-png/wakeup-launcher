package com.wakeup.dna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneIntentTest {
    private val prompt = "A quiet Scandinavian lake at 6:30 PM in autumn, soft rain, dark green forest, warm cabin lights, gentle fog."

    @Test fun readsTheHeadlineExample() {
        val i = SceneIntents.parse(prompt)
        assertEquals("fjordlys", i.baseThemeId)
        assertEquals("rain", i.weatherKey)
        assertEquals((18.5f - 6f) / 24f, i.timeOfDay!!, .001f)
        assertTrue(i.delta.fogBias > 0f)
        assertTrue(i.delta.motionScale < 1f)
    }

    @Test fun buildsANewValidThemeAndLeavesTheOriginalAlone() {
        val fj = BuiltInThemes.load("fjordlys")
        val before = fj.encode()
        val t = SceneIntents.toTheme(SceneIntents.parse(prompt), BuiltInThemes.load("linen"), { BuiltInThemes.load(it) }, "quiet-lake", "Quiet lake at 6:30")
        assertEquals(before, fj.encode())
        assertEquals("fjordlys@1", t.parent)
        assertTrue(t.provenance.generated)
        assertEquals("rain", t.pin.weather)
        assertTrue(DnaValidator.errors(t).isEmpty())
    }

    @Test fun clockTimeParsing() {
        assertEquals(18.5f, SceneIntents.parseHour("at 6:30 PM")!!, .01f)
        assertEquals(7f, SceneIntents.parseHour("7am please")!!, .01f)
        assertEquals(0.25f, SceneIntents.parseHour("12:15 am")!!, .01f)
        assertEquals(18.5f, SceneIntents.parseHour("at dusk")!!, .01f)
        assertNull(SceneIntents.parseHour("a plain wall"))
    }

    @Test fun unknownTextProducesNoChangeRatherThanGuessing() {
        val i = SceneIntents.parse("zzz")
        assertNull(i.baseThemeId); assertNull(i.timeOfDay); assertNull(i.weatherKey)
        assertTrue(i.delta.isEmpty)
    }

    @Test fun pinnedThemeRoundTrips() {
        val t = BuiltInThemes.load("linen").copy(pin = PinSpec(.5f, "fog"))
        assertEquals(t, ThemeDna.parse(t.encode()))
        assertNotNull(DnaValidator.errors(t.copy(pin = PinSpec(2f, "tornado"))).firstOrNull())
    }
}
