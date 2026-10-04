package pl.rafal.contextlauncher

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

// Teksty z zasobów (res/values = angielski, res/values-pl = polski) tam, gdzie nie ma Compose
// ani Context pod ręką: etykiety enumów, opisy akcji, komunikaty z warstwy danych.
// Odpowiednik ResourceManager.GetString z .resx w .NET — język wybiera system według ustawień telefonu.
object AppText {
    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) app.getString(id) else app.getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        app.resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf<Any>(count) else args))
}
