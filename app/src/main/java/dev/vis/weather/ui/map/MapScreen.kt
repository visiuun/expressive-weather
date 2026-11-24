@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package dev.vis.weather.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Cyclone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.vis.weather.L
import dev.vis.weather.R
import dev.vis.weather.data.Place
import dev.vis.weather.data.Settings
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.components.ShapeBadge
import dev.vis.weather.ui.components.WeatherIcon
import dev.vis.weather.ui.theme.LocalAppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private const val RADAR_ALPHA = 0.75f
private const val FIELD_ALPHA = 0.55f
private const val INITIAL_ZOOM = 7.0

/**
 * Data that outlives the tab: switching away from the map and back keeps radar frames,
 * forecast grids and storms instead of downloading them again.
 */
@Stable
internal object MapSession {
    var radar by mutableStateOf<RadarData?>(null)
    var radarIndex by mutableIntStateOf(0)
    var playing by mutableStateOf<Boolean?>(null)
    var grid by mutableStateOf<FieldGrid?>(null)
    val grids = ArrayList<FieldGrid>()
    var storms by mutableStateOf<List<Storm>?>(null)
    var stormsAt = 0L
    var loading by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var retry by mutableIntStateOf(0)

    /** Untranslated key ([fetch]'s `what`) of the request that produced [error]. */
    private var errorKey: String? = null

    private fun errorMessage(what: String): String = L.s(
        when (what) {
            "radar" -> R.string.map_error_radar
            "forecast map" -> R.string.map_error_forecast
            "storms" -> R.string.map_error_storms
            else -> R.string.map_error_generic
        },
    )

