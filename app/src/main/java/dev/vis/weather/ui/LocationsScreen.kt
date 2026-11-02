@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.data.Place
import dev.vis.weather.ui.components.ShapeBadge
import java.util.Locale

/** Shape for an item in an M3 Expressive segmented list. */
fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val big = 24.dp
    val small = 6.dp
    return when {
        count == 1 -> RoundedCornerShape(big)
        index == 0 -> RoundedCornerShape(big, big, small, small)
        index == count - 1 -> RoundedCornerShape(small, small, big, big)
        else -> RoundedCornerShape(small)
    }
}

@Composable
fun LocationsScreen(
    state: UiState,
    search: SearchState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onUseCurrent: () -> Unit,
    onSelect: (Place) -> Unit,
    onRemove: (Place) -> Unit,
) {
    val focus = LocalFocusManager.current
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            TextField(
                value = search.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                placeholder = { Text("Search cities and places") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (search.query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val w = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            if (search.query.isNotBlank()) {
                if (search.searching) {
                    item {
                        Box(w.padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                } else if (search.error != null) {
                    item { Hint(search.error, w) }
                } else if (search.results.isEmpty()) {
                    item { Hint("No places found for “${search.query}”", w) }
                }
                itemsIndexed(search.results, key = { i, p -> "r$i${p.key}" }) { i, p ->
                    PlaceRow(
                        title = p.name,
                        subtitle = listOf(p.region, String.format(Locale.US, "%.2f, %.2f", p.lat, p.lon)).filter { it.isNotBlank() }.joinToString(" · "),
                        icon = Icons.Rounded.Place,
                        shape = MaterialShapes.Pill,
                        index = i,
                        count = search.results.size,
                        selected = false,
                        modifier = w,
                        onClick = { focus.clearFocus(); onSelect(p) },
                    )
                }
            } else {
                item { SectionLabel("Your location", w) }
                item {
                    PlaceRow(
                        title = "Current location",
                        subtitle = state.currentPlace?.let { listOf(it.name, it.region).filter { s -> s.isNotBlank() }.joinToString(", ") }
                            ?: "Use GPS / network location",
                        icon = Icons.Rounded.MyLocation,
                        shape = MaterialShapes.Cookie7Sided,
                        index = 0, count = 1,
                        selected = state.usingCurrent,
                        modifier = w,
                        onClick = onUseCurrent,
                    )
                }
                item { SectionLabel("Saved places", w.padding(top = 18.dp)) }
                if (state.places.isEmpty()) {
                    item { Hint("Search above to add places.", w) }
                }
                itemsIndexed(state.places, key = { _, p -> p.key }) { i, p ->
                    PlaceRow(
                        title = p.name,
                        subtitle = p.region,
                        icon = Icons.Rounded.LocationOn,
                        shape = MaterialShapes.Clover4Leaf,
                        index = i,
                        count = state.places.size,
                        selected = state.selection == p.key,
                        modifier = w.animateItem(),
                        onClick = { onSelect(p) },
                        trailing = {
                            IconButton(onClick = { onRemove(p) }) { Icon(Icons.Rounded.DeleteOutline, "Remove ${p.name}") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(16.dp),
    )
}

@Composable
private fun PlaceRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    index: Int,
    count: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = segmentShape(index, count),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(
                icon, shape, size = 42.dp,
                container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) Icon(Icons.Rounded.Check, "Selected", Modifier.padding(horizontal = 8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            trailing?.invoke()
        }
    }
}
