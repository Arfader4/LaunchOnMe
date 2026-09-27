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
    const val COLUMNS = 8   // drobna siatka: ikona zajmuje 2×2 komórki,
    const val ROWS = 12     // więc na szerokość mieszczą się 4 ikony, ale można je przesuwać o pół ikony
    const val APP_SIZE = 2

    fun canPlace(rect: GridRect, others: List<GridRect>): Boolean =
        rect.fitsIn(COLUMNS, ROWS) && others.none { it.overlaps(rect) }

    // Pierwsze wolne miejsce: najpierw "równe" pozycje co 2 komórki (porządny układ),
    // a gdy ich zabraknie, dowolna wolna pozycja.
    fun findFreeSpot(others: List<GridRect>, w: Int, h: Int): GridRect? {
        for (step in listOf(2, 1)) {
            for (y in 0..ROWS - h step step) {
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
            y = (original.y + dyCells).roundToInt().coerceIn(0, ROWS - original.h),
        )

    // Zmiana rozmiaru o (dw, dh) komórek od prawego dolnego rogu; lewy górny róg zostaje na miejscu.
    fun resize(original: GridRect, dwCells: Float, dhCells: Float, minW: Int = 2, minH: Int = 2): GridRect =
        original.copy(
            w = (original.w + dwCells).roundToInt().coerceIn(minW, maxOf(minW, COLUMNS - original.x)),
            h = (original.h + dhCells).roundToInt().coerceIn(minH, maxOf(minH, ROWS - original.y)),
        )
}
