package pl.rafal.contextlauncher.data

import org.json.JSONObject
import pl.rafal.contextlauncher.system.QuickToggle

// Co ma się stać po dotknięciu naklejki (i w przyszłości innych elementów).
// sealed interface = zamknięta rodzina typów, jak abstrakcyjny rekord z kilkoma podklasami w C#.
sealed interface TapAction {
    val label: String // opis do okna ustawień, np. "Otwórz: Spotify"

    data class OpenApp(val packageName: String, val className: String, val userSerial: Long, val appLabel: String) : TapAction {
        override val label get() = "Otwórz: $appLabel"
    }
    data class Dial(val number: String, val name: String) : TapAction {
        override val label get() = "Zadzwoń: $name"
    }
    data class Sms(val number: String, val name: String) : TapAction {
        override val label get() = "SMS: $name"
    }
    data class Toggle(val toggle: QuickToggle) : TapAction {
        override val label get() = "Przełącz: ${toggle.label}"
    }
    data class SwitchMode(val modeId: Long, val modeName: String) : TapAction {
        override val label get() = "Włącz tryb: $modeName"
    }
    data class OpenLink(val url: String) : TapAction {
        override val label get() = "Otwórz link: $url"
    }

    fun toJson(): JSONObject = when (this) {
        is OpenApp -> JSONObject().put("type", "app").put("pkg", packageName).put("cls", className).put("serial", userSerial).put("name", appLabel)
        is Dial -> JSONObject().put("type", "dial").put("number", number).put("name", name)
        is Sms -> JSONObject().put("type", "sms").put("number", number).put("name", name)
        is Toggle -> JSONObject().put("type", "toggle").put("toggle", toggle.name)
        is SwitchMode -> JSONObject().put("type", "mode").put("id", modeId).put("name", modeName)
        is OpenLink -> JSONObject().put("type", "link").put("url", url)
    }

    companion object {
        // Odczyt z configu widżetu (klucz "action"); nieznany albo uszkodzony wpis = brak akcji.
        fun from(config: JSONObject): TapAction? {
            val o = config.optJSONObject("action") ?: return null
            return runCatching {
                when (o.getString("type")) {
                    "app" -> OpenApp(o.getString("pkg"), o.getString("cls"), o.getLong("serial"), o.optString("name"))
                    "dial" -> Dial(o.getString("number"), o.optString("name"))
                    "sms" -> Sms(o.getString("number"), o.optString("name"))
                    "toggle" -> Toggle(QuickToggle.valueOf(o.getString("toggle")))
                    "mode" -> SwitchMode(o.getLong("id"), o.optString("name"))
                    "link" -> OpenLink(o.getString("url"))
                    else -> null
                }
            }.getOrNull()
        }
    }
}

// Zapis akcji do istniejącego configu (zachowuje plik, obrót itd.); null usuwa akcję.
fun JSONObject.withAction(action: TapAction?): JSONObject =
    if (action == null) apply { remove("action") } else put("action", action.toJson())
