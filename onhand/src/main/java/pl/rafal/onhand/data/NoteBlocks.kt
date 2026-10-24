package pl.rafal.onhand.data

// 1.10.0 (On5): załączniki w treści notatki. Treść dalej jest zwykłym tekstem (bez migracji bazy) — załącznik
// w środku to osobna linia-znacznik „[📎 nazwa.jpg]” z nazwą pliku (nazwy są w notatce unikalne, patrz uniqueFile).
// W .txt i w „Udostępnij” znacznik czyta się jak zwykły tekst; edytor rysuje w tym miejscu zdjęcie albo plik.
// Czysta logika bez Androida (jak SuggestionEngine), więc łatwo ją sprawdzić testem JUnit.
object NoteBlocks {
    private const val PREFIX = "[📎 "
    private const val SUFFIX = "]"

    // Treść pocięta na kawałki tekstu i załączniki między nimi: segments.size == names.size + 1
    // (tekst, załącznik, tekst, załącznik, tekst) — jak naprzemienna lista w C#.
    data class Parsed(val segments: List<String>, val names: List<String>)

    fun token(name: String): String = PREFIX + name + SUFFIX

    // Nazwa z linii-znacznika albo null, gdy to zwykła linia.
    fun nameOf(line: String): String? =
        line.takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) && it.length > PREFIX.length + SUFFIX.length }
            ?.substring(PREFIX.length, line.length - SUFFIX.length)

    fun parse(text: String): Parsed {
        val segments = ArrayList<String>()
        val names = ArrayList<String>()
        val buffer = ArrayList<String>()
        for (line in text.split('\n')) {
            val name = nameOf(line)
            if (name != null) {
                segments += buffer.joinToString("\n")
                buffer.clear()
                names += name
            } else {
                buffer += line
            }
        }
        segments += buffer.joinToString("\n")
        return Parsed(segments, names)
    }

    // Odwrotność parse: znacznik zawsze w osobnej linii. Puste kawałki na brzegach nie dokładają pustych linii,
    // więc join(parse(x)) to już postać „ustalona” — kolejne parse/join jej nie zmienia.
    fun join(segments: List<String>, names: List<String>): String {
        val out = StringBuilder()
        segments.forEachIndexed { i, segment ->
            val atEdge = i == 0 || i == segments.lastIndex
            if (segment.isNotEmpty() || !atEdge) {
                if (out.isNotEmpty() || i > 0) out.append('\n')
                out.append(segment)
            }
            if (i < names.size) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(token(names[i]))
            }
        }
        return out.toString()
    }

    fun names(text: String): List<String> = text.split('\n').mapNotNull(::nameOf)

    // Tekst bez znaczników — podgląd na liście, tytuł z pierwszej linii, wyszukiwanie.
    fun plain(text: String): String = text.split('\n').filter { nameOf(it) == null }.joinToString("\n")

    // Usunięcie załącznika z treści (wszystkie jego znaczniki).
    fun remove(text: String, name: String): String {
        val parsed = parse(text)
        val segments = ArrayList<String>()
        val names = ArrayList<String>()
        var pending = parsed.segments[0]
        parsed.names.forEachIndexed { i, n ->
            val next = parsed.segments[i + 1]
            if (n == name) {
                // Sklejamy sąsiednie kawałki tekstu (pusta linia między nimi tylko, gdy oba coś mają).
                pending = listOf(pending, next).filter { it.isNotEmpty() }.joinToString("\n")
            } else {
                segments += pending
                names += n
                pending = next
            }
        }
        segments += pending
        return join(segments, names)
    }
}
