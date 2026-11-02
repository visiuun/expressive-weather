package dev.vis.weather.data

import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, FREEZING, SNOW, THUNDER }

object WeatherInfo {
    fun sky(code: Int): Sky = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55 -> Sky.DRIZZLE
        56, 57, 66, 67 -> Sky.FREEZING
        61, 63, 65, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.THUNDER
        else -> Sky.CLOUDY
    }

    fun describe(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "Sunny" else "Clear"
        1 -> if (isDay) "Mostly sunny" else "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45 -> "Fog"
        48 -> "Freezing fog"
        51 -> "Light drizzle"
        53 -> "Drizzle"
        55 -> "Heavy drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Light showers"
        81 -> "Showers"
        82 -> "Violent showers"
        85 -> "Snow showers"
        86 -> "Heavy snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }

    fun beaufort(kmh: Double): String = when {
        kmh < 1 -> "Calm"
        kmh < 6 -> "Light air"
        kmh < 12 -> "Light breeze"
        kmh < 20 -> "Gentle breeze"
        kmh < 29 -> "Moderate breeze"
        kmh < 39 -> "Fresh breeze"
        kmh < 50 -> "Strong breeze"
        kmh < 62 -> "Near gale"
        kmh < 75 -> "Gale"
        kmh < 89 -> "Strong gale"
        kmh < 103 -> "Storm"
        kmh < 118 -> "Violent storm"
        else -> "Hurricane force"
    }

    fun compass(deg: Double): String {
        if (deg.isNaN()) return "–"
        val dirs = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return dirs[(((deg % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16]
    }

    fun uvLevel(uv: Double): String = when {
        uv.isNaN() -> "–"
        uv < 3 -> "Low"
        uv < 6 -> "Moderate"
        uv < 8 -> "High"
        uv < 11 -> "Very high"
        else -> "Extreme"
    }

    fun uvAdvice(uv: Double): String = when {
        uv.isNaN() -> ""
        uv < 3 -> "No protection needed."
        uv < 6 -> "Wear sunscreen if outside for long."
        uv < 8 -> "Sunscreen, hat and shade at midday."
        else -> "Avoid the sun around midday."
    }

    fun visibilityLevel(m: Double): String = when {
        m.isNaN() -> "–"
        m < 1000 -> "Fog — very poor"
        m < 4000 -> "Poor"
        m < 10000 -> "Moderate"
        m < 20000 -> "Good"
        else -> "Excellent"
    }

    data class AqiBand(val label: String, val color: Long, val advice: String)

    fun usAqi(v: Double): AqiBand = when {
        v <= 50 -> AqiBand("Good", 0xFF4CAF50, "Air quality is great for outdoor activities.")
        v <= 100 -> AqiBand("Moderate", 0xFFFFC107, "Unusually sensitive people should limit long exertion.")
        v <= 150 -> AqiBand("Unhealthy for sensitive groups", 0xFFFF9800, "Sensitive groups should reduce outdoor exertion.")
        v <= 200 -> AqiBand("Unhealthy", 0xFFF44336, "Everyone should reduce prolonged outdoor exertion.")
        v <= 300 -> AqiBand("Very unhealthy", 0xFF9C27B0, "Avoid prolonged outdoor exertion.")
        else -> AqiBand("Hazardous", 0xFF7E0023, "Stay indoors and keep activity low.")
    }

    fun euAqi(v: Double): String = when {
        v <= 20 -> "Good"
        v <= 40 -> "Fair"
        v <= 60 -> "Moderate"
        v <= 80 -> "Poor"
        v <= 100 -> "Very poor"
        else -> "Extremely poor"
    }

    fun pollenLevel(v: Double): Pair<String, Float> = when {
        v < 1 -> "None" to 0f
        v < 20 -> "Low" to 0.25f
        v < 50 -> "Moderate" to 0.5f
        v < 200 -> "High" to 0.75f
        else -> "Very high" to 1f
    }

    // ---- Moon

    data class MoonInfo(val phase: Double, val illumination: Double, val name: String, val daysToFull: Double, val daysToNew: Double)

    private const val SYNODIC = 29.530588853

    fun moon(epochSec: Long = Instant.now().epochSecond): MoonInfo {
        val jd = epochSec / 86400.0 + 2440587.5
        // Reference new moon: 2000-01-06 18:14 UTC (JD 2451550.26)
        var age = (jd - 2451550.26) % SYNODIC
        if (age < 0) age += SYNODIC
        val p = age / SYNODIC
        val illum = (1 - cos(2 * PI * p)) / 2
        val name = when {
            p < 0.0339 -> "New moon"
            p < 0.216 -> "Waxing crescent"
            p < 0.284 -> "First quarter"
            p < 0.466 -> "Waxing gibbous"
            p < 0.534 -> "Full moon"
            p < 0.716 -> "Waning gibbous"
            p < 0.784 -> "Last quarter"
            p < 0.966 -> "Waning crescent"
            else -> "New moon"
        }
        val toFull = ((0.5 - p + 1) % 1) * SYNODIC
        val toNew = ((1 - p) % 1) * SYNODIC
        return MoonInfo(p, illum, name, toFull, toNew)
    }
}
