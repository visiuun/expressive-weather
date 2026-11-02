@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import dev.vis.weather.data.Sky
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.theme.LocalAppColors

private val SunColor = Color(0xFFFFB627)
private val MoonColor = Color(0xFFE8E4D8)
private val RainColor = Color(0xFF4A9EFF)
private val BoltColor = Color(0xFFFFD23F)
private val Moon = MoonColor

/**
 * Hand-drawn weather glyphs. The sun uses the M3 Expressive "Sunny" shape,
 * clouds are composed of overlapping circles.
 */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, modifier: Modifier = Modifier, animated: Boolean = false) {
    val app = LocalAppColors.current
    val dark = app.isDark
    val mono = app.monoIcons
    val ink = if (dark) Color.White else Color.Black
    val Sun = if (mono) ink else SunColor
    val Moon = if (mono) ink else MoonColor
    val Rain = if (mono) ink.copy(alpha = 0.85f) else RainColor
    val Bolt = if (mono) ink else BoltColor
    val sky = WeatherInfo.sky(code)
    val sunPath = MaterialShapes.Sunny.toPath()
    val rotation = if (animated) {
        val t = rememberInfiniteTransition(label = "sun")
        val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "rot")
        r
    } else 0f

    val cloudLight = when {
        mono -> if (dark) Color(0xFFBDBDBD) else Color(0xFF5C5C5C)
        dark -> Color(0xFFE6EBF2)
        else -> Color(0xFFB9C3CF)
    }
    val cloudDark = when {
        mono -> if (dark) Color(0xFF6E6E6E) else Color(0xFF9E9E9E)
        dark -> Color(0xFFA9B4C2)
        else -> Color(0xFF8C98A6)
    }
    val snow = if (mono) ink else if (dark) Color(0xFFDDF1FF) else Color(0xFF7DB8E8)

    Canvas(modifier) {
        val s = size.minDimension
        when (sky) {
            Sky.CLEAR -> if (isDay) drawSun(sunPath, Offset(0f, 0f), s, rotation, Sun) else drawMoon(Offset(s * 0.12f, s * 0.12f), s * 0.76f, Moon)
            Sky.PARTLY -> {
                if (isDay) drawSun(sunPath, Offset(s * 0.28f, 0f), s * 0.68f, rotation, Sun)
                else drawMoon(Offset(s * 0.38f, s * 0.06f), s * 0.5f, Moon)
                drawCloud(Offset(0f, s * 0.32f), s * 0.82f, cloudLight)
            }
            Sky.CLOUDY -> {
                drawCloud(Offset(s * 0.22f, s * 0.12f), s * 0.72f, cloudDark)
                drawCloud(Offset(0f, s * 0.3f), s * 0.86f, cloudLight)
            }
            Sky.FOG -> {
                drawCloud(Offset(s * 0.08f, s * 0.04f), s * 0.84f, cloudLight)
                val w = s * 0.07f
                for (i in 0..2) {
                    val y = s * (0.72f + i * 0.11f)
                    val x0 = s * (0.12f + (i % 2) * 0.08f)
                    drawLine(cloudDark, Offset(x0, y), Offset(x0 + s * 0.68f, y), w, StrokeCap.Round)
                }
            }
            Sky.DRIZZLE, Sky.RAIN, Sky.FREEZING -> {
                drawCloud(Offset(s * 0.06f, 0f), s * 0.88f, if (sky == Sky.RAIN) cloudDark else cloudLight)
                val heavy = code in listOf(65, 82, 63, 81)
                val n = if (sky == Sky.DRIZZLE) 3 else if (heavy) 4 else 3
                for (i in 0 until n) {
                    val x = s * (0.26f + i * (0.5f / (n - 1).coerceAtLeast(1)))
                    val y = s * 0.66f + (i % 2) * s * 0.06f
                    if (sky == Sky.DRIZZLE) {
                        drawCircle(Rain, s * 0.045f, Offset(x, y + s * 0.06f))
                    } else {
                        drawLine(Rain, Offset(x, y), Offset(x - s * 0.06f, y + s * 0.2f), s * 0.07f, StrokeCap.Round)
                    }
                }
                if (sky == Sky.FREEZING) drawFlake(Offset(s * 0.78f, s * 0.86f), s * 0.11f, snow)
            }
            Sky.SNOW -> {
                drawCloud(Offset(s * 0.06f, 0f), s * 0.88f, cloudLight)
                drawFlake(Offset(s * 0.3f, s * 0.78f), s * 0.11f, snow)
                drawFlake(Offset(s * 0.56f, s * 0.86f), s * 0.11f, snow)
                drawFlake(Offset(s * 0.8f, s * 0.74f), s * 0.09f, snow)
            }
            Sky.THUNDER -> {
                drawCloud(Offset(s * 0.06f, 0f), s * 0.88f, cloudDark)
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.5f)
                    lineTo(s * 0.36f, s * 0.78f)
                    lineTo(s * 0.5f, s * 0.78f)
                    lineTo(s * 0.42f, s * 1.0f)
                    lineTo(s * 0.68f, s * 0.68f)
                    lineTo(s * 0.54f, s * 0.68f)
                    lineTo(s * 0.64f, s * 0.5f)
                    close()
                }
                drawPath(bolt, Bolt)
                drawLine(Rain, Offset(s * 0.26f, s * 0.7f), Offset(s * 0.21f, s * 0.86f), s * 0.06f, StrokeCap.Round)
                drawLine(Rain, Offset(s * 0.8f, s * 0.68f), Offset(s * 0.75f, s * 0.84f), s * 0.06f, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawSun(path: Path, topLeft: Offset, size: Float, rotation: Float, Sun: Color) {
    translate(topLeft.x, topLeft.y) {
        rotate(rotation, pivot = Offset(size / 2, size / 2)) {
            scale(size, size, pivot = Offset.Zero) { drawPath(path, Sun) }
        }
    }
}

private fun DrawScope.drawMoon(topLeft: Offset, size: Float, Moon: Color) {
    val full = Path().apply { addOval(Rect(topLeft, Size(size, size))) }
    val cut = Path().apply { addOval(Rect(Offset(topLeft.x + size * 0.38f, topLeft.y - size * 0.18f), Size(size, size))) }
    val crescent = Path().apply { op(full, cut, PathOperation.Difference) }
    drawPath(crescent, Moon)
}

private fun DrawScope.drawCloud(topLeft: Offset, w: Float, color: Color) {
    val h = w * 0.62f
    translate(topLeft.x, topLeft.y) {
        drawCircle(color, h * 0.36f, Offset(w * 0.28f, h * 0.6f))
        drawCircle(color, h * 0.48f, Offset(w * 0.55f, h * 0.48f))
        drawCircle(color, h * 0.32f, Offset(w * 0.8f, h * 0.66f))
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.1f, h * 0.6f),
            size = Size(w * 0.82f, h * 0.38f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.19f),
        )
    }
}

