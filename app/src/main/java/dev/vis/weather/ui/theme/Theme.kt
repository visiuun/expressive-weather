@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.vis.weather.data.CardStyle
import dev.vis.weather.data.ColorSource
import dev.vis.weather.data.Settings
import dev.vis.weather.data.ThemeMode


// Fallback palette (seed: sky blue) for devices without Material You (Android < 12).
internal val LightScheme = lightColorScheme(
    primary = Color(0xFF2E6BB0), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E3FF), onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF545F71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8), onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF8A5100), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE), onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C6CF),
    inverseSurface = Color(0xFF2E3135), inverseOnSurface = Color(0xFFEFF0F7), inversePrimary = Color(0xFFA5C8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E9), surfaceDim = Color(0xFFD8DAE0), surfaceBright = Color(0xFFF8F9FF),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFFA5C8FF), onPrimary = Color(0xFF00315E),
    primaryContainer = Color(0xFF0E4A85), onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFFBCC7DC), onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3D4758), onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFFFB871), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00), onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318), onBackground = Color(0xFFE1E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE1E2E9),
    surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199), outlineVariant = Color(0xFF43474E),
    inverseSurface = Color(0xFFE1E2E9), inverseOnSurface = Color(0xFF2E3135), inversePrimary = Color(0xFF2E6BB0),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A), surfaceDim = Color(0xFF111318), surfaceBright = Color(0xFF37393E),
)

/** True-black variant for OLED panels: pure black backgrounds, containers pulled toward black. */
fun ColorScheme.toOled(): ColorScheme {
    fun dim(c: Color, f: Float) = lerp(c, Color.Black, f)
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = dim(surfaceContainerLow, 0.65f),
        surfaceContainer = dim(surfaceContainer, 0.55f),
        surfaceContainerHigh = dim(surfaceContainerHigh, 0.45f),
        surfaceContainerHighest = dim(surfaceContainerHighest, 0.4f),
        surfaceBright = dim(surfaceBright, 0.4f),
        surfaceVariant = dim(surfaceVariant, 0.4f),
        primaryContainer = dim(primaryContainer, 0.35f),
        secondaryContainer = dim(secondaryContainer, 0.35f),
        tertiaryContainer = dim(tertiaryContainer, 0.35f),
        inverseOnSurface = Color.Black,
    )
}

@Immutable
data class AppColors(
    val isDark: Boolean,
    val monoIcons: Boolean = false,
    val cardStyle: CardStyle = CardStyle.FILLED,
    /** Dark theme with true-black surfaces. */
    val oled: Boolean = false,
    /** Conditions the colours were picked for (null before the first forecast). */
    val mood: WeatherMood? = null,
    /** Sky gradient + glow colours for the current mood and theme; use it for backdrops behind content. */
    val sky: SkyPalette = skyPalette(SkyKind.Default, isDark, oled),
    /** Set while a secret theme is active: the live sky is tinted toward it. */
    val skyTint: Color? = null,
)

val LocalAppColors = staticCompositionLocalOf { AppColors(false) }

/** Light/dark as chosen in settings, before secret themes that only exist in one mode. */
@Composable
fun Settings.prefersDark(): Boolean = when (theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The colour scheme [settings] asks for, without animation. [mood] drives the WEATHER source
 * (and MATERIAL_YOU below Android 12). Secret themes override everything.
 */
fun targetScheme(context: Context, settings: Settings, kind: SkyKind, dark: Boolean): ColorScheme {
    val secret = SecretThemes.byId(settings.secretTheme)
    val base = when {
        secret != null -> secret.scheme(dark)
        settings.colorSource == ColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        settings.colorSource == ColorSource.CUSTOM -> SchemeGen.scheme(settings.customSeed, dark, settings.paletteStyle)
        else -> SchemeGen.scheme(kind.spec(settings.paletteStyle), dark)
    }
    if (secret != null) return base // secret themes pick their own blacks
    val scheme = if (dark && settings.oled) base.toOled() else base
    return if (dark) scheme.unbrown() else scheme
}

/**
 * Dark orange/amber containers read as brown. In dark themes, warm containers are swapped for their
 * bright "fixed dim" tone (golden pills with dark text) so sunny themes stay sunny.
 */
fun ColorScheme.unbrown(): ColorScheme {
    val hsv = FloatArray(3)
    fun brownish(c: Color): Boolean {
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        return hsv[0] in 20f..66f && hsv[1] > 0.18f && hsv[2] < 0.55f
    }
    var s = this
    if (brownish(primaryContainer)) s = s.copy(primaryContainer = primaryFixedDim, onPrimaryContainer = onPrimaryFixed)
    if (brownish(secondaryContainer)) s = s.copy(secondaryContainer = secondaryFixedDim, onSecondaryContainer = onSecondaryFixed)
    if (brownish(tertiaryContainer)) s = s.copy(tertiaryContainer = tertiaryFixedDim, onTertiaryContainer = onTertiaryFixed)
    return s
}

@Composable
fun rememberTargetScheme(settings: Settings, mood: WeatherMood?, dark: Boolean): ColorScheme {
    val context = LocalContext.current
    val kind = mood?.kind ?: SkyKind.Default
    return remember(settings.colorSource, settings.customSeed, settings.paletteStyle, settings.oled, settings.secretTheme, kind, dark) {
        targetScheme(context, settings, kind, dark)
    }
}

/**
 * App theme. Colours come from the sky ([ColorSource.WEATHER], default), the wallpaper or a custom seed,
 * and every colour role animates (~600 ms) when the weather, theme or source changes.
 *
 * @param mood current conditions from the forecast; null before data loads (a soft sky blue is used).
 */
@Composable
fun WeatherTheme(settings: Settings, mood: WeatherMood? = null, content: @Composable () -> Unit) {
    val secret = SecretThemes.byId(settings.secretTheme)
    val requested = settings.prefersDark()
    val dark = secret?.resolveDark(requested) ?: requested
    val scheme = animateColorScheme(rememberTargetScheme(settings, mood, dark))
    val kind = mood?.kind ?: SkyKind.Default
    val sky = remember(kind, dark, settings.oled, secret) { skyPalette(kind, dark, settings.oled, secret?.sky) }
    val typography = remember(settings.fontStyle, settings.fontEdges, settings.readingFont) {
        AppType.typography(settings.fontStyle, settings.fontEdges, settings.readingFont)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val app = AppColors(
        isDark = dark,
        monoIcons = settings.monoIcons,
        cardStyle = settings.cardStyle,
        oled = dark && settings.oled,
        mood = mood,
        sky = sky,
        skyTint = secret?.sky,
    )
    CompositionLocalProvider(LocalAppColors provides app) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography,
            content = content,
        )
    }
}

