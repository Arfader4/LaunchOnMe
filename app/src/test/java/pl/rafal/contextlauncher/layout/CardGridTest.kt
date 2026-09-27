package pl.rafal.contextlauncher.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Testy jednostkowe: prawy przycisk na klasie → Run 'CardGridTest'. Działają bez emulatora.
class CardGridTest {

    @Test
    fun `stykające się prostokąty nie nachodzą na siebie`() {
        val a = GridRect(0, 0, 2, 2)
        val b = GridRect(2, 0, 2, 2)
        assertFalse(a.overlaps(b))
    }

    @Test
    fun `przesunięte o pół ikony nachodzą na siebie`() {
        val a = GridRect(0, 0, 2, 2)
        val b = GridRect(1, 1, 2, 2)
        assertTrue(a.overlaps(b))
    }

    @Test
    fun `na pustej karcie pierwsza ikona trafia w lewy górny róg`() {
        assertEquals(GridRect(0, 0, 2, 2), CardGrid.findFreeSpot(emptyList(), 2, 2))
    }

    @Test
    fun `kolejna ikona trafia obok poprzedniej`() {
        val taken = listOf(GridRect(0, 0, 2, 2))
        assertEquals(GridRect(2, 0, 2, 2), CardGrid.findFreeSpot(taken, 2, 2))
    }

    @Test
    fun `pełna karta nie ma wolnego miejsca`() {
        // Wypełniamy całą siatkę ikonami 2×2.
        val full = (0 until CardGrid.ROWS step 2).flatMap { y ->
            (0 until CardGrid.COLUMNS step 2).map { x -> GridRect(x, y, 2, 2) }
        }
        assertNull(CardGrid.findFreeSpot(full, 2, 2))
    }

    @Test
    fun `przeciągnięcie poza kartę zatrzymuje się na krawędzi`() {
        val snapped = CardGrid.snap(GridRect(6, 10, 2, 2), dxCells = 5.4f, dyCells = -20f)
        assertEquals(GridRect(6, 0, 2, 2), snapped)
    }

    @Test
    fun `przeciągnięcie zaokrągla do najbliższej komórki`() {
        val snapped = CardGrid.snap(GridRect(0, 0, 2, 2), dxCells = 2.6f, dyCells = 0.4f)
        assertEquals(GridRect(3, 0, 2, 2), snapped)
    }

    @Test
    fun `powiększenie widżetu zatrzymuje się na krawędzi karty`() {
        val resized = CardGrid.resize(GridRect(4, 0, 2, 2), dwCells = 10f, dhCells = 1.2f)
        assertEquals(GridRect(4, 0, 4, 3), resized)
    }

    @Test
    fun `widżetu nie da się zmniejszyć poniżej 2x2`() {
        val resized = CardGrid.resize(GridRect(0, 0, 4, 4), dwCells = -9f, dhCells = -9f)
        assertEquals(GridRect(0, 0, 2, 2), resized)
    }
}
