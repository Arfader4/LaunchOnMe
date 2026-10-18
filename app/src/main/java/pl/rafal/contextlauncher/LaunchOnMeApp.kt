package pl.rafal.contextlauncher

import android.app.Application

// Klasa aplikacji: tworzona przez system przed każdą aktywnością, usługą i odbiornikiem
// (jak Program.Main / punkt startowy w .NET). Tu podpinamy dostęp do tekstów w językach i most do OnHand.
class LaunchOnMeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppText.init(this)
        pl.rafal.stickonme.StudioText.init(this)
        // OnThemes (motywy): teksty modułu + gospodarz, przez którego moduł widzi tryby launchera.
        pl.rafal.onthemes.OnThemes.init(this, pl.rafal.contextlauncher.data.OnThemesBridge(this))
        pl.rafal.onhand.OnHandText.init(this)
        // OnHand: launcher jako gospodarz (tryby, przypinanie) + przeniesienie starych notatek do OnHand.
        pl.rafal.contextlauncher.data.OnHandBridge.install(this)
    }
}
