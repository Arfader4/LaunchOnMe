package pl.rafal.contextlauncher.data

import org.json.JSONArray
import org.json.JSONObject
import pl.rafal.contextlauncher.data.db.CardItemDao
import pl.rafal.contextlauncher.layout.CardGrid
import pl.rafal.contextlauncher.layout.GridRect

// --- Stos widżetów ---
// Stos to zwykły wiersz karty (własny widżet STACK) z listą id swoich widżetów w config.
// Widżety w stosie zostają pełnoprawnymi wierszami (z własnymi ustawieniami, id widżetu systemowego itd.),
// tylko dostają stronę STACKED_PAGE — dzięki temu nie ma ich na żadnej stronie karty i nie zajmują miejsca.
// (Podobnie jak element "schowany" w C# przez filtr: dane są, ale widok ich nie pokazuje.)
const val STACKED_PAGE = -1

data class StackData(
    val members: List<Long>, // id wierszy karty w kolejności przewijania
    val index: Int = 0,      // który widżet jest teraz na wierzchu
    val swipe: String = SWIPE_AUTO, // gdzie przesunięcie w pionie zmienia widżet (patrz stałe niżej)
) {
    fun toJson(): String = JSONObject()
        .put("members", JSONArray(members))
        .put("index", index)
        .put("swipe", swipe)
        .toString()

    companion object {
        const val SWIPE_AUTO = "auto"         // widżet z przewijaną treścią → tylko uchwyt, pozostałe → cała powierzchnia
        const val SWIPE_ANYWHERE = "anywhere" // zawsze cała powierzchnia
        const val SWIPE_HANDLE = "handle"     // zawsze tylko uchwyt z kropkami

        fun of(json: String?): StackData {
            val cfg = runCatching { JSONObject(json ?: "{}") }.getOrDefault(JSONObject())
            // optLong: uszkodzony wpis (np. w ręcznie zmienionej kopii) pomijamy, zamiast wywracać ekran.
            val members = cfg.optJSONArray("members")?.let { a -> (0 until a.length()).mapNotNull { a.optLong(it, -1).takeIf { id -> id > 0 } } }.orEmpty()
            return StackData(
                members,
                cfg.optInt("index", 0).coerceIn(0, (members.size - 1).coerceAtLeast(0)),
                cfg.optString("swipe", SWIPE_AUTO).takeIf { it in setOf(SWIPE_AUTO, SWIPE_ANYWHERE, SWIPE_HANDLE) } ?: SWIPE_AUTO,
            )
        }
    }
}

// Porządki po edycji / imporcie kopii zapasowej:
//  - stos z jednym widżetem znika, a widżet wraca na jego miejsce (w rozmiarze stosu),
//  - pusty stos znika,
//  - widżet "w stosie", którego żaden stos nie zawiera (sierota), wraca na pierwszą stronę.
suspend fun repairStacks(dao: CardItemDao, modeId: Long) {
    val items = dao.getForMode(modeId)
    val byId = items.associateBy { it.id }
    val claimed = mutableSetOf<Long>()
    items.filter { it.widgetKind == CustomWidgetKind.STACK.name }.forEach { stack ->
        val data = StackData.of(stack.config)
        // claimed.add zwraca false, gdy id już należy do innego stosu (jak HashSet.Add w C#).
        val members = data.members.filter { id -> byId[id]?.page == STACKED_PAGE && claimed.add(id) }
        when {
            members.isEmpty() -> dao.delete(stack.id)
            members.size == 1 -> {
                dao.updatePage(members[0], stack.page, stack.x, stack.y)
                dao.updateSize(members[0], stack.w, stack.h)
                dao.delete(stack.id)
            }
            members != data.members -> dao.updateConfig(stack.id, data.copy(members = members, index = data.index.coerceAtMost(members.size - 1)).toJson())
        }
    }
    items.filter { it.page == STACKED_PAGE && it.id !in claimed }.forEach { orphan ->
        val taken = dao.getForMode(modeId).filter { it.page == 0 }.map { it.toRect() }
        val spot = CardGrid.findFreeSpot(taken, orphan.w, orphan.h) ?: GridRect(0, 0, orphan.w, orphan.h)
        dao.updatePage(orphan.id, 0, spot.x, spot.y)
    }
}
