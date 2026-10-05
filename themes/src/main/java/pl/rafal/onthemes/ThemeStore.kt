package pl.rafal.onthemes

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Globalne ustawienia wyglądu (dawne ThemePrefs z launchera). Ten sam plik SharedPreferences "appearance"
// i te same klucze co wcześniej — po aktualizacji nic nie trzeba przenosić; stare nazwy motywów tłumaczy Themes.find.
// Singleton: launcher, OnThemes i import kopii zapasowej zmieniają te same StateFlow, więc wszystko odświeża się na żywo.
class ThemeStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }.getOrDefault(ThemeMode.AUTO),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        SystemColors.refresh(context) // przed odczytem motywu: "Systemowy" ma od razu właściwe kolory

        // Motywy własne PRZED odczytem motywu globalnego (globalny może być własnym, np. "custom:2").
        // Od 1.5 lista w JSON; przy pierwszym starcie przenosimy dawny jedyny "Własny schemat" (klucze custom_*).
        val json = prefs.getString(KEY_CUSTOMS, null)
        CustomThemes.defs = if (json != null) {
            CustomThemes.fromJson(json)
        } else {
            val d = CustomColors()
            val legacy = CustomThemeDef(
                id = CustomThemes.LEGACY_ID,
                name = null,
                colors = CustomColors(
                    background = prefs.getLong(KEY_C_BG, d.background),
                    surface = prefs.getLong(KEY_C_SURFACE, d.surface),
                    accent = prefs.getLong(KEY_C_ACCENT, d.accent),
                    text = prefs.getLong(KEY_C_TEXT, d.text),
                ),
            )
            listOf(legacy).also { prefs.edit().putString(KEY_CUSTOMS, CustomThemes.toJson(it)).apply() }
        }
    }

    private val _defaultTheme = MutableStateFlow(Themes.find(prefs.getString(KEY_THEME, null)) ?: Themes.NIGHT)
    val defaultTheme: StateFlow<ThemeSpec> = _defaultTheme.asStateFlow()

    // Luxury: wolny refleks światła przesuwający się po klawiszu ON i dużych znaczkach (domyślnie wyłączony — bateria).
    private val _luxurySheen = MutableStateFlow(prefs.getBoolean(KEY_SHEEN, false))
    val luxurySheen: StateFlow<Boolean> = _luxurySheen.asStateFlow()

    fun setLuxurySheen(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHEEN, on).apply()
        _luxurySheen.value = on
    }

    // Kolory pierwszego motywu własnego ("CUSTOM") — dla kopii zapasowej i starszego kodu.
    fun setCustomColors(colors: CustomColors) {
        val current = CustomThemes.def(CustomThemes.LEGACY_ID)
        saveCustom(current?.copy(colors = colors) ?: CustomThemeDef(CustomThemes.LEGACY_ID, null, colors))
        // Stare klucze też — gdyby ktoś wrócił do wersji sprzed 1.5.
        prefs.edit()
            .putLong(KEY_C_BG, colors.background)
            .putLong(KEY_C_SURFACE, colors.surface)
            .putLong(KEY_C_ACCENT, colors.accent)
            .putLong(KEY_C_TEXT, colors.text)
            .apply()
    }

    // Dodaje albo podmienia motyw własny (po id). Launcher i OnThemes przerysują się same (stan Compose).
    fun saveCustom(def: CustomThemeDef) {
        val list = CustomThemes.defs
        val updated = if (list.any { it.id == def.id }) list.map { if (it.id == def.id) def else it } else list + def
        CustomThemes.defs = updated
        prefs.edit().putString(KEY_CUSTOMS, CustomThemes.toJson(updated)).apply()
    }

    // Usuwa motyw własny. Gdy był globalnym — globalny wraca do "Nocnego"; tryby z nim wracają do globalnego same.
    fun deleteCustom(id: String) {
        val updated = CustomThemes.defs.filter { it.id != id }
        CustomThemes.defs = updated
        prefs.edit().putString(KEY_CUSTOMS, CustomThemes.toJson(updated)).apply()
        if (_defaultTheme.value.id == id) setDefaultTheme(Themes.NIGHT)
    }

    // Cała lista naraz (import kopii zapasowej).
    fun replaceCustoms(list: List<CustomThemeDef>) {
        CustomThemes.defs = list
        prefs.edit().putString(KEY_CUSTOMS, CustomThemes.toJson(list)).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setDefaultTheme(theme: ThemeSpec) {
        prefs.edit().putString(KEY_THEME, theme.id).apply()
        _defaultTheme.value = theme
    }

    // Jasność wymuszona w ustawieniach: true/false, albo null = "Auto" (za systemem).
    fun forcedDark(): Boolean? = when (_themeMode.value) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> null
    }

    companion object {
        private const val KEY_MODE = "theme_mode"
        private const val KEY_THEME = "default_palette" // nazwa klucza z czasów "schematów" — zostaje dla zgodności
        private const val KEY_SHEEN = "luxury_sheen"
        private const val KEY_CUSTOMS = "custom_themes" // lista motywów własnych (JSON), od 1.5
        private const val KEY_C_BG = "custom_bg"
        private const val KEY_C_SURFACE = "custom_surface"
        private const val KEY_C_ACCENT = "custom_accent"
        private const val KEY_C_TEXT = "custom_text"

        @Volatile private var instance: ThemeStore? = null

        fun get(context: Context): ThemeStore =
            instance ?: synchronized(this) { instance ?: ThemeStore(context.applicationContext).also { instance = it } }
    }
}
