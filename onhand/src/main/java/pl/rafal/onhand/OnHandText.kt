package pl.rafal.onhand

import android.content.Context
import androidx.annotation.StringRes

// Teksty OnHand spoza Compose (komunikaty, nazwy plików eksportu). Inicjowane przez klasę aplikacji
// launchera (moduł :onhand nie ma własnej), a na wszelki wypadek także przez OnHandActivity — jak StudioText.
object OnHandText {
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        val c = app ?: error("OnHandText.init nie zostało wywołane")
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }
}
