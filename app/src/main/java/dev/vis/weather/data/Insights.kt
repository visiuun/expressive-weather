package dev.vis.weather.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sqrt

private fun Double.ok() = !isNaN()
private fun List<Double>.cleanMax() = filter { it.ok() }.maxOrNull() ?: Double.NaN
private fun List<Double>.cleanMin() = filter { it.ok() }.minOrNull() ?: Double.NaN
private fun List<Double>.cleanSum() = filter { it.ok() }.sum()
private fun List<Double>.cleanAvg() = filter { it.ok() }.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN

/** Derived weather indices. All inputs metric (°C, %, km/h, hPa, mm). */
object Indices {
    /** A level on a qualitative scale. [level] 0 = best/lowest. [color] ARGB. */
    data class Band(val label: String, val level: Int, val color: Long, val text: String)

    private const val GREEN = 0xFF4CAF50
    private const val TEAL = 0xFF35C7B0
    private const val BLUE = 0xFF4A9EFF
    private const val YELLOW = 0xFFFFC107
    private const val ORANGE = 0xFFFF9800
    private const val RED = 0xFFF44336
    private const val PURPLE = 0xFF9C27B0
    private const val MAROON = 0xFF7E0023

    // ---- wet bulb & cooling comfort

    /** Stull (2011) wet-bulb approximation, valid for RH 5–99 %, T −20…50 °C. */
    fun wetBulb(t: Double, rh: Double): Double {
        if (t.isNaN() || rh.isNaN()) return Double.NaN
        val r = rh.coerceIn(5.0, 99.0)
        return t * atan(0.151977 * sqrt(r + 8.313659)) + atan(t + r) - atan(r - 1.676331) +
            0.00391838 * r.pow(1.5) * atan(0.023101 * r) - 4.686035
    }

    /** How well sweat can cool the body, from the wet-bulb temperature. */
    fun coolingComfort(wetBulb: Double, temp: Double = Double.NaN): Band = when {
        wetBulb.isNaN() -> Band("–", -1, TEAL, "")
        !temp.isNaN() && temp < 20 && wetBulb < 18 -> Band("Normal", 0, BLUE, "Not warm enough for cooling to matter.")
        wetBulb < 21 -> Band("Good", 1, GREEN, "Sweat evaporates easily and your body cools well.")
        wetBulb < 24 -> Band("Reduced", 2, YELLOW, "Sweating cools you less. Take it easier during exercise and drink water.")
        wetBulb < 27 -> Band("Difficult", 3, ORANGE, "Cooling is hard. Avoid strenuous activity in the heat of the day, seek shade and fans.")
        wetBulb < 31 -> Band("Very difficult", 4, RED, "Heat stress is likely even at rest for vulnerable people. Stay in cool places.")
        else -> Band("Critical", 5, MAROON, "Near the limit of human cooling. Stay in air-conditioning and check on others.")
    }

    const val COOLING_EXPLAINER =
        "Your body cools by sweating. The wet-bulb temperature combines heat and humidity to show how well that sweat can " +
            "evaporate: the closer it gets to skin temperature (~35 °C), the less cooling you get. Above ~31 °C even " +
            "healthy people struggle to stay cool for long."

    // ---- humidity comfort (dew point)

    /** Comfort from the dew point. [rh] (optional) keeps damp, cool air from being called "dry". */
    fun humidityComfort(dew: Double, rh: Double = Double.NaN): Band = when {
        dew.isNaN() -> Band("–", -1, TEAL, "")
        dew < 10 && (rh.isNaN() || rh < 55) -> Band("Dry", 0, BLUE, "Crisp air; skin and lips may feel dry.")
        dew < 10 -> Band("Comfortable", 1, TEAL, "Cool, damp air, but humidity won't feel uncomfortable.")
        dew < 15 -> Band("Comfortable", 1, TEAL, "Pleasant, you won't notice the humidity.")
        dew < 18 -> Band("Noticeable", 2, GREEN, "You can start to feel moisture in the air.")
        dew < 21 -> Band("Sticky", 3, YELLOW, "Clammy; exercise feels harder.")
        dew < 24 -> Band("Muggy", 4, ORANGE, "Heavy, uncomfortable air for most people.")
        else -> Band("Oppressive", 5, RED, "Very uncomfortable; take it easy.")
    }

