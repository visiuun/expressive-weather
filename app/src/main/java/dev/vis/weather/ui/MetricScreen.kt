@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Settings
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.components.LiveCompass
import dev.vis.weather.ui.components.Metric
import dev.vis.weather.ui.components.MoonPhase
import dev.vis.weather.ui.components.MorphingBlob
import dev.vis.weather.ui.components.SectionCard
import dev.vis.weather.ui.components.WaterFill
import dev.vis.weather.ui.components.WeatherIcon
import dev.vis.weather.ui.components.cardBackdrop
import dev.vis.weather.ui.components.rememberHeading
import dev.vis.weather.ui.components.rememberSlosh
import dev.vis.weather.ui.components.uvColor
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sin

private val Blue = Color(0xFF4A9EFF)
private val Teal = Color(0xFF35C7B0)
private val Amber = Color(0xFFFFB627)
private val Slate = Color(0xFF9AA6B5)

private val POLLEN_NAMES = listOf("Alder", "Birch", "Grass", "Mugwort", "Olive", "Ragweed")

private fun Double.ok() = !isNaN()
private fun List<Double>.cleanMin() = filter { it.ok() }.minOrNull() ?: Double.NaN
private fun List<Double>.cleanMax() = filter { it.ok() }.maxOrNull() ?: Double.NaN

