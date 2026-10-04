package com.wakeup.dna

/**
 * A structured reading of a plain-language request. This is what a generation provider (local rules,
 * a user's own endpoint, a future model) must produce: data, never code. Applying it yields a new Theme DNA.
 */
data class SceneIntent(
    val baseThemeId: String?,
    val timeOfDay: Float?,
    val weatherKey: String?,
    val delta: RemixDelta,
    val matched: List<String>,
)

object SceneIntents {
    private fun hourToT(h: Float) = (((h - 6f) % 24f + 24f) % 24f) / 24f

    /** Extracts a clock time such as "6:30 PM", "18:30", "7pm". Returns hours 0..24 or null. */
    fun parseHour(text: String): Float? {
        val s = text.lowercase()
        Regex("\\b(\\d{1,2})[:.](\\d{2})\\s*(am|pm)?").find(s)?.let { m ->
            var h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
            val ap = m.groupValues[3]
            if (h > 24 || min > 59) return@let
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            return h + min / 60f
        }
        Regex("\\b(\\d{1,2})\\s*(am|pm)\\b").find(s)?.let { m ->
            var h = m.groupValues[1].toInt()
            if (h > 12) return@let
            if (m.groupValues[2] == "pm" && h < 12) h += 12
            if (m.groupValues[2] == "am" && h == 12) h = 0
            return h.toFloat()
        }
        return when {
            "dawn" in s || "sunrise" in s -> 6f
            "morning" in s -> 8.5f
            "noon" in s || "midday" in s -> 12.5f
            "afternoon" in s -> 15.5f
            "golden hour" in s -> 17.5f
            "dusk" in s || "sunset" in s || "evening" in s -> 18.5f
            "midnight" in s || "deep night" in s || "late night" in s -> 0.5f
            "night" in s -> 22f
            else -> null
        }
    }

    fun parse(text: String): SceneIntent {
        val s = text.lowercase()
        val base = when {
            Regex("lake|fjord|forest|cabin|nordic|scandinav|pine|mountain|shore").containsMatchIn(s) -> "fjordlys"
            Regex("desert|dune|sand|salt|arid|dust|canyon").containsMatchIn(s) -> "salt-flat"
            Regex("wall|paper|linen|minimal|plain|window light|shutter|room").containsMatchIn(s) -> "linen"
            else -> null
        }
        val weather = when {
            Regex("snow|blizzard|flurr").containsMatchIn(s) -> "snow"
            Regex("rain|drizzle|storm|shower|monsoon").containsMatchIn(s) -> "rain"
            Regex("fog|mist|haze").containsMatchIn(s) && !Regex("no fog|less fog|clear air").containsMatchIn(s) -> "fog"
            Regex("overcast|cloudy|grey|gray").containsMatchIn(s) -> "cloudy"
            Regex("clear|sunny|cloudless|bright").containsMatchIn(s) -> "clear"
            else -> null
        }
        val hour = parseHour(text)
        val d = Remix.interpret(text)
        val m = ArrayList<String>()
        base?.let { m += "world: $it" }
        hour?.let { m += "time: %02d:%02d".format(it.toInt(), ((it - it.toInt()) * 60).toInt()) }
        weather?.let { m += "weather: $it" }
        d.notes.forEach { m += "${it.first}: ${it.second}" }
        return SceneIntent(base, hour?.let(::hourToT), weather, d, m)
    }

    /**
     * Builds a NEW theme from [base] (the intent's world if it named one, otherwise the supplied fallback).
     * The base theme is never modified; time and weather are pinned only if the user asked for them.
     */
    fun toTheme(intent: SceneIntent, fallback: ThemeDna, resolve: (String) -> ThemeDna?, id: String, name: String): ThemeDna {
        val base = intent.baseThemeId?.let(resolve) ?: fallback
        val remixed = Remix.apply(base, intent.delta, id, name)
        return remixed.copy(
            describe = name.take(160),
            provenance = remixed.provenance.copy(generated = true),
            pin = PinSpec(intent.timeOfDay, intent.weatherKey),
        )
    }
}
