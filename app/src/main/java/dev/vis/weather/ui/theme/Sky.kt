package dev.vis.weather.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.vis.weather.data.PaletteStyle
import dev.vis.weather.data.Sky
import dev.vis.weather.data.WeatherInfo

/** Current conditions that drive the weather colour source and the live sky. Null before data loads. */
@Immutable
data class WeatherMood(val code: Int, val isDay: Boolean) {
    val sky: Sky get() = WeatherInfo.sky(code)
    val kind: SkyKind get() = SkyKind.of(sky, isDay)
    /** Thunderstorm with hail (WMO 96 / 99). */
    val hail: Boolean get() = code == 96 || code == 99
}

/**
 * Every look the sky can take. Seeds are hand-picked to stay off purple; nights lean navy.
 * [lightSky] / [darkSky] are top → middle → horizon gradient stops for light and dark themes.
 */
enum class SkyKind(
    val label: String,
    val sample: Int,
    val isDay: Boolean,
    val seed: Long,
    val accent: Long,
    val primaryChroma: Double?,
    val lightSky: LongArray,
    val darkSky: LongArray,
    /** Sun / moon / lightning glow. */
    val glow: Long,
    /** Rain, snow, cloud and star colour. */
    val particle: Long,
) {
    CLEAR_DAY("Sunny", 0, true, 0xFFF5A623, 0xFFFFC83D, null,
        longArrayOf(0xFFFFBE5C, 0xFFFFD993, 0xFFFFF0D4), longArrayOf(0xFF2A64A8, 0xFF1D4A80, 0xFF112C4E),
        0xFFFFC65C, 0xFFFFFFFF),
    CLEAR_NIGHT("Clear night", 0, false, 0xFF2B4C8F, 0xFFE8C66A, null,
        longArrayOf(0xFF8EA5D8, 0xFFB4C3E6, 0xFFDCE4F5), longArrayOf(0xFF0A1633, 0xFF0E1F45, 0xFF0B1530),
        0xFFCFDBFF, 0xFFFFFFFF),
    PARTLY_DAY("Partly cloudy", 2, true, 0xFF5DA9E9, 0xFFF4B740, null,
        longArrayOf(0xFF7DBDF0, 0xFFA8D3F5, 0xFFDCEEFB), longArrayOf(0xFF1C4A78, 0xFF1A3B5E, 0xFF12283F),
        0xFFFFE08A, 0xFFFFFFFF),
    PARTLY_NIGHT("Cloudy night", 2, false, 0xFF34558B, 0xFFD9BE72, null,
        longArrayOf(0xFF97A9CF, 0xFFB9C6E2, 0xFFE0E6F3), longArrayOf(0xFF0F1A36, 0xFF16233F, 0xFF111A2C),
        0xFFC9D4F2, 0xFF8C99B5),
    CLOUDY_DAY("Overcast", 3, true, 0xFF66788A, 0xFF5E9AA8, 14.0,
        longArrayOf(0xFF9AA6B4, 0xFFBAC3CD, 0xFFE1E6EB), longArrayOf(0xFF2E3640, 0xFF262D36, 0xFF1B2027),
        0xFFE6EBF0, 0xFFDDE3EA),
    CLOUDY_NIGHT("Overcast night", 3, false, 0xFF4B5868, 0xFF6F9FB8, 12.0,
        longArrayOf(0xFF8E97A6, 0xFFB0B8C4, 0xFFDCE0E7), longArrayOf(0xFF1A1E25, 0xFF171B21, 0xFF121519),
        0xFF9AA4B4, 0xFF4A5260),
    FOG_DAY("Fog", 45, true, 0xFF87A08F, 0xFFA3B18A, 16.0,
        longArrayOf(0xFFB3C2B8, 0xFFCAD5CE, 0xFFE7ECE8), longArrayOf(0xFF2E3833, 0xFF27302C, 0xFF1C2320),
        0xFFF0F4F1, 0xFFE8EEEA),
    FOG_NIGHT("Night fog", 45, false, 0xFF5F7468, 0xFF8FA383, 14.0,
        longArrayOf(0xFFA5B2AB, 0xFFC2CCC6, 0xFFE2E7E4), longArrayOf(0xFF1C2320, 0xFF18201D, 0xFF121714),
        0xFF9AA79F, 0xFF6E7C74),
    DRIZZLE_DAY("Drizzle", 53, true, 0xFF4A9BAE, 0xFF7FB8C9, null,
        longArrayOf(0xFF8DBCC8, 0xFFAFD0D8, 0xFFDDECEF), longArrayOf(0xFF1E4550, 0xFF193A43, 0xFF132A30),
        0xFFE0F2F6, 0xFFCFE8F0),
    DRIZZLE_NIGHT("Night drizzle", 53, false, 0xFF2F6F80, 0xFF6FA6C0, null,
        longArrayOf(0xFF8FA9B8, 0xFFB0C4CF, 0xFFDCE6EB), longArrayOf(0xFF0F2630, 0xFF112A33, 0xFF0D1C22),
        0xFFA9C8D6, 0xFF9FC4D4),
    RAIN_DAY("Rain", 63, true, 0xFF2C7DA0, 0xFF45B0C2, null,
        longArrayOf(0xFF6FA3B8, 0xFF95BCCB, 0xFFD4E5EB), longArrayOf(0xFF173E52, 0xFF143444, 0xFF0F2531),
        0xFFDDF0F8, 0xFFD9EEF7),
    RAIN_NIGHT("Night rain", 63, false, 0xFF1F5C78, 0xFF4FA3B8, null,
        longArrayOf(0xFF7F98AE, 0xFFA3B8C8, 0xFFD9E2EA), longArrayOf(0xFF0B1C28, 0xFF0E2230, 0xFF0A1720),
        0xFF9DBFD2, 0xFF9CC3D8),
    FREEZING_DAY("Freezing rain", 66, true, 0xFF3FB8D0, 0xFF7FB2E5, null,
        longArrayOf(0xFF7FD3E3, 0xFFA9E3EE, 0xFFE0F6FA), longArrayOf(0xFF134B58, 0xFF123D48, 0xFF0D2B33),
        0xFFE6FBFF, 0xFFE6FBFF),
    FREEZING_NIGHT("Night ice", 66, false, 0xFF2A8299, 0xFF79A9D8, null,
        longArrayOf(0xFF86B9C8, 0xFFAACFDA, 0xFFDCEEF2), longArrayOf(0xFF0A2630, 0xFF0C2C36, 0xFF081D24),
        0xFFB5E6F0, 0xFFC4EEF7),
    SNOW_DAY("Snow", 73, true, 0xFF8DB3DA, 0xFF9CC9E8, null,
        longArrayOf(0xFFB8CDE3, 0xFFD2E0EE, 0xFFF0F5FA), longArrayOf(0xFF2A3B52, 0xFF233245, 0xFF1A2533),
        0xFFFFFFFF, 0xFFFFFFFF),
    SNOW_NIGHT("Night snow", 73, false, 0xFF5E80AB, 0xFF8DB9DC, null,
        longArrayOf(0xFFA6B6CF, 0xFFC6D2E3, 0xFFE8EDF5), longArrayOf(0xFF141E30, 0xFF18233A, 0xFF111827),
        0xFFDCE6F5, 0xFFF2F6FF),
    THUNDER_DAY("Thunderstorm", 95, true, 0xFF4A5059, 0xFFFFD60A, 8.0,
        longArrayOf(0xFF9AA0A8, 0xFFB6BAC0, 0xFFDCDEE1), longArrayOf(0xFF1B1D22, 0xFF202329, 0xFF15171B),
        0xFFFFE14D, 0xFFD0D8E0),
    THUNDER_NIGHT("Night storm", 95, false, 0xFF3A3F47, 0xFFFFD60A, 6.0,
        longArrayOf(0xFF8C929B, 0xFFA9AEB5, 0xFFD6D8DC), longArrayOf(0xFF0E0F12, 0xFF15171B, 0xFF0E0F12),
        0xFFFFE14D, 0xFFB8C0CA),
    ;

    val mood: WeatherMood get() = WeatherMood(sample, isDay)

    val wet: Boolean get() = sample in 50..69 || sample >= 95

    fun spec(style: PaletteStyle) = SchemeGen.Spec(seed.toInt(), style, accent.toInt(), primaryChroma)

    companion object {
        fun of(sky: Sky, isDay: Boolean): SkyKind = when (sky) {
            Sky.CLEAR -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
            Sky.PARTLY -> if (isDay) PARTLY_DAY else PARTLY_NIGHT
            Sky.CLOUDY -> if (isDay) CLOUDY_DAY else CLOUDY_NIGHT
            Sky.FOG -> if (isDay) FOG_DAY else FOG_NIGHT
            Sky.DRIZZLE -> if (isDay) DRIZZLE_DAY else DRIZZLE_NIGHT
            Sky.RAIN -> if (isDay) RAIN_DAY else RAIN_NIGHT
            Sky.FREEZING -> if (isDay) FREEZING_DAY else FREEZING_NIGHT
            Sky.SNOW -> if (isDay) SNOW_DAY else SNOW_NIGHT
            Sky.THUNDER -> if (isDay) THUNDER_DAY else THUNDER_NIGHT
        }

        /** Used before the first forecast arrives. */
        val Default = PARTLY_DAY
    }
}

