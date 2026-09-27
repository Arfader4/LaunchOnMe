package pl.rafal.contextlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.FolderApp
import pl.rafal.contextlauncher.data.FolderTree
import pl.rafal.contextlauncher.data.db.FolderEntity

// Wszystko, co przeglądarka folderów może zlecić na zewnątrz. Klasa z polami-funkcjami
// zamiast dziesięciu osobnych parametrów (jak obiekt z delegatami w C#).
class FolderCallbacks(
    val onLaunch: (AppInfo) -> Unit,
    val onAppInfo: (AppInfo) -> Unit,
    val onCreateFolder: (parentId: Long?, name: String) -> Unit,
    val onRenameFolder: (FolderEntity, String) -> Unit,
    val onDeleteFolder: (FolderEntity) -> Unit,
    val onAddApps: (folderId: Long, List<AppInfo>) -> Unit,
    val onMoveApp: (FolderApp, folderId: Long) -> Unit,
    val onRemoveApp: (FolderApp) -> Unit,
    val onAddToCard: ((folderId: Long) -> Unit)?, // null = nie pokazuj opcji "Dodaj na kartę"
)

// Przeglądarka folderów: ścieżka, akcje, podfoldery i aplikacje bieżącego folderu.
// startFolderId = null → zaczynamy od korzenia (lista folderów głównych).
@Composable
fun FolderBrowser(
    tree: FolderTree,
    allApps: List<AppInfo>,
    startFolderId: Long?,
    callbacks: FolderCallbacks,
    modifier: Modifier = Modifier,
) {
    var currentId by rememberSaveable(startFolderId) { mutableStateOf(startFolderId) }
    val current = tree.folder(currentId)

    // Okna dialogowe tej przeglądarki.
    var newFolderOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var pickAppsOpen by remember { mutableStateOf(false) }
    var appToMove by remember { mutableStateOf<FolderApp?>(null) }

    // Wstecz = poziom wyżej, dopóki nie wrócimy do folderu startowego.
    BackHandler(enabled = currentId != startFolderId) { currentId = current?.parentId }

    Column(modifier) {
        // Ścieżka: "Foldery › Praca › Dokumenty" (każdy element klikalny).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            val path = tree.path(currentId)
            // Gdy zaczynamy od konkretnego folderu (np. z widżetu), ścieżka zaczyna się od niego.
            val visible = if (startFolderId == null) path else path.dropWhile { it.id != startFolderId }
            if (startFolderId == null) {
                Crumb("Foldery", isLast = visible.isEmpty()) { currentId = null }
            }
            visible.forEachIndexed { index, folder ->
                if (index > 0 || startFolderId == null) {
                    Text(" › ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Crumb(folder.name, isLast = index == visible.lastIndex) { currentId = folder.id }
            }
        }

        // Akcje bieżącego folderu.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AssistChip(onClick = { newFolderOpen = true }, label = { Text(if (current == null) "+ Folder" else "+ Podfolder") })
            if (current != null) {
                AssistChip(onClick = { pickAppsOpen = true }, label = { Text("+ Aplikacje") })
                Spacer(Modifier.weight(1f))
                OverflowMenu(
                    buildList { // buildList ≈ new List<T> { ... } z warunkami w środku
                        add(MenuAction("Zmień nazwę") { renameOpen = true })
                        callbacks.onAddToCard?.let { add(MenuAction("Dodaj na kartę trybu") { it(current.id) }) }
                        add(MenuAction("Usuń folder") { deleteOpen = true })
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val subfolders = tree.subfolders(currentId)
        val apps = current?.let { tree.apps(it.id) }.orEmpty()

        if (subfolders.isEmpty() && apps.isEmpty()) {
            Text(
                if (current == null) "Nie masz jeszcze folderów. Utwórz pierwszy przyciskiem „+ Folder”."
                else "Pusty folder. Dodaj aplikacje albo podfolder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(LauncherViewModel.COLUMNS),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(subfolders, key = { "f${it.id}" }) { folder ->
                FolderTile(
                    name = folder.name,
                    preview = tree.previewApps(folder.id),
                    count = tree.totalApps(folder.id),
                    onClick = { currentId = folder.id },
                )
            }
            if (subfolders.isNotEmpty() && apps.isNotEmpty()) {
                // Separator na całą szerokość siatki.
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(4.dp)) }
            }
            items(apps, key = { "a${it.entry.id}" }) { folderApp ->
                AppTile(
                    app = folderApp.app,
                    onClick = { callbacks.onLaunch(folderApp.app) },
                    menu = listOf(
                        MenuAction("Przenieś do…") { appToMove = folderApp },
                        MenuAction("Usuń z folderu") { callbacks.onRemoveApp(folderApp) },
                        MenuAction("Informacje o aplikacji") { callbacks.onAppInfo(folderApp.app) },
                    ),
                )
            }
        }
    }

    if (newFolderOpen) {
        TextInputDialog(
            title = if (current == null) "Nowy folder" else "Nowy podfolder w „${current.name}”",
            initial = "",
            confirmLabel = "Utwórz",
            onConfirm = { name ->
                callbacks.onCreateFolder(currentId, name)
                newFolderOpen = false
            },
            onDismiss = { newFolderOpen = false },
        )
    }

    if (renameOpen && current != null) {
        TextInputDialog(
            title = "Zmień nazwę",
            initial = current.name,
            confirmLabel = "Zapisz",
            onConfirm = { name ->
                callbacks.onRenameFolder(current, name)
                renameOpen = false
            },
            onDismiss = { renameOpen = false },
        )
    }

    if (deleteOpen && current != null) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Usunąć folder „${current.name}”?") },
            text = { Text("Znikną też jego podfoldery. Same aplikacje zostaną w telefonie.") },
            confirmButton = {
                TextButton(onClick = {
                    val folder = current
                    currentId = folder.parentId // najpierw wychodzimy z folderu, potem go usuwamy
                    callbacks.onDeleteFolder(folder)
                    deleteOpen = false
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Anuluj") } },
        )
    }

    if (pickAppsOpen && current != null) {
        AppMultiPickerDialog(
            allApps = allApps,
            alreadySelected = tree.apps(current.id).map { it.app.key }.toSet(),
            onConfirm = { chosen ->
                callbacks.onAddApps(current.id, chosen)
                pickAppsOpen = false
            },
            onDismiss = { pickAppsOpen = false },
        )
    }

    appToMove?.let { folderApp ->
        FolderPickerDialog(
            tree = tree,
            title = "Przenieś „${folderApp.app.label}” do…",
            onPick = { target ->
                callbacks.onMoveApp(folderApp, target.id)
                appToMove = null
            },
            onDismiss = { appToMove = null },
        )
    }
}

@Composable
private fun Crumb(label: String, isLast: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    )
}

// Kafelek folderu: miniatura 2×2 z pierwszych ikon + nazwa + liczba aplikacji.
@Composable
fun FolderTile(name: String, preview: List<AppInfo>, count: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                preview.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { app -> Image(app.icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "$name ($count)",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OverflowMenu(actions: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { open = true }
                .semantics { contentDescription = "Więcej opcji folderu" },
        ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, onClick = {
                    open = false
                    action.onClick()
                })
            }
        }
    }
}

// Proste okno z jednym polem tekstowym (nowy folder, zmiana nazwy).
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// Wybór wielu aplikacji naraz, z wyszukiwarką.
@Composable
fun AppMultiPickerDialog(
    allApps: List<AppInfo>,
    alreadySelected: Set<String>,
    onConfirm: (List<AppInfo>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) } // klucze zaznaczonych aplikacji
    var filter by remember { mutableStateOf("") }
    val shown = allApps.filter { filter.isBlank() || it.label.contains(filter.trim(), ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Wybierz aplikacje", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Szukaj") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                    items(shown, key = { it.key }) { app ->
                        val inFolder = app.key in alreadySelected
                        val checked = inFolder || app.key in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !inFolder) {
                                    // Zbiory są niezmienne: tworzymy nowy z dodanym/usuniętym elementem.
                                    selected = if (app.key in selected) selected - app.key else selected + app.key
                                }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 8.dp),
                        ) {
                            Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Checkbox(checked = checked, onCheckedChange = null, enabled = !inFolder)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Anuluj") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = { onConfirm(allApps.filter { it.key in selected }) },
                    ) { Text("Dodaj (${selected.size})") }
                }
            }
        }
    }
}

// Wybór folderu z całego drzewa (z wcięciami pokazującymi zagnieżdżenie).
@Composable
fun FolderPickerDialog(
    tree: FolderTree,
    title: String,
    onPick: (FolderEntity) -> Unit,
    onDismiss: () -> Unit,
    onCreateNew: ((name: String) -> Unit)? = null, // null = bez pola "Nowy folder"
) {
    val entries = tree.flatten()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column {
                // Nowy folder w miejscu, bez wychodzenia z okna.
                if (onCreateNew != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("Nowy folder") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(enabled = newName.isNotBlank(), onClick = { onCreateNew(newName) }) { Text("Utwórz") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (entries.isEmpty()) {
                    Text(
                        if (onCreateNew != null) "Nie masz jeszcze folderów. Wpisz nazwę pierwszego."
                        else "Nie masz jeszcze folderów. Utwórz je w menu ⋯ → Foldery.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FolderPickerList(entries, onPick)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun FolderPickerList(entries: List<Pair<FolderEntity, Int>>, onPick: (FolderEntity) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        items(entries, key = { it.first.id }) { (folder, depth) ->
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPick(folder) }
                    .padding(start = (8 + depth * 20).dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            )
        }
    }
}
