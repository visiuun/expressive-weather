package dev.vis.weather.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cloud
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
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
import dev.vis.weather.L
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
        when {
            size.height < 100.dp -> Slim(d, size.width, size.height)
            size.width >= 250.dp && size.height >= 170.dp -> Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PlaceLabel(d)
                    Fill()
                    Txt(d.hiLo, 13.sp, GlanceTheme.colors.onSurfaceVariant, FontWeight.Medium)
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    val heroW = (size.width - 24.dp) * 0.46f
                    NowTile(d, GlanceModifier.width(heroW).fillMaxHeight().clickable(open), heroW)
                    val n = ((size.width - 24.dp - heroW) / 58.dp).toInt().coerceIn(2, 6)
                    HourTiles(d, d.hours.take(n), GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 6.dp))
                }
            }
            else -> Column(GlanceModifier.fillMaxSize().clickable(open)) {
                PlaceLabel(d)
                Fill()
                Fill()
                val wide = size.width >= 250.dp
                val icon = min(size.height * 0.34f, if (wide) 64.dp else 56.dp)
                val numSize = min(size.height * 0.4f, 76.dp)
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Num(d.fmt.temp(d.temp), numSize, maxWidth = size.width - icon - 40.dp)
                    if (wide) {
                        Spacer(GlanceModifier.width(12.dp))
                        WIcon(d.code, d.isDay, icon, d.s.monoIcons)
                        Spacer(GlanceModifier.width(12.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Txt(d.desc, 15.sp, weight = FontWeight.Bold, maxLines = 2)
                            Txt(wStr(R.string.w_feels_hilo, d.fmt.temp(d.feels), d.hiLo), 12.sp, GlanceTheme.colors.onSurfaceVariant)
                        }
                    } else {
                        Fill()
                        WIcon(d.code, d.isDay, icon, d.s.monoIcons)
                    }
                }
                if (!wide) {
                    Spacer(GlanceModifier.height(4.dp))
                    if (size.height >= 150.dp) Txt(wStr(R.string.w_feels, d.fmt.temp(d.feels)) + "  " + d.hiLo, 12.sp, GlanceTheme.colors.onSurfaceVariant)
                    Txt(d.desc, 14.sp, weight = FontWeight.Bold)
                }
                Fill()
            }
        }
    }

    /** 1-row pill: icon, temperature, condition and place. */
    @Composable
    private fun Slim(d: WData, w: Dp, h: Dp) {
        Row(GlanceModifier.fillMaxSize().clickable(openApp(d)), verticalAlignment = Alignment.CenterVertically) {
            WIcon(d.code, d.isDay, min(h - 16.dp, 44.dp), d.s.monoIcons)
            Spacer(GlanceModifier.width(8.dp))
            Num(d.fmt.temp(d.temp), min(h * 0.6f, 40.dp))
            if (w >= 170.dp) {
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Txt(d.desc, 13.sp, weight = FontWeight.Bold)
                    if (w >= 240.dp) PlaceLabel(d, 12.sp) else Txt(d.hiLo, 11.sp, GlanceTheme.colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Current conditions in an inset tile: chunky temperature, icon, feels like, condition. */
@Composable
fun NowTile(d: WData, modifier: GlanceModifier, width: Dp) {
    Tile(modifier, radius = 22.dp, padding = 12.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                val icon = min(width * 0.32f, 48.dp)
                Num(d.fmt.temp(d.temp), min(width * 0.36f, 56.dp), maxWidth = width - icon - 28.dp)
                Fill()
                WIcon(d.code, d.isDay, icon, d.s.monoIcons)
            }
            Fill()
            Txt(wStr(R.string.w_feels, d.fmt.temp(d.feels)), 12.sp, GlanceTheme.colors.onSurfaceVariant)
            Txt(d.desc, 14.sp, weight = FontWeight.Bold, maxLines = 2)
        }
    }
}

// ---- Hourly: pageable tiles

class HourlyWidget : WeatherWidget(PreviewSizes.WIDE) {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val perPage = ((size.width - 24.dp + 6.dp) / 56.dp).toInt().coerceIn(3, 8)
        val pages = ((d.hours.size + perPage - 1) / perPage).coerceAtLeast(1)
        val page = d.page.coerceIn(0, pages - 1)
        val slice = d.hours.drop(page * perPage).take(perPage)
        // Height left for the tiles under the header; bigger tiles get bigger icons and numbers.
        val tileH = size.height - 24.dp - 36.dp
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The label gives way (clips) before the arrows do.
                Box(GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlaceLabel(d)
                        if (size.width >= 330.dp) {
                            Spacer(GlanceModifier.width(8.dp))
                            WIcon(d.code, d.isDay, 20.dp, d.s.monoIcons)
                            Spacer(GlanceModifier.width(4.dp))
                            Num(d.fmt.temp(d.temp), 16.dp)
                        }
                    }
                }
                if (slice.isNotEmpty() && size.width >= 220.dp) {
                    Txt(d.fmt.dayName(d.fmt.localDate(slice.first().time), d.today), 12.sp, GlanceTheme.colors.onSurfaceVariant, FontWeight.Medium)
                    Spacer(GlanceModifier.width(8.dp))
                }
                RoundButton(R.drawable.ic_w_chevron_left, wStr(R.string.w_earlier), page > 0, actionRunCallback<SetPageAction>(actionParametersOf(PageParam to page - 1)), 28.dp)
                Spacer(GlanceModifier.width(4.dp))
                RoundButton(R.drawable.ic_w_chevron_right, wStr(R.string.w_later), page < pages - 1, actionRunCallback<SetPageAction>(actionParametersOf(PageParam to page + 1)), 28.dp)
            }
            Spacer(GlanceModifier.height(8.dp))
            HourTiles(
                d, slice, GlanceModifier.fillMaxWidth().defaultWeight(),
                iconSize = when { tileH >= 140.dp -> 34.dp; tileH >= 90.dp -> 28.dp; else -> 22.dp },
                numSize = when { tileH >= 140.dp -> 24.dp; tileH >= 90.dp -> 20.dp; else -> 16.dp },
                rain = size.height >= 120.dp,
            )
        }
    }
}

