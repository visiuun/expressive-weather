@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.detail

import dev.vis.weather.inSentence
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sailing
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vis.weather.L
import dev.vis.weather.R
import androidx.compose.ui.unit.sp
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Marine
import dev.vis.weather.data.MarineHour
import dev.vis.weather.data.MarineInfo
import dev.vis.weather.data.SeaActivity
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.components.ConnectedChoice
import dev.vis.weather.ui.components.MorphingBlob
import dev.vis.weather.ui.components.SectionCard
import dev.vis.weather.ui.components.cardBackdrop
import kotlin.math.roundToInt

private fun Double.ok() = !isNaN()

fun seaColor(level: Int): Color = Color(MarineInfo.seaColor(level))

/** Marine hour for "now". */
fun Marine.now(f: Forecast): MarineHour? = at(f.current.time)

@Composable
fun SeaHero(f: Forecast, fmt: Fmt) {
    val m = f.marine ?: return
    val h = m.now(f)
    val state = MarineInfo.seaState(h?.waveHeight ?: Double.NaN)
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            MorphingBlob(
                listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Clover8Leaf, MaterialShapes.Flower),
                seaColor(state.level).copy(alpha = 0.32f),
                Modifier.matchParentSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt.waveNum(h?.waveHeight ?: Double.NaN), style = MaterialTheme.typography.displayLargeEmphasized)
                Text(stringResource(R.string.dt_sea_unit_waves, fmt.waveUnit), style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(state.label, style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        val parts = listOfNotNull(
            state.description.takeIf { it.isNotEmpty() },
            h?.waveDir?.takeIf { it.ok() }?.let { stringResource(R.string.dt_sea_waves_from, WeatherInfo.compass(it)) },
            h?.seaTemp?.takeIf { it.ok() }?.let { stringResource(R.string.dt_sea_water_temp, fmt.temp(it)) },
        )
        Text(parts.joinToString(" "), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun SeaSections(f: Forecast, fmt: Fmt) {
    val m = f.marine ?: return
    val now = m.now(f)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (m.hasWaves) WaveChartCard(f, m, fmt, now)
        if (m.hasWaves) WaveOutlook(f, m, fmt)
        if (now?.seaTemp?.ok() == true) SeaTempCard(f, m, fmt, now)
        if (now?.currentSpeed?.ok() == true) CurrentCard(now, fmt)
        if (m.hasTides) TideCard(f, m, fmt)
        if (now != null) ActivityCard(f, now)
    }
}

// ---- waves

@Composable
private fun WaveChartCard(f: Forecast, m: Marine, fmt: Fmt, now: MarineHour?) {
    val hours = remember(m, f.current.time) { m.hourly.filter { it.time > f.current.time - 3600 }.take(24) }
    if (hours.isEmpty()) return
    val swellC = MaterialTheme.colorScheme.primary
    val windC = MaterialTheme.colorScheme.tertiary
    SectionCard(pluralStringResource(R.plurals.dt_next_hours, 24, 24), Icons.Rounded.Schedule, MaterialShapes.Cookie4Sided) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Swatch(swellC, stringResource(R.string.dt_swell))
            Spacer(Modifier.width(16.dp))
            Swatch(windC, stringResource(R.string.dt_wind_waves))
        }
        Gap(8.dp)
        WaveBars(hours, fmt, swellC, windC)
        if (now != null && (now.swellPeriod.ok() || now.wavePeriod.ok())) {
            val p = if (now.swellPeriod.ok()) now.swellPeriod else now.wavePeriod
            Gap()
            StatChips(
                listOf(
                    Triple(stringResource(R.string.dt_wave_period), stringResource(R.string.dt_seconds_short, p.roundToInt()), MarineInfo.swellQuality(p)),
                    Triple(stringResource(R.string.dt_swell), fmt.wave(now.swellHeight), now.swellDir.takeIf { it.ok() }?.let { stringResource(R.string.dt_from_dir, WeatherInfo.compass(it)) }),
                    Triple(stringResource(R.string.dt_wind_waves), fmt.wave(now.windWaveHeight), now.windWavePeriod.takeIf { it.ok() }?.let { stringResource(R.string.dt_seconds_short, it.roundToInt()) }),
                )
            )
            Gap(10.dp)
            Body(MarineInfo.periodExplainer(p), Modifier.padding(horizontal = 18.dp))
        }
    }
}

