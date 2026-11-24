@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import dev.vis.weather.L
import dev.vis.weather.R
import dev.vis.weather.data.Settings
import dev.vis.weather.data.Sky
import dev.vis.weather.data.WeatherInfo
import dev.vis.weather.ui.theme.DarkScheme
import dev.vis.weather.ui.theme.LightScheme

/*
 * Shared pieces of the "Weather shape" widget: the shape catalogue, colour modes, per-widget
 * config, and the geometry/bitmap helpers used both by the Glance widget and the config screen.
 */

/** M3 Expressive shapes a shape widget can take. [stretch] shapes fill a non-square widget instead of staying square. */
enum class WidgetShape(@StringRes private val res: Int, val stretch: Boolean = false, val polygon: () -> RoundedPolygon) {
    COOKIE_9(R.string.w_shape_cookie_9, polygon = { MaterialShapes.Cookie9Sided }),
    CIRCLE(R.string.w_shape_circle, polygon = { MaterialShapes.Circle }),
    SQUARE(R.string.w_shape_square, stretch = true, polygon = { MaterialShapes.Square }),
    PILL(R.string.w_shape_pill, polygon = { MaterialShapes.Pill }),
    COOKIE_4(R.string.w_shape_cookie_4, polygon = { MaterialShapes.Cookie4Sided }),
    COOKIE_6(R.string.w_shape_cookie_6, polygon = { MaterialShapes.Cookie6Sided }),
    COOKIE_7(R.string.w_shape_cookie_7, polygon = { MaterialShapes.Cookie7Sided }),
    COOKIE_12(R.string.w_shape_cookie_12, polygon = { MaterialShapes.Cookie12Sided }),
    CLOVER_4(R.string.w_shape_clover_4, polygon = { MaterialShapes.Clover4Leaf }),
    CLOVER_8(R.string.w_shape_clover_8, polygon = { MaterialShapes.Clover8Leaf }),
    SUNNY(R.string.w_shape_sunny, polygon = { MaterialShapes.Sunny }),
    VERY_SUNNY(R.string.w_shape_very_sunny, polygon = { MaterialShapes.VerySunny }),
    SOFT_BURST(R.string.w_shape_soft_burst, polygon = { MaterialShapes.SoftBurst }),
    SOFT_BOOM(R.string.w_shape_soft_boom, polygon = { MaterialShapes.SoftBoom }),
    FLOWER(R.string.w_shape_flower, polygon = { MaterialShapes.Flower }),
    PIXEL_CIRCLE(R.string.w_shape_pixel_circle, polygon = { MaterialShapes.PixelCircle }),
    GEM(R.string.w_shape_gem, polygon = { MaterialShapes.Gem }),
    PENTAGON(R.string.w_shape_pentagon, polygon = { MaterialShapes.Pentagon }),
    DIAMOND(R.string.w_shape_diamond, polygon = { MaterialShapes.Diamond }),
    SLANTED(R.string.w_shape_slanted, polygon = { MaterialShapes.Slanted }),
    BUN(R.string.w_shape_bun, polygon = { MaterialShapes.Bun }),
    GHOST(R.string.w_shape_ghost, polygon = { MaterialShapes.Ghostish }),
    ARCH(R.string.w_shape_arch, polygon = { MaterialShapes.Arch }),
    CLAMSHELL(R.string.w_shape_clamshell, polygon = { MaterialShapes.ClamShell }),
    OVAL(R.string.w_shape_oval, polygon = { MaterialShapes.Oval }),
    HEART(R.string.w_shape_heart, polygon = { MaterialShapes.Heart }),
    ;

    val label: String get() = L.s(res)

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: COOKIE_9
    }
}

/** Where a shape widget's colours come from. */
enum class ShapeColor(@StringRes private val res: Int, val swatch: Long = 0, val onSwatch: Long = 0) {
    /** Wallpaper colours on Android 12+, the app's palette below; black at night with the OLED option. */
    THEME(R.string.w_color_wallpaper),
    /** A gradient that follows the current sky, like the weather icons. */
    SKY(R.string.w_color_sky),
    BLACK(R.string.w_color_black, 0xFF000000, 0xFFFFFFFF),
    WHITE(R.string.w_color_white, 0xFFFFFFFF, 0xFF1B1B1F),
    CORAL(R.string.w_color_coral, 0xFFFFB4A6, 0xFF5C1408),
    AMBER(R.string.w_color_amber, 0xFFFFD787, 0xFF462B00),
    MINT(R.string.w_color_mint, 0xFFA8EBC9, 0xFF003823),
    SKY_BLUE(R.string.w_color_sky_blue, 0xFFA9D4FF, 0xFF002F57),
    ROSE(R.string.w_color_rose, 0xFFFFB3C8, 0xFF5A0F2A),
    SLATE(R.string.w_color_slate, 0xFF37424F, 0xFFE6EDF5),
    ;

