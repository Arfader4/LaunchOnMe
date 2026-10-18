package pl.rafal.contextlauncher.drawer

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.rafal.contextlauncher.data.DrawerArrange
import pl.rafal.contextlauncher.data.DrawerItem
import pl.rafal.contextlauncher.data.DrawerSort
import java.text.Collator
import java.util.Locale

class DrawerArrangeTest {

    private val collator = Collator.getInstance(Locale.forLanguageTag("pl"))
    private val maps = DrawerItem("a:maps", "Mapy", isFolder = false, uses = 5)
    private val lidl = DrawerItem("a:lidl", "Lidl Plus", isFolder = false, uses = 0)
    private val lodz = DrawerItem("a:lodz", "Łódź Bus", isFolder = false, uses = 0)
    private val chrome = DrawerItem("a:chrome", "Chrome", isFolder = false, uses = 9)
    private val work = DrawerItem("f:1", "Praca", isFolder = true, uses = 7)
    private val all = listOf(maps, lidl, lodz, chrome, work)

    private fun keys(list: List<DrawerItem>) = list.map { it.key }

    @Test
    fun alphabetical_polishLettersAfterBase() {
        val out = DrawerArrange.arrange(all, DrawerSort.ALPHA, foldersFirst = false, manualOrder = emptyList(), collator = collator)
        assertEquals(listOf("a:chrome", "a:lidl", "a:lodz", "a:maps", "f:1"), keys(out))
    }

    @Test
    fun mostUsed_tiesBrokenAlphabetically() {
        val out = DrawerArrange.arrange(all, DrawerSort.MOST_USED, foldersFirst = false, manualOrder = emptyList(), collator = collator)
        assertEquals(listOf("a:chrome", "f:1", "a:maps", "a:lidl", "a:lodz"), keys(out))
    }

    @Test
    fun foldersFirst_keepsOrderInsideGroups() {
        val out = DrawerArrange.arrange(all, DrawerSort.ALPHA, foldersFirst = true, manualOrder = emptyList(), collator = collator)
        assertEquals(listOf("f:1", "a:chrome", "a:lidl", "a:lodz", "a:maps"), keys(out))
    }

    @Test
    fun manual_withoutSavedOrder_isAlphabetical() {
        val out = DrawerArrange.arrange(all, DrawerSort.MANUAL, foldersFirst = false, manualOrder = emptyList(), collator = collator)
        assertEquals(keys(DrawerArrange.arrange(all, DrawerSort.ALPHA, false, emptyList(), collator)), keys(out))
    }

    @Test
    fun manual_savedFirst_newOnesAppendedAlphabetically() {
        val out = DrawerArrange.arrange(all, DrawerSort.MANUAL, foldersFirst = false, manualOrder = listOf("a:maps", "f:1", "a:gone"), collator = collator)
        assertEquals(listOf("a:maps", "f:1", "a:chrome", "a:lidl", "a:lodz"), keys(out))
    }

    @Test
    fun mergeOrder_keepsFolderSlotsWhenArrangingAppsOnly() {
        val old = listOf("a:maps", "f:1", "a:chrome", "a:lidl")
        val merged = DrawerArrange.mergeOrder(old, listOf("a:lidl", "a:maps", "a:chrome", "a:lodz"))
        assertEquals(listOf("a:lidl", "f:1", "a:maps", "a:chrome", "a:lodz"), merged)
    }

    @Test
    fun mergeOrder_emptyOld_isArranged() {
        assertEquals(listOf("a:x", "a:y"), DrawerArrange.mergeOrder(emptyList(), listOf("a:x", "a:y")))
    }
}
