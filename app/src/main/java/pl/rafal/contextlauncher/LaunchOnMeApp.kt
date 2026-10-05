package pl.rafal.contextlauncher

import android.app.Application

// Klasa aplikacji: tworzona przez system przed każdą aktywnością, usługą i odbiornikiem
// (jak Program.Main / punkt startowy w .NET). Tu tylko podpinamy dostęp do tekstów w językach.
class LaunchOnMeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppText.init(this)
        pl.rafal.stickonme.StudioText.init(this)
        pl.rafal.onhand.OnHandText.init(this)
    }
}
