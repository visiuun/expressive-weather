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
 *  - Open-Meteo forecast / air quality / marine / geocoding (open source, no API key, CC BY 4.0 data)
 *  - NOAA aviationweather.gov METAR for local correction (see [Observations])
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
            "wind_direction_10m,wind_gusts_10m,uv_index,pressure_msl,visibility,cloud_cover," +
            "rain,showers,snowfall,snow_depth,wet_bulb_temperature_2m,freezing_level_height,shortwave_radiation"

    private const val CURRENT_VARS =
        "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,precipitation," +
            "weather_code,is_day,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m," +
            "wind_gusts_10m,visibility,uv_index,rain,showers,snowfall"

    private const val DAILY_VARS =
        "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max," +
            "apparent_temperature_min,sunrise,sunset,daylight_duration,sunshine_duration," +
            "uv_index_max,precipitation_sum,precipitation_hours,precipitation_probability_max," +
            "wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,rain_sum,showers_sum,snowfall_sum"

    /**
     * Forecast JSON. With a specific [model] the request also asks for "best_match" so that variables or days the
     * model doesn't provide (many national models lack UV, visibility or rain chance, and end after 2–7 days)
     * can be filled in by [parse]. Unknown/legacy ids are normalised via [ForecastModels.resolve].
     */
    suspend fun forecastJson(lat: Double, lon: Double, model: String = ForecastModels.AUTO): String = withContext(Dispatchers.IO) {
        val m = ForecastModels.resolve(model)
        val models = if (m == ForecastModels.AUTO) "" else "&models=$m,${ForecastModels.AUTO}"
        get(
            "https://api.open-meteo.com/v1/forecast?latitude=${lat.c()}&longitude=${lon.c()}" +
                "&current=$CURRENT_VARS&hourly=$HOURLY_VARS&daily=$DAILY_VARS" +
                "&minutely_15=precipitation&forecast_minutely_15=12" +
                "&timezone=auto&timeformat=unixtime&forecast_days=16&past_days=1$models"
        )
    }

    /** Sea conditions (Open-Meteo Marine). Null on failure; inland points parse to `Forecast.marine == null`. */
    suspend fun marineJson(lat: Double, lon: Double): String? = Marine.fetchJson(lat, lon)

    /** Raw METAR JSON for the area (see [Observations]); pass `Observations.nearest(json, lat, lon)` to [parse]. */
    suspend fun metarJson(lat: Double, lon: Double): String? = Observations.fetchJson(lat, lon)

    private const val POLLEN = "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"

    suspend fun airJson(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            get(
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${lat.c()}&longitude=${lon.c()}" +
                    "&current=european_aqi,us_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,$POLLEN" +
                    "&hourly=us_aqi,european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide,$POLLEN&forecast_days=5" +
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

    /**
     * One variable, possibly from two models: the chosen model first, best_match where it has gaps.
     * With a single-model response, [primary] is the plain array and [fallback] is null.
     */
    private class Col(val primary: JSONArray?, val fallback: JSONArray?) {
        var filled = false
            private set

        fun num(i: Int): Double {
            val p = primary.n(i)
            if (!p.isNaN() || fallback == null) return p
            val f = fallback.n(i)
            if (!f.isNaN()) filled = true
            return f
        }

        fun long(i: Int): Long {
            val p = primary.l(i)
            if (p != 0L || fallback == null) return p
            val f = fallback.l(i)
            if (f != 0L) filled = true
            return f
        }

        private fun JSONArray?.n(i: Int): Double =
            if (this == null || i >= length() || isNull(i)) Double.NaN else optDouble(i, Double.NaN)

        private fun JSONArray?.l(i: Int): Long =
            if (this == null || i >= length() || isNull(i)) 0L else optLong(i)
    }

    private class Block(val obj: JSONObject?, val model: String?) {
        private val cols = mutableListOf<Col>()
        operator fun get(key: String): Col {
            val c = when {
                obj == null -> Col(null, null)
                model == null -> Col(obj.optJSONArray(key), null)
                else -> Col(obj.optJSONArray("${key}_$model"), obj.optJSONArray("${key}_${ForecastModels.AUTO}"))
            }
            cols += c
            return c
        }
        val filled get() = cols.any { it.filled }
    }

    /** The explicitly chosen model in a two-model response, or null for a plain best_match response. */
    private fun detectModel(hourly: JSONObject): String? {
        val suffix = "_${ForecastModels.AUTO}"
        val keys = hourly.keys().asSequence().toList()
        if (keys.none { it.endsWith(suffix) }) return null
        return keys.firstOrNull { it.startsWith("temperature_2m_") && !it.endsWith(suffix) }?.removePrefix("temperature_2m_")
            ?: ForecastModels.AUTO
    }

    fun parse(forecastJson: String, airJson: String?, fetchedAt: Long): Forecast = parse(forecastJson, airJson, fetchedAt, null, null)

    /**
     * Parses a forecast. [marineJson] is the raw Marine API response (null/inland → no sea data).
     * [obs] is the nearest station observation; it's applied as a local correction when recent enough.
     */
    fun parse(forecastJson: String, airJson: String?, fetchedAt: Long, marineJson: String?, obs: Observation? = null): Forecast {
        val root = JSONObject(forecastJson)
        val zone = try {
            ZoneId.of(root.optString("timezone", "UTC"))
        } catch (_: Exception) {
            ZoneId.of("UTC")
        }

        val hObj = root.getJSONObject("hourly")
        val model = detectModel(hObj)
        val h = Block(hObj, model)
        val ht = hObj.getJSONArray("time")
        val hTemp = h["temperature_2m"]; val hFeels = h["apparent_temperature"]; val hRh = h["relative_humidity_2m"]
        val hDew = h["dew_point_2m"]; val hProb = h["precipitation_probability"]; val hPrecip = h["precipitation"]
        val hCode = h["weather_code"]; val hDay = h["is_day"]; val hWind = h["wind_speed_10m"]; val hDir = h["wind_direction_10m"]
        val hGust = h["wind_gusts_10m"]; val hUv = h["uv_index"]; val hP = h["pressure_msl"]; val hVis = h["visibility"]
        val hCloud = h["cloud_cover"]; val hRain = h["rain"]; val hShowers = h["showers"]; val hSnow = h["snowfall"]
        val hDepth = h["snow_depth"]; val hWb = h["wet_bulb_temperature_2m"]; val hFrz = h["freezing_level_height"]
        val hRad = h["shortwave_radiation"]
        val hourly = (0 until ht.length()).map { i ->
            val temp = hTemp.num(i)
            val rh = hRh.num(i)
            Hour(
                time = ht.long(i),
                temp = temp,
                feelsLike = hFeels.num(i),
                humidity = rh,
                dewPoint = hDew.num(i),
                precipProb = hProb.num(i),
                precip = hPrecip.num(i),
                code = hCode.num(i).let { if (it.isNaN()) 0 else it.toInt() },
                isDay = hDay.num(i) != 0.0,
                windSpeed = hWind.num(i),
                windDir = hDir.num(i),
                windGust = hGust.num(i),
                uv = hUv.num(i),
                pressure = hP.num(i),
                visibility = hVis.num(i),
                cloudCover = hCloud.num(i),
                rain = hRain.num(i),
                showers = hShowers.num(i),
                snowfall = hSnow.num(i),
                snowDepth = hDepth.num(i),
                wetBulb = hWb.num(i).let { if (it.isNaN()) Indices.wetBulb(temp, rh) else it },
                freezingLevel = hFrz.num(i),
                radiation = hRad.num(i),
            )
        }

        // "current" is unsuffixed and comes from the first (chosen) model; fill its gaps from the hour we're in.
        val c = root.getJSONObject("current")
        val cTime = c.optLong("time")
        val hNow = hourly.lastOrNull { it.time <= cTime } ?: hourly.firstOrNull()
        fun cur(key: String, fromHour: (Hour) -> Double): Double =
            c.num(key).let { v -> if (v.isNaN() && hNow != null) fromHour(hNow) else v }
        val current = Current(
            time = cTime,
            temp = cur("temperature_2m") { it.temp },
            feelsLike = cur("apparent_temperature") { it.feelsLike },
            humidity = cur("relative_humidity_2m") { it.humidity },
            dewPoint = cur("dew_point_2m") { it.dewPoint },
            precipitation = cur("precipitation") { it.precip },
            code = if (c.has("weather_code") && !c.isNull("weather_code")) c.optInt("weather_code", 0) else hNow?.code ?: 0,
            isDay = if (c.has("is_day") && !c.isNull("is_day")) c.optInt("is_day", 1) == 1 else hNow?.isDay ?: true,
            cloudCover = cur("cloud_cover") { it.cloudCover },
            pressure = cur("pressure_msl") { it.pressure },
            windSpeed = cur("wind_speed_10m") { it.windSpeed },
            windDir = cur("wind_direction_10m") { it.windDir },
            windGust = cur("wind_gusts_10m") { it.windGust },
            visibility = cur("visibility") { it.visibility },
            uv = cur("uv_index") { it.uv },
            rain = cur("rain") { it.rain },
            showers = cur("showers") { it.showers },
            snowfall = cur("snowfall") { it.snowfall },
        )

        val dObj = root.getJSONObject("daily")
        val d = Block(dObj, model)
        val dt = dObj.getJSONArray("time")
        val todayDate = java.time.Instant.ofEpochSecond(current.time).atZone(zone).toLocalDate()
        val dCode = d["weather_code"]; val dMax = d["temperature_2m_max"]; val dMin = d["temperature_2m_min"]
        val dFMax = d["apparent_temperature_max"]; val dFMin = d["apparent_temperature_min"]; val dRise = d["sunrise"]
        val dSet = d["sunset"]; val dLight = d["daylight_duration"]; val dSun = d["sunshine_duration"]; val dUv = d["uv_index_max"]
        val dSum = d["precipitation_sum"]; val dHours = d["precipitation_hours"]; val dProb = d["precipitation_probability_max"]
        val dWind = d["wind_speed_10m_max"]; val dGust = d["wind_gusts_10m_max"]; val dDir = d["wind_direction_10m_dominant"]
        val dRain = d["rain_sum"]; val dShowers = d["showers_sum"]; val dSnow = d["snowfall_sum"]
        val allDays = (0 until dt.length()).map { i ->
            Day(
                date = java.time.Instant.ofEpochSecond(dt.long(i)).atZone(zone).toLocalDate(),
                code = dCode.num(i).let { if (it.isNaN()) 0 else it.toInt() },
                max = dMax.num(i),
                min = dMin.num(i),
                feelsMax = dFMax.num(i),
                feelsMin = dFMin.num(i),
                sunrise = dRise.long(i),
                sunset = dSet.long(i),
                daylight = dLight.num(i),
                sunshine = dSun.num(i),
                uvMax = dUv.num(i),
                precipSum = dSum.num(i),
                precipHours = dHours.num(i),
                precipProbMax = dProb.num(i),
                windMax = dWind.num(i),
                gustMax = dGust.num(i),
                windDir = dDir.num(i),
                rainSum = dRain.num(i),
                showersSum = dShowers.num(i),
                snowfallSum = dSnow.num(i),
            )
        }
        // The last forecast day is often incomplete (no max/min); drop such trailing days.
        val daily = allDays.filter { !it.date.isBefore(todayDate) }.dropLastWhile { it.max.isNaN() && it.min.isNaN() }
        val yesterday = allDays.lastOrNull { it.date == todayDate.minusDays(1) }

        val minutely = root.optJSONObject("minutely_15")?.let { m ->
            val t = m.getJSONArray("time")
            val p = Block(m, model)["precipitation"]
            (0 until t.length()).map { Minute(t.long(it), p.num(it)) }
        }.orEmpty()

        val air = airJson?.let { parseAir(it) }

        val base = Forecast(
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
            model = model ?: ForecastModels.AUTO,
            modelFilled = h.filled || d.filled,
            yesterday = yesterday,
            marine = Marine.parse(marineJson, zone),
        )
        return Observations.correct(base, obs)
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
                    no2 = h.optJSONArray("nitrogen_dioxide").num(i),
                    so2 = h.optJSONArray("sulphur_dioxide").num(i),
                    co = h.optJSONArray("carbon_monoxide").num(i),
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
