package pl.rafal.onhand

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import pl.rafal.onhand.data.NoteRepository

// Kolory OnHand: ciemne tło jak w LaunchOnMe i StickOnMe, akcent z logo (bursztyn → koral).
internal val OnHandColors = darkColorScheme(
    primary = Color(0xFFFFB020),
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF4A3510),
    onPrimaryContainer = Color(0xFFFFDCA0),
    secondary = Color(0xFFFF7A45),
    background = Color(0xFF101214),
    surface = Color(0xFF1B1E22),
    surfaceVariant = Color(0xFF262A30),
    surfaceContainerHigh = Color(0xFF262A30),
    onSurface = Color(0xFFECEEF0),
    onSurfaceVariant = Color(0xFFA9B0B8),
    outline = Color(0xFF3A4048),
)

// Stałe dla stanu "który ekran": brak edytora, nowa notatka (id jeszcze nie ma) albo konkretna notatka (id > 0).
private const val NO_EDITOR = -1L
private const val NEW_NOTE = 0L

// OnHand — notatki. Z szuflady: lista + edytor. Z launchera: od razu notatka (EXTRA_NOTE_ID),
// nowa notatka z treścią (EXTRA_NEW_TEXT) albo wybór notatki (EXTRA_PICK, wynik wraca do launchera).
class OnHandActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OnHandText.init(this)
        enableEdgeToEdge()
        val pickMode = intent.getBooleanExtra(OnHand.EXTRA_PICK, false)
        val directId = intent.getLongExtra(OnHand.EXTRA_NOTE_ID, -1L).takeIf { it > 0 }
        val newText = intent.getStringExtra(OnHand.EXTRA_NEW_TEXT)
        val newTitle = intent.getStringExtra(OnHand.EXTRA_NEW_TITLE)
        val repo = OnHand.repository(this)
        setContent {
            MaterialTheme(colorScheme = OnHandColors) {
                OnHandApp(
                    repo = repo,
                    pickMode = pickMode,
                    directId = directId,
                    newText = newText,
                    newTitle = newTitle,
                    onPicked = { id ->
                        setResult(Activity.RESULT_OK, Intent().putExtra(OnHand.EXTRA_NOTE_ID, id))
                        finish()
                    },
                    onExit = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun OnHandApp(
    repo: NoteRepository,
    pickMode: Boolean,
    directId: Long?,
    newText: String?,
    newTitle: String?,
    onPicked: (Long) -> Unit,
    onExit: () -> Unit,
) {
    // Otwarte "z zewnątrz" (konkretna notatka albo nowa z treścią): zamknięcie edytora zamyka OnHand
    // i wraca tam, skąd przyszliśmy (launcher, inna aplikacja).
    val direct = directId != null || newText != null
    // rememberSaveable: przeżywa obrót ekranu i zabicie procesu w tle (jak ViewState).
    // Po utworzeniu nowej notatki trzymamy jej id — odtworzony ekran otworzy ją zamiast tworzyć drugą.
    var editing by rememberSaveable {
        mutableLongStateOf(directId ?: if (newText != null) NEW_NOTE else NO_EDITOR)
    }
    // Zakładka listy (Notatki / Archiwum) — tutaj, bo lista znika z ekranu na czas edytora.
    var archiveTab by rememberSaveable { mutableStateOf(false) }
    if (editing == NO_EDITOR) {
        NoteListScreen(
            repo = repo,
            pickMode = pickMode,
            archiveTab = archiveTab,
            onArchiveTab = { archiveTab = it },
            onOpen = { id -> if (pickMode) onPicked(id) else { editing = id } },
            onNew = { editing = NEW_NOTE },
        )
    } else {
        NoteEditorScreen(
            repo = repo,
            noteId = editing,
            initialTitle = newTitle.orEmpty(),
            initialText = newText.orEmpty(),
            onIdKnown = { id -> editing = id },
            onClose = { if (direct) onExit() else { editing = NO_EDITOR } },
        )
    }
}
