package dev.vis.weather.alerts

import dev.vis.weather.inSentence
import android.content.Context
import androidx.core.content.edit
import dev.vis.weather.L
import dev.vis.weather.R
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.ForecastRepo
import dev.vis.weather.data.Friends
import dev.vis.weather.data.Place
import dev.vis.weather.data.Prefs
import dev.vis.weather.data.Settings
import dev.vis.weather.data.Sky
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** Last known warnings per place, shared by the app (banners) and the background job (notifications). */
object WarningStore {
    data class Entry(val time: Long, val warnings: List<Warning>)

    private val _byPlace = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val byPlace: StateFlow<Map<String, Entry>> = _byPlace.asStateFlow()

    private fun sp(c: Context) = c.getSharedPreferences("warnings", Context.MODE_PRIVATE)

    fun get(context: Context, key: String): Entry? {
        _byPlace.value[key]?.let { return it }
        val s = sp(context).getString(key, null) ?: return null
        return runCatching {
            val o = JSONObject(s)
            val a = o.getJSONArray("w")
            Entry(o.getLong("t"), (0 until a.length()).map { Warning.fromJson(a.getJSONObject(it)) })
        }.getOrNull()?.also { _byPlace.value = _byPlace.value + (key to it) }
    }

    fun put(context: Context, key: String, list: List<Warning>) {
        val e = Entry(System.currentTimeMillis(), list)
        _byPlace.value = _byPlace.value + (key to e)
        sp(context).edit { putString(key, JSONObject().put("t", e.time).put("w", JSONArray(list.map { it.toJson() })).toString()) }
    }

    /** Fetch if older than [maxAgeMs]; returns the cached list on failure. */
    suspend fun refresh(context: Context, place: Place, maxAgeMs: Long = 25 * 60_000L): List<Warning> {
        val cached = get(context, place.key)
        if (cached != null && System.currentTimeMillis() - cached.time < maxAgeMs) return active(cached.warnings)
        return runCatching { WarningsApi.fetch(context, place).also { put(context, place.key, it) } }
            .getOrElse { cached?.warnings?.let(::active) ?: emptyList() }
    }

    fun active(list: List<Warning>): List<Warning> {
        val now = System.currentTimeMillis() / 1000
        return list.filter { it.expires == 0L || it.expires > now }
    }
}

object AlertEngine {
    private fun Double.ok() = !isNaN()

    private fun fmt(settings: Settings, f: Forecast) = Fmt(settings, f.zone)

    private fun targetPlace(context: Context, cfg: AlertConfig): Pair<String, Place>? {
        val prefs = Prefs(context)
        val key = if (cfg.location == "auto") prefs.selection ?: "current" else cfg.location
        val place = ForecastRepo.resolve(context, key) ?: return null
        return (if (key == "current") "current" else place.key) to place
    }

    // ================================================================ periodic watch

    /** Run by the periodic background job. Never throws. */
    suspend fun runWatch(context: Context) {
        val alerts = AlertPrefs.get(context)
        val settings = Prefs(context).settings.value
        val cfg = alerts.configs.value
        alerts.prune()

        runCatching { if (cfg.getValue(AlertType.WARNINGS).enabled) checkWarnings(context, settings, cfg.getValue(AlertType.WARNINGS)) }
        runCatching { if (cfg.getValue(AlertType.FRIENDS).enabled) checkFriends(context, cfg.getValue(AlertType.FRIENDS)) }
        runCatching { if (cfg.getValue(AlertType.NOWCAST).enabled) checkNowcast(context, settings, cfg.getValue(AlertType.NOWCAST)) else Notifier.cancelNowcast(context) }
        runCatching { cfg.getValue(AlertType.RAIN).takeIf { it.enabled && it.allDay }?.let { checkRainAllDay(context, settings, it) } }
        runCatching { if (cfg.getValue(AlertType.STORM).enabled) checkStorm(context, settings, cfg.getValue(AlertType.STORM)) }
        runCatching { if (cfg.getValue(AlertType.PRESSURE).enabled) checkPressure(context, settings, cfg.getValue(AlertType.PRESSURE)) }
        runCatching { if (cfg.getValue(AlertType.HEAT).enabled) checkHeat(context, settings, cfg.getValue(AlertType.HEAT)) }
        runCatching { if (cfg.getValue(AlertType.ONGOING).enabled) updateOngoing(context, settings, cfg.getValue(AlertType.ONGOING)) else Notifier.cancelOngoing(context) }
    }

    // ---- warnings

