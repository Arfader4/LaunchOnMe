package pl.rafal.onhand.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    // (archivedAt IS NOT NULL) daje w SQLite 1/0 — porównujemy z Boolean (Room zapisuje go jako 1/0).
    @Query("SELECT * FROM notes WHERE (archivedAt IS NOT NULL) = :archived ORDER BY updatedAt DESC")
    fun observe(archived: Boolean): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    // Kilka notatek naraz (zaznaczanie wielu): WHERE id IN (...) — Room sam rozwinie listę w parametry.
    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    // Dopisanie na końcu treści (Udostępnij → Dopisz do…). || to łączenie tekstów w SQLite (jak + w T-SQL).
    @Query("UPDATE notes SET text = CASE WHEN text = '' THEN :added ELSE text || :separator || :added END, updatedAt = :time WHERE id = :id")
    suspend fun appendText(id: Long, added: String, separator: String, time: Long)

    @Query("UPDATE notes SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("UPDATE notes SET title = :title, text = :text, updatedAt = :time WHERE id = :id")
    suspend fun update(id: Long, title: String, text: String, time: Long)

    // time = null przywraca z archiwum.
    @Query("UPDATE notes SET archivedAt = :time WHERE id = :id")
    suspend fun setArchived(id: Long, time: Long?)

    @Query("UPDATE notes SET archivedAt = :time WHERE id IN (:ids)")
    suspend fun setArchivedMany(ids: List<Long>, time: Long?)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    // Import kopii zapasowej: wszystko od nowa (załączniki znikną kaskadą).
    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    // Wszystkie notatki z tytułem i treścią — dla launchera (żywe tytuły odnośników na kartach).
    @Query("SELECT * FROM notes")
    fun observeAll(): Flow<List<NoteEntity>>
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt")
    fun observeFor(noteId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt")
    suspend fun getFor(noteId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: Long): AttachmentEntity?

    // Liczba załączników każdej notatki — znaczek 📎 na liście (GROUP BY jak w SQL Server).
    @Query("SELECT noteId, COUNT(*) AS count FROM attachments GROUP BY noteId")
    fun observeCounts(): Flow<List<AttachmentCount>>

    @Query("SELECT COUNT(*) FROM attachments WHERE noteId = :noteId")
    suspend fun countFor(noteId: Long): Int

    @Insert
    suspend fun insert(item: AttachmentEntity): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<AttachmentEntity>
}
