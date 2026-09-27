package pl.rafal.contextlauncher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.rafal.contextlauncher.suggest.CalendarEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Najbliższe wydarzenie dla widżetu "W skrócie" (z id, żeby dotknięcie otworzyło właśnie je).
data class GlanceEvent(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)

// Odczyt wydarzeń z systemowego kalendarza (wszystkie konta w telefonie, np. Google).
class CalendarReader(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    // Instances = wystąpienia wydarzeń w przedziale czasu (z rozwinięciem wydarzeń cyklicznych).
    suspend fun events(from: Instant, to: Instant): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, from.toEpochMilli())
            ContentUris.appendId(this, to.toEpochMilli())
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
        )

        runCatching {
            context.contentResolver.query(uri, columns, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                CalendarEvent(
                                    title = cursor.getString(0).orEmpty(),
                                    begin = cursor.getLong(1).toLocalDateTime(),
                                    end = cursor.getLong(2).toLocalDateTime(),
                                ),
                            )
                        }
                    }
                }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun Long.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

    // Wystąpienia z id (do otwarcia konkretnego wydarzenia) — dla widżetów "W skrócie" i "Dziś".
    suspend fun instances(fromMs: Long, toMs: Long): List<GlanceEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, fromMs)
            ContentUris.appendId(this, toMs)
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        runCatching {
            context.contentResolver.query(uri, columns, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(GlanceEvent(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getLong(3), c.getInt(4) == 1))
                        }
                    }
                }.orEmpty()
        }.getOrDefault(emptyList())
    }

    // Najbliższe wydarzenie w ciągu doby: trwające albo nadchodzące. Całodniowe tylko wtedy, gdy nie ma godzinowych.
    suspend fun nextEvent(nowMs: Long): GlanceEvent? {
        val events = instances(nowMs - 12 * HOUR, nowMs + 24 * HOUR)
        val today = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
        return events.firstOrNull { !it.allDay && it.end > nowMs }
            ?: events.firstOrNull { it.allDay && it.day() == today }
    }

    // Widżety W skrócie i Dziś z jednego zapytania: najbliższe wydarzenie + plan na dziś i jutro.
    suspend fun glance(nowMs: Long): Pair<GlanceEvent?, List<GlanceEvent>> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val endOfTomorrow = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = instances(nowMs - 12 * HOUR, endOfTomorrow)
        val next = events.firstOrNull { !it.allDay && it.end > nowMs && it.begin < nowMs + 24 * HOUR }
            ?: events.firstOrNull { it.allDay && it.day() == today }
        val agenda = events
            .filter { if (it.allDay) it.day() >= today else it.end > nowMs }
            .sortedWith(compareBy<GlanceEvent>({ it.day(zone) }, { !it.allDay }, { it.begin }))
        return next to agenda
    }

    // Plan na dziś i jutro: bez tego, co już się skończyło.
    suspend fun agenda(nowMs: Long): List<GlanceEvent> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val endOfTomorrow = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        return instances(nowMs - 12 * HOUR, endOfTomorrow)
            .filter { if (it.allDay) it.day() >= today else it.end > nowMs }
            .sortedWith(compareBy<GlanceEvent>({ it.day(zone) }, { !it.allDay }, { it.begin })) // całodniowe na początku dnia
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}

// Dzień wydarzenia. Całodniowe mają początek o północy UTC, więc liczymy je w UTC, żeby nie "przeskoczyły" na poprzedni dzień.
fun GlanceEvent.day(zone: ZoneId = ZoneId.systemDefault()): java.time.LocalDate =
    Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneId.of("UTC") else zone).toLocalDate()
