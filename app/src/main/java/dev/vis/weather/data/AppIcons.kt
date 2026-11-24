package dev.vis.weather.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import dev.vis.weather.L
import dev.vis.weather.R

/**
 * Switches the launcher icon by enabling exactly one <activity-alias> ("dev.vis.weather.Launcher…").
 *
 * [AppIcon.DYNAMIC] is a mode, not an alias: it resolves to one of the weather icons through [forWeather].
 * Several enum entries can share an alias (DYNAMIC and SUNNY), so everything here works on alias names.
 */
object AppIcons {
    /** What an icon looks like, for drawing it inside the app: a background (colour or drawable) and a foreground. */
    class Layers(val background: Long?, @param:DrawableRes val backgroundRes: Int?, @param:DrawableRes val foreground: Int)

    val classic = listOf(AppIcon.OLED, AppIcon.LIGHT, AppIcon.COLOR)
    val weather = listOf(
        AppIcon.SUNNY, AppIcon.CLEAR_NIGHT, AppIcon.PARTLY, AppIcon.PARTLY_NIGHT, AppIcon.CLOUDY,
        AppIcon.FOGGY, AppIcon.RAINY, AppIcon.SNOWY, AppIcon.STORMY,
    )

    fun label(icon: AppIcon): String = when (icon) {
        AppIcon.OLED -> L.s(R.string.d_icon_black)
        AppIcon.LIGHT -> L.s(R.string.d_icon_white)
        AppIcon.COLOR -> L.s(R.string.d_icon_color)
        AppIcon.DYNAMIC -> L.s(R.string.d_icon_dynamic)
        AppIcon.SUNNY -> L.s(R.string.d_icon_sunny)
        AppIcon.CLEAR_NIGHT -> L.s(R.string.d_icon_clear_night)
        AppIcon.PARTLY -> L.s(R.string.d_icon_partly)
        AppIcon.PARTLY_NIGHT -> L.s(R.string.d_icon_partly_night)
        AppIcon.CLOUDY -> L.s(R.string.d_icon_cloudy)
        AppIcon.FOGGY -> L.s(R.string.d_icon_foggy)
        AppIcon.RAINY -> L.s(R.string.d_icon_rainy)
        AppIcon.SNOWY -> L.s(R.string.d_icon_snowy)
        AppIcon.STORMY -> L.s(R.string.d_icon_stormy)
    }

    fun layers(icon: AppIcon): Layers = when (icon) {
        AppIcon.OLED -> Layers(0xFF000000, null, R.drawable.ic_launcher_foreground_oled)
        AppIcon.LIGHT -> Layers(0xFFFFFFFF, null, R.drawable.ic_launcher_foreground_light)
        AppIcon.COLOR -> Layers(0xFF0F1B2E, null, R.drawable.ic_launcher_foreground)
        AppIcon.DYNAMIC, AppIcon.SUNNY -> Layers(null, R.drawable.ic_launcher_bg_sunny, R.drawable.ic_launcher_fg_sunny)
        AppIcon.CLEAR_NIGHT -> Layers(null, R.drawable.ic_launcher_bg_clear_night, R.drawable.ic_launcher_fg_clear_night)
        AppIcon.PARTLY -> Layers(null, R.drawable.ic_launcher_bg_partly, R.drawable.ic_launcher_fg_partly)
        AppIcon.PARTLY_NIGHT -> Layers(null, R.drawable.ic_launcher_bg_partly_night, R.drawable.ic_launcher_fg_partly_night)
        AppIcon.CLOUDY -> Layers(null, R.drawable.ic_launcher_bg_cloudy, R.drawable.ic_launcher_fg_cloudy)
        AppIcon.FOGGY -> Layers(null, R.drawable.ic_launcher_bg_foggy, R.drawable.ic_launcher_fg_foggy)
        AppIcon.RAINY -> Layers(null, R.drawable.ic_launcher_bg_rainy, R.drawable.ic_launcher_fg_rainy)
        AppIcon.SNOWY -> Layers(null, R.drawable.ic_launcher_bg_snowy, R.drawable.ic_launcher_fg_snowy)
        AppIcon.STORMY -> Layers(null, R.drawable.ic_launcher_bg_stormy, R.drawable.ic_launcher_fg_stormy)
    }

    /** The weather icon for a WMO [code]; null code (no data yet) shows the sunny one. */
    fun forWeather(code: Int?, isDay: Boolean): AppIcon = when (code?.let(WeatherInfo::sky)) {
        null -> AppIcon.SUNNY
        Sky.CLEAR -> if (isDay) AppIcon.SUNNY else AppIcon.CLEAR_NIGHT
        Sky.PARTLY -> if (isDay) AppIcon.PARTLY else AppIcon.PARTLY_NIGHT
        Sky.CLOUDY -> AppIcon.CLOUDY
        Sky.FOG -> AppIcon.FOGGY
        Sky.DRIZZLE, Sky.RAIN, Sky.FREEZING -> AppIcon.RAINY
        Sky.SNOW -> AppIcon.SNOWY
        Sky.THUNDER -> AppIcon.STORMY
    }

