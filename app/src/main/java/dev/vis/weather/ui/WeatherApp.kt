@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import androidx.compose.ui.res.stringResource
import dev.vis.weather.R
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vis.weather.alerts.AlertType
import dev.vis.weather.alerts.WarningStore
import dev.vis.weather.data.ForecastRepo
import dev.vis.weather.data.Friends
import dev.vis.weather.data.LocationProvider
import dev.vis.weather.ui.alerts.AlertDetailScreen
import dev.vis.weather.ui.alerts.AlertsScreen
import dev.vis.weather.ui.alerts.WarningsScreen
import dev.vis.weather.ui.components.LocalOpenMetric
import dev.vis.weather.ui.components.Metric
import dev.vis.weather.ui.friends.FriendsScreen
import dev.vis.weather.ui.map.MapMarker
import dev.vis.weather.ui.map.MapScreen
import dev.vis.weather.ui.map.MarkerKind
import dev.vis.weather.ui.theme.WeatherTheme

enum class Screen(val tab: Boolean = false) {
    MAIN(true), MAP(true), ALERTS(true),
    LOCATIONS, SETTINGS, DAY, METRIC, ALERT_DETAIL, WARNINGS, FRIENDS,
    APPEARANCE, LAYOUT, UNITS, APP_ICON, WIDGETS, DATA, STATS, GAME,
}

private val stackSaver = listSaver<List<Screen>, String>(
    save = { list -> list.map { it.name } },
    restore = { names -> names.mapNotNull { n -> Screen.entries.firstOrNull { it.name == n } }.ifEmpty { listOf(Screen.MAIN) } },
)