    suspend fun <T> fetch(what: String, block: suspend () -> T): T? {
        loading++
        return try {
            block().also {
                if (error != null && errorKey == what) {
                    error = null
                    errorKey = null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorKey = what
            error = errorMessage(what)
            null
        } finally {
            loading--
        }
    }
}

private class FieldImage(val grid: FieldGrid, val layer: MapLayer, val image: ImageBitmap, val isolines: Isolines?, val isoPath: android.graphics.Path?)

@Composable
fun MapScreen(
    center: Place,
    markers: List<MapMarker>,
    settings: Settings,
    onOpenPlace: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    remember { TileLoader.init(context) }
    DisposableEffect(Unit) {
        onDispose {
            TileLoader.cancelAll()
            TileLoader.trimMemory()
        }
    }
    val dark = LocalAppColors.current.isDark
    val cs = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val fmt = remember(settings) { Fmt(settings, ZoneId.systemDefault()) }
    val haptics = LocalHapticFeedback.current
    val tick: (HapticFeedbackType) -> Unit = { if (settings.haptics) haptics.performHapticFeedback(it) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)

    val camera = rememberSaveable(saver = MapCamera.Saver) {
        MapCamera(Mercator.x(center.lon), Mercator.y(center.lat), INITIAL_ZOOM)
    }
    camera.tileSize = with(density) { 256.dp.toPx() }
    var layer by rememberSaveable { mutableStateOf(MapLayer.RADAR) }
    var hour by rememberSaveable { mutableIntStateOf(0) }
    var showSources by remember { mutableStateOf(false) }
    var openStorm by remember { mutableStateOf<Storm?>(null) }
    val s = MapSession

    // Follow the app's selected place when it changes (not on first show: keep the restored camera).
    var lastCenter by rememberSaveable { mutableStateOf(center.key) }
    LaunchedEffect(center.key) {
        if (center.key != lastCenter) {
            lastCenter = center.key
            camera.flyTo(scope, center.lat, center.lon, maxOf(camera.zoom, 6.0), motion.slowSpatialSpec())
        }
    }

    // ---- data loading

    LaunchedEffect(layer == MapLayer.RADAR, s.retry, resumed) {
        if (layer != MapLayer.RADAR || !resumed) return@LaunchedEffect
        while (true) {
            val r = s.radar
            if (r == null || System.currentTimeMillis() - r.fetchedAt > 5 * 60_000) {
                val wasLatest = r == null || s.radarIndex >= r.latestPast
                s.fetch("radar") { MapData.radar() }?.let { fresh ->
                    s.radar = fresh
                    if (wasLatest || s.radarIndex > fresh.frames.lastIndex) s.radarIndex = fresh.latestPast
                    if (s.playing == null) s.playing = settings.animations
                }
            }
            delay(60_000)
        }
    }

    LaunchedEffect(layer.isModel, s.retry) {
        if (!layer.isModel) return@LaunchedEffect
        snapshotFlow { camera.x + camera.y * 3 + camera.zoom * 7 + camera.width * 11.0 + camera.height * 13.0 }
            .collectLatest {
                if (camera.width == 0 || camera.height == 0) return@collectLatest
                fun fresh(g: FieldGrid) = System.currentTimeMillis() - g.fetchedAt < 45 * 60_000
                val g = s.grid
                if (g != null && fresh(g) && g.spec.covers(camera)) return@collectLatest
                delay(600)
                val cached = s.grids.lastOrNull { fresh(it) && it.spec.covers(camera) }
                if (cached != null) {
                    s.grid = cached
                    return@collectLatest
                }
                val spec = GridSpec.around(camera)
                val fetched = s.fetch("forecast map") { MapData.grid(spec) } ?: return@collectLatest
                s.grids += fetched
                while (s.grids.size > 6) s.grids.removeAt(0)
                s.grid = fetched
                if (hour > fetched.hours - 1) hour = 0
            }
    }

    LaunchedEffect(layer == MapLayer.STORMS, s.retry) {
        if (layer != MapLayer.STORMS) return@LaunchedEffect
        if (s.storms == null || System.currentTimeMillis() - s.stormsAt > 15 * 60_000) {
            s.fetch("storms") { MapData.storms() }?.let {
                s.storms = it
                s.stormsAt = System.currentTimeMillis()
            }
        }
    }

    // Radar playback: advance only once the next frame's visible tiles are in memory (no flicker).
    val radar = s.radar
    val playing = s.playing == true && resumed && layer == MapLayer.RADAR
    LaunchedEffect(playing, radar) {
        if (!playing || radar == null || radar.frames.size < 2) return@LaunchedEffect
        while (true) {
            delay(if (s.radarIndex >= radar.frames.lastIndex) 1500 else 550)
            val next = (s.radarIndex + 1) % radar.frames.size
            val src = radar.frames[next].source
            withTimeoutOrNull(3000) {
                snapshotFlow { TileLoader.version.intValue; missingTiles(camera, src) }.first { it == 0 }
            }
            s.radarIndex = next
        }
    }

    val field by produceState<FieldImage?>(null, s.grid, layer, hour, dark) {
        val g = s.grid
        val ramp = Ramp.forLayer(layer, dark)
        val values = g?.values(layer)
        if (g == null || ramp == null || values == null) {
            value = null
            return@produceState
        }
        val h = hour.coerceIn(0, g.hours - 1)
        value = withContext(Dispatchers.Default) {
            val iso = if (layer == MapLayer.PRESSURE) isobars(g, h) else null
            val path = iso?.let { lines ->
                android.graphics.Path().apply {
                    val sg = lines.segments
                    var k = 0
                    while (k + 3 < sg.size) {
                        moveTo(sg[k] * 1000f, sg[k + 1] * 1000f)
                        lineTo(sg[k + 2] * 1000f, sg[k + 3] * 1000f)
                        k += 4
                    }
                }
            }
            FieldImage(g, layer, buildField(g, values, h, ramp).asImageBitmap(), iso, path)
        }
    }

    // ---- drawing resources (allocated once, reused every frame)

    val baseSource = TileLoader.basemap(dark)
    val baseFilter = if (dark && baseSource === TileSource.Osm) DarkMapFilter else null
    val background = cs.surfaceContainerLow
    val ink = if (dark) Color.White else Color(0xFF1B1B1F)
    val arrowPath = remember(density) { windArrow(with(density) { 18.dp.toPx() }) }
    val arrowColor = ink.copy(alpha = if (dark) 0.85f else 0.75f)
    val isoPaint = remember(dark, density) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = with(density) { 1.4.dp.toPx() }
            color = ink.copy(alpha = 0.55f).toArgb()
        }
    }
    val isoScratch = remember { android.graphics.Path() }
    val isoMatrix = remember { android.graphics.Matrix() }
    val radarIndex = s.radarIndex

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(background),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    camera.width = it.width
                    camera.height = it.height
                }
                .mapGestures(camera, scope) { motion.defaultSpatialSpec() },
        ) {
            TileLoader.version.intValue // repaint when tiles arrive
            drawTiles(camera, baseSource, 1f, priority = 0, colorFilter = baseFilter)
            when {
                layer == MapLayer.RADAR && radar != null && radar.frames.isNotEmpty() -> {
                    val idx = radarIndex.coerceIn(0, radar.frames.lastIndex)
                    val current = radar.frames[idx].source
                    drawTiles(camera, current, RADAR_ALPHA, priority = 1)
                    // Preload the frames that come next, but only as many as memory holds — otherwise
                    // the LRU would evict and re-read frames forever.
                    val perFrame = visibleTileCount(camera, current).coerceAtLeast(1)
                    val ahead = minOf(radar.frames.size - 1, TileLoader.overlayTileCapacity / perFrame - 1)
                    for (k in 1..ahead) {
                        prefetchTiles(camera, radar.frames[(idx + k) % radar.frames.size].source, priority = 2)
                    }
                }
                layer.isModel -> field?.let { f ->
                    if (f.layer == layer) drawField(camera, f, hour, arrowPath, arrowColor, isoPaint, isoScratch, isoMatrix)
                }
            }
            TileLoader.endPass()
        }

        // ---- things pinned to the map

        val f = field
        if (layer.isModel && f != null && f.layer == layer) FieldLabels(f, hour, camera, fmt)
        if (layer == MapLayer.STORMS) {
            s.storms?.forEach { st ->
                key(st.id, st.name) {
                    StormMarker(st, settings.animations, Modifier.anchorAt(camera, Mercator.x(st.lon), Mercator.y(st.lat), ay = 0.33f)) {
                        openStorm = st
                    }
                }
            }
        }
        val showNames by remember { derivedStateOf { camera.zoom >= 6.0 } }
        val dotPx = with(density) { 7.dp.toPx() }
        markers.sortedBy { if (it.kind == MarkerKind.SELF) 2 else if (it.kind == MarkerKind.FRIEND) 1 else 0 }.forEach { m ->
            key(m.key, m.kind) {
                PlaceMarker(
                    m, fmt, showNames || m.kind == MarkerKind.FRIEND, settings.animations,
                    Modifier.anchorAt(camera, Mercator.x(m.lon), Mercator.y(m.lat), ay = 1f, dy = dotPx),
                ) { onOpenPlace(m.key) }
            }
        }

        // ---- chrome

        // Soft scrim so the status bar stays legible over busy map areas.
        Box(
            Modifier
                .fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp)
                .background(Brush.verticalGradient(listOf(cs.surface.copy(alpha = 0.7f), Color.Transparent))),
        )

        TopPanel(layer, fmt, radar, radarIndex, f, hour, s.storms, s.loading > 0, s.error,
            onRetry = { s.error = null; s.retry++ },
            onStorm = { st ->
                openStorm = st
                camera.flyTo(scope, st.lat, st.lon, maxOf(camera.zoom, 5.0), motion.slowSpatialSpec())
            },
        )

        BottomPanel(
            layer = layer,
            onLayer = {
                tick(HapticFeedbackType.SegmentTick)
                layer = it
            },
            radar = radar,
            radarIndex = radarIndex,
            playing = s.playing == true,
            onPlayToggle = {
                tick(HapticFeedbackType.ToggleOn)
                s.playing = s.playing != true
            },
            onRadarIndex = {
                if (it != s.radarIndex) tick(HapticFeedbackType.SegmentFrequentTick)
                s.playing = false
                s.radarIndex = it
            },
            grid = s.grid,
            hour = hour,
            onHour = {
                if (it != hour) tick(HapticFeedbackType.SegmentFrequentTick)
                hour = it
            },
            fmt = fmt,
            onRecenter = {
                tick(HapticFeedbackType.Confirm)
                camera.flyTo(scope, center.lat, center.lon, maxOf(camera.zoom, INITIAL_ZOOM), motion.slowSpatialSpec())
            },
            onSources = { showSources = true },
        )
    }

    if (showSources) SourcesSheet { showSources = false }
    openStorm?.let { st -> StormSheet(st, fmt) { openStorm = null } }
}

