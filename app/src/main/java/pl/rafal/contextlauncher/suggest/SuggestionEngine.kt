package pl.rafal.contextlauncher.suggest

import androidx.annotation.StringRes
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Silnik sugestii: czysta logika bez Androida, więc testowana zwykłym JUnitem (jak CardGrid).
// Dostaje reguły, aktualny czas i wydarzenia z kalendarza, a zwraca jedną sugestię albo nic.

// Reguła przypięta do trybu.
sealed interface Rule {
    // Pora dnia: w wybrane dni, od start do end (może przechodzić przez północ, np. 22:00–6:00).
    data class TimeWindow(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime) : Rule

    // Kalendarz: wydarzenie, którego tytuł zawiera słowo kluczowe, trwa albo zacznie się niedługo.
    data class CalendarKeyword(val keyword: String) : Rule

    // Połączone urządzenie Bluetooth (np. auto, słuchawki). Rozpoznajemy po adresie, nazwa jest do opisu.
    data class BluetoothDevice(val address: String, val name: String) : Rule

    // Sieć Wi-Fi, z którą telefon jest połączony (po nazwie SSID).
    data class WifiNetwork(val ssid: String) : Rule

    // Miejsce: środek + promień w metrach.
    data class Place(val latitude: Double, val longitude: Double, val radiusMeters: Int, val label: String) : Rule

    // Ładowanie: charging = true "podczas ładowania", false "bez ładowarki".
    data class Charging(val charging: Boolean) : Rule

    // Podłączone jakiekolwiek słuchawki: przewodowe, USB albo Bluetooth.
    data object Headphones : Rule

    // Bateria poniżej progu (w procentach), np. tryb oszczędny przy 20%.
    data class BatteryBelow(val percent: Int) : Rule
}

// Punkt na mapie.
data class GeoPoint(val latitude: Double, val longitude: Double)

// Stan otoczenia telefonu w chwili liczenia sugestii (poza czasem i kalendarzem).
data class Signals(
    val connectedBluetooth: Set<String> = emptySet(), // adresy połączonych urządzeń
    val wifiSsid: String? = null,
    val location: GeoPoint? = null,
    val charging: Boolean? = null,     // null = nie wiadomo (nie czytaliśmy)
    val headphones: Boolean = false,
    val batteryPercent: Int? = null,
)

data class ModeRule(val modeId: Long, val rule: Rule)

data class CalendarEvent(val title: String, val begin: LocalDateTime, val end: LocalDateTime)

sealed interface Suggestion {
    // Klucz do zapamiętania odrzucenia ("nie pokazuj mi tego przez chwilę").
    val key: String

    data class SwitchTo(val modeId: Long, val reason: String) : Suggestion {
        override val key get() = "switch:$modeId"
    }

    data class EndMode(val activeModeId: Long, val backToModeId: Long) : Suggestion {
        override val key get() = "end:$activeModeId"
    }
}

object SuggestionEngine {
    val CALENDAR_LOOKAHEAD: Duration = Duration.ofHours(3)  // jak wcześnie podpowiadać przed wydarzeniem
    val END_GRACE: Duration = Duration.ofMinutes(30)        // świeżo włączonego trybu nie proponujemy kończyć

    private val TimeFormat = DateTimeFormatter.ofPattern("H:mm")
    private val Weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val Weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val ShortDay = mapOf(
        DayOfWeek.MONDAY to (R.string.rule_day_mon to "pon."), DayOfWeek.TUESDAY to (R.string.rule_day_tue to "wt."),
        DayOfWeek.WEDNESDAY to (R.string.rule_day_wed to "śr."), DayOfWeek.THURSDAY to (R.string.rule_day_thu to "czw."),
        DayOfWeek.FRIDAY to (R.string.rule_day_fri to "pt."), DayOfWeek.SATURDAY to (R.string.rule_day_sat to "sob."),
        DayOfWeek.SUNDAY to (R.string.rule_day_sun to "niedz."),
    )

