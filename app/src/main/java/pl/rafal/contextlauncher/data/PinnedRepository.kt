package pl.rafal.contextlauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Patterns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import pl.rafal.contextlauncher.data.db.LauncherDatabase
import pl.rafal.contextlauncher.data.db.PinnedItemEntity
import java.io.File
import java.util.UUID

// "Pod ręką": przypinanie plików, linków i notatek do trybu.
// Pliki kopiujemy do prywatnej pamięci aplikacji, bo dostęp do cudzego pliku z "Udostępnij"
// jest tylko chwilowy. Kopia działa po restarcie telefonu i po usunięciu oryginału.
class PinnedRepository(private val context: Context) {

    private val dao = LauncherDatabase.get(context).pinnedItemDao()

    // get() = wyliczane przy każdym odczycie; mkdirs() tworzy folder, jeśli go nie ma.
    private val folder: File get() = File(context.filesDir, "pinned").apply { mkdirs() }

    fun observe(modeId: Long): Flow<List<PinnedItemEntity>> = dao.observeForMode(modeId)

    // Dispatchers.IO = pula wątków do operacji na plikach i sieci.
    suspend fun pinFile(modeId: Long, source: Uri, title: String? = null): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = displayName(source) ?: "plik"
        val safeName = name.replace(Regex("""[^\w.\- ]"""), "_") // tylko bezpieczne znaki w nazwie pliku
        val target = File(folder, "${UUID.randomUUID()}_$safeName")

        // use { } ≈ using (...) { } w C#: zamyka strumień nawet przy wyjątku.
        val input = resolver.openInputStream(source) ?: error("Nie udało się odczytać pliku")
        input.use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }

        dao.insert(
            PinnedItemEntity(
                modeId = modeId,
                kind = PinnedItemEntity.KIND_FILE,
                title = title?.takeIf { it.isNotBlank() } ?: name,
                uri = target.absolutePath,
                mimeType = resolver.getType(source),
            ),
        )
    }

    suspend fun pinLink(modeId: Long, url: String, title: String = ""): Long {
        val normalized = if (url.contains("://")) url.trim() else "https://${url.trim()}"
        return dao.insert(
            PinnedItemEntity(
                modeId = modeId,
                kind = PinnedItemEntity.KIND_LINK,
                title = title.ifBlank { Uri.parse(normalized).host ?: normalized },
                uri = normalized,
            ),
        )
    }

    suspend fun pinNote(modeId: Long, title: String, text: String): Long =
        dao.insert(
            PinnedItemEntity(
                modeId = modeId,
                kind = PinnedItemEntity.KIND_NOTE,
                title = title.ifBlank { text.lineSequence().first().take(40) },
                text = text,
            ),
        )

    suspend fun updateNote(item: PinnedItemEntity, title: String, text: String) =
        dao.updateNote(item.id, title.ifBlank { text.lineSequence().first().take(40) }, text)

    suspend fun archive(item: PinnedItemEntity) = dao.setArchived(item.id, System.currentTimeMillis())

    suspend fun restore(item: PinnedItemEntity) = dao.setArchived(item.id, null)

    suspend fun delete(item: PinnedItemEntity) = withContext(Dispatchers.IO) {
        if (item.kind == PinnedItemEntity.KIND_FILE) item.uri?.let { File(it).delete() }
        dao.delete(item.id)
    }

    // Intencja otwierająca element w odpowiedniej aplikacji (PDF w czytniku, link w przeglądarce).
    fun openIntent(item: PinnedItemEntity): Intent? = when (item.kind) {
        PinnedItemEntity.KIND_FILE -> {
            val file = File(item.uri ?: return null)
            // FileProvider zamienia prywatną ścieżkę na adres content://, który inna aplikacja może chwilowo przeczytać.
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, item.mimeType ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        PinnedItemEntity.KIND_LINK -> Intent(Intent.ACTION_VIEW, Uri.parse(item.uri))
        else -> null
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    companion object {
        // Czy tekst to sam adres WWW (wtedy przypinamy jako link, a nie notatkę)?
        fun isUrl(text: String): Boolean {
            val trimmed = text.trim()
            return !trimmed.contains(' ') && Patterns.WEB_URL.matcher(trimmed).matches()
        }
    }
}
