@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.detail

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vis.weather.data.AirQuality
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Hour
import dev.vis.weather.data.Indices
import dev.vis.weather.data.Insights
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.components.SectionCard
import java.time.LocalDate
import kotlin.math.roundToInt

private fun Double.ok() = !isNaN()

private fun Forecast.hoursOn(fmt: Fmt, date: LocalDate): List<Hour> = hourlyAll.filter { fmt.localDate(it.time) == date }

private val HumidityColors = listOf(0xFF4A9EFF, 0xFF35C7B0, 0xFF4CAF50, 0xFFFFC107, 0xFFFF9800, 0xFFF44336).map { Color(it) }
private val CoolingColors = listOf(0xFF4A9EFF, 0xFF4CAF50, 0xFFFFC107, 0xFFFF9800, 0xFFF44336, 0xFF7E0023).map { Color(it) }
private val FireColors = listOf(0xFF4CAF50, 0xFFFFC107, 0xFFFF9800, 0xFFF44336, 0xFF7E0023).map { Color(it) }

// ---------------------------------------------------------------- Humidity

@Composable
fun HumiditySections(f: Forecast, fmt: Fmt) {
    val today = fmt.localDate(f.current.time)
    val c = f.current
    val now = Indices.humidityComfort(c.dewPoint, c.humidity)
    val day = remember(f) { Insights.humidityDay(f, today) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InsightCard(
            "Humidity comfort", Icons.Rounded.WaterDrop, MaterialShapes.Cookie7Sided,
            day?.first ?: now.label,
            day?.second ?: now.text,
            accent = now.colour,
        ) {
            Gap()
            LevelScale(HumidityColors, now.level, labels = listOf("Dry", "Comfy", "Noticed", "Sticky", "Muggy", "Heavy"))
            Gap()
            Body("Right now: ${now.label.lowercase()} with a dew point of ${fmt.temp(c.dewPoint)}.", Modifier.padding(horizontal = 18.dp))
            Gap()
            OutlookList(
                f.daily.take(7).mapNotNull { d ->
                    val hs = f.hoursOn(fmt, d.date).filter { it.dewPoint.ok() }
                    val dew = hs.maxOfOrNull { it.dewPoint } ?: return@mapNotNull null
                    val b = Indices.humidityComfort(dew, hs.map { it.humidity }.filter { it.ok() }.average())
                    OutlookItem(fmt.dayName(d.date, today), d.code, b.label, fmt.temp(dew), b.colour, (b.level + 1) / 6f)
                }
            )
        }

        val wbNow = f.upcoming.firstOrNull()?.wetBulb ?: Indices.wetBulb(c.temp, c.humidity)
        val todayHours = f.hoursOn(fmt, today).filter { it.wetBulb.ok() }
        val peak = todayHours.maxByOrNull { it.wetBulb }
        val band = Indices.coolingComfort(wbNow, c.temp)
        val peakBand = peak?.let { Indices.coolingComfort(it.wetBulb, it.temp) }
        InsightCard(
            "Cooling comfort", Icons.Rounded.Thermostat, MaterialShapes.Cookie7Sided,
            band.label,
            band.text + if (peakBand != null && peakBand.level > band.level) " ${peakBand.label} around ${fmt.hour(peak.time)}." else "",
            accent = band.colour,
        ) {
            Gap()
            LevelScale(CoolingColors, band.level)
            Gap()
            StatChips(
                listOfNotNull(
                    Triple("Wet-bulb now", fmt.temp(wbNow), null),
                    peak?.let { Triple("Today's peak", fmt.temp(it.wetBulb), fmt.hour(it.time)) },
                    Triple("Feels like", fmt.temp(c.feelsLike), null),
                )
            )
            Gap()
            OutlookList(
                f.daily.take(7).mapNotNull { d ->
                    val hs = f.hoursOn(fmt, d.date).filter { it.wetBulb.ok() }
                    val p = hs.maxByOrNull { it.wetBulb } ?: return@mapNotNull null
                    val b = Indices.coolingComfort(p.wetBulb, d.max)
                    OutlookItem(fmt.dayName(d.date, today), d.code, b.label, fmt.temp(p.wetBulb), b.colour, (b.level + 1) / 6f)
                }
            )
            Gap()
            Note(Indices.COOLING_EXPLAINER)
        }
    }
}

// ---------------------------------------------------------------- Pressure

