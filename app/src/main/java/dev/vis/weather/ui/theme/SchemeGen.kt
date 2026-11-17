package dev.vis.weather.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import dev.vis.weather.data.PaletteStyle
import kotlin.math.max
import kotlin.math.min

/**
 * Full Material 3 colour schemes from a single seed, built on the HCT tonal palettes of
 * material-color-utilities (2025 "Expressive" colour spec).
 *
 * On top of the stock algorithm:
 *  - [accent] replaces the tertiary palette (e.g. electric yellow in a storm, sun gold on a partly cloudy day);
 *  - [primaryChroma] pins the primary chroma (charcoal storms and slate overcast stay grey, brand reds stay red);
 *  - purple guard: the stock tertiary hue rotation turns most blues into lavender. Unless the seed itself is purple,
 *    a tertiary landing in the purple band is swung back toward teal.
 */
object SchemeGen {

    data class Spec(
        val seed: Int,
        val style: PaletteStyle,
        val accent: Int? = null,
        val primaryChroma: Double? = null,
    )

    private val cache = object : LinkedHashMap<Pair<Spec, Boolean>, ColorScheme>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Spec, Boolean>, ColorScheme>?) = size > 96
    }

    fun scheme(spec: Spec, dark: Boolean): ColorScheme = synchronized(cache) {
        cache.getOrPut(spec to dark) { build(spec, dark) }
    }

    fun scheme(seed: Int, dark: Boolean, style: PaletteStyle): ColorScheme = scheme(customSpec(seed, style), dark)

    /** Spec for a user-picked colour: keeps saturated seeds (e.g. #FF3300) vivid instead of muddy. */
    fun customSpec(seed: Int, style: PaletteStyle): Spec {
        val c = Hct.fromInt(seed).chroma
        val chroma = when (style) {
            PaletteStyle.BALANCED -> max(32.0, min(c, 56.0))
            PaletteStyle.VIBRANT -> max(74.0, min(c, 96.0))
            PaletteStyle.EXPRESSIVE -> max(48.0, min(c, 64.0))
        }
        // Near-greys stay grey.
        return Spec(seed, style, primaryChroma = if (c < 12) c + 6 else chroma)
    }

    private fun isPurple(hue: Double) = hue >= 272.0 && hue < 335.0

    private fun build(spec: Spec, dark: Boolean): ColorScheme {
        val hct = Hct.fromInt(spec.seed)
        val v = ColorSpec.SpecVersion.SPEC_2025
        val base: DynamicScheme = when (spec.style) {
            PaletteStyle.BALANCED -> SchemeTonalSpot(hct, dark, 0.0, v)
            PaletteStyle.VIBRANT -> SchemeVibrant(hct, dark, 0.0, v)
            PaletteStyle.EXPRESSIVE -> SchemeExpressive(hct, dark, 0.0, v)
        }
        val primary = spec.primaryChroma?.let { TonalPalette.fromHueAndChroma(base.primaryPalette.hue, it) } ?: base.primaryPalette
        var tertiary = base.tertiaryPalette
        var secondary = base.secondaryPalette
        if (spec.accent != null) {
            val a = Hct.fromInt(spec.accent)
            tertiary = TonalPalette.fromHueAndChroma(a.hue, max(a.chroma, 36.0))
        } else if (!isPurple(hct.hue) && isPurple(tertiary.hue)) {
            tertiary = TonalPalette.fromHueAndChroma((hct.hue - 55.0 + 360.0) % 360.0, tertiary.chroma)
        }
        if (!isPurple(hct.hue) && isPurple(secondary.hue)) {
            secondary = TonalPalette.fromHueAndChroma(hct.hue, secondary.chroma)
        }
        val s = DynamicScheme(
            sourceColorHct = hct,
            variant = base.variant,
            isDark = dark,
            contrastLevel = 0.0,
            primaryPalette = primary,
            secondaryPalette = secondary,
            tertiaryPalette = tertiary,
            neutralPalette = base.neutralPalette,
            neutralVariantPalette = base.neutralVariantPalette,
            platform = DynamicScheme.Platform.PHONE,
            specVersion = v,
            errorPalette = base.errorPalette,
        )
        return s.toColorScheme()
    }

    private fun DynamicScheme.toColorScheme(): ColorScheme = ColorScheme(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(secondary), onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer), onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary), onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer), onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background), onBackground = Color(onBackground),
        surface = Color(surface), onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant), onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(surfaceTint),
        inverseSurface = Color(inverseSurface), inverseOnSurface = Color(inverseOnSurface),
        error = Color(error), onError = Color(onError),
        errorContainer = Color(errorContainer), onErrorContainer = Color(onErrorContainer),
        outline = Color(outline), outlineVariant = Color(outlineVariant),
        scrim = Color(scrim),
        surfaceBright = Color(surfaceBright), surfaceDim = Color(surfaceDim),
        surfaceContainer = Color(surfaceContainer), surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest), surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainerLowest = Color(surfaceContainerLowest),
        primaryFixed = Color(primaryFixed), primaryFixedDim = Color(primaryFixedDim),
        onPrimaryFixed = Color(onPrimaryFixed), onPrimaryFixedVariant = Color(onPrimaryFixedVariant),
        secondaryFixed = Color(secondaryFixed), secondaryFixedDim = Color(secondaryFixedDim),
        onSecondaryFixed = Color(onSecondaryFixed), onSecondaryFixedVariant = Color(onSecondaryFixedVariant),
        tertiaryFixed = Color(tertiaryFixed), tertiaryFixedDim = Color(tertiaryFixedDim),
        onTertiaryFixed = Color(onTertiaryFixed), onTertiaryFixedVariant = Color(onTertiaryFixedVariant),
    )
}
