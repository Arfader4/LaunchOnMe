package pl.rafal.onhand.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

// Dostęp do notatek (jak repozytorium nad DbContext w .NET). Używa go aktywność OnHand, a w paczce On4 także launcher.
class NoteRepository(private val context: Context) {

    private val db = OnHandDatabase.get(context)
    private val notes = db.noteDao()
    private val attachments = db.attachmentDao()

    fun observe(archived: Boolean): Flow<List<NoteEntity>> = notes.observe(archived)

    // Wyszukiwanie po tytule i treści. Filtrujemy w Kotlinie, nie przez LIKE: SQLite porównuje bez wielkości liter
    // tylko litery ASCII, a my chcemy też "Ą" = "ą". Notatek jest najwyżej kilkaset, więc to tanie.
    fun search(archived: Boolean, query: Flow<String>): Flow<List<NoteEntity>> =
        combine(notes.observe(archived), query) { list, q -> list.filter { matches(it, q) } }

    fun observeNote(id: Long): Flow<NoteEntity?> = notes.observeById(id)

    // Wszystkie notatki po id — dla launchera (żywy tytuł i podgląd odnośnika na karcie trybu).
    fun observeAllById(): Flow<Map<Long, NoteEntity>> = notes.observeAll().map { list -> list.associateBy { it.id } }

    suspend fun get(id: Long): NoteEntity? = notes.get(id)

    // Notatki o podanych id, najnowsze pierwsze (kolejność jak na liście — tak trafiają do eksportu i Udostępnij).
    // Po 500 naraz: starszy SQLite (Android 10–11) przyjmuje najwyżej 999 parametrów w jednym zapytaniu.
    suspend fun getMany(ids: Collection<Long>): List<NoteEntity> =
        ids.toList().chunked(500).flatMap { notes.getMany(it) }.sortedByDescending { it.updatedAt }

    suspend fun create(title: String, text: String, createdAt: Long = System.currentTimeMillis()): Long =
        notes.insert(NoteEntity(title = title, text = text, createdAt = createdAt))

    // Notatka o identycznym tytule i treści (migracja starych notatek launchera bez duplikatów).
    suspend fun findIdentical(title: String, text: String): Long? =
        notes.getAll().firstOrNull { it.title == title && it.text == text }?.id

    suspend fun update(id: Long, title: String, text: String) = notes.update(id, title, text, System.currentTimeMillis())

    suspend fun archive(id: Long) = notes.setArchived(id, System.currentTimeMillis())

    suspend fun restore(id: Long) = notes.setArchived(id, null)

    suspend fun archiveMany(ids: Collection<Long>) {
        val time = System.currentTimeMillis()
        for (part in ids.toList().chunked(500)) notes.setArchivedMany(part, time)
    }

    suspend fun restoreMany(ids: Collection<Long>) {
        for (part in ids.toList().chunked(500)) notes.setArchivedMany(part, null)
    }

    // Dopisanie tekstu na końcu notatki (pusta linia odstępu), np. z "Udostępnij → Dopisz do…".
    suspend fun appendText(id: Long, added: String) {
        if (added.isNotBlank()) notes.appendText(id, added.trim(), "\n\n", System.currentTimeMillis())
    }

    // ---------- Załączniki ----------

    fun observeAttachments(noteId: Long): Flow<List<AttachmentEntity>> = attachments.observeFor(noteId)

    suspend fun attachmentsFor(noteId: Long): List<AttachmentEntity> = attachments.getFor(noteId)

    fun observeAttachmentCounts(): Flow<Map<Long, Int>> =
        attachments.observeCounts().map { list -> list.associate { it.noteId to it.count } }

    suspend fun attachmentCount(noteId: Long): Int = attachments.countFor(noteId)

    // Plik załącznika na dysku (path w bazie jest względna do filesDir).
    fun file(item: AttachmentEntity): File = File(context.filesDir, item.path)

