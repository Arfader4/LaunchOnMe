package pl.rafal.contextlauncher.data

import androidx.annotation.StringRes
import org.json.JSONObject
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R

enum class RingerSetting(@StringRes private val labelRes: Int) {
    NORMAL(R.string.mphone_ringer_normal), VIBRATE(R.string.mphone_ringer_vibrate), SILENT(R.string.mphone_ringer_silent);
    val label: String get() = AppText.get(labelRes)
}

// Ustawienia telefonu w danym trybie. null = "nie zmieniaj".
// Głośności i jasność w procentach (0–100), wygaszanie ekranu w sekundach.
data class ModePhoneSettings(
    val dnd: Boolean? = null,
    val ringer: RingerSetting? = null,
    val mediaVolume: Int? = null,
    val ringVolume: Int? = null,
    val alarmVolume: Int? = null,
    val brightness: Int? = null,
    val autoRotate: Boolean? = null,
    val screenTimeoutSec: Int? = null,
    // Poniższych Android nie pozwala zmieniać aplikacjom — launcher tylko przypomina i otwiera panel.
    val wifi: Boolean? = null,
    val bluetooth: Boolean? = null,
    val nfc: Boolean? = null,
    val mobileData: Boolean? = null,
    val batterySaver: Boolean? = null,
) {
    fun isEmpty(): Boolean = this == ModePhoneSettings()

    // Do JSON trafiają tylko ustawione pola.
    fun toJson(): String = JSONObject().apply {
        dnd?.let { put("dnd", it) }
        ringer?.let { put("ringer", it.name) }
        mediaVolume?.let { put("mediaVolume", it) }
        ringVolume?.let { put("ringVolume", it) }
        alarmVolume?.let { put("alarmVolume", it) }
        brightness?.let { put("brightness", it) }
        autoRotate?.let { put("autoRotate", it) }
        screenTimeoutSec?.let { put("screenTimeoutSec", it) }
        wifi?.let { put("wifi", it) }
        bluetooth?.let { put("bluetooth", it) }
        nfc?.let { put("nfc", it) }
        mobileData?.let { put("mobileData", it) }
        batterySaver?.let { put("batterySaver", it) }
    }.toString()

    companion object {
        fun parse(json: String?): ModePhoneSettings {
            if (json.isNullOrBlank()) return ModePhoneSettings()
            return runCatching {
                val o = JSONObject(json)
                // Małe funkcje pomocnicze: wartość albo null, gdy klucza nie ma.
                fun bool(key: String) = if (o.has(key)) o.getBoolean(key) else null
                fun int(key: String) = if (o.has(key)) o.getInt(key) else null
                ModePhoneSettings(
                    dnd = bool("dnd"),
                    ringer = if (o.has("ringer")) RingerSetting.entries.firstOrNull { it.name == o.getString("ringer") } else null,
                    mediaVolume = int("mediaVolume"),
                    ringVolume = int("ringVolume"),
                    alarmVolume = int("alarmVolume"),
                    brightness = int("brightness"),
                    autoRotate = bool("autoRotate"),
                    screenTimeoutSec = int("screenTimeoutSec"),
                    wifi = bool("wifi"),
                    bluetooth = bool("bluetooth"),
                    nfc = bool("nfc"),
                    mobileData = bool("mobileData"),
                    batterySaver = bool("batterySaver"),
                )
            }.getOrDefault(ModePhoneSettings())
        }
    }
}