// ---- Daily: scrollable 16-day list

class DailyWidget : WeatherWidget(PreviewSizes.WIDE_3) {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val days = d.days.take(16)
        val lo = days.minOfOrNull { it.min } ?: 0.0
        val hi = days.maxOfOrNull { it.max } ?: 1.0
        val bar = dayBarWidth(size.width)
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.defaultWeight()) { PlaceLabel(d) }
                WIcon(d.code, d.isDay, 24.dp, d.s.monoIcons)
                Spacer(GlanceModifier.width(6.dp))
                Num(d.fmt.temp(d.temp), 20.dp)
            }
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(days.indices.toList(), itemId = { it.toLong() }) { i -> DayRow(d, i, lo, hi, bar) }
            }
        }
    }
}

/** Range bar width for [DayRow]s in a widget this wide; 0 hides it when there's no room. */
fun dayBarWidth(width: Dp): Dp = (width - 24.dp - 300.dp).coerceAtMost(110.dp).let { if (it < 36.dp) 0.dp else it }

// ---- Forecast hub: tabbed, interactive

class ForecastWidget : WeatherWidget(PreviewSizes.LARGE) {
    private val tabs = listOf(R.string.w_tab_hourly, R.string.w_tab_daily, R.string.w_tab_details)

    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val tab = d.tab.coerceIn(0, tabs.lastIndex)
        Column(GlanceModifier.fillMaxSize()) {
            PlaceLabel(d, modifier = GlanceModifier.padding(start = 4.dp))
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth().clickable(openApp(d)), verticalAlignment = Alignment.CenterVertically) {
                Num(d.fmt.temp(d.temp), 60.dp, maxWidth = size.width * 0.4f)
                Spacer(GlanceModifier.width(10.dp))
                WIcon(d.code, d.isDay, 52.dp, d.s.monoIcons)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Txt(d.desc, 15.sp, weight = FontWeight.Bold, maxLines = 2)
                    Txt(wStr(R.string.w_feels_hilo, d.fmt.temp(d.feels), d.hiLo), 12.sp, GlanceTheme.colors.onSurfaceVariant)
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            Tabs(tabs.map { wStr(it) }, tab, { i -> actionRunCallback<SetTabAction>(actionParametersOf(TabParam to i)) }, GlanceModifier.fillMaxWidth())
            Spacer(GlanceModifier.height(8.dp))
            val m = GlanceModifier.fillMaxWidth().defaultWeight()
            when (tab) {
                0 -> LazyColumn(m) {
                    items(d.hours.indices.toList(), itemId = { it.toLong() }) { i -> HourRow(d, i) }
                }
                1 -> {
                    val days = d.days
                    val lo = days.minOfOrNull { it.min } ?: 0.0
                    val hi = days.maxOfOrNull { it.max } ?: 1.0
                    val bar = dayBarWidth(size.width)
                    LazyColumn(m) { items(days.indices.toList(), itemId = { it.toLong() }) { i -> DayRow(d, i, lo, hi, bar) } }
                }
                else -> {
                    val pairs = detailTiles(d).chunked(2)
                    LazyColumn(m) {
                        items(pairs.indices.toList(), itemId = { it.toLong() }) { i ->
                            Row(GlanceModifier.fillMaxWidth().height(84.dp).padding(bottom = 6.dp)) {
                                pairs[i].forEachIndexed { j, t ->
                                    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = if (j == 0) 0.dp else 6.dp)) {
                                        DetailTile(d, t, GlanceModifier.fillMaxSize())
                                    }
                                }
                                if (pairs[i].size == 1) Box(GlanceModifier.defaultWeight().padding(start = 6.dp)) {}
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
        Box(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Tile(GlanceModifier.fillMaxWidth().height(42.dp).clickable(openApp(d)), radius = 21.dp, padding = 0.dp, align = Alignment.CenterStart) {
                Row(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(if (i == 0) wStr(R.string.w_now) else d.fmt.hour(h.time), 13.sp, weight = FontWeight.Medium, modifier = GlanceModifier.width(58.dp))
                    WIcon(h.code, h.isDay, 24.dp, d.s.monoIcons)
                    Spacer(GlanceModifier.width(10.dp))
                    Txt(WeatherInfo.describe(h.code, h.isDay), 12.sp, GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.defaultWeight())
                    if (h.precipProb >= 10) {
                        RainPill(d.fmt.pct(h.precipProb))
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    Txt(d.fmt.temp(h.temp), 15.sp, weight = FontWeight.Bold, modifier = GlanceModifier.width(38.dp), align = TextAlign.End)
                }
            }
        }
    }
}

// ---- Clock: time, date and weather

class ClockWidget : WeatherWidget(PreviewSizes.WIDE) {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val context = LocalContext.current
        val stacked = size.height >= 200.dp && size.width < 280.dp
        val roomy = size.height >= 160.dp
        when {
            stacked -> Column(GlanceModifier.fillMaxSize()) {
                ClockTile(context, d, (size.width.value / 4.4f).coerceIn(34f, 64f), GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DatePill(context, d)
                    Fill()
                    PlaceLabel(d, 13.sp)
                }
                Spacer(GlanceModifier.height(6.dp))
                NowTile(d, GlanceModifier.fillMaxWidth().defaultWeight().clickable(openApp(d)), size.width - 24.dp)
            }
            roomy -> Column(GlanceModifier.fillMaxSize()) {
                val topH = (size.height * 0.38f).coerceIn(64.dp, 110.dp)
                Row(GlanceModifier.fillMaxWidth().height(topH), verticalAlignment = Alignment.CenterVertically) {
                    ClockTile(context, d, (topH.value * 0.56f).coerceIn(32f, 64f), GlanceModifier.fillMaxHeight())
                    Fill()
                    Column(horizontalAlignment = Alignment.End) {
                        DatePill(context, d)
                        Spacer(GlanceModifier.height(8.dp))
                        PlaceLabel(d, 13.sp)
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    val heroW = (size.width - 24.dp) * 0.5f
                    NowTile(d, GlanceModifier.width(heroW).fillMaxHeight().clickable(openApp(d)), heroW)
                    val n = ((size.width - 24.dp - heroW) / 58.dp).toInt().coerceIn(2, 5)
                    HourTiles(d, d.hours.take(n), GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 6.dp), iconSize = 24.dp, numSize = 18.dp, rain = false)
                }
            }
            else -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                    ClockTile(context, d, (size.height.value * 0.36f).coerceIn(28f, 60f), GlanceModifier.fillMaxWidth().defaultWeight())
                    if (size.height >= 110.dp) {
                        Spacer(GlanceModifier.height(6.dp))
                        DatePill(context, d)
                    }
                }
                Spacer(GlanceModifier.width(8.dp))
                Tile(GlanceModifier.fillMaxHeight().clickable(openApp(d)), radius = 22.dp, padding = 10.dp, align = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Num(d.fmt.temp(d.temp), 30.dp)
                            Spacer(GlanceModifier.width(6.dp))
                            WIcon(d.code, d.isDay, 30.dp, d.s.monoIcons)
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        PlaceLabel(d, 12.sp)
                    }
                }
            }
        }
    }

