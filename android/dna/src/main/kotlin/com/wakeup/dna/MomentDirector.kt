package com.wakeup.dna

import kotlin.random.Random

/**
 * Decides when a rare cinematic moment may play. Pure and clock-injected so the rules are testable:
 * - never below intensity 2, never with Reduced Motion, never mid-gesture
 * - a global minimum gap between any two moments, and a per-moment cooldown
 * - per-check probability, so even an eligible moment usually does not fire
 */
class MomentDirector(
    private val moments: List<MomentSpec>,
    private val rng: Random = Random.Default,
    private val globalGapMs: Long = 25L * 60_000L,
) {
    private val lastFired = HashMap<String, Long>()
    private var lastAny = Long.MIN_VALUE / 2
    var active: MomentSpec? = null; private set
    private var activeUntil = 0L

    fun poll(nowMs: Long, level: Int, interacting: Boolean, reducedMotion: Boolean, timeOfDay: Float, force: Boolean = false): MomentSpec? {
        val a = active
        if (a != null) {
            if (nowMs >= activeUntil) active = null
            return null
        }
        if (!force) {
            if (reducedMotion || interacting || level < 2) return null
            if (nowMs - lastAny < globalGapMs) return null
        } else if (reducedMotion) return null
        val candidates = moments.filter { m ->
            level >= m.minIntensity &&
                (force || nowMs - (lastFired[m.id] ?: Long.MIN_VALUE / 2) >= m.cooldownMin * 60_000L) &&
                eligibleAtTime(m, timeOfDay)
        }
        if (candidates.isEmpty()) return null
        val pick = candidates[rng.nextInt(candidates.size)]
        if (!force && rng.nextFloat() > pick.probability) return null
        lastFired[pick.id] = nowMs
        lastAny = nowMs
        active = pick
        activeUntil = nowMs + (pick.durationS * 1000).toLong()
        return pick
    }

    fun cancel() { active = null }

    private fun eligibleAtTime(m: MomentSpec, t: Float): Boolean = when (m.type) {
        "birds" -> t in 0.02f..0.55f          // daylight to dusk
        "lights-on" -> t in 0.44f..0.62f      // as the evening settles in
        else -> true
    }
}
