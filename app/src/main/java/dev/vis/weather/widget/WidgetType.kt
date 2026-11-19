package dev.vis.weather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import dev.vis.weather.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Glance can't use font files, so big numbers are drawn into bitmaps with the same Google Sans Flex
 * cut the app uses (font style, rounded or sharp edges, weight and optical size follow the user's
 * Appearance settings) and shown with Image. Bitmaps are white masks: tint them with a theme colour.
 * A trailing "°" is drawn like the app's hero: smaller and raised.
 */
object ChunkyText {
    /** Largest bitmap we render (px); bigger numbers are drawn smaller and scaled up. ~0.7 MB. */
    private const val MAX_AREA = 180_000

    class Glyphs(val bitmap: Bitmap, val widthPx: Float, val heightPx: Float)

    private val faces = HashMap<String, Typeface?>()
    private val cache = object : LruCache<String, Glyphs>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Glyphs) = value.bitmap.byteCount
    }

    /** Axes matching [dev.vis.weather.ui.theme.AppType] for a display-size number (hero uses Medium + shift). */
    private fun axes(s: dev.vis.weather.data.Settings, opsz: Int, weight: Int? = null): String {
        val chunky = s.fontStyle == dev.vis.weather.data.FontStyle.CHUNKY
        val wdth = when (s.fontStyle) {
            dev.vis.weather.data.FontStyle.DEFAULT -> 100
            dev.vis.weather.data.FontStyle.CHUNKY -> if (opsz >= 36) 118 else 125
            dev.vis.weather.data.FontStyle.TALL -> 76
        }
        val wght = weight ?: (500 + if (chunky) 100 else 0)
        val rond = if (s.fontEdges == dev.vis.weather.data.FontEdges.ROUNDED) 100 else 0
        return "'wght' $wght, 'wdth' $wdth, 'ROND' $rond, 'opsz' $opsz, 'slnt' 0"
    }

    /** A typeface with the axes baked in (API 29+ Font.Builder applies them reliably), else null. */
    private fun face(context: Context, axes: String): Typeface? = synchronized(faces) {
        faces.getOrPut(axes) {
            if (Build.VERSION.SDK_INT >= 29) runCatching {
                val font = android.graphics.fonts.Font.Builder(context.applicationContext.resources, R.font.google_sans_flex)
                    .setFontVariationSettings(axes).build()
                Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(font).build()).build()
            }.getOrNull() else null
        }
    }

    private fun paint(context: Context, axes: String, sizePx: Float): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { color = android.graphics.Color.WHITE; textSize = sizePx }
        val baked = face(context, axes)
        when {
            baked != null -> p.typeface = baked
            Build.VERSION.SDK_INT >= 26 -> {
                p.typeface = runCatching { ResourcesCompat.getFont(context.applicationContext, R.font.google_sans_flex) }.getOrNull() ?: Typeface.DEFAULT_BOLD
                p.fontVariationSettings = axes
            }
            else -> p.typeface = Typeface.DEFAULT_BOLD
        }
        return p
    }

    /**
     * [sizePx] is the font size; [maxWidthPx] shrinks the text to fit. [opsz] is the font's optical size
     * (display cut for big numbers, sturdier text cut for small ones).
     */
    fun render(context: Context, text: String, sizePx: Float, maxWidthPx: Float, opsz: Int): Glyphs {
        val settings = dev.vis.weather.data.Prefs(context).settings.value
        val ax = axes(settings, opsz)
        val key = "$text|${sizePx.toInt()}|${maxWidthPx.toInt()}|$ax"
        cache.get(key)?.let { return it }

        val degree = text.endsWith("°") && text.length > 1
        val body = if (degree) text.dropLast(1) else text
        val display = opsz >= 36
        val p = paint(context, ax, sizePx)
        // The hero's tight tracking (-6 sp at 136 sp).
        if (display && settings.fontStyle == dev.vis.weather.data.FontStyle.CHUNKY) p.letterSpacing = -0.044f
        val dp = if (degree) paint(context, axes(settings, (opsz * 0.53f).toInt().coerceAtLeast(9), if (settings.fontStyle == dev.vis.weather.data.FontStyle.CHUNKY) 500 else 400), sizePx * 0.53f) else null

        fun measure(): Pair<Rect, Float> {
            val b = Rect(); p.getTextBounds(body, 0, body.length, b)
            var extra = 0f
            if (dp != null) {
                val db = Rect(); dp.getTextBounds("°", 0, 1, db)
                extra = p.textSize * 0.03f + db.width()
            }
            return b to extra
        }
        var (b, extra) = measure()
        if (maxWidthPx > 0 && b.width() + extra > maxWidthPx) {
            val k = maxWidthPx / (b.width() + extra)
            p.textSize *= k; dp?.let { it.textSize *= k }
            measure().let { b = it.first; extra = it.second }
        }
        // A fixed top (digit height) so values of different lengths line up.
        val z = Rect()
        p.getTextBounds("0", 0, 1, z)
        val pad = ceil(p.textSize * 0.04f)
        val top = min(b.top, z.top) - pad
        val bottom = max(b.bottom, 0) + pad
        val left = b.left - pad
        val right = b.right + extra + pad
        val w = right - left
        val h = bottom - top
        val k = min(1f, sqrt(MAX_AREA / (w * h).coerceAtLeast(1f)))
        val bmp = createBitmap(ceil(w * k).toInt().coerceAtLeast(1), ceil(h * k).toInt().coerceAtLeast(1))
        Canvas(bmp).apply {
            scale(k, k)
            drawText(body, -left, -top, p)
            if (dp != null) {
                val db = Rect(); dp.getTextBounds("°", 0, 1, db)
                // Top of the degree sits on the digits' cap line, like the app's raised "°".
                drawText("°", b.right - left + p.textSize * 0.03f - db.left, -top + z.top - db.top, dp)
            }
        }
        return Glyphs(bmp, w, h).also { cache.put(key, it) }
    }
}

/** Material icons (the rounded set the app uses) as white masks for widgets. */
object WidgetSymbols {
    private val cache = LruCache<String, Bitmap>(48)

    fun mask(icon: ImageVector, px: Int): Bitmap {
        val size = px.coerceIn(8, 192)
        val key = "${icon.name}$size"
        return cache.get(key) ?: draw(icon, size).also { cache.put(key, it) }
    }

    private fun draw(icon: ImageVector, px: Int): Bitmap {
        val bmp = createBitmap(px, px)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        val m = Matrix().apply { setScale(px / icon.viewportWidth, px / icon.viewportHeight) }
        fun walk(g: VectorGroup) {
            for (node in g) when (node) {
                is VectorGroup -> walk(node)
                is VectorPath -> {
                    val p: Path = node.pathData.toPath().asAndroidPath()
                    p.fillType = if (node.pathFillType == androidx.compose.ui.graphics.PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    p.transform(m)
                    paint.alpha = (node.fillAlpha * 255).toInt().coerceIn(0, 255)
                    c.drawPath(p, paint)
                }
            }
        }
        walk(icon.root)
        return bmp
    }
}
