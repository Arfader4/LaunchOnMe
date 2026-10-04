package pl.rafal.contextlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// Naklejki: kopie obrazków w prywatnej pamięci launchera (jak pliki OnHand — oryginał może zniknąć).
object StickerStore {

    private fun folder(context: Context) = File(context.filesDir, "stickers").apply { mkdirs() }

    // Kopiuje obrazek i zwraca ścieżkę kopii. Rozszerzenie zachowujemy, bo PNG i WebP mają przezroczystość.
    suspend fun import(context: Context, source: Uri): String = withContext(Dispatchers.IO) {
        // file:// przyjmujemy tylko z biblioteki StickOnMe (nie np. z "Udostępnij" innej aplikacji —
        // inaczej dałoby się podrzucić ścieżkę do prywatnych plików launchera).
        if (source.scheme == "file") {
            val library = File(context.filesDir, "stickers/library").canonicalPath
            val path = File(source.path.orEmpty()).canonicalPath
            require(path.startsWith("$library/")) { AppText.get(R.string.data_file_not_allowed) }
        }
        // Plik z biblioteki StickOnMe (file://) nie ma typu MIME — bierzemy go z rozszerzenia.
        val mime = if (source.scheme == "file") "image/" + source.lastPathSegment.orEmpty().substringAfterLast('.', "png")
            else context.contentResolver.getType(source).orEmpty()
        val extension = when {
            "png" in mime -> "png"
            "webp" in mime -> "webp"
            "gif" in mime -> "gif"
            else -> "jpg"
        }
        val target = File(folder(context), "${UUID.randomUUID()}.$extension")
        val input = context.contentResolver.openInputStream(source) ?: error(AppText.get(R.string.data_image_read_failed))
        input.use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
        target.absolutePath
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    // Wczytanie z pomniejszeniem: telefon robi zdjęcia 4000 px, a naklejka zajmuje najwyżej pół ekranu.
    suspend fun load(path: String, maxSide: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        // Z uwzględnieniem orientacji z EXIF (zdjęcie w pionie zostaje w pionie).
        ImageFiles.decode(path, maxSide)
    }
}
