package pl.rafal.stickonme

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

// Teksty StickOnMe spoza Compose (etykiety ramek i kształtów, komunikaty). Inicjowane przez klasę aplikacji
// launchera (moduł :studio nie ma własnej), a na wszelki wypadek także przez StudioActivity.
object StudioText {
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        val c = app ?: error("StudioText.init nie zostało wywołane")
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        val c = app ?: error("StudioText.init nie zostało wywołane")
        return c.resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf<Any>(count) else args))
    }
}
