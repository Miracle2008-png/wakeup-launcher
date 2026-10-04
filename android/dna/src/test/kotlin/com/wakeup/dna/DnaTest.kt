package com.wakeup.dna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnaTest {
    @Test fun builtInThemesParseAndValidate() {
        for (id in BuiltInThemes.IDS) {
            val t = BuiltInThemes.load(id)
            val errs = DnaValidator.errors(t)
            assertTrue("$id: $errs", errs.isEmpty())
            assertEquals(id, t.id)
        }
    }

    @Test fun builtInThemesRoundTrip() {
        for (t in BuiltInThemes.all()) assertEquals(t, ThemeDna.parse(t.encode()))
    }

    @Test fun lockedSoundIsRejected() {
        val t = BuiltInThemes.load("fjordlys")
        val bad = t.copy(sound = t.sound.copy(whileLocked = true))
        assertTrue(DnaValidator.errors(bad).any { it.path == "sound.whileLocked" })
    }

    @Test fun animatedLayerWithoutReasonIsRejected() {
        val t = BuiltInThemes.load("fjordlys")
        val layers = t.world.layers.map { if (it.id == "rain") it.copy(reason = "") else it }
        assertTrue(DnaValidator.errors(t.copy(world = t.world.copy(layers = layers))).any { "reason" in it.message })
    }

    @Test fun momentsMustBeRare() {
        val t = BuiltInThemes.load("fjordlys")
        val bad = t.copy(moments = listOf(MomentSpec("x", "birds", cooldownMin = 5, probability = .9f, minIntensity = 0)))
        assertTrue(DnaValidator.errors(bad).size >= 3)
    }

    @Test fun unknownColourReferenceIsRejected() {
        val t = BuiltInThemes.load("linen")
        val layers = t.world.layers.map { l ->
            if (l.id == "wall") l.copy(params = kotlinx.serialization.json.JsonObject(l.params + ("topColor" to kotlinx.serialization.json.JsonPrimitive("nope")))) else l
        }
        assertTrue(DnaValidator.errors(t.copy(world = t.world.copy(layers = layers))).any { "nope" in it.message })
    }

    @Test fun samplerIsContinuousAndWraps() {
        val t = BuiltInThemes.load("fjordlys")
        val s = TimeSampler(t.time.keys)
        val a = s.sample(0.0f).color("skyTop")
        val b = s.sample(1.0f).color("skyTop")
        assertEquals(a.l, b.l, 1e-4f) // wraps to dawn
        var prev = s.sample(0f).color("skyBot").l
        var step = 0f
        for (i in 1..1000) { val v = s.sample(i / 1000f).color("skyBot").l; step = maxOf(step, kotlin.math.abs(v - prev)); prev = v }
        assertTrue("no jumps, max step $step", step < .02f)
    }

    @Test fun nightIsDarkerThanDayAndInkFlips() {
        val t = BuiltInThemes.load("fjordlys")
        val s = TimeSampler(t.time.keys)
        val day = s.sample(.22f); val night = s.sample(.7f)
        val dl = LightModel.derive(day.color("skyTop"), day.color("skyBot"))
        val nl = LightModel.derive(night.color("skyTop"), night.color("skyBot"))
        assertTrue(dl.luminance > nl.luminance)
        assertFalse(nl.darkInk)
    }

    @Test fun remixPreservesOriginal() {
        val o = BuiltInThemes.load("fjordlys")
        val before = o.encode()
        val d = Remix.interpret("Make this world colder, quieter and more atmospheric, with stronger fog and less movement.")
        assertTrue(d.cold > 0 && d.fogBias > 0 && d.motionScale < 1f && d.volumeDb < 0)
        val r = Remix.apply(o, d, "fjordlys-colder", "Fjordlys (colder)")
        assertEquals(before, o.encode())
        assertEquals("fjordlys@1", r.parent)
        assertNotEquals(o.grade, r.grade)
        assertTrue(DnaValidator.errors(r).isEmpty())
    }

    @Test fun modesMergeOverBase() {
        val t = BuiltInThemes.load("fjordlys")
        val pure = Modes.apply(t, "pure")
        assertEquals(0, pure.intensity.default)
        assertEquals("matte", pure.ui.materials["dock"])
        assertEquals("frost", t.ui.materials["dock"])
        assertEquals(t.world, pure.world)
    }

    @Test fun contrastRatioSanity() {
        val white = Colors.parseHex("#ffffff"); val black = Colors.parseHex("#000000")
        assertEquals(21f, Contrast.ratio(white, black), .5f)
    }
}
