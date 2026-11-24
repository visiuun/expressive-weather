@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.vis.weather.R
import dev.vis.weather.data.Settings
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.components.AppCard
import dev.vis.weather.ui.components.ShapeBadge
import dev.vis.weather.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val PREVIEW_ZOOM = 6.6

/**
 * A small, non-interactive radar map on the main screen: the latest RainViewer frames around the
 * place, looping when animations are on. Tapping it opens the full map. Shares frames with the
 * map tab through [MapSession], so switching between them doesn't download anything twice.
 */
@Composable
fun RadarCard(lat: Double, lon: Double, settings: Settings, fmt: Fmt, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TileLoader.init(context) // no-op after the first call
    val density = LocalDensity.current
    val dark = LocalAppColors.current.isDark
    val cs = MaterialTheme.colorScheme
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val s = MapSession

    val camera = remember(lat, lon) { MapCamera(Mercator.x(lon), Mercator.y(lat), PREVIEW_ZOOM) }
    camera.tileSize = with(density) { 256.dp.toPx() }

    LaunchedEffect(resumed, s.retry) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            val r = s.radar
            if (r == null || System.currentTimeMillis() - r.fetchedAt > 5 * 60_000) {
                s.fetch("radar") { MapData.radar() }?.let { s.radar = it; s.radarIndex = it.latestPast }
            }
            delay(60_000)
        }
    }

    val radar = s.radar
    var index by remember(radar) { mutableIntStateOf(radar?.latestPast ?: 0) }
    // Loop the frames like the map does, but only advance once the next frame is in memory.
    LaunchedEffect(radar, resumed, settings.animations) {
        if (radar == null || !resumed || !settings.animations || radar.frames.size < 2) return@LaunchedEffect
        while (true) {
            delay(if (index == radar.latestPast) 1800 else 600)
            val next = (index + 1) % radar.frames.size
            val src = radar.frames[next].source
            withTimeoutOrNull(3000) {
                snapshotFlow { TileLoader.version.intValue; missingTiles(camera, src) }.first { it == 0 }
            }
            index = next
        }
    }

    val baseSource = TileLoader.basemap(dark)
    val baseFilter = if (dark && baseSource === TileSource.Osm) DarkMapFilter else null
    val frame = radar?.frames?.getOrNull(index.coerceIn(0, radar.frames.lastIndex))

    AppCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(vertical = 18.dp)) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(Icons.Rounded.Radar, MaterialShapes.Cookie9Sided, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.radar_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.radar_open_map), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = cs.primary)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cs.surfaceContainerLow),
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            camera.width = it.width
                            camera.height = it.height
                        },
                ) {
                    TileLoader.version.intValue // repaint when tiles arrive
                    drawTiles(camera, baseSource, 1f, priority = 0, colorFilter = baseFilter)
                    if (radar != null && frame != null) {
                        drawTiles(camera, frame.source, 0.8f, priority = 1)
                        val ahead = minOf(radar.frames.size - 1, 3)
                        for (k in 1..ahead) prefetchTiles(camera, radar.frames[(index + k) % radar.frames.size].source, priority = 2)
                    }
                    TileLoader.endPass()
                }
                // Where you are
                Box(Modifier.align(Alignment.Center).size(18.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(cs.primary))
                }
                if (radar != null && frame != null) {
                    Surface(
                        Modifier.align(Alignment.TopStart).padding(10.dp).zIndex(1f),
                        shape = RoundedCornerShape(50),
                        color = cs.surfaceContainerHigh.copy(alpha = 0.92f),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(radarLabel(radar, index), style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
                            Text(fmt.time(frame.time), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                    }
                    if (frame.forecast) {
                        Surface(
                            Modifier.align(Alignment.TopEnd).padding(10.dp),
                            shape = RoundedCornerShape(50),
                            color = cs.tertiaryContainer,
                        ) {
                            Text(
                                stringResource(R.string.radar_forecast),
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer,
                            )
                        }
                    }
                    // Progress through the loop
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHigh.copy(alpha = 0.92f))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        radar.frames.forEachIndexed { i, fr ->
                            Box(
                                Modifier
                                    .size(width = if (i == index) 14.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            i == index -> cs.primary
                                            fr.forecast -> cs.tertiary.copy(alpha = 0.6f)
                                            else -> cs.onSurface.copy(alpha = 0.35f)
                                        },
                                    ),
                            )
                        }
                    }
                } else if (s.error != null && s.loading == 0) {
                    Text(
                        stringResource(R.string.radar_unavailable),
                        Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                    )
                } else {
                    LoadingIndicator(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(36.dp))
                }
            }
            Text(
                stringResource(R.string.radar_credit),
                Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
            )
        }
    }
}
