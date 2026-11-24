package dev.vis.weather.alerts

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import dev.vis.weather.L
import dev.vis.weather.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** How an alert is triggered. */
enum class Trigger {
    /** Checked by the periodic background job (every 15–30 min). */
    WATCH,
    /** Fires once a day at [AlertConfig.time]. */
    DAILY,
    /** Not a notification event: a persistent notification refreshed in the background. */
    PERSISTENT,
}

enum class AlertGroup(@StringRes private val titleRes: Int) {
    SAFETY(R.string.al_group_safety),
    DAILY(R.string.al_group_daily),
    HEALTH(R.string.al_group_health),
    MORE(R.string.al_group_more);

    val title: String get() = L.s(titleRes)
}

/** Everything the app can notify about. Order = order on the Alerts screen. */
enum class AlertType(
    @StringRes private val titleRes: Int,
    @StringRes private val subtitleRes: Int,
    @StringRes private val descriptionRes: Int,
    val trigger: Trigger,
    val group: AlertGroup,
    val channel: Channel,
    val defaultTime: Int = 7 * 60,
) {
    WARNINGS(
        R.string.al_type_warnings_title, R.string.al_type_warnings_subtitle, R.string.al_type_warnings_desc,
        Trigger.WATCH, AlertGroup.SAFETY, Channel.WARNINGS,
    ),
    NOWCAST(
        R.string.al_type_nowcast_title, R.string.al_type_nowcast_subtitle, R.string.al_type_nowcast_desc,
        Trigger.WATCH, AlertGroup.SAFETY, Channel.NOWCAST,
    ),
    RAIN(
        R.string.al_type_rain_title, R.string.al_type_rain_subtitle, R.string.al_type_rain_desc,
        Trigger.DAILY, AlertGroup.SAFETY, Channel.ALERTS, defaultTime = 7 * 60 + 30,
    ),
    STORM(
        R.string.al_type_storm_title, R.string.al_type_storm_subtitle, R.string.al_type_storm_desc,
        Trigger.WATCH, AlertGroup.SAFETY, Channel.ALERTS,
    ),
    TODAY(
        R.string.al_type_today_title, R.string.al_type_today_subtitle, R.string.al_type_today_desc,
        Trigger.DAILY, AlertGroup.DAILY, Channel.DAILY, defaultTime = 7 * 60,
    ),
    TOMORROW(
        R.string.al_type_tomorrow_title, R.string.al_type_tomorrow_subtitle, R.string.al_type_tomorrow_desc,
        Trigger.DAILY, AlertGroup.DAILY, Channel.DAILY, defaultTime = 19 * 60,
    ),
    WARDROBE(
        R.string.al_type_wardrobe_title, R.string.al_type_wardrobe_subtitle, R.string.al_type_wardrobe_desc,
        Trigger.DAILY, AlertGroup.DAILY, Channel.DAILY, defaultTime = 7 * 60,
    ),
    FROST(
        R.string.al_type_frost_title, R.string.al_type_frost_subtitle, R.string.al_type_frost_desc,
        Trigger.DAILY, AlertGroup.HEALTH, Channel.ALERTS, defaultTime = 19 * 60 + 30,
    ),
    UV(
        R.string.al_type_uv_title, R.string.al_type_uv_subtitle, R.string.al_type_uv_desc,
        Trigger.DAILY, AlertGroup.HEALTH, Channel.ALERTS, defaultTime = 8 * 60,
    ),
    HEAT(
        R.string.al_type_heat_title, R.string.al_type_heat_subtitle, R.string.al_type_heat_desc,
        Trigger.WATCH, AlertGroup.HEALTH, Channel.WARNINGS,
    ),
    AIR(
        R.string.al_type_air_title, R.string.al_type_air_subtitle, R.string.al_type_air_desc,
        Trigger.DAILY, AlertGroup.HEALTH, Channel.ALERTS, defaultTime = 8 * 60,
    ),
    POLLEN(
        R.string.al_type_pollen_title, R.string.al_type_pollen_subtitle, R.string.al_type_pollen_desc,
        Trigger.DAILY, AlertGroup.HEALTH, Channel.ALERTS, defaultTime = 7 * 60 + 15,
    ),
    TABLET(
        R.string.al_type_tablet_title, R.string.al_type_tablet_subtitle, R.string.al_type_tablet_desc,
        Trigger.DAILY, AlertGroup.HEALTH, Channel.REMINDERS, defaultTime = 7 * 60,
    ),
    PRESSURE(
        R.string.al_type_pressure_title, R.string.al_type_pressure_subtitle, R.string.al_type_pressure_desc,
        Trigger.WATCH, AlertGroup.HEALTH, Channel.ALERTS,
    ),
    FRIENDS(
        R.string.al_type_friends_title, R.string.al_type_friends_subtitle, R.string.al_type_friends_desc,
        Trigger.WATCH, AlertGroup.MORE, Channel.FRIENDS,
    ),
    ONGOING(
        R.string.al_type_ongoing_title, R.string.al_type_ongoing_subtitle, R.string.al_type_ongoing_desc,
        Trigger.PERSISTENT, AlertGroup.MORE, Channel.ONGOING,
    ),
    ;

    val title: String get() = L.s(titleRes)
    val subtitle: String get() = L.s(subtitleRes)
    val description: String get() = L.s(descriptionRes)
}

