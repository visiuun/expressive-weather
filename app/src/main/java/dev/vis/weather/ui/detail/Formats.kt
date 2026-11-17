package dev.vis.weather.ui.detail

import dev.vis.weather.data.DistanceUnit
import dev.vis.weather.data.PrecipUnit
import dev.vis.weather.ui.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Formatting helpers for the detail screens, kept as extensions so Fmt.kt stays small.
 * Inputs are metric (m, cm, km/h, km).
 */

private val loc: Locale get() = Locale.getDefault()
private val Fmt.imperial get() = s.distanceUnit == DistanceUnit.MI

/** Wave / sea-level height: metres, or feet with miles. */
fun Fmt.waveValue(m: Double): Double = if (imperial) m * 3.28084 else m
val Fmt.waveUnit: String get() = if (imperial) "ft" else "m"
fun Fmt.waveNum(m: Double): String = when {
    m.isNaN() -> "–"
    imperial -> waveValue(m).let { if (abs(it) < 10) String.format(loc, "%.1f", it) else it.roundToInt().toString() }
    else -> String.format(loc, "%.1f", m)
}
fun Fmt.wave(m: Double): String = "${waveNum(m)} $waveUnit"

/** Signed tide height, e.g. "+1.2 m". */
fun Fmt.tide(m: Double): String = if (m.isNaN()) "–" else (if (m >= 0) "+" else "−") + waveNum(abs(m)) + " " + waveUnit

/** Fresh snow (cm, or inches when precipitation is in inches). */
fun Fmt.snow(cm: Double): String = when {
    cm.isNaN() -> "–"
    s.precipUnit == PrecipUnit.IN -> String.format(loc, "%.1f in", cm / 2.54)
    cm > 0 && cm < 0.1 -> "<0.1 cm"
    cm < 10 -> String.format(loc, "%.1f cm", cm)
    else -> "${cm.roundToInt()} cm"
}

/** Height above sea level (m or ft). */
fun Fmt.altitude(m: Double): String = when {
    m.isNaN() -> "–"
    imperial -> "${(m * 3.28084 / 10).roundToInt() * 10} ft"
    else -> "${(m / 10).roundToInt() * 10} m"
}

/** Distance from kilometres. */
fun Fmt.km(km: Double): String = "${distanceNum(km * 1000)} $distanceUnit"

/** "4 min 12 s" style change, signed. */
fun signedMinutes(seconds: Double): String {
    if (seconds.isNaN()) return "–"
    val sign = if (seconds >= 0) "+" else "−"
    val a = abs(seconds).roundToInt()
    val m = a / 60
    val s = a % 60
    return when {
        m == 0 -> "$sign$s s"
        m >= 60 -> "$sign${m / 60} h ${m % 60} min"
        else -> "$sign$m min $s s"
    }
}

fun Fmt.weekday(epoch: Long): String = Instant.ofEpochSecond(epoch).atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, loc)

fun Fmt.longDate(date: LocalDate): String = DateTimeFormatter.ofPattern("EEE d MMM", loc).format(date)

fun Fmt.dateOf(epoch: Long): String = longDate(localDate(epoch))

/** "Today 14:20", "Tomorrow 03:10" or "Thu 14:20". */
fun Fmt.whenText(epoch: Long, now: Long): String {
    if (epoch == 0L) return "–"
    val d = localDate(epoch)
    val today = localDate(now)
    val day = when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> weekday(epoch)
    }
    return "$day ${time(epoch)}"
}

/** Relative day count: "in 3 days", "tomorrow", "today". */
fun Fmt.inDays(epoch: Long, now: Long): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(localDate(now), localDate(epoch))
    return when (days) {
        0L -> "today"
        1L -> "tomorrow"
        else -> "in $days days"
    }
}

/** Compass bearing with degrees: "SW 236°". */
fun bearing(deg: Double): String = if (deg.isNaN()) "–" else "${dev.vis.weather.data.WeatherInfo.compass(deg)} ${deg.roundToInt()}°"

fun pctOf(v: Double): String = if (v.isNaN()) "–" else "${(v * 100).roundToInt()}%"

fun Fmt.tempDelta(c: Double): String {
    if (c.isNaN()) return "–"
    val v = if (s.tempUnit == dev.vis.weather.data.TempUnit.F) c * 9 / 5 else c
    val r = (v * 10).roundToInt() / 10.0
    return (if (r >= 0) "+" else "−") + String.format(loc, "%.1f", abs(r)) + "°"
}
