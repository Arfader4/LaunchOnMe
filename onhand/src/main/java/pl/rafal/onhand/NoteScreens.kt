package pl.rafal.onhand

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.rafal.onhand.data.NoteEntity
import pl.rafal.onhand.data.NoteRepository

// ---------- Lista notatek ----------

@Composable
internal fun NoteListScreen(
    repo: NoteRepository,
    pickMode: Boolean,
    archiveTab: Boolean,
    onArchiveTab: (Boolean) -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = rememberNoteExporter(repo)

    var query by rememberSaveable { mutableStateOf("") }
    // Zakładka (archiveTab) trzyma OnHandApp — lista znika na czas edytora, a po powrocie ma być ta sama zakładka.
    // W trybie wyboru (z launchera) tylko aktywne notatki.
    val archived = archiveTab && !pickMode
    // Zaznaczone notatki (LongArray, bo da się go zapisać w Bundle — przeżyje obrót ekranu).
    var selectedIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var shareDialog by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Zapytanie jako Flow: lista przelicza się sama przy zmianie bazy i przy pisaniu w polu wyszukiwania
    // (jak IObservable połączony z drugim w Rx.NET — combine).
    val queryFlow = remember { MutableStateFlow("") }
    LaunchedEffect(query) { queryFlow.value = query }
    // onStart { emit(null) }: po zmianie zakładki najpierw "wczytuje się", a nie stara lista z drugiej zakładki.
    val notes by remember(repo, archived) {
        repo.search(archived = archived, query = queryFlow)
            .map<List<NoteEntity>, List<NoteEntity>?> { it }
            .onStart { emit(null) }
    }.collectAsState(initial = null)

    // Zaznaczenie liczy się tylko dla notatek widocznych na liście (inne mogły zniknąć — usunięte, przeniesione).
    val visibleIds = notes.orEmpty().map { it.id }.toSet()
    val selected = selectedIds.filter { it in visibleIds }.toSet()
    val selecting = selected.isNotEmpty()
    val clearSelection = { selectedIds = longArrayOf() }
    val toggle = { id: Long ->
        selectedIds = if (id in selected) (selected - id).toLongArray() else (selected + id).toLongArray()
    }

    // Wstecz: najpierw kończy zaznaczanie, potem czyści wyszukiwanie.
    BackHandler(enabled = selecting) { clearSelection() }
    BackHandler(enabled = !selecting && query.isEmpty() && archived) { onArchiveTab(false) }
    BackHandler(enabled = !selecting && query.isNotEmpty()) { query = "" }

    // Archiwizuj / Przywróć / Usuń zaznaczone — w tle, z krótkim komunikatem (lokalna funkcja ≈ lokalna funkcja w C#).
    fun runOnSelected(@StringRes done: Int, action: suspend (List<Long>) -> Unit) {
        val ids = selected.toList()
        clearSelection()
        val app = context.applicationContext
        NoteRepository.backgroundScope.launch {
            action(ids)
            withContext(Dispatchers.Main) {
                Toast.makeText(app, OnHandText.get(done, ids.size), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!pickMode && !selecting && !archived) {
                ExtendedFloatingActionButton(onClick = onNew) {
                    Text("+  " + stringResource(R.string.oh_new_note))
                }
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionBar(
                    archived = archived,
                    onShare = {
                        // Jedna notatka — od razu jako tekst. Kilka — pytamy: jeden tekst czy osobne pliki.
                        if (selected.size == 1) {
                            val ids = selected.toList()
                            clearSelection()
                            scope.launch { NoteExport.shareText(context, repo.getMany(ids)) }
                        } else {
                            shareDialog = true
                        }
                    },
                    onExport = { exportDialog = true },
                    onArchive = {
                        if (archived) {
                            runOnSelected(R.string.oh_restored_n) { ids -> repo.restoreMany(ids) }
                        } else {
                            runOnSelected(R.string.oh_archived_n) { ids -> repo.archiveMany(ids) }
                        }
                    },
                    onDelete = { confirmDelete = true },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (selecting) {
                // Pasek zaznaczania zamiast nagłówka: ✕, liczba, "Zaznacz wszystkie".
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { clearSelection() }) {
                        Text("✕", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = stringResource(R.string.oh_selected_n, selected.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected.size < visibleIds.size) {
                        TextButton(onClick = { selectedIds = visibleIds.toLongArray() }) {
                            Text(stringResource(R.string.oh_select_all))
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(if (pickMode) R.string.oh_pick_title else R.string.onhand_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.oh_search)) },
                leadingIcon = { Text("🔍") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { query = "" }) { Text("✕") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (!pickMode) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TabChip(stringResource(R.string.oh_notes), selected = !archiveTab) {
                        if (archiveTab) {
                            onArchiveTab(false)
                            clearSelection()
                        }
                    }
                    TabChip(stringResource(R.string.oh_tab_archive), selected = archiveTab) {
                        if (!archiveTab) {
                            onArchiveTab(true)
                            clearSelection()
                        }
                    }
                }
            }
            val list = notes
            if (list == null) {
                Spacer(Modifier.weight(1f)) // jeszcze się wczytuje — pusto, bez migania komunikatu
            } else if (list.isEmpty()) {
                val empty = when {
                    query.isNotBlank() -> R.string.oh_no_results
                    archived -> R.string.oh_archive_empty
                    else -> R.string.oh_empty
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            selected = note.id in selected,
                            // Przy zaznaczaniu dotknięcie zaznacza/odznacza; długie przytrzymanie zaczyna zaznaczanie.
                            onClick = { if (selecting) toggle(note.id) else onOpen(note.id) },
                            onLongClick = if (pickMode) null else ({ toggle(note.id) }),
                        )
                    }
                }
            }
        }
    }

    if (shareDialog) {
        val ids = selected.toList()
        ChoiceDialog(
            title = stringResource(R.string.oh_share_n, ids.size),
            options = listOf(
                stringResource(R.string.oh_share_as_text) to {
                    clearSelection()
                    scope.launch { NoteExport.shareText(context, repo.getMany(ids)) }
                    Unit
                },
                stringResource(R.string.oh_share_as_files) to {
                    clearSelection()
                    scope.launch { NoteExport.shareFiles(context, repo.getMany(ids), NoteFormat.TXT) }
                    Unit
                },
            ),
            onDismiss = { shareDialog = false },
        )
    }

    if (exportDialog) {
        val ids = selected.toList()
        ChoiceDialog(
            title = stringResource(R.string.oh_export_format),
            message = if (ids.size > 1) stringResource(R.string.oh_export_zip_hint, ids.size) else null,
            options = listOf(
                stringResource(R.string.oh_format_txt) to {
                    exporter.export(ids, NoteFormat.TXT)
                    clearSelection()
                },
                stringResource(R.string.oh_format_md) to {
                    exporter.export(ids, NoteFormat.MD)
                    clearSelection()
                },
            ),
            onDismiss = { exportDialog = false },
        )
    }

    if (confirmDelete) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.oh_delete_many_title, count)) },
            text = { Text(stringResource(R.string.oh_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    runOnSelected(R.string.oh_deleted_n) { ids -> repo.deleteMany(ids) }
                }) { Text(stringResource(R.string.oh_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.oh_cancel)) }
            },
        )
    }
}

