package pl.rafal.onhand

import android.content.Context
import android.content.Intent
import pl.rafal.onhand.data.NoteRepository

// Wejście do OnHand dla launchera (wzór: obiekt StickOnMe w :studio). Launcher woła te funkcje,
// a OnHand nic nie wie o launcherze — jak publiczne API biblioteki klas w .NET.
object OnHand {
    const val EXTRA_NOTE_ID = "pl.rafal.onhand.NOTE_ID"     // otwórz od razu tę notatkę; też wynik wyboru
    const val EXTRA_PICK = "pl.rafal.onhand.PICK"           // wybór notatki — wynik (EXTRA_NOTE_ID) wraca do launchera
    const val EXTRA_NEW_TEXT = "pl.rafal.onhand.NEW_TEXT"   // nowa notatka z gotową treścią
    const val EXTRA_NEW_TITLE = "pl.rafal.onhand.NEW_TITLE"

    fun appIntent(context: Context): Intent = Intent(context, OnHandActivity::class.java)

    fun openIntent(context: Context, noteId: Long): Intent = appIntent(context).putExtra(EXTRA_NOTE_ID, noteId)

    fun pickIntent(context: Context): Intent = appIntent(context).putExtra(EXTRA_PICK, true)

    fun newIntent(context: Context, text: String, title: String? = null): Intent =
        appIntent(context).putExtra(EXTRA_NEW_TEXT, text).putExtra(EXTRA_NEW_TITLE, title)

    // -1 = brak wyniku (np. wybór anulowany).
    fun resultNoteId(data: Intent?): Long? = data?.getLongExtra(EXTRA_NOTE_ID, -1L)?.takeIf { it > 0 }

    fun repository(context: Context): NoteRepository = NoteRepository(context.applicationContext)

    // Gospodarz (launcher). @Volatile — zapis z wątku głównego, odczyt z korutyn w tle (jak volatile w C#).
    // null = OnHand działa sam, bez przypinania do trybów.
    @Volatile
    var host: OnHandHost? = null
        private set

    fun registerHost(host: OnHandHost) {
        this.host = host
    }
}
