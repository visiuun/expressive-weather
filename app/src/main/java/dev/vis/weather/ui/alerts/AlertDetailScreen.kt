@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.vis.weather.ui.alerts

import dev.vis.weather.inSentence
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vis.weather.L
import dev.vis.weather.R
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
                        stringResource(if (c.enabled) R.string.al_on else R.string.al_off),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        modifier = Modifier.weight(1f),
                        color = if (c.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(checked = c.enabled, onCheckedChange = { if (it) askPermission() else set { x -> x.copy(enabled = false) } })
                }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.al_how_it_works), w) {
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
                SettingsGroup(stringResource(if (type == AlertType.RAIN && c.allDay) R.string.al_active_hours else R.string.al_notification_time), w) {
                    if (type == AlertType.RAIN) {
                        SettingsChoice(
                            stringResource(R.string.al_check), null, MaterialShapes.Circle, 0, 2, listOf(false, true), c.allDay,
                            { L.s(if (it) R.string.al_all_day else R.string.al_once_daily) },
                        ) { v -> set { it.copy(allDay = v) } }
                    }
                    SettingsItemSurface(if (type == AlertType.RAIN) 1 else 0, if (type == AlertType.RAIN) 2 else 1) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (type == AlertType.RAIN && c.allDay) {
                                TimeChip(stringResource(R.string.al_start), timeLabel(c.time, settings.use24h), Modifier.weight(1f)) { picking = 1 }
                                TimeChip(stringResource(R.string.al_end), timeLabel(c.endTime, settings.use24h), Modifier.weight(1f)) { picking = 2 }
                            } else {
                                TimeChip(
                                    when (type) {
                                        AlertType.TABLET -> stringResource(R.string.al_reminder_time)
                                        else -> stringResource(R.string.al_alert_time)
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
                    stringResource(if (type == AlertType.WARDROBE) R.string.al_home_location else R.string.al_location), c.location, places, currentPlace, w,
                ) { v -> set { it.copy(location = v) } }
            }
        }
        if (type == AlertType.WARDROBE) {
            item {
                LocationPicker(stringResource(R.string.al_work_location), c.extra.ifBlank { "none" }, places, currentPlace, w, allowNone = true, auto = false) { v ->
                    set { it.copy(extra = if (v == "none") "" else v) }
                }
            }
        }

        item {
            val sentText = stringResource(R.string.al_test_sent)
            Column(w, horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        testing = true
                        scope.launch {
                            testMessage = AlertEngine.test(context, type) ?: sentText
                            testing = false
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Rounded.NotificationsActive, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(if (testing) R.string.al_checking_forecast else R.string.al_send_test), style = MaterialTheme.typography.titleMedium)
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
        AlertType.WARNINGS -> L.s(if ("saved" in c.flags) R.string.al_how_warnings_saved else R.string.al_how_warnings)
        AlertType.NOWCAST -> {
            val snow = "snow" in c.flags
            val what = when {
                c.threshold >= 2 -> if (snow) R.string.al_how_nowcast_heavy_rain_snow else R.string.al_how_nowcast_heavy_rain
                c.threshold >= 0.5 -> if (snow) R.string.al_how_nowcast_moderate_rain_snow else R.string.al_how_nowcast_moderate_rain
                else -> if (snow) R.string.al_how_nowcast_rain_snow else R.string.al_how_nowcast_rain
            }
            L.p(R.plurals.al_how_nowcast, c.lookahead, c.lookahead, L.s(what))
        }
        AlertType.RAIN -> if (c.allDay) L.p(R.plurals.al_how_rain_all_day, c.lookahead, c.lookahead, timeLabel(c.time, s.use24h), timeLabel(c.endTime, s.use24h), c.threshold.roundToInt())
        else L.p(R.plurals.al_how_rain_once, c.lookahead, c.lookahead, t, c.threshold.roundToInt())
        AlertType.STORM -> L.p(R.plurals.al_how_storm, c.lookahead, c.lookahead, fmt.wind(c.threshold))
        AlertType.TODAY -> L.s(R.string.al_how_today, t)
        AlertType.TOMORROW -> L.s(R.string.al_how_tomorrow, t)
        AlertType.WARDROBE -> L.s(R.string.al_how_wardrobe, t) +
            if (c.extra.isNotBlank()) " " + L.s(R.string.al_how_wardrobe_work) else ""
        AlertType.FROST -> L.s(R.string.al_how_frost, t, fmt.temp(c.threshold))
        AlertType.UV -> L.s(R.string.al_how_uv, t, c.threshold.roundToInt())
        AlertType.HEAT -> L.p(R.plurals.al_how_heat, c.lookahead, c.lookahead, fmt.temp(c.threshold), dev.vis.weather.alerts.Briefings.coolingLevel(c.threshold).first.inSentence())
        AlertType.AIR -> L.s(R.string.al_how_air, t, dev.vis.weather.alerts.Briefings.eaqiNames[c.threshold.roundToInt().coerceIn(1, 6)].inSentence())
        AlertType.POLLEN -> {
            val names = mapOf("trees" to R.string.al_pollen_trees, "grass" to R.string.al_pollen_grasses, "weeds" to R.string.al_pollen_weeds)
            val list = c.flags.filter { it in names }.joinToString { L.s(names.getValue(it)) }.ifBlank { L.s(R.string.al_how_pollen_fallback) }
            L.s(R.string.al_how_pollen, t, list) + " " +
                L.s(listOf(R.string.al_how_pollen_low, R.string.al_how_pollen_moderate, R.string.al_how_pollen_high)[c.level])
        }
        AlertType.TABLET -> L.s(if ("pollen-days" in c.flags) R.string.al_how_tablet_pollen else R.string.al_how_tablet, t)
        AlertType.PRESSURE -> L.s(R.string.al_how_pressure, AlertEngine.pressureThreshold(c.level).roundToInt())
        AlertType.FRIENDS -> L.s(R.string.al_how_friends, Severity.entries[c.threshold.roundToInt()].colorName.lowercase())
        AlertType.ONGOING -> L.s(R.string.al_how_ongoing)
    }
}

private fun disclaimer(type: AlertType): String? = when (type) {
    AlertType.WARNINGS, AlertType.FRIENDS -> L.s(R.string.al_disclaimer_warnings)
    AlertType.POLLEN, AlertType.TABLET -> L.s(R.string.al_disclaimer_pollen)
    AlertType.PRESSURE, AlertType.HEAT -> L.s(R.string.al_disclaimer_health)
    AlertType.NOWCAST, AlertType.RAIN -> L.s(R.string.al_disclaimer_precip)
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
        AlertType.WARNINGS, AlertType.FRIENDS -> SettingsGroup(stringResource(R.string.al_opt_alert_levels), w) {
            val n = if (type == AlertType.WARNINGS) 2 else 2
            SettingsChoice(
                stringResource(R.string.al_opt_notify_from), null, MaterialShapes.Circle, 0, n, Severity.entries, Severity.entries[c.threshold.roundToInt().coerceIn(0, 3)],
                { it.colorName },
            ) { v -> set { it.copy(threshold = v.ordinal.toDouble()) } }
            if (type == AlertType.WARNINGS) {
                SettingsSwitch(
                    stringResource(R.string.al_opt_saved_locations), stringResource(R.string.al_opt_saved_locations_sub),
                    Icons.Rounded.Place, MaterialShapes.Cookie6Sided, 1, 2, "saved" in c.flags,
                ) { on -> set { it.copy(flags = if (on) it.flags + "saved" else it.flags - "saved") } }
            } else {
                SettingsItemSurface(1, 2, onClick = onManageFriends) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShapeBadge(Icons.Rounded.Groups, MaterialShapes.Flower, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.al_opt_manage_friends), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LevelLegend()
        }
        AlertType.NOWCAST -> SettingsGroup(stringResource(R.string.al_opt_sensitivity), w) {
            val levels = listOf(0.1, 0.5, 2.0)
            SettingsChoice(
                stringResource(R.string.al_opt_intensity), null, MaterialShapes.Circle, 0, 3, levels, levels.minBy { kotlin.math.abs(it - c.threshold) },
                { L.s(when (it) { 0.1 -> R.string.al_opt_intensity_any; 0.5 -> R.string.al_opt_intensity_moderate; else -> R.string.al_opt_intensity_heavy }) },
            ) { v -> set { it.copy(threshold = v) } }
            SettingsSlider(
                stringResource(R.string.al_opt_heads_up), pluralStringResource(R.plurals.al_opt_min_before, c.lookahead, c.lookahead), 1, 3, c.lookahead.toFloat(), 15f..120f, steps = 6,
            ) { v -> set { it.copy(lookahead = (v / 15).roundToInt() * 15) } }
            SettingsSwitch(stringResource(R.string.al_opt_include_snow), null, null, MaterialShapes.Circle, 2, 3, "snow" in c.flags) { on ->
                set { it.copy(flags = if (on) it.flags + "snow" else it.flags - "snow") }
            }
        }
        AlertType.RAIN -> SettingsGroup(stringResource(R.string.al_opt_rain), w) {
            SettingsSlider(stringResource(R.string.al_opt_lookahead_window), stringResource(R.string.al_opt_hours_short, c.lookahead), 0, 2, c.lookahead.toFloat(), 1f..12f, steps = 10) { v ->
                set { it.copy(lookahead = v.roundToInt()) }
            }
            SettingsSlider(stringResource(R.string.al_opt_chance_of_rain), stringResource(R.string.al_opt_percent_or_more, c.threshold.roundToInt()), 1, 2, c.threshold.toFloat(), 30f..90f, steps = 5) { v ->
                set { it.copy(threshold = ((v / 10).roundToInt() * 10).toDouble()) }
            }
        }
        AlertType.STORM -> SettingsGroup(stringResource(R.string.al_opt_thresholds), w) {
            SettingsSlider(stringResource(R.string.al_opt_gusts), fmt.wind(c.threshold), 0, 2, c.threshold.toFloat(), 30f..120f, steps = 17) { v ->
                set { it.copy(threshold = ((v / 5).roundToInt() * 5).toDouble()) }
            }
            SettingsSlider(stringResource(R.string.al_opt_look_ahead), stringResource(R.string.al_opt_hours_short, c.lookahead), 1, 2, c.lookahead.toFloat(), 3f..24f, steps = 6) { v ->
                set { it.copy(lookahead = ((v / 3).roundToInt() * 3)) }
            }
        }
        AlertType.FROST -> SettingsGroup(stringResource(R.string.al_opt_threshold), w) {
            SettingsSlider(stringResource(R.string.al_opt_frost_low), fmt.temp(c.threshold), 0, 1, c.threshold.toFloat(), -6f..4f, steps = 9) { v ->
                set { it.copy(threshold = v.roundToInt().toDouble()) }
            }
        }
        AlertType.UV -> SettingsGroup(stringResource(R.string.al_opt_threshold), w) {
            SettingsSlider(
                stringResource(R.string.al_opt_uv_above), "${c.threshold.roundToInt()} · ${dev.vis.weather.data.WeatherInfo.uvLevel(c.threshold)}", 0, 1,
                c.threshold.toFloat(), 1f..11f, steps = 9,
                caption = stringResource(R.string.al_opt_uv_caption),
            ) { v -> set { it.copy(threshold = v.roundToInt().toDouble()) } }
        }
        AlertType.HEAT -> SettingsGroup(stringResource(R.string.al_opt_threshold), w) {
            SettingsSlider(
                stringResource(R.string.al_opt_warn_from), "${fmt.temp(c.threshold)} · ${dev.vis.weather.alerts.Briefings.coolingLevel(c.threshold).first}", 0, 2,
                c.threshold.toFloat(), 24f..32f, steps = 7,
                caption = stringResource(R.string.al_opt_heat_caption),
            ) { v -> set { it.copy(threshold = v.roundToInt().toDouble()) } }
            SettingsSlider(stringResource(R.string.al_opt_look_ahead), stringResource(R.string.al_opt_hours_short, c.lookahead), 1, 2, c.lookahead.toFloat(), 3f..24f, steps = 6) { v ->
                set { it.copy(lookahead = ((v / 3).roundToInt() * 3)) }
            }
        }
        AlertType.AIR -> SettingsGroup(stringResource(R.string.al_opt_threshold), w) {
            SettingsChoice(
                stringResource(R.string.al_opt_alert_from), null, MaterialShapes.Circle, 0, 1, listOf(2, 3, 4, 5), c.threshold.roundToInt().coerceIn(2, 5),
                { dev.vis.weather.alerts.Briefings.eaqiNames[it] },
            ) { v -> set { it.copy(threshold = v.toDouble()) } }
        }
        AlertType.POLLEN -> SettingsGroup(stringResource(R.string.al_opt_my_allergies), w) {
            SettingsItemSurface(0, 2) {
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "trees" to stringResource(R.string.al_opt_allergy_trees),
                        "grass" to stringResource(R.string.al_opt_allergy_grass),
                        "weeds" to stringResource(R.string.al_opt_allergy_weeds),
                    ).forEach { (k, label) ->
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
            SettingsChoice(
                stringResource(R.string.al_opt_sensitivity), null, MaterialShapes.Circle, 1, 2, listOf(0, 1, 2), c.level,
                { L.s(listOf(R.string.al_opt_sens_low, R.string.al_opt_sens_moderate, R.string.al_opt_sens_high)[it]) },
            ) { v ->
                set { it.copy(level = v) }
            }
        }
        AlertType.TABLET -> SettingsGroup(stringResource(R.string.al_opt_options), w) {
            SettingsSwitch(
                stringResource(R.string.al_opt_pollen_days), stringResource(R.string.al_opt_pollen_days_sub), null, MaterialShapes.Circle, 0, 1,
                "pollen-days" in c.flags,
            ) { on -> set { it.copy(flags = if (on) it.flags + "pollen-days" else it.flags - "pollen-days") } }
        }
        AlertType.PRESSURE -> SettingsGroup(stringResource(R.string.al_opt_sensitivity), w) {
            SettingsChoice(
                stringResource(R.string.al_opt_drop_threshold), null, MaterialShapes.Circle, 0, 1, listOf(0, 1, 2), c.level,
                {
                    L.s(
                        listOf(R.string.al_opt_drop_low, R.string.al_opt_drop_medium, R.string.al_opt_drop_high)[it],
                        AlertEngine.pressureThreshold(it).let { v -> if (v % 1.0 == 0.0) v.roundToInt().toString() else v.toString() },
                    )
                },
                subtitle = stringResource(R.string.al_opt_drop_sub),
            ) { v -> set { it.copy(level = v) } }
        }
        AlertType.WARDROBE -> SettingsGroup(stringResource(R.string.al_opt_you), w) {
            val commute = c.flags.firstOrNull { it in setOf("walk", "transit", "balanced") } ?: "balanced"
            SettingsChoice(
                stringResource(R.string.al_opt_commute), null, MaterialShapes.Circle, 0, 2, listOf("walk", "balanced", "transit"), commute,
                { L.s(when (it) { "walk" -> R.string.al_opt_commute_walk; "transit" -> R.string.al_opt_commute_transit; else -> R.string.al_opt_commute_mixed }) },
                subtitle = stringResource(
                    when (commute) {
                        "walk" -> R.string.al_opt_commute_walk_sub
                        "transit" -> R.string.al_opt_commute_transit_sub
                        else -> R.string.al_opt_commute_mixed_sub
                    }
                ),
            ) { v -> set { it.copy(flags = it.flags - setOf("walk", "transit", "balanced") + v) } }
            SettingsChoice(
                stringResource(R.string.al_opt_temp_sensitivity), null, MaterialShapes.Circle, 1, 2, listOf(0, 1, 2), c.level,
                { L.s(listOf(R.string.al_opt_feel_cold, R.string.al_opt_feel_normal, R.string.al_opt_feel_warm)[it]) },
                subtitle = stringResource(R.string.al_opt_temp_sensitivity_sub),
            ) { v -> set { it.copy(level = v) } }
        }
        else -> {}
    }
}

@Composable
private fun LevelLegend() {
    val rows = listOf(
        Severity.MODERATE to stringResource(R.string.al_legend_yellow),
        Severity.SEVERE to stringResource(R.string.al_legend_amber),
        Severity.EXTREME to stringResource(R.string.al_legend_red),
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
    val notSet = stringResource(R.string.al_loc_not_set)
    val notSetSub = stringResource(R.string.al_loc_not_set_sub)
    val follow = stringResource(R.string.al_loc_auto)
    val followSub = stringResource(R.string.al_loc_auto_sub)
    val current = stringResource(R.string.al_loc_current)
    val currentSub = currentPlace?.name ?: stringResource(R.string.al_loc_current_sub)
    val options = buildList {
        if (allowNone) add(Triple("none", notSet, notSetSub))
        if (auto) add(Triple("auto", follow, followSub))
        add(Triple("current", current, currentSub))
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

