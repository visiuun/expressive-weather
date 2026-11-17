package dev.vis.weather.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val cache = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private inline fun <reified T : Enum<T>> enumOf(key: String, default: T): T =
        sp.getString(key, null)?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    private fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            theme = enumOf("theme", d.theme),
            oled = sp.getBoolean("oled", d.oled),
            // 1.4 introduced the weather-reactive theme; the old "dynamic colour" switch isn't carried over so everyone sees it.
            colorSource = enumOf("color_source", d.colorSource),
            customSeed = sp.getInt("custom_seed", d.customSeed),
            paletteStyle = enumOf("palette_style", d.paletteStyle),
            secretTheme = sp.getString("secret_theme", null),
            fontStyle = enumOf("font_style", d.fontStyle),
            fontEdges = enumOf("font_edges", d.fontEdges),
            readingFont = enumOf("reading_font", d.readingFont),
            heroBackground = sp.getBoolean("sky_backdrop", d.heroBackground),
            cardStyle = enumOf("card_style", d.cardStyle),
            monoIcons = sp.getBoolean("mono_icons", d.monoIcons),
            appIcon = enumOf("app_icon", d.appIcon),
            animations = sp.getBoolean("animations", d.animations),
            pauseOnBatterySaver = sp.getBoolean("pause_battery_saver", d.pauseOnBatterySaver),
            haptics = sp.getBoolean("haptics", d.haptics),
            hourlyStyle = enumOf("hourly_style", d.hourlyStyle),
            showWetBulb = sp.getBoolean("show_wet_bulb", d.showWetBulb),
            dailyDays = sp.getInt("daily_days", d.dailyDays),
            cardOrder = sp.getString("card_order", null)?.split(',')?.mapNotNull { n -> HomeCard.entries.firstOrNull { it.name == n } }
                ?.let { saved -> saved + HomeCard.entries.filter { it !in saved } } ?: d.cardOrder,
            hiddenCards = sp.getString("hidden_cards", null)?.split(',')?.mapNotNull { n -> HomeCard.entries.firstOrNull { it.name == n } }
                ?.toSet() ?: d.hiddenCards,
            tempUnit = enumOf("temp", d.tempUnit),
            windUnit = enumOf("wind", d.windUnit),
            precipUnit = enumOf("precip", d.precipUnit),
            pressureUnit = enumOf("pressure", d.pressureUnit),
            distanceUnit = enumOf("distance", if (sp.getString("temp", null) == "F") DistanceUnit.MI else d.distanceUnit),
            use24h = sp.getBoolean("24h", d.use24h),
            forecastModel = sp.getString("forecast_model", d.forecastModel) ?: d.forecastModel,
            localCorrection = sp.getBoolean("local_correction", d.localCorrection),
            showDataSource = sp.getBoolean("show_source", d.showDataSource),
            autoUpdate = sp.getBoolean("auto_update", d.autoUpdate),
        )
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        sp.edit {
            putString("theme", s.theme.name)
            putBoolean("oled", s.oled)
            putString("color_source", s.colorSource.name)
            remove("dynamic")
            putInt("custom_seed", s.customSeed)
            putString("palette_style", s.paletteStyle.name)
            putString("secret_theme", s.secretTheme)
            putString("font_style", s.fontStyle.name)
            putString("font_edges", s.fontEdges.name)
            putString("reading_font", s.readingFont.name)
            putBoolean("sky_backdrop", s.heroBackground)
            remove("hero_bg")
            putString("card_style", s.cardStyle.name)
            putBoolean("mono_icons", s.monoIcons)
            putString("app_icon", s.appIcon.name)
            putBoolean("animations", s.animations)
            putBoolean("pause_battery_saver", s.pauseOnBatterySaver)
            putBoolean("haptics", s.haptics)
            putString("hourly_style", s.hourlyStyle.name)
            putBoolean("show_wet_bulb", s.showWetBulb)
            putInt("daily_days", s.dailyDays)
            putString("card_order", s.cardOrder.joinToString(",") { it.name })
            putString("hidden_cards", s.hiddenCards.joinToString(",") { it.name })
            putString("temp", s.tempUnit.name)
            putString("wind", s.windUnit.name)
            putString("precip", s.precipUnit.name)
            putString("pressure", s.pressureUnit.name)
            putString("distance", s.distanceUnit.name)
            putBoolean("24h", s.use24h)
            putString("forecast_model", s.forecastModel)
            putBoolean("local_correction", s.localCorrection)
            putBoolean("show_source", s.showDataSource)
            putBoolean("auto_update", s.autoUpdate)
        }
    }

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(value) = sp.edit { putLong("last_update_check", value) }

    // ---- places

    private fun Place.toJson() = JSONObject().put("name", name).put("region", region).put("lat", lat).put("lon", lon)
    private fun JSONObject.toPlace() = Place(getString("name"), optString("region"), getDouble("lat"), getDouble("lon"))

    var places: List<Place>
        get() = runCatching {
            val arr = JSONArray(sp.getString("places", "[]"))
            (0 until arr.length()).map { arr.getJSONObject(it).toPlace() }
        }.getOrDefault(emptyList())
        set(value) = sp.edit { putString("places", JSONArray().apply { value.forEach { put(it.toJson()) } }.toString()) }

    var currentPlace: Place?
        get() = sp.getString("current_place", null)?.let { runCatching { JSONObject(it).toPlace() }.getOrNull() }
        set(value) = sp.edit { putString("current_place", value?.toJson()?.toString()) }

    /** "current" for device location, a place key for a saved place, or null if never chosen. */
    var selection: String?
        get() = sp.getString("selection", null)
        set(value) = sp.edit { putString("selection", value) }

    // ---- offline cache of the last successful response per place

    fun saveCache(key: String, forecast: String, air: String?, time: Long, marine: String? = null, metar: String? = null) = cache.edit {
        putString("f_$key", forecast)
        putString("a_$key", air)
        putLong("t_$key", time)
        putString("m_$key", marine)
        putString("o_$key", metar)
    }

    /** Marine and METAR JSON stored next to the forecast (either may be null). */
    fun loadExtras(key: String): Pair<String?, String?> = cache.getString("m_$key", null) to cache.getString("o_$key", null)

    fun loadCache(key: String): Triple<String, String?, Long>? {
        val f = cache.getString("f_$key", null) ?: return null
        return Triple(f, cache.getString("a_$key", null), cache.getLong("t_$key", 0L))
    }
}
