package dev.vis.weather.ui.effects

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import dev.vis.weather.data.Settings
import dev.vis.weather.data.Sky
import dev.vis.weather.ui.theme.WeatherMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Subtle haptics that match the weather. Everything is a no-op when "Haptic feedback" is off.
 *
 * With the VIBRATE permission on Android 11+ (and a vibrator that supports composition primitives),
 * patterns use [VibrationEffect.Composition] (thuds, low ticks) for a real rumble. Otherwise they fall
 * back to short sequences of [View.performHapticFeedback], which needs no permission.
 */
class WeatherHaptics internal constructor(
    private val view: View,
    private val vibrator: Vibrator?,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
) {
    private var lastThunder = 0L

    private fun tap(constant: Int) {
        view.performHapticFeedback(constant)
    }

    private val tick get() = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    private val confirm get() = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY

    /** Plays a composition if possible; returns false when the caller should fall back to view haptics. */
    private fun compose(build: (VibrationEffect.Composition) -> Unit, vararg primitives: Int): Boolean {
        val v = vibrator ?: return false
        if (Build.VERSION.SDK_INT < 31) return false
        return try {
            if (!v.areAllPrimitivesSupported(*primitives)) return false
            val c = VibrationEffect.startComposition()
            build(c)
            v.vibrate(c.compose())
            true
        } catch (_: Exception) {
            false
        }
    }

    /** A light selection tick (scrubbing the hourly chart, flipping a switch). */
    fun onTick() {
        if (enabled()) tap(tick)
    }

    /** Toggle feedback for switches and choices. */
    fun onToggle(on: Boolean) {
        if (!enabled()) return
        tap(
            when {
                Build.VERSION.SDK_INT >= 34 -> if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
                else -> HapticFeedbackConstants.CLOCK_TICK
            }
        )
    }

    /** Something completed successfully. */
    fun onConfirm() {
        if (enabled()) tap(confirm)
    }

    /** Refresh finished: a patter of raindrops when it's wet, a rumble in a storm, a soft tap otherwise. */
    fun onRefresh(mood: WeatherMood?) {
        if (!enabled()) return
        when (mood?.sky) {
            Sky.THUNDER -> onThunder(0.6f)
            Sky.RAIN, Sky.DRIZZLE, Sky.FREEZING -> raindrops(if (mood.sky == Sky.DRIZZLE) 3 else 5)
            Sky.SNOW -> if (!compose({ it.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.35f) }, VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) tap(tick)
            else -> tap(confirm)
        }
    }

    private fun raindrops(count: Int) {
        val gaps = intArrayOf(0, 70, 45, 110, 60, 90)
        val ok = compose({ c ->
            for (i in 0 until count) c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.25f + (i % 2) * 0.2f, gaps[i % gaps.size])
        }, VibrationEffect.Composition.PRIMITIVE_TICK)
        if (!ok) scope.launch {
            for (i in 0 until count) {
                tap(tick)
                delay(gaps[(i + 1) % gaps.size].toLong() + 20)
            }
        }
    }

    /**
     * A low rumble for a lightning strike; [strength] 0..1. Throttled so overlapping strikes
     * (and the live sky + a refresh at once) never buzz continuously.
     */
    fun onThunder(strength: Float = 1f) {
        if (!enabled()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastThunder < 1800) return
        lastThunder = now
        val s = strength.coerceIn(0.1f, 1f)
        val ok = compose({ c ->
            c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f * s)
            c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.7f * s, 40)
            c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.5f * s, 60)
            c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.3f * s, 90)
        }, VibrationEffect.Composition.PRIMITIVE_THUD, VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
        if (!ok) scope.launch {
            tap(HapticFeedbackConstants.LONG_PRESS)
            delay(110)
            tap(tick)
            delay(140)
            tap(tick)
        }
    }
}

private fun vibratorOf(context: Context): Vibrator? {
    if (context.checkSelfPermission(Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) return null
    return if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }?.takeIf { it.hasVibrator() }
}

/** Weather haptics bound to the current view; follows [Settings.haptics] live. */
@Composable
fun rememberWeatherHaptics(settings: Settings): WeatherHaptics {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val on = rememberUpdatedState(settings.haptics)
    return remember(view, scope) { WeatherHaptics(view, vibratorOf(context), scope) { on.value } }
}