    /** The live time in the chunky font, on a tonal tile. Tapping opens alarms. */
    @Composable
    private fun ClockTile(context: Context, d: WData, sp: Float, modifier: GlanceModifier) {
        Tile(modifier, radius = 24.dp, padding = 0.dp, align = Alignment.Center) {
            AndroidRemoteViews(clockViews(context, d, sp, date = false), GlanceModifier.padding(horizontal = 16.dp))
        }
    }

    @Composable
    private fun DatePill(context: Context, d: WData) {
        Box(GlanceModifier.height(30.dp).background(GlanceTheme.colors.primaryContainer).cornerRadius(15.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            AndroidRemoteViews(clockViews(context, d, 14f, date = true))
        }
    }

    /**
     * RemoteViews can't load the app's font files, so the live clock uses the closest variable system
     * family (Google Sans Flex on recent Pixels, else Roboto Flex) set to the app's chunky axes.
     */
    private val clockLayout: Int by lazy {
        fun has(family: String) = android.os.Build.VERSION.SDK_INT >= 26 &&
            android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL) != android.graphics.Typeface.DEFAULT
        when {
            has("google-sans-flex") -> R.layout.widget_clock_gsf
            has("roboto-flex") -> R.layout.widget_clock_rf
            else -> R.layout.widget_clock
        }
    }

