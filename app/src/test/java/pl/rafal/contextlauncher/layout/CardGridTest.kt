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
    fun `widżetu nie da się zmniejszyć poniżej 1x1`() {
        val resized = CardGrid.resize(GridRect(0, 0, 4, 4), dwCells = -9f, dhCells = -9f)
        assertEquals(GridRect(0, 0, 1, 1), resized)
    }

    @Test
    fun `wyższy ekran daje więcej rzędów`() {
        val old = CardGrid.rows
        try {
            CardGrid.rows = 16
            assertEquals(GridRect(0, 14, 2, 2), CardGrid.snap(GridRect(0, 0, 2, 2), 0f, 30f))
        } finally {
            CardGrid.rows = old
        }
    }

    @Test
    fun `wypychanie przesuwa zasloniety element w najblizsze wolne miejsce`() {
        val items = mapOf(1L to GridRect(0, 0, 2, 2), 2L to GridRect(2, 0, 2, 2))
        // Element 1 jedzie na miejsce elementu 2 — dwójka zajmuje zwolnione miejsce (zamiana miejscami).
        val plan = CardGrid.placeWithPush(1L, GridRect(2, 0, 2, 2), items)!!
        assertEquals(GridRect(2, 0, 2, 2), plan[1L])
        val moved = plan[2L]!!
        assertTrue(!moved.overlaps(GridRect(2, 0, 2, 2)))
        assertEquals(GridRect(0, 0, 2, 2), moved)
    }

    @Test
    fun `elementy, ktorych nie dotyczy ruch, zostaja na miejscu`() {
        val items = mapOf(1L to GridRect(0, 0, 2, 2), 2L to GridRect(6, 10, 2, 2))
        val plan = CardGrid.placeWithPush(1L, GridRect(2, 2, 2, 2), items)!!
        assertEquals(setOf(1L), plan.keys)
    }

    @Test
    fun `pelna karta nie pozwala wypchnac`() {
        val full = (0 until CardGrid.ROWS step 2).flatMap { y ->
            (0 until CardGrid.COLUMNS step 2).map { x -> GridRect(x, y, 2, 2) }
        }.mapIndexed { i, r -> i.toLong() to r }.toMap()
        // Nowy element (id -1) nie ma dokąd wypchnąć tego, na który nachodzi.
        assertNull(CardGrid.placeWithPush(-1L, GridRect(0, 0, 2, 2), full))
    }
}
