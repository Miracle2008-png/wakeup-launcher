package com.wakeup.launcher.scene

import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import com.wakeup.dna.AudioPolicy
import com.wakeup.dna.BuiltInThemes
import com.wakeup.dna.LightModel
import com.wakeup.dna.Modes
import com.wakeup.dna.MomentDirector
import com.wakeup.dna.QualityInputs
import com.wakeup.dna.QualityMode
import com.wakeup.dna.QualityPolicy
import com.wakeup.dna.QualityProfile
import com.wakeup.dna.ThemeDna
import com.wakeup.dna.Weather
import com.wakeup.launcher.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Owns the living world while the launcher is visible: resolves the effective theme, decides the intensity
 * level through the quality governor, drives frames at the world's own cadence, schedules rare moments,
 * and starts or stops sensors and ambient audio according to policy. Everything here stops in [pause].
 */
class SceneController(private val g: Graph) {
    val state = SceneState()
    val renderer: WorldRenderStrategy = CanvasWorldRenderer()
    private var view: WorldView? = null
    private val choreographer = Choreographer.getInstance()

    private val _profile = MutableStateFlow(QualityPolicy.decide(QualityMode.ADAPTIVE, QualityInputs()))
    val profile: StateFlow<QualityProfile> get() = _profile
    private val _theme = MutableStateFlow<ThemeDna?>(null)
    val theme: StateFlow<ThemeDna?> get() = _theme
    private val _light = MutableStateFlow(state.light)
    val light: StateFlow<LightModel> get() = _light
    private val _level = MutableStateFlow(2)
    val level: StateFlow<Int> get() = _level

    /** Studio and Theme Browser preview without touching saved settings. */
    var previewTheme: ThemeDna? = null; set(v) { field = v; resolveTheme() }
    var previewT: Float? = null
    var previewWeather: String? = null
    var interacting = false

    var resumed = false; private set
    var cinema = false; set(v) { field = v; recompute() }
    private var running = false
    private var lastNanos = 0L
    private var lastDrawNanos = 0L
    private var frameMsEma = 8f
    private var director = MomentDirector(emptyList())
    private var momentStartMs = 0L
    private var momentDurMs = 1L
    private var momentType = ""
    private var lastPoll = 0L
    private var lastLightEmit = 0L
    private var lastAudioCheck = 0L
    private var reducedSystem = false
    private var dirty = true
    private var started = SystemClock.elapsedRealtime()

