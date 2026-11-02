package dev.vis.weather.ui

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
import kotlin.math.abs

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
data class WidgetNav(val metric: dev.vis.weather.ui.components.Metric?, val day: Int)

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
                        message = if (r == null) "You're on the latest version (${Updater.currentVersion})" else "Version ${r.version} is available",
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _update.update { it.copy(checking = false, message = if (manual) "Couldn't check for updates" else null) }
            }
        }
    }

    fun installUpdate() {
        val r = _update.value.release ?: return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            _update.update { it.copy(message = "Allow installing apps from Expressive Weather, then tap Update again") }
            Updater.openInstallPermissionSettings(app)
            return
        }
        viewModelScope.launch {
            _update.update { it.copy(progress = 0f, message = null) }
            try {
                val apk = Updater.download(app, r) { p -> _update.update { it.copy(progress = p) } }
                Updater.install(app, apk)
                _update.update { it.copy(progress = null, message = "Installing ${r.version}…") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _update.update { it.copy(progress = null, message = "Update failed: ${e.message ?: "unknown error"}") }
            }
        }
    }

    fun dismissUpdate() = _update.update { it.copy(dismissed = true) }

    fun updateSettings(t: (Settings) -> Settings) {
        val before = settings.value.appIcon
        prefs.updateSettings(t)
        val after = settings.value.appIcon
        if (after != before) dev.vis.weather.data.AppIcons.apply(getApplication(), after)
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

    private fun showCached() {
        val place = _state.value.activePlace ?: return
        val cached = prefs.loadCache(place.key) ?: return
        viewModelScope.launch {
            val f = withContext(Dispatchers.Default) {
                runCatching { WeatherApi.parse(cached.first, cached.second, cached.third) }.getOrNull()
            }
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
                val (fJson, aJson) = coroutineScope {
                    val f = async { WeatherApi.forecastJson(place.lat, place.lon) }
                    val a = async { WeatherApi.airJson(place.lat, place.lon) }
                    f.await() to a.await()
                }
                val now = System.currentTimeMillis()
                val forecast = withContext(Dispatchers.Default) { WeatherApi.parse(fJson, aJson, now) }
                withContext(Dispatchers.IO) { prefs.saveCache(place.key, fJson, aJson, now) }
                _state.update { it.copy(forecast = forecast, forecastKey = place.key, loading = false, error = null) }
                updateWidgets()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = when (e) {
                            is java.net.UnknownHostException -> "No internet connection"
                            is java.net.SocketTimeoutException -> "The weather service timed out"
                            else -> e.message ?: "Something went wrong"
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
        val loc = LocationProvider.getLocation(app)
        val previous = _state.value.currentPlace
        if (loc == null) {
            if (previous != null) return previous
            val msg = if (!LocationProvider.isLocationEnabled(app)) "Location is turned off. Turn it on or search for a city."
            else "Couldn't get your location. Try again or search for a city."
            _state.update { it.copy(loading = false, error = msg) }
            return null
        }
        // Avoid re-geocoding when we haven't moved much (~1 km).
        val place = if (previous != null && abs(previous.lat - loc.latitude) < 0.01 && abs(previous.lon - loc.longitude) < 0.01) {
            previous
        } else {
            WeatherApi.reverse(app, loc.latitude, loc.longitude)
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
                it.copy(needsPermission = true, error = "Location permission denied. You can search for a city instead.")
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
                _search.update { it.copy(searching = false, error = "Search failed — check your connection") }
            }
        }
    }

    fun clearSearch() = onQueryChange("")
}