private val SchemeSpec: AnimationSpec<Color> = tween(600, easing = FastOutSlowInEasing)

@Composable
private fun Color.animated(): Color = animateColorAsState(this, SchemeSpec, label = "role").value

/** Animates every role of [target] so a weather change sweeps smoothly through the whole UI. */
@Composable
fun animateColorScheme(target: ColorScheme): ColorScheme = ColorScheme(
    primary = target.primary.animated(),
    onPrimary = target.onPrimary.animated(),
    primaryContainer = target.primaryContainer.animated(),
    onPrimaryContainer = target.onPrimaryContainer.animated(),
    inversePrimary = target.inversePrimary.animated(),
    secondary = target.secondary.animated(),
    onSecondary = target.onSecondary.animated(),
    secondaryContainer = target.secondaryContainer.animated(),
    onSecondaryContainer = target.onSecondaryContainer.animated(),
    tertiary = target.tertiary.animated(),
    onTertiary = target.onTertiary.animated(),
    tertiaryContainer = target.tertiaryContainer.animated(),
    onTertiaryContainer = target.onTertiaryContainer.animated(),
    background = target.background.animated(),
    onBackground = target.onBackground.animated(),
    surface = target.surface.animated(),
    onSurface = target.onSurface.animated(),
    surfaceVariant = target.surfaceVariant.animated(),
    onSurfaceVariant = target.onSurfaceVariant.animated(),
    surfaceTint = target.surfaceTint.animated(),
    inverseSurface = target.inverseSurface.animated(),
    inverseOnSurface = target.inverseOnSurface.animated(),
    error = target.error.animated(),
    onError = target.onError.animated(),
    errorContainer = target.errorContainer.animated(),
    onErrorContainer = target.onErrorContainer.animated(),
    outline = target.outline.animated(),
    outlineVariant = target.outlineVariant.animated(),
    scrim = target.scrim.animated(),
    surfaceBright = target.surfaceBright.animated(),
    surfaceDim = target.surfaceDim.animated(),
    surfaceContainer = target.surfaceContainer.animated(),
    surfaceContainerHigh = target.surfaceContainerHigh.animated(),
    surfaceContainerHighest = target.surfaceContainerHighest.animated(),
    surfaceContainerLow = target.surfaceContainerLow.animated(),
    surfaceContainerLowest = target.surfaceContainerLowest.animated(),
    primaryFixed = target.primaryFixed.animated(),
    primaryFixedDim = target.primaryFixedDim.animated(),
    onPrimaryFixed = target.onPrimaryFixed.animated(),
    onPrimaryFixedVariant = target.onPrimaryFixedVariant.animated(),
    secondaryFixed = target.secondaryFixed.animated(),
    secondaryFixedDim = target.secondaryFixedDim.animated(),
    onSecondaryFixed = target.onSecondaryFixed.animated(),
    onSecondaryFixedVariant = target.onSecondaryFixedVariant.animated(),
    tertiaryFixed = target.tertiaryFixed.animated(),
    tertiaryFixedDim = target.tertiaryFixedDim.animated(),
    onTertiaryFixed = target.onTertiaryFixed.animated(),
    onTertiaryFixedVariant = target.onTertiaryFixedVariant.animated(),
)
