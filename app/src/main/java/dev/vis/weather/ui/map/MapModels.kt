package dev.vis.weather.ui.map

import androidx.annotation.StringRes
import dev.vis.weather.L
import dev.vis.weather.R
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin

/** A labelled point drawn on the map (the current location, a saved place or a friend). */
data class MapMarker(
    val key: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val temp: Double?,
    val code: Int?,
    val isDay: Boolean,
    val kind: MarkerKind,
)

enum class MarkerKind { SELF, SAVED, FRIEND }

/** Weather overlays. Only one is shown at a time. */
enum class MapLayer(@StringRes private val titleRes: Int, @StringRes private val shortRes: Int) {
    NONE(R.string.map_layer_none_title, R.string.map_layer_none_short),
    RADAR(R.string.map_layer_radar_title, R.string.map_layer_radar_short),
    TEMPERATURE(R.string.map_layer_temperature_title, R.string.map_layer_temperature_short),
    WIND(R.string.map_layer_wind_title, R.string.map_layer_wind_short),
    CLOUDS(R.string.map_layer_clouds_title, R.string.map_layer_clouds_short),
    PRESSURE(R.string.map_layer_pressure_title, R.string.map_layer_pressure_short),
    PRECIPITATION(R.string.map_layer_precipitation_title, R.string.map_layer_precipitation_short),
    STORMS(R.string.map_layer_storms_title, R.string.map_layer_storms_short);

    val title: String get() = L.s(titleRes)
    val short: String get() = L.s(shortRes)

    /** Layers sampled from the Open-Meteo forecast grid. */
    val isModel: Boolean get() = this == TEMPERATURE || this == WIND || this == CLOUDS || this == PRESSURE || this == PRECIPITATION
}

/**
 * Spherical (Web) Mercator in normalised world units: x and y both run 0..1,
 * x from 180°W to 180°E and y from 85.0511°N (0) down to 85.0511°S (1).
 */
object Mercator {
    const val MAX_LAT = 85.05112878

    fun x(lon: Double): Double = (lon + 180.0) / 360.0

    fun y(lat: Double): Double {
        val l = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180.0
        val s = sin(l)
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }

    /** Longitude for a (possibly unwrapped) x; result is normalised to -180..180. */
    fun lon(x: Double): Double {
        val w = x - floor(x)
        return w * 360.0 - 180.0
    }

    fun lat(y: Double): Double {
        val n = PI * (1 - 2 * y.coerceIn(0.0, 1.0))
        return atan(0.5 * (exp(n) - exp(-n))) * 180.0 / PI
    }

    /** Wraps a world-x difference into -0.5..0.5 so the nearest copy of a point is used. */
    fun wrapDelta(dx: Double): Double = dx - floor(dx + 0.5)
}