    private val tick = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) { if (running) { frame(frameTimeNanos); choreographer.postFrameCallback(this) } }
    }

    init {
        g.scope.launch { g.settings.state.collect { resolveTheme(); recompute(); g.weather.recompute() } }
        g.scope.launch { g.themes.themes.collect { resolveTheme() } }
        g.scope.launch { g.device.state.collect { recompute() } }
    }

    fun attach(v: WorldView?) { view = v; requestFrame() }

    fun resume() {
        if (resumed) return
        resumed = true
        reducedSystem = runCatching { Settings.Global.getFloat(g.app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
        g.device.start(); g.weather.start(); g.apps.start()
        recompute(); startLoop()
    }

    fun pause() {
        if (!resumed) return
        resumed = false
        stopLoop()
        g.tilt.stop(); g.ambient.stop()
        g.weather.stop(); g.device.stop(); g.apps.stop()
        recompute()
    }

    fun requestFrame() { dirty = true }

    private fun startLoop() { if (running) return; running = true; lastNanos = 0L; choreographer.postFrameCallback(tick) }
    private fun stopLoop() { running = false; choreographer.removeFrameCallback(tick) }

    // ---- resolution ----
    private fun resolveTheme() {
        val s = g.settings.value
        val base = previewTheme ?: g.themes.find(s.themeId)?.dna ?: g.themes.find("linen")?.dna ?: runCatching { BuiltInThemes.load("linen") }.getOrNull() ?: return
        val dna = runCatching { Modes.apply(base, s.mode) }.getOrDefault(base)
        val prev = _theme.value
        if (prev == null || prev != dna) {
            _theme.value = dna
            state.dna = dna
            state.assets = { ref -> g.themes.asset(base.id, ref) }
            director = MomentDirector(dna.moments)
        }
        dirty = true
    }

    fun reduced(): Boolean = when (g.settings.value.reducedMotion) { 1 -> true; 2 -> false; else -> reducedSystem }

    private fun recompute() {
        val s = g.settings.value
        val d = g.device.state.value
        val dna = _theme.value ?: return
        val inputs = QualityInputs(
            thermal = d.thermal, batteryPct = d.batteryPct, charging = d.charging, powerSave = d.powerSave, lowMemory = d.lowMemory,
            frameMsEma = frameMsEma, refreshHz = g.refreshHz(), reducedMotion = reduced(), cinema = cinema, screenOn = d.screenOn, resumed = resumed,
        )
        val p = QualityPolicy.decide(QualityMode.parse(s.quality), inputs)
        if (p != _profile.value) _profile.value = p
        val base = if (s.level >= 0) s.level else dna.intensity.default
        val lvl = when {
            p.suspended || p.maxLevel == 0 -> 0
            cinema -> p.maxLevel
            else -> minOf(base, dna.intensity.ceiling, p.maxLevel)
        }
        if (lvl != _level.value) _level.value = lvl
        state.level = lvl
        state.parallax = !reduced() && p.maxLevel > 0
        state.particleScale = p.particleScale
        state.charging = d.charging
        state.lowBattery = d.batteryPct < 15 && !d.charging
        updateSensors(lvl, s.tilt, d.screenOn)
        updateAudio(force = true)
        dirty = true
    }

    private fun updateSensors(lvl: Int, enabled: Boolean, screenOn: Boolean) {
        if (resumed && screenOn && enabled && lvl >= 3 && g.tilt.available) g.tilt.start() else g.tilt.stop()
    }

    private fun updateAudio(force: Boolean = false) {
        val s = g.settings.value; val dna = _theme.value ?: return; val d = g.device.state.value
        val themeVol = s.themeVolumes[dna.id] ?: dna.sound.volume
        val ins = AudioPolicy.Inputs(
            enabled = s.ambientSound && dna.sound.ambient != "none", masterVolume = s.masterVolume, themeVolume = themeVol,
            locked = d.locked, screenOn = d.screenOn, launcherResumed = resumed,
            otherMediaPlaying = g.device.musicPlaying() && !g.ambient.isRunning, inCall = g.inCall(),
        )
        if (AudioPolicy.mayPlay(ins)) {
            val boost = if (d.headphones) 1.15f else 1f
            g.ambient.update(AudioPolicy.effectiveVolume(ins) * boost, state.weather.rain, state.weather.wind)
            g.ambient.start(dna.sound.ambient)
        } else g.ambient.stop()
    }

    // ---- frame ----
    private fun frame(nanos: Long) {
        val dt = if (lastNanos == 0L) .016f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, .1f)
        if (lastNanos != 0L) frameMsEma += (dt * 1000f - frameMsEma) * .03f
        lastNanos = nanos
        val p = _profile.value
        if (p.suspended) return
        val nowMs = System.currentTimeMillis()
        val lvl = state.level

        // cadence: the world runs at its own rate; gestures and changes draw immediately
        val interval = if (lvl == 0) Long.MAX_VALUE else 1_000_000_000L / p.worldFps.coerceAtLeast(1)
        val due = dirty || interacting || (lvl > 0 && nanos - lastDrawNanos >= interval - 2_000_000L)
        if (nowMs - lastPoll > 1000) { lastPoll = nowMs; slowTick(nowMs, lvl) }
        if (!due) return

        state.seconds = ((SystemClock.elapsedRealtime() - started) / 1000f) % 3600f
        state.t = previewT ?: realT()
        val target = previewWeather?.let { Weather.PRESETS[it] } ?: g.weather.report.value.vector
        val elapsed = if (lastDrawNanos == 0L) .016f else ((nanos - lastDrawNanos) / 1e9f).coerceIn(.016f, .5f)
        val before = state.weather
        state.weather = before.approach(target, elapsed, if (reduced()) .3f else 2f)
        val moving = Math.abs(state.weather.rain - target.rain) + Math.abs(state.weather.fog - target.fog) + Math.abs(state.weather.cloud - target.cloud) +
            Math.abs(state.weather.snow - target.snow) + Math.abs(state.weather.wind - target.wind) > .004f
        state.tiltX = if (state.level >= 3) g.tilt.tilt else 0f
        advanceMoment(nowMs)
        state.musicPlaying = false
        lastDrawNanos = nanos
        view?.invalidate()
        dirty = moving // keep drawing until a weather change has settled, even at Still

        if (nowMs - lastLightEmit > 250) {
            lastLightEmit = nowMs
            val l = state.light
            val cur = _light.value
            if (cur.darkInk != l.darkInk || Math.abs(cur.luminance - l.luminance) > .008f) _light.value = l
        }
    }

    private fun slowTick(nowMs: Long, lvl: Int) {
        updateAudio()
        val dna = _theme.value ?: return
        val fired = director.poll(nowMs, lvl, interacting, reduced(), state.t)
        if (fired != null) startMoment(fired.type, fired.durationS, nowMs)
        g.automation.evaluate()
    }

    fun playMoment(type: String = "birds") {
        val dna = _theme.value ?: return
        val spec = dna.moments.firstOrNull { it.type == type } ?: dna.moments.firstOrNull() ?: return
        val fired = director.poll(System.currentTimeMillis(), maxOf(state.level, 2), false, reduced(), state.t, force = true)
        if (fired != null) startMoment(fired.type, fired.durationS, System.currentTimeMillis()) else startMoment(spec.type, spec.durationS, System.currentTimeMillis())
    }

    private fun startMoment(type: String, durS: Float, nowMs: Long) {
        momentType = type; momentStartMs = nowMs; momentDurMs = (durS * 1000).toLong().coerceAtLeast(1)
        state.birdsDir = if (nowMs % 2L == 0L) 1 else -1
    }

    private fun advanceMoment(nowMs: Long) {
        if (momentType.isEmpty()) { state.birdsProgress = -1f; state.lightsBoost = 0f; return }
        val p = (nowMs - momentStartMs).toFloat() / momentDurMs
        if (p >= 1f) { momentType = ""; state.birdsProgress = -1f; state.lightsBoost = 0f; return }
        when (momentType) {
            "birds" -> state.birdsProgress = p
            "lights-on" -> state.lightsBoost = (Math.sin(p * Math.PI)).toFloat().coerceIn(0f, 1f)
        }
    }

    private fun realT(): Float {
        val c = Calendar.getInstance()
        val h = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f + c.get(Calendar.SECOND) / 3600f
        return ((h - 6f + 24f) % 24f) / 24f
    }

    fun currentT(): Float = state.t
}
