package pl.rafal.contextlauncher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// @Entity = tabela, jak klasa encji w Entity Framework. Room sam wygeneruje CREATE TABLE.
@Entity(tableName = "modes")
data class ModeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 0 = "nadaj sam" (IDENTITY)
    val name: String,
    val color: Long,          // kolor ARGB, np. 0xFFF0A844
    val sortOrder: Int = 0,
    val lastActiveAt: Long = 0, // czas ostatniego włączenia; najnowszy = aktywny tryb
    val icon: String? = null,     // klucz ikony, np. "work"; null = domyślna
    val palette: String? = null,  // schemat kolorów trybu, np. "ELEGANT"; null = domyślny z ustawień
    val accent: Long? = null,     // kolor główny trybu; null = kolor ze schematu
    val settings: String? = null, // ustawienia telefonu w tym trybie (JSON), np. {"dnd":true,"ringer":"VIBRATE"}
)

// Element karty trybu. Na razie tylko aplikacje; x, y, w, h przydadzą się przy swobodnym układzie.
@Entity(
    tableName = "card_items",
    foreignKeys = [
        ForeignKey(
            entity = ModeEntity::class,
            parentColumns = ["id"],
            childColumns = ["modeId"],
            onDelete = ForeignKey.CASCADE, // usunięcie trybu usuwa jego elementy (ON DELETE CASCADE)
        ),
    ],
    indices = [Index("modeId")],
)
data class CardItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeId: Long,
    val type: String = TYPE_APP,
    val packageName: String,
    val className: String,
    val userSerial: Long, // numer profilu użytkownika (osobisty / służbowy)
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val appWidgetId: Int? = null, // tylko dla widżetów: identyfikator nadany przez system (Int? = może być null)
    val widgetKind: String? = null, // tylko dla własnych widżetów: rodzaj, np. DUAL_CLOCK
    val config: String? = null,     // ustawienia własnego widżetu jako JSON, np. {"zone":"Europe/Lisbon"}
    // Strona karty (0 = pierwsza). defaultValue musi się zgadzać z migracją — Room porównuje schemat przy starcie.
    @ColumnInfo(defaultValue = "0") val page: Int = 0,
) {
    // companion object ≈ składowe static w C#.
    companion object {
        const val TYPE_APP = "APP"
        const val TYPE_WIDGET = "WIDGET"
        const val TYPE_CUSTOM = "CUSTOM"
        const val TYPE_SHORTCUT = "SHORTCUT" // skrót aplikacji: className = id skrótu (np. "czat z Kasią")
    }
}

// Element OnHand: plik, link albo notatka przypięta do trybu.
@Entity(
    tableName = "pinned_items",
    foreignKeys = [
        ForeignKey(
            entity = ModeEntity::class,
            parentColumns = ["id"],
            childColumns = ["modeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("modeId")],
)
data class PinnedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeId: Long,
    val kind: String,
    val title: String,
    val uri: String? = null,       // plik: ścieżka kopii w pamięci aplikacji; link: adres
    val text: String? = null,      // notatka: treść
    val mimeType: String? = null,  // plik: typ, np. application/pdf
    val createdAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null,  // null = aktywny, liczba = w archiwum od tej chwili
) {
    companion object {
        const val KIND_FILE = "FILE"
        const val KIND_LINK = "LINK"
        const val KIND_NOTE = "NOTE"
    }
}

// Folder aplikacji. parentId = null oznacza folder główny; inny folder jako rodzic = podfolder.
// Klucz obcy do tej samej tabeli (jak drzewo kategorii w SQL Server) z kaskadą: usunięcie folderu
// usuwa wszystkie jego podfoldery i ich zawartość.
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentId")],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val icon: String? = null,  // klucz symbolu z FolderIcon; null = miniatura z ikon aplikacji
    val color: Long? = null,   // kolor tła (ARGB); null = neutralne tło z motywu
)

// Aplikacja w folderze.
@Entity(
    tableName = "folder_apps",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("folderId")],
)
data class FolderAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val packageName: String,
    val className: String,
    val userSerial: Long,
)

// Reguła sugestii trybu. Parametry jako JSON, np. {"days":"1,2,3,4,5","start":"08:00","end":"16:00"}.
@Entity(
    tableName = "suggestion_rules",
    foreignKeys = [
        ForeignKey(
            entity = ModeEntity::class,
            parentColumns = ["id"],
            childColumns = ["modeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("modeId")],
)
data class SuggestionRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeId: Long,
    val type: String,
    val params: String,
) {
    companion object {
        const val TYPE_TIME = "TIME_WINDOW"
        const val TYPE_CALENDAR = "CALENDAR_KEYWORD"
        const val TYPE_BLUETOOTH = "BLUETOOTH_DEVICE"
        const val TYPE_WIFI = "WIFI_NETWORK"
        const val TYPE_PLACE = "PLACE"
        const val TYPE_CHARGING = "CHARGING"
        const val TYPE_HEADPHONES = "HEADPHONES"   // parametry: {}
        const val TYPE_BATTERY = "BATTERY_BELOW"
    }
}

// Ile razy aplikację uruchomiono w danym trybie. Klucz złożony z czterech kolumn (jak PRIMARY KEY (a, b, c, d) w SQL).
@Entity(
    tableName = "launch_stats",
    primaryKeys = ["modeId", "packageName", "className", "userSerial"],
    foreignKeys = [
        ForeignKey(
            entity = ModeEntity::class,
            parentColumns = ["id"],
            childColumns = ["modeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LaunchStatEntity(
    val modeId: Long,
    val packageName: String,
    val className: String,
    val userSerial: Long,
    val count: Int,
    val lastLaunched: Long,
)

// Blokada albo ukrycie aplikacji w trybie. Dotyczy całej aplikacji (pakiet + profil), nie pojedynczej aktywności.
// Unikalny indeks: w jednym trybie aplikacja jest albo zablokowana, albo ukryta (jak UNIQUE w SQL Server).
@Entity(
    tableName = "app_restrictions",
    foreignKeys = [
        ForeignKey(
            entity = ModeEntity::class,
            parentColumns = ["id"],
            childColumns = ["modeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["modeId", "packageName", "userSerial"], unique = true)],
)
data class AppRestrictionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeId: Long,
    val packageName: String,
    val userSerial: Long,
    val kind: String, // KIND_BLOCK albo KIND_HIDE
) {
    companion object {
        const val KIND_BLOCK = "BLOCK" // launcher pyta przed otwarciem
        const val KIND_HIDE = "HIDE"   // znika z szuflady w tym trybie
    }
}
