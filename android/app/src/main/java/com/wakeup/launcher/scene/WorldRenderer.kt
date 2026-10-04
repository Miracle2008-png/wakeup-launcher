package com.wakeup.launcher.scene

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.wakeup.dna.Lab
import com.wakeup.dna.LayerSpec
import com.wakeup.dna.LightModel
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Renders a Theme DNA world with the Android 2D canvas. The strategy interface leaves room for GL or Vulkan. */
interface WorldRenderStrategy {
    fun draw(canvas: Canvas, widthPx: Int, heightPx: Int, s: SceneState)
}

private const val LOGICAL_H = 891f

private fun hash(i: Float, s: Float): Float {
    val x = sin((i * 127.1 + s * 311.7)) * 43758.5453
    return (x - floor(x)).toFloat()
}
private fun smooth(f: Float) = f * f * (3f - 2f * f)
private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun noise(x: Float, s: Float): Float {
    val i = floor(x); val f = x - i
    return lerp(hash(i, s), hash(i + 1, s), smooth(f))
}
private fun fbm(x: Float, s: Float) = noise(x, s) * .6f + noise(x * 2.1f, s + 3f) * .28f + noise(x * 4.3f, s + 7f) * .12f
private fun frac(x: Float) = x - floor(x)
private fun wrap(x: Float, m: Float): Float { val r = x % m; return if (r < 0) r + m else r }

class CanvasWorldRenderer : WorldRenderStrategy {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tmp = Path()
    private val rect = RectF()

    private class Ridge(val path: Path, val x0: Float, val step: Float, val trees: FloatArray)
    private val ridges = HashMap<String, Ridge>()
    private var cacheKey = ""

    private class Ripple(val x: Float, val y: Float, var age: Float)
    private val ripples = ArrayList<Ripple>()
    private var lastSeconds = 0f
    private var pts = FloatArray(800)

    override fun draw(canvas: Canvas, widthPx: Int, heightPx: Int, s: SceneState) {
        val dna = s.dna ?: return
        val sampler = s.sampler ?: return
        val k = heightPx / LOGICAL_H
        val w = widthPx / k
        val key = "${s.themeVersion}:${w.toInt()}"
        if (key != cacheKey) { ridges.clear(); ripples.clear(); cacheKey = key }
        canvas.save()
        canvas.scale(k, k)
        val f = Frame(canvas, w, LOGICAL_H, s, dna, sampler.sample(s.t))
        s.lastSample = f.sample
        f.run()
        lastSeconds = s.seconds
        canvas.restore()
        s.light = f.lightModel()
    }

