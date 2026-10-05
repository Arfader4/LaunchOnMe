package pl.rafal.onhand

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import pl.rafal.onhand.data.NoteEntity
import pl.rafal.onhand.data.NoteRepository

// ---------- Lista notatek ----------

@Composable
internal fun NoteListScreen(
    repo: NoteRepository,
    pickMode: Boolean,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Zapytanie jako Flow: lista przelicza się sama przy zmianie bazy i przy pisaniu w polu wyszukiwania
    // (jak IObservable połączony z drugim w Rx.NET — combine).
    val queryFlow = remember { MutableStateFlow("") }
    LaunchedEffect(query) { queryFlow.value = query }
    val notes by remember(repo) { repo.search(archived = false, query = queryFlow) }.collectAsState(initial = null)

    // Wstecz przy wpisanym wyszukiwaniu najpierw czyści pole.
    BackHandler(enabled = query.isNotEmpty()) { query = "" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!pickMode) {
                ExtendedFloatingActionButton(onClick = onNew) {
                    Text("+  " + stringResource(R.string.oh_new_note))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(if (pickMode) R.string.oh_pick_title else R.string.onhand_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp),
            )
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
            val list = notes
            if (list == null) {
                Spacer(Modifier.weight(1f)) // jeszcze się wczytuje — pusto, bez migania komunikatu
            } else if (list.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(if (query.isBlank()) R.string.oh_empty else R.string.oh_no_results),
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
                        NoteRow(note = note, onClick = { onOpen(note.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: NoteEntity, onClick: () -> Unit) {
    val preview = notePreview(note)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = note.displayTitle.ifBlank { stringResource(R.string.oh_untitled) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
