@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.toPath
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import dev.vis.weather.data.Sky
import dev.vis.weather.data.WeatherInfo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bitmaps for widgets. RemoteViews can't host a Compose Canvas, so the in-app glyphs from
 * WeatherIcon.kt are re-drawn here with android.graphics.
 *
 * "Mask" bitmaps are drawn in white with varying alpha and meant to be tinted with a theme
 * colour through ColorFilter.tint, so they follow light/dark and Material You on the launcher.
 */
object WidgetArt {
    private val cache = LruCache<String, Bitmap>(96)

    private inline fun cached(key: String, make: () -> Bitmap): Bitmap =
        cache.get(key) ?: make().also { cache.put(key, it) }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private const val SUN = 0xFFFFB627.toInt()
    private const val MOON = 0xFFE8E4D8.toInt()
    private const val RAIN = 0xFF4A9EFF.toInt()
    private const val BOLT = 0xFFFFD23F.toInt()
    // Mid-tones that read on both light and dark launcher wallpapers / widget backgrounds.
    private const val CLOUD_LIGHT = 0xFFD3DBE5.toInt()
    private const val CLOUD_DARK = 0xFF98A4B3.toInt()
    private const val SNOW = 0xFF9ACDF2.toInt()

    private val sunPath: Path by lazy { MaterialShapes.Sunny.toPath(Path()) }

    /** Weather glyph. With [mono] it's a white mask (tint it with onSurface). */
    fun weather(code: Int, isDay: Boolean, px: Int, mono: Boolean = false): Bitmap =
        cached("w$code$isDay$px$mono") { drawWeather(code, isDay, px.coerceIn(24, 256), mono) }

    private fun drawWeather(code: Int, isDay: Boolean, px: Int, mono: Boolean): Bitmap {
        val bmp = createBitmap(px, px)
        val c = Canvas(bmp)
        val s = px.toFloat()
        val white = 0xFFFFFFFF.toInt()
        val sun = if (mono) white else SUN
        val moon = if (mono) white else MOON
        val rain = if (mono) 0xD9FFFFFF.toInt() else RAIN
        val bolt = if (mono) white else BOLT
        val cloudLight = if (mono) 0xFFFFFFFF.toInt() else CLOUD_LIGHT
        val cloudDark = if (mono) 0x8CFFFFFF.toInt() else CLOUD_DARK
        val snow = if (mono) white else SNOW
        val sky = WeatherInfo.sky(code)

        when (sky) {
            Sky.CLEAR -> if (isDay) c.sun(0f, 0f, s, sun) else c.moon(s * 0.12f, s * 0.12f, s * 0.76f, moon)
            Sky.PARTLY -> {
                if (isDay) c.sun(s * 0.28f, 0f, s * 0.68f, sun) else c.moon(s * 0.38f, s * 0.06f, s * 0.5f, moon)
                c.cloud(0f, s * 0.32f, s * 0.82f, cloudLight)
            }
            Sky.CLOUDY -> {
                c.cloud(s * 0.22f, s * 0.12f, s * 0.72f, cloudDark)
                c.cloud(0f, s * 0.3f, s * 0.86f, cloudLight)
            }
            Sky.FOG -> {
                c.cloud(s * 0.08f, s * 0.04f, s * 0.84f, cloudLight)
                val p = stroke(cloudDark, s * 0.07f)
                for (i in 0..2) {
                    val y = s * (0.72f + i * 0.11f)
                    val x0 = s * (0.12f + (i % 2) * 0.08f)
                    c.drawLine(x0, y, x0 + s * 0.68f, y, p)
                }
            }
            Sky.DRIZZLE, Sky.RAIN, Sky.FREEZING -> {
                c.cloud(s * 0.06f, 0f, s * 0.88f, if (sky == Sky.RAIN) cloudDark else cloudLight)
                val heavy = code in listOf(65, 82, 63, 81)
                val n = if (sky == Sky.DRIZZLE) 3 else if (heavy) 4 else 3
                val drop = fill(rain)
                val streak = stroke(rain, s * 0.07f)
                for (i in 0 until n) {
                    val x = s * (0.26f + i * (0.5f / (n - 1).coerceAtLeast(1)))
                    val y = s * 0.66f + (i % 2) * s * 0.06f
                    if (sky == Sky.DRIZZLE) c.drawCircle(x, y + s * 0.06f, s * 0.045f, drop)
                    else c.drawLine(x, y, x - s * 0.06f, y + s * 0.2f, streak)
                }
                if (sky == Sky.FREEZING) c.flake(s * 0.78f, s * 0.86f, s * 0.11f, snow)
            }
            Sky.SNOW -> {
                c.cloud(s * 0.06f, 0f, s * 0.88f, cloudLight)
                c.flake(s * 0.3f, s * 0.78f, s * 0.11f, snow)
                c.flake(s * 0.56f, s * 0.86f, s * 0.11f, snow)
                c.flake(s * 0.8f, s * 0.74f, s * 0.09f, snow)
            }
            Sky.THUNDER -> {
                c.cloud(s * 0.06f, 0f, s * 0.88f, cloudDark)
                val p = Path().apply {
                    moveTo(s * 0.54f, s * 0.5f); lineTo(s * 0.36f, s * 0.78f); lineTo(s * 0.5f, s * 0.78f)
                    lineTo(s * 0.42f, s * 1.0f); lineTo(s * 0.68f, s * 0.68f); lineTo(s * 0.54f, s * 0.68f)
                    lineTo(s * 0.64f, s * 0.5f); close()
                }
                c.drawPath(p, fill(bolt))
                val streak = stroke(rain, s * 0.06f)
                c.drawLine(s * 0.26f, s * 0.7f, s * 0.21f, s * 0.86f, streak)
                c.drawLine(s * 0.8f, s * 0.68f, s * 0.75f, s * 0.84f, streak)
            }
        }
        return bmp
    }

