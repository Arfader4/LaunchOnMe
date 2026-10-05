package pl.rafal.onhand

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.rafal.onhand.data.NoteEntity
import pl.rafal.onhand.data.NoteRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Format pliku notatki: rozszerzenie i typ MIME (dla okna zapisu i Udostępnij).
internal enum class NoteFormat(val extension: String, val mimeType: String) {
    TXT("txt", "text/plain"),
    MD("md", "text/markdown"),
}

// Notatki "na zewnątrz": zamiana na tekst / pliki, Udostępnij (ACTION_SEND / SEND_MULTIPLE) i eksport do pliku.
// Zwykły obiekt bez stanu — jak statyczna klasa pomocnicza w C#.
internal object NoteExport {
    private const val SHARE_DIR = "onhand_share" // musi się zgadzać z res/xml/onhand_paths.xml

    // Treść notatki jako tekst. .txt: tytuł, pusta linia, treść. .md: tytuł jako nagłówek "# …".
    fun format(title: String, text: String, fmt: NoteFormat): String {
        val t = title.trim()
        val body = text.trimEnd()
        return when {
            t.isEmpty() -> body
            fmt == NoteFormat.MD -> "# $t\n\n$body"
            else -> "$t\n\n$body"
        }.trimEnd() + "\n"
    }

    fun format(note: NoteEntity, fmt: NoteFormat): String = format(note.title, note.text, fmt)

    // Nazwa pliku z tytułu: bez znaków, których nie lubią systemy plików (Windows: \ / : * ? " < > |).
    fun fileBaseName(note: NoteEntity): String {
        val clean = note.displayTitle
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
            .take(60)
            .trim()
        return clean.ifEmpty { OnHandText.get(R.string.oh_untitled) + " " + note.id }
    }

    fun fileName(note: NoteEntity, fmt: NoteFormat): String = fileBaseName(note) + "." + fmt.extension

    // Nazwa paczki .zip, np. "OnHand 2026-10-05.zip".
    fun zipName(): String = "OnHand " + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".zip"

    // Nazwy w paczce muszą być różne: dwie notatki "Zakupy" → "Zakupy.txt" i "Zakupy (2).txt".
    private fun uniqueNames(notes: List<NoteEntity>, fmt: NoteFormat): List<String> {
        val used = HashSet<String>()
        return notes.map { note ->
            val base = fileBaseName(note)
            var name = "$base.${fmt.extension}"
            var n = 2
            while (!used.add(name.lowercase())) {
                name = "$base ($n).${fmt.extension}"
                n++
            }
            name
        }
    }

    // Sekcja "Załączniki" na końcu pliku w paczce .zip: .txt — lista nazw, .md — odnośniki (obrazy jako ![…]),
    // ścieżki względne do folderu notatki obok pliku. <…> wokół ścieżki pozwala na spacje (CommonMark).
    private fun attachmentSection(names: List<String>, mimes: List<String?>, folder: String, fmt: NoteFormat): String {
        if (names.isEmpty()) return ""
        val header = OnHandText.get(R.string.oh_attachments)
        return if (fmt == NoteFormat.MD) {
            "\n## $header\n\n" + names.mapIndexed { i, n ->
                val bang = if (mimes[i]?.startsWith("image/") == true) "!" else ""
                val label = n.replace("[", "\\[").replace("]", "\\]") // nawiasy w nazwie psułyby odnośnik
                "- $bang[$label](<$folder/$n>)"
            }.joinToString("\n") + "\n"
        } else {
            "\n$header:\n" + names.joinToString("\n") { "- $folder/$it" } + "\n"
        }
    }

    // ---------- Udostępnij ----------

    // Plik do wysłania (kopia notatki albo załącznik) z typem MIME.
    class SharedFile(val file: File, val mimeType: String?)

    suspend fun attachmentFiles(repo: NoteRepository, notes: List<NoteEntity>): List<SharedFile> =
        withContext(Dispatchers.IO) {
            notes.flatMap { note ->
                repo.attachmentsFor(note.id).map { SharedFile(repo.file(it), it.mimeType) }
            }.filter { it.file.exists() }
        }

    // Jako tekst (+ załączniki, jeśli są). To przyjmują Keep, Samsung Notes, komunikatory.
    fun shareText(context: Context, title: String, text: String, files: List<SharedFile> = emptyList()) {
        send(context, format(title, text, NoteFormat.TXT).trimEnd(), title.trim().takeIf { it.isNotEmpty() }, files)
    }

