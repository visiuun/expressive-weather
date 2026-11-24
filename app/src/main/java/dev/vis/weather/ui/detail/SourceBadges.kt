@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vis.weather.L
import dev.vis.weather.R
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.ForecastModels
import dev.vis.weather.data.Observation
import dev.vis.weather.ui.Fmt

/** "Locally verified · EHAM 12 km" — shown when a nearby station observation corrected the forecast. */
fun observationLabel(o: Observation, fmt: Fmt): String =
    L.s(R.string.dt_obs_label, o.stationId, fmt.km(o.distanceKm).replace(".0 ", " "))

/** Tonal pill for the hero or header. Renders nothing when no correction was applied. */
@Composable
fun ObservationBadge(f: Forecast, fmt: Fmt, modifier: Modifier = Modifier) {
    val o = f.observation?.takeIf { it.applied } ?: return
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Verified, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(observationLabel(o, fmt), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Longer explanation of the correction, e.g. for a tooltip or the data screen. */
fun observationDetail(o: Observation, fmt: Fmt, now: Long): String {
    val mins = ((now - o.time) / 60).coerceAtLeast(0)
    return L.p(
        R.plurals.dt_obs_detail, mins.toInt(),
        o.name.ifBlank { o.stationId }, o.stationId, fmt.km(o.distanceKm), fmt.temp(o.temp), mins, fmt.tempDelta(o.tempBias),
    )
}

/** "Forecast: ECMWF IFS · Open-Meteo · Locally verified · EHAM 12 km" — for the bottom of the main screen. */
fun dataSourceText(f: Forecast, fmt: Fmt): String {
    val m = ForecastModels.byId(f.model)
    val model = if (m.id == ForecastModels.AUTO) L.s(R.string.dt_src_best_model) else m.name
    val parts = mutableListOf(L.s(R.string.dt_src_forecast, model))
    if (f.modelFilled && m.id != ForecastModels.AUTO) parts += L.s(R.string.dt_src_gaps)
    f.observation?.takeIf { it.applied }?.let { parts += L.s(R.string.dt_src_corrected, it.stationId, fmt.km(it.distanceKm)) }
    if (f.marine != null) parts += L.s(R.string.dt_src_sea)
    return parts.joinToString(" · ")
}

@Composable
fun DataSourceLine(f: Forecast, fmt: Fmt, modifier: Modifier = Modifier) {
    Text(
        dataSourceText(f, fmt),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
