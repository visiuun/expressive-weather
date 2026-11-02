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

    private fun loadSettings() = Settings(
        theme = enumOf("theme", ThemeMode.SYSTEM),
        oled = sp.getBoolean("oled", false),
        dynamicColor = sp.getBoolean("dynamic", true),
        tempUnit = enumOf("temp", TempUnit.C),
        windUnit = enumOf("wind", WindUnit.KMH),
        precipUnit = enumOf("precip", PrecipUnit.MM),
        pressureUnit = enumOf("pressure", PressureUnit.HPA),
        use24h = sp.getBoolean("24h", true),
        heroBackground = sp.getBoolean("hero_bg", false),
        cardStyle = enumOf("card_style", CardStyle.FILLED),
        monoIcons = sp.getBoolean("mono_icons", false),
        appIcon = enumOf("app_icon", AppIcon.OLED),
        autoUpdate = sp.getBoolean("auto_update", true),
    )

    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        sp.edit {
            putString("theme", s.theme.name)
            putBoolean("oled", s.oled)
            putBoolean("dynamic", s.dynamicColor)
            putString("temp", s.tempUnit.name)
            putString("wind", s.windUnit.name)
            putString("precip", s.precipUnit.name)
            putString("pressure", s.pressureUnit.name)
            putBoolean("24h", s.use24h)
            putBoolean("hero_bg", s.heroBackground)
            putString("card_style", s.cardStyle.name)
            putBoolean("mono_icons", s.monoIcons)
            putString("app_icon", s.appIcon.name)
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

    fun saveCache(key: String, forecast: String, air: String?, time: Long) = cache.edit {
        putString("f_$key", forecast)
        putString("a_$key", air)
        putLong("t_$key", time)
    }

    fun loadCache(key: String): Triple<String, String?, Long>? {
        val f = cache.getString("f_$key", null) ?: return null
        return Triple(f, cache.getString("a_$key", null), cache.getLong("t_$key", 0L))
    }
}
