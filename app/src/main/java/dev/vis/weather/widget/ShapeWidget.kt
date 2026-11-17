package dev.vis.weather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import dev.vis.weather.R
import dev.vis.weather.data.Prefs
import dev.vis.weather.data.WeatherInfo

/**
 * "Weather shape": temperature and condition inside an M3 Expressive shape.
 * Shape, colours, opacity, place and extras are per widget (see [ShapeKeys]); set in [ShapeWidgetConfigActivity].
 */
class ShapeWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val previewSizeMode: androidx.glance.appwidget.PreviewSizeMode get() = SizeMode.Responsive(setOf(PreviewSizes.SMALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (WidgetRepo.model(context, null)?.forecast == null) WidgetRefreshWorker.runNow(context)
        provideContent {
            val st = currentState<Preferences>()
            val cfg = ShapeConfig.from(st)
            val stamp = st[WidgetKeys.STAMP] ?: 0L
            val model = remember(cfg.place, stamp) { WidgetRepo.model(context, cfg.place.ifEmpty { null }) }
            val busy = st[WidgetKeys.BUSY] == true
            val settings = model?.settings ?: Prefs(context).settings.value
            WidgetTheme(settings) {
                val f = model?.forecast
                when {
                    model == null -> ShapeEmpty(cfg, "Pick a place", actionStartActivity(WidgetLinks.open(context, null)))
                    f == null -> ShapeEmpty(cfg, if (busy) "Loading…" else "Tap to load", actionRunCallback<RefreshAction>(), busy)
                    else -> ShapeContent(WData(model, f, busy, 0, 0), cfg)
                }
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            val model = WidgetPreview.model()
            WidgetTheme(model.settings) { ShapeContent(WData(model, model.forecast!!, false, 0, 0), ShapeConfig()) }
        }
    }
}

class ShapeWidgetReceiver : WeatherWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShapeWidget()
}

/** Text sizes and what fits, derived from the widget size. Shared with the config preview so both match. */
class ShapeLayout(size: DpSize, shape: WidgetShape, showPlace: Boolean, showHiLo: Boolean) {
    /** Side of the square the shape is drawn in (whole widget for stretchy shapes). */
    val side: Dp = min(size.width, size.height)
    val wide = shape.stretch && size.width >= size.height * 1.6f
    val inset: Dp = side * (if (shape.stretch) 0.12f else 0.17f)
    val hiLo = showHiLo && side >= 110.dp
    val place = showPlace && side >= 120.dp
    private val extras = (if (hiLo) 1 else 0) + (if (place) 1 else 0)
    val icon: Dp = side * (if (wide) 0.34f else when (extras) { 0 -> 0.3f; 1 -> 0.24f; else -> 0.2f })
    val temp: Float = (side.value * (if (wide) 0.26f else if (extras == 2) 0.17f else 0.2f)).coerceIn(16f, 72f)
    val small: Float = (side.value * 0.07f).coerceIn(10f, 15f)
}

@Composable
private fun ShapeBackground(cfg: ShapeConfig, pal: ShapePalette) {
    val size = LocalSize.current
    val w = dp2px(size.width).coerceAtLeast(8)
    val h = dp2px(size.height).coerceAtLeast(8)
    val g = pal.gradient
    if (g != null) {
        Image(ImageProvider(ShapeArt.gradient(cfg.shape, w, h, cfg.opacity, g)), null, GlanceModifier.fillMaxSize(), ContentScale.FillBounds)
    } else if (cfg.opacity > 0f) {
        Image(
            ImageProvider(ShapeArt.mask(cfg.shape, w, h, cfg.opacity)), null, GlanceModifier.fillMaxSize(), ContentScale.FillBounds,
            ColorFilter.tint(ColorProvider(day = pal.bgDay, night = pal.bgNight)),
        )
    }
}

@Composable
private fun ShapeContent(d: WData, cfg: ShapeConfig) {
    val context = LocalContext.current
    val size = LocalSize.current
    val pal = remember(cfg, d.code, d.isDay, d.s) { ShapePalette.resolve(context, cfg, d.s, d.code, d.isDay) }
    val fg = ColorProvider(day = pal.fgDay, night = pal.fgNight)
    val soft = ColorProvider(day = pal.fgDay.copy(alpha = 0.78f), night = pal.fgNight.copy(alpha = 0.78f))
    val l = ShapeLayout(size, cfg.shape, cfg.showPlace, cfg.showHiLo)
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().clickable(openApp(d)),
        contentAlignment = Alignment.Center,
    ) {
        ShapeBackground(cfg, pal)
        val temp = d.fmt.temp(d.temp)
        val hiLo = d.day?.let { "${d.fmt.temp(it.max)} / ${d.fmt.temp(it.min)}" }.orEmpty()
        if (l.wide) {
            Row(GlanceModifier.padding(horizontal = l.inset), verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(d, pal, fg, l.icon)
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Num(temp, l.temp.dp, fg, maxWidth = size.width - l.inset * 2 - l.icon - 10.dp)
                    if (l.hiLo) Txt(hiLo, l.small.sp, soft)
                    if (l.place) Txt(d.model.place.name, l.small.sp, soft)
                }
            }
        } else {
            Column(
                GlanceModifier.size(l.side).padding(l.inset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (l.place) {
                    Txt(d.model.place.name, l.small.sp, soft, align = TextAlign.Center)
                    Spacer(GlanceModifier.height(2.dp))
                }
                ShapeIcon(d, pal, fg, l.icon)
                Spacer(GlanceModifier.height(2.dp))
                Num(temp, l.temp.dp, fg, maxWidth = l.side - l.inset * 2)
                Spacer(GlanceModifier.height(2.dp))
                if (l.hiLo) Txt(hiLo, l.small.sp, soft, align = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ShapeIcon(d: WData, pal: ShapePalette, fg: ColorProvider, s: Dp) {
    val bmp = WidgetArt.weather(d.code, d.isDay, dp2px(s).coerceAtMost(256), pal.monoIcon)
    Image(
        ImageProvider(bmp), WeatherInfo.describe(d.code, d.isDay), GlanceModifier.size(s),
        colorFilter = if (pal.monoIcon) ColorFilter.tint(fg) else null,
    )
}

@Composable
private fun ShapeEmpty(cfg: ShapeConfig, text: String, action: Action, busy: Boolean = false) {
    val context = LocalContext.current
    val settings = Prefs(context).settings.value
    val pal = remember(cfg) { ShapePalette.resolve(context, cfg, settings, 2, true) }
    val fg = ColorProvider(day = pal.fgDay, night = pal.fgNight)
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().clickable(action), contentAlignment = Alignment.Center) {
        ShapeBackground(cfg, pal)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(GlanceModifier.size(22.dp), color = fg)
            else Glyph(R.drawable.ic_w_refresh, 22.dp, fg)
            Spacer(GlanceModifier.height(4.dp))
            Txt(text, 11.sp, fg, align = TextAlign.Center, maxLines = 2)
        }
    }
}
