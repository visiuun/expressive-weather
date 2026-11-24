@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.vis.weather.L
import dev.vis.weather.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.CardStyle
import dev.vis.weather.data.ColorSource
import dev.vis.weather.data.FontEdges
import dev.vis.weather.data.FontStyle
import dev.vis.weather.data.PaletteStyle
import dev.vis.weather.data.ReadingFont
import dev.vis.weather.data.Settings
import dev.vis.weather.data.ThemeMode
import dev.vis.weather.ui.components.SettingsAction
import dev.vis.weather.ui.components.SettingsChoice
import dev.vis.weather.ui.components.SettingsGroup
import dev.vis.weather.ui.components.SettingsInfo
import dev.vis.weather.ui.components.SettingsItemSurface
import dev.vis.weather.ui.components.SettingsSwitch
import dev.vis.weather.ui.components.SubScreen
import dev.vis.weather.ui.effects.WeatherSky
import dev.vis.weather.ui.effects.rememberWeatherHaptics
import dev.vis.weather.ui.theme.LocalAppColors
import dev.vis.weather.ui.theme.SchemeGen
import dev.vis.weather.ui.theme.SecretTheme
import dev.vis.weather.ui.theme.SecretThemes
import dev.vis.weather.ui.theme.SkyKind
import dev.vis.weather.ui.theme.animateColorScheme
import dev.vis.weather.ui.theme.skyPalette
import dev.vis.weather.ui.theme.targetScheme
import dev.vis.weather.ui.theme.toOled

/** Preset seed colours (no purple). The first is the house red. */
private val Presets = listOf(
    0xFFFF3300, 0xFFFF7A1A, 0xFFFFB000, 0xFFE0C200,
    0xFF7CB342, 0xFF2EA05A, 0xFF00968A, 0xFF00ACC1,
    0xFF3D8BEA, 0xFF2F55D4, 0xFFE84A7F, 0xFF5F6B78,
)

private val PresetShapes: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Flower, MaterialShapes.Sunny, MaterialShapes.Gem,
    MaterialShapes.Clover4Leaf, MaterialShapes.Cookie7Sided, MaterialShapes.Cookie6Sided, MaterialShapes.SoftBurst,
    MaterialShapes.Pentagon, MaterialShapes.Cookie12Sided, MaterialShapes.Clover8Leaf, MaterialShapes.Diamond,
)