    private suspend fun checkWarnings(context: Context, settings: Settings, cfg: AlertConfig) {
        val alerts = AlertPrefs.get(context)
        val prefs = Prefs(context)
        val places = buildList {
            targetPlace(context, cfg)?.let { add(it) }
            if ("saved" in cfg.flags) {
                prefs.currentPlace?.let { add("current" to it) }
                prefs.places.forEach { add(it.key to it) }
            }
        }.distinctBy { it.second.key }
        val min = Severity.entries[cfg.threshold.roundToInt().coerceIn(0, 3)]
        for ((key, place) in places) {
            val list = WarningStore.refresh(context, place)
            for (w in list) {
                if (w.severity < min) continue
                // Key includes severity so an upgrade (yellow → amber) notifies again.
                val dedupe = "w:${place.key}:${w.id}:${w.severity}"
                if (alerts.wasSent(dedupe)) continue
                alerts.markSent(dedupe)
                notifyWarning(context, w, key, place.name, AlertType.WARNINGS, settings)
            }
        }
    }

    fun warningNote(w: Warning, placeKey: String?, placeName: String, type: AlertType, settings: Settings, zone: ZoneId = ZoneId.systemDefault()): Note {
        val f = Fmt(settings, zone)
        val now = System.currentTimeMillis() / 1000
        val window = when {
            w.onset > now && w.expires > 0 -> L.s(R.string.al_warning_from_until, f.dayTime(w.onset), f.dayTime(w.expires))
            w.expires > 0 -> L.s(R.string.al_warning_until, f.dayTime(w.expires))
            else -> ""
        }
        val title = L.s(R.string.al_warning_title, w.severity.colorName, w.hazard.label.inSentence())
        return Note(
            type = type,
            key = "warning:${w.id}",
            title = title + if (type == AlertType.FRIENDS) " · $placeName" else "",
            text = listOf(w.headline.ifBlank { w.title }, window).filter { it.isNotBlank() }.joinToString(" · "),
            bigText = listOf(w.headline.ifBlank { w.title }, window, w.description.take(600), L.s(R.string.al_warning_issued_by, w.sender))
                .filter { it.isNotBlank() }.joinToString("\n\n"),
            placeKey = placeKey,
            placeName = placeName,
            icon = R.drawable.ic_stat_warning,
            urgent = w.severity >= Severity.SEVERE,
            open = "warnings",
            color = w.severity.argb.toInt(),
        )
    }

    private fun notifyWarning(context: Context, w: Warning, placeKey: String, placeName: String, type: AlertType, settings: Settings) {
        Notifier.post(context, warningNote(w, placeKey, placeName, type, settings))
    }

    private suspend fun checkFriends(context: Context, cfg: AlertConfig) {
        val alerts = AlertPrefs.get(context)
        val settings = Prefs(context).settings.value
        val min = Severity.entries[cfg.threshold.roundToInt().coerceIn(0, 3)]
        for (friend in Friends.all(context)) {
            val list = WarningStore.refresh(context, friend.place, 40 * 60_000L)
            for (w in list.filter { it.severity >= min }) {
                val dedupe = "fw:${friend.id}:${w.id}:${w.severity}"
                if (alerts.wasSent(dedupe)) continue
                alerts.markSent(dedupe)
                val note = warningNote(w, friend.place.key, friend.name, AlertType.FRIENDS, settings).copy(
                    key = "friend:${friend.id}:${w.id}",
                    title = L.s(R.string.al_warning_title_friend, friend.name, w.severity.colorName.inSentence(), w.hazard.label.inSentence()),
                    open = "friends",
                    icon = R.drawable.ic_stat_person,
                    placeName = friend.place.name,
                )
                Notifier.post(context, note)
            }
        }
    }

    // ---- nowcast (imminent rain & snow)

    private fun isSnowAt(f: Forecast, t: Long): Boolean {
        val h = f.hourlyAll.lastOrNull { it.time <= t } ?: return false
        val sky = WeatherInfo.sky(h.code)
        return sky == Sky.SNOW || (h.temp.ok() && h.temp <= 0.5 && sky != Sky.CLEAR)
    }

    /** 0 light, 1 moderate, 2 heavy. */
    private fun intensity(mm15: Double) = when {
        mm15 < 0.5 -> 0
        mm15 < 2.0 -> 1
        else -> 2
    }

    private suspend fun checkNowcast(context: Context, settings: Settings, cfg: AlertConfig) {
        val (key, place) = targetPlace(context, cfg) ?: return
        val f = ForecastRepo.get(context, place, maxAgeMs = 12 * 60_000L) ?: return
        evaluateNowcast(context, f, key, place, cfg, force = false)
    }

