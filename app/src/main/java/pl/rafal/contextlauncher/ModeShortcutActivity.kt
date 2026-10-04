package pl.rafal.contextlauncher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import pl.rafal.contextlauncher.data.db.LauncherDatabase
import pl.rafal.contextlauncher.system.ModeActivation

// Niewidoczna aktywność uruchamiana przez skrót "Włącz tryb …": włącza tryb i od razu się zamyka.
class ModeShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modeId = intent.getLongExtra(EXTRA_MODE_ID, -1)
        lifecycleScope.launch {
            val mode = LauncherDatabase.get(applicationContext).modeDao().getAll().firstOrNull { it.id == modeId }
            if (mode != null) {
                ModeActivation.activate(applicationContext, mode, manual = true)
                Toast.makeText(applicationContext, getString(R.string.act_mode_enabled, mode.name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, getString(R.string.act_mode_gone), Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_MODE_ID = "mode_id"
    }
}
