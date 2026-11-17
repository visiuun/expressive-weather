package dev.vis.weather.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

const val MIN_ZOOM = 2.0
const val MAX_ZOOM = 17.0

/**
 * Camera over the Mercator world. Position is the normalised world point at the centre of the viewport;
 * [zoom] is fractional (tiles are scaled between integer levels). Everything is plain snapshot state,
 * so drawing / layout that reads it is invalidated without recomposition.
 */
@Stable
class MapCamera(x: Double, y: Double, zoom: Double) {
    var x by mutableDoubleStateOf(x)
        private set
    var y by mutableDoubleStateOf(y)
        private set
    var zoom by mutableDoubleStateOf(zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        private set

    var width by mutableIntStateOf(0)
        internal set
    var height by mutableIntStateOf(0)
        internal set

    /** On-screen size of one tile at an integer zoom, in px (256 dp). */
    var tileSize: Float = 256f
        internal set

    internal var animJob: Job? = null

    /** World size in px at the current zoom. */
    val worldSize: Double get() = tileSize * 2.0.pow(zoom)

    fun screenX(wx: Double): Float = (Mercator.wrapDelta(wx - x) * worldSize + width / 2.0).toFloat()
    fun screenY(wy: Double): Float = ((wy - y) * worldSize + height / 2.0).toFloat()
    fun worldX(sx: Float): Double = x + (sx - width / 2.0) / worldSize
    fun worldY(sy: Float): Double = y + (sy - height / 2.0) / worldSize

    /** Visible bounds in (unwrapped) world units. */
    val left: Double get() = x - width / 2.0 / worldSize
    val right: Double get() = x + width / 2.0 / worldSize
    val top: Double get() = y - height / 2.0 / worldSize
    val bottom: Double get() = y + height / 2.0 / worldSize

    fun set(nx: Double, ny: Double, nz: Double = zoom) {
        zoom = nz.coerceIn(MIN_ZOOM, MAX_ZOOM)
        x = nx - floor(nx)
        y = clampY(ny)
    }

    fun panBy(dx: Float, dy: Float) {
        val ws = worldSize
        set(x - dx / ws, y - dy / ws)
    }

    /** Sets the zoom while keeping the world point under [anchor] fixed on screen. */
    fun zoomAround(anchor: Offset, newZoom: Double) {
        val wx = worldX(anchor.x)
        val wy = worldY(anchor.y)
        zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val ws = worldSize
        set(wx - (anchor.x - width / 2.0) / ws, wy - (anchor.y - height / 2.0) / ws)
    }

    /** Keeps the map filling the viewport vertically. */
    private fun clampY(ny: Double): Double {
        val half = height / 2.0 / worldSize
        return if (half >= 0.5) 0.5 else ny.coerceIn(half, 1 - half)
    }

    /** Animates to a place; [spec] should be a spatial spring from the motion scheme. */
    fun flyTo(scope: CoroutineScope, lat: Double, lon: Double, targetZoom: Double, spec: AnimationSpec<Float>) {
        animJob?.cancel()
        val sx = x
        val sy = y
        val sz = zoom
        val tx = sx + Mercator.wrapDelta(Mercator.x(lon) - sx)
        val ty = Mercator.y(lat)
        animJob = scope.launch {
            Animatable(0f).animateTo(1f, spec) {
                val t = value.toDouble()
                // Interpolate in screen space at the start zoom so long hops don't overshoot wildly.
                set(sx + (tx - sx) * t, sy + (ty - sy) * t, sz + (targetZoom - sz) * t)
            }
        }
    }

    fun animateZoomBy(scope: CoroutineScope, anchor: Offset, delta: Double, spec: AnimationSpec<Float>) {
        animJob?.cancel()
        val start = zoom
        val target = (start + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        animJob = scope.launch {
            Animatable(0f).animateTo(1f, spec) { zoomAround(anchor, start + (target - start) * value) }
        }
    }

    companion object {
        val Saver: Saver<MapCamera, *> = listSaver(
            save = { listOf(it.x, it.y, it.zoom) },
            restore = { MapCamera(it[0], it[1], it[2]) },
        )
    }
}

/**
 * Pan (with fling), pinch-zoom around the centroid and double-tap zoom.
 * Everything is handled in one detector so taps and drags never fight.
 */
fun Modifier.mapGestures(
    camera: MapCamera,
    scope: CoroutineScope,
    zoomSpec: () -> AnimationSpec<Float>,
): Modifier = pointerInput(camera) {
    val slop = viewConfiguration.touchSlop
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    val doubleTapSlop = 48.dp.toPx()
    var lastTapTime = 0L
    var lastTapPos = Offset.Zero
    val decay = exponentialDecay<Offset>(frictionMultiplier = 1.6f)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        camera.animJob?.cancel()
        val tracker = VelocityTracker()
        var virtual = Offset.Zero
        tracker.addPosition(down.uptimeMillis, virtual)
        var pastSlop = false
        var panAcc = Offset.Zero
        var zoomAcc = 1f
        var pointers = 1
        var maxPointers = 1
        var lastUptime = down.uptimeMillis
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) break
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) {
                lastUptime = event.changes.first().uptimeMillis
                break
            }
            maxPointers = max(maxPointers, pressed)
            val zoomChange = event.calculateZoom()
            val pan = event.calculatePan()
            if (!pastSlop) {
                zoomAcc *= zoomChange
                panAcc += pan
                val zoomMotion = abs(1 - zoomAcc) * event.calculateCentroidSize(useCurrent = false)
                if (zoomMotion > slop || panAcc.getDistance() > slop) pastSlop = true
            }
            if (pastSlop) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (zoomChange != 1f && centroid != Offset.Unspecified) {
                    camera.zoomAround(centroid, camera.zoom + ln(zoomChange.toDouble()) / ln(2.0))
                }
                if (pan != Offset.Zero) camera.panBy(pan.x, pan.y)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
            if (pressed != pointers) {
                // Finger count changed: restart velocity tracking so the centroid jump isn't read as a fling.
                pointers = pressed
                tracker.resetTracking()
            }
            virtual += pan
            tracker.addPosition(event.changes.first().uptimeMillis, virtual)
        }
        if (pastSlop) {
            if (pointers == 1) {
                val v = tracker.calculateVelocity()
                val velocity = Offset(v.x, v.y)
                if (velocity.getDistance() > 300f) {
                    camera.animJob = scope.launch {
                        var last = Offset.Zero
                        Animatable(Offset.Zero, Offset.VectorConverter).animateDecay(velocity, decay) {
                            val d = value - last
                            last = value
                            camera.panBy(d.x, d.y)
                        }
                    }
                }
            }
        } else if (maxPointers == 1) {
            val pos = down.position
            if (lastUptime - lastTapTime < doubleTapTimeout && (pos - lastTapPos).getDistance() < doubleTapSlop) {
                lastTapTime = 0L
                camera.animateZoomBy(scope, pos, 1.0, zoomSpec())
            } else {
                lastTapTime = lastUptime
                lastTapPos = pos
            }
        } else if (maxPointers == 2) {
            // Two-finger tap zooms out, like most map apps.
            camera.animateZoomBy(scope, Offset(camera.width / 2f, camera.height / 2f), -1.0, zoomSpec())
        }
    }
}
