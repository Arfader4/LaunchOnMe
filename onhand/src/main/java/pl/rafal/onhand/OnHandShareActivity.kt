package pl.rafal.onhand

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.rafal.onhand.data.NoteRepository

// To, co przyszło z "Udostępnij": temat (np. tytuł strony), tekst/link i pliki (content://).
internal data class Incoming(val subject: String?, val text: String?, val uris: List<Uri>) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && uris.isEmpty()

    companion object {
        fun from(intent: Intent): Incoming {
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() }
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            // IntentCompat — ta sama metoda na starych i nowych Androidach (od 13 zmieniła się sygnatura).
            val uris = ArrayList<Uri>()
            if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.addAll(it) }
            } else {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.add(it) }
            }
            // Część aplikacji podaje pliki tylko w ClipData.
            if (uris.isEmpty()) {
                val clip = intent.clipData
                if (clip != null) {
                    for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                }
            }
            return Incoming(subject, text, uris.distinct())
        }
    }
}

// Cel "Udostępnij → OnHand": małe okno nad aplikacją, z której udostępniamy. Nowa notatka albo dopisanie do istniejącej.
class OnHandShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OnHandText.init(this)
        enableEdgeToEdge()
        val incoming = Incoming.from(intent)
        if (incoming.isEmpty) {
            Toast.makeText(this, getString(R.string.oh_share_nothing), Toast.LENGTH_SHORT).show()
            finish()
        } else {
            val repo = OnHand.repository(this)
            setContent {
                MaterialTheme(colorScheme = OnHandColors) {
                    ShareSheet(
                        repo = repo,
                        incoming = incoming,
                        onDone = { noteId, open ->
                            if (open) {
                                // NEW_TASK: notatka otwiera się w zadaniu OnHand, nie w aplikacji, z której udostępniono.
                                // setIdentifier: każda notatka to "inna" intencja — inaczej Android mógłby tylko wysunąć stare zadanie.
                                val open = OnHand.openIntent(this, noteId).setIdentifier("note:$noteId")
                                runCatching { startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                            finish()
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareSheet(
    repo: NoteRepository,
    incoming: Incoming,
    onDone: (Long, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(incoming.subject.orEmpty()) }
    var openAfter by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // Zapis: notatka (nowa albo istniejąca), potem kopie plików. NonCancellable — żeby wyjście w trakcie
    // nie zostawiło notatki bez części załączników.
    val save = { targetId: Long? ->
        saving = true
        scope.launch {
            val result = withContext(NonCancellable) { saveIncoming(repo, incoming, title, targetId) }
            val (id, failed) = result
            val message = if (targetId == null) {
                context.getString(R.string.oh_share_saved)
            } else {
                val name = repo.get(id)?.displayTitle.orEmpty().ifBlank { context.getString(R.string.oh_untitled) }
                context.getString(R.string.oh_share_appended, name)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (failed > 0) {
                Toast.makeText(context, context.getString(R.string.oh_attach_failed, failed), Toast.LENGTH_LONG).show()
            }
            onDone(id, openAfter)
        }
        Unit
    }

    // W trakcie zapisu Wstecz nic nie robi: zamknięcie okna odebrałoby prawo odczytu plików w połowie kopiowania.
    BackHandler(enabled = saving) {}

    // "Dopisz do…": wybór notatki na liście OnHand (tryb wyboru), wynik wraca tutaj.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = if (result.resultCode == Activity.RESULT_OK) OnHand.resultNoteId(result.data) else null
        if (id != null) save(id)
    }

    // Tło (przygaszone przez motyw okna): dotknięcie poza kartą zamyka, jak przy oknie dialogowym.
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !saving,
                onClick = onCancel,
            )
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                // Dotknięcie karty nie zamyka okna (pusta obsługa przechwytuje kliknięcie).
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.oh_share_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.oh_share_title_hint)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                val text = incoming.text
                if (text != null) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    )
                }
                if (incoming.uris.isNotEmpty()) {
                    Text(
                        text = "📎  " + stringResource(R.string.oh_attachments_n, incoming.uris.size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clickable(enabled = !saving) { openAfter = !openAfter },
                ) {
                    Checkbox(checked = openAfter, onCheckedChange = { openAfter = it }, enabled = !saving)
                    Text(stringResource(R.string.oh_share_open_after), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                if (saving) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { runCatching { pick.launch(OnHand.pickIntent(context)) } },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.oh_share_append)) }
                        Button(
                            onClick = { save(null) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.oh_new_note)) }
                    }
                }
            }
        }
    }
}

// Zapis tego, co przyszło: nowa notatka (tytuł z pola, treść = tekst) albo dopisanie do istniejącej
// (tytuł z pola trafia wtedy jako pierwsza linia dopisku, chyba że już jest w tekście). Zwraca (id, ile plików się nie udało).
private suspend fun saveIncoming(repo: NoteRepository, incoming: Incoming, title: String, targetId: Long?): Pair<Long, Int> {
    val text = incoming.text.orEmpty()
    val id = if (targetId == null) {
        repo.create(title.trim(), text)
    } else {
        val head = title.trim().takeIf { it.isNotEmpty() && !text.contains(it) }
        repo.appendText(targetId, listOfNotNull(head, text.takeIf { it.isNotBlank() }).joinToString("\n"))
        targetId
    }
    var failed = 0
    for (uri in incoming.uris) {
        if (repo.addAttachment(id, uri) == null) failed++
    }
    return id to failed
}
