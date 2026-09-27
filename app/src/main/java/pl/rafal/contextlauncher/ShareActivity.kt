package pl.rafal.contextlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                        // lifecycleScope: korutyna żyje tak długo jak ta aktywność.
                        lifecycleScope.launch {
                            val result = runCatching {
                                when {
                                    asSticker && content is SharedContent.File -> addSticker(mode.id, content.uri)
                                    content is SharedContent.File -> repository.pinFile(mode.id, content.uri, title)
                                    content is SharedContent.Link -> repository.pinLink(mode.id, content.url, title)
                                    content is SharedContent.Note -> repository.pinNote(mode.id, title, content.text)
                                    else -> Unit
                                }
                            }
                            val message = when {
                                result.isFailure -> "Nie udało się przypiąć"
                                asSticker -> "Dodano naklejkę do trybu ${mode.name}"
                                else -> "Przypięto do trybu ${mode.name}"
                            }
                            Toast.makeText(this@ShareActivity, message, Toast.LENGTH_SHORT).show()
                            if (result.isSuccess) finish()
                        }
                    },
                    onCancel = ::finish,
                )
            }
        }
    }

    // Naklejka z innej aplikacji: kopia obrazka + widżet w pierwszym wolnym miejscu karty.
    private suspend fun addSticker(modeId: Long, uri: Uri) {
        val path = StickerStore.import(applicationContext, uri)
        val dao = LauncherDatabase.get(this).cardItemDao()
        val config = JSONObject().put("file", path).toString()
        if (!placeCustomWidget(dao, modeId, CustomWidgetKind.STICKER, config)) {
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
