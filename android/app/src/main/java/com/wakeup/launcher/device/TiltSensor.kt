package com.wakeup.launcher.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.PI
import kotlin.math.abs

/**
 * Smoothed left/right tilt in -1..1, measured as how far the phone has turned from where it was
 * resting a moment ago. A slowly following baseline means natural holding angles produce no bias,
 * and a dead zone plus a low-pass filter keep it free of jitter.
 *
 * The sensor is registered only between [start] and [stop]; the host calls those when tilt is both
 * enabled and visible. Uses the game rotation vector (gyro + accelerometer, no magnetometer drift).
 */
class TiltSensor(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rot = FloatArray(9)
    private val ori = FloatArray(3)
    private var baseYaw = Float.NaN
    private var baseRoll = Float.NaN
    private var smoothed = 0f
    var tilt = 0f; private set
    val available get() = sensor != null
    private var running = false

    fun start() {
        if (running || sensor == null) return
        running = true; baseYaw = Float.NaN
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        running = false
        sm.unregisterListener(this)
        smoothed = 0f; tilt = 0f
    }

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        SensorManager.getOrientation(rot, ori)
        val yaw = ori[0]; val roll = ori[2]
        if (baseYaw.isNaN()) { baseYaw = yaw; baseRoll = roll }
        val dy = angleDiff(yaw, baseYaw); val dr = angleDiff(roll, baseRoll)
        // baseline follows slowly so the resting pose becomes "centre"
        baseYaw = wrapPi(baseYaw + dy * .01f); baseRoll = wrapPi(baseRoll + dr * .01f)
        val deg = Math.toDegrees((dy + dr * .6f).toDouble()).toFloat()
        val raw = (deg / 16f).coerceIn(-1f, 1f)
        smoothed += (raw - smoothed) * .1f
        tilt = if (abs(smoothed) < .05f) 0f else smoothed
    }

    private fun wrapPi(a: Float): Float { var x = a; while (x > PI) x -= (2 * PI).toFloat(); while (x < -PI) x += (2 * PI).toFloat(); return x }
    private fun angleDiff(a: Float, b: Float) = wrapPi(a - b)

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
}
