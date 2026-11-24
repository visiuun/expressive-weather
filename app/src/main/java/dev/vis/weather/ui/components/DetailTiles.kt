@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.components

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import dev.vis.weather.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.toShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.DetailTile
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Indices
import dev.vis.weather.data.Insights
import dev.vis.weather.data.MarineInfo
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.detail.DataSourceLine
import dev.vis.weather.ui.detail.seaColor
import dev.vis.weather.ui.detail.snow
import dev.vis.weather.ui.detail.waveNum
import dev.vis.weather.ui.detail.waveUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
private fun Tile(
    title: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    modifier: Modifier = Modifier,
    metric: Metric? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val open = LocalOpenMetric.current
    AppCard(
        modifier.heightIn(min = 176.dp),
        shape = LocalTileShape.current,
        onClick = metric?.let { m -> { open(m) } },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(icon, shape, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun BigValue(value: String, unit: String = "") {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.displaySmallEmphasized, maxLines = 1)
        if (unit.isNotEmpty()) {
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

val LocalTileShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(32.dp) }

private val Big = 32.dp
private val Small = 10.dp
private val LeftTile = RoundedCornerShape(Big, Small, Small, Big)
private val RightTile = RoundedCornerShape(Small, Big, Big, Small)

/** Two tiles joined like an M3 Expressive connected group: round outside, tight inside. */
@Composable
private fun TileRow(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompositionLocalProvider(LocalTileShape provides LeftTile) { left(Modifier.weight(1f).fillMaxHeight()) }
        CompositionLocalProvider(LocalTileShape provides RightTile) { right(Modifier.weight(1f).fillMaxHeight()) }
    }
}

@Composable
fun DetailGrid(f: Forecast, fmt: Fmt) {
    val c = f.current
    // Tiles in the user's order (Settings → Layout), skipping hidden ones and those with nothing to say.
    val tiles = remember(f, fmt.s.tileOrder, fmt.s.hiddenTiles) {
        val today = fmt.localDate(f.current.time)
        fmt.s.tileOrder.filter { it !in fmt.s.hiddenTiles }.filter { t ->
            when (t) {
                DetailTile.FIRE -> f.upcoming.take(24).any { !Indices.fireIndex(it).isNaN() }
                DetailTile.COOLING -> (f.hourlyAll.filter { fmt.localDate(it.time) == today }.map { it.wetBulb }.filter { !it.isNaN() }.maxOrNull() ?: 0.0) >= 20
                DetailTile.SEA -> f.marine?.at(f.current.time) != null
                DetailTile.AIR -> f.air != null
                DetailTile.POLLEN -> f.air?.pollen?.isNotEmpty() == true
                else -> true
            }
        }
    }
    @Composable
    fun Small(t: DetailTile, m: Modifier) = when (t) {
        DetailTile.WIND -> WindTile(f, fmt, m)
        DetailTile.HUMIDITY -> HumidityTile(f, fmt, m)
        DetailTile.UV -> UvTile(f, m)
        DetailTile.FEELS -> FeelsLikeTile(f, fmt, m)
        DetailTile.PRESSURE -> PressureTile(f, fmt, m)
        DetailTile.VISIBILITY -> VisibilityTile(c.visibility, fmt, m)
        DetailTile.PRECIP -> PrecipTile(f, fmt, m)
        DetailTile.CLOUD -> CloudTile(c.cloudCover, m)
        DetailTile.TODAY_PRECIP -> TodayPrecipTile(f, fmt, m)
        DetailTile.FIRE -> FireTile(f, fmt, m)
        DetailTile.COOLING -> CoolingTile(f, fmt, m)
        else -> {}
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Small tiles pair up two by two; a wide tile (or the end) leaves an unpaired one spanning the full width.
        var pending: DetailTile? = null
        @Composable
        fun flush() {
            pending?.let { Small(it, Modifier.fillMaxWidth()) }
            pending = null
        }
        tiles.forEach { t ->
            if (!t.wide) {
                val p = pending
                if (p == null) pending = t else {
                    key(p, t) { TileRow({ Small(p, it) }, { Small(t, it) }) }
                    pending = null
                }
            } else {
                flush()
                key(t) {
                    Spacer(Modifier.height(8.dp))
                    when (t) {
                        DetailTile.SUN -> SunTile(f, fmt)
                        DetailTile.MOON -> MoonTile(f, fmt)
                        DetailTile.SEA -> SeaTile(f, fmt)
                        DetailTile.AIR -> f.air?.let { AirQualityTile(it) }
                        DetailTile.POLLEN -> f.air?.let { PollenTile(it.pollen) }
                        else -> {}
                    }
                }
            }
        }
        flush()
        if (fmt.s.showDataSource) {
            Spacer(Modifier.height(8.dp))
            DataSourceLine(f, fmt, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }
}

// ---- Wind

@Composable
private fun WindTile(f: Forecast, fmt: Fmt, modifier: Modifier) {
    val c = f.current
    Tile(stringResource(R.string.m_metric_wind), Icons.Rounded.Air, MaterialShapes.Cookie6Sided, modifier, metric = Metric.WIND) {
        val heading by rememberHeading(f.latitude, if (f.longitude.isNaN()) 0.0 else f.longitude)
        BigValue(fmt.windNum(c.windSpeed), fmt.windUnit)
        Caption(stringResource(R.string.m_wind_from_gusts, WeatherInfo.compass(c.windDir), fmt.windNum(c.windGust)))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LiveCompass(c.windDir, heading, Modifier.size(112.dp))
        }
        Spacer(Modifier.height(10.dp))
        Caption(WeatherInfo.beaufort(c.windSpeed))
    }
}

// ---- Humidity

@Composable
private fun HumidityTile(f: Forecast, fmt: Fmt, modifier: Modifier) {
    val c = f.current
    Tile(stringResource(R.string.m_metric_humidity), Icons.Rounded.WaterDrop, MaterialShapes.Cookie7Sided, modifier, metric = Metric.HUMIDITY) {
        BigValue(if (c.humidity.isNaN()) "–" else c.humidity.roundToInt().toString(), "%")
        Caption(stringResource(R.string.m_dew_point, fmt.temp(c.dewPoint)))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(12.dp))
        val slosh = rememberSlosh()
        WaterFill(
            fraction = ((if (c.humidity.isNaN()) 0.0 else c.humidity) / 100).toFloat(),
            slosh = slosh,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.height(8.dp))
        Caption(Indices.humidityComfort(c.dewPoint, c.humidity).label)
    }
}

// ---- UV

@Composable
private fun UvTile(f: Forecast, modifier: Modifier) {
    val uv = f.current.uv
    val max = f.today?.uvMax ?: Double.NaN
    val ring = cardBackdrop()
    val dot = MaterialTheme.colorScheme.onSurface
    Tile(stringResource(R.string.m_metric_uv), Icons.Rounded.WbSunny, MaterialShapes.Sunny, modifier, metric = Metric.UV) {
        BigValue(if (uv.isNaN()) "–" else uv.roundToInt().toString())
        Text(WeatherInfo.uvLevel(uv), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val h = size.height
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFF44336), Color(0xFF9C27B0))),
                size = size,
                cornerRadius = CornerRadius(h / 2),
            )
            if (!uv.isNaN()) {
                val x = (uv / 11.0).toFloat().coerceIn(0f, 1f) * size.width
                drawCircle(ring, h * 0.95f, Offset(x, h / 2))
                drawCircle(dot, h * 0.6f, Offset(x, h / 2))
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption(if (max.isNaN()) WeatherInfo.uvAdvice(uv) else stringResource(R.string.m_uv_today_max, max.roundToInt(), WeatherInfo.uvAdvice(max)))
    }
}

