@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vis.weather.data.Updater

@Composable
fun UpdateDialog(u: UpdateUi, onUpdate: () -> Unit, onLater: () -> Unit) {
    val r = u.release ?: return
    if (u.dismissed) return
    val busy = u.progress != null
    AlertDialog(
        onDismissRequest = { if (!busy) onLater() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("Update available") },
        text = {
            Column {
                Text("Version ${r.version} is out (you have ${Updater.currentVersion}).", style = MaterialTheme.typography.bodyMedium)
                if (r.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        r.notes.take(1500),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    )
                }
                u.progress?.let { p ->
                    Spacer(Modifier.height(16.dp))
                    LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
                val msg = u.message
                if (!busy && msg != null && msg != "Version ${r.version} is available") {
                    Spacer(Modifier.height(12.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !busy) {
                Text(if (busy) "Downloading ${((u.progress ?: 0f) * 100).toInt()}%" else "Update")
            }
        },
        dismissButton = { TextButton(onClick = onLater, enabled = !busy) { Text("Later") } },
    )
}
