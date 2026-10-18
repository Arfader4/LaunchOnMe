package pl.rafal.contextlauncher.data

import java.text.Collator

// Pozycja szuflady sprowadzona do tego, czego potrzeba do sortowania (bez ikon i klas Androida),
// więc całą logikę da się sprawdzić zwykłym testem jednostkowym.
data class DrawerItem(
    val key: String,       // "a:…" albo "f:…" (patrz DrawerOrder)
    val label: String,
    val isFolder: Boolean,
    val uses: Int = 0,     // uruchomienia w aktywnym OnMode (folder = suma jego aplikacji)
)

object DrawerArrange {

    fun arrange(
        items: List<DrawerItem>,
        sort: DrawerSort,
        foldersFirst: Boolean,
        manualOrder: List<String>,
        collator: Comparator<Any> = Collator.getInstance(), // zgodnie z językiem: Ł po L, Ś po S
    ): List<DrawerItem> {
        val alpha = compareBy<DrawerItem, Any>(collator) { it.label }
        val sorted = when (sort) {
            DrawerSort.ALPHA -> items.sortedWith(alpha)
            // Remis (np. obie nigdy nie otwierane) rozstrzyga alfabet, żeby lista nie "skakała".
            DrawerSort.MOST_USED -> items.sortedWith(compareByDescending<DrawerItem> { it.uses }.then(alpha))
            DrawerSort.MANUAL -> manual(items, manualOrder, alpha)
        }
        // partition ≈ dwa Where naraz; kolejność wewnątrz obu grup zostaje (sortowanie stabilne).
        if (!foldersFirst) return sorted
        val (folders, apps) = sorted.partition { it.isFolder }
        return folders + apps
    }

    // Własna kolejność: najpierw to, co już ułożone (w zapisanej kolejności), potem nowe pozycje alfabetycznie.
    // Bez zapisanej kolejności wychodzi więc zwykła lista A–Z — to jest punkt startowy do układania.
    private fun manual(items: List<DrawerItem>, order: List<String>, alpha: Comparator<DrawerItem>): List<DrawerItem> {
        val rank = order.withIndex().associate { (i, k) -> k to i }
        val (placed, unplaced) = items.partition { it.key in rank }
        return placed.sortedBy { rank.getValue(it.key) } + unplaced.sortedWith(alpha)
    }

    // Zapis po układaniu. W widoku zakładek układamy same aplikacje — foldery mają zostać tam, gdzie były
    // w zapisanej kolejności. Miejsca zajmowane przez ułożone klucze wypełniamy po kolei nową kolejnością,
    // reszta zostaje na swoich miejscach, a nadwyżka (np. nowe aplikacje) idzie na koniec.
    fun mergeOrder(old: List<String>, arranged: List<String>): List<String> {
        val moved = arranged.toSet()
        val next = arranged.iterator()
        val out = ArrayList<String>(old.size + arranged.size)
        for (k in old) {
            if (k in moved) { if (next.hasNext()) out += next.next() } else out += k
        }
        next.forEachRemaining { out += it }
        return out.distinct()
    }
}
