package com.wakeup.launcher.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural ambient beds (rain, wind, water). Nothing is bundled: the sound is synthesised from filtered
 * noise on a small worker thread, so there is no audio asset to license and no file to ship.
 *
 * [start] and [stop] are called by the host according to AudioPolicy. [stop] releases the AudioTrack and ends
 * the thread, it does not merely mute, so nothing keeps the audio path open while the device is locked.
 */
class AmbientEngine {
    @Volatile private var running = false
    @Volatile private var kind = "none"
    @Volatile private var volume = 0f
    @Volatile private var rain = 0f
    @Volatile private var wind = 0f
    private var thread: Thread? = null
    val isRunning get() = running

    fun update(volume: Float, rain: Float, wind: Float) { this.volume = volume.coerceIn(0f, 1f); this.rain = rain; this.wind = wind }

    @Synchronized fun start(kind: String) {
        if (kind == "none") { stop(); return }
        if (running && this.kind == kind) return
        stop()
        this.kind = kind
        running = true
        thread = Thread({ run(kind) }, "wakeup-ambient").also { it.priority = Thread.NORM_PRIORITY - 1; it.start() }
    }

    @Synchronized fun stop() {
        running = false
        thread?.let { runCatching { it.join(500) } }
        thread = null
    }

    private fun run(kind: String) {
        val rate = 22050
        val chunk = 2048
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuf, chunk * 4))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) { running = false; return } // audio unavailable: silently carry on
        val rnd = Random(System.nanoTime())
        val buf = ShortArray(chunk)
        var lp1 = 0f; var lp2 = 0f; var hp = 0f; var lastIn = 0f
        var lfo = 0f; var gust = 0f; var gustTarget = 0f
        var drop = 0f
        var fade = 0f
        try {
            track.play()
            while (running) {
                val vol = volume; val rainAmt = rain; val windAmt = wind
                for (i in buf.indices) {
                    val n = rnd.nextFloat() * 2f - 1f
                    var s = 0f
                    when (kind) {
                        "rain" -> {
                            lp1 += (n - lp1) * .55f              // soft body
                            val hiss = n - lastIn; lastIn = n    // bright sheet
                            hp += (hiss - hp) * .3f
                            if (rnd.nextFloat() < .0009f + rainAmt * .006f) drop = (.5f + rnd.nextFloat() * .5f)
                            drop *= .97f
                            s = (lp1 * .55f + hp * .18f) * (.35f + rainAmt * .9f) + drop * (rnd.nextFloat() - .5f) * .9f
                        }
                        "wind" -> {
                            lp1 += (n - lp1) * .02f
                            lp2 += (lp1 - lp2) * .05f
                            lfo += 2f * PI.toFloat() * .07f / rate
                            if (rnd.nextFloat() < .00004f) gustTarget = rnd.nextFloat()
                            gust += (gustTarget - gust) * .00015f
                            s = lp2 * 14f * (.35f + .35f * sin(lfo) + gust * .8f + windAmt * .4f)
                        }
                        else -> { // water
                            lp1 += (n - lp1) * .08f
                            lp2 += (lp1 - lp2) * .08f
                            lfo += 2f * PI.toFloat() * .2f / rate
                            s = lp2 * 9f * (.6f + .4f * sin(lfo))
                        }
                    }
                    fade += ((if (running) 1f else 0f) - fade) * .0004f
                    buf[i] = (s * vol * fade * 9000f).coerceIn(-12000f, 12000f).toInt().toShort()
                }
                track.write(buf, 0, buf.size)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { track.pause(); track.flush() }
            runCatching { track.release() }
        }
    }
}
