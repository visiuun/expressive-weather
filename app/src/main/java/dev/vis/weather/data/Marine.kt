package dev.vis.weather.data

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

enum class SeaActivity(val label: String) { GENERAL("General"), SWIMMING("Swimming"), SURFING("Surfing"), SAILING("Sailing") }

/** Plain-language interpretation of sea conditions. */
object MarineInfo {
    data class SeaState(val label: String, val level: Int, val description: String)

    /** Douglas sea scale from significant wave height (m). */
    fun seaState(h: Double): SeaState = when {
        h.isNaN() -> SeaState("–", -1, "")
        h < 0.1 -> SeaState("Calm", 0, "Glassy, mirror-like water.")
        h < 0.5 -> SeaState("Rippled", 1, "Small wavelets with no breaking crests.")
        h < 1.25 -> SeaState("Slight", 2, "Small waves; a few white horses.")
        h < 2.5 -> SeaState("Moderate", 3, "Longer waves with many white horses.")
        h < 4.0 -> SeaState("Rough", 4, "Large waves, extensive foam and spray.")
        h < 6.0 -> SeaState("Very rough", 5, "Heaped-up sea with streaks of foam.")
        h < 9.0 -> SeaState("High", 6, "High waves with dense foam; dangerous.")
        h < 14.0 -> SeaState("Very high", 7, "Very high waves with long breaking crests.")
        else -> SeaState("Phenomenal", 8, "Exceptional, extremely dangerous seas.")
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
        period < 6 -> "Short, choppy wind swell"
        period < 9 -> "Mixed swell of average quality"
        period < 12 -> "Clean groundswell"
        else -> "Long-period groundswell with real power"
    }

    fun periodExplainer(period: Double): String = when {
        period.isNaN() -> ""
        period < 6 -> "Waves arrive every ${period.toInt()} s: short and bumpy, built by local wind."
        period < 9 -> "Waves arrive every ${period.toInt()} s: a mix of local chop and swell from further away."
        else -> "Waves arrive every ${period.toInt()} s: they travelled a long way and carry more energy than their height suggests."
    }

    fun wetsuit(sst: Double): String = when {
        sst.isNaN() -> ""
        sst >= 25 -> "No wetsuit needed."
        sst >= 22 -> "Rash vest or a thin 0.5–2 mm top."
        sst >= 19 -> "2 mm shorty or spring suit."
        sst >= 16 -> "3/2 mm full suit."
        sst >= 13 -> "4/3 mm full suit; booties optional."
        sst >= 10 -> "5/4 mm suit with booties and gloves."
        else -> "5/4–6/5 mm suit with hood, booties and gloves."
    }

    fun waterFeel(sst: Double): String = when {
        sst.isNaN() -> ""
        sst >= 26 -> "Bath-warm"
        sst >= 22 -> "Warm"
        sst >= 19 -> "Pleasant"
        sst >= 16 -> "Fresh"
        sst >= 12 -> "Cold"
        else -> "Very cold"
    }

    data class CurrentInfo(val label: String, val caution: Boolean, val text: String)

    /** [kmh] ocean current speed. An average swimmer manages roughly 2 km/h. */
    fun current(kmh: Double): CurrentInfo = when {
        kmh.isNaN() -> CurrentInfo("–", false, "")
        kmh < 0.5 -> CurrentInfo("Weak", false, "Barely noticeable drift.")
        kmh < 1.5 -> CurrentInfo("Moderate", false, "You'll drift slowly; easy to swim against.")
        kmh < 3.0 -> CurrentInfo("Strong", true, "Caution for swimmers: as fast as many people can swim. Stay close to shore.")
        else -> CurrentInfo("Very strong", true, "Dangerous for swimmers: faster than most people can swim.")
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
                wave.isNaN() -> "No wave data for this spot."
                state.level <= 1 -> "Calm water — a good time to be on or by the sea."
                state.level == 2 -> "Slight sea. Fine for most activities; small boats may feel some motion."
                state.level == 3 -> "Moderate sea. Expect spray on the beach and a bumpy ride in small boats."
                state.level == 4 -> "Rough sea. Stay well back from breakwaters and the waterline."
                else -> "Dangerous seas. Keep away from exposed coasts, piers and cliff edges."
            }
            SeaActivity.SWIMMING -> buildString {
                append(
                    when {
                        wave.isNaN() -> "No wave data."
                        wave < 0.5 -> "Gentle water for swimming."
                        wave < 1.0 -> "Some waves; stay within your depth."
                        wave < 1.5 -> "Choppy. Swim only at lifeguarded beaches."
                        else -> "Too rough for swimming — strong surf and rip currents likely."
                    }
                )
                if (cur.caution) append(" ${cur.text}")
                if (!h.seaTemp.isNaN() && h.seaTemp < 15) append(" Cold water: risk of cold shock, enter slowly and keep swims short.")
                else if (!h.seaTemp.isNaN()) append(" Water ${waterFeel(h.seaTemp).lowercase()}.")
            }
            SeaActivity.SURFING -> {
                val sw = if (h.swellHeight.isNaN()) wave else h.swellHeight
                val p = if (h.swellPeriod.isNaN()) h.wavePeriod else h.swellPeriod
                val choppy = !h.windWaveHeight.isNaN() && !sw.isNaN() && h.windWaveHeight > sw
                when {
                    sw.isNaN() -> "No swell data."
                    sw < 0.4 -> "Flat. Not much to ride."
                    sw < 1.0 && (p.isNaN() || p < 8) -> "Small and weak — longboards and beginners only."
                    sw < 1.0 -> "Small but clean. Good for longboards and learning."
                    sw < 2.5 && !p.isNaN() && p >= 9 -> "Good, powerful swell for intermediate surfers."
                    sw < 2.5 -> "Decent size, but short-period and less organised."
                    else -> "Big swell — experienced surfers only."
                } + if (choppy) " Wind chop is on top of the swell, so expect messy faces." else ""
            }
            SeaActivity.SAILING -> {
                val w = windKmh
                when {
                    w.isNaN() -> "No wind data."
                    w < 9 -> "Very light wind — slow going, maybe drifting."
                    w < 29 && state.level <= 3 -> "Good sailing breeze."
                    w < 39 -> "Fresh to strong breeze. Reef early; for experienced crews."
                    else -> "Strong winds — small craft should stay in harbour."
                } + if (!gustKmh.isNaN() && !w.isNaN() && gustKmh > w * 1.6 && gustKmh > 30) " Gusty: expect sudden shifts." else ""
            }
        }
    }

    const val DISCLAIMER =
        "Model guidance only, not for navigation or safety decisions. Conditions at your beach can differ — " +
            "always check local warnings, flags and lifeguards."
}
