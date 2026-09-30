package pl.rafal.stickonme

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// Biblioteka gotowych naklejek StickOnMe: pliki PNG w prywatnej pamięci aplikacji.
// Launcher i studio to jedna instalacja, więc launcher czyta ten sam folder (bez udostępniania plików między aplikacjami).
object StickerLibrary {
    private fun folder(context: Context) = File(context.filesDir, "stickers/library").apply { mkdirs() }

    // Najnowsze na początku (jak "ostatnie" w galerii).
    fun list(context: Context): List<File> =
        folder(context).listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() }.orEmpty()

    suspend fun save(context: Context, sticker: Bitmap): File = withContext(Dispatchers.IO) {
        val file = File(folder(context), "${UUID.randomUUID()}.png")
        file.outputStream().use { sticker.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file
    }

    // Miniatura do siatki biblioteki.
    fun thumbnail(file: File, maxSide: Int = 320): ImageBitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val scale = minOf(1f, maxSide.toFloat() / maxOf(info.size.width, info.size.height).coerceAtLeast(1))
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }.asImageBitmap()
    }.getOrNull()

    // Gotowa naklejka (plik PNG) do ponownej edycji.
    suspend fun decodeFile(path: String, maxSide: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                val scale = minOf(1f, maxSide.toFloat() / maxOf(info.size.width, info.size.height).coerceAtLeast(1))
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
            }.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()
    }

    fun delete(file: File) {
        runCatching { file.delete() }
    }

    // Udostępnienie naklejki innej aplikacji (WhatsApp, Messenger…) przez FileProvider — link z czasowym prawem odczytu.
    fun share(context: Context, file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.stickonme", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Udostępnij naklejkę").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // Czy obraz ma już przezroczystość (gotowa naklejka, np. z czatu)? Wtedy nie wycinamy automatycznie.
    fun hasTransparency(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val w = bitmap.width
        val row = IntArray(w)
        var y = 0
        while (y < bitmap.height) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                if ((row[x] ushr 24) < 250) return true
                x += 3
            }
            y += 3
        }
        return false
    }

    // Zdjęcie z galerii, obrócone zgodnie z EXIF (ImageDecoder robi to sam) i pomniejszone do maxSide.
    suspend fun decode(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, maxSide.toFloat() / maxOf(w, h).coerceAtLeast(1))
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // piksele czytamy i edytujemy maskę
                // Zdjęcia HDR / szeroka gama → zwykłe sRGB (ML Kit i zapis PNG oczekują ARGB_8888).
                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                decoder.isMutableRequired = false
            }
        }.getOrNull()
    }
}

// Jak launcher prosi studio o naklejkę: "wybierz albo zrób nową i oddaj ścieżkę" (jak okno wyboru pliku).
object StickOnMe {
    const val EXTRA_PICK = "pl.rafal.stickonme.PICK"
    const val EXTRA_PATH = "pl.rafal.stickonme.PATH"

    const val EXTRA_EDIT = "pl.rafal.stickonme.EDIT"
    const val EXTRA_PHOTO = "pl.rafal.stickonme.PHOTO"   // zdjęcie do wycięcia (np. z "Udostępnij")
    const val EXTRA_SHAPE = "pl.rafal.stickonme.SHAPE"   // początkowy kształt (StickerShapes.id)
    const val EXTRA_FRAME = "pl.rafal.stickonme.FRAME"   // początkowa ramka (Layer.FRAME_*)
    const val EXTRA_WALLPAPER = "pl.rafal.stickonme.WALLPAPER" // wybór tablicy na tapetę trybu

    fun pickIntent(context: Context): Intent = Intent(context, StudioActivity::class.java).putExtra(EXTRA_PICK, true)

    // Poprawka naklejki, która już leży na karcie (plik launchera): edytor startuje z nią, wynik wraca jak przy wyborze.
    // shape / frame: dawne ustawienia z karty launchera — edytor startuje z nimi, żeby naklejka wyglądała tak samo.
    fun editIntent(context: Context, path: String, shape: String? = null, frame: String? = null): Intent =
        pickIntent(context).putExtra(EXTRA_EDIT, path).putExtra(EXTRA_SHAPE, shape).putExtra(EXTRA_FRAME, frame)

    // Tapeta trybu z tablicy: studio otwiera "Tablice", a wynik (EXTRA_PATH) to gotowy obraz PNG tablicy.
    fun wallpaperIntent(context: Context): Intent = pickIntent(context).putExtra(EXTRA_WALLPAPER, true)

    // Obrazek z zewnątrz (plik w pamięci launchera): wycinanie od zera, albo poprawka, jeśli już jest przezroczysty.
    fun cutoutIntent(context: Context, path: String): Intent = pickIntent(context).putExtra(EXTRA_PHOTO, path)

    fun resultPath(data: Intent?): String? = data?.getStringExtra(EXTRA_PATH)

    fun library(context: Context): List<File> = StickerLibrary.list(context)
}
