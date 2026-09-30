package pl.rafal.contextlauncher.system

import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import pl.rafal.contextlauncher.data.SystemAction

// Intencja dla akcji systemowej (odpowiednik Process.Start("ms-settings:...") w Windows).
// Panele (Settings.Panel) to małe okienka z przełącznikiem na dole ekranu — Android 10+ zamiast zmiany "po cichu".
fun systemActionIntent(action: SystemAction): Intent? = when (action) {
    SystemAction.WIFI_PANEL -> Intent(Settings.Panel.ACTION_WIFI)
    SystemAction.INTERNET_PANEL -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    SystemAction.VOLUME_PANEL -> Intent(Settings.Panel.ACTION_VOLUME)
    SystemAction.NFC_PANEL -> Intent(Settings.Panel.ACTION_NFC)
    SystemAction.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    SystemAction.ALARMS -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
    SystemAction.TIMER -> Intent(AlarmClock.ACTION_SHOW_TIMERS)
    SystemAction.BLUETOOTH_SETTINGS -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    SystemAction.LOCATION_SETTINGS -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    SystemAction.BATTERY_SAVER -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    SystemAction.DISPLAY_SETTINGS -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
    SystemAction.SOUND_SETTINGS -> Intent(Settings.ACTION_SOUND_SETTINGS)
    SystemAction.APP_SETTINGS -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
    // Te dwa launcher obsługuje sam (rozwinięcie paska stanu), bez intencji.
    SystemAction.QUICK_SETTINGS, SystemAction.NOTIFICATIONS -> null
}.also { it?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
