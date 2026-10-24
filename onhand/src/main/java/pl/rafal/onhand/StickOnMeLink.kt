package pl.rafal.onhand

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

// 1.10.0 (On7): naklejki ze StickOnMe w notatkach. StickOnMe to osobna aplikacja — OnHand woła ją jawną intencją
// (jak launcher w StickOnMeBridge) i dostaje naklejkę jako link content:// z prawem odczytu, który od razu kopiuje
// do załączników. Stałe muszą się zgadzać z obiektem StickOnMe w module :studio (StickerLibrary.kt).
internal object StickOnMeLink {
    private const val PACKAGE = "pl.rafal.stickonme"
    private const val ACTIVITY = "pl.rafal.stickonme.StudioActivity"
    private const val EXTRA_PICK = "pl.rafal.stickonme.PICK"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun base(): Intent = Intent().setComponent(ComponentName(PACKAGE, ACTIVITY)).putExtra(EXTRA_PICK, true)

    // Biblioteka StickOnMe w trybie wyboru: gotowa naklejka albo nowa — wynik: data = link do PNG.
    fun pickIntent(): Intent = base()

    // Zdjęcie z notatki do wycięcia w StickOnMe (link tylko do odczytu na czas edycji) — wynik: gotowa naklejka.
    fun cutoutIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.onhand", file)
        // ClipData obok data: część systemów przenosi pozwolenie tylko z ClipData.
        return base().setDataAndType(uri, "image/*")
            .also { it.clipData = ClipData.newRawUri("", uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun resultUri(data: Intent?): Uri? = data?.data
}