    private fun clockViews(context: Context, d: WData, sp: Float, date: Boolean): RemoteViews {
        val rv = RemoteViews(context.packageName, clockLayout)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        if (date) {
            rv.setViewVisibility(R.id.clock_time, View.GONE)
            rv.setTextViewTextSize(R.id.clock_date, TypedValue.COMPLEX_UNIT_SP, sp)
            rv.setOnClickPendingIntent(
                R.id.clock_date,
                PendingIntent.getActivity(
                    context, 2,
                    Intent(Intent.ACTION_VIEW, "content://com.android.calendar/time/${System.currentTimeMillis()}".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    flags,
                ),
            )
        } else {
            rv.setViewVisibility(R.id.clock_date, View.GONE)
            val fmt = if (d.s.use24h) "HH:mm" else "h:mm"
            rv.setCharSequence(R.id.clock_time, "setFormat12Hour", fmt)
            rv.setCharSequence(R.id.clock_time, "setFormat24Hour", fmt)
            rv.setTextViewTextSize(R.id.clock_time, TypedValue.COMPLEX_UNIT_SP, sp)
            rv.setOnClickPendingIntent(
                R.id.clock_time,
                PendingIntent.getActivity(context, 1, Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags),
            )
        }
        return rv
    }
}

// ---- Details: tile grid

class Stat(
    val label: String,
    val value: String,
    val unit: String,
    val sub: String,
    val metric: Metric,
    val icon: ImageVector,
    val windDir: Double = Double.NaN,
)

fun detailTiles(d: WData): List<Stat> {
    val c = d.f.current
    val f = d.fmt
    val h = d.hours.firstOrNull()
    val air = d.f.air
    val moon = WeatherInfo.moon()
    return buildList {
        add(Stat(L.s(R.string.w_metric_feels), f.temp(d.feels), "", L.s(R.string.w_note_actual, f.temp(d.temp)), Metric.FEELS, Icons.Rounded.Thermostat))
        add(Stat(L.s(R.string.w_metric_wind), f.windNum(c.windSpeed), f.windUnit, L.s(R.string.w_note_from, WeatherInfo.compass(c.windDir)), Metric.WIND, Icons.Rounded.Air, c.windDir))
        add(Stat(L.s(R.string.w_metric_humidity), f.pct(c.humidity), "", L.s(R.string.w_note_dew, f.temp(c.dewPoint)), Metric.HUMIDITY, Icons.Rounded.WaterDrop))
        add(Stat(L.s(R.string.w_metric_uv), if (c.uv.isNaN()) "–" else c.uv.roundToInt().toString(), "", WeatherInfo.uvLevel(c.uv), Metric.UV, Icons.Rounded.WbSunny))
        if (air != null && !air.usAqi.isNaN()) add(Stat(L.s(R.string.w_metric_air), air.usAqi.roundToInt().toString(), "", aqiShort(air.usAqi), Metric.AIR, Icons.Rounded.Masks))
        add(Stat(L.s(R.string.w_metric_rain), f.pct(h?.precipProb ?: Double.NaN), "", d.day?.let { L.s(R.string.w_note_today_precip, f.precip(it.precipSum)) } ?: "", Metric.PRECIP, Icons.Rounded.Umbrella))
        add(Stat(L.s(R.string.w_metric_pressure), f.pressureNum(c.pressure), f.pressureUnit, "", Metric.PRESSURE, Icons.Rounded.Compress))
        add(Stat(L.s(R.string.w_metric_visibility), f.distanceNum(c.visibility), f.distanceUnit, WeatherInfo.visibilityLevel(c.visibility), Metric.VISIBILITY, Icons.Rounded.Visibility))
        d.day?.let {
            val t = f.time(it.sunrise)
            add(Stat(L.s(R.string.w_sunrise), t.substringBefore(' '), t.substringAfter(' ', ""), L.s(R.string.w_note_set, f.time(it.sunset)), Metric.SUN, Icons.Rounded.WbTwilight))
        }
        add(Stat(L.s(R.string.w_metric_cloud), f.pct(c.cloudCover), "", "", Metric.CLOUD, Icons.Rounded.Cloud))
        add(Stat(L.s(R.string.w_metric_moon), "${(moon.illumination * 100).roundToInt()}%", "", moon.name, Metric.MOON, Icons.Rounded.DarkMode))
    }
}

/** A stat tile: icon + label, chunky value with unit, a short note. Tapping opens its detail screen. */
@Composable
fun DetailTile(d: WData, t: Stat, modifier: GlanceModifier, valueSize: Dp = 26.dp) {
    Tile(modifier.clickable(openApp(d, t.metric.name)), radius = 20.dp, padding = 10.dp) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym(t.icon, 14.dp)
                Spacer(GlanceModifier.width(4.dp))
                Txt(t.label, 11.sp, GlanceTheme.colors.onSurfaceVariant, FontWeight.Medium)
            }
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!t.windDir.isNaN()) {
                    Image(
                        ImageProvider(WidgetArt.windArrow(t.windDir, dp2px(16.dp))), wStr(R.string.w_wind_direction),
                        GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    )
                    Spacer(GlanceModifier.width(3.dp))
                }
                NumUnit(t.value, t.unit, valueSize, unitSize = 11.sp)
            }
            if (t.sub.isNotEmpty()) {
                Spacer(GlanceModifier.height(2.dp))
                Txt(t.sub, 11.sp, GlanceTheme.colors.onSurfaceVariant)
            }
        }
    }
}

