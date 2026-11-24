package dev.vis.weather.ui.map

import dev.vis.weather.L
import dev.vis.weather.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One radar image set from RainViewer. [time] is epoch seconds. */
data class RadarFrame(val time: Long, val source: TileSource, val forecast: Boolean)

data class RadarData(val frames: List<RadarFrame>, val fetchedAt: Long) {
    /** Index of the newest observed (non-forecast) frame. */
    val latestPast: Int get() = frames.indexOfLast { !it.forecast }.coerceAtLeast(0)
}

/** Points sampled over a Mercator rectangle (corners included). */
class GridSpec(
    val x0: Double,
    val y0: Double,
    val x1: Double,
    val y1: Double,
    val cols: Int,
    val rows: Int,
    val zoom: Double,
) {
    val size: Int get() = cols * rows
    fun px(i: Int): Double = x0 + (x1 - x0) * i / (cols - 1)
    fun py(j: Int): Double = y0 + (y1 - y0) * j / (rows - 1)

    /** True if this grid still covers the camera's viewport at a similar resolution. */
    fun covers(camera: MapCamera): Boolean {
        if (kotlin.math.abs(camera.zoom - zoom) > 1.1) return false
        val shift = Math.round(camera.x - (x0 + x1) / 2).toDouble()
        val l = camera.left - shift
        val r = camera.right - shift
        val tol = (x1 - x0) * 0.02
        return l >= x0 - tol && r <= x1 + tol &&
            camera.top >= y0 - tol && camera.bottom <= y1 + tol
    }

    companion object {
        private const val TARGET_POINTS = 84

        /** A grid over the viewport plus a 15 % margin, shaped to the viewport's aspect ratio. */
        fun around(camera: MapCamera): GridSpec {
            val w = camera.right - camera.left
            val h = camera.bottom - camera.top
            var gw = minOf(w * 1.3, 1.0)
            val cx = camera.x
            val y0 = (camera.top - h * 0.15).coerceAtLeast(0.0)
            val y1 = (camera.bottom + h * 0.15).coerceAtMost(1.0)
            if (gw <= 0) gw = 0.01
            val gh = (y1 - y0).coerceAtLeast(1e-6)
            val aspect = gw / gh
            val cols = sqrt(TARGET_POINTS * aspect).roundToInt().coerceIn(4, 14)
            val rows = (TARGET_POINTS.toDouble() / cols).roundToInt().coerceIn(4, 14)
            return GridSpec(cx - gw / 2, y0, cx + gw / 2, y1, cols, rows, camera.zoom)
        }
    }
}

/**
 * Hourly model values for every grid point: arrays are indexed [hour * size + row * cols + col].
 * Units as returned by Open-Meteo (°C, km/h, °, %, hPa, mm).
 */
class FieldGrid(
    val spec: GridSpec,
    val times: LongArray,
    val temp: FloatArray,
    val wind: FloatArray,
    val windDir: FloatArray,
    val cloud: FloatArray,
    val pressure: FloatArray,
    val precip: FloatArray,
    val fetchedAt: Long,
) {
    val hours: Int get() = times.size

    fun values(layer: MapLayer): FloatArray? = when (layer) {
        MapLayer.TEMPERATURE -> temp
        MapLayer.WIND -> wind
        MapLayer.CLOUDS -> cloud
        MapLayer.PRESSURE -> pressure
        MapLayer.PRECIPITATION -> precip
        else -> null
    }
}

/** An active tropical cyclone from the NHC. Intensity in knots, movement speed in mph (NHC units). */
data class Storm(
    val id: String,
    val name: String,
    val classification: String,
    val intensityKt: Double,
    val pressureMb: Double,
    val lat: Double,
    val lon: Double,
    val movementDir: Double,
    val movementMph: Double,
    val lastUpdate: String?,
    val advisoryUrl: String?,
) {
    val windKmh: Double get() = intensityKt * 1.852
    val movementKmh: Double get() = movementMph * 1.609344

    /** Saffir–Simpson category (1–5) for hurricanes/typhoons, 0 otherwise. */
    val category: Int
        get() = when {
            intensityKt.isNaN() || intensityKt < 64 -> 0
            intensityKt < 83 -> 1
            intensityKt < 96 -> 2
            intensityKt < 113 -> 3
            intensityKt < 137 -> 4
            else -> 5
        }

    val classLabel: String
        get() = when (classification.uppercase(Locale.US)) {
            "TD" -> L.s(R.string.map_storm_td)
            "STD" -> L.s(R.string.map_storm_std)
            "TS" -> L.s(R.string.map_storm_ts)
            "STS" -> L.s(R.string.map_storm_sts)
            "HU" -> if (category > 0) L.s(R.string.map_storm_hu_cat, category) else L.s(R.string.map_storm_hu)
            "TY" -> if (category > 0) L.s(R.string.map_storm_ty_cat, category) else L.s(R.string.map_storm_ty)
            "PTC" -> L.s(R.string.map_storm_ptc)
            "PC" -> L.s(R.string.map_storm_pc)
            else -> classification.ifBlank { L.s(R.string.map_storm_generic) }
        }
}

/**
 * Network access for the map's weather layers. Hosts: api.rainviewer.com, api.open-meteo.com,
 * www.nhc.noaa.gov (tiles: *.basemaps.cartocdn.com, tilecache.rainviewer.com via [TileLoader]).
 */
