package pl.rafal.onhand.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Osobna baza OnHand (onhand.db) — niezależna od launcher.db, więc moduł da się kiedyś wydzielić do osobnej aplikacji.
@Database(
    entities = [NoteEntity::class, AttachmentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OnHandDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        @Volatile
        private var instance: OnHandDatabase? = null

        // Singleton z podwójnym sprawdzeniem (jak lock w C#) — jedna baza na proces.
        fun get(context: Context): OnHandDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, OnHandDatabase::class.java, "onhand.db")
                    .build()
                    .also { instance = it }
            }
    }
}
