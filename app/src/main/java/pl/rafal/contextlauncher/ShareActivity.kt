package pl.rafal.contextlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import pl.rafal.contextlauncher.data.CustomWidgetKind
import pl.rafal.contextlauncher.data.PinnedRepository
import pl.rafal.contextlauncher.data.StickerStore
import pl.rafal.contextlauncher.data.placeCustomWidget
import pl.rafal.contextlauncher.data.db.LauncherDatabase
import pl.rafal.contextlauncher.ui.ShareScreen
import pl.rafal.contextlauncher.ui.SharedContent
import pl.rafal.contextlauncher.ui.theme.ContextLauncherTheme

// Ekran otwierany z systemowego "Udostępnij → Przypnij do trybu".
class ShareActivity : ComponentActivity() {

    // Naklejka z udostępnionego obrazka idzie przez StickOnMe (wycięcie, ramka) — tu pamiętamy, dla którego trybu.
    // Zapisywane w savedInstanceState: system może zamknąć ten proces, gdy użytkownik siedzi w edytorze.
    private var pendingModeId: Long? = null
    private var pendingModeName: String? = null
    private var pendingTemp: String? = null

    private val studio = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val modeId = pendingModeId
        val temp = pendingTemp
        val name = pendingModeName.orEmpty()
        pendingModeId = null
        pendingTemp = null
        val path = pl.rafal.stickonme.StickOnMe.resultPath(result.data)
        lifecycleScope.launch {
            temp?.let(StickerStore::delete) // kopia robocza już niepotrzebna (wynik jest w bibliotece StickOnMe)
            if (modeId != null && path != null) {
                val ok = runCatching { addSticker(modeId, Uri.fromFile(java.io.File(path))) }.isSuccess
                Toast.makeText(
                    this@ShareActivity,
                    if (ok) "Dodano naklejkę do trybu $name" else "Nie udało się dodać naklejki (brak miejsca?)",
                    Toast.LENGTH_SHORT,
                ).show()
                if (ok) finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingModeId?.let { outState.putLong("pendingModeId", it) }
        outState.putString("pendingModeName", pendingModeName)
        outState.putString("pendingTemp", pendingTemp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            pendingModeId = savedInstanceState.getLong("pendingModeId", -1L).takeIf { it >= 0 }
            pendingModeName = savedInstanceState.getString("pendingModeName")
            pendingTemp = savedInstanceState.getString("pendingTemp")
        }

        val content = readSharedContent(intent)
        if (content == null) {
            Toast.makeText(this, "Nie rozpoznano udostępnionej treści", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val repository = PinnedRepository(applicationContext)
        val modes = LauncherDatabase.get(this).modeDao().observeAll()

        enableEdgeToEdge()
        setContent {
            ContextLauncherTheme {
                ShareScreen(
                    content = content,
                    modesFlow = modes,
                    onPin = { mode, title, asSticker ->
                        if (asSticker && content is SharedContent.File) {
                            // Kopia obrazka (link z innej aplikacji może wygasnąć) → edytor StickOnMe → wynik na kartę.
                            // Znacznik "w toku" od razu (przed kopiowaniem): podwójne dotknięcie nie uruchomi edytora dwa razy.
                            if (pendingModeId == null) lifecycleScope.launch {
                                pendingModeId = mode.id
                                val temp = runCatching { StickerStore.import(applicationContext, content.uri) }.getOrNull()
                                if (temp == null) {
                                    pendingModeId = null
                                    Toast.makeText(this@ShareActivity, "Nie udało się odczytać obrazka", Toast.LENGTH_SHORT).show()
                                } else {
                                    pendingModeName = mode.name
                                    pendingTemp = temp
                                    studio.launch(pl.rafal.stickonme.StickOnMe.cutoutIntent(this@ShareActivity, temp))
                                }
                            }
                        } else lifecycleScope.launch { // lifecycleScope: korutyna żyje tak długo jak ta aktywność.
                            val result = runCatching {
                                when {
                                    content is SharedContent.File -> repository.pinFile(mode.id, content.uri, title)
                                    content is SharedContent.Link -> repository.pinLink(mode.id, content.url, title)
                                    content is SharedContent.Note -> repository.pinNote(mode.id, title, content.text)
                                    else -> Unit
                                }
                            }
                            val message = when {
                                result.isFailure -> "Nie udało się przypiąć"
                                else -> "Przypięto do trybu ${mode.name}"
                            }
                            Toast.makeText(this@ShareActivity, message, Toast.LENGTH_SHORT).show()
                            if (result.isSuccess) finish()
                        }
                        Unit
                    },
                    onCancel = ::finish,
                )
            }
        }
    }

    // Naklejka (gotowa z StickOnMe): kopia obrazka + widżet w pierwszym wolnym miejscu karty.
    private suspend fun addSticker(modeId: Long, uri: Uri) {
        val path = StickerStore.import(applicationContext, uri)
        val dao = LauncherDatabase.get(this).cardItemDao()
        val config = JSONObject().put("file", path).toString()
        val maxPages = pl.rafal.contextlauncher.data.AppPrefs.get(applicationContext).maxPages.value
        if (placeCustomWidget(dao, modeId, CustomWidgetKind.STICKER, config, page = 0, maxPages = maxPages) == null) {
            StickerStore.delete(path)
            error("Brak miejsca na karcie")
        }
    }

    // Co przyszło w intencji: plik (EXTRA_STREAM) albo tekst (EXTRA_TEXT), który może być linkiem.
    private fun readSharedContent(intent: Intent): SharedContent? {
        if (intent.action != Intent.ACTION_SEND) return null

        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (stream != null) {
            val mime = contentResolver.getType(stream) ?: intent.type
            return SharedContent.File(stream, intent.getStringExtra(Intent.EXTRA_SUBJECT), mime)
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) return null
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        return if (PinnedRepository.isUrl(text)) SharedContent.Link(text, subject) else SharedContent.Note(text, subject)
    }
}
