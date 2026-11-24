@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.graphics.shapes.Morph
import androidx.lifecycle.lifecycleScope
import dev.vis.weather.R
import dev.vis.weather.data.Place
import dev.vis.weather.data.Prefs
import dev.vis.weather.data.Settings
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.UiState
import dev.vis.weather.ui.components.SettingsAction
import dev.vis.weather.ui.components.SettingsGroup
import dev.vis.weather.ui.components.SettingsItemSurface
import dev.vis.weather.ui.components.SettingsSlider
import dev.vis.weather.ui.components.SettingsSwitch
import dev.vis.weather.ui.theme.WeatherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Per-widget setup for [ShapeWidget]. Opened when the widget is added (Android < 12) or reconfigured. */
class ShapeWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Configuration is optional: backing out keeps the widget with its current (or default) look.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val glanceId = runCatching { GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId) }.getOrNull()
        if (glanceId == null) {
            finish()
            return
        }
        val settings = Prefs(this).settings.value
        val choices = WidgetRepo.choices(this)
        setContent {
            WeatherTheme(settings) {
                val initial by produceState<ShapeConfig?>(null) {
                    value = runCatching {
                        ShapeConfig.from(getAppWidgetState(this@ShapeWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId))
                    }.getOrDefault(ShapeConfig())
                }
                initial?.let { start ->
                    ShapeConfigScreen(start, settings, choices, onClose = ::finish) { cfg ->
                        lifecycleScope.launch {
                            save(cfg, glanceId)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private suspend fun save(cfg: ShapeConfig, glanceId: androidx.glance.GlanceId) {
        val ctx = applicationContext
        updateAppWidgetState(ctx, glanceId) { p ->
            p[ShapeKeys.SHAPE] = cfg.shape.name
            p[ShapeKeys.COLOR] = cfg.color.name
            p[ShapeKeys.OPACITY] = cfg.opacity
            p[ShapeKeys.SHOW_PLACE] = cfg.showPlace
            p[ShapeKeys.SHOW_HILO] = cfg.showHiLo
            if (cfg.place.isEmpty()) p.remove(WidgetKeys.PLACE) else p[WidgetKeys.PLACE] = cfg.place
            p[WidgetKeys.STAMP] = System.currentTimeMillis()
        }
        ShapeWidget().update(ctx, glanceId)
        val place = WidgetRepo.resolve(ctx, cfg.place.ifEmpty { null })?.second
        if (place != null && Prefs(ctx).loadCache(place.key) == null) WidgetRefreshWorker.runNow(ctx)
    }
}

@Composable
private fun ShapeConfigScreen(
    initial: ShapeConfig,
    settings: Settings,
    choices: List<Pair<String, Place>>,
    onClose: () -> Unit,
    onSave: (ShapeConfig) -> Unit,
) {
    var cfg by remember { mutableStateOf(initial) }
    val context = LocalContext.current
    // Real cached data for the chosen place; made-up data until there is some.
    val model by produceState<WidgetModel?>(null, cfg.place) {
        value = withContext(Dispatchers.IO) {
            WidgetRepo.model(context, cfg.place.ifEmpty { null })?.takeIf { it.forecast != null } ?: WidgetPreview.model()
        }
    }
    val d = remember(model) { model?.let { WData(it, it.forecast!!, false, 0, 0) } }
    val code = d?.code ?: 2
    val isDay = d?.isDay ?: true
    val dark = isSystemInDarkTheme()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.w_cfg_cancel)) }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.widget_shape_config_title), style = MaterialTheme.typography.headlineMediumEmphasized, modifier = Modifier.weight(1f))
            Button(onClick = { onSave(cfg) }) { Text(stringResource(R.string.w_cfg_save)) }
            Spacer(Modifier.width(8.dp))
        }

        PreviewStage(cfg, settings, d, dark, Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp, vertical = 4.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(64.dp),
            modifier = Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + navBottom),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            full {
                Row(Modifier.padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.w_cfg_shape), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(cfg.shape.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(WidgetShape.entries, key = { it.name }) { s ->
                ShapeCell(s, s == cfg.shape) { cfg = cfg.copy(shape = s) }
            }
            full {
                SettingsGroup(stringResource(R.string.w_cfg_colours), Modifier.padding(top = 18.dp)) {
                    SettingsItemSurface(0, 2) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.w_cfg_colour), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(
                                    if (cfg.color == ShapeColor.THEME && android.os.Build.VERSION.SDK_INT < 31) stringResource(R.string.w_cfg_app_colours) else cfg.color.label,
                                    style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ShapeColor.entries.forEach { c ->
                                    val pal = remember(c, code, isDay) { ShapePalette.resolve(context, cfg.copy(color = c), settings, code, isDay) }
                                    Swatch(c, pal, dark, c == cfg.color) { cfg = cfg.copy(color = c) }
                                }
                            }
                        }
                    }
                    SettingsSlider(
                        stringResource(R.string.w_cfg_background_opacity), "${(cfg.opacity * 100).roundToInt()}%", 1, 2, cfg.opacity, 0f..1f, steps = 19,
                        icon = Icons.Rounded.Opacity, shape = MaterialShapes.Cookie7Sided,
                        caption = stringResource(R.string.w_cfg_background_caption),
                    ) { cfg = cfg.copy(opacity = (it * 20).roundToInt() / 20f) }
                }
            }
            full {
                SettingsGroup(stringResource(R.string.w_cfg_show), Modifier.padding(top = 18.dp)) {
                    SettingsSwitch(
                        stringResource(R.string.w_cfg_hilo), stringResource(R.string.w_cfg_hilo_desc), Icons.Rounded.Thermostat,
                        MaterialShapes.Arch, 0, 2, cfg.showHiLo,
                    ) { cfg = cfg.copy(showHiLo = it) }
                    SettingsSwitch(
                        stringResource(R.string.w_cfg_place_name), stringResource(R.string.w_cfg_place_name_desc), Icons.Rounded.Title,
                        MaterialShapes.Cookie6Sided, 1, 2, cfg.showPlace,
                    ) { cfg = cfg.copy(showPlace = it) }
                }
            }
            full {
                SettingsGroup(stringResource(R.string.w_cfg_location), Modifier.padding(top = 18.dp)) {
                    val n = choices.size + 1
                    SettingsAction(
                        stringResource(R.string.w_cfg_follow_app), stringResource(R.string.w_cfg_follow_app_desc), Icons.Rounded.MyLocation, MaterialShapes.Cookie9Sided, 0, n,
                        trailing = { RadioButton(selected = cfg.place.isEmpty(), onClick = null) },
                    ) { cfg = cfg.copy(place = "") }
                    choices.forEachIndexed { i, (key, place) ->
                        val current = key == UiState.CURRENT
                        SettingsAction(
                            place.name, if (current) stringResource(R.string.w_cfg_your_location) else place.region.ifBlank { null },
                            if (current) Icons.Rounded.NearMe else Icons.Rounded.Place,
                            if (current) MaterialShapes.Circle else MaterialShapes.Clover4Leaf, i + 1, n,
                            trailing = { RadioButton(selected = cfg.place == key, onClick = null) },
                        ) { cfg = cfg.copy(place = key) }
                    }
                }
            }
        }
    }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

