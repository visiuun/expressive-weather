package dev.vis.weather.ui.components

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Device heading in degrees clockwise from TRUE north (magnetic declination applied),
 * low-pass filtered and "unwrapped" (may exceed 0..360) so rotations animate smoothly.
 * Null when the device has no compass.
 */
@Composable
fun rememberHeading(lat: Double, lon: Double): State<Float?> {
    val context = LocalContext.current
    val view = LocalView.current
    val state = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(lat, lon) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (sm == null || sensor == null) return@DisposableEffect onDispose { }
        val declination = runCatching {
            GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        }.getOrDefault(0f)
        val rot = FloatArray(9)
        val remapped = FloatArray(9)
        val orient = FloatArray(3)
        var smooth = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                val (ax, ay) = when (view.display?.rotation ?: Surface.ROTATION_0) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rot, ax, ay, remapped)
                SensorManager.getOrientation(remapped, orient)
                val az = Math.toDegrees(orient[0].toDouble()).toFloat() + declination
                if (smooth.isNaN()) smooth = az
                val d = ((az - smooth) % 360f + 540f) % 360f - 180f
                smooth += d * 0.18f
                state.value = smooth
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

/** Physical liquid state driven by the accelerometer: tilt follows gravity, shakes make waves. */
@Stable
class SloshState {
    /** Surface tilt in radians, screen space (positive = clockwise). */
    var tilt by mutableFloatStateOf(0f)
    /** Wave amplitude as a fraction of the container height. */
    var amp by mutableFloatStateOf(0.02f)
    var phase by mutableFloatStateOf(0f)
    internal var target = 0f
    internal var vel = 0f
    internal var kick = 0f
}

@Composable
fun rememberSlosh(): SloshState {
    val context = LocalContext.current
    val view = LocalView.current
    val s = remember { SloshState() }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || acc == null) return@DisposableEffect onDispose { }
        var lastMag = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                var x = e.values[0]
                var y = e.values[1]
                val z = e.values[2]
                when (view.display?.rotation ?: Surface.ROTATION_0) {
                    Surface.ROTATION_90 -> { val t = x; x = -y; y = t }
                    Surface.ROTATION_180 -> { x = -x; y = -y }
                    Surface.ROTATION_270 -> { val t = x; x = y; y = -t }
                }
                // Angle of "up" within the screen plane; fades out when the phone lies flat.
                val inPlane = hypot(x, y)
                val weight = (inPlane / 4f).coerceIn(0f, 1f)
                s.target = (atan2(x, y) * weight).coerceIn(-0.75f, 0.75f)
                val mag = sqrt(x * x + y * y + z * z)
                if (!lastMag.isNaN()) s.kick += abs(mag - lastMag).coerceAtMost(12f) * 0.006f
                lastMag = mag
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    LaunchedEffect(s) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0.001f, 0.05f)
                last = t
                // Under-damped spring: the surface overshoots and settles like real water.
                val force = (s.target - s.tilt) * 38f - s.vel * 3.2f
                s.vel += force * dt
                s.tilt += s.vel * dt
                var a = s.amp * exp(-1.1f * dt) + abs(s.vel) * dt * 0.05f + s.kick
                s.kick = 0f
                a = a.coerceIn(0.012f, 0.22f)
                s.amp = a
                s.phase += dt * (2.2f + a * 14f)
            }
        }
    }
    return s
}

/** Draws water filling [fraction] of the canvas, reacting to [slosh]. Clip the modifier to a shape. */
@Composable
fun WaterFill(
    fraction: Float,
    slosh: SloshState,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4A9EFF),
) {
    val back = color.copy(alpha = 0.45f)
    val top = lerpColor(color, Color.White, 0.25f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val f = fraction.coerceIn(0f, 1f)
        val baseY = h * (1 - f)
        val slope = tan(slosh.tilt)
        val amp = slosh.amp * h
        val ph = slosh.phase
        fun surface(x: Float, k: Float, shift: Float): Float =
            baseY + (x - w / 2) * slope +
                sin(x / w * 2 * PI.toFloat() * 1.3f * k + ph + shift) * amp * 0.6f +
                sin(x / w * 2 * PI.toFloat() * 0.6f - ph * 1.4f + shift) * amp * 0.4f

        fun wave(k: Float, shift: Float): Path = Path().apply {
            moveTo(0f, h)
            val steps = 40
            for (i in 0..steps) {
                val x = w * i / steps
                lineTo(x, surface(x, k, shift))
            }
            lineTo(w, h)
            close()
        }
        drawPath(wave(1.4f, 2.1f), back)
        drawPath(wave(1f, 0f), Brush.verticalGradient(listOf(top, color), startY = baseY - amp, endY = h))
        // bubbles
        for (i in 0 until 5) {
            val bx = w * (0.15f + i * 0.17f)
            val travel = ((ph * 0.08f + i * 0.37f) % 1f)
            val by = h - travel * (h - baseY)
            if (by > surface(bx, 1f, 0f) + 4f) drawCircle(Color.White.copy(alpha = 0.25f), 2.5f + i % 3, Offset(bx, by))
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
