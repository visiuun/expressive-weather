package dev.vis.weather.alerts

import dev.vis.weather.data.Day
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Hour
import dev.vis.weather.data.Sky
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.Fmt
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Plain-language summaries used by notifications and in-app cards. */
object Briefings {
    private fun Double.ok() = !isNaN()

    fun hoursOf(f: Forecast, date: LocalDate): List<Hour> =
        f.hourlyAll.filter { Instant.ofEpochSecond(it.time).atZone(f.zone).toLocalDate() == date }

    fun hoursBetween(f: Forecast, from: Long, to: Long): List<Hour> = f.hourlyAll.filter { it.time in from until to }

    fun yesterday(f: Forecast): Pair<Double, Double>? {
        val today = f.today?.date ?: return null
        val hrs = hoursOf(f, today.minusDays(1)).map { it.temp }.filter { it.ok() }
        if (hrs.size < 12) return null
        return hrs.max() to hrs.min()
    }

    /** "A few degrees warmer than yesterday." style comparison of daily highs. */
    fun compare(now: Double, before: Double, vs: String): String {
        if (!now.ok() || !before.ok()) return ""
        val d = now - before
        val pick = (abs(now * 10).toInt()) % 3
        val warmer = d > 0
        return when {
            abs(d) < 1.5 -> listOf("About the same as $vs.", "Similar to $vs.", "Much the same as $vs.")[pick]
            abs(d) < 3.5 -> if (warmer) listOf("A touch warmer than $vs.", "Slightly warmer than $vs.", "A little milder than $vs.")[pick]
            else listOf("A touch cooler than $vs.", "Slightly cooler than $vs.", "A little cooler than $vs.")[pick]
            abs(d) < 7 -> if (warmer) listOf("A few degrees warmer than $vs.", "Noticeably warmer than $vs.", "Warmer than $vs.")[pick]
            else listOf("A few degrees cooler than $vs.", "Noticeably cooler than $vs.", "Cooler than $vs.")[pick]
            else -> if (warmer) listOf("Significantly warmer than $vs.", "Much warmer than $vs.", "A big jump in temperature from $vs.")[pick]
            else listOf("Significantly colder than $vs.", "Much colder than $vs.", "A big drop in temperature from $vs.")[pick]
        }
    }

    private fun conditionPhrase(day: Day, hours: List<Hour>): String {
        val sky = WeatherInfo.sky(day.code)
        val gust = hours.maxOfOrNull { if (it.windGust.ok()) it.windGust else 0.0 } ?: day.gustMax
        return when {
            sky == Sky.THUNDER -> "Thunderstorms possible"
            sky == Sky.SNOW -> "Snow likely"
            sky == Sky.FREEZING -> "Freezing rain possible, watch your step"
            sky == Sky.RAIN && day.precipSum >= 10 -> "Heavy rain expected, take an umbrella"
            sky == Sky.RAIN -> "Rain expected, take an umbrella"
            sky == Sky.DRIZZLE -> "Light drizzle likely"
            sky == Sky.FOG -> "Foggy, so drive carefully"
            gust >= 55 -> "A notably windy day, expect some gusts"
            sky == Sky.CLOUDY -> "Overcast for most of the day"
            sky == Sky.PARTLY -> "Mostly sunny with some cloud"
            sky == Sky.CLEAR -> "Clear skies expected"
            else -> "Mixed conditions expected"
        }
    }

    /** First hour with a meaningful chance of precipitation, as "Rain likely from 15:00 (70%)". */
    fun rainTiming(hours: List<Hour>, fmt: Fmt, minChance: Double = 50.0): String? {
        val wet = hours.firstOrNull { it.precipProb.ok() && it.precipProb >= minChance && (it.precip.ok() && it.precip >= 0.1 || !it.precip.ok()) }
            ?: return null
        val kind = when (WeatherInfo.sky(wet.code)) {
            Sky.SNOW -> "Snow"
            Sky.THUNDER -> "Thunderstorms"
            Sky.DRIZZLE -> "Drizzle"
            else -> "Rain"
        }
        val idx = hours.indexOf(wet)
        val until = hours.drop(idx).firstOrNull { it.precipProb.ok() && it.precipProb < minChance * 0.6 }
        val span = if (until != null && until.time - wet.time <= 4 * 3600) " until ${fmt.hour(until.time)}" else ""
        return "$kind likely from ${fmt.hour(wet.time)}$span (${wet.precipProb.roundToInt()}%)."
    }

    data class Brief(val title: String, val text: String, val big: String)

