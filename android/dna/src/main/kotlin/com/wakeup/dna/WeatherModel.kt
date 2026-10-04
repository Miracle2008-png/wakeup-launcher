package com.wakeup.dna

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

@Serializable
data class HourPoint(val epochMs: Long, val tempC: Float, val precipPct: Int)

@Serializable
data class WeatherReport(
    val place: String,
    val source: String,
    val fetchedAtMs: Long,
    val code: Int,
    val condition: String,
    val tempC: Float?,
    val hiC: Float?,
    val loC: Float?,
    val rain: Float, val fog: Float, val cloud: Float, val snow: Float, val wind: Float,
    val hourly: List<HourPoint> = emptyList(),
) {
    val vector: Weather get() = Weather(rain, fog, cloud, snow, wind)
}

/** Maps WMO weather interpretation codes (used by Open-Meteo and others) to WakeUp's weather vector. */
object WeatherCodes {
    fun label(code: Int) = when (code) {
        0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
        61 -> "Light rain"; 63 -> "Rain"; 65 -> "Heavy rain"; 66, 67 -> "Freezing rain"
        71 -> "Light snow"; 73 -> "Snow"; 75 -> "Heavy snow"; 77 -> "Snow grains"
        80 -> "Light showers"; 81 -> "Showers"; 82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"; 96, 99 -> "Thunderstorm, hail"
        else -> "Unknown"
    }

    fun vector(code: Int, cloudPct: Float?, windKmh: Float?): Weather {
        val rain = when (code) {
            51 -> .25f; 53 -> .35f; 55 -> .45f; 56, 57 -> .4f
            61 -> .45f; 63 -> .7f; 65 -> .95f; 66, 67 -> .7f
            80 -> .5f; 81 -> .75f; 82 -> .95f
            95, 96, 99 -> .9f
            else -> 0f
        }
        val snow = when (code) { 71 -> .35f; 73 -> .6f; 75 -> .9f; 77 -> .4f; 85 -> .5f; 86 -> .85f; else -> 0f }
        val fog = when (code) { 45, 48 -> .85f; 51, 53, 55 -> .25f; 61, 63, 65 -> .2f; else -> 0f }
        val codeCloud = when (code) { 0 -> .08f; 1 -> .2f; 2 -> .5f; 3 -> .9f; in 45..99 -> .85f; else -> .3f }
        val cloud = (cloudPct?.let { it / 100f } ?: codeCloud).coerceIn(0f, 1f).let { maxOf(it, if (rain + snow > 0f) .8f else 0f) }
        val wind = ((windKmh ?: 10f) / 40f).coerceIn(0f, 1f)
        return Weather(rain, fog, cloud, snow, wind)
    }

    private fun obj(e: kotlinx.serialization.json.JsonElement?) = e as? JsonObject
    private fun arr(e: kotlinx.serialization.json.JsonElement?) = e as? JsonArray
    private fun JsonPrimitive.f() = doubleOrNull?.toFloat()

    /** Parses an Open-Meteo forecast response. Throws IllegalArgumentException on anything unexpected. */
    fun parseOpenMeteo(body: String, placeName: String, nowMs: Long): WeatherReport {
        val root = try { Json.parseToJsonElement(body).jsonObject } catch (e: Exception) { throw IllegalArgumentException("not json") }
        val cur = obj(root["current"]) ?: throw IllegalArgumentException("no current block")
        val code = (cur["weather_code"] as? JsonPrimitive)?.intOrNull ?: throw IllegalArgumentException("no weather_code")
        val temp = (cur["temperature_2m"] as? JsonPrimitive)?.f()
        val cloudPct = (cur["cloud_cover"] as? JsonPrimitive)?.f()
        val wind = (cur["wind_speed_10m"] as? JsonPrimitive)?.f()
        val daily = obj(root["daily"])
        val hi = arr(daily?.get("temperature_2m_max"))?.firstOrNull()?.let { (it as? JsonPrimitive)?.f() }
        val lo = arr(daily?.get("temperature_2m_min"))?.firstOrNull()?.let { (it as? JsonPrimitive)?.f() }
        val hourly = obj(root["hourly"])
        val times = arr(hourly?.get("time"))
        val temps = arr(hourly?.get("temperature_2m"))
        val prec = arr(hourly?.get("precipitation_probability"))
        val pts = ArrayList<HourPoint>()
        if (times != null && temps != null) {
            // Open-Meteo with timeformat=unixtime returns epoch seconds
            for (i in 0 until minOf(times.size, temps.size, 48)) {
                val tSec = (times[i] as? JsonPrimitive)?.longOrNull ?: continue
                val tc = (temps[i] as? JsonPrimitive)?.f() ?: continue
                val pp = (prec?.getOrNull(i) as? JsonPrimitive)?.intOrNull ?: 0
                pts += HourPoint(tSec * 1000L, tc, pp)
            }
        }
        val v = vector(code, cloudPct, wind)
        return WeatherReport(placeName, "open-meteo", nowMs, code, label(code), temp, hi, lo, v.rain, v.fog, v.cloud, v.snow, v.wind, pts)
    }

    fun manual(key: String, nowMs: Long): WeatherReport {
        val v = Weather.PRESETS[key] ?: Weather.CLEAR
        val (code, label) = when (key) { "rain" -> 63 to "Rain"; "fog" -> 45 to "Fog"; "snow" -> 73 to "Snow"; "cloudy" -> 3 to "Cloudy"; else -> 0 to "Clear" }
        return WeatherReport("", "manual", nowMs, code, label, null, null, null, v.rain, v.fog, v.cloud, v.snow, v.wind)
    }
}
