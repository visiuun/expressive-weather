@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.AppIcon
import dev.vis.weather.data.CardStyle
import dev.vis.weather.data.PrecipUnit
import dev.vis.weather.data.PressureUnit
import dev.vis.weather.data.Settings
import dev.vis.weather.data.TempUnit
import dev.vis.weather.data.Updater
import dev.vis.weather.data.ThemeMode
import dev.vis.weather.data.WindUnit
import dev.vis.weather.ui.components.ConnectedChoice
import dev.vis.weather.ui.components.ShapeBadge
import dev.vis.weather.widget.AirWidgetReceiver
import dev.vis.weather.widget.ClockWidgetReceiver
import dev.vis.weather.widget.DailyWidgetReceiver
import dev.vis.weather.widget.DetailsWidgetReceiver
import dev.vis.weather.widget.ForecastWidgetReceiver
import dev.vis.weather.widget.HourlyWidgetReceiver
import dev.vis.weather.widget.NowWidgetReceiver
import dev.vis.weather.widget.RainWidgetReceiver
import dev.vis.weather.widget.SunMoonWidgetReceiver

enum class SettingsPage { APPEARANCE, LAYOUT, UNITS, ALERTS, FRIENDS, APP_ICON, WIDGETS, DATA, STATS }

@Composable
fun SettingsScreen(
    settings: Settings,
    updates: UpdateUi,
    onBack: () -> Unit,
    update: ((Settings) -> Settings) -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
) {
    val context = LocalContext.current
    dev.vis.weather.ui.components.SubScreen("Settings", onBack) { w ->
        item {
            dev.vis.weather.ui.components.SettingsGroup("Look & feel", w) {
                val n = 4
                dev.vis.weather.ui.components.SettingsAction(
                    "Appearance", "Colours, theme, fonts and weather effects", Icons.Rounded.Palette, MaterialShapes.Flower, 0, n, chevron = true,
                ) { onOpen(SettingsPage.APPEARANCE) }
                dev.vis.weather.ui.components.SettingsAction(
                    "Layout", "Forecast style and the order of sections", Icons.Rounded.Dashboard, MaterialShapes.Square, 1, n, chevron = true,
                ) { onOpen(SettingsPage.LAYOUT) }
                dev.vis.weather.ui.components.SettingsAction(
                    "App icon", "Static or changes with the weather", Icons.Rounded.Apps, MaterialShapes.Cookie12Sided, 2, n, chevron = true,
                ) { onOpen(SettingsPage.APP_ICON) }
                dev.vis.weather.ui.components.SettingsAction(
                    "Widgets", "Add widgets to your home screen", Icons.Rounded.Widgets, MaterialShapes.Cookie4Sided, 3, n, chevron = true,
                ) { onOpen(SettingsPage.WIDGETS) }
            }
        }
        item {
            dev.vis.weather.ui.components.SettingsGroup("Weather", w) {
                val n = 5
                dev.vis.weather.ui.components.SettingsAction(
                    "Alerts & notifications", "Warnings, rain, daily briefings and more", Icons.Rounded.Notifications, MaterialShapes.Sunny, 0, n, chevron = true,
                ) { onOpen(SettingsPage.ALERTS) }
                dev.vis.weather.ui.components.SettingsAction(
                    "Family & friends", "Weather where your people are", Icons.Rounded.Groups, MaterialShapes.Cookie7Sided, 1, n, chevron = true,
                ) { onOpen(SettingsPage.FRIENDS) }
                dev.vis.weather.ui.components.SettingsAction(
                    "Units", "Metric, imperial and clock", Icons.Rounded.Thermostat, MaterialShapes.Arch, 2, n, chevron = true,
                ) { onOpen(SettingsPage.UNITS) }
                dev.vis.weather.ui.components.SettingsAction(
                    "Data & accuracy", "Forecast model, local correction and sources", Icons.Rounded.Storage, MaterialShapes.Gem, 3, n, chevron = true,
                ) { onOpen(SettingsPage.DATA) }
                dev.vis.weather.ui.components.SettingsAction(
                    "Your activity", "Streaks, records and conditions you've seen", Icons.Rounded.Insights, MaterialShapes.Clover8Leaf, 4, n, chevron = true,
                ) { onOpen(SettingsPage.STATS) }
            }
        }
        item {
            Group("Updates", w) {
                SwitchItem(
                    "Check for updates automatically", "Looks for new releases on GitHub when the app starts",
                    Icons.Rounded.Update, MaterialShapes.Cookie9Sided, 0, 2, settings.autoUpdate,
                ) { v -> update { it.copy(autoUpdate = v) } }
                val r = updates.release
                val progress = updates.progress
                val status = when {
                    progress != null -> "Downloading… ${(progress * 100).toInt()}%"
                    updates.checking -> "Checking…"
                    updates.message != null -> updates.message
                    else -> "Installed version ${Updater.currentVersion}"
                }
                ActionItem(
                    if (r != null) "Install version ${r.version}" else "Check for updates", status,
                    Icons.Rounded.SystemUpdate, MaterialShapes.Flower, 1, 2,
                ) { if (r != null) onInstallUpdate() else onCheckUpdates() }
            }
        }
        item {
            Group("About", w) {
                InfoItem(
                    "Open data, no tracking",
                    "No accounts, ads, analytics or Google Play Services. Forecasts by Open-Meteo.com (CC BY 4.0) from national weather services; " +
                        "warnings from NWS, ECCC, DWD and MeteoAlarm; radar by RainViewer; maps © OpenStreetMap contributors © CARTO. " +
                        "See Data & accuracy for every source.",
                    0, 4,
                )
                InfoItem("Expressive Weather", "Version ${Updater.currentVersion} · Material 3 Expressive", 1, 4)
                ActionItem("Source code", Updater.REPO_URL.removePrefix("https://"), Icons.Rounded.Code, MaterialShapes.Cookie6Sided, 2, 4) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                ActionItem("Report a bug", "Opens a new issue on GitHub", Icons.Rounded.BugReport, MaterialShapes.Clover4Leaf, 3, 4) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL + "/issues/new")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

@Composable
fun WidgetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pinnable = Build.VERSION.SDK_INT >= 26 && AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    dev.vis.weather.ui.components.SubScreen("Widgets", onBack) { w ->
        item {
            Group(if (pinnable) "Tap to add to your home screen" else "Add from your home screen widget picker", w) {
                pinnableWidgets.forEachIndexed { i, (title, receiver) ->
                    ActionItem(title, widgetDescriptions[title], Icons.Rounded.Widgets, widgetShapes[i % widgetShapes.size], i, pinnableWidgets.size) {
                        if (pinnable) AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, receiver), null, null)
                    }
                }
            }
        }
        item {
            Text(
                "Touch and hold a widget, then drag its handles to resize it. Tap the place name on a widget to switch it to your next saved place.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = w.padding(horizontal = 8.dp),
            )
        }
    }
}

