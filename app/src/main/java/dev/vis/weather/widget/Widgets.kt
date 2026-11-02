package dev.vis.weather.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import dev.vis.weather.R
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.components.Metric
import kotlin.math.roundToInt

// ---- Now: current conditions

class NowWidget : WeatherWidget() {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val open = openApp(d)
        if (size.height < 100.dp) {
            // 2x1 / 3x1 / 4x1 pill
            Row(GlanceModifier.fillMaxSize().clickable(open), verticalAlignment = Alignment.CenterVertically) {
                WIcon(d.code, d.isDay, min(size.height - 12.dp, 44.dp), d.s.monoIcons)
                Spacer(GlanceModifier.width(8.dp))
                Txt(d.fmt.temp(d.temp), 28.sp, weight = FontWeight.Medium)
                if (size.width >= 170.dp) {
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Txt(d.desc, 13.sp, weight = FontWeight.Medium)
                        Txt(if (size.width >= 240.dp) "${d.model.place.name} · ${d.hiLo}" else d.hiLo, 11.sp, GlanceTheme.colors.onSurfaceVariant)
                    }
                    if (size.width >= 240.dp) RefreshButton(d, 18.dp)
                }
            }
            return
        }
        val wide = size.width >= 220.dp
        Column(GlanceModifier.fillMaxSize()) {
            Header(d)
            Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(open), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = if (wide) min(size.height * 0.3f, 64.dp) else min(size.width * 0.32f, 48.dp)
                    WIcon(d.code, d.isDay, icon, d.s.monoIcons)
                    Spacer(GlanceModifier.width(8.dp))
                    Txt(d.fmt.temp(d.temp), if (wide) 44.sp else 32.sp)
                    if (wide) {
                        Spacer(GlanceModifier.width(12.dp))
                        Column {
                            Txt(d.desc, 14.sp, weight = FontWeight.Medium)
                            Txt("${d.hiLo}  · Feels ${d.fmt.temp(d.feels)}", 12.sp, GlanceTheme.colors.onSurfaceVariant)
                        }
                    }
                }
                if (!wide) {
                    Txt(d.desc, 13.sp, weight = FontWeight.Medium)
                    Txt(d.hiLo, 12.sp, GlanceTheme.colors.onSurfaceVariant)
                }
            }
            if (size.height >= 170.dp && wide) {
                Spacer(GlanceModifier.height(6.dp))
                val n = ((size.width - 24.dp) / 52.dp).toInt().coerceIn(3, 7)
                HourStrip(d, d.hours.take(n), modifier = GlanceModifier.fillMaxWidth().clickable(open))
            }
        }
    }
}

// ---- Hourly: pageable strip

class HourlyWidget : WeatherWidget() {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val perPage = ((size.width - 72.dp) / 50.dp).toInt().coerceIn(3, 8)
        val pages = ((d.hours.size + perPage - 1) / perPage).coerceAtLeast(1)
        val page = d.page.coerceIn(0, pages - 1)
        val slice = d.hours.drop(page * perPage).take(perPage)
        Column(GlanceModifier.fillMaxSize()) {
            Header(d) {
                WIcon(d.code, d.isDay, 20.dp, d.s.monoIcons)
                Spacer(GlanceModifier.width(4.dp))
                Txt(d.fmt.temp(d.temp), 15.sp, weight = FontWeight.Medium)
                Spacer(GlanceModifier.width(4.dp))
            }
            if (size.height >= 150.dp) {
                Txt(
                    "${d.desc} · ${d.hiLo}", 12.sp, GlanceTheme.colors.onSurfaceVariant,
                    modifier = GlanceModifier.padding(start = 2.dp, bottom = 4.dp),
                )
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                PageArrow(R.drawable.ic_w_chevron_left, "Earlier", page > 0, page - 1)
                HourStrip(
                    d, slice, if (size.height >= 150.dp) 30.dp else 24.dp,
                    GlanceModifier.defaultWeight().clickable(openApp(d)),
                )
                PageArrow(R.drawable.ic_w_chevron_right, "Later", page < pages - 1, page + 1)
            }
            if (size.height >= 150.dp && slice.isNotEmpty()) {
                Txt(
                    "${d.fmt.dayName(d.fmt.localDate(slice.first().time), d.today)} · page ${page + 1}/$pages",
                    10.sp, GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center,
                )
            }
        }
    }

    @Composable
    private fun PageArrow(res: Int, desc: String, enabled: Boolean, target: Int) {
        val m = GlanceModifier.width(26.dp).fillMaxHeight()
        Box(
            if (enabled) m.clickable(actionRunCallback<SetPageAction>(actionParametersOf(PageParam to target))) else m,
            contentAlignment = Alignment.Center,
        ) {
            if (enabled) Glyph(res, 22.dp, GlanceTheme.colors.primary, desc)
        }
    }
}

