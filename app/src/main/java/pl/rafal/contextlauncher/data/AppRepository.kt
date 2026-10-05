package pl.rafal.contextlauncher.data

import android.content.Context
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
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
class AppRepository(
    private val context: Context,
    onAppsChanged: () -> Unit = {},
    onShortcutsChanged: () -> Unit = {},
) {

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
        // Aplikacja zmieniła swoje skróty (np. komunikator dodał nową rozmowę).
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = onShortcutsChanged()
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
                    icon = (ownThemedIcon(activity) ?: activity.getIcon(0)).toBitmap(width = 144, height = 144).asImageBitmap(),
                    category = activity.applicationInfo.category,
                )
            }
        }.sortedWith(compareBy(collator) { it.label })
    }

    // Ikona OnThemes zgodna z jasnością launchera (Jasny / Ciemny), a nie systemu. Przy "Auto" — null,
    // czyli zwykła ikona z systemu (system sam wybiera wersję jasną albo ciemną).
    private fun ownThemedIcon(activity: android.content.pm.LauncherActivityInfo): android.graphics.drawable.Drawable? {
        val c = activity.componentName
        if (c.packageName != context.packageName || c.className != pl.rafal.onthemes.OnThemes.ACTIVITY_CLASS) return null
        val dark = pl.rafal.onthemes.ThemeStore.get(context).forcedDark() ?: return null
        return pl.rafal.onthemes.OnThemes.appIcon(context, dark)
    }

    fun launch(app: AppInfo) {
        val shortcut = app.shortcutId
        // sourceBounds + opcje = okno aplikacji "wyrasta" z ikony (a nie wjeżdża z boku).
        val bounds = pl.rafal.contextlauncher.ui.LaunchOrigin.sourceBounds()
        val options = pl.rafal.contextlauncher.ui.LaunchOrigin.options()
        if (shortcut != null) launcherApps.startShortcut(app.packageName, shortcut, bounds, options, app.user)
        else launcherApps.startMainActivity(app.component, app.user, bounds, options)
    }

    // Skróty są dostępne tylko dla DOMYŚLNEGO launchera (system pilnuje tego uprawnienia).
    fun hasShortcutAccess(): Boolean = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    // Wszystkie skróty aplikacji (statyczne z manifestu, dynamiczne i przypięte), jako "ikony" AppInfo.
    // Ikona skrótu dostaje w rogu małą ikonę aplikacji, żeby było wiadomo, do czego prowadzi (jak w Pixel Launcherze).
    suspend fun loadShortcuts(): List<AppInfo> = withContext(Dispatchers.Default) {
        if (!hasShortcutAccess()) return@withContext emptyList()
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        val density = context.resources.displayMetrics.densityDpi
        launcherApps.profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            val appIcons = mutableMapOf<String, android.graphics.drawable.Drawable?>()
            runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty()
                .filter { it.isEnabled }
                .map { info ->
                    val appIcon = appIcons.getOrPut(info.`package`) {
                        launcherApps.getActivityList(info.`package`, user).firstOrNull()?.getIcon(0)
                    }
                    val own = runCatching { launcherApps.getShortcutIconDrawable(info, density) }.getOrNull()
                    AppInfo(
                        label = (info.shortLabel ?: info.longLabel ?: info.id).toString(),
                        component = info.activity ?: ComponentName(info.`package`, info.`package`),
                        user = user,
                        userSerial = serial,
                        icon = badged(own ?: appIcon, appIcon).asImageBitmap(),
                        shortcutId = info.id,
                    )
                }
        }.distinctBy { it.key }.sortedWith(compareBy(Collator.getInstance()) { it.label })
    }

    // Ikona skrótu + mała ikona aplikacji w prawym dolnym rogu.
    private fun badged(main: android.graphics.drawable.Drawable?, badge: android.graphics.drawable.Drawable?): Bitmap {
        val size = 144
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        main?.let { canvas.drawBitmap(it.toBitmap(size, size), 0f, 0f, null) }
        if (badge != null && main !== badge) {
            val b = size * 4 / 10
            canvas.drawBitmap(badge.toBitmap(b, b), (size - b).toFloat(), (size - b).toFloat(), null)
        }
        return out
    }

    // Systemowy ekran "Informacje o aplikacji" (odinstaluj, uprawnienia, wymuś zatrzymanie).
    fun openAppInfo(app: AppInfo) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }
}
