package dev.vis.weather

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dev.vis.weather.ui.WeatherApp
import dev.vis.weather.ui.WeatherViewModel
import dev.vis.weather.widget.WidgetLinks
import dev.vis.weather.widget.widgetReceivers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleWidgetIntent(intent)
        setContent { WeatherApp(vm) }
        publishWidgetPreviews()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null || !intent.hasExtra(WidgetLinks.EXTRA_PLACE)) return
        vm.openFromWidget(
            intent.getStringExtra(WidgetLinks.EXTRA_PLACE),
            intent.getStringExtra(WidgetLinks.EXTRA_METRIC),
            intent.getIntExtra(WidgetLinks.EXTRA_DAY, -1),
        )
    }

    /** Android 15+ shows live-rendered previews in the widget picker; publish once per app version. */
    private fun publishWidgetPreviews() {
        if (Build.VERSION.SDK_INT < 35) return
        val sp = getSharedPreferences("weather", MODE_PRIVATE)
        val version = BuildConfig.VERSION_CODE.toLong()
        if (sp.getLong("widget_previews", 0) == version) return
        lifecycleScope.launch {
            val mgr = GlanceAppWidgetManager(this@MainActivity)
            val ok = widgetReceivers.all {
                runCatching { mgr.setWidgetPreviews(it) == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }.getOrDefault(false)
            }
            if (ok) sp.edit().putLong("widget_previews", version).apply()
        }
    }
}
