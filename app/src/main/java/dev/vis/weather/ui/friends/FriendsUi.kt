@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.friends

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.vis.weather.alerts.WarningStore
import dev.vis.weather.data.Forecast
import dev.vis.weather.data.ForecastRepo
import dev.vis.weather.data.Friend
import dev.vis.weather.data.Friends
import dev.vis.weather.data.Place
import dev.vis.weather.data.Settings
import dev.vis.weather.data.WeatherApi
import dev.vis.weather.ui.Fmt
import dev.vis.weather.ui.alerts.color
import dev.vis.weather.ui.alerts.icon
import dev.vis.weather.ui.components.AppCard
import dev.vis.weather.ui.components.ConnectedChoice
import dev.vis.weather.ui.components.SubScreen
import dev.vis.weather.ui.components.WeatherIcon
import dev.vis.weather.ui.components.tempColor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private val avatarShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie7Sided, MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided, MaterialShapes.Flower, MaterialShapes.Gem, MaterialShapes.SoftBurst,
)

/** Loads (and keeps fresh) forecasts for every friend. */
@Composable
fun rememberFriendForecasts(friends: List<Friend>): Map<String, Forecast> {
    val context = LocalContext.current
    val map = remember { mutableStateMapOf<String, Forecast>() }
    LaunchedEffect(friends.map { it.place.key }) {
        friends.forEach { f -> ForecastRepo.cached(context, f.place)?.let { map[f.id] = it } }
        coroutineScope {
            friends.map { f -> async { f.id to ForecastRepo.get(context, f.place, 30 * 60_000L) } }.awaitAll()
        }.forEach { (id, fc) -> if (fc != null) map[id] = fc }
    }
    return map
}

