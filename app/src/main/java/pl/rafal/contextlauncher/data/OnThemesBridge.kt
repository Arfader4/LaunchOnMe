package pl.rafal.contextlauncher.data

import android.content.Context
import pl.rafal.contextlauncher.ModeTileService
import pl.rafal.contextlauncher.data.db.LauncherDatabase
import pl.rafal.contextlauncher.ui.ModeIcon
import pl.rafal.onthemes.HostMode
import pl.rafal.onthemes.OnThemesHost

// Launcher jako "gospodarz" dla OnThemes: moduł motywów nie zna bazy launchera, więc pyta przez ten interfejs
// (jak implementacja interfejsu wstrzyknięta do biblioteki w .NET). Rejestrowany w LaunchOnMeApp.
class OnThemesBridge(context: Context) : OnThemesHost {
    private val app = context.applicationContext
    private val modeDao get() = LauncherDatabase.get(app).modeDao()

    override suspend fun modes(): List<HostMode> = modeDao.getAll().map {
        HostMode(
            id = it.id,
            name = it.name,
            iconRes = ModeIcon.of(it.icon).res,
            color = it.color,
            themeId = it.palette,
            accent = it.accent,
        )
    }

    // Zmiana motywu trybu: reszta wyglądu (ikona, kolor, akcent) bez zmian. Karta odświeży się sama (Room Flow).
    override suspend fun setModeTheme(modeId: Long, themeId: String?) {
        val mode = modeDao.getAll().firstOrNull { it.id == modeId }
        if (mode != null) {
            modeDao.updateAppearance(mode.id, mode.icon, mode.color, themeId, mode.accent)
            ModeTileService.requestUpdate(app)
        }
    }
}