    /** Returns true if something was shown. */
    fun evaluateNowcast(context: Context, f: Forecast, key: String, place: Place, cfg: AlertConfig, force: Boolean): Boolean {
        val alerts = AlertPrefs.get(context)
        val nowMs = System.currentTimeMillis()
        val now = nowMs / 1000
        // Each minutely_15 value is the precipitation sum of the 15 minutes *before* its timestamp.
        val slots = f.minutely.map { (it.time - 900) to it.precip }.filter { it.first + 900 > now }
        if (slots.isEmpty()) return false
        val thr = cfg.threshold.coerceAtLeast(0.05)
        val window = now + cfg.lookahead.coerceIn(15, 180) * 60
        val wetNow = slots.first().let { it.first <= now && it.second >= thr }
        val stateKey = "nowcast:${place.key}"
        val state = alerts.getString(stateKey) ?: "dry"
        val includeSnow = "snow" in cfg.flags
        val steps = slots.take(8).map { Notifier.NowcastStep(((it.first - now) / 60).toInt(), it.second) }
        if (wetNow) {
            val end = slots.firstOrNull { it.first > now && it.second < thr * 0.5 }?.first
            val snow = isSnowAt(f, now)
            if (snow && !includeSnow) return false
            val what = L.s(if (snow) R.string.al_nowcast_chip_snow else R.string.al_nowcast_chip_rain)
            val title = L.s(if (snow) R.string.al_nowcast_snowing_now else R.string.al_nowcast_raining_now)
            val titleWithPlace = if (key != "current") L.s(if (snow) R.string.al_nowcast_snowing_now_in else R.string.al_nowcast_raining_now_in, place.name) else title
            val text = if (end != null) ((end - now) / 60).coerceAtLeast(5).toInt().let { L.p(R.plurals.al_nowcast_ending_in, it) }
            else L.s(R.string.al_nowcast_continuing)
            val firstTime = state != "active"
            if (firstTime && !force && nowMs - alerts.lastSent("nowcast-active:${place.key}") < 90 * 60_000L) {
                alerts.putString(stateKey, "active")
                return false
            }
            Notifier.postNowcast(context, titleWithPlace, text, what, end?.times(1000), steps, snow, key, place.name, silent = !firstTime && !force)
            if (firstTime) alerts.markSent("nowcast-active:${place.key}")
            alerts.putString(stateKey, "active")
            if (firstTime || force) alerts.log(AlertLog(nowMs, AlertType.NOWCAST, title, text, place.name))
            return true
        }
        val start = slots.firstOrNull { it.first in now..window && it.second >= thr }
        if (start != null) {
            val startTime = start.first
            val snow = isSnowAt(f, startTime)
            if (snow && !includeSnow) return false
            val peak = slots.filter { it.first >= startTime }.take(4).maxOf { it.second }
            val kind = L.s(
                if (snow) listOf(R.string.al_nowcast_kind_light_snow, R.string.al_nowcast_kind_snow, R.string.al_nowcast_kind_heavy_snow)[intensity(peak)]
                else listOf(R.string.al_nowcast_kind_light_rain, R.string.al_nowcast_kind_rain, R.string.al_nowcast_kind_heavy_rain)[intensity(peak)]
            )
            val mins = ((startTime - now) / 60).toInt().coerceAtLeast(1)
            val title = if (key != "current") L.s(R.string.al_nowcast_approaching_place, kind, place.name) else L.s(R.string.al_nowcast_approaching, kind)
            val text = if (mins <= 5) L.s(if (snow) R.string.al_nowcast_snow_any_moment else R.string.al_nowcast_rain_any_moment)
            else L.p(R.plurals.al_nowcast_starting_in, mins)
            val firstTime = state != "approaching"
            // Smart cooldown: a new shower right after the last one updates quietly instead of buzzing again.
            val recently = nowMs - alerts.lastSent("nowcast:${place.key}") < 75 * 60_000L
            Notifier.postNowcast(
                context, title, text, L.s(R.string.al_nowcast_chip_minutes, mins), startTime * 1000, steps, snow, key, place.name,
                silent = !force && (!firstTime || recently),
            )
            if (firstTime && !recently) {
                alerts.markSent("nowcast:${place.key}")
                alerts.log(AlertLog(nowMs, AlertType.NOWCAST, title, text, place.name))
            }
            alerts.putString(stateKey, "approaching")
            return true
        }
        if (state != "dry") {
            Notifier.cancelNowcast(context)
            alerts.putString(stateKey, "dry")
        }
        return false
    }

    // ---- rain (all-day mode)

    private suspend fun checkRainAllDay(context: Context, settings: Settings, cfg: AlertConfig) {
        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val inWindow = if (cfg.time <= cfg.endTime) nowMin in cfg.time..cfg.endTime else nowMin >= cfg.time || nowMin <= cfg.endTime
        if (!inWindow) return
        val (key, place) = targetPlace(context, cfg) ?: return
        val f = ForecastRepo.get(context, place) ?: return
        rainNote(f, key, place, cfg, settings)?.let { (dedupe, note) ->
            val alerts = AlertPrefs.get(context)
            if (alerts.wasSent(dedupe) || System.currentTimeMillis() - alerts.lastSent("rain:${place.key}") < 3 * 3600_000L) return
            alerts.markSent(dedupe)
            alerts.markSent("rain:${place.key}")
            Notifier.post(context, note)
        }
    }

