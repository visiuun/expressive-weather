@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
private fun ColorScheme.toOled(): ColorScheme {
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

data class AppColors(
    val isDark: Boolean,
    val monoIcons: Boolean = false,
    val cardStyle: dev.vis.weather.data.CardStyle = dev.vis.weather.data.CardStyle.FILLED,
)

val LocalAppColors = staticCompositionLocalOf { AppColors(false) }

@Composable
fun WeatherTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    var scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    if (dark && settings.oled) scheme = scheme.toOled()

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

    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides AppColors(dark, settings.monoIcons, settings.cardStyle)) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = Typography(),
            content = content,
        )
    }
}
