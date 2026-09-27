package pl.rafal.contextlauncher

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.rafal.contextlauncher.data.db.LauncherDatabase
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.contextlauncher.system.ModeActivation
import pl.rafal.contextlauncher.ui.ModeIcon

// Kafelek "Tryb" w szybkich ustawieniach (panel wysuwany z góry, obok Wi-Fi i Bluetooth).
// Pokazuje ikonę i nazwę aktywnego trybu; dotknięcie otwiera listę trybów.
// Kolor ikony nadaje system (wszystkie kafelki są barwione tak samo), dlatego tu tylko symbol.
class ModeTileService : TileService() {

    // Własny zakres korutyn: serwis nie ma lifecycleScope, więc sprzątamy go w onDestroy.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val modeDao by lazy { LauncherDatabase.get(applicationContext).modeDao() }

    // Wywoływane, gdy użytkownik otwiera panel z kafelkiem.
    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val modes = modeDao.getAll()
            if (modes.isEmpty()) return@launch
            val active = modes.maxByOrNull { it.lastActiveAt }

            // Na zablokowanym ekranie najpierw prosimy o odblokowanie (lista trybów to już "wnętrze" telefonu).
            val showPicker = {
                val names = modes.map { if (it.id == active?.id) "${it.name}  ✓" else it.name }.toTypedArray()
                val dialog = AlertDialog.Builder(this@ModeTileService, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Wybierz tryb")
                    .setItems(names) { _, which -> select(modes[which]) }
                    .setNegativeButton("Anuluj", null)
                    .create()
                showDialog(dialog) // specjalna metoda kafelka: okno nad panelem szybkich ustawień
            }
            if (isLocked) unlockAndRun { showPicker() } else showPicker()
        }
    }

    private fun select(mode: ModeEntity) {
        scope.launch {
            // To samo co wybór trybu w launcherze: zapis + ustawienia telefonu.
            ModeActivation.activate(applicationContext, mode, manual = true)
            // Launcher obserwuje tę samą bazę (Room), więc karta zmieni się sama.
            refreshTile()
        }
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val active = modeDao.getAll().maxByOrNull { it.lastActiveAt }
        if (active == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Tryb"
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = active.name
            tile.subtitle = "Tryb" // podpis pod nazwą (Android 10+)
            tile.icon = Icon.createWithResource(this, ModeIcon.of(active.icon).res)
            tile.contentDescription = "Tryb ${active.name}"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        // Prośba do systemu o odświeżenie kafelka, gdy tryb zmieniono w launcherze.
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, ModeTileService::class.java))
            }
        }
    }
}
