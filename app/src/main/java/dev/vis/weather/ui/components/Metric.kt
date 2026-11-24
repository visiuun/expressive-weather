package dev.vis.weather.ui.components

import androidx.annotation.StringRes
import dev.vis.weather.R
import dev.vis.weather.L

/** Every stat that has its own detail screen. */
enum class Metric(@StringRes private val res: Int) {
    WIND(R.string.m_metric_wind),
    HUMIDITY(R.string.m_metric_humidity),
    UV(R.string.m_metric_uv),
    FEELS(R.string.m_metric_feels),
    PRESSURE(R.string.m_metric_pressure),
    VISIBILITY(R.string.m_metric_visibility),
    PRECIP(R.string.m_metric_precip),
    CLOUD(R.string.m_metric_cloud),
    SUN(R.string.m_metric_sun),
    MOON(R.string.m_metric_moon),
    AIR(R.string.m_metric_air),
    POLLEN(R.string.m_metric_pollen),
    SEA(R.string.m_metric_sea),
    FIRE(R.string.m_metric_fire),
    ;

    val title: String get() = L.s(res)
}

/** Lets any tile open its detail screen without threading callbacks through every function. */
val LocalOpenMetric = androidx.compose.runtime.staticCompositionLocalOf<(Metric) -> Unit> { {} }
