@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.Fmt
import kotlin.math.roundToInt

private val RainBlue = Color(0xFF4A9EFF)

enum class HourlyMetric(val label: String) { TEMP("Temp"), RAIN("Rain"), WIND("Wind"), UV("UV") }

@Composable
fun HourlyCard(f: Forecast, fmt: Fmt) {
    var metric by rememberSaveable { mutableStateOf(HourlyMetric.TEMP) }
    SectionCard("Next 48 hours", Icons.Rounded.Schedule, MaterialShapes.Cookie4Sided) {
        ConnectedChoice(
            options = HourlyMetric.entries,
            selected = metric,
            label = { it.label },
            onSelect = { metric = it },
            modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        HourlyGraph(f.upcoming, fmt, metric, firstIsNow = true)
    }
}

@Composable
fun HourlyGraph(hours: List<dev.vis.weather.data.Hour>, fmt: Fmt, metric: HourlyMetric, firstIsNow: Boolean) {
    if (hours.isEmpty()) return
    val colW = 58.dp
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = cs.onSurface, fontWeight = FontWeight.SemiBold)
    val subStyle = MaterialTheme.typography.labelMedium.copy(color = cs.onSurfaceVariant)
    val ring = cardBackdrop()
    val lineColor = cs.primary
    val trackColor = cs.surfaceContainerHighest

    val values: List<Double> = hours.map {
        when (metric) {
            HourlyMetric.TEMP -> fmt.tempValue(it.temp)
            HourlyMetric.RAIN -> it.precipProb.takeIf { v -> !v.isNaN() } ?: 0.0
            HourlyMetric.WIND -> fmt.windValue(it.windSpeed)
            HourlyMetric.UV -> it.uv.takeIf { v -> !v.isNaN() } ?: 0.0
        }
    }
    val animProgress by animateFloatAsState(1f, label = "graph")

    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        Box(Modifier.width(colW * hours.size).height(186.dp)) {
            // Columns: time + icon
            Row {
                hours.forEachIndexed { i, h ->
                    Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (i == 0 && firstIsNow) "Now" else fmt.hour(h.time),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (i == 0 && firstIsNow) cs.primary else cs.onSurfaceVariant,
                            fontWeight = if (i == 0 && firstIsNow) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(6.dp))
                        WeatherIcon(h.code, h.isDay, Modifier.size(30.dp))
                    }
                }
            }
            Canvas(Modifier.matchParentSize()) {
                val cw = colW.toPx()
                val chartTop = 90.dp.toPx()
                val chartBottom = 150.dp.toPx()
                val bottomTextY = 160.dp.toPx()

                val vMin = values.minOrNull() ?: 0.0
                val vMax = values.maxOrNull() ?: 1.0
                fun yFor(v: Double, lo: Double, hi: Double): Float {
                    val span = (hi - lo).takeIf { it > 0.0001 } ?: 1.0
                    return (chartBottom - ((v - lo) / span).toFloat() * (chartBottom - chartTop))
                }
                fun label(i: Int, text: String, y: Float, style: TextStyle = labelStyle) {
                    val m = measurer.measure(text, style)
                    drawText(m, topLeft = Offset(i * cw + cw / 2 - m.size.width / 2, y - m.size.height - 4.dp.toPx()))
                }
                fun bottom(i: Int, text: String, color: Color = subStyle.color) {
                    if (text.isEmpty()) return
                    val m = measurer.measure(text, subStyle.copy(color = color))
                    drawText(m, topLeft = Offset(i * cw + cw / 2 - m.size.width / 2, bottomTextY))
                }

                when (metric) {
                    HourlyMetric.TEMP, HourlyMetric.WIND -> {
                        val pts = values.mapIndexed { i, v ->
                            Offset(i * cw + cw / 2, yFor(v, vMin, vMax) * animProgress + chartBottom * (1 - animProgress))
                        }
                        val path = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (i in 1 until pts.size) {
                                val mx = (pts[i - 1].x + pts[i].x) / 2
                                cubicTo(mx, pts[i - 1].y, mx, pts[i].y, pts[i].x, pts[i].y)
                            }
                        }
                        val fill = Path().apply {
                            addPath(path)
                            lineTo(pts.last().x, chartBottom + 8.dp.toPx())
                            lineTo(pts.first().x, chartBottom + 8.dp.toPx())
                            close()
                        }
                        val colorTop = if (metric == HourlyMetric.TEMP) lineColor else Color(0xFF35C7B0)
                        drawPath(fill, Brush.verticalGradient(listOf(colorTop.copy(alpha = 0.28f), Color.Transparent), chartTop, chartBottom + 8.dp.toPx()))
                        drawPath(path, colorTop, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                        pts.forEachIndexed { i, p ->
                            val c = if (metric == HourlyMetric.TEMP) tempColor(hours[i].temp) else colorTop
                            drawCircle(ring, 6.dp.toPx(), p)
                            drawCircle(c, 4.dp.toPx(), p)
                            label(i, if (metric == HourlyMetric.TEMP) fmt.temp(hours[i].temp) else fmt.windNum(hours[i].windSpeed), p.y)
                            if (metric == HourlyMetric.TEMP) {
                                val pp = hours[i].precipProb
                                bottom(i, if (!pp.isNaN() && pp >= 10) "${pp.roundToInt()}%" else "", RainBlue)
                            } else {
                                // wind direction arrow (points where the wind is blowing to)
                                val c0 = Offset(i * cw + cw / 2, bottomTextY + 9.dp.toPx())
                                rotate(hours[i].windDir.toFloat() + 180f, pivot = c0) {
                                    val a = 7.dp.toPx()
                                    val arrow = Path().apply {
                                        moveTo(c0.x, c0.y - a)
                                        lineTo(c0.x + a * 0.7f, c0.y + a)
                                        lineTo(c0.x, c0.y + a * 0.45f)
                                        lineTo(c0.x - a * 0.7f, c0.y + a)
                                        close()
                                    }
                                    drawPath(arrow, subStyle.color)
                                }
                            }
                        }
                    }
                    HourlyMetric.RAIN, HourlyMetric.UV -> {
                        val hi = if (metric == HourlyMetric.RAIN) 100.0 else maxOf(vMax, 8.0)
                        values.forEachIndexed { i, v ->
                            val barW = cw * 0.42f
                            val x = i * cw + (cw - barW) / 2
                            drawRoundRect(trackColor, Offset(x, chartTop), Size(barW, chartBottom - chartTop), CornerRadius(barW / 2))
                            val y = yFor(v, 0.0, hi)
                            val h = (chartBottom - y) * animProgress
                            val color = if (metric == HourlyMetric.RAIN) RainBlue else uvColor(v)
                            if (h > 0.5f) {
                                drawRoundRect(color, Offset(x, chartBottom - maxOf(h, barW)), Size(barW, maxOf(h, barW)), CornerRadius(barW / 2))
                            }
                            label(i, if (metric == HourlyMetric.RAIN) "${v.roundToInt()}%" else v.roundToInt().toString(), chartTop)
                            if (metric == HourlyMetric.RAIN) {
                                val mm = hours[i].precip
                                bottom(i, if (!mm.isNaN() && mm > 0.0) fmt.precip(mm).substringBefore(' ') else "")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun uvColor(uv: Double): Color = when {
    uv < 3 -> Color(0xFF4CAF50)
    uv < 6 -> Color(0xFFFFC107)
    uv < 8 -> Color(0xFFFF9800)
    uv < 11 -> Color(0xFFF44336)
    else -> Color(0xFF9C27B0)
}

@Composable
fun DailyCard(f: Forecast, fmt: Fmt, onOpenDay: (Int) -> Unit) {
    val days = f.daily
    if (days.isEmpty()) return
    val lo = days.mapNotNull { it.min.takeIf { v -> !v.isNaN() } }.minOrNull() ?: 0.0
    val hi = days.mapNotNull { it.max.takeIf { v -> !v.isNaN() } }.maxOrNull() ?: 1.0
    val today = days.first().date

    SectionCard("${days.size}-day forecast", Icons.Rounded.CalendarMonth, MaterialShapes.Clover4Leaf) {
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            days.forEachIndexed { i, d ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenDay(i) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(76.dp)) {
                        Text(
                            fmt.dayName(d.date, today),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                        Text(fmt.shortDate(d.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    WeatherIcon(d.code, true, Modifier.size(30.dp))
                    Text(
                        if (!d.precipProbMax.isNaN() && d.precipProbMax >= 10) "${d.precipProbMax.roundToInt()}%" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = RainBlue,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(42.dp),
                    )
                    Text(
                        fmt.temp(d.min),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(34.dp),
                    )
                    RangeBar(
                        lo, hi, d.min, d.max,
                        current = if (i == 0) f.current.temp else null,
                        modifier = Modifier.weight(1f).height(8.dp).padding(horizontal = 8.dp),
                    )
                    Text(
                        fmt.temp(d.max),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp),
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 2)
        }
    }
}

@Composable
fun RangeBar(lo: Double, hi: Double, min: Double, max: Double, current: Double?, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val dotRing = cardBackdrop()
    val dot = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val h = size.height
        drawRoundRect(track, size = size, cornerRadius = CornerRadius(h / 2))
        if (min.isNaN() || max.isNaN()) return@Canvas
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val x0 = ((min - lo) / span * size.width).toFloat()
        val x1 = ((max - lo) / span * size.width).toFloat().coerceAtLeast(x0 + h)
        drawRoundRect(
            Brush.horizontalGradient(listOf(tempColor(min), tempColor(max)), x0, x1),
            topLeft = Offset(x0, 0f),
            size = Size(x1 - x0, h),
            cornerRadius = CornerRadius(h / 2),
        )
        if (current != null && !current.isNaN()) {
            val cx = ((current - lo) / span * size.width).toFloat().coerceIn(0f, size.width)
            drawCircle(dotRing, h * 0.85f, Offset(cx, h / 2))
            drawCircle(dot, h * 0.55f, Offset(cx, h / 2))
        }
    }
}

/** Minute-level (15 min) precipitation for the next 3 hours. Returns null if dry. */
fun nowcastText(f: Forecast, fmt: Fmt): String? {
    val mins = f.minutely.filter { it.time >= f.current.time - 900 }
    if (mins.isEmpty()) return null
    val wet = { p: Double -> !p.isNaN() && p >= 0.1 }
    val rainingNow = wet(mins.first().precip) || (!f.current.precipitation.isNaN() && f.current.precipitation >= 0.1)
    return if (rainingNow) {
        val stop = mins.indexOfFirst { !wet(it.precip) }
        if (stop > 0) "Precipitation should stop around ${fmt.time(mins[stop].time)}" else "Precipitation will continue for at least 3 hours"
    } else {
        val start = mins.indexOfFirst { wet(it.precip) }
        if (start >= 0) "Precipitation expected from around ${fmt.time(mins[start].time)}" else null
    }
}

@Composable
fun NowcastCard(f: Forecast, fmt: Fmt, text: String) {
    val mins = f.minutely.filter { it.time >= f.current.time - 900 }
    val maxP = maxOf(mins.maxOfOrNull { if (it.precip.isNaN()) 0.0 else it.precip } ?: 0.0, 1.0)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    SectionCard(text, Icons.Rounded.Umbrella, MaterialShapes.Pill) {
        Canvas(
            Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .height(56.dp),
        ) {
            val n = mins.size
            val gap = 4.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            mins.forEachIndexed { i, m ->
                val x = i * (bw + gap)
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(8.dp.toPx()))
                val p = if (m.precip.isNaN()) 0.0 else m.precip
                if (p > 0) {
                    val h = (p / maxP).toFloat().coerceIn(0.12f, 1f) * size.height
                    drawRoundRect(RainBlue, Offset(x, size.height - h), Size(bw, h), CornerRadius(8.dp.toPx()))
                }
            }
        }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            mins.lastOrNull()?.let {
                Text(fmt.time(it.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
