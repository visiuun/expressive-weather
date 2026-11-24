@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package dev.vis.weather.ui.alerts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.FrontHand
import androidx.compose.material.icons.rounded.Cyclone
import androidx.compose.material.icons.rounded.Tornado
import androidx.compose.material.icons.rounded.Landslide
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.SevereCold
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Flood
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wysiwyg
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.R
import dev.vis.weather.alerts.AlertType
import dev.vis.weather.alerts.Severity
import dev.vis.weather.alerts.Warning
import dev.vis.weather.alerts.dayTime
import dev.vis.weather.ui.Fmt
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val AlertType.icon: ImageVector
    get() = when (this) {
        AlertType.WARNINGS -> Icons.Rounded.Warning
        AlertType.NOWCAST -> Icons.Rounded.Grain
        AlertType.RAIN -> Icons.Rounded.Umbrella
        AlertType.STORM -> Icons.Rounded.Air
        AlertType.TODAY -> Icons.Rounded.WbSunny
        AlertType.TOMORROW -> Icons.Rounded.Bedtime
        AlertType.WARDROBE -> Icons.Rounded.Checkroom
        AlertType.FROST -> Icons.Rounded.AcUnit
        AlertType.UV -> Icons.Rounded.LightMode
        AlertType.HEAT -> Icons.Rounded.Thermostat
        AlertType.AIR -> Icons.Rounded.Masks
        AlertType.POLLEN -> Icons.Rounded.LocalFlorist
        AlertType.TABLET -> Icons.Rounded.Medication
        AlertType.PRESSURE -> Icons.Rounded.Compress
        AlertType.FRIENDS -> Icons.Rounded.Groups
        AlertType.ONGOING -> Icons.Rounded.Wysiwyg
    }

private val shapeCycle = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Cookie7Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Gem,
    MaterialShapes.Cookie6Sided, MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Cookie12Sided, MaterialShapes.Arch,
    MaterialShapes.Diamond, MaterialShapes.Clover8Leaf, MaterialShapes.Pill, MaterialShapes.Cookie4Sided, MaterialShapes.Square,
    MaterialShapes.Bun,
)

val AlertType.shape: RoundedPolygon get() = shapeCycle[ordinal % shapeCycle.size]

/** Expressive Material icons for warning hazards (no emojis). */
val dev.vis.weather.alerts.Hazard.icon: ImageVector
    get() = when (this) {
        dev.vis.weather.alerts.Hazard.THUNDERSTORM -> Icons.Rounded.Thunderstorm
        dev.vis.weather.alerts.Hazard.WIND -> Icons.Rounded.Air
        dev.vis.weather.alerts.Hazard.RAIN -> Icons.Rounded.Umbrella
        dev.vis.weather.alerts.Hazard.FLOOD -> Icons.Rounded.Flood
        dev.vis.weather.alerts.Hazard.SNOW_ICE -> Icons.Rounded.AcUnit
        dev.vis.weather.alerts.Hazard.FOG -> Icons.Rounded.Dehaze
        dev.vis.weather.alerts.Hazard.HEAT -> Icons.Rounded.Thermostat
        dev.vis.weather.alerts.Hazard.COLD -> Icons.Rounded.SevereCold
        dev.vis.weather.alerts.Hazard.COASTAL -> Icons.Rounded.Waves
        dev.vis.weather.alerts.Hazard.FIRE -> Icons.Rounded.LocalFireDepartment
        dev.vis.weather.alerts.Hazard.AVALANCHE -> Icons.Rounded.Landslide
        dev.vis.weather.alerts.Hazard.TORNADO -> Icons.Rounded.Tornado
        dev.vis.weather.alerts.Hazard.HURRICANE -> Icons.Rounded.Cyclone
        dev.vis.weather.alerts.Hazard.DUST -> Icons.Rounded.Grain
        dev.vis.weather.alerts.Hazard.WEATHER -> Icons.Rounded.Warning
    }

