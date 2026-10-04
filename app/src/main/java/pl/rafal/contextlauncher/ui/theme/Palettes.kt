package pl.rafal.contextlauncher.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R

// Jasny / ciemny / za systemem — wspólne dla całego launchera.
enum class ThemeMode(@StringRes private val labelRes: Int) {
    LIGHT(R.string.look_theme_light),
    DARK(R.string.look_theme_dark),
    AUTO(R.string.look_theme_auto),
    ;

    val label: String get() = AppText.get(labelRes)
}

// Role kolorów jednego wariantu schematu (jasnego albo ciemnego).
// Wszystkie ekrany biorą kolory z tych ról, nigdy "na sztywno" — dlatego zmiana motywu działa wszędzie.
internal data class Roles(
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val outline: Long,
    val onBackground: Long,
    val onSurfaceVariant: Long,
    val primary: Long,
    val onPrimary: Long,
)

// Schemat kolorystyczny: nazwa + wersja jasna i ciemna. Enum z polami, jak w C# klasa z instancjami statycznymi.
enum class Palette(@StringRes private val labelRes: Int, private val light: Roles, private val dark: Roles) {
    NIGHT(
        R.string.look_palette_night,
        light = Roles(0xFFF4F2EE, 0xFFFFFFFF, 0xFFE9E6E0, 0xFFD3CFC7, 0xFF1B1C1E, 0xFF5C6068, 0xFFB26A00, 0xFFFFFFFF),
        dark = Roles(0xFF0F1012, 0xFF1A1C20, 0xFF23262B, 0xFF2E3238, 0xFFECEAE4, 0xFFA3A7AE, 0xFFF0A844, 0xFF1A1206),
    ),
    VINTAGE(
        R.string.look_palette_vintage,
        light = Roles(0xFFF6EEDF, 0xFFFFF9EF, 0xFFEEDFC8, 0xFFD8C3A5, 0xFF3B2A1D, 0xFF7A6450, 0xFF8B5A2B, 0xFFFFFFFF),
        dark = Roles(0xFF1C1611, 0xFF2A2119, 0xFF362A20, 0xFF4A3A2C, 0xFFF1E6D6, 0xFFC2AE96, 0xFFD4A373, 0xFF2A1A0C),
    ),
    CONTRAST(
        R.string.look_palette_contrast,
        light = Roles(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF0F0F0, 0xFF7A7A7A, 0xFF000000, 0xFF333333, 0xFF000000, 0xFFFFFFFF),
        dark = Roles(0xFF000000, 0xFF0D0D0D, 0xFF1A1A1A, 0xFF5A5A5A, 0xFFFFFFFF, 0xFFD0D0D0, 0xFFFFFFFF, 0xFF000000),
    ),
    // Własny schemat z kreatora (Ustawienia → Wygląd). Wartości poniżej są tylko zastępcze — prawdziwe
    // kolory bierze z CustomTheme, więc zmiana w kreatorze od razu przemalowuje cały launcher.
    CUSTOM(
        R.string.look_palette_custom,
        light = Roles(0xFF10151A, 0xFF1A2229, 0xFF232D35, 0xFF33414C, 0xFFE8EEF0, 0xFFA6B3BA, 0xFF4DF5CD, 0xFF06201A),
        dark = Roles(0xFF10151A, 0xFF1A2229, 0xFF232D35, 0xFF33414C, 0xFFE8EEF0, 0xFFA6B3BA, 0xFF4DF5CD, 0xFF06201A),
    ),
    ELEGANT(
        R.string.look_palette_elegant,
        light = Roles(0xFFF3F1E8, 0xFFFFFFFF, 0xFFE3E8E1, 0xFFC3CFC5, 0xFF17261E, 0xFF4F6358, 0xFF8C6D1F, 0xFFFFFFFF),
        dark = Roles(0xFF0E1A15, 0xFF15251E, 0xFF1D3128, 0xFF2F4A3D, 0xFFEDE6D3, 0xFFAFC0B4, 0xFFD4AF37, 0xFF1E1A08),
    ),
    ;

