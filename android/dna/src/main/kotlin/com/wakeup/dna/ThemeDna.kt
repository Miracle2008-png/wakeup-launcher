package com.wakeup.dna

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Theme DNA v0.2. A theme is only ever this document plus optional validated image assets.
 * It contains no code; behaviour is described by a closed vocabulary of layer primitives
 * (see [LayerTypes]) that the renderer implements natively.
 */
@Serializable
data class ThemeDna(
    val dna: String = DNA_VERSION,
    val id: String,
    val name: String,
    val version: Int = 1,
    val describe: String = "",
    val parent: String? = null,
    val provenance: Provenance = Provenance(),
    val intensity: IntensitySpec = IntensitySpec(),
    val world: WorldSpec,
    val time: TimeSpec,
    val ui: UiSpec = UiSpec(),
    val motion: MotionSpec = MotionSpec(),
    val icons: IconSpec = IconSpec(),
    val sound: SoundSpec = SoundSpec(),
    val haptics: HapticSpec = HapticSpec(),
    val layout: LayoutSpec = LayoutSpec(),
    val grade: GradeSpec = GradeSpec(),
    val moments: List<MomentSpec> = emptyList(),
    val modes: Map<String, JsonObject> = emptyMap(),
    val requires: Requirements = Requirements(),
) {
    companion object {
        const val DNA_VERSION = "0.2"
        val json = Json {
            ignoreUnknownKeys = false
            isLenient = false
            encodeDefaults = true
            prettyPrint = true
        }
        fun parse(text: String): ThemeDna = json.decodeFromString(serializer(), text)
    }

    fun encode(): String = json.encodeToString(serializer(), this)
}

/** Every bundled or imported asset needs an answer to "where did this come from". */
@Serializable
data class Provenance(
    val author: String = "",
    val license: String = "",
    val source: String = "",
    val generated: Boolean = false,
    /** Third-party or reference-image-derived themes are personal-use and not publishable. */
    val personalUseOnly: Boolean = false,
)

@Serializable
data class IntensitySpec(val default: Int = 2, val ceiling: Int = 3)

@Serializable
data class WorldSpec(
    /** World width in screen widths. Above 1 turns page swipes into a pan across one scene. */
    val span: Float = 1f,
    val layers: List<LayerSpec>,
)

@Serializable
data class LayerSpec(
    val id: String,
    val type: String,
    /** 0 = infinitely far (sky), 1 = nearest. Scales how far the layer pans and tilts. */
    val depth: Float = 0.5f,
    val wind: Float = 0f,
    val minIntensity: Int = 0,
    /** Every animated layer must say why it moves. Enforced by the validator. */
    val reason: String = "",
    val params: JsonObject = JsonObject(emptyMap()),
) {
    fun num(key: String, d: Float): Float = (params[key] as? JsonPrimitive)?.floatOrNull ?: d
    fun str(key: String, d: String = ""): String = (params[key] as? JsonPrimitive)?.contentOrNull ?: d
    fun bool(key: String, d: Boolean = false): Boolean = (params[key] as? JsonPrimitive)?.booleanOrNull ?: d
}

@Serializable
data class TimeSpec(val keys: List<TimeKey>)

/** t runs 0..1 where 0 is dawn and 1 is the next dawn (hour = 6 + 24 t). */
@Serializable
data class TimeKey(
    val t: Float,
    val colors: Map<String, String>,
    val values: Map<String, Float> = emptyMap(),
)

@Serializable
data class UiSpec(
    val clock: ClockSpec = ClockSpec(),
    /** air | matte | frost | lacquer | etched, per element. */
    val materials: Map<String, String> = mapOf("clock" to "air", "widgets" to "matte", "dock" to "frost", "drawer" to "matte"),
    val accent: String = "glow",
)

@Serializable
data class ClockSpec(
    /** cinematic | minimal | editorial | practical */
    val style: String = "minimal",
    val weight: Int = 300,
    val seconds: Boolean = false,
)

@Serializable
data class MotionSpec(
    /** slide | pan | fade */
    val page: String = "slide",
    val reasons: Map<String, String> = emptyMap(),
)

@Serializable
data class IconSpec(
    /** original | light | mono */
    val treatment: String = "original",
)

@Serializable
data class SoundSpec(
    /** Procedurally synthesised bed: none | rain | wind | water. No audio files are bundled. */
    val ambient: String = "none",
    val volume: Float = 0.5f,
    val interaction: Boolean = false,
    /** Not configurable: a locked device never makes WakeUp sounds. Validation rejects true. */
    val whileLocked: Boolean = false,
)

@Serializable
data class HapticSpec(
    val tick: Boolean = true,
    val settle: Boolean = true,
    val confirm: Boolean = true,
    val soft: Boolean = true,
)

@Serializable
data class LayoutSpec(val columns: Int = 4, val rows: Int = 6, val dock: Int = 4)

/** Remix and Studio adjust the world through these knobs; the base layers are never rewritten. */
@Serializable
data class GradeSpec(
    val cold: Float = 0f,
    val fogBias: Float = 0f,
    val windScale: Float = 1f,
    val motionScale: Float = 1f,
)

@Serializable
data class MomentSpec(
    val id: String,
    /** birds | lights-on | light-breaks | train */
    val type: String,
    val cooldownMin: Int = 40,
    val probability: Float = 0.1f,
    val durationS: Float = 9f,
    val minIntensity: Int = 2,
)

@Serializable
data class Requirements(
    /** Capabilities the theme wants; the host may deny any of them and the theme still works. */
    val capabilities: List<String> = emptyList(),
    val minQuality: String = "battery-saver",
)

object LayerTypes {
    val ALL = setOf(
        "sky", "stars", "glow", "disc", "clouds", "ridge", "water", "building",
        "fog", "rain", "snow", "dust", "ripples", "beams", "washes", "shimmer", "vignette", "bitmap",
    )
    val ANIMATED = setOf("clouds", "water", "fog", "rain", "snow", "dust", "ripples", "beams", "washes", "shimmer", "stars")
}