    // Jedna notatka albo kilka sklejonych w jedną wiadomość, z załącznikami wszystkich.
    suspend fun shareText(context: Context, repo: NoteRepository, notes: List<NoteEntity>) {
        if (notes.isEmpty()) return
        val files = attachmentFiles(repo, notes)
        if (notes.size == 1) {
            shareText(context, notes[0].title, notes[0].text, files)
        } else {
            val text = notes.joinToString("\n\n— — —\n\n") { format(it, NoteFormat.TXT).trimEnd() }
            send(context, text, null, files)
        }
    }

    // Jako pliki (.txt) + załączniki: kopie notatek w cache/onhand_share/, wszystko przez FileProvider (z prawem odczytu).
    // Poprzednie kopie kasujemy dopiero przy kolejnym udostępnianiu — odbiorca może je czytać jeszcze chwilę po wysłaniu.
    suspend fun shareFiles(context: Context, repo: NoteRepository, notes: List<NoteEntity>, fmt: NoteFormat) {
        if (notes.isEmpty()) return
        val noteFiles = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, SHARE_DIR)
            dir.deleteRecursively()
            val names = uniqueNames(notes, fmt)
            notes.mapIndexed { i, note ->
                // Każdy plik we własnym podfolderze: nazwa pliku (= tytuł) zostaje czytelna dla odbiorcy.
                val file = File(File(dir, i.toString()).apply { mkdirs() }, names[i])
                file.writeText(format(note, fmt))
                SharedFile(file, fmt.mimeType)
            }
        }
        send(context, null, null, noteFiles + attachmentFiles(repo, notes))
    }

    // Otwarcie załącznika w innej aplikacji (zdjęcie w galerii, PDF w czytniku…).
    fun open(context: Context, file: File, mimeType: String?) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, file), mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(view) }
            .onFailure { Toast.makeText(context, OnHandText.get(R.string.oh_open_failed), Toast.LENGTH_SHORT).show() }
    }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.onhand", file)

    // Wspólny typ kilku plików: ten sam → on, same obrazy → image/*, inaczej */* (jak najwęższy wspólny typ bazowy).
    private fun commonMime(mimes: List<String?>): String {
        val list = mimes.map { it ?: "application/octet-stream" }.distinct()
        val groups = list.map { it.substringBefore('/') }.distinct()
        return when {
            list.size == 1 -> list[0]
            groups.size == 1 -> groups[0] + "/*"
            else -> "*/*"
        }
    }

    // ACTION_SEND (tekst / jeden plik) albo ACTION_SEND_MULTIPLE (kilka plików), z tekstem w EXTRA_TEXT.
    private fun send(context: Context, text: String?, subject: String?, files: List<SharedFile>) {
        val intent: Intent
        if (files.isEmpty()) {
            intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        } else {
            val uris = files.map { uriFor(context, it.file) }
            intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            intent.setType(commonMime(files.map { it.mimeType })).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // ClipData z wszystkimi linkami: dzięki niej prawo odczytu przechodzi przez okno wyboru aplikacji.
            val clip = ClipData.newRawUri(null, uris[0])
            for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
            intent.clipData = clip
        }
        if (text != null) intent.putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        startChooser(context, intent)
    }

    private fun startChooser(context: Context, send: Intent) {
        val chooser = Intent.createChooser(send, OnHandText.get(R.string.oh_share))
        runCatching { context.startActivity(chooser) }
            .onFailure { Toast.makeText(context, OnHandText.get(R.string.oh_share_failed), Toast.LENGTH_SHORT).show() }
    }

    // ---------- Eksport do pliku (systemowe okno zapisu) ----------

    // Czy eksport musi być paczką .zip: kilka notatek albo notatka z załącznikami.
    suspend fun needsZip(repo: NoteRepository, notes: List<NoteEntity>): Boolean =
        notes.size > 1 || notes.any { repo.attachmentCount(it.id) > 0 }

    // Nazwa paczki: jedna notatka → jej tytuł, kilka → "OnHand 2026-10-05.zip".
    fun zipName(notes: List<NoteEntity>): String = if (notes.size == 1) fileBaseName(notes[0]) + ".zip" else zipName()

    // Plik .txt/.md albo .zip: każda notatka jako plik, jej załączniki w folderze o tej samej nazwie
    // ("Zakupy.md" + "Zakupy/paragon.jpg"). Zwraca false przy błędzie zapisu.
    suspend fun write(context: Context, repo: NoteRepository, uri: Uri, notes: List<NoteEntity>, fmt: NoteFormat, zip: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream = context.contentResolver.openOutputStream(uri) ?: error("Brak strumienia dla $uri")
                stream.use { out ->
                    if (zip) {
                        // ZipOutputStream ≈ System.IO.Compression.ZipArchive w .NET.
                        ZipOutputStream(out.buffered()).use { z ->
                            val names = uniqueNames(notes, fmt)
                            notes.forEachIndexed { i, note ->
                                val folder = names[i].substringBeforeLast('.')
                                // distinctBy: dwa wiersze z tą samą nazwą dałyby w zipie "duplicate entry".
                                val items = repo.attachmentsFor(note.id).filter { repo.file(it).exists() }.distinctBy { it.name.lowercase() }
                                val body = format(note, fmt) +
                                    attachmentSection(items.map { it.name }, items.map { it.mimeType }, folder, fmt)
                                val entry = ZipEntry(names[i])
                                entry.time = note.updatedAt
                                z.putNextEntry(entry)
                                z.write(body.toByteArray(Charsets.UTF_8))
                                z.closeEntry()
                                for (item in items) {
                                    val file = repo.file(item)
                                    val fileEntry = ZipEntry("$folder/${item.name}")
                                    fileEntry.time = file.lastModified()
                                    z.putNextEntry(fileEntry)
                                    file.inputStream().use { it.copyTo(z) }
                                    z.closeEntry()
                                }
                            }
                        }
                    } else {
                        out.write(format(notes.first(), fmt).toByteArray(Charsets.UTF_8))
                    }
                }
            }.isSuccess
        }
}

