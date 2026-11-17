package dev.vis.weather.ui.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.floor

/** Value → colour lookup with a 256-entry table. Colours may carry alpha. */
class Ramp(private val values: FloatArray, private val colors: IntArray) {
    val min: Float get() = values.first()
    val max: Float get() = values.last()
    private val lut = IntArray(256)

    init {
        for (i in 0 until 256) lut[i] = interpolate(min + (max - min) * i / 255f)
    }

    private fun interpolate(v: Float): Int {
        if (v <= values.first()) return colors.first()
        if (v >= values.last()) return colors.last()
        var k = 1
        while (values[k] < v) k++
        val f = (v - values[k - 1]) / (values[k] - values[k - 1])
        return lerpArgb(colors[k - 1], colors[k], f)
    }

    fun argb(v: Float): Int {
        if (v.isNaN()) return 0
        val i = ((v - min) / (max - min) * 255f).toInt()
        return lut[if (i < 0) 0 else if (i > 255) 255 else i]
    }

    /** The ramp's own stop colours (opaque ones only), for non-linear scales like precipitation. */
    fun stopColors(): List<Color> = colors.filter { (it ushr 24) > 0 }.map { Color(it).copy(alpha = 1f) }

    /** Evenly spaced colours for a legend gradient (alpha forced up so the bar stays visible). */
    fun legend(steps: Int = 12): List<Color> = List(steps) {
        val c = Color(argb(min + (max - min) * it / (steps - 1f)))
        c.copy(alpha = 0.35f + c.alpha * 0.65f)
    }

    companion object {
        private fun lerpArgb(a: Int, b: Int, f: Float): Int {
            fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * f)).toInt()
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        private fun of(vararg stops: Pair<Float, Long>) =
            Ramp(FloatArray(stops.size) { stops[it].first }, IntArray(stops.size) { stops[it].second.toInt() })

        /** °C — extends the app's tempColor() ramp at both ends. */
        val Temperature = of(
            -40f to 0xFFE6E9FF, -25f to 0xFFB7A6FF, -15f to 0xFF9C7CFF, 0f to 0xFF4A9EFF,
            10f to 0xFF35C7B0, 20f to 0xFFFFC94D, 30f to 0xFFFF8A3D, 40f to 0xFFE5484D, 50f to 0xFF9E1F45,
        )

        /** km/h */
        val Wind = of(
            0f to 0xFF3B6FD8, 10f to 0xFF4A9EFF, 20f to 0xFF35C7B0, 35f to 0xFF8BD346,
            50f to 0xFFFFC94D, 70f to 0xFFFF8A3D, 90f to 0xFFE5484D, 120f to 0xFFB0306A,
        )

        /** hPa */
        val Pressure = of(
            960f to 0xFF2A4FBF, 985f to 0xFF4A9EFF, 1000f to 0xFF35C7B0, 1013f to 0xFFB5DE7A,
            1022f to 0xFFFFC94D, 1035f to 0xFFFF8A3D, 1050f to 0xFFE5484D,
        )

        /** mm per hour; transparent when dry. */
        val Precipitation = of(
            0f to 0x004A9EFF, 0.09f to 0x004A9EFF, 0.1f to 0x8077C4FF, 0.5f to 0xC04A9EFF,
            1.5f to 0xE02E7BD9, 3f to 0xF035C7B0, 6f to 0xFFFFC94D, 12f to 0xFFFF8A3D, 25f to 0xFFE5484D,
        )

        val CloudsDark = of(0f to 0x00E8EEF5, 15f to 0x00E8EEF5, 60f to 0x99E8EEF5, 100f to 0xF2F4F7FB)
        val CloudsLight = of(0f to 0x00566170, 15f to 0x00566170, 60f to 0x8C566170, 100f to 0xE6424B57)

        /** Approximation of RainViewer's "Universal Blue" scheme, for the radar legend only. */
        val RadarLegend = listOf(
            Color(0xFF9BE1FF), Color(0xFF3DA5F4), Color(0xFF0C6BC4), Color(0xFF00468F),
            Color(0xFFFFEE00), Color(0xFFFF9300), Color(0xFFFF2600), Color(0xFFC10087), Color(0xFFFFAAFF),
        )

        fun forLayer(layer: MapLayer, dark: Boolean): Ramp? = when (layer) {
            MapLayer.TEMPERATURE -> Temperature
            MapLayer.WIND -> Wind
            MapLayer.CLOUDS -> if (dark) CloudsDark else CloudsLight
            MapLayer.PRESSURE -> Pressure
            MapLayer.PRECIPITATION -> Precipitation
            else -> null
        }
    }
}

/** Pixels per grid cell in the colour-field bitmap; the GPU stretches it with bilinear filtering. */
private const val CELL_PX = 12

