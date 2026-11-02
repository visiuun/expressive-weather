@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import dev.vis.weather.data.CardStyle
import dev.vis.weather.ui.theme.LocalAppColors
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.Sky

/** Temperature (°C) → color ramp used by range bars. */
fun tempColor(c: Double): Color {
    val stops = listOf(
        -15.0 to Color(0xFF9C7CFF),
        0.0 to Color(0xFF4A9EFF),
        10.0 to Color(0xFF35C7B0),
        20.0 to Color(0xFFFFC94D),
        30.0 to Color(0xFFFF8A3D),
        40.0 to Color(0xFFE5484D),
    )
    if (c.isNaN()) return stops[2].second
    if (c <= stops.first().first) return stops.first().second
    if (c >= stops.last().first) return stops.last().second
    val i = stops.indexOfFirst { it.first > c }
    val (t0, c0) = stops[i - 1]
    val (t1, c1) = stops[i]
    return androidx.compose.ui.graphics.lerp(c0, c1, ((c - t0) / (t1 - t0)).toFloat())
}

/** A small icon set inside one of the M3 Expressive shapes. */
@Composable
fun ShapeBadge(
    icon: ImageVector,
    shape: RoundedPolygon,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier
            .size(size)
            .clip(shape.toShape())
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = content)
    }
}

/** Container color for cards, honouring the "Card style" setting. */
@Composable
fun cardColor(): Color =
    if (LocalAppColors.current.cardStyle == CardStyle.FILLED) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent

/** The color actually visible behind card content (used for "cut-out" rings on dots). */
@Composable
fun cardBackdrop(): Color =
    if (LocalAppColors.current.cardStyle == CardStyle.FILLED) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background

@Composable
fun cardBorder(): BorderStroke? =
    if (LocalAppColors.current.cardStyle == CardStyle.OUTLINED) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null

/** A card surface that follows the card-style setting. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(32.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = cardColor(), border = cardBorder(), content = content)
    } else {
        Surface(modifier = modifier, shape = shape, color = cardColor(), border = cardBorder(), content = content)
    }
}

/**
 * Morphs between M3 Expressive shapes while slowly rotating:
 * holds each shape, then springs into the next.
 */
@Composable
fun MorphingBlob(
    shapes: List<RoundedPolygon>,
    color: Color,
    modifier: Modifier = Modifier,
    holdMs: Int = 2600,
    morphMs: Int = 900,
) {
    val morphs = remember(shapes) { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val path = remember { android.graphics.Path() }
    val t = rememberInfiniteTransition(label = "blob")
    val step = holdMs + morphMs
    val p by t.animateFloat(
        0f, shapes.size.toFloat(),
        infiniteRepeatable(tween(step * shapes.size, easing = LinearEasing)), label = "p",
    )
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "rot")
    Canvas(modifier) {
        val i = p.toInt().coerceIn(0, shapes.lastIndex)
        val local = ((p - i) * step - holdMs) / morphMs
        val eased = FastOutSlowInEasing.transform(local.coerceIn(0f, 1f))
        path.rewind()
        morphs[i].toPath(eased, path)
        val cp = path.asComposePath()
        val s = size.minDimension
        rotate(rot, pivot = center) {
            scale(s, s, pivot = Offset.Zero) {
                drawPath(cp, color)
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 18.dp)) {
            Row(
                Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapeBadge(icon, shape, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                trailing?.invoke()
            }
            Spacer(Modifier.padding(top = 12.dp))
            content()
        }
    }
}

/** Connected button group (M3 Expressive) for single choice. */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(label(option), maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Hero container color, tinted by the current conditions. */
@Composable
fun heroColors(sky: Sky, isDay: Boolean): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when {
        !isDay -> cs.secondaryContainer to cs.onSecondaryContainer
        sky == Sky.CLEAR || sky == Sky.PARTLY -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> cs.primaryContainer to cs.onPrimaryContainer
    }
}
