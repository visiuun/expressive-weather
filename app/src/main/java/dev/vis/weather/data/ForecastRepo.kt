package dev.vis.weather.data

import android.content.Context
import dev.vis.weather.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * One place to get a forecast for any [Place] from anywhere (app, widgets, notifications, friends):
 * returns the cached copy when fresh enough, otherwise fetches and caches; falls back to the stale cache offline.
 */
object ForecastRepo {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val memory = ConcurrentHashMap<String, Forecast>()

    fun cached(context: Context, place: Place): Forecast? {
        memory[place.key]?.let { return it }
        return parseCache(context, place)?.also { memory[place.key] = it }
    }

    /** Parse the disk cache for [place] (no memory cache). */
    fun parseCache(context: Context, place: Place): Forecast? {
        val prefs = Prefs(context)
        val c = prefs.loadCache(place.key) ?: return null
        val (marine, metar) = prefs.loadExtras(place.key)
        return runCatching {
            WeatherApi.parse(c.first, c.second, c.third, marine, Observations.nearest(metar, place.lat, place.lon))
        }.getOrNull()
    }

    /** Remember a forecast the app just fetched so background work doesn't refetch it. */
    fun remember(place: Place, forecast: Forecast) {
        memory[place.key] = forecast
    }

    suspend fun get(context: Context, place: Place, maxAgeMs: Long = 20 * 60_000L): Forecast? {
        val lock = locks.getOrPut(place.key) { Mutex() }
        return lock.withLock {
            val cached = withContext(Dispatchers.Default) { cached(context, place) }
            if (cached != null && System.currentTimeMillis() - cached.fetchedAt < maxAgeMs) return@withLock cached
            runCatching { fetch(context, place) }.getOrNull() ?: cached
        }
    }

    /** Always hits the network; throws on failure. Uses the chosen model, sea data and the local station correction. */
    suspend fun fetch(context: Context, place: Place): Forecast {
        val prefs = Prefs(context)
        val settings = prefs.settings.value
        val (fJson, aJson, extras) = coroutineScope {
            val f = async { WeatherApi.forecastJson(place.lat, place.lon, settings.forecastModel) }
            val a = async { WeatherApi.airJson(place.lat, place.lon) }
            val m = async { runCatching { WeatherApi.marineJson(place.lat, place.lon) }.getOrNull() }
            val o = async { if (settings.localCorrection) runCatching { WeatherApi.metarJson(place.lat, place.lon) }.getOrNull() else null }
            Triple(f.await(), a.await(), m.await() to o.await())
        }
        val (marine, metar) = extras
        val now = System.currentTimeMillis()
        val forecast = withContext(Dispatchers.Default) {
            WeatherApi.parse(fJson, aJson, now, marine, Observations.nearest(metar, place.lat, place.lon))
        }
        withContext(Dispatchers.IO) { prefs.saveCache(place.key, fJson, aJson, now, marine, metar) }
        memory[place.key] = forecast
        return forecast
    }

    /** Forget in-memory copies (e.g. after the forecast model changed). */
    fun clearMemory() = memory.clear()

    /** Resolve "auto" / "current" / place key to a Place using what the app knows. */
    fun resolve(context: Context, location: String): Place? {
        val prefs = Prefs(context)
        val key = if (location == "auto") prefs.selection else location
        return when (key) {
            null -> prefs.currentPlace ?: prefs.places.firstOrNull()
            "current" -> prefs.currentPlace
            else -> prefs.places.firstOrNull { it.key == key } ?: Friends.all(context).firstOrNull { it.place.key == key }?.place
        }
    }

    fun label(context: Context, location: String): String = when (location) {
        "auto" -> context.getString(R.string.d_loc_selected_place)
        "current" -> context.getString(R.string.d_loc_current_location)
        else -> resolve(context, location)?.name ?: context.getString(R.string.d_loc_saved_place)
    }
}
