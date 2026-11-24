package dev.vis.weather.widget

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import dev.vis.weather.L
import dev.vis.weather.R
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.components.Metric
import kotlin.math.abs
import kotlin.math.roundToInt

/** What a "Data tile" widget shows; chosen per widget in [DataTileConfigActivity]. */
enum class TileMetric(@StringRes private val res: Int, val icon: ImageVector, val metric: Metric) {
    WIND(R.string.w_metric_wind, Icons.Rounded.Air, Metric.WIND),
    UV(R.string.w_metric_uv, Icons.Rounded.WbSunny, Metric.UV),
    AIR(R.string.w_metric_air, Icons.Rounded.Masks, Metric.AIR),
    FEELS(R.string.w_metric_feels, Icons.Rounded.Thermostat, Metric.FEELS),
    HUMIDITY(R.string.w_metric_humidity, Icons.Rounded.WaterDrop, Metric.HUMIDITY),
    PRESSURE(R.string.w_metric_pressure, Icons.Rounded.Compress, Metric.PRESSURE),
    RAIN(R.string.w_metric_rain, Icons.Rounded.Umbrella, Metric.PRECIP),
    SUN(R.string.w_metric_sun, Icons.Rounded.WbTwilight, Metric.SUN),
    MOON(R.string.w_metric_moon, Icons.Rounded.DarkMode, Metric.MOON),
    VISIBILITY(R.string.w_metric_visibility, Icons.Rounded.Visibility, Metric.VISIBILITY),
    ;

    val label: String get() = L.s(res)

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: WIND
    }
}

object TileKeys {
    val METRIC = stringPreferencesKey("tile_metric")
}

/** One metric ready to draw: a short title, the value (+ unit), a level pill and a note. */
class Reading(
    val title: String,
    val value: String,
    val unit: String = "",
    val pill: String? = null,
    val dot: Long? = null,
    val note: String? = null,
    val windDir: Double = Double.NaN,
)

fun TileMetric.read(d: WData): Reading {
    val c = d.f.current
    val f = d.fmt
    return when (this) {
        TileMetric.WIND -> Reading(
            L.s(R.string.w_tile_wind_from, WeatherInfo.compass(c.windDir)), f.windNum(c.windSpeed), f.windUnit,
            WeatherInfo.beaufort(c.windSpeed), note = if (c.windGust.isNaN()) null else L.s(R.string.w_tile_gusts, f.wind(c.windGust)), windDir = c.windDir,
        )
        TileMetric.UV -> Reading(
            label, if (c.uv.isNaN()) "–" else c.uv.roundToInt().toString(), pill = WeatherInfo.uvLevel(c.uv), dot = uvColor(c.uv),
            note = d.day?.uvMax?.takeIf { !it.isNaN() }?.let { L.s(R.string.w_tile_uv_max, it.roundToInt()) },
        )
        TileMetric.AIR -> {
            val aqi = d.f.air?.usAqi ?: Double.NaN
            if (aqi.isNaN()) Reading(label, "–", note = L.s(R.string.w_unavailable_here))
            else Reading(label, aqi.roundToInt().toString(), pill = aqiShort(aqi), dot = aqiColor(aqi), note = "PM2.5 ${d.f.air!!.pm25.roundToInt()} µg/m³")
        }
        TileMetric.FEELS -> {
            val diff = f.tempValue(d.feels) - f.tempValue(d.temp)
            Reading(
                label, f.temp(d.feels),
                pill = when { diff <= -2 -> L.s(R.string.w_tile_feels_colder); diff >= 2 -> L.s(R.string.w_tile_feels_warmer); else -> L.s(R.string.w_tile_feels_close) },
                note = L.s(R.string.w_note_actual, f.temp(d.temp)),
            )
        }
        TileMetric.HUMIDITY -> Reading(
            label, f.pct(c.humidity),
            pill = when {
                c.dewPoint.isNaN() -> null
                c.dewPoint < 10 -> L.s(R.string.w_tile_humidity_dry)
                c.dewPoint < 16 -> L.s(R.string.w_tile_humidity_comfortable)
                c.dewPoint < 21 -> L.s(R.string.w_tile_humidity_humid)
                else -> L.s(R.string.w_tile_humidity_muggy)
            },
            note = L.s(R.string.w_note_dew_point, f.temp(c.dewPoint)),
        )
        TileMetric.PRESSURE -> {
            val past = d.f.hourlyAll.minByOrNull { abs(it.time - (d.nowSec - 3 * 3600)) }?.pressure ?: Double.NaN
            val delta = c.pressure - past
            Reading(
                label, f.pressureNum(c.pressure), f.pressureUnit,
                pill = when { delta.isNaN() -> null; delta >= 1 -> L.s(R.string.w_tile_pressure_rising); delta <= -1 -> L.s(R.string.w_tile_pressure_falling); else -> L.s(R.string.w_tile_pressure_steady) },
                note = L.s(R.string.w_tile_pressure_note),
            )
        }
        TileMetric.RAIN -> {
            val next = d.hours.take(12)
            val peak = next.filter { !it.precipProb.isNaN() }.maxByOrNull { it.precipProb }
            val total = next.sumOf { if (it.precip.isNaN()) 0.0 else it.precip }
            val p = peak?.precipProb ?: Double.NaN
            Reading(
                label, f.pct(p),
                pill = when { p.isNaN() -> null; p >= 50 -> L.s(R.string.w_tile_rain_likely_at, f.hour(peak!!.time)); p >= 20 -> L.s(R.string.w_tile_rain_possible); else -> L.s(R.string.w_tile_rain_unlikely) },
                note = L.s(R.string.w_tile_rain_note, f.precip(total)),
            )
        }
        TileMetric.SUN -> {
            val today = d.day
            if (today == null) Reading(L.s(R.string.w_sunrise), "–")
            else {
                val now = d.nowSec
                val (title, at, other) = when {
                    now < today.sunrise -> Triple(L.s(R.string.w_sunrise), today.sunrise, L.s(R.string.w_note_sunset_at, f.time(today.sunset)))
                    now < today.sunset -> Triple(L.s(R.string.w_sunset), today.sunset, L.s(R.string.w_note_sunrise_at, f.time(today.sunrise)))
                    else -> {
                        val next = d.days.getOrNull(1)
                        Triple(L.s(R.string.w_sunrise), next?.sunrise ?: today.sunrise, L.s(R.string.w_note_sunset_at, f.time(next?.sunset ?: today.sunset)))
                    }
                }
                val t = f.time(at)
                Reading(title, t.substringBefore(' '), t.substringAfter(' ', ""), L.s(R.string.w_tile_daylight, f.duration(today.daylight)), note = other)
            }
        }
        TileMetric.MOON -> {
            val m = WeatherInfo.moon()
            val full = m.daysToFull.roundToInt()
            Reading(m.name, "${(m.illumination * 100).roundToInt()}%", pill = if (full == 0 || full >= 29) L.s(R.string.w_tile_moon_full_tonight) else L.p(R.plurals.w_tile_moon_full_in, full), note = L.s(R.string.w_tile_moon_illuminated))
        }
        TileMetric.VISIBILITY -> Reading(label, f.distanceNum(c.visibility), f.distanceUnit, WeatherInfo.visibilityLevel(c.visibility))
    }
}