@Composable
fun AppearanceScreen(settings: Settings, update: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = LocalAppColors.current
    val haptics = rememberWeatherHaptics(settings)
    var previewKind by rememberSaveable { mutableStateOf(app.mood?.kind ?: SkyKind.Default) }
    var customOpen by remember { mutableStateOf(false) }
    val secretVersion = SecretThemes.version.intValue
    val unlocked = remember(secretVersion) { SecretThemes.unlocked(context) }
    val activeSecret = SecretThemes.byId(settings.secretTheme)

    SubScreen(stringResource(R.string.s_appearance), onBack) { w ->
        item(key = "preview") { LivePreview(previewKind, settings, w) }

        item(key = "theme") {
            SettingsGroup(stringResource(R.string.s_ap_group_theme), w) {
                SettingsChoice(
                    stringResource(R.string.s_ap_theme), Icons.Rounded.Contrast, MaterialShapes.Cookie4Sided, 0, 2,
                    ThemeMode.entries, settings.theme,
                    { when (it) { ThemeMode.SYSTEM -> L.s(R.string.s_ap_theme_system); ThemeMode.LIGHT -> L.s(R.string.s_ap_theme_light); ThemeMode.DARK -> L.s(R.string.s_ap_theme_dark) } },
                    subtitle = activeSecret?.let { s ->
                        when {
                            !s.lightAvailable -> stringResource(R.string.s_ap_secret_dark_only, s.name)
                            !s.darkAvailable -> stringResource(R.string.s_ap_secret_light_only, s.name)
                            else -> null
                        }
                    },
                ) { v -> update { it.copy(theme = v) } }
                SettingsSwitch(
                    stringResource(R.string.s_ap_oled), stringResource(R.string.s_ap_oled_sub),
                    Icons.Rounded.DarkMode, MaterialShapes.Cookie9Sided, 1, 2, settings.oled,
                ) { v -> haptics.onToggle(v); update { it.copy(oled = v) } }
            }
        }

        item(key = "colour") {
            SettingsGroup(stringResource(R.string.s_ap_group_colour), w) {
                val sources = buildList {
                    add(ColorSource.WEATHER)
                    if (Build.VERSION.SDK_INT >= 31) add(ColorSource.MATERIAL_YOU)
                    add(ColorSource.CUSTOM)
                }
                val n = 5
                SettingsChoice(
                    stringResource(R.string.s_ap_colour_source), Icons.Rounded.Palette, MaterialShapes.Flower, 0, n,
                    sources, if (settings.colorSource in sources) settings.colorSource else ColorSource.WEATHER,
                    { when (it) { ColorSource.WEATHER -> L.s(R.string.s_ap_source_weather); ColorSource.MATERIAL_YOU -> L.s(R.string.s_ap_source_wallpaper); ColorSource.CUSTOM -> L.s(R.string.s_ap_source_custom) } },
                    subtitle = when {
                        activeSecret != null -> stringResource(R.string.s_ap_source_overridden, activeSecret.name)
                        settings.colorSource == ColorSource.WEATHER -> stringResource(R.string.s_ap_source_weather_sub)
                        settings.colorSource == ColorSource.MATERIAL_YOU -> stringResource(R.string.s_ap_source_wallpaper_sub)
                        else -> stringResource(R.string.s_ap_source_custom_sub)
                    },
                ) { v -> haptics.onTick(); update { it.copy(colorSource = v, secretTheme = null) } }
                WeatherMap(settings, previewKind, 1, n) { haptics.onTick(); previewKind = it }
                PresetRow(settings, 2, n) { c ->
                    haptics.onTick()
                    update { it.copy(colorSource = ColorSource.CUSTOM, customSeed = c, secretTheme = null) }
                }
                SettingsAction(
                    stringResource(R.string.s_ap_custom_colour_action), "#%06X".format(settings.customSeed and 0xFFFFFF),
                    Icons.Rounded.Colorize, MaterialShapes.Gem, 3, n,
                    trailing = {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(settings.customSeed)))
                    },
                ) { customOpen = true }
                SettingsChoice(
                    stringResource(R.string.s_ap_palette), Icons.Rounded.Gradient, MaterialShapes.SoftBurst, 4, n,
                    PaletteStyle.entries, settings.paletteStyle,
                    { when (it) { PaletteStyle.BALANCED -> L.s(R.string.s_ap_palette_balanced); PaletteStyle.VIBRANT -> L.s(R.string.s_ap_palette_vibrant); PaletteStyle.EXPRESSIVE -> L.s(R.string.s_ap_palette_expressive) } },
                    subtitle = stringResource(R.string.s_ap_palette_sub),
                ) { v -> haptics.onTick(); update { it.copy(paletteStyle = v) } }
            }
        }

        if (unlocked.isNotEmpty()) {
            item(key = "secret") {
                SettingsGroup(stringResource(R.string.s_ap_group_secret), w) {
                    SecretRow(unlocked, settings.secretTheme, 0, 2) { id ->
                        haptics.onConfirm()
                        update { it.copy(secretTheme = id) }
                    }
                    SettingsInfo(
                        activeSecret?.name ?: stringResource(R.string.s_ap_secret_found, unlocked.size, SecretThemes.all.size),
                        activeSecret?.blurb ?: stringResource(R.string.s_ap_secret_hint),
                        1, 2, icon = Icons.Rounded.AutoAwesome,
                    )
                }
            }
        }

        item(key = "text") {
            SettingsGroup(stringResource(R.string.s_ap_group_text), w) {
                val n = 4
                SettingsChoice(
                    stringResource(R.string.s_ap_font_style), Icons.Rounded.TextFields, MaterialShapes.Square, 0, n,
                    FontStyle.entries, settings.fontStyle,
                    { when (it) { FontStyle.DEFAULT -> L.s(R.string.s_ap_font_default); FontStyle.CHUNKY -> L.s(R.string.s_ap_font_chunky); FontStyle.TALL -> L.s(R.string.s_ap_font_tall) } },
                    subtitle = if (settings.readingFont != ReadingFont.NONE) stringResource(R.string.s_ap_font_replaced) else "Google Sans Flex",
                ) { v -> haptics.onTick(); update { it.copy(fontStyle = v) } }
                SettingsChoice(
                    stringResource(R.string.s_ap_edges), Icons.Rounded.RoundedCorner, MaterialShapes.Cookie6Sided, 1, n,
                    FontEdges.entries, settings.fontEdges,
                    { when (it) { FontEdges.SHARP -> L.s(R.string.s_ap_edges_sharp); FontEdges.ROUNDED -> L.s(R.string.s_ap_edges_rounded) } },
                ) { v -> haptics.onTick(); update { it.copy(fontEdges = v) } }
                FontSample(2, n)
                SettingsChoice(
                    stringResource(R.string.s_ap_reading_font), Icons.Rounded.Accessibility, MaterialShapes.Circle, 3, n,
                    ReadingFont.entries, settings.readingFont,
                    { when (it) { ReadingFont.NONE -> L.s(R.string.s_ap_reading_none); ReadingFont.LEXEND -> "Lexend"; ReadingFont.OPEN_DYSLEXIC -> L.s(R.string.s_ap_reading_dyslexic) } },
                    subtitle = stringResource(R.string.s_ap_reading_sub),
                ) { v -> haptics.onTick(); update { it.copy(readingFont = v) } }
            }
        }

        item(key = "layout") {
            SettingsGroup(stringResource(R.string.s_layout), w) {
                SettingsSwitch(
                    stringResource(R.string.s_ap_hero), stringResource(R.string.s_ap_hero_sub),
                    Icons.Rounded.Wallpaper, MaterialShapes.Cookie6Sided, 0, 3, settings.heroBackground,
                ) { v -> haptics.onToggle(v); update { it.copy(heroBackground = v) } }
                SettingsChoice(
                    stringResource(R.string.s_ap_card_style), Icons.Rounded.Dashboard, MaterialShapes.Square, 1, 3,
                    CardStyle.entries, settings.cardStyle,
                    { when (it) { CardStyle.FILLED -> L.s(R.string.s_ap_card_filled); CardStyle.OUTLINED -> L.s(R.string.s_ap_card_outlined); CardStyle.NONE -> L.s(R.string.s_ap_card_none) } },
                ) { v -> haptics.onTick(); update { it.copy(cardStyle = v) } }
                SettingsChoice(
                    stringResource(R.string.s_ap_icons), Icons.Rounded.WbCloudy, MaterialShapes.Cookie7Sided, 2, 3,
                    listOf(false, true), settings.monoIcons,
                    { if (it) L.s(R.string.s_ap_icons_mono) else L.s(R.string.s_ap_icons_colour) },
                ) { v -> haptics.onTick(); update { it.copy(monoIcons = v) } }
            }
        }

        item(key = "effects") {
            SettingsGroup(stringResource(R.string.s_ap_group_effects), w) {
                SettingsSwitch(
                    stringResource(R.string.s_ap_animations), stringResource(R.string.s_ap_animations_sub),
                    Icons.Rounded.Animation, MaterialShapes.Sunny, 0, 3, settings.animations,
                ) { v -> haptics.onToggle(v); update { it.copy(animations = v) } }
                SettingsSwitch(
                    stringResource(R.string.s_ap_battery_saver), stringResource(R.string.s_ap_battery_saver_sub),
                    Icons.Rounded.BatterySaver, MaterialShapes.Pill, 1, 3, settings.pauseOnBatterySaver,
                    enabled = settings.animations,
                ) { v -> haptics.onToggle(v); update { it.copy(pauseOnBatterySaver = v) } }
                SettingsSwitch(
                    stringResource(R.string.s_ap_haptics), stringResource(R.string.s_ap_haptics_sub),
                    Icons.Rounded.Vibration, MaterialShapes.Clover4Leaf, 2, 3, settings.haptics,
                ) { v -> haptics.onToggle(v); update { it.copy(haptics = v) } }
            }
        }
    }

    if (customOpen) {
        CustomColorDialog(
            initial = settings.customSeed,
            style = settings.paletteStyle,
            dark = app.isDark,
            oled = app.oled,
            onDismiss = { customOpen = false },
        ) { c ->
            customOpen = false
            haptics.onConfirm()
            update { it.copy(colorSource = ColorSource.CUSTOM, customSeed = c, secretTheme = null) }
        }
    }
}

