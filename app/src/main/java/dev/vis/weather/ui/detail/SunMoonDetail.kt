@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.detail

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vis.weather.data.Astro
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Insights
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.components.SectionCard
import dev.vis.weather.ui.components.cardBackdrop
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Amber = Color(0xFFFFB627)
private val Dusk = Color(0xFFFF7A59)
private val Night = Color(0xFF5C6BC0)

private fun Forecast.lonOr0() = if (longitude.isNaN()) 0.0 else longitude

/** Today's sun events, computed on device. */
fun sunToday(f: Forecast, fmt: Fmt): Astro.SunDay = Astro.sunDay(fmt.localDate(f.current.time), f.latitude, f.lonOr0())

/** First light · sunrise · sunset · last light under the sun arc. */
@Composable
fun SunHeroTimes(f: Forecast, fmt: Fmt) {
    val s = remember(f) { sunToday(f, fmt) }
    val d = f.today
    val rise = d?.sunrise?.takeIf { it != 0L } ?: s.sunrise
    val set = d?.sunset?.takeIf { it != 0L } ?: s.sunset
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        TimeCol("First light", fmt.time(s.firstLight), Modifier.weight(1f), Alignment.Start)
        TimeCol("Sunrise", fmt.time(rise), Modifier.weight(1f), Alignment.CenterHorizontally, big = true)
        TimeCol("Sunset", fmt.time(set), Modifier.weight(1f), Alignment.CenterHorizontally, big = true)
        TimeCol("Last light", fmt.time(s.lastLight), Modifier.weight(1f), Alignment.End)
    }
}

@Composable
private fun TimeCol(label: String, value: String, modifier: Modifier, align: Alignment.Horizontal, big: Boolean = false) {
    Column(modifier, horizontalAlignment = align) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = if (big) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun SunSections(f: Forecast, fmt: Fmt) {
    val lat = f.latitude
    val lon = f.lonOr0()
    val today = fmt.localDate(f.current.time)
    val s = remember(f) { Astro.sunDay(today, lat, lon) }
    val year = remember(f.latitude, f.longitude, today.year) { Astro.yearDaylight(today.year, lat, lon) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---- Sun path today
        SectionCard("Sun today", Icons.Rounded.WbSunny, MaterialShapes.Sunny) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                SunCompass(s.riseAzimuth, s.setAzimuth, Modifier.size(120.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sunset direction", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(bearing(s.setAzimuth), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Spacer(Modifier.height(4.dp))
                    Body("Rises in the ${WeatherInfo.compass(s.riseAzimuth)} (${s.riseAzimuth.takeIf { !it.isNaN() }?.roundToInt() ?: "–"}°). Face ${WeatherInfo.compass(s.setAzimuth)} for the sunset.")
                }
            }
            Gap()
            StatChips(
                listOf(
                    Triple("Solar noon", fmt.time(s.solarNoon), "Sun at its highest"),
                    Triple("Max elevation", "${s.noonElevation.roundToInt()}°", elevationWord(s.noonElevation)),
                    Triple("Golden hour", if (s.sunset != 0L) fmt.time(s.sunset - 3600) else "–", "before sunset"),
                )
            )
            when {
                s.polarDay -> { Gap(10.dp); Body("Midnight sun: the sun stays above the horizon all day.", Modifier.padding(horizontal = 18.dp)) }
                s.polarNight -> { Gap(10.dp); Body("Polar night: the sun stays below the horizon today.", Modifier.padding(horizontal = 18.dp)) }
            }
        }

        // ---- Daylight change & annual curve
        val yest = Astro.daylight(today.minusDays(1), lat, lon)
        val week = Astro.daylight(today.minusDays(7), lat, lon)
        val dYest = s.daylight - yest
        val dWeek = s.daylight - week
        val longestIdx = year.indices.maxBy { year[it] }
        val shortestIdx = year.indices.minBy { year[it] }
        val jan1 = LocalDate.of(today.year, 1, 1)
        val (event, eventTime) = remember(f.current.time) { Astro.nextSeasonEvent(f.current.time, f.zone) }
        InsightCard(
            "Daylight", Icons.Rounded.WbTwilight, MaterialShapes.SoftBurst,
            if (abs(dYest) < 5) "About the same as yesterday" else "${signedMinutes(dYest)} vs yesterday",
            "${fmt.duration(s.daylight)} of daylight today. " + when {
                abs(dWeek) < 60 -> "Day length is barely changing this week."
                dWeek > 0 -> "Days are getting longer: ${signedMinutes(dWeek)} since last week."
                else -> "Days are getting shorter: ${signedMinutes(dWeek)} since last week."
            },
        ) {
            Gap()
            DaylightYearChart(year, today.dayOfYear - 1, Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 14.dp))
            Gap()
            StatChips(
                listOf(
                    Triple("Longest day", fmt.shortDate(jan1.plusDays(longestIdx.toLong())), fmt.duration(year[longestIdx])),
                    Triple("Shortest day", fmt.shortDate(jan1.plusDays(shortestIdx.toLong())), fmt.duration(year[shortestIdx])),
                    Triple(Astro.seasonName(event, lat), fmt.shortDate(fmt.localDate(eventTime)), fmt.inDays(eventTime, f.current.time)),
                )
            )
        }

        // ---- Seasonal highlights
        SectionCard("Seasonal highlights", Icons.Rounded.CalendarMonth, MaterialShapes.Clover4Leaf) {
            val events = remember(today.year) {
                (Astro.SeasonEvent.entries.map { it to Astro.seasonEvent(today.year, it) } +
                    Astro.SeasonEvent.entries.map { it to Astro.seasonEvent(today.year + 1, it) })
                    .filter { it.second > f.current.time }.take(4)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                events.forEach { (e, t) ->
                    val d = fmt.localDate(t)
                    FactRow(
                        Astro.seasonName(e, lat),
                        fmt.shortDate(d),
                        "${fmt.whenText(t, f.current.time)} · ${fmt.duration(Astro.daylight(d, lat, lon))} of daylight",
                        icon = if (e == Astro.SeasonEvent.JUNE_SOLSTICE || e == Astro.SeasonEvent.DECEMBER_SOLSTICE) Icons.Rounded.LightMode else Icons.Rounded.WbTwilight,
                        shape = if (e.ordinal % 2 == 0) MaterialShapes.Cookie6Sided else MaterialShapes.Sunny,
                    )
                }
            }
        }
    }
}

