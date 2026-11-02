@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.Day
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Hour
import dev.vis.weather.data.Settings
import dev.vis.weather.data.Sky
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.components.AppCard
import dev.vis.weather.ui.components.ConnectedChoice
import dev.vis.weather.ui.components.HourlyGraph
import dev.vis.weather.ui.components.HourlyMetric
import dev.vis.weather.ui.components.LocalTileShape
import dev.vis.weather.ui.components.MoonPhase
import dev.vis.weather.ui.components.MorphingBlob
import dev.vis.weather.ui.components.SectionCard
import dev.vis.weather.ui.components.ShapeBadge
import dev.vis.weather.ui.components.WeatherIcon
import dev.vis.weather.ui.components.heroColors
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val RainBlue = Color(0xFF4A9EFF)

@Composable
fun DayScreen(
    forecast: Forecast,
    settings: Settings,
    index: Int,
    onSelectDay: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val fmt = remember(settings, forecast.zone) { Fmt(settings, forecast.zone) }
    val days = forecast.daily
    val i = index.coerceIn(0, days.lastIndex)
    val today = days.first().date

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(10.dp))
            AnimatedContent(i, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "title") { di ->
                val d = days[di]
                Column {
                    Text(
                        when (d.date) {
                            today -> "Today"
                            today.plusDays(1) -> "Tomorrow"
                            else -> d.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                        },
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                    Text(
                        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()).format(d.date),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DayStrip(days, i, fmt, onSelectDay)
        val inSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val outSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            i,
            transitionSpec = { fadeIn(inSpec) togetherWith fadeOut(outSpec) },
            label = "day",
        ) { di ->
            DayContent(forecast, days[di], fmt)
        }
    }
}

@Composable
private fun DayStrip(days: List<Day>, selected: Int, fmt: Fmt, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { state.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    val today = days.first().date
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(days) { idx, d ->
            val sel = idx == selected
            val corner by animateDpAsState(if (sel) 22.dp else 32.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "c")
            val bg by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.motionScheme.fastEffectsSpec(), label = "bg",
            )
            val fg = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Surface(
                onClick = { onSelect(idx) },
                shape = RoundedCornerShape(corner),
                color = bg,
                contentColor = fg,
            ) {
                Column(
                    Modifier.width(64.dp).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (d.date == today) "Today" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(d.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
                    Spacer(Modifier.height(6.dp))
                    WeatherIcon(d.code, true, Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(fmt.temp(d.max), style = MaterialTheme.typography.titleSmallEmphasized)
                    Text(fmt.temp(d.min), style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.7f))
                }
            }
        }
    }
}

private fun avg(v: List<Double>): Double = v.filter { !it.isNaN() }.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN
private fun minOf(v: List<Double>): Double = v.filter { !it.isNaN() }.minOrNull() ?: Double.NaN
private fun maxOf(v: List<Double>): Double = v.filter { !it.isNaN() }.maxOrNull() ?: Double.NaN

