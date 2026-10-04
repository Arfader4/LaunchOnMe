package pl.rafal.contextlauncher.data

import androidx.annotation.StringRes
import org.json.JSONObject
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.system.QuickToggle

// Co ma się stać po dotknięciu naklejki (i w przyszłości innych elementów).
// sealed interface = zamknięta rodzina typów, jak abstrakcyjny rekord z kilkoma podklasami w C#.
sealed interface TapAction {
    val label: String // opis do okna ustawień, np. "Otwórz: Spotify"

    data class OpenApp(val packageName: String, val className: String, val userSerial: Long, val appLabel: String) : TapAction {
        override val label get() = AppText.get(R.string.tap_label_open_app, appLabel)
    }
    data class Dial(val number: String, val name: String) : TapAction {
        override val label get() = AppText.get(R.string.tap_label_dial, name)
    }
    data class Sms(val number: String, val name: String) : TapAction {
        override val label get() = AppText.get(R.string.tap_label_sms, name)
    }
    data class Toggle(val toggle: QuickToggle) : TapAction {
        override val label get() = AppText.get(R.string.tap_label_toggle, toggle.label)
    }
    data class SwitchMode(val modeId: Long, val modeName: String) : TapAction {
        override val label get() = AppText.get(R.string.tap_label_switch_mode, modeName)
    }
    data class OpenLink(val url: String) : TapAction {
        override val label get() = AppText.get(R.string.tap_label_open_link, url)
    }
    // Skrót aplikacji (np. konkretny czat) — działa, gdy LaunchOnMe jest domyślnym launcherem.
    data class OpenShortcut(val packageName: String, val shortcutId: String, val userSerial: Long, val name: String) : TapAction {
        override val label get() = AppText.get(R.string.tap_label_shortcut, name)
    }
    // Akcja systemowa: panel Wi-Fi, ustawienia, aparat, budziki…
    data class System(val action: SystemAction) : TapAction {
        override val label get() = action.label
    }

    fun toJson(): JSONObject = when (this) {
        is OpenApp -> JSONObject().put("type", "app").put("pkg", packageName).put("cls", className).put("serial", userSerial).put("name", appLabel)
        is Dial -> JSONObject().put("type", "dial").put("number", number).put("name", name)
        is Sms -> JSONObject().put("type", "sms").put("number", number).put("name", name)
        is Toggle -> JSONObject().put("type", "toggle").put("toggle", toggle.name)
        is SwitchMode -> JSONObject().put("type", "mode").put("id", modeId).put("name", modeName)
        is OpenLink -> JSONObject().put("type", "link").put("url", url)
        is OpenShortcut -> JSONObject().put("type", "shortcut").put("pkg", packageName).put("id", shortcutId).put("serial", userSerial).put("name", name)
        is System -> JSONObject().put("type", "system").put("action", action.name)
    }

    companion object {
        // Odczyt z configu widżetu (klucz "action"); nieznany albo uszkodzony wpis = brak akcji.
        fun from(config: JSONObject): TapAction? = listFrom(config).firstOrNull()

        // Wszystkie akcje (nowy zapis: tablica "actions"; stary: pojedyncze "action").
        fun listFrom(config: JSONObject): List<TapAction> {
            config.optJSONArray("actions")?.let { arr ->
                return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parse) }
            }
            return listOfNotNull(config.optJSONObject("action")?.let(::parse))
        }

        private fun parse(o: JSONObject): TapAction? {
            return runCatching {
                when (o.getString("type")) {
                    "app" -> OpenApp(o.getString("pkg"), o.getString("cls"), o.getLong("serial"), o.optString("name"))
                    "dial" -> Dial(o.getString("number"), o.optString("name"))
                    "sms" -> Sms(o.getString("number"), o.optString("name"))
                    "toggle" -> Toggle(QuickToggle.valueOf(o.getString("toggle")))
                    "mode" -> SwitchMode(o.getLong("id"), o.optString("name"))
                    "link" -> OpenLink(o.getString("url"))
                    "shortcut" -> OpenShortcut(o.getString("pkg"), o.getString("id"), o.getLong("serial"), o.optString("name"))
                    "system" -> System(SystemAction.valueOf(o.getString("action")))
                    else -> null
                }
            }.getOrNull()
        }
    }
}

// Zapis akcji do istniejącego configu (zachowuje plik, obrót itd.); null usuwa akcję.
fun JSONObject.withAction(action: TapAction?): JSONObject =
    if (action == null) apply { remove("action") } else put("action", action.toJson())

// Zapis listy akcji (wykonywanych po kolei). Pusta lista = naklejka bez akcji.
fun JSONObject.withActions(actions: List<TapAction>): JSONObject = apply {
    remove("action")
    if (actions.isEmpty()) remove("actions")
    else put("actions", org.json.JSONArray(actions.map { it.toJson() }))
}

// Opis kilku akcji w jednej linii, np. "Przełącz: Latarka → Otwórz: Spotify".
fun List<TapAction>.describe(): String? = if (isEmpty()) null else joinToString(" → ") { it.label }

// Akcje systemowe, które da się wywołać bez specjalnych uprawnień. Android od wersji 10 nie pozwala
// aplikacjom samym włączać Wi-Fi — zamiast tego otwieramy systemowy panel z przełącznikiem (1 dotknięcie).
enum class SystemAction(@StringRes private val labelRes: Int) {
    WIFI_PANEL(R.string.tap_sys_wifi_panel),
    INTERNET_PANEL(R.string.tap_sys_internet_panel),
    VOLUME_PANEL(R.string.tap_sys_volume_panel),
    NFC_PANEL(R.string.tap_sys_nfc_panel),
    QUICK_SETTINGS(R.string.tap_sys_quick_settings),
    NOTIFICATIONS(R.string.tap_sys_notifications),
    CAMERA(R.string.tap_sys_camera),
    ALARMS(R.string.tap_sys_alarms),
    TIMER(R.string.tap_sys_timer),
    BLUETOOTH_SETTINGS(R.string.tap_sys_bluetooth_settings),
    LOCATION_SETTINGS(R.string.tap_sys_location_settings),
    BATTERY_SAVER(R.string.tap_sys_battery_saver),
    DISPLAY_SETTINGS(R.string.tap_sys_display_settings),
    SOUND_SETTINGS(R.string.tap_sys_sound_settings),
    APP_SETTINGS(R.string.tap_sys_app_settings),
    ;

    val label: String get() = AppText.get(labelRes)
}
