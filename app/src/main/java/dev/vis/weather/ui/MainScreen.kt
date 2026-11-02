@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package dev.vis.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Settings
import dev.vis.weather.data.Sky
import dev.vis.weather.ui.components.MorphingBlob
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.components.DailyCard
import dev.vis.weather.ui.components.DetailGrid
import dev.vis.weather.ui.components.HourlyCard
import dev.vis.weather.ui.components.NowcastCard
import dev.vis.weather.ui.components.WeatherIcon
import dev.vis.weather.ui.components.heroColors
import dev.vis.weather.ui.components.nowcastText
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    state: UiState,
    settings: Settings,
    onRefresh: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDay: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(state, onOpenLocations, onOpenSettings)
        val f = state.forecast
        when {
            state.selection == null -> Welcome(onRequestLocation, onOpenLocations)
            state.usingCurrent && state.needsPermission && f == null ->
                PermissionNeeded(onRequestLocation, onOpenAppSettings, onOpenLocations)
            f == null && state.loading -> Loading()
            f == null -> ErrorState(state.error ?: "No data yet", onRefresh, onOpenLocations)
            else -> Content(f, state, settings, onRefresh, onOpenDay)
        }
    }
}

@Composable
private fun TopBar(state: UiState, onOpenLocations: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val place = state.activePlace
        Surface(
            onClick = onOpenLocations,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Row(
                Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (state.usingCurrent) Icons.Rounded.MyLocation else Icons.Rounded.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        place?.name ?: if (state.usingCurrent) "Current location" else "Choose a place",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!place?.region.isNullOrBlank()) {
                        Text(
                            place.region,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun Content(f: Forecast, state: UiState, settings: Settings, onRefresh: () -> Unit, onOpenDay: (Int) -> Unit) {
    val fmt = remember(settings, f.zone) { Fmt(settings, f.zone) }
    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val nowcast = remember(f, settings) { nowcastText(f, fmt) }

    LaunchedEffect(state.forecastKey) { listState.scrollToItem(0) }

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = state.loading,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val w = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            if (state.error != null) {
                item(key = "err") {
                    Surface(w, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${state.error}. Showing data from ${fmt.time(f.fetchedAt / 1000)}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
            item(key = "hero") { Hero(f, fmt, settings, w) }
            item(key = "highlights") { Highlights(f, fmt, w) }
            if (nowcast != null) item(key = "nowcast") { Box(w) { NowcastCard(f, fmt, nowcast) } }
            item(key = "hourly") { Box(w) { HourlyCard(f, fmt) } }
            item(key = "daily") { Box(w) { DailyCard(f, fmt, onOpenDay) } }
            item(key = "details") { Box(w) { DetailGrid(f, fmt) } }
            item(key = "footer") { Footer(f, fmt, w) }
        }
    }
}

@Composable
private fun Hero(f: Forecast, fmt: Fmt, settings: Settings, modifier: Modifier) {
    val c = f.current
    val sky = WeatherInfo.sky(c.code)
    val cs = MaterialTheme.colorScheme
    val today = f.today
    val (bg, fg) = heroColors(sky, c.isDay)
    val withBg = settings.heroBackground
    val blobShapes = run {
        when {
            !c.isDay -> listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Puffy, MaterialShapes.Cookie6Sided)
            sky == Sky.CLEAR || sky == Sky.PARTLY -> listOf(MaterialShapes.SoftBurst, MaterialShapes.Cookie12Sided, MaterialShapes.Sunny)
            sky == Sky.RAIN || sky == Sky.DRIZZLE || sky == Sky.THUNDER -> listOf(MaterialShapes.Puffy, MaterialShapes.Clover8Leaf, MaterialShapes.Cookie9Sided)
            else -> listOf(MaterialShapes.Cookie12Sided, MaterialShapes.PuffyDiamond, MaterialShapes.Flower)
        }
    }
    val content = if (withBg) fg else cs.onSurface
    val blob = if (withBg) fg.copy(alpha = 0.12f) else bg

    val body: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = if (withBg) 24.dp else 8.dp, vertical = if (withBg) 22.dp else 4.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 6.dp).size(172.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MorphingBlob(blobShapes, blob, Modifier.matchParentSize())
                    WeatherIcon(c.code, c.isDay, Modifier.size(108.dp), animated = true)
                }
                Column {
                    Text(
                        WeatherInfo.describe(c.code, c.isDay),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = content,
                    )
                    Row {
                        Text(
                            fmt.temp(c.temp).removeSuffix("°"),
                            style = MaterialTheme.typography.displayLargeEmphasized.copy(
                                fontSize = 136.sp,
                                lineHeight = 136.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-6).sp,
                            ),
                            color = content,
                            maxLines = 1,
                        )
                        Text(
                            "°",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, lineHeight = 72.sp),
                            color = if (withBg) fg else cs.primary,
                            modifier = Modifier.padding(top = 14.dp, start = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroPill("Feels ${fmt.temp(c.feelsLike)}", withBg, fg)
                if (today != null) {
                    Spacer(Modifier.width(6.dp))
                    HeroPill("↑ ${fmt.temp(today.max)}", withBg, fg)
                    Spacer(Modifier.width(6.dp))
                    HeroPill("↓ ${fmt.temp(today.min)}", withBg, fg)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                summary(f, fmt),
                style = MaterialTheme.typography.bodyLarge,
                color = if (withBg) fg else cs.onSurfaceVariant,
            )
        }
    }
    if (withBg) {
        Surface(modifier, shape = RoundedCornerShape(44.dp), color = bg, contentColor = fg) { body() }
    } else {
        Box(modifier) { body() }
    }
}

@Composable
private fun HeroPill(text: String, withBg: Boolean, fg: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = CircleShape,
        color = if (withBg) fg.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (withBg) fg else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmallEmphasized,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private data class HighlightItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val value: String,
    val label: String,
    val shape: androidx.graphics.shapes.RoundedPolygon,
    val metric: dev.vis.weather.ui.components.Metric,
)

/** Horizontal strip of key numbers, each set in a different M3 Expressive shape. */
@Composable
private fun Highlights(f: Forecast, fmt: Fmt, modifier: Modifier) {
    val c = f.current
    val cs = MaterialTheme.colorScheme
    val today = f.today
    val rainChance = f.upcoming.take(12).maxOfOrNull { if (it.precipProb.isNaN()) 0.0 else it.precipProb } ?: Double.NaN
    val items = buildList {
        add(HighlightItem(Icons.Rounded.Air, fmt.windNum(c.windSpeed), "${fmt.windUnit} ${WeatherInfo.compass(c.windDir)}", MaterialShapes.Cookie6Sided, dev.vis.weather.ui.components.Metric.WIND))
        add(HighlightItem(Icons.Rounded.WaterDrop, fmt.pct(c.humidity), "Humidity", MaterialShapes.Puffy, dev.vis.weather.ui.components.Metric.HUMIDITY))
        add(HighlightItem(Icons.Rounded.Umbrella, fmt.pct(rainChance), "Rain 12 h", MaterialShapes.Clover4Leaf, dev.vis.weather.ui.components.Metric.PRECIP))
        add(HighlightItem(Icons.Rounded.WbSunny, if (c.uv.isNaN()) "–" else c.uv.roundToInt().toString(), "UV ${WeatherInfo.uvLevel(c.uv).lowercase()}", MaterialShapes.Sunny, dev.vis.weather.ui.components.Metric.UV))
        f.air?.usAqi?.takeIf { !it.isNaN() }?.let {
            add(HighlightItem(Icons.Rounded.Masks, it.roundToInt().toString(), "Air quality", MaterialShapes.Flower, dev.vis.weather.ui.components.Metric.AIR))
        }
        add(HighlightItem(Icons.Rounded.Compress, fmt.pressureNum(c.pressure), fmt.pressureUnit, MaterialShapes.Gem, dev.vis.weather.ui.components.Metric.PRESSURE))
        add(HighlightItem(Icons.Rounded.Visibility, fmt.distanceNum(c.visibility), "${fmt.distanceUnit} visibility", MaterialShapes.Oval, dev.vis.weather.ui.components.Metric.VISIBILITY))
        if (today != null) {
            val upNext = if (c.time < today.sunrise || c.time > today.sunset) "Sunrise" to today.sunrise else "Sunset" to today.sunset
            add(HighlightItem(Icons.Rounded.WbTwilight, fmt.time(upNext.second), upNext.first, MaterialShapes.SoftBurst, dev.vis.weather.ui.components.Metric.SUN))
        }
    }
    val containers = listOf(
        cs.primaryContainer to cs.onPrimaryContainer,
        cs.tertiaryContainer to cs.onTertiaryContainer,
        cs.secondaryContainer to cs.onSecondaryContainer,
    )
    LazyRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        itemsIndexed(items) { i, h ->
            val (bg, fg) = containers[i % containers.size]
            val open = dev.vis.weather.ui.components.LocalOpenMetric.current
            Column(
                Modifier
                    .width(92.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { open(h.metric) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(h.shape.toShape())
                        .background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(h.icon, null, Modifier.size(18.dp), tint = fg)
                        Text(h.value, style = MaterialTheme.typography.titleMediumEmphasized, color = fg, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    h.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun summary(f: Forecast, fmt: Fmt): String {
    val parts = mutableListOf<String>()
    val today = f.today
    val tomorrow = f.daily.getOrNull(1)
    val upcoming = f.upcoming
    // Next notable precipitation in the next 12 hours
    val wetHour = upcoming.drop(1).take(12).firstOrNull { !it.precipProb.isNaN() && it.precipProb >= 50 }
    if (wetHour != null && WeatherInfo.sky(f.current.code) !in setOf(Sky.RAIN, Sky.DRIZZLE)) {
        val kind = when (WeatherInfo.sky(wetHour.code)) {
            Sky.SNOW -> "Snow"
            Sky.THUNDER -> "Thunderstorms"
            else -> "Rain"
        }
        parts += "$kind likely around ${fmt.hour(wetHour.time)}."
    }
    if (today != null) {
        val later = WeatherInfo.describe(today.code).lowercase()
        parts += "Today: $later, high ${fmt.temp(today.max)}."
    }
    if (today != null && tomorrow != null && !today.max.isNaN() && !tomorrow.max.isNaN()) {
        val d = tomorrow.max - today.max
        parts += when {
            d >= 3 -> "Tomorrow will be warmer (${fmt.temp(tomorrow.max)})."
            d <= -3 -> "Tomorrow will be cooler (${fmt.temp(tomorrow.max)})."
            else -> "Similar temperatures tomorrow."
        }
    }
    val gust = upcoming.take(12).maxOfOrNull { if (it.windGust.isNaN()) 0.0 else it.windGust } ?: 0.0
    if (gust >= 50) parts += "Strong gusts up to ${fmt.wind(gust)}."
    return parts.joinToString(" ")
}

@Composable
private fun Footer(f: Forecast, fmt: Fmt, modifier: Modifier) {
    val mins = ((System.currentTimeMillis() - f.fetchedAt) / 60000).toInt()
    Column(modifier.padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (mins < 1) "Updated just now" else "Updated ${fmt.time(f.fetchedAt / 1000)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Weather data by Open-Meteo.com (CC BY 4.0) · Air quality: Copernicus CAMS · Places © OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!f.elevation.isNaN()) {
            Text(
                "Elevation ${f.elevation.roundToInt()} m · ${f.zone.id}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingIndicator(Modifier.size(96.dp))
            Spacer(Modifier.height(16.dp))
            Text("Fetching the forecast…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BigShapeIcon(content: @Composable () -> Unit) {
    val spin = rememberInfiniteTransition(label = "s")
    val rot by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "r")
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(200.dp)
                .graphicsLayer { rotationZ = rot }
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        content()
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(true, enter = fadeIn() + slideInVertically { it / 6 }, exit = fadeOut()) {
            Column(
                Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    }
}

@Composable
private fun Welcome(onRequestLocation: () -> Unit, onSearch: () -> Unit) = CenteredColumn {
    BigShapeIcon { WeatherIcon(2, true, Modifier.size(110.dp), animated = true) }
    Spacer(Modifier.height(28.dp))
    Text("Hello, weather", style = MaterialTheme.typography.displaySmallEmphasized, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        "Detailed forecasts from open data. Use your location or search for any place on Earth.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    Button(onClick = onRequestLocation, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Icon(Icons.Rounded.MyLocation, null)
        Spacer(Modifier.width(10.dp))
        Text("Use my location", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(10.dp))
    FilledTonalButton(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Icon(Icons.Rounded.Search, null)
        Spacer(Modifier.width(10.dp))
        Text("Search for a place", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PermissionNeeded(onRequest: () -> Unit, onOpenAppSettings: () -> Unit, onSearch: () -> Unit) = CenteredColumn {
    BigShapeIcon { Icon(Icons.Rounded.MyLocation, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
    Spacer(Modifier.height(28.dp))
    Text("Location access", style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        "Allow location to get the forecast where you are. Approximate location is enough.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Allow location") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Open app settings") }
    Spacer(Modifier.height(8.dp))
    FilledTonalButton(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Search instead") }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onSearch: () -> Unit) = CenteredColumn {
    BigShapeIcon { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
    Spacer(Modifier.height(28.dp))
    Text("Couldn't load weather", style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Try again") }
    Spacer(Modifier.height(8.dp))
    FilledTonalButton(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Choose another place") }
}
