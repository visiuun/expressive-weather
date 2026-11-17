package dev.vis.weather.data

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

    val auto = ForecastModel(
        AUTO, "Auto (recommended)", "Open-Meteo",
        "Everywhere",
        "Picks the best high-resolution national model for your location and blends in global models for later days.",
        "https://open-meteo.com", "CC BY 4.0",
    )

    val all: List<ForecastModel> = listOf(
        auto,
        ForecastModel(
            "ecmwf_ifs", "ECMWF IFS", "European Centre for Medium-Range Weather Forecasts",
            "Global · often the most accurate global model",
            "9 km · 15 days", "https://www.ecmwf.int", "CC BY 4.0",
        ),
        ForecastModel(
            "ecmwf_aifs025_single", "ECMWF AIFS", "European Centre for Medium-Range Weather Forecasts",
            "Global · AI-driven model",
            "25 km · 15 days · no UV, visibility or rain chance", "https://www.ecmwf.int", "CC BY 4.0",
        ),
        ForecastModel(
            "dwd_icon_seamless", "DWD ICON", "Deutscher Wetterdienst",
            "Central Europe (2 km), Europe (7 km), global",
            "2–13 km · 7.5 days", "https://www.dwd.de", "CC BY 4.0 (DWD Open Data)",
        ),
        ForecastModel(
            "ncep_gfs_seamless", "GFS & HRRR", "NOAA / NCEP",
            "United States (HRRR 3 km), global",
            "3–25 km · 16 days", "https://www.weather.gov", "Public domain (US Government)",
        ),
        ForecastModel(
            "meteofrance_seamless", "Météo-France AROME & ARPEGE", "Météo-France",
            "France (1.3 km), Europe, global",
            "1.3–25 km · 4 days", "https://meteofrance.com", "Etalab Open Licence 2.0",
        ),
        ForecastModel(
            "ukmo_seamless", "UK Met Office", "Met Office",
            "United Kingdom & Ireland (2 km), global",
            "2–10 km · 7 days", "https://www.metoffice.gov.uk", "CC BY-SA 4.0",
        ),
        ForecastModel(
            "knmi_seamless", "KNMI Harmonie", "Royal Netherlands Meteorological Institute",
            "Netherlands & Belgium (2 km), Western Europe",
            "2–5.5 km · 2.5 days, then ECMWF", "https://www.knmi.nl", "CC BY 4.0",
        ),
        ForecastModel(
            "dmi_seamless", "DMI Harmonie", "Danish Meteorological Institute",
            "Denmark, Northern Europe, Iceland, Greenland",
            "2 km · 2.5 days, then ECMWF", "https://www.dmi.dk", "CC BY 4.0",
        ),
        ForecastModel(
            "metno_seamless", "MET Norway Nordic", "Norwegian Meteorological Institute",
            "Norway, Sweden, Finland, Denmark, Baltics",
            "1 km · 2.5 days, then ECMWF", "https://www.met.no", "CC BY 4.0 / NLOD",
        ),
        ForecastModel(
            "meteoswiss_icon_seamless", "MeteoSwiss ICON", "MeteoSwiss",
            "Switzerland and the Alps",
            "1–2 km · 5 days, then ECMWF", "https://www.meteoswiss.admin.ch", "CC BY 4.0",
        ),
        ForecastModel(
            "geosphere_seamless", "GeoSphere Austria", "GeoSphere Austria",
            "Austria and the Eastern Alps",
            "1–2.5 km · 2.5 days, then ECMWF", "https://www.geosphere.at", "CC BY 4.0",
        ),
        ForecastModel(
            "italia_meteo_arpae_icon_2i", "ItaliaMeteo ICON-2I", "ItaliaMeteo / ARPAE",
            "Italy",
            "2.2 km · 3 days", "https://www.italiameteo.eu", "CC BY 4.0",
        ),
        ForecastModel(
            "cmc_gem_seamless", "GEM", "Environment and Climate Change Canada",
            "Canada & North America (2.5 km), global",
            "2.5–15 km · 10 days", "https://weather.gc.ca", "ECCC Open Data Licence",
        ),
        ForecastModel(
            "jma_seamless", "JMA", "Japan Meteorological Agency",
            "Japan & East Asia (5 km), global",
            "5–55 km · 11 days", "https://www.jma.go.jp", "JMA terms (attribution)",
        ),
        ForecastModel(
            "cma_grapes_global", "CMA GRAPES", "China Meteorological Administration",
            "Global · China",
            "15 km · 10 days", "https://www.cma.gov.cn", "CMA open data terms",
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