    val label: String get() = AppText.get(labelRes)

    // Role dla wariantu; własny schemat jest jeden (bez osobnej wersji jasnej/ciemnej).
    private fun roles(dark: Boolean): Roles = if (this == CUSTOM) CustomTheme.colors.roles() else if (dark) this.dark else light

    // Trzy kolory do podglądu schematu w ustawieniach (tło, powierzchnia, akcent).
    fun swatches(dark: Boolean): List<Color> = roles(dark).let {
        listOf(Color(it.background), Color(it.surfaceVariant), Color(it.primary))
    }

    // Pełny ColorScheme Material 3 z ról; accent (kolor główny) opcjonalnie nadpisuje primary.
    fun colorScheme(dark: Boolean, accent: Long?): ColorScheme {
        val r = roles(dark)
        val primary = accent?.let { Color(it) } ?: Color(r.primary)
        // Tekst na kolorze głównym: czarny na jasnym, biały na ciemnym (luminancja = jasność postrzegana).
        val onPrimary = if (accent == null) Color(r.onPrimary) else if (primary.luminance() > 0.5f) Color.Black else Color.White
        // Własny schemat sam "wie", czy jest ciemny (po jasności tła) — niezależnie od przełącznika Jasny/Ciemny.
        val isDark = if (this == CUSTOM) Color(r.background).luminance() < 0.5f else dark
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary,
            onPrimary = onPrimary,
            background = Color(r.background),
            onBackground = Color(r.onBackground),
            surface = Color(r.surface),
            onSurface = Color(r.onBackground),
            surfaceVariant = Color(r.surfaceVariant),
            onSurfaceVariant = Color(r.onSurfaceVariant),
            outline = Color(r.outline),
            // Tła menu, arkuszy i okien też z palety, żeby nie wyglądały na "obce".
            surfaceContainer = Color(r.surface),
            surfaceContainerLow = Color(r.surface),
            surfaceContainerHigh = Color(r.surfaceVariant),
            surfaceContainerHighest = Color(r.surfaceVariant),
        )
    }

    companion object {
        fun fromName(name: String?): Palette? = entries.firstOrNull { it.name == name }
    }
}

// Kolory główne do wyboru (poza kolorem "ze schematu").
val AccentColors = listOf(
    0xFFF0A844, 0xFFD4AF37, 0xFFC0703A, 0xFFE0736B, 0xFFB9A5FF,
    0xFF8FB2FF, 0xFF4FB3BF, 0xFF7FD6AE, 0xFF3F8F5F, 0xFFECEAE4,
    0xFF4DF5CD, 0xFFEF476F, 0xFFFFD166, 0xFF118AB2, 0xFFF472B6, 0xFF84CC16,
)

// Kolory własnego schematu: 4 wybierane w kreatorze, reszta ról wyliczana (mieszanie kolorów jak w Material).
data class CustomColors(
    val background: Long = 0xFF10151A,
    val surface: Long = 0xFF1A2229,
    val accent: Long = 0xFF4DF5CD,
    val text: Long = 0xFFE8EEF0,
) {
    internal fun roles(): Roles = Roles(
        background = background,
        surface = surface,
        surfaceVariant = mix(surface, text, 0.08f),   // lekko jaśniejsza/ciemniejsza powierzchnia (karty, pola)
        outline = mix(surface, text, 0.22f),          // ramki i separatory
        onBackground = text,
        onSurfaceVariant = mix(text, background, 0.35f), // drugorzędny tekst
        primary = accent,
        onPrimary = if (Color(accent).luminance() > 0.5f) 0xFF111111 else 0xFFFFFFFF,
    )
}

private fun mix(a: Long, b: Long, t: Float): Long = lerp(Color(a), Color(b), t).toArgb().toLong() and 0xFFFFFFFFL

// Globalny stan własnego schematu. mutableStateOf = stan Compose: ekrany, które go czytają, przerysują się same.
object CustomTheme {
    var colors by mutableStateOf(CustomColors())
}