@Composable
fun Avatar(friend: Friend?, size: Dp, shape: RoundedPolygon, label: String = friend?.initials ?: "You") {
    val bmp = remember(friend?.photo) { friend?.photo?.let { p -> runCatching { BitmapFactory.decodeFile(p)?.asImageBitmap() }.getOrNull() } }
    Box(
        Modifier.size(size).clip(shape.toShape()).background(if (friend == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, null, Modifier.size(size), contentScale = ContentScale.Crop)
        else Text(
            label, style = if (size > 48.dp) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleSmallEmphasized,
            color = if (friend == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1,
        )
    }
}

/** Main-screen card: everyone's temperature at a glance, plus warmest / coldest. */
@Composable
fun FriendsCard(me: Forecast, settings: Settings, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val friends by Friends.flow(context).collectAsState()
    val forecasts = rememberFriendForecasts(friends)
    val fmt = remember(settings, me.zone) { Fmt(settings, me.zone) }
    AppCard(modifier, onClick = onOpen) {
        Column(Modifier.padding(vertical = 18.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Family & friends", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
            }
            if (friends.isEmpty()) {
                Text(
                    "See the weather where the people you care about live, and get their warnings.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                FilledTonalButton(onClick = onOpen, modifier = Modifier.padding(horizontal = 20.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add someone")
                }
                return@Column
            }
            val people = listOf<Pair<String, Forecast?>>("You" to me) + friends.map { it.name.substringBefore(' ') to forecasts[it.id] }
            val known = people.filter { it.second != null && !it.second!!.current.temp.isNaN() }
            if (known.size >= 2) {
                val warm = known.maxOf { it.second!!.current.temp }
                val cold = known.minOf { it.second!!.current.temp }
                fun names(t: Double) = known.filter { kotlin.math.abs(it.second!!.current.temp - t) < 0.5 }.map { it.first }.take(2).joinToString(" & ")
                Text(
                    if (warm - cold < 1.5) "Similar weather for everyone" else "Warmest: ${names(warm)} · Coldest: ${names(cold)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Person(null, "You", me, fmt, 0) }
                items(friends.size) { i -> Person(friends[i], friends[i].name.substringBefore(' '), forecasts[friends[i].id], fmt, i + 1) }
            }
        }
    }
}

@Composable
private fun Person(friend: Friend?, name: String, f: Forecast?, fmt: Fmt, index: Int) {
    val context = LocalContext.current
    val warned = friend?.let { WarningStore.get(context, it.place.key)?.warnings?.let(WarningStore::active)?.maxByOrNull { w -> w.severity } }
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Avatar(friend, 60.dp, avatarShapes[index % avatarShapes.size])
            if (warned != null) {
                Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(MaterialShapes.Sunny.toShape()).background(warned.severity.color()))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (f != null) {
                WeatherIcon(f.current.code, f.current.isDay, Modifier.size(18.dp))
                Spacer(Modifier.width(2.dp))
                Text(fmt.temp(f.current.temp), style = MaterialTheme.typography.titleSmallEmphasized)
            } else {
                Text("–", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private enum class Compare(val label: String) { TEMP("Temperature"), RAIN("Rain chance"), UV("UV index") }

@Composable
fun FriendsScreen(me: Pair<Place, Forecast>?, settings: Settings, onBack: () -> Unit, onOpenOnMap: () -> Unit) {
    val context = LocalContext.current
    val friends by Friends.flow(context).collectAsState()
    val forecasts = rememberFriendForecasts(friends)
    var metric by remember { mutableStateOf(Compare.TEMP) }
    var day by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Friend?>(null) }
    val zone = me?.second?.zone ?: java.time.ZoneId.systemDefault()
    val fmt = remember(settings, zone) { Fmt(settings, zone) }

    SubScreen("Family & friends", onBack, actions = {
        IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, "Add") }
    }) { w ->
        item {
            Column(w, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConnectedChoice(Compare.entries, metric, { it.label }, { metric = it }, Modifier.fillMaxWidth())
                ConnectedChoice(listOf(0, 1, 2), day, { listOf("Now", "Today", "Tomorrow")[it] }, { day = it }, Modifier.fillMaxWidth())
            }
        }
        val rows = buildList {
            if (me != null) add(Triple<Friend?, String, Forecast?>(null, "You · ${me.first.name}", me.second))
            friends.forEach { add(Triple(it, it.name, forecasts[it.id])) }
        }
        if (friends.isEmpty()) {
            item {
                AppCard(w) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        dev.vis.weather.ui.components.ShapeBadge(
                            Icons.Rounded.Groups, MaterialShapes.Cookie12Sided, size = 88.dp,
                            container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Family & friends weather", style = MaterialTheme.typography.titleLargeEmphasized)
                        Spacer(Modifier.height(6.dp))
                        listOf(
                            "Compare temperature, rain and UV with the people you care about",
                            "Get official weather warnings for their places",
                            "See everyone on the weather map",
                        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { adding = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add someone") }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Names and photos come from contacts you pick and stay on this device. Nothing is uploaded except their place's coordinates to the weather service.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            val values = rows.map { (_, _, f) -> f?.let { valueFor(it, metric, day) } ?: Double.NaN }
            val max = values.filter { !it.isNaN() }.maxOrNull() ?: 1.0
            val min = values.filter { !it.isNaN() }.minOrNull() ?: 0.0
            rows.forEachIndexed { i, (friend, name, f) ->
                item(key = friend?.id ?: "me") {
                    FriendRow(
                        friend, name, f, fmt, metric, day, values[i], min, max, i, w,
                        onRemove = { removing = friend },
                    )
                }
            }
            item {
                TextButton(onClick = onOpenOnMap, modifier = w) { Text("See everyone on the map") }
            }
        }
    }

    if (adding) AddFriendDialog(onDismiss = { adding = false })
    removing?.let { f ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${f.name}?") },
            text = { Text("They'll no longer appear in Family & friends and you won't get warnings for ${f.place.name}.") },
            confirmButton = { TextButton(onClick = { Friends.remove(context, f); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

private fun valueFor(f: Forecast, m: Compare, day: Int): Double {
    val d = if (day == 0) null else f.daily.getOrNull(day - 1)
    return when (m) {
        Compare.TEMP -> d?.max ?: f.current.temp
        Compare.RAIN -> d?.precipProbMax ?: f.upcoming.take(3).maxOfOrNull { if (it.precipProb.isNaN()) 0.0 else it.precipProb } ?: Double.NaN
        Compare.UV -> d?.uvMax ?: f.current.uv
    }
}

@Composable
private fun FriendRow(
    friend: Friend?, name: String, f: Forecast?, fmt: Fmt, metric: Compare, day: Int, value: Double, min: Double, max: Double, index: Int,
    w: Modifier, onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val warning = friend?.let { WarningStore.get(context, it.place.key)?.warnings?.let(WarningStore::active)?.maxByOrNull { x -> x.severity } }
    AppCard(w) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(friend, 52.dp, avatarShapes[index % avatarShapes.size])
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        friend?.place?.let { listOf(it.name, it.region).filter { s -> s.isNotBlank() }.joinToString(", ") }
                            ?: (f?.let { dev.vis.weather.data.WeatherInfo.describe(it.current.code, it.current.isDay) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (f != null) {
                    val d = if (day == 0) null else f.daily.getOrNull(day - 1)
                    WeatherIcon(d?.code ?: f.current.code, d == null && f.current.isDay || d != null, Modifier.size(32.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (metric) {
                            Compare.TEMP -> if (d != null) "${fmt.temp(d.max)} / ${fmt.temp(d.min)}" else fmt.temp(value)
                            Compare.RAIN -> fmt.pct(value)
                            Compare.UV -> if (value.isNaN()) "–" else value.roundToInt().toString()
                        },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                } else {
                    LoadingIndicator(Modifier.size(32.dp))
                }
            }
            if (!value.isNaN()) {
                Spacer(Modifier.height(10.dp))
                val frac = if (max - min < 0.01) 1f else (0.15f + 0.85f * ((value - min) / (max - min)).toFloat())
                val barColor = when (metric) {
                    Compare.TEMP -> tempColor(value)
                    Compare.RAIN -> MaterialTheme.colorScheme.primary
                    Compare.UV -> androidx.compose.ui.graphics.Color(0xFFFFB020)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(barColor))
                }
            }
            if (warning != null) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = warning.severity.color()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(warning.hazard.icon, null, Modifier.size(16.dp), tint = androidx.compose.ui.graphics.Color(warning.severity.onArgb))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${warning.severity.colorName} ${warning.hazard.label.lowercase()} warning",
                            style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color(warning.severity.onArgb),
                        )
                    }
                }
            }
            if (friend != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    friend.contactUri?.let { uri ->
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Contact") }
                    }
                    TextButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Remove") }
                }
            }
        }
    }
}

/** Name (typed or picked from contacts) + a place from search. */
@Composable
private fun AddFriendDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var contact by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf<Place?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        Friends.readPickedContact(context, uri)?.let { (n, p) ->
            name = n
            photo?.let { old -> runCatching { File(old).delete() } }
            photo = p
            contact = uri.toString()
        }
    }
    LaunchedEffect(query) {
        if (query.isBlank() || place?.name == query) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        results = runCatching { WeatherApi.search(query) }.getOrDefault(emptyList())
        searching = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add family or friend") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)) }) {
                        Icon(Icons.Rounded.Contacts, "Pick a contact")
                    }
                }
                OutlinedTextField(
                    query, { query = it; place = null }, label = { Text("Where do they live?") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth(),
                )
                if (searching) LoadingIndicator(Modifier.size(32.dp).align(Alignment.CenterHorizontally))
                if (place == null && results.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(results) { p ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { place = p; query = p.name; results = emptyList() }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                if (p.region.isNotBlank()) Text(p.region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                place?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("${it.name}${if (it.region.isNotBlank()) ", ${it.region}" else ""}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && place != null,
                onClick = {
                    val p = place ?: return@TextButton
                    Friends.add(context, Friend(Friends.newId(), name.trim(), p, photo, contact))
                    scope.launch { runCatching { WarningStore.refresh(context, p, 0) } }
                    onDismiss()
                },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = { photo?.let { runCatching { File(it).delete() } }; onDismiss() }) { Text("Cancel") }
        },
    )
}