    // ---- fire weather (Fosberg)

    /** Fosberg Fire Weather Index (0–100) from °C, %RH and km/h. Doesn't consider fuel dryness or recent rain. */
    fun fosberg(tempC: Double, rh: Double, windKmh: Double): Double {
        if (tempC.isNaN() || rh.isNaN() || windKmh.isNaN()) return Double.NaN
        val t = tempC * 9 / 5 + 32
        val h = rh.coerceIn(0.0, 100.0)
        val u = windKmh * 0.621371
        val m = when {
            h < 10 -> 0.03229 + 0.281073 * h - 0.000578 * h * t
            h <= 50 -> 2.22749 + 0.160107 * h - 0.01478 * t
            else -> 21.0606 + 0.005565 * h * h - 0.00035 * h * t - 0.483199 * h
        }.coerceAtLeast(0.0)
        val x = (m / 30).coerceAtMost(1.0)
        val eta = 1 - 2 * x + 1.5 * x * x - 0.5 * x * x * x
        return (eta * sqrt(1 + u * u) / 0.3002).coerceIn(0.0, 100.0)
    }

    /** Hourly index, damped while it's raining or snow covers the ground. */
    fun fireIndex(h: Hour): Double {
        val v = fosberg(h.temp, h.humidity, h.windSpeed)
        if (v.isNaN()) return v
        return when {
            !h.snowDepth.isNaN() && h.snowDepth > 0.02 -> minOf(v, 5.0)
            !h.precip.isNaN() && h.precip >= 0.5 -> v * 0.4
            else -> v
        }
    }

    fun fireBand(v: Double): Band = when {
        v.isNaN() -> Band("–", -1, TEAL, "")
        v < 15 -> Band("Low", 0, GREEN, "Fires are unlikely to spread quickly.")
        v < 30 -> Band("Moderate", 1, YELLOW, "Fires can start and spread in dry vegetation. Be careful with flames outdoors.")
        v < 50 -> Band("High", 2, ORANGE, "Fires can spread quickly. Avoid open fires and barbecues near dry grass.")
        v < 75 -> Band("Very high", 3, RED, "Fires would spread rapidly and be hard to control. No open fires.")
        else -> Band("Extreme", 4, MAROON, "Explosive fire weather. Follow local fire bans and be ready to leave if told to.")
    }

    const val FIRE_EXPLAINER =
        "The Fosberg Fire Weather Index rates how quickly a fire could spread from the weather alone: heat and dry air " +
            "dry out fine fuels like grass, and wind feeds the flames. It doesn't know how dry the ground has been over " +
            "recent weeks, so always follow official fire danger ratings and bans."

    // ---- pressure

    data class PressureTrend(val label: String, val change3h: Double, val text: String, val fast: Boolean, val headache: String?)

    /** 3-hour pressure tendency at the current time (hPa). */
    fun pressureTrend(f: Forecast): PressureTrend? {
        val idx = f.hourlyAll.indexOfLast { it.time <= f.current.time }
        if (idx < 3) return null
        val now = f.current.pressure.takeIf { it.ok() } ?: f.hourlyAll[idx].pressure
        val past = f.hourlyAll[idx - 3].pressure
        if (now.isNaN() || past.isNaN()) return null
        val d = now - past
        val next24 = f.hourlyAll.drop(idx).take(25).map { it.pressure }
        val drop24 = (now - next24.cleanMin()).takeIf { it.ok() } ?: 0.0
        val (label, text) = when {
            d <= -3.5 -> "Dropping fast" to "Pressure is falling quickly — a sign of an approaching storm or strong wind."
            d < -1.0 -> "Falling" to "Falling pressure often comes before clouds, rain and wind."
            d >= 3.5 -> "Rising fast" to "Pressure is climbing quickly; weather is clearing, but it can be breezy."
            d > 1.0 -> "Rising" to "Rising pressure usually means settling, drier weather."
            else -> "Steady" to "No big change from pressure alone."
        }
        val headache = when {
            d <= -3.5 || drop24 >= 8 -> "Headache risk: sharp pressure drops can trigger headaches and migraines in sensitive people."
            drop24 >= 5 -> "Pressure will fall ${drop24.toInt()} hPa over the next day, which some people feel as headaches or joint pain."
            else -> null
        }
        return PressureTrend(label, d, text, d <= -3.5, headache)
    }

