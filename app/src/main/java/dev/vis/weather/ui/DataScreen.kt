@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import androidx.compose.ui.res.stringResource
import dev.vis.weather.R
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

    SubScreen(stringResource(R.string.s_data_title), onBack) { w ->
        item(key = "intro") {
            SettingsGroup(null, w) {
                SettingsInfo(
                    stringResource(R.string.s_data_intro),
                    stringResource(R.string.s_data_intro_body),
                    0, 1, Icons.Rounded.Source,
                )
            }
        }

        item(key = "model") {
            val models = ForecastModels.all
            SettingsGroup(stringResource(R.string.s_data_group_model), w) {
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
                    stringResource(R.string.s_data_about_models),
                    stringResource(R.string.s_data_about_models_body),
                    0, 1,
                )
            }
        }

        item(key = "accuracy") {
            SettingsGroup(stringResource(R.string.s_data_group_accuracy), w) {
                SettingsSwitch(
                    stringResource(R.string.s_data_local_correction),
                    stringResource(R.string.s_data_local_correction_sub, Observations.RADIUS_KM.toInt()),
                    Icons.Rounded.Verified, MaterialShapes.Cookie9Sided, 0, 2, settings.localCorrection,
                ) { v -> update { it.copy(localCorrection = v) } }
                SettingsSwitch(
                    stringResource(R.string.s_data_show_source),
                    stringResource(R.string.s_data_show_source_sub),
                    Icons.Rounded.Hub, MaterialShapes.Gem, 1, 2, settings.showDataSource,
                ) { v -> update { it.copy(showDataSource = v) } }
            }
        }

        item(key = "sources") {
            val sources = buildList {
                add(Source("Open-Meteo", stringResource(R.string.s_src_openmeteo_use), "CC BY 4.0", "https://open-meteo.com", Icons.Rounded.Cloud, MaterialShapes.Cookie6Sided))
                if (selected.id == ForecastModels.AUTO) {
                    add(
                        Source(
                            stringResource(R.string.s_src_national), stringResource(R.string.s_src_national_use),
                            stringResource(R.string.s_src_national_licence), "https://open-meteo.com/en/docs", Icons.Rounded.Public, MaterialShapes.Clover4Leaf,
                        )
                    )
                } else {
                    add(Source(selected.provider, stringResource(R.string.s_src_model_use, selected.name), selected.licence, selected.url, Icons.Rounded.Public, MaterialShapes.Clover4Leaf))
                }
                add(Source("Copernicus Atmosphere Monitoring Service", stringResource(R.string.s_src_cams_use), stringResource(R.string.s_src_cams_licence), "https://atmosphere.copernicus.eu", Icons.Rounded.Masks, MaterialShapes.Flower))
                add(Source("Open-Meteo Marine", stringResource(R.string.s_src_marine_use), "CC BY 4.0", "https://open-meteo.com/en/docs/marine-weather-api", Icons.Rounded.Waves, MaterialShapes.Clover8Leaf))
                add(Source("NOAA Aviation Weather Center", stringResource(R.string.s_src_awc_use), stringResource(R.string.s_src_public_domain), "https://aviationweather.gov", Icons.Rounded.Flight, MaterialShapes.Cookie9Sided))
                add(Source("OpenStreetMap Nominatim", stringResource(R.string.s_src_nominatim_use), stringResource(R.string.s_src_nominatim_licence), "https://www.openstreetmap.org/copyright", Icons.Rounded.Place, MaterialShapes.Pill))
                add(Source("RainViewer", stringResource(R.string.s_src_rainviewer_use), stringResource(R.string.s_src_rainviewer_licence), "https://www.rainviewer.com", Icons.Rounded.Radar, MaterialShapes.Circle))
                add(Source("CARTO", stringResource(R.string.s_src_carto_use), stringResource(R.string.s_src_carto_licence), "https://carto.com/attributions", Icons.Rounded.Map, MaterialShapes.Square))
                add(Source("NOAA National Hurricane Center", stringResource(R.string.s_src_nhc_use), stringResource(R.string.s_src_public_domain), "https://www.nhc.noaa.gov", Icons.Rounded.Thunderstorm, MaterialShapes.SoftBurst))
                add(Source("National Weather Service", stringResource(R.string.s_src_nws_use), stringResource(R.string.s_src_public_domain), "https://www.weather.gov", Icons.Rounded.Warning, MaterialShapes.Arch))
                add(Source("MeteoAlarm (EUMETNET)", stringResource(R.string.s_src_meteoalarm_use), "CC BY 4.0", "https://meteoalarm.org", Icons.Rounded.Warning, MaterialShapes.Arch))
                add(Source("DWD via Bright Sky", stringResource(R.string.s_src_dwd_use), stringResource(R.string.s_src_dwd_licence), "https://brightsky.dev", Icons.Rounded.Warning, MaterialShapes.Arch))
                add(Source("Environment and Climate Change Canada", stringResource(R.string.s_src_eccc_use), stringResource(R.string.s_src_eccc_licence), "https://weather.gc.ca", Icons.Rounded.Warning, MaterialShapes.Arch))
                add(Source("GitHub", stringResource(R.string.s_src_github_use), stringResource(R.string.s_src_github_licence), Updater.REPO_URL, Icons.Rounded.SystemUpdate, MaterialShapes.Gem))
            }
            SettingsGroup(stringResource(R.string.s_data_group_sources), w) {
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
                                Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.s_open_website),
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
