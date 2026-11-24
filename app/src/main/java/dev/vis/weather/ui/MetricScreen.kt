@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import dev.vis.weather.inSentence
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.L
import dev.vis.weather.R
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Indices
import dev.vis.weather.ui.detail.AirSections
import dev.vis.weather.ui.detail.CloudSections
import dev.vis.weather.ui.detail.FeelsSections
import dev.vis.weather.ui.detail.FireHero
import dev.vis.weather.ui.detail.FireSections
import dev.vis.weather.ui.detail.HumiditySections
import dev.vis.weather.ui.detail.MoonHeroText
import dev.vis.weather.ui.detail.MoonSections
import dev.vis.weather.ui.detail.PrecipSections
import dev.vis.weather.ui.detail.PressureSections
import dev.vis.weather.ui.detail.SeaHero
import dev.vis.weather.ui.detail.SeaSections
import dev.vis.weather.ui.detail.SunHeroTimes
import dev.vis.weather.ui.detail.SunSections
import dev.vis.weather.ui.detail.UvSections
import dev.vis.weather.ui.detail.VisibilitySections
import dev.vis.weather.ui.detail.fireSeries
import dev.vis.weather.ui.detail.seaColor
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

private val POLLEN_NAMES = listOf(
    R.string.dt_pollen_alder, R.string.dt_pollen_birch, R.string.dt_pollen_grass,
    R.string.dt_pollen_mugwort, R.string.dt_pollen_olive, R.string.dt_pollen_ragweed,
)

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
            FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.dt_back)) }
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
            if (metric in TOP_SECTIONS) item(key = "insights") { Box(w) { TopSections(f, fmt, metric) } }
            if (series != null && series.values.isNotEmpty()) {
                item(key = "chart") {
                    Box(w) {
                        SectionCard(pluralStringResource(R.plurals.dt_next_hours, series.values.size, series.values.size), Icons.Rounded.Schedule, MaterialShapes.Cookie4Sided) {
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
                        SectionCard(pluralStringResource(R.plurals.dt_next_days, days.size, days.size), Icons.Rounded.CalendarMonth, MaterialShapes.Clover4Leaf) {
                            DayRanges(days, fmt)
                        }
                    }
                }
            }
            when (metric) {
                Metric.SUN -> item(key = "sun") { Box(w) { SunTable(f, fmt) } }
                Metric.MOON -> item(key = "moon") { Box(w) { MoonCalendar(f, fmt) } }
                Metric.POLLEN -> f.air?.let { a -> item(key = "pl") { Box(w) { PollenTypes(a) } } }
                else -> {}
            }
            item(key = "about") {
                Box(w) {
                    SectionCard(stringResource(R.string.dt_about_metric, metric.title.inSentence()), Icons.Rounded.Info, MaterialShapes.Circle) {
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


private val TOP_SECTIONS = setOf(
    Metric.HUMIDITY, Metric.UV, Metric.FEELS, Metric.PRESSURE, Metric.VISIBILITY, Metric.PRECIP,
    Metric.CLOUD, Metric.SUN, Metric.MOON, Metric.AIR, Metric.SEA, Metric.FIRE,
)

/** Insight cards shown between the hero and the hourly chart. */
@Composable
private fun TopSections(f: Forecast, fmt: Fmt, metric: Metric) {
    when (metric) {
        Metric.HUMIDITY -> HumiditySections(f, fmt)
        Metric.UV -> UvSections(f, fmt)
        Metric.FEELS -> FeelsSections(f)
        Metric.PRESSURE -> PressureSections(f, fmt)
        Metric.VISIBILITY -> VisibilitySections(f, fmt)
        Metric.PRECIP -> PrecipSections(f, fmt)
        Metric.CLOUD -> CloudSections(f, fmt)
        Metric.SUN -> SunSections(f, fmt)
        Metric.MOON -> MoonSections(f, fmt)
        Metric.AIR -> f.air?.let { AirSections(it, fmt) }
        Metric.SEA -> SeaSections(f, fmt)
        Metric.FIRE -> FireSections(f, fmt)
        else -> {}
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
                    stringResource(R.string.dt_wind_from, WeatherInfo.compass(c.windDir), WeatherInfo.beaufort(c.windSpeed)),
                    stringResource(R.string.dt_wind_gusts_up_to, fmt.wind(c.windGust)) + " " +
                        stringResource(if (heading != null) R.string.dt_wind_compass_note else R.string.dt_wind_no_compass),
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
                BigNumber("", "", Indices.humidityComfort(c.dewPoint, c.humidity).label, stringResource(R.string.dt_hum_hero_sub, fmt.temp(c.dewPoint)))
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
                BlobHero(listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Arch, MaterialShapes.Cookie7Sided), cs.tertiaryContainer) {
                    Text(fmt.temp(c.feelsLike), style = MaterialTheme.typography.displayLargeEmphasized, color = cs.onTertiaryContainer)
                }
                Spacer(Modifier.height(18.dp))
                val diff = c.feelsLike - c.temp
                BigNumber(
                    "", "", stringResource(R.string.dt_feels_actual, fmt.temp(c.temp)),
                    when {
                        diff.isNaN() -> ""
                        diff < -1.5 && c.windSpeed > 10 -> stringResource(R.string.dt_feels_wind_chill)
                        diff < -1.5 -> stringResource(R.string.dt_feels_colder)
                        diff > 1.5 && c.humidity > 60 -> stringResource(R.string.dt_feels_humid)
                        diff > 1.5 -> stringResource(R.string.dt_feels_warmer)
                        else -> stringResource(R.string.dt_feels_close)
                    },
                )
            }
            Metric.PRESSURE -> {
                PressureGauge(c.pressure, Modifier.size(260.dp, 200.dp), fmt)
                Spacer(Modifier.height(10.dp))
                val trend = remember(f) { Indices.pressureTrend(f) }
                BigNumber("", "", trend?.label ?: "", trend?.let { dev.vis.weather.ui.detail.pressureDelta(fmt, it.change3h) } ?: "")
            }
            Metric.VISIBILITY -> {
                BlobHero(listOf(MaterialShapes.Oval, MaterialShapes.Pill, MaterialShapes.Cookie6Sided), cs.secondaryContainer) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmt.distanceNum(c.visibility), style = MaterialTheme.typography.displayLargeEmphasized, color = cs.onSecondaryContainer)
                        Text(fmt.distanceUnit, style = MaterialTheme.typography.titleMedium, color = cs.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.height(18.dp))
                BigNumber("", "", Indices.visibilityRating(c.visibility).label, stringResource(R.string.dt_vis_hero_sub))
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
                BigNumber("", "", stringResource(R.string.dt_precip_hero_caption), if (prob.ok()) stringResource(R.string.dt_precip_hero_chance, prob.roundToInt()) else "")
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
                BigNumber(fmt.pct(c.cloudCover), "", cloudWord(c.cloudCover), stringResource(R.string.dt_cloud_hero_sub))
            }
            Metric.SUN -> {
                SunArc(f, Modifier.fillMaxWidth().height(170.dp))
                Spacer(Modifier.height(10.dp))
                SunHeroTimes(f, fmt)
                Spacer(Modifier.height(14.dp))
                if (today != null) {
                    BigNumber(
                        "", "",
                        "",
                        stringResource(R.string.dt_sun_hero_sub, fmt.duration(today.daylight), fmt.duration(today.sunshine)),
                    )
                }
            }
            Metric.MOON -> {
                val moon = remember(c.time) { dev.vis.weather.data.Astro.moonState(c.time) }
                MoonPhase(moon.phase, f.latitude < 0, Modifier.size(220.dp))
                Spacer(Modifier.height(18.dp))
                MoonHeroText(f, fmt)
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
                        Text(stringResource(R.string.dt_us_aqi), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(18.dp))
                BigNumber(
                    "", "", band.label,
                    band.advice + (a?.euAqi?.takeIf { it.ok() }?.let { " " + stringResource(R.string.dt_air_eu_suffix, it.roundToInt(), WeatherInfo.euAqi(it)) } ?: ""),
                )
            }
            Metric.POLLEN -> {
                val top = f.air?.pollen?.maxByOrNull { it.second }
                BlobHero(listOf(MaterialShapes.Flower, MaterialShapes.Clover4Leaf, MaterialShapes.Clover8Leaf), pollenColor(top?.second ?: 0.0).copy(alpha = 0.35f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(top?.second?.roundToInt()?.toString() ?: "–", style = MaterialTheme.typography.displayLargeEmphasized)
                        Text(stringResource(R.string.dt_pollen_unit), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(18.dp))
                BigNumber(
                    "", "",
                    top?.let { stringResource(R.string.dt_label_value, it.first, WeatherInfo.pollenLevel(it.second).first) } ?: stringResource(R.string.dt_pollen_none),
                    stringResource(R.string.dt_pollen_europe_only),
                )
            }
            Metric.SEA -> SeaHero(f, fmt)
            Metric.FIRE -> FireHero(f, fmt)
        }
    }
}

private fun cloudWord(cover: Double) = when {
    cover.isNaN() -> ""
    cover < 12 -> L.s(R.string.dt_cloud_clear)
    cover < 37 -> L.s(R.string.dt_cloud_mostly_clear)
    cover < 62 -> L.s(R.string.dt_cloud_partly)
    cover < 87 -> L.s(R.string.dt_cloud_mostly_cloudy)
    else -> L.s(R.string.dt_cloud_overcast)
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
            lo = 0.0, second = hrs.map { fmt.windValue(it.windGust) }, secondName = L.s(R.string.dt_gusts),
            bottom = { WeatherInfo.compass(hrs[it].windDir) },
        )
        Metric.HUMIDITY -> Series(
            times, icons, hrs.map { it.humidity }, { "${dash(it)}%" }, false, Blue, lo = 0.0, hi = 100.0,
            bottom = { fmt.temp(hrs[it].dewPoint) },
        )
        Metric.UV -> Series(times, icons, hrs.map { it.uv }, { dash(it) }, true, Amber, lo = 0.0, hi = maxOf(hrs.map { it.uv }.cleanMax().takeIf { it.ok() } ?: 0.0, 8.0), barColor = { uvColor(it) })
        Metric.FEELS -> Series(
            times, icons, hrs.map { fmt.tempValue(it.feelsLike) }, { "${dash(it)}°" }, false, Color(0xFFFF8A3D),
            second = hrs.map { fmt.tempValue(it.temp) }, secondName = L.s(R.string.dt_actual),
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
                bottom = { i -> ah[i].pollen.withIndex().filter { it.value.ok() }.maxByOrNull { it.value }?.let { L.s(POLLEN_NAMES[it.index]).take(5) } ?: "" },
            )
        }
        Metric.FIRE -> {
            val v = fireSeries(f)
            if (v.all { it.isNaN() }) null else Series(
                times, icons, v, { dash(it) }, true, Color(0xFFFF9800),
                lo = 0.0, hi = maxOf(v.cleanMax().takeIf { it.ok() } ?: 0.0, 50.0), barColor = { Color(Indices.fireBand(it).color) },
                bottom = { fmt.pct(hrs[it].humidity) },
            )
        }
        Metric.SUN, Metric.MOON, Metric.SEA -> null
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
                            if (i == 0) stringResource(R.string.dt_now) else fmt.hour(s.times[i]),
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
        Metric.FIRE -> f.daily.mapNotNull { d ->
            val v = byDate[d.date]?.map { Indices.fireIndex(it) }?.cleanMax() ?: return@mapNotNull null
            if (v.isNaN()) null else {
                val b = Indices.fireBand(v)
                DayRange(d.date, d.code, 0.0, v, "", r(v), Color(b.color), b.label)
            }
        }
        Metric.SUN, Metric.MOON, Metric.SEA -> emptyList()
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
    SectionCard(stringResource(R.string.dt_sunrise_sunset), Icons.Rounded.CalendarMonth, MaterialShapes.SoftBurst) {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            f.daily.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.dayName(d.date, today), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(84.dp))
                    Text("↑ ${fmt.time(d.sunrise)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("↓ ${fmt.time(d.sunset)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmt.duration(d.daylight), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.dt_sun_duration, fmt.duration(d.sunshine)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoonCalendar(f: Forecast, fmt: Fmt) {
    val start = fmt.localDate(f.current.time)
    SectionCard(pluralStringResource(R.plurals.dt_next_nights, 30, 30), Icons.Rounded.CalendarMonth, MaterialShapes.Cookie9Sided) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (i in 0 until 30) {
                val date = start.plusDays(i.toLong())
                val epoch = date.atTime(22, 0).atZone(f.zone).toEpochSecond()
                val m = WeatherInfo.moon(epoch)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                    Text(if (i == 0) stringResource(R.string.dt_tonight) else fmt.shortDate(date), style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
private fun PollenTypes(a: dev.vis.weather.data.AirQuality) {
    if (a.pollen.isEmpty()) return
    SectionCard(stringResource(R.string.dt_by_plant), Icons.Rounded.Science, MaterialShapes.Clover4Leaf) {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            a.pollen.forEach { (name, v) ->
                val (level, frac) = WeatherInfo.pollenLevel(v)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(84.dp))
                    LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.weight(1f), color = pollenColor(v))
                    Column(Modifier.width(90.dp).padding(start = 10.dp)) {
                        Text(level, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(stringResource(R.string.dt_pollen_grains, v.roundToInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---- explanations

private fun about(m: Metric): String = when (m) {
    Metric.WIND -> L.s(R.string.dt_about_wind)
    Metric.HUMIDITY -> L.s(R.string.dt_about_humidity)
    Metric.UV -> L.s(R.string.dt_about_uv)
    Metric.FEELS -> L.s(R.string.dt_about_feels)
    Metric.PRESSURE -> L.s(R.string.dt_about_pressure)
    Metric.VISIBILITY -> L.s(R.string.dt_about_visibility)
    Metric.PRECIP -> L.s(R.string.dt_about_precip)
    Metric.CLOUD -> L.s(R.string.dt_about_cloud)
    Metric.SUN -> L.s(R.string.dt_about_sun)
    Metric.MOON -> L.s(R.string.dt_about_moon)
    Metric.AIR -> L.s(R.string.dt_about_air)
    Metric.SEA -> L.s(R.string.dt_about_sea)
    Metric.FIRE -> Indices.FIRE_EXPLAINER
    Metric.POLLEN -> L.s(R.string.dt_about_pollen)
}

private fun scale(m: Metric): List<Triple<Color, String, String>> {
    fun row(c: Color, label: Int, text: Int) = Triple(c, L.s(label), L.s(text))
    fun rowKmh(c: Color, label: String, text: Int) = Triple(c, label, L.s(text))
    return when (m) {
        Metric.UV -> listOf(
            row(Color(0xFF4CAF50), R.string.dt_scale_uv_low, R.string.dt_scale_uv_low_text),
            row(Color(0xFFFFC107), R.string.dt_scale_uv_moderate, R.string.dt_scale_uv_moderate_text),
            row(Color(0xFFFF9800), R.string.dt_scale_uv_high, R.string.dt_scale_uv_high_text),
            row(Color(0xFFF44336), R.string.dt_scale_uv_very_high, R.string.dt_scale_uv_very_high_text),
            row(Color(0xFF9C27B0), R.string.dt_scale_uv_extreme, R.string.dt_scale_uv_extreme_text),
        )
        Metric.AIR -> listOf(
            row(Color(0xFF4CAF50), R.string.dt_scale_aqi_good, R.string.dt_scale_aqi_good_text),
            row(Color(0xFFFFC107), R.string.dt_scale_aqi_moderate, R.string.dt_scale_aqi_moderate_text),
            row(Color(0xFFFF9800), R.string.dt_scale_aqi_sensitive, R.string.dt_scale_aqi_sensitive_text),
            row(Color(0xFFF44336), R.string.dt_scale_aqi_unhealthy, R.string.dt_scale_aqi_unhealthy_text),
            row(Color(0xFF9C27B0), R.string.dt_scale_aqi_very_unhealthy, R.string.dt_scale_aqi_very_unhealthy_text),
            row(Color(0xFF7E0023), R.string.dt_scale_aqi_hazardous, R.string.dt_scale_aqi_hazardous_text),
        )
        Metric.HUMIDITY -> listOf(
            row(Color(0xFF4A9EFF), R.string.dt_scale_hum_dry, R.string.dt_scale_hum_dry_text),
            row(Color(0xFF35C7B0), R.string.dt_scale_hum_comfortable, R.string.dt_scale_hum_comfortable_text),
            row(Color(0xFF4CAF50), R.string.dt_scale_hum_noticeable, R.string.dt_scale_hum_noticeable_text),
            row(Color(0xFFFFC107), R.string.dt_scale_hum_sticky, R.string.dt_scale_hum_sticky_text),
            row(Color(0xFFFF9800), R.string.dt_scale_hum_muggy, R.string.dt_scale_hum_muggy_text),
            row(Color(0xFFF44336), R.string.dt_scale_hum_oppressive, R.string.dt_scale_hum_oppressive_text),
        )
        Metric.SEA -> listOf(
            row(seaColor(0), R.string.dt_scale_sea_calm, R.string.dt_scale_sea_calm_text),
            row(seaColor(1), R.string.dt_scale_sea_rippled, R.string.dt_scale_sea_rippled_text),
            row(seaColor(2), R.string.dt_scale_sea_slight, R.string.dt_scale_sea_slight_text),
            row(seaColor(3), R.string.dt_scale_sea_moderate, R.string.dt_scale_sea_moderate_text),
            row(seaColor(4), R.string.dt_scale_sea_rough, R.string.dt_scale_sea_rough_text),
            row(seaColor(5), R.string.dt_scale_sea_very_rough, R.string.dt_scale_sea_very_rough_text),
            row(seaColor(6), R.string.dt_scale_sea_high, R.string.dt_scale_sea_high_text),
        )
        Metric.FIRE -> listOf(
            row(Color(0xFF4CAF50), R.string.dt_scale_fire_low, R.string.dt_scale_fire_low_text),
            row(Color(0xFFFFC107), R.string.dt_scale_fire_moderate, R.string.dt_scale_fire_moderate_text),
            row(Color(0xFFFF9800), R.string.dt_scale_fire_high, R.string.dt_scale_fire_high_text),
            row(Color(0xFFF44336), R.string.dt_scale_fire_very_high, R.string.dt_scale_fire_very_high_text),
            row(Color(0xFF7E0023), R.string.dt_scale_fire_extreme, R.string.dt_scale_fire_extreme_text),
        )
        Metric.WIND -> listOf(
            rowKmh(Color(0xFF4CAF50), "< 20 km/h", R.string.dt_scale_wind_light_text),
            rowKmh(Color(0xFFFFC107), "20–38 km/h", R.string.dt_scale_wind_moderate_text),
            rowKmh(Color(0xFFFF9800), "39–61 km/h", R.string.dt_scale_wind_strong_text),
            rowKmh(Color(0xFFF44336), "62+ km/h", R.string.dt_scale_wind_gale_text),
        )
        else -> emptyList()
    }
}
