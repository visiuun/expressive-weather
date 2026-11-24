package dev.vis.weather.data

import androidx.annotation.StringRes
import dev.vis.weather.L
import dev.vis.weather.R

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class TempUnit { C, F }
enum class WindUnit { KMH, MS, MPH, KN }
enum class PrecipUnit { MM, IN }
enum class PressureUnit { HPA, INHG, MMHG }
enum class DistanceUnit { KM, MI }
enum class CardStyle { FILLED, OUTLINED, NONE }

/** Where the app's colour scheme comes from. */
enum class ColorSource {
    /** Shifts with the sky: amber on a clear afternoon, charcoal in a storm, deep blue at night. */
    WEATHER,
    /** Wallpaper colours (Android 12+), falls back to [WEATHER] below that. */
    MATERIAL_YOU,
    /** Generated from [Settings.customSeed]. */
    CUSTOM,
}

/** Tonal palette flavour used when generating a scheme from a seed (weather or custom). */
enum class PaletteStyle { BALANCED, VIBRANT, EXPRESSIVE }

/** Display typeface shape. Backed by the variable Google Sans Flex (wdth / wght / ROND axes). */
enum class FontStyle { DEFAULT, CHUNKY, TALL }
enum class FontEdges { SHARP, ROUNDED }

/** Optional typeface designed for readability; overrides [FontStyle] when not NONE. */
enum class ReadingFont { NONE, LEXEND, OPEN_DYSLEXIC }

enum class HourlyStyle { STANDARD, COMPACT, CARDS }

/**
 * Launcher icon variants, each backed by an <activity-alias> named "Launcher<alias>" in the manifest.
 * [DYNAMIC] is a mode, not an alias: a background job switches between the weather icons to match the sky.
 */
enum class AppIcon(val alias: String, val weather: Boolean = false) {
    OLED("LauncherOled"),
    LIGHT("LauncherLight"),
    COLOR("LauncherColor"),
    DYNAMIC("LauncherSunny", weather = true),
    SUNNY("LauncherSunny", weather = true),
    CLEAR_NIGHT("LauncherClearNight", weather = true),
    PARTLY("LauncherPartly", weather = true),
    PARTLY_NIGHT("LauncherPartlyNight", weather = true),
    CLOUDY("LauncherCloudy", weather = true),
    FOGGY("LauncherFoggy", weather = true),
    RAINY("LauncherRainy", weather = true),
    SNOWY("LauncherSnowy", weather = true),
    STORMY("LauncherStormy", weather = true),
}

/** Sections of the main screen that can be reordered or hidden (Settings → Layout). */
enum class HomeCard(@StringRes private val titleRes: Int, val removable: Boolean = true) {
    WARNINGS(R.string.d_card_warnings, removable = false),
    HIGHLIGHTS(R.string.d_card_highlights),
    NOWCAST(R.string.d_card_nowcast),
    HOURLY(R.string.d_card_hourly),
    DAILY(R.string.d_card_daily),
    RADAR(R.string.d_card_radar),
    WARDROBE(R.string.d_card_wardrobe),
    DETAILS(R.string.d_card_details),
    FRIENDS(R.string.d_card_friends);

    val title: String get() = L.s(titleRes)
}

/** Tiles in the main screen's detail grid, in their default order. [wide] tiles span the full width. */
enum class DetailTile(@StringRes private val titleRes: Int, val wide: Boolean = false) {
    WIND(R.string.m_metric_wind),
    HUMIDITY(R.string.m_metric_humidity),
    UV(R.string.m_metric_uv),
    FEELS(R.string.m_metric_feels),
    PRESSURE(R.string.m_metric_pressure),
    VISIBILITY(R.string.m_metric_visibility),
    PRECIP(R.string.m_metric_precip),
    CLOUD(R.string.m_metric_cloud),
    TODAY_PRECIP(R.string.h_tile_today_precip),
    FIRE(R.string.m_metric_fire),
    COOLING(R.string.m_tile_cooling),
    SUN(R.string.m_metric_sun, wide = true),
    MOON(R.string.m_metric_moon, wide = true),
    SEA(R.string.m_metric_sea, wide = true),
    AIR(R.string.m_metric_air, wide = true),
    POLLEN(R.string.m_metric_pollen, wide = true);

    val title: String get() = L.s(titleRes)
}

data class Settings(
    // Look
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val oled: Boolean = true,
    val colorSource: ColorSource = ColorSource.WEATHER,
    val customSeed: Int = 0xFFFF5A36.toInt(),
    val paletteStyle: PaletteStyle = PaletteStyle.EXPRESSIVE,
    /** Id of an unlocked hidden theme that overrides the colours, or null. */
    val secretTheme: String? = null,
    val fontStyle: FontStyle = FontStyle.CHUNKY,
    val fontEdges: FontEdges = FontEdges.ROUNDED,
    val readingFont: ReadingFont = ReadingFont.NONE,
    val heroBackground: Boolean = true,
    val cardStyle: CardStyle = CardStyle.FILLED,
    val monoIcons: Boolean = false,
    val appIcon: AppIcon = AppIcon.OLED,
    // Weather effects
    val animations: Boolean = true,
    val pauseOnBatterySaver: Boolean = true,
    val haptics: Boolean = true,
    // Layout
    val hourlyStyle: HourlyStyle = HourlyStyle.COMPACT,
    /** Show the wet-bulb temperature next to "feels like" on the main screen. */
    val showWetBulb: Boolean = false,
    /** Days in the daily list on the main screen: 3, 5, 7 or 16. */
    val dailyDays: Int = 7,
    val cardOrder: List<HomeCard> = HomeCard.entries,
    val hiddenCards: Set<HomeCard> = emptySet(),
    val tileOrder: List<DetailTile> = DetailTile.entries,
    val hiddenTiles: Set<DetailTile> = emptySet(),
    // Units
    val tempUnit: TempUnit = TempUnit.C,
    val windUnit: WindUnit = WindUnit.KMH,
    val precipUnit: PrecipUnit = PrecipUnit.MM,
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val use24h: Boolean = true,
    // Data
    /** Open-Meteo model id ("best_match" picks the best regional model automatically). */
    val forecastModel: String = "best_match",
    /** Nudge the next hours toward the nearest live station observation (METAR). */
    val localCorrection: Boolean = true,
    val showDataSource: Boolean = false,
    // Misc
    val autoUpdate: Boolean = true,
) {
    /** Kept for older call sites: true when colours come from the wallpaper. */
    val dynamicColor: Boolean get() = colorSource == ColorSource.MATERIAL_YOU
}
