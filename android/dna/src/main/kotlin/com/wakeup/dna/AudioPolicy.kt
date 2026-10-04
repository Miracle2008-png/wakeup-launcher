package com.wakeup.dna

/**
 * The single place that decides whether WakeUp may make sound. Kept pure so the hard rule,
 * "a locked device never makes WakeUp sounds", is covered by a test rather than by convention.
 */
object AudioPolicy {
    data class Inputs(
        val enabled: Boolean,
        val masterVolume: Float,
        val themeVolume: Float,
        val locked: Boolean,
        val screenOn: Boolean,
        val launcherResumed: Boolean,
        val otherMediaPlaying: Boolean,
        val inCall: Boolean,
    )

    fun mayPlay(i: Inputs): Boolean {
        if (i.locked || !i.screenOn) return false       // hard rule
        if (!i.launcherResumed) return false             // never from the background
        if (!i.enabled) return false
        if (i.inCall || i.otherMediaPlaying) return false // yield to anything the user is actually listening to
        return effectiveVolume(i) > 0.005f
    }

    fun effectiveVolume(i: Inputs): Float = (i.masterVolume.coerceIn(0f, 1f) * i.themeVolume.coerceIn(0f, 1f))
}
