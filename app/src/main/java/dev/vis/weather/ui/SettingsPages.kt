@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.DetailTile
import dev.vis.weather.ui.components.ConnectedChoice
import dev.vis.weather.ui.components.ReorderableColumn
import dev.vis.weather.ui.components.ShapeBadge
import dev.vis.weather.ui.components.segmentedShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vis.weather.L
import dev.vis.weather.R
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
    SubScreen(stringResource(R.string.h_customize_home), onBack) { w ->
        item {
            SettingsGroup(stringResource(R.string.s_layout_group_screen), w) {
                Text(
                    stringResource(R.string.h_sections_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                )
                var expanded by rememberSaveable { mutableStateOf<String?>(null) }
                ReorderableColumn(
                    items = settings.cardOrder,
                    onReorder = { order -> update { it.copy(cardOrder = order) } },
                    moveUpLabel = stringResource(R.string.s_move_up),
                    moveDownLabel = stringResource(R.string.s_move_down),
                    haptics = settings.haptics,
                ) { i, count, card, handle, dragging ->
                    val visible = card !in settings.hiddenCards
                    val hasOptions = card == HomeCard.HOURLY || card == HomeCard.DAILY || card == HomeCard.DETAILS
                    val open = hasOptions && expanded == card.name && visible
                    SectionRow(
                        card, i, count, visible, open, dragging, handle,
                        summary = sectionSummary(card, settings),
                        onToggleOpen = if (hasOptions && visible) ({ expanded = if (open) null else card.name }) else null,
                        onVisible = { on -> update { s -> s.copy(hiddenCards = if (on) s.hiddenCards - card else s.hiddenCards + card) } },
                    ) {
                        when (card) {
                            HomeCard.HOURLY -> {
                                val graph = stringResource(R.string.s_layout_hourly_graph)
                                val compact = stringResource(R.string.s_layout_hourly_compact)
                                val cards = stringResource(R.string.s_layout_hourly_cards)
                                ConnectedChoice(
                                    HourlyStyle.entries, settings.hourlyStyle,
                                    { when (it) { HourlyStyle.STANDARD -> graph; HourlyStyle.COMPACT -> compact; HourlyStyle.CARDS -> cards } },
                                    { v -> update { it.copy(hourlyStyle = v) } },
                                    Modifier.fillMaxWidth(),
                                )
                            }
                            HomeCard.DAILY -> ConnectedChoice(
                                listOf(3, 5, 7, 16), settings.dailyDays, { it.toString() },
                                { v -> update { it.copy(dailyDays = v) } },
                                Modifier.fillMaxWidth(),
                            )
                            HomeCard.DETAILS -> TileOptions(settings, update)
                            else -> {}
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    TextButton(onClick = {
                        update {
                            it.copy(
                                cardOrder = HomeCard.entries, hiddenCards = emptySet(),
                                tileOrder = DetailTile.entries, hiddenTiles = emptySet(),
                            )
                        }
                    }) { Text(stringResource(R.string.s_layout_reset_order)) }
                }
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
            SettingsGroup(stringResource(R.string.s_wetbulb_group), w) {
                SettingsSwitch(
                    stringResource(R.string.s_wetbulb_show), stringResource(R.string.s_wetbulb_show_sub),
                    Icons.Rounded.WaterDrop, MaterialShapes.Cookie9Sided, 0, 2, settings.showWetBulb,
                ) { v -> update { it.copy(showWetBulb = v) } }
                SettingsSwitch(
                    stringResource(R.string.s_wetbulb_warn), stringResource(R.string.s_wetbulb_warn_sub),
                    Icons.Rounded.Thermostat, MaterialShapes.Sunny, 1, 2, heatOn,
                ) { v -> if (v) askPermission() else setHeat(false) }
            }
        }
    }
}

private fun HomeCard.icon(): ImageVector = when (this) {
    HomeCard.WARNINGS -> Icons.Rounded.Warning
    HomeCard.HIGHLIGHTS -> Icons.Rounded.AutoAwesome
    HomeCard.NOWCAST -> Icons.Rounded.Umbrella
    HomeCard.RADAR -> Icons.Rounded.Radar
    HomeCard.HOURLY -> Icons.Rounded.Schedule
    HomeCard.DAILY -> Icons.Rounded.CalendarMonth
    HomeCard.WARDROBE -> Icons.Rounded.Checkroom
    HomeCard.FRIENDS -> Icons.Rounded.Group
    HomeCard.DETAILS -> Icons.Rounded.GridView
}

private fun HomeCard.shape(): RoundedPolygon = when (this) {
    HomeCard.WARNINGS -> MaterialShapes.Triangle
    HomeCard.HIGHLIGHTS -> MaterialShapes.Sunny
    HomeCard.NOWCAST -> MaterialShapes.Pill
    HomeCard.RADAR -> MaterialShapes.Cookie9Sided
    HomeCard.HOURLY -> MaterialShapes.Cookie4Sided
    HomeCard.DAILY -> MaterialShapes.Clover4Leaf
    HomeCard.WARDROBE -> MaterialShapes.Arch
    HomeCard.FRIENDS -> MaterialShapes.Flower
    HomeCard.DETAILS -> MaterialShapes.Square
}

@Composable
private fun sectionSummary(card: HomeCard, s: Settings): String? = when (card) {
    HomeCard.WARNINGS -> stringResource(R.string.h_warnings_always)
    HomeCard.HOURLY -> stringResource(
        when (s.hourlyStyle) {
            HourlyStyle.STANDARD -> R.string.s_layout_hourly_graph
            HourlyStyle.COMPACT -> R.string.s_layout_hourly_compact
            HourlyStyle.CARDS -> R.string.s_layout_hourly_cards
        },
    )
    HomeCard.DAILY -> L.p(R.plurals.s_layout_days, s.dailyDays)
    HomeCard.DETAILS -> stringResource(R.string.h_tiles_shown, DetailTile.entries.size - s.hiddenTiles.size, DetailTile.entries.size)
    else -> null
}

@Composable
private fun SectionRow(
    card: HomeCard,
    index: Int,
    count: Int,
    visible: Boolean,
    open: Boolean,
    dragging: Boolean,
    handle: Modifier,
    summary: String?,
    onToggleOpen: (() -> Unit)?,
    onVisible: (Boolean) -> Unit,
    options: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (dragging) RoundedCornerShape(20.dp) else segmentedShape(index, count),
        color = if (dragging) cs.surfaceContainerHighest else cs.surfaceContainer,
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (onToggleOpen != null) Modifier.clickable(onClick = onToggleOpen) else Modifier)
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(handle.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DragIndicator, stringResource(R.string.h_drag_handle), tint = cs.onSurfaceVariant)
                }
                Box(Modifier.alpha(if (visible) 1f else 0.45f)) { ShapeBadge(card.icon(), card.shape(), size = 40.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        card.title, style = MaterialTheme.typography.titleMedium,
                        color = if (visible) cs.onSurface else cs.onSurfaceVariant,
                    )
                    if (summary != null && visible) {
                        Text(summary, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                if (onToggleOpen != null) {
                    Icon(
                        if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        stringResource(R.string.h_options),
                        Modifier.padding(end = 8.dp), tint = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = visible, enabled = card.removable, onCheckedChange = onVisible)
            }
            if (open) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) { options() }
            }
        }
    }
}