    val label: String get() = L.s(res)

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: THEME
    }
}

/** Per-widget keys on top of [WidgetKeys] (which still holds the pinned place). */
object ShapeKeys {
    val SHAPE = stringPreferencesKey("shape_shape")
    val COLOR = stringPreferencesKey("shape_color")
    /** Background opacity, 0..1. */
    val OPACITY = floatPreferencesKey("shape_opacity")
    val SHOW_PLACE = booleanPreferencesKey("shape_place")
    val SHOW_HILO = booleanPreferencesKey("shape_hilo")
}

data class ShapeConfig(
    val shape: WidgetShape = WidgetShape.COOKIE_9,
    val color: ShapeColor = ShapeColor.THEME,
    val opacity: Float = 1f,
    val showPlace: Boolean = false,
    val showHiLo: Boolean = true,
    /** Pinned place key; "" follows the app. */
    val place: String = "",
) {
    companion object {
        fun from(p: Preferences) = ShapeConfig(
            shape = WidgetShape.of(p[ShapeKeys.SHAPE]),
            color = ShapeColor.of(p[ShapeKeys.COLOR]),
            opacity = (p[ShapeKeys.OPACITY] ?: 1f).coerceIn(0f, 1f),
            showPlace = p[ShapeKeys.SHOW_PLACE] ?: false,
            showHiLo = p[ShapeKeys.SHOW_HILO] ?: true,
            place = p[WidgetKeys.PLACE].orEmpty(),
        )
    }
}

/** Resolved colours for one widget: background (solid or gradient) and content, for light and dark launchers. */
class ShapePalette(
    val bgDay: Color, val bgNight: Color,
    val fgDay: Color, val fgNight: Color,
    /** Non-null for [ShapeColor.SKY]: gradient stops, same day and night. */
    val gradient: Pair<Color, Color>? = null,
    /** Draw weather icons as a tinted silhouette instead of in colour. */
    val monoIcon: Boolean,
) {
    fun bg(dark: Boolean) = if (dark) bgNight else bgDay
    fun fg(dark: Boolean) = if (dark) fgNight else fgDay

    companion object {
        fun schemes(context: Context): Pair<ColorScheme, ColorScheme> =
            if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) to dynamicDarkColorScheme(context)
            else LightScheme to DarkScheme

        fun resolve(context: Context, cfg: ShapeConfig, settings: Settings, code: Int, isDay: Boolean): ShapePalette =
            when (val c = cfg.color) {
                ShapeColor.THEME -> {
                    val (light, dark) = schemes(context)
                    ShapePalette(
                        light.primaryContainer, if (settings.oled) Color.Black else dark.primaryContainer,
                        light.onPrimaryContainer, if (settings.oled) dark.primary else dark.onPrimaryContainer,
                        monoIcon = settings.monoIcons,
                    )
                }
                ShapeColor.SKY -> {
                    val g = skyGradient(code, isDay)
                    ShapePalette(g.first, g.first, Color.White, Color.White, gradient = g, monoIcon = true)
                }
                ShapeColor.BLACK, ShapeColor.WHITE -> ShapePalette(
                    Color(c.swatch), Color(c.swatch), Color(c.onSwatch), Color(c.onSwatch), monoIcon = settings.monoIcons,
                )
                else -> ShapePalette(Color(c.swatch), Color(c.swatch), Color(c.onSwatch), Color(c.onSwatch), monoIcon = true)
            }

        /** Sky gradient (top-left → bottom-right) for a WMO code, matching the weather launcher icons. */
        fun skyGradient(code: Int, isDay: Boolean): Pair<Color, Color> {
            val (a, b) = when (WeatherInfo.sky(code)) {
                Sky.CLEAR -> if (isDay) 0xFFFFB43A to 0xFFFF6A1F else 0xFF0C1A3A to 0xFF24569A
                Sky.PARTLY -> if (isDay) 0xFF5AB4FA to 0xFF1C64C4 else 0xFF13284F to 0xFF2F619F
                Sky.CLOUDY -> if (isDay) 0xFF8A9DB2 to 0xFF4A5D74 else 0xFF3E4A5A to 0xFF1E2731
                Sky.FOG -> if (isDay) 0xFF9AA6B0 to 0xFF5E6C78 else 0xFF4A545E to 0xFF262D35
                Sky.DRIZZLE, Sky.RAIN, Sky.FREEZING -> if (isDay) 0xFF4E93DC to 0xFF1B3B6E else 0xFF22406A to 0xFF0D1A30
                Sky.SNOW -> if (isDay) 0xFF7CC6EC to 0xFF3B7FC0 else 0xFF2F5C88 to 0xFF14293F
                Sky.THUNDER -> 0xFF56677A to 0xFF1C252E
            }
            return Color(a) to Color(b)
        }
    }
}

