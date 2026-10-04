package pl.rafal.contextlauncher.system

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.GlanceEvent

// Dokąd prowadzą poszczególne części widżetu "W skrócie".
// Zwracamy gotowe Intenty; uruchamia je ekran (i łapie błąd, gdy telefon nie ma takiej aplikacji).
object GlanceActions {

    // Godzina → aplikacja Zegar. Nie ma na to standardowej akcji, więc szukamy aplikacji,
    // która obsługuje "pokaż budziki", i otwieramy jej ekran główny.
    fun clock(context: Context): Intent {
        val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        val pkg = context.packageManager.resolveActivity(alarms, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return pkg?.let { context.packageManager.getLaunchIntentForPackage(it) } ?: alarms
    }

    fun alarms(): Intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)

    // Data → kalendarz otwarty na danym dniu (content://com.android.calendar/time/<ms>).
    fun calendarAt(millis: Long): Intent {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, millis) }.build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun event(event: GlanceEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)

    // Pogoda → pierwsza zainstalowana znana aplikacja pogodowa, a gdy żadnej nie ma — wyszukiwarka.
    fun weather(context: Context): Intent =
        WEATHER_APPS.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
            ?: Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", context.getString(R.string.sys_weather_search_query))

    private val WEATHER_APPS = listOf(
        "com.sec.android.daemonapp",          // Samsung Pogoda
        "com.google.android.apps.weather",    // Google Pogoda (Pixel)
        "com.accuweather.android",
        "com.weather.Weather",
        "com.miui.weather2",
        "com.yahoo.mobile.client.android.weather",
    )

    // --- Widżet "Dziś" ---

    fun newEvent(): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)

    // --- Ulubione kontakty (bez uprawnień do dzwonienia: otwieramy wybieranie z wpisanym numerem) ---

    fun dial(number: String): Intent = Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", number, null))

    fun sms(number: String): Intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.fromParts("smsto", number, null))

    fun contact(lookupUri: android.net.Uri): Intent = Intent(Intent.ACTION_VIEW, lookupUri)

    fun contactsApp(): Intent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
}
