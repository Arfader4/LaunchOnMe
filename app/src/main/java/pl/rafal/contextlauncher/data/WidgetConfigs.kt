package pl.rafal.contextlauncher.data

import org.json.JSONArray
import org.json.JSONObject
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import java.time.LocalDate

// Ustawienia widżetów "Lista" i "Odliczanie" trzymane w kolumnie config (JSON), jak inne własne widżety.

data class CheckItem(val text: String, val done: Boolean = false)

data class Checklist(val title: String, val items: List<CheckItem>) {
    fun toJson(): String = JSONObject()
        .put("title", title)
        .put("items", JSONArray(items.map { JSONObject().put("t", it.text).put("d", it.done) }))
        .toString()

    companion object {
        fun parse(config: JSONObject): Checklist {
            val array = config.optJSONArray("items") ?: JSONArray()
            val items = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                CheckItem(o.optString("t"), o.optBoolean("d"))
            }
            return Checklist(config.optString("title", AppText.get(R.string.w_checklist_default_title)), items)
        }

        // Do szablonów: tytuł + pozycje (wszystkie nieodhaczone).
        fun of(title: String, vararg items: String) = Checklist(title, items.map { CheckItem(it) }).toJson()
    }
}

data class Countdown(val title: String, val date: LocalDate?) {
    fun toJson(): String = JSONObject().put("title", title).apply { date?.let { put("date", it.toString()) } }.toString()

    companion object {
        fun parse(config: JSONObject) = Countdown(
            title = config.optString("title", ""),
            date = config.optString("date").takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )

        fun of(title: String) = Countdown(title, null).toJson()
    }
}