    // ---- UV

    data class UvWindow(val start: Long, val end: Long, val peakTime: Long, val peak: Double)

    /** Hours on [date] when UV ≥ [threshold]; null when it never gets there. */
    fun uvWindow(f: Forecast, date: LocalDate, threshold: Double = 3.0): UvWindow? {
        val hours = f.hourlyAll.filter { Instant.ofEpochSecond(it.time).atZone(f.zone).toLocalDate() == date && it.uv.ok() }
        if (hours.isEmpty()) return null
        val peak = hours.maxBy { it.uv }
        val above = hours.filter { it.uv >= threshold }
        if (above.isEmpty()) return UvWindow(0, 0, peak.time, peak.uv)
        return UvWindow(above.first().time, above.last().time + 3600, peak.time, peak.uv)
    }

    // ---- air quality

    data class Pollutant(val name: String, val key: String, val value: Double, val limit: Double, val about: String)

    fun pollutants(a: AirQuality): List<Pollutant> = listOf(
        Pollutant("PM2.5", "pm25", a.pm25, 15.0, "Fine particles from combustion and traffic; they reach deep into the lungs."),
        Pollutant("PM10", "pm10", a.pm10, 45.0, "Coarser particles: dust, pollen, construction and sea salt."),
        Pollutant("O₃ Ozone", "o3", a.o3, 100.0, "Forms in sunlight from traffic emissions; peaks on hot afternoons."),
        Pollutant("NO₂", "no2", a.no2, 25.0, "Mainly from vehicle exhaust; highest near busy roads."),
        Pollutant("SO₂", "so2", a.so2, 40.0, "From burning sulphur-containing fuels and shipping."),
        Pollutant("CO", "co", a.co, 4000.0, "Carbon monoxide from incomplete combustion."),
    )

    /** WHO 2021 guideline comparison (24 h / 8 h guideline values). */
    fun whoStatus(v: Double, limit: Double): Band {
        if (v.isNaN()) return Band("–", -1, TEAL, "")
        val r = v / limit
        return when {
            r < 0.5 -> Band("Well below limit", 0, GREEN, "")
            r < 1.0 -> Band("Near limit", 1, YELLOW, "")
            r < 2.0 -> Band("Above limit", 2, ORANGE, "")
            else -> Band("Well above limit", 3, RED, "")
        }
    }

    fun euAqiBand(v: Double): Band = when {
        v.isNaN() -> Band("–", -1, TEAL, "")
        v <= 20 -> Band("Good", 0, 0xFF50F0E6, "The air quality is good. Enjoy your usual outdoor activities.")
        v <= 40 -> Band("Fair", 1, 0xFF50CCAA, "Enjoy your usual outdoor activities.")
        v <= 60 -> Band("Moderate", 2, 0xFFF0E641, "Sensitive people should consider reducing intense outdoor activity if they have symptoms.")
        v <= 80 -> Band("Poor", 3, 0xFFFF5050, "Consider reducing intense activities outdoors if you have symptoms such as sore eyes, a cough or sore throat.")
        v <= 100 -> Band("Very poor", 4, 0xFF960032, "Reduce physical activity outdoors, particularly if you experience symptoms.")
        else -> Band("Extremely poor", 5, 0xFF7D2181, "Avoid physical activity outdoors; sensitive groups should stay indoors.")
    }

