package pl.rafal.onhand.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File

// Dostęp do notatek (jak repozytorium nad DbContext w .NET). Używa go aktywność OnHand, a w paczce On4 także launcher.
class NoteRepository(private val context: Context) {

    private val db = OnHandDatabase.get(context)
    private val notes = db.noteDao()

    fun observe(archived: Boolean): Flow<List<NoteEntity>> = notes.observe(archived)

    // Wyszukiwanie po tytule i treści. Filtrujemy w Kotlinie, nie przez LIKE: SQLite porównuje bez wielkości liter
    // tylko litery ASCII, a my chcemy też "Ą" = "ą". Notatek jest najwyżej kilkaset, więc to tanie.
    fun search(archived: Boolean, query: Flow<String>): Flow<List<NoteEntity>> =
        combine(notes.observe(archived), query) { list, q -> list.filter { matches(it, q) } }

    fun observeNote(id: Long): Flow<NoteEntity?> = notes.observeById(id)

    // Tytuły notatek po id — dla launchera (odnośniki na karcie trybu, paczka On4).
    fun observeTitles(): Flow<Map<Long, String>> =
        combine(notes.observe(false), notes.observe(true)) { a, b -> (a + b).associate { it.id to it.displayTitle } }

    suspend fun get(id: Long): NoteEntity? = notes.get(id)

    suspend fun create(title: String, text: String): Long = notes.insert(NoteEntity(title = title, text = text))

    suspend fun update(id: Long, title: String, text: String) = notes.update(id, title, text, System.currentTimeMillis())

    suspend fun archive(id: Long) = notes.setArchived(id, System.currentTimeMillis())

    suspend fun restore(id: Long) = notes.setArchived(id, null)

    // Usunięcie notatki razem z plikami załączników (wiersze usuwa kaskada w bazie).
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        notes.delete(id)
        attachmentFolder(context, id).deleteRecursively()
    }

    companion object {
        // Zakres do zapisów "w tle", które mają się dokończyć po zamknięciu ekranu (zapis notatki przy wyjściu).
        // SupervisorJob: błąd jednego zapisu nie anuluje kolejnych.
        val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun attachmentFolder(context: Context, noteId: Long): File = File(context.filesDir, "onhand/$noteId")

        fun matches(note: NoteEntity, query: String): Boolean {
            val q = query.trim()
            return q.isEmpty() || note.title.contains(q, ignoreCase = true) || note.text.contains(q, ignoreCase = true)
        }
    }
}
