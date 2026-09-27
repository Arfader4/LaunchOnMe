package pl.rafal.contextlauncher.system

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.IntentCompat

// Odbiornik systemowych komunikatów "połączono / rozłączono urządzenie Bluetooth".
// Zapisany w manifeście, więc działa nawet wtedy, gdy launcher nie jest otwarty.
// Zapamiętuje adresy połączonych urządzeń; silnik sugestii czyta je przy powrocie na ekran główny.
class BluetoothConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val address = device.address ?: return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> ConnectedDevices.add(context, address)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> ConnectedDevices.remove(context, address)
        }
    }
}

// Mały magazyn połączonych urządzeń w SharedPreferences (zbiór napisów).
object ConnectedDevices {
    private const val PREFS = "bluetooth"
    private const val KEY = "connected"

    private const val KEY_BOOT = "boot_count"

    // Numer uruchomienia telefonu. Po restarcie nie przychodzi "rozłączono", więc stary zbiór trzeba unieważnić.
    private fun bootCount(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(0)

    fun get(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_BOOT, -1) != bootCount(context)) return emptySet()
        return prefs.getStringSet(KEY, emptySet()).orEmpty()
    }

    fun add(context: Context, address: String) = update(context) { it + address }

    fun remove(context: Context, address: String) = update(context) { it - address }

    // Funkcja przyjmująca funkcję: "weź zbiór, zmień go i zapisz" (jak Func<Set, Set> w C#).
    private fun update(context: Context, change: (Set<String>) -> Set<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Uwaga: zbioru zwróconego przez getStringSet nie wolno modyfikować, dlatego tworzymy nowy.
        prefs.edit().putStringSet(KEY, change(get(context)).toSet()).putInt(KEY_BOOT, bootCount(context)).apply()
    }
}
