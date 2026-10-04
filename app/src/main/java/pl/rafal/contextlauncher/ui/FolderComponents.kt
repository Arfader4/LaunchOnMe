package pl.rafal.contextlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.alpha
import pl.rafal.contextlauncher.data.CategoryLabels
import pl.rafal.contextlauncher.data.suggestedApps
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R

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
    val onSetLook: (FolderEntity, icon: String?, color: Long?) -> Unit,
    val onPlaceOnCard: ((folderId: Long, w: Int, h: Int) -> Unit)?, // null = bez opcji "Na kartę"
    val sizeOnCard: (folderId: Long) -> Pair<Int, Int>? = { null },  // rozmiar widżetu na aktywnej karcie
    val modeName: String = "",
    val onCopyToCard: ((folderId: Long) -> Unit)? = null, // niezależna kopia na kartę (tylko dla tego trybu)
)

// Przeglądarka folderów: ścieżka, akcje, podfoldery i aplikacje bieżącego folderu.
// startFolderId = null → zaczynamy od korzenia (lista folderów głównych).
// dragOut ≠ null (szuflada): foldery i aplikacje można przytrzymać i przeciągnąć na kartę.
@Composable
fun FolderBrowser(
    tree: FolderTree,
    allApps: List<AppInfo>,
    startFolderId: Long?,
    callbacks: FolderCallbacks,
    modifier: Modifier = Modifier,
    dragOut: ExternalDrag? = null,
) {
    var currentId by rememberSaveable(startFolderId) { mutableStateOf(startFolderId) }
    val current = tree.folder(currentId)

    // Okna dialogowe. Trzymają folder, którego dotyczą — bieżący (menu ⋮) albo kafelek (przytrzymanie).
    var newFolderOpen by remember { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<FolderEntity?>(null) }
    var deleteFor by remember { mutableStateOf<FolderEntity?>(null) }
    var lookFor by remember { mutableStateOf<FolderEntity?>(null) }
    var sizeFor by remember { mutableStateOf<FolderEntity?>(null) }
    var pickAppsOpen by remember { mutableStateOf(false) }
    var appToMove by remember { mutableStateOf<FolderApp?>(null) }
    val context = LocalContext.current

    // Te same opcje w menu ⋮ bieżącego folderu i po przytrzymaniu kafelka podfolderu.
    fun menuFor(folder: FolderEntity): List<MenuAction> = buildList { // buildList ≈ new List<T> { ... } z warunkami
        add(MenuAction(context.getString(R.string.common_rename)) { renameFor = folder })
        add(MenuAction(context.getString(R.string.folder_menu_look)) { lookFor = folder })
        if (callbacks.onPlaceOnCard != null) {
            val onCard = callbacks.sizeOnCard(folder.id) != null
            add(MenuAction(if (onCard) context.getString(R.string.folder_menu_size_on_card) else context.getString(R.string.folder_menu_place_on_card, callbacks.modeName)) { sizeFor = folder })
        }
        callbacks.onCopyToCard?.let { copy ->
            add(MenuAction(context.getString(R.string.folder_menu_copy_to_card, callbacks.modeName)) { copy(folder.id) })
        }
        add(MenuAction(context.getString(R.string.folder_menu_delete_folder)) { deleteFor = folder })
    }

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
                Crumb(stringResource(R.string.folder_root_crumb), isLast = visible.isEmpty()) { currentId = null }
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
            if (current != null) FolderBadge(current, tree.previewApps(current.id), 32.dp, Modifier.clickable { lookFor = current })
            AssistChip(onClick = { newFolderOpen = true }, label = { Text(if (current == null) stringResource(R.string.folder_add_folder_chip) else stringResource(R.string.folder_add_subfolder_chip)) })
            if (current != null) {
                AssistChip(onClick = { pickAppsOpen = true }, label = { Text(stringResource(R.string.folder_add_apps_button)) })
                Spacer(Modifier.weight(1f))
                OverflowMenu(menuFor(current))
            }
        }
        if (dragOut != null && current == null && tree.folders.isNotEmpty()) {
            Text(
                stringResource(R.string.folder_browser_drag_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        val subfolders = tree.subfolders(currentId)
        val apps = current?.let { tree.apps(it.id) }.orEmpty()

        if (subfolders.isEmpty() && apps.isEmpty()) {
            Text(
                if (current == null) stringResource(R.string.folder_browser_no_folders)
                else stringResource(R.string.folder_browser_empty),
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
                    folder = folder,
                    name = folder.name,
                    preview = tree.previewApps(folder.id),
                    count = tree.totalApps(folder.id),
                    onClick = { currentId = folder.id },
                    menu = menuFor(folder),
                    dragOut = dragOut,
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
                        MenuAction(stringResource(R.string.folder_menu_move_to)) { appToMove = folderApp },
                        MenuAction(stringResource(R.string.folder_menu_remove_from_folder)) { callbacks.onRemoveApp(folderApp) },
                        MenuAction(stringResource(R.string.folder_menu_app_info)) { callbacks.onAppInfo(folderApp.app) },
                    ),
                    dragOut = dragOut, // aplikację z folderu też można wyciągnąć na kartę
                )
            }
        }
    }

    if (newFolderOpen) {
        TextInputDialog(
            title = if (current == null) stringResource(R.string.folder_new_folder) else stringResource(R.string.folder_new_subfolder_in_title, current.name),
            initial = "",
            confirmLabel = stringResource(R.string.common_create),
            onConfirm = { name ->
                callbacks.onCreateFolder(currentId, name)
                newFolderOpen = false
            },
            onDismiss = { newFolderOpen = false },
        )
    }

    renameFor?.let { folder ->
        TextInputDialog(
            title = stringResource(R.string.common_rename),
            initial = folder.name,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { name ->
                callbacks.onRenameFolder(folder, name)
                renameFor = null
            },
            onDismiss = { renameFor = null },
        )
    }

    deleteFor?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.folder_delete_title, folder.name)) },
            text = { Text(stringResource(R.string.folder_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    // Usuwamy folder, w którym właśnie jesteśmy (albo jego przodka)? Najpierw z niego wychodzimy.
                    if (tree.path(currentId).any { it.id == folder.id }) currentId = folder.parentId
                    callbacks.onDeleteFolder(folder)
                    deleteFor = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (pickAppsOpen && current != null) {
        AppMultiPickerDialog(
            allApps = allApps,
            alreadySelected = tree.apps(current.id).map { it.app.key }.toSet(),
            suggestions = suggestedApps(allApps, tree.apps(current.id).map { it.app }, current.name),
            title = stringResource(R.string.folder_add_to_title, current.name),
            onConfirm = { chosen ->
                callbacks.onAddApps(current.id, chosen)
                pickAppsOpen = false
            },
            onDismiss = { pickAppsOpen = false },
        )
    }

    lookFor?.let { folder ->
        FolderLookDialog(
            folder = folder,
            allApps = allApps,
            preview = tree.previewApps(folder.id),
            onSave = { icon, color ->
                callbacks.onSetLook(folder, icon, color)
                lookFor = null
            },
            onDismiss = { lookFor = null },
        )
    }

    val place = callbacks.onPlaceOnCard
    val sizeTarget = sizeFor
    if (sizeTarget != null && place != null) {
        FolderSizeDialog(
            folderName = sizeTarget.name,
            modeName = callbacks.modeName,
            current = callbacks.sizeOnCard(sizeTarget.id),
            onPick = { w, h ->
                place(sizeTarget.id, w, h)
                sizeFor = null
            },
            onDismiss = { sizeFor = null },
        )
    }

    appToMove?.let { folderApp ->
        FolderPickerDialog(
            tree = tree,
            title = stringResource(R.string.folder_move_app_title, folderApp.app.label),
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

// Kafelek folderu: jego symbol albo miniatura 2×2 z pierwszych ikon + nazwa + liczba aplikacji.
// Przytrzymanie = menu folderu; przytrzymanie i ruch palcem = przeciąganie na kartę (jak ikona aplikacji).
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTile(
    folder: FolderEntity,
    name: String,
    preview: List<AppInfo>,
    count: Int,
    onClick: () -> Unit,
    menu: List<MenuAction> = emptyList(),
    dragOut: ExternalDrag? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val currentDrag by rememberUpdatedState(dragOut)
    val currentFolder by rememberUpdatedState(folder)
    val currentPreview by rememberUpdatedState(preview)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = if (menu.isNotEmpty()) ({ menuOpen = true }) else null)
                .onGloballyPositioned { origin = it.positionInRoot() }
                .then(
                    if (dragOut != null) Modifier.pointerInput(folder.id) {
                        var total = Offset.Zero
                        var started = false
                        val threshold = with(density) { 16.dp.toPx() }
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                total = Offset.Zero
                                started = false
                            },
                            onDrag = { change, amount ->
                                total += amount
                                if (!started && total.getDistance() > threshold) {
                                    started = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuOpen = false
                                    currentDrag?.onStart(DragItem.Folder(currentFolder, currentPreview), origin + change.position)
                                }
                                if (started) {
                                    change.consume()
                                    currentDrag?.onMove(origin + change.position)
                                }
                            },
                            onDragEnd = { if (started) currentDrag?.onEnd() },
                            onDragCancel = { if (started) currentDrag?.onCancel() },
                        )
                    } else Modifier,
                )
                .padding(vertical = 6.dp),
        ) {
            FolderBadge(folder, preview, 52.dp)
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

@Composable
private fun OverflowMenu(actions: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    val moreDesc = stringResource(R.string.folder_more_options_desc)
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { open = true }
                .semantics { contentDescription = moreDesc },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// Wybór wielu aplikacji naraz (dawna nazwa zostaje, żeby nie zmieniać wywołań).
@Composable
fun AppMultiPickerDialog(
    allApps: List<AppInfo>,
    alreadySelected: Set<String>,
    onConfirm: (List<AppInfo>) -> Unit,
    onDismiss: () -> Unit,
    suggestions: List<AppInfo> = emptyList(),
    title: String = stringResource(R.string.folder_pick_apps_title),
) = AppGridPickerDialog(allApps, title, single = false, alreadySelected, suggestions, onConfirm, onDismiss)

// Wybór aplikacji w układzie szuflady (siatka ikon, a nie długa lista), z wyszukiwarką i filtrami-tagami:
// "Proponowane" (pasujące do folderu) oraz kategorie ze sklepu. single = jedno dotknięcie wybiera i zamyka.
@Composable
fun AppGridPickerDialog(
    allApps: List<AppInfo>,
    title: String,
    single: Boolean = false,
    alreadySelected: Set<String> = emptySet(),
    suggestions: List<AppInfo> = emptyList(),
    onConfirm: (List<AppInfo>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) } // klucze zaznaczonych aplikacji
    var filter by remember { mutableStateOf("") }
    // Filtr-tag: null = wszystkie, -1 = proponowane, inaczej numer kategorii.
    var tag by remember { mutableStateOf(if (suggestions.isNotEmpty()) -1 else null) }
    val presentCategories = remember(allApps) { allApps.map { it.category }.toSet() }
    val suggestedKeys = remember(suggestions) { suggestions.map { it.key }.toSet() }
    val shown = allApps.filter { app ->
        (filter.isBlank() || app.label.contains(filter.trim(), ignoreCase = true)) &&
            when (tag) {
                null -> true
                -1 -> app.key in suggestedKeys
                else -> app.category == tag
            }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text(stringResource(R.string.folder_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                ) {
                    if (suggestions.isNotEmpty()) {
                        FilterChip(selected = tag == -1, onClick = { tag = -1 }, label = { Text(stringResource(R.string.folder_pick_suggested, suggestions.size)) })
                    }
                    FilterChip(selected = tag == null, onClick = { tag = null }, label = { Text(stringResource(R.string.folder_pick_all)) })
                    CategoryLabels.filterKeys { it in presentCategories }.forEach { (cat, name) ->
                        FilterChip(selected = tag == cat, onClick = { tag = cat }, label = { Text(name) })
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.folder_pick_no_match),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(76.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(shown, key = { it.key }) { app ->
                        val inFolder = app.key in alreadySelected
                        val checked = inFolder || app.key in selected
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !inFolder) {
                                    if (single) {
                                        onConfirm(listOf(app))
                                    } else {
                                        // Zbiory są niezmienne: tworzymy nowy z dodanym/usuniętym elementem.
                                        selected = if (app.key in selected) selected - app.key else selected + app.key
                                    }
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Box {
                                Image(
                                    app.icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .alpha(if (inFolder) 0.4f else 1f),
                                )
                                if (checked) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    ) { Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                            Text(
                                app.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    if (!single) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = selected.isNotEmpty(),
                            onClick = { onConfirm(allApps.filter { it.key in selected }) },
                        ) { Text(stringResource(R.string.folder_pick_add_count, selected.size)) }
                    }
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
                            placeholder = { Text(stringResource(R.string.folder_new_folder)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(enabled = newName.isNotBlank(), onClick = { onCreateNew(newName) }) { Text(stringResource(R.string.common_create)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (entries.isEmpty()) {
                    Text(
                        if (onCreateNew != null) stringResource(R.string.folder_picker_none_type_name)
                        else stringResource(R.string.folder_picker_none_drawer),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FolderPickerList(entries, onPick)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
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