private fun DrawScope.drawFlake(c: Offset, r: Float, color: Color) {
    for (i in 0 until 3) {
        rotate(i * 60f, pivot = c) {
            drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), r * 0.32f, StrokeCap.Round)
        }
    }
}

/** Moon phase disc. phase: 0 = new, 0.5 = full. */
@Composable
fun MoonPhase(phase: Double, southern: Boolean, modifier: Modifier = Modifier) {
    val shadow = if (LocalAppColors.current.isDark) Color(0xFF2A2C33) else Color(0xFFCFD3DB)
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(shadow, r, c)
        val k = kotlin.math.cos(2 * Math.PI * phase).toFloat() // 1 new, -1 full
        val waxing = phase < 0.5
        val rx = r * kotlin.math.abs(k)
        val outer = Rect(c.x - r, c.y - r, c.x + r, c.y + r)
        val ell = Rect(c.x - rx, c.y - r, c.x + rx, c.y + r)
        val p = Path().apply {
            if (waxing) {
                moveTo(c.x, c.y - r)
                arcTo(outer, -90f, 180f, false)
                arcTo(ell, 90f, if (k > 0) -180f else 180f, false)
            } else {
                moveTo(c.x, c.y + r)
                arcTo(outer, 90f, 180f, false)
                arcTo(ell, -90f, if (k > 0) -180f else 180f, false)
            }
            close()
        }
        scale(if (southern) -1f else 1f, 1f, pivot = c) {
            drawPath(p, Moon)
        }
        drawCircle(Moon.copy(alpha = 0.35f), r, c, style = Stroke(r * 0.04f))
    }
}
