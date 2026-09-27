package pl.rafal.contextlauncher.system

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Przełączniki widżetu. Część zmieniamy sami (latarka, DND, dźwięk, obrót),
// a Wi-Fi i Bluetooth Android pozwala zmienić tylko w panelu systemowym — wtedy zwracamy Intent.
enum class QuickToggle(val label: String) {
    TORCH("Latarka"),
    DND("Nie przeszk."),
    RINGER("Dźwięk"),
    ROTATION("Obrót"),
    INTERNET("Internet"),
    BLUETOOTH("Bluetooth"),
}

enum class RingerState { NORMAL, VIBRATE, SILENT }

data class QuickStates(
    val torch: Boolean = false,
    val torchAvailable: Boolean = false,
    val dnd: Boolean = false,
    val ringer: RingerState = RingerState.NORMAL,
    val rotation: Boolean = false,
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
)

class QuickToggles(context: Context) {
    private val app = context.applicationContext
    private val camera = app.getSystemService(CameraManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val audio = app.getSystemService(AudioManager::class.java)

    // Aparat z lampą błyskową (zwykle tylny). null = telefon bez latarki.
    private val torchCameraId: String? = runCatching {
        camera.cameraIdList.firstOrNull { id ->
            camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private val _states = MutableStateFlow(QuickStates())
    val states: StateFlow<QuickStates> = _states.asStateFlow()
    private var torchOn = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                torchOn = enabled
                refresh()
            }
        }
    }

    init {
        // Latarkę może włączyć też panel systemowy — słuchamy zmian, żeby ikona zawsze pokazywała prawdę.
        runCatching { camera.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper())) }
        refresh()
    }

    // Wyrejestrowanie przy zamknięciu ViewModelu — inaczej system trzymałby referencję (wyciek pamięci).
    fun close() {
        runCatching { camera.unregisterTorchCallback(torchCallback) }
    }

    fun refresh() {
        val resolver = app.contentResolver
        _states.value = QuickStates(
            torch = torchOn,
            torchAvailable = torchCameraId != null,
            dnd = notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
            ringer = when (audio.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> RingerState.SILENT
                AudioManager.RINGER_MODE_VIBRATE -> RingerState.VIBRATE
                else -> RingerState.NORMAL
            },
            rotation = runCatching { Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION) == 1 }.getOrDefault(false),
            wifi = runCatching { app.getSystemService(WifiManager::class.java)?.isWifiEnabled == true }.getOrDefault(false),
            bluetooth = runCatching { app.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false),
        )
    }

    // Wykonuje przełączenie. Zwraca Intent, gdy trzeba otworzyć ekran systemu (panel albo prośbę o zgodę).
    fun toggle(toggle: QuickToggle): Intent? {
        val current = _states.value
        val result: Intent? = when (toggle) {
            QuickToggle.TORCH -> {
                torchCameraId?.let { id -> runCatching { camera.setTorchMode(id, !current.torch) } }
                null
            }
            QuickToggle.DND ->
                if (!notifications.isNotificationPolicyAccessGranted) Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                else {
                    runCatching {
                        notifications.setInterruptionFilter(
                            if (current.dnd) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                        )
                    }
                    null
                }
            // Dzwonek → wibracje → cisza → dzwonek. Cisza wymaga zgody na DND, bez niej pomijamy ją w cyklu.
            QuickToggle.RINGER -> {
                val canSilence = notifications.isNotificationPolicyAccessGranted
                val next = when (current.ringer) {
                    RingerState.NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    RingerState.VIBRATE -> if (canSilence) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                    RingerState.SILENT -> AudioManager.RINGER_MODE_NORMAL
                }
                runCatching { audio.ringerMode = next }
                null
            }
            QuickToggle.ROTATION ->
                if (!Settings.System.canWrite(app)) {
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${app.packageName}"))
                } else {
                    runCatching { Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (current.rotation) 0 else 1) }
                    null
                }
            QuickToggle.INTERNET -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            // Włączenie: systemowe okienko "Zezwolić?"; wyłączenie: tylko w ustawieniach.
            QuickToggle.BLUETOOTH ->
                if (current.bluetooth) Intent(Settings.ACTION_BLUETOOTH_SETTINGS) else Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        }
        refresh()
        return result
    }
}