// Zakładka "Notatki" / "Archiwum" — mały przełącznik w kształcie pigułki.
@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// Dolny pasek przy zaznaczaniu: Udostępnij, Eksport, Archiwizuj/Przywróć, Usuń.
@Composable
private fun SelectionBar(
    archived: Boolean,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onShare) { Text(stringResource(R.string.oh_share)) }
            TextButton(onClick = onExport) { Text(stringResource(R.string.oh_export)) }
            TextButton(onClick = onArchive) {
                Text(stringResource(if (archived) R.string.oh_restore else R.string.oh_archive))
            }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.oh_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// Okno z kilkoma przyciskami do wyboru (jedna kolumna), np. "Jako tekst / Jako pliki". Wybór zamyka okno.
@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                options.forEach { (label, action) ->
                    TextButton(
                        onClick = {
                            onDismiss()
                            action()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label, modifier = Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.oh_cancel)) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(note: NoteEntity, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val preview = notePreview(note)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // combinedClickable ≈ osobne zdarzenia Click i LongPress w jednej kontrolce.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Text(
                        "✓  ",
                        color = Color(0xFF5BD47A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = note.displayTitle.ifBlank { stringResource(R.string.oh_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = relativeTime(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// Podgląd treści pod tytułem. Gdy tytułu nie ma, tytułem jest pierwsza linia — wtedy podgląd zaczyna się od kolejnej.
private fun notePreview(note: NoteEntity): String {
    val lines = note.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val rest = if (note.title.isBlank()) lines.drop(1) else lines
    return rest.joinToString(" · ").take(300)
}

// "5 min temu", "wczoraj"… w języku telefonu.
internal fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

// ---------- Edytor notatki ----------

@Composable
internal fun NoteEditorScreen(
    repo: NoteRepository,
    noteId: Long,
    initialTitle: String,
    initialText: String,
    onIdKnown: (Long) -> Unit,
    onClose: () -> Unit,
) {
    // Bez klucza: gdy nowa notatka dostanie id (noteId 0 → id), edytor zostaje ten sam (kursor, klawiatura).
    val startId = remember { noteId }
    val draft = remember { NoteDraft(repo, startId.takeIf { it > 0 }) }
    var title by rememberSaveable { mutableStateOf(if (startId > 0) "" else initialTitle) }
    var text by rememberSaveable { mutableStateOf(if (startId > 0) "" else initialText) }
    // Istniejąca notatka: dopóki się nie wczyta, nie zapisujemy (inaczej puste pola nadpisałyby treść).
    var loaded by rememberSaveable { mutableStateOf(startId <= 0) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = rememberNoteExporter(repo)

    LaunchedEffect(Unit) {
        if (startId > 0) {
            val note = repo.get(startId)
            if (note == null) {
                draft.detach() // notatki już nie ma — wpisany tekst zapisze się jako nowa
            } else {
                // Punkt odniesienia "zapisane" zawsze z bazy — także po odtworzeniu ekranu, gdy w polach
                // może być tekst, który nie zdążył się zapisać (wtedy zapis go dogoni).
                draft.markLoaded(note.title, note.text)
                if (!loaded) {
                    title = note.title
                    text = note.text
                }
            }
        }
        loaded = true
    }

    // Zapis sam: 0,7 s po ostatniej zmianie (każda zmiana anuluje poprzednie odliczanie — jak debounce w Rx).
    LaunchedEffect(title, text, loaded) {
        if (loaded) {
            delay(700)
            draft.save(title, text)
            draft.id?.let(onIdKnown)
        }
    }

    // Wyjście: zapis "w tle" (backgroundScope), bo ekran znika od razu, a zapis ma się dokończyć.
    val close = {
        if (loaded) {
            val t = title
            val x = text
            NoteRepository.backgroundScope.launch { draft.finish(t, x) }
        }
        onClose()
    }
    BackHandler { close() }

    // Eksport najpierw zapisuje (plik ma mieć to, co widać na ekranie), potem otwiera okno "Zapisz jako".
    val exportAs = { fmt: NoteFormat ->
        val t = title
        val x = text
        scope.launch {
            draft.save(t, x)
            val id = draft.id
            if (id != null) {
                onIdKnown(id)
                exporter.export(listOf(id), fmt)
            }
        }
        Unit
    }

    // Archiwizacja zamyka edytor (jak w Keep); notatka czeka w zakładce Archiwum.
    val archiveAndClose = {
        val t = title
        val x = text
        val app = context.applicationContext
        NoteRepository.backgroundScope.launch {
            draft.finish(t, x)
            draft.id?.let { repo.archive(it) }
        }
        Toast.makeText(app, OnHandText.get(R.string.oh_archived_n, 1), Toast.LENGTH_SHORT).show()
        onClose()
    }

    // Czas ostatniej zmiany pod treścią (tylko dla zapisanej notatki).
    val savedId = draft.id ?: startId
    val note by remember(savedId) { repo.observeNote(savedId) }.collectAsState(initial = null)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { close() }) { Text("‹  " + stringResource(R.string.oh_notes)) }
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text("⋮", style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Pusta notatka: nie ma czego udostępniać ani eksportować.
                    val hasContent = loaded && (title.isNotBlank() || text.isNotBlank())
                    val isArchived = note?.archivedAt != null
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.oh_share)) },
                        enabled = hasContent,
                        onClick = {
                            menuOpen = false
                            NoteExport.shareText(context, title, text)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.oh_export_txt)) },
                        enabled = hasContent,
                        onClick = {
                            menuOpen = false
                            exportAs(NoteFormat.TXT)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.oh_export_md)) },
                        enabled = hasContent,
                        onClick = {
                            menuOpen = false
                            exportAs(NoteFormat.MD)
                        },
                    )
                    if (isArchived) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.oh_restore)) },
                            onClick = {
                                menuOpen = false
                                val id = draft.id
                                if (id != null) {
                                    NoteRepository.backgroundScope.launch { repo.restore(id) }
                                    Toast.makeText(context, OnHandText.get(R.string.oh_restored_n, 1), Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.oh_archive)) },
                            enabled = hasContent,
                            onClick = {
                                menuOpen = false
                                archiveAndClose()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.oh_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        },
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            EditorField(
                value = title,
                onValueChange = { title = it },
                hint = stringResource(R.string.oh_title_hint),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 3,
                readOnly = !loaded, // dopóki notatka się wczytuje — inaczej wczytanie nadpisałoby wpisany tekst
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            EditorField(
                value = text,
                onValueChange = { text = it },
                hint = stringResource(R.string.oh_text_hint),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = Int.MAX_VALUE,
                readOnly = !loaded,
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
            )
        }
        val current = note
        if (current != null) {
            Text(
                text = stringResource(R.string.oh_edited, relativeTime(current.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.oh_delete_confirm_title)) },
            text = { Text(stringResource(R.string.oh_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    NoteRepository.backgroundScope.launch { draft.delete() } // w tle: ekran zaraz znika
                    onClose()
                }) { Text(stringResource(R.string.oh_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.oh_cancel)) }
            },
        )
    }
}

// Pole tekstowe bez ramki (jak w Keep): sam tekst z podpowiedzią, gdy pusto.
@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    style: androidx.compose.ui.text.TextStyle,
    maxLines: Int,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        maxLines = maxLines,
        readOnly = readOnly,
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        },
    )
}
