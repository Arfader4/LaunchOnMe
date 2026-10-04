package pl.rafal.contextlauncher.data

import androidx.annotation.StringRes
import org.json.JSONObject
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.db.SuggestionRuleEntity
import pl.rafal.contextlauncher.suggest.timeRuleParams
import java.time.DayOfWeek
import java.time.LocalTime

// Kreator nowego trybu ("wywiad"): kilka pytań → gotowa propozycja karty.
// Cała logika jest tutaj (bez UI), żeby dało się ją testować i czytać jak zwykły przepis.

// Do czego ma służyć tryb. Każdy cel ma bazowy szablon (aplikacje, widżety, reguły) z ModeTemplates albo własny.
enum class WizardPurpose(
    @StringRes private val labelRes: Int,
    val emoji: String,
    private val templateFactory: () -> ModeTemplate,
) {
    WORK(R.string.wiz_purpose_work, "💼", { ModeTemplates.Work }),
    STUDY(R.string.wiz_purpose_study, "🎓", { ModeTemplates.Study }),
    HOME(R.string.wiz_purpose_home, "🛋", { ModeTemplates.Home }),
    TRAVEL(R.string.wiz_purpose_travel, "✈", { ModeTemplates.Travel }),
    SPORT(R.string.wiz_purpose_sport, "🏃", { ModeTemplates.Sport }),
    CAR(
        R.string.wiz_purpose_car, "🚗",
        {
            ModeTemplate(
                name = AppText.get(R.string.wiz_car_name), icon = "car", color = 0xFF6FC3DF,
                description = AppText.get(R.string.wiz_car_desc),
                appHints = listOf("com.google.android.apps.maps", "waze", "spotify", "music", "youtube.music", "podcast", "dialer", "yanosik"),
                widgets = listOf(
                    TemplateWidget(CustomWidgetKind.CONTACTS, w = 8, h = 2),
                    TemplateWidget(CustomWidgetKind.QUICK_TOGGLES, w = 8, h = 2),
                ),
            )
        },
    ),
    EVENING(
        R.string.wiz_purpose_evening, "🌙",
        {
            ModeTemplate(
                name = AppText.get(R.string.wiz_evening_name), icon = "night", color = 0xFF9C8CF0,
                description = AppText.get(R.string.wiz_evening_desc),
                appHints = listOf("kindle", "legimi", "audible", "storytel", "calm", "headspace", "sleep", "spotify", "clock"),
                widgets = listOf(
                    TemplateWidget(CustomWidgetKind.CLOCK, w = 4, h = 4),
                    TemplateWidget(CustomWidgetKind.QUICK_TOGGLES, w = 4, h = 4),
                ),
                rules = listOf(
                    SuggestionRuleEntity.TYPE_TIME to timeRuleParams(DayOfWeek.values().toSet(), LocalTime.of(22, 0), LocalTime.of(23, 59)),
                ),
            )
        },
    ),
    FAMILY(
        R.string.wiz_purpose_family, "👪",
        {
            ModeTemplate(
                name = AppText.get(R.string.wiz_family_name), icon = "family", color = 0xFFFFB86B,
                description = AppText.get(R.string.wiz_family_desc),
                appHints = listOf("whatsapp", "messenger", "com.google.android.apps.photos", "gallery", "calendar", "librus", "vulcan", "messaging"),
                widgets = listOf(
                    TemplateWidget(CustomWidgetKind.CONTACTS, w = 8, h = 2),
                    TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of(AppText.get(R.string.wiz_family_checklist)), w = 4, h = 4),
                    TemplateWidget(CustomWidgetKind.TODAY, w = 4, h = 4),
                ),
            )
        },
    ),
    FUN(
        R.string.wiz_purpose_fun, "🎮",
        {
            ModeTemplate(
                name = AppText.get(R.string.wiz_fun_name), icon = "game", color = 0xFFFF7FB0,
                description = AppText.get(R.string.wiz_fun_desc),
                appHints = listOf("game", "gry", "steam", "discord", "twitch", "youtube", "netflix", "tiktok", "instagram"),
                widgets = listOf(TemplateWidget(CustomWidgetKind.MODE_NOTE, JSONObject().put("text", AppText.get(R.string.wiz_fun_note)).toString(), w = 8, h = 2)),
            )
        },
    ),
    SHOPPING(
        R.string.wiz_purpose_shopping, "🛒",
        {
            ModeTemplate(
                name = AppText.get(R.string.wiz_shopping_name), icon = "cart", color = 0xFF7FD6AE,
                description = AppText.get(R.string.wiz_shopping_desc),
                appHints = listOf("allegro", "lidl", "biedronka", "zabka", "olx", "vinted", "blik", "bank", "pay", "wallet"),
                widgets = listOf(TemplateWidget(CustomWidgetKind.CHECKLIST, Checklist.of(AppText.get(R.string.tpl_shopping_title), AppText.get(R.string.tpl_item_milk), AppText.get(R.string.tpl_item_bread)), w = 4, h = 5)),
            )
        },
    ),
    EMPTY(
        R.string.wiz_purpose_empty, "➕",
        { ModeTemplate(name = AppText.get(R.string.wiz_empty_name), icon = "star", color = 0xFF8FB2FF, description = AppText.get(R.string.wiz_empty_desc), appHints = emptyList()) },
    ),
    ;

    val label: String get() = AppText.get(labelRes)
    val template: ModeTemplate get() = templateFactory()
}

// Jak dużo ma być na karcie.
enum class WizardStyle(@StringRes private val labelRes: Int, @StringRes private val descriptionRes: Int) {
    MINIMAL(R.string.wiz_style_minimal, R.string.wiz_style_minimal_desc),
    BALANCED(R.string.wiz_style_balanced, R.string.wiz_style_balanced_desc),
    RICH(R.string.wiz_style_rich, R.string.wiz_style_rich_desc),
    ;

    val label: String get() = AppText.get(labelRes)
    val description: String get() = AppText.get(descriptionRes)
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