private fun elevationWord(e: Double) = when {
    e.isNaN() -> ""
    e < 0 -> "Below horizon"
    e < 15 -> "Very low sun"
    e < 35 -> "Low sun"
    e < 55 -> "Mid-height"
    else -> "High overhead"
}

/** Small compass with the sunrise and sunset bearings. */
@Composable
private fun SunCompass(riseAz: Double, setAz: Double, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold)
    Canvas(modifier) {
        val r = size.minDimension / 2 - 12.dp.toPx()
        val c = center
        drawCircle(cs.surfaceContainerHighest, r + 8.dp.toPx(), c)
        drawCircle(cs.outlineVariant, r, c, style = Stroke(1.dp.toPx()))
        listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (t, a) ->
            val m = measurer.measure(t, style)
            val rad = Math.toRadians(a.toDouble() - 90)
            val p = Offset(c.x + (r - 10.dp.toPx()) * kotlin.math.cos(rad).toFloat(), c.y + (r - 10.dp.toPx()) * kotlin.math.sin(rad).toFloat())
            drawText(m, topLeft = Offset(p.x - m.size.width / 2, p.y - m.size.height / 2))
        }
        fun ray(az: Double, col: Color) {
            if (az.isNaN()) return
            rotate(az.toFloat(), pivot = c) {
                drawLine(col, c, Offset(c.x, c.y - r), 4.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(col, 6.dp.toPx(), Offset(c.x, c.y - r))
            }
        }
        ray(riseAz, Amber)
        ray(setAz, Dusk)
        drawCircle(cs.onSurface, 3.dp.toPx(), c)
    }
}

/** Daylight length across the year, with today's position. */
@Composable
fun DaylightYearChart(values: List<Double>, todayIdx: Int, modifier: Modifier) {
    if (values.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = cs.onSurface, fontWeight = FontWeight.Bold)
    val ring = cardBackdrop()
    val line = Amber
    Canvas(modifier) {
        val bottom = size.height - 18.dp.toPx()
        val top = 22.dp.toPx()
        val lo = (values.min() / 3600).let { kotlin.math.floor(it) }.coerceAtLeast(0.0)
        val hi = (values.max() / 3600).let { kotlin.math.ceil(it) }.coerceAtMost(24.0).coerceAtLeast(lo + 1)
        fun x(i: Int) = i / (values.size - 1f) * size.width
        fun y(v: Double) = bottom - (((v / 3600) - lo) / (hi - lo)).toFloat() * (bottom - top)
        // 12 h reference line
        if (12.0 in lo..hi) {
            val yy = y(12.0 * 3600)
            drawLine(cs.outlineVariant, Offset(0f, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val m = measurer.measure("12 h", style)
            drawText(m, topLeft = Offset(size.width - m.size.width, yy - m.size.height - 2.dp.toPx()))
        }
        val path = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size step 2) lineTo(x(i), y(values[i]))
            lineTo(x(values.lastIndex), y(values.last()))
        }
        val fill = Path().apply { addPath(path); lineTo(size.width, bottom); lineTo(0f, bottom); close() }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.30f), Night.copy(alpha = 0.06f)), top, bottom))
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        // Month initials
        val months = (1..12).map { java.time.Month.of(it) }
        var day = 0
        months.forEach { mo ->
            val len = mo.length(values.size == 366)
            val t = mo.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            val m = measurer.measure(t, style)
            drawText(m, topLeft = Offset(x(day + len / 2) - m.size.width / 2, bottom + 3.dp.toPx()))
            day += len
        }
        val ti = todayIdx.coerceIn(0, values.lastIndex)
        val p = Offset(x(ti), y(values[ti]))
        drawLine(cs.primary.copy(alpha = 0.5f), Offset(p.x, top - 6.dp.toPx()), Offset(p.x, bottom), 1.5.dp.toPx())
        drawCircle(ring, 8.dp.toPx(), p)
        drawCircle(cs.primary, 6.dp.toPx(), p)
        val tm = measurer.measure("Today", labelStyle)
        drawText(tm, topLeft = Offset((p.x - tm.size.width / 2).coerceIn(0f, size.width - tm.size.width), 0f))
    }
}

