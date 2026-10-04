package com.wakeup.dna

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** A place used for sun and weather. Chosen by the user, never read from a location sensor. */
data class Place(val name: String, val lat: Double, val lon: Double, val tz: String = "")

/** Sun and moon maths done on-device so the Horizon and Moon widgets work offline. */
object Astro {
    private const val RAD = PI / 180.0
    private const val DEG = 180.0 / PI

    fun julianDay(epochMs: Long): Double = epochMs / 86_400_000.0 + 2440587.5

    data class SunTimes(val sunriseMs: Long?, val sunsetMs: Long?, val solarNoonMs: Long, val polar: Polar)
    enum class Polar { NONE, DAY, NIGHT }

    /** NOAA solar calculator: sunrise, sunset and solar noon for the UTC day containing [epochMs]. */
    fun sunTimes(epochMs: Long, lat: Double, lon: Double): SunTimes {
        val dayStart = floor(epochMs / 86_400_000.0).toLong() * 86_400_000L
        val jd = julianDay(dayStart) + 0.5 // noon UTC of that day
        val t = (jd - 2451545.0) / 36525.0
        val l0 = mod(280.46646 + t * (36000.76983 + 0.0003032 * t), 360.0)
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(m * RAD) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(2 * m * RAD) * (0.019993 - 0.000101 * t) + sin(3 * m * RAD) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(omega * RAD)
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(omega * RAD)
        val decl = asin(sin(eps * RAD) * sin(lambda * RAD))
        val y = tan(eps * RAD / 2).let { it * it }
        val eqTime = 4.0 * DEG * (y * sin(2 * l0 * RAD) - 2 * e * sin(m * RAD) + 4 * e * y * sin(m * RAD) * cos(2 * l0 * RAD) - 0.5 * y * y * sin(4 * l0 * RAD) - 1.25 * e * e * sin(2 * m * RAD))
        val noonMin = 720.0 - 4.0 * lon - eqTime
        val cosHa = cos(90.833 * RAD) / (cos(lat * RAD) * cos(decl)) - tan(lat * RAD) * tan(decl)
        val noonMs = dayStart + (noonMin * 60_000.0).toLong()
        return when {
            cosHa > 1 -> SunTimes(null, null, noonMs, Polar.NIGHT)
            cosHa < -1 -> SunTimes(null, null, noonMs, Polar.DAY)
            else -> {
                val ha = acos(cosHa) * DEG
                SunTimes(dayStart + ((noonMin - 4.0 * ha) * 60_000.0).toLong(), dayStart + ((noonMin + 4.0 * ha) * 60_000.0).toLong(), noonMs, Polar.NONE)
            }
        }
    }

    /** Solar elevation in degrees at [epochMs]. Used to place the sun on the Horizon arc. */
    fun sunElevation(epochMs: Long, lat: Double, lon: Double): Double {
        val st = sunTimes(epochMs, lat, lon)
        val declSource = (epochMs - st.solarNoonMs) / 3_600_000.0 // hours from solar noon
        val jd = julianDay(epochMs)
        val t = (jd - 2451545.0) / 36525.0
        val l0 = mod(280.46646 + t * 36000.76983, 360.0)
        val m = 357.52911 + t * 35999.05029
        val c = sin(m * RAD) * 1.914602 + sin(2 * m * RAD) * 0.019993
        val lambda = l0 + c - 0.00569
        val eps = 23.4393 - 0.013 * t
        val decl = asin(sin(eps * RAD) * sin(lambda * RAD))
        val ha = declSource * 15.0 * RAD
        return asin(sin(lat * RAD) * sin(decl) + cos(lat * RAD) * cos(decl) * cos(ha)) * DEG
    }

    /** Moon elongation from the sun in degrees: 0 new, 90 first quarter, 180 full, 270 last quarter. */
    fun moonElongation(epochMs: Long): Double {
        // Meeus ch. 47, truncated to the ten largest longitude terms: well under a degree of error.
        val t = (julianDay(epochMs) - 2451545.0) / 36525.0
        val lp = 218.3164477 + 481267.88123421 * t
        val dd = (297.8501921 + 445267.1114034 * t) * RAD
        val ms = (357.5291092 + 35999.0502909 * t) * RAD
        val mp = (134.9633964 + 477198.8675055 * t) * RAD
        val ff = (93.2720950 + 483202.0175233 * t) * RAD
        val lonMoon = lp + 6.288774 * sin(mp) + 1.274027 * sin(2 * dd - mp) + 0.658314 * sin(2 * dd) +
            0.213618 * sin(2 * mp) - 0.185116 * sin(ms) - 0.114332 * sin(2 * ff) + 0.058793 * sin(2 * dd - 2 * mp) +
            0.057066 * sin(2 * dd - ms - mp) + 0.053322 * sin(2 * dd + mp) + 0.045758 * sin(2 * dd - ms)
        val ls = 280.46646 + 36000.76983 * t
        val lonSun = ls + (1.914602 - 0.004817 * t) * sin(ms) + 0.019993 * sin(2 * ms)
        return mod(lonMoon - lonSun, 360.0)
    }

    data class Moon(val elongation: Double, val illumination: Double, val waxing: Boolean, val name: String)

    fun moon(epochMs: Long): Moon {
        val e = moonElongation(epochMs)
        val illum = (1 - cos(e * RAD)) / 2
        val name = when {
            e < 6.5 || e >= 353.5 -> "New moon"
            e < 83.5 -> "Waxing crescent"
            e < 96.5 -> "First quarter"
            e < 173.5 -> "Waxing gibbous"
            e < 186.5 -> "Full moon"
            e < 263.5 -> "Waning gibbous"
            e < 276.5 -> "Last quarter"
            else -> "Waning crescent"
        }
        return Moon(e, illum, e < 180.0, name)
    }

