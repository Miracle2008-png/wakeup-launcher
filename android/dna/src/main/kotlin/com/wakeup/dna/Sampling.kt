package com.wakeup.dna

import kotlin.math.exp

/** The five weather channels every theme may respond to, each 0..1. */
data class Weather(
    val rain: Float = 0f,
    val fog: Float = 0f,
    val cloud: Float = 0f,
    val snow: Float = 0f,
    val wind: Float = 0.2f,
) {
    fun approach(target: Weather, dtSeconds: Float, tau: Float): Weather {
        val k = 1f - exp(-dtSeconds / tau)
        fun s(a: Float, b: Float) = a + (b - a) * k
        return Weather(s(rain, target.rain), s(fog, target.fog), s(cloud, target.cloud), s(snow, target.snow), s(wind, target.wind))
    }

    /** How strongly the sky should flatten toward grey for this weather. */
    val overcast: Float get() = maxOf(cloud * .55f, rain * .8f, fog * .5f).coerceIn(0f, .8f)

    companion object {
        val CLEAR = Weather(0f, 0f, .1f, 0f, .25f)
        val CLOUDY = Weather(0f, .1f, .8f, 0f, .45f)
        val RAIN = Weather(.65f, .3f, .9f, 0f, .5f)
        val FOG = Weather(0f, .85f, .6f, 0f, .15f)
        val SNOW = Weather(0f, .25f, .8f, .7f, .25f)
        val PRESETS = mapOf("clear" to CLEAR, "cloudy" to CLOUDY, "rain" to RAIN, "fog" to FOG, "snow" to SNOW)
    }
}

/** Interpolated state of a theme at one moment of the day. */
class Sample(val colors: Map<String, Lab>, val values: Map<String, Float>) {
    fun color(name: String, fallback: Lab = Lab(.5f, 0f, 0f)) = colors[name] ?: fallback
    fun value(name: String, d: Float = 0f) = values[name] ?: d
}

/** Samples a theme's time keyframes continuously (not in four buckets), blending in OKLab. */
class TimeSampler(private val keys: List<TimeKey>) {
    private val labs: List<Map<String, Lab>> = keys.map { k -> k.colors.mapValues { Colors.parseHex(it.value) } }

    fun sample(tIn: Float): Sample {
        val t = (tIn - Math.floor(tIn.toDouble()).toFloat()).coerceIn(0f, 1f)
        var i = 0
        while (i < keys.size - 2 && t > keys[i + 1].t) i++
        val a = keys[i]; val b = keys[i + 1]
        val raw = ((t - a.t) / (b.t - a.t)).coerceIn(0f, 1f)
        val f = raw * raw * (3f - 2f * raw)
        val colors = labs[i].mapValues { (n, la) -> Lab.mix(la, labs[i + 1][n] ?: la, f) }
        val values = a.values.mapValues { (n, va) -> va + ((b.values[n] ?: va) - va) * f }
        return Sample(colors, values)
    }
}

/** What the UI reads from the world each frame: the single source of light. */
data class LightModel(
    val luminance: Float,
    val darkInk: Boolean,
    val skyTop: Lab,
    val skyBottom: Lab,
) {
    companion object {
        /** Ink flips to dark when the backdrop is bright. Threshold tuned so mid-dusk keeps light ink. */
        fun derive(skyTop: Lab, skyBottom: Lab, dim: Float = 1f): LightModel {
            val lum = (skyTop.luminance * .45f + skyBottom.luminance * .55f) * dim
            return LightModel(lum, lum > .34f, skyTop, skyBottom)
        }
    }
}

/** WCAG contrast helpers used by the accessibility checks. */
object Contrast {
    private fun lin(c: Float) = if (c <= .04045f) c / 12.92f else Math.pow(((c + .055f) / 1.055f).toDouble(), 2.4).toFloat()
    fun relativeLuminance(lab: Lab): Float {
        val (r, g, b) = Colors.labToSrgb(lab)
        return .2126f * lin(r.coerceIn(0f, 1f)) + .7152f * lin(g.coerceIn(0f, 1f)) + .0722f * lin(b.coerceIn(0f, 1f))
    }
    fun ratio(a: Lab, b: Lab): Float {
        val la = relativeLuminance(a); val lb = relativeLuminance(b)
        return (maxOf(la, lb) + .05f) / (minOf(la, lb) + .05f)
    }
}
