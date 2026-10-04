package com.wakeup.launcher.core

import kotlinx.serialization.Serializable

@Serializable
data class SettingsState(
    val themeId: String = "linen",
    /** pure | minimal | living | immersive | cinema | focus */
    val mode: String = "living",
    /** Persisted QualityMode name. */
    val quality: String = "ADAPTIVE",
    /** -1 follows the theme default, otherwise 0..3. */
    val level: Int = -1,
    /** 0 follow system, 1 force on, 2 force off. */
    val reducedMotion: Int = 0,

    val masterVolume: Float = 0.8f,
    val ambientSound: Boolean = false,
    val interactionSounds: Boolean = false,
    val themeVolumes: Map<String, Float> = emptyMap(),
    val haptics: Boolean = true,
    val hapticStrength: Float = 1f,
    val tilt: Boolean = true,

    /** Network weather is opt-in. When off, weather is whatever the user set by hand. */
    val liveWeather: Boolean = false,
    val placeName: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val manualWeather: String = "clear",

    /** theme | original | light | mono */
    val iconTreatment: String = "theme",
    val showLabels: Boolean = true,
    val onboarded: Boolean = false,

    /** Most recently launched apps, kept only on this device to fill the drawer's "Now" row. */
    val recents: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),

    /** Optional, user-supplied HTTPS endpoint for theme generation. Empty and disabled by default. */
    val aiEndpoint: String = "",
    val aiEnabled: Boolean = false,

    val currentSetupId: String = "",
    val nowCard: Boolean = true,
) {
    val hasPlace get() = placeName.isNotBlank() || lat != 0.0 || lon != 0.0
}
