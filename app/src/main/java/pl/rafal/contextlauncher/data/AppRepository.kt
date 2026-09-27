package pl.rafal.contextlauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

// Repozytorium ukrywa szczegóły Androida przed resztą aplikacji (jak warstwa DAL w .NET).
class AppRepository(context: Context, onAppsChanged: () -> Unit = {}) {

    // LauncherApps to systemowa usługa stworzona właśnie dla launcherów.
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    // System woła te metody przy instalacji, usunięciu, aktualizacji i (od)blokowaniu profilu służbowego.
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = onAppsChanged()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = onAppsChanged()
        override fun onPackageChanged(packageName: String, user: UserHandle) = onAppsChanged()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onAppsChanged()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onAppsChanged()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun close() {
        runCatching { launcherApps.unregisterCallback(callback) }
    }

    // suspend ≈ async w C#. withContext(Dispatchers.Default) ≈ await Task.Run(...):
    // ładowanie ikon jest ciężkie, więc robimy je poza wątkiem UI.
    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val collator = Collator.getInstance() // sortowanie zgodne z językiem (Ł po L, Ś po S)

        // flatMap ≈ SelectMany, map ≈ Select, sortedWith ≈ OrderBy z własnym komparatorem.
        launcherApps.profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).map { activity ->
                AppInfo(
                    label = activity.label.toString(),
                    component = activity.componentName,
                    user = user,
                    userSerial = serial,
                    icon = activity.getIcon(0).toBitmap(width = 144, height = 144).asImageBitmap(),
                    category = activity.applicationInfo.category,
                )
            }
        }.sortedWith(compareBy(collator) { it.label })
    }

    fun launch(app: AppInfo) {
        launcherApps.startMainActivity(app.component, app.user, null, null)
    }

    // Systemowy ekran "Informacje o aplikacji" (odinstaluj, uprawnienia, wymuś zatrzymanie).
    fun openAppInfo(app: AppInfo) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }
}
