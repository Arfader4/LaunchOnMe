package pl.rafal.contextlauncher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ModeEntity::class,
        CardItemEntity::class,
        PinnedItemEntity::class,
        FolderEntity::class,
        FolderAppEntity::class,
        SuggestionRuleEntity::class,
        LaunchStatEntity::class,
        AppRestrictionEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun modeDao(): ModeDao
    abstract fun cardItemDao(): CardItemDao
    abstract fun pinnedItemDao(): PinnedItemDao
    abstract fun folderDao(): FolderDao
    abstract fun suggestionDao(): SuggestionDao
    abstract fun appRestrictionDao(): AppRestrictionDao

    companion object {
        // Jedna instancja bazy na całą aplikację (singleton z podwójnym sprawdzeniem, jak lock w C#).
        @Volatile
        private var instance: LauncherDatabase? = null

        fun get(context: Context): LauncherDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LauncherDatabase::class.java,
                    "launcher.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .build()
                    .also { instance = it }
            }
    }
}

// Migracja schematu, jak skrypt ALTER/UPDATE przy wdrożeniu nowej wersji bazy.
// Wersja 1 liczyła pozycje w pełnych ikonach (4 kolumny), wersja 2 w drobnej siatce 8×12,
// gdzie ikona zajmuje 2×2 komórki. Bez migracji Room przerwałby start aplikacji.
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE card_items SET x = x * 2, y = y * 2, w = 2, h = 2")
    }
}

// Wersja 3: widżety potrzebują identyfikatora nadanego przez system.
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE card_items ADD COLUMN appWidgetId INTEGER")
    }
}

// Wersja 4: nowa tabela "Pod ręką". Room po migracji porównuje schemat z encją,
// więc kolumny, typy i NOT NULL muszą dokładnie odpowiadać PinnedItemEntity.
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pinned_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                modeId INTEGER NOT NULL,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                uri TEXT,
                text TEXT,
                mimeType TEXT,
                createdAt INTEGER NOT NULL,
                archivedAt INTEGER,
                FOREIGN KEY(modeId) REFERENCES modes(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pinned_items_modeId ON pinned_items (modeId)")
    }
}

// Wersja 5: własne widżety launchera (rodzaj + ustawienia w JSON).
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE card_items ADD COLUMN widgetKind TEXT")
        db.execSQL("ALTER TABLE card_items ADD COLUMN config TEXT")
    }
}

// Wersja 6: foldery aplikacji z podfolderami.
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                parentId INTEGER,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(parentId) REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_parentId ON folders (parentId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folder_apps (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                folderId INTEGER NOT NULL,
                packageName TEXT NOT NULL,
                className TEXT NOT NULL,
                userSerial INTEGER NOT NULL,
                FOREIGN KEY(folderId) REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_folder_apps_folderId ON folder_apps (folderId)")
    }
}

// Wersja 7: reguły sugestii i liczniki uruchomień.
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS suggestion_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                modeId INTEGER NOT NULL,
                type TEXT NOT NULL,
                params TEXT NOT NULL,
                FOREIGN KEY(modeId) REFERENCES modes(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_suggestion_rules_modeId ON suggestion_rules (modeId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS launch_stats (
                modeId INTEGER NOT NULL,
                packageName TEXT NOT NULL,
                className TEXT NOT NULL,
                userSerial INTEGER NOT NULL,
                count INTEGER NOT NULL,
                lastLaunched INTEGER NOT NULL,
                PRIMARY KEY(modeId, packageName, className, userSerial),
                FOREIGN KEY(modeId) REFERENCES modes(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}

// Wersja 8: ikona i motyw trybu. Kolumny mogą być puste (null = wartość domyślna), więc wystarczy ALTER.
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE modes ADD COLUMN icon TEXT")
        db.execSQL("ALTER TABLE modes ADD COLUMN palette TEXT")
        db.execSQL("ALTER TABLE modes ADD COLUMN accent INTEGER")
    }
}

// Wersja 9: ustawienia telefonu przypisane do trybu (JSON).
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE modes ADD COLUMN settings TEXT")
    }
}

// Wersja 10: blokowanie i ukrywanie aplikacji w trybach. Nazwa indeksu musi być dokładnie taka,
// jaką nadaje Room (index_<tabela>_<kolumny>), inaczej walidacja schematu po migracji się nie powiedzie.
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_restrictions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                modeId INTEGER NOT NULL,
                packageName TEXT NOT NULL,
                userSerial INTEGER NOT NULL,
                kind TEXT NOT NULL,
                FOREIGN KEY(modeId) REFERENCES modes(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_app_restrictions_modeId_packageName_userSerial " +
                "ON app_restrictions (modeId, packageName, userSerial)",
        )
    }
}

// Wersja 11: wygląd folderu (symbol i kolor). Obie kolumny mogą być puste, więc wystarczy ALTER.
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN icon TEXT")
        db.execSQL("ALTER TABLE folders ADD COLUMN color INTEGER")
    }
}

// Wersja 12: strony karty. Istniejące elementy trafiają na pierwszą stronę (0).
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE card_items ADD COLUMN page INTEGER NOT NULL DEFAULT 0")
    }
}