/**
 * Resolved sky colours for the current theme: a top → [mid] → [horizon] gradient that should fade into
 * `colorScheme.background` below, plus the glow/particle colours and [onSky], a readable content colour.
 */
@Immutable
data class SkyPalette(
    val kind: SkyKind,
    val top: Color,
    val mid: Color,
    val horizon: Color,
    val glow: Color,
    val particle: Color,
    val onSky: Color,
    /** Overall strength of particles / glows (lower on OLED so the sky stays subtle). */
    val intensity: Float,
)

/**
 * Sky colours for [kind] in the current theme. On OLED the horizon is pure black and the top only
 * hints at the sky. A [tint] (secret themes) pulls the gradient toward that theme's colour.
 */
fun skyPalette(kind: SkyKind, dark: Boolean, oled: Boolean, tint: Color? = null): SkyPalette {
    val src = if (dark) kind.darkSky else kind.lightSky
    var top = Color(src[0])
    var mid = Color(src[1])
    var horizon = Color(src[2])
    if (tint != null) {
        top = lerp(top, tint, 0.55f)
        mid = lerp(mid, tint, 0.4f)
        horizon = lerp(horizon, tint, 0.25f)
    }
    if (dark && oled) {
        top = lerp(top, Color.Black, 0.3f)
        mid = lerp(mid, Color.Black, 0.6f)
        horizon = Color.Black
    }
    return SkyPalette(
        kind = kind,
        top = top,
        mid = mid,
        horizon = horizon,
        glow = Color(kind.glow),
        // Light skies: rain streaks need to be darker than the sky to show up at all.
        particle = if (!dark && kind.wet) lerp(Color(kind.particle), Color(0xFF2F4E66), 0.6f) else Color(kind.particle),
        onSky = if (dark) Color(0xFFF4F6FA) else Color(0xFF15181D),
        intensity = when {
            dark && oled -> 0.7f
            dark -> 0.9f
            else -> 1f
        },
    )
}