@Composable
private fun Swatch(c: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) { drawRoundRect(c, cornerRadius = CornerRadius(3.dp.toPx())) }
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WaveBars(hours: List<MarineHour>, fmt: Fmt, swellC: Color, windC: Color) {
    val colW = 44.dp
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = cs.onSurface, fontWeight = FontWeight.SemiBold)
    val timeStyle = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val nowStyle = timeStyle.copy(color = cs.primary, fontWeight = FontWeight.Bold)
    val track = cs.surfaceContainerHighest
    val nowLabel = stringResource(R.string.dt_now)
    val hi = maxOf(hours.maxOf { maxOf(if (it.waveHeight.ok()) it.waveHeight else 0.0, (if (it.swellHeight.ok()) it.swellHeight else 0.0) + (if (it.windWaveHeight.ok()) it.windWaveHeight else 0.0)) }, 1.0)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        Canvas(Modifier.width(colW * hours.size).height(150.dp)) {
            val cw = colW.toPx()
            val top = 22.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            val bw = cw * 0.46f
            hours.forEachIndexed { i, h ->
                val x = i * cw + (cw - bw) / 2
                drawRoundRect(track, Offset(x, top), Size(bw, bottom - top), CornerRadius(bw / 2))
                val swell = if (h.swellHeight.ok()) h.swellHeight else 0.0
                val wind = if (h.windWaveHeight.ok()) h.windWaveHeight else if (h.waveHeight.ok()) (h.waveHeight - swell).coerceAtLeast(0.0) else 0.0
                val total = swell + wind
                if (total > 0) {
                    val full = (bottom - top)
                    val hs = (swell / hi * full).toFloat()
                    val hw = (wind / hi * full).toFloat()
                    val totalH = maxOf(hs + hw, bw)
                    // Stack: swell at the bottom, wind waves on top, one rounded pill overall.
                    drawRoundRect(windC, Offset(x, bottom - totalH), Size(bw, totalH), CornerRadius(bw / 2))
                    if (hs > 0) {
                        val sh = hs.coerceIn(minOf(bw, totalH), totalH)
                        drawRoundRect(swellC, Offset(x, bottom - sh), Size(bw, sh), CornerRadius(bw / 2))
                    }
                }
                if (i % 3 == 0) {
                    val wv = if (h.waveHeight.ok()) h.waveHeight else total
                    val ml = measurer.measure(fmt.waveNum(wv), labelStyle)
                    drawText(ml, topLeft = Offset(i * cw + cw / 2 - ml.size.width / 2, 0f))
                    val mt = measurer.measure(if (i == 0) nowLabel else fmt.hour(h.time), if (i == 0) nowStyle else timeStyle)
                    drawText(mt, topLeft = Offset(i * cw + cw / 2 - mt.size.width / 2, bottom + 4.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun WaveOutlook(f: Forecast, m: Marine, fmt: Fmt) {
    val days = m.daily.filter { it.waveMax.ok() }.take(7)
    if (days.isEmpty()) return
    val today = fmt.localDate(f.current.time)
    val roughest = days.maxBy { it.waveMax }
    val calmest = days.minBy { it.waveMax }
    val headline = when {
        roughest.waveMax - calmest.waveMax < 0.3 -> stringResource(R.string.dt_wave_similar)
        else -> stringResource(R.string.dt_wave_roughest, dayWord(roughest.date, today, fmt))
    }
    val body = if (roughest.waveMax - calmest.waveMax < 0.3) stringResource(R.string.dt_wave_stay_around, fmt.wave(roughest.waveMax))
    else stringResource(
        R.string.dt_wave_outlook_body,
        fmt.wave(roughest.waveMax), MarineInfo.seaState(roughest.waveMax).label.inSentence(),
        dayWord(calmest.date, today, fmt), fmt.wave(calmest.waveMax),
    )
    val hi = days.maxOf { it.waveMax }.coerceAtLeast(1.0)
    InsightCard(stringResource(R.string.dt_wave_outlook), Icons.Rounded.CalendarMonth, MaterialShapes.Clover4Leaf, headline, body) {
        Gap()
        OutlookList(
            days.map { d ->
                val st = MarineInfo.seaState(d.waveMax)
                OutlookItem(fmt.dayName(d.date, today), f.daily.firstOrNull { it.date == d.date }?.code, st.label, fmt.wave(d.waveMax), seaColor(st.level), (d.waveMax / hi).toFloat())
            }
        )
        if (m.daily.size > days.size) {
            Gap(8.dp)
            Body(pluralStringResource(R.plurals.dt_wave_reach, days.size, days.size), Modifier.padding(horizontal = 18.dp))
        }
    }
}

private fun dayWord(d: java.time.LocalDate, today: java.time.LocalDate, fmt: Fmt): String = when (d) {
    today -> L.s(R.string.dt_today_lc)
    today.plusDays(1) -> L.s(R.string.dt_tomorrow_lc)
    else -> L.s(R.string.dt_on_weekday, d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()))
}

// ---- sea temperature

@Composable
private fun SeaTempCard(f: Forecast, m: Marine, fmt: Fmt, now: MarineHour) {
    val sst = now.seaTemp
    val week = m.daily.map { it.seaTempAvg }.filter { it.ok() }
    val trend = if (week.size >= 3) week.last() - week.first() else Double.NaN
    InsightCard(
        stringResource(R.string.dt_sea_temperature), Icons.Rounded.Thermostat, MaterialShapes.Cookie7Sided,
        stringResource(R.string.dt_dot_pair, fmt.temp(sst), MarineInfo.waterFeel(sst)),
        if (sst < 15) stringResource(R.string.dt_sea_cold_shock, MarineInfo.wetsuit(sst)) else MarineInfo.wetsuit(sst),
    ) {
        Gap()
        StatChips(
            listOfNotNull(
                Triple(stringResource(R.string.dt_air_now), fmt.temp(f.current.temp), null),
                Triple(stringResource(R.string.dt_water_now), fmt.temp(sst), null),
                if (trend.ok()) Triple(
                    stringResource(R.string.dt_next_week), fmt.tempDelta(trend),
                    stringResource(if (kotlin.math.abs(trend) < 0.5) R.string.dt_trend_steady else if (trend > 0) R.string.dt_trend_warming else R.string.dt_trend_cooling),
                ) else null,
            )
        )
    }
}

// ---- currents

@Composable
private fun CurrentCard(now: MarineHour, fmt: Fmt) {
    val info = MarineInfo.current(now.currentSpeed)
    InsightCard(
        stringResource(R.string.dt_currents), Icons.Rounded.Explore, MaterialShapes.Gem,
        stringResource(R.string.dt_dot_pair, info.label, fmt.wind(now.currentSpeed)),
        listOfNotNull(
            now.currentDir.takeIf { it.ok() }?.let { stringResource(R.string.dt_current_flow, WeatherInfo.compass(it)) },
            info.text,
        ).joinToString(" "),
        accent = if (info.caution) Color(0xFFFF9800) else Color(0xFF35C7B0),
    ) {
        if (info.caution) {
            Gap(10.dp)
            Note(stringResource(R.string.dt_current_caution), icon = Icons.Rounded.Warning)
        }
    }
}

// ---- tides

@Composable
private fun TideCard(f: Forecast, m: Marine, fmt: Fmt) {
    val hours = remember(m, f.current.time) { m.hourly.filter { it.time >= f.current.time - 3 * 3600 && it.seaLevel.ok() }.take(48) }
    if (hours.size < 6) return
    val extremes = remember(hours) { MarineInfo.tides(hours) }
    val next = extremes.firstOrNull { it.time > f.current.time }
    val now = f.current.time
    val rising = hours.zipWithNext().firstOrNull { (a, b) -> now in a.time..b.time }?.let { (a, b) -> b.seaLevel > a.seaLevel }
    val headline = when {
        next == null -> stringResource(R.string.dt_tides)
        next.high -> stringResource(R.string.dt_high_tide, fmt.time(next.time))
        else -> stringResource(R.string.dt_low_tide, fmt.time(next.time))
    }
    val rangeText = stringResource(R.string.dt_tide_range, fmt.wave(MarineInfo.tideRange(hours)))
    val body = listOfNotNull(
        rising?.let { stringResource(if (it) R.string.dt_tide_coming_in else R.string.dt_tide_going_out) },
        rangeText,
    ).joinToString(" ")
    InsightCard(stringResource(R.string.dt_tides), Icons.Rounded.WaterDrop, MaterialShapes.Pill, headline, body) {
        Gap()
        TideChart(hours, extremes, now, fmt)
    }
}

@Composable
private fun TideChart(hours: List<MarineHour>, extremes: List<dev.vis.weather.data.TideExtreme>, now: Long, fmt: Fmt) {
    val cs = MaterialTheme.colorScheme
    val line = cs.primary
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelSmall.copy(color = cs.onSurface, fontWeight = FontWeight.SemiBold)
    val sub = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant, fontSize = 10.sp)
    val ring = cardBackdrop()
    val colW = 22.dp
    val nowLabel = stringResource(R.string.dt_now)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        Canvas(Modifier.width(colW * hours.size + 40.dp).height(170.dp)) {
            val cw = colW.toPx()
            val pad = 20.dp.toPx()
            val top = 36.dp.toPx()
            val bottom = size.height - 40.dp.toPx()
            val lo = hours.minOf { it.seaLevel }
            val hi = hours.maxOf { it.seaLevel }
            val span = (hi - lo).coerceAtLeast(0.1)
            val t0 = hours.first().time
            fun x(t: Long) = pad + ((t - t0) / 3600f) * cw
            fun y(v: Double) = bottom - ((v - lo) / span).toFloat() * (bottom - top)
            val pts = hours.map { Offset(x(it.time), y(it.seaLevel)) }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val mx = (pts[i - 1].x + pts[i].x) / 2
                    cubicTo(mx, pts[i - 1].y, mx, pts[i].y, pts[i].x, pts[i].y)
                }
            }
            val fill = Path().apply { addPath(path); lineTo(pts.last().x, size.height - 30.dp.toPx()); lineTo(pts.first().x, size.height - 30.dp.toPx()); close() }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), Color.Transparent), top, size.height))
            drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            // Now marker
            if (now in t0..hours.last().time) {
                val nx = x(now)
                drawLine(cs.outline, Offset(nx, top - 10.dp.toPx()), Offset(nx, bottom + 6.dp.toPx()), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                val mt = measurer.measure(nowLabel, label.copy(color = cs.primary))
                drawText(mt, topLeft = Offset(nx - mt.size.width / 2, size.height - mt.size.height))
            }
            extremes.forEach { e ->
                val p = Offset(x(e.time), y(e.height))
                drawCircle(ring, 7.dp.toPx(), p)
                drawCircle(if (e.high) line else cs.tertiary, 5.dp.toPx(), p)
                val t1 = measurer.measure(fmt.time(e.time), label)
                val t2 = measurer.measure(L.s(if (e.high) R.string.dt_tide_high_short else R.string.dt_tide_low_short, fmt.tide(e.height)), sub)
                val above = e.high
                val ty = if (above) p.y - 10.dp.toPx() - t1.size.height - t2.size.height else p.y + 10.dp.toPx()
                drawText(t1, topLeft = Offset(p.x - t1.size.width / 2, ty))
                drawText(t2, topLeft = Offset(p.x - t2.size.width / 2, ty + t1.size.height))
            }
        }
    }
}

// ---- activities

@Composable
private fun ActivityCard(f: Forecast, now: MarineHour) {
    var activity by rememberSaveable { mutableStateOf(SeaActivity.GENERAL) }
    SectionCard(stringResource(R.string.dt_activities), Icons.Rounded.Sailing, MaterialShapes.Slanted) {
        ConnectedChoice(
            SeaActivity.entries, activity, { it.label }, { activity = it },
            Modifier.padding(horizontal = 18.dp).fillMaxWidth(),
        )
        Gap()
        val inSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val outSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(activity, transitionSpec = { fadeIn(inSpec) togetherWith fadeOut(outSpec) }, label = "act") { a ->
            Text(
                MarineInfo.guidance(a, now, f.current.windSpeed, f.current.windGust),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth(),
            )
        }
        Gap()
        Note(MarineInfo.DISCLAIMER, icon = Icons.Rounded.Waves)
    }
}
