package dev.vis.weather.data

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
enum class HomeCard(val title: String, val removable: Boolean = true) {
    WARNINGS("Weather warnings", removable = false),
    HIGHLIGHTS("Highlights"),
    NOWCAST("Rain in the next hours"),
    HOURLY("Hourly forecast"),
    DAILY("Daily forecast"),
    WARDROBE("What to wear"),
    FRIENDS("Family & friends"),
    DETAILS("Detail tiles"),
}

data class Settings(
    // Look
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val oled: Boolean = true,
    val colorSource: ColorSource = ColorSource.WEATHER,
    val customSeed: Int = 0xFFFF5A36.toInt(),
    val paletteStyle: PaletteStyle = PaletteStyle.BALANCED,
    /** Id of an unlocked hidden theme that overrides the colours, or null. */
    val secretTheme: String? = null,
    val fontStyle: FontStyle = FontStyle.DEFAULT,
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
    val hourlyStyle: HourlyStyle = HourlyStyle.STANDARD,
    /** Show the wet-bulb temperature next to "feels like" on the main screen. */
    val showWetBulb: Boolean = false,
    /** Days in the daily list on the main screen: 3, 5, 7 or 16. */
    val dailyDays: Int = 16,
    val cardOrder: List<HomeCard> = HomeCard.entries,
    val hiddenCards: Set<HomeCard> = emptySet(),
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
