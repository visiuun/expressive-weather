package dev.vis.weather.data

import dev.vis.weather.L
import dev.vis.weather.R
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
        !temp.isNaN() && temp < 20 && wetBulb < 18 -> Band(L.s(R.string.d_cool_normal), 0, BLUE, L.s(R.string.d_cool_normal_text))
        wetBulb < 21 -> Band(L.s(R.string.d_cool_good), 1, GREEN, L.s(R.string.d_cool_good_text))
        wetBulb < 24 -> Band(L.s(R.string.d_cool_reduced), 2, YELLOW, L.s(R.string.d_cool_reduced_text))
        wetBulb < 27 -> Band(L.s(R.string.d_cool_difficult), 3, ORANGE, L.s(R.string.d_cool_difficult_text))
        wetBulb < 31 -> Band(L.s(R.string.d_cool_very_difficult), 4, RED, L.s(R.string.d_cool_very_difficult_text))
        else -> Band(L.s(R.string.d_cool_critical), 5, MAROON, L.s(R.string.d_cool_critical_text))
    }

    val COOLING_EXPLAINER: String get() = L.s(R.string.d_cool_explainer)

    // ---- humidity comfort (dew point)

    /** Comfort from the dew point. [rh] (optional) keeps damp, cool air from being called "dry". */
    fun humidityComfort(dew: Double, rh: Double = Double.NaN): Band = when {
        dew.isNaN() -> Band("–", -1, TEAL, "")
        dew < 10 && (rh.isNaN() || rh < 55) -> Band(L.s(R.string.d_humid_dry), 0, BLUE, L.s(R.string.d_humid_dry_text))
        dew < 10 -> Band(L.s(R.string.d_humid_comfortable), 1, TEAL, L.s(R.string.d_humid_comfortable_damp_text))
        dew < 15 -> Band(L.s(R.string.d_humid_comfortable), 1, TEAL, L.s(R.string.d_humid_comfortable_text))
        dew < 18 -> Band(L.s(R.string.d_humid_noticeable), 2, GREEN, L.s(R.string.d_humid_noticeable_text))
        dew < 21 -> Band(L.s(R.string.d_humid_sticky), 3, YELLOW, L.s(R.string.d_humid_sticky_text))
        dew < 24 -> Band(L.s(R.string.d_humid_muggy), 4, ORANGE, L.s(R.string.d_humid_muggy_text))
        else -> Band(L.s(R.string.d_humid_oppressive), 5, RED, L.s(R.string.d_humid_oppressive_text))
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
        v < 15 -> Band(L.s(R.string.d_fire_low), 0, GREEN, L.s(R.string.d_fire_low_text))
        v < 30 -> Band(L.s(R.string.d_fire_moderate), 1, YELLOW, L.s(R.string.d_fire_moderate_text))
        v < 50 -> Band(L.s(R.string.d_fire_high), 2, ORANGE, L.s(R.string.d_fire_high_text))
        v < 75 -> Band(L.s(R.string.d_fire_very_high), 3, RED, L.s(R.string.d_fire_very_high_text))
        else -> Band(L.s(R.string.d_fire_extreme), 4, MAROON, L.s(R.string.d_fire_extreme_text))
    }

    val FIRE_EXPLAINER: String get() = L.s(R.string.d_fire_explainer)

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
            d <= -3.5 -> L.s(R.string.d_press_dropping_fast) to L.s(R.string.d_press_dropping_fast_text)
            d < -1.0 -> L.s(R.string.d_press_falling) to L.s(R.string.d_press_falling_text)
            d >= 3.5 -> L.s(R.string.d_press_rising_fast) to L.s(R.string.d_press_rising_fast_text)
            d > 1.0 -> L.s(R.string.d_press_rising) to L.s(R.string.d_press_rising_text)
            else -> L.s(R.string.d_press_steady) to L.s(R.string.d_press_steady_text)
        }
        val headache = when {
            d <= -3.5 || drop24 >= 8 -> L.s(R.string.d_press_headache_sharp)
            drop24 >= 5 -> L.s(R.string.d_press_headache_fall, drop24.toInt())
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
        Pollutant("PM2.5", "pm25", a.pm25, 15.0, L.s(R.string.d_pollutant_pm25_about)),
        Pollutant("PM10", "pm10", a.pm10, 45.0, L.s(R.string.d_pollutant_pm10_about)),
        Pollutant(L.s(R.string.d_pollutant_o3), "o3", a.o3, 100.0, L.s(R.string.d_pollutant_o3_about)),
        Pollutant("NO₂", "no2", a.no2, 25.0, L.s(R.string.d_pollutant_no2_about)),
        Pollutant("SO₂", "so2", a.so2, 40.0, L.s(R.string.d_pollutant_so2_about)),
        Pollutant("CO", "co", a.co, 4000.0, L.s(R.string.d_pollutant_co_about)),
    )

    /** WHO 2021 guideline comparison (24 h / 8 h guideline values). */
    fun whoStatus(v: Double, limit: Double): Band {
        if (v.isNaN()) return Band("–", -1, TEAL, "")
        val r = v / limit
        return when {
            r < 0.5 -> Band(L.s(R.string.d_who_well_below_limit), 0, GREEN, "")
            r < 1.0 -> Band(L.s(R.string.d_who_near_limit), 1, YELLOW, "")
            r < 2.0 -> Band(L.s(R.string.d_who_above_limit), 2, ORANGE, "")
            else -> Band(L.s(R.string.d_who_well_above_limit), 3, RED, "")
        }
    }

    fun euAqiBand(v: Double): Band = when {
        v.isNaN() -> Band("–", -1, TEAL, "")
        v <= 20 -> Band(L.s(R.string.d_euaqib_good), 0, 0xFF50F0E6, L.s(R.string.d_euaqib_good_text))
        v <= 40 -> Band(L.s(R.string.d_euaqib_fair), 1, 0xFF50CCAA, L.s(R.string.d_euaqib_fair_text))
        v <= 60 -> Band(L.s(R.string.d_euaqib_moderate), 2, 0xFFF0E641, L.s(R.string.d_euaqib_moderate_text))
        v <= 80 -> Band(L.s(R.string.d_euaqib_poor), 3, 0xFFFF5050, L.s(R.string.d_euaqib_poor_text))
        v <= 100 -> Band(L.s(R.string.d_euaqib_very_poor), 4, 0xFF960032, L.s(R.string.d_euaqib_very_poor_text))
        else -> Band(L.s(R.string.d_euaqib_extremely_poor), 5, 0xFF7D2181, L.s(R.string.d_euaqib_extremely_poor_text))
    }

    fun usAqiBand(v: Double): Band = when {
        v.isNaN() -> Band("–", -1, TEAL, "")
        v <= 50 -> Band(L.s(R.string.d_usaqib_good), 0, GREEN, L.s(R.string.d_usaqib_good_text))
        v <= 100 -> Band(L.s(R.string.d_usaqib_moderate), 1, YELLOW, L.s(R.string.d_usaqib_moderate_text))
        v <= 150 -> Band(L.s(R.string.d_usaqib_unhealthy_for_sensitive_groups), 2, ORANGE, L.s(R.string.d_usaqib_unhealthy_for_sensitive_groups_text))
        v <= 200 -> Band(L.s(R.string.d_usaqib_unhealthy), 3, RED, L.s(R.string.d_usaqib_unhealthy_text))
        v <= 300 -> Band(L.s(R.string.d_usaqib_very_unhealthy), 4, PURPLE, L.s(R.string.d_usaqib_very_unhealthy_text))
        else -> Band(L.s(R.string.d_usaqib_hazardous), 5, MAROON, L.s(R.string.d_usaqib_hazardous_text))
    }

    // ---- clouds & visibility

    fun cloudGuidance(cover: Double, f: Forecast): String {
        if (cover.isNaN()) return ""
        val next = f.upcoming.take(12).map { it.cloudCover }
        val later = next.drop(4).cleanAvg()
        val trend = when {
            later.isNaN() -> ""
            later < cover - 25 -> L.s(R.string.d_cloud_clearing)
            later > cover + 25 -> L.s(R.string.d_cloud_building)
            else -> ""
        }
        val base = when {
            cover < 12 -> if (f.current.isDay) L.s(R.string.d_cloud_full_sun) else L.s(R.string.d_cloud_clear_night)
            cover < 37 -> if (f.current.isDay) L.s(R.string.d_cloud_mostly_sunny) else L.s(R.string.d_cloud_mostly_clear_night)
            cover < 62 -> L.s(R.string.d_cloud_mixed)
            cover < 87 -> if (f.current.isDay) L.s(R.string.d_cloud_mostly_grey) else L.s(R.string.d_cloud_mostly_cloudy_night)
            else -> L.s(R.string.d_cloud_overcast)
        }
        return if (trend.isEmpty()) base else "$base $trend"
    }

    fun visibilityRating(m: Double): Band = when {
        m.isNaN() -> Band("–", -1, TEAL, "")
        m < 200 -> Band(L.s(R.string.d_visr_dense_fog), 5, RED, L.s(R.string.d_visr_dense_fog_text))
        m < 1000 -> Band(L.s(R.string.d_visr_fog), 4, ORANGE, L.s(R.string.d_visr_fog_text))
        m < 4000 -> Band(L.s(R.string.d_visr_poor), 3, YELLOW, L.s(R.string.d_visr_poor_text))
        m < 10000 -> Band(L.s(R.string.d_visr_moderate), 2, TEAL, L.s(R.string.d_visr_moderate_text))
        m < 20000 -> Band(L.s(R.string.d_visr_good), 1, GREEN, L.s(R.string.d_visr_good_text))
        else -> Band(L.s(R.string.d_visr_excellent), 0, BLUE, L.s(R.string.d_visr_excellent_text))
    }
}

