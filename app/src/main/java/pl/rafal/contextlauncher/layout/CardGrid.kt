package pl.rafal.contextlauncher.layout

import kotlin.math.roundToInt

// Czysta logika układu karty: bez Androida i bez Compose, więc da się ją testować zwykłym JUnitem.

// Prostokąt na siatce, w komórkach (nie w pikselach).
data class GridRect(val x: Int, val y: Int, val w: Int, val h: Int) {

    // Dwa prostokąty nachodzą na siebie, gdy przecinają się w poziomie I w pionie.
    fun overlaps(other: GridRect): Boolean =
        x < other.x + other.w && other.x < x + w &&
            y < other.y + other.h && other.y < y + h

    fun fitsIn(columns: Int, rows: Int): Boolean =
        x >= 0 && y >= 0 && x + w <= columns && y + h <= rows
}

// object = singleton; w C# odpowiednik klasy static.
object CardGrid {
    const val COLUMNS = 8   // 8 kolumn: ikona 1×1 (8 w rzędzie) albo 2×2 (4 w rzędzie, jak dawniej)
    const val ROWS = 12     // minimalna liczba rzędów (tyle mają szablony i stare układy)

    // Ile rzędów mieści ekran — ustawia widok karty po zmierzeniu wysokości (kwadratowe komórki,
    // więc na wysokim telefonie jest ich więcej niż 12). Pole zamiast stałej: to cecha ekranu, nie kodu.
    @Volatile var rows: Int = ROWS

    // Rozmiar ikony aplikacji w komórkach (Ustawienia → Wygląd): 1 = mała, 2 = duża.
    @Volatile var appSize: Int = 2
    val APP_SIZE: Int get() = appSize

    fun canPlace(rect: GridRect, others: List<GridRect>): Boolean =
        rect.fitsIn(COLUMNS, rows) && others.none { it.overlaps(rect) }

    // Pierwsze wolne miejsce: najpierw "równe" pozycje co 2 komórki (porządny układ),
    // a gdy ich zabraknie, dowolna wolna pozycja.
    fun findFreeSpot(others: List<GridRect>, w: Int, h: Int): GridRect? {
        for (step in listOf(2, 1)) {
            for (y in 0..rows - h step step) {
                for (x in 0..COLUMNS - w step step) {
                    val candidate = GridRect(x, y, w, h)
                    if (canPlace(candidate, others)) return candidate
                }
            }
        }
        return null // karta pełna
    }

    // Przesunięcie o (dx, dy) komórek, zaokrąglone do najbliższej komórki i przycięte do siatki.
    fun snap(original: GridRect, dxCells: Float, dyCells: Float): GridRect =
        original.copy(
            x = (original.x + dxCells).roundToInt().coerceIn(0, COLUMNS - original.w),
            y = (original.y + dyCells).roundToInt().coerceIn(0, rows - original.h),
        )

    // Zmiana rozmiaru o (dw, dh) komórek od prawego dolnego rogu; lewy górny róg zostaje na miejscu.
    fun resize(original: GridRect, dwCells: Float, dhCells: Float, minW: Int = 1, minH: Int = 1): GridRect =
        original.copy(
            w = (original.w + dwCells).roundToInt().coerceIn(minW, maxOf(minW, COLUMNS - original.x)),
            h = (original.h + dhCells).roundToInt().coerceIn(minH, maxOf(minH, rows - original.y)),
        )

    // Położenie elementu z "wypychaniem": elementy, na które nachodzi, przesuwają się w najbliższe
    // wolne miejsce (licząc od ich obecnej pozycji). Reszta karty zostaje nietknięta — bez efektu domina,
    // żeby układ był przewidywalny. Zwraca nowe pozycje (klucz = id) albo null, gdy się nie da.
    fun <K> placeWithPush(id: K, target: GridRect, items: Map<K, GridRect>): Map<K, GridRect>? {
        if (!target.fitsIn(COLUMNS, rows)) return null
        val others = items.filterKeys { it != id }
        val displaced = others.filterValues { it.overlaps(target) }
        val fixed = (others - displaced.keys).values.toMutableList()
        fixed += target

        val result = linkedMapOf(id to target)
        // Najpierw większe elementy (trudniej je upchnąć), potem mniejsze.
        for ((key, rect) in displaced.entries.sortedByDescending { it.value.w * it.value.h }) {
            val spot = nearestFreeSpot(rect, fixed) ?: return null
            fixed += spot
            result[key] = spot
        }
        return result
    }

    // Wolne miejsce najbliższe oryginalnej pozycji (odległość środków), o tym samym rozmiarze.
    fun nearestFreeSpot(original: GridRect, taken: List<GridRect>): GridRect? {
        var best: GridRect? = null
        var bestDistance = Int.MAX_VALUE
        for (y in 0..rows - original.h) {
            for (x in 0..COLUMNS - original.w) {
                val candidate = GridRect(x, y, original.w, original.h)
                if (taken.any { it.overlaps(candidate) }) continue
                val dx = x - original.x
                val dy = y - original.y
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    best = candidate
                    bestDistance = distance
                }
            }
        }
        return best
    }
}
