package com.wakeup.launcher.device

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DeviceSnapshot(
    val batteryPct: Int = 100,
    val charging: Boolean = false,
    /** PowerManager.THERMAL_STATUS_*; 0 is none. */
    val thermal: Int = 0,
    val powerSave: Boolean = false,
    val screenOn: Boolean = true,
    val locked: Boolean = false,
    val headphones: Boolean = false,
    val lowMemory: Boolean = false,
)

/**
 * Watches device state only while the launcher is on screen. Every listener registered in [start]
 * is removed in [stop]; nothing here runs in the background.
 */
class DeviceMonitor(private val ctx: Context) {
    private val _state = MutableStateFlow(DeviceSnapshot())
    val state: StateFlow<DeviceSnapshot> get() = _state

    private val power = ctx.getSystemService(PowerManager::class.java)
    private val keyguard = ctx.getSystemService(KeyguardManager::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED -> readBattery(i)
                Intent.ACTION_SCREEN_OFF -> _state.value = _state.value.copy(screenOn = false, locked = true)
                Intent.ACTION_SCREEN_ON -> _state.value = _state.value.copy(screenOn = true, locked = keyguard.isKeyguardLocked)
                Intent.ACTION_USER_PRESENT -> _state.value = _state.value.copy(locked = false)
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> _state.value = _state.value.copy(powerSave = power.isPowerSaveMode)
            }
        }
    }

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status -> _state.value = _state.value.copy(thermal = status) }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refreshHeadphones()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refreshHeadphones()
    }

    fun start() {
        if (started) return
        started = true
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT); addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val sticky = ctx.registerReceiver(receiver, f)
        sticky?.let { readBattery(it) }
        runCatching { power.addThermalStatusListener(ctx.mainExecutor, thermalListener) }
        audio.registerAudioDeviceCallback(deviceCallback, null)
        _state.value = _state.value.copy(
            thermal = runCatching { power.currentThermalStatus }.getOrDefault(0),
            powerSave = power.isPowerSaveMode,
            screenOn = power.isInteractive,
            locked = keyguard.isKeyguardLocked,
        )
        refreshHeadphones()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { ctx.unregisterReceiver(receiver) }
        runCatching { power.removeThermalStatusListener(thermalListener) }
        runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
    }

    fun trimMemory(low: Boolean) { _state.value = _state.value.copy(lowMemory = low) }

    /** True when something else is playing music; ambient sound yields to it. */
    fun musicPlaying(): Boolean = audio.isMusicActive

    private fun readBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else _state.value.batteryPct
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        _state.value = _state.value.copy(batteryPct = pct, charging = charging)
    }

    private fun refreshHeadphones() {
        val has = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        _state.value = _state.value.copy(headphones = has)
    }
}
