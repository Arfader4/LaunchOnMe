package pl.rafal.contextlauncher.data

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Most do StickOnMe — od 1.9.0 to OSOBNA aplikacja (pakiet pl.rafal.stickonme), więc launcher nie ma jej kodu,
// tylko woła ją jawną intencją (jak Process.Start z argumentami w .NET) i dostaje wynik jako link content://.
// Stałe muszą się zgadzać z obiektem StickOnMe w module :studio (StickerLibrary.kt).
object StickOnMeBridge {
    const val PACKAGE = "pl.rafal.stickonme"
    private const val ACTIVITY = "pl.rafal.stickonme.StudioActivity"
    private const val IMPORT_ACTIVITY = "pl.rafal.stickonme.LibraryImportActivity"
    private const val EXTRA_PICK = "pl.rafal.stickonme.PICK"
    private const val EXTRA_EDIT = "pl.rafal.stickonme.EDIT"
    private const val EXTRA_SHAPE = "pl.rafal.stickonme.SHAPE"
    private const val EXTRA_FRAME = "pl.rafal.stickonme.FRAME"
    private const val EXTRA_WALLPAPER = "pl.rafal.stickonme.WALLPAPER"
    private const val ACTION_IMPORT = "pl.rafal.stickonme.action.IMPORT_LIBRARY"

    // Czy StickOnMe jest zainstalowane (widoczność pakietu: <queries> z LAUNCHER w manifeście launchera wystarcza).
    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    // Sklep / strona StickOnMe (na razie aplikacja instalowana z Android Studio — link do sklepu na przyszłość).
    fun storeIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))

    private fun base(): Intent = Intent().setComponent(ComponentName(PACKAGE, ACTIVITY)).putExtra(EXTRA_PICK, true)

    // Wybór gotowej naklejki albo zrobienie nowej — wynik: resultUri.
    fun pickIntent(): Intent = base()

    // Tapeta trybu z tablicy — wynik: obraz JPEG (resultUri).
    fun wallpaperIntent(): Intent = base().putExtra(EXTRA_WALLPAPER, true)

    // Poprawka naklejki z karty: plik launchera udostępniony linkiem (tylko do odczytu, na czas edycji).
    fun editIntent(context: Context, path: String, shape: String?, frame: String?): Intent =
        withFile(context, base().putExtra(EXTRA_EDIT, true).putExtra(EXTRA_SHAPE, shape).putExtra(EXTRA_FRAME, frame), File(path))

    // Zdjęcie do wycięcia (np. z „Udostępnij”) — kopia robocza w pamięci launchera.
    fun cutoutIntent(context: Context, path: String): Intent = withFile(context, base(), File(path))

    // Wynik StickOnMe: link content:// z prawem odczytu (ważny, dopóki żyje aktywność, która go odebrała).
    fun resultUri(data: Intent?): Uri? = data?.data

    private fun withFile(context: Context, intent: Intent, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        // ClipData obok data: część systemów przenosi pozwolenie tylko z ClipData.
        return intent.setDataAndType(uri, "image/*")
            .also { it.clipData = ClipData.newRawUri("", uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // --- Przeniesienie biblioteki (do 1.8 StickOnMe było w launcherze i trzymało pliki w jego pamięci) ---

    private fun legacyLibrary(context: Context) = File(context.filesDir, "stickers/library")
    private fun legacyBoards(context: Context) = File(context.filesDir, "stickers/boards")

    // Czy w launcherze zostały naklejki / tablice z czasów wspólnej instalacji.
    fun hasLegacyLibrary(context: Context): Boolean =
        listOf(legacyLibrary(context), legacyBoards(context)).any { dir -> dir.walkTopDown().any { it.isFile } }

    // Pakuje bibliotekę i tablice do .zip (library/…, boards/…) i zwraca intencję dla StickOnMe.
    suspend fun migrationIntent(context: Context): Intent = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "stickonme").apply { mkdirs() }
        val zip = File(out, "library.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { z ->
            listOf("library" to legacyLibrary(context), "boards" to legacyBoards(context)).forEach { (prefix, dir) ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    z.putNextEntry(ZipEntry("$prefix/" + f.relativeTo(dir).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
        Intent(ACTION_IMPORT)
            .setComponent(ComponentName(PACKAGE, IMPORT_ACTIVITY))
            .setDataAndType(uri, "application/zip")
            .also { it.clipData = ClipData.newRawUri("", uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // Po udanym przeniesieniu: usuwamy starą kopię z launchera (StickOnMe ma już swoją).
    suspend fun clearLegacyLibrary(context: Context) = withContext(Dispatchers.IO) {
        legacyLibrary(context).deleteRecursively()
        legacyBoards(context).deleteRecursively()
        File(context.cacheDir, "stickonme").deleteRecursively()
        File(context.cacheDir, "stickonme_thumbs").deleteRecursively() // miniatury dawnego studia w launcherze
    }
}