    private fun rainNote(f: Forecast, key: String, place: Place, cfg: AlertConfig, settings: Settings): Pair<String, Note>? {
        val fm = fmt(settings, f)
        val now = f.current.time
        val hours = f.hourlyAll.filter { it.time >= now - 1800 && it.time < now + cfg.lookahead.coerceIn(1, 12) * 3600 }
        val wet = hours.firstOrNull { it.precipProb.ok() && it.precipProb >= cfg.threshold && (!it.precip.ok() || it.precip >= 0.1) } ?: return null
        val total = hours.dropWhile { it != wet }.sumOf { if (it.precip.ok()) it.precip else 0.0 }
        val snow = WeatherInfo.sky(wet.code) == Sky.SNOW
        val title = L.s(if (snow) R.string.al_snow_expected_at else R.string.al_rain_expected_at, fm.hour(wet.time))
        val text = L.s(if (snow) R.string.al_snow_advice else R.string.al_rain_advice)
        val big = if (total >= 0.1) L.s(R.string.al_rain_big_amount, text, wet.precipProb.roundToInt(), fm.precip(total), cfg.lookahead)
        else L.s(R.string.al_rain_big, text, wet.precipProb.roundToInt())
        return "rain:${place.key}:${wet.time}" to Note(
            AlertType.RAIN, "rain:${place.key}", title, text, big, key, place.name,
            if (snow) R.drawable.ic_stat_snow else R.drawable.ic_stat_rain, open = "metric:PRECIP",
        )
    }

    // ---- wind & storms

    private suspend fun checkStorm(context: Context, settings: Settings, cfg: AlertConfig) {
        val (key, place) = targetPlace(context, cfg) ?: return
        val f = ForecastRepo.get(context, place) ?: return
        val note = stormNote(f, key, place, cfg, settings) ?: return
        val alerts = AlertPrefs.get(context)
        val day = Instant.now().atZone(f.zone).toLocalDate()
        val dedupe = "storm:${place.key}:$day:${note.title.take(12)}"
        if (alerts.wasSent(dedupe) || System.currentTimeMillis() - alerts.lastSent("storm:${place.key}") < 8 * 3600_000L) return
        alerts.markSent(dedupe)
        alerts.markSent("storm:${place.key}")
        Notifier.post(context, note)
    }

    private fun stormNote(f: Forecast, key: String, place: Place, cfg: AlertConfig, settings: Settings): Note? {
        val fm = fmt(settings, f)
        val now = f.current.time
        val hours = f.hourlyAll.filter { it.time >= now - 1800 && it.time < now + cfg.lookahead.coerceIn(3, 24) * 3600 }
        val thunder = hours.firstOrNull { it.code in 95..99 && (!it.precipProb.ok() || it.precipProb >= 40) }
        val gusty = hours.filter { it.windGust.ok() && it.windGust >= cfg.threshold }
        if (thunder == null && gusty.isEmpty()) return null
        return if (thunder != null) {
            Note(
                AlertType.STORM, "storm:${place.key}", L.s(R.string.al_storm_thunder_title, fm.hour(thunder.time)),
                L.s(R.string.al_storm_thunder_text),
                placeKey = key, placeName = place.name, icon = R.drawable.ic_stat_warning, open = "metric:PRECIP",
            )
        } else {
            val peak = gusty.maxBy { it.windGust }
            Note(
                AlertType.STORM, "storm:${place.key}", L.s(R.string.al_storm_gust_title, fm.wind(peak.windGust), fm.hour(peak.time)),
                L.s(R.string.al_storm_gust_text, fm.hour(gusty.first().time)),
                placeKey = key, placeName = place.name, icon = R.drawable.ic_stat_wind, open = "metric:WIND",
            )
        }
    }

    // ---- pressure

    private suspend fun checkPressure(context: Context, settings: Settings, cfg: AlertConfig) {
        val (key, place) = targetPlace(context, cfg) ?: return
        val f = ForecastRepo.get(context, place) ?: return
        val note = pressureNote(f, key, place, cfg, settings, force = false) ?: return
        val alerts = AlertPrefs.get(context)
        if (System.currentTimeMillis() - alerts.lastSent("pressure:${place.key}") < 18 * 3600_000L) return
        alerts.markSent("pressure:${place.key}")
        Notifier.post(context, note)
    }

    fun pressureThreshold(level: Int) = when (level) { 0 -> 6.0; 2 -> 2.5; else -> 4.0 }

