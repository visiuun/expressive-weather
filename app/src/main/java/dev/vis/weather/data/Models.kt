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
data class AirHour(val time: Long, val usAqi: Double, val euAqi: Double, val pm25: Double, val pm10: Double, val o3: Double, val pollen: List<Double>)

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
) {
    /** Next 48 hours starting from the current hour. */
    val upcoming: List<Hour> by lazy {
        val start = current.time - 3600
        hourlyAll.filter { it.time > start }.take(48)
    }

    val today: Day? get() = daily.firstOrNull()
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class TempUnit { C, F }
enum class WindUnit { KMH, MS, MPH, KN }
enum class PrecipUnit { MM, IN }
enum class PressureUnit { HPA, INHG, MMHG }
enum class CardStyle { FILLED, OUTLINED, NONE }

/** Launcher icon variants, each backed by an <activity-alias> in the manifest. */
enum class AppIcon(val alias: String) { OLED("LauncherOled"), LIGHT("LauncherLight"), COLOR("LauncherColor") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val oled: Boolean = false,
    val dynamicColor: Boolean = true,
    val tempUnit: TempUnit = TempUnit.C,
    val windUnit: WindUnit = WindUnit.KMH,
    val precipUnit: PrecipUnit = PrecipUnit.MM,
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val use24h: Boolean = true,
    val heroBackground: Boolean = false,
    val cardStyle: CardStyle = CardStyle.FILLED,
    val monoIcons: Boolean = false,
    val appIcon: AppIcon = AppIcon.OLED,
    val autoUpdate: Boolean = true,
)
