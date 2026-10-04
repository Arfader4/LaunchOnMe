package pl.rafal.contextlauncher.system

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.ModePhoneSettings
import pl.rafal.contextlauncher.data.RingerSetting
import kotlin.math.roundToInt

// Przełącznik, którego Android nie pozwala zmienić aplikacji: pokazujemy przypomnienie i otwieramy panel.
enum class ManualToggle(@StringRes private val labelRes: Int) {
    WIFI(R.string.sys_toggle_wifi),
    BLUETOOTH(R.string.sys_toggle_bluetooth),
    NFC(R.string.sys_toggle_nfc),
    MOBILE_DATA(R.string.sys_toggle_mobile_data),
    BATTERY_SAVER(R.string.sys_toggle_battery_saver);

    val label: String get() = AppText.get(labelRes)
}

data class ManualTask(val toggle: ManualToggle, val turnOn: Boolean) {
    val label: String get() = AppText.get(if (turnOn) R.string.sys_manual_task_on else R.string.sys_manual_task_off, toggle.label)
}

// Wykonuje ustawienia trybu. Wszystko w runCatching: brak zgody albo nietypowy telefon nie może wywrócić launchera.
class PhoneSettingsApplier(private val context: Context) {

    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    // Specjalne zgody nadawane przez użytkownika w ustawieniach systemu (nie zwykłe okienko uprawnień).
    fun hasDndAccess(): Boolean = notifications.isNotificationPolicyAccessGranted

    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    fun dndAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun writeSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun apply(settings: ModePhoneSettings) {
        if (hasDndAccess()) {
            settings.dnd?.let { on ->
                // "Priorytet" = Nie przeszkadzać z wyjątkami ustawionymi w systemie (jak domyślnie na Samsungu).
                runCatching {
                    notifications.setInterruptionFilter(
                        if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
                    )
                }
            }
        }
        // Tryb dźwięku: cisza wymaga zgody na "Nie przeszkadzać", więc przy jej braku może się nie udać.
        settings.ringer?.let { ringer ->
            runCatching {
                audio.ringerMode = when (ringer) {
                    RingerSetting.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                    RingerSetting.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                    RingerSetting.SILENT -> AudioManager.RINGER_MODE_SILENT
                }
            }
        }
        settings.mediaVolume?.let { setVolume(AudioManager.STREAM_MUSIC, it) }
        settings.ringVolume?.let { setVolume(AudioManager.STREAM_RING, it) }
        settings.alarmVolume?.let { setVolume(AudioManager.STREAM_ALARM, it) }

        if (canWriteSettings()) {
            val resolver = context.contentResolver
            settings.brightness?.let { pct ->
                runCatching {
                    Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (pct.coerceIn(1, 100) * 255 / 100.0).roundToInt())
                }
            }
            settings.autoRotate?.let { on ->
                runCatching { Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0) }
            }
            settings.screenTimeoutSec?.let { sec ->
                runCatching { Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, sec * 1000) }
            }
        }
    }

    private fun setVolume(stream: Int, percent: Int) {
        runCatching {
            val max = audio.getStreamMaxVolume(stream)
            audio.setStreamVolume(stream, (percent.coerceIn(0, 100) * max / 100.0).roundToInt(), 0)
        }
    }

    // Co trzeba przełączyć ręcznie, bo stan telefonu różni się od ustawień trybu.
    fun pendingManual(settings: ModePhoneSettings): List<ManualTask> = buildList {
        fun check(want: Boolean?, current: Boolean?, toggle: ManualToggle) {
            if (want != null && current != null && want != current) add(ManualTask(toggle, want))
        }
        check(settings.wifi, wifiOn(), ManualToggle.WIFI)
        check(settings.bluetooth, bluetoothOn(), ManualToggle.BLUETOOTH)
        check(settings.nfc, nfcOn(), ManualToggle.NFC)
        check(settings.mobileData, mobileDataOn(), ManualToggle.MOBILE_DATA)
        check(settings.batterySaver, batterySaverOn(), ManualToggle.BATTERY_SAVER)
    }

    // Systemowe mini-panele (Android 10+) albo ekrany ustawień tam, gdzie panelu nie ma.
    fun intentFor(task: ManualTask): Intent = when (task.toggle) {
        ManualToggle.WIFI -> Intent(Settings.Panel.ACTION_WIFI)
        ManualToggle.NFC -> Intent(Settings.Panel.ACTION_NFC)
        ManualToggle.MOBILE_DATA -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        ManualToggle.BATTERY_SAVER -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        // Włączanie Bluetooth ma systemowe okienko "Zezwolić na włączenie?"; wyłączanie tylko w ustawieniach.
        ManualToggle.BLUETOOTH ->
            if (task.turnOn) Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE) else Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    private fun wifiOn(): Boolean? =
        runCatching { context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled }.getOrNull()

    private fun bluetoothOn(): Boolean? =
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }.getOrNull()

    private fun nfcOn(): Boolean? = runCatching { NfcAdapter.getDefaultAdapter(context)?.isEnabled }.getOrNull()

    // Ustawienie "mobile_data" w Settings.Global da się odczytać bez uprawnień (zmienić już nie).
    private fun mobileDataOn(): Boolean? =
        runCatching { Settings.Global.getInt(context.contentResolver, "mobile_data") == 1 }.getOrNull()

    private fun batterySaverOn(): Boolean? =
        runCatching { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode }.getOrNull()
}