    private fun pressureNote(f: Forecast, key: String, place: Place, cfg: AlertConfig, settings: Settings, force: Boolean): Note? {
        val fm = fmt(settings, f)
        val now = f.current.time
        val hrs = f.hourlyAll.filter { it.time >= now - 3600 && it.time <= now + 12 * 3600 && it.pressure.ok() }
        var best = 0.0
        var at = 0L
        for (i in hrs.indices) {
            val j = hrs.indexOfFirst { it.time >= hrs[i].time + 3 * 3600 }
            if (j < 0) break
            val drop = hrs[i].pressure - hrs[j].pressure
            if (drop > best) { best = drop; at = hrs[i].time }
        }
        val thr = pressureThreshold(cfg.level)
        if (best < thr && !force) return null
        val value = fm.pressureValue(best)
        val amount = (if (settings.pressureUnit == dev.vis.weather.data.PressureUnit.INHG) String.format(java.util.Locale.getDefault(), "%.2f", value)
        else value.roundToInt().toString()) + " " + fm.pressureUnit
        val titleRes = when {
            best >= 8 -> R.string.al_pressure_rapid
            best >= 5 -> R.string.al_pressure_significant
            else -> R.string.al_pressure_moderate
        }
        return Note(
            AlertType.PRESSURE, "pressure:${place.key}", L.s(titleRes, amount),
            L.s(R.string.al_pressure_text, amount, fm.hour(if (at == 0L) now else at)),
            placeKey = key, placeName = place.name, icon = R.drawable.ic_stat_bell, open = "metric:PRESSURE",
        )
    }

    // ---- dangerous wet bulb

    private suspend fun checkHeat(context: Context, settings: Settings, cfg: AlertConfig) {
        val (key, place) = targetPlace(context, cfg) ?: return
        val f = ForecastRepo.get(context, place) ?: return
        val note = heatNote(f, key, place, cfg, settings, force = false) ?: return
        val alerts = AlertPrefs.get(context)
        // Once per level per day: notify again only if it gets worse.
        val day = Instant.now().atZone(f.zone).toLocalDate()
        val dedupe = "heat:${place.key}:$day:${note.title}"
        if (alerts.wasSent(dedupe)) return
        alerts.markSent(dedupe)
        Notifier.post(context, note)
    }

    private fun heatNote(f: Forecast, key: String, place: Place, cfg: AlertConfig, settings: Settings, force: Boolean): Note? {
        val fm = fmt(settings, f)
        val now = f.current.time
        val hours = f.hourlyAll.filter { it.time >= now - 1800 && it.time < now + cfg.lookahead.coerceIn(3, 24) * 3600 }
        fun wb(h: dev.vis.weather.data.Hour) = if (!h.wetBulb.isNaN()) h.wetBulb else Briefings.wetBulb(h.temp, h.humidity)
        val nowWb = Briefings.wetBulb(f.current.temp, f.current.humidity)
        val peak = hours.map { it to wb(it) }.filter { it.second.ok() }.maxByOrNull { it.second }
        val peakWb = maxOf(peak?.second ?: Double.NaN, nowWb).takeIf { it.ok() } ?: return null
        if (peakWb < cfg.threshold && !force) return null
        val (level, guidance) = Briefings.coolingLevel(peakWb)
        val first = hours.firstOrNull { wb(it) >= cfg.threshold }
        val dangerNow = nowWb.ok() && nowWb >= cfg.threshold
        val title = when {
            peakWb < cfg.threshold -> L.s(R.string.al_heat_safe)
            dangerNow -> L.s(R.string.al_heat_danger_now, fm.temp(nowWb))
            else -> L.s(R.string.al_heat_danger_from, fm.hour(first?.time ?: peak!!.first.time))
        }
        val text = if (peak != null) L.s(R.string.al_heat_text_at, fm.temp(peakWb), level, fm.hour(peak.first.time), guidance)
        else L.s(R.string.al_heat_text, fm.temp(peakWb), level, guidance)
        return Note(
            AlertType.HEAT, "heat:${place.key}", title, text,
            placeKey = key, placeName = place.name, icon = R.drawable.ic_stat_thermo,
            urgent = peakWb >= cfg.threshold, open = "metric:HUMIDITY",
        )
    }

    // ---- status bar weather

    private suspend fun updateOngoing(context: Context, settings: Settings, cfg: AlertConfig) {
        val (key, place) = targetPlace(context, cfg) ?: return
        val f = ForecastRepo.get(context, place) ?: ForecastRepo.cached(context, place) ?: return
        showOngoing(context, f, key, place, settings)
    }

