@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space tab screens leave at the bottom so their last items can scroll clear of the floating pill. */
val LocalBottomInset = compositionLocalOf { 0.dp }

/** Height of the pill itself (without its outer margin). */
val PillNavHeight: Dp = 64.dp

data class PillNavItem(val label: String, val icon: ImageVector, val selected: Boolean, val badge: Int = 0, val onClick: () -> Unit)

/**
 * Floating pill navigator: a rounded capsule that hovers over the content. The selected destination grows
 * into a filled pill with its label; the others stay as icons. Width and colour changes use spring motion.
 */
@Composable
fun PillNav(items: List<PillNavItem>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(PillNavHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { PillNavButton(it) }
        }
    }
}

@Composable
private fun PillNavButton(item: PillNavItem) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val bg by animateColorAsState(
        if (item.selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "pillBg",
    )
    val fg by animateColorAsState(
        if (item.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "pillFg",
    )
    Surface(
        onClick = item.onClick,
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        modifier = Modifier.height(48.dp).semantics {
            role = Role.Tab
            selected = item.selected
        },
    ) {
        Row(
            Modifier.padding(horizontal = if (item.selected) 18.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (item.badge > 0) {
                    BadgedBox(badge = { Badge { Text(item.badge.toString()) } }) {
                        Icon(item.icon, item.label, Modifier.size(24.dp))
                    }
                } else {
                    Icon(item.icon, item.label, Modifier.size(24.dp))
                }
            }
            AnimatedVisibility(
                visible = item.selected,
                enter = expandHorizontally(spatial) + fadeIn(),
                exit = shrinkHorizontally(spatial) + fadeOut(),
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
                }
            }
        }
    }
}