    private fun Canvas.sun(x: Float, y: Float, size: Float, color: Int) {
        val p = Path(sunPath).apply { transform(Matrix().apply { setScale(size, size); postTranslate(x, y) }) }
        drawPath(p, fill(color))
    }

    private fun Canvas.moon(x: Float, y: Float, size: Float, color: Int) {
        val full = Path().apply { addOval(RectF(x, y, x + size, y + size), Path.Direction.CW) }
        val cut = Path().apply {
            val cx = x + size * 0.38f; val cy = y - size * 0.18f
            addOval(RectF(cx, cy, cx + size, cy + size), Path.Direction.CW)
        }
        full.op(cut, Path.Op.DIFFERENCE)
        drawPath(full, fill(color))
    }

    private fun Canvas.cloud(x: Float, y: Float, w: Float, color: Int) {
        val h = w * 0.62f
        // Build one path so semi-transparent mono clouds don't show overlaps.
        val p = Path().apply { addCircle(w * 0.28f, h * 0.6f, h * 0.36f, Path.Direction.CW) }
        p.op(Path().apply { addCircle(w * 0.55f, h * 0.48f, h * 0.48f, Path.Direction.CW) }, Path.Op.UNION)
        p.op(Path().apply { addCircle(w * 0.8f, h * 0.66f, h * 0.32f, Path.Direction.CW) }, Path.Op.UNION)
        p.op(Path().apply { addRoundRect(RectF(w * 0.1f, h * 0.6f, w * 0.92f, h * 0.98f), h * 0.19f, h * 0.19f, Path.Direction.CW) }, Path.Op.UNION)
        withTranslation(x, y) { drawPath(p, fill(color)) }
    }

    private fun Canvas.flake(cx: Float, cy: Float, r: Float, color: Int) {
        val p = stroke(color, r * 0.32f)
        for (i in 0 until 3) withRotation(i * 60f, cx, cy) { drawLine(cx, cy - r, cx, cy + r, p) }
    }

    /** Moon phase disc (fixed colours). phase: 0 = new, 0.5 = full. */
    fun moonPhase(phase: Double, southern: Boolean, px: Int): Bitmap =
        cached("m${(phase * 200).toInt()}$southern$px") {
            val bmp = createBitmap(px, px)
            val c = Canvas(bmp)
            val r = px / 2f * 0.96f
            val cx = px / 2f; val cy = px / 2f
            c.drawCircle(cx, cy, r, fill(0x664A4E57)) // translucent shadow side
            val k = cos(2 * PI * phase).toFloat()
            val waxing = phase < 0.5
            val rx = r * abs(k)
            val outer = RectF(cx - r, cy - r, cx + r, cy + r)
            val ell = RectF(cx - rx, cy - r, cx + rx, cy + r)
            val p = Path().apply {
                if (waxing) {
                    moveTo(cx, cy - r); arcTo(outer, -90f, 180f, false); arcTo(ell, 90f, if (k > 0) -180f else 180f, false)
                } else {
                    moveTo(cx, cy + r); arcTo(outer, 90f, 180f, false); arcTo(ell, -90f, if (k > 0) -180f else 180f, false)
                }
                close()
            }
            if (southern) c.scale(-1f, 1f, cx, cy)
            c.drawPath(p, fill(MOON))
            c.drawCircle(cx, cy, r, stroke(0x59E8E4D8, r * 0.05f))
            bmp
        }

