package com.wakeup.dna

/** Semantic validation on top of schema parsing. Errors block installation; warnings do not. */
data class Issue(val severity: Severity, val path: String, val message: String) {
    enum class Severity { ERROR, WARNING }
    override fun toString() = "${severity.name.lowercase()} $path: $message"
}

object DnaValidator {
    const val MAX_LAYERS = 16
    private val ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
    private val MATERIALS = setOf("air", "matte", "frost", "lacquer", "etched")
    private val CLOCKS = setOf("cinematic", "minimal", "editorial", "practical")
    private val PAGES = setOf("slide", "pan", "fade")
    private val AMBIENT = setOf("none", "rain", "wind", "water")
    private val ICONS = setOf("original", "light", "mono")
    private val MOMENTS = setOf("birds", "lights-on", "light-breaks", "train")
    private val CAPABILITIES = setOf("weather", "tilt", "ambient-audio", "personal-media")

    fun validate(t: ThemeDna): List<Issue> {
        val out = ArrayList<Issue>()
        fun err(p: String, m: String) = out.add(Issue(Issue.Severity.ERROR, p, m))
        fun warn(p: String, m: String) = out.add(Issue(Issue.Severity.WARNING, p, m))

        if (t.dna != ThemeDna.DNA_VERSION) err("dna", "unsupported version ${t.dna}, expected ${ThemeDna.DNA_VERSION}")
        if (!ID.matches(t.id)) err("id", "must match ${ID.pattern}")
        if (t.name.isBlank() || t.name.length > 60) err("name", "1 to 60 characters")
        if (t.describe.length > 160) err("describe", "keep it to one plain line (160 characters)")
        if (t.describe.isBlank()) warn("describe", "screen readers announce this once on apply; add one line")

        if (t.intensity.ceiling !in 0..4) err("intensity.ceiling", "0..4")
        if (t.intensity.default !in 0..3) err("intensity.default", "0..3")
        if (t.intensity.default > t.intensity.ceiling) err("intensity", "default exceeds ceiling")

        if (t.world.span !in 1f..1.5f) err("world.span", "1..1.5 (anti-nausea cap on pan travel)")
        if (t.world.layers.isEmpty()) err("world.layers", "at least one layer")
        if (t.world.layers.size > MAX_LAYERS) err("world.layers", "at most $MAX_LAYERS layers")
        val seen = HashSet<String>()
        t.world.layers.forEachIndexed { i, l ->
            val p = "world.layers[$i:${l.id}]"
            if (!seen.add(l.id)) err(p, "duplicate layer id")
            if (l.type !in LayerTypes.ALL) err(p, "unknown layer type '${l.type}'")
            if (l.depth !in 0f..1f) err(p, "depth 0..1")
            if (l.wind !in 0f..1f) err(p, "wind 0..1")
            if (l.minIntensity !in 0..4) err(p, "minIntensity 0..4")
            if (l.type in LayerTypes.ANIMATED && l.reason.length < 8) err(p, "animated layer needs a reason it moves")
        }

        val keys = t.time.keys
        if (keys.size < 2) err("time.keys", "need at least two keyframes")
        else {
            if (keys.first().t != 0f) err("time.keys", "first key must be t=0")
            if (keys.last().t != 1f) err("time.keys", "last key must be t=1 (wraps to dawn)")
            if (keys.zipWithNext().any { (a, b) -> b.t <= a.t }) err("time.keys", "t must strictly increase")
            val names = keys.first().colors.keys
            keys.forEachIndexed { i, k ->
                if (k.colors.keys != names) err("time.keys[$i]", "colour names must match the first key")
                k.colors.forEach { (n, c) -> if (!Colors.isHex(c)) err("time.keys[$i].colors.$n", "use #rrggbb") }
            }
            val vnames = keys.first().values.keys
            keys.forEachIndexed { i, k -> if (k.values.keys != vnames) err("time.keys[$i]", "value names must match the first key") }
            // every colour a layer names must exist
            t.world.layers.forEach { l ->
                l.params.forEach { (pk, pv) ->
                    if (pk.endsWith("Color") || pk == "color") {
                        val n = pv.toString().trim('"')
                        if (n !in names) err("world.layers[${l.id}].params.$pk", "colour '$n' is not defined in time.keys")
                    }
                }
            }
        }

        t.ui.materials.forEach { (k, v) -> if (v !in MATERIALS) err("ui.materials.$k", "one of $MATERIALS") }
        if (t.ui.clock.style !in CLOCKS) err("ui.clock.style", "one of $CLOCKS")
        if (t.ui.clock.weight !in 100..900) err("ui.clock.weight", "100..900")
        if (t.motion.page !in PAGES) err("motion.page", "one of $PAGES")
        if (t.icons.treatment !in ICONS) err("icons.treatment", "one of $ICONS")
        if (t.sound.ambient !in AMBIENT) err("sound.ambient", "one of $AMBIENT")
        if (t.sound.volume !in 0f..1f) err("sound.volume", "0..1")
        if (t.sound.whileLocked) err("sound.whileLocked", "a locked device never makes WakeUp sounds; this cannot be enabled")
        if (t.layout.columns !in 3..6 || t.layout.rows !in 4..8 || t.layout.dock !in 3..6) err("layout", "columns 3..6, rows 4..8, dock 3..6")
        if (t.grade.cold !in -1f..1f || t.grade.fogBias !in -0.5f..0.5f || t.grade.windScale !in 0f..2f || t.grade.motionScale !in 0f..2f) err("grade", "values out of range")

        t.moments.forEachIndexed { i, m ->
            val p = "moments[$i:${m.id}]"
            if (m.type !in MOMENTS) err(p, "unknown moment type")
            if (m.cooldownMin < 25) err(p, "cooldown at least 25 minutes (cinematic moments must be rare)")
            if (m.probability !in 0f..0.2f) err(p, "probability 0..0.2")
            if (m.minIntensity < 2) err(p, "moments need intensity 2 or more")
            if (m.durationS !in 2f..30f) err(p, "duration 2..30 s")
        }
        t.requires.capabilities.forEach { if (it !in CAPABILITIES) err("requires.capabilities", "unknown capability '$it'") }
        t.modes.keys.forEach { if (it !in setOf("pure", "minimal", "living", "immersive", "cinema", "focus")) err("modes.$it", "unknown mode") }
        return out
    }

    fun errors(t: ThemeDna) = validate(t).filter { it.severity == Issue.Severity.ERROR }
}
