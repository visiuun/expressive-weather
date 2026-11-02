@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Rounded arrowhead with a notched back, pointing up, in a unit square. */
private val WindArrow: RoundedPolygon = RoundedPolygon(
    vertices = floatArrayOf(
        0.5f, 0.04f, // tip
        0.93f, 0.9f, // right
        0.5f, 0.72f, // notch
        0.07f, 0.9f, // left
    ),
    perVertexRounding = listOf(
        CornerRounding(0.07f),
        CornerRounding(0.08f),
        CornerRounding(0.06f),
        CornerRounding(0.08f),
    ),
)

/**
 * Wind dial: a single rounded M3 Expressive arrow shape pointing where the wind blows,
 * speed in the middle. When [heading] (device heading from true north) is known, the dial turns
 * with the phone so the arrow points the real-world direction and the "N" marker shows real north.
 */
@Composable
fun LiveCompass(
    windDir: Double,
    heading: Float?,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val arrowPath = remember { WindArrow.toPath().asComposePath() }
    val measurer = rememberTextMeasurer()
    val nStyle = MaterialTheme.typography.labelMedium.copy(color = cs.primary, fontWeight = FontWeight.Bold)
    val dial = cs.surfaceContainerHigh
    val shapeColor = cs.primaryContainer
    val onShape = cs.onPrimaryContainer
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2
            val ctr = Offset(size.width / 2, size.height / 2)
            val h = heading ?: 0f
            drawCircle(dial, r, ctr)
            if (!windDir.isNaN()) {
                val s = r * 1.62f
                rotate(windDir.toFloat() + 180f - h, pivot = ctr) {
                    translate(ctr.x - s / 2, ctr.y - s / 2) {
                        scale(s, s, pivot = Offset.Zero) { drawPath(arrowPath, shapeColor) }
                    }
                }
            }
            if (heading != null) {
                // Real north marker orbiting the rim.
                val m = measurer.measure("N", nStyle)
                val a = (-h) * PI / 180
                val rr = r - 12.dp.toPx()
                drawText(m, topLeft = Offset(ctr.x + rr * sin(a).toFloat() - m.size.width / 2, ctr.y - rr * cos(a).toFloat() - m.size.height / 2))
            }
        }
    }
}
