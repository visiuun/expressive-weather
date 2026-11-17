package dev.vis.weather.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import dev.vis.weather.data.PaletteStyle

/**
 * A hidden, hand-tuned colour theme. Some exist only in dark ([lightAvailable] = false) or only in light;
 * picking one forces that mode. [sky] tints the live sky toward the theme.
 */
class SecretTheme internal constructor(
    val id: String,
    val name: String,
    val blurb: String,
    internal val code: String,
    /** Preview colours: background, primary, secondary accent, tertiary accent. */
    val swatches: List<Color>,
    val lightAvailable: Boolean,
    val darkAvailable: Boolean,
    val sky: Color,
    private val build: (dark: Boolean) -> ColorScheme,
) {
    private val cache = arrayOfNulls<ColorScheme>(2)

    /** Resolves the theme mode: the requested one when available, otherwise the only one this theme has. */
    fun resolveDark(requestedDark: Boolean): Boolean = when {
        requestedDark && darkAvailable -> true
        !requestedDark && lightAvailable -> false
        else -> darkAvailable
    }

    fun scheme(requestedDark: Boolean): ColorScheme {
        val dark = resolveDark(requestedDark)
        val i = if (dark) 1 else 0
        return cache[i] ?: build(dark).also { cache[i] = it }
    }
}

object SecretThemes {
    private const val PREFS = "look"
    private const val KEY = "unlocked_secret_themes"

    /** Bumped on every unlock so composables that list unlocked themes recompose. */
    val version = mutableIntStateOf(0)

    private fun c(v: Long) = Color(v)

