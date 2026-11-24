package dev.vis.weather.alerts

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import dev.vis.weather.BuildConfig
import dev.vis.weather.L
import dev.vis.weather.R
import dev.vis.weather.data.Place
import dev.vis.weather.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.OffsetDateTime
import java.util.Locale

/** Official warning levels, mapped from CAP severity (Minor/Moderate/Severe/Extreme). */
enum class Severity(@StringRes private val labelRes: Int, @StringRes private val colorRes: Int, val argb: Long, val onArgb: Long) {
    MINOR(R.string.al_severity_minor, R.string.al_level_blue, 0xFF4F8EF7, 0xFFFFFFFF),
    MODERATE(R.string.al_severity_moderate, R.string.al_level_yellow, 0xFFFFC928, 0xFF241A00),
    SEVERE(R.string.al_severity_severe, R.string.al_level_amber, 0xFFFF8A1F, 0xFF2A1200),
    EXTREME(R.string.al_severity_extreme, R.string.al_level_red, 0xFFE5312B, 0xFFFFFFFF);

    val label: String get() = L.s(labelRes)
    val colorName: String get() = L.s(colorRes)
}

enum class Hazard(@StringRes private val labelRes: Int) {
    THUNDERSTORM(R.string.al_hazard_thunderstorm), WIND(R.string.al_hazard_wind), RAIN(R.string.al_hazard_rain), FLOOD(R.string.al_hazard_flood),
    SNOW_ICE(R.string.al_hazard_snow_ice), FOG(R.string.al_hazard_fog), HEAT(R.string.al_hazard_heat), COLD(R.string.al_hazard_cold),
    COASTAL(R.string.al_hazard_coastal), FIRE(R.string.al_hazard_fire), AVALANCHE(R.string.al_hazard_avalanche), TORNADO(R.string.al_hazard_tornado),
    HURRICANE(R.string.al_hazard_hurricane), DUST(R.string.al_hazard_dust), WEATHER(R.string.al_hazard_weather);

    val label: String get() = L.s(labelRes)

    companion object {
        fun from(text: String): Hazard {
            val t = text.lowercase(Locale.ROOT)
            fun has(vararg k: String) = k.any { it in t }
            return when {
                has("tornado") -> TORNADO
                has("hurricane", "typhoon", "cyclone", "tropical storm") -> HURRICANE
                has("thunder", "gewitter", "orage", "tormenta", "temporal", "onweer", "lightning") -> THUNDERSTORM
                has("avalanche", "lawine", "valanghe", "aludes") -> AVALANCHE
                has("fire", "brand", "incendi", "feu") -> FIRE
                has("coast", "surf", "tide", "storm surge", "küste", "sturmflut", "marine", "high seas", "waves") -> COASTAL
                has("flood", "hochwasser", "inond", "inund", "overstroming") -> FLOOD
                has("snow", "ice", "icy", "frost", "freez", "glätte", "glatteis", "schnee", "frost", "neige", "verglas", "nieve", "hielo", "neve", "gelo", "sneeuw", "gladheid", "sleet", "blizzard", "winter") -> SNOW_ICE
                has("fog", "nebel", "brouillard", "niebla", "nebbia", "mist") -> FOG
                has("heat", "hitze", "canicule", "calor", "caldo", "hitte", "high temperature") -> HEAT
                has("cold", "kälte", "froid", "frío", "freddo", "chill", "low temperature") -> COLD
                has("wind", "gale", "gust", "sturm", "böen", "orkan", "vent", "viento", "vento", "storm") -> WIND
                has("rain", "regen", "pluie", "lluvia", "pioggia", "precip", "starkregen", "dauerregen") -> RAIN
                has("dust", "sand", "staub") -> DUST
                else -> WEATHER
            }
        }
    }
}

