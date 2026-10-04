package com.wakeup.launcher.device

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * A closed haptic vocabulary routed through View.performHapticFeedback, which respects the
 * system's own touch-feedback setting. Nothing vibrates just because an animation exists.
 */
class Haptics(private val viewProvider: () -> View?) {
    @Volatile var enabled = true
    @Volatile var strength = 1f

    enum class Kind { TICK, SETTLE, CONFIRM, SOFT }

    fun play(kind: Kind) {
        if (!enabled || strength < .05f) return
        val v = viewProvider() ?: return
        // Strength is a gate rather than an amplitude: view haptics have no amplitude, so a low setting drops the lightest events.
        if (strength < .5f && kind == Kind.TICK) return
        val c = when (kind) {
            Kind.TICK -> HapticFeedbackConstants.CLOCK_TICK
            Kind.SETTLE -> HapticFeedbackConstants.VIRTUAL_KEY
            Kind.CONFIRM -> HapticFeedbackConstants.CONFIRM
            Kind.SOFT -> HapticFeedbackConstants.LONG_PRESS
        }
        v.performHapticFeedback(c)
    }
}
