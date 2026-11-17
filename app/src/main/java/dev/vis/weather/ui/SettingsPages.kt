@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vis.weather.data.DistanceUnit
import dev.vis.weather.data.HomeCard
import dev.vis.weather.data.HourlyStyle
import dev.vis.weather.data.PrecipUnit
import dev.vis.weather.data.PressureUnit
import dev.vis.weather.data.Settings
import dev.vis.weather.data.TempUnit
import dev.vis.weather.data.WindUnit
import dev.vis.weather.ui.components.SettingsChoice
import dev.vis.weather.ui.components.SettingsGroup
import dev.vis.weather.ui.components.SettingsItemSurface
import dev.vis.weather.ui.components.SettingsSwitch
import dev.vis.weather.ui.components.SubScreen

@Composable
fun LayoutScreen(settings: Settings, update: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    SubScreen("Layout", onBack) { w ->
        item {
            SettingsGroup("Forecast", w) {
                SettingsChoice(
                    "Hourly forecast", Icons.Rounded.Schedule, MaterialShapes.Cookie4Sided, 0, 2, HourlyStyle.entries, settings.hourlyStyle,
                    { when (it) { HourlyStyle.STANDARD -> "Graph"; HourlyStyle.COMPACT -> "Compact"; HourlyStyle.CARDS -> "Cards" } },
                ) { v -> update { it.copy(hourlyStyle = v) } }
                SettingsChoice(
                    "Daily forecast", Icons.Rounded.CalendarMonth, MaterialShapes.Clover4Leaf, 1, 2, listOf(3, 5, 7, 16), settings.dailyDays,
                    { "$it days" },
                ) { v -> update { it.copy(dailyDays = v) } }
            }
        }
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            val alerts = androidx.compose.runtime.remember { dev.vis.weather.alerts.AlertPrefs.get(context) }
            val configs by alerts.configs.collectAsState()
            val heatOn = configs.getValue(dev.vis.weather.alerts.AlertType.HEAT).enabled
            fun setHeat(on: Boolean) {
                alerts.update(dev.vis.weather.alerts.AlertType.HEAT) { it.copy(enabled = on) }
                dev.vis.weather.alerts.AlertScheduler.sync(context)
                if (on) dev.vis.weather.alerts.AlertScheduler.runWatchNow(context)
            }
            val askPermission = dev.vis.weather.ui.alerts.rememberNotificationPermission { ok -> if (ok) setHeat(true) }
            SettingsGroup("Wet bulb", w) {
                SettingsSwitch(
                    "Show wet bulb temperature", "Next to \"feels like\": how well sweat can cool you in this heat and humidity",
                    Icons.Rounded.WaterDrop, MaterialShapes.Cookie9Sided, 0, 2, settings.showWetBulb,
                ) { v -> update { it.copy(showWetBulb = v) } }
                SettingsSwitch(
                    "Warn me when it gets dangerous", "A notification as soon as the wet bulb is forecast to reach a dangerous level",
                    Icons.Rounded.Thermostat, MaterialShapes.Sunny, 1, 2, heatOn,
                ) { v -> if (v) askPermission() else setHeat(false) }
            }
        }
        item {
            SettingsGroup("Weather screen", w) {
                Text(
                    "Reorder the sections of the weather screen, or hide the ones you don't need.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
                val order = settings.cardOrder
                order.forEachIndexed { i, card ->
                    val visible = card !in settings.hiddenCards
                    SettingsItemSurface(i, order.size) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).animateContentSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ViewDay, null, tint = if (visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                card.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                                color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilledTonalIconButton(onClick = { update { s -> s.copy(cardOrder = s.cardOrder.move(i, -1)) } }, enabled = i > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move up")
                            }
                            FilledTonalIconButton(onClick = { update { s -> s.copy(cardOrder = s.cardOrder.move(i, 1)) } }, enabled = i < order.lastIndex) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Move down")
                            }
                            Spacer(Modifier.width(4.dp))
                            Switch(
                                checked = visible, enabled = card.removable,
                                onCheckedChange = { on -> update { s -> s.copy(hiddenCards = if (on) s.hiddenCards - card else s.hiddenCards + card) } },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { update { it.copy(cardOrder = HomeCard.entries, hiddenCards = emptySet()) } }) { Text("Reset order") }
                }
            }
        }
    }
}

