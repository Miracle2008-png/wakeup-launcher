package com.wakeup.dna

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Automation is entirely user-authored: nothing here runs unless the user created and enabled the rule. */
@Serializable
sealed class Trigger {
    @Serializable @SerialName("time") data class TimeWindow(val startMin: Int, val endMin: Int, val daysMask: Int = 0b1111111) : Trigger()
    @Serializable @SerialName("charging") data class Charging(val on: Boolean = true) : Trigger()
    @Serializable @SerialName("battery") data class BatteryBelow(val pct: Int) : Trigger()
    @Serializable @SerialName("weather") data class WeatherIs(val key: String) : Trigger()
    @Serializable @SerialName("sun") data class SunIs(val up: Boolean) : Trigger()
}

@Serializable
sealed class Action {
    @Serializable @SerialName("setup") data class ApplySetup(val setupId: String) : Action()
    @Serializable @SerialName("theme") data class SetTheme(val themeId: String) : Action()
    @Serializable @SerialName("mode") data class SetMode(val mode: String) : Action()
    @Serializable @SerialName("quality") data class SetQuality(val quality: String) : Action()
    @Serializable @SerialName("ambient") data class SetAmbient(val on: Boolean) : Action()
}

@Serializable
data class Rule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val trigger: Trigger,
    val action: Action,
    val lastRunMs: Long = 0L,
)

@Serializable
data class AutomationState(val rules: List<Rule> = emptyList())

data class AutoContext(
    val minuteOfDay: Int,
    /** 1 = Monday .. 7 = Sunday (java.time.DayOfWeek.getValue). */
    val dayOfWeek: Int,
    val charging: Boolean,
    val batteryPct: Int,
    val weatherKey: String,
    val sunUp: Boolean?,
)

/**
 * Edge-triggered evaluation: a rule fires when its condition becomes true, not on every poll,
 * so a user is never surprised by a setup being re-applied after they manually changed it.
 */
class AutomationEngine {
    private val wasActive = HashMap<String, Boolean>()

    fun active(t: Trigger, c: AutoContext): Boolean = when (t) {
        is Trigger.TimeWindow -> {
            val dayBit = 1 shl (c.dayOfWeek - 1)
            val inWindow = if (t.startMin <= t.endMin) c.minuteOfDay in t.startMin until t.endMin
            else c.minuteOfDay >= t.startMin || c.minuteOfDay < t.endMin
            // for windows crossing midnight, the day mask refers to the day the window started
            val dayOk = if (t.startMin <= t.endMin || c.minuteOfDay >= t.startMin) t.daysMask and dayBit != 0
            else t.daysMask and (1 shl ((c.dayOfWeek + 5) % 7)) != 0
            inWindow && dayOk
        }
        is Trigger.Charging -> c.charging == t.on
        is Trigger.BatteryBelow -> c.batteryPct < t.pct && !c.charging
        is Trigger.WeatherIs -> c.weatherKey == t.key
        is Trigger.SunIs -> c.sunUp != null && c.sunUp == t.up
    }

    /** Returns the rules that just became active. The first evaluation only records state, so enabling a rule never fires it retroactively. */
    fun evaluate(rules: List<Rule>, c: AutoContext, firstRun: Boolean = false): List<Rule> {
        val fired = ArrayList<Rule>()
        val ids = rules.map { it.id }.toSet()
        wasActive.keys.retainAll(ids)
        for (r in rules) {
            if (!r.enabled) { wasActive[r.id] = false; continue }
            val now = active(r.trigger, c)
            val before = wasActive[r.id]
            if (now && before == false && !firstRun) fired += r
            wasActive[r.id] = now
        }
        return fired
    }

    fun describe(r: Rule): String {
        val t = when (val tr = r.trigger) {
            is Trigger.TimeWindow -> "${hm(tr.startMin)} to ${hm(tr.endMin)}"
            is Trigger.Charging -> if (tr.on) "when charging" else "when unplugged"
            is Trigger.BatteryBelow -> "battery under ${tr.pct}%"
            is Trigger.WeatherIs -> "when it is ${tr.key}"
            is Trigger.SunIs -> if (tr.up) "after sunrise" else "after sunset"
        }
        val a = when (val ac = r.action) {
            is Action.ApplySetup -> "apply a setup"
            is Action.SetTheme -> "switch theme"
            is Action.SetMode -> "set ${ac.mode} mode"
            is Action.SetQuality -> "set quality to ${ac.quality.lowercase().replace('_', ' ')}"
            is Action.SetAmbient -> if (ac.on) "turn ambient sound on" else "turn ambient sound off"
        }
        return "$t: $a"
    }

    private fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
}