    fun usAqiBand(v: Double): Band = when {
        v.isNaN() -> Band("–", -1, TEAL, "")
        v <= 50 -> Band("Good", 0, GREEN, "Air quality is satisfactory and poses little or no risk.")
        v <= 100 -> Band("Moderate", 1, YELLOW, "Acceptable. Unusually sensitive people should consider limiting prolonged exertion.")
        v <= 150 -> Band("Unhealthy for sensitive groups", 2, ORANGE, "Children, older adults and people with heart or lung disease should reduce prolonged exertion.")
        v <= 200 -> Band("Unhealthy", 3, RED, "Everyone may begin to feel effects; sensitive groups should avoid prolonged exertion.")
        v <= 300 -> Band("Very unhealthy", 4, PURPLE, "Health alert: everyone should avoid prolonged outdoor exertion.")
        else -> Band("Hazardous", 5, MAROON, "Emergency conditions: stay indoors with windows closed.")
    }

    // ---- clouds & visibility

    fun cloudGuidance(cover: Double, f: Forecast): String {
        if (cover.isNaN()) return ""
        val next = f.upcoming.take(12).map { it.cloudCover }
        val later = next.drop(4).cleanAvg()
        val trend = when {
            later.isNaN() -> ""
            later < cover - 25 -> " Skies clear over the next few hours."
            later > cover + 25 -> " Clouds build over the next few hours."
            else -> ""
        }
        val base = when {
            cover < 12 -> if (f.current.isDay) "Full sunshine — great light, but remember the UV." else "Clear sky: good for stars, and it'll get colder overnight."
            cover < 37 -> if (f.current.isDay) "Mostly sunny with a few clouds." else "Mostly clear night."
            cover < 62 -> "Sun and clouds taking turns."
            cover < 87 -> if (f.current.isDay) "Mostly grey with occasional brighter spells." else "Mostly cloudy, which keeps the night milder."
            else -> "Overcast — flat light and no direct sun."
        }
        return base + trend
    }

    fun visibilityRating(m: Double): Band = when {
        m.isNaN() -> Band("–", -1, TEAL, "")
        m < 200 -> Band("Dense fog", 5, RED, "Drive with fog lights and leave a big gap.")
        m < 1000 -> Band("Fog", 4, ORANGE, "Slow down on the road; landmarks disappear quickly.")
        m < 4000 -> Band("Poor", 3, YELLOW, "Mist, haze or heavy precipitation.")
        m < 10000 -> Band("Moderate", 2, TEAL, "Some haze; distant hills look faded.")
        m < 20000 -> Band("Good", 1, GREEN, "Clear views across town.")
        else -> Band("Excellent", 0, BLUE, "Crystal clear — far horizons are sharp.")
    }
}

/** Plain-language comparisons and summaries. */
object Insights {
    private fun date(f: Forecast, t: Long): LocalDate = Instant.ofEpochSecond(t).atZone(f.zone).toLocalDate()

    private fun pick(options: List<String>, seed: LocalDate): String = options[abs(seed.toEpochDay().toInt()) % options.size]

    private fun phrase(diff: Double, ref: String, seed: LocalDate): String {
        val warmer = diff > 0
        val a = abs(diff)
        val w = if (warmer) "warmer" else listOf("cooler", "colder")[abs(seed.dayOfYear) % 2]
        return when {
            a < 1.0 -> pick(listOf("Similar to $ref.", "About the same as $ref.", "Much like $ref."), seed)
            a < 3.0 -> pick(listOf("A little $w than $ref.", "Slightly $w than $ref.", "A touch $w than $ref."), seed)
            a < 6.0 -> pick(listOf("A few degrees $w than $ref.", "Noticeably $w than $ref.", "${w.replaceFirstChar { it.uppercase() }} than $ref."), seed)
            else -> pick(listOf("Much $w than $ref.", "A lot $w than $ref.", "Far $w than $ref."), seed)
        }
    }

