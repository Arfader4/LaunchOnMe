package pl.rafal.onthemes

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// Model motywu OnThemes. Przeniesiony z launchera (dawne ui/theme/Palettes.kt): moduł trzyma motywy,
// a launcher tylko pyta o kolory (OnThemes / Themes / ThemeStore). Moduł nie zna launchera — jak biblioteka
// klas w .NET, do której aplikacja ma referencję, ale nie odwrotnie.

// Jasny / ciemny / za systemem — wspólne dla całego launchera i OnThemes.
enum class ThemeMode(@StringRes private val labelRes: Int) {
    LIGHT(R.string.ot_mode_light),
    DARK(R.string.ot_mode_dark),
    AUTO(R.string.ot_mode_auto),
    ;

    val label: String get() = OnThemesText.get(labelRes)

    // Czy ciemno: LIGHT/DARK na sztywno, AUTO według systemu (systemDark podaje wywołujący).
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        AUTO -> systemDark
    }
}

// Role kolorów jednego wariantu motywu (jasnego albo ciemnego).
// Wszystkie ekrany biorą kolory z tych ról, nigdy "na sztywno" — dlatego zmiana motywu działa wszędzie.
data class Roles(
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val outline: Long,
    val onBackground: Long,
    val onSurfaceVariant: Long,
    val primary: Long,
    val onPrimary: Long,
)

// Rodzina motywu: mówi, jak go pokazać i edytować (np. Luxury ma wybór metalu i bazy — od T3).
enum class ThemeFamily { STANDARD, NIGHT, HIGH_CONTRAST, LUXURY, BW, VINTAGE, PASTEL, AMOLED, SYSTEM, CUSTOM }

// Jak wygląda znaczek trybu w motywie (Badges.kt). Na razie wszystkie motywy mają TINTED (jak dotąd),
// INVERTED / MONO / METAL dochodzą w kolejnych paczkach.
enum class BadgeStyle {
    TINTED,   // kolorowe tło dopasowane do kontrastu + jasny/ciemny symbol (dotychczasowy wygląd)
    INVERTED, // High Contrast: czarne/białe tło + kolorowy symbol
    MONO,     // Black & White: szare tło, czarny/biały symbol
    METAL,    // Luxury: gradient metalu z połyskiem
}

// Wykończenie akcentów (klawisz ON, znaczki): płaskie albo metaliczne (Luxury, od T3).
enum class Finish { FLAT, METAL }

// Jeden motyw: nazwa, wersja jasna i ciemna, krótka paleta kolorów (po odcieniu), styl znaczków.
// Zwykła klasa, nie enum — motywów własnych może być więcej (od T4). Porównujemy po id.
class ThemeSpec(
    val id: String,
    @StringRes private val labelRes: Int,
    val family: ThemeFamily,
    private val light: Roles,
    private val dark: Roles,
    palette: List<Long>,
    val badge: BadgeStyle = BadgeStyle.TINTED,
    val finish: Finish = Finish.FLAT,
    val darkOnly: Boolean = false, // np. AMOLED: zawsze ciemny, przełącznik Jasny/Ciemny go nie zmienia
) {
    private val basePalette: List<Long> = sortByHue(palette)

    val label: String get() = OnThemesText.get(labelRes)

    // Krótka paleta motywu (kolory trybów, akcent). Własny motyw dokłada swój akcent na początek listy.
    // Systemowy bierze kolory z One UI / Material You (SystemColors), gdy są dostępne.
    val palette: List<Long>
        get() = when (family) {
            ThemeFamily.CUSTOM -> sortByHue((listOf(CustomTheme.colors.accent) + basePalette).distinct())
            ThemeFamily.SYSTEM -> SystemColors.palette.takeIf { it.isNotEmpty() } ?: basePalette
            else -> basePalette
        }

    // Role dla wariantu. Własny motyw jest jeden (bez osobnej wersji jasnej/ciemnej), systemowy pyta Androida,
    // "tylko ciemny" (AMOLED) zawsze daje wersję ciemną.
    fun roles(dark: Boolean): Roles = when {
        family == ThemeFamily.CUSTOM -> CustomTheme.colors.roles()
        family == ThemeFamily.SYSTEM -> SystemColors.roles(dark) ?: if (dark) this.dark else light
        darkOnly -> this.dark
        dark -> this.dark
        else -> light
    }

    // Czy motyw wyjdzie ciemny: własny "wie" to sam (po jasności tła), AMOLED zawsze, reszta słucha przełącznika.
    fun isDark(dark: Boolean): Boolean = when {
        family == ThemeFamily.CUSTOM -> Color(roles(dark).background).luminance() < 0.5f
        darkOnly -> true
        else -> dark
    }

    // Trzy kolory do podglądu motywu (tło, powierzchnia, akcent).
    fun swatches(dark: Boolean): List<Color> = roles(dark).let {
        listOf(Color(it.background), Color(it.surfaceVariant), Color(it.primary))
    }

    // Pełny ColorScheme Material 3 z ról; accent (kolor główny trybu) opcjonalnie nadpisuje primary.
    fun colorScheme(dark: Boolean, accent: Long?): ColorScheme {
        val r = roles(dark)
        val primary = accent?.let { Color(it) } ?: Color(r.primary)
        // Tekst na kolorze głównym: czarny na jasnym, biały na ciemnym (luminancja = jasność postrzegana).
        val onPrimary = if (accent == null) Color(r.onPrimary) else if (primary.luminance() > 0.5f) Color.Black else Color.White
        val base = if (isDark(dark)) darkColorScheme() else lightColorScheme()
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
            // Tła menu, arkuszy i okien też z motywu, żeby nie wyglądały na "obce".
            surfaceContainer = Color(r.surface),
            surfaceContainerLow = Color(r.surface),
            surfaceContainerHigh = Color(r.surfaceVariant),
            surfaceContainerHighest = Color(r.surfaceVariant),
        )
    }

    override fun equals(other: Any?): Boolean = other is ThemeSpec && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "ThemeSpec($id)"
}

