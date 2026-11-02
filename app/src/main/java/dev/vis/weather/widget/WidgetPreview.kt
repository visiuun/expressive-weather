package dev.vis.weather.widget

import dev.vis.weather.data.AirQuality
import dev.vis.weather.data.Current
import dev.vis.weather.data.Day
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.Hour
import dev.vis.weather.data.Minute
import dev.vis.weather.data.Place
import dev.vis.weather.data.Settings
import dev.vis.weather.ui.UiState
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

/** Made-up but plausible data for the launcher's widget picker previews (Android 15+). */
object WidgetPreview {
    fun model(): WidgetModel {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis() / 1000 / 3600 * 3600
        val today = LocalDate.now(zone)
        val codes = listOf(1, 2, 2, 3, 61, 80, 3, 2, 1, 0, 0, 1, 2, 3, 61, 63)
        val hours = (-24 until 48).map { i ->
            val t = now + i * 3600L
            val hourOfDay = java.time.Instant.ofEpochSecond(t).atZone(zone).hour
            val temp = 14 + 6 * sin((hourOfDay - 9) / 24.0 * 2 * PI)
            val prob = if (i in 5..9) 40.0 + i * 5 else 5.0
            Hour(
                t, temp, temp - 1.5, 62.0, 7.0, prob, if (prob > 50) 0.6 else 0.0,
                if (prob > 50) 61 else codes[(i + 24) % codes.size].coerceAtMost(3), hourOfDay in 7..18,
                12.0, 240.0, 24.0, 3.0, 1016.0, 24000.0, 40.0,
            )
        }
        val days = (0 until 16).map { i ->
            val date = today.plusDays(i.toLong())
            val sunrise = date.atTime(7, 2).atZone(zone).toEpochSecond()
            val sunset = date.atTime(18, 21).atZone(zone).toEpochSecond()
            Day(
                date, codes[i], 18.0 + (i % 5) - 2, 8.0 + (i % 3), 17.0, 7.0, sunrise, sunset,
                (sunset - sunrise).toDouble(), 20000.0, 4.0, if (codes[i] >= 61) 3.2 else 0.0, 2.0,
                if (codes[i] >= 61) 70.0 else 10.0, 22.0, 38.0, 240.0,
            )
        }
        val current = Current(now, 16.0, 15.0, 62.0, 7.0, 0.0, 2, true, 40.0, 1016.0, 12.0, 240.0, 24.0, 24000.0, 3.0)
        val forecast = Forecast(
            zone = zone, latitude = 48.2, longitude = 16.4, elevation = 180.0, current = current,
            hourlyAll = hours, daily = days, minutely = (0 until 12).map { Minute(now + it * 900L, 0.0) },
            air = AirQuality(32.0, 42.0, 9.0, 16.0, 210.0, 12.0, 2.0, 60.0, listOf("Grass" to 24.0, "Birch" to 3.0)),
            fetchedAt = System.currentTimeMillis(),
        )
        return WidgetModel(UiState.CURRENT, Place("Vienna", "Austria", 48.2, 16.4), forecast, Settings(), pinned = false)
    }
}
