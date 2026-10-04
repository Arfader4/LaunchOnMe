package pl.rafal.contextlauncher.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import org.json.JSONArray
import org.json.JSONObject
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.db.CardItemEntity
import pl.rafal.contextlauncher.data.db.FolderEntity
import pl.rafal.contextlauncher.layout.GridRect

// Folder "na karcie": powstaje z upuszczenia ikony na ikonę i żyje TYLKO na tej karcie.
// Cała zawartość siedzi w konfiguracji widżetu (JSON), a nie w tabeli folderów — dzięki temu:
//  - nie pojawia się w szufladzie (foldery szuflady → karta działają, w drugą stronę nie),
//  - "✕" w edycji układu cofa go razem z resztą karty (zdjęcie karty obejmuje config).
data class CardFolderData(
    val name: String,
    val icon: String?,
    val color: Long?,
    val keys: List<String>, // AppInfo.key kolejnych aplikacji (kolejność = kolejność w folderze)
    val grid: Int = 2,           // miniatura: 2×2 albo 3×3 ikony
    val sort: String = SORT_MANUAL,
    val keep: Boolean = false,   // folder utworzony celowo (np. pusty z listy widżetów) — nie znika sam przy 1 aplikacji
    val children: List<CardFolderData> = emptyList(), // podfoldery (zapisane w tym samym JSON-ie, dowolnie głęboko)
    val align: String = ALIGN_START, // wyrównanie niepełnego rzędu: do lewej / środka / prawej
    val fromBottom: Boolean = false, // true = pełne rzędy przy dole (bliżej kciuka), niepełny rząd na górze
) {
    // Podfolder po ścieżce indeksów, np. [1, 0] = pierwszy podfolder drugiego podfolderu. Pusta ścieżka = ten folder.
    fun at(path: List<Int>): CardFolderData? =
        path.fold(this as CardFolderData?) { folder, index -> folder?.children?.getOrNull(index) }

    // Kopia z podmienionym podfolderem pod ścieżką (dane są niezmienne — jak rekordy "with" w C#).
    fun update(path: List<Int>, change: (CardFolderData) -> CardFolderData): CardFolderData =
        if (path.isEmpty()) change(this)
        else copy(children = children.mapIndexed { i, child -> if (i == path[0]) child.update(path.drop(1), change) else child })

    // Wszystkie klucze aplikacji razem z podfolderami (np. przy rozwiązywaniu folderu).
    fun allKeys(): List<String> = keys + children.flatMap { it.allKeys() }

    // Usunięcie podfolderu: jego aplikacje (także z głębszych poziomów) przechodzą do tego folderu.
    fun removeChild(index: Int): CardFolderData {
        val removed = children.getOrNull(index) ?: return this
        return copy(
            keys = keys + removed.allKeys().filter { it !in keys }.distinct(),
            children = children.filterIndexed { i, _ -> i != index },
        )
    }

    // Kolejność pokazywania: ręczna, alfabetyczna albo według liczby uruchomień w tym trybie.
    fun ordered(apps: List<AppInfo>, launches: Map<String, Int>): List<AppInfo> = when (sort) {
        SORT_ALPHA -> apps.sortedBy { it.label.lowercase() }
        SORT_USAGE -> apps.sortedByDescending { launches[it.key] ?: 0 } // sortowanie stabilne: remis = kolejność ręczna
        else -> apps
    }

    fun toJson(): String = toJsonObject().toString()

    fun toJsonObject(): JSONObject = JSONObject()
        .put("name", name)
        .putOpt("icon", icon)
        .putOpt("color", color)
        .put("apps", JSONArray(keys))
        .put("grid", grid)
        .put("sort", sort)
        .put("keep", keep)
        .put("align", align)
        .put("fromBottom", fromBottom)
        .apply { if (children.isNotEmpty()) put("children", JSONArray(children.map { it.toJsonObject() })) }

    // Ten sam wygląd co foldery szuflady, więc do rysowania "udajemy" encję (id = id elementu karty).
    fun asEntity(id: Long) = FolderEntity(id = id, name = name, icon = icon, color = color)

    companion object {
        const val SORT_MANUAL = "manual"
        const val SORT_ALPHA = "alpha"
        const val SORT_USAGE = "usage"
        const val ALIGN_START = "start"
        const val ALIGN_CENTER = "center"
        const val ALIGN_END = "end"

        fun of(json: String?): CardFolderData = of(runCatching { JSONObject(json ?: "{}") }.getOrDefault(JSONObject()))

        fun of(cfg: JSONObject): CardFolderData = CardFolderData(
            name = cfg.optString("name", "Folder").ifBlank { "Folder" },
            icon = if (cfg.isNull("icon")) null else cfg.optString("icon").ifBlank { null },
            color = if (cfg.has("color") && !cfg.isNull("color")) cfg.getLong("color") else null,
            keys = cfg.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            grid = cfg.optInt("grid", 2).coerceIn(2, 3),
            sort = cfg.optString("sort", SORT_MANUAL),
            keep = cfg.optBoolean("keep", false),
            children = cfg.optJSONArray("children")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> of(o) } } }.orEmpty(),
            align = cfg.optString("align", ALIGN_START).takeIf { it in setOf(ALIGN_START, ALIGN_CENTER, ALIGN_END) } ?: ALIGN_START,
            fromBottom = cfg.optBoolean("fromBottom", false),
        )
    }
}

