package com.wakeup.launcher.scene

import android.content.Context
import android.graphics.Canvas
import android.view.View

/** Hosts the world renderer. It draws only when told to: no timers of its own, so a hidden launcher costs nothing. */
class WorldView(ctx: Context, private val scene: SceneState, private val renderer: WorldRenderStrategy) : View(ctx) {
    init { setWillNotDraw(false); isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        if (scene.dna == null || width == 0) return
        renderer.draw(canvas, width, height, scene)
    }
}
