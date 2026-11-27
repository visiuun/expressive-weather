package dev.vis.weather.ui

import dev.vis.weather.R
import dev.vis.weather.L
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.LocationProvider
import dev.vis.weather.data.Place
import dev.vis.weather.data.Prefs
import dev.vis.weather.data.Settings
import dev.vis.weather.data.Updater
import dev.vis.weather.data.WeatherApi
import dev.vis.weather.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val places: List<Place> = emptyList(),
    /** "current", a place key, or null when the user hasn't picked anything yet. */
    val selection: String? = null,
    val currentPlace: Place? = null,
    val forecast: Forecast? = null,
    val forecastKey: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val needsPermission: Boolean = false,
) {
    val usingCurrent get() = selection == CURRENT
    val activePlace: Place?
        get() = if (usingCurrent) currentPlace else places.firstOrNull { it.key == selection }

    companion object {
        const val CURRENT = "current"
    }
}

/** Where a widget tap should land once the app is open. */
/** Where a widget / notification tap should land. [open]: "alerts", "warnings", "friends", "wardrobe", "metric:X", "day:N". */
data class WidgetNav(val metric: dev.vis.weather.ui.components.Metric?, val day: Int, val open: String? = null)

data class UpdateUi(
    val release: dev.vis.weather.data.Release? = null,
    val checking: Boolean = false,
    /** Download progress 0..1 while an update is being fetched. */
    val progress: Float? = null,
    val message: String? = null,
    val dismissed: Boolean = false,
)

