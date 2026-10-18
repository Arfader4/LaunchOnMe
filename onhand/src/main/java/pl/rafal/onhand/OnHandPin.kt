package pl.rafal.onhand

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.rafal.onhand.data.NoteRepository

// "Przypnij do trybu…": lista trybów launchera z polami wyboru. Zaznaczenie od razu przypina notatkę
// na karcie trybu (OnHand w LaunchOnMe), odznaczenie — odpina. Notatka zostaje w OnHand bez zmian.
@Composable
internal fun PinToModesDialog(host: OnHandHost, repo: NoteRepository, noteId: Long, onDismiss: () -> Unit) {
    var modes by remember { mutableStateOf<List<OnHandHost.Mode>?>(null) }
    var pinned by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(noteId) {
        val list = runCatching { host.modes() }.getOrDefault(emptyList())
        pinned = runCatching { host.pinnedModes(noteId) }.getOrDefault(emptySet())
        modes = list
    }

    fun toggle(mode: OnHandHost.Mode) {
        val on = mode.id !in pinned
        pinned = if (on) pinned + mode.id else pinned - mode.id
        // W tle — zapis ma się dokończyć, nawet gdy okno od razu zamkniemy.
        NoteRepository.backgroundScope.launch {
            runCatching {
                if (on) {
                    val title = repo.get(noteId)?.displayTitle.orEmpty().ifBlank { OnHandText.get(R.string.oh_untitled) }
                    host.pin(noteId, mode.id, title)
                } else {
                    host.unpin(noteId, mode.id)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.oh_pin_title)) },
        text = {
            val list = modes
            if (list == null) {
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) // wczytuje się
            } else if (list.isEmpty()) {
                Text(stringResource(R.string.oh_pin_no_modes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.oh_pin_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    list.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { toggle(mode) }
                                .padding(end = 8.dp),
                        ) {
                            Checkbox(checked = mode.id in pinned, onCheckedChange = { toggle(mode) })
                            // Kropka w kolorze trybu (jak na liście trybów w launcherze).
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(mode.color.toInt())),
                            )
                            Text(
                                text = mode.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.oh_done)) }
        },
    )
}