// Eksport z ekranu: najpierw systemowe okno "Zapisz jako" (CreateDocument, jak SaveFileDialog), potem zapis w tle.
// Trzy "wyrzutnie", bo typ pliku (MIME) jest częścią kontraktu i nie da się go zmienić przy uruchomieniu.
internal class NoteExporter(private val start: (List<Long>, NoteFormat) -> Unit) {
    fun export(ids: List<Long>, fmt: NoteFormat) = start(ids, fmt)
}

@Composable
internal fun rememberNoteExporter(repo: NoteRepository): NoteExporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Co eksportujemy, gdy okno zapisu jest otwarte (rememberSaveable — przeżyje obrót ekranu w tym czasie).
    var pendingIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var pendingFormat by rememberSaveable { mutableStateOf(NoteFormat.TXT) }
    var pendingZip by rememberSaveable { mutableStateOf(false) }

    val onResult: (Uri?) -> Unit = { uri ->
        val ids = pendingIds.toList()
        val fmt = pendingFormat
        val zip = pendingZip
        pendingIds = longArrayOf()
        if (uri != null && ids.isNotEmpty()) {
            val app = context.applicationContext
            // W tle (backgroundScope): zapis ma się dokończyć, nawet gdy ekran zaraz zniknie.
            NoteRepository.backgroundScope.launch {
                val notes = repo.getMany(ids)
                val ok = notes.isNotEmpty() && NoteExport.write(app, repo, uri, notes, fmt, zip)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, OnHandText.get(if (ok) R.string.oh_export_done else R.string.oh_export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val txtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(NoteFormat.TXT.mimeType), onResult)
    val mdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(NoteFormat.MD.mimeType), onResult)
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), onResult)

    return remember {
        NoteExporter { ids, fmt ->
            scope.launch {
                val notes = repo.getMany(ids)
                if (notes.isNotEmpty()) {
                    val zip = NoteExport.needsZip(repo, notes)
                    pendingIds = notes.map { it.id }.toLongArray()
                    pendingFormat = fmt
                    pendingZip = zip
                    val result = runCatching {
                        if (zip) {
                            zipLauncher.launch(NoteExport.zipName(notes))
                        } else if (fmt == NoteFormat.MD) {
                            mdLauncher.launch(NoteExport.fileName(notes[0], fmt))
                        } else {
                            txtLauncher.launch(NoteExport.fileName(notes[0], fmt))
                        }
                    }
                    if (result.isFailure) {
                        pendingIds = longArrayOf()
                        Toast.makeText(context, OnHandText.get(R.string.oh_export_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