/** "Data tile": one metric, big. */
class DataTileWidget : WeatherWidget() {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val metric = TileMetric.of(d.state?.get(TileKeys.METRIC))
        val r = metric.read(d)
        val open = openApp(d, metric.metric.name)
        if (size.height < 110.dp) {
            // 2x1 / 4x1: title and place on the left, the value on the right.
            Row(GlanceModifier.fillMaxSize().clickable(open), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Title(metric, r)
                    if (size.width >= 220.dp) PlaceLabel(d, 12.sp)
                }
                Value(r, min(size.height * 0.5f, 40.dp), size.width * 0.5f)
            }
            return
        }
        Column(
            GlanceModifier.fillMaxSize().clickable(open),
            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
        ) {
            Title(metric, r)
            Spacer(GlanceModifier.height(6.dp))
            Value(r, min(min(size.height * 0.34f, size.width * 0.34f), 72.dp), size.width - 48.dp)
            if (size.height >= 140.dp && r.pill != null) {
                Spacer(GlanceModifier.height(8.dp))
                Pill(r.pill, dot = r.dot, height = 26.dp)
            }
            if (r.note != null && (size.height >= 190.dp || (r.pill == null && size.height >= 140.dp))) {
                Spacer(GlanceModifier.height(6.dp))
                Txt(r.note, 12.sp, GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center)
            }
            if (size.height >= 130.dp) {
                Spacer(GlanceModifier.height(8.dp))
                PlaceLabel(d, 13.sp)
            }
        }
    }

    @Composable
    private fun Title(metric: TileMetric, r: Reading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sym(metric.icon, 16.dp)
            Spacer(GlanceModifier.width(6.dp))
            Txt(r.title, 13.sp, GlanceTheme.colors.onSurfaceVariant, FontWeight.Medium)
        }
    }

    @Composable
    private fun Value(r: Reading, size: androidx.compose.ui.unit.Dp, maxWidth: androidx.compose.ui.unit.Dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!r.windDir.isNaN()) {
                Image(
                    ImageProvider(WidgetArt.windArrow(r.windDir, dp2px(size * 0.42f))), wStr(R.string.w_wind_direction),
                    GlanceModifier.size(size * 0.42f), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                )
                Spacer(GlanceModifier.width(6.dp))
            }
            NumUnit(r.value, r.unit, size, maxWidth = maxWidth * (if (r.unit.isEmpty()) 1f else 0.7f))
        }
    }
}