@Composable
fun PressureSections(f: Forecast, fmt: Fmt) {
    val trend = remember(f) { Indices.pressureTrend(f) } ?: return
    val today = fmt.localDate(f.current.time)
    val dailyMean = f.daily.take(7).mapNotNull { d ->
        val v = f.hoursOn(fmt, d.date).map { it.pressure }.filter { it.ok() }
        if (v.isEmpty()) null else d to v.average()
    }
    val low = dailyMean.minByOrNull { it.second }
    val high = dailyMean.maxByOrNull { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InsightCard(
            "3-hour trend", Icons.Rounded.Compress, MaterialShapes.Gem,
            "${trend.label} · ${pressureDelta(fmt, trend.change3h)}",
            trend.text,
            accent = if (trend.fast) Color(0xFFF44336) else MaterialTheme.colorScheme.primary,
        ) {
            if (trend.headache != null) {
                Gap()
                Note(trend.headache, icon = Icons.Rounded.HealthAndSafety)
            }
        }
        if (low != null && high != null) {
            val range = high.second - low.second
            InsightCard(
                "Week ahead", Icons.Rounded.CalendarMonth, MaterialShapes.Clover4Leaf,
                if (range < 6) "Fairly steady" else if (low.first.date.isBefore(high.first.date)) "Dipping, then rising" else "Rising, then dipping",
                if (range < 6) "Pressure stays within a few ${fmt.pressureUnit} all week — no big changes from pressure alone."
                else "Lowest ${fmt.dayName(low.first.date, today).lowercase()} (${fmt.pressureNum(low.second)} ${fmt.pressureUnit}): unsettled. " +
                    "Highest ${fmt.dayName(high.first.date, today).lowercase()} (${fmt.pressureNum(high.second)}): calmer weather likely.",
            )
        }
    }
}

// ---------------------------------------------------------------- Precipitation

@Composable
fun PrecipSections(f: Forecast, fmt: Fmt) {
    val p = remember(f) { Insights.precipitationToday(f) }
    val today = fmt.localDate(f.current.time)
    val types = remember(f) { Insights.precipTypes(f.hoursOn(fmt, today)) }
    val week = remember(f) { Insights.weekRain(f) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InsightCard(
            "Today", Icons.Rounded.Today, MaterialShapes.Clover8Leaf,
            p.headline,
            buildString {
                if (p.soFar >= 0.05) append("${fmt.precip(p.soFar)} so far. ")
                if (p.later >= 0.05) append("${fmt.precip(p.later)} more expected. ")
                append(p.laterText)
            },
            accent = Color(0xFF4A9EFF),
        ) {
            if (types.any) {
                Gap()
                StatChips(
                    listOfNotNull(
                        Triple("Rain", fmt.precip(types.rainMm), "incl. showers"),
                        if (types.snowCm >= 0.1) Triple("Snow", fmt.snow(types.snowCm), "fresh snow") else null,
                        if (types.iceMm >= 0.1) Triple("Ice", fmt.precip(types.iceMm), "freezing rain") else null,
                    )
                )
            }
            Gap()
            Note(Insights.precipGuidance(f), icon = Icons.Rounded.Umbrella)
        }
        InsightCard(
            "This week", Icons.Rounded.CalendarMonth, MaterialShapes.Clover4Leaf,
            if (week.total < 0.5) "A dry week" else "${fmt.precip(week.total)} in 7 days",
            when {
                week.wetDays == 0 -> "No day with more than 1 ${if (fmt.s.precipUnit == dev.vis.weather.data.PrecipUnit.IN) "mm (0.04 in)" else "mm"} expected."
                week.wettest != null -> "${week.wetDays} wet ${if (week.wetDays == 1) "day" else "days"}; wettest ${fmt.dayName(week.wettest.date, today).lowercase()} with ${fmt.precip(week.wettest.precipSum)}."
                else -> ""
            } + f.daily.take(7).sumOf { if (it.snowfallSum.ok()) it.snowfallSum else 0.0 }.let { s -> if (s >= 0.5) " Up to ${fmt.snow(s)} of snow in total." else "" },
        )
    }
}

// ---------------------------------------------------------------- UV