data class Warning(
    val id: String,
    val event: String,
    val headline: String,
    val description: String,
    val instruction: String,
    val severity: Severity,
    val hazard: Hazard,
    /** Epoch seconds; 0 when unknown. */
    val onset: Long,
    val expires: Long,
    val sender: String,
    val source: String,
    val url: String?,
) {
    val title: String get() = event.replaceFirstChar { it.titlecase(Locale.getDefault()) }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("event", event).put("headline", headline).put("description", description)
        .put("instruction", instruction).put("severity", severity.name).put("hazard", hazard.name)
        .put("onset", onset).put("expires", expires).put("sender", sender).put("source", source).put("url", url)

    companion object {
        fun fromJson(o: JSONObject) = Warning(
            o.getString("id"), o.optString("event"), o.optString("headline"), o.optString("description"),
            o.optString("instruction"), runCatching { Severity.valueOf(o.getString("severity")) }.getOrDefault(Severity.MODERATE),
            runCatching { Hazard.valueOf(o.getString("hazard")) }.getOrDefault(Hazard.WEATHER),
            o.optLong("onset"), o.optLong("expires"), o.optString("sender"), o.optString("source"),
            o.optString("url").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}

/**
 * Official weather warnings from free, key-less public feeds:
 *  - United States: National Weather Service (api.weather.gov), point query
 *  - Germany: Deutscher Wetterdienst via Bright Sky (api.brightsky.dev), point query
 *  - Canada: Environment and Climate Change Canada (api.weather.gc.ca), polygon query
 *  - 36 more European countries: MeteoAlarm / EUMETNET (feeds.meteoalarm.org), matched by polygon or region name
 */
object WarningsApi {
    private val USER_AGENT = "ExpressiveWeather/${BuildConfig.VERSION_NAME} (+${Updater.REPO_URL})"

    private fun get(url: String, accept: String = "application/json"): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", accept)
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun f(v: Double) = String.format(Locale.US, "%.4f", v)

    private val meteoAlarmSlugs = mapOf(
        "at" to "austria", "be" to "belgium", "ba" to "bosnia-herzegovina", "bg" to "bulgaria", "hr" to "croatia",
        "cy" to "cyprus", "cz" to "czechia", "dk" to "denmark", "ee" to "estonia", "fi" to "finland", "fr" to "france",
        "gr" to "greece", "hu" to "hungary", "is" to "iceland", "ie" to "ireland", "il" to "israel", "it" to "italy",
        "lv" to "latvia", "lt" to "lithuania", "lu" to "luxembourg", "mt" to "malta", "md" to "moldova", "me" to "montenegro",
        "nl" to "netherlands", "no" to "norway", "pl" to "poland", "pt" to "portugal", "ro" to "romania", "rs" to "serbia",
        "sk" to "slovakia", "si" to "slovenia", "es" to "spain", "se" to "sweden", "ch" to "switzerland", "ua" to "ukraine",
        "gb" to "united-kingdom",
    )

    /** Which region the place's warnings come from, for UI copy ("Warnings from MeteoAlarm"). */
    fun coverage(countryCode: String?): String? = when (countryCode) {
        "us" -> "National Weather Service"
        "de" -> "Deutscher Wetterdienst"
        "ca" -> "Environment Canada"
        in meteoAlarmSlugs -> "MeteoAlarm"
        else -> null
    }

    /** Active and upcoming warnings for [place], most severe first. Throws on network failure. */
    suspend fun fetch(context: Context, place: Place): List<Warning> = withContext(Dispatchers.IO) {
        val region = PlaceRegions.get(context, place)
        val cc = region?.countryCode
        val list = when {
            cc == "us" -> nws(place)
            cc == "de" -> brightSky(place)
            cc == "ca" -> eccc(place)
            cc != null && cc in meteoAlarmSlugs -> meteoAlarm(meteoAlarmSlugs.getValue(cc), place, region.names)
            cc == null -> coroutineScope {
                // Unknown country (offline geocoder): try the point-based services that answer quickly.
                val a = async { runCatching { nws(place) }.getOrDefault(emptyList()) }
                val b = async { runCatching { brightSky(place) }.getOrDefault(emptyList()) }
                a.await() + b.await()
            }
            else -> emptyList()
        }
        val now = System.currentTimeMillis() / 1000
        list.filter { it.expires == 0L || it.expires > now }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<Warning> { it.severity.ordinal }.thenBy { it.onset })
    }

    // ---- United States

    private fun nws(place: Place): List<Warning> {
        val json = JSONObject(get("https://api.weather.gov/alerts/active?point=${f(place.lat)},${f(place.lon)}", "application/geo+json"))
        val features = json.optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val p = features.getJSONObject(i).optJSONObject("properties") ?: return@mapNotNull null
            if (p.optString("status") != "Actual") return@mapNotNull null
            val event = p.optString("event")
            Warning(
                id = "nws:" + p.optString("id"),
                event = event,
                headline = p.optString("headline"),
                description = p.optString("description").replace(Regex("\n(?!\n)"), " ").trim(),
                instruction = p.optString("instruction").takeIf { it != "null" }.orEmpty().replace(Regex("\n(?!\n)"), " ").trim(),
                severity = cap(p.optString("severity")),
                hazard = Hazard.from(event),
                onset = time(p.optString("onset").ifBlank { p.optString("effective") }),
                expires = time(p.optString("ends").takeIf { it.isNotBlank() && it != "null" } ?: p.optString("expires")),
                sender = p.optString("senderName").ifBlank { "National Weather Service" },
                source = "NWS",
                url = "https://alerts.weather.gov",
            )
        }
    }

    // ---- Germany

    private fun brightSky(place: Place): List<Warning> {
        val lang = Locale.getDefault().language
        val json = JSONObject(get("https://api.brightsky.dev/alerts?lat=${f(place.lat)}&lon=${f(place.lon)}"))
        val alerts = json.optJSONArray("alerts") ?: return emptyList()
        val de = lang == "de"
        return (0 until alerts.length()).map { i ->
            val a = alerts.getJSONObject(i)
            fun t(k: String) = a.optString(if (de) "${k}_de" else "${k}_en").takeIf { it.isNotBlank() && it != "null" }
                ?: a.optString("${k}_en").takeIf { it != "null" }.orEmpty()
            val eventEn = a.optString("event_en")
            Warning(
                id = "dwd:" + a.optString("alert_id"),
                event = t("event").lowercase().replaceFirstChar { it.titlecase() },
                headline = t("headline"),
                description = t("description"),
                instruction = t("instruction"),
                severity = cap(a.optString("severity")),
                hazard = Hazard.from(eventEn),
                onset = time(a.optString("onset")),
                expires = time(a.optString("expires")),
                sender = "Deutscher Wetterdienst",
                source = "DWD",
                url = "https://www.dwd.de/warnungen",
            )
        }
    }

    // ---- Canada

    private fun eccc(place: Place): List<Warning> {
        val d = 0.02
        val json = JSONObject(
            get(
                "https://api.weather.gc.ca/collections/weather-alerts/items?f=json&limit=50&bbox=" +
                    "${f(place.lon - d)},${f(place.lat - d)},${f(place.lon + d)},${f(place.lat + d)}"
            )
        )
        val features = json.optJSONArray("features") ?: return emptyList()
        val fr = Locale.getDefault().language == "fr"
        return (0 until features.length()).mapNotNull { i ->
            val feat = features.getJSONObject(i)
            val p = feat.optJSONObject("properties") ?: return@mapNotNull null
            if (p.optString("status_en") == "ended") return@mapNotNull null
            val geom = feat.optJSONObject("geometry")
            if (geom != null && !geoJsonContains(geom, place.lat, place.lon)) return@mapNotNull null
            fun t(k: String) = p.optString(if (fr) "${k}_fr" else "${k}_en").takeIf { it.isNotBlank() && it != "None" }.orEmpty()
            val type = p.optString("alert_type")
            val colour = p.optString("risk_colour_en").lowercase()
            val severity = when {
                colour == "red" -> Severity.EXTREME
                colour == "orange" -> Severity.SEVERE
                colour == "yellow" -> Severity.MODERATE
                type == "warning" -> Severity.SEVERE
                type == "watch" -> Severity.MODERATE
                else -> Severity.MINOR
            }
            val nameEn = p.optString("alert_name_en")
            Warning(
                id = "eccc:" + p.optString("alert_code") + ":" + p.optString("publication_datetime") + ":" + nameEn,
                event = t("alert_name").replaceFirstChar { it.titlecase() },
                headline = t("alert_short_name"),
                description = t("alert_text"),
                instruction = t("impact"),
                severity = severity,
                hazard = Hazard.from(nameEn),
                onset = time(p.optString("validity_datetime").ifBlank { p.optString("publication_datetime") }),
                expires = time(p.optString("event_end_datetime").ifBlank { p.optString("expiration_datetime") }),
                sender = "Environment and Climate Change Canada",
                source = "ECCC",
                url = "https://weather.gc.ca/warnings/index_e.html",
            )
        }.distinctBy { it.event + it.onset }
    }

    // ---- Europe (MeteoAlarm)

    private fun meteoAlarm(slug: String, place: Place, regionNames: List<String>): List<Warning> {
        val json = JSONObject(get("https://feeds.meteoalarm.org/api/v1/warnings/feeds-$slug"))
        val arr = json.optJSONArray("warnings") ?: return emptyList()
        val wantLang = Locale.getDefault().language
        val names = regionNames.map { normalize(it) }.filter { it.length >= 3 }.toSet()
        val out = mutableListOf<Warning>()
        for (i in 0 until arr.length()) {
            val alert = arr.getJSONObject(i).optJSONObject("alert") ?: continue
            if (alert.optString("msgType") == "Cancel" || alert.optString("status") != "Actual") continue
            val infos = alert.optJSONArray("info") ?: continue
            val all = (0 until infos.length()).map { infos.getJSONObject(it) }
            val info = all.firstOrNull { it.optString("language").startsWith(wantLang) }
                ?: all.firstOrNull { it.optString("language").startsWith("en") } ?: all.firstOrNull() ?: continue
            val areas = info.optJSONArray("area") ?: all.firstNotNullOfOrNull { it.optJSONArray("area") } ?: continue
            val hit = (0 until areas.length()).any { k ->
                val area = areas.getJSONObject(k)
                val polys = area.optJSONArray("polygon")
                if (polys != null && polys.length() > 0) {
                    (0 until polys.length()).any { capPolygonContains(polys.getString(it), place.lat, place.lon) }
                } else {
                    areaNameMatches(area.optString("areaDesc"), names)
                }
            }
            if (!hit) continue
            // Awareness level "2; yellow; Moderate" is more reliable than CAP severity for colour.
            var severity = cap(info.optString("severity"))
            info.optJSONArray("parameter")?.let { ps ->
                for (p in 0 until ps.length()) {
                    val o = ps.getJSONObject(p)
                    if (o.optString("valueName") == "awareness_level") {
                        severity = when (o.optString("value").substringBefore(';').trim()) {
                            "1" -> Severity.MINOR
                            "2" -> Severity.MODERATE
                            "3" -> Severity.SEVERE
                            "4" -> Severity.EXTREME
                            else -> severity
                        }
                    }
                }
            }
            if (severity == Severity.MINOR && info.optString("event").contains("green", ignoreCase = true)) continue
            val eventText = info.optString("event")
            val englishEvent = all.firstOrNull { it.optString("language").startsWith("en") }?.optString("event") ?: eventText
            out += Warning(
                id = "ma:" + alert.optString("identifier"),
                event = eventText,
                headline = info.optString("headline"),
                description = info.optString("description"),
                instruction = info.optString("instruction"),
                severity = severity,
                hazard = Hazard.from(englishEvent + " " + awarenessType(info)),
                onset = time(info.optString("onset").ifBlank { info.optString("effective") }),
                expires = time(info.optString("expires")),
                sender = info.optString("senderName").ifBlank { "MeteoAlarm" },
                source = "MeteoAlarm",
                url = info.optString("web").takeIf { it.startsWith("http") } ?: "https://meteoalarm.org",
            )
        }
        return out
    }

    private fun awarenessType(info: JSONObject): String {
        val ps = info.optJSONArray("parameter") ?: return ""
        for (p in 0 until ps.length()) {
            val o = ps.getJSONObject(p)
            if (o.optString("valueName") == "awareness_type") return o.optString("value").substringAfter(';').trim()
        }
        return ""
    }

    // ---- helpers

    private fun cap(s: String) = when (s.lowercase()) {
        "extreme" -> Severity.EXTREME
        "severe" -> Severity.SEVERE
        "moderate" -> Severity.MODERATE
        else -> Severity.MINOR
    }

    private fun time(s: String?): Long = runCatching { OffsetDateTime.parse(s).toEpochSecond() }.getOrDefault(0L)

    fun normalize(s: String): String {
        val noAccents = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noAccents
            .replace(Regex("\\((stadt|city|land)\\)"), " ")
            .replace(
                Regex("\\b(landkreis|kreis|bezirk|stadt|provincia|province|provinz|di|de|del|la|le|les|du|des|departement|county|region|regione|comunidad|okres|powiat|wojewodztwo|kommune|municipality|district|city of|gemeente|arrondissement|canton|kanton|zona|litoral|interior)\\b"),
                " ",
            )
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** areaDesc may list several areas ("A | B, C"); match whole normalized names, either containing the other. */
    private fun areaNameMatches(areaDesc: String, names: Set<String>): Boolean {
        if (names.isEmpty()) return false
        return areaDesc.split('|', ',', ';', '/').map { normalize(it) }.filter { it.length >= 3 }.any { a ->
            names.any { n -> a == n || (n.length >= 5 && Regex("\\b${Regex.escape(n)}\\b").containsMatchIn(a)) || (a.length >= 5 && Regex("\\b${Regex.escape(a)}\\b").containsMatchIn(n)) }
        }
    }

    /** CAP polygon: "lat,lon lat,lon …". */
    private fun capPolygonContains(poly: String, lat: Double, lon: Double): Boolean {
        val pts = poly.trim().split(Regex("\\s+")).mapNotNull { p ->
            val (a, b) = p.split(',').takeIf { it.size >= 2 } ?: return@mapNotNull null
            val la = a.toDoubleOrNull() ?: return@mapNotNull null
            val lo = b.toDoubleOrNull() ?: return@mapNotNull null
            la to lo
        }
        return pointInRing(pts, lat, lon)
    }

    private fun geoJsonContains(geom: JSONObject, lat: Double, lon: Double): Boolean {
        fun ring(a: JSONArray) = (0 until a.length()).map { val p = a.getJSONArray(it); p.getDouble(1) to p.getDouble(0) }
        fun polygon(rings: JSONArray): Boolean {
            if (rings.length() == 0 || !pointInRing(ring(rings.getJSONArray(0)), lat, lon)) return false
            return (1 until rings.length()).none { pointInRing(ring(rings.getJSONArray(it)), lat, lon) }
        }
        val c = geom.optJSONArray("coordinates") ?: return true
        return when (geom.optString("type")) {
            "Polygon" -> polygon(c)
            "MultiPolygon" -> (0 until c.length()).any { polygon(c.getJSONArray(it)) }
            else -> true
        }
    }

    /** Ray casting on (lat, lon) pairs. */
    fun pointInRing(pts: List<Pair<Double, Double>>, lat: Double, lon: Double): Boolean {
        if (pts.size < 3) return false
        var inside = false
        var j = pts.lastIndex
        for (i in pts.indices) {
            val (yi, xi) = pts[i]
            val (yj, xj) = pts[j]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

/**
 * Country code + local administrative names for a place, from OSM Nominatim (in the local language,
 * which is what national warning feeds use). Cached forever per place, so it costs one request per place.
 */
object PlaceRegions {
    data class Region(val countryCode: String?, val names: List<String>)

    private val memory = mutableMapOf<String, Region>()

    fun cached(context: Context, place: Place): Region? {
        memory[place.key]?.let { return it }
        val sp = context.getSharedPreferences("regions", Context.MODE_PRIVATE)
        val s = sp.getString(place.key, null) ?: return null
        return runCatching {
            val o = JSONObject(s)
            val arr = o.getJSONArray("names")
            Region(o.optString("cc").takeIf { it.isNotBlank() }, (0 until arr.length()).map { arr.getString(it) })
        }.getOrNull()?.also { memory[place.key] = it }
    }

    suspend fun get(context: Context, place: Place): Region? {
        cached(context, place)?.let { return it }
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val ua = "ExpressiveWeather/${BuildConfig.VERSION_NAME} (+${Updater.REPO_URL})"
                val conn = URL(
                    "https://nominatim.openstreetmap.org/reverse?lat=${String.format(Locale.US, "%.4f", place.lat)}" +
                        "&lon=${String.format(Locale.US, "%.4f", place.lon)}&format=jsonv2&zoom=12&addressdetails=1"
                ).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("User-Agent", ua)
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val a = JSONObject(body).getJSONObject("address")
                val keys = listOf(
                    "city", "town", "village", "municipality", "city_district", "suburb", "county", "state_district",
                    "state", "region", "province", "district", "borough", "ISO3166-2-lvl4", "ISO3166-2-lvl6",
                )
                Region(a.optString("country_code").takeIf { it.isNotBlank() }, keys.mapNotNull { k -> a.optString(k).takeIf { it.isNotBlank() } }.distinct())
            }.getOrNull()
        } ?: return null
        memory[place.key] = r
        context.getSharedPreferences("regions", Context.MODE_PRIVATE).edit {
            putString(place.key, JSONObject().put("cc", r.countryCode).put("names", JSONArray(r.names)).toString())
        }
        return r
    }
}
