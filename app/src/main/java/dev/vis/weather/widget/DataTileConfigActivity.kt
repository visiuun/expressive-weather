@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dev.vis.weather.data.Place
import dev.vis.weather.data.Prefs
import dev.vis.weather.ui.UiState
import dev.vis.weather.ui.components.SettingsAction
import dev.vis.weather.ui.components.SettingsGroup
import dev.vis.weather.ui.theme.WeatherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Per-widget setup for [DataTileWidget]: which metric and which place. Opened from the widget's settings. */
class DataTileConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Configuration is optional: backing out keeps the widget as it is (wind by default).
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val glanceId = runCatching { GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId) }.getOrNull()
        if (glanceId == null) {
            finish()
            return
        }
        val settings = Prefs(this).settings.value
        val choices = WidgetRepo.choices(this)
        setContent {
            WeatherTheme(settings) {
                val initial by produceState<Pair<TileMetric, String>?>(null) {
                    value = runCatching {
                        val st = getAppWidgetState(this@DataTileConfigActivity, PreferencesGlanceStateDefinition, glanceId)
                        TileMetric.of(st[TileKeys.METRIC]) to st[WidgetKeys.PLACE].orEmpty()
                    }.getOrDefault(TileMetric.WIND to "")
                }
                initial?.let { (metric, place) ->
                    TileConfigScreen(metric, place, choices, onClose = ::finish) { m, p ->
                        lifecycleScope.launch {
                            save(m, p, glanceId)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private suspend fun save(metric: TileMetric, place: String, glanceId: GlanceId) {
        val ctx = applicationContext
        updateAppWidgetState(ctx, glanceId) { p ->
            p[TileKeys.METRIC] = metric.name
            if (place.isEmpty()) p.remove(WidgetKeys.PLACE) else p[WidgetKeys.PLACE] = place
            p[WidgetKeys.STAMP] = System.currentTimeMillis()
        }
        DataTileWidget().update(ctx, glanceId)
        val resolved = WidgetRepo.resolve(ctx, place.ifEmpty { null })?.second
        if (resolved != null && Prefs(ctx).loadCache(resolved.key) == null) WidgetRefreshWorker.runNow(ctx)
    }
}

private val metricShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Cookie4Sided, MaterialShapes.Arch, MaterialShapes.Cookie7Sided,
    MaterialShapes.Gem, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie12Sided, MaterialShapes.Circle, MaterialShapes.Pentagon,
)

@Composable
private fun TileConfigScreen(
    initialMetric: TileMetric,
    initialPlace: String,
    choices: List<Pair<String, Place>>,
    onClose: () -> Unit,
    onSave: (TileMetric, String) -> Unit,
) {
    var metric by remember { mutableStateOf(initialMetric) }
    var place by remember { mutableStateOf(initialPlace) }
    val context = LocalContext.current
    // Current values for the chosen place, shown next to each option.
    val d by produceState<WData?>(null, place) {
        value = withContext(Dispatchers.IO) {
            WidgetRepo.model(context, place.ifEmpty { null })?.takeIf { it.forecast != null }?.let { WData(it, it.forecast!!, false, 0, 0) }
        }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel") }
                Spacer(Modifier.width(4.dp))
                Text("Data tile", style = MaterialTheme.typography.headlineMediumEmphasized, modifier = Modifier.weight(1f))
                Button(onClick = { onSave(metric, place) }) { Text("Save") }
                Spacer(Modifier.width(8.dp))
            }
            LazyColumn(
                Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + navBottom),
            ) {
                item {
                    SettingsGroup("Show") {
                        TileMetric.entries.forEachIndexed { i, m ->
                            val now = d?.let { data -> m.read(data).let { r -> listOfNotNull("${r.value}${if (r.unit.isEmpty()) "" else " " + r.unit}", r.pill).joinToString(" · ") } }
                            SettingsAction(
                                m.label, now, m.icon, metricShapes[i % metricShapes.size], i, TileMetric.entries.size,
                                trailing = { RadioButton(selected = metric == m, onClick = null) },
                            ) { metric = m }
                        }
                    }
                }
                item {
                    SettingsGroup("Location", Modifier.padding(top = 18.dp)) {
                        val n = choices.size + 1
                        SettingsAction(
                            "Follow the app", "Shows the place open in the app", Icons.Rounded.MyLocation, MaterialShapes.Cookie9Sided, 0, n,
                            trailing = { RadioButton(selected = place.isEmpty(), onClick = null) },
                        ) { place = "" }
                        choices.forEachIndexed { i, (key, p) ->
                            val current = key == UiState.CURRENT
                            SettingsAction(
                                p.name, if (current) "Your location" else p.region.ifBlank { null },
                                if (current) Icons.Rounded.NearMe else Icons.Rounded.Place,
                                if (current) MaterialShapes.Circle else MaterialShapes.Clover4Leaf, i + 1, n,
                                trailing = { RadioButton(selected = place == key, onClick = null) },
                            ) { place = key }
                        }
                    }
                }
            }
        }
    }
}
