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
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.rounded.Language
import dev.vis.weather.R
import java.util.Locale
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
    dev.vis.weather.ui.components.SubScreen(stringResource(R.string.s_settings_title), onBack) { w ->
        item {
            dev.vis.weather.ui.components.SettingsGroup(stringResource(R.string.s_group_look), w) {
                val n = 5
                dev.vis.weather.ui.components.SettingsAction(
                    stringResource(R.string.s_appearance), stringResource(R.string.s_appearance_sub), Icons.Rounded.Palette, MaterialShapes.Flower, 0, n, chevron = true,
                ) { onOpen(SettingsPage.APPEARANCE) }
                dev.vis.weather.ui.components.SettingsAction(
                    stringResource(R.string.h_customize_home), stringResource(R.string.h_customize_sub), Icons.Rounded.Dashboard, MaterialShapes.Square, 1, n, chevron = true,
                ) { onOpen(SettingsPage.LAYOUT) }
                dev.vis.weather.ui.components.SettingsAction(
                    stringResource(R.string.s_app_icon), stringResource(R.string.s_app_icon_sub), Icons.Rounded.Apps, MaterialShapes.Cookie12Sided, 2, n, chevron = true,
                ) { onOpen(SettingsPage.APP_ICON) }
                dev.vis.weather.ui.components.SettingsAction(
                    stringResource(R.string.s_widgets), stringResource(R.string.s_widgets_sub), Icons.Rounded.Widgets, MaterialShapes.Cookie4Sided, 3, n, chevron = true,
                ) { onOpen(SettingsPage.WIDGETS) }
                val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                val languageName = locale.getDisplayName(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                dev.vis.weather.ui.components.SettingsAction(
                    stringResource(R.string.s_language), stringResource(R.string.s_language_sub, languageName),
                    Icons.Rounded.Language, MaterialShapes.Clover4Leaf, 4, n, chevron = true,
                ) {
                    val intent = if (Build.VERSION.SDK_INT >= 33) {
                        Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    } else {
                        Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)
                    }
                    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
                        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
        }
        item {
            dev.vis.weather.ui.components.SettingsGroup(stringResource(R.string.s_group_weather), w) {
                val n = 5
                dev.vis.weather.ui.components.SettingsAction(
                    stringResource(R.string.s_alerts), stringResource(R.string.s_alerts_sub), Icons.Rounded.Notifications, MaterialShapes.Sunny, 0, n, chevron = true,
                ) { onOpen(SettingsPage.ALERTS) }
                dev.vis.weather.ui.components.SettingsAction(
                    stringResource(R.string.s_friends), stringResource(R.string.s_friends_sub), Icons.Rounded.Groups, MaterialShapes.Cookie7Sided, 1, n, chevron = true,
                ) { onOpen(SettingsPage.FRIENDS) }
                dev.vis.weather.ui.components.SettingsAction(
                    stringResource(R.string.s_units), stringResource(R.string.s_units_sub), Icons.Rounded.Thermostat, MaterialShapes.Arch, 2, n, chevron = true,
                ) { onOpen(SettingsPage.UNITS) }
                dev.vis.weather.ui.components.SettingsAction(
                    stringResource(R.string.s_data), stringResource(R.string.s_data_sub), Icons.Rounded.Storage, MaterialShapes.Gem, 3, n, chevron = true,
                ) { onOpen(SettingsPage.DATA) }
                dev.vis.weather.ui.components.SettingsAction(
                    stringResource(R.string.s_activity), stringResource(R.string.s_activity_sub), Icons.Rounded.Insights, MaterialShapes.Clover8Leaf, 4, n, chevron = true,
                ) { onOpen(SettingsPage.STATS) }
            }
        }
        item {
            Group(stringResource(R.string.s_group_updates), w) {
                SwitchItem(
                    stringResource(R.string.s_auto_update), stringResource(R.string.s_auto_update_sub),
                    Icons.Rounded.Update, MaterialShapes.Cookie9Sided, 0, 2, settings.autoUpdate,
                ) { v -> update { it.copy(autoUpdate = v) } }
                val r = updates.release
                val progress = updates.progress
                val status = when {
                    progress != null -> stringResource(R.string.s_update_downloading, (progress * 100).toInt())
                    updates.checking -> stringResource(R.string.s_update_checking)
                    updates.message != null -> updates.message
                    else -> stringResource(R.string.s_update_installed, Updater.currentVersion)
                }
                ActionItem(
                    if (r != null) stringResource(R.string.s_update_install, r.version) else stringResource(R.string.s_update_check), status,
                    Icons.Rounded.SystemUpdate, MaterialShapes.Flower, 1, 2,
                ) { if (r != null) onInstallUpdate() else onCheckUpdates() }
            }
        }
        item {
            Group(stringResource(R.string.s_group_about), w) {
                InfoItem(
                    stringResource(R.string.s_about_open_data),
                    stringResource(R.string.s_about_open_data_body),
                    0, 4,
                )
                InfoItem("Expressive Weather", stringResource(R.string.s_about_version, Updater.currentVersion), 1, 4)
                ActionItem(stringResource(R.string.s_source_code), Updater.REPO_URL.removePrefix("https://"), Icons.Rounded.Code, MaterialShapes.Cookie6Sided, 2, 4) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                ActionItem(stringResource(R.string.s_report_bug), stringResource(R.string.s_report_bug_sub), Icons.Rounded.BugReport, MaterialShapes.Clover4Leaf, 3, 4) {
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
    dev.vis.weather.ui.components.SubScreen(stringResource(R.string.s_widgets), onBack) { w ->
        item {
            Group(stringResource(if (pinnable) R.string.s_widgets_tap_to_add else R.string.s_widgets_add_from_picker), w) {
                pinnableWidgets.forEachIndexed { i, (titleRes, receiver) ->
                    ActionItem(stringResource(titleRes), widgetDescriptions[titleRes]?.let { stringResource(it) }, Icons.Rounded.Widgets, widgetShapes[i % widgetShapes.size], i, pinnableWidgets.size) {
                        if (pinnable) AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, receiver), null, null)
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.s_widgets_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = w.padding(horizontal = 8.dp),
            )
        }
    }
}

private val widgetDescriptions: Map<Int, Int> = mapOf(
    R.string.s_widget_now to R.string.s_widget_now_desc,
    R.string.s_widget_hourly to R.string.s_widget_hourly_desc,
    R.string.s_widget_daily to R.string.s_widget_daily_desc,
    R.string.s_widget_hub to R.string.s_widget_hub_desc,
    R.string.s_widget_clock to R.string.s_widget_clock_desc,
    R.string.s_widget_details to R.string.s_widget_details_desc,
    R.string.s_widget_rain to R.string.s_widget_rain_desc,
    R.string.s_widget_sunmoon to R.string.s_widget_sunmoon_desc,
    R.string.s_widget_air to R.string.s_widget_air_desc,
    R.string.s_widget_shape to R.string.s_widget_shape_desc,
    R.string.s_widget_stack to R.string.s_widget_stack_desc,
    R.string.s_widget_tile to R.string.s_widget_tile_desc,
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

private val pinnableWidgets: List<Pair<Int, Class<*>>> = listOf(
    R.string.s_widget_now to NowWidgetReceiver::class.java,
    R.string.s_widget_hourly to HourlyWidgetReceiver::class.java,
    R.string.s_widget_daily to DailyWidgetReceiver::class.java,
    R.string.s_widget_hub to ForecastWidgetReceiver::class.java,
    R.string.s_widget_clock to ClockWidgetReceiver::class.java,
    R.string.s_widget_details to DetailsWidgetReceiver::class.java,
    R.string.s_widget_rain to RainWidgetReceiver::class.java,
    R.string.s_widget_sunmoon to SunMoonWidgetReceiver::class.java,
    R.string.s_widget_air to AirWidgetReceiver::class.java,
    R.string.s_widget_shape to dev.vis.weather.widget.ShapeWidgetReceiver::class.java,
    R.string.s_widget_stack to dev.vis.weather.widget.StackWidgetReceiver::class.java,
    R.string.s_widget_tile to dev.vis.weather.widget.DataTileWidgetReceiver::class.java,
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
