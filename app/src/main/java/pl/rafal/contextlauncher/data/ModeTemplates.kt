package pl.rafal.contextlauncher.data

import org.json.JSONObject
import pl.rafal.contextlauncher.data.db.SuggestionRuleEntity
import pl.rafal.contextlauncher.suggest.calendarRuleParams
import pl.rafal.contextlauncher.suggest.timeRuleParams
import java.time.DayOfWeek
import java.time.LocalTime

// Widżet w szablonie: rodzaj, ustawienia i rozmiar (domyślnie rozmiar rodzaju).
data class TemplateWidget(
    val kind: CustomWidgetKind,
    val config: String = "{}",
    val w: Int = kind.w,
    val h: Int = kind.h,
)

private fun note(text: String) = JSONObject().put("text", text).toString()

// Gotowy przepis na tryb: nazwa, kolor, jakie aplikacje dobrać, jakie widżety i reguły dodać.
data class ModeTemplate(
    val name: String,
    val icon: String, // klucz ikony trybu, np. "work"
    val color: Long,
    val description: String,
    val appHints: List<String>,
    val widgets: List<TemplateWidget> = emptyList(),
    val rules: List<Pair<String, String>> = emptyList(), // (typ reguły, parametry JSON)
)

object ModeTemplates {
    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val everyDay = DayOfWeek.values().toSet()