// ---------------------------------------------------------------------------------------------
// Drawing

/** Arrow pointing up (north), centred on the origin. */
private fun windArrow(len: Float): Path = Path().apply {
    val h = len / 2
    val head = len * 0.42f
    val hw = len * 0.3f
    val sw = len * 0.09f
    moveTo(0f, -h)
    lineTo(hw, -h + head)
    lineTo(sw, -h + head * 0.82f)
    lineTo(sw, h)
    lineTo(-sw, h)
    lineTo(-sw, -h + head * 0.82f)
    lineTo(-hw, -h + head)
    close()
}

private fun DrawScope.drawField(
    camera: MapCamera,
    f: FieldImage,
    hour: Int,
    arrow: Path,
    arrowColor: Color,
    isoPaint: android.graphics.Paint,
    isoScratch: android.graphics.Path,
    isoMatrix: android.graphics.Matrix,
) {
    val spec = f.grid.spec
    val ws = camera.worldSize
    val wpx = ((spec.x1 - spec.x0) * ws).toFloat()
    val l = camera.screenX((spec.x0 + spec.x1) / 2) - wpx / 2
    val t = camera.screenY(spec.y0)
    val b = camera.screenY(spec.y1)
    drawImage(
        f.image, IntOffset.Zero, IntSize(f.image.width, f.image.height),
        IntOffset(l.roundToInt(), t.roundToInt()), IntSize(wpx.roundToInt(), (b - t).roundToInt()),
        alpha = FIELD_ALPHA, filterQuality = FilterQuality.Low,
    )
    f.isoPath?.let { src ->
        isoMatrix.setScale(wpx / 1000f, (b - t) / 1000f)
        isoMatrix.postTranslate(l, t)
        src.transform(isoMatrix, isoScratch)
        drawIntoCanvas { it.nativeCanvas.drawPath(isoScratch, isoPaint) }
    }
    if (f.layer == MapLayer.WIND) {
        val g = f.grid
        val base = hour.coerceIn(0, g.hours - 1) * spec.size
        val margin = 40f
        for (j in 0 until spec.rows) for (i in 0 until spec.cols) {
            val k = base + j * spec.cols + i
            val speed = g.wind[k]
            val dir = g.windDir[k]
            if (speed.isNaN() || dir.isNaN()) continue
            val x = l + wpx * i / (spec.cols - 1)
            val y = t + (b - t) * j / (spec.rows - 1)
            if (x < -margin || y < -margin || x > size.width + margin || y > size.height + margin) continue
            val sc = 0.75f + (speed / 60f).coerceIn(0f, 1f) * 0.6f
            // Meteorological direction is where the wind comes FROM; the arrow shows where it goes.
            rotate(dir + 180f, pivot = Offset(x, y)) {
                translate(x, y) {
                    scale(sc, sc, pivot = Offset.Zero) { drawPath(arrow, arrowColor) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Map-pinned composables

@Composable
private fun FieldLabels(f: FieldImage, hour: Int, camera: MapCamera, fmt: Fmt) {
    val g = f.grid
    val spec = g.spec
    val values = g.values(f.layer) ?: return
    val h = hour.coerceIn(0, g.hours - 1)
    val base = h * spec.size
    val iso = f.isolines
    if (f.layer == MapLayer.PRESSURE && iso != null && iso.labels.isNotEmpty()) {
        var k = 0
        while (k + 2 < iso.labels.size) {
            val u = iso.labels[k].toDouble()
            val v = iso.labels[k + 1].toDouble()
            val p = iso.labels[k + 2].toDouble()
            key("iso", p) {
                ValueChip(
                    fmt.pressureNum(p),
                    Modifier.anchorAt(camera, spec.x0 + (spec.x1 - spec.x0) * u, spec.y0 + (spec.y1 - spec.y0) * v),
                )
            }
            k += 3
        }
        return
    }
    val wind = f.layer == MapLayer.WIND
    val dy = with(LocalDensity.current) { if (wind) 20.dp.toPx() else 0f }
    for (j in 1 until spec.rows - 1 step 2) for (i in 1 until spec.cols - 1 step 2) {
        val v = values[base + j * spec.cols + i].toDouble()
        if (v.isNaN()) continue
        val text = when (f.layer) {
            MapLayer.TEMPERATURE -> fmt.temp(v)
            MapLayer.WIND -> fmt.windNum(v)
            MapLayer.CLOUDS -> fmt.pct(v)
            MapLayer.PRESSURE -> fmt.pressureNum(v)
            MapLayer.PRECIPITATION -> if (v >= 0.1) fmt.precip(v) else null
            else -> null
        } ?: continue
        key(i, j) { ValueChip(text, Modifier.anchorAt(camera, spec.px(i), spec.py(j), dy = dy)) }
    }
}

@Composable
private fun ValueChip(text: String, modifier: Modifier) {
    Surface(
        modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
        shadowElevation = 1.dp,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlaceMarker(
    m: MapMarker,
    fmt: Fmt,
    showName: Boolean,
    animate: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (container, content) = when (m.kind) {
        MarkerKind.SELF -> cs.primary to cs.onPrimary
        MarkerKind.FRIEND -> cs.tertiary to cs.onTertiary
        MarkerKind.SAVED -> cs.surfaceContainerHighest to cs.onSurface
    }
    val description = buildString {
        append(m.label)
        if (m.temp != null) append(", ").append(fmt.temp(m.temp))
        if (m.code != null) append(", ").append(WeatherInfo.describe(m.code, m.isDay))
    }
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = container,
            contentColor = content,
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                when (m.kind) {
                    MarkerKind.FRIEND -> Box(
                        Modifier
                            .size(24.dp)
                            .clip(MaterialShapes.Cookie6Sided.toShape())
                            .background(cs.onTertiary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            m.label.trim().take(1).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = cs.tertiary,
                        )
                    }
                    MarkerKind.SELF -> Icon(Icons.Rounded.NearMe, null, Modifier.size(18.dp))
                    MarkerKind.SAVED -> Spacer(Modifier.width(2.dp))
                }
                if (m.code != null) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (m.kind == MarkerKind.SAVED) Color.Transparent else cs.surfaceContainerLowest.copy(alpha = 0.9f))
                            .padding(3.dp),
                    ) { WeatherIcon(m.code, m.isDay, Modifier.fillMaxSize()) }
                }
                if (m.temp != null) {
                    Text(fmt.temp(m.temp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                AnimatedVisibility(
                    showName || m.temp == null,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    Text(
                        m.label,
                        Modifier.widthIn(max = 120.dp),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        LocationDot(container, m.kind == MarkerKind.SELF && animate)
    }
}

@Composable
private fun LocationDot(color: Color, pulse: Boolean) {
    val ring = MaterialTheme.colorScheme.surface
    val p = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "p")
        v
    } else -1f
    Canvas(Modifier.size(14.dp)) {
        val r = size.minDimension / 2
        if (p >= 0f) drawCircle(color.copy(alpha = 0.35f * (1 - p)), r * (1f + p * 1.6f))
        drawCircle(ring, r)
        drawCircle(color, r * 0.66f)
    }
}

@Composable
private fun StormMarker(storm: Storm, animate: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = stormColor(storm)
    val outline = MaterialTheme.colorScheme.surface
    val rot = if (animate) {
        val t = rememberInfiniteTransition(label = "storm")
        val v by t.animateFloat(0f, -360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "r")
        v
    } else 0f
    val cd = stringResource(R.string.map_storm_cd, storm.classLabel, storm.name)
    val badge = if (storm.category > 0) stringResource(R.string.map_storm_cat_short, storm.category) else storm.classification.uppercase()
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = cd },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(44.dp)) {
            val r = size.minDimension / 2
            val c = center
            // Southern-hemisphere cyclones turn (and are drawn) the other way round.
            val flip = if (storm.lat < 0) -1f else 1f
            rotate(rot * flip, pivot = c) {
                scale(flip, 1f, pivot = c) {
                    for (pass in 0..1) {
                        val col = if (pass == 0) outline else color
                        val w = if (pass == 0) r * 0.34f else r * 0.2f
                        drawArc(col, 180f, 110f, false, Offset(c.x - r * 0.3f, c.y - r * 0.65f), Size(r * 1.3f, r * 1.3f), style = Stroke(w, cap = StrokeCap.Round))
                        drawArc(col, 0f, 110f, false, Offset(c.x - r, c.y - r * 0.65f), Size(r * 1.3f, r * 1.3f), style = Stroke(w, cap = StrokeCap.Round))
                    }
                }
            }
            drawCircle(outline, r * 0.42f, c)
            drawCircle(color, r * 0.32f, c)
            drawCircle(outline, r * 0.12f, c)
        }
        Surface(shape = CircleShape, color = color, contentColor = Color(0xFF1B1B1F), shadowElevation = 3.dp) {
            Text(
                stringResource(R.string.map_storm_marker, storm.name, badge),
                Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Overlays

private fun MapLayer.icon(): ImageVector = when (this) {
    MapLayer.NONE -> Icons.Rounded.Map
    MapLayer.RADAR -> Icons.Rounded.Radar
    MapLayer.TEMPERATURE -> Icons.Rounded.Thermostat
    MapLayer.WIND -> Icons.Rounded.Air
    MapLayer.CLOUDS -> Icons.Rounded.Cloud
    MapLayer.PRESSURE -> Icons.Rounded.Compress
    MapLayer.PRECIPITATION -> Icons.Rounded.WaterDrop
    MapLayer.STORMS -> Icons.Rounded.Cyclone
}

private fun MapLayer.shape(): RoundedPolygon = when (this) {
    MapLayer.NONE -> MaterialShapes.Circle
    MapLayer.RADAR -> MaterialShapes.Cookie9Sided
    MapLayer.TEMPERATURE -> MaterialShapes.Sunny
    MapLayer.WIND -> MaterialShapes.Clover4Leaf
    MapLayer.CLOUDS -> MaterialShapes.Cookie7Sided
    MapLayer.PRESSURE -> MaterialShapes.Cookie6Sided
    MapLayer.PRECIPITATION -> MaterialShapes.Pill
    MapLayer.STORMS -> MaterialShapes.SoftBurst
}

private fun relativeLabel(minutes: Long): String {
    if (minutes == 0L) return L.s(R.string.map_now)
    val a = abs(minutes)
    val sign = if (minutes < 0) "−" else "+"
    return when {
        a < 60 -> L.s(R.string.map_rel_minutes, sign, a)
        a % 60 == 0L -> L.s(R.string.map_rel_hours, sign, a / 60)
        else -> L.s(R.string.map_rel_hours_minutes, sign, a / 60, a % 60)
    }
}

internal fun radarLabel(r: RadarData, i: Int): String {
    val latest = r.frames.getOrNull(r.latestPast)?.time ?: return ""
    val f = r.frames.getOrNull(i) ?: return ""
    return relativeLabel((f.time - latest) / 60)
}

private fun hourLabel(fmt: Fmt, g: FieldGrid, h: Int): Pair<String, String> {
    val i = h.coerceIn(0, g.hours - 1)
    val epoch = g.times[i]
    val today = fmt.localDate(System.currentTimeMillis() / 1000)
    val day = fmt.dayName(fmt.localDate(epoch), today)
    return (if (i == 0) L.s(R.string.map_now) else L.s(R.string.map_rel_hours, "+", i)) to
        L.s(R.string.map_day_time, day, fmt.hour(epoch))
}

@Composable
private fun BoxScope.TopPanel(
    layer: MapLayer,
    fmt: Fmt,
    radar: RadarData?,
    radarIndex: Int,
    field: FieldImage?,
    hour: Int,
    storms: List<Storm>?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onStorm: (Storm) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            layer != MapLayer.NONE,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = cs.surfaceContainerHigh.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .animateContentSize(motion.defaultSpatialSpec())
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShapeBadge(
                            layer.icon(), layer.shape(), size = 40.dp,
                            container = cs.primaryContainer, content = cs.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(layer.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1)
                            val sub = when {
                                layer == MapLayer.RADAR && radar != null && radar.frames.isNotEmpty() -> {
                                    val fr = radar.frames[radarIndex.coerceIn(0, radar.frames.lastIndex)]
                                    stringResource(R.string.map_sub_radar, radarLabel(radar, radarIndex), fmt.time(fr.time))
                                }
                                layer == MapLayer.RADAR && radar != null -> stringResource(R.string.map_sub_radar_empty)
                                layer.isModel && field != null -> {
                                    val (a, b) = hourLabel(fmt, field.grid, hour)
                                    stringResource(R.string.map_sub_model, a, b)
                                }
                                layer == MapLayer.STORMS && storms != null ->
                                    if (storms.isEmpty()) "NOAA National Hurricane Center"
                                    else pluralStringResource(R.plurals.map_sub_storms_active, storms.size, storms.size)
                                else -> stringResource(R.string.map_loading)
                            }
                            Text(sub, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        AnimatedVisibility(loading, enter = fadeIn(), exit = fadeOut()) {
                            LoadingIndicator(Modifier.size(36.dp))
                        }
                    }
                    Legend(layer, fmt)
                }
            }
        }
        AnimatedVisibility(error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Surface(shape = CircleShape, color = cs.errorContainer, contentColor = cs.onErrorContainer, shadowElevation = 3.dp) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(error.orEmpty(), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.map_retry)) }
                }
            }
        }
        AnimatedVisibility(layer == MapLayer.STORMS && storms != null && storms.isEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Surface(shape = CircleShape, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, shadowElevation = 3.dp) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WbSunny, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.map_no_storms),style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        AnimatedVisibility(layer == MapLayer.STORMS && !storms.isNullOrEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                storms.orEmpty().forEach { st ->
                    Surface(onClick = { onStorm(st) }, shape = CircleShape, color = cs.surfaceContainerHigh, shadowElevation = 3.dp) {
                        Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).clip(MaterialShapes.SoftBurst.toShape()).background(stormColor(st)))
                            Spacer(Modifier.width(8.dp))
                            Text(st.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text(fmt.wind(st.windKmh), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(layer: MapLayer, fmt: Fmt) {
    val dark = LocalAppColors.current.isDark
    val cs = MaterialTheme.colorScheme
    val (colors, labels) = when (layer) {
        MapLayer.RADAR -> Ramp.RadarLegend to listOf(
            stringResource(R.string.map_legend_radar_light),
            stringResource(R.string.map_legend_radar_moderate),
            stringResource(R.string.map_legend_radar_heavy),
        )
        MapLayer.TEMPERATURE -> Ramp.Temperature.legend() to listOf(fmt.temp(-40.0), fmt.temp(-10.0), fmt.temp(20.0), fmt.temp(50.0))
        MapLayer.WIND -> Ramp.Wind.legend() to listOf(fmt.windNum(0.0), fmt.windNum(60.0), "${fmt.windNum(120.0)} ${fmt.windUnit}")
        MapLayer.CLOUDS -> Ramp.forLayer(layer, dark)!!.legend() to listOf(
            stringResource(R.string.map_legend_cloud_clear),
            stringResource(R.string.map_legend_cloud_partly),
            stringResource(R.string.map_legend_cloud_overcast),
        )
        MapLayer.PRESSURE -> Ramp.Pressure.legend() to listOf(fmt.pressureNum(960.0), fmt.pressureNum(1005.0), "${fmt.pressureNum(1050.0)} ${fmt.pressureUnit}")
        // Non-linear scale: show the stops evenly (0.1 · 0.5 · 1.5 · 3 · 6 · 12 · 25 mm).
        MapLayer.PRECIPITATION -> Ramp.Precipitation.stopColors() to listOf(fmt.precip(0.1), fmt.precip(3.0), stringResource(R.string.map_legend_per_hour, fmt.precip(25.0)))
        MapLayer.STORMS -> listOf(Color(0xFF4A9EFF), Color(0xFF35C7B0), Color(0xFFFFD84D), Color(0xFFFFA63D), Color(0xFFFF6B3D), Color(0xFFE5304D), Color(0xFFC2185B)) to
            listOf("TD", "TS", "1", "2", "3", "4", "5")
        MapLayer.NONE -> return
    }
    Spacer(Modifier.height(10.dp))
    if (layer == MapLayer.STORMS) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            colors.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(if (i == 0) 11.dp else 4.dp, if (i == colors.lastIndex) 11.dp else 4.dp, if (i == colors.lastIndex) 11.dp else 4.dp, if (i == 0) 11.dp else 4.dp))
                        .background(c),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(labels[i], style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F))
                }
            }
        }
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainerLowest)
            .background(Brush.horizontalGradient(colors)),
    )
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
    }
}

