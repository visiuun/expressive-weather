@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.vis.weather.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.Indices
import dev.vis.weather.ui.components.SectionCard
import dev.vis.weather.ui.components.WeatherIcon

/*
 * Small building blocks shared by the detail screens so every insight card looks alike.
 */

val Indices.Band.colour: Color get() = Color(color)

/** Section card with an emphasised headline and supporting text. */
@Composable
fun InsightCard(
    title: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    SectionCard(title, icon, shape, modifier, trailing) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (accent != null) {
                    Box(Modifier.size(14.dp).clip(MaterialShapes.Cookie4Sided.toShape()).background(accent))
                    Spacer(Modifier.width(10.dp))
                }
                Text(headline, style = MaterialTheme.typography.headlineSmallEmphasized)
            }
            if (body.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Body(body)
            }
        }
        content()
    }
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** A rounded note with an info icon (explainers, disclaimers). */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.Info) {
    Surface(
        modifier.padding(horizontal = 18.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Coloured label chip. */
@Composable
fun BandPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = color.copy(alpha = 0.18f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/**
 * A segmented scale (connected segments, rounded ends) with the active level highlighted and
 * springing into place.
 */
@Composable
fun LevelScale(colors: List<Color>, level: Int, modifier: Modifier = Modifier, labels: List<String>? = null) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val ring = MaterialTheme.colorScheme.onSurface
    val anim by animateFloatAsState(
        level.coerceAtLeast(0).toFloat(),
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "lvl",
    )
    Column(modifier.padding(horizontal = 18.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val n = colors.size
            val gap = 3.dp.toPx()
            val segW = (size.width - gap * (n - 1)) / n
            val h = size.height
            colors.forEachIndexed { i, c ->
                val x = i * (segW + gap)
                val big = h / 2
                val small = 3.dp.toPx()
                val active = level >= 0 && i <= level
                val col = if (active) c else c.copy(alpha = 0.28f).compositeOverSafe(track)
                // Outer ends fully rounded, inner joins tight — like a connected button group.
                drawRoundRect(col, Offset(x, 0f), Size(segW, h), CornerRadius(if (i == 0 || i == n - 1) big else small))
            }
            if (level >= 0) {
                val cx = anim * (segW + gap) + segW / 2
                drawCircle(ring, h * 0.42f, Offset(cx, h / 2))
                drawCircle(colors[level.coerceIn(0, n - 1)], h * 0.26f, Offset(cx, h / 2))
            }
        }
        if (labels != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, l ->
                    Text(
                        l,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == level) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (i == level) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun Color.compositeOverSafe(bg: Color): Color = androidx.compose.ui.graphics.lerp(bg, this.copy(alpha = 1f), alpha)

/** Grid of small label/value tiles. */
@Composable
fun StatChips(items: List<Triple<String, String, String?>>, modifier: Modifier = Modifier, perRow: Int = 3) {
    FlowRow(
        modifier.padding(horizontal = 18.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = perRow,
    ) {
        items.forEach { (label, value, sub) ->
            Surface(Modifier.weight(1f).fillMaxRowHeight(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(value, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!sub.isNullOrEmpty()) {
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** One outlook row: day, icon, label pill, value. */
class OutlookItem(val day: String, val code: Int?, val label: String, val value: String, val color: Color, val fraction: Float)

@Composable
fun OutlookList(items: List<OutlookItem>, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { it ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it.day, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(76.dp), maxLines = 1)
                if (it.code != null) WeatherIcon(it.code, true, Modifier.size(24.dp)) else Spacer(Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    val fr = it.fraction.coerceIn(0f, 1f)
                    val c = it.color
                    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                        drawRoundRect(track, size = size, cornerRadius = CornerRadius(size.height / 2))
                        if (fr > 0f) drawRoundRect(c, size = Size(maxOf(size.width * fr, size.height), size.height), cornerRadius = CornerRadius(size.height / 2))
                    }
                }
                Text(
                    it.value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(72.dp).padding(start = 10.dp),
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

/** Label + value row used in fact lists. */
@Composable
fun FactRow(label: String, value: String, sub: String? = null, icon: ImageVector? = null, shape: RoundedPolygon = MaterialShapes.Circle) {
    Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            dev.vis.weather.ui.components.ShapeBadge(icon, shape, size = 32.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!sub.isNullOrEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMediumEmphasized, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))