// ---- Feels like

@Composable
private fun FeelsLikeTile(f: Forecast, fmt: Fmt, modifier: Modifier) {
    val c = f.current
    val diff = c.feelsLike - c.temp
    Tile(stringResource(R.string.m_metric_feels), Icons.Rounded.Thermostat, MaterialShapes.Arch, modifier, metric = Metric.FEELS) {
        BigValue(fmt.temp(c.feelsLike))
        Caption(stringResource(R.string.m_feels_actual, fmt.temp(c.temp)))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        Caption(
            when {
                diff.isNaN() -> ""
                diff < -1.5 && c.windSpeed > 10 -> stringResource(R.string.m_feels_wind_colder)
                diff < -1.5 -> stringResource(R.string.m_feels_colder)
                diff > 1.5 && c.humidity > 60 -> stringResource(R.string.m_feels_humid_warmer)
                diff > 1.5 -> stringResource(R.string.m_feels_warmer)
                else -> stringResource(R.string.m_feels_similar)
            }
        )
    }
}

// ---- Pressure

@Composable
private fun PressureTile(f: Forecast, fmt: Fmt, modifier: Modifier) {
    val p = f.current.pressure
    val trend = remember(f) { Indices.pressureTrend(f) }
    val cs = MaterialTheme.colorScheme
    Tile(stringResource(R.string.m_metric_pressure), Icons.Rounded.Compress, MaterialShapes.Gem, modifier, metric = Metric.PRESSURE) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.size(140.dp, 112.dp)) {
                val stroke = 10.dp.toPx()
                // 240° arc: its height is 0.75 × diameter
                val d = minOf(size.width - stroke, (size.height - stroke) / 0.75f)
                val tl = Offset((size.width - d) / 2, stroke / 2)
                val start = 150f
                val sweep = 240f
                drawArc(cs.surfaceContainerHighest, start, sweep, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                if (!p.isNaN()) {
                    val frac = ((p - 960) / (1060 - 960)).toFloat().coerceIn(0f, 1f)
                    drawArc(cs.primary, start, sweep * frac, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                    val ang = Math.toRadians((start + sweep * frac).toDouble())
                    val ctr = Offset(tl.x + d / 2, tl.y + d / 2)
                    val pos = Offset(ctr.x + d / 2 * cos(ang).toFloat(), ctr.y + d / 2 * sin(ang).toFloat())
                    drawCircle(cs.onPrimary, stroke * 0.3f, pos)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 40.dp)) {
                Text(fmt.pressureNum(p), style = MaterialTheme.typography.headlineMediumEmphasized)
                Text(fmt.pressureUnit, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (trend != null) {
            Text(trend.label, style = MaterialTheme.typography.titleMedium, color = if (trend.fast) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurface)
            Caption(if (trend.headache != null && trend.fast) stringResource(R.string.m_pressure_headache) else stringResource(R.string.m_pressure_last_3h))
        }
    }
}

// ---- Visibility

@Composable
private fun VisibilityTile(m: Double, fmt: Fmt, modifier: Modifier) {
    Tile(stringResource(R.string.m_metric_visibility), Icons.Rounded.Visibility, MaterialShapes.Oval, modifier, metric = Metric.VISIBILITY) {
        BigValue(fmt.distanceNum(m), fmt.distanceUnit)
        Text(WeatherInfo.visibilityLevel(m), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        LinearWavyProgressIndicator(
            progress = { ((if (m.isNaN()) 0.0 else m) / 30000).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

// ---- Precipitation

@Composable
private fun PrecipTile(f: Forecast, fmt: Fmt, modifier: Modifier) {
    val next24 = f.upcoming.take(24).sumOf { if (it.precip.isNaN()) 0.0 else it.precip }
    val nowIdx = f.hourlyAll.indexOfLast { it.time <= f.current.time }
    val last24 = if (nowIdx > 0) f.hourlyAll.subList(maxOf(0, nowIdx - 23), nowIdx + 1).sumOf { if (it.precip.isNaN()) 0.0 else it.precip } else Double.NaN
    Tile(stringResource(R.string.m_metric_precip), Icons.Rounded.Grain, MaterialShapes.Clover8Leaf, modifier, metric = Metric.PRECIP) {
        BigValue(fmt.precip(next24).substringBefore(' '), fmt.precip(next24).substringAfter(' ', ""))
        Caption(stringResource(R.string.m_precip_next_24h))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        Caption(stringResource(R.string.m_precip_last_24h, fmt.precip(last24)))
        f.today?.let { if (!it.precipProbMax.isNaN()) Caption(stringResource(R.string.m_precip_chance_today, it.precipProbMax.roundToInt())) }
    }
}

// ---- Cloud cover

@Composable
private fun CloudTile(cover: Double, modifier: Modifier) {
    Tile(stringResource(R.string.m_metric_cloud), Icons.Rounded.Cloud, MaterialShapes.Bun, modifier, metric = Metric.CLOUD) {
        Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { ((if (cover.isNaN()) 0.0 else cover) / 100).toFloat() },
                modifier = Modifier.size(96.dp),
            )
            Text(if (cover.isNaN()) "–" else "${cover.roundToInt()}%", style = MaterialTheme.typography.titleLargeEmphasized)
        }
        Spacer(Modifier.height(6.dp))
        Caption(
            when {
                cover.isNaN() -> ""
                cover < 12 -> stringResource(R.string.m_cloud_clear)
                cover < 37 -> stringResource(R.string.m_cloud_mostly_clear)
                cover < 62 -> stringResource(R.string.m_cloud_partly)
                cover < 87 -> stringResource(R.string.m_cloud_mostly_cloudy)
                else -> stringResource(R.string.m_cloud_overcast)
            }
        )
    }
}

// ---- Today's precipitation

@Composable
private fun TodayPrecipTile(f: Forecast, fmt: Fmt, modifier: Modifier) {
    val p = remember(f) { Insights.precipitationToday(f) }
    Tile(stringResource(R.string.m_tile_today_precip), Icons.Rounded.Umbrella, MaterialShapes.Clover4Leaf, modifier, metric = Metric.PRECIP) {
        val amount = fmt.precip(p.soFar)
        BigValue(amount.substringBefore(' '), amount.substringAfter(' ', ""))
        Text(p.headline, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        if (p.snowSoFar + p.snowLater >= 0.1) Caption(stringResource(R.string.m_snow_today, fmt.snow(p.snowSoFar + p.snowLater)))
        Caption(if (p.later >= 0.05) stringResource(R.string.m_precip_more_later, fmt.precip(p.later)) else p.laterText)
    }
}

// ---- Fire weather

@Composable
private fun FireTile(f: Forecast, fmt: Fmt, modifier: Modifier) {
    val now = f.upcoming.firstOrNull()?.let { Indices.fireIndex(it) } ?: Double.NaN
    val band = Indices.fireBand(now)
    val today = fmt.localDate(f.current.time)
    val peak = f.hourlyAll.filter { fmt.localDate(it.time) == today && it.time >= f.current.time - 3600 }
        .maxByOrNull { Indices.fireIndex(it).let { v -> if (v.isNaN()) -1.0 else v } }
    val peakV = peak?.let { Indices.fireIndex(it) } ?: Double.NaN
    val ring = cardBackdrop()
    val dot = MaterialTheme.colorScheme.onSurface
    Tile(stringResource(R.string.m_metric_fire), Icons.Rounded.LocalFireDepartment, MaterialShapes.SoftBurst, modifier, metric = Metric.FIRE) {
        BigValue(if (now.isNaN()) "–" else now.roundToInt().toString())
        Text(band.label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val h = size.height
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFF44336), Color(0xFF7E0023))),
                size = size, cornerRadius = CornerRadius(h / 2),
            )
            if (!now.isNaN()) {
                val x = (now / 100).toFloat().coerceIn(0f, 1f) * size.width
                drawCircle(ring, h * 0.95f, Offset(x, h / 2))
                drawCircle(dot, h * 0.6f, Offset(x, h / 2))
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption(
            if (peak != null && !peakV.isNaN() && Indices.fireBand(peakV).level > band.level) stringResource(R.string.m_fire_peak_by, Indices.fireBand(peakV).label, fmt.hour(peak.time))
            else stringResource(R.string.m_fire_scale)
        )
    }
}

// ---- Cooling comfort

@Composable
private fun CoolingTile(f: Forecast, fmt: Fmt, modifier: Modifier) {
    val c = f.current
    val wb = f.upcoming.firstOrNull()?.wetBulb ?: Indices.wetBulb(c.temp, c.humidity)
    val band = Indices.coolingComfort(wb, c.temp)
    val today = fmt.localDate(c.time)
    val peak = f.hourlyAll.filter { fmt.localDate(it.time) == today && !it.wetBulb.isNaN() }.maxByOrNull { it.wetBulb }
    Tile(stringResource(R.string.m_tile_cooling), Icons.Rounded.Thermostat, MaterialShapes.Cookie7Sided, modifier, metric = Metric.HUMIDITY) {
        BigValue(fmt.temp(wb))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(MaterialShapes.Cookie4Sided.toShape()).background(Color(band.color)))
            Spacer(Modifier.width(6.dp))
            Text(band.label, style = MaterialTheme.typography.titleMedium)
        }
        Caption(stringResource(R.string.m_wet_bulb_temp))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        if (peak != null) {
            val pb = Indices.coolingComfort(peak.wetBulb, peak.temp)
            Caption(if (pb.level > band.level) stringResource(R.string.m_cooling_peak_around, pb.label, fmt.hour(peak.time)) else band.text.substringBefore('.') + ".")
        }
    }
}

// ---- Sea

@Composable
private fun SeaTile(f: Forecast, fmt: Fmt) {
    val m = f.marine ?: return
    val now = m.at(f.current.time) ?: return
    val state = MarineInfo.seaState(now.waveHeight)
    val hours = remember(m, f.current.time) { m.hourly.filter { it.time > f.current.time - 3600 }.take(24) }
    val tides = remember(m, f.current.time) { if (m.hasTides) MarineInfo.tides(m.hourly.filter { it.time >= f.current.time - 3 * 3600 }.take(36)) else emptyList() }
    val nextTide = tides.firstOrNull { it.time > f.current.time }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Tile(stringResource(R.string.m_metric_sea), Icons.Rounded.Waves, MaterialShapes.Clover8Leaf, Modifier.fillMaxWidth(), metric = Metric.SEA) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(76.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(seaColor(state.level).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt.waveNum(now.waveHeight), style = MaterialTheme.typography.headlineMediumEmphasized)
                    Text(fmt.waveUnit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(state.label, style = MaterialTheme.typography.titleLargeEmphasized)
                Caption(
                    listOfNotNull(
                        now.seaTemp.takeIf { !it.isNaN() }?.let { stringResource(R.string.m_sea_water, fmt.temp(it)) },
                        now.swellPeriod.takeIf { !it.isNaN() }?.let { stringResource(R.string.m_sea_swell, it.roundToInt()) },
                        now.waveDir.takeIf { !it.isNaN() }?.let { stringResource(R.string.m_sea_from, WeatherInfo.compass(it)) },
                    ).joinToString(" · ")
                )
            }
        }
        if (hours.size >= 6 && hours.any { !it.waveHeight.isNaN() }) {
            Spacer(Modifier.height(12.dp))
            val hi = maxOf(hours.maxOf { if (it.waveHeight.isNaN()) 0.0 else it.waveHeight }, 0.5)
            Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                val n = hours.size
                val bw = size.width / n * 0.55f
                hours.forEachIndexed { i, h ->
                    val x = size.width * (i + 0.5f) / n - bw / 2
                    drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                    if (!h.waveHeight.isNaN()) {
                        val hh = maxOf((h.waveHeight / hi).toFloat() * size.height, bw)
                        drawRoundRect(seaColor(MarineInfo.seaState(h.waveHeight).level), Offset(x, size.height - hh), Size(bw, hh), CornerRadius(bw / 2))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.m_next_24_hours), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val cur = MarineInfo.current(now.currentSpeed)
        val bits = listOfNotNull(
            nextTide?.let { if (it.high) stringResource(R.string.m_tide_high, fmt.time(it.time)) else stringResource(R.string.m_tide_low, fmt.time(it.time)) },
            if (cur.caution) stringResource(R.string.m_sea_current_caution) else null,
        )
        if (bits.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Caption(bits.joinToString(" · "))
        }
    }
}

// ---- Sun

@Composable
private fun SunTile(f: Forecast, fmt: Fmt) {
    val d = f.today ?: return
    val now = f.current.time
    val cs = MaterialTheme.colorScheme
    val progress = if (d.sunset > d.sunrise) ((now - d.sunrise).toFloat() / (d.sunset - d.sunrise)).coerceIn(0f, 1f) else 0f
    val tomorrow = f.daily.getOrNull(1)
    val dayDelta = if (tomorrow != null && !tomorrow.daylight.isNaN() && !d.daylight.isNaN()) (tomorrow.daylight - d.daylight) / 60 else Double.NaN
    Tile(stringResource(R.string.m_metric_sun), Icons.Rounded.WbTwilight, MaterialShapes.SoftBurst, Modifier.fillMaxWidth(), metric = Metric.SUN) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val pad = 16.dp.toPx()
            val w = size.width - pad * 2
            val baseY = size.height - 10.dp.toPx()
            val h = size.height - 26.dp.toPx()
            fun pt(t: Float): Offset {
                val x = pad + w * t
                val y = baseY - h * sin(PI * t).toFloat()
                return Offset(x, y)
            }
            drawLine(cs.outlineVariant, Offset(0f, baseY), Offset(size.width, baseY), 1.dp.toPx())
            val track = Path().apply {
                moveTo(pt(0f).x, pt(0f).y)
                for (i in 1..60) pt(i / 60f).let { lineTo(it.x, it.y) }
            }
            drawPath(track, cs.outline, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))))
            if (progress > 0f) {
                val done = Path().apply {
                    moveTo(pt(0f).x, pt(0f).y)
                    val steps = (60 * progress).toInt().coerceAtLeast(1)
                    for (i in 1..steps) pt(progress * i / steps).let { lineTo(it.x, it.y) }
                }
                drawPath(done, Color(0xFFFFB627), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }
            if (now in d.sunrise..d.sunset) {
                val p = pt(progress)
                drawCircle(Color(0xFFFFB627).copy(alpha = 0.25f), 16.dp.toPx(), p)
                drawCircle(Color(0xFFFFB627), 9.dp.toPx(), p)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Caption(stringResource(R.string.m_sunrise))
                Text(fmt.time(d.sunrise), style = MaterialTheme.typography.titleLargeEmphasized)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Caption(stringResource(R.string.m_daylight))
                Text(fmt.duration(d.daylight), style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Caption(stringResource(R.string.m_sunset))
                Text(fmt.time(d.sunset), style = MaterialTheme.typography.titleLargeEmphasized)
            }
        }
        Spacer(Modifier.height(8.dp))
        val sunshine = if (d.sunshine.isNaN()) "" else stringResource(R.string.m_sunshine_today, fmt.duration(d.sunshine))
        val delta = if (dayDelta.isNaN()) "" else {
            val m = dayDelta.roundToInt()
            when {
                m > 0 -> pluralStringResource(R.plurals.m_daylight_longer, m, m)
                m < 0 -> pluralStringResource(R.plurals.m_daylight_shorter, -m, -m)
                else -> stringResource(R.string.m_daylight_same)
            }
        }
        Caption(listOf(sunshine, delta).filter { it.isNotEmpty() }.joinToString(" "))
    }
}

