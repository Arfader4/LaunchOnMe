package pl.rafal.contextlauncher.system

import android.content.Context
import pl.rafal.contextlauncher.ModeTileService
import pl.rafal.contextlauncher.data.AppPrefs
import pl.rafal.contextlauncher.data.ModePhoneSettings
import pl.rafal.contextlauncher.data.WallpaperStore
import pl.rafal.contextlauncher.data.db.LauncherDatabase
import pl.rafal.contextlauncher.data.db.ModeEntity

// Jedno miejsce "włączania trybu" — używa go launcher, kafelek, skróty i automat,
// więc wszystkie zachowują się identycznie.
object ModeActivation {
    // manual = true: zmiana zrobiona przez człowieka (automat odczeka wtedy 30 minut).
    suspend fun activate(context: Context, mode: ModeEntity, manual: Boolean) {
        val now = System.currentTimeMillis()
        val dao = LauncherDatabase.get(context).modeDao()
        val previousId = dao.getAll().maxByOrNull { it.lastActiveAt }?.id // aktywny przed zmianą
        dao.markActive(mode.id, now)
        val prefs = AppPrefs.get(context)
        if (manual) {
            prefs.lastManualSwitch.set(now)
            prefs.lastManualLeft.set(previousId?.takeIf { it != mode.id } ?: -1L)
            prefs.timedUntil.set(0) // ręczna zmiana kończy "tryb na czas"
        }
        if (prefs.applyPhoneSettings.value) {
            PhoneSettingsApplier(context).apply(ModePhoneSettings.parse(mode.settings))
        }
        ModeTileService.requestUpdate(context)
        WallpaperStore(context).applyFor(mode.id) // na końcu, bo trwa chwilę; bez tapet nic nie robi
    }
}
