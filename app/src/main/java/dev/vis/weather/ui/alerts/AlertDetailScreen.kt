@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.vis.weather.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.vis.weather.alerts.AlertConfig
import dev.vis.weather.alerts.AlertEngine
import dev.vis.weather.alerts.AlertPrefs
import dev.vis.weather.alerts.AlertScheduler
import dev.vis.weather.alerts.AlertType
import dev.vis.weather.alerts.Severity
import dev.vis.weather.alerts.Trigger
import dev.vis.weather.data.Place
import dev.vis.weather.data.Settings
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.components.MorphingBlob
import dev.vis.weather.ui.components.SettingsChoice
import dev.vis.weather.ui.components.SettingsGroup
import dev.vis.weather.ui.components.SettingsItemSurface
import dev.vis.weather.ui.components.SettingsSlider
import dev.vis.weather.ui.components.SettingsSwitch
import dev.vis.weather.ui.components.ShapeBadge
import dev.vis.weather.ui.components.SubScreen
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun AlertDetailScreen(
    type: AlertType,
    settings: Settings,
    places: List<Place>,
    currentPlace: Place?,
    onBack: () -> Unit,
    onManageFriends: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { AlertPrefs.get(context) }
    val configs by prefs.configs.collectAsState()
    val c = configs.getValue(type)
    val scope = rememberCoroutineScope()
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var picking by remember { mutableIntStateOf(0) }
    val fmt = remember(settings) { Fmt(settings, ZoneId.systemDefault()) }

    fun set(t: (AlertConfig) -> AlertConfig) {
        prefs.update(type, t)
        AlertScheduler.sync(context)
    }
    val askPermission = rememberNotificationPermission { ok ->
        if (ok) {
            set { it.copy(enabled = true) }
            if (type.trigger != Trigger.DAILY) AlertScheduler.runWatchNow(context)
        }
    }

    SubScreen(type.title, onBack) { w ->
        item {
            Hero(type, c.enabled, w)
        }
        item {
            Surface(
                onClick = { if (!c.enabled) askPermission() else set { it.copy(enabled = false) } },
                modifier = w,
                shape = RoundedCornerShape(32.dp),
                color = if (c.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (c.enabled) "On" else "Off",
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        modifier = Modifier.weight(1f),
                        color = if (c.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(checked = c.enabled, onCheckedChange = { if (it) askPermission() else set { x -> x.copy(enabled = false) } })
                }
            }
        }
        item {
            SettingsGroup("How it works", w) {
                SettingsItemSurface(0, 1) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(howItWorks(type, c, settings, fmt), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ---- schedule
        if (type.trigger == Trigger.DAILY) {
            item {
                SettingsGroup(if (type == AlertType.RAIN && c.allDay) "Active hours" else "Notification time", w) {
                    if (type == AlertType.RAIN) {
                        SettingsChoice(
                            "Check", null, MaterialShapes.Circle, 0, 2, listOf(false, true), c.allDay,
                            { if (it) "All day" else "Once daily" },
                        ) { v -> set { it.copy(allDay = v) } }
                    }
                    SettingsItemSurface(if (type == AlertType.RAIN) 1 else 0, if (type == AlertType.RAIN) 2 else 1) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (type == AlertType.RAIN && c.allDay) {
                                TimeChip("Start", timeLabel(c.time, settings.use24h), Modifier.weight(1f)) { picking = 1 }
                                TimeChip("End", timeLabel(c.endTime, settings.use24h), Modifier.weight(1f)) { picking = 2 }
                            } else {
                                TimeChip(
                                    when (type) {
                                        AlertType.TABLET -> "Reminder time"
                                        else -> "Alert time"
                                    },
                                    timeLabel(c.time, settings.use24h), Modifier.weight(1f),
                                ) { picking = 1 }
                            }
                        }
                    }
                }
            }
        }

        // ---- type-specific
        item { Options(type, c, settings, places, w, onManageFriends) { set(it) } }

        // ---- location
        if (type != AlertType.FRIENDS && type != AlertType.TABLET) {
            item {
                LocationPicker(
                    if (type == AlertType.WARDROBE) "Home location" else "Location", c.location, places, currentPlace, w,
                ) { v -> set { it.copy(location = v) } }
            }
        }
        if (type == AlertType.WARDROBE) {
            item {
                LocationPicker("Work location", c.extra.ifBlank { "none" }, places, currentPlace, w, allowNone = true, auto = false) { v ->
                    set { it.copy(extra = if (v == "none") "" else v) }
                }
            }
        }

        item {
            Column(w, horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        testing = true
                        scope.launch {
                            testMessage = AlertEngine.test(context, type) ?: "Sent. Check your notifications."
                            testing = false
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Rounded.NotificationsActive, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (testing) "Checking the forecast…" else "Send a test notification", style = MaterialTheme.typography.titleMedium)
                }
                testMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        disclaimer(type)?.let { text ->
            item {
                Text(text, w.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (picking != 0) {
        TimeDialog(if (picking == 1) c.time else c.endTime, settings.use24h, onDismiss = { picking = 0 }) { m ->
            if (picking == 1) set { it.copy(time = m) } else set { it.copy(endTime = m) }
            picking = 0
        }
    }
}

@Composable
private fun Hero(type: AlertType, enabled: Boolean, modifier: Modifier) {
    Column(modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            MorphingBlob(
                listOf(type.shape, MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst),
                if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                Modifier.size(150.dp),
            )
            Icon(
                type.icon, null, Modifier.size(64.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            type.description, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun howItWorks(type: AlertType, c: AlertConfig, s: Settings, fmt: Fmt): String {
    val t = timeLabel(c.time, s.use24h)
    return when (type) {
        AlertType.WARNINGS -> "About every 30 minutes the app checks the national weather service for your location" +
            (if ("saved" in c.flags) " and your saved places" else "") +
            ". You're notified once per warning, and again if it's upgraded. Amber and red warnings break through quiet hours."
        AlertType.NOWCAST -> "Every 15 minutes the app checks the 15-minute precipitation nowcast. When ${if (c.threshold >= 2) "heavy " else if (c.threshold >= 0.5) "moderate " else ""}rain" +
            (if ("snow" in c.flags) " or snow" else "") + " is due within ${c.lookahead} minutes, you get a live countdown that keeps updating until it starts, " +
            "then tells you when it should stop. On Android 16 it appears as a Live Update chip in the status bar."
        AlertType.RAIN -> if (c.allDay) "Roughly every 15 minutes between ${timeLabel(c.time, s.use24h)} and ${timeLabel(c.endTime, s.use24h)}, the app looks ${c.lookahead} hours ahead. " +
            "When the chance of rain reaches ${c.threshold.roundToInt()}%, you'll get a heads-up, at most once every 3 hours."
        else "At $t, the app checks the next ${c.lookahead} hours. If the chance of rain reaches ${c.threshold.roundToInt()}%, you'll get a notification with the expected start time."
        AlertType.STORM -> "Every 30 minutes the app looks ${c.lookahead} hours ahead for gusts of ${fmt.wind(c.threshold)} or more, or thunderstorms. At most one alert every 8 hours."
        AlertType.TODAY -> "At $t you get a short briefing: conditions, high and low, when rain is likely, notable wind or UV, and how it compares with yesterday."
        AlertType.TOMORROW -> "At $t the app looks at tomorrow's forecast and sends a short, readable summary of what to expect and how it compares with today."
        AlertType.WARDROBE -> "At $t (or the evening before if set after 15:00) the app looks at the hours you're out, adjusts for how you feel the cold and your commute, and suggests what to wear and bring." +
            if (c.extra.isNotBlank()) " Your work location's forecast is used for 09:00–17:00." else ""
        AlertType.FROST -> "At $t the app checks tonight's lowest temperature until 10:00 tomorrow. If it reaches ${fmt.temp(c.threshold)} or below, you'll get a frost warning."
        AlertType.UV -> "At $t the app checks today's UV forecast. If it will exceed ${c.threshold.roundToInt()}, you'll be told the peak time and when to protect your skin."
        AlertType.HEAT -> "Every 30 minutes the app checks the wet-bulb temperature now and for the next ${c.lookahead} hours. When it reaches ${fmt.temp(c.threshold)} (${dev.vis.weather.alerts.Briefings.coolingLevel(c.threshold).first.lowercase()}), you're warned right away, and again the same day only if it gets worse. These alerts sound even during quiet hours."
        AlertType.AIR -> "At $t the app checks the European Air Quality Index for the next 18 hours. If it reaches ${dev.vis.weather.alerts.Briefings.eaqiNames[c.threshold.roundToInt().coerceIn(1, 6)].lowercase()} or worse, you'll get an alert."
        AlertType.POLLEN -> "At $t the app checks the pollen forecast for ${c.flags.filter { it in setOf("trees", "grass", "weeds") }.joinToString().ifBlank { "your allergens" }}. " +
            "With ${listOf("low", "moderate", "high")[c.level]} sensitivity you're alerted from ${listOf("very high", "high", "moderate")[c.level]} levels."
        AlertType.TABLET -> "A daily reminder at $t" + if ("pollen-days" in c.flags) ", only on days your pollen alert threshold is reached." else "."
        AlertType.PRESSURE -> "Every 30 minutes the app looks 12 hours ahead for a pressure fall of ${AlertEngine.pressureThreshold(c.level).roundToInt()} hPa or more within 3 hours. At most one alert every 18 hours."
        AlertType.FRIENDS -> "About every 40 minutes the app checks official warnings at each Family & friends place and lets you know about ${Severity.entries[c.threshold.roundToInt()].colorName.lowercase()} warnings or worse."
        AlertType.ONGOING -> "A silent notification keeps the current temperature in your status bar, with today's high and low, rain timing and the next hours. It refreshes in the background every 15–30 minutes."
    }
}

private fun disclaimer(type: AlertType): String? = when (type) {
    AlertType.WARNINGS, AlertType.FRIENDS -> "Weather warnings are for guidance only. Always follow official advice from your national weather service and local authorities."
    AlertType.POLLEN, AlertType.TABLET -> "Pollen data is for guidance only and is not a substitute for professional advice."
    AlertType.PRESSURE, AlertType.HEAT -> "This is not medical advice. Consult a healthcare professional for health concerns."
    AlertType.NOWCAST, AlertType.RAIN -> "Precipitation forecasts are estimates and may vary from actual conditions."
    else -> null
}

@Composable
private fun Options(
    type: AlertType,
    c: AlertConfig,
    s: Settings,
    places: List<Place>,
    w: Modifier,
    onManageFriends: () -> Unit,
    set: ((AlertConfig) -> AlertConfig) -> Unit,
) {
    val fmt = remember(s) { Fmt(s, ZoneId.systemDefault()) }
    when (type) {
        AlertType.WARNINGS, AlertType.FRIENDS -> SettingsGroup("Alert levels", w) {
            val n = if (type == AlertType.WARNINGS) 2 else 2
            SettingsChoice(
                "Notify me from", null, MaterialShapes.Circle, 0, n, Severity.entries, Severity.entries[c.threshold.roundToInt().coerceIn(0, 3)],
                { it.colorName },
            ) { v -> set { it.copy(threshold = v.ordinal.toDouble()) } }
            if (type == AlertType.WARNINGS) {
                SettingsSwitch(
                    "Saved locations", "Also notify about warnings at your current location and every saved place",
                    Icons.Rounded.Place, MaterialShapes.Cookie6Sided, 1, 2, "saved" in c.flags,
                ) { on -> set { it.copy(flags = if (on) it.flags + "saved" else it.flags - "saved") } }
            } else {
                SettingsItemSurface(1, 2, onClick = onManageFriends) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShapeBadge(Icons.Rounded.Groups, MaterialShapes.Flower, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Manage family & friends", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LevelLegend()
        }
        AlertType.NOWCAST -> SettingsGroup("Sensitivity", w) {
            val levels = listOf(0.1, 0.5, 2.0)
            SettingsChoice(
                "Intensity", null, MaterialShapes.Circle, 0, 3, levels, levels.minBy { kotlin.math.abs(it - c.threshold) },
                { when (it) { 0.1 -> "Any"; 0.5 -> "Moderate+"; else -> "Heavy" } },
            ) { v -> set { it.copy(threshold = v) } }
            SettingsSlider(
                "Heads-up", "${c.lookahead} min before", 1, 3, c.lookahead.toFloat(), 15f..120f, steps = 6,
            ) { v -> set { it.copy(lookahead = (v / 15).roundToInt() * 15) } }
            SettingsSwitch("Include snow", null, null, MaterialShapes.Circle, 2, 3, "snow" in c.flags) { on ->
                set { it.copy(flags = if (on) it.flags + "snow" else it.flags - "snow") }
            }
        }
        AlertType.RAIN -> SettingsGroup("Rain", w) {
            SettingsSlider("Look-ahead window", "${c.lookahead} h", 0, 2, c.lookahead.toFloat(), 1f..12f, steps = 10) { v ->
                set { it.copy(lookahead = v.roundToInt()) }
            }
            SettingsSlider("Chance of rain", "${c.threshold.roundToInt()}% or more", 1, 2, c.threshold.toFloat(), 30f..90f, steps = 5) { v ->
                set { it.copy(threshold = ((v / 10).roundToInt() * 10).toDouble()) }
            }
        }
        AlertType.STORM -> SettingsGroup("Thresholds", w) {
            SettingsSlider("Gusts", fmt.wind(c.threshold), 0, 2, c.threshold.toFloat(), 30f..120f, steps = 17) { v ->
                set { it.copy(threshold = ((v / 5).roundToInt() * 5).toDouble()) }
            }
            SettingsSlider("Look ahead", "${c.lookahead} h", 1, 2, c.lookahead.toFloat(), 3f..24f, steps = 6) { v ->
                set { it.copy(lookahead = ((v / 3).roundToInt() * 3)) }
            }
        }
        AlertType.FROST -> SettingsGroup("Threshold", w) {
            SettingsSlider("Overnight low at or below", fmt.temp(c.threshold), 0, 1, c.threshold.toFloat(), -6f..4f, steps = 9) { v ->
                set { it.copy(threshold = v.roundToInt().toDouble()) }
            }
        }
        AlertType.UV -> SettingsGroup("Threshold", w) {
            SettingsSlider(
                "UV index above", "${c.threshold.roundToInt()} · ${dev.vis.weather.data.WeatherInfo.uvLevel(c.threshold)}", 0, 1,
                c.threshold.toFloat(), 1f..11f, steps = 9,
                caption = "Adjust the slider to set the minimum UV level that triggers an alert.",
            ) { v -> set { it.copy(threshold = v.roundToInt().toDouble()) } }
        }
        AlertType.HEAT -> SettingsGroup("Threshold", w) {
            SettingsSlider(
                "Warn me from", "${fmt.temp(c.threshold)} · ${dev.vis.weather.alerts.Briefings.coolingLevel(c.threshold).first}", 0, 2,
                c.threshold.toFloat(), 24f..32f, steps = 7,
                caption = "Around 31° wet bulb the body can no longer cool itself, even at rest in the shade.",
            ) { v -> set { it.copy(threshold = v.roundToInt().toDouble()) } }
            SettingsSlider("Look ahead", "${c.lookahead} h", 1, 2, c.lookahead.toFloat(), 3f..24f, steps = 6) { v ->
                set { it.copy(lookahead = ((v / 3).roundToInt() * 3)) }
            }
        }
        AlertType.AIR -> SettingsGroup("Threshold", w) {
            SettingsChoice(
                "Alert from", null, MaterialShapes.Circle, 0, 1, listOf(2, 3, 4, 5), c.threshold.roundToInt().coerceIn(2, 5),
                { dev.vis.weather.alerts.Briefings.eaqiNames[it] },
            ) { v -> set { it.copy(threshold = v.toDouble()) } }
        }
        AlertType.POLLEN -> SettingsGroup("My allergies", w) {
            SettingsItemSurface(0, 2) {
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("trees" to "Trees (alder, birch, olive)", "grass" to "Grass", "weeds" to "Weeds (mugwort, ragweed)").forEach { (k, label) ->
                        val on = k in c.flags
                        FilterChip(
                            selected = on,
                            onClick = { set { it.copy(flags = if (on) it.flags - k else it.flags + k) } },
                            label = { Text(label) },
                            leadingIcon = if (on) { { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                        )
                    }
                }
            }
            SettingsChoice("Sensitivity", null, MaterialShapes.Circle, 1, 2, listOf(0, 1, 2), c.level, { listOf("Low", "Moderate", "High")[it] }) { v ->
                set { it.copy(level = v) }
            }
        }
        AlertType.TABLET -> SettingsGroup("Options", w) {
            SettingsSwitch(
                "Only on high-pollen days", "Uses your Pollen alert allergies and sensitivity", null, MaterialShapes.Circle, 0, 1,
                "pollen-days" in c.flags,
            ) { on -> set { it.copy(flags = if (on) it.flags + "pollen-days" else it.flags - "pollen-days") } }
        }
        AlertType.PRESSURE -> SettingsGroup("Sensitivity", w) {
            SettingsChoice(
                "Drop threshold", null, MaterialShapes.Circle, 0, 1, listOf(0, 1, 2), c.level,
                { "${listOf("Low", "Medium", "High")[it]} (${AlertEngine.pressureThreshold(it).let { v -> if (v % 1.0 == 0.0) v.roundToInt().toString() else v.toString() }} hPa/3h)" },
                subtitle = "More sensitive alerts for smaller drops",
            ) { v -> set { it.copy(level = v) } }
        }
        AlertType.WARDROBE -> SettingsGroup("You", w) {
            val commute = c.flags.firstOrNull { it in setOf("walk", "transit", "balanced") } ?: "balanced"
            SettingsChoice(
                "Daily commute", null, MaterialShapes.Circle, 0, 2, listOf("walk", "balanced", "transit"), commute,
                { when (it) { "walk" -> "Walk & bike"; "transit" -> "Transit & car"; else -> "Mixed" } },
                subtitle = when (commute) {
                    "walk" -> "Active commute with high exposure to wind, spray and cold"
                    "transit" -> "Subway, bus or car, mostly sheltered from the weather"
                    else -> "Walking to transit, mixed travel, or general advice"
                },
            ) { v -> set { it.copy(flags = it.flags - setOf("walk", "transit", "balanced") + v) } }
            SettingsChoice(
                "Temperature sensitivity", null, MaterialShapes.Circle, 1, 2, listOf(0, 1, 2), c.level,
                { listOf("Feel cold", "Normal", "Feel warm")[it] },
                subtitle = "Adjusts advice to how easily you feel the cold or heat",
            ) { v -> set { it.copy(level = v) } }
        }
        else -> {}
    }
}

@Composable
private fun LevelLegend() {
    val rows = listOf(
        Severity.MODERATE to "Severe weather possible. Be aware and prepared.",
        Severity.SEVERE to "Severe weather likely. Take action to stay safe.",
        Severity.EXTREME to "Extreme weather. Danger to life — take immediate action.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (sev, text) ->
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = sev.color().copy(alpha = 0.18f)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(14.dp), shape = androidx.compose.foundation.shape.CircleShape, color = sev.color()) {}
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(sev.colorName, style = MaterialTheme.typography.titleSmall)
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationPicker(
    title: String,
    value: String,
    places: List<Place>,
    currentPlace: Place?,
    w: Modifier,
    allowNone: Boolean = false,
    auto: Boolean = true,
    onPick: (String) -> Unit,
) {
    val options = buildList {
        if (allowNone) add(Triple("none", "Not set", "Use home for the whole day"))
        if (auto) add(Triple("auto", "Follow the app", "Whichever place you're viewing"))
        add(Triple("current", "Current location", currentPlace?.name ?: "Your device location"))
        places.forEach { add(Triple(it.key, it.name, it.region)) }
    }
    SettingsGroup(title, w) {
        options.forEachIndexed { i, (key, name, sub) ->
            val selected = key == value
            SettingsItemSurface(i, options.size, onClick = { onPick(key) }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(
                        when (key) { "current", "auto" -> Icons.Rounded.MyLocation; "none" -> Icons.Rounded.Work; else -> Icons.Rounded.Place },
                        if (selected) MaterialShapes.Cookie9Sided else MaterialShapes.Circle, size = 36.dp,
                        container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