class DetailsWidget : WeatherWidget(PreviewSizes.WIDE) {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val cols = ((size.width - 24.dp + 6.dp) / 96.dp).toInt().coerceIn(2, 5)
        val rows = ((size.height - 50.dp + 6.dp) / 74.dp).toInt().coerceIn(1, 4)
        val tiles = detailTiles(d).take(cols * rows)
        val valueSize = if ((size.height - 50.dp) / rows >= 86.dp) 28.dp else 22.dp
        Column(GlanceModifier.fillMaxSize()) {
            PlaceLabel(d, modifier = GlanceModifier.padding(start = 4.dp, bottom = 8.dp))
            // Gaps come from padding: Glance caps Rows/Columns at 10 children.
            tiles.chunked(cols).forEachIndexed { r, line ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight().padding(top = if (r == 0) 0.dp else 6.dp)) {
                    line.forEachIndexed { i, t ->
                        Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = if (i == 0) 0.dp else 6.dp)) {
                            DetailTile(d, t, GlanceModifier.fillMaxSize(), valueSize)
                        }
                    }
                    repeat(cols - line.size) { Box(GlanceModifier.defaultWeight().padding(start = 6.dp)) {} }
                }
            }
        }
    }
}

// ---- Rain: nowcast + chart

class RainWidget : WeatherWidget(PreviewSizes.WIDE) {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val n = if (size.width >= 320.dp) 24 else 12
        val hours = d.hours.take(n)
        val peak = d.hours.take(12).maxOfOrNull { if (it.precipProb.isNaN()) 0.0 else it.precipProb } ?: 0.0
        Column(GlanceModifier.fillMaxSize().clickable(openApp(d, Metric.PRECIP.name))) {
            PlaceLabel(d, modifier = GlanceModifier.padding(start = 4.dp))
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Num(d.fmt.pct(peak), if (size.height >= 150.dp) 40.dp else 30.dp)
                    Txt(wStr(R.string.w_rain_max_chance), 11.sp, GlanceTheme.colors.onSurfaceVariant)
                }
                Spacer(GlanceModifier.width(14.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Txt(rainSummary(d), 14.sp, weight = FontWeight.Bold, maxLines = 2)
                    d.day?.let { Txt(wStr(R.string.w_note_today_precip, d.fmt.precip(it.precipSum)), 12.sp, GlanceTheme.colors.onSurfaceVariant) }
                }
            }
            if (size.height >= 140.dp && hours.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                Tile(GlanceModifier.fillMaxWidth().defaultWeight(), radius = 20.dp, padding = 10.dp) {
                    Column(GlanceModifier.fillMaxSize()) {
                        val chartH = (size.height - 150.dp).coerceIn(20.dp, 140.dp)
                        val (track, bars) = WidgetArt.bars(
                            hours.map { if (it.precipProb.isNaN()) 0f else (it.precipProb / 100).toFloat() },
                            dp2px(size.width - 44.dp).coerceAtMost(900), dp2px(chartH).coerceAtMost(360),
                        )
                        Layered(track, bars, GlanceModifier.fillMaxWidth().defaultWeight(), LocalPalette.current.bg, GlanceTheme.colors.primary)
                        Spacer(GlanceModifier.height(4.dp))
                        Row(GlanceModifier.fillMaxWidth()) {
                            hours.chunked(if (n == 24) 6 else 3).forEach { g ->
                                Txt(
                                    if (g.first() === d.hours.first()) wStr(R.string.w_now) else d.fmt.hour(g.first().time), 10.sp,
                                    GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.defaultWeight(),
                                )
                            }
                        }
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
                if (stop < 0) L.s(R.string.w_rain_next_2h) else L.p(R.plurals.w_rain_stopping, stop * 15)
            } else {
                L.p(R.plurals.w_rain_starting, slots.indexOfFirst { wet(it.precip) } * 15)
            }
        }
        val next = d.hours.take(12).firstOrNull { it.precipProb >= 50 }
        return when {
            next != null -> L.s(R.string.w_rain_likely_around, d.fmt.hour(next.time))
            d.hours.take(12).any { it.precipProb >= 25 } -> L.s(R.string.w_rain_slight_chance)
            else -> L.s(R.string.w_rain_none)
        }
    }
}

