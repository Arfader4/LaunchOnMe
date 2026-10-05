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

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("UPDATE notes SET title = :title, text = :text, updatedAt = :time WHERE id = :id")
    suspend fun update(id: Long, title: String, text: String, time: Long)

    // time = null przywraca z archiwum.
    @Query("UPDATE notes SET archivedAt = :time WHERE id = :id")
    suspend fun setArchived(id: Long, time: Long?)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt")
    fun observeFor(noteId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt")
    suspend fun getFor(noteId: Long): List<AttachmentEntity>

    @Insert
    suspend fun insert(item: AttachmentEntity): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)
}