    fun today(f: Forecast, fmt: Fmt, placeName: String): Brief? {
        val day = f.today ?: return null
        val hours = hoursOf(f, day.date).filter { it.time >= f.current.time - 3600 }
        val cond = WeatherInfo.describe(day.code)
        val parts = mutableListOf<String>()
        parts += "${conditionPhrase(day, hours)}."
        rainTiming(hours, fmt)?.let { parts += it }
        val gust = hours.maxOfOrNull { if (it.windGust.ok()) it.windGust else 0.0 } ?: 0.0
        if (gust >= 45) parts += "Gusts up to ${fmt.wind(gust)}."
        if (day.uvMax.ok() && day.uvMax >= 6) parts += "UV ${day.uvMax.roundToInt()} (${WeatherInfo.uvLevel(day.uvMax).lowercase()}) around midday."
        yesterday(f)?.let { (yMax, _) -> compare(day.max, yMax, "yesterday").takeIf { it.isNotBlank() }?.let { parts += it } }
        val title = "Today: ${cond.lowercase().replaceFirstChar { it.titlecase() }}, ${fmt.temp(day.max)} / ${fmt.temp(day.min)}"
        val text = parts.take(2).joinToString(" ")
        return Brief(title, text, parts.joinToString(" "))
    }

    fun tomorrow(f: Forecast, fmt: Fmt): Brief? {
        val today = f.today ?: return null
        val day = f.daily.getOrNull(1) ?: return null
        val hours = hoursOf(f, day.date)
        val cond = conditionPhrase(day, hours)
        val d = day.max - today.max
        val temp = when {
            !d.ok() -> "Check the app for the full forecast"
            d >= 6 -> "A much warmer day tomorrow than today"
            d >= 2 -> "A bit warmer tomorrow than today"
            d <= -6 -> "A noticeably colder day tomorrow"
            d <= -2 -> "A bit colder tomorrow than today"
            else -> "Similar temperatures to today"
        }
        val sky = WeatherInfo.sky(day.code)
        val gust = hours.maxOfOrNull { if (it.windGust.ok()) it.windGust else 0.0 } ?: 0.0
        val highlight = when {
            sky == Sky.SNOW -> "Dress warmly, lows around ${fmt.temp(day.min)}."
            sky == Sky.RAIN && day.precipSum >= 10 -> "An umbrella is a must."
            sky == Sky.RAIN || sky == Sky.DRIZZLE || sky == Sky.THUNDER -> "An umbrella might come in handy."
            gust >= 50 -> "Hold onto your hat, it'll be gusty."
            else -> "Highs around ${fmt.temp(day.max)}, dropping to ${fmt.temp(day.min)} overnight."
        }
        val rain = rainTiming(hours, fmt)
        val title = "Tomorrow: ${WeatherInfo.describe(day.code).lowercase().replaceFirstChar { it.titlecase() }}, ${fmt.temp(day.max)} / ${fmt.temp(day.min)}"
        val text = "$cond. $temp."
        return Brief(title, "$text $highlight", listOfNotNull("$cond.", "$temp.", rain, highlight).joinToString(" "))
    }

    // ---- derived values

    /** Stull (2011) wet-bulb temperature from air temperature (°C) and relative humidity (%). */
    fun wetBulb(t: Double, rh: Double): Double {
        if (!t.ok() || !rh.ok()) return Double.NaN
        return t * atan(0.151977 * sqrt(rh + 8.313659)) + atan(t + rh) - atan(rh - 1.676331) +
            0.00391838 * rh.pow(1.5) * atan(0.023101 * rh) - 4.686035
    }

    fun coolingLevel(wb: Double): Pair<String, String> = when {
        wb >= 31 -> "Critical" to "Dangerous conditions. Avoid prolonged time outdoors and seek air-conditioned spaces."
        wb >= 28 -> "Very difficult" to "Significant strain on your body. Avoid strenuous activity and rest in the shade."
        wb >= 26 -> "Difficult" to "Limit strenuous outdoor activity. Rest in cool, shaded areas and drink plenty of water."
        wb >= 24 -> "Reduced" to "Take regular breaks in shade and stay hydrated during the warmest hours."
        wb >= 20 -> "Good" to "Comfortable for outdoor activity. Stay hydrated as usual."
        else -> "Normal" to "No precautions needed. Enjoy outdoor activities freely."
    }

    /** European AQI band 1..6. */
    fun eaqiBand(v: Double): Int = when {
        !v.ok() -> 0
        v < 20 -> 1
        v < 40 -> 2
        v < 60 -> 3
        v < 80 -> 4
        v < 100 -> 5
        else -> 6
    }

    val eaqiNames = listOf("", "Good", "Fair", "Moderate", "Poor", "Very poor", "Extremely poor")
}

// ---------------------------------------------------------------- Wardrobe

/** What kind of item it is, so the UI can pick a matching icon. */
enum class Wear { COAT, GLOVES, LAYERS, SWEATER, JACKET, TOP, SHOES, BOOTS, RAIN, UMBRELLA, HOOD, WIND, SUN }

data class WearItem(val label: String, val kind: Wear)

data class WardrobeAdvice(
    val dayLabel: String,
    val items: List<WearItem>,
    val summary: String,
)

