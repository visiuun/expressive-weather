package dev.vis.weather.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.appwidget.PreviewSizeMode
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import dev.vis.weather.data.ColorSource
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
import androidx.glance.color.ColorProvider as DayNight

/** Forecast data prepared for drawing, with "now" taken from the clock rather than the fetch time. */
class WData(
    val model: WidgetModel,
    val f: Forecast,
    val busy: Boolean,
    val tab: Int,
    val page: Int,
    /** This widget's own Glance state (null in picker previews). */
    val state: Preferences? = null,
) {
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

/** Typical launcher sizes (about 90 × 111 dp per cell), used for widget-picker previews. */
object PreviewSizes {
    val SMALL = DpSize(171.dp, 221.dp)
    val WIDE = DpSize(359.dp, 221.dp)
    val WIDE_3 = DpSize(359.dp, 332.dp)
    val LARGE = DpSize(359.dp, 444.dp)
}

/** Base for every widget: loads the per-widget state + cache, themes it and handles empty states. */
abstract class WeatherWidget(private val previewSize: DpSize = PreviewSizes.SMALL) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    /** Previews render at one representative size (the default would be the provider's minimum). */
    override val previewSizeMode: PreviewSizeMode get() = SizeMode.Responsive(setOf(previewSize))

    /** False for widgets that draw their own floating panes instead of one rounded card. */
    open val framed: Boolean = true

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
                    else -> Body { Content(WData(model, f, busy, st[WidgetKeys.TAB] ?: 0, st[WidgetKeys.PAGE] ?: 0, st)) }
                }
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            val model = WidgetPreview.model()
            WidgetTheme(model.settings) { Body { Content(WData(model, model.forecast!!, false, 0, 0)) } }
        }
    }

    @Composable
    private fun Body(content: @Composable () -> Unit) =
        if (framed) Frame(content = content)
        else Box(GlanceModifier.fillMaxSize().appWidgetBackground()) { content() }

    @Composable
    abstract fun Content(d: WData)
}

// ---- theme

/** Widget surfaces on top of GlanceTheme: the card and the tonal tiles nested in it. */
class WidgetPalette(val bg: ColorProvider, val tile: ColorProvider)

val LocalPalette = staticCompositionLocalOf {
    WidgetPalette(ColorProvider(R.color.widget_bg), ColorProvider(R.color.widget_tile))
}

@Composable
fun WidgetTheme(settings: Settings, content: @Composable () -> Unit) {
    // Widgets follow the wallpaper unless the app uses a custom colour.
    val dynamic = settings.colorSource != ColorSource.CUSTOM && Build.VERSION.SDK_INT >= 31
    val oled = settings.oled
    val palette = remember(dynamic, oled) {
        if (dynamic) {
            // Resource colours resolve on the launcher, so they follow wallpaper and dark-mode changes live.
            WidgetPalette(
                ColorProvider(if (oled) R.color.widget_bg_oled else R.color.widget_bg),
                ColorProvider(if (oled) R.color.widget_tile_oled else R.color.widget_tile),
            )
        } else {
            WidgetPalette(
                DayNight(day = LightScheme.surfaceBright, night = if (oled) Color.Black else DarkScheme.surfaceContainer),
                DayNight(day = LightScheme.surfaceContainerHigh, night = if (oled) DarkScheme.surfaceContainerHigh else DarkScheme.surfaceContainerHighest),
            )
        }
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        if (dynamic) GlanceTheme(content = content)
        else GlanceTheme(colors = ColorProviders(light = LightScheme, dark = DarkScheme), content = content)
    }
}

/** The widget card: rounded like the launcher's other widgets. */
@Composable
fun Frame(padding: Dp = 12.dp, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(LocalPalette.current.bg)
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

// ---- text, numbers, icons

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

/**
 * A number in the app's chunky Google Sans Flex, drawn as a bitmap ([ChunkyText]) at the screen's
 * density so it stays crisp. [size] is the font size; the image is cropped to the glyphs, so its
 * height is about 0.75 × size. Shrinks to fit [maxWidth] when given.
 */
@Composable
fun Num(
    text: String,
    size: Dp,
    color: ColorProvider = GlanceTheme.colors.onSurface,
    maxWidth: Dp = 0.dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val opsz = if (size >= 40.dp) 120 else size.value.toInt().coerceIn(9, 120)
    val g = ChunkyText.render(context, text, size.value * density, maxWidth.value * density, opsz)
    Image(
        ImageProvider(g.bitmap), text,
        modifier.width((g.widthPx / density).dp).height((g.heightPx / density).dp),
        ContentScale.Fit, ColorFilter.tint(color),
    )
}

/** A chunky value with a small unit after it ("12 km/h"). */
@Composable
fun NumUnit(
    value: String,
    unit: String,
    size: Dp,
    color: ColorProvider = GlanceTheme.colors.onSurface,
    maxWidth: Dp = 0.dp,
    unitSize: TextUnit = (size.value * 0.36f).coerceIn(11f, 18f).sp,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Num(value, size, color, maxWidth)
        if (unit.isNotEmpty()) {
            Txt(
                unit, unitSize, GlanceTheme.colors.onSurfaceVariant, FontWeight.Medium,
                GlanceModifier.padding(start = (size.value * 0.08f).dp),
            )
        }
    }
}

