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

    // ---------- Udostępnij ----------

    // Jako tekst: jedna notatka albo kilka sklejonych w jedną wiadomość. To przyjmują Keep, Samsung Notes, komunikatory.
    fun shareText(context: Context, title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, format(title, text, NoteFormat.TXT).trimEnd())
        if (title.isNotBlank()) send.putExtra(Intent.EXTRA_SUBJECT, title.trim())
        startChooser(context, send)
    }

    fun shareText(context: Context, notes: List<NoteEntity>) {
        if (notes.size == 1) {
            shareText(context, notes[0].title, notes[0].text)
        } else if (notes.isNotEmpty()) {
            val text = notes.joinToString("\n\n— — —\n\n") { format(it, NoteFormat.TXT).trimEnd() }
            startChooser(context, Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text))
        }
    }

    // Jako pliki (.txt): kopie w cache/onhand_share/ i linki content:// przez FileProvider (z prawem odczytu).
    // Poprzednie kopie kasujemy dopiero przy kolejnym udostępnianiu — odbiorca może je czytać jeszcze chwilę po wysłaniu.
    suspend fun shareFiles(context: Context, notes: List<NoteEntity>, fmt: NoteFormat) {
        if (notes.isEmpty()) return
        val uris = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, SHARE_DIR)
            dir.deleteRecursively()
            val names = uniqueNames(notes, fmt)
            notes.mapIndexed { i, note ->
                // Każdy plik we własnym podfolderze: nazwa pliku (= tytuł) zostaje czytelna dla odbiorcy.
                val file = File(File(dir, i.toString()).apply { mkdirs() }, names[i])
                file.writeText(format(note, fmt))
                FileProvider.getUriForFile(context, "${context.packageName}.onhand", file)
            }
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.setType(fmt.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData z wszystkimi linkami: dzięki niej prawo odczytu przechodzi przez okno wyboru aplikacji.
        val clip = ClipData.newRawUri(null, uris[0])
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        send.clipData = clip
        startChooser(context, send)
    }

    private fun startChooser(context: Context, send: Intent) {
        val chooser = Intent.createChooser(send, OnHandText.get(R.string.oh_share))
        runCatching { context.startActivity(chooser) }
            .onFailure { Toast.makeText(context, OnHandText.get(R.string.oh_share_failed), Toast.LENGTH_SHORT).show() }
    }

    // ---------- Eksport do pliku (systemowe okno zapisu) ----------

    // Jedna notatka → plik .txt/.md, kilka → .zip z plikami. Zwraca false przy błędzie zapisu.
    suspend fun write(context: Context, uri: Uri, notes: List<NoteEntity>, fmt: NoteFormat, zip: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream = context.contentResolver.openOutputStream(uri) ?: error("Brak strumienia dla $uri")
                stream.use { out ->
                    if (zip) {
                        // ZipOutputStream ≈ System.IO.Compression.ZipArchive w .NET.
                        ZipOutputStream(out.buffered()).use { z ->
                            val names = uniqueNames(notes, fmt)
                            notes.forEachIndexed { i, note ->
                                val entry = ZipEntry(names[i])
                                entry.time = note.updatedAt
                                z.putNextEntry(entry)
                                z.write(format(note, fmt).toByteArray(Charsets.UTF_8))
                                z.closeEntry()
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

    val onResult: (Uri?) -> Unit = { uri ->
        val ids = pendingIds.toList()
        val fmt = pendingFormat
        pendingIds = longArrayOf()
        if (uri != null && ids.isNotEmpty()) {
            val app = context.applicationContext
            // W tle (backgroundScope): zapis ma się dokończyć, nawet gdy ekran zaraz zniknie.
            NoteRepository.backgroundScope.launch {
                val notes = repo.getMany(ids)
                val ok = notes.isNotEmpty() && NoteExport.write(app, uri, notes, fmt, zip = ids.size > 1)
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
                    pendingIds = notes.map { it.id }.toLongArray()
                    pendingFormat = fmt
                    val result = runCatching {
                        if (notes.size > 1) {
                            zipLauncher.launch(NoteExport.zipName())
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
