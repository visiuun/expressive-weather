@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.vis.weather.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.vis.weather.R
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.Settings
import dev.vis.weather.data.Sky
import dev.vis.weather.data.Stats
import dev.vis.weather.ui.components.AppCard
import dev.vis.weather.ui.components.SubScreen
import dev.vis.weather.ui.components.WeatherIcon
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Stats.read(context) }
    val fmt = remember(settings) { Fmt(settings, ZoneId.systemDefault()) }
    SubScreen(stringResource(R.string.s_activity), onBack) { w ->
        item {
            Row(w, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Big("${s.streak}", stringResource(R.string.s_stats_streak), pluralStringResource(R.plurals.s_stats_days_unit, s.streak), MaterialShapes.Sunny, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f))
                Big("${s.bestStreak}", stringResource(R.string.s_stats_best_streak), pluralStringResource(R.plurals.s_stats_days_unit, s.bestStreak), MaterialShapes.Cookie9Sided, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.weight(1f))
            }
        }
        item {
            Row(w, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Big(fmt.temp(s.hottest), stringResource(R.string.s_stats_hottest), s.hottestWhere, MaterialShapes.SoftBurst, Color(0xFFFFB59A), Color(0xFF3A0B00), Modifier.weight(1f))
                Big(fmt.temp(s.coldest), stringResource(R.string.s_stats_coldest), s.coldestWhere, MaterialShapes.Flower, Color(0xFFB7DCFF), Color(0xFF00213D), Modifier.weight(1f))
            }
        }
        item {
            Row(w, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Big("${s.daysCheckedIn}", stringResource(R.string.s_stats_days_checked_in), s.installedSince?.let { stringResource(R.string.s_stats_since, it.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))) } ?: "", MaterialShapes.Cookie12Sided, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f))
                Big("${s.places.size}", stringResource(R.string.s_stats_places), "", MaterialShapes.Clover8Leaf, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }
        item {
            AppCard(w) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.s_stats_conditions, s.conditions.size, Sky.entries.size), style = MaterialTheme.typography.titleMediumEmphasized)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Sky.entries.forEach { sky ->
                            val seen = sky.ordinal in s.conditions
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(width = 64.dp, height = 84.dp)) {
                                Box(
                                    Modifier.size(56.dp).clip(MaterialShapes.Cookie6Sided.toShape())
                                        .background(if (seen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (seen) WeatherIcon(codeFor(sky), true, Modifier.size(34.dp)) else Text("?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    stringResource(skyLabel(sky)), style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center, maxLines = 1,
                                    color = if (seen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.s_stats_privacy),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = w.padding(horizontal = 8.dp),
            )
        }
    }
}

private fun skyLabel(sky: Sky): Int = when (sky) {
    Sky.CLEAR -> R.string.s_stats_sky_clear; Sky.PARTLY -> R.string.s_stats_sky_partly; Sky.CLOUDY -> R.string.s_stats_sky_cloudy
    Sky.FOG -> R.string.s_stats_sky_fog; Sky.DRIZZLE -> R.string.s_stats_sky_drizzle; Sky.RAIN -> R.string.s_stats_sky_rain
    Sky.FREEZING -> R.string.s_stats_sky_freezing; Sky.SNOW -> R.string.s_stats_sky_snow; Sky.THUNDER -> R.string.s_stats_sky_thunder
}

private fun codeFor(sky: Sky) = when (sky) {
    Sky.CLEAR -> 0; Sky.PARTLY -> 2; Sky.CLOUDY -> 3; Sky.FOG -> 45; Sky.DRIZZLE -> 53
    Sky.RAIN -> 63; Sky.FREEZING -> 66; Sky.SNOW -> 73; Sky.THUNDER -> 95
}

@Composable
private fun Big(value: String, label: String, sub: String, shape: RoundedPolygon, bg: Color, fg: Color, modifier: Modifier) {
    AppCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(shape.toShape()).background(bg), contentAlignment = Alignment.Center) {
                Text(value, style = MaterialTheme.typography.headlineMediumEmphasized, color = fg, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
