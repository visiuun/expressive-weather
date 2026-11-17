package dev.vis.weather.ui.effects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.vis.weather.data.Settings
import dev.vis.weather.ui.theme.LocalAppColors
import dev.vis.weather.ui.theme.WeatherMood
import dev.vis.weather.ui.theme.skyPalette
import kotlinx.coroutines.isActive

/**
 * Full-bleed animated sky for [mood]: a gradient that fades into `colorScheme.background` at the bottom
 * edge, plus live weather — rain angled by [windKmh] and as dense as [precipMm] (mm/h) says, drizzle, snow,
 * hail, fog banks, drifting clouds, sun rays, twinkling stars with the odd shooting star, and lightning
 * (with a haptic rumble).
 *
 * Draw it behind the top of the main screen (e.g. `Box { WeatherSky(..., Modifier.matchParentSize()) ; content }`
 * or a fixed-height layer under a transparent list). One Canvas, a frame loop with preallocated particle arrays.
 * Shows only the static gradient when animations are off, in battery saver ([Settings.pauseOnBatterySaver]),
 * when the system animator scale is 0, or while the screen isn't visible.
 */
@Composable
fun WeatherSky(
    mood: WeatherMood,
    windKmh: Double,
    precipMm: Double,
    settings: Settings,
    modifier: Modifier = Modifier,
) {
    val app = LocalAppColors.current
    val kind = mood.kind
    val target = remember(kind, app.isDark, app.oled, app.skyTint) { skyPalette(kind, app.isDark, app.oled, app.skyTint) }
    val spec = remember { tween<Color>(600, easing = FastOutSlowInEasing) }
    val top by animateColorAsState(target.top, spec, label = "skyTop")
    val mid by animateColorAsState(target.mid, spec, label = "skyMid")
    val horizon by animateColorAsState(target.horizon, spec, label = "skyHorizon")
    val glow by animateColorAsState(target.glow, spec, label = "skyGlow")
    val particle by animateColorAsState(target.particle, spec, label = "skyParticle")
    val bg = MaterialTheme.colorScheme.background

    val gradient = remember(top, mid, horizon, bg) {
        Brush.verticalGradient(0f to top, 0.42f to mid, 0.78f to horizon, 1f to bg)
    }
    // Unit-radius (100 px) soft blobs, scaled into puffs, fog banks and glows at draw time.
    // Dark skies: clouds take on the sky colour (storm clouds stay heavy, not white); glows go paler so
    // they read as light rather than turning brown over the dark gradient.
    val cloudColor = when {
        app.isDark -> lerp(particle, top, 0.5f)
        kind.sample >= 95 -> lerp(particle, Color(0xFF4A5560), 0.25f)
        // Night cloud colours are dark; on a light sky they'd smudge the text, so lift them.
        particle.luminance() < 0.4f -> lerp(particle, Color.White, 0.6f)
        else -> particle
    }
    val glowColor = if (app.isDark) lerp(glow, Color.White, 0.45f) else glow
    val cloudBrush = remember(cloudColor) { softBrush(cloudColor) }
    val glowBrush = remember(glowColor) { softBrush(glowColor) }

    val running = rememberSkyRunning(settings)
    val haptics = rememberWeatherHaptics(settings)
    val engine = remember { SkyEngine() }
    engine.density = LocalDensity.current.density
    engine.configure(mood, windKmh, precipMm)

    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                val strike = engine.step(dt)
                if (strike > 0f) haptics.onThunder(strike)
                frame.longValue = now
            }
        }
    }

    Canvas(modifier.clipToBounds()) {
        frame.longValue // redraw every frame while running (draw-phase read: no recomposition)
        drawRect(gradient)
        engine.width = size.width
        engine.height = size.height
        if (running) engine.draw(this, particle, glow, cloudBrush, glowBrush, target.intensity, app.isDark)
    }
}

private fun softBrush(c: Color) = Brush.radialGradient(
    0f to c,
    0.45f to c.copy(alpha = c.alpha * 0.75f),
    1f to c.copy(alpha = 0f),
    center = androidx.compose.ui.geometry.Offset.Zero,
    radius = SkyEngine.UNIT,
)

/** True while the sky may animate: setting on, screen visible, no battery saver pause, animator scale > 0. */
@Composable
private fun rememberSkyRunning(settings: Settings): Boolean {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val powerSave by rememberPowerSaveMode()
    val scale by rememberAnimatorScale()
    return settings.animations &&
        lifecycle.isAtLeast(Lifecycle.State.STARTED) &&
        !(settings.pauseOnBatterySaver && powerSave) &&
        scale > 0f
}

@Composable
private fun rememberPowerSaveMode(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val state = remember { mutableStateOf(pm.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                state.value = pm.isPowerSaveMode
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        state.value = pm.isPowerSaveMode
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

@Composable
private fun rememberAnimatorScale(): State<Float> {
    val context = LocalContext.current.applicationContext
    val resolver = context.contentResolver
    fun read() = AndroidSettings.Global.getFloat(resolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val state = remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = read()
            }
        }
        resolver.registerContentObserver(AndroidSettings.Global.getUriFor(AndroidSettings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return state
}
