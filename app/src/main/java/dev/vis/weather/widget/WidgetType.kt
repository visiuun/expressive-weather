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
 * Glance can't use font files, so big numbers are drawn into bitmaps with the app's chunky cut of
 * Google Sans Flex and shown with Image. Bitmaps are white masks: tint them with a theme colour.
 */
object ChunkyText {
    /** The app's "chunky" cut: wide, semi-bold, fully rounded. opsz is added per size. */
    private const val AXES = "'wdth' 118, 'wght' 600, 'ROND' 100"

    /** Largest bitmap we render (px); bigger numbers are drawn smaller and scaled up. ~0.7 MB. */
    private const val MAX_AREA = 180_000

    class Glyphs(val bitmap: Bitmap, val widthPx: Float, val heightPx: Float)

    @Volatile private var flex: Typeface? = null
    private val cache = object : LruCache<String, Glyphs>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Glyphs) = value.bitmap.byteCount
    }

    private fun typeface(context: Context): Typeface? {
        if (Build.VERSION.SDK_INT < 26) return null
        flex?.let { return it }
        return runCatching { ResourcesCompat.getFont(context.applicationContext, R.font.google_sans_flex) }.getOrNull()?.also { flex = it }
    }

    /**
     * [sizePx] is the font size; [maxWidthPx] shrinks the text to fit. [opsz] is the font's optical size
     * (display cut for big numbers, sturdier text cut for small ones).
     */
    fun render(context: Context, text: String, sizePx: Float, maxWidthPx: Float, opsz: Int): Glyphs {
        val key = "$text|${sizePx.toInt()}|${maxWidthPx.toInt()}|$opsz"
        cache.get(key)?.let { return it }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { color = android.graphics.Color.WHITE }
        val face = typeface(context)
        if (face != null && Build.VERSION.SDK_INT >= 26) {
            p.typeface = face
            p.fontVariationSettings = "$AXES, 'opsz' $opsz"
        } else {
            p.typeface = Typeface.DEFAULT_BOLD
        }
        p.textSize = sizePx
        val b = Rect()
        p.getTextBounds(text, 0, text.length, b)
        if (b.width() > maxWidthPx && maxWidthPx > 0) {
            p.textSize = sizePx * maxWidthPx / b.width()
            p.getTextBounds(text, 0, text.length, b)
        }
        // A fixed top (digit height) so values of different lengths line up.
        val z = Rect()
        p.getTextBounds("0", 0, 1, z)
        val pad = ceil(p.textSize * 0.04f)
        val top = min(b.top, z.top) - pad
        val bottom = max(b.bottom, 0) + pad
        val left = b.left - pad
        val right = b.right + pad
        val w = right - left
        val h = bottom - top
        val k = min(1f, sqrt(MAX_AREA / (w * h).coerceAtLeast(1f)))
        val bmp = createBitmap(ceil(w * k).toInt().coerceAtLeast(1), ceil(h * k).toInt().coerceAtLeast(1))
        Canvas(bmp).apply {
            scale(k, k)
            drawText(text, -left, -top, p)
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