// ---- Sun & moon

class SunMoonWidget : WeatherWidget(PreviewSizes.WIDE) {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val day = d.day
        val showMoon = size.width >= 250.dp || day == null
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (day != null) SunTile(d, GlanceModifier.defaultWeight().fillMaxHeight(), if (showMoon) size.width - 160.dp else size.width - 24.dp)
                if (showMoon) {
                    if (day != null) Spacer(GlanceModifier.width(6.dp))
                    MoonTile(d, if (day == null) GlanceModifier.defaultWeight().fillMaxHeight() else GlanceModifier.width(128.dp).fillMaxHeight())
                }
            }
            if (size.height >= 150.dp) {
                Spacer(GlanceModifier.height(8.dp))
                PlaceLabel(d, 13.sp, GlanceModifier.padding(start = 4.dp))
            }
        }
    }

    @Composable
    private fun SunTile(d: WData, modifier: GlanceModifier, width: Dp) {
        val day = d.day ?: return
        val size = LocalSize.current
        Tile(modifier.clickable(openApp(d, Metric.SUN.name)), radius = 22.dp, padding = 12.dp) {
            Column(GlanceModifier.fillMaxSize()) {
                val now = d.nowSec
                val progress = if (now < day.sunrise || now > day.sunset) -1f
                else (now - day.sunrise).toFloat() / (day.sunset - day.sunrise).coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sym(Icons.Rounded.WbTwilight, 16.dp)
                    Spacer(GlanceModifier.width(6.dp))
                    Txt(wStr(R.string.w_sun_of_daylight, d.fmt.duration(day.daylight)), 12.sp, GlanceTheme.colors.onSurfaceVariant, FontWeight.Medium)
                }
                if (size.height >= 130.dp) {
                    val arcH = min((width - 24.dp) / 2, size.height - 130.dp).coerceAtLeast(24.dp)
                    val (track, fill) = WidgetArt.sunArc(progress, dp2px(arcH * 2).coerceAtMost(700), dp2px(arcH).coerceAtMost(350))
                    Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                        Layered(track, fill, GlanceModifier.width(arcH * 2).height(arcH), GlanceTheme.colors.outline, GlanceTheme.colors.tertiary)
                    }
                } else {
                    Fill()
                }
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val num = if (size.height >= 150.dp) 24.dp else 20.dp
                    Sym(Icons.Rounded.ArrowUpward, 16.dp)
                    TimeNum(d, day.sunrise, num, maxWidth = width / 2 - 40.dp)
                    Fill()
                    Sym(Icons.Rounded.ArrowDownward, 16.dp)
                    TimeNum(d, day.sunset, num, maxWidth = width / 2 - 40.dp)
                }
            }
        }
    }

    @Composable
    private fun MoonTile(d: WData, modifier: GlanceModifier) {
        val moon = WeatherInfo.moon()
        val size = LocalSize.current
        Tile(modifier.clickable(openApp(d, Metric.MOON.name)), radius = 22.dp, padding = 10.dp, align = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                val s = min(size.height * 0.3f, 56.dp).coerceAtLeast(32.dp)
                Image(ImageProvider(WidgetArt.moonPhase(moon.phase, d.f.latitude < 0, dp2px(s))), moon.name, GlanceModifier.size(s))
                Spacer(GlanceModifier.height(6.dp))
                Txt(moon.name, 12.sp, weight = FontWeight.Bold, align = TextAlign.Center, maxLines = 2)
                val full = moon.daysToFull.roundToInt()
                Txt(
                    if (full == 0 || full >= 29) wStr(R.string.w_moon_full_tonight, (moon.illumination * 100).roundToInt())
                    else LocalContext.current.resources.getQuantityString(R.plurals.w_moon_full_in, full, (moon.illumination * 100).roundToInt(), full),
                    11.sp, GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center,
                )
            }
        }
    }
}

