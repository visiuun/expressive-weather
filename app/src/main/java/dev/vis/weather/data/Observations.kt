package dev.vis.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A live surface observation (METAR) from an airport weather station near the location.
 * [tempBias] / [dewBias] are what was added to the model at the observation time (°C), NaN when not applied.
 */
data class Observation(
    val stationId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Distance from the forecast location, km. */
    val distanceKm: Double,
    /** Station elevation, m (NaN if unknown). */
    val elevation: Double,
    /** Observation time, epoch seconds. */
    val time: Long,
    val temp: Double,
    val dewPoint: Double,
    /** km/h. */
    val windSpeed: Double,
    val windDir: Double,
    val raw: String,
    val tempBias: Double = Double.NaN,
    val dewBias: Double = Double.NaN,
) {
    /** True when the observation actually nudged the forecast. */
    val applied: Boolean get() = !tempBias.isNaN()
}

/**
 * Local correction: nudges the model toward the nearest METAR.
 *
 * Privacy: only a bounding box around the location (~±35 km, rounded to 0.1°) is sent to NOAA's
 * aviationweather.gov; the API returns every station in that box and the nearest is chosen on the device.
 */
object Observations {
    const val RADIUS_KM = 35.0
    /** Observations older than this (relative to the fetch) are ignored. */
    const val MAX_AGE_S = 90 * 60L
    /** The bias fades out linearly over this many seconds after the observation. */
    const val DECAY_S = 6 * 3600L
    /** Larger differences are more likely a bad report or a very different site than a model error. */
    private const val MAX_BIAS = 6.0

    private val USER_AGENT = "ExpressiveWeather/${dev.vis.weather.BuildConfig.VERSION_NAME} (+${Updater.REPO_URL})"

    private fun Double.c() = String.format(Locale.US, "%.1f", this)