private fun aqiColor(v: Double) = if (v.isNaN()) Slate else Color(WeatherInfo.usAqi(v).color)
private fun pollenColor(v: Double): Color {
    val f = WeatherInfo.pollenLevel(if (v.isNaN()) 0.0 else v).second
    return when {
        f <= 0.25f -> Color(0xFF4CAF50)
        f <= 0.5f -> Color(0xFFFFC107)
        f <= 0.75f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}

/** One plotted series over time. */
private class Series(
    val times: List<Long>,
    val icons: List<Pair<Int, Boolean>>?,
    val values: List<Double>,
    val label: (Double) -> String,
    val bars: Boolean,
    val color: Color,
    val lo: Double? = null,
    val hi: Double? = null,
    val second: List<Double>? = null,
    val secondName: String? = null,
    val barColor: ((Double) -> Color)? = null,
    val bottom: ((Int) -> String)? = null,
    val bottomColor: Color? = null,
)

/** A day row: bar from [min] to [max] on the shared scale. */
private class DayRange(val date: LocalDate, val code: Int?, val min: Double, val max: Double, val minText: String, val maxText: String, val color: Color, val extra: String = "")

@Composable
fun MetricScreen(f: Forecast, settings: Settings, metric: Metric, onBack: () -> Unit) {
    val fmt = remember(settings, f.zone) { Fmt(settings, f.zone) }
    val series = remember(f, settings, metric) { seriesFor(f, fmt, metric) }
    val days = remember(f, settings, metric) { daysFor(f, fmt, metric) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(12.dp))
            Text(metric.title, style = MaterialTheme.typography.headlineSmallEmphasized)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val w = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            item(key = "hero") { Box(w) { Hero(f, fmt, metric) } }
            if (series != null && series.values.isNotEmpty()) {
                item(key = "chart") {
                    Box(w) {
                        SectionCard("Next ${series.values.size} hours", Icons.Rounded.Schedule, MaterialShapes.Cookie4Sided) {
                            if (series.secondName != null) {
                                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Legend(series.color, metric.title, dashed = false)
                                    Spacer(Modifier.width(16.dp))
                                    Legend(series.color.copy(alpha = 0.6f), series.secondName, dashed = true)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Chart(series, fmt)
                        }
                    }
                }
            }
            if (days.isNotEmpty()) {
                item(key = "days") {
                    Box(w) {
                        SectionCard("Next ${days.size} days", Icons.Rounded.CalendarMonth, MaterialShapes.Clover4Leaf) {
                            DayRanges(days, fmt)
                        }
                    }
                }
            }
            when (metric) {
                Metric.SUN -> item(key = "sun") { Box(w) { SunTable(f, fmt) } }
                Metric.MOON -> item(key = "moon") { Box(w) { MoonCalendar(f, fmt) } }
                Metric.AIR -> f.air?.let { a -> item(key = "pol") { Box(w) { Pollutants(a) } } }
                Metric.POLLEN -> f.air?.let { a -> item(key = "pl") { Box(w) { PollenTypes(a) } } }
                else -> {}
            }
            item(key = "about") {
                Box(w) {
                    SectionCard("About ${metric.title.lowercase()}", Icons.Rounded.Info, MaterialShapes.Circle) {
                        Text(
                            about(metric),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                        scale(metric).takeIf { it.isNotEmpty() }?.let { rows ->
                            Spacer(Modifier.height(12.dp))
                            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                rows.forEach { (c, label, text) ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(c))
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(label, style = MaterialTheme.typography.labelLarge)
                                            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun Legend(c: Color, text: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(22.dp, 10.dp)) {
            drawLine(
                c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- hero visuals

@Composable
private fun BigNumber(value: String, unit: String, caption: String, sub: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (value.isNotEmpty()) Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displayLargeEmphasized.copy(fontSize = 72.sp, lineHeight = 76.sp))
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                )
            }
        }
        if (caption.isNotEmpty()) Text(caption, style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BlobHero(shapes: List<RoundedPolygon>, color: Color, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            MorphingBlob(shapes, color, Modifier.matchParentSize())
            content()
        }
    }
}

@Composable
private fun Hero(f: Forecast, fmt: Fmt, metric: Metric) {
    val c = f.current
    val cs = MaterialTheme.colorScheme
    val today = f.today
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        when (metric) {
            Metric.WIND -> {
                val heading by rememberHeading(f.latitude, if (f.longitude.isNaN()) 0.0 else f.longitude)
                LiveCompass(c.windDir, heading, Modifier.size(260.dp), large = true)
                Spacer(Modifier.height(18.dp))
                BigNumber(
                    fmt.windNum(c.windSpeed), fmt.windUnit,
                    "From ${WeatherInfo.compass(c.windDir)} · ${WeatherInfo.beaufort(c.windSpeed)}",
                    "Gusts up to ${fmt.wind(c.windGust)}. " +
                        if (heading != null) "The arrow points where the wind is blowing — it follows your phone's compass, so it matches the real world."
                        else "This device has no compass, so north is at the top.",
                )
            }
            Metric.HUMIDITY -> {
                val slosh = rememberSlosh()
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    WaterFill(
                        fraction = ((if (c.humidity.ok()) c.humidity else 0.0) / 100).toFloat(),
                        slosh = slosh,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(cs.surfaceContainerHigh),
                    )
                    Text(fmt.pct(c.humidity), style = MaterialTheme.typography.displayMediumEmphasized, color = Color.White)
                }
                Spacer(Modifier.height(18.dp))
                BigNumber("", "", comfort(c.dewPoint), "Dew point ${fmt.temp(c.dewPoint)}. Tilt or shake your phone — the water moves with it.")
            }
            Metric.UV -> {
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        progress = { ((if (c.uv.ok()) c.uv else 0.0) / 11).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.matchParentSize(),
                        color = uvColor(if (c.uv.ok()) c.uv else 0.0),
                    )
                    Text(if (c.uv.ok()) c.uv.roundToInt().toString() else "–", style = MaterialTheme.typography.displayLargeEmphasized)
                }
                Spacer(Modifier.height(18.dp))
                BigNumber("", "", WeatherInfo.uvLevel(c.uv), WeatherInfo.uvAdvice(maxOf(c.uv.takeIf { it.ok() } ?: 0.0, today?.uvMax?.takeIf { it.ok() } ?: 0.0)))
            }
            Metric.FEELS -> {
                BlobHero(listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Arch, MaterialShapes.Puffy), cs.tertiaryContainer) {
                    Text(fmt.temp(c.feelsLike), style = MaterialTheme.typography.displayLargeEmphasized, color = cs.onTertiaryContainer)
                }
                Spacer(Modifier.height(18.dp))
                val diff = c.feelsLike - c.temp
                BigNumber(
                    "", "", "Actual ${fmt.temp(c.temp)}",
                    when {
                        diff.isNaN() -> ""
                        diff < -1.5 && c.windSpeed > 10 -> "Wind chill is making it feel colder."
                        diff < -1.5 -> "Feels colder than the thermometer says."
                        diff > 1.5 && c.humidity > 60 -> "Humidity is making it feel warmer."
                        diff > 1.5 -> "Sun and still air make it feel warmer."
                        else -> "Close to the actual temperature."
                    },
                )
            }
            Metric.PRESSURE -> {
                PressureGauge(c.pressure, Modifier.size(260.dp, 200.dp), fmt)
                Spacer(Modifier.height(10.dp))
                val nowIdx = f.hourlyAll.indexOfLast { it.time <= c.time }
                val past = if (nowIdx >= 3) f.hourlyAll[nowIdx - 3].pressure else Double.NaN
                val d = c.pressure - past
                BigNumber(
                    "", "",
                    when { d.isNaN() -> ""; d > 1 -> "Rising"; d < -1 -> "Falling"; else -> "Steady" },
                    when {
                        d.isNaN() -> ""
                        d > 1 -> "Rising pressure usually means settling, drier weather."
                        d < -1 -> "Falling pressure often comes before clouds, rain and wind."
                        else -> "No big changes expected from pressure alone."
                    },
                )
            }
            Metric.VISIBILITY -> {
                BlobHero(listOf(MaterialShapes.Oval, MaterialShapes.Pill, MaterialShapes.Cookie6Sided), cs.secondaryContainer) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmt.distanceNum(c.visibility), style = MaterialTheme.typography.displayLargeEmphasized, color = cs.onSecondaryContainer)
                        Text(fmt.distanceUnit, style = MaterialTheme.typography.titleMedium, color = cs.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.height(18.dp))
                BigNumber("", "", WeatherInfo.visibilityLevel(c.visibility), "How far you can clearly see objects. Fog, rain, snow and haze lower it.")
            }
            Metric.PRECIP -> {
                val next24 = f.upcoming.take(24).sumOf { if (it.precip.ok()) it.precip else 0.0 }
                val slosh = rememberSlosh()
                Box(Modifier.size(200.dp, 240.dp), contentAlignment = Alignment.Center) {
                    WaterFill(
                        fraction = (next24 / 20.0).toFloat().coerceIn(0.04f, 1f),
                        slosh = slosh,
                        modifier = Modifier.matchParentSize().clip(MaterialShapes.Pill.toShape()).background(cs.surfaceContainerHigh),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmt.precip(next24).substringBefore(' '), style = MaterialTheme.typography.displayMediumEmphasized)
                        Text(fmt.precip(next24).substringAfter(' ', ""), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(18.dp))
                val prob = f.upcoming.take(24).map { it.precipProb }.cleanMax()
                BigNumber("", "", "Expected in the next 24 h", if (prob.ok()) "Highest hourly chance ${prob.roundToInt()}%." else "")
            }
            Metric.CLOUD -> {
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        progress = { ((if (c.cloudCover.ok()) c.cloudCover else 0.0) / 100).toFloat() },
                        modifier = Modifier.matchParentSize(),
                    )
                    WeatherIcon(c.code, c.isDay, Modifier.size(110.dp), animated = true)
                }
                Spacer(Modifier.height(18.dp))
                BigNumber(fmt.pct(c.cloudCover), "", cloudWord(c.cloudCover), "Share of the sky covered by clouds.")
            }
            Metric.SUN -> {
                SunArc(f, Modifier.fillMaxWidth().height(170.dp))
                Spacer(Modifier.height(10.dp))
                if (today != null) {
                    BigNumber(
                        "", "",
                        "${fmt.time(today.sunrise)}  →  ${fmt.time(today.sunset)}",
                        "${fmt.duration(today.daylight)} of daylight, ${fmt.duration(today.sunshine)} of sunshine expected.",
                    )
                }
            }
            Metric.MOON -> {
                val moon = WeatherInfo.moon(c.time)
                MoonPhase(moon.phase, f.latitude < 0, Modifier.size(220.dp))
                Spacer(Modifier.height(18.dp))
                BigNumber("", "", moon.name, "${(moon.illumination * 100).roundToInt()}% illuminated · full moon in ${moon.daysToFull.roundToInt()} days · new moon in ${moon.daysToNew.roundToInt()} days")
            }
            Metric.AIR -> {
                val a = f.air
                val v = a?.usAqi ?: Double.NaN
                val band = WeatherInfo.usAqi(if (v.ok()) v else 0.0)
                Box(
                    Modifier.size(220.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(aqiColor(v).copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (v.ok()) v.roundToInt().toString() else "–", style = MaterialTheme.typography.displayLargeEmphasized)
                        Text("US AQI", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(18.dp))
                BigNumber(
                    "", "", band.label,
                    band.advice + (a?.euAqi?.takeIf { it.ok() }?.let { " European AQI ${it.roundToInt()} (${WeatherInfo.euAqi(it)})." } ?: ""),
                )
            }
            Metric.POLLEN -> {
                val top = f.air?.pollen?.maxByOrNull { it.second }
                BlobHero(listOf(MaterialShapes.Flower, MaterialShapes.Clover4Leaf, MaterialShapes.Clover8Leaf), pollenColor(top?.second ?: 0.0).copy(alpha = 0.35f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(top?.second?.roundToInt()?.toString() ?: "–", style = MaterialTheme.typography.displayLargeEmphasized)
                        Text("grains/m³", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(18.dp))
                BigNumber(
                    "", "",
                    top?.let { "${it.first}: ${WeatherInfo.pollenLevel(it.second).first}" } ?: "No pollen data",
                    "Pollen forecasts from Copernicus CAMS cover Europe only.",
                )
            }
        }
    }
}

private fun comfort(dew: Double) = when {
    dew.isNaN() -> ""
    dew < 10 -> "Dry and comfortable"
    dew < 16 -> "Comfortable"
    dew < 20 -> "Slightly humid"
    dew < 24 -> "Humid and muggy"
    else -> "Oppressive"
}

private fun cloudWord(cover: Double) = when {
    cover.isNaN() -> ""
    cover < 12 -> "Clear sky"
    cover < 37 -> "Mostly clear"
    cover < 62 -> "Partly cloudy"
    cover < 87 -> "Mostly cloudy"
    else -> "Overcast"
}

@Composable
private fun PressureGauge(p: Double, modifier: Modifier, fmt: Fmt) {
    val cs = MaterialTheme.colorScheme
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 18.dp.toPx()
            val d = minOf(size.width - stroke, (size.height - stroke) / 0.75f)
            val tl = Offset((size.width - d) / 2, stroke / 2)
            drawArc(cs.surfaceContainerHighest, 150f, 240f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            if (p.ok()) {
                val frac = ((p - 960) / 100).toFloat().coerceIn(0f, 1f)
                drawArc(cs.primary, 150f, 240f * frac, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmt.pressureNum(p), style = MaterialTheme.typography.displayMediumEmphasized)
            Text(fmt.pressureUnit, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SunArc(f: Forecast, modifier: Modifier) {
    val d = f.today ?: return
    val now = f.current.time
    val cs = MaterialTheme.colorScheme
    val progress = if (d.sunset > d.sunrise) ((now - d.sunrise).toFloat() / (d.sunset - d.sunrise)).coerceIn(0f, 1f) else 0f
    Canvas(modifier) {
        val pad = 20.dp.toPx()
        val w = size.width - pad * 2
        val baseY = size.height - 12.dp.toPx()
        val h = size.height - 36.dp.toPx()
        fun pt(t: Float) = Offset(pad + w * t, baseY - h * sin(Math.PI * t).toFloat())
        drawLine(cs.outlineVariant, Offset(0f, baseY), Offset(size.width, baseY), 1.dp.toPx())
        val track = Path().apply { moveTo(pt(0f).x, pt(0f).y); for (i in 1..80) pt(i / 80f).let { lineTo(it.x, it.y) } }
        drawPath(track, cs.outline, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))))
        if (progress > 0f) {
            val done = Path().apply {
                moveTo(pt(0f).x, pt(0f).y)
                val steps = (80 * progress).toInt().coerceAtLeast(1)
                for (i in 1..steps) pt(progress * i / steps).let { lineTo(it.x, it.y) }
            }
            drawPath(done, Amber, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
        if (now in d.sunrise..d.sunset) {
            drawCircle(Amber.copy(alpha = 0.25f), 24.dp.toPx(), pt(progress))
            drawCircle(Amber, 13.dp.toPx(), pt(progress))
        }
    }
}

// ---- hourly chart

private fun seriesFor(f: Forecast, fmt: Fmt, m: Metric): Series? {
    val hrs = f.upcoming
    val times = hrs.map { it.time }
    val icons = hrs.map { it.code to it.isDay }
    fun dash(v: Double) = if (v.isNaN()) "–" else v.roundToInt().toString()
    return when (m) {
        Metric.WIND -> Series(
            times, icons, hrs.map { fmt.windValue(it.windSpeed) }, { dash(it) }, false, Teal,
            lo = 0.0, second = hrs.map { fmt.windValue(it.windGust) }, secondName = "Gusts",
            bottom = { WeatherInfo.compass(hrs[it].windDir) },
        )
        Metric.HUMIDITY -> Series(
            times, icons, hrs.map { it.humidity }, { "${dash(it)}%" }, false, Blue, lo = 0.0, hi = 100.0,
            bottom = { fmt.temp(hrs[it].dewPoint) },
        )
        Metric.UV -> Series(times, icons, hrs.map { it.uv }, { dash(it) }, true, Amber, lo = 0.0, hi = maxOf(hrs.map { it.uv }.cleanMax().takeIf { it.ok() } ?: 0.0, 8.0), barColor = { uvColor(it) })
        Metric.FEELS -> Series(
            times, icons, hrs.map { fmt.tempValue(it.feelsLike) }, { "${dash(it)}°" }, false, Color(0xFFFF8A3D),
            second = hrs.map { fmt.tempValue(it.temp) }, secondName = "Actual",
        )
        Metric.PRESSURE -> Series(times, icons, hrs.map { fmt.pressureValue(it.pressure) }, { fmt.pressureNum(it / fmt.pressureValue(1.0)) }, false, Color(0xFF9C7CFF))
        Metric.VISIBILITY -> Series(times, icons, hrs.map { it.visibility / 1000 }, { fmt.distanceNum(it * 1000) }, false, Teal, lo = 0.0)
        Metric.PRECIP -> Series(
            times, icons, hrs.map { if (it.precip.ok()) it.precip else 0.0 },
            { if (it <= 0) "" else fmt.precip(it).substringBefore(' ') }, true, Blue,
            lo = 0.0, hi = maxOf(hrs.map { it.precip }.cleanMax().takeIf { it.ok() } ?: 0.0, 2.0),
            bottom = { val p = hrs[it].precipProb; if (p.ok()) "${p.roundToInt()}%" else "" }, bottomColor = Blue,
        )
        Metric.CLOUD -> Series(times, icons, hrs.map { it.cloudCover }, { "${dash(it)}%" }, true, Slate, lo = 0.0, hi = 100.0)
        Metric.AIR -> {
            val ah = f.air?.hourly?.filter { it.time > f.current.time - 3600 }?.take(48).orEmpty()
            if (ah.isEmpty()) null else Series(
                ah.map { it.time }, null, ah.map { it.usAqi }, { dash(it) }, true, Teal,
                lo = 0.0, hi = maxOf(ah.map { it.usAqi }.cleanMax().takeIf { it.ok() } ?: 0.0, 100.0), barColor = { aqiColor(it) },
                bottom = { val p = ah[it].pm25; if (p.ok()) "${p.roundToInt()}" else "" },
            )
        }
        Metric.POLLEN -> {
            val ah = f.air?.hourly?.filter { it.time > f.current.time - 3600 }?.take(48).orEmpty()
            val v = ah.map { it.pollen.cleanMax() }
            if (ah.isEmpty() || v.all { it.isNaN() }) null else Series(
                ah.map { it.time }, null, v, { dash(it) }, true, Color(0xFF4CAF50),
                lo = 0.0, hi = maxOf(v.cleanMax(), 10.0), barColor = { pollenColor(it) },
                bottom = { i -> ah[i].pollen.withIndex().filter { it.value.ok() }.maxByOrNull { it.value }?.let { POLLEN_NAMES[it.index].take(5) } ?: "" },
            )
        }
        Metric.SUN, Metric.MOON -> null
    }
}

@Composable
private fun Chart(s: Series, fmt: Fmt) {
    val n = s.values.size
    val colW = 56.dp
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = cs.onSurface, fontWeight = FontWeight.SemiBold)
    val subStyle = MaterialTheme.typography.labelMedium.copy(color = cs.onSurfaceVariant)
    val ring = cardBackdrop()
    val track = cs.surfaceContainerHighest
    val headerH = if (s.icons != null) 62.dp else 26.dp
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        Box(Modifier.width(colW * n).height(headerH + 120.dp)) {
            Row {
                for (i in 0 until n) {
                    Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (i == 0) "Now" else fmt.hour(s.times[i]),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (i == 0) cs.primary else cs.onSurfaceVariant,
                            fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                        s.icons?.let {
                            Spacer(Modifier.height(6.dp))
                            WeatherIcon(it[i].first, it[i].second, Modifier.size(28.dp))
                        }
                    }
                }
            }
            Canvas(Modifier.matchParentSize()) {
                val cw = colW.toPx()
                val top = headerH.toPx() + 30.dp.toPx()
                val bottom = headerH.toPx() + 88.dp.toPx()
                val textY = bottom + 8.dp.toPx()
                val all = s.values + (s.second ?: emptyList())
                val lo = s.lo ?: all.cleanMin().takeIf { it.ok() } ?: 0.0
                val hi = s.hi ?: all.cleanMax().takeIf { it.ok() } ?: 1.0
                val span = (hi - lo).takeIf { it > 1e-6 } ?: 1.0
                fun y(v: Double) = bottom - ((v - lo) / span).toFloat().coerceIn(0f, 1f) * (bottom - top)
                fun label(i: Int, text: String, yy: Float) {
                    if (text.isEmpty()) return
                    val m = measurer.measure(text, labelStyle)
                    drawText(m, topLeft = Offset(i * cw + cw / 2 - m.size.width / 2, yy - m.size.height - 4.dp.toPx()))
                }
                if (s.bars) {
                    val bw = cw * 0.42f
                    s.values.forEachIndexed { i, v ->
                        val x = i * cw + (cw - bw) / 2
                        drawRoundRect(track, Offset(x, top), Size(bw, bottom - top), CornerRadius(bw / 2))
                        if (v.ok() && v > 0) {
                            val hgt = maxOf(bottom - y(v), bw)
                            drawRoundRect(s.barColor?.invoke(v) ?: s.color, Offset(x, bottom - hgt), Size(bw, hgt), CornerRadius(bw / 2))
                        }
                        label(i, if (v.ok()) s.label(v) else "", top)
                    }
                } else {
                    fun pathOf(vals: List<Double>): Pair<Path, List<Offset>> {
                        val pts = vals.mapIndexed { i, v -> Offset(i * cw + cw / 2, if (v.ok()) y(v) else bottom) }
                        val p = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (i in 1 until pts.size) {
                                val mx = (pts[i - 1].x + pts[i].x) / 2
                                cubicTo(mx, pts[i - 1].y, mx, pts[i].y, pts[i].x, pts[i].y)
                            }
                        }
                        return p to pts
                    }
                    s.second?.let { sec ->
                        val (p2, _) = pathOf(sec)
                        drawPath(p2, s.color.copy(alpha = 0.6f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
                    }
                    val (p, pts) = pathOf(s.values)
                    val fill = Path().apply {
                        addPath(p); lineTo(pts.last().x, bottom + 6.dp.toPx()); lineTo(pts.first().x, bottom + 6.dp.toPx()); close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.22f), Color.Transparent), top, bottom + 6.dp.toPx()))
                    drawPath(p, s.color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    pts.forEachIndexed { i, pt ->
                        drawCircle(ring, 6.dp.toPx(), pt)
                        drawCircle(s.color, 4.dp.toPx(), pt)
                        label(i, if (s.values[i].ok()) s.label(s.values[i]) else "", pt.y)
                    }
                }
                s.bottom?.let { b ->
                    val st = subStyle.copy(color = s.bottomColor ?: subStyle.color)
                    for (i in 0 until n) {
                        val t = b(i)
                        if (t.isEmpty()) continue
                        val m = measurer.measure(t, st)
                        drawText(m, topLeft = Offset(i * cw + cw / 2 - m.size.width / 2, textY))
                    }
                }
            }
        }
    }
}

// ---- daily ranges

private fun daysFor(f: Forecast, fmt: Fmt, m: Metric): List<DayRange> {
    val byDate = f.hourlyAll.groupBy { fmt.localDate(it.time) }
    fun hourly(sel: (dev.vis.weather.data.Hour) -> Double, color: Color, text: (Double) -> String, conv: (Double) -> Double = { it }) =
        f.daily.mapNotNull { d ->
            val v = byDate[d.date]?.map { conv(sel(it)) } ?: return@mapNotNull null
            val lo = v.cleanMin(); val hi = v.cleanMax()
            if (lo.isNaN()) null else DayRange(d.date, d.code, lo, hi, text(lo), text(hi), color)
        }
    fun r(v: Double) = if (v.isNaN()) "–" else v.roundToInt().toString()
    return when (m) {
        Metric.WIND -> f.daily.map { d ->
            val v = byDate[d.date]?.map { fmt.windValue(it.windSpeed) }.orEmpty()
            DayRange(d.date, d.code, v.cleanMin(), fmt.windValue(d.windMax), r(v.cleanMin()), r(fmt.windValue(d.windMax)), Teal, WeatherInfo.compass(d.windDir))
        }
        Metric.HUMIDITY -> hourly({ it.humidity }, Blue, { "${r(it)}%" })
        Metric.UV -> f.daily.map { d -> DayRange(d.date, d.code, 0.0, d.uvMax, "", r(d.uvMax), uvColor(if (d.uvMax.ok()) d.uvMax else 0.0), WeatherInfo.uvLevel(d.uvMax)) }
        Metric.FEELS -> f.daily.map { d ->
            DayRange(d.date, d.code, fmt.tempValue(d.feelsMin), fmt.tempValue(d.feelsMax), fmt.temp(d.feelsMin), fmt.temp(d.feelsMax), Color(0xFFFF8A3D))
        }
        Metric.PRESSURE -> hourly({ it.pressure }, Color(0xFF9C7CFF), { fmt.pressureNum(it / fmt.pressureValue(1.0)) }, { fmt.pressureValue(it) })
        Metric.VISIBILITY -> hourly({ it.visibility }, Teal, { fmt.distanceNum(it) })
        Metric.PRECIP -> f.daily.map { d ->
            DayRange(d.date, d.code, 0.0, d.precipSum, "", fmt.precip(d.precipSum), Blue, if (d.precipProbMax.ok()) "${d.precipProbMax.roundToInt()}%" else "")
        }
        Metric.CLOUD -> hourly({ it.cloudCover }, Slate, { "${r(it)}%" })
        Metric.AIR, Metric.POLLEN -> {
            val ah = f.air?.hourly.orEmpty().groupBy { fmt.localDate(it.time) }
            ah.keys.sorted().filter { !it.isBefore(f.daily.first().date) }.mapNotNull { date ->
                val v = ah[date]!!.map { if (m == Metric.AIR) it.usAqi else it.pollen.cleanMax() }
                val lo = v.cleanMin(); val hi = v.cleanMax()
                if (hi.isNaN()) null else DayRange(
                    date, f.daily.firstOrNull { it.date == date }?.code, lo, hi, r(lo), r(hi),
                    if (m == Metric.AIR) aqiColor(hi) else pollenColor(hi),
                    if (m == Metric.AIR) WeatherInfo.usAqi(hi).label else WeatherInfo.pollenLevel(hi).first,
                )
            }
        }
        Metric.SUN, Metric.MOON -> emptyList()
    }
}

@Composable
private fun DayRanges(days: List<DayRange>, fmt: Fmt) {
    val zeroBased = days.any { it.minText.isEmpty() }
    val lo = if (zeroBased) 0.0 else days.map { it.min }.cleanMin().let { if (it.isNaN()) 0.0 else it }
    val hi = days.map { it.max }.cleanMax().let { if (it.isNaN() || it <= lo) lo + 1 else it }
    val today = days.first().date
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        days.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fmt.dayName(d.date, today),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.width(78.dp),
                    maxLines = 1,
                )
                if (d.code != null) WeatherIcon(d.code, true, Modifier.size(24.dp)) else Spacer(Modifier.size(24.dp))
                Text(
                    d.minText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(52.dp),
                    maxLines = 1,
                )
                Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 8.dp)) {
                    val h = size.height
                    drawRoundRect(track, size = size, cornerRadius = CornerRadius(h / 2))
                    if (d.max.ok()) {
                        val span = hi - lo
                        val x0 = (((if (d.min.ok()) d.min else lo) - lo) / span * size.width).toFloat()
                        val x1 = ((d.max - lo) / span * size.width).toFloat().coerceAtLeast(x0 + h)
                        drawRoundRect(d.color, Offset(x0, 0f), Size(x1 - x0, h), CornerRadius(h / 2))
                    }
                }
                Column(Modifier.width(78.dp)) {
                    Text(d.maxText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (d.extra.isNotEmpty()) {
                        Text(d.extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---- special sections

@Composable
private fun SunTable(f: Forecast, fmt: Fmt) {
    val today = f.daily.first().date
    SectionCard("Sunrise & sunset", Icons.Rounded.CalendarMonth, MaterialShapes.SoftBurst) {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            f.daily.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.dayName(d.date, today), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(84.dp))
                    Text("↑ ${fmt.time(d.sunrise)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("↓ ${fmt.time(d.sunset)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmt.duration(d.daylight), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("☀ ${fmt.duration(d.sunshine)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoonCalendar(f: Forecast, fmt: Fmt) {
    val start = fmt.localDate(f.current.time)
    SectionCard("Next 30 nights", Icons.Rounded.CalendarMonth, MaterialShapes.Cookie9Sided) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (i in 0 until 30) {
                val date = start.plusDays(i.toLong())
                val epoch = date.atTime(22, 0).atZone(f.zone).toEpochSecond()
                val m = WeatherInfo.moon(epoch)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                    Text(if (i == 0) "Tonight" else fmt.shortDate(date), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    MoonPhase(m.phase, f.latitude < 0, Modifier.size(40.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("${(m.illumination * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Pollutants(a: dev.vis.weather.data.AirQuality) {
    val items = listOf(
        Triple("PM2.5", a.pm25, "Fine particles from combustion and traffic; reach deep into the lungs. WHO 24 h guideline: 15 µg/m³." to 15.0),
        Triple("PM10", a.pm10, "Coarser particles: dust, pollen, construction. WHO 24 h guideline: 45 µg/m³." to 45.0),
        Triple("O₃ Ozone", a.o3, "Forms in sunlight from traffic emissions; peaks on hot afternoons. WHO 8 h guideline: 100 µg/m³." to 100.0),
        Triple("NO₂", a.no2, "Mainly from vehicle exhaust. WHO 24 h guideline: 25 µg/m³." to 25.0),
        Triple("SO₂", a.so2, "From burning sulphur-containing fuels. WHO 24 h guideline: 40 µg/m³." to 40.0),
        Triple("CO", a.co, "Carbon monoxide from incomplete combustion. WHO 24 h guideline: 4000 µg/m³." to 4000.0),
    )
    SectionCard("Pollutants", Icons.Rounded.Science, MaterialShapes.Flower) {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items.forEach { (name, v, info) ->
                val (text, limit) = info
                val frac = if (v.ok()) (v / (limit * 2)).toFloat().coerceIn(0f, 1f) else 0f
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            if (v.isNaN()) "–" else if (v < 10) String.format("%.1f", v) else v.roundToInt().toString(),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                        Text(" µg/m³", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearWavyProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (frac <= 0.5f) Color(0xFF4CAF50) else if (frac < 0.8f) Color(0xFFFF9800) else Color(0xFFF44336),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PollenTypes(a: dev.vis.weather.data.AirQuality) {
    if (a.pollen.isEmpty()) return
    SectionCard("By plant", Icons.Rounded.Science, MaterialShapes.Clover4Leaf) {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            a.pollen.forEach { (name, v) ->
                val (level, frac) = WeatherInfo.pollenLevel(v)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(84.dp))
                    LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.weight(1f), color = pollenColor(v))
                    Column(Modifier.width(90.dp).padding(start = 10.dp)) {
                        Text(level, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text("${v.roundToInt()} grains/m³", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---- explanations

private fun about(m: Metric): String = when (m) {
    Metric.WIND -> "Wind speed is the average over 10 minutes at 10 m above ground. Gusts are short bursts that can be much stronger. Direction is where the wind comes FROM: a north wind blows from north to south."
    Metric.HUMIDITY -> "Relative humidity is how much water vapour the air holds compared to the maximum at that temperature. The dew point is a better measure of how muggy it feels: the higher it is, the stickier the air."
    Metric.UV -> "The UV index measures the strength of sunburn-causing ultraviolet radiation. It peaks around solar noon and is higher at altitude, near water, snow and sand. Clouds reduce it, but not always much."
    Metric.FEELS -> "Feels-like (apparent) temperature combines air temperature with wind, humidity and sunshine to estimate how the weather feels on your skin."
    Metric.PRESSURE -> "Air pressure at sea level. Standard pressure is 1013 hPa. Highs usually bring stable, settled weather; lows bring clouds, wind and rain. Rapid falls are a sign of an approaching storm."
    Metric.VISIBILITY -> "Horizontal distance at which objects can be clearly seen. Below 1 km is fog; below 10 km usually means mist, haze or precipitation."
    Metric.PRECIP -> "Total rain, showers and snow (as water) per hour. The percentage is the chance that at least 0.1 mm falls in that hour, combined from several weather models."
    Metric.CLOUD -> "Fraction of the sky covered by clouds at all heights. Low clouds block the most sun; high thin clouds can still let a lot of sunshine through."
    Metric.SUN -> "Sunrise and sunset are when the top edge of the sun touches the horizon. Daylight is the time between them; sunshine is the time with direct sun not hidden by clouds."
    Metric.MOON -> "The moon goes through a full cycle of phases every 29.5 days. Phases are calculated on your device, so they work offline."
    Metric.AIR -> "The Air Quality Index turns pollutant concentrations into one number. It is based on the Copernicus Atmosphere Monitoring Service (CAMS) model, not a local sensor, so very local sources may not show up."
    Metric.POLLEN -> "Pollen concentration in grains per cubic metre of air, from the CAMS European pollen model. Sensitive people may notice symptoms from around 10–20 grains/m³ for grass and birch."
}

private fun scale(m: Metric): List<Triple<Color, String, String>> = when (m) {
    Metric.UV -> listOf(
        Triple(Color(0xFF4CAF50), "0–2 Low", "No protection needed."),
        Triple(Color(0xFFFFC107), "3–5 Moderate", "Seek shade at midday, wear sunscreen."),
        Triple(Color(0xFFFF9800), "6–7 High", "Protection essential, reduce midday sun."),
        Triple(Color(0xFFF44336), "8–10 Very high", "Extra protection, avoid midday sun."),
        Triple(Color(0xFF9C27B0), "11+ Extreme", "Stay inside around midday."),
    )
    Metric.AIR -> listOf(
        Triple(Color(0xFF4CAF50), "0–50 Good", "Air quality is satisfactory."),
        Triple(Color(0xFFFFC107), "51–100 Moderate", "Acceptable; unusually sensitive people may be affected."),
        Triple(Color(0xFFFF9800), "101–150 Unhealthy for sensitive groups", "Children, elderly and people with lung disease should limit exertion."),
        Triple(Color(0xFFF44336), "151–200 Unhealthy", "Everyone may begin to feel effects."),
        Triple(Color(0xFF9C27B0), "201–300 Very unhealthy", "Health alert."),
        Triple(Color(0xFF7E0023), "301+ Hazardous", "Emergency conditions."),
    )
    Metric.HUMIDITY -> listOf(
        Triple(Color(0xFF4A9EFF), "Dew point < 10°C", "Dry and comfortable."),
        Triple(Color(0xFF35C7B0), "10–16°C", "Comfortable."),
        Triple(Color(0xFFFFC107), "16–20°C", "Slightly humid."),
        Triple(Color(0xFFFF9800), "20–24°C", "Humid and muggy."),
        Triple(Color(0xFFF44336), "24°C+", "Oppressive."),
    )
    Metric.WIND -> listOf(
        Triple(Color(0xFF4CAF50), "< 20 km/h", "Light: leaves rustle, flags flutter."),
        Triple(Color(0xFFFFC107), "20–38 km/h", "Moderate to fresh: small branches move, dust blows."),
        Triple(Color(0xFFFF9800), "39–61 km/h", "Strong: large branches move, walking against it is hard."),
        Triple(Color(0xFFF44336), "62+ km/h", "Gale: twigs break, structural damage possible."),
    )
    else -> emptyList()
}