// ---- Air quality

class AirWidget : WeatherWidget() {
    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val air = d.f.air
        Column(GlanceModifier.fillMaxSize().clickable(openApp(d, Metric.AIR.name)).padding(horizontal = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym(Icons.Rounded.Masks, 16.dp, GlanceTheme.colors.onSurfaceVariant)
                Spacer(GlanceModifier.width(6.dp))
                Txt(wStr(R.string.w_metric_air), 13.sp, GlanceTheme.colors.onSurfaceVariant, FontWeight.Medium)
            }
            if (air == null || air.usAqi.isNaN()) {
                Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                    Txt(wStr(R.string.w_unavailable_here), 13.sp, GlanceTheme.colors.onSurfaceVariant)
                }
                PlaceLabel(d, 13.sp)
                return@Column
            }
            val wide = size.width >= 250.dp
            Fill()
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Num(air.usAqi.roundToInt().toString(), min(size.height * 0.4f, 64.dp), maxWidth = size.width * 0.45f)
                Spacer(GlanceModifier.width(12.dp))
                if (wide || size.height < 150.dp) Pill(aqiShort(air.usAqi), dot = aqiColor(air.usAqi), height = 28.dp, size = 13.sp)
            }
            if (!wide && size.height >= 150.dp) {
                Spacer(GlanceModifier.height(6.dp))
                Pill(aqiShort(air.usAqi), dot = aqiColor(air.usAqi), height = 26.dp)
            }
            if (size.height >= 130.dp) {
                Spacer(GlanceModifier.height(6.dp))
                val pollen = air.pollen.maxByOrNull { it.second }?.takeIf { it.second >= 1 }
                val parts = listOfNotNull(
                    "PM2.5 ${air.pm25.roundToInt()}",
                    if (wide) "PM10 ${air.pm10.roundToInt()}" else null,
                    pollen?.let { wStr(R.string.w_air_pollen, it.first, WeatherInfo.pollenLevel(it.second).first.lowercase()) },
                )
                Txt(parts.joinToString(" · "), 11.sp, GlanceTheme.colors.onSurfaceVariant)
                if (wide && size.height >= 170.dp) Txt(WeatherInfo.usAqi(air.usAqi).advice, 11.sp, GlanceTheme.colors.onSurfaceVariant, maxLines = 2)
            }
            Fill()
            PlaceLabel(d, 13.sp)
        }
    }
}

