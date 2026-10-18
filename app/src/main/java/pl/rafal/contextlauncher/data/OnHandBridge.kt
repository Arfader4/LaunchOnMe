package pl.rafal.contextlauncher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.rafal.contextlauncher.data.db.LauncherDatabase
import pl.rafal.contextlauncher.data.db.PinnedItemEntity
import pl.rafal.onhand.OnHand
import pl.rafal.onhand.OnHandHost

// Most launcher ↔ OnHand. Launcher implementuje OnHandHost (tryby, przypinanie), a OnHand nic nie wie
// o bazie launchera — jak implementacja interfejsu z biblioteki, wstrzyknięta przy starcie (LaunchOnMeApp).
object OnHandBridge {
    // Zakres na prace "przy starcie" (migracja) — żyje tyle co proces.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val migrationLock = Mutex()

    fun install(context: Context) {
        val app = context.applicationContext
        OnHand.registerHost(Host(app))
        scope.launch { runCatching { migrateLegacyNotes(app) } }
    }

    private class Host(private val context: Context) : OnHandHost {
        private val db get() = LauncherDatabase.get(context)
        // Przypinanie i odpinanie po kolei (szybkie dotknięcia pola wyboru nie mogą się wyprzedzić).
        private val pinLock = Mutex()

        override suspend fun modes(): List<OnHandHost.Mode> =
            db.modeDao().getAll().map { OnHandHost.Mode(it.id, it.name, it.color) }

        override suspend fun pinnedModes(noteId: Long): Set<Long> =
            db.pinnedItemDao().modesWithOnHand(noteId.toString()).toSet()

        override suspend fun pin(noteId: Long, modeId: Long, title: String) {
            pinLock.withLock { PinnedRepository(context).pinOnHand(modeId, noteId, title) }
        }

        override suspend fun unpin(noteId: Long, modeId: Long) {
            pinLock.withLock { db.pinnedItemDao().deleteOnHand(noteId.toString(), modeId) }
        }

        override suspend fun notesDeleted(noteIds: Collection<Long>) {
            if (noteIds.isEmpty()) return
            for (part in noteIds.map { it.toString() }.chunked(500)) db.pinnedItemDao().deleteOnHandNotes(part)
        }
    }

    // Stare notatki launchera (rodzaj NOTE) → notatki OnHand; na karcie zostaje odnośnik ONHAND w tym samym
    // wierszu (tryb, kolejność i archiwum bez zmian). Powtarzalne: przy kolejnym starcie nie ma już czego
    // przenosić, a stara kopia zapasowa z notatkami NOTE zostanie przeniesiona po imporcie.
    // (Nie przez migrację Room: to dwie różne bazy, a migracja Room widzi tylko jedną.)
    suspend fun migrateLegacyNotes(context: Context) = migrationLock.withLock {
        val dao = LauncherDatabase.get(context).pinnedItemDao()
        val legacy = dao.getByKind(PinnedItemEntity.KIND_NOTE)
        if (legacy.isEmpty()) return@withLock
        val notes = OnHand.repository(context)
        for (item in legacy) {
            val text = item.text.orEmpty()
            // Tytuł launchera był często wycięty z pierwszej linii — wtedy w OnHand go nie powtarzamy
            // (OnHand sam pokaże pierwszą linię jako tytuł).
            val derived = text.lineSequence().firstOrNull().orEmpty().take(40)
            val title = if (item.title == derived) "" else item.title
            // Ta sama notatka już jest w OnHand (np. ponowny import starej kopii) → odnośnik do niej, bez duplikatu.
            val noteId = notes.findIdentical(title, text) ?: notes.create(title, text, createdAt = item.createdAt)
            dao.convertToOnHand(item.id, noteId.toString())
        }
    }
}