@Composable
fun WeatherApp(vm: WeatherViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val nav by vm.nav.collectAsStateWithLifecycle()
    val updateUi by vm.update.collectAsStateWithLifecycle()
    val warningMap by WarningStore.byPlace.collectAsState()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        vm.refreshIfStale()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onPermissionResult(result.values.any { it })
    }
    val requestLocation = {
        if (LocationProvider.hasPermission(context)) vm.useCurrentLocation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val openAppSettings = {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    val mood = state.forecast?.current?.let { dev.vis.weather.ui.theme.WeatherMood(it.code, it.isDay) }
    WeatherTheme(settings, mood) {
        var stack by rememberSaveable(stateSaver = stackSaver) { mutableStateOf(listOf(Screen.MAIN)) }
        val screen = stack.last()
        fun push(s: Screen) { stack = stack + s }
        fun pop() { stack = if (stack.size > 1) stack.dropLast(1) else stack }
        fun tab(s: Screen) { stack = listOf(s) }

        var dayIndex by rememberSaveable { mutableIntStateOf(0) }
        var metric by rememberSaveable { mutableStateOf(Metric.WIND) }
        var alertType by rememberSaveable { mutableStateOf(AlertType.WARNINGS) }

        val activeKey = state.activePlace?.key
        val warnings = remember(warningMap, activeKey) {
            activeKey?.let { warningMap[it] }?.warnings?.let(WarningStore::active).orEmpty()
        }

        // Deep link from a widget tile / notification, applied once data for that place is on screen.
        LaunchedEffect(nav, state.forecast) {
            val n = nav ?: return@LaunchedEffect
            when (n.open) {
                "alerts" -> { tab(Screen.ALERTS); vm.consumeNav(); return@LaunchedEffect }
                "map" -> { tab(Screen.MAP); vm.consumeNav(); return@LaunchedEffect }
                "game" -> { stack = listOf(Screen.MAIN, Screen.GAME); vm.consumeNav(); return@LaunchedEffect }
                "warnings" -> { stack = listOf(Screen.MAIN, Screen.WARNINGS); vm.consumeNav(); return@LaunchedEffect }
                "friends" -> { stack = listOf(Screen.MAIN, Screen.FRIENDS); vm.consumeNav(); return@LaunchedEffect }
                "wardrobe" -> { tab(Screen.MAIN); vm.consumeNav(); return@LaunchedEffect }
            }
            if (state.forecast == null) return@LaunchedEffect
            when {
                n.metric != null -> { metric = n.metric; stack = listOf(Screen.MAIN, Screen.METRIC) }
                n.day >= 0 -> { dayIndex = n.day.coerceAtMost(state.forecast!!.daily.lastIndex); stack = listOf(Screen.MAIN, Screen.DAY) }
            }
            vm.consumeNav()
        }
        BackHandler(enabled = stack.size > 1 || screen != Screen.MAIN) {
            vm.clearSearch()
            if (stack.size > 1) pop() else tab(Screen.MAIN)
        }
        UpdateDialog(updateUi, onUpdate = vm::installUpdate, onLater = vm::dismissUpdate)

        val openAlert: (AlertType) -> Unit = { alertType = it; push(Screen.ALERT_DETAIL) }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val showNav = screen.tab && state.selection != null
            val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
                .asPaddingValues().calculateBottomPadding()
            val bottomInset = if (showNav) dev.vis.weather.ui.components.PillNavHeight + 28.dp + navBottom else navBottom
            Box(Modifier.fillMaxSize()) {
                val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val scale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                CompositionLocalProvider(
                    LocalOpenMetric provides { m -> metric = m; push(Screen.METRIC) },
                    dev.vis.weather.ui.components.LocalBottomInset provides bottomInset,
                ) {
                    AnimatedContent(
                        targetState = screen,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            when {
                                initialState.tab && targetState.tab -> fadeIn(effects) togetherWith fadeOut(effects)
                                initialState in stack ->
                                    (slideInHorizontally(spatial) { it / 3 } + fadeIn(effects)) togetherWith
                                        (scaleOut(scale, targetScale = 0.94f) + fadeOut(effects))
                                else -> (scaleIn(scale, initialScale = 0.94f) + fadeIn(effects)) togetherWith
                                    (slideOutHorizontally(spatial) { it / 3 } + fadeOut(effects))
                            }
                        },
                        label = "screen",
                    ) { s ->
                        when (s) {
                            Screen.MAIN -> MainScreen(
                                state = state,
                                settings = settings,
                                warnings = warnings,
                                onRefresh = vm::refresh,
                                onOpenLocations = { push(Screen.LOCATIONS) },
                                onOpenSettings = { push(Screen.SETTINGS) },
                                onRequestLocation = requestLocation,
                                onOpenAppSettings = openAppSettings,
                                onOpenDay = { dayIndex = it; push(Screen.DAY) },
                                onOpenWarnings = { push(Screen.WARNINGS) },
                                onOpenFriends = { push(Screen.FRIENDS) },
                                onOpenAlert = openAlert,
                                onOpenMap = { tab(Screen.MAP) },
                                onCustomize = { push(Screen.LAYOUT) },
                            )
                            Screen.MAP -> {
                                val place = state.activePlace ?: state.currentPlace ?: state.places.firstOrNull()
                                if (place == null) {
                                    Box(Modifier.fillMaxSize()) { Text(stringResource(R.string.m_choose_place_first), Modifier.fillMaxWidth()) }
                                } else {
                                    val friends by Friends.flow(context).collectAsState()
                                    val markers = remember(state.forecast, state.places, friends, place) {
                                        buildList {
                                            val f = state.forecast
                                            add(MapMarker(state.selection ?: place.key, place.name, place.lat, place.lon, f?.current?.temp, f?.current?.code, f?.current?.isDay ?: true, MarkerKind.SELF))
                                            state.places.filter { it.key != place.key }.forEach { p ->
                                                val c = ForecastRepo.cached(context, p)
                                                add(MapMarker(p.key, p.name, p.lat, p.lon, c?.current?.temp, c?.current?.code, c?.current?.isDay ?: true, MarkerKind.SAVED))
                                            }
                                            friends.forEach { fr ->
                                                val c = ForecastRepo.cached(context, fr.place)
                                                add(MapMarker("friend:${fr.id}", fr.name, fr.place.lat, fr.place.lon, c?.current?.temp, c?.current?.code, c?.current?.isDay ?: true, MarkerKind.FRIEND))
                                            }
                                        }
                                    }
                                    MapScreen(place, markers, settings, onOpenPlace = { key ->
                                        when {
                                            key.startsWith("friend:") -> { tab(Screen.MAIN); push(Screen.FRIENDS) }
                                            key == UiState.CURRENT -> { vm.useCurrentLocation(); tab(Screen.MAIN) }
                                            else -> state.places.firstOrNull { it.key == key }?.let { vm.selectPlace(it); tab(Screen.MAIN) }
                                        }
                                    })
                                }
                            }
                            Screen.ALERTS -> AlertsScreen(
                                settings = settings,
                                place = state.activePlace,
                                warnings = warnings,
                                zone = state.forecast?.zone,
                                onOpenAlert = openAlert,
                                onOpenWarnings = { push(Screen.WARNINGS) },
                            )
                            Screen.ALERT_DETAIL -> AlertDetailScreen(
                                type = alertType,
                                settings = settings,
                                places = state.places,
                                currentPlace = state.currentPlace,
                                onBack = ::pop,
                                onManageFriends = { push(Screen.FRIENDS) },
                            )
                            Screen.WARNINGS -> {
                                val others = state.places.filter { it.key != activeKey }.mapNotNull { p ->
                                    warningMap[p.key]?.warnings?.let(WarningStore::active)?.takeIf { it.isNotEmpty() }?.let { p.name to it }
                                }
                                WarningsScreen(
                                    state.activePlace?.name, warnings, others, settings,
                                    state.forecast?.let { Fmt(settings, it.zone) },
                                    onBack = ::pop,
                                    onOpenAlertSettings = { openAlert(AlertType.WARNINGS) },
                                )
                            }
                            Screen.FRIENDS -> FriendsScreen(
                                me = state.activePlace?.let { p -> state.forecast?.let { p to it } },
                                settings = settings,
                                onBack = ::pop,
                                onOpenOnMap = { tab(Screen.MAP) },
                            )
                            Screen.LOCATIONS -> LocationsScreen(
                                state = state,
                                search = search,
                                onBack = { vm.clearSearch(); pop() },
                                onQuery = vm::onQueryChange,
                                onUseCurrent = { requestLocation(); vm.clearSearch(); pop() },
                                onSelect = { vm.selectPlace(it); vm.clearSearch(); pop() },
                                onRemove = vm::removePlace,
                            )
                            Screen.DAY -> {
                                val f = state.forecast
                                if (f == null || f.daily.isEmpty()) {
                                    LaunchedEffect(Unit) { pop() }
                                } else {
                                    DayScreen(forecast = f, settings = settings, index = dayIndex, onSelectDay = { dayIndex = it }, onBack = ::pop)
                                }
                            }
                            Screen.METRIC -> {
                                val f = state.forecast
                                if (f == null) LaunchedEffect(Unit) { pop() } else MetricScreen(f, settings, metric, onBack = ::pop)
                            }
                            Screen.SETTINGS -> SettingsScreen(
                                settings = settings,
                                updates = updateUi,
                                onBack = ::pop,
                                update = vm::updateSettings,
                                onCheckUpdates = { vm.checkForUpdate() },
                                onInstallUpdate = vm::installUpdate,
                                onOpen = { page ->
                                    when (page) {
                                        SettingsPage.APPEARANCE -> push(Screen.APPEARANCE)
                                        SettingsPage.LAYOUT -> push(Screen.LAYOUT)
                                        SettingsPage.UNITS -> push(Screen.UNITS)
                                        SettingsPage.ALERTS -> tab(Screen.ALERTS)
                                        SettingsPage.FRIENDS -> push(Screen.FRIENDS)
                                        SettingsPage.APP_ICON -> push(Screen.APP_ICON)
                                        SettingsPage.WIDGETS -> push(Screen.WIDGETS)
                                        SettingsPage.DATA -> push(Screen.DATA)
                                        SettingsPage.STATS -> push(Screen.STATS)
                                    }
                                },
                            )
                            Screen.APPEARANCE -> AppearanceScreen(settings, vm::updateSettings, onBack = ::pop)
                            Screen.LAYOUT -> LayoutScreen(settings, vm::updateSettings, onBack = ::pop)
                            Screen.UNITS -> UnitsScreen(settings, vm::updateSettings, onBack = ::pop)
                            Screen.APP_ICON -> AppIconScreen(settings, vm::updateSettings, onBack = ::pop)
                            Screen.WIDGETS -> WidgetsScreen(onBack = ::pop)
                            Screen.STATS -> StatsScreen(settings, onBack = ::pop)
                            Screen.GAME -> dev.vis.weather.ui.game.DropDashScreen(onExit = ::pop)
                            Screen.DATA -> DataScreen(settings, vm::updateSettings, onBack = { pop(); vm.refresh() })
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showNav,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
                    enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it * 2 } + fadeIn(),
                    exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it * 2 } + fadeOut(),
                ) {
                    dev.vis.weather.ui.components.PillNav(
                        listOf(
                            dev.vis.weather.ui.components.PillNavItem(stringResource(R.string.m_nav_weather), Icons.Rounded.WbSunny, screen == Screen.MAIN) { tab(Screen.MAIN) },
                            dev.vis.weather.ui.components.PillNavItem(stringResource(R.string.m_nav_map), Icons.Rounded.Map, screen == Screen.MAP) { tab(Screen.MAP) },
                            dev.vis.weather.ui.components.PillNavItem(stringResource(R.string.m_nav_alerts), Icons.Rounded.Notifications, screen == Screen.ALERTS, warnings.size) { tab(Screen.ALERTS) },
                        ),
                    )
                }
            }
        }
    }
}
