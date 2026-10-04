package com.wakeup.dna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MomentTest {
    private val m = listOf(MomentSpec("b", "birds", cooldownMin = 40, probability = 1f, durationS = 9f, minIntensity = 2))
    private val min = 60_000L

    @Test fun neverBelowIntensityTwoOrWhenReducedOrInteracting() {
        val d = MomentDirector(m, Random(1))
        assertNull(d.poll(0, level = 1, interacting = false, reducedMotion = false, timeOfDay = .3f))
        assertNull(d.poll(0, level = 3, interacting = true, reducedMotion = false, timeOfDay = .3f))
        assertNull(d.poll(0, level = 3, interacting = false, reducedMotion = true, timeOfDay = .3f))
        assertNotNull(d.poll(0, level = 3, interacting = false, reducedMotion = false, timeOfDay = .3f))
    }

    @Test fun globalGapAndCooldownAreRespected() {
        val d = MomentDirector(m, Random(1))
        assertNotNull(d.poll(0, 3, false, false, .3f))
        assertNull(d.poll(5 * min, 3, false, false, .3f))      // still playing
        assertNull(d.poll(10 * min, 3, false, false, .3f))     // inside the 25 min global gap
        assertNull(d.poll(30 * min, 3, false, false, .3f))     // gap passed but per-moment cooldown (40) not
        assertNotNull(d.poll(41 * min, 3, false, false, .3f))
    }

    @Test fun rareByProbability() {
        val low = listOf(m[0].copy(probability = .1f, cooldownMin = 25))
        val d = MomentDirector(low, Random(7))
        var fired = 0
        for (i in 0 until 1000) if (d.poll(i * 26 * min, 3, false, false, .3f) != null) fired++
        assertTrue("fired $fired of 1000 eligible polls", fired in 40..180)
    }

    @Test fun timeOfDayGatesBirds() {
        val d = MomentDirector(m, Random(1))
        assertNull(d.poll(0, 3, false, false, .8f)) // night: no birds
    }

    @Test fun forceStillHonoursReducedMotion() {
        val d = MomentDirector(m, Random(1))
        assertNull(d.poll(0, 3, false, true, .3f, force = true))
        assertEquals("b", d.poll(0, 3, false, false, .3f, force = true)?.id)
    }
}
