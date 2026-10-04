package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.AppInfo

// Wybór skrótu aplikacji (np. "Nowa wiadomość", czat z konkretną osobą, "Skanuj kod").
// Skróty są pogrupowane po aplikacji: nagłówek z ikoną aplikacji, pod nim jej skróty.
@Composable
fun ShortcutPickerDialog(
    shortcuts: List<AppInfo>,
    apps: List<AppInfo>,       // do nagłówków grup (nazwa i ikona aplikacji)
    hasAccess: Boolean,        // skróty udostępnia system tylko domyślnemu launcherowi
    title: String,
    onPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val appByPackage = remember(apps) { apps.associateBy { it.packageName + "#" + it.userSerial } }
    // groupBy ≈ GroupBy w LINQ; kolejność grup = alfabetycznie po nazwie aplikacji.
    val groups = remember(shortcuts, filter) {
        shortcuts
            .filter { sc ->
                val app = appByPackage[sc.packageName + "#" + sc.userSerial]
                filter.isBlank() || sc.label.contains(filter.trim(), true) || (app?.label?.contains(filter.trim(), true) == true)
            }
            .groupBy { it.packageName + "#" + it.userSerial }
            .toList()
            .sortedBy { (k, _) -> appByPackage[k]?.label?.lowercase() ?: k }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                if (!hasAccess) {
                    Text(
                        stringResource(R.string.shortcut_no_access),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = { Text(stringResource(R.string.shortcut_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (groups.isEmpty()) {
                        Text(
                            stringResource(R.string.shortcut_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                        groups.forEach { (groupKey, list) ->
                            val app = appByPackage[groupKey]
                            item(key = "h:$groupKey") {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                                    app?.let { Image(it.icon, contentDescription = null, modifier = Modifier.size(22.dp)) }
                                    Spacer(Modifier.width(8.dp))
                                    Text(app?.label ?: list.first().packageName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            items(list, key = { it.key }) { sc ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onPick(sc) }
                                        .heightIn(min = 48.dp)
                                        .padding(start = 30.dp, end = 8.dp),
                                ) {
                                    Image(sc.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(sc.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
            }
        }
    }
}
