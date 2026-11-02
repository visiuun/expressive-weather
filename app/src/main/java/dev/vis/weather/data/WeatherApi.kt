package dev.vis.weather.data

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Free & open data sources only:
 *  - Open-Meteo forecast / air quality / geocoding (open source, no API key, CC BY 4.0 data)
 *  - OpenStreetMap Nominatim for reverse geocoding (ODbL data)
 */
object WeatherApi {
    private val USER_AGENT = "ExpressiveWeather/${dev.vis.weather.BuildConfig.VERSION_NAME} (+${Updater.REPO_URL})"

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Server returned HTTP $code ${err.take(200)}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun Double.c() = String.format(Locale.US, "%.4f", this)

    private const val HOURLY_VARS =
        "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m," +
            "precipitation_probability,precipitation,weather_code,is_day,wind_speed_10m," +
            "wind_direction_10m,wind_gusts_10m,uv_index,pressure_msl,visibility,cloud_cover"

    private const val CURRENT_VARS =
        "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,precipitation," +
            "weather_code,is_day,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m," +
            "wind_gusts_10m,visibility,uv_index"

    private const val DAILY_VARS =
        "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max," +
            "apparent_temperature_min,sunrise,sunset,daylight_duration,sunshine_duration," +
            "uv_index_max,precipitation_sum,precipitation_hours,precipitation_probability_max," +
            "wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant"

    suspend fun forecastJson(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        get(
            "https://api.open-meteo.com/v1/forecast?latitude=${lat.c()}&longitude=${lon.c()}" +
                "&current=$CURRENT_VARS&hourly=$HOURLY_VARS&daily=$DAILY_VARS" +
                "&minutely_15=precipitation&forecast_minutely_15=12" +
                "&timezone=auto&timeformat=unixtime&forecast_days=16&past_days=1"
        )
    }

    private const val POLLEN = "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"

    suspend fun airJson(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            get(
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${lat.c()}&longitude=${lon.c()}" +
                    "&current=european_aqi,us_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,$POLLEN" +
                    "&hourly=us_aqi,european_aqi,pm2_5,pm10,ozone,$POLLEN&forecast_days=5" +
                    "&timezone=auto&timeformat=unixtime"
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val lang = Locale.getDefault().language.ifBlank { "en" }
        val json = JSONObject(
            get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=12&language=$lang&format=json")
        )
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val region = listOfNotNull(
                r.optStringOrNull("admin1"),
                r.optStringOrNull("country"),
            ).distinct().joinToString(", ")
            Place(r.getString("name"), region, r.getDouble("latitude"), r.getDouble("longitude"))
        }
    }