object MapData {
    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", MAP_USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Server returned HTTP $code ${err.take(160)}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun radar(): RadarData = withContext(Dispatchers.IO) {
        parseRadar(get("https://api.rainviewer.com/public/weather-maps.json"), System.currentTimeMillis())
    }

    fun parseRadar(json: String, now: Long): RadarData {
        val root = JSONObject(json)
        val host = root.optString("host", "https://tilecache.rainviewer.com").trimEnd('/')
        val radar = root.optJSONObject("radar") ?: return RadarData(emptyList(), now)
        val frames = ArrayList<RadarFrame>()
        fun add(arr: JSONArray?, forecast: Boolean) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val path = o.optString("path").takeIf { it.isNotBlank() } ?: continue
                frames += RadarFrame(o.optLong("time"), TileSource.radar(host, path), forecast)
            }
        }
        add(radar.optJSONArray("past"), false)
        add(radar.optJSONArray("nowcast"), true)
        frames.sortBy { it.time }
        return RadarData(frames, now)
    }

    private fun Double.c() = String.format(Locale.US, "%.3f", this)

    suspend fun grid(spec: GridSpec): FieldGrid = withContext(Dispatchers.IO) {
        val lats = StringBuilder()
        val lons = StringBuilder()
        for (j in 0 until spec.rows) for (i in 0 until spec.cols) {
            if (lats.isNotEmpty()) {
                lats.append(','); lons.append(',')
            }
            lats.append(Mercator.lat(spec.py(j)).coerceIn(-85.0, 85.0).c())
            lons.append(Mercator.lon(spec.px(i)).c())
        }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lons" +
            "&hourly=temperature_2m,wind_speed_10m,wind_direction_10m,cloud_cover,pressure_msl,precipitation" +
            "&forecast_hours=48&timezone=GMT&timeformat=unixtime"
        parseGrid(get(url), spec, System.currentTimeMillis())
    }

    fun parseGrid(json: String, spec: GridSpec, now: Long): FieldGrid {
        val trimmed = json.trimStart()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONArray().put(JSONObject(trimmed))
        val n = spec.size
        val first = arr.optJSONObject(0)?.optJSONObject("hourly")?.optJSONArray("time")
            ?: throw IOException("Unexpected forecast response")
        val hours = first.length()
        val times = LongArray(hours) { first.optLong(it) }
        fun blank() = FloatArray(hours * n) { Float.NaN }
        val temp = blank()
        val wind = blank()
        val dir = blank()
        val cloud = blank()
        val pres = blank()
        val prec = blank()
        for (k in 0 until minOf(arr.length(), n)) {
            val o = arr.optJSONObject(k) ?: continue
            val idx = if (o.has("location_id")) o.optInt("location_id", k) else k
            if (idx !in 0 until n) continue
            val h = o.optJSONObject("hourly") ?: continue
            fun fill(name: String, out: FloatArray) {
                val a = h.optJSONArray(name) ?: return
                for (t in 0 until minOf(hours, a.length())) {
                    if (!a.isNull(t)) out[t * n + idx] = a.optDouble(t, Double.NaN).toFloat()
                }
            }
            fill("temperature_2m", temp)
            fill("wind_speed_10m", wind)
            fill("wind_direction_10m", dir)
            fill("cloud_cover", cloud)
            fill("pressure_msl", pres)
            fill("precipitation", prec)
        }
        return FieldGrid(spec, times, temp, wind, dir, cloud, pres, prec, now)
    }

    suspend fun storms(): List<Storm> = withContext(Dispatchers.IO) {
        parseStorms(get("https://www.nhc.noaa.gov/CurrentStorms.json"))
    }

    fun parseStorms(json: String): List<Storm> {
        val arr = JSONObject(json).optJSONArray("activeStorms") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.numOrNull("latitudeNumeric") ?: o.numOrNull("latitude_numeric") ?: coord(o.optString("latitude"))
            val lon = o.numOrNull("longitudeNumeric") ?: o.numOrNull("longitude_numeric") ?: coord(o.optString("longitude"))
            if (lat == null || lon == null) return@mapNotNull null
            Storm(
                id = o.optString("id"),
                name = o.optString("name").ifBlank { L.s(R.string.map_storm_unnamed) },
                classification = o.optString("classification"),
                intensityKt = o.numOrNull("intensity") ?: Double.NaN,
                pressureMb = o.numOrNull("pressure") ?: Double.NaN,
                lat = lat,
                lon = lon,
                movementDir = o.numOrNull("movementDir") ?: Double.NaN,
                movementMph = o.numOrNull("movementSpeed") ?: Double.NaN,
                lastUpdate = o.optString("lastUpdate").takeIf { it.isNotBlank() && it != "null" },
                advisoryUrl = o.optJSONObject("publicAdvisory")?.optString("url")?.takeIf { it.startsWith("https://www.nhc.noaa.gov/") },
            )
        }
    }

    /** NHC encodes most numbers as strings ("105"); accept either. */
    private fun JSONObject.numOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return when (val v = opt(key)) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            null -> null
            else -> null
        }?.takeIf { !it.isNaN() }
    }

    /** "17.1N" / "58.4W" → signed degrees. */
    private fun coord(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        val t = s.trim().uppercase(Locale.US)
        val v = t.dropLast(1).toDoubleOrNull() ?: return t.toDoubleOrNull()
        return when (t.last()) {
            'S', 'W' -> -v
            else -> v
        }
    }
}
