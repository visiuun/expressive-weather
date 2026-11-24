package dev.vis.weather.data

import androidx.annotation.StringRes
import dev.vis.weather.L
import dev.vis.weather.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** One hour of sea conditions from the Open-Meteo Marine API. Heights m, periods s, speeds km/h, directions ° (from). */
data class MarineHour(
    val time: Long,
    val waveHeight: Double,
    val waveDir: Double,
    val wavePeriod: Double,
    val windWaveHeight: Double,
    val windWavePeriod: Double,
    val swellHeight: Double,
    val swellPeriod: Double,
    val swellDir: Double,
    val seaTemp: Double,
    val currentSpeed: Double,
    val currentDir: Double,
    /** Sea level incl. tides and surge, m relative to mean sea level. */
    val seaLevel: Double,
)

data class MarineDay(val date: LocalDate, val waveMax: Double, val swellMax: Double, val seaTempAvg: Double)

/** A tide turning point found in the hourly sea level. */
data class TideExtreme(val time: Long, val height: Double, val high: Boolean)

data class Marine(val hourly: List<MarineHour>, val daily: List<MarineDay>) {
    /** Hour closest to [epoch]. */
    fun at(epoch: Long): MarineHour? = hourly.minByOrNull { kotlin.math.abs(it.time - epoch) }?.takeIf { kotlin.math.abs(it.time - epoch) <= 2 * 3600 }

    val hasWaves: Boolean get() = hourly.any { !it.waveHeight.isNaN() }
    val hasTides: Boolean get() = hourly.count { !it.seaLevel.isNaN() } >= 24 && MarineInfo.tideRange(hourly) >= 0.15

