@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.components

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/*
 * Shared building blocks for every settings-style screen (Settings, Appearance, Alerts, Data, App icon…),
 * so they all share one look: grouped rows with connected corners and an icon in an M3 Expressive shape.
 */

/** Corner radii for item [index] of [count] in a connected group: big outer corners, small inner ones. */
fun segmentedShape(index: Int, count: Int): RoundedCornerShape {
    val big = 24.dp
    val small = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
}

/** Full-screen scaffold: back arrow + large title, then a centred, width-limited list. */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: LazyListScope.(Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(4.dp))
            Text(title, style = MaterialTheme.typography.headlineMediumEmphasized, modifier = Modifier.weight(1f))
            actions()
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            content(Modifier.widthIn(max = 720.dp).fillMaxWidth())
        }
    }
}

@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }
        content()
    }
}

@Composable
fun SettingsItemSurface(index: Int, count: Int, onClick: (() -> Unit)? = null, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = segmentedShape(index, count),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) { content() }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = segmentedShape(index, count),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) { content() }
    }
}

@Composable
private fun RowLead(icon: ImageVector?, shape: RoundedPolygon) {
    if (icon != null) {
        ShapeBadge(icon, shape, size = 40.dp)
        Spacer(Modifier.width(14.dp))
    }
}

@Composable
fun <T> SettingsChoice(
    title: String,
    icon: ImageVector?,
    shape: RoundedPolygon,
    index: Int,
    count: Int,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    subtitle: String? = null,
    onSelect: (T) -> Unit,
) {
    SettingsItemSurface(index, count) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowLead(icon, shape)
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ConnectedChoice(options, selected, label, onSelect, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    shape: RoundedPolygon,
    index: Int,
    count: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SettingsItemSurface(index, count, onClick = { onChange(!checked) }, enabled = enabled) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RowLead(icon, shape)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onChange,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

/** A tappable row; shows a chevron when [chevron] (for rows that open another screen). */
@Composable
fun SettingsAction(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    shape: RoundedPolygon,
    index: Int,
    count: Int,
    chevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    SettingsItemSurface(index, count, onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RowLead(icon, shape)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
            if (chevron) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsInfo(title: String, body: String, index: Int, count: Int, icon: ImageVector = Icons.Rounded.Info) {
    SettingsItemSurface(index, count) {
        Row(Modifier.padding(16.dp)) {
            ShapeBadge(icon, MaterialShapes.Circle, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A slider row with a live value label. */
@Composable
fun SettingsSlider(
    title: String,
    valueLabel: String,
    index: Int,
    count: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    icon: ImageVector? = null,
    shape: RoundedPolygon = MaterialShapes.Circle,
    caption: String? = null,
    onChange: (Float) -> Unit,
) {
    SettingsItemSurface(index, count) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowLead(icon, shape)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(valueLabel, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.fillMaxWidth())
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