private val widgetDescriptions = mapOf(
    "Now" to "Current conditions, from a slim pill to a dashboard",
    "Hourly" to "The next 48 hours, paged",
    "16-day forecast" to "Scrollable daily list with range bars",
    "Forecast hub" to "Hourly, daily and details tabs",
    "Clock & weather" to "Big clock and date beside the weather",
    "Details" to "Feels like, wind, humidity, UV, air and more",
    "Rain" to "Nowcast and rain chance chart",
    "Sun & moon" to "Sun position and moon phase",
    "Air quality" to "AQI gauge, particles and pollen",
    "Weather shape" to "Temperature in an expressive shape you choose",
)

@Composable
private fun Group(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun ItemSurface(index: Int, count: Int, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = segmentShape(index, count),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) { content() }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = segmentShape(index, count),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) { content() }
    }
}

@Composable
private fun <T> ChoiceItem(
    title: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    index: Int,
    count: Int,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    ItemSurface(index, count) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(icon, shape, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            ConnectedChoice(options, selected, label, onSelect, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SwitchItem(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    shape: RoundedPolygon,
    index: Int,
    count: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ItemSurface(index, count, onClick = { onChange(!checked) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(icon, shape, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

private val pinnableWidgets = listOf(
    "Now" to NowWidgetReceiver::class.java,
    "Hourly" to HourlyWidgetReceiver::class.java,
    "16-day forecast" to DailyWidgetReceiver::class.java,
    "Forecast hub" to ForecastWidgetReceiver::class.java,
    "Clock & weather" to ClockWidgetReceiver::class.java,
    "Details" to DetailsWidgetReceiver::class.java,
    "Rain" to RainWidgetReceiver::class.java,
    "Sun & moon" to SunMoonWidgetReceiver::class.java,
    "Air quality" to AirWidgetReceiver::class.java,
    "Weather shape" to dev.vis.weather.widget.ShapeWidgetReceiver::class.java,
)

private val widgetShapes = listOf(
    MaterialShapes.Cookie4Sided, MaterialShapes.Cookie7Sided, MaterialShapes.Gem, MaterialShapes.Flower,
    MaterialShapes.Cookie12Sided, MaterialShapes.Square, MaterialShapes.Arch, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf,
)

@Composable
private fun ActionItem(title: String, subtitle: String?, icon: ImageVector, shape: RoundedPolygon, index: Int, count: Int, onClick: () -> Unit) {
    ItemSurface(index, count, onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(icon, shape, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InfoItem(title: String, body: String, index: Int, count: Int) {
    ItemSurface(index, count) {
        Row(Modifier.padding(16.dp)) {
            ShapeBadge(Icons.Rounded.Info, MaterialShapes.Circle, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