/** A clock time split into a chunky "6:41" and a small "AM" (12-hour clocks). */
@Composable
fun TimeNum(d: WData, epoch: Long, size: Dp, color: ColorProvider = GlanceTheme.colors.onSurface, maxWidth: Dp = 0.dp) {
    val t = d.fmt.time(epoch)
    NumUnit(t.substringBefore(' '), t.substringAfter(' ', ""), size, color, maxWidth)
}

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

/** A Material icon (rounded set) tinted with a theme colour. */
@Composable
fun Sym(icon: ImageVector, size: Dp, tint: ColorProvider = GlanceTheme.colors.primary, desc: String? = null, modifier: GlanceModifier = GlanceModifier) =
    Image(ImageProvider(WidgetSymbols.mask(icon, dp2px(size))), desc, modifier.size(size), colorFilter = ColorFilter.tint(tint))

/** Two masks layered: a tinted track under a tinted value layer. */
@Composable
fun Layered(back: android.graphics.Bitmap, front: android.graphics.Bitmap, modifier: GlanceModifier, backTint: ColorProvider, frontTint: ColorProvider) =
    Box(modifier) {
        Image(ImageProvider(back), null, GlanceModifier.fillMaxSize(), ContentScale.FillBounds, ColorFilter.tint(backTint))
        Image(ImageProvider(front), null, GlanceModifier.fillMaxSize(), ContentScale.FillBounds, ColorFilter.tint(frontTint))
    }

// ---- containers

/** A tonal tile nested in the card (or any colour) with rounded corners. */
@Composable
fun Tile(
    modifier: GlanceModifier = GlanceModifier,
    color: ColorProvider = LocalPalette.current.tile,
    radius: Dp = 20.dp,
    padding: Dp = 10.dp,
    align: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) = Box(modifier.background(color).cornerRadius(radius).padding(padding), contentAlignment = align) { content() }

/** Small rounded label: a level ("Moderate"), a date, a condition. Optional coloured dot or icon in front. */
@Composable
fun Pill(
    text: String,
    color: ColorProvider = GlanceTheme.colors.secondaryContainer,
    textColor: ColorProvider = GlanceTheme.colors.onSecondaryContainer,
    dot: Long? = null,
    icon: ImageVector? = null,
    size: TextUnit = 12.sp,
    height: Dp = 24.dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier.height(height).background(color).cornerRadius(height / 2).padding(horizontal = height * 0.42f),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(GlanceModifier.size(height * 0.34f).background(ColorProvider(Color(dot))).cornerRadius(height * 0.17f)) {}
                Spacer(GlanceModifier.width(6.dp))
            } else if (icon != null) {
                Sym(icon, height * 0.6f, textColor)
                Spacer(GlanceModifier.width(4.dp))
            }
            Txt(text, size, textColor, FontWeight.Medium)
        }
    }
}

/** Outlined rain-chance pill ("40%" with a drop). [surface] is the colour it sits on. */
@Composable
fun RainPill(pct: String, surface: ColorProvider = LocalPalette.current.tile, height: Dp = 20.dp) {
    Box(GlanceModifier.height(height).background(GlanceTheme.colors.primary).cornerRadius(height / 2).padding(1.dp)) {
        Box(
            GlanceModifier.fillMaxHeight().background(surface).cornerRadius(height / 2).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_w_drop, height * 0.5f, GlanceTheme.colors.primary)
                Spacer(GlanceModifier.width(2.dp))
                Txt(pct, 10.sp, GlanceTheme.colors.primary, FontWeight.Medium)
            }
        }
    }
}