    private fun yesterdayMax(f: Forecast): Double {
        f.yesterday?.max?.takeIf { it.ok() }?.let { return it }
        val today = f.today?.date ?: return Double.NaN
        return f.hourlyAll.filter { date(f, it.time) == today.minusDays(1) }.map { it.temp }.cleanMax()
    }

    /** "A few degrees warmer than yesterday." — today's high vs yesterday's high. Null without data. */
    fun compareYesterday(f: Forecast): String? {
        val t = f.today ?: return null
        val y = yesterdayMax(f)
        if (y.isNaN() || t.max.isNaN()) return null
        return phrase(t.max - y, "yesterday", t.date)
    }

    /** "Much colder than today." — tomorrow's high vs today's high. Null without data. */
    fun compareTomorrow(f: Forecast): String? {
        val t = f.today ?: return null
        val tm = f.daily.getOrNull(1) ?: return null
        if (t.max.isNaN() || tm.max.isNaN()) return null
        return phrase(tm.max - t.max, "today", tm.date)
    }

    data class PrecipToday(
        /** mm fallen since midnight (model). */
        val soFar: Double,
        /** mm still expected until midnight. */
        val later: Double,
        val total: Double,
        /** Fresh snow, cm. */
        val snowSoFar: Double,
        val snowLater: Double,
        val fallingNow: Boolean,
        /** "None so far today", "Fell earlier today", "Falling now". */
        val headline: String,
        /** "2.4 mm more expected this evening." style detail, unit-agnostic (caller formats amounts). */
        val laterText: String,
    )

    /** Today's accumulation split at the current time. */
    fun precipitationToday(f: Forecast): PrecipToday {
        val today = f.today?.date ?: date(f, f.current.time)
        val todays = f.hourlyAll.filter { date(f, it.time) == today }
        // Hourly values are the amount during the preceding hour.
        val past = todays.filter { it.time <= f.current.time }
        val fut = todays.filter { it.time > f.current.time }
        val soFar = past.map { it.precip }.cleanSum()
        val later = fut.map { it.precip }.cleanSum()
        val snowSoFar = past.map { it.snowfall }.cleanSum()
        val snowLater = fut.map { it.snowfall }.cleanSum()
        val nowP = maxOf(
            f.current.precipitation.takeIf { it.ok() } ?: 0.0,
            f.minutely.firstOrNull { it.time >= f.current.time - 900 }?.precip?.takeIf { it.ok() } ?: 0.0,
        )
        val falling = nowP >= 0.05 || WeatherInfo.sky(f.current.code).let { it == Sky.RAIN || it == Sky.DRIZZLE || it == Sky.SNOW || it == Sky.THUNDER || it == Sky.FREEZING } && nowP > 0
        val headline = when {
            falling -> if (WeatherInfo.sky(f.current.code) == Sky.SNOW) "Snowing now" else "Falling now"
            soFar >= 0.05 -> "Fell earlier today"
            else -> "None so far today"
        }
        val wetLater = fut.filter { it.precip.ok() && it.precip >= 0.1 }
        val laterText = when {
            wetLater.isEmpty() -> if (soFar >= 0.05 || falling) "Drying out for the rest of the day." else "Staying dry for the rest of the day."
            else -> {
                val h = Instant.ofEpochSecond(wetLater.first().time - 3600).atZone(f.zone).hour
                val part = when (h) { in 0..5 -> "overnight"; in 6..11 -> "this morning"; in 12..17 -> "this afternoon"; else -> "this evening" }
                "More expected $part."
            }
        }
        return PrecipToday(soFar, later, soFar + later, snowSoFar, snowLater, falling, headline, laterText)
    }

    data class PrecipTypes(val rainMm: Double, val snowCm: Double, val iceMm: Double) {
        val any get() = rainMm >= 0.1 || snowCm >= 0.1 || iceMm >= 0.1
    }

