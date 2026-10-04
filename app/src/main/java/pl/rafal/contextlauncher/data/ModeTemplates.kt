package pl.rafal.contextlauncher.data

import org.json.JSONObject
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
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
    val Start: ModeTemplate get() = ModeTemplate(
        name = AppText.get(R.string.tpl_start_name),
        icon = "star",
        color = 0xFFF0A844,
        description = AppText.get(R.string.tpl_start_desc),
        appHints = listOf("dialer", "messaging", "camera", "com.android.chrome", "browser"),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.GLANCE, w = 8, h = 3),
            TemplateWidget(CustomWidgetKind.MODE_DIAL, w = 8, h = 6),
        ),
    )

    val Work: ModeTemplate get() = ModeTemplate(
        name = AppText.get(R.string.tpl_work_name),
        icon = "work",
        color = 0xFF8FB2FF,
        description = AppText.get(R.string.tpl_work_desc),
        appHints = listOf(
            "com.google.android.gm", "calendar", "com.google.android.apps.docs", "com.microsoft.teams",
            "com.slack", "outlook", "com.microsoft.office", "com.google.android.keep",
        ),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.TODAY, w = 4, h = 5),
            TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of(AppText.get(R.string.tpl_work_checklist)), w = 4, h = 5),
            TemplateWidget(CustomWidgetKind.HANDY, w = 8, h = 3),
        ),
        rules = listOf(
            SuggestionRuleEntity.TYPE_TIME to timeRuleParams(weekdays, LocalTime.of(8, 0), LocalTime.of(16, 0)),
        ),
    )

    val Home: ModeTemplate get() = ModeTemplate(
        name = AppText.get(R.string.tpl_home_name),
        icon = "home",
        color = 0xFF7FD6AE,
        description = AppText.get(R.string.tpl_home_desc),
        appHints = listOf(
            "youtube", "netflix", "spotify", "com.google.android.apps.chromecast", "smartthings",
            "com.google.android.apps.photos", "primevideo", "disney",
        ),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.CONTACTS, w = 8, h = 2),
            TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of(AppText.get(R.string.tpl_shopping_title), AppText.get(R.string.tpl_item_milk), AppText.get(R.string.tpl_item_bread)), w = 4, h = 4),
            TemplateWidget(CustomWidgetKind.QUICK_TOGGLES, w = 4, h = 4),
        ),
        rules = listOf(
            SuggestionRuleEntity.TYPE_TIME to timeRuleParams(everyDay, LocalTime.of(18, 0), LocalTime.of(23, 0)),
        ),
    )

    val Travel: ModeTemplate get() = ModeTemplate(
        name = AppText.get(R.string.tpl_travel_name),
        icon = "travel",
        color = 0xFFF0A844,
        description = AppText.get(R.string.tpl_travel_desc),
        appHints = listOf(
            "com.google.android.apps.maps", "booking", "intercity", "translate", "uber", "bolt", "airbnb", "weather",
        ),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.DUAL_CLOCK, JSONObject().put("zone", "Europe/London").toString(), w = 4, h = 3),
            TemplateWidget(CustomWidgetKind.COUNTDOWN, Countdown.of(AppText.get(R.string.tpl_travel_countdown)), w = 4, h = 3),
            TemplateWidget(CustomWidgetKind.HANDY, w = 4, h = 4),
            TemplateWidget(
                CustomWidgetKind.CHECKLIST,
                Checklist.of(
                    AppText.get(R.string.tpl_travel_checklist),
                    AppText.get(R.string.tpl_travel_item_id),
                    AppText.get(R.string.tpl_travel_item_charger),
                    AppText.get(R.string.tpl_travel_item_meds),
                    AppText.get(R.string.tpl_travel_item_headphones),
                ),
                w = 4, h = 4,
            ),
        ),
        rules = listOf(
            SuggestionRuleEntity.TYPE_CALENDAR to calendarRuleParams(AppText.get(R.string.tpl_travel_keyword_flight)),
            SuggestionRuleEntity.TYPE_CALENDAR to calendarRuleParams(AppText.get(R.string.tpl_travel_keyword_train)),
        ),
    )

    val Study: ModeTemplate get() = ModeTemplate(
        name = AppText.get(R.string.tpl_study_name),
        icon = "study",
        color = 0xFFB9A5FF,
        description = AppText.get(R.string.tpl_study_desc),
        appHints = listOf(
            "classroom", "com.google.android.apps.docs", "keep", "teams", "moodle", "onenote", "notion", "pdf",
        ),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.TODAY, w = 4, h = 4),
            TemplateWidget(CustomWidgetKind.COUNTDOWN, Countdown.of(AppText.get(R.string.tpl_study_countdown)), w = 4, h = 4),
            TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of(AppText.get(R.string.tpl_study_checklist)), w = 4, h = 4),
            TemplateWidget(CustomWidgetKind.HANDY, w = 4, h = 4),
        ),
        rules = listOf(
            SuggestionRuleEntity.TYPE_TIME to timeRuleParams(weekend, LocalTime.of(8, 0), LocalTime.of(16, 0)),
        ),
    )

    val Sport: ModeTemplate get() = ModeTemplate(
        name = AppText.get(R.string.tpl_sport_name),
        icon = "sport",
        color = 0xFFFF9A7F,
        description = AppText.get(R.string.tpl_sport_desc),
        appHints = listOf("strava", "fitness", "shealth", "health", "garmin", "spotify"),
        widgets = listOf(
            TemplateWidget(CustomWidgetKind.QUICK_TOGGLES, w = 8, h = 2),
            TemplateWidget(CustomWidgetKind.WEATHER, w = 4, h = 4),
            TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of(AppText.get(R.string.tpl_sport_checklist), AppText.get(R.string.tpl_sport_item_warmup), AppText.get(R.string.tpl_sport_item_stretch)), w = 4, h = 4),
        ),
    )

    val All: List<ModeTemplate> get() = listOf(Start, Work, Home, Travel, Study, Sport)

    // Gdy ktoś pominie kreator: jeden prosty tryb, żeby launcher miał co pokazać.
    val Default: ModeTemplate get() = ModeTemplate(name = AppText.get(R.string.tpl_default_name), icon = "sun", color = 0xFFF0A844, description = "", appHints = emptyList())

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
