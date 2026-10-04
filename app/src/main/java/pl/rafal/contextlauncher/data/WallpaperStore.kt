package pl.rafal.contextlauncher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// Tapety trybów. Obrazek kopiujemy do pamięci launchera (jak naklejki), a przy włączeniu trybu
// ustawiamy go jako tapetę systemu. Kto nie ma własnej tapety, dostaje "domyślną" (jeśli ją wybrano).
class WallpaperStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("wallpapers", Context.MODE_PRIVATE)
    private fun folder() = File(app.filesDir, "wallpapers").apply { mkdirs() }

    private fun key(modeId: Long?) = if (modeId == null) "default" else "mode_$modeId"

    fun pathFor(modeId: Long?): String? = prefs.getString(key(modeId), null)?.takeIf { File(it).exists() }

    fun lockScreenFor(modeId: Long?): Boolean = prefs.getBoolean("lock_${key(modeId)}", false)

    fun setLockScreen(modeId: Long?, on: Boolean) {
        prefs.edit().putBoolean("lock_${key(modeId)}", on).remove(KEY_APPLIED).apply() // wymuś ponowne ustawienie
    }

    // Klucz "crop2_": kadry sprzed poprawki orientacji (EXIF) liczone były na nieobróconym zdjęciu — pomijamy je.
    // Kadr tapety: widoczny fragment obrazka jako ułamki (0..1) szerokości i wysokości. null = cały obraz
    // wycentrowany i przycięty do ekranu przez system (jak dawniej). Ustawiany w oknie "Dopasuj tapetę".
    fun cropFor(modeId: Long?): RectF? = prefs.getString("crop2_${key(modeId)}", null)?.let { parseCrop(it) }

    fun setCrop(modeId: Long?, crop: RectF?) {
        prefs.edit().apply {
            if (crop == null) remove("crop2_${key(modeId)}") else putString("crop2_${key(modeId)}", "${crop.left},${crop.top},${crop.right},${crop.bottom}")
        }.remove(KEY_APPLIED).apply() // wymuś ponowne ustawienie z nowym kadrem
    }

    // --- Kopia zapasowa (.zip) ---

    // Zapisana tapeta: tryb (null = domyślna), plik, ekran blokady i kadr.
    data class Saved(val modeId: Long?, val path: String, val lock: Boolean, val crop: RectF?)

    fun saved(modeIds: List<Long>): List<Saved> = (listOf<Long?>(null) + modeIds).mapNotNull { id ->
        pathFor(id)?.let { Saved(id, it, lockScreenFor(id), cropFor(id)) }
    }

    // Import kopii: wszystkie tapety zastąpione tymi z pliku (pliki już rozpakowane do folderu tapet).
    fun replaceAll(entries: List<Saved>) {
        val keep = entries.map { File(it.path).canonicalPath }.toSet()
        folder().listFiles()?.filter { it.isFile && it.canonicalPath !in keep }?.forEach { it.delete() }
        val edit = prefs.edit()
        prefs.all.keys
            .filter { it == "default" || it.startsWith("mode_") || it.startsWith("lock_") || it.startsWith("crop2_") }
            .forEach { edit.remove(it) }
        edit.remove(KEY_APPLIED)
        entries.forEach { e ->
            edit.putString(key(e.modeId), e.path).putBoolean("lock_${key(e.modeId)}", e.lock)
            e.crop?.let { c -> edit.putString("crop2_${key(e.modeId)}", "${c.left},${c.top},${c.right},${c.bottom}") }
        }
        edit.apply()
    }

    // Podgląd do okna kadrowania: obraz zmniejszony do ok. 2048 px (wystarczy na ekran, nie zapcha pamięci).
    suspend fun preview(modeId: Long?): Bitmap? = withContext(Dispatchers.IO) {
        pathFor(modeId)?.let { decodeScaled(it, 2048) }
    }

    // Kopia wybranego obrazka; poprzednia tapeta tego trybu jest usuwana z dysku.
    suspend fun set(modeId: Long?, source: Uri) = withContext(Dispatchers.IO) {
        val target = File(folder(), "${UUID.randomUUID()}.jpg")
        app.contentResolver.openInputStream(source)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error(AppText.get(R.string.data_image_read_failed))
        pathFor(modeId)?.let { File(it).delete() }
        // Nowy obraz = nowy kadr (stary dotyczył innego zdjęcia).
        prefs.edit().putString(key(modeId), target.absolutePath).remove("crop2_${key(modeId)}").remove(KEY_APPLIED).apply()
    }

    // Usunięcie tapety. Jeśli właśnie ona jest ustawiona w telefonie, przywracamy systemową tapetę domyślną —
    // inaczej obrazek zostawałby na ekranie i wyglądało to tak, jakby usuwanie nie działało.
    suspend fun clear(modeId: Long?) = withContext(Dispatchers.IO) {
        val path = pathFor(modeId)
        val lock = lockScreenFor(modeId)
        val shownNow = path != null && prefs.getString(KEY_APPLIED, null)?.startsWith("$path|") == true
        path?.let { File(it).delete() }
        prefs.edit().remove(key(modeId)).remove("lock_${key(modeId)}").remove("crop2_${key(modeId)}").apply {
            if (shownNow) remove(KEY_APPLIED) // zdjęta była właśnie ta — przy następnym trybie ustawimy od nowa
        }.apply()
        if (shownNow) {
            runCatching {
                val which = WallpaperManager.FLAG_SYSTEM or (if (lock) WallpaperManager.FLAG_LOCK else 0)
                WallpaperManager.getInstance(app).clear(which)
            }
        }
    }

    fun hasAny(): Boolean = prefs.all.keys.any { it == "default" || it.startsWith("mode_") }

    // Ustawia tapetę trybu (albo domyślną). Nic nie robi, gdy ta sama już jest ustawiona — to trwa ok. pół sekundy.
    suspend fun applyFor(modeId: Long) = withContext(Dispatchers.IO) {
        val owner: Long? = if (pathFor(modeId) != null) modeId else null
        val path = pathFor(owner) ?: return@withContext
        val lock = lockScreenFor(owner)
        val crop = cropFor(owner)
        val signature = "$path|$lock|${crop?.toShortString()}" // zmiana kadru też wymaga ponownego ustawienia
        if (prefs.getString(KEY_APPLIED, null) == signature) return@withContext

        runCatching {
            val manager = WallpaperManager.getInstance(app)
            val maxSide = maxOf(manager.desiredMinimumWidth, manager.desiredMinimumHeight, 1440)
            val bitmap = (if (crop != null) decodeCrop(path, crop, maxSide) else decodeScaled(path, maxSide)) ?: return@runCatching
            // FLAG_SYSTEM = ekran główny, FLAG_LOCK = ekran blokady. "or" to bitowe | z C#.
            val which = WallpaperManager.FLAG_SYSTEM or (if (lock) WallpaperManager.FLAG_LOCK else 0)
            manager.setBitmap(bitmap, null, true, which)
            prefs.edit().putString(KEY_APPLIED, signature).apply()
        }
    }

    // Tylko wybrany fragment: obraz wczytany z orientacją z EXIF (tak jak w oknie kadrowania) i przycięty.
    // Im mniejszy kadr, tym większa rozdzielczość odczytu (do 4096 px), żeby powiększenie nie było rozmyte.
    private fun decodeCrop(path: String, crop: RectF, maxSide: Int): Bitmap? = runCatching {
        val fraction = maxOf(crop.width(), crop.height()).coerceAtLeast(0.05f)
        val full = ImageFiles.decode(path, (maxSide / fraction).toInt().coerceIn(maxSide, maxOf(maxSide, 4096))) ?: return@runCatching null
        val region = Rect(
            (crop.left * full.width).toInt().coerceIn(0, full.width - 1),
            (crop.top * full.height).toInt().coerceIn(0, full.height - 1),
            (crop.right * full.width).toInt().coerceIn(1, full.width),
            (crop.bottom * full.height).toInt().coerceIn(1, full.height),
        )
        if (region.width() <= 0 || region.height() <= 0) return@runCatching null
        val out = Bitmap.createBitmap(full, region.left, region.top, region.width(), region.height())
        if (out !== full) full.recycle() // duży obraz nie jest już potrzebny — zwalniamy pamięć od razu
        out
    }.getOrNull()

    // Zdjęcie z aparatu ma 4000+ px — zmniejszamy do rozmiaru ekranu, żeby nie zapchać pamięci (z orientacją z EXIF).
    private fun decodeScaled(path: String, maxSide: Int) = ImageFiles.decode(path, maxSide)

    companion object {
        private const val KEY_APPLIED = "applied"

        fun parseCrop(text: String): RectF? {
            val v = text.split(',').mapNotNull { it.toFloatOrNull() }
            if (v.size != 4) return null
            val r = RectF(v[0].coerceIn(0f, 1f), v[1].coerceIn(0f, 1f), v[2].coerceIn(0f, 1f), v[3].coerceIn(0f, 1f))
            return r.takeIf { it.width() > 0.01f && it.height() > 0.01f }
        }
    }
}