/** Plain-language comparisons and summaries. */
object Insights {
    private fun date(f: Forecast, t: Long): LocalDate = Instant.ofEpochSecond(t).atZone(f.zone).toLocalDate()

    private fun pick(options: List<String>, seed: LocalDate): String = options[abs(seed.toEpochDay().toInt()) % options.size]

    /** [today]: compare against today (else yesterday). Each option list holds string resources. */
    private fun phrase(diff: Double, today: Boolean, seed: LocalDate): String {
        val warmer = diff > 0
        val a = abs(diff)
        // 0 = warmer, 1 = cooler, 2 = colder (cooler/colder alternate by day for variety).
        val w = if (warmer) 0 else 1 + abs(seed.dayOfYear) % 2
        val ids = when {
            a < 1.0 -> if (today) listOf(R.string.d_cmp_today_same_1, R.string.d_cmp_today_same_2, R.string.d_cmp_today_same_3) else listOf(R.string.d_cmp_yday_same_1, R.string.d_cmp_yday_same_2, R.string.d_cmp_yday_same_3)
            a < 3.0 -> if (today) when (w) {
                0 -> listOf(R.string.d_cmp_today_little_warmer_1, R.string.d_cmp_today_little_warmer_2, R.string.d_cmp_today_little_warmer_3)
                1 -> listOf(R.string.d_cmp_today_little_cooler_1, R.string.d_cmp_today_little_cooler_2, R.string.d_cmp_today_little_cooler_3)
                else -> listOf(R.string.d_cmp_today_little_colder_1, R.string.d_cmp_today_little_colder_2, R.string.d_cmp_today_little_colder_3)
            } else when (w) {
                0 -> listOf(R.string.d_cmp_yday_little_warmer_1, R.string.d_cmp_yday_little_warmer_2, R.string.d_cmp_yday_little_warmer_3)
                1 -> listOf(R.string.d_cmp_yday_little_cooler_1, R.string.d_cmp_yday_little_cooler_2, R.string.d_cmp_yday_little_cooler_3)
                else -> listOf(R.string.d_cmp_yday_little_colder_1, R.string.d_cmp_yday_little_colder_2, R.string.d_cmp_yday_little_colder_3)
            }
            a < 6.0 -> if (today) when (w) {
                0 -> listOf(R.string.d_cmp_today_few_warmer_1, R.string.d_cmp_today_few_warmer_2, R.string.d_cmp_today_few_warmer_3)
                1 -> listOf(R.string.d_cmp_today_few_cooler_1, R.string.d_cmp_today_few_cooler_2, R.string.d_cmp_today_few_cooler_3)
                else -> listOf(R.string.d_cmp_today_few_colder_1, R.string.d_cmp_today_few_colder_2, R.string.d_cmp_today_few_colder_3)
            } else when (w) {
                0 -> listOf(R.string.d_cmp_yday_few_warmer_1, R.string.d_cmp_yday_few_warmer_2, R.string.d_cmp_yday_few_warmer_3)
                1 -> listOf(R.string.d_cmp_yday_few_cooler_1, R.string.d_cmp_yday_few_cooler_2, R.string.d_cmp_yday_few_cooler_3)
                else -> listOf(R.string.d_cmp_yday_few_colder_1, R.string.d_cmp_yday_few_colder_2, R.string.d_cmp_yday_few_colder_3)
            }
            else -> if (today) when (w) {
                0 -> listOf(R.string.d_cmp_today_much_warmer_1, R.string.d_cmp_today_much_warmer_2, R.string.d_cmp_today_much_warmer_3)
                1 -> listOf(R.string.d_cmp_today_much_cooler_1, R.string.d_cmp_today_much_cooler_2, R.string.d_cmp_today_much_cooler_3)
                else -> listOf(R.string.d_cmp_today_much_colder_1, R.string.d_cmp_today_much_colder_2, R.string.d_cmp_today_much_colder_3)
            } else when (w) {
                0 -> listOf(R.string.d_cmp_yday_much_warmer_1, R.string.d_cmp_yday_much_warmer_2, R.string.d_cmp_yday_much_warmer_3)
                1 -> listOf(R.string.d_cmp_yday_much_cooler_1, R.string.d_cmp_yday_much_cooler_2, R.string.d_cmp_yday_much_cooler_3)
                else -> listOf(R.string.d_cmp_yday_much_colder_1, R.string.d_cmp_yday_much_colder_2, R.string.d_cmp_yday_much_colder_3)
            }
        }
        return pick(ids.map { L.s(it) }, seed)
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
        return phrase(t.max - y, false, t.date)
    }

