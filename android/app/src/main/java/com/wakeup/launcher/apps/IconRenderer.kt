package com.wakeup.launcher.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Draws every launcher icon onto one continuous-corner (superellipse) tile so the grid reads as a single
 * system whatever each app ships. The default treatment keeps the app's own artwork untouched.
 */
object IconRenderer {
    private const val N = 5.0 // superellipse exponent: higher is squarer, 5 reads as a smooth "squircle"
    private val pathCache = HashMap<Int, Path>()

    fun squircle(size: Int): Path = synchronized(pathCache) {
        pathCache.getOrPut(size) {
            val p = Path(); val h = size / 2f
            val steps = 96
            for (i in 0..steps) {
                val a = i.toDouble() / steps * Math.PI * 2
                val c = cos(a); val s = sin(a)
                val x = h + h * sign(c) * abs(c).pow(2.0 / N); val y = h + h * sign(s) * abs(s).pow(2.0 / N)
                if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
            }
            p.close(); p
        }
    }

    /** treatment: original | light | mono. Light's shading is added by the UI so it can follow the live light model. */
    fun render(icon: Drawable, size: Int, treatment: String, accent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.save(); c.clipPath(squircle(size))
        val adaptive = icon as? AdaptiveIconDrawable
        when {
            adaptive != null && treatment == "mono" && adaptive.monochrome != null -> {
                c.drawColor(Color.argb(255, 24, 28, 36))
                val m = adaptive.monochrome!!.mutate()
                m.colorFilter = PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN)
                val ex = (size * .25f).toInt(); m.setBounds(-ex, -ex, size + ex, size + ex); m.draw(c)
            }
            adaptive != null -> {
                val ex = (size * .25f).toInt()
                adaptive.background?.let { it.setBounds(-ex, -ex, size + ex, size + ex); it.draw(c) }
                adaptive.foreground?.let { it.setBounds(-ex, -ex, size + ex, size + ex); it.draw(c) }
            }
            else -> {
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (treatment == "mono") Color.argb(255, 24, 28, 36) else Color.argb(255, 240, 238, 232) }
                c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)
                val pad = (size * .14f).toInt()
                icon.setBounds(pad, pad, size - pad, size - pad); icon.draw(c)
            }
        }
        c.restore()
        return bmp
    }
}