    // Tekst z zasobów (angielski / polski). W czystych testach JUnit AppText nie jest zainicjowany
    // (brak Androida) — wtedy używamy polskiego wzorca, identycznego z values-pl.
    private fun text(@StringRes id: Int, fallback: String, vararg args: Any): String =
        runCatching { AppText.get(id, *args) }.getOrElse { String.format(fallback, *args) }

    fun evaluate(
        rules: List<ModeRule>,
        activeModeId: Long?,
        previousModeId: Long?,
        activeSince: LocalDateTime?,
        now: LocalDateTime,
        events: List<CalendarEvent>,
        dismissed: Set<String>,
        signals: Signals = Signals(),
    ): Suggestion? {
        // Najważniejsza pasująca reguła AKTYWNEGO trybu. Inny tryb podpowiadamy tylko wtedy, gdy jego reguła
        // jest ważniejsza — inaczej dwa pasujące tryby (np. Praca w godzinach + auto przez Bluetooth)
        // przełączałyby się nawzajem co minutę.
        val activeBest = rules
            .filter { it.modeId == activeModeId && reasonIfMatches(it.rule, now, events, signals) != null }
            .minOfOrNull { priority(it.rule) }

        // 1. Czy pasuje jakiś INNY tryb? Konkretniejsze reguły mają pierwszeństwo (patrz priority).
        val switchTo = rules
            .filter { it.modeId != activeModeId }
            .mapNotNull { modeRule ->
                reasonIfMatches(modeRule.rule, now, events, signals)?.let { reason ->
                    Suggestion.SwitchTo(modeRule.modeId, reason) to priority(modeRule.rule)
                }
            }
            .filter { activeBest == null || it.second < activeBest }
            .sortedBy { it.second } // mniejsza liczba = ważniejsza reguła
            .map { it.first }
            .firstOrNull { it.key !in dismissed }
        if (switchTo != null) return switchTo

        // 2. Czy aktywny tryb "wygasł"? Ma reguły, żadna nie pasuje, i nie włączono go przed chwilą.
        if (activeModeId == null || previousModeId == null) return null
        val activeRules = rules.filter { it.modeId == activeModeId }
        if (activeRules.isEmpty()) return null // tryb bez reguł = użytkownik sam decyduje
        if (activeSince != null && Duration.between(activeSince, now) < END_GRACE) return null
        if (activeRules.any { reasonIfMatches(it.rule, now, events, signals) != null }) return null
        return Suggestion.EndMode(activeModeId, previousModeId).takeIf { it.key !in dismissed }
    }

    // Opis dla użytkownika, dlaczego reguła pasuje, albo null, gdy nie pasuje.
    // Kolejność ważności: wydarzenie w kalendarzu, auto/słuchawki, miejsce, sieć, na końcu pora dnia.
    // Niska bateria wygrywa ze wszystkim — to sytuacja "awaryjna".
    fun priority(rule: Rule): Int = when (rule) {
        is Rule.BatteryBelow -> -1
        is Rule.CalendarKeyword -> 0
        Rule.Headphones -> 1
        is Rule.BluetoothDevice -> 1
        is Rule.Place -> 2
        is Rule.WifiNetwork -> 3
        is Rule.Charging -> 3
        is Rule.TimeWindow -> 4
    }

