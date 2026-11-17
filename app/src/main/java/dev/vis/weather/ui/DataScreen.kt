@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.ForecastModels
import dev.vis.weather.data.Observations
import dev.vis.weather.data.Settings
import dev.vis.weather.data.Updater
import dev.vis.weather.ui.components.SettingsAction
import dev.vis.weather.ui.components.SettingsGroup
import dev.vis.weather.ui.components.SettingsInfo
import dev.vis.weather.ui.components.SettingsSwitch
import dev.vis.weather.ui.components.SubScreen

/** A provider in the data-sources list. */
private class Source(
    val name: String,
    val use: String,
    val licence: String,
    val url: String,
    val icon: ImageVector,
    val shape: RoundedPolygon,
)

/** Settings → Data: forecast model, local correction and the full list of data providers. */
@Composable
fun DataScreen(settings: Settings, update: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { runCatching { uri.openUri(it) } }
    val selected = ForecastModels.byId(settings.forecastModel)

    SubScreen("Data", onBack) { w ->
        item(key = "intro") {
            SettingsGroup(null, w) {
                SettingsInfo(
                    "Where your forecast comes from",
                    "Expressive Weather uses free, open data from national weather services. No account, no API key, no tracking: " +
                        "only the location you're viewing is sent to the services below.",
                    0, 1, Icons.Rounded.Source,
                )
            }
        }

        item(key = "model") {
            val models = ForecastModels.all
            SettingsGroup("Forecast model", w) {
                models.forEachIndexed { i, m ->
                    val isSel = m.id == selected.id
                    SettingsAction(
                        title = m.name,
                        subtitle = if (m.id == ForecastModels.AUTO) m.detail else "${m.provider}\n${m.region} · ${m.detail}",
                        icon = if (m.id == ForecastModels.AUTO) Icons.Rounded.AutoAwesome else Icons.Rounded.Public,
                        shape = if (m.id == ForecastModels.AUTO) MaterialShapes.Sunny else SHAPES[i % SHAPES.size],
                        index = i,
                        count = models.size,
                        trailing = { RadioButton(selected = isSel, onClick = null) },
                        onClick = { update { it.copy(forecastModel = m.id) } },
                    )
                }
            }
        }

        item(key = "modelnote") {
            SettingsGroup(null, w) {
                SettingsInfo(
                    "About models",
                    "Auto picks the sharpest model for your area — for example ICON-D2 in Germany, AROME in France, HRRR in the US or " +
                        "Harmonie in the Netherlands — and blends in ECMWF and GFS for later days. Picking a model yourself is great " +
                        "for comparing; when it has no data for something (UV, visibility, rain chance or later days), the Auto " +
                        "forecast fills the gap.",
                    0, 1,
                )
            }
        }

        item(key = "accuracy") {
            SettingsGroup("Accuracy", w) {
                SettingsSwitch(
                    "Local correction",
                    "Compares the forecast with the nearest airport weather station (METAR, within ${Observations.RADIUS_KM.toInt()} km) and " +
                        "nudges the next 6 hours toward what's actually measured. Privacy: only a rough area around you " +
                        "(a ~70 km box, rounded) is sent to NOAA's aviationweather.gov — never your exact location.",
                    Icons.Rounded.Verified, MaterialShapes.Cookie9Sided, 0, 2, settings.localCorrection,
                ) { v -> update { it.copy(localCorrection = v) } }
                SettingsSwitch(
                    "Show data source",
                    "Show which model and station were used at the bottom of the forecast.",
                    Icons.Rounded.Hub, MaterialShapes.Gem, 1, 2, settings.showDataSource,
                ) { v -> update { it.copy(showDataSource = v) } }
            }
        }

        item(key = "sources") {
            val sources = buildList {
                add(Source("Open-Meteo", "Forecasts, air quality (Copernicus CAMS), marine and place search", "CC BY 4.0", "https://open-meteo.com", Icons.Rounded.Cloud, MaterialShapes.Cookie6Sided))
                if (selected.id == ForecastModels.AUTO) {
                    add(
                        Source(
                            "National weather services", "Models used by Auto: DWD, NOAA, ECMWF, Météo-France, Met Office, KNMI, DMI, MET Norway, MeteoSwiss, ECCC, JMA and more",
                            "Open data, mostly CC BY 4.0", "https://open-meteo.com/en/docs", Icons.Rounded.Public, MaterialShapes.Clover4Leaf,
                        )
                    )
                } else {
                    add(Source(selected.provider, "Forecast model: ${selected.name}", selected.licence, selected.url, Icons.Rounded.Public, MaterialShapes.Clover4Leaf))
                }
                add(Source("Copernicus Atmosphere Monitoring Service", "Air quality and pollen model (via Open-Meteo)", "Copernicus licence", "https://atmosphere.copernicus.eu", Icons.Rounded.Masks, MaterialShapes.Flower))
                add(Source("Open-Meteo Marine", "Waves, swell, sea temperature, currents and tides", "CC BY 4.0", "https://open-meteo.com/en/docs/marine-weather-api", Icons.Rounded.Waves, MaterialShapes.Clover8Leaf))
                add(Source("NOAA Aviation Weather Center", "Live airport observations (METAR) for local correction", "Public domain", "https://aviationweather.gov", Icons.Rounded.Flight, MaterialShapes.Cookie9Sided))
                add(Source("OpenStreetMap Nominatim", "Names for your current location", "ODbL, © OpenStreetMap contributors", "https://www.openstreetmap.org/copyright", Icons.Rounded.Place, MaterialShapes.Pill))
                add(Source("RainViewer", "Rain radar images", "Free API, attribution", "https://www.rainviewer.com", Icons.Rounded.Radar, MaterialShapes.Circle))
                add(Source("CARTO", "Map tiles under the radar", "© CARTO, © OpenStreetMap contributors", "https://carto.com/attributions", Icons.Rounded.Map, MaterialShapes.Square))
                add(Source("NOAA National Hurricane Center", "Tropical storm and hurricane tracks", "Public domain", "https://www.nhc.noaa.gov", Icons.Rounded.Thunderstorm, MaterialShapes.SoftBurst))
                add(Source("National Weather Service", "Weather alerts in the United States", "Public domain", "https://www.weather.gov", Icons.Rounded.Warning, MaterialShapes.Arch))
                add(Source("MeteoAlarm (EUMETNET)", "Weather warnings across Europe", "CC BY 4.0", "https://meteoalarm.org", Icons.Rounded.Warning, MaterialShapes.Arch))
                add(Source("DWD via Bright Sky", "Weather warnings in Germany", "DWD open data, CC BY 4.0", "https://brightsky.dev", Icons.Rounded.Warning, MaterialShapes.Arch))
                add(Source("Environment and Climate Change Canada", "Weather alerts in Canada", "ECCC Open Data Licence", "https://weather.gc.ca", Icons.Rounded.Warning, MaterialShapes.Arch))
                add(Source("GitHub", "App updates", "GitHub terms", Updater.REPO_URL, Icons.Rounded.SystemUpdate, MaterialShapes.Gem))
            }
            SettingsGroup("Data sources", w) {
                sources.forEachIndexed { i, s ->
                    SettingsAction(
                        title = s.name,
                        subtitle = "${s.use}\n${s.licence}",
                        icon = s.icon,
                        shape = s.shape,
                        index = i,
                        count = sources.size,
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew, "Open website",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { open(s.url) },
                    )
                }
            }
        }
    }
}

private val SHAPES = listOf(
    MaterialShapes.Cookie4Sided, MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie7Sided,
    MaterialShapes.Gem, MaterialShapes.Pill, MaterialShapes.Cookie9Sided, MaterialShapes.Arch, MaterialShapes.Bun,
    MaterialShapes.SoftBurst, MaterialShapes.Flower, MaterialShapes.Oval, MaterialShapes.Clover8Leaf, MaterialShapes.Cookie12Sided,
)