// ---- Moon

@Composable
private fun MoonTile(f: Forecast, fmt: Fmt) {
    val moon = WeatherInfo.moon(f.current.time)
    val lat = f.latitude
    Tile(stringResource(R.string.m_metric_moon), Icons.Rounded.DarkMode, MaterialShapes.Cookie9Sided, Modifier.fillMaxWidth(), metric = Metric.MOON) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonPhase(moon.phase, southern = lat < 0, modifier = Modifier.size(84.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(moon.name, style = MaterialTheme.typography.headlineSmallEmphasized)
                Caption(stringResource(R.string.m_moon_illuminated, (moon.illumination * 100).roundToInt()))
                val today = fmt.localDate(f.current.time)
                Caption(
                    if (moon.daysToFull < moon.daysToNew) {
                        stringResource(R.string.m_moon_full_on, fmt.shortDate(today.plusDays(moon.daysToFull.roundToInt().toLong())))
                    } else {
                        stringResource(R.string.m_moon_new_on, fmt.shortDate(today.plusDays(moon.daysToNew.roundToInt().toLong())))
                    }
                )
            }
        }
    }
}

// ---- Air quality

@Composable
private fun AirQualityTile(air: dev.vis.weather.data.AirQuality) {
    val band = WeatherInfo.usAqi(if (air.usAqi.isNaN()) 0.0 else air.usAqi)
    val ring = cardBackdrop()
    val dot = MaterialTheme.colorScheme.onSurface
    Tile(stringResource(R.string.m_metric_air), Icons.Rounded.Masks, MaterialShapes.Flower, Modifier.fillMaxWidth(), metric = Metric.AIR) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(Color(band.color).copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (air.usAqi.isNaN()) "–" else air.usAqi.roundToInt().toString(),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(band.label, style = MaterialTheme.typography.titleLargeEmphasized)
                Caption(if (!air.euAqi.isNaN()) stringResource(R.string.m_aqi_us_eu, air.euAqi.roundToInt(), WeatherInfo.euAqi(air.euAqi)) else stringResource(R.string.m_aqi_us))
            }
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val h = size.height
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(Color(0xFF4CAF50), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFF44336), Color(0xFF9C27B0), Color(0xFF7E0023))
                ),
                size = size,
                cornerRadius = CornerRadius(h / 2),
            )
            if (!air.usAqi.isNaN()) {
                val x = (air.usAqi / 300).toFloat().coerceIn(0f, 1f) * size.width
                drawCircle(ring, h * 0.95f, Offset(x, h / 2))
                drawCircle(dot, h * 0.6f, Offset(x, h / 2))
            }
        }
        Spacer(Modifier.height(10.dp))
        Caption(band.advice)
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3,
        ) {
            val m = Modifier.weight(1f)
            Pollutant("PM2.5", air.pm25, m)
            Pollutant("PM10", air.pm10, m)
            Pollutant("O₃", air.o3, m)
            Pollutant("NO₂", air.no2, m)
            Pollutant("SO₂", air.so2, m)
            Pollutant("CO", air.co, m)
        }
        Spacer(Modifier.height(6.dp))
        Text("µg/m³ · Copernicus CAMS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Pollutant(label: String, v: Double, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (v.isNaN()) "–" else if (v < 10) String.format("%.1f", v) else v.roundToInt().toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---- Pollen

@Composable
private fun PollenTile(pollen: List<Pair<String, Double>>) {
    Tile(stringResource(R.string.m_metric_pollen), Icons.Rounded.LocalFlorist, MaterialShapes.Clover4Leaf, Modifier.fillMaxWidth(), metric = Metric.POLLEN) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            pollen.forEach { (name, v) ->
                val (level, frac) = WeatherInfo.pollenLevel(v)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(80.dp))
                    LinearWavyProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.weight(1f),
                        color = when {
                            frac <= 0.25f -> Color(0xFF4CAF50)
                            frac <= 0.5f -> Color(0xFFFFC107)
                            frac <= 0.75f -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        },
                    )
                    Text(
                        level,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(76.dp).padding(start = 10.dp),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.m_pollen_footer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
