package pl.rafal.contextlauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// DAO to interfejs z zapytaniami SQL; implementację generuje Room podczas kompilacji.
// Flow = strumień, który emituje nowy wynik za każdym razem, gdy tabela się zmieni.

@Dao
interface ModeDao {
    @Query("SELECT * FROM modes ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ModeEntity>>

    @Insert
    suspend fun insert(mode: ModeEntity): Long

    @Query("UPDATE modes SET lastActiveAt = :time WHERE id = :id")
    suspend fun markActive(id: Long, time: Long)

    @Query("UPDATE modes SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE modes SET icon = :icon, color = :color, palette = :palette, accent = :accent WHERE id = :id")
    suspend fun updateAppearance(id: Long, icon: String?, color: Long, palette: String?, accent: Long?)

    @Query("UPDATE modes SET settings = :settings WHERE id = :id")
    suspend fun updateSettings(id: Long, settings: String?)

    // Jednorazowy odczyt (dla kafelka w szybkich ustawieniach, który nie obserwuje zmian).
    @Query("SELECT * FROM modes ORDER BY sortOrder, id")
    suspend fun getAll(): List<ModeEntity>

    @Query("DELETE FROM modes WHERE id = :id")
    suspend fun delete(id: Long) // elementy karty, Pod ręką i reguły znikną kaskadowo

    @Query("DELETE FROM modes")
    suspend fun deleteAll()
}

@Dao
interface CardItemDao {
    @Query("SELECT * FROM card_items WHERE modeId = :modeId ORDER BY y, x")
    fun observeForMode(modeId: Long): Flow<List<CardItemEntity>>

    // Jednorazowy odczyt (bez obserwowania), gdy trzeba policzyć wolne miejsce.
    @Query("SELECT * FROM card_items WHERE modeId = :modeId")
    suspend fun getForMode(modeId: Long): List<CardItemEntity>

    @Insert
    suspend fun insert(item: CardItemEntity): Long

    @Query("UPDATE card_items SET x = :x, y = :y WHERE id = :id")
    suspend fun updatePosition(id: Long, x: Int, y: Int)

    @Query("SELECT * FROM card_items")
    suspend fun getAll(): List<CardItemEntity>

    @Query("UPDATE card_items SET w = :w, h = :h WHERE id = :id")
    suspend fun updateSize(id: Long, w: Int, h: Int)

    @Query("UPDATE card_items SET config = :config WHERE id = :id")
    suspend fun updateConfig(id: Long, config: String)

    @Query("DELETE FROM card_items WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PinnedItemDao {
    @Query("SELECT * FROM pinned_items WHERE modeId = :modeId ORDER BY createdAt DESC")
    fun observeForMode(modeId: Long): Flow<List<PinnedItemEntity>>

    @Insert
    suspend fun insert(item: PinnedItemEntity): Long

    // time = null przywraca z archiwum.
    @Query("SELECT * FROM pinned_items")
    suspend fun getAll(): List<PinnedItemEntity>

    @Query("UPDATE pinned_items SET archivedAt = :time WHERE id = :id")
    suspend fun setArchived(id: Long, time: Long?)

    @Query("UPDATE pinned_items SET title = :title, text = :text WHERE id = :id")
    suspend fun updateNote(id: Long, title: String, text: String?)

    @Query("DELETE FROM pinned_items WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FolderDao {
    // Folderów i ich aplikacji jest mało, więc trzymamy w pamięci całe drzewo i składamy je w Kotlinie.
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folder_apps")
    fun observeApps(): Flow<List<FolderAppEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getFolders(): List<FolderEntity>

    @Query("SELECT * FROM folder_apps")
    suspend fun getApps(): List<FolderAppEntity>

    @Query("DELETE FROM folders")
    suspend fun deleteAll() // aplikacje w folderach znikną kaskadowo

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Insert
    suspend fun insertApps(apps: List<FolderAppEntity>)

    @Query("UPDATE folder_apps SET folderId = :folderId WHERE id = :id")
    suspend fun moveApp(id: Long, folderId: Long)

    @Query("DELETE FROM folder_apps WHERE id = :id")
    suspend fun deleteApp(id: Long)
}

@Dao
interface SuggestionDao {
    @Query("SELECT * FROM suggestion_rules")
    fun observeRules(): Flow<List<SuggestionRuleEntity>>

    @Query("SELECT * FROM suggestion_rules")
    suspend fun getRules(): List<SuggestionRuleEntity>

    @Query("DELETE FROM launch_stats")
    suspend fun clearStats()

    @Insert
    suspend fun insertRule(rule: SuggestionRuleEntity): Long

    @Query("DELETE FROM suggestion_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    // "Upsert" w dwóch krokach (odpowiednik MERGE z SQL Server): wstaw zero, jeśli wiersza nie ma...
    @Query(
        """
        INSERT OR IGNORE INTO launch_stats (modeId, packageName, className, userSerial, count, lastLaunched)
        VALUES (:modeId, :packageName, :className, :userSerial, 0, :time)
        """,
    )
    suspend fun ensureStat(modeId: Long, packageName: String, className: String, userSerial: Long, time: Long)

    // ...a potem zwiększ licznik.
    @Query(
        """
        UPDATE launch_stats SET count = count + 1, lastLaunched = :time
        WHERE modeId = :modeId AND packageName = :packageName AND className = :className AND userSerial = :userSerial
        """,
    )
    suspend fun incrementStat(modeId: Long, packageName: String, className: String, userSerial: Long, time: Long)

    // @Transaction: oba kroki w jednej transakcji (BEGIN TRAN ... COMMIT).
    @Transaction
    suspend fun recordLaunch(modeId: Long, packageName: String, className: String, userSerial: Long, time: Long) {
        ensureStat(modeId, packageName, className, userSerial, time)
        incrementStat(modeId, packageName, className, userSerial, time)
    }

    @Query("SELECT * FROM launch_stats WHERE modeId = :modeId ORDER BY count DESC, lastLaunched DESC LIMIT :limit")
    fun observeTop(modeId: Long, limit: Int): Flow<List<LaunchStatEntity>>
}

@Dao
interface AppRestrictionDao {
    @Query("SELECT * FROM app_restrictions")
    fun observeAll(): Flow<List<AppRestrictionEntity>>

    @Query("SELECT * FROM app_restrictions")
    suspend fun getAll(): List<AppRestrictionEntity>

    // REPLACE: nowy wpis dla tej samej aplikacji w tym trybie zastępuje stary (blokada ↔ ukrycie),
    // jak MERGE / "upsert" w SQL Server — dzięki unikalnemu indeksowi.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<AppRestrictionEntity>)

    @Query("DELETE FROM app_restrictions WHERE modeId = :modeId AND packageName = :packageName AND userSerial = :userSerial")
    suspend fun delete(modeId: Long, packageName: String, userSerial: Long)
}