@Composable
fun UvSections(f: Forecast, fmt: Fmt) {
    val today = fmt.localDate(f.current.time)
    val w = remember(f) { Indices.uvWindow(f, today) }
    val tw = remember(f) { Indices.uvWindow(f, today.plusDays(1)) }
    if (w == null && tw == null) return
    InsightCard(
        "Sun protection", Icons.Rounded.WbSunny, MaterialShapes.Sunny,
        when {
            w == null -> "No UV data today"
            w.start == 0L -> "No protection needed today"
            else -> "Protect ${fmt.time(w.start)}–${fmt.time(w.end)}"
        },
        when {
            w == null || w.start == 0L -> "UV stays low. Sunglasses are still a good idea on bright days or near snow and water."
            else -> "Reduce time in the sun between ${fmt.time(w.start)} and ${fmt.time(w.end)}. Peak UV ${w.peak.roundToInt()} around ${fmt.time(w.peakTime)}."
        },
    ) {
        Gap()
        StatChips(
            listOfNotNull(
                w?.let { Triple("Peak today", "${it.peak.roundToInt()}", "${fmt.time(it.peakTime)} · ${dev.vis.weather.data.WeatherInfo.uvLevel(it.peak)}") },
                tw?.let { Triple("Peak tomorrow", "${it.peak.roundToInt()}", "${fmt.time(it.peakTime)} · ${dev.vis.weather.data.WeatherInfo.uvLevel(it.peak)}") },
                Triple("Now", if (f.current.uv.ok()) f.current.uv.roundToInt().toString() else "–", dev.vis.weather.data.WeatherInfo.uvLevel(f.current.uv)),
            )
        )
    }
}

// ---------------------------------------------------------------- Air quality