    /**
     * Daylight arc as two masks: the dashed track, and the travelled part + sun dot.
     * [progress] is 0 at sunrise, 1 at sunset; outside that range only the track is drawn.
     */
    fun sunArc(progress: Float, w: Int, h: Int): Pair<Bitmap, Bitmap> {
        val pk = (progress.coerceIn(-0.01f, 1.01f) * 100).toInt()
        val track = cached("st$w$h") {
            val bmp = createBitmap(w, h)
            val c = Canvas(bmp)
            val sw = h * 0.045f
            val oval = arcOval(w, h, sw)
            val p = stroke(0xFFFFFFFF.toInt(), sw).apply {
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(sw * 0.2f, sw * 2.2f), 0f)
            }
            c.drawArc(oval, 180f, 180f, false, p)
            c.drawLine(0f, h - sw / 2, w.toFloat(), h - sw / 2, stroke(0x80FFFFFF.toInt(), sw * 0.6f))
            bmp
        }
        val fillBmp = cached("sf$w$h$pk") {
            val bmp = createBitmap(w, h)
            if (progress in 0f..1f) {
                val c = Canvas(bmp)
                val sw = h * 0.045f
                val oval = arcOval(w, h, sw)
                c.drawArc(oval, 180f, 180f * progress, false, stroke(0xFFFFFFFF.toInt(), sw * 1.4f))
                val a = PI * (1 - progress)
                val sx = oval.centerX() + oval.width() / 2 * cos(a).toFloat()
                val sy = oval.bottom - oval.height() * sin(a).toFloat()
                c.drawCircle(sx, sy, h * 0.1f, fill(0xFFFFFFFF.toInt()))
            }
            bmp
        }
        return track to fillBmp
    }

    private fun arcOval(w: Int, h: Int, sw: Float): RectF {
        val pad = h * 0.12f
        return RectF(pad, pad, w - pad, (h - sw) * 2 - pad)
    }

    /** Bar chart masks: full-height tracks and value bars. [values] are 0..1. */
    fun bars(values: List<Float>, w: Int, h: Int): Pair<Bitmap, Bitmap> {
        val key = values.joinToString(",") { (it * 50).toInt().toString() }
        val track = cached("bt${values.size}$w$h") {
            val bmp = createBitmap(w, h)
            val c = Canvas(bmp)
            eachBar(values.size, w) { l, r -> c.drawRoundRect(RectF(l, 0f, r, h.toFloat()), (r - l) / 2, (r - l) / 2, fill(0x59FFFFFF)) }
            bmp
        }
        val bars = cached("bv$key$w$h") {
            val bmp = createBitmap(w, h)
            val c = Canvas(bmp)
            var i = 0
            eachBar(values.size, w) { l, r ->
                val v = values[i++].coerceIn(0f, 1f)
                if (v > 0.005f) {
                    val bh = (h * v).coerceAtLeast(r - l)
                    c.drawRoundRect(RectF(l, h - bh, r, h.toFloat()), (r - l) / 2, (r - l) / 2, fill(0xFFFFFFFF.toInt()))
                }
            }
            bmp
        }
        return track to bars
    }

    private inline fun eachBar(n: Int, w: Int, draw: (Float, Float) -> Unit) {
        if (n == 0) return
        val slot = w.toFloat() / n
        val bw = slot * 0.62f
        for (i in 0 until n) {
            val l = i * slot + (slot - bw) / 2
            draw(l, l + bw)
        }
    }

    /** Arrow pointing where the wind blows *to* (meteorological [deg] is where it comes from). */
    fun windArrow(deg: Double, px: Int): Bitmap {
        val d = if (deg.isNaN()) 0 else ((deg + 180) % 360).toInt() / 5 * 5
        return cached("a$d$px") {
            val bmp = createBitmap(px, px)
            val c = Canvas(bmp)
            val s = px / 24f
            c.withRotation(d.toFloat(), px / 2f, px / 2f) {
                val p = Path().apply {
                    moveTo(12f * s, 2f * s); lineTo(4.5f * s, 20.29f * s); lineTo(5.21f * s, 21f * s)
                    lineTo(12f * s, 18f * s); lineTo(18.79f * s, 21f * s); lineTo(19.5f * s, 20.29f * s); close()
                }
                drawPath(p, fill(0xFFFFFFFF.toInt()))
            }
            bmp
        }
    }

    /** AQI gauge ring in the band colour, 270° sweep. */
    fun aqiRing(value: Double, max: Double, color: Int, px: Int): Bitmap =
        cached("q${value.toInt()}${max.toInt()}$color$px") {
            val bmp = createBitmap(px, px)
            val c = Canvas(bmp)
            val sw = px * 0.1f
            val o = RectF(sw, sw, px - sw, px - sw)
            c.drawArc(o, 135f, 270f, false, stroke(0x40888888, sw))
            val frac = if (value.isNaN()) 0f else (value / max).toFloat().coerceIn(0.02f, 1f)
            c.drawArc(o, 135f, 270f * frac, false, stroke(color, sw))
            bmp
        }
}
