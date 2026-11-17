@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.vis.weather.alerts.AlertConfig
import dev.vis.weather.alerts.AlertGroup
import dev.vis.weather.alerts.AlertPrefs
import dev.vis.weather.alerts.AlertScheduler
import dev.vis.weather.alerts.AlertType
import dev.vis.weather.alerts.Trigger
import dev.vis.weather.alerts.Warning
import dev.vis.weather.alerts.WarningsApi
import dev.vis.weather.alerts.PlaceRegions
import dev.vis.weather.data.Place
import dev.vis.weather.data.Settings
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.components.SettingsGroup
import dev.vis.weather.ui.components.SettingsItemSurface
import dev.vis.weather.ui.components.ShapeBadge
import dev.vis.weather.ui.components.segmentedShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One-line status for an alert row: "07:00", "All day 07:00–19:00", "Every 15 min"… */
fun AlertType.status(c: AlertConfig, use24h: Boolean): String = when {
    !c.enabled -> "Off"
    this == AlertType.RAIN && c.allDay -> "All day, ${timeLabel(c.time, use24h)}–${timeLabel(c.endTime, use24h)}"
    trigger == Trigger.DAILY -> "Daily at ${timeLabel(c.time, use24h)}"
    trigger == Trigger.PERSISTENT -> "Always on"
    this == AlertType.NOWCAST -> "Checks every 15 min"
    else -> "Checks every 30 min"
}