@Composable
fun AirSections(a: AirQuality, fmt: Fmt) {
    val eu = Indices.euAqiBand(a.euAqi)
    val us = Indices.usAqiBand(a.usAqi)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("Health", Icons.Rounded.HealthAndSafety, MaterialShapes.Cookie6Sided) {
            Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AqiBox("European AQI", a.euAqi, eu, Modifier.weight(1f))
                AqiBox("US AQI", a.usAqi, us, Modifier.weight(1f))
            }
            Gap()
            Body(us.text.ifEmpty { eu.text }, Modifier.padding(horizontal = 18.dp))
            if (eu.text.isNotEmpty() && eu.text != us.text) {
                Gap(6.dp)
                Body("EU guidance: ${eu.text}", Modifier.padding(horizontal = 18.dp))
            }
        }
        SectionCard("Pollutants vs WHO limits", Icons.Rounded.Science, MaterialShapes.Flower) {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Indices.pollutants(a).forEach { p ->
                    val st = Indices.whoStatus(p.value, p.limit)
                    val frac = if (p.value.ok()) (p.value / (p.limit * 2)).toFloat().coerceIn(0f, 1f) else 0f
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (st.level >= 0) BandPill(st.label, st.colour)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (p.value.isNaN()) "–" else if (p.value < 10) String.format("%.1f", p.value) else p.value.roundToInt().toString(),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                            Text(" µg/m³", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth(), color = st.colour)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${p.about} WHO guideline ${p.limit.roundToInt()} µg/m³.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AqiBox(label: String, v: Double, band: Indices.Band, modifier: Modifier) {
    Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = band.colour.copy(alpha = 0.18f)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (v.ok()) v.roundToInt().toString() else "–", style = MaterialTheme.typography.displaySmallEmphasized)
            Text(band.label, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- Clouds & visibility

@Composable
fun CloudSections(f: Forecast, fmt: Fmt) {
    val c = f.current
    val vis = Indices.visibilityRating(c.visibility)
    val clearest = f.upcoming.take(24).filter { it.cloudCover.ok() }.minByOrNull { it.cloudCover }
    InsightCard("Sky outlook", Icons.Rounded.Cloud, MaterialShapes.Bun, cloudLabel(c.cloudCover), Indices.cloudGuidance(c.cloudCover, f)) {
        Gap()
        StatChips(
            listOfNotNull(
                Triple("Visibility", fmt.distanceNum(c.visibility) + " " + fmt.distanceUnit, vis.label),
                f.today?.sunshine?.takeIf { it.ok() }?.let { Triple("Sunshine today", fmt.duration(it), null) },
                clearest?.let { Triple("Clearest", fmt.hour(it.time), "${it.cloudCover.roundToInt()}% cloud") },
            )
        )
        if (vis.level >= 3) {
            Gap()
            Note(vis.text, icon = Icons.Rounded.Visibility)
        }
    }
}

private fun cloudLabel(cover: Double) = when {
    cover.isNaN() -> "–"
    cover < 12 -> "Clear sky"
    cover < 37 -> "Mostly clear"
    cover < 62 -> "Partly cloudy"
    cover < 87 -> "Mostly cloudy"
    else -> "Overcast"
}

@Composable
fun VisibilitySections(f: Forecast, fmt: Fmt) {
    val v = f.current.visibility
    val b = Indices.visibilityRating(v)
    val worst = f.upcoming.take(24).filter { it.visibility.ok() }.minByOrNull { it.visibility }
    InsightCard(
        "Visibility rating", Icons.Rounded.Visibility, MaterialShapes.Oval, b.label, b.text, accent = b.colour,
    ) {
        if (worst != null && worst.visibility < 4000 && worst.visibility < v) {
            Gap()
            Note("Lowest around ${fmt.hour(worst.time)}: ${fmt.distanceNum(worst.visibility)} ${fmt.distanceUnit} (${Indices.visibilityRating(worst.visibility).label.lowercase()}).")
        }
    }
}

// ---------------------------------------------------------------- Feels like / temperature trend

@Composable
fun FeelsSections(f: Forecast) {
    val y = remember(f) { Insights.compareYesterday(f) }
    val t = remember(f) { Insights.compareTomorrow(f) }
    if (y == null && t == null) return
    InsightCard(
        "Compared with yesterday", Icons.Rounded.Thermostat, MaterialShapes.Arch,
        y ?: "–",
        t?.let { "Tomorrow: ${it.replaceFirstChar { c -> c.lowercase() }}" } ?: "",
    )
}

// ---------------------------------------------------------------- Fire

fun fireSeries(f: Forecast): List<Double> = f.upcoming.map { Indices.fireIndex(it) }

@Composable
fun FireHero(f: Forecast, fmt: Fmt) {
    val v = f.upcoming.firstOrNull()?.let { Indices.fireIndex(it) } ?: Double.NaN
    val b = Indices.fireBand(v)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { ((if (v.ok()) v else 0.0) / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.matchParentSize(),
                color = b.colour,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v.ok()) v.roundToInt().toString() else "–", style = MaterialTheme.typography.displayLargeEmphasized)
                Text("FFWI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(b.label, style = MaterialTheme.typography.titleLargeEmphasized)
        Spacer(Modifier.height(6.dp))
        Text(b.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun FireSections(f: Forecast, fmt: Fmt) {
    val today = fmt.localDate(f.current.time)
    val todays = f.hoursOn(fmt, today).filter { it.time >= f.current.time - 3600 }.ifEmpty { f.upcoming.take(12) }
    val peak = todays.maxByOrNull { Indices.fireIndex(it).let { v -> if (v.isNaN()) -1.0 else v } }
    val pv = peak?.let { Indices.fireIndex(it) } ?: Double.NaN
    val pb = Indices.fireBand(pv)
    val c = f.current
    InsightCard(
        "Today's peak", Icons.Rounded.LocalFireDepartment, MaterialShapes.SoftBurst,
        if (peak == null) "–" else "${pb.label} around ${fmt.hour(peak.time)}",
        pb.text, accent = pb.colour,
    ) {
        Gap()
        LevelScale(FireColors, pb.level, labels = listOf("Low", "Mod.", "High", "V. high", "Extreme"))
        Gap()
        StatChips(
            listOf(
                Triple("Temperature", fmt.temp(c.temp), null),
                Triple("Humidity", fmt.pct(c.humidity), if (c.humidity.ok() && c.humidity < 30) "very dry" else null),
                Triple("Wind", fmt.wind(c.windSpeed), "gusts ${fmt.windNum(c.windGust)}"),
            )
        )
        Gap()
        Note(Indices.FIRE_EXPLAINER, icon = Icons.Rounded.LocalFireDepartment)
    }
}

/** Signed pressure change in the user's unit, e.g. "−2.4 hPa in 3 h". */
fun pressureDelta(fmt: Fmt, hpa: Double): String {
    val v = fmt.pressureValue(kotlin.math.abs(hpa))
    val n = if (fmt.pressureUnit == "inHg") String.format("%.2f", v) else String.format("%.1f", v)
    return "${if (hpa >= 0) "+" else "−"}$n ${fmt.pressureUnit} in 3 h"
}
