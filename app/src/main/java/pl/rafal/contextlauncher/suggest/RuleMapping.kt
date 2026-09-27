package pl.rafal.contextlauncher.suggest

import org.json.JSONObject
import pl.rafal.contextlauncher.data.db.SuggestionRuleEntity
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Tłumaczenie między wierszem z bazy (typ + JSON) a regułą silnika. Jak mapper encja ↔ model domenowy.

fun SuggestionRuleEntity.toRule(): Rule? = runCatching {
    val json = JSONObject(params)
    when (type) {
        SuggestionRuleEntity.TYPE_TIME -> Rule.TimeWindow(
            days = json.getString("days").split(',').filter { it.isNotBlank() }.map { DayOfWeek.of(it.trim().toInt()) }.toSet(),
            start = LocalTime.parse(json.getString("start")),
            end = LocalTime.parse(json.getString("end")),
        )
        SuggestionRuleEntity.TYPE_CALENDAR -> Rule.CalendarKeyword(json.getString("keyword"))
        SuggestionRuleEntity.TYPE_BLUETOOTH -> Rule.BluetoothDevice(json.getString("address"), json.getString("name"))
        SuggestionRuleEntity.TYPE_WIFI -> Rule.WifiNetwork(json.getString("ssid"))
        SuggestionRuleEntity.TYPE_PLACE -> Rule.Place(
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lon"),
            radiusMeters = json.getInt("radius"),
            label = json.getString("label"),
        )
        SuggestionRuleEntity.TYPE_CHARGING -> Rule.Charging(json.optBoolean("charging", true))
        SuggestionRuleEntity.TYPE_HEADPHONES -> Rule.Headphones
        SuggestionRuleEntity.TYPE_BATTERY -> Rule.BatteryBelow(json.getInt("below"))
        else -> null
    }
}.getOrNull() // uszkodzona reguła = pomijamy ją zamiast wywracać launcher

fun timeRuleParams(days: Set<DayOfWeek>, start: LocalTime, end: LocalTime): String =
    JSONObject()
        .put("days", days.sorted().joinToString(",") { it.value.toString() }) // 1 = poniedziałek ... 7 = niedziela
        .put("start", start.toString())
        .put("end", end.toString())
        .toString()

fun calendarRuleParams(keyword: String): String = JSONObject().put("keyword", keyword.trim()).toString()

fun bluetoothRuleParams(address: String, name: String): String =
    JSONObject().put("address", address).put("name", name).toString()

fun wifiRuleParams(ssid: String): String = JSONObject().put("ssid", ssid.trim()).toString()

fun placeRuleParams(latitude: Double, longitude: Double, radiusMeters: Int, label: String): String =
    JSONObject().put("lat", latitude).put("lon", longitude).put("radius", radiusMeters).put("label", label.trim()).toString()

fun chargingRuleParams(charging: Boolean): String = JSONObject().put("charging", charging).toString()

fun batteryRuleParams(belowPercent: Int): String = JSONObject().put("below", belowPercent).toString()

// "8:00", "08:00", "8" → LocalTime; zły format → null (walidacja pola w oknie reguły).
fun parseTimeOrNull(text: String): LocalTime? {
    val t = text.trim()
    val normalized = if (t.contains(':')) t else "$t:00"
    return runCatching { LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
}