/**
 * Deterministic "what to wear" rules on the hourly forecast for the hours you're out.
 * [sensitivity]: 0 feels cold, 1 normal, 2 feels warm. [commute]: "walk", "transit" or "balanced".
 * [work] (optional) supplies the 09–17 hours when you're at another place.
 */
object Wardrobe {
    fun advise(home: Forecast, work: Forecast?, fmt: Fmt, tomorrow: Boolean, sensitivity: Int, commute: String): WardrobeAdvice? {
        val day = (if (tomorrow) home.daily.getOrNull(1) else home.today) ?: return null
        val date = day.date
        fun hours(f: Forecast, from: Int, to: Int) = Briefings.hoursOf(f, date).filter {
            val h = Instant.ofEpochSecond(it.time).atZone(f.zone).hour
            h in from until to && (tomorrow || it.time >= f.current.time - 3600)
        }
        val out = if (work != null) hours(home, 7, 9) + hours(work, 9, 17) + hours(home, 17, 20) else hours(home, 7, 20)
        if (out.isEmpty()) return null
        val shift = when (sensitivity) { 0 -> -3.0; 2 -> 3.0; else -> 0.0 }
        val exposed = commute == "walk"
        val sheltered = commute == "transit"
        val feels = out.mapNotNull { it.feelsLike.takeIf { v -> !v.isNaN() } }.map { it + shift - if (exposed) 1.5 else 0.0 }
        val lo = feels.minOrNull() ?: return null
        val hi = feels.maxOrNull() ?: lo
        val rainChance = out.maxOf { if (it.precipProb.isNaN()) 0.0 else it.precipProb }
        val rainTotal = out.sumOf { if (it.precip.isNaN()) 0.0 else it.precip }
        val snow = out.any { WeatherInfo.sky(it.code) == Sky.SNOW }
        val gust = out.maxOf { if (it.windGust.isNaN()) 0.0 else it.windGust }
        val uv = out.maxOf { if (it.uv.isNaN()) 0.0 else it.uv }
        val sunny = out.count { WeatherInfo.sky(it.code) == Sky.CLEAR || WeatherInfo.sky(it.code) == Sky.PARTLY } >= out.size / 2

        val items = mutableListOf<WearItem>()
        when {
            lo < 0 -> { items += WearItem("Winter coat", Wear.COAT); items += WearItem("Hat & gloves", Wear.GLOVES); items += WearItem("Thermal layers", Wear.LAYERS) }
            lo < 6 -> { items += WearItem("Winter coat", Wear.COAT); if (lo < 3 || gust >= 35) items += WearItem("Hat & gloves", Wear.GLOVES) }
            lo < 11 -> { items += WearItem("Warm sweater", Wear.SWEATER); items += WearItem("Light jacket", Wear.JACKET) }
            lo < 16 -> items += WearItem(if (hi >= 20) "Light jacket" else "Long sleeve", if (hi >= 20) Wear.JACKET else Wear.TOP)
            else -> items += WearItem("Light top", Wear.TOP)
        }
        if (hi - lo >= 8 && lo < 18) items += WearItem("Layers", Wear.LAYERS)
        if (hi >= 25) items += WearItem("Breathable shoes", Wear.SHOES)
        val wet = rainChance >= 50 || rainTotal >= 1.0
        when {
            snow -> items += WearItem("Snow boots", Wear.BOOTS)
            wet && rainTotal >= 5 -> { items += WearItem("Raincoat", Wear.RAIN); items += WearItem("Waterproof shoes", Wear.BOOTS) }
            wet && (exposed || gust >= 40) -> { items += WearItem("Rain jacket", Wear.RAIN); if (exposed) items += WearItem("Water-resistant shoes", Wear.SHOES) }
            wet && sheltered -> items += WearItem("Umbrella", Wear.UMBRELLA)
            wet -> items += WearItem("Umbrella", Wear.UMBRELLA)
            rainChance >= 30 -> items += WearItem("Waterproof hood", Wear.HOOD)
        }
        if (gust >= 40 && lo >= 6 && !wet) items += WearItem("Windbreaker", Wear.WIND)
        if (uv >= 4 || (sunny && hi >= 22)) items += WearItem("Sunglasses & SPF", Wear.SUN)

        val d = items.distinctBy { it.label }
        val real = out.mapNotNull { it.temp.takeIf { t -> !t.isNaN() } }
        val summary = buildString {
            append("High ${fmt.temp(real.maxOrNull() ?: Double.NaN)}, low ${fmt.temp(real.minOrNull() ?: Double.NaN)}.")
            Briefings.rainTiming(out, fmt)?.let { append(' ').append(it) }
            if (gust >= 40) append(" Gusts up to ${fmt.wind(gust)}.")
        }
        return WardrobeAdvice(if (tomorrow) "Tomorrow" else "Today", d, summary)
    }
}
