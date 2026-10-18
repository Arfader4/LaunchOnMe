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

    // Najnowsze na początku (jak "ostatnie" w galerii). Data pliku czytana raz na plik, a nie w każdym porównaniu sortowania.
    fun list(context: Context): List<File> =
        folder(context).listFiles { f -> f.extension == "png" }
            ?.map { it to it.lastModified() }
            ?.sortedByDescending { it.second }
            ?.map { it.first }
            .orEmpty()

    suspend fun save(context: Context, sticker: Bitmap): File = withContext(Dispatchers.IO) {
        val file = File(folder(context), "${UUID.randomUUID()}.png")
        file.outputStream().use { sticker.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file
    }

    // --- Miniatury (1.3.0) ---
    // Dwa poziomy pamięci podręcznej, jak w przeglądarce: w pamięci (LruCache, limit w bajtach) i na dysku
    // (małe PNG w cacheDir — system może je usunąć, wtedy po prostu powstaną od nowa). Klucz zawiera datę zmiany pliku,
    // więc poprawiona naklejka / zapisana tablica dostaje nową miniaturę sama.
    const val THUMB_SIDE = 320

    private val thumbMemory = object : android.util.LruCache<String, ImageBitmap>(
        minOf(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    // Najwyżej 3 dekodowania miniatur naraz (zamiast dziesiątek równoległych na Dispatchers.IO → skoki pamięci).
    // Jak SemaphoreSlim(3) w C#. Użycie: withContext(StickerLibrary.thumbDispatcher) { thumbnail(...) }.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val thumbDispatcher = Dispatchers.IO.limitedParallelism(3)

    private fun thumbKey(file: File, modified: Long, maxSide: Int) = "${file.absolutePath}|$modified|$maxSide"

    // Miniatura z pamięci bez czekania (np. żeby kafelek od razu coś pokazał przy powrocie na ekran). null = trzeba wczytać.
    fun cachedThumbnail(file: File, maxSide: Int = THUMB_SIDE): ImageBitmap? =
        thumbMemory.get(thumbKey(file, file.lastModified(), maxSide))

    // Miniatura do siatki (biblioteka, tablice, okno wyboru naklejki). Blokuje — wołać w tle (thumbDispatcher).
    // cache = false: jednorazowy obraz (np. animacja "odklejenia"), bez zapisu na dysk.
    fun thumbnail(context: Context, file: File, maxSide: Int = THUMB_SIDE, cache: Boolean = true): ImageBitmap? = runCatching {
        if (!cache) return@runCatching decodeScaled(file, maxSide, software = false)?.asImageBitmap()
        val modified = file.lastModified()
        if (modified == 0L) return@runCatching null // pliku nie ma
        val key = thumbKey(file, modified, maxSide)
        thumbMemory.get(key)?.let { return@runCatching it }
        val dir = thumbFolder(context)
        val disk = File(dir, "${file.absolutePath.hashCode().toUInt()}_${modified}_$maxSide.png")
        // Kopia z dysku (mały plik — szybko; bitmapa sprzętowa, bez kopiowania do GPU). Uszkodzona (np. przerwany zapis)
        // → usuwamy i robimy od nowa, żeby kafelek nie został pusty na zawsze.
        val fromDisk = if (disk.exists()) {
            runCatching { decodeScaled(disk, maxSide, software = false) }.getOrNull().also { if (it == null) disk.delete() }
        } else {
            null
        }
        val image = fromDisk ?: decodeScaled(file, maxSide, software = true)?.also { small ->
            // Pierwszy raz: dekodowanie od razu w małym rozmiarze, zapis miniatury na dysk.
            // Mały plik (np. miniatura tablicy 360 px) nie potrzebuje kopii na dysku.
            if (file.length() > 64 * 1024) {
                runCatching {
                    dir.mkdirs()
                    dropThumbs(context, file) // stare miniatury tego pliku (sprzed zmiany) do kosza
                    // Zapis do pliku tymczasowego i zmiana nazwy — nikt nie przeczyta pliku w połowie zapisu.
                    val tmp = File(dir, disk.name + ".tmp")
                    tmp.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (!tmp.renameTo(disk)) tmp.delete()
                }
            }
        }
        image?.asImageBitmap()?.also { thumbMemory.put(key, it) }
    }.getOrNull()

    private fun thumbFolder(context: Context): File = File(context.cacheDir, "stickonme_thumbs")

    // Usunięcie miniatur danego pliku z dysku (także po usunięciu tablicy — BoardStore.delete).
    fun dropThumbs(context: Context, file: File) {
        val prefix = "${file.absolutePath.hashCode().toUInt()}_"
        thumbFolder(context).listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    private fun decodeScaled(file: File, maxSide: Int, software: Boolean): Bitmap? =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val scale = minOf(1f, maxSide.toFloat() / maxOf(info.size.width, info.size.height).coerceAtLeast(1))
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // potrzebne do compress()
        }

    // Mało pamięci (onTrimMemory) albo wyjście ze studia: miniatury w pamięci do wyrzucenia (na dysku zostają).
    fun trimMemory() {
        thumbMemory.evictAll()
    }

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

    fun delete(context: Context, file: File) {
        runCatching {
            // Miniatury usuwanej naklejki też (na dysku; w pamięci wypadną same, klucz z datą już nie pasuje).
            dropThumbs(context, file)
            file.delete()
        }
    }

    // Udostępnienie naklejki innej aplikacji (WhatsApp, Messenger…) przez FileProvider — link z czasowym prawem odczytu.
    fun share(context: Context, file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.stickonme", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.som_lib_share_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