    companion object {
        private const val VARS =
            "wave_height,wave_direction,wave_period,wind_wave_height,wind_wave_period,swell_wave_height," +
                "swell_wave_period,swell_wave_direction,sea_surface_temperature,ocean_current_velocity," +
                "ocean_current_direction,sea_level_height_msl"

        private val USER_AGENT = "ExpressiveWeather/${dev.vis.weather.BuildConfig.VERSION_NAME} (+${Updater.REPO_URL})"

        /** Raw Marine API JSON (cache it next to the forecast), or null on failure. Inland points return all-null data. */
        suspend fun fetchJson(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
            try {
                val url = "https://marine-api.open-meteo.com/v1/marine?latitude=${String.format(Locale.US, "%.4f", lat)}" +
                    "&longitude=${String.format(Locale.US, "%.4f", lon)}&hourly=$VARS&daily=wave_height_max" +
                    "&timezone=auto&timeformat=unixtime&forecast_days=7"
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 12_000
                    conn.readTimeout = 15_000
                    conn.setRequestProperty("User-Agent", USER_AGENT)
                    if (conn.responseCode !in 200..299) return@withContext null
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

        private fun JSONArray?.num(i: Int): Double =
            if (this == null || i >= length() || isNull(i)) Double.NaN else optDouble(i, Double.NaN)

        /** Parses [fetchJson] output; null when the point is inland (no waves and no sea temperature). */
        fun parse(json: String?, zone: ZoneId): Marine? {
            if (json.isNullOrBlank()) return null
            return try {
                val h = JSONObject(json).optJSONObject("hourly") ?: return null
                val t = h.getJSONArray("time")
                fun a(k: String) = h.optJSONArray(k)
                val wh = a("wave_height"); val wd = a("wave_direction"); val wp = a("wave_period")
                val wwh = a("wind_wave_height"); val wwp = a("wind_wave_period")
                val sh = a("swell_wave_height"); val sp = a("swell_wave_period"); val sd = a("swell_wave_direction")
                val sst = a("sea_surface_temperature"); val cv = a("ocean_current_velocity"); val cd = a("ocean_current_direction")
                val sl = a("sea_level_height_msl")
                val hours = (0 until t.length()).map { i ->
                    MarineHour(
                        time = t.optLong(i),
                        waveHeight = wh.num(i), waveDir = wd.num(i), wavePeriod = wp.num(i),
                        windWaveHeight = wwh.num(i), windWavePeriod = wwp.num(i),
                        swellHeight = sh.num(i), swellPeriod = sp.num(i), swellDir = sd.num(i),
                        seaTemp = sst.num(i), currentSpeed = cv.num(i), currentDir = cd.num(i), seaLevel = sl.num(i),
                    )
                }
                if (hours.none { !it.waveHeight.isNaN() } && hours.none { !it.seaTemp.isNaN() }) return null
                val days = hours.groupBy { Instant.ofEpochSecond(it.time).atZone(zone).toLocalDate() }.map { (date, hs) ->
                    MarineDay(
                        date,
                        hs.map { it.waveHeight }.filter { !it.isNaN() }.maxOrNull() ?: Double.NaN,
                        hs.map { it.swellHeight }.filter { !it.isNaN() }.maxOrNull() ?: Double.NaN,
                        hs.map { it.seaTemp }.filter { !it.isNaN() }.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN,
                    )
                }.sortedBy { it.date }
                Marine(hours, days)
            } catch (_: Exception) {
                null
            }
        }
    }
}

enum class SeaActivity(@StringRes private val labelRes: Int) {
    GENERAL(R.string.d_sea_act_general), SWIMMING(R.string.d_sea_act_swimming), SURFING(R.string.d_sea_act_surfing), SAILING(R.string.d_sea_act_sailing);

    val label: String get() = L.s(labelRes)
}

/** Plain-language interpretation of sea conditions. */
object MarineInfo {
    data class SeaState(val label: String, val level: Int, val description: String)

    /** Douglas sea scale from significant wave height (m). */
    fun seaState(h: Double): SeaState = when {
        h.isNaN() -> SeaState("–", -1, "")
        h < 0.1 -> SeaState(L.s(R.string.d_sea_state_calm), 0, L.s(R.string.d_sea_state_calm_text))
        h < 0.5 -> SeaState(L.s(R.string.d_sea_state_rippled), 1, L.s(R.string.d_sea_state_rippled_text))
        h < 1.25 -> SeaState(L.s(R.string.d_sea_state_slight), 2, L.s(R.string.d_sea_state_slight_text))
        h < 2.5 -> SeaState(L.s(R.string.d_sea_state_moderate), 3, L.s(R.string.d_sea_state_moderate_text))
        h < 4.0 -> SeaState(L.s(R.string.d_sea_state_rough), 4, L.s(R.string.d_sea_state_rough_text))
        h < 6.0 -> SeaState(L.s(R.string.d_sea_state_very_rough), 5, L.s(R.string.d_sea_state_very_rough_text))
        h < 9.0 -> SeaState(L.s(R.string.d_sea_state_high), 6, L.s(R.string.d_sea_state_high_text))
        h < 14.0 -> SeaState(L.s(R.string.d_sea_state_very_high), 7, L.s(R.string.d_sea_state_very_high_text))
        else -> SeaState(L.s(R.string.d_sea_state_phenomenal), 8, L.s(R.string.d_sea_state_phenomenal_text))
    }

    /** Colour per sea-state level, calm green → high red/purple. */
    fun seaColor(level: Int): Long = when (level) {
        -1, 0, 1 -> 0xFF35C7B0
        2 -> 0xFF4CAF50
        3 -> 0xFFFFC107
        4 -> 0xFFFF9800
        5 -> 0xFFF44336
        else -> 0xFF7E0023
    }

    fun swellQuality(period: Double): String = when {
        period.isNaN() -> ""
        period < 6 -> L.s(R.string.d_swell_short)
        period < 9 -> L.s(R.string.d_swell_mixed)
        period < 12 -> L.s(R.string.d_swell_clean)
        else -> L.s(R.string.d_swell_long)
    }

    fun periodExplainer(period: Double): String = when {
        period.isNaN() -> ""
        period < 6 -> L.s(R.string.d_period_short, period.toInt())
        period < 9 -> L.s(R.string.d_period_mixed, period.toInt())
        else -> L.s(R.string.d_period_long, period.toInt())
    }

    fun wetsuit(sst: Double): String = when {
        sst.isNaN() -> ""
        sst >= 25 -> L.s(R.string.d_wetsuit_none)
        sst >= 22 -> L.s(R.string.d_wetsuit_rash)
        sst >= 19 -> L.s(R.string.d_wetsuit_shorty)
        sst >= 16 -> L.s(R.string.d_wetsuit_32)
        sst >= 13 -> L.s(R.string.d_wetsuit_43)
        sst >= 10 -> L.s(R.string.d_wetsuit_54)
        else -> L.s(R.string.d_wetsuit_65)
    }

    fun waterFeel(sst: Double): String = when {
        sst.isNaN() -> ""
        sst >= 26 -> L.s(R.string.d_water_bath)
        sst >= 22 -> L.s(R.string.d_water_warm)
        sst >= 19 -> L.s(R.string.d_water_pleasant)
        sst >= 16 -> L.s(R.string.d_water_fresh)
        sst >= 12 -> L.s(R.string.d_water_cold)
        else -> L.s(R.string.d_water_very_cold)
    }

    private fun waterSentence(sst: Double): Int = when {
        sst >= 26 -> R.string.d_seag_water_bath
        sst >= 22 -> R.string.d_seag_water_warm
        sst >= 19 -> R.string.d_seag_water_pleasant
        sst >= 16 -> R.string.d_seag_water_fresh
        sst >= 12 -> R.string.d_seag_water_cold
        else -> R.string.d_seag_water_very_cold
    }

    data class CurrentInfo(val label: String, val caution: Boolean, val text: String)

    /** [kmh] ocean current speed. An average swimmer manages roughly 2 km/h. */
    fun current(kmh: Double): CurrentInfo = when {
        kmh.isNaN() -> CurrentInfo("–", false, "")
        kmh < 0.5 -> CurrentInfo(L.s(R.string.d_current_weak), false, L.s(R.string.d_current_weak_text))
        kmh < 1.5 -> CurrentInfo(L.s(R.string.d_current_moderate), false, L.s(R.string.d_current_moderate_text))
        kmh < 3.0 -> CurrentInfo(L.s(R.string.d_current_strong), true, L.s(R.string.d_current_strong_text))
        else -> CurrentInfo(L.s(R.string.d_current_very_strong), true, L.s(R.string.d_current_very_strong_text))
    }

    fun tideRange(hours: List<MarineHour>): Double {
        val v = hours.map { it.seaLevel }.filter { !it.isNaN() }
        return if (v.isEmpty()) 0.0 else v.max() - v.min()
    }

    /** High and low tides from the hourly sea level (parabolic interpolation between hours). */
    fun tides(hours: List<MarineHour>): List<TideExtreme> {
        val pts = hours.filter { !it.seaLevel.isNaN() }
        if (pts.size < 5) return emptyList()
        val out = mutableListOf<TideExtreme>()
        for (i in 1 until pts.lastIndex) {
            val a = pts[i - 1].seaLevel; val b = pts[i].seaLevel; val c = pts[i + 1].seaLevel
            val isMax = b >= a && b > c
            val isMin = b <= a && b < c
            if (!isMax && !isMin) continue
            val denom = a - 2 * b + c
            val off = if (denom != 0.0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
            val height = b - 0.25 * (a - c) * off
            val step = pts[i + 1].time - pts[i].time
            out += TideExtreme(pts[i].time + (off * step).toLong(), height, isMax)
        }
        // Drop wiggles: successive extremes must differ by a meaningful amount.
        val clean = mutableListOf<TideExtreme>()
        for (e in out) {
            val last = clean.lastOrNull()
            when {
                last == null -> clean += e
                last.high == e.high -> if ((e.high && e.height > last.height) || (!e.high && e.height < last.height)) clean[clean.lastIndex] = e
                kotlin.math.abs(last.height - e.height) < 0.08 -> {}
                else -> clean += e
            }
        }
        return clean
    }

    /** Guidance for [activity] given the conditions now and the wind (km/h) from the weather forecast. */
    fun guidance(activity: SeaActivity, h: MarineHour, windKmh: Double, gustKmh: Double): String {
        val wave = h.waveHeight
        val state = seaState(wave)
        val cur = current(h.currentSpeed)
        return when (activity) {
            SeaActivity.GENERAL -> when {
                wave.isNaN() -> L.s(R.string.d_seag_no_data)
                state.level <= 1 -> L.s(R.string.d_seag_calm)
                state.level == 2 -> L.s(R.string.d_seag_slight)
                state.level == 3 -> L.s(R.string.d_seag_moderate)
                state.level == 4 -> L.s(R.string.d_seag_rough)
                else -> L.s(R.string.d_seag_dangerous)
            }
            SeaActivity.SWIMMING -> buildString {
                append(
                    when {
                        wave.isNaN() -> L.s(R.string.d_seag_swim_no_data)
                        wave < 0.5 -> L.s(R.string.d_seag_swim_gentle)
                        wave < 1.0 -> L.s(R.string.d_seag_swim_some)
                        wave < 1.5 -> L.s(R.string.d_seag_swim_choppy)
                        else -> L.s(R.string.d_seag_swim_rough)
                    }
                )
                if (cur.caution) append(" ").append(cur.text)
                if (!h.seaTemp.isNaN() && h.seaTemp < 15) append(" ").append(L.s(R.string.d_seag_swim_cold))
                else if (!h.seaTemp.isNaN()) append(" ").append(L.s(waterSentence(h.seaTemp)))
            }
            SeaActivity.SURFING -> {
                val sw = if (h.swellHeight.isNaN()) wave else h.swellHeight
                val p = if (h.swellPeriod.isNaN()) h.wavePeriod else h.swellPeriod
                val choppy = !h.windWaveHeight.isNaN() && !sw.isNaN() && h.windWaveHeight > sw
                when {
                    sw.isNaN() -> L.s(R.string.d_seag_surf_no_data)
                    sw < 0.4 -> L.s(R.string.d_seag_surf_flat)
                    sw < 1.0 && (p.isNaN() || p < 8) -> L.s(R.string.d_seag_surf_small_weak)
                    sw < 1.0 -> L.s(R.string.d_seag_surf_small_clean)
                    sw < 2.5 && !p.isNaN() && p >= 9 -> L.s(R.string.d_seag_surf_good)
                    sw < 2.5 -> L.s(R.string.d_seag_surf_decent)
                    else -> L.s(R.string.d_seag_surf_big)
                }.let { if (choppy) it + " " + L.s(R.string.d_seag_surf_chop) else it }
            }
            SeaActivity.SAILING -> {
                val w = windKmh
                when {
                    w.isNaN() -> L.s(R.string.d_seag_sail_no_data)
                    w < 9 -> L.s(R.string.d_seag_sail_light)
                    w < 29 && state.level <= 3 -> L.s(R.string.d_seag_sail_good)
                    w < 39 -> L.s(R.string.d_seag_sail_fresh)
                    else -> L.s(R.string.d_seag_sail_strong)
                }.let { if (!gustKmh.isNaN() && !w.isNaN() && gustKmh > w * 1.6 && gustKmh > 30) it + " " + L.s(R.string.d_seag_sail_gusty) else it }
            }
        }
    }

    val DISCLAIMER: String get() = L.s(R.string.d_sea_disclaimer)
}
