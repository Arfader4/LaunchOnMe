package pl.rafal.onhand.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Notatka OnHand. Treść to zwykły tekst. archivedAt: null = aktywna, liczba = w archiwum od tej chwili
// (ten sam zwyczaj co pinned_items w launcherze).
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null,
) {
    // Tytuł do wyświetlenia: własny albo pierwsza linia treści (jak w Keep). Pusty = "Bez tytułu" w UI.
    val displayTitle: String
        get() = title.ifBlank { text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60).orEmpty() }
}

// Załącznik notatki (zdjęcie, plik). Kopia w filesDir/onhand/<noteId>/ — oryginał z "Udostępnij" bywa chwilowy.
// path jest względna do filesDir (np. "onhand/12/zdjecie.jpg"), żeby kopia zapasowa działała też na innym telefonie.
// Tabela powstaje od razu w wersji 1 bazy, żeby paczka On3 (załączniki) nie potrzebowała migracji.
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE, // usunięcie notatki usuwa jej wiersze załączników (jak ON DELETE CASCADE w SQL Server)
        ),
    ],
    indices = [Index("noteId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val path: String,
    val name: String,
    val mimeType: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

// Wynik zapytania z GROUP BY (Room wypełnia go po nazwach kolumn — jak Dapper w .NET).
data class AttachmentCount(val noteId: Long, val count: Int)
