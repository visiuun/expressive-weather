package dev.vis.weather.data

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class Place(
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
) {
    val key: String get() = String.format(Locale.US, "%.3f,%.3f", lat, lon)
}

data class Current(
    val time: Long,
    val temp: Double,
    val feelsLike: Double,
    val humidity: Double,
    val dewPoint: Double,
    val precipitation: Double,
    val code: Int,
    val isDay: Boolean,
    val cloudCover: Double,
    val pressure: Double,
    val windSpeed: Double,
    val windDir: Double,
    val windGust: Double,
    val visibility: Double,
    val uv: Double,
    /** Liquid rain in the last interval (mm), excluding [showers]. */
    val rain: Double = Double.NaN,
    /** Convective showers (mm). */
    val showers: Double = Double.NaN,
    /** Snowfall in cm (fresh snow, not water equivalent). */
    val snowfall: Double = Double.NaN,
)

data class Hour(
    val time: Long,
    val temp: Double,
    val feelsLike: Double,
    val humidity: Double,
    val dewPoint: Double,
    val precipProb: Double,
    val precip: Double,
    val code: Int,
    val isDay: Boolean,
    val windSpeed: Double,
    val windDir: Double,
    val windGust: Double,
    val uv: Double,
    val pressure: Double,
    val visibility: Double,
    val cloudCover: Double,
    /** mm of rain from large-scale systems. */
    val rain: Double = Double.NaN,
    /** mm of convective showers. */
    val showers: Double = Double.NaN,
    /** cm of fresh snow. Water equivalent ≈ cm × 10 / 7 mm. */
    val snowfall: Double = Double.NaN,
    /** Snow on the ground, metres. */
    val snowDepth: Double = Double.NaN,
    /** Wet-bulb temperature, °C. */
    val wetBulb: Double = Double.NaN,
    /** Height of the 0 °C level above sea level, metres. */
    val freezingLevel: Double = Double.NaN,
    /** Global horizontal (shortwave) radiation, W/m². */
    val radiation: Double = Double.NaN,
)

data class Day(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val feelsMax: Double,
    val feelsMin: Double,
    val sunrise: Long,
    val sunset: Long,
    val daylight: Double,
    val sunshine: Double,
    val uvMax: Double,
    val precipSum: Double,
    val precipHours: Double,
    val precipProbMax: Double,
    val windMax: Double,
    val gustMax: Double,
    val windDir: Double,
    val rainSum: Double = Double.NaN,
    val showersSum: Double = Double.NaN,
    /** cm of fresh snow over the day. */
    val snowfallSum: Double = Double.NaN,
)

data class Minute(val time: Long, val precip: Double)

data class AirQuality(
    val euAqi: Double,
    val usAqi: Double,
    val pm25: Double,
    val pm10: Double,
    val co: Double,
    val no2: Double,
    val so2: Double,
    val o3: Double,
    val pollen: List<Pair<String, Double>>,
    val hourly: List<AirHour> = emptyList(),
)

/** Hourly air-quality forecast. [pollen] holds alder, birch, grass, mugwort, olive, ragweed in that order. */
data class AirHour(
    val time: Long,
    val usAqi: Double,
    val euAqi: Double,
    val pm25: Double,
    val pm10: Double,
    val o3: Double,
    val pollen: List<Double>,
    val no2: Double = Double.NaN,
    val so2: Double = Double.NaN,
    val co: Double = Double.NaN,
)

data class Forecast(
    val zone: ZoneId,
    val latitude: Double,
    val longitude: Double = Double.NaN,
    val elevation: Double,
    val current: Current,
    /** All hourly entries returned (includes the past day, used for trends). */
    val hourlyAll: List<Hour>,
    val daily: List<Day>,
    val minutely: List<Minute>,
    val air: AirQuality?,
    val fetchedAt: Long,
    /** Open-Meteo model id the forecast came from ("best_match" = automatic). */
    val model: String = ForecastModels.AUTO,
    /** True when the chosen model had gaps (variables or days) that were filled from the automatic blend. */
    val modelFilled: Boolean = false,
    /** Yesterday's daily summary (from past_days=1), used for "warmer than yesterday" comparisons. */
    val yesterday: Day? = null,
    /** Sea conditions; null inland. */
    val marine: Marine? = null,
    /** Nearby station observation used to correct the first hours (local correction), or null. */
    val observation: Observation? = null,
) {
    /** Next 48 hours starting from the current hour. */
    val upcoming: List<Hour> by lazy {
        val start = current.time - 3600
        hourlyAll.filter { it.time > start }.take(48)
    }

    val today: Day? get() = daily.firstOrNull()
}