    /** "Much colder than today." — tomorrow's high vs today's high. Null without data. */
    fun compareTomorrow(f: Forecast): String? {
        val t = f.today ?: return null
        val tm = f.daily.getOrNull(1) ?: return null
        if (t.max.isNaN() || tm.max.isNaN()) return null
        return phrase(tm.max - t.max, true, tm.date)
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
        /** L.s(R.string.d_prec_none_so_far), L.s(R.string.d_prec_fell_earlier), L.s(R.string.d_prec_falling_now). */
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
            falling -> if (WeatherInfo.sky(f.current.code) == Sky.SNOW) L.s(R.string.d_prec_snowing_now) else L.s(R.string.d_prec_falling_now)
            soFar >= 0.05 -> L.s(R.string.d_prec_fell_earlier)
            else -> L.s(R.string.d_prec_none_so_far)
        }
        val wetLater = fut.filter { it.precip.ok() && it.precip >= 0.1 }
        val laterText = when {
            wetLater.isEmpty() -> if (soFar >= 0.05 || falling) L.s(R.string.d_prec_drying_out) else L.s(R.string.d_prec_staying_dry)
            else -> {
                val h = Instant.ofEpochSecond(wetLater.first().time - 3600).atZone(f.zone).hour
                L.s(when (h) { in 0..5 -> R.string.d_prec_more_overnight; in 6..11 -> R.string.d_prec_more_morning; in 12..17 -> R.string.d_prec_more_afternoon; else -> R.string.d_prec_more_evening })
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
            types.iceMm >= 0.2 -> L.s(R.string.d_precg_freezing)
            types.snowCm >= 2 -> L.s(R.string.d_precg_snow)
            amount >= 10 -> L.s(R.string.d_precg_heavy)
            amount >= 2 || (prob.ok() && prob >= 60) -> L.s(R.string.d_precg_umbrella)
            prob.ok() && prob >= 30 -> L.s(R.string.d_precg_shower)
            else -> L.s(R.string.d_precg_none)
        }
    }