@Composable
private fun DayContent(f: Forecast, d: Day, fmt: Fmt) {
    val hours = remember(f, d) { f.hourlyAll.filter { fmt.localDate(it.time) == d.date } }
    var metric by rememberSaveable { mutableStateOf(HourlyMetric.TEMP) }
    val cs = MaterialTheme.colorScheme

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

        item(key = "hero") { DayHero(d, hours, fmt, w) }

        if (hours.isNotEmpty()) {
            item(key = "graph") {
                Box(w) {
                    SectionCard("Through the day", Icons.Rounded.Schedule, MaterialShapes.Cookie4Sided) {
                        ConnectedChoice(
                            options = HourlyMetric.entries,
                            selected = metric,
                            label = { it.label },
                            onSelect = { metric = it },
                            modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        HourlyGraph(hours, fmt, metric, firstIsNow = false)
                    }
                }
            }
        }

        item(key = "stats") { Box(w) { DayStats(f, d, hours, fmt) } }

        if (hours.isNotEmpty()) {
            item(key = "hhdr") {
                Row(w.padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(Icons.Rounded.ViewTimeline, MaterialShapes.Pill, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Hour by hour", style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }
            itemsIndexed(hours, key = { _, h -> "h${h.time}" }) { idx, h ->
                HourRow(h, fmt, idx, hours.size, w, highlight = h.time <= f.current.time && f.current.time < h.time + 3600)
            }
        }
        item(key = "src") {
            Text(
                "Hourly model data · Open-Meteo.com",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DayHero(d: Day, hours: List<Hour>, fmt: Fmt, modifier: Modifier) {
    val sky = WeatherInfo.sky(d.code)
    val (blob, _) = heroColors(sky, true)
    val shapes = run {
        if (sky == Sky.CLEAR || sky == Sky.PARTLY) listOf(MaterialShapes.SoftBurst, MaterialShapes.Sunny, MaterialShapes.Cookie12Sided)
        else listOf(MaterialShapes.Puffy, MaterialShapes.Cookie9Sided, MaterialShapes.Clover8Leaf)
    }
    Column(modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(WeatherInfo.describe(d.code), style = MaterialTheme.typography.titleLargeEmphasized)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmt.temp(d.max),
                        style = MaterialTheme.typography.displayLargeEmphasized.copy(fontSize = 84.sp, lineHeight = 88.sp, letterSpacing = (-3).sp),
                    )
                    Text(
                        " / ${fmt.temp(d.min)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
                Text(
                    "Feels like ${fmt.temp(d.feelsMax)} / ${fmt.temp(d.feelsMin)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                MorphingBlob(shapes, blob, Modifier.matchParentSize())
                WeatherIcon(d.code, true, Modifier.size(88.dp), animated = true)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(daySummary(d, hours, fmt), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun daySummary(d: Day, hours: List<Hour>, fmt: Fmt): String {
    val parts = mutableListOf<String>()
    val wet = hours.filter { !it.precipProb.isNaN() && it.precipProb >= 40 }
    if (wet.isNotEmpty()) {
        parts += "Precipitation most likely ${fmt.hour(wet.first().time)}–${fmt.hour(wet.last().time + 3600)}" +
            (if (!d.precipSum.isNaN() && d.precipSum > 0) ", ${fmt.precip(d.precipSum)} in total." else ".")
    } else if (!d.precipProbMax.isNaN() && d.precipProbMax < 20) {
        parts += "Dry day expected."
    }
    val warmest = hours.filter { !it.temp.isNaN() }.maxByOrNull { it.temp }
    val coldest = hours.filter { !it.temp.isNaN() }.minByOrNull { it.temp }
    if (warmest != null && coldest != null) {
        parts += "Warmest around ${fmt.hour(warmest.time)}, coolest around ${fmt.hour(coldest.time)}."
    }
    if (!d.windMax.isNaN()) {
        parts += "Wind up to ${fmt.wind(d.windMax)} from the ${WeatherInfo.compass(d.windDir)}" +
            (if (!d.gustMax.isNaN() && d.gustMax > d.windMax) ", gusts ${fmt.wind(d.gustMax)}." else ".")
    }
    return parts.joinToString(" ")
}

// ---- stats grid

private val Big = 32.dp
private val Small = 10.dp

@Composable
private fun Pair2(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CompositionLocalProvider(LocalTileShape provides RoundedCornerShape(Big, Small, Small, Big)) { left(Modifier.weight(1f).fillMaxHeight()) }
        CompositionLocalProvider(LocalTileShape provides RoundedCornerShape(Small, Big, Big, Small)) { right(Modifier.weight(1f).fillMaxHeight()) }
    }
}

@Composable
private fun Stat(
    title: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    value: String,
    unit: String,
    caption: String,
    modifier: Modifier,
) {
    AppCard(modifier.heightIn(min = 132.dp), shape = LocalTileShape.current) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(icon, shape, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineLargeEmphasized, maxLines = 1)
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                    )
                }
            }
            if (caption.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun split(s: String): Pair<String, String> = s.substringBefore(' ') to s.substringAfter(' ', "")

@Composable
private fun DayStats(f: Forecast, d: Day, hours: List<Hour>, fmt: Fmt) {
    val hum = hours.map { it.humidity }
    val dew = hours.map { it.dewPoint }
    val pres = hours.map { it.pressure }
    val vis = hours.map { it.visibility }
    val cloud = hours.map { it.cloudCover }
    val dash = "–"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Pair2(
            {
                val (v, u) = split(fmt.precip(d.precipSum))
                Stat(
                    "Precipitation", Icons.Rounded.Grain, MaterialShapes.Clover8Leaf, v, u,
                    listOfNotNull(
                        d.precipProbMax.takeIf { !it.isNaN() }?.let { "${it.roundToInt()}% chance" },
                        d.precipHours.takeIf { !it.isNaN() && it > 0 }?.let { "${it.roundToInt()} h of rain" },
                    ).joinToString(" · "),
                    it,
                )
            },
            {
                Stat(
                    "Wind", Icons.Rounded.Air, MaterialShapes.Cookie6Sided, fmt.windNum(d.windMax), fmt.windUnit,
                    (if (!d.gustMax.isNaN() && d.gustMax > d.windMax) "Gusts ${fmt.wind(d.gustMax)} · " else "") +
                        "${WeatherInfo.compass(d.windDir)}\n${WeatherInfo.beaufort(d.windMax)}",
                    it,
                )
            },
        )
        Pair2(
            {
                Stat(
                    "UV max", Icons.Rounded.WbSunny, MaterialShapes.Sunny,
                    if (d.uvMax.isNaN()) dash else d.uvMax.roundToInt().toString(), "",
                    "${WeatherInfo.uvLevel(d.uvMax)}. ${WeatherInfo.uvAdvice(d.uvMax)}",
                    it,
                )
            },
            {
                val (v, u) = split(fmt.duration(d.sunshine))
                Stat(
                    "Sunshine", Icons.Rounded.WbTwilight, MaterialShapes.SoftBurst, v, u,
                    if (!d.daylight.isNaN() && !d.sunshine.isNaN() && d.daylight > 0)
                        "${(d.sunshine / d.daylight * 100).roundToInt()}% of ${fmt.duration(d.daylight)} daylight" else "",
                    it,
                )
            },
        )
        Pair2(
            {
                val a = avg(hum)
                Stat(
                    "Humidity", Icons.Rounded.WaterDrop, MaterialShapes.Puffy,
                    if (a.isNaN()) dash else a.roundToInt().toString(), "%",
                    "Range ${fmt.pct(minOf(hum))}–${fmt.pct(maxOf(hum))}",
                    it,
                )
            },
            {
                val a = avg(dew)
                Stat(
                    "Dew point", Icons.Rounded.DeviceThermostat, MaterialShapes.Arch, fmt.temp(a), "",
                    when {
                        a.isNaN() -> ""
                        a < 10 -> "Dry and comfortable"
                        a < 16 -> "Comfortable"
                        a < 20 -> "Slightly humid"
                        a < 24 -> "Humid and muggy"
                        else -> "Oppressive"
                    },
                    it,
                )
            },
        )
        Pair2(
            {
                Stat(
                    "Pressure", Icons.Rounded.Compress, MaterialShapes.Gem, fmt.pressureNum(avg(pres)), fmt.pressureUnit,
                    "Range ${fmt.pressureNum(minOf(pres))}–${fmt.pressureNum(maxOf(pres))}",
                    it,
                )
            },
            {
                val m = minOf(vis)
                Stat(
                    "Visibility", Icons.Rounded.Visibility, MaterialShapes.Oval, fmt.distanceNum(m), fmt.distanceUnit,
                    "Lowest of the day · ${WeatherInfo.visibilityLevel(m)}",
                    it,
                )
            },
        )
        Pair2(
            {
                val a = avg(cloud)
                Stat(
                    "Cloud cover", Icons.Rounded.Cloud, MaterialShapes.Bun,
                    if (a.isNaN()) dash else a.roundToInt().toString(), "%",
                    "Average over the day",
                    it,
                )
            },
            {
                Stat(
                    "Feels like", Icons.Rounded.Thermostat, MaterialShapes.Cookie4Sided,
                    fmt.temp(d.feelsMax), "",
                    "Low ${fmt.temp(d.feelsMin)}",
                    it,
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        SunMoon(f, d, fmt)
    }
}

@Composable
private fun SunMoon(f: Forecast, d: Day, fmt: Fmt) {
    val noon = d.date.atTime(12, 0).toEpochSecond(ZoneOffset.UTC)
    val moon = WeatherInfo.moon(noon)
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(Icons.Rounded.WbTwilight, MaterialShapes.SoftBurst, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text("Sun & moon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Sunrise", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt.time(d.sunrise), style = MaterialTheme.typography.titleLargeEmphasized)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Daylight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt.duration(d.daylight), style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Sunset", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt.time(d.sunset), style = MaterialTheme.typography.titleLargeEmphasized)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonPhase(moon.phase, southern = f.latitude < 0, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(moon.name, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        "${(moon.illumination * 100).roundToInt()}% illuminated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.DarkMode, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- hour list

@Composable
private fun HourRow(h: Hour, fmt: Fmt, index: Int, count: Int, modifier: Modifier, highlight: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = segmentShape(index, count),
        color = if (highlight) cs.secondaryContainer else cs.surfaceContainerLow,
        contentColor = if (highlight) cs.onSecondaryContainer else cs.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                fmt.hour(h.time),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.width(56.dp),
                maxLines = 1,
            )
            WeatherIcon(h.code, h.isDay, Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    WeatherInfo.describe(h.code, h.isDay),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${fmt.wind(h.windSpeed)} ${WeatherInfo.compass(h.windDir)} · ${fmt.pct(h.humidity)} RH",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (!h.precipProb.isNaN() && h.precipProb >= 5) "${h.precipProb.roundToInt()}%" else "",
                style = MaterialTheme.typography.labelMedium,
                color = RainBlue,
                modifier = Modifier.width(40.dp),
            )
            Text(fmt.temp(h.temp), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.width(40.dp))
        }
    }
}
