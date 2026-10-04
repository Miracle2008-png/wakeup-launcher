package com.wakeup.launcher.weather

import android.util.Log
import com.wakeup.dna.Place
import com.wakeup.dna.WeatherCodes
import com.wakeup.dna.WeatherReport
import com.wakeup.launcher.core.JsonStore
import com.wakeup.launcher.core.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** A weather source. Replaceable: nothing in the launcher depends on a particular vendor. */
interface WeatherProvider {
    val id: String
    val displayName: String
    /** What the user is told about this provider before it is ever contacted. */
    val disclosure: String
    suspend fun fetch(place: Place, nowMs: Long): WeatherReport
}

/**
 * Open-Meteo: no API key, no account. Only the chosen place's coordinates are sent (never a device
 * identifier). Their free tier is for non-commercial use; a commercial release must either take a
 * paid plan or swap this provider (see docs/security/third-party-services.md).
 */
class OpenMeteoProvider : WeatherProvider {
    override val id = "open-meteo"
    override val displayName = "Open-Meteo"
    override val disclosure = "Sends the latitude and longitude of the place you chose to api.open-meteo.com over HTTPS. Nothing else."

    override suspend fun fetch(place: Place, nowMs: Long): WeatherReport = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,cloud_cover,wind_speed_10m" +
                "&hourly=temperature_2m,precipitation_probability&daily=temperature_2m_max,temperature_2m_min&forecast_days=2&timeformat=unixtime&timezone=auto",
            place.lat, place.lon,
        )
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            check(c.url.protocol == "https") { "refusing non-HTTPS connection" }
            c.connectTimeout = 8000; c.readTimeout = 8000; c.requestMethod = "GET"
            c.setRequestProperty("User-Agent", "WakeUp-Launcher")
            c.instanceFollowRedirects = false
            val code = c.responseCode
            check(code == 200) { "http $code" }
            val body = c.inputStream.use { s ->
                val buf = java.io.ByteArrayOutputStream(); val tmp = ByteArray(8192); var n: Int; var total = 0
                while (s.read(tmp).also { n = it } > 0) { total += n; check(total <= 256 * 1024) { "response too large" }; buf.write(tmp, 0, n) }
                buf.toString(Charsets.UTF_8.name())
            }
            WeatherCodes.parseOpenMeteo(body, place.name, nowMs)
        } finally { c.disconnect() }
    }
}

@Serializable
data class WeatherCache(val report: WeatherReport? = null, val lat: Double = 0.0, val lon: Double = 0.0)

/**
 * Effective weather for the launcher. Live data is used only when the user switched it on and chose a place;
 * otherwise (or while offline, or if the provider fails) it falls back to the cached last-known report, then to
 * the manual setting. A failure here can never affect the Home Screen.
 */
class WeatherRepository(
    private val scope: CoroutineScope,
    cacheFile: File,
    private val settings: () -> SettingsState,
    private val provider: WeatherProvider = OpenMeteoProvider(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val cache = JsonStore(cacheFile, WeatherCache.serializer(), scope) { WeatherCache() }
    private val _report = MutableStateFlow(WeatherCodes.manual("clear", clock()))
    val report: StateFlow<WeatherReport> get() = _report
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> get() = _status
    private var loop: Job? = null
    private var failures = 0

    val providerDisclosure get() = provider.disclosure
    val providerName get() = provider.displayName

    /** Recompute the effective report from current settings and cache (cheap; call after settings change). */
    fun recompute() {
        val s = settings()
        val c = cache.value
        _report.value = if (s.liveWeather && s.hasPlace && c.report != null && sameSpot(c, s)) c.report else WeatherCodes.manual(s.manualWeather, clock())
    }

    private fun sameSpot(c: WeatherCache, s: SettingsState) = Math.abs(c.lat - s.lat) < .01 && Math.abs(c.lon - s.lon) < .01

    fun ageMs(): Long = cache.value.report?.let { clock() - it.fetchedAtMs } ?: Long.MAX_VALUE

    /** Starts periodic refresh while the launcher is visible. Stops entirely when [stop] is called. */
    fun start() {
        recompute()
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                val s = settings()
                if (s.liveWeather && s.hasPlace) {
                    if (ageMs() > 30 * 60_000L || !sameSpot(cache.value, s)) refreshNow()
                    // back off after failures: 30 min, then up to 4 h
                    delay(minOf(30L * 60_000L * (1L shl failures.coerceAtMost(3)), 4L * 3_600_000L))
                } else delay(60_000L)
            }
        }
    }

    fun stop() { loop?.cancel(); loop = null }

    suspend fun refreshNow(): Boolean {
        val s = settings()
        if (!s.hasPlace) { _status.value = "Choose a place first"; return false }
        return try {
            val r = provider.fetch(Place(s.placeName.ifBlank { "Selected place" }, s.lat, s.lon), clock())
            cache.set(WeatherCache(r, s.lat, s.lon))
            failures = 0; _status.value = "Updated"; recompute(); true
        } catch (e: Exception) {
            failures++
            Log.w("Weather", "refresh failed: ${e.javaClass.simpleName}")
            _status.value = if (cache.value.report != null) "Offline, showing last update" else "Weather unavailable, using your manual setting"
            recompute(); false
        }
    }

    fun forgetCache() { cache.set(WeatherCache()); recompute() }
}