// ---- Daily: scrollable 16-day list

class DailyWidget : WeatherWidget() {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val days = d.days.take(16)
        val lo = days.minOfOrNull { it.min } ?: 0.0
        val hi = days.maxOfOrNull { it.max } ?: 1.0
        val bar = if (size.width >= 290.dp) (size.width - 260.dp).coerceAtMost(110.dp) else 0.dp
        Column(GlanceModifier.fillMaxSize()) {
            Header(d) {
                WIcon(d.code, d.isDay, 20.dp, d.s.monoIcons)
                Spacer(GlanceModifier.width(4.dp))
                Txt(d.fmt.temp(d.temp), 15.sp, weight = FontWeight.Medium)
                Spacer(GlanceModifier.width(4.dp))
            }
            Spacer(GlanceModifier.height(4.dp))
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(days.indices.toList(), itemId = { it.toLong() }) { i -> DayRow(d, i, lo, hi, bar) }
            }
        }
    }
}

// ---- Forecast: tabbed, interactive

class ForecastWidget : WeatherWidget() {
    private val tabs = listOf("Hourly", "Daily", "Details")

    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val tab = d.tab.coerceIn(0, tabs.lastIndex)
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(GlanceModifier.defaultWeight().clickable(openApp(d)), verticalAlignment = Alignment.CenterVertically) {
                    WIcon(d.code, d.isDay, 52.dp, d.s.monoIcons)
                    Spacer(GlanceModifier.width(10.dp))
                    Txt(d.fmt.temp(d.temp), 40.sp)
                    Spacer(GlanceModifier.width(10.dp))
                    Column {
                        Txt(d.desc, 14.sp, weight = FontWeight.Medium)
                        Txt(d.hiLo, 12.sp, GlanceTheme.colors.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    PlaceChip(d, 12.sp)
                    RefreshButton(d, 18.dp)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, t ->
                    Chip(t, i == tab, actionRunCallback<SetTabAction>(actionParametersOf(TabParam to i)), GlanceModifier.defaultWeight())
                    if (i < tabs.lastIndex) Spacer(GlanceModifier.width(6.dp))
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            val m = GlanceModifier.fillMaxWidth().defaultWeight()
            when (tab) {
                0 -> LazyColumn(m) {
                    items(d.hours.indices.toList(), itemId = { it.toLong() }) { i -> HourRow(d, i) }
                }
                1 -> {
                    val days = d.days
                    val lo = days.minOfOrNull { it.min } ?: 0.0
                    val hi = days.maxOfOrNull { it.max } ?: 1.0
                    val bar = if (size.width >= 290.dp) (size.width - 260.dp).coerceAtMost(110.dp) else 0.dp
                    LazyColumn(m) { items(days.indices.toList(), itemId = { it.toLong() }) { i -> DayRow(d, i, lo, hi, bar) } }
                }
                else -> {
                    val rows = detailTiles(d)
                    LazyColumn(m) {
                        items(rows.indices.toList(), itemId = { it.toLong() }) { i ->
                            val t = rows[i]
                            Row(
                                GlanceModifier.fillMaxWidth().height(34.dp).clickable(openApp(d, t.metric.name)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Txt(t.label, 13.sp, GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.defaultWeight())
                                Txt(t.value, 14.sp, weight = FontWeight.Medium)
                                if (t.sub.isNotEmpty()) Txt("  ${t.sub}", 12.sp, GlanceTheme.colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HourRow(d: WData, i: Int) {
        val h = d.hours[i]
        Row(GlanceModifier.fillMaxWidth().height(34.dp).clickable(openApp(d)), verticalAlignment = Alignment.CenterVertically) {
            Txt(if (i == 0) "Now" else d.fmt.hour(h.time), 13.sp, weight = FontWeight.Medium, modifier = GlanceModifier.width(58.dp))
            WIcon(h.code, h.isDay, 22.dp, d.s.monoIcons)
            Spacer(GlanceModifier.width(8.dp))
            Txt(WeatherInfo.describe(h.code, h.isDay), 12.sp, GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.defaultWeight())
            Txt(if (h.precipProb >= 10) d.fmt.pct(h.precipProb) else "", 11.sp, GlanceTheme.colors.primary, modifier = GlanceModifier.width(40.dp), align = TextAlign.End)
            Txt(d.fmt.temp(h.temp), 14.sp, weight = FontWeight.Medium, modifier = GlanceModifier.width(40.dp), align = TextAlign.End)
        }
    }
}

// ---- Clock: time, date and weather

class ClockWidget : WeatherWidget() {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val context = LocalContext.current
        val stacked = size.height >= 160.dp && size.width < 300.dp
        val clockSp = when {
            stacked -> (size.width.value / 4.2f).coerceIn(36f, 72f)
            else -> (size.height.value / 2.4f).coerceIn(30f, 72f)
        }
        val clock = clockViews(context, d, clockSp)
        if (stacked) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                AndroidRemoteViews(clock, GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.height(8.dp))
                WeatherSummary(d, Alignment.Start)
            }
        } else {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                AndroidRemoteViews(clock, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                WeatherSummary(d, Alignment.End)
            }
        }
    }

    @Composable
    private fun WeatherSummary(d: WData, align: Alignment.Horizontal) {
        Column(GlanceModifier.clickable(openApp(d)), horizontalAlignment = align) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WIcon(d.code, d.isDay, 36.dp, d.s.monoIcons)
                Spacer(GlanceModifier.width(6.dp))
                Txt(d.fmt.temp(d.temp), 30.sp)
            }
            Txt(d.desc, 13.sp, weight = FontWeight.Medium)
            Txt(d.hiLo, 12.sp, GlanceTheme.colors.onSurfaceVariant)
            PlaceChip(d, 12.sp)
        }
    }

    private fun clockViews(context: Context, d: WData, clockSp: Float): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_clock)
        val fmt = if (d.s.use24h) "HH:mm" else "h:mm"
        rv.setCharSequence(R.id.clock_time, "setFormat12Hour", fmt)
        rv.setCharSequence(R.id.clock_time, "setFormat24Hour", fmt)
        rv.setTextViewTextSize(R.id.clock_time, TypedValue.COMPLEX_UNIT_SP, clockSp)
        rv.setTextViewTextSize(R.id.clock_date, TypedValue.COMPLEX_UNIT_SP, (clockSp / 4.2f).coerceIn(12f, 18f))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        rv.setOnClickPendingIntent(
            R.id.clock_time,
            PendingIntent.getActivity(context, 1, Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags),
        )
        rv.setOnClickPendingIntent(
            R.id.clock_date,
            PendingIntent.getActivity(
                context, 2,
                Intent(Intent.ACTION_VIEW, "content://com.android.calendar/time/${System.currentTimeMillis()}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags,
            ),
        )
        return rv
    }
}

// ---- Details: tile grid

class Tile(val label: String, val value: String, val sub: String, val metric: Metric, val windDir: Double = Double.NaN)

fun detailTiles(d: WData): List<Tile> {
    val c = d.f.current
    val f = d.fmt
    val h = d.hours.firstOrNull()
    val air = d.f.air
    return buildList {
        add(Tile("Feels like", f.temp(d.feels), "", Metric.FEELS))
        add(Tile("Wind", f.windNum(c.windSpeed), "${f.windUnit} ${WeatherInfo.compass(c.windDir)}", Metric.WIND, c.windDir))
        add(Tile("Humidity", f.pct(c.humidity), "Dew ${f.temp(c.dewPoint)}", Metric.HUMIDITY))
        add(Tile("UV index", if (c.uv.isNaN()) "–" else c.uv.roundToInt().toString(), WeatherInfo.uvLevel(c.uv), Metric.UV))
        if (air != null && !air.usAqi.isNaN()) add(Tile("Air quality", air.usAqi.roundToInt().toString(), WeatherInfo.usAqi(air.usAqi).label.substringBefore(" for"), Metric.AIR))
        add(Tile("Rain chance", f.pct(h?.precipProb ?: Double.NaN), d.day?.let { f.precip(it.precipSum) } ?: "", Metric.PRECIP))
        add(Tile("Pressure", f.pressureNum(c.pressure), f.pressureUnit, Metric.PRESSURE))
        add(Tile("Visibility", f.distanceNum(c.visibility), f.distanceUnit, Metric.VISIBILITY))
        d.day?.let { add(Tile("Sunrise", f.time(it.sunrise), "Set ${f.time(it.sunset)}", Metric.SUN)) }
        add(Tile("Cloud cover", f.pct(c.cloudCover), "", Metric.CLOUD))
        add(Tile("Moon", "${(WeatherInfo.moon().illumination * 100).roundToInt()}%", WeatherInfo.moon().name, Metric.MOON))
    }
}

class DetailsWidget : WeatherWidget() {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val cols = ((size.width + 6.dp) / 96.dp).toInt().coerceIn(2, 5)
        val rows = ((size.height - 34.dp + 6.dp) / 68.dp).toInt().coerceIn(1, 4)
        val tiles = detailTiles(d).take(cols * rows)
        Column(GlanceModifier.fillMaxSize()) {
            Header(d)
            // Gaps come from padding: Glance caps Rows/Columns at 10 children.
            tiles.chunked(cols).forEach { line ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight().padding(vertical = 3.dp)) {
                    line.forEach { t -> TileView(d, t) }
                    repeat(cols - line.size) { Box(GlanceModifier.defaultWeight()) {} }
                }
            }
        }
    }

    @Composable
    private fun RowScope.TileView(d: WData, t: Tile) {
        Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 3.dp)) {
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(16.dp).background(GlanceTheme.colors.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp).clickable(openApp(d, t.metric.name)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(t.label, 11.sp, GlanceTheme.colors.onSecondaryContainer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!t.windDir.isNaN()) {
                    Image(
                        ImageProvider(WidgetArt.windArrow(t.windDir, dp2px(14.dp))), "Wind direction",
                        GlanceModifier.size(14.dp), colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.primary),
                    )
                    Spacer(GlanceModifier.width(3.dp))
                }
                Txt(t.value, 18.sp, GlanceTheme.colors.onSecondaryContainer, FontWeight.Bold)
            }
            if (t.sub.isNotEmpty()) Txt(t.sub, 11.sp, GlanceTheme.colors.onSecondaryContainer)
        }
        }
    }
}

