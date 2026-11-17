package dev.vis.weather.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import dev.vis.weather.R
import dev.vis.weather.data.FontEdges
import dev.vis.weather.data.ReadingFont
import androidx.compose.ui.text.font.FontStyle as TextFontStyle
import dev.vis.weather.data.FontStyle as AppFontStyle

/**
 * App typography built on the variable Google Sans Flex (opsz / wdth / wght / ROND / slnt axes).
 *
 * Every style gets its own family whose optical size matches the style's font size, so the 136 sp hero
 * temperature uses display-cut glyphs while 11 sp labels use the sturdier text cut.
 *  - DEFAULT: wdth 100.  CHUNKY: wide (wdth 125, 118 for display sizes) and one weight step heavier.
 *  - TALL: condensed (wdth 76) and set slightly larger, which reads as taller.
 *  - SHARP / ROUNDED: ROND 0 / 100.
 * Reading fonts (Lexend, OpenDyslexic) replace the face entirely. Below API 26 variable axes are
 * unavailable, so the system sans is used (reading fonts still apply).
 */
object AppType {
    private data class FlexKey(val opsz: Int, val wdth: Int, val rond: Int, val shift: Int)

    private val families = HashMap<FlexKey, FontFamily>()
    private val typographies = HashMap<Triple<AppFontStyle, FontEdges, ReadingFont>, Typography>()

    private val weights = (100..900 step 100).toList()

    private fun flexFamily(key: FlexKey): FontFamily = synchronized(families) {
        families.getOrPut(key) {
            FontFamily(
                weights.flatMap { w ->
                    listOf(TextFontStyle.Normal, TextFontStyle.Italic).map { style ->
                        Font(
                            R.font.google_sans_flex,
                            weight = FontWeight(w),
                            style = style,
                            variationSettings = FontVariation.Settings(
                                FontVariation.Setting("wght", (w + key.shift).coerceIn(1, 1000).toFloat()),
                                FontVariation.Setting("wdth", key.wdth.toFloat()),
                                FontVariation.Setting("ROND", key.rond.toFloat()),
                                FontVariation.Setting("opsz", key.opsz.toFloat()),
                                FontVariation.Setting("slnt", if (style == TextFontStyle.Italic) -10f else 0f),
                            ),
                        )
                    }
                }
            )
        }
    }

    private val lexend: FontFamily by lazy {
        if (Build.VERSION.SDK_INT >= 26) {
            FontFamily(weights.map { w ->
                Font(R.font.lexend, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
            })
        } else {
            FontFamily(Font(R.font.lexend, FontWeight.Normal))
        }
    }

    private val openDyslexic: FontFamily by lazy {
        FontFamily(
            Font(R.font.open_dyslexic_regular, FontWeight.Normal),
            Font(R.font.open_dyslexic_bold, FontWeight.Bold),
        )
    }

    fun typography(style: AppFontStyle, edges: FontEdges, reading: ReadingFont): Typography = synchronized(typographies) {
        typographies.getOrPut(Triple(style, edges, reading)) { build(style, edges, reading) }
    }

    private fun build(style: AppFontStyle, edges: FontEdges, reading: ReadingFont): Typography {
        val base = Typography()
        return when {
            reading == ReadingFont.LEXEND -> base.map { it.copy(fontFamily = lexend) }
            reading == ReadingFont.OPEN_DYSLEXIC -> base.map {
                // OpenDyslexic is wide with tall ascenders: a touch smaller and more line height.
                it.copy(
                    fontFamily = openDyslexic,
                    fontSize = it.fontSize * 0.92f,
                    lineHeight = if (it.lineHeight.isSpecified) it.lineHeight * 1.05f else it.lineHeight,
                    letterSpacing = 0.sp,
                )
            }
            Build.VERSION.SDK_INT < 26 -> base
            else -> {
                val rond = if (edges == FontEdges.ROUNDED) 100 else 0
                base.map { s ->
                    val size = s.fontSize.value
                    val display = size >= 36f
                    val scale = if (style == AppFontStyle.TALL) 1.04f else 1f
                    val key = FlexKey(
                        // displayLarge is also blown up to 72–136 sp (hero temperatures), so it gets the display cut.
                        opsz = if (size >= 50f) 120 else (size * scale).toInt().coerceIn(6, 144),
                        wdth = when (style) {
                            AppFontStyle.DEFAULT -> 100
                            AppFontStyle.CHUNKY -> if (display) 118 else 125
                            AppFontStyle.TALL -> 76
                        },
                        rond = rond,
                        shift = if (style == AppFontStyle.CHUNKY) 100 else 0,
                    )
                    s.copy(
                        fontFamily = flexFamily(key),
                        fontSize = s.fontSize * scale,
                        lineHeight = if (s.lineHeight.isSpecified) s.lineHeight * (if (style == AppFontStyle.TALL) 1.08f else 1f) else s.lineHeight,
                        letterSpacing = if (style == AppFontStyle.CHUNKY && display) s.letterSpacing.tighten() else s.letterSpacing,
                    )
                }
            }
        }
    }

    private fun TextUnit.tighten(): TextUnit = if (isSpecified) (value - 0.5f).sp else this

    private inline fun Typography.map(f: (TextStyle) -> TextStyle) = Typography(
        displayLarge = f(displayLarge), displayMedium = f(displayMedium), displaySmall = f(displaySmall),
        headlineLarge = f(headlineLarge), headlineMedium = f(headlineMedium), headlineSmall = f(headlineSmall),
        titleLarge = f(titleLarge), titleMedium = f(titleMedium), titleSmall = f(titleSmall),
        bodyLarge = f(bodyLarge), bodyMedium = f(bodyMedium), bodySmall = f(bodySmall),
        labelLarge = f(labelLarge), labelMedium = f(labelMedium), labelSmall = f(labelSmall),
        displayLargeEmphasized = f(displayLargeEmphasized), displayMediumEmphasized = f(displayMediumEmphasized),
        displaySmallEmphasized = f(displaySmallEmphasized),
        headlineLargeEmphasized = f(headlineLargeEmphasized), headlineMediumEmphasized = f(headlineMediumEmphasized),
        headlineSmallEmphasized = f(headlineSmallEmphasized),
        titleLargeEmphasized = f(titleLargeEmphasized), titleMediumEmphasized = f(titleMediumEmphasized),
        titleSmallEmphasized = f(titleSmallEmphasized),
        bodyLargeEmphasized = f(bodyLargeEmphasized), bodyMediumEmphasized = f(bodyMediumEmphasized),
        bodySmallEmphasized = f(bodySmallEmphasized),
        labelLargeEmphasized = f(labelLargeEmphasized), labelMediumEmphasized = f(labelMediumEmphasized),
        labelSmallEmphasized = f(labelSmallEmphasized),
    )
}