/** Pin + place name in the accent colour; tapping it pins the widget to the next saved location. */
@Composable
fun PlaceLabel(d: WData, size: TextUnit = 14.sp, modifier: GlanceModifier = GlanceModifier, color: ColorProvider = GlanceTheme.colors.primary) {
    Row(modifier.clickable(actionRunCallback<NextPlaceAction>()), verticalAlignment = Alignment.CenterVertically) {
        val icon = (size.value + 3).dp
        if (d.busy) CircularProgressIndicator(GlanceModifier.size(icon - 2.dp), color = color)
        else Glyph(if (d.model.isCurrent) R.drawable.ic_w_near_me else R.drawable.ic_w_place, icon, color, "Switch location")
        Spacer(GlanceModifier.width(4.dp))
        Txt(d.model.place.name, size, color, FontWeight.Medium)
        if (d.stale) {
            // Data older than 3 h: say when it's from rather than pretending it's current.
            Spacer(GlanceModifier.width(6.dp))
            Txt(d.updated, (size.value - 2).sp, GlanceTheme.colors.error)
        }
    }
}

@Composable
fun openApp(d: WData, metric: String? = null, day: Int = -1): Action =
    actionStartActivity(WidgetLinks.open(LocalContext.current, d.key, metric, day))

// ---- forecast pieces

/** One hour as a tall pill tile: time, icon, chunky temperature, rain chance. */
@Composable
fun HourTile(d: WData, h: Hour, modifier: GlanceModifier, iconSize: Dp = 26.dp, numSize: Dp = 20.dp, rain: Boolean = true, maxNum: Dp = 0.dp) {
    Tile(modifier, radius = 22.dp, padding = 0.dp, align = Alignment.Center) {
        Column(GlanceModifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val label = if (h === d.hours.firstOrNull()) "Now" else d.fmt.hour(h.time)
            Txt(label, 11.sp, GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center)
            Spacer(GlanceModifier.height(4.dp))
            WIcon(h.code, h.isDay, iconSize, d.s.monoIcons)
            Spacer(GlanceModifier.height(4.dp))
            Num(d.fmt.temp(h.temp), numSize, maxWidth = maxNum)
            if (rain) {
                Spacer(GlanceModifier.height(2.dp))
                Txt(if (h.precipProb >= 10) d.fmt.pct(h.precipProb) else " ", 10.sp, GlanceTheme.colors.primary, FontWeight.Medium, align = TextAlign.Center)
            }
        }
    }
}

/** A row of [HourTile]s with even gaps (Glance caps a Row at 10 children, so gaps are padding). */
@Composable
fun HourTiles(d: WData, hours: List<Hour>, modifier: GlanceModifier, gap: Dp = 6.dp, iconSize: Dp = 26.dp, numSize: Dp = 20.dp, rain: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        hours.forEachIndexed { i, h ->
            Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = if (i == 0) 0.dp else gap)) {
                HourTile(d, h, GlanceModifier.fillMaxSize().clickable(openApp(d)), iconSize, numSize, rain)
            }
        }
    }
}

/** One day as a pill tile: name, chunky high, low, icon. Tapping opens that day. */
@Composable
fun DayTile(d: WData, index: Int, modifier: GlanceModifier, iconSize: Dp = 24.dp) {
    val day = d.days[index]
    Tile(modifier.clickable(openApp(d, day = index)), radius = 22.dp, padding = 0.dp, align = Alignment.Center) {
        Column(GlanceModifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(d.fmt.dayName(day.date, d.today), 11.sp, GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center)
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Num(d.fmt.temp(day.max), 18.dp)
                Spacer(GlanceModifier.width(4.dp))
                Txt(d.fmt.temp(day.min), 12.sp, GlanceTheme.colors.onSurfaceVariant, FontWeight.Medium)
            }
            Spacer(GlanceModifier.height(4.dp))
            WIcon(day.code, true, iconSize, d.s.monoIcons)
        }
    }
}

