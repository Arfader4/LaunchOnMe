package pl.rafal.stickonme.app

import android.app.Application
import pl.rafal.stickonme.StudioText

// Klasa aplikacji StickOnMe (jak Program/App w .NET — startuje przed pierwszym ekranem).
// Teksty spoza Compose (etykiety ramek, komunikaty) potrzebują kontekstu aplikacji.
class StickOnMeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StudioText.init(this)
    }
}
