package pl.rafal.onthemes

import android.content.Context
import androidx.annotation.StringRes

// Teksty OnThemes spoza Compose (nazwy motywów, etykiety enumów). Inicjowane przez klasę aplikacji launchera
// (moduł nie ma własnej), a na wszelki wypadek także przez OnThemesActivity. Jak StudioText w StickOnMe.
object OnThemesText {
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        val c = app ?: error("OnThemesText.init nie zostało wywołane")
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }
}
