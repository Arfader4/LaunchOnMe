package pl.rafal.contextlauncher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Jasny / ciemny / za systemem — wspólne dla całego launchera.
enum class ThemeMode(val label: String) {
    LIGHT("Jasny"),
    DARK("Ciemny"),
    AUTO("Auto"),
}

// Role kolorów jednego wariantu schematu (jasnego albo ciemnego).
// Wszystkie ekrany biorą kolory z tych ról, nigdy "na sztywno" — dlatego zmiana motywu działa wszędzie.
private data class Roles(
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
enum class Palette(val label: String, private val light: Roles, private val dark: Roles) {
    NIGHT(
        "Nocny",
        light = Roles(0xFFF4F2EE, 0xFFFFFFFF, 0xFFE9E6E0, 0xFFD3CFC7, 0xFF1B1C1E, 0xFF5C6068, 0xFFB26A00, 0xFFFFFFFF),
        dark = Roles(0xFF0F1012, 0xFF1A1C20, 0xFF23262B, 0xFF2E3238, 0xFFECEAE4, 0xFFA3A7AE, 0xFFF0A844, 0xFF1A1206),
    ),
    VINTAGE(
        "Vintage",
        light = Roles(0xFFF6EEDF, 0xFFFFF9EF, 0xFFEEDFC8, 0xFFD8C3A5, 0xFF3B2A1D, 0xFF7A6450, 0xFF8B5A2B, 0xFFFFFFFF),
        dark = Roles(0xFF1C1611, 0xFF2A2119, 0xFF362A20, 0xFF4A3A2C, 0xFFF1E6D6, 0xFFC2AE96, 0xFFD4A373, 0xFF2A1A0C),
    ),
    CONTRAST(
        "Contrast",
        light = Roles(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF0F0F0, 0xFF7A7A7A, 0xFF000000, 0xFF333333, 0xFF000000, 0xFFFFFFFF),
        dark = Roles(0xFF000000, 0xFF0D0D0D, 0xFF1A1A1A, 0xFF5A5A5A, 0xFFFFFFFF, 0xFFD0D0D0, 0xFFFFFFFF, 0xFF000000),
    ),
    ELEGANT(
        "Elegant",
        light = Roles(0xFFF3F1E8, 0xFFFFFFFF, 0xFFE3E8E1, 0xFFC3CFC5, 0xFF17261E, 0xFF4F6358, 0xFF8C6D1F, 0xFFFFFFFF),
        dark = Roles(0xFF0E1A15, 0xFF15251E, 0xFF1D3128, 0xFF2F4A3D, 0xFFEDE6D3, 0xFFAFC0B4, 0xFFD4AF37, 0xFF1E1A08),
    ),
    ;

    // Trzy kolory do podglądu schematu w ustawieniach (tło, powierzchnia, akcent).
    fun swatches(dark: Boolean): List<Color> = (if (dark) this.dark else light).let {
        listOf(Color(it.background), Color(it.surfaceVariant), Color(it.primary))
    }

    // Pełny ColorScheme Material 3 z ról; accent (kolor główny) opcjonalnie nadpisuje primary.
    fun colorScheme(dark: Boolean, accent: Long?): ColorScheme {
        val r = if (dark) this.dark else light
        val primary = accent?.let { Color(it) } ?: Color(r.primary)
        // Tekst na kolorze głównym: czarny na jasnym, biały na ciemnym (luminancja = jasność postrzegana).
        val onPrimary = if (accent == null) Color(r.onPrimary) else if (primary.luminance() > 0.5f) Color.Black else Color.White
        val base = if (dark) darkColorScheme() else lightColorScheme()
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
)