/** Notification channels (user can fine-tune sound/vibration per channel in system settings). */
enum class Channel(val id: String, @StringRes private val titleRes: Int, @StringRes private val descriptionRes: Int, val importance: Int) {
    WARNINGS("warnings", R.string.al_channel_warnings_title, R.string.al_channel_warnings_desc, 4),
    NOWCAST("nowcast", R.string.al_channel_nowcast_title, R.string.al_channel_nowcast_desc, 4),
    ALERTS("alerts", R.string.al_channel_alerts_title, R.string.al_channel_alerts_desc, 3),
    DAILY("daily", R.string.al_channel_daily_title, R.string.al_channel_daily_desc, 3),
    REMINDERS("reminders", R.string.al_channel_reminders_title, R.string.al_channel_reminders_desc, 3),
    FRIENDS("friends", R.string.al_channel_friends_title, R.string.al_channel_friends_desc, 3),
    ONGOING("ongoing", R.string.al_channel_ongoing_title, R.string.al_channel_ongoing_desc, 2);

    val title: String get() = L.s(titleRes)
    val description: String get() = L.s(descriptionRes)
}

/**
 * Settings for one alert. Not every field applies to every type; see the detail screen for which are shown.
 * Times are minutes after midnight in the device's time zone.
 */
data class AlertConfig(
    val enabled: Boolean = false,
    val time: Int = 7 * 60,
    /** "auto" = the place selected in the app, "current" = device location, or a saved place key. */
    val location: String = "auto",
    /** Type-specific threshold (°C, UV index, EAQI band, km/h, % chance, hPa/3h…). */
    val threshold: Double = 0.0,
    /** Look-ahead window in hours. */
    val lookahead: Int = 3,
    /** RAIN: false = once daily at [time], true = all day between [time] and [endTime]. */
    val allDay: Boolean = false,
    val endTime: Int = 19 * 60,
    /** 0 low, 1 moderate, 2 high (pollen / pressure sensitivity). */
    val level: Int = 1,
    /** Free-form flags: pollen allergens ("trees","grass","weeds"), wardrobe ("walk","transit","balanced"), "saved" (all saved places). */
    val flags: Set<String> = emptySet(),
    /** WARDROBE: optional work place key; TABLET: "pollen-days" in flags limits it to high-pollen days. */
    val extra: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled).put("time", time).put("location", location).put("threshold", threshold)
        .put("lookahead", lookahead).put("allDay", allDay).put("endTime", endTime).put("level", level)
        .put("flags", JSONArray(flags.toList())).put("extra", extra)

    companion object {
        fun fromJson(o: JSONObject, d: AlertConfig) = AlertConfig(
            enabled = o.optBoolean("enabled", d.enabled),
            time = o.optInt("time", d.time),
            location = o.optString("location", d.location),
            threshold = o.optDouble("threshold", d.threshold),
            lookahead = o.optInt("lookahead", d.lookahead),
            allDay = o.optBoolean("allDay", d.allDay),
            endTime = o.optInt("endTime", d.endTime),
            level = o.optInt("level", d.level),
            flags = o.optJSONArray("flags")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: d.flags,
            extra = o.optString("extra", d.extra),
        )

        fun default(type: AlertType): AlertConfig {
            val base = AlertConfig(time = type.defaultTime)
            return when (type) {
                AlertType.WARNINGS -> base.copy(enabled = true, threshold = Severity.MODERATE.ordinal.toDouble(), flags = setOf("saved"))
                // threshold: mm per 15 minutes; lookahead: minutes
                AlertType.NOWCAST -> base.copy(threshold = 0.1, lookahead = 60, flags = setOf("snow"))
                AlertType.RAIN -> base.copy(threshold = 60.0, lookahead = 4, endTime = 19 * 60)
                AlertType.STORM -> base.copy(threshold = 65.0, lookahead = 12)
                AlertType.FROST -> base.copy(threshold = 0.0)
                AlertType.UV -> base.copy(threshold = 6.0)
                // Wet bulb °C: 28 = "very difficult", 31 = "critical"
                AlertType.HEAT -> base.copy(threshold = 28.0, lookahead = 12)
                AlertType.AIR -> base.copy(threshold = 3.0)
                AlertType.POLLEN -> base.copy(flags = setOf("trees", "grass", "weeds"), level = 1)
                AlertType.PRESSURE -> base.copy(level = 1)
                AlertType.WARDROBE -> base.copy(flags = setOf("balanced"), level = 1)
                AlertType.FRIENDS -> base.copy(threshold = Severity.SEVERE.ordinal.toDouble())
                else -> base
            }
        }
    }
}

