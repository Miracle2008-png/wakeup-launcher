package com.wakeup.dna

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A structured change request. The AI layer (or the local parser) produces this, never code. */
data class RemixDelta(
    val cold: Float = 0f,
    val fogBias: Float = 0f,
    val motionScale: Float = 1f,
    val windScale: Float = 1f,
    val volumeDb: Float = 0f,
    val notes: List<Pair<String, String>> = emptyList(),
) {
    val isEmpty get() = notes.isEmpty()
}

object Remix {
    /** Original → Remix → new Theme DNA. The input is never mutated; the result is a new branch. */
    fun apply(base: ThemeDna, d: RemixDelta, newId: String, newName: String): ThemeDna {
        val g = base.grade
        val vol = (base.sound.volume * Math.pow(10.0, d.volumeDb / 20.0).toFloat()).coerceIn(0f, 1f)
        return base.copy(
            id = newId,
            name = newName,
            version = 1,
            parent = "${base.id}@${base.version}",
            provenance = base.provenance.copy(generated = base.provenance.generated),
            grade = GradeSpec(
                cold = (g.cold + d.cold).coerceIn(-1f, 1f),
                fogBias = (g.fogBias + d.fogBias).coerceIn(-.5f, .5f),
                windScale = (g.windScale * d.windScale).coerceIn(0f, 2f),
                motionScale = (g.motionScale * d.motionScale).coerceIn(0f, 2f),
            ),
            sound = base.sound.copy(volume = vol),
        )
    }

    /**
     * Deterministic, local, free interpreter for plain-language requests.
     * Understands a deliberately small vocabulary and says exactly what it changed.
     */
    fun interpret(textIn: String): RemixDelta {
        val s = textIn.lowercase()
        var cold = 0f; var fog = 0f; var motion = 1f; var wind = 1f; var vol = 0f
        val notes = ArrayList<Pair<String, String>>()
        val strong = Regex("strong|more|thick|heavy|dense|deep").containsMatchIn(s)
        if (Regex("\\bcold|cool|blue|winter|frost|icy").containsMatchIn(s)) { cold = if (strong) 1f else .7f; notes += "colour temperature" to "colder (+${"%.1f".format(cold)})" }
        if (Regex("warm|golden|amber|sunny|cosy|cozy").containsMatchIn(s)) { cold = -.7f; notes += "colour temperature" to "warmer (-0.7)" }
        if (Regex("fog|mist|haze|atmospher|moody").containsMatchIn(s)) { fog = if (strong) .4f else .2f; notes += "fog" to "+${"%.2f".format(fog)}" }
        if (Regex("clear air|less fog|no fog|clearer").containsMatchIn(s)) { fog = -.3f; notes += "fog" to "-0.30" }
        if (Regex("less movement|calm|still|slow|quiet|gentle|peaceful").containsMatchIn(s)) { motion = .5f; wind = .5f; notes += "motion" to "x0.50"; notes += "wind" to "x0.50" }
        if (Regex("more movement|alive|stormy|dramatic|windy").containsMatchIn(s)) { motion = 1.4f; wind = 1.5f; notes += "motion" to "x1.40"; notes += "wind" to "x1.50" }
        if (Regex("quiet|quieter|silent|hush").containsMatchIn(s)) { vol = -6f; notes += "ambient volume" to "-6 dB" }
        if (Regex("louder|more sound").containsMatchIn(s)) { vol = 4f; notes += "ambient volume" to "+4 dB" }
        return RemixDelta(cold, fog, motion, wind, vol, notes)
    }
}

/** Mode overrides are partial DNA documents merged over the base; switching modes is a parameter crossfade. */
object Modes {
    fun apply(base: ThemeDna, mode: String): ThemeDna {
        val override = base.modes[mode] ?: return base
        val merged = merge(ThemeDna.json.encodeToJsonElement(ThemeDna.serializer(), base), override)
        return ThemeDna.json.decodeFromJsonElement(ThemeDna.serializer(), merged)
    }

    private fun merge(a: JsonElement, b: JsonElement): JsonElement {
        if (a is JsonObject && b is JsonObject) {
            return buildJsonObject {
                for ((k, v) in a) put(k, if (k in b) merge(v, b.getValue(k)) else v)
                for ((k, v) in b) if (k !in a) put(k, v)
            }
        }
        return if (b is JsonNull) a else b
    }
}