@Composable
fun AlertsScreen(
    settings: Settings,
    place: Place?,
    warnings: List<Warning>,
    zone: ZoneId?,
    onOpenAlert: (AlertType) -> Unit,
    onOpenWarnings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { AlertPrefs.get(context) }
    val configs by prefs.configs.collectAsState()
    val history by prefs.history.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val granted = remember(resumeTick) { notificationsGranted(context) && androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exact = remember(resumeTick) { AlertScheduler.canExact(context) }
    val anyDaily = configs.any { (t, c) -> c.enabled && t.trigger == Trigger.DAILY }
    var exactDismissed by remember { mutableStateOf(prefs.exactBannerDismissed) }
    var pendingEnable by remember { mutableStateOf<AlertType?>(null) }
    val askPermission = rememberNotificationPermission { ok ->
        resumeTick++
        val t = pendingEnable
        if (ok && t != null) {
            prefs.update(t) { it.copy(enabled = true) }
            AlertScheduler.sync(context)
            AlertScheduler.runWatchNow(context)
        }
        pendingEnable = null
    }
    val fmt = remember(settings, zone) { zone?.let { Fmt(settings, it) } }
    val coverage = remember(place) { place?.let { PlaceRegions.cached(context, it) }?.countryCode?.let { WarningsApi.coverage(it) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp + dev.vis.weather.ui.components.LocalBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val w = Modifier.widthIn(max = 720.dp).fillMaxWidth()
        item {
            Column(w.statusBarsPadding().padding(top = 16.dp, start = 4.dp)) {
                Text("Alerts", style = MaterialTheme.typography.displaySmallEmphasized)
                Text(
                    "Notifications for warnings, rain and your daily plans",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!granted) {
            item {
                Banner(
                    w, Icons.Rounded.NotificationsOff, "Notifications are off",
                    "Allow notifications so warnings and alerts can reach you on time.", "Allow notifications",
                ) { askPermission() }
            }
        } else if (anyDaily && !exact && !exactDismissed) {
            item {
                Banner(
                    w, Icons.Rounded.Alarm, "For accurate timing",
                    "Grant \"Alarms & reminders\" so daily alerts arrive at exactly the time you've set (otherwise within ~10 minutes).",
                    "Grant in Settings", onDismiss = { prefs.exactBannerDismissed = true; exactDismissed = true },
                ) { openExactAlarmSettings(context) }
            }
        }
        item {
            SettingsGroup(if (place != null) "Active warnings · ${place.name}" else "Active warnings", w) {
                if (warnings.isEmpty()) {
                    SettingsItemSurface(0, 1, onClick = onOpenWarnings) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShapeBadge(
                                Icons.Rounded.CheckCircle, MaterialShapes.Cookie9Sided, size = 40.dp,
                                container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("No warnings in effect", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    coverage?.let { "Checked with $it" } ?: "Official warnings cover the US, Canada and 37 European countries",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        warnings.forEach { WarningCard(it, fmt, Modifier.fillMaxWidth(), onClick = onOpenWarnings) }
                    }
                }
            }
        }
        AlertGroup.entries.forEach { group ->
            val types = AlertType.entries.filter { it.group == group }
            item(key = group.name) {
                SettingsGroup(group.title, w) {
                    types.forEachIndexed { i, t ->
                        val c = configs.getValue(t)
                        AlertRow(t, c, settings.use24h, i, types.size, onClick = { onOpenAlert(t) }) { on ->
                            if (on && !granted) {
                                pendingEnable = t
                                askPermission()
                            } else {
                                prefs.update(t) { it.copy(enabled = on) }
                                AlertScheduler.sync(context)
                                if (on && t.trigger != Trigger.DAILY) AlertScheduler.runWatchNow(context)
                            }
                        }
                    }
                }
            }
        }
        item {
            QuietHours(prefs, settings, w)
        }
        if (history.isNotEmpty()) {
            item {
                Row(w.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Recent notifications", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { prefs.clearHistory() }) { Text("Clear") }
                }
            }
            val shown = history.take(12)
            items(shown.size) { i ->
                val h = shown[i]
                Surface(w, shape = segmentedShape(i, shown.size), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShapeBadge(h.type.icon, h.type.shape, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(h.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Instant.ofEpochMilli(h.time).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern(if (settings.use24h) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault())),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(
    type: AlertType,
    c: AlertConfig,
    use24h: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    SettingsItemSurface(index, count, onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(
                type.icon, type.shape, size = 44.dp,
                container = if (c.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                content = if (c.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(type.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (c.enabled) type.status(c, use24h) else type.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (c.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = c.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun QuietHours(prefs: AlertPrefs, settings: Settings, modifier: Modifier) {
    var on by remember { mutableStateOf(prefs.quietHours) }
    var start by remember { mutableIntStateOf(prefs.quietStart) }
    var end by remember { mutableIntStateOf(prefs.quietEnd) }
    var picking by remember { mutableIntStateOf(0) }
    SettingsGroup("Quiet hours", modifier) {
        SettingsItemSurface(0, if (on) 2 else 1, onClick = { on = !on; prefs.quietHours = on }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(Icons.Rounded.Bedtime, MaterialShapes.Cookie12Sided, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Quiet hours", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Alerts arrive silently overnight. Amber and red warnings still sound.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = on, onCheckedChange = { on = it; prefs.quietHours = it })
            }
        }
        if (on) {
            SettingsItemSurface(1, 2) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeChip("From", timeLabel(start, settings.use24h), Modifier.weight(1f)) { picking = 1 }
                    TimeChip("Until", timeLabel(end, settings.use24h), Modifier.weight(1f)) { picking = 2 }
                }
            }
        }
    }
    if (picking != 0) {
        TimeDialog(if (picking == 1) start else end, settings.use24h, onDismiss = { picking = 0 }) { m ->
            if (picking == 1) { start = m; prefs.quietStart = m } else { end = m; prefs.quietEnd = m }
            picking = 0
        }
    }
}

@Composable
fun TimeChip(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(value, style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun Banner(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    onDismiss: (() -> Unit)? = null,
    onAction: () -> Unit,
) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(icon, MaterialShapes.Sunny, size = 40.dp, container = MaterialTheme.colorScheme.tertiary, content = MaterialTheme.colorScheme.onTertiary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAction) { Text(action) }
                if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}