/** A fake home screen with the widget at 2×2 and 1×1. */
@Composable
private fun PreviewStage(cfg: ShapeConfig, settings: Settings, d: WData?, dark: Boolean, modifier: Modifier) {
    val wallpaper = Brush.linearGradient(
        if (dark) listOf(Color(0xFF26323F), Color(0xFF0E1217)) else listOf(Color(0xFFBFD7EE), Color(0xFFF2E3D5)),
    )
    Box(
        modifier.fillMaxWidth().height(212.dp).clip(RoundedCornerShape(32.dp)).background(wallpaper),
        contentAlignment = Alignment.Center,
    ) {
        if (d == null) return@Box
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            ShapePreview(cfg, settings, d, dark, DpSize(168.dp, 168.dp))
            ShapePreview(cfg, settings, d, dark, DpSize(76.dp, 76.dp))
        }
        Text(
            stringResource(if (dark) R.string.w_cfg_dark_home else R.string.w_cfg_light_home),
            style = MaterialTheme.typography.labelSmall,
            color = (if (dark) Color.White else Color.Black).copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

/** Compose twin of the Glance widget, morphing when the shape changes. */
@Composable
private fun ShapePreview(cfg: ShapeConfig, settings: Settings, d: WData, dark: Boolean, size: DpSize) {
    val context = LocalContext.current
    val pal = remember(cfg.color, d.code, d.isDay, settings) { ShapePalette.resolve(context, cfg, settings, d.code, d.isDay) }
    var from by remember { mutableStateOf(cfg.shape) }
    var to by remember { mutableStateOf(cfg.shape) }
    val progress = remember { Animatable(1f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(cfg.shape) {
        if (cfg.shape == to) return@LaunchedEffect
        from = to
        to = cfg.shape
        progress.snapTo(0f)
        launch { pop.snapTo(0.9f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)) }
        progress.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
    }
    val morph = remember(from, to) { Morph(from.polygon(), to.polygon()) }
    val shape = PolygonShape(morph, progress.value.coerceIn(0f, 1f), to.stretch)
    val fg = pal.fg(dark)
    val soft = fg.copy(alpha = 0.78f)
    val bg = pal.gradient?.let { Brush.linearGradient(listOf(it.first, it.second)) } ?: Brush.linearGradient(listOf(pal.bg(dark), pal.bg(dark)))
    val l = ShapeLayout(size, cfg.shape, cfg.showPlace, cfg.showHiLo)
    val density = LocalDensity.current
    val iconPx = with(density) { l.icon.roundToPx() }.coerceIn(24, 256)
    val icon = remember(d.code, d.isDay, iconPx, pal.monoIcon) { WidgetArt.weather(d.code, d.isDay, iconPx, pal.monoIcon).asImageBitmap() }

    Box(Modifier.size(size.width, size.height), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(shape)
                .background(bg, alpha = cfg.opacity),
        )
        Column(
            Modifier.size(l.side).padding(l.inset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (l.place) {
                Text(
                    d.model.place.name, color = soft, fontSize = l.small.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
            }
            Image(
                icon, WeatherInfo.describe(d.code, d.isDay), Modifier.size(l.icon),
                colorFilter = if (pal.monoIcon) ColorFilter.tint(fg) else null,
            )
            // Same chunky bitmap numerals as the widget.
            val tempText = d.fmt.temp(d.temp)
            val maxPx = with(density) { (l.side - l.inset * 2).toPx() }
            val glyphs = remember(tempText, l.temp, maxPx) {
                ChunkyText.render(context, tempText, with(density) { l.temp.dp.toPx() }, maxPx, if (l.temp >= 40f) 120 else l.temp.toInt())
            }
            Spacer(Modifier.height(2.dp))
            Image(
                glyphs.bitmap.asImageBitmap(), tempText,
                Modifier.size(with(density) { glyphs.widthPx.toDp() }, with(density) { glyphs.heightPx.toDp() }),
                colorFilter = ColorFilter.tint(fg),
            )
            Spacer(Modifier.height(2.dp))
            if (l.hiLo) {
                val day = d.day
                if (day != null) Text("${d.fmt.temp(day.max)} / ${d.fmt.temp(day.min)}", color = soft, fontSize = l.small.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ShapeCell(shape: WidgetShape, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        if (selected) 1f else 0.78f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "scale",
    )
    val spin = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            spin.snapTo(-40f)
            spin.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        }
    }
    val color by animateColorAsState(if (selected) cs.primary else cs.secondaryContainer, label = "color")
    val outline = remember(shape) { PolygonShape(shape.polygon()) }
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .semantics { contentDescription = shape.label; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize(0.84f)
                .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = spin.value }
                .clip(outline)
                .background(color),
        )
    }
}

@Composable
private fun Swatch(c: ShapeColor, pal: ShapePalette, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val progress by animateFloatAsState(
        if (selected) 1f else 0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow), label = "swatch",
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val shape = PolygonShape(morph, progress)
    val bg = pal.bg(dark)
    val brush = pal.gradient?.let { Brush.linearGradient(listOf(it.first, it.second)) } ?: Brush.linearGradient(listOf(bg, bg))
    val ink = pal.fg(dark)
    Box(
        Modifier.size(48.dp).clip(shape).background(brush)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (bg.luminance() > 0.8f || bg.luminance() < 0.02f) 1f else 0f), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = c.label; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = ink, modifier = Modifier.size(22.dp))
    }
}
