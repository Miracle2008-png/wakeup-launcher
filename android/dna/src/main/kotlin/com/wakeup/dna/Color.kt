package com.wakeup.dna

import kotlin.math.cbrt
import kotlin.math.pow

/** A colour in OKLab. Blending here keeps time-of-day transitions perceptually even. */
data class Lab(val l: Float, val a: Float, val b: Float) {
    operator fun times(k: Float) = Lab(l * k, a, b)
    fun withChroma(k: Float) = Lab(l, a * k, b * k)

    /** Approximate relative luminance, good enough for ink polarity decisions. */
    val luminance: Float get() = l * l * l

    fun toArgb(alpha: Float = 1f): Int {
        val (r, g, bl) = Colors.labToSrgb(this)
        val ai = (alpha.coerceIn(0f, 1f) * 255f + .5f).toInt()
        return (ai shl 24) or (to8(r) shl 16) or (to8(g) shl 8) or to8(bl)
    }

    private fun to8(v: Float) = (v.coerceIn(0f, 1f) * 255f + .5f).toInt()

    companion object {
        fun mix(x: Lab, y: Lab, f: Float) = Lab(x.l + (y.l - x.l) * f, x.a + (y.a - x.a) * f, x.b + (y.b - x.b) * f)
    }
}

object Colors {
    private val HEX = Regex("^#([0-9a-fA-F]{6})$")

    fun isHex(s: String) = HEX.matches(s)

    fun parseHex(s: String): Lab {
        val m = HEX.matchEntire(s) ?: throw IllegalArgumentException("not a #rrggbb colour: $s")
        val v = m.groupValues[1].toInt(16)
        return srgbToLab(((v shr 16) and 255) / 255f, ((v shr 8) and 255) / 255f, (v and 255) / 255f)
    }

    private fun toLin(c: Float) = if (c <= .04045f) c / 12.92f else ((c + .055f) / 1.055f).pow(2.4f)
    private fun fromLin(c: Float) = if (c <= .0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - .055f

    fun srgbToLab(r0: Float, g0: Float, b0: Float): Lab {
        val r = toLin(r0); val g = toLin(g0); val b = toLin(b0)
        val l = cbrt(.4122214708f * r + .5363325363f * g + .0514459929f * b)
        val m = cbrt(.2119034982f * r + .6806995451f * g + .1073969566f * b)
        val s = cbrt(.0883024619f * r + .2817188376f * g + .6299787005f * b)
        return Lab(
            .2104542553f * l + .793617785f * m - .0040720468f * s,
            1.9779984951f * l - 2.428592205f * m + .4505937099f * s,
            .0259040371f * l + .7827717662f * m - .808675766f * s,
        )
    }

    fun labToSrgb(c: Lab): Triple<Float, Float, Float> {
        val l = (c.l + .3963377774f * c.a + .2158037573f * c.b).let { it * it * it }
        val m = (c.l - .1055613458f * c.a - .0638541728f * c.b).let { it * it * it }
        val s = (c.l - .0894841775f * c.a - 1.291485548f * c.b).let { it * it * it }
        return Triple(
            fromLin(4.0767416621f * l - 3.3077115913f * m + .2309699292f * s),
            fromLin(-1.2684380046f * l + 2.6097574011f * m - .3413193965f * s),
            fromLin(-.0041960863f * l - .7034186147f * m + 1.707614701f * s),
        )
    }
}