// Kolory główne do wyboru (poza kolorem "z motywu"). Od T2 zastąpi je paleta motywu + pełna lista po odcieniu.
val AccentColors = listOf(
    0xFFF0A844, 0xFFD4AF37, 0xFFC0703A, 0xFFE0736B, 0xFFB9A5FF,
    0xFF8FB2FF, 0xFF4FB3BF, 0xFF7FD6AE, 0xFF3F8F5F, 0xFFECEAE4,
    0xFF4DF5CD, 0xFFEF476F, 0xFFFFD166, 0xFF118AB2, 0xFFF472B6, 0xFF84CC16,
)

// Wszystkie kolory do wyboru (kolory trybów + kolory główne), bez powtórzeń, posortowane po odcieniu.
// W launcherze pokazujemy je pod krótką paletą motywu ("Z motywu" / "Wszystkie kolory").
val AllColors: List<Long> = sortByHue(
    (
        listOf(
            0xFFF0A844, 0xFF8FB2FF, 0xFF7FD6AE, 0xFFFF9A7F, 0xFFB9A5FF, 0xFFD4AF37, 0xFF4FB3BF, 0xFFE0736B, 0xFF8B5A2B,
            0xFF4DF5CD, 0xFFFFD166, 0xFFEF476F, 0xFF06D6A0, 0xFF118AB2, 0xFFA78BFA, 0xFFF472B6, 0xFF94A3B8, 0xFF84CC16,
        ) + AccentColors
        ).distinct(),
)

// Motyw, w którym rysuje się bieżący ekran — znaczki trybów biorą z niego styl (BadgeStyle).
// CompositionLocal ≈ wartość dziedziczona w dół drzewa (jak DynamicResource w WPF). Ustawia ją ContextLauncherTheme.
val LocalThemeSpec = compositionLocalOf { Themes.NIGHT }

// Kolory własnego motywu: 4 wybierane w kreatorze, reszta ról wyliczana (mieszanie kolorów jak w Material).
data class CustomColors(
    val background: Long = 0xFF10151A,
    val surface: Long = 0xFF1A2229,
    val accent: Long = 0xFF4DF5CD,
    val text: Long = 0xFFE8EEF0,
) {
    fun roles(): Roles = Roles(
        background = background,
        surface = surface,
        surfaceVariant = mix(surface, text, 0.08f),      // lekko jaśniejsza/ciemniejsza powierzchnia (karty, pola)
        outline = mix(surface, text, 0.22f),             // ramki i separatory
        onBackground = text,
        onSurfaceVariant = mix(text, background, 0.35f), // drugorzędny tekst
        primary = accent,
        onPrimary = if (Color(accent).luminance() > 0.5f) 0xFF111111 else 0xFFFFFFFF,
    )
}

internal fun mix(a: Long, b: Long, t: Float): Long = lerp(Color(a), Color(b), t).toArgb().toLong() and 0xFFFFFFFFL

// Globalny stan własnego motywu. mutableStateOf = stan Compose: ekrany, które go czytają, przerysują się same.
object CustomTheme {
    var colors by mutableStateOf(CustomColors())
}

// Kolory uporządkowane "po tęczy": najpierw barwne według odcienia (czerwony → żółty → zielony → niebieski → fiolet),
// w obrębie podobnego odcienia od ciemniejszych; na końcu szarości (prawie bez nasycenia) od ciemnych do jasnych.
// Jak OrderBy(hue).ThenBy(lightness) w LINQ, z osobną grupą dla szarości.
fun sortByHue(colors: List<Long>): List<Long> {
    fun hsl(c: Long): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(c.toInt(), it) }
    val (greys, chromatic) = colors.partition { c -> hsl(c).let { it[1] < 0.12f || it[2] < 0.06f || it[2] > 0.96f } }
    return chromatic.sortedWith(compareBy<Long>({ (hsl(it)[0] / 15f).toInt() }, { hsl(it)[2] })) +
        greys.sortedBy { hsl(it)[2] }
}
