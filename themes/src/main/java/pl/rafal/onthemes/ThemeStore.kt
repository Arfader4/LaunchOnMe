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
    }

    private val _defaultTheme = MutableStateFlow(Themes.find(prefs.getString(KEY_THEME, null)) ?: Themes.NIGHT)
    val defaultTheme: StateFlow<ThemeSpec> = _defaultTheme.asStateFlow()

    init {
        // Własny motyw wczytujemy od razu, żeby pierwsza klatka miała już właściwe kolory.
        val d = CustomColors()
        CustomTheme.colors = CustomColors(
            background = prefs.getLong(KEY_C_BG, d.background),
            surface = prefs.getLong(KEY_C_SURFACE, d.surface),
            accent = prefs.getLong(KEY_C_ACCENT, d.accent),
            text = prefs.getLong(KEY_C_TEXT, d.text),
        )
    }

    // Luxury: wolny refleks światła przesuwający się po klawiszu ON i dużych znaczkach (domyślnie wyłączony — bateria).
    private val _luxurySheen = MutableStateFlow(prefs.getBoolean(KEY_SHEEN, false))
    val luxurySheen: StateFlow<Boolean> = _luxurySheen.asStateFlow()

    fun setLuxurySheen(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHEEN, on).apply()
        _luxurySheen.value = on
    }

    fun setCustomColors(colors: CustomColors) {
        prefs.edit()
            .putLong(KEY_C_BG, colors.background)
            .putLong(KEY_C_SURFACE, colors.surface)
            .putLong(KEY_C_ACCENT, colors.accent)
            .putLong(KEY_C_TEXT, colors.text)
            .apply()
        CustomTheme.colors = colors
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
        private const val KEY_C_BG = "custom_bg"
        private const val KEY_C_SURFACE = "custom_surface"
        private const val KEY_C_ACCENT = "custom_accent"
        private const val KEY_C_TEXT = "custom_text"

        @Volatile private var instance: ThemeStore? = null

        fun get(context: Context): ThemeStore =
            instance ?: synchronized(this) { instance ?: ThemeStore(context.applicationContext).also { instance = it } }
    }
}