    private inner class Frame(
        val c: Canvas, val W: Float, val H: Float, val s: SceneState,
        val dna: com.wakeup.dna.ThemeDna, val sample: com.wakeup.dna.Sample,
    ) {
        val lvl = s.level.coerceIn(0, 4)
        val span = dna.world.span
        val wx = s.weather
        val grade = dna.grade
        val mot = if (lvl == 0) 0f else grade.motionScale
        val tt = s.seconds
        val ov = (wx.overcast * dna.overcast)
        val fogA = (wx.fog + grade.fogBias).coerceIn(0f, 1f)
        val windA = (wx.wind * grade.windScale).coerceIn(0f, 1.5f)
        val dim = if (s.lowBattery) .78f else 1f
        val cold = grade.cold
        val pan = if (s.parallax) s.pan else 0f
        val tilt = if (lvl >= 3 && s.parallax) s.tiltX else 0f
        var skyTop: Lab? = null
        var skyBot: Lab? = null
        var sunX = W * .5f
        var birdsDrawn = false

        fun adj(l: Lab, w: Float = 1f): Lab {
            var a = l.a * (1f - ov * .7f * w); var b = l.b * (1f - ov * .7f * w); var ll = l.l * dim
            if (cold != 0f) { a -= .018f * cold * w; b -= .03f * cold * w; ll *= 1f - .03f * cold }
            return Lab(ll, a, b)
        }
        fun col(name: String, w: Float = 1f) = adj(sample.color(name), w)
        fun raw(name: String) = sample.color(name)
        fun px(depth: Float) = -pan * (span - 1f) * W * depth + tilt * 14f * depth
        fun particles(base: Float, drive: Float) = (base * drive * s.particleScale).toInt().coerceIn(0, 400)

        fun run() {
            for (l in dna.world.layers) {
                if (lvl < l.minIntensity) continue
                if (l.type == "ridge" && !birdsDrawn) { birds(); birdsDrawn = true }
                when (l.type) {
                    "sky" -> sky(l); "stars" -> stars(l); "glow" -> glow(l); "disc" -> disc(l); "clouds" -> clouds(l)
                    "ridge" -> ridge(l); "water" -> water(l); "building" -> building(l); "fog" -> fog(l)
                    "rain" -> rain(l); "snow" -> snow(l); "dust" -> dust(l); "ripples" -> ripples(l)
                    "beams" -> beams(l); "washes" -> washes(l); "shimmer" -> shimmer(l); "vignette" -> vignette(l)
                    "bitmap" -> bitmap(l)
                }
            }
            if (!birdsDrawn) birds()
        }

        fun lightModel(): LightModel {
            val top = skyTop ?: adj(sample.color("skyTop", sample.color("wall")))
            val bot = skyBot ?: adj(sample.color("skyBot", sample.color("wall2")))
            return LightModel.derive(top, bot, 1f)
        }

        // ---- layers ----
        fun sky(l: LayerSpec) {
            val top = col(l.str("topColor")); val bot = col(l.str("bottomColor"))
            if (skyTop == null) { skyTop = top; skyBot = bot }
            val h = l.num("height", .56f) * H
            fill.shader = LinearGradient(0f, 0f, 0f, h, top.toArgb(), bot.toArgb(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W, H, fill); fill.shader = null
        }

        fun stars(l: LayerSpec) {
            val strength = sample.value(l.str("strengthValue", "stars")) * (1f - ov) * dim
            if (strength < .02f) return
            val n = (l.num("count", 110f) * min(1f, s.particleScale + .3f)).toInt()
            val seed = l.num("seed", 21f); val region = l.num("region", .5f) * H
            for (i in 0 until n) {
                val tw = if (lvl >= 1) .6f + .4f * sin(tt * .8f + hash(i.toFloat(), seed + 3f) * 6f) else 1f
                fill.color = android.graphics.Color.argb((strength * tw * .8f * 255).toInt().coerceIn(0, 255), 235, 240, 255)
                val x = hash(i.toFloat(), seed) * W * span * .75f + px(l.depth) - 20f
                val y = hash(i.toFloat(), seed + 1f) * region
                val r = .4f + hash(i.toFloat(), seed + 2f) * 1.1f
                c.drawRect(x, y, x + r, y + r, fill)
            }
        }

        private fun celestial(l: LayerSpec): Pair<Float, Float> {
            val x0 = l.num("x0", .15f); val x1 = l.num("x1", .85f); val tEnd = l.num("tEnd", .62f)
            val horizon = l.num("horizon", .5f) * H
            val rise = lerp(l.num("riseMin", 20f), l.num("rise", 150f), sin(min(1f, s.t / .5f) * PI.toFloat()).coerceAtLeast(0f))
            val x = lerp(x0, x1, (s.t / tEnd).coerceIn(0f, 1f)) * W * span * .7f + px(l.depth)
            return x to (horizon - rise * (1f - ov * .4f))
        }

        fun glow(l: LayerSpec) {
            val (x, y) = celestial(l)
            sunX = x
            val color = col(l.str("color", "glow"))
            val a = l.num("alpha", .5f) * (1f - ov * .7f) * dim
            val r = l.num("radius", 300f)
            fill.shader = RadialGradient(x, y, r, color.toArgb(a), color.toArgb(0f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W, l.num("horizon", .5f) * H + 40f, fill); fill.shader = null
        }

        fun disc(l: LayerSpec) {
            if (sample.value(l.str("hideBelowValue", "stars")) >= .5f || ov >= .6f) return
            val (x, y) = celestial(l)
            fill.color = col(l.str("color", "glow")).toArgb(.95f * (1f - ov))
            c.drawCircle(x, y, l.num("radius", 15f), fill)
        }

        fun clouds(l: LayerSpec) {
            if (wx.cloud < .05f) return
            val n = l.num("count", 6f).toInt(); val seed = l.num("seed", 31f)
            val region = l.num("region", .5f) * H; val drift = l.num("drift", 4f)
            val cc = Lab.mix(col(l.str("topColor", "skyTop")), col(l.str("bottomColor", "skyBot")), .35f)
            val a = l.num("alpha", .5f) * wx.cloud
            for (i in 0 until n) {
                val cx = wrap(hash(i.toFloat(), seed) * 1.5f * W + tt * drift * mot * (.4f + windA) + px(l.depth), W * 1.7f) - W * .2f
                val cy = 40f + hash(i.toFloat(), seed + 1f) * region * .7f
                val w = 170f + hash(i.toFloat(), seed + 2f) * 150f
                c.save(); c.scale(1f, .28f, cx, cy)
                fill.shader = RadialGradient(cx, cy, w, cc.toArgb(a), cc.toArgb(0f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, w, fill); fill.shader = null
                c.restore()
            }
        }

        fun birds() {
            val p = s.birdsProgress
            if (p < 0f || p > 1f) return
            val d = s.birdsDir
            line.color = android.graphics.Color.argb(215, 24, 28, 36); line.strokeWidth = 1.4f
            val y0 = H * .17f
            for (i in 0 until 5) {
                val x = (if (d > 0) -40f + p * (W + 80f) else W + 40f - p * (W + 80f)) - i * 22f * d
                val y = y0 + sin(p * 6f + i) * 5f + i * 7f
                val fl = sin(tt * 7f + i) * 4f
                tmp.reset(); tmp.moveTo(x - 7f, y - fl * .4f)
                tmp.quadTo(x - 3f, y - 3f - fl, x, y); tmp.quadTo(x + 3f, y - 3f - fl, x + 7f, y - fl * .4f)
                c.drawPath(tmp, line)
            }
        }

        private fun ridgeFor(l: LayerSpec): Ridge = ridges.getOrPut(l.id) {
            val base = l.num("base", .5f) * H; val amp = l.num("amp", 40f); val freq = l.num("freq", .01f); val seed = l.num("seed", 1f)
            val bottom = l.num("fill", 1f) * H
            val x0 = -40f; val step = 5f; val xEnd = W * span + 40f
            val p = Path(); p.moveTo(x0, bottom)
            var x = x0
            while (x <= xEnd) { p.lineTo(x, base - amp * fbm(x * freq, seed)); x += step }
            p.lineTo(xEnd, bottom); p.close()
            val trees = if (l.num("trees", 0f) > 0f) {
                val list = ArrayList<Float>(); var tx = -20f
                while (tx < W * span + 20f) {
                    list += tx; list += 5f + hash(tx, 7f) * 3f
                    list += 16f + hash(tx, 6f) * 26f + 10f * fbm(tx * .02f, 2f)
                    list += base - amp * fbm(tx * freq, seed)
                    tx += 7f + hash(tx, 4f) * 6f
                }
                list.toFloatArray()
            } else FloatArray(0)
            Ridge(p, x0, step, trees)
        }

        fun ridge(l: LayerSpec) {
            val r = ridgeFor(l)
            val base = col(l.str("color"))
            val dx = px(l.depth)
            c.save(); c.translate(dx, 0f)
            if (l.num("lit", 0f) > 0f) {
                val dir = if (sunX < W * .6f) 220f else -220f
                fill.shader = LinearGradient(sunX - dx, 0f, sunX - dx + dir, 0f, Lab(base.l * 1.12f, base.a, base.b).toArgb(), Lab(base.l * .78f, base.a, base.b).toArgb(), Shader.TileMode.CLAMP)
            } else fill.color = base.toArgb()
            c.drawPath(r.path, fill); fill.shader = null
            if (l.str("edgeColor").isNotEmpty()) {
                line.color = col(l.str("edgeColor")).toArgb(.1f + .1f * (1f - sample.value("stars")))
                line.strokeWidth = 1.2f
                c.drawPath(r.path, line)
            }
            if (r.trees.isNotEmpty()) {
                val sway = if (lvl >= 1) windA * 1.2f * mot * l.wind else 0f
                tmp.reset(); var i = 0
                while (i < r.trees.size) {
                    val x = r.trees[i]; val hw = r.trees[i + 1]; val h = r.trees[i + 2]; val b = r.trees[i + 3]
                    val off = if (sway != 0f) sin(tt * .9f + x) * sway else 0f
                    tmp.moveTo(x - hw, b + 4f); tmp.lineTo(x + off, b - h); tmp.lineTo(x + hw, b + 4f)
                    i += 4
                }
                fill.color = base.toArgb(); c.drawPath(tmp, fill)
            }
            if (l.num("haze", 0f) > 0f) {
                val hc = col(l.str("hazeColor", "skyBot"))
                val y0 = l.num("base", .5f) * H - 70f
                val a = (l.num("haze", .35f) + fogA * .4f).coerceIn(0f, 1f)
                fill.shader = LinearGradient(0f, y0, 0f, y0 + 150f, hc.toArgb(0f), hc.toArgb(a), Shader.TileMode.CLAMP)
                c.drawRect(-dx - 50f, y0, W * span + 50f - dx, y0 + 150f, fill); fill.shader = null
            }
            c.restore()
        }

        var waterY = 0f
        fun water(l: LayerSpec) {
            val y = l.num("y", .545f) * H; waterY = y
            val topC = col(l.str("topColor")); val botC = col(l.str("bottomColor"))
            val mid = Lab.mix(topC, botC, .7f)
            fill.shader = LinearGradient(0f, y, 0f, H, intArrayOf(topC.toArgb(), mid.toArgb(), Lab(botC.l * .7f, botC.a, botC.b).toArgb()), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, y, W, H, fill); fill.shader = null
            val m = l.str("mirror")
            val src = dna.world.layers.firstOrNull { it.id == m }
            if (src != null) {
                val r = ridgeFor(src)
                c.save(); c.clipRect(0f, y, W, H); c.translate(px(src.depth + .1f), 0f); c.translate(0f, y * 2f); c.scale(1f, -1f)
                fill.color = col(src.str("color")).toArgb(l.num("mirrorAlpha", .32f)); c.drawPath(r.path, fill); c.restore()
            }
            if (lvl >= 1 && l.num("shimmer", 0f) > 0f) {
                val n = (l.num("shimmer", 40f) * min(1f, s.particleScale + .2f)).toInt()
                val gc = col(l.str("shimmerColor", "glow")); line.strokeWidth = 1f
                for (i in 0 until n) {
                    val yy = y + 14f + Math.pow(hash(i.toFloat(), 41f).toDouble(), 1.5).toFloat() * (H - y - 120f)
                    val d = (yy - y) / (H - y)
                    val xx = wrap(hash(i.toFloat(), 42f) * W * 1.3f + tt * (6f + d * 22f) * (windA + .15f) * mot * (if (i % 2 == 0) -.6f else 1f) + px(.6f + d * .3f), W * 1.3f)
                    line.color = gc.toArgb(.09f + .1f * d)
                    c.drawLine(xx, yy, xx + 14f + d * 50f, yy, line)
                }
            }
        }

        fun building(l: LayerSpec) {
            val cx = l.num("x", 1f) * W + px(l.depth); val cy = l.num("y", .53f) * H
            fill.color = col(l.str("color")).toArgb()
            tmp.reset(); tmp.moveTo(cx - 20f, cy); tmp.lineTo(cx - 20f, cy - 14f); tmp.lineTo(cx, cy - 26f); tmp.lineTo(cx + 20f, cy - 14f); tmp.lineTo(cx + 20f, cy); tmp.close()
            c.drawPath(tmp, fill)
            val warm = max(sample.value(l.str("warmValue", "warm")), s.lightsBoost)
            val pulse = if (s.charging && lvl >= 1) .82f + .18f * sin(tt * 1.4f) else 1f
            val a = (max(warm, if (s.charging) .5f else 0f) * pulse * dim).coerceIn(0f, 1f)
            if (a < .02f) return
            fill.shader = RadialGradient(cx + 4f, cy - 8f, 60f, android.graphics.Color.argb((.5f * a * 255).toInt(), 255, 196, 120), android.graphics.Color.argb(0, 255, 196, 120), Shader.TileMode.CLAMP)
            c.drawCircle(cx + 4f, cy - 8f, 60f, fill); fill.shader = null
            fill.color = android.graphics.Color.argb((a * 242).toInt(), 255, 214, 150)
            c.drawRect(cx - 4f, cy - 12f, cx + 4f, cy - 5f, fill); c.drawRect(cx + 9f, cy - 12f, cx + 14f, cy - 5f, fill)
            if (waterY > 0f) {
                fill.shader = LinearGradient(0f, waterY, 0f, waterY + 150f, android.graphics.Color.argb((.45f * a * 255).toInt(), 255, 190, 110), android.graphics.Color.argb(0, 255, 190, 110), Shader.TileMode.CLAMP)
                for (i in 0 until 14) {
                    val yy = waterY + 4f + i * 10f; val hw = (10f - i * .5f) * (1f + sin(tt * 1.2f + i) * .12f * mot)
                    c.drawRect(cx - hw, yy, cx + hw, yy + 2.5f, fill)
                }
                fill.shader = null
            }
        }

        fun ripples(l: LayerSpec) {
            if (wx.rain < .05f) return
            val dt = (s.seconds - lastSeconds).coerceIn(0f, .1f)
            for (r in ripples) r.age += dt
            ripples.removeAll { it.age > 1.4f }
            val yFrom = l.num("yFrom", .575f) * H
            if (ripples.size < 26 && hash(tt * 31f, 5f) < wx.rain * .5f * s.particleScale) {
                ripples += Ripple(hash(tt * 17f, 9f) * W, yFrom + 20f + hash(tt * 13f, 3f) * (H - yFrom - 200f), 0f)
            }
            val cc = col(l.str("color", "glow"))
            line.strokeWidth = 1f
            for (r in ripples) {
                line.color = cc.toArgb((1f - r.age / 1.4f) * .28f * wx.rain)
                rect.set(r.x + px(.6f) - r.age * 18f, r.y - r.age * 5f, r.x + px(.6f) + r.age * 18f, r.y + r.age * 5f)
                c.drawOval(rect, line)
            }
        }

        fun fog(l: LayerSpec) {
            if (fogA < .03f) return
            val n = l.num("bands", 4f).toInt(); val y0 = l.num("y", .4f)
            val fc = col(l.str("color", "skyBot"))
            for (i in 0 until n) {
                val y = H * (y0 + i * .07f)
                val x = wrap((if (lvl > 0) tt * (4f + i * 2f) * (windA + .2f) * mot else 0f) + i * 140f + px(.5f + i * .1f) * 1f, W * 1.6f) - W * .3f
                c.save(); c.scale(1f, .2f, 0f, y)
                fill.shader = RadialGradient(x + W * .4f, y, W * .8f, fc.toArgb(.55f * fogA), fc.toArgb(0f), Shader.TileMode.CLAMP)
                c.drawRect(x - W * .4f, y - W, x + W * 1.2f, y + W, fill); fill.shader = null
                c.restore()
            }
            fill.color = fc.toArgb(.2f * fogA); c.drawRect(0f, H * .28f, W, H * .78f, fill)
        }

        fun rain(l: LayerSpec) {
            if (wx.rain < .04f || lvl < 1) return
            val n = particles(l.num("count", 150f), wx.rain)
            if (n == 0) return
            ensurePts(n * 4)
            val sl = 2f + windA * 7f
            var o = 0
            for (i in 0 until n) {
                val z = .4f + hash(i.toFloat(), 9f) * .8f
                val sp = 520f * z * mot
                val y = wrap(hash(i.toFloat(), 5f) * H + tt * sp, H + 60f) - 30f
                val x = wrap(hash(i.toFloat(), 2f) * (W + 40f) - tt * sp * .18f * (1f + windA), W + 40f) - 20f
                pts[o++] = x; pts[o++] = y; pts[o++] = x - sl * z; pts[o++] = y + 14f * z
            }
            val a = l.num("alpha", .24f * wx.rain + .05f).let { if (l.params.containsKey("alpha")) it * wx.rain else it }
            line.color = col(l.str("color", "glow")).toArgb(a.coerceIn(0f, 1f)); line.strokeWidth = 1f
            c.drawLines(pts, 0, o, line)
        }

        fun snow(l: LayerSpec) {
            if (wx.snow < .04f || lvl < 1) return
            val n = particles(l.num("count", 110f), wx.snow)
            if (n == 0) return
            ensurePts(n * 2)
            var o = 0
            for (i in 0 until n) {
                val z = .4f + hash(i.toFloat(), 9f) * .8f
                val y = wrap(hash(i.toFloat(), 5f) * H * 1.3f + tt * (20f + 30f * z) * mot, H + 20f) - 10f
                val x = wrap(hash(i.toFloat(), 2f) * W * .9f + sin(tt * .6f + i) * 14f * mot, W)
                pts[o++] = x; pts[o++] = y
            }
            dots.color = android.graphics.Color.argb(205, 255, 255, 255); dots.strokeWidth = 2.6f
            c.drawPoints(pts, 0, o, dots)
        }

        fun dust(l: LayerSpec) {
            if (lvl < 1) return
            val n = particles(l.num("count", 70f), 1f)
            ensurePts(n * 4)
            var o = 0
            for (i in 0 until n) {
                val z = .3f + hash(i.toFloat(), 13f)
                val x = wrap(hash(i.toFloat(), 11f) * W * 1.4f + tt * (14f + windA * 70f) * z * mot + px(.7f), W * 1.4f + 40f) - 20f
                val y = H * .45f + hash(i.toFloat(), 12f) * H * .5f + sin(tt * .5f + i) * 6f * mot
                pts[o++] = x; pts[o++] = y; pts[o++] = x + 2f + z * 3f; pts[o++] = y
            }
            line.color = col(l.str("color", "glow")).toArgb((.1f + windA * .1f).coerceAtMost(.3f)); line.strokeWidth = 1.2f
            c.drawLines(pts, 0, o, line)
        }

        fun beams(l: LayerSpec) {
            val a = sample.value(l.str("alphaValue", "beamA")) * (1f - ov * .75f) * dim
            val beam = raw(l.str("color", "beam"))
            val drift = if (lvl >= 1) sin(tt * .05f) * 10f * mot else 0f
            val sx = lerp(-.55f, .35f, ((s.t - .02f) / .55f).coerceIn(0f, 1f)) * W + px(l.depth) * .6f
            c.save(); c.translate(sx + drift, 0f); c.skew(-.42f, 0f)
            val bars = l.num("bars", 5f).toInt()
            for (i in 0 until bars) {
                val x = 70f + i * 118f; val w = 44f + i * 3f
                fill.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(beam.toArgb(a * .55f), beam.toArgb(a * .32f), beam.toArgb(0f)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(x, -40f, x + w, H + 80f, fill)
            }
            fill.shader = null; c.restore()
        }

        fun washes(l: LayerSpec) {
            val a = sample.value(l.str("alphaValue", "beamA")) * (1f - ov * .75f) * dim
            val beam = raw(l.str("color", "beam"))
            for (i in 0 until 2) {
                val cx = (if (i == 1) .75f else .2f) * W + sin(tt * .04f + i * 2f) * 30f * mot + px(.3f)
                val cy = (if (i == 1) .7f else .25f) * H
                fill.shader = RadialGradient(cx, cy, 380f, beam.toArgb(a * .25f), beam.toArgb(0f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W, H, fill); fill.shader = null
            }
        }

        fun shimmer(l: LayerSpec) {
            if (lvl < 2 || sample.value("stars") >= .4f) return
            val y0 = l.num("y", .5f) * H
            val gc = col(l.str("color", "glow")); line.strokeWidth = 1f
            for (i in 0 until 12) {
                val y = y0 + 4f + i * 5f
                line.color = gc.toArgb(.05f * (1f - i / 12f) * (1f - ov))
                tmp.reset(); tmp.moveTo(0f, y)
                var x = 14f
                while (x <= W) { tmp.lineTo(x, y + sin(x * .05f + tt * 1.4f + i) * 1.4f * mot); x += 14f }
                c.drawPath(tmp, line)
            }
        }

        fun vignette(l: LayerSpec) {
            val h = l.num("height", .3f) * H
            fill.shader = LinearGradient(0f, H - h, 0f, H, android.graphics.Color.argb(0, 0, 0, 0), android.graphics.Color.argb((l.num("alpha", .22f) * dim * 255).toInt(), 0, 0, 0), Shader.TileMode.CLAMP)
            c.drawRect(0f, H - h, W, H, fill); fill.shader = null
        }

        fun bitmap(l: LayerSpec) {
            val bmp = s.assets(l.str("ref")) ?: return
            val scale = max(H / bmp.height, W * span / bmp.width)
            val dx = px(l.depth)
            rect.set(dx, 0f, dx + bmp.width * scale, bmp.height * scale)
            fill.alpha = (l.num("alpha", 1f) * 255).toInt()
            c.drawBitmap(bmp, null, rect, fill); fill.alpha = 255
        }

        private fun ensurePts(n: Int) { if (pts.size < n) pts = FloatArray(n + 64) }
    }

}
