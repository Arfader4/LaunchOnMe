package pl.rafal.contextlauncher.data

import org.json.JSONObject
import pl.rafal.contextlauncher.data.db.SuggestionRuleEntity
import pl.rafal.contextlauncher.suggest.timeRuleParams
import java.time.DayOfWeek
import java.time.LocalTime

// Kreator nowego trybu ("wywiad"): kilka pytań → gotowa propozycja karty.
// Cała logika jest tutaj (bez UI), żeby dało się ją testować i czytać jak zwykły przepis.

// Do czego ma służyć tryb. Każdy cel ma bazowy szablon (aplikacje, widżety, reguły) z ModeTemplates albo własny.
enum class WizardPurpose(val label: String, val emoji: String, val template: ModeTemplate) {
    WORK("Praca", "💼", ModeTemplates.Work),
    STUDY("Nauka", "🎓", ModeTemplates.Study),
    HOME("Dom i relaks", "🛋", ModeTemplates.Home),
    TRAVEL("Podróż", "✈", ModeTemplates.Travel),
    SPORT("Sport", "🏃", ModeTemplates.Sport),
    CAR(
        "W samochodzie", "🚗",
        ModeTemplate(
            name = "Auto", icon = "car", color = 0xFF6FC3DF,
            description = "Nawigacja, muzyka i kontakty pod kciukiem.",
            appHints = listOf("com.google.android.apps.maps", "waze", "spotify", "music", "youtube.music", "podcast", "dialer", "yanosik"),
            widgets = listOf(
                TemplateWidget(CustomWidgetKind.CONTACTS, w = 8, h = 2),
                TemplateWidget(CustomWidgetKind.QUICK_TOGGLES, w = 8, h = 2),
            ),
        ),
    ),
    EVENING(
        "Wieczór i sen", "🌙",
        ModeTemplate(
            name = "Wieczór", icon = "night", color = 0xFF9C8CF0,
            description = "Spokojna karta na koniec dnia: zegar, przełączniki (Nie przeszkadzać), czytanie i muzyka.",
            appHints = listOf("kindle", "legimi", "audible", "storytel", "calm", "headspace", "sleep", "spotify", "clock"),
            widgets = listOf(
                TemplateWidget(CustomWidgetKind.CLOCK, w = 4, h = 4),
                TemplateWidget(CustomWidgetKind.QUICK_TOGGLES, w = 4, h = 4),
            ),
            rules = listOf(
                SuggestionRuleEntity.TYPE_TIME to timeRuleParams(DayOfWeek.values().toSet(), LocalTime.of(22, 0), LocalTime.of(23, 59)),
            ),
        ),
    ),
    FAMILY(
        "Rodzina", "👪",
        ModeTemplate(
            name = "Rodzina", icon = "family", color = 0xFFFFB86B,
            description = "Bliscy jednym dotknięciem, wspólna lista, zdjęcia i kalendarz.",
            appHints = listOf("whatsapp", "messenger", "com.google.android.apps.photos", "gallery", "calendar", "librus", "vulcan", "messaging"),
            widgets = listOf(
                TemplateWidget(CustomWidgetKind.CONTACTS, w = 8, h = 2),
                TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of("Do zrobienia razem"), w = 4, h = 4),
                TemplateWidget(CustomWidgetKind.TODAY, w = 4, h = 4),
            ),
        ),
    ),
    FUN(
        "Rozrywka i gry", "🎮",
        ModeTemplate(
            name = "Rozrywka", icon = "game", color = 0xFFFF7FB0,
            description = "Gry, filmy i znajomi w jednym miejscu.",
            appHints = listOf("game", "gry", "steam", "discord", "twitch", "youtube", "netflix", "tiktok", "instagram"),
            widgets = listOf(TemplateWidget(CustomWidgetKind.MODE_NOTE, JSONObject().put("text", "Tylko godzinka 🙂").toString(), w = 8, h = 2)),
        ),
    ),
    SHOPPING(
        "Zakupy", "🛒",
        ModeTemplate(
            name = "Zakupy", icon = "cart", color = 0xFF7FD6AE,
            description = "Lista zakupów, sklepy i płatności.",
            appHints = listOf("allegro", "lidl", "biedronka", "zabka", "olx", "vinted", "blik", "bank", "pay", "wallet"),
            widgets = listOf(TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of("Zakupy", "mleko", "chleb"), w = 4, h = 5)),
        ),
    ),
    EMPTY(
        "Pusty — sam ułożę", "➕",
        ModeTemplate(name = "Nowy tryb", icon = "star", color = 0xFF8FB2FF, description = "Bez widżetów i aplikacji — dodasz je w edycji układu.", appHints = emptyList()),
    ),
}

// Jak dużo ma być na karcie.
enum class WizardStyle(val label: String, val description: String) {
    MINIMAL("Minimalny", "Kilka najważniejszych aplikacji, bez widżetów"),
    BALANCED("Zrównoważony", "Dwa widżety i najpotrzebniejsze aplikacje"),
    RICH("Pełny", "Wszystkie widżety z propozycji i więcej aplikacji"),
}

// Odpowiedzi z wywiadu → co dokładnie powstanie. Karta (widżety + aplikacje) jest liczona z góry,
// żeby pokazać ją na ostatnim kroku i pozwolić usunąć zbędne aplikacje przed utworzeniem.
data class ModePlan(
    val purpose: WizardPurpose,
    val style: WizardStyle,
    val name: String,
    val icon: String,
    val color: Long,
    val useRules: Boolean,
    val iconCells: Int?, // null = jak w Ustawieniach, 1 = małe ikony, 2 = duże
) {
    val widgets: List<TemplateWidget>
        get() = when (style) {
            WizardStyle.MINIMAL -> emptyList()
            WizardStyle.BALANCED -> purpose.template.widgets.take(2)
            WizardStyle.RICH -> purpose.template.widgets
        }

    val appLimit: Int
        get() = if (purpose == WizardPurpose.EMPTY) 0 else when (style) {
            WizardStyle.MINIMAL -> 6
            WizardStyle.BALANCED -> 10
            WizardStyle.RICH -> 16
        }

    // Szablon, z którego VM tworzy tryb (ta sama ścieżka co kreator pierwszego uruchomienia).
    fun toTemplate(): ModeTemplate = purpose.template.copy(
        name = name,
        icon = icon,
        color = color,
        widgets = widgets,
        rules = if (useRules) purpose.template.rules else emptyList(),
    )

    // Propozycja aplikacji: najpierw pasujące do celu, a przy stylu pełnym dopełnione uniwersalnymi.
    fun suggestApps(installed: List<AppInfo>): List<AppInfo> {
        if (appLimit == 0) return emptyList()
        val own = ModeTemplates.pickApps(installed, purpose.template)
        val pool = if (style == WizardStyle.RICH || own.size < 4) ModeTemplates.pickAppsForFill(installed, purpose.template) else own
        return pool.take(appLimit)
    }

    companion object {
        fun start(purpose: WizardPurpose) = ModePlan(
            purpose = purpose,
            style = WizardStyle.BALANCED,
            name = purpose.template.name,
            icon = purpose.template.icon,
            color = purpose.template.color,
            useRules = purpose.template.rules.isNotEmpty(),
            iconCells = null,
        )
    }
}
