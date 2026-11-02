package dev.vis.weather.ui

import dev.vis.weather.data.PrecipUnit
import dev.vis.weather.data.PressureUnit
import dev.vis.weather.data.Settings
import dev.vis.weather.data.TempUnit
import dev.vis.weather.data.WindUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Unit conversion + formatting. All inputs are metric as returned by Open-Meteo. */
class Fmt(val s: Settings, val zone: ZoneId) {
    private val locale: Locale = Locale.getDefault()

    fun tempValue(c: Double): Double = if (s.tempUnit == TempUnit.F) c * 9 / 5 + 32 else c

    fun temp(c: Double): String = if (c.isNaN()) "–" else "${tempValue(c).roundToInt()}°"

    fun windValue(kmh: Double): Double = when (s.windUnit) {
        WindUnit.KMH -> kmh
        WindUnit.MS -> kmh / 3.6
        WindUnit.MPH -> kmh * 0.621371
        WindUnit.KN -> kmh * 0.539957
    }

    val windUnit: String
        get() = when (s.windUnit) {
            WindUnit.KMH -> "km/h"; WindUnit.MS -> "m/s"; WindUnit.MPH -> "mph"; WindUnit.KN -> "kn"
        }

    fun windNum(kmh: Double): String = if (kmh.isNaN()) "–" else windValue(kmh).roundToInt().toString()
    fun wind(kmh: Double): String = "${windNum(kmh)} $windUnit"

    fun precip(mm: Double): String = when {
        mm.isNaN() -> "–"
        s.precipUnit == PrecipUnit.IN -> String.format(locale, "%.2f in", mm / 25.4)
        mm > 0 && mm < 0.1 -> "<0.1 mm"
        else -> String.format(locale, "%.1f mm", mm)
    }

    fun pressureValue(hpa: Double): Double = when (s.pressureUnit) {
        PressureUnit.HPA -> hpa
        PressureUnit.INHG -> hpa * 0.0295300
        PressureUnit.MMHG -> hpa * 0.750062
    }

    val pressureUnit: String
        get() = when (s.pressureUnit) {
            PressureUnit.HPA -> "hPa"; PressureUnit.INHG -> "inHg"; PressureUnit.MMHG -> "mmHg"
        }

    fun pressureNum(hpa: Double): String = when {
        hpa.isNaN() -> "–"
        s.pressureUnit == PressureUnit.INHG -> String.format(locale, "%.2f", pressureValue(hpa))
        else -> pressureValue(hpa).roundToInt().toString()
    }

    private val imperial get() = s.tempUnit == TempUnit.F

    fun distanceNum(m: Double): String {
        if (m.isNaN()) return "–"
        val v = if (imperial) m / 1609.34 else m / 1000
        return if (v < 10) String.format(locale, "%.1f", v) else v.roundToInt().toString()
    }

    val distanceUnit get() = if (imperial) "mi" else "km"

    fun pct(v: Double): String = if (v.isNaN()) "–" else "${v.roundToInt()}%"

    private val timeFmt = DateTimeFormatter.ofPattern(if (s.use24h) "HH:mm" else "h:mm a", locale)
    private val hourFmt = DateTimeFormatter.ofPattern(if (s.use24h) "HH:mm" else "h a", locale)

    fun time(epoch: Long): String = if (epoch == 0L) "–" else timeFmt.format(Instant.ofEpochSecond(epoch).atZone(zone))
    fun hour(epoch: Long): String = hourFmt.format(Instant.ofEpochSecond(epoch).atZone(zone))

    fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    }

    fun shortDate(date: LocalDate): String = DateTimeFormatter.ofPattern("d MMM", locale).format(date)

    fun duration(seconds: Double): String {
        if (seconds.isNaN()) return "–"
        val total = (seconds / 60).roundToInt()
        return "${total / 60}h ${total % 60}m"
    }

    fun localDate(epoch: Long): LocalDate = Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()
}
