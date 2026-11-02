package dev.vis.weather.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vis.weather.data.LocationProvider
import dev.vis.weather.ui.theme.WeatherTheme

private enum class Screen { MAIN, LOCATIONS, SETTINGS, DAY, METRIC }

@Composable
fun WeatherApp(vm: WeatherViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val nav by vm.nav.collectAsStateWithLifecycle()
    val updateUi by vm.update.collectAsStateWithLifecycle()
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

    WeatherTheme(settings) {
        var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
        var dayIndex by rememberSaveable { mutableIntStateOf(0) }
        var metric by rememberSaveable { mutableStateOf(dev.vis.weather.ui.components.Metric.WIND) }
        // Deep link from a widget tile / day row, applied once data for that place is on screen.
        LaunchedEffect(nav, state.forecast) {
            val n = nav ?: return@LaunchedEffect
            if (state.forecast == null) return@LaunchedEffect
            when {
                n.metric != null -> { metric = n.metric; screen = Screen.METRIC }
                n.day >= 0 -> { dayIndex = n.day.coerceAtMost(state.forecast!!.daily.lastIndex); screen = Screen.DAY }
            }
            vm.consumeNav()
        }
        BackHandler(enabled = screen != Screen.MAIN) {
            vm.clearSearch()
            screen = Screen.MAIN
        }
        UpdateDialog(updateUi, onUpdate = vm::installUpdate, onLater = vm::dismissUpdate)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val scale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            androidx.compose.runtime.CompositionLocalProvider(
                dev.vis.weather.ui.components.LocalOpenMetric provides { m -> metric = m; screen = Screen.METRIC },
            ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState != Screen.MAIN) {
                        (slideInHorizontally(spatial) { it / 3 } + fadeIn(effects)) togetherWith
                            (scaleOut(scale, targetScale = 0.94f) + fadeOut(effects))
                    } else {
                        (scaleIn(scale, initialScale = 0.94f) + fadeIn(effects)) togetherWith
                            (slideOutHorizontally(spatial) { it / 3 } + fadeOut(effects))
                    }
                },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.MAIN -> MainScreen(
                        state = state,
                        settings = settings,
                        onRefresh = vm::refresh,
                        onOpenLocations = { screen = Screen.LOCATIONS },
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onRequestLocation = requestLocation,
                        onOpenAppSettings = openAppSettings,
                        onOpenDay = { dayIndex = it; screen = Screen.DAY },
                    )
                    Screen.LOCATIONS -> LocationsScreen(
                        state = state,
                        search = search,
                        onBack = { vm.clearSearch(); screen = Screen.MAIN },
                        onQuery = vm::onQueryChange,
                        onUseCurrent = { requestLocation(); vm.clearSearch(); screen = Screen.MAIN },
                        onSelect = { vm.selectPlace(it); vm.clearSearch(); screen = Screen.MAIN },
                        onRemove = vm::removePlace,
                    )
                    Screen.DAY -> {
                        val f = state.forecast
                        if (f == null || f.daily.isEmpty()) {
                            LaunchedEffect(Unit) { screen = Screen.MAIN }
                        } else {
                            DayScreen(
                                forecast = f,
                                settings = settings,
                                index = dayIndex,
                                onSelectDay = { dayIndex = it },
                                onBack = { screen = Screen.MAIN },
                            )
                        }
                    }
                    Screen.METRIC -> {
                        val f = state.forecast
                        if (f == null) {
                            LaunchedEffect(Unit) { screen = Screen.MAIN }
                        } else {
                            MetricScreen(f, settings, metric, onBack = { screen = Screen.MAIN })
                        }
                    }
                    Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        updates = updateUi,
                        onBack = { screen = Screen.MAIN },
                        update = vm::updateSettings,
                        onCheckUpdates = { vm.checkForUpdate() },
                        onInstallUpdate = vm::installUpdate,
                    )
                }
            }
            }
        }
    }
}
