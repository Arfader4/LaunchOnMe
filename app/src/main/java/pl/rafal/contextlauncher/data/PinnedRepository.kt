package pl.rafal.contextlauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Patterns
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.db.LauncherDatabase
import pl.rafal.contextlauncher.data.db.PinnedItemEntity
import pl.rafal.onhand.OnHand
import java.io.File
import java.util.UUID

// OnHand na karcie trybu: przypinanie plików, linków i notatek (notatki mieszkają w aplikacji OnHand,
// tu jest tylko odnośnik; usunięcie odnośnika nie usuwa notatki).
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
        val input = resolver.openInputStream(source) ?: error(AppText.get(R.string.data_file_read_failed))
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

    // Nowa notatka (z okna notatki albo z "Udostępnij → Przypnij do trybu") powstaje w OnHand,
    // a na karcie trybu ląduje do niej odnośnik.
    suspend fun pinNote(modeId: Long, title: String, text: String): Long {
        val noteId = OnHand.repository(context).create(title.trim(), text)
        val shown = title.ifBlank { text.lineSequence().first().take(40) }
        return pinOnHand(modeId, noteId, shown)
    }

    // Odnośnik do notatki OnHand. Ta sama notatka w tym samym trybie tylko raz (z archiwum wraca na kartę).
    // Sprawdzenie i wstawienie w jednej transakcji (jak w SQL Server: SELECT + INSERT w BEGIN TRAN).
    suspend fun pinOnHand(modeId: Long, noteId: Long, title: String): Long {
        val key = noteId.toString()
        return LauncherDatabase.get(context).withTransaction {
            if (dao.countOnHand(key, modeId) > 0) {
                dao.restoreOnHand(key, modeId)
                -1L
            } else {
                dao.insert(
                    PinnedItemEntity(
                        modeId = modeId,
                        kind = PinnedItemEntity.KIND_ONHAND,
                        title = title,
                        uri = key,
                    ),
                )
            }
        }
    }

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
        // Notatka otwiera się w OnHand (we własnym zadaniu, z ikoną OnHand w "ostatnich aplikacjach").
        PinnedItemEntity.KIND_ONHAND -> item.uri?.toLongOrNull()?.let { OnHand.openIntent(context, it) }
        else -> null
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    companion object {
        // Znacznik w mimeType odnośnika ONHAND, którego notatki nie ma już w OnHand (ustawia go LauncherViewModel).
        const val ONHAND_MISSING = "x-onhand/missing"

        // Czy tekst to sam adres WWW (wtedy przypinamy jako link, a nie notatkę)?
        fun isUrl(text: String): Boolean {
            val trimmed = text.trim()
            return !trimmed.contains(' ') && Patterns.WEB_URL.matcher(trimmed).matches()
        }
    }
}