    fun showOngoing(context: Context, f: Forecast, key: String, place: Place, settings: Settings) {
        val fm = fmt(settings, f)
        val c = f.current
        val today = f.today
        val title = "${fm.temp(c.temp)} · ${WeatherInfo.describe(c.code, c.isDay)}"
        val text = if (today != null) L.s(R.string.al_ongoing_feels_high_low, fm.temp(c.feelsLike), fm.temp(today.max), fm.temp(today.min))
        else L.s(R.string.al_ongoing_feels, fm.temp(c.feelsLike))
        val upcoming = f.upcoming.drop(1)
        val big = buildString {
            append(text)
            Briefings.rainTiming(upcoming.take(12), fm)?.let { append("\n").append(it) }
            append("\n").append(L.s(R.string.al_ongoing_wind, fm.wind(c.windSpeed), WeatherInfo.compass(c.windDir)))
            if (c.uv.ok() && c.isDay) append(" · UV ${c.uv.roundToInt()}")
            append("\n").append(upcoming.filterIndexed { i, _ -> i % 3 == 0 }.take(5).joinToString("   ") { "${fm.hour(it.time)} ${fm.temp(it.temp)}" })
        }
        Notifier.postOngoing(context, fm.temp(c.temp), title, text, big, key, place.name)
    }

    // ================================================================ daily alerts

    /**
     * Run at the configured time. When [force] (test button), thresholds are ignored where it makes sense.
     * Returns true if notified, false if there was nothing to say, null if the forecast couldn't be loaded.
     */
    suspend fun runDaily(context: Context, type: AlertType, force: Boolean = false): Boolean? {
        val alerts = AlertPrefs.get(context)
        val cfg = alerts.config(type)
        if (!cfg.enabled && !force) return false
        val settings = Prefs(context).settings.value
        val (key, place) = targetPlace(context, cfg) ?: return false
        val f = ForecastRepo.get(context, place, maxAgeMs = 30 * 60_000L) ?: return null
        val day = LocalDate.now()
        val dedupe = "daily:${type.name}:$day"
        if (!force && alerts.wasSent(dedupe)) return false
        val note = dailyNote(context, type, f, key, place, cfg, settings, force) ?: return false
        if (!force) alerts.markSent(dedupe)
        Notifier.post(context, note)
        return true
    }

    private suspend fun dailyNote(
        context: Context, type: AlertType, f: Forecast, key: String, place: Place, cfg: AlertConfig, settings: Settings, force: Boolean,
    ): Note? {
        val fm = fmt(settings, f)
        val today = f.today ?: return null
        val now = f.current.time
        val base = Note(type, "${type.name}:${place.key}", "", "", placeKey = key, placeName = place.name)
        return when (type) {
            AlertType.TODAY -> Briefings.today(f, fm, place.name)?.let { base.copy(title = it.title, text = it.text, bigText = it.big, icon = R.drawable.ic_stat_sun) }
            AlertType.TOMORROW -> Briefings.tomorrow(f, fm)?.let { base.copy(title = it.title, text = it.text, bigText = it.big, icon = R.drawable.ic_stat_cloud, open = "day:1") }
            AlertType.RAIN -> rainNote(f, key, place, cfg, settings)?.second
                ?: if (force) base.copy(title = L.s(R.string.al_no_rain_title), text = L.p(R.plurals.al_no_rain_text, cfg.lookahead), icon = R.drawable.ic_stat_rain) else null
            AlertType.WARDROBE -> {
                val tomorrow = LocalTime.now().hour >= 15
                val work = cfg.extra.takeIf { it.isNotBlank() }?.let { ForecastRepo.resolve(context, it) }?.let { ForecastRepo.get(context, it) }
                val commute = cfg.flags.firstOrNull { it in setOf("walk", "transit", "balanced") } ?: "balanced"
                Wardrobe.advise(f, work, fm, tomorrow, cfg.level, commute)?.let { w ->
                    base.copy(
                        title = L.s(if (tomorrow) R.string.al_wardrobe_title_tomorrow else R.string.al_wardrobe_title_today),
                        text = w.items.joinToString(" · ") { it.label },
                        bigText = w.items.joinToString(" · ") { it.label } + "\n" + w.summary,
                        icon = R.drawable.ic_stat_shirt, open = "wardrobe",
                    )
                }
            }
            AlertType.FROST -> {
                val tomorrowDate = today.date.plusDays(1)
                val night = f.hourlyAll.filter {
                    val t = Instant.ofEpochSecond(it.time).atZone(f.zone)
                    it.time >= now && (t.toLocalDate() == today.date || (t.toLocalDate() == tomorrowDate && t.hour < 10))
                }
                val low = night.minOfOrNull { if (it.temp.ok()) it.temp else 99.0 } ?: return null
                if (low > cfg.threshold && !force) return null
                base.copy(
                    title = L.s(if (low <= cfg.threshold) R.string.al_frost_title else R.string.al_frost_none_title),
                    text = L.s(if (low <= cfg.threshold) R.string.al_frost_low_advice else R.string.al_frost_low, fm.temp(low)),
                    icon = R.drawable.ic_stat_snow, open = "day:1",
                )
            }
            AlertType.UV -> {
                val hrs = Briefings.hoursOf(f, today.date)
                val peak = hrs.filter { it.uv.ok() }.maxByOrNull { it.uv } ?: return null
                if (peak.uv < cfg.threshold && !force) return null
                val protect = hrs.filter { it.uv.ok() && it.uv >= 3 }
                val text = if (protect.isNotEmpty()) L.s(R.string.al_uv_peaks_protect, fm.hour(peak.time), fm.hour(protect.first().time), fm.hour(protect.last().time + 3600))
                else L.s(R.string.al_uv_peaks, fm.hour(peak.time))
                base.copy(
                    title = L.s(R.string.al_uv_title, peak.uv.roundToInt(), WeatherInfo.uvLevel(peak.uv)),
                    text = text,
                    bigText = "$text ${WeatherInfo.uvAdvice(peak.uv)}",
                    icon = R.drawable.ic_stat_sun, open = "metric:UV",
                )
            }
            AlertType.HEAT -> null
            AlertType.AIR -> {
                val air = f.air ?: return null
                val next = air.hourly.filter { it.time >= now && it.time < now + 18 * 3600 && it.euAqi.ok() }
                val peak = next.maxByOrNull { it.euAqi } ?: return null
                val band = Briefings.eaqiBand(peak.euAqi)
                if (band < cfg.threshold.roundToInt() && !force) return null
                base.copy(
                    title = L.s(R.string.al_air_title, Briefings.eaqiNames[band]),
                    text = L.s(if (band >= 3) R.string.al_air_text_advice else R.string.al_air_text, peak.euAqi.roundToInt(), fm.hour(peak.time)),
                    icon = R.drawable.ic_stat_leaf, open = "metric:AIR",
                )
            }
            AlertType.POLLEN -> pollenNote(f, cfg, force)?.let { (t, x) -> base.copy(title = t, text = x, icon = R.drawable.ic_stat_leaf, open = "metric:POLLEN") }
            AlertType.TABLET -> {
                if ("pollen-days" in cfg.flags && !force) {
                    val pollen = AlertPrefs.get(context).config(AlertType.POLLEN)
                    if (pollenNote(f, pollen, false) == null) return null
                }
                base.copy(title = L.s(R.string.al_tablet_title), text = L.s(R.string.al_tablet_text), icon = R.drawable.ic_stat_pill, open = "metric:POLLEN")
            }
            else -> null
        }
    }

