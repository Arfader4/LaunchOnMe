package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.db.PinnedItemEntity
import pl.rafal.contextlauncher.data.db.PinnedItemEntity.Companion.KIND_FILE
import pl.rafal.contextlauncher.data.db.PinnedItemEntity.Companion.KIND_LINK

// Arkusz z pełną listą: dodawanie, otwieranie, archiwum.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandySheet(
    modeName: String,
    items: List<PinnedItemEntity>,
    onAddFile: () -> Unit,
    onAddLink: () -> Unit,
    onAddNote: () -> Unit,
    onOpen: (PinnedItemEntity) -> Unit,
    onArchive: (PinnedItemEntity) -> Unit,
    onRestore: (PinnedItemEntity) -> Unit,
    onDelete: (PinnedItemEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    // partition ≈ podział listy na dwie według warunku; (a, b) = dekonstrukcja pary.
    val (active, archived) = items.partition { it.archivedAt == null }
    var showArchive by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            item {
                Text(
                    "OnHand · $modeName",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onAddFile, label = { Text("+ Plik") })
                    AssistChip(onClick = onAddLink, label = { Text("+ Link") })
                    AssistChip(onClick = onAddNote, label = { Text("+ Notatka") })
                }
            }
            if (active.isEmpty()) {
                item {
                    Text(
                        "Nic tu jeszcze nie ma. Dodaj plik, link albo notatkę, albo w dowolnej aplikacji wybierz " +
                            "„Udostępnij → Przypnij do trybu”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(active, key = { it.id }) { item ->
                PinnedRow(
                    item = item,
                    onOpen = { onOpen(item) },
                    menu = listOf(
                        MenuAction("Archiwizuj") { onArchive(item) },
                        MenuAction("Usuń") { onDelete(item) },
                    ),
                )
            }
            if (archived.isNotEmpty()) {
                item {
                    TextButton(onClick = { showArchive = !showArchive }) {
                        Text(if (showArchive) "Ukryj archiwum" else "Archiwum (${archived.size})")
                    }
                }
                if (showArchive) {
                    items(archived, key = { it.id }) { item ->
                        PinnedRow(
                            item = item,
                            onOpen = { onOpen(item) },
                            menu = listOf(
                                MenuAction("Przywróć") { onRestore(item) },
                                MenuAction("Usuń") { onDelete(item) },
                            ),
                            dimmed = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedRow(
    item: PinnedItemEntity,
    onOpen: () -> Unit,
    menu: List<MenuAction>,
    dimmed: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.6f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .heightIn(min = 64.dp)
            .padding(start = 12.dp),
    ) {
        KindBadge(item)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { menuOpen = true }
                    .semantics { contentDescription = "Więcej opcji" },
            ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menu.forEach { action ->
                    DropdownMenuItem(text = { Text(action.label) }, onClick = {
                        menuOpen = false
                        action.onClick()
                    })
                }
            }
        }
    }
}

// Kolorowy kwadrat z literą rodzaju: P = plik, L = link, N = notatka.
@Composable
fun KindBadge(item: PinnedItemEntity, size: Int = 40) {
    // Kolor rodzaju + jego półprzezroczysta wersja jako tło: czytelne w jasnym i ciemnym motywie.
    val (letter, fg) = when (item.kind) {
        KIND_FILE -> "P" to MaterialTheme.colorScheme.primary
        KIND_LINK -> "L" to Color(0xFF5B8DEF)
        else -> "N" to Color(0xFF3FA07A)
    }
    val bg = fg.copy(alpha = 0.18f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(bg),
    ) {
        Text(letter, color = fg, fontWeight = FontWeight.Bold)
    }
}

private fun subtitle(item: PinnedItemEntity): String = when (item.kind) {
    KIND_FILE -> when {
        item.mimeType?.contains("pdf") == true -> "PDF"
        item.mimeType?.startsWith("image/") == true -> "Obraz"
        else -> "Plik"
    }
    KIND_LINK -> item.uri.orEmpty()
    else -> item.text?.lineSequence()?.firstOrNull().orEmpty()
}

// Okno dodawania linku.
@Composable
fun LinkDialog(onConfirm: (url: String, title: String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Nowy link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Adres") }, singleLine = true)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Nazwa (opcjonalnie)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank(), onClick = { onConfirm(url, title) }) { Text("Przypnij") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// Okno notatki: nowa albo edycja istniejącej.
@Composable
fun NoteDialog(
    initialTitle: String,
    initialText: String,
    confirmLabel: String,
    onConfirm: (title: String, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var text by remember { mutableStateOf(initialText) }
    // Własne okno zamiast AlertDialog: edge-to-edge + imePadding, więc okno przesuwa się nad klawiaturę,
    // a długa treść przewija się w środku (AlertDialog bywał częściowo zasłonięty przez klawiaturę).
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .systemBarsPadding()
                .imePadding()
                .padding(16.dp),
        ) {
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // Dotknięcie wewnątrz okna nie może go zamykać.
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text("Notatka", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Tytuł (opcjonalnie)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Treść") },
                        minLines = 4,
                        maxLines = 14,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDismiss) { Text("Anuluj") }
                        TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(title, text) }) { Text(confirmLabel) }
                    }
                }
            }
        }
    }
}