// ---- Rain: nowcast + chart

class RainWidget : WeatherWidget() {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val n = if (size.width >= 320.dp) 24 else 12
        val hours = d.hours.take(n)
        Column(GlanceModifier.fillMaxSize().clickable(openApp(d, Metric.PRECIP.name))) {
            Header(d) {
                Glyph(R.drawable.ic_w_drop, 16.dp)
                Spacer(GlanceModifier.width(4.dp))
            }
            Txt(rainSummary(d), 14.sp, weight = FontWeight.Medium, maxLines = 2)
            d.day?.let {
                Txt(
                    "Today ${d.fmt.precip(it.precipSum)} · ${d.fmt.pct(it.precipProbMax)} max chance", 11.sp,
                    GlanceTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            if (size.height >= 130.dp && hours.isNotEmpty()) {
                val chartH = (size.height - 110.dp).coerceIn(28.dp, 120.dp)
                val (track, bars) = WidgetArt.bars(
                    hours.map { if (it.precipProb.isNaN()) 0f else (it.precipProb / 100).toFloat() },
                    dp2px(size.width - 24.dp).coerceAtMost(900), dp2px(chartH).coerceAtMost(360),
                )
                Layered(
                    track, bars, GlanceModifier.fillMaxWidth().height(chartH),
                    GlanceTheme.colors.secondaryContainer, GlanceTheme.colors.primary,
                )
                Spacer(GlanceModifier.height(2.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    hours.chunked(if (n == 24) 6 else 3).forEach { g ->
                        Txt(
                            if (g.first() === d.hours.first()) "Now" else d.fmt.hour(g.first().time), 10.sp,
                            GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
            }
        }
    }

    private fun rainSummary(d: WData): String {
        val slots = d.f.minutely.filter { it.time >= d.nowSec - 900 }.take(8)
        val wet = { p: Double -> !p.isNaN() && p >= 0.05 }
        if (slots.any { wet(it.precip) }) {
            return if (wet(slots.first().precip)) {
                val stop = slots.indexOfFirst { !wet(it.precip) }
                if (stop < 0) "Rain for at least the next 2 hours" else "Rain stopping in ~${stop * 15} min"
            } else {
                "Rain starting in ~${slots.indexOfFirst { wet(it.precip) } * 15} min"
            }
        }
        val next = d.hours.take(12).firstOrNull { it.precipProb >= 50 }
        return when {
            next != null -> "Rain likely around ${d.fmt.hour(next.time)} (${d.fmt.pct(next.precipProb)})"
            d.hours.take(12).any { it.precipProb >= 25 } -> "Slight chance of rain in the next 12 h"
            else -> "No rain expected in the next 12 h"
        }
    }
}

// ---- Sun & moon

class SunMoonWidget : WeatherWidget() {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val day = d.day
        val showMoon = size.width >= 250.dp || day == null
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (day != null) {
                Column(GlanceModifier.defaultWeight().fillMaxHeight().clickable(openApp(d, Metric.SUN.name))) {
                    val now = d.nowSec
                    val progress = ((now - day.sunrise).toFloat() / (day.sunset - day.sunrise).coerceAtLeast(1)).let {
                        if (now < day.sunrise || now > day.sunset) -1f else it
                    }
                    Txt(if (progress < 0) "Night" else "Daylight", 12.sp, GlanceTheme.colors.onSurfaceVariant)
                    val arcW = if (showMoon) size.width - 140.dp else size.width - 24.dp
                    val arcH = min(arcW / 2, size.height - 80.dp).coerceAtLeast(30.dp)
                    val (track, fill) = WidgetArt.sunArc(progress, dp2px(arcH * 2).coerceAtMost(700), dp2px(arcH).coerceAtMost(350))
                    Layered(
                        track, fill, GlanceModifier.width(arcH * 2).height(arcH).defaultWeight(),
                        GlanceTheme.colors.outline, GlanceTheme.colors.tertiary,
                    )
                    Row(GlanceModifier.fillMaxWidth()) {
                        Txt("↑ ${d.fmt.time(day.sunrise)}", 12.sp, weight = FontWeight.Medium)
                        Spacer(GlanceModifier.defaultWeight())
                        Txt("↓ ${d.fmt.time(day.sunset)}", 12.sp, weight = FontWeight.Medium)
                    }
                    Txt("${d.fmt.duration(day.daylight)} of daylight", 11.sp, GlanceTheme.colors.onSurfaceVariant)
                }
            }
            if (showMoon) {
                if (day != null) Spacer(GlanceModifier.width(12.dp))
                MoonColumn(d, if (day == null) GlanceModifier.defaultWeight() else GlanceModifier.width(112.dp))
            }
        }
    }

    @Composable
    private fun MoonColumn(d: WData, modifier: GlanceModifier) {
        val moon = WeatherInfo.moon()
        Column(
            modifier.fillMaxHeight().clickable(openApp(d, Metric.MOON.name)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
        ) {
            val s = 54.dp
            Image(ImageProvider(WidgetArt.moonPhase(moon.phase, d.f.latitude < 0, dp2px(s))), moon.name, GlanceModifier.size(s))
            Spacer(GlanceModifier.height(6.dp))
            Txt(moon.name, 12.sp, weight = FontWeight.Medium, align = TextAlign.Center)
            Txt("${(moon.illumination * 100).roundToInt()}% lit", 11.sp, GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center)
            val full = moon.daysToFull.roundToInt()
            Txt(if (full == 0 || full >= 29) "Full tonight" else "Full in $full d", 11.sp, GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center)
        }
    }
}

// ---- Air quality

class AirWidget : WeatherWidget() {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val air = d.f.air
        Column(GlanceModifier.fillMaxSize().clickable(openApp(d, Metric.AIR.name))) {
            Header(d)
            if (air == null || air.usAqi.isNaN()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Txt("Air quality unavailable", 13.sp, GlanceTheme.colors.onSurfaceVariant)
                }
                return@Column
            }
            val band = WeatherInfo.usAqi(air.usAqi)
            val ring = min(min(size.height - 48.dp, size.width * 0.42f), 92.dp).coerceAtLeast(44.dp)
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(ring), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(WidgetArt.aqiRing(air.usAqi, 300.0, band.color.toInt(), dp2px(ring))), null, GlanceModifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(air.usAqi.roundToInt().toString(), if (ring > 70.dp) 24.sp else 17.sp, weight = FontWeight.Bold)
                        Txt("AQI", 10.sp, GlanceTheme.colors.onSurfaceVariant)
                    }
                }
                if (size.width >= 160.dp) {
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) { AirText(d, band, air) }
                }
            }
            if (size.width < 160.dp) Txt(band.label, 12.sp, weight = FontWeight.Medium, maxLines = 2)
        }
    }

    @Composable
    private fun ColumnScope.AirText(d: WData, band: WeatherInfo.AqiBand, air: dev.vis.weather.data.AirQuality) {
        val narrow = LocalSize.current.width < 250.dp
        Txt(if (narrow) band.label.substringBefore(" for") else band.label, if (narrow) 13.sp else 14.sp, weight = FontWeight.Medium, maxLines = 2)
        Txt(if (narrow) "PM2.5 ${air.pm25.roundToInt()}" else "PM2.5 ${air.pm25.roundToInt()} · PM10 ${air.pm10.roundToInt()} µg/m³", 11.sp, GlanceTheme.colors.onSurfaceVariant)
        val pollen = air.pollen.maxByOrNull { it.second }
        if (pollen != null && pollen.second >= 1) {
            Txt("${pollen.first} pollen: ${WeatherInfo.pollenLevel(pollen.second).first}", 11.sp, GlanceTheme.colors.onSurfaceVariant)
        }
        if (!narrow && LocalSize.current.height >= 150.dp) Txt(band.advice, 11.sp, GlanceTheme.colors.onSurfaceVariant, maxLines = 3)
    }
}

// ---- receivers

abstract class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
        WidgetRefreshWorker.runNow(context)
    }
}

class NowWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NowWidget() }
class HourlyWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyWidget() }
class DailyWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyWidget() }
class ForecastWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ForecastWidget() }
class ClockWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ClockWidget() }
class DetailsWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DetailsWidget() }
class RainWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RainWidget() }
class SunMoonWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SunMoonWidget() }
class AirWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = AirWidget() }

val widgetReceivers = listOf(
    NowWidgetReceiver::class, HourlyWidgetReceiver::class, DailyWidgetReceiver::class, ForecastWidgetReceiver::class,
    ClockWidgetReceiver::class, DetailsWidgetReceiver::class, RainWidgetReceiver::class, SunMoonWidgetReceiver::class,
    AirWidgetReceiver::class,
)
