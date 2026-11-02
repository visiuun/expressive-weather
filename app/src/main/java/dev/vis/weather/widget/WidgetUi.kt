package dev.vis.weather.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.vis.weather.R
import dev.vis.weather.data.Day
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Hour
import dev.vis.weather.data.Prefs
import dev.vis.weather.data.Settings
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.theme.DarkScheme
import dev.vis.weather.ui.theme.LightScheme
import java.time.LocalDate

/** Forecast data prepared for drawing, with "now" taken from the clock rather than the fetch time. */
class WData(val model: WidgetModel, val f: Forecast, val busy: Boolean, val tab: Int, val page: Int) {
    val fmt: Fmt = model.fmt!!
    val s: Settings = model.settings
    val key get() = model.key
    val nowSec = System.currentTimeMillis() / 1000
    val today: LocalDate = LocalDate.now(f.zone)
    val hours: List<Hour> = f.hourlyAll.filter { it.time > nowSec - 3600 }.take(48)
    val days: List<Day> = f.daily.filter { !it.date.isBefore(today) }
    val day: Day? = days.firstOrNull()

    /** The cached "current" block goes stale; after an hour fall back to the hourly forecast. */
    private val live = nowSec - f.current.time < 3600 || hours.isEmpty()
    val temp = if (live) f.current.temp else hours.first().temp
    val feels = if (live) f.current.feelsLike else hours.first().feelsLike
    val code = if (live) f.current.code else hours.first().code
    val isDay = if (live) f.current.isDay else hours.first().isDay
    val desc get() = WeatherInfo.describe(code, isDay)
    val hiLo get() = day?.let { "↑${fmt.temp(it.max)}  ↓${fmt.temp(it.min)}" } ?: ""
    val updated get() = fmt.time(f.fetchedAt / 1000)
    val stale get() = nowSec - f.fetchedAt / 1000 > 3 * 3600
}

/** Base for every widget: loads the per-widget state + cache, themes it and handles empty states. */
abstract class WeatherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (WidgetRepo.model(context, null)?.forecast == null) WidgetRefreshWorker.runNow(context)
        provideContent {
            val st = currentState<Preferences>()
            val pinned = st[WidgetKeys.PLACE]
            val stamp = st[WidgetKeys.STAMP] ?: 0L
            val model = remember(pinned, stamp) { WidgetRepo.model(context, pinned) }
            val busy = st[WidgetKeys.BUSY] == true
            WidgetTheme(model?.settings ?: Prefs(context).settings.value) {
                val f = model?.forecast
                when {
                    model == null -> Empty("Pick a location in the app", actionStartActivity(WidgetLinks.open(context, null)))
                    f == null -> Empty(if (busy) "Loading ${model.place.name}…" else "Tap to load ${model.place.name}", actionRunCallback<RefreshAction>(), busy)
                    else -> Frame { Content(WData(model, f, busy, st[WidgetKeys.TAB] ?: 0, st[WidgetKeys.PAGE] ?: 0)) }
                }
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            val model = WidgetPreview.model()
            WidgetTheme(model.settings) { Frame { Content(WData(model, model.forecast!!, false, 0, 0)) } }
        }
    }

    @Composable
    abstract fun Content(d: WData)
}

// ---- theme + building blocks

@Composable
fun WidgetTheme(settings: Settings, content: @Composable () -> Unit) {
    if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) GlanceTheme(content = content)
    else GlanceTheme(colors = ColorProviders(light = LightScheme, dark = DarkScheme), content = content)
}


@Composable
fun Frame(padding: Dp = 12.dp, content: @Composable () -> Unit) {
    val oled = Prefs(LocalContext.current).settings.value.oled
    val bg = if (oled) androidx.glance.color.ColorProvider(day = Color(0xFFF8F9FF), night = Color.Black) else GlanceTheme.colors.widgetBackground
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(bg)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius).padding(padding),
    ) { content() }
}