/** Geometry shared by the widget bitmaps and the Compose previews. */
object ShapeGeometry {
    /** Scales [path] (any bounds) into a w×h box: centred and uniform, or filling it when [stretch]. */
    fun fit(path: Path, w: Float, h: Float, stretch: Boolean) {
        val b = RectF()
        @Suppress("DEPRECATION") path.computeBounds(b, true)
        if (b.width() <= 0f || b.height() <= 0f) return
        val m = Matrix()
        m.setRectToRect(b, RectF(0f, 0f, w, h), if (stretch) Matrix.ScaleToFit.FILL else Matrix.ScaleToFit.CENTER)
        path.transform(m)
    }

    fun path(polygon: RoundedPolygon, w: Float, h: Float, stretch: Boolean): Path =
        polygon.toPath(Path()).also { fit(it, w, h, stretch) }
}

/** Compose [Shape] for a polygon, or a morph between two at [progress]; fitted like the widget does it. */
class PolygonShape(
    private val morph: Morph,
    private val progress: Float,
    private val stretch: Boolean = false,
) : Shape {
    constructor(polygon: RoundedPolygon, stretch: Boolean = false) : this(Morph(polygon, polygon), 0f, stretch)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        morph.toPath(progress, p)
        ShapeGeometry.fit(p, size.width, size.height, stretch)
        return Outline.Generic(p.asComposePath())
    }
}

/** Bitmaps for the shape widget's background. */
object ShapeArt {
    private val cache = LruCache<String, Bitmap>(12)

    /** Largest side we render; RemoteViews have a bitmap memory budget. */
    private const val MAX_PX = 720

    private fun dims(wPx: Int, hPx: Int): Pair<Int, Int> {
        val k = (MAX_PX.toFloat() / maxOf(wPx, hPx)).coerceAtMost(1f)
        return (wPx * k).toInt().coerceAtLeast(8) to (hPx * k).toInt().coerceAtLeast(8)
    }

    /** White mask with alpha = [opacity]; tint it with the background colour. */
    fun mask(shape: WidgetShape, wPx: Int, hPx: Int, opacity: Float): Bitmap {
        val (w, h) = dims(wPx, hPx)
        val a = (opacity * 255).toInt().coerceIn(0, 255)
        val key = "m${shape.name}$w$h$a"
        return cache.get(key) ?: draw(shape, w, h) { it.color = Color.White.copy(alpha = a / 255f).toArgb() }.also { cache.put(key, it) }
    }

    /** Shape filled with a diagonal gradient, [opacity] applied. */
    fun gradient(shape: WidgetShape, wPx: Int, hPx: Int, opacity: Float, colors: Pair<Color, Color>): Bitmap {
        val (w, h) = dims(wPx, hPx)
        val a = (opacity * 255).toInt().coerceIn(0, 255)
        val key = "g${shape.name}$w$h$a${colors.first.toArgb()}${colors.second.toArgb()}"
        return cache.get(key) ?: draw(shape, w, h) {
            it.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors.first.toArgb(), colors.second.toArgb(), Shader.TileMode.CLAMP)
            it.alpha = a
        }.also { cache.put(key, it) }
    }

    private inline fun draw(shape: WidgetShape, w: Int, h: Int, paint: (Paint) -> Unit): Bitmap {
        val bmp = createBitmap(w, h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        paint(p)
        Canvas(bmp).drawPath(ShapeGeometry.path(shape.polygon(), w.toFloat(), h.toFloat(), shape.stretch), p)
        return bmp
    }
}
