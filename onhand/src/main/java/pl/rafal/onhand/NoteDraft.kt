package pl.rafal.onhand

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.rafal.onhand.data.NoteRepository

// Stan zapisu otwartej notatki (zwykła klasa, nie @Composable). Edytor zapisuje sam: po chwili bez pisania
// i przy wyjściu. Mutex (≈ SemaphoreSlim(1) w C#) pilnuje, żeby dwa zapisy naraz nie utworzyły dwóch notatek.
class NoteDraft(private val repo: NoteRepository, initialId: Long?) {
    private val mutex = Mutex()
    // Osobna blokada na dodawanie załączników: wyjście z edytora czeka, aż trwające kopiowanie się skończy
    // (inaczej pusta notatka "bez załączników" zostałaby usunięta w trakcie kopiowania).
    private val attachMutex = Mutex()

    var id: Long? = initialId
        private set

    private var savedTitle: String? = null
    private var savedText: String? = null
    private var deleted = false

    // Po wczytaniu istniejącej notatki: to jest stan "zapisany", więc nic nie zapisujemy bez zmian.
    fun markLoaded(title: String, text: String) {
        savedTitle = title
        savedText = text
    }

    // Notatki nie ma już w bazie (np. usunięta, a launcher miał do niej odnośnik): to, co się wpisze,
    // zapisze się jako nowa notatka zamiast UPDATE na nieistniejącym wierszu.
    fun detach() {
        id = null
        savedTitle = null
        savedText = null
    }

    // Zapis bez anulowania (NonCancellable): wyjście z ekranu w trakcie zapisu nie zostawi notatki "w połowie"
    // (np. utworzonej w bazie, ale bez zapamiętanego id — wtedy następny zapis zrobiłby duplikat).
    suspend fun save(title: String, text: String) = withContext(NonCancellable) {
        mutex.withLock {
            val changed = title != savedTitle || text != savedText
            val current = id
            if (!deleted && changed) {
                if (current == null) {
                    // Nowa notatka powstaje dopiero, gdy coś w niej jest (pusta "Nowa notatka" nie zostaje na liście).
                    if (title.isNotBlank() || text.isNotBlank()) id = repo.create(title, text)
                } else {
                    repo.update(current, title, text)
                }
                savedTitle = title
                savedText = text
            }
        }
    }

    // Id notatki teraz — także dla pustej (dodanie załącznika do nowej notatki potrzebuje wiersza w bazie).
    suspend fun ensureId(title: String, text: String): Long = withContext(NonCancellable) {
        mutex.withLock {
            id ?: repo.create(title, text).also {
                id = it
                savedTitle = title
                savedText = text
            }
        }
    }

    // Dodanie załączników: id notatki (tworzy pusty wiersz, jeśli trzeba) i kopiowanie pod blokadą.
    suspend fun addAttachments(title: String, text: String, add: suspend (Long) -> Unit) = withContext(NonCancellable) {
        attachMutex.withLock { add(ensureId(title, text)) }
    }

    // Wyjście z edytora: zapis, a notatka wyczyszczona do zera (bez tekstu i bez załączników) znika (jak w Keep).
    suspend fun finish(title: String, text: String) {
        save(title, text)
        attachMutex.withLock { } // poczekaj na kopiowanie w toku
        val current = id
        if (current != null && title.isBlank() && text.isBlank() && repo.attachmentCount(current) == 0) delete()
    }

    suspend fun delete() = withContext(NonCancellable) {
        mutex.withLock {
            deleted = true
            id?.let { repo.delete(it) }
        }
    }
}
