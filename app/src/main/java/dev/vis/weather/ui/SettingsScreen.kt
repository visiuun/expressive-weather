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

@Composable
fun SettingsScreen(
    settings: Settings,
    updates: UpdateUi,
    onBack: () -> Unit,
    update: ((Settings) -> Settings) -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMediumEmphasized)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            val w = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            item {
                Group("Appearance", w) {
                    val items = buildList<@Composable (Int, Int) -> Unit> {
                        add { i, n ->
                            ChoiceItem(
                                "Theme", Icons.Rounded.Contrast, MaterialShapes.Cookie4Sided, i, n,
                                ThemeMode.entries, settings.theme,
                                { when (it) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" } },
                            ) { v -> update { it.copy(theme = v) } }
                        }
                        add { i, n ->
                            SwitchItem(
                                "OLED black", "Pure black background in dark theme, saves battery on OLED screens",
                                Icons.Rounded.DarkMode, MaterialShapes.Cookie9Sided, i, n, settings.oled,
                            ) { v -> update { it.copy(oled = v) } }
                        }
                        if (Build.VERSION.SDK_INT >= 31) {
                            add { i, n ->
                                SwitchItem(
                                    "Dynamic color", "Use colors from your wallpaper (Material You)",
                                    Icons.Rounded.Palette, MaterialShapes.Flower, i, n, settings.dynamicColor,
                                ) { v -> update { it.copy(dynamicColor = v) } }
                            }
                        }
                        add { i, n ->
                            SwitchItem(
                                "Hero background", "Colored card behind the current conditions",
                                Icons.Rounded.Wallpaper, MaterialShapes.Cookie6Sided, i, n, settings.heroBackground,
                            ) { v -> update { it.copy(heroBackground = v) } }
                        }
                        add { i, n ->
                            ChoiceItem(
                                "Card style", Icons.Rounded.Dashboard, MaterialShapes.Square, i, n,
                                CardStyle.entries, settings.cardStyle,
                                { when (it) { CardStyle.FILLED -> "Filled"; CardStyle.OUTLINED -> "Outlined"; CardStyle.NONE -> "None" } },
                            ) { v -> update { it.copy(cardStyle = v) } }
                        }
                        add { i, n ->
                            ChoiceItem(
                                "Weather icons", Icons.Rounded.WbCloudy, MaterialShapes.Puffy, i, n,
                                listOf(false, true), settings.monoIcons,
                                { if (it) "Black & white" else "Color" },
                            ) { v -> update { it.copy(monoIcons = v) } }
                        }
                        add { i, n ->
                            ChoiceItem(
                                "App icon", Icons.Rounded.Apps, MaterialShapes.Cookie12Sided, i, n,
                                AppIcon.entries, settings.appIcon,
                                { when (it) { AppIcon.OLED -> "Black"; AppIcon.LIGHT -> "White"; AppIcon.COLOR -> "Color" } },
                            ) { v -> update { it.copy(appIcon = v) } }
                        }
                    }
                    items.forEachIndexed { i, c -> c(i, items.size) }
                }
            }
            item {
                Group("Units", w.padding(top = 20.dp)) {
                    val n = 5
                    ChoiceItem("Temperature", Icons.Rounded.Thermostat, MaterialShapes.Arch, 0, n, TempUnit.entries, settings.tempUnit, {
                        if (it == TempUnit.C) "°C" else "°F"
                    }) { v -> update { it.copy(tempUnit = v) } }
                    ChoiceItem("Wind speed", Icons.Rounded.Air, MaterialShapes.Cookie6Sided, 1, n, WindUnit.entries, settings.windUnit, {
                        when (it) { WindUnit.KMH -> "km/h"; WindUnit.MS -> "m/s"; WindUnit.MPH -> "mph"; WindUnit.KN -> "kn" }
                    }) { v -> update { it.copy(windUnit = v) } }
                    ChoiceItem("Precipitation", Icons.Rounded.WaterDrop, MaterialShapes.Puffy, 2, n, PrecipUnit.entries, settings.precipUnit, {
                        if (it == PrecipUnit.MM) "mm" else "in"
                    }) { v -> update { it.copy(precipUnit = v) } }
                    ChoiceItem("Pressure", Icons.Rounded.Compress, MaterialShapes.Gem, 3, n, PressureUnit.entries, settings.pressureUnit, {
                        when (it) { PressureUnit.HPA -> "hPa"; PressureUnit.INHG -> "inHg"; PressureUnit.MMHG -> "mmHg" }
                    }) { v -> update { it.copy(pressureUnit = v) } }
                    SwitchItem("24-hour clock", null, Icons.Rounded.Schedule, MaterialShapes.Cookie12Sided, 4, n, settings.use24h) { v ->
                        update { it.copy(use24h = v) }
                    }
                }
            }
            val pinnable = Build.VERSION.SDK_INT >= 26 &&
                AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
            if (pinnable) {
                item {
                    Group("Widgets", w.padding(top = 20.dp)) {
                        pinnableWidgets.forEachIndexed { i, (title, receiver) ->
                            ActionItem(title, "Add to home screen", Icons.Rounded.Widgets, widgetShapes[i % widgetShapes.size], i, pinnableWidgets.size) {
                                AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, receiver), null, null)
                            }
                        }
                    }
                }
            }
            item {
                Group("Updates", w.padding(top = 20.dp)) {
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
                Group("About", w.padding(top = 20.dp)) {
                    InfoItem(
                        "Open data, no tracking",
                        "Forecasts, air quality & geocoding by Open-Meteo.com (CC BY 4.0), using national weather services (DWD, NOAA, Météo-France, ECMWF and more). " +
                            "Air quality from Copernicus CAMS. Place names © OpenStreetMap contributors via Nominatim. " +
                            "Location uses Android's own LocationManager, so no Google Play Services are needed.",
                        0, 3,
                    )
                    InfoItem("Expressive Weather", "Version ${Updater.currentVersion} · Material 3 Expressive", 1, 3)
                    ActionItem("Source code", Updater.REPO_URL.removePrefix("https://"), Icons.Rounded.Code, MaterialShapes.Cookie6Sided, 2, 3) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
}

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
)

private val widgetShapes = listOf(
    MaterialShapes.Cookie4Sided, MaterialShapes.Puffy, MaterialShapes.Gem, MaterialShapes.Flower,
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
