package pl.rafal.onhand

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pl.rafal.onhand.data.AttachmentEntity
import pl.rafal.onhand.data.NoteEntity
import pl.rafal.onhand.data.NoteRepository
import pl.rafal.onhand.data.OnHandDatabase
import java.io.File

// Notatki OnHand w kopii zapasowej launchera (Backup.kt). Moduł sam zna swoją bazę, więc launcher dostaje
// gotowy JSON i listę plików do .zip, a przy imporcie mapę "stare id notatki → nowe" (do odnośników na kartach).
object OnHandBackup {
    // Folder, do którego Backup.kt rozpakowuje załączniki z .zip (files/onhand/… → files/onhand_import/…).
    const val STAGING = "onhand_import"
    private const val ATTACH_ROOT = "onhand/"

    // {"notes":[{id,title,text,createdAt,updatedAt,archivedAt,attachments:[{path,name,mimeType,createdAt}]}]}
    // path jest względna do filesDir (np. "onhand/12/zdjecie.jpg") — tak jak w bazie.
    suspend fun export(context: Context): JSONObject = withContext(Dispatchers.IO) {
        val db = OnHandDatabase.get(context)
        val byNote = db.attachmentDao().getAll().groupBy { it.noteId }
        val notes = db.noteDao().getAll().sortedBy { it.id }.map { n ->
            JSONObject()
                .put("id", n.id).put("title", n.title).put("text", n.text)
                .put("createdAt", n.createdAt).put("updatedAt", n.updatedAt).putOpt("archivedAt", n.archivedAt)
                .put(
                    "attachments",
                    JSONArray(
                        byNote[n.id].orEmpty().map { a ->
                            JSONObject().put("path", a.path).put("name", a.name)
                                .putOpt("mimeType", a.mimeType).put("createdAt", a.createdAt)
                        },
                    ),
                )
        }
        JSONObject().put("notes", JSONArray(notes))
    }

    // Pliki załączników do spakowania (istniejące, tylko z folderu onhand/).
    suspend fun attachmentFiles(context: Context): List<File> = withContext(Dispatchers.IO) {
        OnHandDatabase.get(context).attachmentDao().getAll()
            .map { File(context.filesDir, it.path) }
            .filter { it.isFile }
    }

    // Import ZASTĘPUJE notatki OnHand. Załącznik bierzemy z rozpakowanej kopii (STAGING) albo — przy kopii
    // bez plików (sam JSON) na tym samym telefonie — z jego dotychczasowego miejsca. Kolejność: odczyt całości
    // (bez zmian na dysku), transakcja w bazie, dopiero potem pliki — błąd po drodze nie psuje obecnych notatek.
    suspend fun import(context: Context, json: JSONObject): Map<Long, Long> = withContext(Dispatchers.IO) {
        val root = context.filesDir.canonicalFile
        val staging = File(root, STAGING)
        val onhandDir = File(root, "onhand").canonicalPath + File.separator
        val stagingDir = staging.canonicalPath + File.separator

        // 1. Odczyt całości (błąd w JSON-ie = wyjątek, zanim cokolwiek skasujemy).
        // Załącznik: plik z .zip (STAGING) albo zapasowo jego obecne miejsce na tym telefonie (kopia bez plików).
        class Src(val att: AttachmentEntity, val staged: File, val current: File?)
        class In(val note: NoteEntity, val oldId: Long, val files: List<Src>)
        val array = json.optJSONArray("notes") ?: JSONArray()
        val incoming = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val oldId = o.getLong("id")
            val atts = o.optJSONArray("attachments") ?: JSONArray()
            val files = (0 until atts.length()).mapNotNull { j ->
                val a = atts.getJSONObject(j)
                val path = a.getString("path")
                if (!path.startsWith(ATTACH_ROOT)) return@mapNotNull null
                // Ochrona przed "../" w podrobionej kopii — oba miejsca muszą leżeć w swoich folderach.
                val staged = File(staging, path.removePrefix(ATTACH_ROOT)).canonicalFile
                if (!staged.path.startsWith(stagingDir)) return@mapNotNull null
                val current = File(root, path).canonicalFile.takeIf { it.path.startsWith(onhandDir) && it.isFile }
                // Tylko odczyt — niczego jeszcze nie przenosimy (błąd dalej w pliku nie może zepsuć obecnych notatek).
                if (!staged.isFile && current == null) return@mapNotNull null
                Src(
                    AttachmentEntity(
                        noteId = 0,
                        path = "",
                        name = a.getString("name"),
                        mimeType = if (a.has("mimeType") && !a.isNull("mimeType")) a.getString("mimeType") else null,
                        createdAt = a.optLong("createdAt", System.currentTimeMillis()),
                    ),
                    staged,
                    current,
                )
            }
            In(
                note = NoteEntity(
                    title = o.optString("title"),
                    text = o.optString("text"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    archivedAt = if (o.has("archivedAt") && !o.isNull("archivedAt")) o.getLong("archivedAt") else null,
                ),
                oldId = oldId,
                files = files,
            )
        }

        // 2. Baza od nowa (transakcja: błąd w połowie = stare notatki zostają).
        val db = OnHandDatabase.get(context)
        val ids = mutableMapOf<Long, Long>()
        val placed = mutableListOf<Pair<Src, File>>() // źródło → miejsce docelowe
        db.withTransaction {
            db.noteDao().deleteAll()
            for (item in incoming) {
                val newId = db.noteDao().insert(item.note)
                ids[item.oldId] = newId
                val used = HashSet<String>()
                for (src in item.files) {
                    val att = src.att
                    // Nazwa unikalna w folderze nowej notatki (jak przy dodawaniu).
                    var name = NoteRepository.sanitizeFileName(att.name)
                    val base = name.substringBeforeLast('.', name)
                    val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
                    var n = 2
                    while (!used.add(name.lowercase())) {
                        name = "$base ($n)$ext"
                        n++
                    }
                    val path = "onhand/$newId/$name"
                    db.attachmentDao().insert(att.copy(noteId = newId, path = path))
                    placed += src to File(root, path)
                }
            }
        }

        // 3. Pliki. Najpierw te, których nie było w .zip, z obecnego miejsca do STAGING (dopiero teraz, po udanym
        //    zapisie w bazie), potem stary folder onhand/ znika, a wszystko trafia na nowe miejsca.
        for ((src, _) in placed) {
            val current = src.current
            if (!src.staged.isFile && current != null && current.isFile) {
                src.staged.parentFile?.mkdirs()
                if (!current.renameTo(src.staged)) runCatching { current.copyTo(src.staged, overwrite = true) }
            }
        }
        val target = File(root, "onhand")
        target.listFiles()?.forEach { it.deleteRecursively() }
        for ((src, to) in placed) {
            to.parentFile?.mkdirs()
            if (!src.staged.renameTo(to)) {
                runCatching { src.staged.copyTo(to, overwrite = true) }
            }
        }
        target.mkdirs()
        staging.deleteRecursively()
        ids
    }

    // Sprzątanie STAGING (np. gdy import launchera się nie udał, a .zip zdążył się rozpakować).
    fun clearStaging(context: Context) {
        File(context.filesDir, STAGING).deleteRecursively()
    }
}
