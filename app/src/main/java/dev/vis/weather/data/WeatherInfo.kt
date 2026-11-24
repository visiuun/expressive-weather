package dev.vis.weather.data

import dev.vis.weather.L
import dev.vis.weather.R
import java.time.Instant

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
        0 -> if (isDay) L.s(R.string.d_wx_sunny) else L.s(R.string.d_wx_clear)
        1 -> if (isDay) L.s(R.string.d_wx_mostly_sunny) else L.s(R.string.d_wx_mostly_clear)
        2 -> L.s(R.string.d_wx_partly_cloudy)
        3 -> L.s(R.string.d_wx_overcast)
        45 -> L.s(R.string.d_wx_fog)
        48 -> L.s(R.string.d_wx_freezing_fog)
        51 -> L.s(R.string.d_wx_light_drizzle)
        53 -> L.s(R.string.d_wx_drizzle)
        55 -> L.s(R.string.d_wx_heavy_drizzle)
        56, 57 -> L.s(R.string.d_wx_freezing_drizzle)
        61 -> L.s(R.string.d_wx_light_rain)
        63 -> L.s(R.string.d_wx_rain)
        65 -> L.s(R.string.d_wx_heavy_rain)
        66, 67 -> L.s(R.string.d_wx_freezing_rain)
        71 -> L.s(R.string.d_wx_light_snow)
        73 -> L.s(R.string.d_wx_snow)
        75 -> L.s(R.string.d_wx_heavy_snow)
        77 -> L.s(R.string.d_wx_snow_grains)
        80 -> L.s(R.string.d_wx_light_showers)
        81 -> L.s(R.string.d_wx_showers)
        82 -> L.s(R.string.d_wx_violent_showers)
        85 -> L.s(R.string.d_wx_snow_showers)
        86 -> L.s(R.string.d_wx_heavy_snow_showers)
        95 -> L.s(R.string.d_wx_thunderstorm)
        96, 99 -> L.s(R.string.d_wx_thunderstorm_with_hail)
        else -> L.s(R.string.d_wx_unknown)
    }

    fun beaufort(kmh: Double): String = when {
        kmh < 1 -> L.s(R.string.d_bft_calm)
        kmh < 6 -> L.s(R.string.d_bft_light_air)
        kmh < 12 -> L.s(R.string.d_bft_light_breeze)
        kmh < 20 -> L.s(R.string.d_bft_gentle_breeze)
        kmh < 29 -> L.s(R.string.d_bft_moderate_breeze)
        kmh < 39 -> L.s(R.string.d_bft_fresh_breeze)
        kmh < 50 -> L.s(R.string.d_bft_strong_breeze)
        kmh < 62 -> L.s(R.string.d_bft_near_gale)
        kmh < 75 -> L.s(R.string.d_bft_gale)
        kmh < 89 -> L.s(R.string.d_bft_strong_gale)
        kmh < 103 -> L.s(R.string.d_bft_storm)
        kmh < 118 -> L.s(R.string.d_bft_violent_storm)
        else -> L.s(R.string.d_bft_hurricane_force)
    }

    fun compass(deg: Double): String {
        if (deg.isNaN()) return "–"
        return L.s(dirs[(((deg % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16])
    }

    private val dirs = intArrayOf(
        R.string.d_dir_n, R.string.d_dir_nne, R.string.d_dir_ne, R.string.d_dir_ene,
        R.string.d_dir_e, R.string.d_dir_ese, R.string.d_dir_se, R.string.d_dir_sse,
        R.string.d_dir_s, R.string.d_dir_ssw, R.string.d_dir_sw, R.string.d_dir_wsw,
        R.string.d_dir_w, R.string.d_dir_wnw, R.string.d_dir_nw, R.string.d_dir_nnw,
    )

    fun uvLevel(uv: Double): String = when {
        uv.isNaN() -> "–"
        uv < 3 -> L.s(R.string.d_uv_low)
        uv < 6 -> L.s(R.string.d_uv_moderate)
        uv < 8 -> L.s(R.string.d_uv_high)
        uv < 11 -> L.s(R.string.d_uv_very_high)
        else -> L.s(R.string.d_uv_extreme)
    }

    fun uvAdvice(uv: Double): String = when {
        uv.isNaN() -> ""
        uv < 3 -> L.s(R.string.d_uv_advice_low)
        uv < 6 -> L.s(R.string.d_uv_advice_moderate)
        uv < 8 -> L.s(R.string.d_uv_advice_high)
        else -> L.s(R.string.d_uv_advice_very_high)
    }

    fun visibilityLevel(m: Double): String = when {
        m.isNaN() -> "–"
        m < 1000 -> L.s(R.string.d_vis_fog_very_poor)
        m < 4000 -> L.s(R.string.d_vis_poor)
        m < 10000 -> L.s(R.string.d_vis_moderate)
        m < 20000 -> L.s(R.string.d_vis_good)
        else -> L.s(R.string.d_vis_excellent)
    }

    data class AqiBand(val label: String, val color: Long, val advice: String)

    fun usAqi(v: Double): AqiBand = when {
        v <= 50 -> AqiBand(L.s(R.string.d_usaqi_good), 0xFF4CAF50, L.s(R.string.d_usaqi_good_advice))
        v <= 100 -> AqiBand(L.s(R.string.d_usaqi_moderate), 0xFFFFC107, L.s(R.string.d_usaqi_moderate_advice))
        v <= 150 -> AqiBand(L.s(R.string.d_usaqi_sensitive), 0xFFFF9800, L.s(R.string.d_usaqi_sensitive_advice))
        v <= 200 -> AqiBand(L.s(R.string.d_usaqi_unhealthy), 0xFFF44336, L.s(R.string.d_usaqi_unhealthy_advice))
        v <= 300 -> AqiBand(L.s(R.string.d_usaqi_very_unhealthy), 0xFF9C27B0, L.s(R.string.d_usaqi_very_unhealthy_advice))
        else -> AqiBand(L.s(R.string.d_usaqi_hazardous), 0xFF7E0023, L.s(R.string.d_usaqi_hazardous_advice))
    }

    fun euAqi(v: Double): String = when {
        v <= 20 -> L.s(R.string.d_euaqi_good)
        v <= 40 -> L.s(R.string.d_euaqi_fair)
        v <= 60 -> L.s(R.string.d_euaqi_moderate)
        v <= 80 -> L.s(R.string.d_euaqi_poor)
        v <= 100 -> L.s(R.string.d_euaqi_very_poor)
        else -> L.s(R.string.d_euaqi_extremely_poor)
    }

    fun pollenLevel(v: Double): Pair<String, Float> = when {
        v < 1 -> L.s(R.string.d_pollen_none) to 0f
        v < 20 -> L.s(R.string.d_pollen_low) to 0.25f
        v < 50 -> L.s(R.string.d_pollen_moderate) to 0.5f
        v < 200 -> L.s(R.string.d_pollen_high) to 0.75f
        else -> L.s(R.string.d_pollen_very_high) to 1f
    }

    // ---- Moon

    data class MoonInfo(val phase: Double, val illumination: Double, val name: String, val daysToFull: Double, val daysToNew: Double)

    private const val SYNODIC = 29.530588853

    /** Moon phase now (or at [epochSec]); computed with [Astro] (Meeus), accurate to a few minutes of phase. */
    fun moon(epochSec: Long = Instant.now().epochSecond): MoonInfo {
        val m = Astro.moonState(epochSec)
        val p = m.phase
        val toFull = ((0.5 - p + 1) % 1) * SYNODIC
        val toNew = ((1 - p) % 1) * SYNODIC
        return MoonInfo(p, m.illumination, m.name, toFull, toNew)
    }
}
