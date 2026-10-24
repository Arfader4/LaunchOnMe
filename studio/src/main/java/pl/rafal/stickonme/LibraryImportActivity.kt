package pl.rafal.stickonme

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

// Jednorazowe przeniesienie biblioteki z LaunchOnMe. Do wersji 1.8 StickOnMe było częścią launchera (wspólna
// instalacja), więc naklejki i tablice leżą w pamięci launchera. Launcher pakuje je do .zip i wysyła tu
// (intent.data = content://, ACTION_IMPORT) przez startActivityForResult; my rozpakowujemy do własnego filesDir
// i poprawiamy ścieżki w tablicach. RESULT_OK = launcher może usunąć swoją kopię.
class LibraryImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StudioText.init(this)
        val source = intent.data
        // Przyjmujemy tylko od launchera (callingPackage znamy tylko przy startActivityForResult — i tak go wymagamy).
        if (savedInstanceState != null || intent.action != StickOnMe.ACTION_IMPORT || source == null || callingPackage != LAUNCHER) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) { runCatching { importZip(applicationContext, source) }.getOrNull() }
            if (counts == null) {
                Toast.makeText(this@LibraryImportActivity, getString(R.string.som_import_failed), Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_CANCELED)
            } else {
                Toast.makeText(this@LibraryImportActivity, getString(R.string.som_import_done, counts.first, counts.second), Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_OK)
            }
            finish()
        }
    }

    companion object {
        private const val LAUNCHER = "pl.rafal.contextlauncher"

        // Zwraca (naklejki, tablice). Wpisy spoza library/ i boards/ pomijamy; istniejących plików nie nadpisujemy
        // (nazwy to UUID — ponowne przeniesienie niczego nie zdubluje ani nie zepsuje).
        fun importZip(context: Context, source: Uri): Pair<Int, Int> {
            val root = File(context.filesDir, "stickers").apply { mkdirs() }
            val rootPath = root.canonicalPath + File.separator
            var stickers = 0
            val boards = mutableListOf<File>()
            val input = context.contentResolver.openInputStream(source) ?: error("brak pliku")
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val allowed = !entry.isDirectory && !name.contains("..") && (name.startsWith("library/") || name.startsWith("boards/"))
                    if (allowed) {
                        val target = File(root, name)
                        // Ochrona przed „zip slip” (ścieżka wychodząca poza folder) — jak Path.GetFullPath + StartsWith w C#.
                        if (target.canonicalPath.startsWith(rootPath) && !target.exists()) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            if (name.startsWith("library/")) stickers++
                            if (name.startsWith("boards/") && target.extension == "json" && target.parentFile?.name == "boards") boards += target
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            // Ścieżki w tablicach wskazywały na pamięć launchera → te same pliki u nas (część po „/stickers/”).
            fun local(path: String): String =
                if ("/stickers/" in path) File(root, path.substringAfter("/stickers/")).absolutePath else path
            boards.forEach { file ->
                runCatching {
                    val board = Board.of(file.readText()) ?: return@runCatching
                    val fixed = board.copy(
                        bgImage = board.bgImage?.let(::local),
                        layers = board.layers.map { l -> l.copy(path = l.path?.let(::local), original = l.original?.let(::local)) },
                    )
                    if (fixed != board) file.writeText(fixed.toJson())
                }
            }
            return stickers to boards.size
        }
    }
}