// ---------------------------------------------------------------- Moon

@Composable
fun MoonHeroText(f: Forecast, fmt: Fmt) {
    val today = fmt.localDate(f.current.time)
    val ms = remember(f) { Astro.moonState(f.current.time, f.latitude, f.lonOr0()) }
    val md = remember(f) { Astro.moonDay(today, f.zone, f.latitude, f.lonOr0()) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(ms.name, style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "${pctOf(ms.illumination)} illuminated · " + (if (ms.waxing) "waxing" else "waning") +
                if (ms.altitude > 0) " · up now, ${ms.altitude.roundToInt()}° high in the ${WeatherInfo.compass(ms.azimuth)}" else " · below the horizon",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            TimeCol("Moonrise", if (md.rise != 0L) fmt.time(md.rise) else if (md.alwaysUp) "Up all day" else "–", Modifier.weight(1f), Alignment.Start, big = true)
            TimeCol("Moonset", if (md.set != 0L) fmt.time(md.set) else if (md.alwaysDown) "Down all day" else "–", Modifier.weight(1f), Alignment.End, big = true)
        }
    }
}

@Composable
fun MoonSections(f: Forecast, fmt: Fmt) {
    val now = f.current.time
    val ms = remember(f) { Astro.moonState(now, f.latitude, f.lonOr0()) }
    val full = remember(now) { Astro.nextFullMoon(now) }
    val new = remember(now) { Astro.nextNewMoon(now) }
    val star = remember(f) { Insights.stargazing(f) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (star != null) {
            val colors = listOf(Color(0xFF4CAF50), Color(0xFF35C7B0), Color(0xFFFFC107), Color(0xFF9AA6B5))
            InsightCard(
                "Stargazing tonight", Icons.Rounded.AutoAwesome, MaterialShapes.Sunny,
                star.rating, star.sentence, accent = colors[star.level],
            ) {
                Gap()
                LevelScale(colors.reversed(), 3 - star.level, labels = listOf("Poor", "Fair", "Good", "Excellent"))
                Gap()
                StatChips(
                    listOf(
                        Triple("Clouds", if (star.cloud.isNaN()) "–" else "${star.cloud.roundToInt()}%", "average tonight"),
                        Triple("Moonlight", pctOf(star.moonIllumination), if (star.moonUp < 0.1) "moon down" else "up ${(star.moonUp * 100).roundToInt()}% of night"),
                        Triple("Best time", if (star.best != 0L) fmt.time(star.best) else "–", "dark from ${fmt.time(star.darkStart)}"),
                    )
                )
            }
        }
        SectionCard("Moon facts", Icons.Rounded.DarkMode, MaterialShapes.Cookie9Sided) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FactRow("Next full moon", fmt.dateOf(full), fmt.inDays(full, now).replaceFirstChar { it.uppercase() } + " · " + fmt.time(full), Icons.Rounded.Nightlight, MaterialShapes.Circle)
                FactRow("Next new moon", fmt.dateOf(new), fmt.inDays(new, now).replaceFirstChar { it.uppercase() } + " · " + fmt.time(new), Icons.Rounded.Bedtime, MaterialShapes.Cookie6Sided)
                FactRow(
                    "Distance", "${fmt.km(ms.distanceKm)}",
                    when {
                        ms.distanceKm < 362_000 -> "Close to perigee — the moon looks a little bigger" + if (abs(ms.phase - 0.5) < 0.04) ". Supermoon!" else ""
                        ms.distanceKm > 402_000 -> "Near apogee — the moon looks a little smaller"
                        else -> "Average distance 384 400 km"
                    },
                    Icons.Rounded.Straighten, MaterialShapes.Pill,
                )
                FactRow(
                    "Position now",
                    if (ms.altitude > 0) "${ms.altitude.roundToInt()}° ${WeatherInfo.compass(ms.azimuth)}" else "Below horizon",
                    "Altitude and compass direction", Icons.Rounded.Explore, MaterialShapes.Gem,
                )
            }
        }
    }
}
