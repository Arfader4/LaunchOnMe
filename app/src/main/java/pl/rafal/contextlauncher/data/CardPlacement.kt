package pl.rafal.contextlauncher.data

import pl.rafal.contextlauncher.data.db.CardItemDao
import pl.rafal.contextlauncher.data.db.CardItemEntity
import pl.rafal.contextlauncher.layout.CardGrid

// Położenie własnego widżetu w pierwszym wolnym miejscu karty trybu.
// Wspólne dla launchera i ekranu "Udostępnij" (naklejka wysłana z innej aplikacji).
// Zwraca false, gdy na karcie nie ma miejsca.
suspend fun placeCustomWidget(dao: CardItemDao, modeId: Long, kind: CustomWidgetKind, config: String): Boolean {
    val existing = dao.getForMode(modeId)
    val spot = CardGrid.findFreeSpot(existing.map { it.toRect() }, kind.w, kind.h) ?: return false
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
        ),
    )
    return true
}
