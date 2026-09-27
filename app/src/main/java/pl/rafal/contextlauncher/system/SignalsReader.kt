package pl.rafal.contextlauncher.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import pl.rafal.contextlauncher.suggest.GeoPoint
import pl.rafal.contextlauncher.suggest.Signals

// Urządzenie sparowane z telefonem (do wyboru w regule Bluetooth).
data class PairedDevice(val address: String, val name: String)

// Odczyt stanu otoczenia telefonu dla silnika sugestii: Bluetooth, Wi-Fi, lokalizacja.
// Każdy odczyt jest "miękki": bez uprawnienia albo przy błędzie zwraca pustą wartość zamiast wyjątku.
class SignalsReader(private val context: Context) {

    fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // Od Androida 12 Bluetooth ma osobne uprawnienie "urządzenia w pobliżu".
    fun bluetoothPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null

    fun hasBluetoothPermission(): Boolean = bluetoothPermission()?.let(::has) ?: true

    fun hasLocationPermission(): Boolean = has(Manifest.permission.ACCESS_FINE_LOCATION)

    // Do pogody wystarczy lokalizacja przybliżona (użytkownik może wybrać "w przybliżeniu").
    fun hasApproxLocationPermission(): Boolean =
        hasLocationPermission() || has(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun read(needBluetooth: Boolean, needWifi: Boolean, needLocation: Boolean, needPower: Boolean = false, needHeadphones: Boolean = false): Signals {
        val battery = if (needPower) batteryState() else null
        return Signals(
            connectedBluetooth = if (needBluetooth) ConnectedDevices.get(context) else emptySet(),
            wifiSsid = if (needWifi) currentSsid() else null,
            location = if (needLocation) lastLocation()?.let { GeoPoint(it.latitude, it.longitude) } else null,
            charging = battery?.first,
            batteryPercent = battery?.second,
            headphones = needHeadphones && headphonesConnected(),
        )
    }

    // Stan baterii z "przyklejonego" komunikatu systemu (sticky broadcast): odbiornik null = tylko odczyt, bez nasłuchu.
    fun batteryState(): Pair<Boolean, Int>? = runCatching {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        plugged to (level * 100 / scale.coerceAtLeast(1))
    }.getOrNull()

    // Słuchawki = któreś z wyjść audio to słuchawki (przewodowe, USB albo Bluetooth). Bez uprawnień.
    fun headphonesConnected(): Boolean = runCatching {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HeadphoneTypes }
    }.getOrDefault(false)

    private companion object {
        val HeadphoneTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }

    @SuppressLint("MissingPermission") // sprawdzamy uprawnienie ręcznie, linijkę wyżej
    fun pairedDevices(): List<PairedDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty().map { PairedDevice(it.address, it.name ?: it.address) }.sortedBy { it.name }
        }.getOrDefault(emptyList())
    }

    // Nazwa sieci Wi-Fi. Android podaje ją tylko aplikacjom z uprawnieniem do lokalizacji
    // (bo po nazwie sieci da się ustalić, gdzie jesteś); bez niego wraca "<unknown ssid>".
    @Suppress("DEPRECATION") // connectionInfo jest przestarzałe, ale wciąż najprostsze do odczytu SSID
    fun currentSsid(): String? {
        if (!hasLocationPermission()) return null
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        val ssid = runCatching { wifi.connectionInfo?.ssid }.getOrNull() ?: return null
        return ssid.removeSurrounding("\"").takeUnless { it.isBlank() || it == "<unknown ssid>" }
    }

    // Ostatnia znana lokalizacja z dowolnego dostawcy (GPS, sieć). Nie włącza GPS-u, więc nie zużywa baterii.
    @SuppressLint("MissingPermission")
    fun lastLocation(): Location? {
        if (!hasApproxLocationPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time } // najświeższa
        }.getOrNull()
    }
}
