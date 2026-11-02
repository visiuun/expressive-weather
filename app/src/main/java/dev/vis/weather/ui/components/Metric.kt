package dev.vis.weather.ui.components

/** Every stat that has its own detail screen. */
enum class Metric(val title: String) {
    WIND("Wind"),
    HUMIDITY("Humidity"),
    UV("UV index"),
    FEELS("Feels like"),
    PRESSURE("Pressure"),
    VISIBILITY("Visibility"),
    PRECIP("Precipitation"),
    CLOUD("Cloud cover"),
    SUN("Sun"),
    MOON("Moon"),
    AIR("Air quality"),
    POLLEN("Pollen"),
}

/** Lets any tile open its detail screen without threading callbacks through every function. */
val LocalOpenMetric = androidx.compose.runtime.staticCompositionLocalOf<(Metric) -> Unit> { {} }