    /** Rain (rain + showers) vs snow vs freezing rain/drizzle over [hours]. */
    fun precipTypes(hours: List<Hour>): PrecipTypes {
        var rain = 0.0; var snow = 0.0; var ice = 0.0
        for (h in hours) {
            val freezing = h.code in listOf(56, 57, 66, 67)
            val liquid = (if (h.rain.ok()) h.rain else 0.0) + (if (h.showers.ok()) h.showers else 0.0)
            val liquidOrTotal = if (h.rain.isNaN() && h.showers.isNaN()) {
                // Model without the split: attribute by weather code.
                val p = if (h.precip.ok()) h.precip else 0.0
                if (WeatherInfo.sky(h.code) == Sky.SNOW) { snow += p * 0.7; 0.0 } else p
            } else liquid
            if (freezing) ice += liquidOrTotal else rain += liquidOrTotal
            if (h.snowfall.ok()) snow += h.snowfall
        }
        return PrecipTypes(rain, snow, ice)
    }

    data class WeekRain(val total: Double, val wetDays: Int, val wettest: Day?)

    fun weekRain(f: Forecast): WeekRain {
        val week = f.daily.take(7)
        val total = week.map { it.precipSum }.cleanSum()
        return WeekRain(total, week.count { it.precipSum.ok() && it.precipSum >= 1.0 }, week.filter { it.precipSum.ok() && it.precipSum >= 1.0 }.maxByOrNull { it.precipSum })
    }

    fun precipGuidance(f: Forecast): String {
        val next6 = f.upcoming.take(6)
        val prob = next6.map { it.precipProb }.cleanMax()
        val amount = next6.map { it.precip }.cleanSum()
        val types = precipTypes(f.upcoming.take(12))
        return when {
            types.iceMm >= 0.2 -> "Freezing rain possible — roads and paths can turn to sheet ice."
            types.snowCm >= 2 -> "Snow on the way. Allow extra time for travel."
            amount >= 10 -> "Heavy rain in the next hours. Expect puddles and spray on the roads."
            amount >= 2 || (prob.ok() && prob >= 60) -> "Take an umbrella if you're heading out."
            prob.ok() && prob >= 30 -> "A shower is possible; a light jacket should do."
            else -> "No umbrella needed for the next few hours."
        }
    }

    // ---- humidity through the day

    /** e.g. "Sticky afternoon" + sentence, from the stickiest part of [date]. */
    fun humidityDay(f: Forecast, date: LocalDate): Pair<String, String>? {
        val hours = f.hourlyAll.filter { date(f, it.time) == date && it.dewPoint.ok() }
        if (hours.isEmpty()) return null
        fun part(h: Hour) = when (Instant.ofEpochSecond(h.time).atZone(f.zone).hour) {
            in 6..11 -> "morning"; in 12..17 -> "afternoon"; in 18..23 -> "evening"; else -> "night"
        }
        val byPart = hours.groupBy { part(it) }
        fun bandOf(v: List<Hour>) = v.maxBy { it.dewPoint }.let { Indices.humidityComfort(it.dewPoint, v.map { h -> h.humidity }.cleanAvg()) }
        val bands = byPart.mapValues { (_, v) -> bandOf(v) }
        val worstEntry = bands.maxByOrNull { it.value.level } ?: return null
        val worst = worstEntry.key
        val band = worstEntry.value
        val best = bands.values.minBy { it.level }
        return when {
            band.level <= 0 -> "Dry all day" to "Low moisture in the air; skin and lips may feel dry."
            band.level <= 1 -> "Comfortable all day" to "Humidity won't be noticeable."
            best.level == band.level -> "${band.label} all day" to band.text
            else -> "${band.label} $worst" to "${band.text} ${best.label} at other times."
        }
    }

    // ---- stargazing