    // ---- humidity through the day

    /** e.g. "Sticky afternoon" + sentence, from the stickiest part of [date]. */
    fun humidityDay(f: Forecast, date: LocalDate): Pair<String, String>? {
        val hours = f.hourlyAll.filter { date(f, it.time) == date && it.dewPoint.ok() }
        if (hours.isEmpty()) return null
        // Internal keys, not shown.
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
            band.level <= 0 -> L.s(R.string.d_humday_dry) to L.s(R.string.d_humday_dry_text)
            band.level <= 1 -> L.s(R.string.d_humday_comfortable) to L.s(R.string.d_humday_comfortable_text)
            best.level == band.level -> L.s(R.string.d_humday_all_day, band.label) to band.text
            else -> L.s(
                when (worst) {
                    "morning" -> R.string.d_humday_morning
                    "afternoon" -> R.string.d_humday_afternoon
                    "evening" -> R.string.d_humday_evening
                    else -> R.string.d_humday_night
                },
                band.label,
            ) to L.s(R.string.d_humday_other_times, band.text, best.label)
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
            score >= 75 -> L.s(R.string.d_star_excellent) to 0
            score >= 55 -> L.s(R.string.d_star_good) to 1
            score >= 35 -> L.s(R.string.d_star_fair) to 2
            else -> L.s(R.string.d_star_poor) to 3
        }
        // Best hour: least cloud, preferring the moon below the horizon.
        val best = hours.minByOrNull { h ->
            val alt = samples.firstOrNull { it.first == h.time }?.second ?: 0.0
            (if (h.cloudCover.ok()) h.cloudCover else 50.0) + (if (alt > 0) moon.illumination * 30 else 0.0)
        }?.time ?: 0L
        val moonBright = moon.illumination * moonUp
        // 0 = clear, 1 = mostly clear, 2 = patchy cloud (thicker cloud is handled by its own sentence).
        val sky = when {
            c < 15 -> 0
            c < 40 -> 1
            else -> 2
        }
        val sentence = L.s(when {
            fog -> R.string.d_star_fog
            c >= 70 -> R.string.d_star_cloudy
            moonBright < 0.2 && c < 40 -> if (sky == 0) R.string.d_star_clear_dark else R.string.d_star_mostly_clear_dark
            moonBright < 0.2 -> R.string.d_star_patchy_dark
            moon.illumination > 0.7 && moonUp > 0.5 -> intArrayOf(R.string.d_star_clear_bright, R.string.d_star_mostly_clear_bright, R.string.d_star_patchy_bright)[sky]
            moonUp < 0.5 -> intArrayOf(R.string.d_star_clear_partmoon, R.string.d_star_mostly_clear_partmoon, R.string.d_star_patchy_partmoon)[sky]
            else -> intArrayOf(R.string.d_star_clear_some, R.string.d_star_mostly_clear_some, R.string.d_star_patchy_some)[sky]
        })
        return Stargazing(rating, level, score, cloud, moon.illumination, moonUp, start, end, best, sentence)
    }
}
