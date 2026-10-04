package com.wakeup.launcher.scene

import android.graphics.Bitmap
import com.wakeup.dna.LightModel
import com.wakeup.dna.Sample
import com.wakeup.dna.ThemeDna
import com.wakeup.dna.TimeSampler
import com.wakeup.dna.Weather

/**
 * Everything the renderer needs for one frame. Mutated on the main thread by the scene driver and read
 * by the renderer in the same frame, so there is no cross-thread sharing and no copying.
 */
class SceneState {
    var dna: ThemeDna? = null
        set(v) { if (field !== v) { field = v; sampler = v?.let { TimeSampler(it.time.keys) }; themeVersion++ } }
    var sampler: TimeSampler? = null; private set
    var themeVersion = 0; private set

    /** 0 dawn .. 1 next dawn. */
    var t = 0.47f
    var weather = Weather()
    /** Page progress, 0 first page .. pages-1. Position-driven, never time-driven. */
    var pan = 0f
    var tiltX = 0f
    /** Effective intensity after the quality governor and user choice: 0 still .. 4 cinema. */
    var level = 2
    var parallax = true
    var particleScale = 1f
    var seconds = 0f
    var charging = false
    var lowBattery = false
    var musicPlaying = false
    var birdsProgress = -1f
    var birdsDir = 1
    var lightsBoost = 0f

    /** Image assets for `bitmap` layers, resolved by the content library. */
    var assets: (String) -> Bitmap? = { null }

    /** Result of the last frame, read by the UI to choose ink polarity and material tints. */
    var light: LightModel = LightModel(.3f, false, com.wakeup.dna.Lab(.4f, 0f, 0f), com.wakeup.dna.Lab(.5f, 0f, 0f))
    var lastSample: Sample? = null
}