/** A delivered notification, kept for the in-app history. */
data class AlertLog(val time: Long, val type: AlertType, val title: String, val text: String, val place: String)

class AlertPrefs private constructor(context: Context) {
    private val sp = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    private val _configs = MutableStateFlow(load())
    val configs: StateFlow<Map<AlertType, AlertConfig>> = _configs.asStateFlow()

    private fun load(): Map<AlertType, AlertConfig> = AlertType.entries.associateWith { t ->
        val d = AlertConfig.default(t)
        sp.getString("cfg_${t.name}", null)?.let { runCatching { AlertConfig.fromJson(JSONObject(it), d) }.getOrNull() } ?: d
    }

    fun config(type: AlertType): AlertConfig = _configs.value.getValue(type)

    fun update(type: AlertType, transform: (AlertConfig) -> AlertConfig) {
        val c = transform(config(type))
        _configs.value = _configs.value + (type to c)
        sp.edit { putString("cfg_${type.name}", c.toJson().toString()) }
    }

    // ---- global options

    var quietHours: Boolean
        get() = sp.getBoolean("quiet", false)
        set(v) = sp.edit { putBoolean("quiet", v) }
    var quietStart: Int
        get() = sp.getInt("quiet_start", 22 * 60)
        set(v) = sp.edit { putInt("quiet_start", v) }
    var quietEnd: Int
        get() = sp.getInt("quiet_end", 7 * 60)
        set(v) = sp.edit { putInt("quiet_end", v) }
    /** Dismissed the "allow exact alarms" banner. */
    var exactBannerDismissed: Boolean
        get() = sp.getBoolean("exact_banner_dismissed", false)
        set(v) = sp.edit { putBoolean("exact_banner_dismissed", v) }

    // ---- delivery state (dedupe + cooldowns)

    fun wasSent(key: String): Boolean = sp.getLong("sent_$key", 0L) > 0
    fun lastSent(key: String): Long = sp.getLong("sent_$key", 0L)
    fun markSent(key: String, time: Long = System.currentTimeMillis()) = sp.edit { putLong("sent_$key", time) }

    fun getString(key: String): String? = sp.getString("st_$key", null)
    fun putString(key: String, value: String?) = sp.edit { putString("st_$key", value) }

    /** Drop dedupe keys older than two weeks so the file doesn't grow forever. */
    fun prune() {
        val cutoff = System.currentTimeMillis() - 14 * 86_400_000L
        val stale = sp.all.filter { (k, v) -> k.startsWith("sent_") && v is Long && v < cutoff }.keys
        if (stale.isNotEmpty()) sp.edit { stale.forEach { remove(it) } }
    }

    // ---- history

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<AlertLog>> = _history.asStateFlow()

    private fun loadHistory(): List<AlertLog> = runCatching {
        val a = JSONArray(sp.getString("history", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val type = AlertType.entries.firstOrNull { it.name == o.getString("type") } ?: return@mapNotNull null
            AlertLog(o.getLong("time"), type, o.getString("title"), o.getString("text"), o.optString("place"))
        }
    }.getOrDefault(emptyList())

    fun log(entry: AlertLog) {
        val list = (listOf(entry) + loadHistory()).take(40)
        _history.value = list
        sp.edit {
            putString("history", JSONArray().apply {
                list.forEach {
                    put(JSONObject().put("time", it.time).put("type", it.type.name).put("title", it.title).put("text", it.text).put("place", it.place))
                }
            }.toString())
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        sp.edit { remove("history") }
    }

    companion object {
        @Volatile private var instance: AlertPrefs? = null
        fun get(context: Context): AlertPrefs =
            instance ?: synchronized(this) { instance ?: AlertPrefs(context.applicationContext).also { instance = it } }
    }
}
