package pl.rafal.contextlauncher.data

import pl.rafal.contextlauncher.data.db.CardItemDao
import pl.rafal.contextlauncher.data.db.CardItemEntity
import pl.rafal.contextlauncher.layout.CardGrid
import pl.rafal.contextlauncher.layout.GridRect

// Wolne miejsca na stronach karty jednego trybu (w pamięci, jak lokalna kopia tabeli w C#).
// Szukamy od wskazanej strony, potem na kolejnych — także na nowej, aż do limitu stron z Ustawień —
// a na końcu na wcześniejszych. Dzięki temu pełna strona nie kończy się komunikatem "brak miejsca".
class PageSpace(items: List<CardItemEntity>, private val maxPages: Int) {
    // Strony, które już istnieją (mogą przekraczać limit, jeśli ktoś go potem zmniejszył) — tam też szukamy.
    private val existingCount = (items.filter { it.page >= 0 }.maxOfOrNull { it.page } ?: -1) + 1
    private val taken: MutableMap<Int, MutableList<GridRect>> =
        items.filter { it.page >= 0 } // widżety w stosach (strona -1) nie zajmują miejsca
            .groupBy { it.page }
            .mapValues { (_, list) -> list.map { it.toRect() }.toMutableList() }
            .toMutableMap()

    private fun takenOn(page: Int) = taken.getOrPut(page) { mutableListOf() }

    // Zwraca (strona, miejsce) i od razu je rezerwuje — kolejne wywołania nie trafią w to samo pole.
    fun find(preferred: Int, w: Int, h: Int): Pair<Int, GridRect>? {
        val last = maxOf(maxPages, existingCount, preferred + 1)
        val order = (preferred until last) + (0 until preferred)
        for (page in order) {
            val spot = CardGrid.findFreeSpot(takenOn(page), w, h) ?: continue
            takenOn(page) += spot
            return page to spot
        }
        return null
    }

    // Zarezerwowanie miejsca wybranego z zewnątrz (np. upuszczenie w konkretne pole).
    fun reserve(page: Int, rect: GridRect) {
        takenOn(page) += rect
    }
}

// Położenie własnego widżetu w pierwszym wolnym miejscu karty trybu (od strony [page], potem dalsze).
// Wspólne dla launchera i ekranu "Udostępnij" (naklejka wysłana z innej aplikacji).
// Zwraca stronę, na którą trafił widżet, albo null, gdy na żadnej stronie (w limicie) nie ma miejsca.
suspend fun placeCustomWidget(
    dao: CardItemDao,
    modeId: Long,
    kind: CustomWidgetKind,
    config: String,
    page: Int = 0,
    maxPages: Int = 1,
): Int? {
    val (target, spot) = PageSpace(dao.getForMode(modeId), maxPages).find(page, kind.w, kind.h) ?: return null
    dao.insert(
        CardItemEntity(
            modeId = modeId,
            type = CardItemEntity.TYPE_CUSTOM,
            packageName = "",
            className = "",
            userSerial = 0,
            x = spot.x,
            y = spot.y,
            w = spot.w,
            h = spot.h,
            widgetKind = kind.name,
            config = config,
            page = target,
        ),
    )
    return target
}
