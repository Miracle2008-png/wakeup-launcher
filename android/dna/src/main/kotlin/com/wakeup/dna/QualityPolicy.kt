package com.wakeup.dna

enum class QualityMode(val label: String) {
    BATTERY_SAVER("Battery saver"), BALANCED("Balanced"), HIGH("High"), ULTRA("Ultra"), ADAPTIVE("Adaptive");

    companion object { fun parse(s: String?) = entries.firstOrNull { it.name == s } ?: ADAPTIVE }
}

/** Everything the governor can observe. Pure data so the policy is unit-testable. */
data class QualityInputs(
    /** android.os.PowerManager THERMAL_STATUS_*: 0 none, 1 light, 2 moderate, 3 severe, 4 critical, 5 emergency, 6 shutdown. */
    val thermal: Int = 0,
    val batteryPct: Int = 100,
    val charging: Boolean = false,
    val powerSave: Boolean = false,
    val lowMemory: Boolean = false,
    val frameMsEma: Float = 0f,
    val refreshHz: Float = 60f,
    val reducedMotion: Boolean = false,
    val cinema: Boolean = false,
    val screenOn: Boolean = true,
    val resumed: Boolean = true,
)

data class QualityProfile(
    /** Highest scene intensity allowed (0 still, 1 calm, 2 living, 3 immersive, 4 cinema). */
    val maxLevel: Int,
    val worldFps: Int,
    val particleScale: Float,
    val backdropBlur: Boolean,
    val widgetAnimation: Boolean,
    val renderScale: Float,
    val suspended: Boolean,
    val reasons: List<String>,
)

object QualityPolicy {
    private data class Tier(val level: Int, val fps: Int, val particles: Float, val blur: Boolean, val widgets: Boolean)

    private val TIERS = listOf(
        Tier(1, 24, .3f, false, false),   // 0 battery saver
        Tier(2, 30, .7f, true, true),     // 1 balanced
        Tier(3, 45, 1f, true, true),      // 2 high
        Tier(3, 60, 1.25f, true, true),   // 3 ultra
    )

    fun decide(mode: QualityMode, i: QualityInputs): QualityProfile {
        val why = ArrayList<String>()
        if (!i.screenOn || !i.resumed) return QualityProfile(0, 1, 0f, false, false, 1f, true, listOf(if (!i.screenOn) "screen off" else "launcher not visible"))

        var tier = when (mode) {
            QualityMode.BATTERY_SAVER -> 0
            QualityMode.BALANCED -> 1
            QualityMode.HIGH -> 2
            QualityMode.ULTRA -> 3
            QualityMode.ADAPTIVE -> if (i.charging && i.thermal == 0 && i.batteryPct > 30) 2 else 1
        }
        var renderScale = 1f
        var particleMul = 1f

        if (i.powerSave) { tier = 0; why += "system battery saver" }
        if (i.batteryPct < 15 && !i.charging) { tier = 0; why += "battery under 15%" }
        else if (i.batteryPct < 30 && !i.charging && tier > 1) { tier = 1; why += "battery under 30%" }
        if (i.thermal >= 3) { tier = 0; renderScale = .8f; why += "device is hot" }
        else if (i.thermal == 2) { tier = minOf(tier, 1); why += "device is warm" }
        if (i.lowMemory) { particleMul = .6f; renderScale = minOf(renderScale, .85f); why += "memory pressure" }
        val budget = 1000f / i.refreshHz.coerceAtLeast(30f)
        if (i.frameMsEma > budget * 1.35f && tier > 0) { tier -= 1; why += "frames running long" }

        var t = TIERS[tier]
        var maxLevel = t.level
        var fps = t.fps
        // Cinema may temporarily exceed the everyday ceiling, but never on a hot or low-battery device.
        if (i.cinema && i.thermal < 3 && (i.batteryPct >= 15 || i.charging) && !i.powerSave) {
            maxLevel = 4; fps = 60; particleMul *= 1.4f; why += "cinema boost"
        } else if (i.cinema) why += "cinema boost withheld"
        if (i.reducedMotion) { maxLevel = 0; fps = 1; why += "reduced motion" }
        return QualityProfile(maxLevel, fps, t.particles * particleMul, t.blur && i.thermal < 3 && !i.reducedMotion, t.widgets && !i.reducedMotion, renderScale, false, why)
    }
}