/** Which detail tiles show, and in what order. */
@Composable
private fun TileOptions(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(
            stringResource(R.string.h_tiles_hint),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ReorderableColumn(
            items = settings.tileOrder,
            onReorder = { order -> update { it.copy(tileOrder = order) } },
            moveUpLabel = stringResource(R.string.s_move_up),
            moveDownLabel = stringResource(R.string.s_move_down),
            haptics = settings.haptics,
        ) { _, _, tile, handle, dragging ->
            val on = tile !in settings.hiddenTiles
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (dragging) cs.surfaceContainerHighest else cs.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .toggleable(on, role = Role.Checkbox) { v ->
                            update { s -> s.copy(hiddenTiles = if (v) s.hiddenTiles - tile else s.hiddenTiles + tile) }
                        }
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(handle.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.DragIndicator, stringResource(R.string.h_drag_handle), Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                    }
                    Text(
                        tile.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        color = if (on) cs.onSurface else cs.onSurfaceVariant,
                    )
                    if (tile.wide) {
                        Text(
                            stringResource(R.string.h_tile_wide), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Checkbox(checked = on, onCheckedChange = null)
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
    SubScreen(stringResource(R.string.s_units), onBack) { w ->
        item {
            SettingsGroup(null, w) {
                val n = 6
                SettingsChoice(stringResource(R.string.s_units_temperature), Icons.Rounded.Thermostat, MaterialShapes.Arch, 0, n, TempUnit.entries, settings.tempUnit, {
                    if (it == TempUnit.C) "°C" else "°F"
                }) { v -> update { it.copy(tempUnit = v) } }
                SettingsChoice(stringResource(R.string.s_units_wind), Icons.Rounded.Air, MaterialShapes.Cookie6Sided, 1, n, WindUnit.entries, settings.windUnit, {
                    when (it) { WindUnit.KMH -> "km/h"; WindUnit.MS -> "m/s"; WindUnit.MPH -> "mph"; WindUnit.KN -> "kn" }
                }) { v -> update { it.copy(windUnit = v) } }
                SettingsChoice(stringResource(R.string.s_units_precip), Icons.Rounded.WaterDrop, MaterialShapes.Cookie7Sided, 2, n, PrecipUnit.entries, settings.precipUnit, {
                    if (it == PrecipUnit.MM) "mm" else "in"
                }) { v -> update { it.copy(precipUnit = v) } }
                SettingsChoice(stringResource(R.string.s_units_pressure), Icons.Rounded.Compress, MaterialShapes.Gem, 3, n, PressureUnit.entries, settings.pressureUnit, {
                    when (it) { PressureUnit.HPA -> "hPa"; PressureUnit.INHG -> "inHg"; PressureUnit.MMHG -> "mmHg" }
                }) { v -> update { it.copy(pressureUnit = v) } }
                SettingsChoice(stringResource(R.string.s_units_distance), Icons.Rounded.Straighten, MaterialShapes.Pill, 4, n, DistanceUnit.entries, settings.distanceUnit, {
                    if (it == DistanceUnit.KM) "km" else "mi"
                }) { v -> update { it.copy(distanceUnit = v) } }
                SettingsSwitch(stringResource(R.string.s_units_24h), null, Icons.Rounded.Schedule, MaterialShapes.Cookie12Sided, 5, n, settings.use24h) { v ->
                    update { it.copy(use24h = v) }
                }
            }
        }
        item {
            Column(w.padding(horizontal = 8.dp)) {
                Text(
                    stringResource(R.string.s_units_presets), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                Row {
                    TextButton(onClick = {
                        update { it.copy(tempUnit = TempUnit.C, windUnit = WindUnit.KMH, precipUnit = PrecipUnit.MM, pressureUnit = PressureUnit.HPA, distanceUnit = DistanceUnit.KM, use24h = true) }
                    }) { Text(stringResource(R.string.s_units_preset_metric)) }
                    TextButton(onClick = {
                        update { it.copy(tempUnit = TempUnit.F, windUnit = WindUnit.MPH, precipUnit = PrecipUnit.IN, pressureUnit = PressureUnit.INHG, distanceUnit = DistanceUnit.MI, use24h = false) }
                    }) { Text(stringResource(R.string.s_units_preset_imperial)) }
                    TextButton(onClick = {
                        update { it.copy(tempUnit = TempUnit.C, windUnit = WindUnit.MPH, precipUnit = PrecipUnit.MM, pressureUnit = PressureUnit.HPA, distanceUnit = DistanceUnit.MI, use24h = true) }
                    }) { Text(stringResource(R.string.s_units_preset_uk)) }
                }
            }
        }
    }
}
