package pl.rafal.contextlauncher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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

    // Kopia wybranego obrazka; poprzednia tapeta tego trybu jest usuwana z dysku.
    suspend fun set(modeId: Long?, source: Uri) = withContext(Dispatchers.IO) {
        val target = File(folder(), "${UUID.randomUUID()}.jpg")
        app.contentResolver.openInputStream(source)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error("Nie udało się odczytać obrazka")
        pathFor(modeId)?.let { File(it).delete() }
        prefs.edit().putString(key(modeId), target.absolutePath).remove(KEY_APPLIED).apply()
    }

    fun clear(modeId: Long?) {
        pathFor(modeId)?.let { File(it).delete() }
        prefs.edit().remove(key(modeId)).remove("lock_${key(modeId)}").remove(KEY_APPLIED).apply()
    }

    fun hasAny(): Boolean = prefs.all.keys.any { it == "default" || it.startsWith("mode_") }

    // Ustawia tapetę trybu (albo domyślną). Nic nie robi, gdy ta sama już jest ustawiona — to trwa ok. pół sekundy.
    suspend fun applyFor(modeId: Long) = withContext(Dispatchers.IO) {
        val owner: Long? = if (pathFor(modeId) != null) modeId else null
        val path = pathFor(owner) ?: return@withContext
        val lock = lockScreenFor(owner)
        val signature = "$path|$lock"
        if (prefs.getString(KEY_APPLIED, null) == signature) return@withContext

        runCatching {
            val manager = WallpaperManager.getInstance(app)
            val bitmap = decodeScaled(path, maxOf(manager.desiredMinimumWidth, manager.desiredMinimumHeight, 1440)) ?: return@runCatching
            // FLAG_SYSTEM = ekran główny, FLAG_LOCK = ekran blokady. "or" to bitowe | z C#.
            val which = WallpaperManager.FLAG_SYSTEM or (if (lock) WallpaperManager.FLAG_LOCK else 0)
            manager.setBitmap(bitmap, null, true, which)
            prefs.edit().putString(KEY_APPLIED, signature).apply()
        }
    }

    // Zdjęcie z aparatu ma 4000+ px — zmniejszamy do rozmiaru ekranu, żeby nie zapchać pamięci.
    private fun decodeScaled(path: String, maxSide: Int) = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private companion object {
        const val KEY_APPLIED = "applied"
    }
}