    /** Reverse geocode: OSM Nominatim first, platform Geocoder as fallback. */
    suspend fun reverse(context: Context, lat: Double, lon: Double): Place = withContext(Dispatchers.IO) {
        try {
            val lang = Locale.getDefault().toLanguageTag()
            val json = JSONObject(
                get(
                    "https://nominatim.openstreetmap.org/reverse?lat=${lat.c()}&lon=${lon.c()}" +
                        "&format=jsonv2&zoom=12&addressdetails=1&accept-language=$lang"
                )
            )
            val a = json.optJSONObject("address")
            if (a != null) {
                val name = listOf("city", "town", "village", "municipality", "suburb", "hamlet", "county", "state")
                    .firstNotNullOfOrNull { a.optStringOrNull(it) }
                if (name != null) {
                    val region = listOfNotNull(a.optStringOrNull("state"), a.optStringOrNull("country"))
                        .filter { it != name }.joinToString(", ")
                    return@withContext Place(name, region, lat, lon)
                }
            }
        } catch (_: Exception) {
        }
        try {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                val addr = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                if (addr != null) {
                    val name = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    if (name != null) {
                        return@withContext Place(name, listOfNotNull(addr.adminArea, addr.countryName).filter { it != name }.joinToString(", "), lat, lon)
                    }
                }
            }
        } catch (_: Exception) {
        }
        Place("Current location", String.format(Locale.US, "%.3f, %.3f", lat, lon), lat, lon)
    }

    // ---- parsing

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.num(key: String): Double =
        if (!has(key) || isNull(key)) Double.NaN else optDouble(key, Double.NaN)

    private fun JSONArray?.num(i: Int): Double =
        if (this == null || i >= length() || isNull(i)) Double.NaN else optDouble(i, Double.NaN)

    private fun JSONArray?.long(i: Int): Long =
        if (this == null || i >= length() || isNull(i)) 0L else optLong(i)

    fun parse(forecastJson: String, airJson: String?, fetchedAt: Long): Forecast {
        val root = JSONObject(forecastJson)
        val zone = try {
            ZoneId.of(root.optString("timezone", "UTC"))
        } catch (_: Exception) {
            ZoneId.of("UTC")
        }

        val c = root.getJSONObject("current")
        val current = Current(
            time = c.optLong("time"),
            temp = c.num("temperature_2m"),
            feelsLike = c.num("apparent_temperature"),
            humidity = c.num("relative_humidity_2m"),
            dewPoint = c.num("dew_point_2m"),
            precipitation = c.num("precipitation"),
            code = c.optInt("weather_code", 0),
            isDay = c.optInt("is_day", 1) == 1,
            cloudCover = c.num("cloud_cover"),
            pressure = c.num("pressure_msl"),
            windSpeed = c.num("wind_speed_10m"),
            windDir = c.num("wind_direction_10m"),
            windGust = c.num("wind_gusts_10m"),
            visibility = c.num("visibility"),
            uv = c.num("uv_index"),
        )

        val h = root.getJSONObject("hourly")
        val ht = h.getJSONArray("time")
        val hourly = (0 until ht.length()).map { i ->
            Hour(
                time = ht.long(i),
                temp = h.optJSONArray("temperature_2m").num(i),
                feelsLike = h.optJSONArray("apparent_temperature").num(i),
                humidity = h.optJSONArray("relative_humidity_2m").num(i),
                dewPoint = h.optJSONArray("dew_point_2m").num(i),
                precipProb = h.optJSONArray("precipitation_probability").num(i),
                precip = h.optJSONArray("precipitation").num(i),
                code = h.optJSONArray("weather_code").num(i).let { if (it.isNaN()) 0 else it.toInt() },
                isDay = h.optJSONArray("is_day").num(i) != 0.0,
                windSpeed = h.optJSONArray("wind_speed_10m").num(i),
                windDir = h.optJSONArray("wind_direction_10m").num(i),
                windGust = h.optJSONArray("wind_gusts_10m").num(i),
                uv = h.optJSONArray("uv_index").num(i),
                pressure = h.optJSONArray("pressure_msl").num(i),
                visibility = h.optJSONArray("visibility").num(i),
                cloudCover = h.optJSONArray("cloud_cover").num(i),
            )
        }

        val d = root.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val todayDate = java.time.Instant.ofEpochSecond(current.time).atZone(zone).toLocalDate()
        val daily = (0 until dt.length()).map { i ->
            Day(
                date = java.time.Instant.ofEpochSecond(dt.long(i)).atZone(zone).toLocalDate(),
                code = d.optJSONArray("weather_code").num(i).let { if (it.isNaN()) 0 else it.toInt() },
                max = d.optJSONArray("temperature_2m_max").num(i),
                min = d.optJSONArray("temperature_2m_min").num(i),
                feelsMax = d.optJSONArray("apparent_temperature_max").num(i),
                feelsMin = d.optJSONArray("apparent_temperature_min").num(i),
                sunrise = d.optJSONArray("sunrise").long(i),
                sunset = d.optJSONArray("sunset").long(i),
                daylight = d.optJSONArray("daylight_duration").num(i),
                sunshine = d.optJSONArray("sunshine_duration").num(i),
                uvMax = d.optJSONArray("uv_index_max").num(i),
                precipSum = d.optJSONArray("precipitation_sum").num(i),
                precipHours = d.optJSONArray("precipitation_hours").num(i),
                precipProbMax = d.optJSONArray("precipitation_probability_max").num(i),
                windMax = d.optJSONArray("wind_speed_10m_max").num(i),
                gustMax = d.optJSONArray("wind_gusts_10m_max").num(i),
                windDir = d.optJSONArray("wind_direction_10m_dominant").num(i),
            )
        }.filter { !it.date.isBefore(todayDate) }

        val minutely = root.optJSONObject("minutely_15")?.let { m ->
            val t = m.getJSONArray("time")
            val p = m.optJSONArray("precipitation")
            (0 until t.length()).map { Minute(t.long(it), p.num(it)) }
        }.orEmpty()

        val air = airJson?.let { parseAir(it) }

        return Forecast(
            zone = zone,
            latitude = root.num("latitude"),
            longitude = root.num("longitude"),
            elevation = root.num("elevation"),
            current = current,
            hourlyAll = hourly,
            daily = daily,
            minutely = minutely,
            air = air,
            fetchedAt = fetchedAt,
        )
    }

    private fun parseAir(json: String): AirQuality? = try {
        val c = JSONObject(json).getJSONObject("current")
        val pollen = listOf(
            "Alder" to "alder_pollen",
            "Birch" to "birch_pollen",
            "Grass" to "grass_pollen",
            "Mugwort" to "mugwort_pollen",
            "Olive" to "olive_pollen",
            "Ragweed" to "ragweed_pollen",
        ).map { (label, key) -> label to c.num(key) }.filter { !it.second.isNaN() }
        val hourly = JSONObject(json).optJSONObject("hourly")?.let { h ->
            val t = h.getJSONArray("time")
            val keys = listOf("alder_pollen", "birch_pollen", "grass_pollen", "mugwort_pollen", "olive_pollen", "ragweed_pollen")
            (0 until t.length()).map { i ->
                AirHour(
                    time = t.long(i),
                    usAqi = h.optJSONArray("us_aqi").num(i),
                    euAqi = h.optJSONArray("european_aqi").num(i),
                    pm25 = h.optJSONArray("pm2_5").num(i),
                    pm10 = h.optJSONArray("pm10").num(i),
                    o3 = h.optJSONArray("ozone").num(i),
                    pollen = keys.map { k -> h.optJSONArray(k).num(i) },
                )
            }
        }.orEmpty()
        AirQuality(
            hourly = hourly,
            euAqi = c.num("european_aqi"),
            usAqi = c.num("us_aqi"),
            pm25 = c.num("pm2_5"),
            pm10 = c.num("pm10"),
            co = c.num("carbon_monoxide"),
            no2 = c.num("nitrogen_dioxide"),
            so2 = c.num("sulphur_dioxide"),
            o3 = c.num("ozone"),
            pollen = pollen,
        )
    } catch (_: Exception) {
        null
    }
}
