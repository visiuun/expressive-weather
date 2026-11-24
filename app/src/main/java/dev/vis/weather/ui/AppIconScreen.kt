@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import androidx.compose.ui.res.stringResource
import dev.vis.weather.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.vis.weather.data.AppIcon
import dev.vis.weather.data.AppIcons
import dev.vis.weather.data.Settings
import dev.vis.weather.ui.components.SettingsGroup
import dev.vis.weather.ui.components.SettingsItemSurface
import dev.vis.weather.ui.components.SettingsSwitch
import dev.vis.weather.ui.components.SubScreen
import dev.vis.weather.widget.PolygonShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings → App icon: classic and weather icons, plus a "Dynamic" mode that follows the sky. */
@Composable
fun AppIconScreen(settings: Settings, update: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val dynamic = settings.appIcon == AppIcon.DYNAMIC
    // What DYNAMIC shows right now, from the cached forecast of the selected place.
    val weatherNow by produceState(AppIcon.SUNNY) {
        value = withContext(Dispatchers.IO) { AppIcons.effective(context, settings.copy(appIcon = AppIcon.DYNAMIC)) }
    }
    val shown = if (dynamic) weatherNow else settings.appIcon

    // The alias only switches once the app is in the background, so launchers don't close the app mid-tap.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { runCatching { AppIcons.sync(context.applicationContext) } }

    SubScreen(stringResource(R.string.s_app_icon), onBack) { w ->
        item { HeroIcon(shown, dynamic, w) }
        item {
            SettingsGroup(null, w) {
                SettingsSwitch(
                    stringResource(R.string.s_icon_dynamic), stringResource(R.string.s_icon_dynamic_sub), Icons.Rounded.AutoAwesome, MaterialShapes.SoftBurst, 0, 1, dynamic,
                ) { on -> update { it.copy(appIcon = if (on) AppIcon.DYNAMIC else weatherNow) } }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.s_icon_group_weather), w) {
                SettingsItemSurface(0, 1) {
                    IconGrid(AppIcons.weather, settings.appIcon, if (dynamic) weatherNow else null) { pick ->
                        update { it.copy(appIcon = pick) }
                    }
                }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.s_icon_group_classic), w) {
                SettingsItemSurface(0, 1) {
                    IconGrid(AppIcons.classic, settings.appIcon, null) { pick -> update { it.copy(appIcon = pick) } }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.s_icon_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = w.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun HeroIcon(icon: AppIcon, dynamic: Boolean, modifier: Modifier) {
    val shapes = remember {
        listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie12Sided, MaterialShapes.Cookie7Sided, MaterialShapes.SoftBurst)
    }
    val morphs = remember { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val t = rememberInfiniteTransition(label = "hero")
    val hold = 2600
    val move = 900
    val p by t.animateFloat(
        0f, shapes.size.toFloat(),
        infiniteRepeatable(tween((hold + move) * shapes.size, easing = LinearEasing)), label = "p",
    )
    val i = p.toInt().coerceIn(0, shapes.lastIndex)
    val local = ((p - i) * (hold + move) - hold) / move
    val shape = PolygonShape(morphs[i], FastOutSlowInEasing.transform(local.coerceIn(0f, 1f)))
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "rot")

    Column(modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(208.dp), contentAlignment = Alignment.Center) {
            // A soft halo in the same morphing shape, turning slowly behind the icon.
            Box(
                Modifier.size(208.dp).graphicsLayer { rotationZ = rot }.clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            )
            AdaptiveIcon(icon, 152.dp, shape)
        }
        Spacer(Modifier.height(12.dp))
        Text(AppIcons.label(icon), style = MaterialTheme.typography.titleLargeEmphasized)
        Text(
            if (dynamic) stringResource(R.string.s_icon_hero_dynamic) else stringResource(R.string.s_icon_hero_fixed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An adaptive icon drawn the way launchers do: 108dp layers, the centre 72dp visible through a mask. */
@Composable
fun AdaptiveIcon(icon: AppIcon, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val l = AppIcons.layers(icon)
    Box(
        modifier.size(size).clip(shape).background(l.background?.let { Color(it) } ?: Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        l.backgroundRes?.let { Image(painterResource(it), null, Modifier.requiredSize(size * 1.5f)) }
        Image(painterResource(l.foreground), null, Modifier.requiredSize(size * 1.5f))
    }
}

@Composable
private fun IconGrid(icons: List<AppIcon>, selected: AppIcon, now: AppIcon?, onPick: (AppIcon) -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        icons.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { icon -> IconCell(icon, icon == selected, icon == now, Modifier.weight(1f)) { onPick(icon) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun IconCell(icon: AppIcon, selected: Boolean, now: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        if (selected || now) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "sel",
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val shape = PolygonShape(morph, progress.coerceIn(0f, 1f))
    val ring by animateColorAsState(if (selected || now) cs.primary else Color.Transparent, label = "ring")
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 8.dp)
            .semantics { contentDescription = AppIcons.label(icon); this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(80.dp).border(3.dp, ring, shape))
            AdaptiveIcon(icon, 64.dp, shape, Modifier.graphicsLayer { val s = 1f + 0.06f * progress; scaleX = s; scaleY = s })
            if (now) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(22.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.AutoAwesome, stringResource(R.string.s_icon_now_cd), tint = cs.onPrimary, modifier = Modifier.size(13.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            AppIcons.label(icon),
            style = if (selected || now) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            color = if (selected || now) cs.primary else cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