    /** The concrete icon that should be on the launcher: DYNAMIC resolved through the weather. */
    fun resolve(icon: AppIcon, code: Int?, isDay: Boolean): AppIcon =
        if (icon == AppIcon.DYNAMIC) forWeather(code, isDay) else icon

    /** Current conditions from a forecast; the cached "current" block goes stale, so after an hour use the hourly one. */
    fun conditions(forecast: Forecast?): Pair<Int, Boolean>? {
        forecast ?: return null
        val now = System.currentTimeMillis() / 1000
        if (now - forecast.current.time < 3600) return forecast.current.code to forecast.current.isDay
        val h = forecast.hourlyAll.lastOrNull { it.time <= now } ?: forecast.hourlyAll.firstOrNull()
            ?: return forecast.current.code to forecast.current.isDay
        return h.code to h.isDay
    }

    private fun aliases() = AppIcon.entries.map { it.alias }.distinct()
    private fun cn(context: Context, alias: String) = ComponentName(context.packageName, "dev.vis.weather.$alias")

    private fun isEnabled(context: Context, pm: PackageManager, alias: String): Boolean {
        val c = cn(context, alias)
        return when (runCatching { pm.getComponentEnabledSetting(c) }.getOrNull()) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                // Falls back to android:enabled in the manifest.
                runCatching { pm.getActivityInfo(c, PackageManager.MATCH_DISABLED_COMPONENTS).enabled }.getOrDefault(false)
            else -> false
        }
    }

    /** The alias that's on the launcher right now (first enabled one), or null. */
    fun enabledAlias(context: Context): String? {
        val pm = context.packageManager
        return aliases().firstOrNull { isEnabled(context, pm, it) }
    }

    /**
     * Shows [icon] on the launcher. For [AppIcon.DYNAMIC] the weather icon is picked from [code] / [isDay].
     * Does nothing when that alias is already the only enabled one, so repeated calls don't make launchers redraw.
     */
    fun apply(context: Context, icon: AppIcon, code: Int? = null, isDay: Boolean = true) {
        val target = resolve(icon, code, isDay).alias
        val pm = context.packageManager
        val all = aliases()
        val enabled = all.filter { isEnabled(context, pm, it) }
        if (enabled == listOf(target)) return
        // Enable the new one first so there's never a moment without a launcher entry.
        val ok = runCatching {
            pm.setComponentEnabledSetting(cn(context, target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }.isSuccess
        if (!ok) return
        all.filter { it != target && it in enabled }.forEach {
            runCatching {
                pm.setComponentEnabledSetting(cn(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }

    /** For background refreshes: when the icon follows the weather, match it to [forecast]. */
    fun refreshDynamic(context: Context, settings: Settings, forecast: Forecast?) {
        if (settings.appIcon != AppIcon.DYNAMIC) return
        val (code, isDay) = conditions(forecast) ?: return
        apply(context, AppIcon.DYNAMIC, code, isDay)
    }

    /**
     * Brings the launcher in line with the saved setting, using the cached forecast of the selected
     * location for DYNAMIC. Call when the app goes to the background (e.g. MainActivity.onStop).
     */
    fun sync(context: Context) {
        val prefs = Prefs(context)
        val settings = prefs.settings.value
        if (settings.appIcon != AppIcon.DYNAMIC) return apply(context, settings.appIcon)
        val now = conditions(selectedForecast(context))
        apply(context, AppIcon.DYNAMIC, now?.first, now?.second ?: true)
    }

    /** The icon the launcher should show for [settings] right now (DYNAMIC resolved from the cached forecast). */
    fun effective(context: Context, settings: Settings): AppIcon {
        if (settings.appIcon != AppIcon.DYNAMIC) return settings.appIcon
        val now = conditions(selectedForecast(context))
        return forWeather(now?.first, now?.second ?: true)
    }

    /** Cached forecast for the location selected in the app, or null. */
    fun selectedForecast(context: Context): Forecast? {
        val prefs = Prefs(context)
        val place = when (val sel = prefs.selection) {
            null -> prefs.currentPlace ?: prefs.places.firstOrNull()
            dev.vis.weather.ui.UiState.CURRENT -> prefs.currentPlace
            else -> prefs.places.firstOrNull { it.key == sel }
        } ?: return null
        return ForecastRepo.cached(context, place)
    }
}
