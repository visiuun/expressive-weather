@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.vis.weather.ui.alerts

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.toShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vis.weather.L
import dev.vis.weather.R
import dev.vis.weather.alerts.AlertPrefs
import dev.vis.weather.alerts.AlertType
import dev.vis.weather.alerts.Wardrobe
import dev.vis.weather.alerts.WardrobeAdvice
import dev.vis.weather.alerts.Warning
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.ForecastRepo
import dev.vis.weather.data.Settings
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.components.AppCard
import dev.vis.weather.ui.components.ConnectedChoice
import dev.vis.weather.ui.components.SubScreen
import java.time.LocalTime

/** Compact stack of warning banners for the top of the main screen. */
@Composable
fun WarningsBanner(warnings: List<Warning>, fmt: Fmt, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (warnings.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WarningCard(warnings.first(), fmt, Modifier.fillMaxWidth(), onClick = onOpen)
        if (warnings.size > 1) {
            Surface(onClick = onOpen, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    pluralStringResource(R.plurals.al_more_warnings, warnings.size - 1, warnings.size - 1),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Every warning for the selected place, fully expanded, plus other saved places with warnings. */
@Composable
fun WarningsScreen(
    placeName: String?,
    warnings: List<Warning>,
    others: List<Pair<String, List<Warning>>>,
    settings: Settings,
    fmt: Fmt?,
    onBack: () -> Unit,
    onOpenAlertSettings: () -> Unit,
) {
    SubScreen(stringResource(R.string.al_warnings_title), onBack, actions = {
        IconButton(onClick = onOpenAlertSettings) { Icon(Icons.Rounded.Tune, stringResource(R.string.al_warning_notifications)) }
    }) { w ->
        item {
            Text(
                if (warnings.isEmpty()) {
                    if (placeName != null) stringResource(R.string.al_no_warnings_for, placeName) else stringResource(R.string.al_no_warnings_sentence)
                } else placeName ?: "",
                style = MaterialTheme.typography.titleLarge, modifier = w.padding(horizontal = 4.dp),
            )
        }
        warnings.forEach { warn -> item { WarningCard(warn, fmt, w, expanded = true) } }
        others.forEach { (name, list) ->
            item { Text(name, style = MaterialTheme.typography.titleLarge, modifier = w.padding(horizontal = 4.dp, vertical = 4.dp)) }
            list.forEach { warn -> item { WarningCard(warn, fmt, w, expanded = false) } }
        }
        item {
            Text(
                stringResource(R.string.al_warnings_sources),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = w.padding(horizontal = 4.dp),
            )
        }
    }
}

/** "What to wear" on the main screen, using the Wardrobe alert's preferences. */
@Composable
fun WardrobeCard(f: Forecast, settings: Settings, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { AlertPrefs.get(context) }
    val configs by prefs.configs.collectAsState()
    val cfg = configs.getValue(AlertType.WARDROBE)
    val fmt = remember(settings, f.zone) { Fmt(settings, f.zone) }
    var tomorrow by remember(f.zone) { mutableStateOf(LocalTime.now(f.zone).hour >= 18) }
    var work by remember { mutableStateOf<Forecast?>(null) }
    LaunchedEffect(cfg.extra) {
        work = cfg.extra.takeIf { it.isNotBlank() }?.let { ForecastRepo.resolve(context, it) }?.let { ForecastRepo.get(context, it) }
    }
    val commute = cfg.flags.firstOrNull { it in setOf("walk", "transit", "balanced") } ?: "balanced"
    val advice: WardrobeAdvice? = remember(f, work, tomorrow, cfg, settings) { Wardrobe.advise(f, work, fmt, tomorrow, cfg.level, commute) }
    AppCard(modifier) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Checkroom, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.al_wardrobe_card_title), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Tune, stringResource(R.string.al_wardrobe_settings), Modifier.size(20.dp)) }
            }
            ConnectedChoice(listOf(false, true), tomorrow, { L.s(if (it) R.string.al_day_tomorrow else R.string.al_day_today) }, { tomorrow = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            if (advice == null) {
                Text(stringResource(R.string.al_wardrobe_no_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    advice.items.forEachIndexed { i, item ->
                        val (bg, fg) = when (i % 3) {
                            0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                            1 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                        }
                        Surface(shape = CircleShape, color = bg, contentColor = fg) {
                            Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(28.dp).clip(wearShapes[i % wearShapes.size].toShape()).background(fg),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(item.kind.icon, null, Modifier.size(16.dp), tint = bg) }
                                Spacer(Modifier.width(8.dp))
                                Text(item.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(advice.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val wearShapes = listOf(
    androidx.compose.material3.MaterialShapes.Cookie9Sided, androidx.compose.material3.MaterialShapes.Clover4Leaf,
    androidx.compose.material3.MaterialShapes.Sunny, androidx.compose.material3.MaterialShapes.Cookie6Sided,
    androidx.compose.material3.MaterialShapes.Gem, androidx.compose.material3.MaterialShapes.SoftBurst,
)
