package dev.vis.weather.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.vis.weather.MainActivity
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Place
import dev.vis.weather.data.Prefs
import dev.vis.weather.data.Settings
import dev.vis.weather.data.WeatherApi
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/** Per-widget state, stored by Glance in a DataStore per widget instance. */
object WidgetKeys {
    /** Place key pinned to this widget; absent / "" = follow the app's selected location. */
    val PLACE = stringPreferencesKey("place")
    val TAB = intPreferencesKey("tab")
    val PAGE = intPreferencesKey("page")
    val BUSY = booleanPreferencesKey("busy")
    /** Bumped whenever data changes so the composition reloads the cache. */
    val STAMP = longPreferencesKey("stamp")
}

/** Everything a widget needs to draw itself. */
class WidgetModel(
    val key: String,
    val place: Place,
    val forecast: Forecast?,
    val settings: Settings,
    val pinned: Boolean,
) {
    val fmt: Fmt? = forecast?.let { Fmt(settings, it.zone) }
    val isCurrent get() = key == UiState.CURRENT
}

object WidgetRepo {
    /** All locations a widget can show: device location (if ever resolved) + saved places. */
    fun choices(context: Context): List<Pair<String, Place>> {
        val prefs = Prefs(context)
        return listOfNotNull(prefs.currentPlace?.let { UiState.CURRENT to it }) + prefs.places.map { it.key to it }
    }

    fun resolve(context: Context, pinned: String?): Pair<String, Place>? {
        val prefs = Prefs(context)
        fun lookup(k: String?): Place? = when (k) {
            null, "" -> null
            UiState.CURRENT -> prefs.currentPlace
            else -> prefs.places.firstOrNull { it.key == k }
        }
        pinned?.let { k -> lookup(k)?.let { return k to it } }
        val sel = prefs.selection
        lookup(sel)?.let { return sel!! to it }
        return choices(context).firstOrNull()
    }

    fun model(context: Context, pinned: String?): WidgetModel? {
        val (key, place) = resolve(context, pinned) ?: return null
        val prefs = Prefs(context)
        val forecast = prefs.loadCache(place.key)?.let { (f, a, t) -> runCatching { WeatherApi.parse(f, a, t) }.getOrNull() }
        return WidgetModel(key, place, forecast, prefs.settings.value, pinned != null && pinned.isNotEmpty() && pinned == key)
    }

    suspend fun fetch(context: Context, place: Place): Boolean = runCatching {
        coroutineScope {
            val f = async { WeatherApi.forecastJson(place.lat, place.lon) }
            val a = async { WeatherApi.airJson(place.lat, place.lon) }
            val fJson = f.await()
            Prefs(context).saveCache(place.key, fJson, a.await(), System.currentTimeMillis())
        }
    }.isSuccess
}

object WidgetUpdater {
    val widgets: List<GlanceAppWidget>
        get() = listOf(
            NowWidget(), HourlyWidget(), DailyWidget(), ForecastWidget(), ClockWidget(),
            DetailsWidget(), RainWidget(), SunMoonWidget(), AirWidget(),
        )

    private suspend fun ids(context: Context): List<Pair<GlanceAppWidget, GlanceId>> {
        val mgr = GlanceAppWidgetManager(context)
        return widgets.flatMap { w -> runCatching { mgr.getGlanceIds(w.javaClass) }.getOrDefault(emptyList()).map { w to it } }
    }

    suspend fun hasWidgets(context: Context) = ids(context).isNotEmpty()

    /** Re-render every widget from the cache (after the app fetched data or settings changed). */
    suspend fun updateAll(context: Context, busy: Boolean? = null) {
        val now = System.currentTimeMillis()
        for ((w, id) in ids(context)) runCatching {
            updateAppWidgetState(context, id) { p ->
                p[WidgetKeys.STAMP] = now
                if (busy != null) p[WidgetKeys.BUSY] = busy
            }
            w.update(context, id)
        }
    }

    /** Fetch fresh data for every location shown on any widget, then re-render. */
    suspend fun refreshAll(context: Context) {
        val all = ids(context)
        if (all.isEmpty()) return
        val places = all.mapNotNull { (_, id) ->
            val st = runCatching { getAppWidgetState(context, PreferencesGlanceStateDefinition, id) }.getOrNull()
            WidgetRepo.resolve(context, st?.get(WidgetKeys.PLACE))?.second
        }.distinctBy { it.key }
        coroutineScope { places.map { async { WidgetRepo.fetch(context, it) } }.awaitAll() }
        updateAll(context, busy = false)
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!WidgetUpdater.hasWidgets(applicationContext)) return Result.success()
        WidgetUpdater.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "widget-refresh"
        private const val NOW = "widget-refresh-now"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setConstraints(network).build(),
            )
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}

// ---- interactive actions

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdater.updateAll(context, busy = true)
        WidgetUpdater.refreshAll(context)
    }
}

/** Tapping the place name pins the widget to the next location in the list. */
class NextPlaceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val choices = WidgetRepo.choices(context)
        if (choices.size < 2) return
        val st = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val current = WidgetRepo.resolve(context, st[WidgetKeys.PLACE])?.first
        val next = choices[(choices.indexOfFirst { it.first == current } + 1).mod(choices.size)]
        val hasCache = Prefs(context).loadCache(next.second.key) != null
        updateAppWidgetState(context, glanceId) {
            it[WidgetKeys.PLACE] = next.first
            it[WidgetKeys.PAGE] = 0
            it[WidgetKeys.STAMP] = System.currentTimeMillis()
            it[WidgetKeys.BUSY] = !hasCache
        }
        val widget = WidgetUpdater.widgets.firstOrNull { w ->
            GlanceAppWidgetManager(context).getGlanceIds(w.javaClass).contains(glanceId)
        } ?: return
        widget.update(context, glanceId)
        if (!hasCache) {
            WidgetRepo.fetch(context, next.second)
            updateAppWidgetState(context, glanceId) { it[WidgetKeys.BUSY] = false; it[WidgetKeys.STAMP] = System.currentTimeMillis() }
            widget.update(context, glanceId)
        }
    }
}

val TabParam = ActionParameters.Key<Int>("tab")
val PageParam = ActionParameters.Key<Int>("page")

class SetTabAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[WidgetKeys.TAB] = parameters[TabParam] ?: 0 }
        updateOne(context, glanceId)
    }
}

class SetPageAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[WidgetKeys.PAGE] = (parameters[PageParam] ?: 0).coerceAtLeast(0) }
        updateOne(context, glanceId)
    }
}

private suspend fun updateOne(context: Context, id: GlanceId) {
    val mgr = GlanceAppWidgetManager(context)
    WidgetUpdater.widgets.firstOrNull { mgr.getGlanceIds(it.javaClass).contains(id) }?.update(context, id)
}

// ---- deep links into the app

object WidgetLinks {
    const val EXTRA_PLACE = "dev.vis.weather.PLACE"
    const val EXTRA_METRIC = "dev.vis.weather.METRIC"
    const val EXTRA_DAY = "dev.vis.weather.DAY"

    fun open(context: Context, placeKey: String?, metric: String? = null, day: Int = -1): Intent =
        Intent(context, MainActivity::class.java)
            // Unique data so each tap target gets its own PendingIntent.
            .setData("weather://open/${placeKey.orEmpty()}/${metric.orEmpty()}/$day".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply {
                if (placeKey != null) putExtra(EXTRA_PLACE, placeKey)
                if (metric != null) putExtra(EXTRA_METRIC, metric)
                if (day >= 0) putExtra(EXTRA_DAY, day)
            }
}
