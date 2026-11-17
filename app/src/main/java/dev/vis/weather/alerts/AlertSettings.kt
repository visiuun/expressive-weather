package dev.vis.weather.alerts

import android.content.Context
import androidx.core.content.edit
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

enum class AlertGroup(val title: String) {
    SAFETY("Warnings & rain"),
    DAILY("Daily briefings"),
    HEALTH("Health & comfort"),
    MORE("More"),
}

/** Everything the app can notify about. Order = order on the Alerts screen. */
enum class AlertType(
    val title: String,
    val subtitle: String,
    val description: String,
    val trigger: Trigger,
    val group: AlertGroup,
    val channel: Channel,
    val defaultTime: Int = 7 * 60,
) {
    WARNINGS(
        "Weather warnings", "Official yellow, amber & red warnings",
        "Get notified as soon as a national weather service issues a warning for your location, and when it is upgraded. " +
            "Covers the US (NWS), Canada (ECCC), Germany (DWD) and 36 more European countries (MeteoAlarm).",
        Trigger.WATCH, AlertGroup.SAFETY, Channel.WARNINGS,
    ),
    NOWCAST(
        "Imminent rain & snow", "Minutes before rain or snow starts",
        "A heads-up shortly before rain or snow arrives, with a live countdown that updates until it starts and tells you when it will stop. " +
            "Uses the 15-minute nowcast, which is high-resolution radar-driven data in Europe and North America.",
        Trigger.WATCH, AlertGroup.SAFETY, Channel.NOWCAST,
    ),
    RAIN(
        "Rain alert", "Rain forecast in the next few hours",
        "Grab an umbrella: get notified when rain is likely in the hours ahead. Check once at a set time, or all day during the hours you choose.",
        Trigger.DAILY, AlertGroup.SAFETY, Channel.ALERTS, defaultTime = 7 * 60 + 30,
    ),
    STORM(
        "Wind & storm alert", "Strong gusts or thunderstorms ahead",
        "Get notified when gusts above your limit or thunderstorms are forecast in the next 12 hours, even when no official warning has been issued.",
        Trigger.WATCH, AlertGroup.SAFETY, Channel.ALERTS,
    ),
    TODAY(
        "Morning briefing", "Your day's weather at a glance",
        "A short summary each morning: conditions, high and low, rain timing, wind and anything notable, compared with yesterday.",
        Trigger.DAILY, AlertGroup.DAILY, Channel.DAILY, defaultTime = 7 * 60,
    ),
    TOMORROW(
        "Weather for tomorrow", "Evening summary of tomorrow",
        "Each evening, a readable summary of tomorrow so you can plan ahead: how it compares with today, rain, wind and snow.",
        Trigger.DAILY, AlertGroup.DAILY, Channel.DAILY, defaultTime = 19 * 60,
    ),
    WARDROBE(
        "What to wear", "Clothing and carry-item advice",
        "Daily advice on what to wear and bring, tailored to your commute, how you feel the cold, and the weather at home and at work.",
        Trigger.DAILY, AlertGroup.DAILY, Channel.DAILY, defaultTime = 7 * 60,
    ),
    FROST(
        "Frost alert", "Overnight temperatures at or below freezing",
        "Get notified the evening before when the overnight temperature is forecast to drop to your threshold, so you can protect plants, pipes and pets and allow time to de-ice the car.",
        Trigger.DAILY, AlertGroup.HEALTH, Channel.ALERTS, defaultTime = 19 * 60 + 30,
    ),
    UV(
        "UV alert", "UV index above your threshold",
        "Each morning, a reminder to protect your skin when the UV index will exceed your threshold, with the hours when protection is needed.",
        Trigger.DAILY, AlertGroup.HEALTH, Channel.ALERTS, defaultTime = 8 * 60,
    ),
    HEAT(
        "Dangerous wet bulb", "When heat and humidity get dangerous",
        "The wet-bulb temperature combines heat and humidity into how well sweat can cool you. Get warned as soon as it's forecast to reach a dangerous level, so you can limit exertion, find shade or cool air and stay hydrated.",
        Trigger.WATCH, AlertGroup.HEALTH, Channel.WARNINGS,
    ),
    AIR(
        "Air quality alert", "Air quality at or below your threshold",
        "At your alert time, checks the European Air Quality Index forecast. If it reaches your threshold or worse, you'll get a heads-up to limit outdoor exertion.",
        Trigger.DAILY, AlertGroup.HEALTH, Channel.ALERTS, defaultTime = 8 * 60,
    ),
    POLLEN(
        "Pollen alert", "Elevated pollen for your allergies",
        "Each morning, checks the pollen forecast for the allergens you choose and lets you know when levels exceed your sensitivity. Pollen forecasts cover Europe.",
        Trigger.DAILY, AlertGroup.HEALTH, Channel.ALERTS, defaultTime = 7 * 60 + 15,
    ),
    TABLET(
        "Hay fever tablet reminder", "A daily reminder to take your tablet",
        "A simple daily reminder at the time you choose. Optionally only on days when your pollen alert threshold is reached.",
        Trigger.DAILY, AlertGroup.HEALTH, Channel.REMINDERS, defaultTime = 7 * 60,
    ),
    PRESSURE(
        "Pressure drop alert", "Rapid falls in barometric pressure",
        "Rapid pressure drops often come before stormy weather and can trigger headaches or migraines for some people. Get notified when pressure is forecast to fall quickly over 3 hours.",
        Trigger.WATCH, AlertGroup.HEALTH, Channel.ALERTS,
    ),
    FRIENDS(
        "Family & friends", "Warnings where your people are",
        "Get notified when an official warning is issued at the place of someone in your Family & friends list.",
        Trigger.WATCH, AlertGroup.MORE, Channel.FRIENDS,
    ),
    ONGOING(
        "Status bar weather", "Current temperature always in view",
        "Shows the current temperature in the status bar and a silent notification with today's forecast, refreshed in the background.",
        Trigger.PERSISTENT, AlertGroup.MORE, Channel.ONGOING,
    ),
}

/** Notification channels (user can fine-tune sound/vibration per channel in system settings). */
enum class Channel(val id: String, val title: String, val description: String, val importance: Int) {
    WARNINGS("warnings", "Weather warnings", "Official severe weather warnings", 4),
    NOWCAST("nowcast", "Imminent rain & snow", "Rain or snow starting within the hour", 4),
    ALERTS("alerts", "Weather alerts", "Rain, wind, frost, UV, air quality, pollen and pressure alerts", 3),
    DAILY("daily", "Daily briefings", "Morning briefing, tomorrow's weather and what to wear", 3),
    REMINDERS("reminders", "Reminders", "Hay fever tablet reminder", 3),
    FRIENDS("friends", "Family & friends", "Warnings at your family and friends' places", 3),
    ONGOING("ongoing", "Status bar weather", "Current conditions, always visible", 2),
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