    // Strona główna: zegar, pogoda, tarcza trybów i 4 podstawowe aplikacje na dole.
    val Start = ModeTemplate(
        name = "Start",
        icon = "star",
        color = 0xFFF0A844,
        description = "Strona główna: godzina, pogoda, wydarzenie i budzik w jednym widżecie oraz tarcza wszystkich trybów.",
        appHints = listOf("dialer", "messaging", "camera", "com.android.chrome", "browser"),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.GLANCE, w = 8, h = 3),
            TemplateWidget(CustomWidgetKind.MODE_DIAL, w = 8, h = 6),
        ),
    )

    val Work = ModeTemplate(
        name = "Praca",
        icon = "work",
        color = 0xFF8FB2FF,
        description = "Plan dnia z kalendarza, lista priorytetów, poczta i dokumenty. Podpowiadany pon.–pt. 8–16.",
        appHints = listOf(
            "com.google.android.gm", "calendar", "com.google.android.apps.docs", "com.microsoft.teams",
            "com.slack", "outlook", "com.microsoft.office", "com.google.android.keep",
        ),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.TODAY, w = 4, h = 5),
            TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of("Priorytety na dziś"), w = 4, h = 5),
            TemplateWidget(CustomWidgetKind.HANDY, w = 8, h = 3),
        ),
        rules = listOf(
            SuggestionRuleEntity.TYPE_TIME to timeRuleParams(weekdays, LocalTime.of(8, 0), LocalTime.of(16, 0)),
        ),
    )

    val Home = ModeTemplate(
        name = "Dom",
        icon = "home",
        color = 0xFF7FD6AE,
        description = "Ulubione kontakty, lista zakupów, szybkie przełączniki oraz filmy i muzyka. Podpowiadany wieczorem.",
        appHints = listOf(
            "youtube", "netflix", "spotify", "com.google.android.apps.chromecast", "smartthings",
            "com.google.android.apps.photos", "primevideo", "disney",
        ),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.CONTACTS, w = 8, h = 2),
            TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of("Zakupy", "mleko", "chleb"), w = 4, h = 4),
            TemplateWidget(CustomWidgetKind.QUICK_TOGGLES, w = 4, h = 4),
        ),
        rules = listOf(
            SuggestionRuleEntity.TYPE_TIME to timeRuleParams(everyDay, LocalTime.of(18, 0), LocalTime.of(23, 0)),
        ),
    )

    val Travel = ModeTemplate(
        name = "Podróż",
        icon = "travel",
        color = 0xFFF0A844,
        description = "Mapy, bilety i noclegi, dwa zegary, odliczanie do wyjazdu, lista do spakowania i Pod ręką. Podpowiadany, gdy w kalendarzu jest lot albo pociąg.",
        appHints = listOf(
            "com.google.android.apps.maps", "booking", "intercity", "translate", "uber", "bolt", "airbnb", "weather",
        ),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.DUAL_CLOCK, JSONObject().put("zone", "Europe/London").toString(), w = 4, h = 3),
            TemplateWidget(CustomWidgetKind.COUNTDOWN, Countdown.of("Wyjazd"), w = 4, h = 3),
            TemplateWidget(CustomWidgetKind.HANDY, w = 4, h = 4),
            TemplateWidget(
                CustomWidgetKind.CHECKLIST,
                Checklist.of("Spakować", "dowód / paszport", "ładowarka i powerbank", "leki", "słuchawki"),
                w = 4, h = 4,
            ),
        ),
        rules = listOf(
            SuggestionRuleEntity.TYPE_CALENDAR to calendarRuleParams("lot"),
            SuggestionRuleEntity.TYPE_CALENDAR to calendarRuleParams("pociąg"),
        ),
    )

    val Study = ModeTemplate(
        name = "Studia",
        icon = "study",
        color = 0xFFB9A5FF,
        description = "Plan zajęć, odliczanie do sesji, lista na zjazd i materiały. Podpowiadany w weekendy 8–16.",
        appHints = listOf(
            "classroom", "com.google.android.apps.docs", "keep", "teams", "moodle", "onenote", "notion", "pdf",
        ),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.TODAY, w = 4, h = 4),
            TemplateWidget(CustomWidgetKind.COUNTDOWN, Countdown.of("Sesja"), w = 4, h = 4),
            TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of("Na najbliższy zjazd"), w = 4, h = 4),
            TemplateWidget(CustomWidgetKind.HANDY, w = 4, h = 4),
        ),
        rules = listOf(
            SuggestionRuleEntity.TYPE_TIME to timeRuleParams(weekend, LocalTime.of(8, 0), LocalTime.of(16, 0)),
        ),
    )

    val Sport = ModeTemplate(
        name = "Trening",
        icon = "sport",
        color = 0xFFFF9A7F,
        description = "Szybkie przełączniki, pogoda, plan treningu, muzyka i zdrowie. Bez reguł: włączasz go sam.",
        appHints = listOf("strava", "fitness", "shealth", "health", "garmin", "spotify"),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.QUICK_TOGGLES, w = 8, h = 2),
            TemplateWidget(CustomWidgetKind.WEATHER, w = 4, h = 4),
            TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of("Plan treningu", "rozgrzewka", "rozciąganie"), w = 4, h = 4),
        ),
    )

    val All = listOf(Start, Work, Home, Travel, Study, Sport)

    // Gdy ktoś pominie kreator: jeden prosty tryb, żeby launcher miał co pokazać.
    val Default = ModeTemplate(name = "Codzienny", icon = "sun", color = 0xFFF0A844, description = "", appHints = emptyList())

    fun pickApps(installed: List<AppInfo>, template: ModeTemplate): List<AppInfo> =
        matchApps(installed, template.appHints, { it.packageName }, { it.label })

    // Uniwersalne aplikacje, którymi dopełniamy kartę, gdy w telefonie brakuje tych z szablonu.
    // Dzięki temu każda karta zaczyna pełna, a nie z dziurami.
    private val FallbackHints = listOf(
        "dialer", "messaging", "camera", "com.android.chrome", "com.sec.android.app.sbrowser",
        "com.google.android.apps.photos", "gallery", "calendar", "clock", "com.google.android.gm",
        "myfiles", "files", "contacts", "maps", "youtube", "settings",
    )

    // Kandydaci do wypełnienia karty: najpierw aplikacje szablonu, potem uniwersalne (bez powtórzeń).
    fun pickAppsForFill(installed: List<AppInfo>, template: ModeTemplate): List<AppInfo> =
        matchApps(installed, template.appHints + FallbackHints, { it.packageName }, { it.label }, limit = 24)
}
