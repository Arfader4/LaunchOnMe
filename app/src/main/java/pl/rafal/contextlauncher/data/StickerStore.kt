package pl.rafal.contextlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// Naklejki: kopie obrazków w prywatnej pamięci launchera (jak pliki "Pod ręką" — oryginał może zniknąć).
object StickerStore {

    private fun folder(context: Context) = File(context.filesDir, "stickers").apply { mkdirs() }

    // Kopiuje obrazek i zwraca ścieżkę kopii. Rozszerzenie zachowujemy, bo PNG i WebP mają przezroczystość.
    suspend fun import(context: Context, source: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(source).orEmpty()
        val extension = when {
            "png" in mime -> "png"
            "webp" in mime -> "webp"
            "gif" in mime -> "gif"
            else -> "jpg"
        }
        val target = File(folder(context), "${UUID.randomUUID()}.$extension")
        val input = context.contentResolver.openInputStream(source) ?: error("Nie udało się odczytać obrazka")
        input.use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
        target.absolutePath
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    // Wczytanie z pomniejszeniem: telefon robi zdjęcia 4000 px, a naklejka zajmuje najwyżej pół ekranu.
    suspend fun load(path: String, maxSide: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            // Najpierw tylko rozmiar (bez wczytywania pikseli), potem właściwy odczyt co n-ty piksel.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
