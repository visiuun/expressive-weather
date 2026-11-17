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
enum class WidgetShape(val label: String, val stretch: Boolean = false, val polygon: () -> RoundedPolygon) {
    COOKIE_9("Cookie 9", polygon = { MaterialShapes.Cookie9Sided }),
    CIRCLE("Circle", polygon = { MaterialShapes.Circle }),
    SQUARE("Square", stretch = true, polygon = { MaterialShapes.Square }),
    PILL("Pill", polygon = { MaterialShapes.Pill }),
    COOKIE_4("Cookie 4", polygon = { MaterialShapes.Cookie4Sided }),
    COOKIE_6("Cookie 6", polygon = { MaterialShapes.Cookie6Sided }),
    COOKIE_7("Cookie 7", polygon = { MaterialShapes.Cookie7Sided }),
    COOKIE_12("Cookie 12", polygon = { MaterialShapes.Cookie12Sided }),
    CLOVER_4("Clover 4", polygon = { MaterialShapes.Clover4Leaf }),
    CLOVER_8("Clover 8", polygon = { MaterialShapes.Clover8Leaf }),
    SUNNY("Sunny", polygon = { MaterialShapes.Sunny }),
    VERY_SUNNY("Very sunny", polygon = { MaterialShapes.VerySunny }),
    SOFT_BURST("Soft burst", polygon = { MaterialShapes.SoftBurst }),
    SOFT_BOOM("Soft boom", polygon = { MaterialShapes.SoftBoom }),
    FLOWER("Flower", polygon = { MaterialShapes.Flower }),
    PIXEL_CIRCLE("Pixel circle", polygon = { MaterialShapes.PixelCircle }),
    GEM("Gem", polygon = { MaterialShapes.Gem }),
    PENTAGON("Pentagon", polygon = { MaterialShapes.Pentagon }),
    DIAMOND("Diamond", polygon = { MaterialShapes.Diamond }),
    SLANTED("Slanted", polygon = { MaterialShapes.Slanted }),
    BUN("Bun", polygon = { MaterialShapes.Bun }),
    GHOST("Ghost", polygon = { MaterialShapes.Ghostish }),
    ARCH("Arch", polygon = { MaterialShapes.Arch }),
    CLAMSHELL("Clamshell", polygon = { MaterialShapes.ClamShell }),
    OVAL("Oval", polygon = { MaterialShapes.Oval }),
    HEART("Heart", polygon = { MaterialShapes.Heart }),
    ;

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: COOKIE_9
    }
}

/** Where a shape widget's colours come from. */
enum class ShapeColor(val label: String, val swatch: Long = 0, val onSwatch: Long = 0) {
    /** Wallpaper colours on Android 12+, the app's palette below; black at night with the OLED option. */
    THEME("Wallpaper"),
    /** A gradient that follows the current sky, like the weather icons. */
    SKY("Sky"),
    BLACK("Black", 0xFF000000, 0xFFFFFFFF),
    WHITE("White", 0xFFFFFFFF, 0xFF1B1B1F),
    CORAL("Coral", 0xFFFFB4A6, 0xFF5C1408),
    AMBER("Amber", 0xFFFFD787, 0xFF462B00),
    MINT("Mint", 0xFFA8EBC9, 0xFF003823),
    SKY_BLUE("Sky blue", 0xFFA9D4FF, 0xFF002F57),
    ROSE("Rose", 0xFFFFB3C8, 0xFF5A0F2A),
    SLATE("Slate", 0xFF37424F, 0xFFE6EDF5),
    ;

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