/** Day rows for lists: a pill tile with name, icon, rain pill, low, range bar, high. Tapping opens that day. */
@Composable
fun DayRow(d: WData, index: Int, lo: Double, hi: Double, barWidth: Dp, height: Dp = 42.dp) {
    val day = d.days[index]
    Box(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Tile(
            GlanceModifier.fillMaxWidth().height(height).clickable(openApp(d, day = index)),
            radius = height / 2, padding = 0.dp, align = Alignment.CenterStart,
        ) {
            Row(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(d.fmt.dayName(day.date, d.today), 13.sp, weight = FontWeight.Medium, modifier = GlanceModifier.width(78.dp))
                WIcon(day.code, true, 26.dp, d.s.monoIcons)
                Box(GlanceModifier.width(58.dp), contentAlignment = Alignment.Center) {
                    if (day.precipProbMax >= 10) RainPill(d.fmt.pct(day.precipProbMax))
                }
                Spacer(GlanceModifier.defaultWeight())
                Txt(d.fmt.temp(day.min), 14.sp, GlanceTheme.colors.onSurfaceVariant, FontWeight.Medium, GlanceModifier.width(36.dp), TextAlign.End)
                if (barWidth > 0.dp) {
                    Spacer(GlanceModifier.width(8.dp))
                    RangeBar(day.min, day.max, lo, hi, barWidth)
                    Spacer(GlanceModifier.width(8.dp))
                }
                Txt(d.fmt.temp(day.max), 15.sp, weight = FontWeight.Bold, modifier = GlanceModifier.width(38.dp), align = TextAlign.End)
            }
        }
    }
}

@Composable
fun RangeBar(min: Double, max: Double, lo: Double, hi: Double, width: Dp) {
    val span = (hi - lo).takeIf { it > 0 } ?: 1.0
    val start = ((min - lo) / span).coerceIn(0.0, 1.0)
    val end = ((max - lo) / span).coerceIn(start, 1.0)
    Box(GlanceModifier.width(width).height(8.dp).background(LocalPalette.current.bg).cornerRadius(4.dp)) {
        Row {
            Spacer(GlanceModifier.width(width * start.toFloat()))
            Box(GlanceModifier.width((width * (end - start).toFloat()).coerceAtLeast(8.dp)).height(8.dp).background(GlanceTheme.colors.primary).cornerRadius(4.dp)) {}
        }
    }
}

/** Segmented tabs: a tonal track with the selected segment filled. */
@Composable
fun Tabs(labels: List<String>, selected: Int, action: (Int) -> Action, modifier: GlanceModifier = GlanceModifier) {
    Tile(modifier.height(40.dp), radius = 20.dp, padding = 4.dp) {
        Row(GlanceModifier.fillMaxSize()) {
            labels.forEachIndexed { i, t ->
                val on = i == selected
                Box(
                    GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(16.dp)
                        .background(if (on) GlanceTheme.colors.primary else LocalPalette.current.tile)
                        .clickable(action(i)),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(t, 13.sp, if (on) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant, FontWeight.Medium, align = TextAlign.Center)
                }
            }
        }
    }
}

/** A round tonal button with a glyph (paging arrows). Drawn faint and inert when disabled. */
@Composable
fun RoundButton(res: Int, desc: String, enabled: Boolean, action: Action, size: Dp = 30.dp) {
    val m = GlanceModifier.size(size).background(LocalPalette.current.tile).cornerRadius(size / 2)
    Box(if (enabled) m.clickable(action) else m, contentAlignment = Alignment.Center) {
        Glyph(res, size * 0.62f, if (enabled) GlanceTheme.colors.onSurface else GlanceTheme.colors.outline, desc)
    }
}

/** Fills the rest of a Row/Column. */
@Composable
fun androidx.glance.layout.RowScope.Fill() = Spacer(GlanceModifier.defaultWeight())

@Composable
fun androidx.glance.layout.ColumnScope.Fill() = Spacer(GlanceModifier.defaultWeight())

/** Widget-friendly AQI colours (no purples). */
fun aqiColor(v: Double): Long = when {
    v <= 50 -> 0xFF4CAF50
    v <= 100 -> 0xFFFFC107
    v <= 150 -> 0xFFFF9800
    v <= 200 -> 0xFFF44336
    v <= 300 -> 0xFFB3261E
    else -> 0xFF7E0023
}

/** Short AQI band names that fit a pill. */
fun aqiShort(v: Double): String = when {
    v <= 50 -> "Good"
    v <= 100 -> "Moderate"
    v <= 150 -> "Unhealthy for some"
    v <= 200 -> "Unhealthy"
    v <= 300 -> "Very unhealthy"
    else -> "Hazardous"
}

fun uvColor(uv: Double): Long = when {
    uv.isNaN() || uv < 3 -> 0xFF4CAF50
    uv < 6 -> 0xFFFFC107
    uv < 8 -> 0xFFFF9800
    uv < 11 -> 0xFFF44336
    else -> 0xFFB3261E
}