    /** Next time the elongation reaches [targetDeg] (0 new, 180 full), found by stepping then bisection. */
    fun nextPhase(fromMs: Long, targetDeg: Double): Long {
        fun diff(ms: Long) = mod(moonElongation(ms) - targetDeg + 180.0, 360.0) - 180.0 // -180..180, crosses 0 going up
        var a = fromMs
        var step = 3_600_000L
        var b = a + step
        var n = 0
        while (!(diff(a) < 0 && diff(b) >= 0) && n < 800) { a = b; b += step; n++ }
        var lo = a; var hi = b
        repeat(30) { val mid = (lo + hi) / 2; if (diff(mid) < 0) lo = mid else hi = mid }
        return (lo + hi) / 2
    }

    private fun mod(x: Double, m: Double): Double { val r = x % m; return if (r < 0) r + m else r }
}

/** Small built-in gazetteer so weather and sun need no location permission. Users may also enter coordinates. */
object Places {
    val ALL = listOf(
        Place("Lagos", 6.5244, 3.3792, "Africa/Lagos"), Place("Abuja", 9.0765, 7.3986, "Africa/Lagos"),
        Place("Port Harcourt", 4.8156, 7.0498, "Africa/Lagos"), Place("Kano", 12.0022, 8.5920, "Africa/Lagos"),
        Place("Ibadan", 7.3775, 3.9470, "Africa/Lagos"), Place("Accra", 5.6037, -0.1870, "Africa/Accra"),
        Place("Nairobi", -1.2921, 36.8219, "Africa/Nairobi"), Place("Cairo", 30.0444, 31.2357, "Africa/Cairo"),
        Place("Johannesburg", -26.2041, 28.0473, "Africa/Johannesburg"), Place("Cape Town", -33.9249, 18.4241, "Africa/Johannesburg"),
        Place("Casablanca", 33.5731, -7.5898, "Africa/Casablanca"), Place("Addis Ababa", 9.0300, 38.7400, "Africa/Addis_Ababa"),
        Place("London", 51.5074, -0.1278, "Europe/London"), Place("Paris", 48.8566, 2.3522, "Europe/Paris"),
        Place("Berlin", 52.5200, 13.4050, "Europe/Berlin"), Place("Madrid", 40.4168, -3.7038, "Europe/Madrid"),
        Place("Rome", 41.9028, 12.4964, "Europe/Rome"), Place("Stockholm", 59.3293, 18.0686, "Europe/Stockholm"),
        Place("Oslo", 59.9139, 10.7522, "Europe/Oslo"), Place("Reykjavik", 64.1466, -21.9426, "Atlantic/Reykjavik"),
        Place("Istanbul", 41.0082, 28.9784, "Europe/Istanbul"), Place("Moscow", 55.7558, 37.6173, "Europe/Moscow"),
        Place("Dubai", 25.2048, 55.2708, "Asia/Dubai"), Place("Riyadh", 24.7136, 46.6753, "Asia/Riyadh"),
        Place("Karachi", 24.8607, 67.0011, "Asia/Karachi"), Place("Mumbai", 19.0760, 72.8777, "Asia/Kolkata"),
        Place("Delhi", 28.6139, 77.2090, "Asia/Kolkata"), Place("Dhaka", 23.8103, 90.4125, "Asia/Dhaka"),
        Place("Bangkok", 13.7563, 100.5018, "Asia/Bangkok"), Place("Singapore", 1.3521, 103.8198, "Asia/Singapore"),
        Place("Jakarta", -6.2088, 106.8456, "Asia/Jakarta"), Place("Manila", 14.5995, 120.9842, "Asia/Manila"),
        Place("Hong Kong", 22.3193, 114.1694, "Asia/Hong_Kong"), Place("Shanghai", 31.2304, 121.4737, "Asia/Shanghai"),
        Place("Seoul", 37.5665, 126.9780, "Asia/Seoul"), Place("Tokyo", 35.6762, 139.6503, "Asia/Tokyo"),
        Place("Sydney", -33.8688, 151.2093, "Australia/Sydney"), Place("Auckland", -36.8509, 174.7645, "Pacific/Auckland"),
        Place("New York", 40.7128, -74.0060, "America/New_York"), Place("Toronto", 43.6532, -79.3832, "America/Toronto"),
        Place("Chicago", 41.8781, -87.6298, "America/Chicago"), Place("Denver", 39.7392, -104.9903, "America/Denver"),
        Place("Los Angeles", 34.0522, -118.2437, "America/Los_Angeles"), Place("Vancouver", 49.2827, -123.1207, "America/Vancouver"),
        Place("Mexico City", 19.4326, -99.1332, "America/Mexico_City"), Place("Bogota", 4.7110, -74.0721, "America/Bogota"),
        Place("Lima", -12.0464, -77.0428, "America/Lima"), Place("Sao Paulo", -23.5505, -46.6333, "America/Sao_Paulo"),
        Place("Buenos Aires", -34.6037, -58.3816, "America/Argentina/Buenos_Aires"), Place("Santiago", -33.4489, -70.6693, "America/Santiago"),
    )

    fun byTimeZone(tz: String): Place? = ALL.firstOrNull { it.tz == tz }
    fun byName(n: String): Place? = ALL.firstOrNull { it.name.equals(n, true) }
}