// Podział n elementów na rzędy po `columns`. Od góry: niepełny rząd na końcu (jak zwykle).
// Od dołu: niepełny rząd na początku, więc pełne rzędy lądują przy dolnej krawędzi — kolejność czytania bez zmian.
// Zwraca zakresy indeksów (jak Enumerable.Chunk w .NET, ale z "resztą" z przodu).
fun folderRows(count: Int, columns: Int, fromBottom: Boolean): List<IntRange> {
    if (count <= 0 || columns <= 0) return emptyList()
    val rest = count % columns
    val starts = if (fromBottom && rest != 0) listOf(0) + (rest until count step columns) else (0 until count step columns).toList()
    return starts.mapIndexed { i, start -> start until (starts.getOrNull(i + 1) ?: count) }
}

// "pakiet/klasa#profil" → (ComponentName, profil). Odwrotność AppInfo.key.
fun parseAppKey(key: String): Pair<ComponentName, Long>? {
    val hash = key.lastIndexOf('#')
    if (hash < 0) return null
    val component = ComponentName.unflattenFromString(key.substring(0, hash)) ?: return null
    val serial = key.substring(hash + 1).toLongOrNull() ?: return null
    return component to serial
}

// Wiersz karty dla aplikacji zapisanej kluczem (np. przy wyjmowaniu z folderu na kartę).
fun appItemFor(modeId: Long, key: String, rect: GridRect, page: Int = 0): CardItemEntity? {
    if (key.startsWith(SHORTCUT_KEY)) {
        // "s:pakiet/idSkrótu#profil" (id zakodowane, bo może zawierać "/" albo "#").
        val body = key.removePrefix(SHORTCUT_KEY)
        val hash = body.lastIndexOf('#')
        val slash = body.indexOf('/')
        if (hash < 0 || slash < 0 || slash > hash) return null
        return CardItemEntity(
            modeId = modeId,
            type = CardItemEntity.TYPE_SHORTCUT,
            packageName = body.substring(0, slash),
            className = android.net.Uri.decode(body.substring(slash + 1, hash)),
            userSerial = body.substring(hash + 1).toLongOrNull() ?: return null,
            x = rect.x, y = rect.y, w = rect.w, h = rect.h,
            page = page,
        )
    }
    val (component, serial) = parseAppKey(key) ?: return null
    return CardItemEntity(
        modeId = modeId,
        packageName = component.packageName,
        className = component.className,
        userSerial = serial,
        x = rect.x, y = rect.y, w = rect.w, h = rect.h,
        page = page,
    )
}

// Polskie nazwy kategorii ze sklepu (ApplicationInfo.category). Służą jako "tagi": do nazw folderów,
// filtrów w wyborze aplikacji i podpowiedzi, co jeszcze pasuje do folderu.
val CategoryLabels: Map<Int, String>
    get() = linkedMapOf(
        ApplicationInfo.CATEGORY_SOCIAL to AppText.get(R.string.folder_cat_social),
        ApplicationInfo.CATEGORY_AUDIO to AppText.get(R.string.folder_cat_audio),
        ApplicationInfo.CATEGORY_VIDEO to AppText.get(R.string.folder_cat_video),
        ApplicationInfo.CATEGORY_IMAGE to AppText.get(R.string.folder_cat_image),
        ApplicationInfo.CATEGORY_GAME to AppText.get(R.string.folder_cat_game),
        ApplicationInfo.CATEGORY_NEWS to AppText.get(R.string.folder_cat_news),
        ApplicationInfo.CATEGORY_MAPS to AppText.get(R.string.folder_cat_maps),
        ApplicationInfo.CATEGORY_PRODUCTIVITY to AppText.get(R.string.folder_cat_productivity),
    )

// Nazwa nowego folderu z kategorii aplikacji (jak w Pixel Launcherze): dwie gry → "Gry".
// Kategorie różne albo nieznane → po prostu "Folder" (nazwę łatwo zmienić jednym dotknięciem).
fun autoFolderName(apps: List<AppInfo>): String {
    val categories = apps.map { it.category }.filter { it != ApplicationInfo.CATEGORY_UNDEFINED }
    val common = categories.groupingBy { it }.eachCount().maxByOrNull { it.value } // ≈ GroupBy + OrderByDescending(Count)
    if (common == null || common.value * 2 <= apps.size) return AppText.get(R.string.folder_default_name) // kategoria musi mieć większość
    return CategoryLabels[common.key] ?: AppText.get(R.string.folder_default_name)
}

// Podpowiedzi do folderu: aplikacje z tych samych kategorii co już dodane albo z kategorii,
// której nazwa pasuje do nazwy folderu ("Gry" → wszystkie gry).
fun suggestedApps(all: List<AppInfo>, inFolder: List<AppInfo>, folderName: String?): List<AppInfo> {
    val cats = inFolder.map { it.category }.toMutableSet()
    CategoryLabels.entries.firstOrNull { folderName != null && it.value.equals(folderName.trim(), ignoreCase = true) }?.let { cats += it.key }
    cats -= ApplicationInfo.CATEGORY_UNDEFINED
    val taken = inFolder.map { it.key }.toSet()
    return all.filter { it.category in cats && it.key !in taken }
}
