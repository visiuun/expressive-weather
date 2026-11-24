package dev.vis.weather.alerts

import dev.vis.weather.inSentence
import dev.vis.weather.L
import dev.vis.weather.R
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
    fun compareYesterday(now: Double, before: Double): String {
        if (!now.ok() || !before.ok()) return ""
        val d = now - before
        val pick = (abs(now * 10).toInt()) % 3
        val warmer = d > 0
        val options = when {
            abs(d) < 1.5 -> listOf(R.string.al_cmp_same_1, R.string.al_cmp_same_2, R.string.al_cmp_same_3)
            abs(d) < 3.5 -> if (warmer) listOf(R.string.al_cmp_warmer_slight_1, R.string.al_cmp_warmer_slight_2, R.string.al_cmp_warmer_slight_3)
            else listOf(R.string.al_cmp_cooler_slight_1, R.string.al_cmp_cooler_slight_2, R.string.al_cmp_cooler_slight_3)
            abs(d) < 7 -> if (warmer) listOf(R.string.al_cmp_warmer_1, R.string.al_cmp_warmer_2, R.string.al_cmp_warmer_3)
            else listOf(R.string.al_cmp_cooler_1, R.string.al_cmp_cooler_2, R.string.al_cmp_cooler_3)
            else -> if (warmer) listOf(R.string.al_cmp_warmer_big_1, R.string.al_cmp_warmer_big_2, R.string.al_cmp_warmer_big_3)
            else listOf(R.string.al_cmp_colder_big_1, R.string.al_cmp_colder_big_2, R.string.al_cmp_colder_big_3)
        }
        return L.s(options[pick])
    }

    private fun conditionPhrase(day: Day, hours: List<Hour>): String {
        val sky = WeatherInfo.sky(day.code)
        val gust = hours.maxOfOrNull { if (it.windGust.ok()) it.windGust else 0.0 } ?: day.gustMax
        // A full sentence, including the final full stop.
        return L.s(
            when {
                sky == Sky.THUNDER -> R.string.al_cond_thunder
                sky == Sky.SNOW -> R.string.al_cond_snow
                sky == Sky.FREEZING -> R.string.al_cond_freezing
                sky == Sky.RAIN && day.precipSum >= 10 -> R.string.al_cond_heavy_rain
                sky == Sky.RAIN -> R.string.al_cond_rain
                sky == Sky.DRIZZLE -> R.string.al_cond_drizzle
                sky == Sky.FOG -> R.string.al_cond_fog
                gust >= 55 -> R.string.al_cond_windy
                sky == Sky.CLOUDY -> R.string.al_cond_cloudy
                sky == Sky.PARTLY -> R.string.al_cond_partly
                sky == Sky.CLEAR -> R.string.al_cond_clear
                else -> R.string.al_cond_mixed
            }
        )
    }

    /** First hour with a meaningful chance of precipitation, as "Rain likely from 15:00 (70%)". */
    fun rainTiming(hours: List<Hour>, fmt: Fmt, minChance: Double = 50.0): String? {
        val wet = hours.firstOrNull { it.precipProb.ok() && it.precipProb >= minChance && (it.precip.ok() && it.precip >= 0.1 || !it.precip.ok()) }
            ?: return null
        val sky = WeatherInfo.sky(wet.code)
        val idx = hours.indexOf(wet)
        val until = hours.drop(idx).firstOrNull { it.precipProb.ok() && it.precipProb < minChance * 0.6 }
        val pct = wet.precipProb.roundToInt()
        return if (until != null && until.time - wet.time <= 4 * 3600) {
            val res = when (sky) {
                Sky.SNOW -> R.string.al_timing_snow_until
                Sky.THUNDER -> R.string.al_timing_thunder_until
                Sky.DRIZZLE -> R.string.al_timing_drizzle_until
                else -> R.string.al_timing_rain_until
            }
            L.s(res, fmt.hour(wet.time), fmt.hour(until.time), pct)
        } else {
            val res = when (sky) {
                Sky.SNOW -> R.string.al_timing_snow
                Sky.THUNDER -> R.string.al_timing_thunder
                Sky.DRIZZLE -> R.string.al_timing_drizzle
                else -> R.string.al_timing_rain
            }
            L.s(res, fmt.hour(wet.time), pct)
        }
    }

    data class Brief(val title: String, val text: String, val big: String)

    fun today(f: Forecast, fmt: Fmt, placeName: String): Brief? {
        val day = f.today ?: return null
        val hours = hoursOf(f, day.date).filter { it.time >= f.current.time - 3600 }
        val cond = WeatherInfo.describe(day.code)
        val parts = mutableListOf<String>()
        parts += conditionPhrase(day, hours)
        rainTiming(hours, fmt)?.let { parts += it }
        val gust = hours.maxOfOrNull { if (it.windGust.ok()) it.windGust else 0.0 } ?: 0.0
        if (gust >= 45) parts += L.s(R.string.al_brief_gusts, fmt.wind(gust))
        if (day.uvMax.ok() && day.uvMax >= 6) parts += L.s(R.string.al_brief_uv, day.uvMax.roundToInt(), WeatherInfo.uvLevel(day.uvMax).inSentence())
        yesterday(f)?.let { (yMax, _) -> compareYesterday(day.max, yMax).takeIf { it.isNotBlank() }?.let { parts += it } }
        val title = L.s(R.string.al_brief_today_title, cond.lowercase().replaceFirstChar { it.titlecase() }, fmt.temp(day.max), fmt.temp(day.min))
        val text = parts.take(2).joinToString(" ")
        return Brief(title, text, parts.joinToString(" "))
    }

    fun tomorrow(f: Forecast, fmt: Fmt): Brief? {
        val today = f.today ?: return null
        val day = f.daily.getOrNull(1) ?: return null
        val hours = hoursOf(f, day.date)
        val cond = conditionPhrase(day, hours)
        val d = day.max - today.max
        // A full sentence, including the final full stop.
        val temp = L.s(
            when {
                !d.ok() -> R.string.al_tmr_unknown
                d >= 6 -> R.string.al_tmr_much_warmer
                d >= 2 -> R.string.al_tmr_warmer
                d <= -6 -> R.string.al_tmr_much_colder
                d <= -2 -> R.string.al_tmr_colder
                else -> R.string.al_tmr_similar
            }
        )
        val sky = WeatherInfo.sky(day.code)
        val gust = hours.maxOfOrNull { if (it.windGust.ok()) it.windGust else 0.0 } ?: 0.0
        val highlight = when {
            sky == Sky.SNOW -> L.s(R.string.al_tmr_snow, fmt.temp(day.min))
            sky == Sky.RAIN && day.precipSum >= 10 -> L.s(R.string.al_tmr_umbrella_must)
            sky == Sky.RAIN || sky == Sky.DRIZZLE || sky == Sky.THUNDER -> L.s(R.string.al_tmr_umbrella_maybe)
            gust >= 50 -> L.s(R.string.al_tmr_gusty)
            else -> L.s(R.string.al_tmr_highs, fmt.temp(day.max), fmt.temp(day.min))
        }
        val rain = rainTiming(hours, fmt)
        val title = L.s(R.string.al_brief_tomorrow_title, WeatherInfo.describe(day.code).lowercase().replaceFirstChar { it.titlecase() }, fmt.temp(day.max), fmt.temp(day.min))
        val text = "$cond $temp"
        return Brief(title, "$text $highlight", listOfNotNull(cond, temp, rain, highlight).joinToString(" "))
    }

    // ---- derived values

    /** Stull (2011) wet-bulb temperature from air temperature (°C) and relative humidity (%). */
    fun wetBulb(t: Double, rh: Double): Double {
        if (!t.ok() || !rh.ok()) return Double.NaN
        return t * atan(0.151977 * sqrt(rh + 8.313659)) + atan(t + rh) - atan(rh - 1.676331) +
            0.00391838 * rh.pow(1.5) * atan(0.023101 * rh) - 4.686035
    }

    fun coolingLevel(wb: Double): Pair<String, String> {
        val (label, text) = when {
            wb >= 31 -> R.string.al_cool_critical to R.string.al_cool_critical_text
            wb >= 28 -> R.string.al_cool_very_difficult to R.string.al_cool_very_difficult_text
            wb >= 26 -> R.string.al_cool_difficult to R.string.al_cool_difficult_text
            wb >= 24 -> R.string.al_cool_reduced to R.string.al_cool_reduced_text
            wb >= 20 -> R.string.al_cool_good to R.string.al_cool_good_text
            else -> R.string.al_cool_normal to R.string.al_cool_normal_text
        }
        return L.s(label) to L.s(text)
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

    val eaqiNames: List<String>
        get() = listOf("") + listOf(
            R.string.al_eaqi_good, R.string.al_eaqi_fair, R.string.al_eaqi_moderate,
            R.string.al_eaqi_poor, R.string.al_eaqi_very_poor, R.string.al_eaqi_extremely_poor,
        ).map { L.s(it) }
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
            lo < 0 -> { items += WearItem(L.s(R.string.al_wear_winter_coat), Wear.COAT); items += WearItem(L.s(R.string.al_wear_hat_gloves), Wear.GLOVES); items += WearItem(L.s(R.string.al_wear_thermal_layers), Wear.LAYERS) }
            lo < 6 -> { items += WearItem(L.s(R.string.al_wear_winter_coat), Wear.COAT); if (lo < 3 || gust >= 35) items += WearItem(L.s(R.string.al_wear_hat_gloves), Wear.GLOVES) }
            lo < 11 -> { items += WearItem(L.s(R.string.al_wear_warm_sweater), Wear.SWEATER); items += WearItem(L.s(R.string.al_wear_light_jacket), Wear.JACKET) }
            lo < 16 -> items += WearItem(L.s(if (hi >= 20) R.string.al_wear_light_jacket else R.string.al_wear_long_sleeve), if (hi >= 20) Wear.JACKET else Wear.TOP)
            else -> items += WearItem(L.s(R.string.al_wear_light_top), Wear.TOP)
        }
        if (hi - lo >= 8 && lo < 18) items += WearItem(L.s(R.string.al_wear_layers), Wear.LAYERS)
        if (hi >= 25) items += WearItem(L.s(R.string.al_wear_breathable_shoes), Wear.SHOES)
        val wet = rainChance >= 50 || rainTotal >= 1.0
        when {
            snow -> items += WearItem(L.s(R.string.al_wear_snow_boots), Wear.BOOTS)
            wet && rainTotal >= 5 -> { items += WearItem(L.s(R.string.al_wear_raincoat), Wear.RAIN); items += WearItem(L.s(R.string.al_wear_waterproof_shoes), Wear.BOOTS) }
            wet && (exposed || gust >= 40) -> { items += WearItem(L.s(R.string.al_wear_rain_jacket), Wear.RAIN); if (exposed) items += WearItem(L.s(R.string.al_wear_water_resistant_shoes), Wear.SHOES) }
            wet && sheltered -> items += WearItem(L.s(R.string.al_wear_umbrella), Wear.UMBRELLA)
            wet -> items += WearItem(L.s(R.string.al_wear_umbrella), Wear.UMBRELLA)
            rainChance >= 30 -> items += WearItem(L.s(R.string.al_wear_waterproof_hood), Wear.HOOD)
        }
        if (gust >= 40 && lo >= 6 && !wet) items += WearItem(L.s(R.string.al_wear_windbreaker), Wear.WIND)
        if (uv >= 4 || (sunny && hi >= 22)) items += WearItem(L.s(R.string.al_wear_sun), Wear.SUN)

        val d = items.distinctBy { it.label }
        val real = out.mapNotNull { it.temp.takeIf { t -> !t.isNaN() } }
        val summary = buildString {
            append(L.s(R.string.al_wear_summary, fmt.temp(real.maxOrNull() ?: Double.NaN), fmt.temp(real.minOrNull() ?: Double.NaN)))
            Briefings.rainTiming(out, fmt)?.let { append(' ').append(it) }
            if (gust >= 40) append(' ').append(L.s(R.string.al_brief_gusts, fmt.wind(gust)))
        }
        return WardrobeAdvice(L.s(if (tomorrow) R.string.al_day_tomorrow else R.string.al_day_today), d, summary)
    }
}