data class SearchState(val query: String = "", val results: List<Place> = emptyList(), val searching: Boolean = false, val error: String? = null)

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    val settings: StateFlow<Settings> = prefs.settings

    private val _state = MutableStateFlow(
        UiState(places = prefs.places, selection = prefs.selection, currentPlace = prefs.currentPlace)
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _nav = MutableStateFlow<WidgetNav?>(null)
    val nav: StateFlow<WidgetNav?> = _nav.asStateFlow()

    private val _update = MutableStateFlow(UpdateUi())
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private var refreshJob: Job? = null
    private var searchJob: Job? = null

    init {
        showCached()
        if (_state.value.selection != null) refresh()
        if (settings.value.autoUpdate && System.currentTimeMillis() - prefs.lastUpdateCheck > 6 * 3600_000L) {
            checkForUpdate(manual = false)
        }
    }

    fun checkForUpdate(manual: Boolean = true) {
        if (_update.value.checking || _update.value.progress != null) return
        viewModelScope.launch {
            _update.update { it.copy(checking = true, message = null, dismissed = false) }
            try {
                val r = Updater.check()
                prefs.lastUpdateCheck = System.currentTimeMillis()
                _update.update {
                    it.copy(
                        checking = false, release = r,
                        message = if (r == null) L.s(R.string.m_update_latest, Updater.currentVersion) else L.s(R.string.m_update_available, r.version),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _update.update { it.copy(checking = false, message = if (manual) L.s(R.string.m_update_check_failed) else null) }
            }
        }
    }

    fun installUpdate() {
        val r = _update.value.release ?: return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            _update.update { it.copy(message = L.s(R.string.m_update_allow_install)) }
            Updater.openInstallPermissionSettings(app)
            return
        }
        viewModelScope.launch {
            _update.update { it.copy(progress = 0f, message = null) }
            try {
                val apk = Updater.download(app, r) { p -> _update.update { it.copy(progress = p) } }
                Updater.install(app, apk)
                _update.update { it.copy(progress = null, message = L.s(R.string.m_update_installing, r.version)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _update.update { it.copy(progress = null, message = L.s(R.string.m_update_failed, e.message ?: L.s(R.string.m_unknown_error))) }
            }
        }
    }

    fun dismissUpdate() = _update.update { it.copy(dismissed = true) }

    fun updateSettings(t: (Settings) -> Settings) {
        val before = settings.value
        prefs.updateSettings(t)
        if (settings.value.forecastModel != before.forecastModel || settings.value.localCorrection != before.localCorrection) {
            dev.vis.weather.data.ForecastRepo.clearMemory()
            refresh()
        }
        // The launcher icon is applied when the app goes to the background (MainActivity.onStop) so the
        // launcher doesn't refresh under the user; keep background work in step with the dynamic icon.
        if (settings.value.appIcon != before.appIcon) dev.vis.weather.alerts.AlertScheduler.sync(getApplication())
        updateWidgets()
    }

    private fun updateWidgets() {
        viewModelScope.launch { runCatching { WidgetUpdater.updateAll(getApplication()) } }
    }

    /** Opened from a widget: switch to its location, then jump to the tapped detail. */
    fun openFromWidget(placeKey: String?, metric: String?, day: Int) {
        val s = _state.value
        if (placeKey != null && placeKey != s.selection) {
            if (placeKey == UiState.CURRENT) useCurrentLocation()
            else s.places.firstOrNull { it.key == placeKey }?.let { selectPlace(it) }
        }
        val m = dev.vis.weather.ui.components.Metric.entries.firstOrNull { it.name == metric }
        if (m != null || day >= 0) _nav.value = WidgetNav(m, day)
    }

    fun consumeNav() { _nav.value = null }

    /** Opened from a notification. */
    fun openFromNotification(open: String?, placeKey: String?) {
        val s = _state.value
        if (placeKey != null && placeKey != s.selection) {
            if (placeKey == UiState.CURRENT) useCurrentLocation()
            else s.places.firstOrNull { it.key == placeKey }?.let { selectPlace(it) }
        }
        if (open == null) return
        val metric = open.removePrefix("metric:").takeIf { open.startsWith("metric:") }
            ?.let { m -> dev.vis.weather.ui.components.Metric.entries.firstOrNull { it.name == m } }
        val day = open.removePrefix("day:").takeIf { open.startsWith("day:") }?.toIntOrNull() ?: -1
        _nav.value = WidgetNav(metric, day, open.takeIf { metric == null && day < 0 })
    }

    private fun afterRefresh(place: Place, forecast: Forecast) {
        val app = getApplication<Application>()
        dev.vis.weather.data.ForecastRepo.remember(place, forecast)
        runCatching { dev.vis.weather.data.Stats.record(app, place, forecast) }
        viewModelScope.launch {
            runCatching { dev.vis.weather.alerts.WarningStore.refresh(app, place) }
            val alerts = dev.vis.weather.alerts.AlertPrefs.get(app)
            if (alerts.config(dev.vis.weather.alerts.AlertType.ONGOING).enabled) {
                val key = if (_state.value.usingCurrent) UiState.CURRENT else place.key
                runCatching { dev.vis.weather.alerts.AlertEngine.showOngoing(app, forecast, key, place, settings.value) }
            }
            runCatching { dev.vis.weather.alerts.BackgroundHooks.afterRefresh(app, settings.value, forecast) }
        }
    }

    private fun showCached() {
        val place = _state.value.activePlace ?: return
        dev.vis.weather.alerts.WarningStore.get(getApplication(), place.key)
        viewModelScope.launch {
            val f = withContext(Dispatchers.Default) { dev.vis.weather.data.ForecastRepo.parseCache(getApplication(), place) }
            if (f != null && _state.value.forecast == null) {
                _state.update { it.copy(forecast = f, forecastKey = place.key) }
            }
        }
    }

    fun refreshIfStale() {
        val f = _state.value.forecast
        if (_state.value.selection != null && !_state.value.loading &&
            (f == null || System.currentTimeMillis() - f.fetchedAt > 15 * 60_000)
        ) refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val place: Place = if (_state.value.usingCurrent) {
                    resolveCurrentPlace() ?: return@launch
                } else {
                    _state.value.activePlace ?: run {
                        _state.update { it.copy(loading = false) }
                        return@launch
                    }
                }
                val forecast = dev.vis.weather.data.ForecastRepo.fetch(getApplication(), place)
                _state.update { it.copy(forecast = forecast, forecastKey = place.key, loading = false, error = null) }
                updateWidgets()
                afterRefresh(place, forecast)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = when (e) {
                            is java.net.UnknownHostException -> L.s(R.string.m_err_no_internet)
                            is java.net.SocketTimeoutException -> L.s(R.string.m_err_timeout)
                            else -> e.message ?: L.s(R.string.m_err_generic)
                        },
                    )
                }
            }
        }
    }

    private suspend fun resolveCurrentPlace(): Place? {
        val app = getApplication<Application>()
        if (!LocationProvider.hasPermission(app)) {
            _state.update { it.copy(loading = false, needsPermission = true) }
            return null
        }
        _state.update { it.copy(needsPermission = false) }
        // The widget worker may have moved it while the app was closed.
        val previous = prefs.currentPlace ?: _state.value.currentPlace
        val place = LocationProvider.currentPlace(app, previous)
        if (place == null) {
            if (previous != null) return previous
            val msg = if (!LocationProvider.isLocationEnabled(app)) L.s(R.string.m_err_location_off)
            else L.s(R.string.m_err_location_failed)
            _state.update { it.copy(loading = false, error = msg) }
            return null
        }
        prefs.currentPlace = place
        _state.update { it.copy(currentPlace = place) }
        return place
    }

    fun useCurrentLocation() {
        prefs.selection = UiState.CURRENT
        _state.update { it.copy(selection = UiState.CURRENT, forecast = null, error = null) }
        showCached()
        refresh()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            useCurrentLocation()
        } else {
            _state.update {
                it.copy(needsPermission = true, error = L.s(R.string.m_err_permission_denied))
            }
        }
    }

    fun selectPlace(place: Place) {
        val places = _state.value.places
        val newPlaces = if (places.none { it.key == place.key }) places + place else places
        prefs.places = newPlaces
        prefs.selection = place.key
        _state.update { it.copy(places = newPlaces, selection = place.key, forecast = null, error = null) }
        showCached()
        refresh()
    }

    fun removePlace(place: Place) {
        val newPlaces = _state.value.places.filter { it.key != place.key }
        prefs.places = newPlaces
        if (_state.value.selection == place.key) {
            val next = newPlaces.firstOrNull()?.key ?: (if (_state.value.currentPlace != null) UiState.CURRENT else null)
            prefs.selection = next
            _state.update { it.copy(places = newPlaces, selection = next, forecast = null) }
            showCached()
            if (next != null) refresh()
        } else {
            _state.update { it.copy(places = newPlaces) }
        }
    }

    fun movePlace(place: Place, delta: Int) {
        val list = _state.value.places.toMutableList()
        val i = list.indexOfFirst { it.key == place.key }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        prefs.places = list
        _state.update { it.copy(places = list) }
    }

    fun onQueryChange(q: String) {
        val code = q.trim().lowercase()
        if (code in dev.vis.weather.ui.game.DropDash.codes) {
            _search.update { SearchState() }
            _nav.value = WidgetNav(null, -1, "game")
            return
        }
        dev.vis.weather.ui.theme.SecretThemes.unlock(getApplication(), q)?.let { theme ->
            _search.update { SearchState() }
            updateSettings { it.copy(secretTheme = theme.id) }
            android.widget.Toast.makeText(getApplication(), L.s(R.string.m_secret_theme_unlocked, theme.name), android.widget.Toast.LENGTH_LONG).show()
            return
        }
        _search.update { it.copy(query = q, error = null) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _search.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _search.update { it.copy(searching = true) }
            try {
                val r = WeatherApi.search(q)
                _search.update { it.copy(results = r, searching = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.update { it.copy(searching = false, error = L.s(R.string.m_search_failed)) }
            }
        }
    }

    fun clearSearch() = onQueryChange("")
}