@Composable
private fun BoxScope.BottomPanel(
    layer: MapLayer,
    onLayer: (MapLayer) -> Unit,
    radar: RadarData?,
    radarIndex: Int,
    playing: Boolean,
    onPlayToggle: () -> Unit,
    onRadarIndex: (Int) -> Unit,
    grid: FieldGrid?,
    hour: Int,
    onHour: (Int) -> Unit,
    fmt: Fmt,
    onRecenter: () -> Unit,
    onSources: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            // The map itself runs under the floating pill navigator; only the controls sit above it.
            .padding(bottom = 12.dp + dev.vis.weather.ui.components.LocalBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Surface(
                onClick = onSources,
                shape = CircleShape,
                color = cs.surfaceContainerHigh.copy(alpha = 0.9f),
                contentColor = cs.onSurfaceVariant,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (TileLoader.cartoBlocked.value) "© OpenStreetMap contributors" else "© OpenStreetMap © CARTO", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Info, stringResource(R.string.map_cd_sources),Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            FloatingActionButton(
                onClick = onRecenter,
                shape = MaterialShapes.Cookie9Sided.toShape(),
                containerColor = cs.primaryContainer,
                contentColor = cs.onPrimaryContainer,
            ) { Icon(Icons.Rounded.MyLocation, stringResource(R.string.map_cd_recenter)) }
        }

        val frames = radar?.frames.orEmpty()
        AnimatedVisibility(
            layer == MapLayer.RADAR && frames.size > 1,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
        ) {
            TimelineCard {
                FilledIconButton(
                    onClick = onPlayToggle,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (playing) R.string.map_cd_pause else R.string.map_cd_play))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val r = radar
                    val idx = radarIndex.coerceIn(0, (frames.size - 1).coerceAtLeast(0))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (r != null) radarLabel(r, idx) else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        frames.getOrNull(idx)?.let {
                            Text(
                                if (it.forecast) stringResource(R.string.map_radar_forecast_time, fmt.time(it.time)) else fmt.time(it.time),
                                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                            )
                        }
                    }
                    @Suppress("DEPRECATION")
                    Slider(
                        value = idx.toFloat(),
                        onValueChange = { onRadarIndex(it.roundToInt()) },
                        valueRange = 0f..(frames.size - 1).coerceAtLeast(1).toFloat(),
                        steps = (frames.size - 2).coerceAtLeast(0),
                    )
                    if (frames.isNotEmpty() && r != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(radarLabel(r, 0), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            Text(radarLabel(r, frames.lastIndex), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            layer.isModel && grid != null,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
        ) {
            val g = grid ?: return@AnimatedVisibility
            val maxH = (g.hours - 1).coerceAtLeast(1)
            TimelineCard {
                FilledTonalIconButton(
                    onClick = { onHour(0) },
                    enabled = hour != 0,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(48.dp),
                ) { Icon(Icons.Rounded.Restore, stringResource(R.string.map_cd_back_to_now)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val (a, b) = hourLabel(fmt, g, hour)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(b, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                    @Suppress("DEPRECATION")
                    Slider(
                        value = hour.coerceIn(0, maxH).toFloat(),
                        onValueChange = { onHour(it.roundToInt()) },
                        valueRange = 0f..maxH.toFloat(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.map_now), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Text(stringResource(R.string.map_rel_hours, "+", maxH / 2), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Text(stringResource(R.string.map_rel_hours, "+", maxH), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MapLayer.entries.forEach { l ->
                    val checked = l == layer
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { onLayer(l) },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ToggleButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = cs.onSurfaceVariant,
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .semantics { contentDescription = l.title },
                    ) {
                        Icon(l.icon(), null, Modifier.size(22.dp))
                        AnimatedVisibility(
                            checked,
                            enter = expandHorizontally(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                            exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                        ) {
                            Text(l.short, Modifier.padding(start = 8.dp), maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Sheets

private class Source(val name: String, val what: String, val license: String, val url: String, val icon: ImageVector, val shape: RoundedPolygon)

// Service names and licence names stay untranslated; descriptions are resolved per call so they follow the locale.
private fun sources() = listOf(
    Source("OpenStreetMap contributors", L.s(R.string.map_source_osm_what), "Open Database License (ODbL)", "https://www.openstreetmap.org/copyright", Icons.Rounded.Map, MaterialShapes.Cookie4Sided),
    Source("CARTO", L.s(R.string.map_source_carto_what), "© CARTO", "https://carto.com/attributions", Icons.Rounded.Map, MaterialShapes.Cookie6Sided),
    Source("RainViewer", L.s(R.string.map_source_rainviewer_what), L.s(R.string.map_source_rainviewer_license), "https://www.rainviewer.com/api.html", Icons.Rounded.Radar, MaterialShapes.Cookie9Sided),
    Source("Open-Meteo", L.s(R.string.map_source_openmeteo_what), "CC BY 4.0", "https://open-meteo.com/", Icons.Rounded.Thermostat, MaterialShapes.Sunny),
    Source("NOAA National Hurricane Center", L.s(R.string.map_source_nhc_what), L.s(R.string.map_source_nhc_license), "https://www.nhc.noaa.gov/", Icons.Rounded.Cyclone, MaterialShapes.SoftBurst),
)

@Composable
private fun SourcesSheet(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.map_sources_title), style = MaterialTheme.typography.headlineSmallEmphasized)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.map_sources_body),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            sources().forEach { src ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { runCatching { uri.openUri(src.url) } }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapeBadge(src.icon, src.shape, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(src.name, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.map_source_line, src.what, src.license),style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StormSheet(storm: Storm, fmt: Fmt, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(MaterialShapes.SoftBurst.toShape())
                        .background(stormColor(storm)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Cyclone, null, Modifier.size(28.dp), tint = Color(0xFF1B1B1F)) }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(storm.name, style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(storm.classLabel, style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            val rows = buildList {
                add(L.s(R.string.map_storm_wind) to if (storm.intensityKt.isNaN()) "–" else "${fmt.wind(storm.windKmh)} · ${storm.intensityKt.roundToInt()} kt")
                add(L.s(R.string.map_storm_pressure) to "${fmt.pressureNum(storm.pressureMb)} ${fmt.pressureUnit}")
                add(
                    L.s(R.string.map_storm_moving) to if (storm.movementDir.isNaN() || storm.movementMph.isNaN()) L.s(R.string.map_storm_stationary)
                    else L.s(R.string.map_storm_moving_value, WeatherInfo.compass(storm.movementDir), fmt.wind(storm.movementKmh)),
                )
                add(
                    L.s(R.string.map_storm_position) to String.format(
                        java.util.Locale.US, "%.1f°%s, %.1f°%s",
                        abs(storm.lat), L.s(if (storm.lat >= 0) R.string.map_hemi_north else R.string.map_hemi_south),
                        abs(storm.lon), L.s(if (storm.lon >= 0) R.string.map_hemi_east else R.string.map_hemi_west),
                    ),
                )
                storm.lastUpdate?.let { iso ->
                    val t = runCatching { java.time.Instant.parse(iso).epochSecond }.getOrNull()
                    if (t != null) add(L.s(R.string.map_storm_updated) to fmt.time(t))
                }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    rows.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(k, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            storm.advisoryUrl?.let { url ->
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { runCatching { uri.openUri(url) } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.map_storm_advisory))
                }
            }
        }
    }
}