    // Kopia pliku z innej aplikacji (content://) do folderu notatki. Kopiujemy od razu: prawo odczytu
    // do cudzego pliku z "Udostępnij" wygasa po zamknięciu okna. null = nie udało się odczytać.
    suspend fun addAttachment(noteId: Long, uri: Uri): AttachmentEntity? = withContext(Dispatchers.IO) {
        runCatching {
            // Tylko content:// z innych aplikacji — file:// albo nasz własny dostawca mógłby "przemycić"
            // prywatne pliki aplikacji (np. bazę) do załącznika.
            require(uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority?.startsWith(context.packageName) != true)
            val resolver = context.contentResolver
            val rawName = sanitizeFileName(displayName(uri) ?: uri.lastPathSegment ?: "plik")
            val mime = resolver.getType(uri)?.takeIf { it != "application/octet-stream" } ?: guessMime(rawName)
            // Nazwa bez rozszerzenia (bywa przy zdjęciach z przeglądarki) → dopisujemy je z typu MIME.
            val ext = if (rawName.contains('.')) null else mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            val name = if (ext != null) "$rawName.$ext" else rawName
            val folder = attachmentFolder(context, noteId).apply { mkdirs() }
            val target = uniqueFile(folder, name)
            // Błąd w połowie (kopiowanie, zapis w bazie) → usuwamy plik, żeby nie został "sierotą" bez wiersza.
            try {
                val input = resolver.openInputStream(uri) ?: error("Brak strumienia dla $uri")
                input.use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
                val item = AttachmentEntity(
                    noteId = noteId,
                    path = target.relativeTo(context.filesDir).path,
                    name = target.name,
                    mimeType = mime,
                )
                val saved = item.copy(id = attachments.insert(item))
                notes.touch(noteId, System.currentTimeMillis())
                saved
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        }.getOrNull()
    }

    suspend fun deleteAttachment(item: AttachmentEntity) = withContext(Dispatchers.IO) {
        attachments.delete(item.id)
        file(item).delete()
        notes.touch(item.noteId, System.currentTimeMillis())
    }

    // Nazwa pliku od aplikacji, która go udostępnia (np. "IMG_2041.jpg") — kolumna DISPLAY_NAME.
    private fun displayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        }.getOrNull()

    // Każda osobno, bo razem z wierszem znika folder załączników tej notatki.
    suspend fun deleteMany(ids: Collection<Long>) {
        for (id in ids) deleteOne(id)
        notifyDeleted(ids)
    }

    // Usunięcie notatki razem z plikami załączników (wiersze usuwa kaskada w bazie).
    suspend fun delete(id: Long) {
        deleteOne(id)
        notifyDeleted(listOf(id))
    }

    private suspend fun deleteOne(id: Long) = withContext(Dispatchers.IO) {
        notes.delete(id)
        attachmentFolder(context, id).deleteRecursively()
    }

    // Launcher usuwa odnośniki do skasowanych notatek z kart trybów (błąd po jego stronie nie psuje usuwania).
    private suspend fun notifyDeleted(ids: Collection<Long>) {
        val host = pl.rafal.onhand.OnHand.host ?: return
        runCatching { host.notesDeleted(ids) }
    }

    companion object {
        // Zakres do zapisów "w tle", które mają się dokończyć po zamknięciu ekranu (zapis notatki przy wyjściu).
        // SupervisorJob: błąd jednego zapisu nie anuluje kolejnych.
        val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun attachmentFolder(context: Context, noteId: Long): File = File(context.filesDir, "onhand/$noteId")

        // Bez znaków, których nie lubią systemy plików i zip (\ / : * ? " < > |) i bez ukrytych plików (".x").
        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimStart('.').take(100).ifBlank { "plik" }

        fun guessMime(name: String): String? =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())

        // "zdjecie.jpg" zajęte → "zdjecie (2).jpg". createNewFile zajmuje nazwę od razu (atomowo), więc dwa
        // równoległe kopiowania nie wybiorą tej samej.
        fun uniqueFile(folder: File, name: String): File {
            var file = File(folder, name)
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
            var n = 2
            while (!file.createNewFile()) {
                file = File(folder, "$base ($n)$ext")
                n++
            }
            return file
        }

        fun matches(note: NoteEntity, query: String): Boolean {
            val q = query.trim()
            return q.isEmpty() || note.title.contains(q, ignoreCase = true) || note.text.contains(q, ignoreCase = true)
        }
    }
}