/** Bilinear interpolation of one hour of [values] into an ARGB bitmap covering the grid's corners. */
fun buildField(grid: FieldGrid, values: FloatArray, hour: Int, ramp: Ramp): Bitmap {
    val spec = grid.spec
    val cols = spec.cols
    val rows = spec.rows
    val n = spec.size
    val base = hour.coerceIn(0, grid.hours - 1) * n
    val w = (cols - 1) * CELL_PX + 1
    val h = (rows - 1) * CELL_PX + 1
    val px = IntArray(w * h)
    for (yy in 0 until h) {
        val gy = yy.toFloat() / CELL_PX
        val j0 = floor(gy).toInt().coerceAtMost(rows - 2)
        val fy = gy - j0
        for (xx in 0 until w) {
            val gx = xx.toFloat() / CELL_PX
            val i0 = floor(gx).toInt().coerceAtMost(cols - 2)
            val fx = gx - i0
            val v = bilinear(values, base, cols, i0, j0, fx, fy)
            px[yy * w + xx] = ramp.argb(v)
        }
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

private fun bilinear(v: FloatArray, base: Int, cols: Int, i: Int, j: Int, fx: Float, fy: Float): Float {
    val a = v[base + j * cols + i]
    val b = v[base + j * cols + i + 1]
    val c = v[base + (j + 1) * cols + i]
    val d = v[base + (j + 1) * cols + i + 1]
    if (a.isNaN() || b.isNaN() || c.isNaN() || d.isNaN()) {
        // Fall back to the nearest valid corner instead of punching a hole.
        val near = if (fx < 0.5f) (if (fy < 0.5f) a else c) else (if (fy < 0.5f) b else d)
        return if (!near.isNaN()) near else listOf(a, b, c, d).firstOrNull { !it.isNaN() } ?: Float.NaN
    }
    val top = a + (b - a) * fx
    val bottom = c + (d - c) * fx
    return top + (bottom - top) * fy
}

/**
 * Isobars by marching squares over a 4× bilinear upsample of the pressure grid.
 * Returns line segments as [u0, v0, u1, v1, …] in 0..1 grid space, and label anchors (u, v, value).
 */
class Isolines(val segments: FloatArray, val labels: FloatArray)

fun isobars(grid: FieldGrid, hour: Int, interval: Float = 4f): Isolines {
    val spec = grid.spec
    val n = spec.size
    val base = hour.coerceIn(0, grid.hours - 1) * n
    val up = 4
    val w = (spec.cols - 1) * up + 1
    val h = (spec.rows - 1) * up + 1
    val f = FloatArray(w * h)
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    for (yy in 0 until h) {
        val gy = yy.toFloat() / up
        val j0 = floor(gy).toInt().coerceAtMost(spec.rows - 2)
        for (xx in 0 until w) {
            val gx = xx.toFloat() / up
            val i0 = floor(gx).toInt().coerceAtMost(spec.cols - 2)
            val v = bilinear(grid.pressure, base, spec.cols, i0, j0, gx - i0, gy - j0)
            f[yy * w + xx] = v
            if (!v.isNaN()) {
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
        }
    }
    if (lo > hi) return Isolines(FloatArray(0), FloatArray(0))
    val seg = ArrayList<Float>()
    val labels = ArrayList<Float>()
    var level = ceil(lo / interval) * interval
    while (level <= hi) {
        val before = seg.size
        for (yy in 0 until h - 1) for (xx in 0 until w - 1) {
            val a = f[yy * w + xx]           // top-left
            val b = f[yy * w + xx + 1]       // top-right
            val c = f[(yy + 1) * w + xx + 1] // bottom-right
            val d = f[(yy + 1) * w + xx]     // bottom-left
            if (a.isNaN() || b.isNaN() || c.isNaN() || d.isNaN()) continue
            var idx = 0
            if (a >= level) idx = idx or 8
            if (b >= level) idx = idx or 4
            if (c >= level) idx = idx or 2
            if (d >= level) idx = idx or 1
            if (idx == 0 || idx == 15) continue
            // Edge crossing points in cell coordinates.
            fun top() = floatArrayOf(xx + t(a, b, level), yy.toFloat())
            fun right() = floatArrayOf(xx + 1f, yy + t(b, c, level))
            fun bottom() = floatArrayOf(xx + t(d, c, level), yy + 1f)
            fun left() = floatArrayOf(xx.toFloat(), yy + t(a, d, level))
            fun line(p: FloatArray, q: FloatArray) {
                seg += p[0] / (w - 1); seg += p[1] / (h - 1); seg += q[0] / (w - 1); seg += q[1] / (h - 1)
            }
            when (idx) {
                1, 14 -> line(left(), bottom())
                2, 13 -> line(bottom(), right())
                3, 12 -> line(left(), right())
                4, 11 -> line(top(), right())
                6, 9 -> line(top(), bottom())
                7, 8 -> line(left(), top())
                5 -> { line(left(), top()); line(bottom(), right()) }
                10 -> { line(top(), right()); line(left(), bottom()) }
            }
        }
        // One label per level, on the segment closest to the middle of its run.
        val count = (seg.size - before) / 4
        if (count > 6) {
            val mid = before + (count / 2) * 4
            labels += (seg[mid] + seg[mid + 2]) / 2; labels += (seg[mid + 1] + seg[mid + 3]) / 2; labels += level
        }
        level += interval
    }
    return Isolines(seg.toFloatArray(), labels.toFloatArray())
}

private fun t(a: Float, b: Float, level: Float): Float {
    val d = b - a
    return if (d == 0f) 0.5f else ((level - a) / d).coerceIn(0f, 1f)
}

/** Category colours for tropical cyclones (TD, TS, Cat 1–5). */
fun stormColor(storm: Storm): Color = when (storm.category) {
    0 -> if (storm.intensityKt >= 34) Color(0xFF35C7B0) else Color(0xFF4A9EFF)
    1 -> Color(0xFFFFD84D)
    2 -> Color(0xFFFFA63D)
    3 -> Color(0xFFFF6B3D)
    4 -> Color(0xFFE5304D)
    else -> Color(0xFFC2185B)
}