/** A window onto the main screen: the live sky for [kind] with the colours and type it would get. */
@Composable
private fun LivePreview(kind: SkyKind, settings: Settings, modifier: Modifier) {
    val context = LocalContext.current
    val app = LocalAppColors.current
    val dark = app.isDark
    val scheme = animateColorScheme(
        remember(kind, settings.colorSource, settings.customSeed, settings.paletteStyle, settings.secretTheme, settings.oled, dark) {
            targetScheme(context, settings, kind, dark)
        }
    )
    val precip = when (kind.sample) {
        53 -> 0.4; 63 -> 2.5; 66 -> 1.0; 73 -> 1.5; 95 -> 6.0; else -> 0.0
    }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
        CompositionLocalProvider(LocalAppColors provides app.copy(mood = kind.mood)) {
            val cs = MaterialTheme.colorScheme
            Box(
                modifier
                    .height(212.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(cs.background)
                    .border(1.dp, cs.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(32.dp)),
            ) {
                WeatherSky(kind.mood, windKmh = if (kind.wet) 22.0 else 10.0, precipMm = precip, settings = settings, modifier = Modifier.matchParentSize())
                Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                    Text(kind.label, style = MaterialTheme.typography.titleLargeEmphasized, color = cs.onSurface)
                    Row {
                        Text(
                            "23",
                            style = MaterialTheme.typography.displayLargeEmphasized.copy(fontSize = 88.sp, lineHeight = 92.sp, fontWeight = FontWeight.Medium),
                            color = cs.onSurface,
                        )
                        Text("°", style = MaterialTheme.typography.displayMedium, color = cs.primary, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(stringResource(R.string.s_ap_preview_feels, "21°"), cs.tertiaryContainer, cs.onTertiaryContainer)
                    Pill("↑ 25°", cs.primaryContainer, cs.onPrimaryContainer)
                    Pill("↓ 14°", cs.secondaryContainer, cs.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Surface(shape = CircleShape, color = bg, contentColor = fg) {
        Text(text, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** "Theme weather map": one tile per sky, each with its gradient and the palette it produces. */
@Composable
private fun WeatherMap(settings: Settings, selected: SkyKind, index: Int, count: Int, onSelect: (SkyKind) -> Unit) {
    val context = LocalContext.current
    val app = LocalAppColors.current
    SettingsItemSurface(index, count) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(stringResource(R.string.s_ap_weather_map), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                stringResource(R.string.s_ap_weather_map_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkyKind.entries.forEach { kind ->
                    val sky = remember(kind, app.isDark, app.oled, app.skyTint) { skyPalette(kind, app.isDark, app.oled, app.skyTint) }
                    val scheme = remember(kind, settings.colorSource, settings.customSeed, settings.paletteStyle, settings.secretTheme, settings.oled, app.isDark) {
                        targetScheme(context, settings, kind, app.isDark)
                    }
                    val isSel = kind == selected
                    val s by animateFloatAsState(if (isSel) 1f else 0.94f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "tile")
                    val radius by animateDpAsState(if (isSel) 28.dp else 18.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "tileR")
                    val shape = RoundedCornerShape(radius)
                    Column(
                        Modifier
                            .scale(s)
                            .width(76.dp)
                            .clip(shape)
                            .background(Brush.verticalGradient(listOf(sky.top, sky.mid, sky.horizon, scheme.background)))
                            .border(
                                BorderStroke(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                shape,
                            )
                            .clickable { onSelect(kind) }
                            .padding(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Dot(scheme.primary)
                            Dot(scheme.secondary)
                            Dot(scheme.tertiary)
                        }
                        Spacer(Modifier.height(30.dp))
                        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(scheme.primaryContainer))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            kind.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = sky.onSky,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(c: Color) {
    Box(Modifier.size(14.dp).clip(CircleShape).background(c).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape))
}

/** Twelve preset seeds, each in its own M3 Expressive shape. */
@Composable
private fun PresetRow(settings: Settings, index: Int, count: Int, onPick: (Int) -> Unit) {
    SettingsItemSurface(index, count) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.s_ap_presets), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Presets.chunked(6).forEachIndexed { row, colours ->
                if (row > 0) Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    colours.forEachIndexed { i, argb ->
                        val c = Color(argb)
                        val selected = settings.colorSource == ColorSource.CUSTOM && settings.secretTheme == null &&
                            (settings.customSeed and 0xFFFFFF) == (argb.toInt() and 0xFFFFFF)
                        val s by animateFloatAsState(if (selected) 1.12f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "preset")
                        Box(
                            Modifier
                                .size(44.dp)
                                .scale(s)
                                .clip(PresetShapes[row * 6 + i].toShape())
                                .background(c)
                                .clickable { onPick(argb.toInt()) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, null, tint = if (c.luminance() > 0.5f) Color.Black else Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretRow(themes: List<SecretTheme>, selected: String?, index: Int, count: Int, onSelect: (String?) -> Unit) {
    SettingsItemSurface(index, count) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecretTile(stringResource(R.string.s_ap_secret_default), listOf(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary), selected == null) { onSelect(null) }
            themes.forEach { t -> SecretTile(t.name, t.swatches, selected == t.id) { onSelect(t.id) } }
        }
    }
}

@Composable
private fun SecretTile(name: String, swatches: List<Color>, selected: Boolean, onClick: () -> Unit) {
    val radius by animateDpAsState(if (selected) 30.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "secret")
    val shape = RoundedCornerShape(radius)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(
            Modifier
                .size(72.dp)
                .clip(shape)
                .background(swatches[0])
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(34.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(swatches[1]))
            Box(Modifier.align(Alignment.BottomStart).padding(10.dp).size(14.dp).clip(CircleShape).background(swatches[2]))
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(14.dp).clip(CircleShape).background(swatches[3]))
            if (selected) Icon(Icons.Rounded.Check, null, tint = if (swatches[1].luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FontSample(index: Int, count: Int) {
    SettingsItemSurface(index, count) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "23°",
                style = MaterialTheme.typography.displayLargeEmphasized.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.s_ap_sample_condition), style = MaterialTheme.typography.titleLargeEmphasized)
                Text(stringResource(R.string.s_ap_sample_detail, "21°"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Hue / saturation / brightness sliders + hex field, with a live preview of the scheme it generates. */
@Composable
private fun CustomColorDialog(
    initial: Int,
    style: PaletteStyle,
    dark: Boolean,
    oled: Boolean,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hex by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }
    fun syncHex(c: Int) {
        hex = "%06X".format(c and 0xFFFFFF)
    }

    val preview = remember(argb, style, dark, oled) {
        SchemeGen.scheme(argb, dark, style).let { if (dark && oled) it.toOled() else it }
    }
    val c = Color(argb)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(argb) }) { Text(stringResource(R.string.s_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_cancel)) } },
        title = { Text(stringResource(R.string.s_ap_custom_colour)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Live mini theme.
                Surface(shape = RoundedCornerShape(24.dp), color = preview.background, border = BorderStroke(1.dp, preview.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(c))
                            Spacer(Modifier.width(10.dp))
                            Text("23°", style = MaterialTheme.typography.headlineLargeEmphasized, color = preview.primary)
                            Spacer(Modifier.weight(1f))
                            Dot(preview.primary)
                            Spacer(Modifier.width(4.dp))
                            Dot(preview.secondary)
                            Spacer(Modifier.width(4.dp))
                            Dot(preview.tertiary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Surface(shape = RoundedCornerShape(16.dp), color = preview.surfaceContainer) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Pill(stringResource(R.string.s_ap_sample_sunny), preview.primaryContainer, preview.onPrimaryContainer)
                                Pill("UV 4", preview.tertiaryContainer, preview.onTertiaryContainer)
                                Pill(stringResource(R.string.s_ap_sample_wind), preview.secondaryContainer, preview.onSecondaryContainer)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.s_ap_hue), style = MaterialTheme.typography.labelLarge)
                Box(
                    Modifier.fillMaxWidth().padding(top = 6.dp).height(10.dp).clip(CircleShape).background(
                        Brush.horizontalGradient(List(7) { i -> Color.hsv(i * 60f % 360f, 1f, 1f) })
                    )
                )
                Slider(
                    value = hue, onValueChange = { hue = it; syncHex(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))) },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(thumbColor = c, activeTrackColor = c),
                )
                Text(stringResource(R.string.s_ap_saturation), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = sat, onValueChange = { sat = it; syncHex(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))) },
                    colors = SliderDefaults.colors(thumbColor = c, activeTrackColor = c),
                )
                Text(stringResource(R.string.s_ap_brightness), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = value, onValueChange = { value = it; syncHex(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))) },
                    colors = SliderDefaults.colors(thumbColor = c, activeTrackColor = c),
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { s ->
                        val clean = s.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hex = clean
                        if (clean.length == 6 && clean.all { it in '0'..'9' || it in 'A'..'F' }) {
                            val parsed = (0xFF000000L or clean.toLong(16)).toInt()
                            val out = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed, out)
                            hue = out[0]; sat = out[1]; value = out[2]
                        }
                    },
                    prefix = { Text("#") },
                    label = { Text(stringResource(R.string.s_ap_hex)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