@Composable
private fun Empty(text: String, action: Action, busy: Boolean = false) = Frame {
    Column(
        GlanceModifier.fillMaxSize().clickable(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(GlanceModifier.size(24.dp), color = GlanceTheme.colors.primary)
        else Image(ImageProvider(R.drawable.ic_w_refresh), null, GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
        Spacer(GlanceModifier.height(6.dp))
        Txt(text, 13.sp, GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun Txt(
    text: String,
    size: TextUnit = 13.sp,
    color: ColorProvider = GlanceTheme.colors.onSurface,
    weight: FontWeight = FontWeight.Normal,
    modifier: GlanceModifier = GlanceModifier,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) = Text(text, modifier, TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align), maxLines)

@Composable
fun dp2px(dp: Dp): Int = (dp.value * LocalContext.current.resources.displayMetrics.density).toInt()

@Composable
fun WIcon(code: Int, isDay: Boolean, size: Dp, mono: Boolean, modifier: GlanceModifier = GlanceModifier) {
    val bmp = WidgetArt.weather(code, isDay, dp2px(size).coerceAtMost(256), mono)
    Image(
        ImageProvider(bmp), WeatherInfo.describe(code, isDay), modifier.size(size),
        colorFilter = if (mono) ColorFilter.tint(GlanceTheme.colors.onSurface) else null,
    )
}

@Composable
fun Glyph(res: Int, size: Dp, tint: ColorProvider = GlanceTheme.colors.primary, desc: String? = null, modifier: GlanceModifier = GlanceModifier) =
    Image(ImageProvider(res), desc, modifier.size(size), colorFilter = ColorFilter.tint(tint))

/** Two masks layered: a tinted track under a tinted value layer. */
@Composable
fun Layered(back: android.graphics.Bitmap, front: android.graphics.Bitmap, modifier: GlanceModifier, backTint: ColorProvider, frontTint: ColorProvider) =
    Box(modifier) {
        Image(ImageProvider(back), null, GlanceModifier.fillMaxSize(), ContentScale.FillBounds, ColorFilter.tint(backTint))
        Image(ImageProvider(front), null, GlanceModifier.fillMaxSize(), ContentScale.FillBounds, ColorFilter.tint(frontTint))
    }

/** Place name; tapping it pins the widget to the next saved location. */
@Composable
fun PlaceChip(d: WData, size: TextUnit = 13.sp, modifier: GlanceModifier = GlanceModifier) {
    Row(modifier.clickable(actionRunCallback<NextPlaceAction>()), verticalAlignment = Alignment.CenterVertically) {
        Glyph(if (d.model.isCurrent) R.drawable.ic_w_near_me else R.drawable.ic_w_place, 14.dp, desc = "Switch location")
        Spacer(GlanceModifier.width(4.dp))
        Txt(d.model.place.name, size, weight = FontWeight.Medium)
    }
}

@Composable
fun RefreshButton(d: WData, size: Dp = 20.dp) {
    Box(GlanceModifier.size(size + 12.dp).clickable(actionRunCallback<RefreshAction>()), contentAlignment = Alignment.Center) {
        if (d.busy) CircularProgressIndicator(GlanceModifier.size(size - 2.dp), color = GlanceTheme.colors.primary)
        else Glyph(R.drawable.ic_w_refresh, size, GlanceTheme.colors.onSurfaceVariant, "Refresh")
    }
}

/** Place on the left, optional middle content, refresh on the right. */
@Composable
fun Header(d: WData, showUpdated: Boolean = false, middle: @Composable RowScope.() -> Unit = {}) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PlaceChip(d)
        if (showUpdated || d.stale) {
            Spacer(GlanceModifier.width(6.dp))
            Txt(d.updated, 11.sp, if (d.stale) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant)
        }
        Spacer(GlanceModifier.defaultWeight())
        middle()
        RefreshButton(d)
    }
}

@Composable
fun openApp(d: WData, metric: String? = null, day: Int = -1): Action =
    actionStartActivity(WidgetLinks.open(LocalContext.current, d.key, metric, day))

/** A row of hour columns: time, icon, temperature and rain chance. */
@Composable
fun HourStrip(d: WData, hours: List<Hour>, iconSize: Dp = 26.dp, modifier: GlanceModifier = GlanceModifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Glance caps a Row at 10 children, so no spacers between columns.
        hours.forEach { h ->
            Column(GlanceModifier.defaultWeight().padding(horizontal = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val label = if (h === d.hours.firstOrNull()) "Now" else d.fmt.hour(h.time)
                Txt(label, 11.sp, GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center)
                Spacer(GlanceModifier.height(4.dp))
                WIcon(h.code, h.isDay, iconSize, d.s.monoIcons)
                Spacer(GlanceModifier.height(4.dp))
                Txt(d.fmt.temp(h.temp), 14.sp, weight = FontWeight.Medium, align = TextAlign.Center)
                Txt(
                    if (h.precipProb >= 10) d.fmt.pct(h.precipProb) else " ", 10.sp,
                    GlanceTheme.colors.primary, align = TextAlign.Center,
                )
            }
        }
    }
}

/** Day name, icon, rain chance, low, range bar, high. Tapping opens that day in the app. */
@Composable
fun DayRow(d: WData, index: Int, lo: Double, hi: Double, barWidth: Dp, height: Dp = 30.dp) {
    val day = d.days[index]
    Row(
        GlanceModifier.fillMaxWidth().height(height).clickable(openApp(d, day = index)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(d.fmt.dayName(day.date, d.today), 13.sp, weight = FontWeight.Medium, modifier = GlanceModifier.width(76.dp))
        WIcon(day.code, true, 22.dp, d.s.monoIcons)
        Txt(
            if (day.precipProbMax >= 10) d.fmt.pct(day.precipProbMax) else "", 11.sp, GlanceTheme.colors.primary,
            modifier = GlanceModifier.width(38.dp), align = TextAlign.Center,
        )
        Spacer(GlanceModifier.defaultWeight())
        Txt(d.fmt.temp(day.min), 13.sp, GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.width(36.dp), align = TextAlign.End)
        if (barWidth > 0.dp) {
            Spacer(GlanceModifier.width(8.dp))
            RangeBar(day.min, day.max, lo, hi, barWidth)
            Spacer(GlanceModifier.width(8.dp))
        }
        Txt(d.fmt.temp(day.max), 13.sp, weight = FontWeight.Medium, modifier = GlanceModifier.width(36.dp), align = TextAlign.End)
    }
}

@Composable
fun RangeBar(min: Double, max: Double, lo: Double, hi: Double, width: Dp) {
    val span = (hi - lo).takeIf { it > 0 } ?: 1.0
    val start = ((min - lo) / span).coerceIn(0.0, 1.0)
    val end = ((max - lo) / span).coerceIn(start, 1.0)
    Box(GlanceModifier.width(width).height(6.dp).background(GlanceTheme.colors.secondaryContainer).cornerRadius(3.dp)) {
        Row {
            Spacer(GlanceModifier.width(width * start.toFloat()))
            Box(GlanceModifier.width((width * (end - start).toFloat()).coerceAtLeast(6.dp)).height(6.dp).background(GlanceTheme.colors.primary).cornerRadius(3.dp)) {}
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier.height(30.dp).cornerRadius(15.dp)
            .background(if (selected) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Txt(
            text, 12.sp, if (selected) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer,
            FontWeight.Medium, align = TextAlign.Center, modifier = GlanceModifier.padding(horizontal = 12.dp),
        )
    }
}