    /** Raw JSON from aviationweather.gov for the area around [lat]/[lon], or null on any failure. Cache this string. */
    suspend fun fetchJson(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val dLat = RADIUS_KM / 111.0 + 0.05
            val dLon = dLat / cos(Math.toRadians(lat)).coerceAtLeast(0.15)
            val url = "https://aviationweather.gov/api/data/metar?bbox=" +
                "${(lat - dLat).c()},${(lon - dLon).c()},${(lat + dLat).c()},${(lon + dLon).c()}&format=json"
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 12_000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                if (code == 204) return@withContext "[]"
                if (code !in 200..299) throw IOException("HTTP $code")
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

    /** Fetches and picks the nearest usable station in one go. */
    suspend fun fetch(lat: Double, lon: Double): Observation? = nearest(fetchJson(lat, lon), lat, lon)

    /** Nearest station with a temperature within [RADIUS_KM] of the location, parsed from [fetchJson]'s output. */
    fun nearest(json: String?, lat: Double, lon: Double): Observation? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val sLat = o.optDouble("lat", Double.NaN)
                val sLon = o.optDouble("lon", Double.NaN)
                if (sLat.isNaN() || sLon.isNaN() || o.isNull("temp")) return@mapNotNull null
                val raw = o.optString("rawOb", "")
                // US stations append a precise "T01110083" group (tenths of °C); prefer it over the rounded value.
                val precise = Regex("""\bT([01])(\d{3})([01])(\d{3})\b""").find(raw)
                val temp = precise?.let { m -> (if (m.groupValues[1] == "1") -1 else 1) * m.groupValues[2].toInt() / 10.0 }
                    ?: o.optDouble("temp", Double.NaN)
                val dew = precise?.let { m -> (if (m.groupValues[3] == "1") -1 else 1) * m.groupValues[4].toInt() / 10.0 }
                    ?: if (o.isNull("dewp")) Double.NaN else o.optDouble("dewp", Double.NaN)
                if (temp.isNaN()) return@mapNotNull null
                val time = o.optLong("obsTime", 0L)
                if (time <= 0L) return@mapNotNull null
                val wdir = o.opt("wdir").let { (it as? Number)?.toDouble() ?: Double.NaN } // "VRB" → NaN
                Observation(
                    stationId = o.optString("icaoId", "").ifBlank { "?" },
                    name = prettyName(o.optString("name", "")),
                    lat = sLat,
                    lon = sLon,
                    distanceKm = distanceKm(lat, lon, sLat, sLon),
                    elevation = if (o.isNull("elev")) Double.NaN else o.optDouble("elev", Double.NaN),
                    time = time,
                    temp = temp,
                    dewPoint = dew,
                    windSpeed = if (o.isNull("wspd")) Double.NaN else o.optDouble("wspd", Double.NaN) * 1.852,
                    windDir = wdir,
                    raw = raw,
                )
            }.filter { it.distanceKm <= RADIUS_KM }.minByOrNull { it.distanceKm }
        } catch (_: Exception) {
            null
        }
    }

    /** "Amsterdam/Schiphol Arpt, NH, NL" → "Amsterdam/Schiphol Airport". */
    private fun prettyName(s: String): String =
        s.substringBefore(',').replace(" Arpt", " Airport").replace(" Intl", " International").replace(" Rgnl", " Regional")
            .replace(" Muni", " Municipal").trim()

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    // ---- correction

    /** Saturation vapour pressure, hPa (Magnus). */
    private fun es(t: Double) = 6.1094 * exp(17.625 * t / (t + 243.04))

    fun relativeHumidity(t: Double, td: Double): Double = (100 * es(td) / es(t)).coerceIn(1.0, 100.0)

    /** Australian apparent temperature (Steadman), the formula Open-Meteo uses. [windKmh] at 10 m. */
    fun apparent(t: Double, rh: Double, windKmh: Double): Double {
        val e = rh / 100 * es(t)
        val ws = (if (windKmh.isNaN()) 0.0 else windKmh) / 3.6
        return t + 0.33 * e - 0.70 * ws - 4.0
    }

    private fun weight(t: Long, obsTime: Long): Double = when {
        t < obsTime - 3600 -> 0.0
        t <= obsTime -> 1.0
        else -> (1.0 - (t - obsTime).toDouble() / DECAY_S).coerceAtLeast(0.0)
    }

    /**
     * Applies [obs] to [f] if it's recent enough. Returns [f] unchanged (observation = null) otherwise.
     * Temperatures are first lapse-rate adjusted from the station's height to the forecast grid height.
     */
    fun correct(f: Forecast, obs: Observation?): Forecast {
        if (obs == null) return f
        val age = f.fetchedAt / 1000 - obs.time
        if (age > MAX_AGE_S || age < -15 * 60) return f
        val hours = f.hourlyAll
        if (hours.isEmpty()) return f
        // Model value at the observation time (linear interpolation between hours).
        fun at(sel: (Hour) -> Double): Double {
            val i = hours.indexOfLast { it.time <= obs.time }
            if (i < 0) return Double.NaN
            val a = hours[i]
            val b = hours.getOrNull(i + 1) ?: return sel(a)
            val fr = (obs.time - a.time).toDouble() / (b.time - a.time)
            val va = sel(a); val vb = sel(b)
            return if (va.isNaN()) vb else if (vb.isNaN()) va else va + (vb - va) * fr
        }
        val lapse = if (!obs.elevation.isNaN() && !f.elevation.isNaN()) 0.0065 * (obs.elevation - f.elevation) else 0.0
        val tBias = (obs.temp + lapse - at { it.temp }).takeIf { !it.isNaN() } ?: return f
        if (abs(tBias) > MAX_BIAS) return f
        val dBias = (obs.dewPoint + lapse - at { it.dewPoint }).takeIf { !it.isNaN() && abs(it) <= MAX_BIAS } ?: 0.0
        // Dew point can't exceed temperature.
        fun fix(t: Double, td: Double, rh: Double, feels: Double, wind: Double, w: Double): Triple<Double, Double, Pair<Double, Double>> {
            if (w <= 0.0 || t.isNaN()) return Triple(t, td, rh to feels)
            val nt = t + tBias * w
            val ntd = if (td.isNaN()) td else minOf(td + dBias * w, nt)
            val nrh = if (td.isNaN()) rh else relativeHumidity(nt, ntd)
            val nfeels = if (feels.isNaN() || rh.isNaN() || nrh.isNaN()) feels + (nt - t)
            else feels + (apparent(nt, nrh, wind) - apparent(t, rh, wind))
            return Triple(nt, ntd, nrh to nfeels)
        }
        val newHours = hours.map { h ->
            val w = weight(h.time, obs.time)
            if (w <= 0.0) h else {
                val (t, td, rest) = fix(h.temp, h.dewPoint, h.humidity, h.feelsLike, h.windSpeed, w)
                val wb = if (h.wetBulb.isNaN()) h.wetBulb else h.wetBulb + (Indices.wetBulb(t, rest.first) - Indices.wetBulb(h.temp, h.humidity)).let { if (it.isNaN()) 0.0 else it }
                h.copy(temp = t, dewPoint = td, humidity = rest.first, feelsLike = rest.second, wetBulb = wb)
            }
        }
        val c = f.current
        val cw = weight(c.time, obs.time)
        val (ct, ctd, crest) = fix(c.temp, c.dewPoint, c.humidity, c.feelsLike, c.windSpeed, cw)
        val current = c.copy(temp = ct, dewPoint = ctd, humidity = crest.first, feelsLike = crest.second)
        // Keep today's high/low consistent with the corrected hours.
        val todayDate = f.daily.firstOrNull()?.date
        val daily = f.daily.mapIndexed { i, d ->
            if (i != 0 || todayDate == null) d else {
                val todays = newHours.filter { java.time.Instant.ofEpochSecond(it.time).atZone(f.zone).toLocalDate() == todayDate }
                    .map { it.temp }.filter { !it.isNaN() } + ct
                val feels = newHours.filter { java.time.Instant.ofEpochSecond(it.time).atZone(f.zone).toLocalDate() == todayDate }
                    .map { it.feelsLike }.filter { !it.isNaN() }
                if (todays.size < 12) d else d.copy(
                    max = todays.max(), min = todays.min(),
                    feelsMax = feels.maxOrNull() ?: d.feelsMax, feelsMin = feels.minOrNull() ?: d.feelsMin,
                )
            }
        }
        return f.copy(
            current = current,
            hourlyAll = newHours,
            daily = daily,
            observation = obs.copy(tempBias = tBias, dewBias = dBias),
        )
    }
}
