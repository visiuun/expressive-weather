package dev.vis.weather.data

import dev.vis.weather.L
import dev.vis.weather.R

/**
 * A numerical weather model served by Open-Meteo.
 * Ids verified against https://open-meteo.com/en/docs (Nov 2026). Retired models (BOM ACCESS, KMA) are left out.
 */
data class ForecastModel(
    /** Open-Meteo `models=` id. */
    val id: String,
    val name: String,
    /** Organisation that runs the model. */
    val provider: String,
    /** Where it is strongest / its native high-resolution domain. */
    val region: String,
    /** Short technical note (resolution, range). */
    val detail: String,
    /** Provider website, for the data sources list. */
    val url: String,
    /** Licence or terms of the underlying open data. */
    val licence: String,
)

object ForecastModels {
    const val AUTO = "best_match"

    val auto: ForecastModel get() = ForecastModel(
        AUTO, L.s(R.string.d_model_auto_name), "Open-Meteo",
        L.s(R.string.d_model_auto_region),
        L.s(R.string.d_model_auto_detail),
        "https://open-meteo.com", "CC BY 4.0",
    )

    // Getters so the texts follow a language change.
    val all: List<ForecastModel> get() = listOf(
        auto,
        ForecastModel(
            "ecmwf_ifs", "ECMWF IFS", "European Centre for Medium-Range Weather Forecasts",
            L.s(R.string.d_model_ecmwf_ifs_region),
            L.s(R.string.d_model_ecmwf_ifs_detail), "https://www.ecmwf.int", "CC BY 4.0",
        ),
        ForecastModel(
            "ecmwf_aifs025_single", "ECMWF AIFS", "European Centre for Medium-Range Weather Forecasts",
            L.s(R.string.d_model_ecmwf_aifs025_single_region),
            L.s(R.string.d_model_ecmwf_aifs025_single_detail), "https://www.ecmwf.int", "CC BY 4.0",
        ),
        ForecastModel(
            "dwd_icon_seamless", "DWD ICON", "Deutscher Wetterdienst",
            L.s(R.string.d_model_dwd_icon_region),
            L.s(R.string.d_model_dwd_icon_detail), "https://www.dwd.de", "CC BY 4.0 (DWD Open Data)",
        ),
        ForecastModel(
            "ncep_gfs_seamless", "GFS & HRRR", "NOAA / NCEP",
            L.s(R.string.d_model_ncep_gfs_region),
            L.s(R.string.d_model_ncep_gfs_detail), "https://www.weather.gov", L.s(R.string.d_model_ncep_gfs_licence),
        ),
        ForecastModel(
            "meteofrance_seamless", "Météo-France AROME & ARPEGE", "Météo-France",
            L.s(R.string.d_model_meteofrance_region),
            L.s(R.string.d_model_meteofrance_detail), "https://meteofrance.com", "Etalab Open Licence 2.0",
        ),
        ForecastModel(
            "ukmo_seamless", "UK Met Office", "Met Office",
            L.s(R.string.d_model_ukmo_region),
            L.s(R.string.d_model_ukmo_detail), "https://www.metoffice.gov.uk", "CC BY-SA 4.0",
        ),
        ForecastModel(
            "knmi_seamless", "KNMI Harmonie", "Royal Netherlands Meteorological Institute",
            L.s(R.string.d_model_knmi_region),
            L.s(R.string.d_model_knmi_detail), "https://www.knmi.nl", "CC BY 4.0",
        ),
        ForecastModel(
            "dmi_seamless", "DMI Harmonie", "Danish Meteorological Institute",
            L.s(R.string.d_model_dmi_region),
            L.s(R.string.d_model_dmi_detail), "https://www.dmi.dk", "CC BY 4.0",
        ),
        ForecastModel(
            "metno_seamless", "MET Norway Nordic", "Norwegian Meteorological Institute",
            L.s(R.string.d_model_metno_region),
            L.s(R.string.d_model_metno_detail), "https://www.met.no", "CC BY 4.0 / NLOD",
        ),
        ForecastModel(
            "meteoswiss_icon_seamless", "MeteoSwiss ICON", "MeteoSwiss",
            L.s(R.string.d_model_meteoswiss_icon_region),
            L.s(R.string.d_model_meteoswiss_icon_detail), "https://www.meteoswiss.admin.ch", "CC BY 4.0",
        ),
        ForecastModel(
            "geosphere_seamless", "GeoSphere Austria", "GeoSphere Austria",
            L.s(R.string.d_model_geosphere_region),
            L.s(R.string.d_model_geosphere_detail), "https://www.geosphere.at", "CC BY 4.0",
        ),
        ForecastModel(
            "italia_meteo_arpae_icon_2i", "ItaliaMeteo ICON-2I", "ItaliaMeteo / ARPAE",
            L.s(R.string.d_model_italia_meteo_arpae_icon_2i_region),
            L.s(R.string.d_model_italia_meteo_arpae_icon_2i_detail), "https://www.italiameteo.eu", "CC BY 4.0",
        ),
        ForecastModel(
            "cmc_gem_seamless", "GEM", "Environment and Climate Change Canada",
            L.s(R.string.d_model_cmc_gem_region),
            L.s(R.string.d_model_cmc_gem_detail), "https://weather.gc.ca", "ECCC Open Data Licence",
        ),
        ForecastModel(
            "jma_seamless", "JMA", "Japan Meteorological Agency",
            L.s(R.string.d_model_jma_region),
            L.s(R.string.d_model_jma_detail), "https://www.jma.go.jp", L.s(R.string.d_model_jma_licence),
        ),
        ForecastModel(
            "cma_grapes_global", "CMA GRAPES", "China Meteorological Administration",
            L.s(R.string.d_model_cma_grapes_global_region),
            L.s(R.string.d_model_cma_grapes_global_detail), "https://www.cma.gov.cn", L.s(R.string.d_model_cma_grapes_global_licence),
        ),
    )

    fun byId(id: String?): ForecastModel = all.firstOrNull { it.id == id } ?: legacy[id]?.let { l -> all.first { it.id == l } } ?: auto

    /** Older Open-Meteo aliases that may still be stored in settings. */
    private val legacy = mapOf(
        "icon_seamless" to "dwd_icon_seamless",
        "gfs_seamless" to "ncep_gfs_seamless",
        "gem_seamless" to "cmc_gem_seamless",
        "ecmwf_ifs025" to "ecmwf_ifs",
    )

    /** Normalises a stored id to one the API accepts. */
    fun resolve(id: String?): String = byId(id).id
}
