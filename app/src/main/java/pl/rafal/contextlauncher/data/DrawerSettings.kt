package pl.rafal.contextlauncher.data

import org.json.JSONArray
import org.json.JSONObject

// 1.7.0: szuflada aplikacji ustawiana osobno w każdym OnMode (np. "Work On Mode" po kolei, "Start On Mode" alfabetycznie).

// Widok szuflady: dwie zakładki (Wszystkie | Foldery) albo jedna lista, w której foldery stoją między aplikacjami.
enum class DrawerView { TABS, INTEGRATED }

// Sortowanie listy: alfabetycznie, najczęściej używane (w tym OnMode) albo własna kolejność ułożona palcem.
enum class DrawerSort { ALPHA, MOST_USED, MANUAL }

data class DrawerSettings(
    val view: DrawerView = DrawerView.TABS,
    val sort: DrawerSort = DrawerSort.ALPHA,
    val foldersFirst: Boolean = false, // tylko w jednej liście: foldery na początku, potem aplikacje
    val showNew: Boolean = true,       // sekcja "Nowe" (świeżo zainstalowane, jeszcze nieotwarte)
    val showFrequent: Boolean = true,  // rząd "Często w …" (przy sortowaniu "najczęściej używane" i tak się chowa)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("view", view.name)
        .put("sort", sort.name)
        .put("foldersFirst", foldersFirst)
        .put("new", showNew)
        .put("frequent", showFrequent)

    companion object {
        val DEFAULT = DrawerSettings()

        // reified = typ generyczny znany w czasie wykonania (w C# to działa samo, w JVM trzeba o to poprosić).
        private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
            runCatching { enumValueOf<E>(name!!) }.getOrDefault(fallback)

        // Ustawienia ogólne (ekran Ustawienia) — jeden obiekt JSON; pusty/uszkodzony = domyślne.
        fun parse(json: String): DrawerSettings =
            runCatching { fromJson(JSONObject(json)) }.getOrDefault(DEFAULT)

        fun fromJson(o: JSONObject) = DrawerSettings(
            view = enumOr(o.optString("view"), DrawerView.TABS),
            sort = enumOr(o.optString("sort"), DrawerSort.ALPHA),
            foldersFirst = o.optBoolean("foldersFirst", false),
            showNew = o.optBoolean("new", true),
            showFrequent = o.optBoolean("frequent", true),
        )

        // Nadpisania per OnMode (tylko tryby z włączoną "własną szufladą") w jednym JSON-ie, jak ModeLayout: {"12": {"view": "TABS", ...}, ...}
        fun parseAll(json: String): Map<Long, DrawerSettings> = runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().mapNotNull { k ->
                val id = k.toLongOrNull() ?: return@mapNotNull null
                val v = o.optJSONObject(k) ?: return@mapNotNull null
                id to fromJson(v)
            }.toMap()
        }.getOrDefault(emptyMap())

        fun writeAll(map: Map<Long, DrawerSettings>): String =
            JSONObject().apply { map.forEach { (id, s) -> put(id.toString(), s.toJson()) } }.toString()
    }
}

// Własna kolejność szuflady per OnMode: {"12": ["a:pakiet/klasa#0", "f:3", ...]}.
// "a:" = aplikacja (AppInfo.key), "f:" = folder (id z bazy).
object DrawerOrder {
    const val APP = "a:"
    const val FOLDER = "f:"
    const val GLOBAL = -1L // kolejność wspólna dla trybów bez własnych ustawień szuflady

    fun parseAll(json: String): Map<Long, List<String>> = runCatching {
        val o = JSONObject(json)
        o.keys().asSequence().mapNotNull { k ->
            val id = k.toLongOrNull() ?: return@mapNotNull null
            val arr = o.optJSONArray(k) ?: return@mapNotNull null
            id to List(arr.length()) { arr.getString(it) }
        }.toMap()
    }.getOrDefault(emptyMap())

    fun writeAll(map: Map<Long, List<String>>): String =
        JSONObject().apply { map.forEach { (id, keys) -> put(id.toString(), JSONArray(keys)) } }.toString()
}

// 1.7.1: sortowanie wewnątrz folderu szuflady (jedno dla folderu, niezależne od OnMode).
// Klucze w kolejności: "f:<id podfolderu>", "a:<AppInfo.key>" — klucz aplikacji przeżywa przeniesienie i kopię zapasową.
data class FolderSort(val sort: DrawerSort = DrawerSort.ALPHA, val order: List<String> = emptyList()) {
    fun toJson(): JSONObject = JSONObject().put("sort", sort.name).put("order", JSONArray(order))

    companion object {
        val DEFAULT = FolderSort()

        fun parseAll(json: String): Map<Long, FolderSort> = runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().mapNotNull { k ->
                val id = k.toLongOrNull() ?: return@mapNotNull null
                val v = o.optJSONObject(k) ?: return@mapNotNull null
                val arr = v.optJSONArray("order")
                id to FolderSort(
                    sort = runCatching { DrawerSort.valueOf(v.optString("sort")) }.getOrDefault(DrawerSort.ALPHA),
                    order = if (arr == null) emptyList() else List(arr.length()) { arr.getString(it) },
                )
            }.toMap()
        }.getOrDefault(emptyMap())

        fun writeAll(map: Map<Long, FolderSort>): String =
            JSONObject().apply { map.forEach { (id, s) -> put(id.toString(), s.toJson()) } }.toString()
    }
}

// Klucze "f:<stare id>" po przywróceniu kopii → nowe id folderów (folder, którego już nie ma, wypada).
fun remapFolderKeys(keys: List<String>, folderIds: Map<Long, Long>): List<String> = keys.mapNotNull { k ->
    if (!k.startsWith(DrawerOrder.FOLDER)) k
    else k.removePrefix(DrawerOrder.FOLDER).toLongOrNull()?.let { folderIds[it] }?.let { DrawerOrder.FOLDER + it }
}
