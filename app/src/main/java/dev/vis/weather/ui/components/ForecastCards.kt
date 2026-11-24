@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import dev.vis.weather.L
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import dev.vis.weather.R
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.Fmt
import kotlin.math.roundToInt

private val RainBlue = Color(0xFF4A9EFF)

enum class HourlyMetric(@StringRes private val res: Int) {
    TEMP(R.string.m_hourly_temp), RAIN(R.string.m_hourly_rain), WIND(R.string.m_hourly_wind), UV(R.string.m_hourly_uv);

    val label: String get() = L.s(res)
}

@Composable
fun HourlyCard(f: Forecast, fmt: Fmt, style: dev.vis.weather.data.HourlyStyle = dev.vis.weather.data.HourlyStyle.STANDARD) {
    var metric by rememberSaveable { mutableStateOf(HourlyMetric.TEMP) }
    if (style != dev.vis.weather.data.HourlyStyle.STANDARD) {
        SectionCard(stringResource(R.string.m_next_48_hours), Icons.Rounded.Schedule, MaterialShapes.Cookie4Sided) {
            HourlyStrip(f, fmt, cards = style == dev.vis.weather.data.HourlyStyle.CARDS)
        }
        return
    }
    SectionCard(stringResource(R.string.m_next_48_hours), Icons.Rounded.Schedule, MaterialShapes.Cookie4Sided) {
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
    // Gusts share the wind scale so the gap between the two lines reads as gustiness.
    val gusts: List<Double>? = if (metric == HourlyMetric.WIND) hours.map { fmt.windValue(it.windGust) }.takeIf { g -> g.any { !it.isNaN() } } else null
    val gustColor = Color(0xFF35C7B0).copy(alpha = 0.6f)
    val animProgress by animateFloatAsState(1f, label = "graph")

    Column {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        Box(Modifier.width(colW * hours.size).height(if (gusts != null) 204.dp else 186.dp)) {
            // Columns: time + icon
            Row {
                hours.forEachIndexed { i, h ->
                    Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (i == 0 && firstIsNow) stringResource(R.string.m_now) else fmt.hour(h.time),
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
                val vMax = maxOf(values.maxOrNull() ?: 1.0, gusts?.filter { !it.isNaN() }?.maxOrNull() ?: Double.NEGATIVE_INFINITY)
                fun yFor(v: Double, lo: Double, hi: Double): Float {
                    val span = (hi - lo).takeIf { it > 0.0001 } ?: 1.0
                    return (chartBottom - ((v - lo) / span).toFloat() * (chartBottom - chartTop))
                }
                fun label(i: Int, text: String, y: Float, style: TextStyle = labelStyle) {
                    val m = measurer.measure(text, style)
                    val tl = Offset(i * cw + cw / 2 - m.size.width / 2, y - m.size.height - 4.dp.toPx())
                    // Keep wind numbers readable where the dashed gust line runs behind them.
                    if (gusts != null) {
                        val pad = 3.dp.toPx()
                        drawRoundRect(ring, Offset(tl.x - pad, tl.y), Size(m.size.width + pad * 2, m.size.height.toFloat()), CornerRadius(m.size.height / 2f))
                    }
                    drawText(m, topLeft = tl)
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
                        if (gusts != null) {
                            val gp = Path()
                            var started = false
                            gusts.forEachIndexed { i, g ->
                                if (g.isNaN()) { started = false; return@forEachIndexed }
                                val p = Offset(i * cw + cw / 2, yFor(g, vMin, vMax) * animProgress + chartBottom * (1 - animProgress))
                                if (!started) gp.moveTo(p.x, p.y) else gp.lineTo(p.x, p.y)
                                started = true
                            }
                            drawPath(
                                gp, gustColor,
                                style = Stroke(
                                    2.dp.toPx(), cap = StrokeCap.Round,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                                ),
                            )
                        }
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
                                gusts?.getOrNull(i)?.takeIf { !it.isNaN() }?.let { g ->
                                    val m = measurer.measure(g.roundToInt().toString(), subStyle.copy(color = gustColor.copy(alpha = 1f)))
                                    drawText(m, topLeft = Offset(i * cw + cw / 2 - m.size.width / 2, bottomTextY + 22.dp.toPx()))
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
    if (gusts != null) {
        Row(
            Modifier.padding(horizontal = 18.dp).padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(width = 14.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF35C7B0)))
            Text(stringResource(R.string.graph_wind_legend, fmt.windUnit), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { Box(Modifier.size(width = 5.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(gustColor)) }
            }
            Text(stringResource(R.string.graph_gust_legend), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
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
fun DailyCard(f: Forecast, fmt: Fmt, onOpenDay: (Int) -> Unit, maxDays: Int = 16) {
    val days = f.daily.take(maxDays.coerceAtLeast(1))
    if (days.isEmpty()) return
    val lo = days.mapNotNull { it.min.takeIf { v -> !v.isNaN() } }.minOrNull() ?: 0.0
    val hi = days.mapNotNull { it.max.takeIf { v -> !v.isNaN() } }.maxOrNull() ?: 1.0
    val today = days.first().date

    SectionCard(pluralStringResource(R.plurals.m_daily_forecast_title, days.size, days.size), Icons.Rounded.CalendarMonth, MaterialShapes.Clover4Leaf) {
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
        if (stop > 0) L.s(R.string.m_nowcast_stop, fmt.time(mins[stop].time)) else L.s(R.string.m_nowcast_continue)
    } else {
        val start = mins.indexOfFirst { wet(it.precip) }
        if (start >= 0) L.s(R.string.m_nowcast_start, fmt.time(mins[start].time)) else null
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
            Text(stringResource(R.string.m_now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            mins.lastOrNull()?.let {
                Text(fmt.time(it.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


/** Compact / card layouts for the hourly forecast (Settings → Layout). */
@Composable
private fun HourlyStrip(f: Forecast, fmt: Fmt, cards: Boolean) {
    val hours = f.upcoming
    val lo = hours.mapNotNull { it.temp.takeIf { v -> !v.isNaN() } }.minOrNull() ?: 0.0
    val hi = hours.mapNotNull { it.temp.takeIf { v -> !v.isNaN() } }.maxOrNull() ?: 1.0
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(if (cards) 8.dp else 2.dp),
    ) {
        hours.forEachIndexed { i, h ->
            val now = i == 0
            val content: @Composable () -> Unit = {
                Column(
                    Modifier.width(if (cards) 64.dp else 52.dp).padding(vertical = if (cards) 12.dp else 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (now) stringResource(R.string.m_now) else fmt.hour(h.time), style = MaterialTheme.typography.labelMedium,
                        color = if (now && cards) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    WeatherIcon(h.code, h.isDay, Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        fmt.temp(h.temp), style = MaterialTheme.typography.titleMediumEmphasized,
                        color = if (now && cards) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (!cards) {
                        // A small thermometer bar so compact mode still reads as a trend.
                        val frac = if (hi - lo < 0.1) 0.5f else ((h.temp - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
                        Box(Modifier.padding(top = 4.dp).width(6.dp).height(36.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            Box(
                                Modifier.align(Alignment.BottomCenter).width(6.dp).height((8 + 28 * frac).dp)
                                    .clip(RoundedCornerShape(3.dp)).background(tempColor(h.temp)),
                            )
                        }
                    }
                    if (!h.precipProb.isNaN() && h.precipProb >= 10) {
                        Text(
                            "${h.precipProb.roundToInt()}%", style = MaterialTheme.typography.labelSmall,
                            color = if (now && cards) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (cards) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (now) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) { content() }
            } else content()
        }
    }
}