    /** Pollen bands in grains/m³: (moderate, high, very high) per category. */
    private val pollenBands = mapOf(
        "trees" to Triple(15.0, 90.0, 1500.0),
        "grass" to Triple(10.0, 50.0, 150.0),
        "weeds" to Triple(10.0, 50.0, 200.0),
    )

    private fun pollenNote(f: Forecast, cfg: AlertConfig, force: Boolean): Pair<String, String>? {
        val air = f.air ?: return null
        val now = f.current.time
        val hrs = air.hourly.filter { it.time >= now - 3600 && it.time < now + 16 * 3600 }
        if (hrs.isEmpty()) return null
        // pollen order: alder, birch, grass, mugwort, olive, ragweed
        fun peak(idx: List<Int>) = hrs.maxOf { h -> idx.maxOf { i -> h.pollen.getOrNull(i)?.takeIf { it.ok() } ?: 0.0 } }
        val levels = mapOf(
            "trees" to peak(listOf(0, 1, 4)),
            "grass" to peak(listOf(2)),
            "weeds" to peak(listOf(3, 5)),
        ).filterKeys { it in cfg.flags }
        // Sensitivity: high → alert from "moderate", moderate → from "high", low → only "very high".
        val hits = levels.mapNotNull { (cat, v) ->
            val (m, h, vh) = pollenBands.getValue(cat)
            val lvl = when { v >= vh -> 3; v >= h -> 2; v >= m -> 1; else -> 0 }
            val need = when (cfg.level) { 2 -> 1; 0 -> 3; else -> 2 }
            if (lvl >= need || (force && lvl > 0)) Triple(cat, lvl, v) else null
        }
        if (hits.isEmpty()) {
            val plural = mapOf("trees" to R.string.al_pollen_trees, "grass" to R.string.al_pollen_grasses, "weeds" to R.string.al_pollen_weeds)
            val list = cfg.flags.joinToString { k -> plural[k]?.let { L.s(it) } ?: k }
            return if (force) L.s(R.string.al_pollen_low_title) to L.s(R.string.al_pollen_low_text, list) else null
        }
        val top = hits.maxBy { it.second }
        val names = mapOf("trees" to R.string.al_pollen_tree, "grass" to R.string.al_pollen_grass, "weeds" to R.string.al_pollen_weed)
        fun name(cat: String) = L.s(names.getValue(cat))
        return if (hits.size == 1) {
            val n = name(top.first)
            val cap = n.replaceFirstChar { it.uppercase() }
            when (top.second) {
                3 -> L.s(R.string.al_pollen_one_very_high_title, n) to L.s(R.string.al_pollen_one_very_high_text, cap)
                2 -> L.s(R.string.al_pollen_one_high_title, n) to L.s(R.string.al_pollen_one_high_text, cap)
                else -> L.s(R.string.al_pollen_one_elevated_title, n) to L.s(R.string.al_pollen_one_elevated_text, cap)
            }
        } else {
            val title = when (top.second) {
                3 -> R.string.al_pollen_many_very_high_title
                2 -> R.string.al_pollen_many_high_title
                else -> R.string.al_pollen_many_elevated_title
            }
            L.s(title) to L.s(R.string.al_pollen_many_text, hits.joinToString { name(it.first) })
        }
    }

