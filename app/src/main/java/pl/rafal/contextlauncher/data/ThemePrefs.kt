package pl.rafal.contextlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.rafal.contextlauncher.ui.theme.Palette
import pl.rafal.contextlauncher.ui.theme.ThemeMode
import pl.rafal.contextlauncher.ui.theme.CustomColors
import pl.rafal.contextlauncher.ui.theme.CustomTheme

// Globalne ustawienia wyglądu w SharedPreferences (proste pary klucz–wartość, jak ustawienia aplikacji w .NET).
// Singleton jak AppPrefs: import konfiguracji musi zmienić te same StateFlow, które obserwuje ekran.
class ThemePrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }.getOrDefault(ThemeMode.AUTO),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _defaultPalette = MutableStateFlow(Palette.fromName(prefs.getString(KEY_PALETTE, null)) ?: Palette.NIGHT)
    val defaultPalette: StateFlow<Palette> = _defaultPalette.asStateFlow()

    init {
        // Własny schemat wczytujemy od razu, żeby pierwsza klatka miała już właściwe kolory.
        CustomTheme.colors = CustomColors(
            background = prefs.getLong(KEY_C_BG, CustomColors().background),
            surface = prefs.getLong(KEY_C_SURFACE, CustomColors().surface),
            accent = prefs.getLong(KEY_C_ACCENT, CustomColors().accent),
            text = prefs.getLong(KEY_C_TEXT, CustomColors().text),
        )
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

    fun setDefaultPalette(palette: Palette) {
        prefs.edit().putString(KEY_PALETTE, palette.name).apply()
        _defaultPalette.value = palette
    }

    companion object {
        private const val KEY_MODE = "theme_mode"
        private const val KEY_PALETTE = "default_palette"
        private const val KEY_C_BG = "custom_bg"
        private const val KEY_C_SURFACE = "custom_surface"
        private const val KEY_C_ACCENT = "custom_accent"
        private const val KEY_C_TEXT = "custom_text"

        @Volatile private var instance: ThemePrefs? = null

        fun get(context: Context): ThemePrefs =
            instance ?: synchronized(this) { instance ?: ThemePrefs(context.applicationContext).also { instance = it } }
    }
}