/** Icons for the clothing advice items. */
val dev.vis.weather.alerts.Wear.icon: ImageVector
    get() = when (this) {
        dev.vis.weather.alerts.Wear.COAT, dev.vis.weather.alerts.Wear.JACKET, dev.vis.weather.alerts.Wear.TOP -> Icons.Rounded.Checkroom
        dev.vis.weather.alerts.Wear.GLOVES -> Icons.Rounded.FrontHand
        dev.vis.weather.alerts.Wear.LAYERS -> Icons.Rounded.Layers
        dev.vis.weather.alerts.Wear.SWEATER -> Icons.Rounded.Thermostat
        dev.vis.weather.alerts.Wear.SHOES -> Icons.Rounded.DirectionsWalk
        dev.vis.weather.alerts.Wear.BOOTS -> Icons.Rounded.Hiking
        dev.vis.weather.alerts.Wear.RAIN -> Icons.Rounded.WaterDrop
        dev.vis.weather.alerts.Wear.UMBRELLA -> Icons.Rounded.Umbrella
        dev.vis.weather.alerts.Wear.HOOD -> Icons.Rounded.Grain
        dev.vis.weather.alerts.Wear.WIND -> Icons.Rounded.Air
        dev.vis.weather.alerts.Wear.SUN -> Icons.Rounded.WbSunny
    }

fun Severity.color() = Color(argb)
fun Severity.onColor() = Color(onArgb)

fun timeLabel(minutes: Int, use24h: Boolean): String =
    LocalTime.of(minutes / 60 % 24, minutes % 60).format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", Locale.getDefault()))

/** Time picker in a dialog. */
@Composable
fun TimeDialog(initial: Int, use24h: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initial / 60, initial % 60, use24h)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.al_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.al_cancel)) } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
    )
}

/** Notification permission helpers. */
fun notificationsGranted(context: Context) = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(granted)
        if (!granted) openNotificationSettings(context)
    }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) openNotificationSettings(context)
        else onResult(true)
    }
}

fun openNotificationSettings(context: Context) {
    val i = if (Build.VERSION.SDK_INT >= 26) {
        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 31) {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** A coloured card for one official warning (Yellow / Amber / Red). */
@Composable
fun WarningCard(w: Warning, fmt: Fmt?, modifier: Modifier = Modifier, expanded: Boolean = false, onClick: (() -> Unit)? = null) {
    val bg = w.severity.color()
    val fg = w.severity.onColor()
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(Modifier.size(44.dp), shape = MaterialShapes.Cookie9Sided.toShape(), color = fg.copy(alpha = 0.16f)) {}
                    androidx.compose.material3.Icon(w.hazard.icon, null, Modifier.size(24.dp), tint = fg)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.al_warning_card_label, w.severity.colorName, w.hazard.label),
                        style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.85f),
                    )
                    Text(w.title, style = MaterialTheme.typography.titleMediumEmphasized, color = fg, fontWeight = FontWeight.SemiBold)
                }
            }
            val window = when {
                fmt == null -> ""
                w.onset > System.currentTimeMillis() / 1000 && w.expires > 0 -> stringResource(R.string.al_warning_from_until, fmt.dayTime(w.onset), fmt.dayTime(w.expires))
                w.expires > 0 -> stringResource(R.string.al_warning_until, fmt.dayTime(w.expires))
                else -> ""
            }
            if (window.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(window, style = MaterialTheme.typography.bodyMedium, color = fg)
            }
            if (w.headline.isNotBlank() && w.headline != w.title) {
                Spacer(Modifier.height(6.dp))
                Text(w.headline, style = MaterialTheme.typography.bodyMedium, color = fg, maxLines = if (expanded) Int.MAX_VALUE else 3)
            }
            if (expanded) {
                if (w.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(w.description, style = MaterialTheme.typography.bodyMedium, color = fg)
                }
                if (w.instruction.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.al_warning_what_to_do), style = MaterialTheme.typography.titleSmall, color = fg)
                    Text(w.instruction, style = MaterialTheme.typography.bodyMedium, color = fg)
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.al_warning_issued_by_via, w.sender, w.source), style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.8f))
            }
        }
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(28.dp), color = bg, contentColor = fg) { content() }
    else Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = bg, contentColor = fg) { content() }
}