    /** "Send test notification": the real content now, with thresholds relaxed. */
    suspend fun test(context: Context, type: AlertType): String? {
        val settings = Prefs(context).settings.value
        val cfg = AlertPrefs.get(context).config(type)
        return when (type) {
            AlertType.WARNINGS, AlertType.FRIENDS -> {
                val (key, place) = targetPlace(context, cfg) ?: return L.s(R.string.al_test_choose_place)
                val list = WarningStore.refresh(context, place, 0)
                val w = list.firstOrNull() ?: Warning(
                    "test:${System.currentTimeMillis()}", L.s(R.string.al_test_warning_event), L.s(R.string.al_test_warning_headline),
                    L.s(R.string.al_test_warning_desc, place.name), "", Severity.MODERATE,
                    Hazard.WIND, 0, 0, "Expressive Weather", L.s(R.string.al_test_source), null,
                )
                Notifier.post(context, warningNote(w, key, place.name, AlertType.WARNINGS, settings).copy(log = false))
                null
            }
            AlertType.NOWCAST -> {
                val (key, place) = targetPlace(context, cfg) ?: return L.s(R.string.al_test_choose_place)
                val f = ForecastRepo.get(context, place, 5 * 60_000L) ?: return L.s(R.string.al_test_load_failed)
                if (!evaluateNowcast(context, f, key, place, cfg, force = true)) {
                    val now = System.currentTimeMillis()
                    Notifier.postNowcast(
                        context, L.s(R.string.al_nowcast_test_title), L.p(R.plurals.al_nowcast_starting_in, 20), L.s(R.string.al_nowcast_chip_minutes, 20), now + 20 * 60_000L,
                        listOf(0.0, 0.0, 0.3, 1.2, 2.5, 0.8, 0.1, 0.0).mapIndexed { i, mm -> Notifier.NowcastStep(i * 15, mm) },
                        false, key, place.name, silent = false,
                    )
                }
                null
            }
            AlertType.HEAT -> {
                val (key, place) = targetPlace(context, cfg) ?: return L.s(R.string.al_test_choose_place)
                val f = ForecastRepo.get(context, place, 5 * 60_000L) ?: return L.s(R.string.al_test_load_failed)
                heatNote(f, key, place, cfg, settings, force = true)?.let { Notifier.post(context, it.copy(log = false)) }
                    ?: return L.s(R.string.al_test_no_wet_bulb)
                null
            }
            AlertType.STORM, AlertType.PRESSURE -> {
                val (key, place) = targetPlace(context, cfg) ?: return L.s(R.string.al_test_choose_place)
                val f = ForecastRepo.get(context, place, 5 * 60_000L) ?: return L.s(R.string.al_test_load_failed)
                val note = if (type == AlertType.STORM) stormNote(f, key, place, cfg, settings) else pressureNote(f, key, place, cfg, settings, force = true)
                Notifier.post(
                    context,
                    (note ?: Note(type, "${type.name}:${place.key}", L.s(R.string.al_test_nothing_title), L.s(R.string.al_test_nothing_text), placeKey = key, placeName = place.name)).copy(log = false),
                )
                null
            }
            AlertType.ONGOING -> {
                val (key, place) = targetPlace(context, cfg) ?: return L.s(R.string.al_test_choose_place)
                val f = ForecastRepo.get(context, place) ?: return L.s(R.string.al_test_load_failed)
                showOngoing(context, f, key, place, settings)
                null
            }
            else -> when (runDaily(context, type, force = true)) {
                true -> null
                false -> L.s(R.string.al_test_nothing_now)
                null -> L.s(R.string.al_test_load_failed)
            }
        }
    }
}

/** "Tue 14:00" or "14:00" today. */
fun Fmt.dayTime(epoch: Long): String {
    val d = localDate(epoch)
    val today = LocalDate.now(zone)
    return if (d == today) time(epoch) else "${dayName(d, today)} ${time(epoch)}"
}
