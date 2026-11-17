package dev.vis.weather.ui.map

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
enum class MapLayer(val title: String, val short: String) {
    NONE("Map", "Map"),
    RADAR("Radar", "Radar"),
    TEMPERATURE("Temperature", "Temp"),
    WIND("Wind", "Wind"),
    CLOUDS("Cloud cover", "Clouds"),
    PRESSURE("Pressure", "Pressure"),
    PRECIPITATION("Precipitation", "Rain"),
    STORMS("Tropical storms", "Storms");

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
