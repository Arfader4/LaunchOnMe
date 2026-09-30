package pl.rafal.contextlauncher.system

import android.annotation.SuppressLint
import android.content.Context

// Rozwinięcie panelu powiadomień gestem w dół na ekranie głównym.
// Android nie ma na to publicznego API, ale StatusBarManager.expandNotificationsPanel() jest dostępne
// przez refleksję (jak wywołanie niepublicznej metody przez System.Reflection w .NET) i używają go
// praktycznie wszystkie launchery. Wymaga zwykłego uprawnienia EXPAND_STATUS_BAR (bez okienka).
object StatusBar {
    @SuppressLint("WrongConstant") // "statusbar" nie jest w publicznej liście stałych Context
    fun expandNotifications(context: Context) {
        runCatching {
            val service = context.getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        }
    }

    // Prawa połowa ekranu: szybkie ustawienia (Wi-Fi, Bluetooth, latarka…), jak w nowszych Androidach.
    @SuppressLint("WrongConstant")
    fun expandQuickSettings(context: Context) {
        runCatching {
            val service = context.getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(service)
        }.onFailure { expandNotifications(context) }
    }
}