private fun <T> List<T>.move(i: Int, d: Int): List<T> {
    val j = i + d
    if (j !in indices) return this
    return toMutableList().apply { add(j, removeAt(i)) }
}

@Composable
fun UnitsScreen(settings: Settings, update: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    SubScreen("Units", onBack) { w ->
        item {
            SettingsGroup(null, w) {
                val n = 6
                SettingsChoice("Temperature", Icons.Rounded.Thermostat, MaterialShapes.Arch, 0, n, TempUnit.entries, settings.tempUnit, {
                    if (it == TempUnit.C) "°C" else "°F"
                }) { v -> update { it.copy(tempUnit = v) } }
                SettingsChoice("Wind speed", Icons.Rounded.Air, MaterialShapes.Cookie6Sided, 1, n, WindUnit.entries, settings.windUnit, {
                    when (it) { WindUnit.KMH -> "km/h"; WindUnit.MS -> "m/s"; WindUnit.MPH -> "mph"; WindUnit.KN -> "kn" }
                }) { v -> update { it.copy(windUnit = v) } }
                SettingsChoice("Precipitation", Icons.Rounded.WaterDrop, MaterialShapes.Cookie7Sided, 2, n, PrecipUnit.entries, settings.precipUnit, {
                    if (it == PrecipUnit.MM) "mm" else "in"
                }) { v -> update { it.copy(precipUnit = v) } }
                SettingsChoice("Pressure", Icons.Rounded.Compress, MaterialShapes.Gem, 3, n, PressureUnit.entries, settings.pressureUnit, {
                    when (it) { PressureUnit.HPA -> "hPa"; PressureUnit.INHG -> "inHg"; PressureUnit.MMHG -> "mmHg" }
                }) { v -> update { it.copy(pressureUnit = v) } }
                SettingsChoice("Distance", Icons.Rounded.Straighten, MaterialShapes.Pill, 4, n, DistanceUnit.entries, settings.distanceUnit, {
                    if (it == DistanceUnit.KM) "km" else "mi"
                }) { v -> update { it.copy(distanceUnit = v) } }
                SettingsSwitch("24-hour clock", null, Icons.Rounded.Schedule, MaterialShapes.Cookie12Sided, 5, n, settings.use24h) { v ->
                    update { it.copy(use24h = v) }
                }
            }
        }
        item {
            Column(w.padding(horizontal = 8.dp)) {
                Text(
                    "Quick presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                Row {
                    TextButton(onClick = {
                        update { it.copy(tempUnit = TempUnit.C, windUnit = WindUnit.KMH, precipUnit = PrecipUnit.MM, pressureUnit = PressureUnit.HPA, distanceUnit = DistanceUnit.KM, use24h = true) }
                    }) { Text("Metric") }
                    TextButton(onClick = {
                        update { it.copy(tempUnit = TempUnit.F, windUnit = WindUnit.MPH, precipUnit = PrecipUnit.IN, pressureUnit = PressureUnit.INHG, distanceUnit = DistanceUnit.MI, use24h = false) }
                    }) { Text("Imperial") }
                    TextButton(onClick = {
                        update { it.copy(tempUnit = TempUnit.C, windUnit = WindUnit.MPH, precipUnit = PrecipUnit.MM, pressureUnit = PressureUnit.HPA, distanceUnit = DistanceUnit.MI, use24h = true) }
                    }) { Text("UK") }
                }
            }
        }
    }
}