// ---- Forecast stack: floating panes

class StackWidget : WeatherWidget(PreviewSizes.WIDE_3) {
    override val framed = false

    @Composable
    override fun Content(d: WData) {
        val size = LocalSize.current
        val pal = LocalPalette.current
        val gap = 6.dp
        val showDays = size.height >= 250.dp
        val showHours = size.height >= 150.dp
        val panes = 1 + (if (showHours) 1 else 0) + (if (showDays) 1 else 0)
        val topH = if (panes == 1) size.height else min(((size.height - gap * (panes - 1)) / panes).coerceAtLeast(56.dp), min(size.width * 0.22f, 88.dp))
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().height(topH), verticalAlignment = Alignment.CenterVertically) {
                ShapeBox(WidgetShape.COOKIE_9, topH, GlanceModifier.clickable(openApp(d))) {
                    WIcon(d.code, d.isDay, topH * 0.56f, d.s.monoIcons)
                }
                Spacer(GlanceModifier.width(gap))
                ShapeBox(WidgetShape.CIRCLE, topH, GlanceModifier.clickable(openApp(d))) {
                    Num(d.fmt.temp(d.temp), topH * 0.38f, maxWidth = topH * 0.78f)
                }
                Spacer(GlanceModifier.width(gap))
                Box(
                    GlanceModifier.defaultWeight().fillMaxHeight().background(pal.bg).cornerRadius(topH / 2)
                        .padding(start = 18.dp, end = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column {
                        PlaceLabel(d, 14.sp)
                        Spacer(GlanceModifier.height(2.dp))
                        Txt(d.desc, 13.sp, weight = FontWeight.Medium, modifier = GlanceModifier.padding(start = 2.dp), maxLines = if (topH >= 72.dp) 2 else 1)
                    }
                }
            }
            if (showHours) {
                Spacer(GlanceModifier.height(gap))
                Pane(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    val n = ((size.width - 12.dp) / 54.dp).toInt().coerceIn(3, 8)
                    HourTiles(d, d.hours.take(n), GlanceModifier.fillMaxSize(), gap = 4.dp, iconSize = 24.dp, numSize = 18.dp, rain = !showDays)
                }
            }
            if (showDays) {
                Spacer(GlanceModifier.height(gap))
                Pane(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    val n = ((size.width - 12.dp) / 92.dp).toInt().coerceIn(2, 6).coerceAtMost(d.days.size)
                    Row(GlanceModifier.fillMaxSize()) {
                        repeat(n) { i ->
                            Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = if (i == 0) 0.dp else 4.dp)) {
                                DayTile(d, i, GlanceModifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }

    /** One of the floating panes: the card colour, rounded, with a small inset for the tiles. */
    @Composable
    private fun Pane(modifier: GlanceModifier, content: @Composable () -> Unit) =
        Tile(modifier, LocalPalette.current.bg, radius = 26.dp, padding = 6.dp) { content() }

    /** Content centred on an M3 Expressive shape in the card colour. */
    @Composable
    private fun ShapeBox(shape: WidgetShape, side: Dp, modifier: GlanceModifier, content: @Composable () -> Unit) {
        val px = dp2px(side)
        Box(modifier.size(side), contentAlignment = Alignment.Center) {
            Image(
                ImageProvider(ShapeArt.mask(shape, px, px, 1f)), null, GlanceModifier.fillMaxSize(), ContentScale.FillBounds,
                ColorFilter.tint(LocalPalette.current.bg),
            )
            content()
        }
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
class StackWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StackWidget() }
class DataTileWidgetReceiver : WeatherWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DataTileWidget() }

val widgetReceivers = listOf(
    NowWidgetReceiver::class, HourlyWidgetReceiver::class, DailyWidgetReceiver::class, ForecastWidgetReceiver::class,
    ClockWidgetReceiver::class, DetailsWidgetReceiver::class, RainWidgetReceiver::class, SunMoonWidgetReceiver::class,
    AirWidgetReceiver::class, ShapeWidgetReceiver::class, StackWidgetReceiver::class, DataTileWidgetReceiver::class,
)