    data class Stargazing(
        val rating: String,
        val level: Int,
        val score: Int,
        val cloud: Double,
        val moonIllumination: Double,
        /** Fraction of the dark hours with the moon above the horizon. */
        val moonUp: Double,
        val darkStart: Long,
        val darkEnd: Long,
        /** Best hour to look up, or 0. */
        val best: Long,
        val sentence: String,
    )

    fun stargazing(f: Forecast): Stargazing? {
        val lat = f.latitude; val lon = if (f.longitude.ok()) f.longitude else 0.0
        if (lat.isNaN()) return null
        val now = f.current.time
        val today = date(f, now)
        val todaySun = Astro.sunDay(today, lat, lon)
        val nightDate = if (todaySun.firstLight != 0L && now < todaySun.firstLight) today.minusDays(1) else today
        val eveSun = if (nightDate == today) todaySun else Astro.sunDay(nightDate, lat, lon)
        val mornSun = Astro.sunDay(nightDate.plusDays(1), lat, lon)
        val zone: ZoneId = f.zone
        val start = eveSun.lastLight.takeIf { it != 0L } ?: eveSun.sunset.takeIf { it != 0L }?.plus(3600)
            ?: nightDate.atTime(21, 0).atZone(zone).toEpochSecond()
        val end = mornSun.firstLight.takeIf { it != 0L && it > start } ?: mornSun.sunrise.takeIf { it != 0L && it > start }?.minus(3600)
            ?: nightDate.plusDays(1).atTime(5, 0).atZone(zone).toEpochSecond()
        if (eveSun.polarDay) return null
        val hours = f.hourlyAll.filter { it.time in start..(end + 3599) && it.time >= now - 3600 }
            .ifEmpty { f.hourlyAll.filter { it.time in start..(end + 3599) } }
        if (hours.isEmpty()) return null
        val cloud = hours.map { it.cloudCover }.cleanAvg()
        val moon = Astro.moonState(start + (end - start) / 2, lat, lon)
        val samples = hours.map { it.time to Astro.moonState(it.time, lat, lon).altitude }
        val moonUp = samples.count { it.second > 0 }.toDouble() / samples.size
        val fog = hours.any { WeatherInfo.sky(it.code) == Sky.FOG }
        val c = if (cloud.isNaN()) 50.0 else cloud
        val score = (100 - c * 0.85 - moon.illumination * moonUp * 35 - (if (fog) 15 else 0)).coerceIn(0.0, 100.0).toInt()
        val (rating, level) = when {
            score >= 75 -> "Excellent" to 0
            score >= 55 -> "Good" to 1
            score >= 35 -> "Fair" to 2
            else -> "Poor" to 3
        }
        // Best hour: least cloud, preferring the moon below the horizon.
        val best = hours.minByOrNull { h ->
            val alt = samples.firstOrNull { it.first == h.time }?.second ?: 0.0
            (if (h.cloudCover.ok()) h.cloudCover else 50.0) + (if (alt > 0) moon.illumination * 30 else 0.0)
        }?.time ?: 0L
        val moonBright = moon.illumination * moonUp
        val sky = when {
            c < 15 -> "Clear skies"
            c < 40 -> "Mostly clear skies"
            c < 70 -> "Patchy cloud"
            else -> "Thick cloud"
        }
        val sentence = when {
            fog -> "Fog or low cloud will hide most of the sky tonight."
            c >= 70 -> "Clouds will hide most of the sky tonight."
            moonBright < 0.2 && c < 40 -> "$sky and a dark moon — a great night to spot faint stars and the Milky Way."
            moonBright < 0.2 -> "$sky, but a dark moon: look for gaps in the cloud."
            moon.illumination > 0.7 && moonUp > 0.5 -> "$sky, but the bright moon will wash out fainter stars. Great for moon-gazing though."
            moonUp < 0.5 -> "$sky, with the moon up for part of the night — aim for when it's down."
            else -> "$sky with some moonlight; bright stars and planets will still stand out."
        }
        return Stargazing(rating, level, score, cloud, moon.illumination, moonUp, start, end, best, sentence)
    }
}