    fun reasonIfMatches(
        rule: Rule,
        now: LocalDateTime,
        events: List<CalendarEvent>,
        signals: Signals = Signals(),
    ): String? = when (rule) {
        is Rule.TimeWindow -> if (inWindow(rule, now)) describe(rule) else null
        is Rule.CalendarKeyword -> events
            .firstOrNull { event ->
                event.title.contains(rule.keyword, ignoreCase = true) &&
                    event.end.isAfter(now) &&                          // jeszcze się nie skończyło
                    event.begin.isBefore(now.plus(CALENDAR_LOOKAHEAD)) // i zaczyna się w ciągu 3 godzin (albo już trwa)
            }
            ?.let { event ->
                if (event.begin.isAfter(now)) text(R.string.rule_reason_event_at, "„%1\$s” o %2\$s", event.title, event.begin.format(TimeFormat))
                else text(R.string.rule_reason_event_now, "trwa „%1\$s”", event.title)
            }
        is Rule.BluetoothDevice ->
            if (rule.address in signals.connectedBluetooth) text(R.string.rule_reason_bluetooth, "połączono z %1\$s", rule.name) else null
        is Rule.WifiNetwork ->
            if (signals.wifiSsid != null && signals.wifiSsid.equals(rule.ssid, ignoreCase = true)) text(R.string.rule_reason_wifi, "sieć %1\$s", rule.ssid) else null
        is Rule.Place -> signals.location
            ?.takeIf { distanceMeters(it, GeoPoint(rule.latitude, rule.longitude)) <= rule.radiusMeters }
            ?.let { text(R.string.rule_reason_place, "jesteś w miejscu „%1\$s”", rule.label) }
        is Rule.Charging -> when (signals.charging) {
            null -> null
            rule.charging -> if (rule.charging) text(R.string.rule_reason_charging, "telefon się ładuje") else text(R.string.rule_reason_unplugged, "telefon odłączony od ładowarki")
            else -> null
        }
        Rule.Headphones -> if (signals.headphones) text(R.string.rule_reason_headphones, "podłączono słuchawki") else null
        is Rule.BatteryBelow -> signals.batteryPercent
            ?.takeIf { it < rule.percent }
            ?.let { text(R.string.rule_reason_battery, "bateria %1\$d%%", it) }
    }

    // Odległość po powierzchni Ziemi (wzór haversine) w metrach.
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) * Math.sin(dLon / 2).let { it * it }
        return 2 * earthRadius * Math.asin(Math.sqrt(h))
    }

    fun inWindow(rule: Rule.TimeWindow, now: LocalDateTime): Boolean {
        val time = now.toLocalTime()
        return if (rule.start <= rule.end) {
            now.dayOfWeek in rule.days && time >= rule.start && time < rule.end
        } else {
            // Okno przez północ: 23:00 w piątek należy do piątku, 5:00 w sobotę też do piątku.
            (now.dayOfWeek in rule.days && time >= rule.start) ||
                (now.dayOfWeek.minus(1) in rule.days && time < rule.end)
        }
    }

    fun describe(rule: Rule): String = when (rule) {
        is Rule.TimeWindow -> text(
            R.string.rule_desc_time, "%1\$s %2\$s–%3\$s",
            describeDays(rule.days), rule.start.format(TimeFormat), rule.end.format(TimeFormat),
        )
        is Rule.CalendarKeyword -> text(R.string.rule_desc_calendar, "wydarzenie z „%1\$s” w kalendarzu", rule.keyword)
        is Rule.BluetoothDevice -> text(R.string.rule_desc_bluetooth, "połączenie z %1\$s (Bluetooth)", rule.name)
        is Rule.WifiNetwork -> text(R.string.rule_desc_wifi, "sieć Wi-Fi %1\$s", rule.ssid)
        is Rule.Place -> text(R.string.rule_desc_place, "miejsce „%1\$s” (%2\$d m)", rule.label, rule.radiusMeters)
        is Rule.Charging -> if (rule.charging) text(R.string.rule_desc_charging, "podczas ładowania") else text(R.string.rule_desc_unplugged, "bez ładowarki")
        Rule.Headphones -> text(R.string.rule_desc_headphones, "podłączone słuchawki")
        is Rule.BatteryBelow -> text(R.string.rule_desc_battery, "bateria poniżej %1\$d%%", rule.percent)
    }

    fun describeDays(days: Set<DayOfWeek>): String = when (days) {
        Weekdays + Weekend -> text(R.string.rule_days_every_day, "codziennie")
        Weekdays -> text(R.string.rule_days_weekdays, "pon.–pt.")
        Weekend -> text(R.string.rule_days_weekend, "weekend")
        else -> days.sorted().joinToString(", ") { day -> ShortDay.getValue(day).let { (id, fallback) -> text(id, fallback) } }
    }
}
