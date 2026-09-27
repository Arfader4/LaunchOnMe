package pl.rafal.contextlauncher.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class SuggestionEngineTest {

    private val work = 1L
    private val home = 2L
    private val travel = 3L

    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val workHours = ModeRule(work, Rule.TimeWindow(weekdays, LocalTime.of(8, 0), LocalTime.of(16, 0)))
    private val flight = ModeRule(travel, Rule.CalendarKeyword("lot"))

    // 28 września 2026 to poniedziałek.
    private val mondayMorning = LocalDateTime.of(2026, 9, 28, 9, 0)
    private val saturdayMorning = LocalDateTime.of(2026, 10, 3, 9, 0)

    private fun evaluate(
        rules: List<ModeRule>,
        now: LocalDateTime,
        active: Long? = home,
        previous: Long? = work,
        activeSince: LocalDateTime? = now.minusHours(5),
        events: List<CalendarEvent> = emptyList(),
        dismissed: Set<String> = emptySet(),
        signals: Signals = Signals(),
    ) = SuggestionEngine.evaluate(rules, active, previous, activeSince, now, events, dismissed, signals)

    @Test
    fun `w poniedziałek o 9 podpowiada tryb Praca`() {
        val result = evaluate(listOf(workHours), mondayMorning)
        assertEquals(Suggestion.SwitchTo(work, "pon.–pt. 8:00–16:00"), result)
    }

    @Test
    fun `w sobotę nie podpowiada trybu Praca`() {
        assertNull(evaluate(listOf(workHours), saturdayMorning))
    }

    @Test
    fun `lot za dwie godziny podpowiada Podróż`() {
        val event = CalendarEvent("Lot do Lizbony", mondayMorning.plusHours(2), mondayMorning.plusHours(5))
        val result = evaluate(listOf(flight), mondayMorning, events = listOf(event))
        assertEquals(Suggestion.SwitchTo(travel, "„Lot do Lizbony” o 11:00"), result)
    }

    @Test
    fun `lot za pięć godzin jeszcze nic nie podpowiada`() {
        val event = CalendarEvent("Lot do Lizbony", mondayMorning.plusHours(5), mondayMorning.plusHours(8))
        assertNull(evaluate(listOf(flight), mondayMorning, events = listOf(event)))
    }

    @Test
    fun `kalendarz ma pierwszeństwo przed porą dnia`() {
        val event = CalendarEvent("Lot do Lizbony", mondayMorning.plusHours(1), mondayMorning.plusHours(4))
        val result = evaluate(listOf(workHours, flight), mondayMorning, events = listOf(event))
        assertTrue(result is Suggestion.SwitchTo && result.modeId == travel)
    }

    @Test
    fun `odrzucona sugestia nie wraca`() {
        val result = evaluate(listOf(workHours), mondayMorning, dismissed = setOf("switch:$work"))
        assertNull(result)
    }

    @Test
    fun `po pracy proponuje powrót do poprzedniego trybu`() {
        val evening = mondayMorning.withHour(18)
        val result = evaluate(listOf(workHours), evening, active = work, previous = home)
        assertEquals(Suggestion.EndMode(work, home), result)
    }

    @Test
    fun `świeżo włączonego trybu nie proponuje kończyć`() {
        val evening = mondayMorning.withHour(18)
        val result = evaluate(listOf(workHours), evening, active = work, previous = home, activeSince = evening.minusMinutes(10))
        assertNull(result)
    }

    @Test
    fun `okno przez północ działa po obu stronach północy`() {
        val night = Rule.TimeWindow(setOf(DayOfWeek.FRIDAY), LocalTime.of(22, 0), LocalTime.of(6, 0))
        val friday23 = LocalDateTime.of(2026, 10, 2, 23, 0)
        val saturday5 = LocalDateTime.of(2026, 10, 3, 5, 0)
        val saturday7 = LocalDateTime.of(2026, 10, 3, 7, 0)
        assertTrue(SuggestionEngine.inWindow(night, friday23))
        assertTrue(SuggestionEngine.inWindow(night, saturday5))
        assertTrue(!SuggestionEngine.inWindow(night, saturday7))
    }

    @Test
    fun `opis dni jest po ludzku`() {
        assertEquals("pon.–pt.", SuggestionEngine.describeDays(weekdays))
        assertEquals("weekend", SuggestionEngine.describeDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
        assertEquals("pon., śr.", SuggestionEngine.describeDays(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)))
    }

    @Test
    fun `połączenie z autem podpowiada tryb i wygrywa z porą dnia`() {
        val car = ModeRule(travel, Rule.BluetoothDevice("AA:BB", "Auto"))
        val result = evaluate(listOf(workHours, car), mondayMorning, signals = Signals(connectedBluetooth = setOf("AA:BB")))
        assertEquals(Suggestion.SwitchTo(travel, "połączono z Auto"), result)
    }

    @Test
    fun `domowe Wi-Fi podpowiada tryb Dom bez względu na wielkość liter`() {
        val wifi = ModeRule(home, Rule.WifiNetwork("MojeWiFi"))
        val result = evaluate(listOf(wifi), saturdayMorning, active = work, signals = Signals(wifiSsid = "mojewifi"))
        assertEquals(Suggestion.SwitchTo(home, "sieć MojeWiFi"), result)
    }

    @Test
    fun `miejsce pasuje w promieniu, a 2 km dalej już nie`() {
        val office = Rule.Place(50.0614, 19.9366, 300, "Biuro") // Kraków, Rynek
        val near = Signals(location = GeoPoint(50.0620, 19.9370)) // ~70 m
        val far = Signals(location = GeoPoint(50.0800, 19.9366))  // ~2 km
        assertTrue(SuggestionEngine.reasonIfMatches(office, mondayMorning, emptyList(), near) != null)
        assertNull(SuggestionEngine.reasonIfMatches(office, mondayMorning, emptyList(), far))
    }

    @Test
    fun `odległość Kraków–Warszawa to około 250 km`() {
        val km = SuggestionEngine.distanceMeters(GeoPoint(50.0614, 19.9366), GeoPoint(52.2297, 21.0122)) / 1000
        assertTrue(km in 245.0..260.0)
    }

    @Test
    fun `ładowanie podpowiada tryb, a bez sygnału nie`() {
        val night = ModeRule(travel, Rule.Charging(true))
        assertEquals(Suggestion.SwitchTo(travel, "telefon się ładuje"), evaluate(listOf(night), saturdayMorning, signals = Signals(charging = true)))
        assertNull(evaluate(listOf(night), saturdayMorning, signals = Signals(charging = false)))
        assertNull(evaluate(listOf(night), saturdayMorning, signals = Signals(charging = null)))
    }

    @Test
    fun `niska bateria wygrywa z kalendarzem`() {
        val saver = ModeRule(work, Rule.BatteryBelow(20))
        val events = listOf(CalendarEvent("Lot do Lizbony", mondayMorning.plusHours(1), mondayMorning.plusHours(4)))
        val result = evaluate(listOf(flight, saver), mondayMorning, events = events, signals = Signals(batteryPercent = 12))
        assertEquals(Suggestion.SwitchTo(work, "bateria 12%"), result)
    }

    @Test
    fun `słuchawki pasują tylko gdy są podłączone`() {
        val music = ModeRule(travel, Rule.Headphones)
        assertTrue(evaluate(listOf(music), saturdayMorning, signals = Signals(headphones = true)) is Suggestion.SwitchTo)
        assertNull(evaluate(listOf(music), saturdayMorning, signals = Signals(headphones = false)))
    }

    @Test
    fun `dwa pasujące tryby nie przełączają się nawzajem`() {
        val car = ModeRule(travel, Rule.BluetoothDevice("AA:BB", "Auto"))
        val signals = Signals(connectedBluetooth = setOf("AA:BB"))
        // Aktywna Praca (pora dnia pasuje), auto ma ważniejszą regułę → podpowiedź auta.
        assertEquals(
            Suggestion.SwitchTo(travel, "połączono z Auto"),
            evaluate(listOf(workHours, car), mondayMorning, active = work, signals = signals),
        )
        // Aktywne auto: Praca też pasuje, ale jej reguła jest słabsza → nie wracamy do Pracy.
        assertNull(evaluate(listOf(workHours, car), mondayMorning, active = travel, previous = work, signals = signals))
    }
}