    val all: List<SecretTheme> = listOf(
        SecretTheme(
            id = "terminal", name = "Terminal", blurb = "Phosphor green on true black. For the root user in you.",
            code = "sudo",
            swatches = listOf(c(0xFF000000), c(0xFF39FF6A), c(0xFF7CE69A), c(0xFFB8FF3C)),
            lightAvailable = false, darkAvailable = true, sky = c(0xFF0B3A18),
        ) { _ ->
            SchemeGen.scheme(SchemeGen.Spec(0xFF33FF66.toInt(), PaletteStyle.VIBRANT, 0xFFB8FF3C.toInt()), true).copy(
                primary = c(0xFF39FF6A), onPrimary = c(0xFF00210A),
                primaryContainer = c(0xFF0B3A18), onPrimaryContainer = c(0xFF8BFFA8),
                secondary = c(0xFF7CE69A), onSecondary = c(0xFF00210A),
                secondaryContainer = c(0xFF0E2A16), onSecondaryContainer = c(0xFFA9F5BE),
                tertiary = c(0xFFB8FF3C), onTertiary = c(0xFF1A2600),
                tertiaryContainer = c(0xFF253A05), onTertiaryContainer = c(0xFFD6FF8F),
                background = Color.Black, onBackground = c(0xFFB6F5C6),
                surface = Color.Black, onSurface = c(0xFFB6F5C6),
                surfaceVariant = c(0xFF0F1F14), onSurfaceVariant = c(0xFF6FBF86),
                surfaceContainerLowest = Color.Black, surfaceContainerLow = c(0xFF050F08),
                surfaceContainer = c(0xFF08140C), surfaceContainerHigh = c(0xFF0C1A10),
                surfaceContainerHighest = c(0xFF112116), surfaceDim = Color.Black, surfaceBright = c(0xFF16291B),
                outline = c(0xFF2F7A47), outlineVariant = c(0xFF1B3F26),
                inverseSurface = c(0xFFB6F5C6), inverseOnSurface = Color.Black, inversePrimary = c(0xFF006E25),
                surfaceTint = c(0xFF39FF6A),
            )
        },
        SecretTheme(
            id = "ember", name = "Ember", blurb = "Matte black and a single hot coal of red.",
            code = "kindle",
            swatches = listOf(c(0xFF0E0E0E), c(0xFFFF3300), c(0xFFFF8A65), c(0xFFFFB199)),
            lightAvailable = true, darkAvailable = true, sky = c(0xFF5C1400),
        ) { dark ->
            if (dark) {
                SchemeGen.scheme(SchemeGen.Spec(0xFFFF3300.toInt(), PaletteStyle.VIBRANT, 0xFFFF8A65.toInt(), 80.0), true).copy(
                    primary = c(0xFFFF3300), onPrimary = Color.White,
                    primaryContainer = c(0xFF6B1700), onPrimaryContainer = c(0xFFFFDBD0),
                    secondary = c(0xFFFF8A65), onSecondary = c(0xFF3D0B00),
                    secondaryContainer = c(0xFF4A1508), onSecondaryContainer = c(0xFFFFD5C8),
                    tertiary = c(0xFFFFB199), onTertiary = c(0xFF4A1300),
                    tertiaryContainer = c(0xFF5A1E0C), onTertiaryContainer = c(0xFFFFDBD0),
                    background = c(0xFF0E0E0E), onBackground = c(0xFFEDE7E5),
                    surface = c(0xFF0E0E0E), onSurface = c(0xFFEDE7E5),
                    surfaceVariant = c(0xFF262424), onSurfaceVariant = c(0xFFBDB3B0),
                    surfaceContainerLowest = c(0xFF090909), surfaceContainerLow = c(0xFF141414),
                    surfaceContainer = c(0xFF1A1A1A), surfaceContainerHigh = c(0xFF222121),
                    surfaceContainerHighest = c(0xFF2B2A2A), surfaceDim = c(0xFF0E0E0E), surfaceBright = c(0xFF333131),
                    outline = c(0xFF8A7F7C), outlineVariant = c(0xFF3D3836),
                    inversePrimary = c(0xFFB82500), surfaceTint = c(0xFFFF3300),
                )
            } else {
                SchemeGen.scheme(SchemeGen.Spec(0xFFFF3300.toInt(), PaletteStyle.VIBRANT, 0xFFFF8A65.toInt(), 80.0), false).copy(
                    primary = c(0xFFD42A00), onPrimary = Color.White,
                    primaryContainer = c(0xFFFFDBD0), onPrimaryContainer = c(0xFF3D0B00),
                    background = c(0xFFFBF9F8), surface = c(0xFFFBF9F8),
                    surfaceContainerLowest = Color.White, surfaceContainerLow = c(0xFFF5F2F1),
                    surfaceContainer = c(0xFFEFECEB), surfaceContainerHigh = c(0xFFE9E6E5),
                    surfaceContainerHighest = c(0xFFE3E0DF), surfaceTint = c(0xFFD42A00),
                )
            }
        },
        SecretTheme(
            id = "glacier", name = "Glacier", blurb = "Blue ice, white light and very cold air.",
            code = "permafrost",
            swatches = listOf(c(0xFFF2FAFC), c(0xFF0F8FB0), c(0xFF8FD8EC), c(0xFFB9C9F2)),
            lightAvailable = true, darkAvailable = true, sky = c(0xFF8FD8EC),
        ) { dark -> SchemeGen.scheme(SchemeGen.Spec(0xFF4FC3E0.toInt(), PaletteStyle.EXPRESSIVE, 0xFF8FB4F0.toInt(), 44.0), dark) },
        SecretTheme(
            id = "sakura", name = "Sakura", blurb = "Cherry blossom pink with fresh spring leaves.",
            code = "hanami",
            swatches = listOf(c(0xFFFFF7F8), c(0xFFC2416E), c(0xFFF2A7C0), c(0xFF6FA35E)),
            lightAvailable = true, darkAvailable = true, sky = c(0xFFF2A7C0),
        ) { dark -> SchemeGen.scheme(SchemeGen.Spec(0xFFF06C9B.toInt(), PaletteStyle.EXPRESSIVE, 0xFF6FA35E.toInt(), 52.0), dark) },
        SecretTheme(
            id = "neon_dusk", name = "Neon Dusk", blurb = "Hot pink and electric cyan over a midnight city.",
            code = "afterglow",
            swatches = listOf(c(0xFF07080F), c(0xFFFF3D9A), c(0xFF22E5FF), c(0xFFFFB13D)),
            lightAvailable = false, darkAvailable = true, sky = c(0xFF3A0F2C),
        ) { _ ->
            SchemeGen.scheme(SchemeGen.Spec(0xFFFF3D9A.toInt(), PaletteStyle.VIBRANT, 0xFF22E5FF.toInt(), 90.0), true).copy(
                primary = c(0xFFFF5CA8), onPrimary = c(0xFF3D0020),
                primaryContainer = c(0xFF6B0F3C), onPrimaryContainer = c(0xFFFFD8E7),
                secondary = c(0xFFFFB13D), onSecondary = c(0xFF3A2200),
                secondaryContainer = c(0xFF4A2D05), onSecondaryContainer = c(0xFFFFDDB3),
                tertiary = c(0xFF22E5FF), onTertiary = c(0xFF00323A),
                tertiaryContainer = c(0xFF004B57), onTertiaryContainer = c(0xFFA8F3FF),
                background = c(0xFF07080F), onBackground = c(0xFFE8E6F0),
                surface = c(0xFF07080F), onSurface = c(0xFFE8E6F0),
                surfaceContainerLowest = c(0xFF040509), surfaceContainerLow = c(0xFF0C0E18),
                surfaceContainer = c(0xFF11131F), surfaceContainerHigh = c(0xFF171A28),
                surfaceContainerHighest = c(0xFF1E2131), surfaceDim = c(0xFF07080F), surfaceBright = c(0xFF262A3C),
                surfaceVariant = c(0xFF1E2131), onSurfaceVariant = c(0xFFB0B3C6),
                outline = c(0xFF5C6078), outlineVariant = c(0xFF2A2D40),
            )
        },
        SecretTheme(
            id = "brass", name = "Brass", blurb = "Polished brass, green verdigris and gaslight.",
            code = "clockwork",
            swatches = listOf(c(0xFF111412), c(0xFFE3B341), c(0xFF5FA88A), c(0xFFD9C6A0)),
            lightAvailable = true, darkAvailable = true, sky = c(0xFF6B5418),
        ) { dark ->
            val s = SchemeGen.scheme(SchemeGen.Spec(0xFFC9A227.toInt(), PaletteStyle.EXPRESSIVE, 0xFF3E8A6E.toInt(), 60.0), dark)
            if (dark) s.copy(
                primary = c(0xFFE3B341), onPrimary = c(0xFF2A1F00),
                background = c(0xFF101312), surface = c(0xFF101312),
                surfaceContainerLowest = c(0xFF0B0D0C), surfaceContainerLow = c(0xFF161A18),
                surfaceContainer = c(0xFF1B201D), surfaceContainerHigh = c(0xFF232926),
                surfaceContainerHighest = c(0xFF2C322F), surfaceDim = c(0xFF101312), surfaceBright = c(0xFF353C38),
            ) else s
        },
        SecretTheme(
            id = "moss", name = "Moss", blurb = "Forest floor greens and late-afternoon gold.",
            code = "lichen",
            swatches = listOf(c(0xFFF6F8F1), c(0xFF4C7A2A), c(0xFFA9C98A), c(0xFFC9A85C)),
            lightAvailable = true, darkAvailable = true, sky = c(0xFF5E8C3A),
        ) { dark -> SchemeGen.scheme(SchemeGen.Spec(0xFF5E8C3A.toInt(), PaletteStyle.BALANCED, 0xFFC9A85C.toInt(), 40.0), dark) },
        SecretTheme(
            id = "paper", name = "Paper", blurb = "Ink on paper. No colour, no distractions.",
            code = "inkwell",
            swatches = listOf(c(0xFFF3F0E8), c(0xFF1A1A1A), c(0xFF6B6B6B), c(0xFFB0ADA5)),
            lightAvailable = true, darkAvailable = true, sky = c(0xFF8A8780),
        ) { dark ->
            val g = SchemeGen.scheme(SchemeGen.Spec(0xFF8A8780.toInt(), PaletteStyle.BALANCED, null, 0.0), dark)
            // Monochrome: every accent role becomes a grey.
            val s = g.copy(
                secondary = g.onSurfaceVariant, onSecondary = g.surface,
                secondaryContainer = g.surfaceContainerHighest, onSecondaryContainer = g.onSurface,
                tertiary = g.onSurfaceVariant, onTertiary = g.surface,
                tertiaryContainer = g.surfaceContainerHigh, onTertiaryContainer = g.onSurface,
                surfaceTint = g.onSurface,
            )
            if (dark) s.copy(
                primary = c(0xFFEDEDED), onPrimary = c(0xFF121212),
                primaryContainer = c(0xFF3A3A3A), onPrimaryContainer = c(0xFFF2F2F2),
                background = c(0xFF121212), surface = c(0xFF121212),
            ) else s.copy(
                primary = c(0xFF1A1A1A), onPrimary = c(0xFFF3F0E8),
                primaryContainer = c(0xFFDEDAD0), onPrimaryContainer = c(0xFF1A1A1A),
                background = c(0xFFF3F0E8), surface = c(0xFFF3F0E8),
                surfaceContainerLowest = c(0xFFFAF8F3), surfaceContainerLow = c(0xFFEDEAE1),
                surfaceContainer = c(0xFFE8E4DA), surfaceContainerHigh = c(0xFFE2DED3),
                surfaceContainerHighest = c(0xFFDCD8CC),
            )
        },
        SecretTheme(
            id = "lagoon", name = "Lagoon", blurb = "Turquoise shallows and coral reefs.",
            code = "atoll",
            swatches = listOf(c(0xFFF2FBFA), c(0xFF00838A), c(0xFF5FD4CF), c(0xFFFF7F66)),
            lightAvailable = true, darkAvailable = true, sky = c(0xFF2FB8B4),
        ) { dark -> SchemeGen.scheme(SchemeGen.Spec(0xFF00A6A6.toInt(), PaletteStyle.VIBRANT, 0xFFFF7F66.toInt()), dark) },
    )

    fun byId(id: String?): SecretTheme? = if (id == null) null else all.firstOrNull { it.id == id }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Call with whatever the user typed into the city search. If it is one of the secret code words,
     * the theme is remembered as unlocked and returned (also when it was unlocked before); otherwise null.
     */
    fun unlock(context: Context, code: String): SecretTheme? {
        val key = normalize(code)
        if (key.length < 4) return null
        val theme = all.firstOrNull { it.code == key } ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY, emptySet()).orEmpty()
        if (theme.id !in set) {
            prefs.edit().putStringSet(KEY, set + theme.id).apply()
            version.intValue++
        }
        return theme
    }

    fun unlocked(context: Context): List<SecretTheme> {
        val set = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
        return all.filter { it.id in set }
    }
}
