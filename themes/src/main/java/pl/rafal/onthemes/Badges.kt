package pl.rafal.onthemes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils

// Kolory znaczka trybu (symbol na kolorowym tle) — przeniesione z launchera (ui/ModeIcons.kt), żeby podgląd
// w OnThemes rysował znaczki dokładnie tak jak launcher. Styl znaczka zależy od motywu (BadgeStyle);
// na razie jest jeden — TINTED — kolejne dochodzą w T2/T3.

// Tło znaczka dopasowane do motywu. Symbol ma jeden kolor na cały motyw: w ciemnym zawsze jasny,
// w jasnym zawsze ciemny — więc to tło się dostosowuje. Zachowujemy odcień i nasycenie (HSL), zmieniamy tylko jasność,
// aż kontrast z symbolem będzie dobry (luminancja jak w WCAG). Dotyczy też kolorów własnych z palety HSV.
fun badgeBackground(color: Long, dark: Boolean, style: BadgeStyle = BadgeStyle.TINTED): Color = when (style) {
    // INVERTED / MONO / METAL — w kolejnych paczkach; do tego czasu jak TINTED.
    BadgeStyle.TINTED, BadgeStyle.INVERTED, BadgeStyle.MONO, BadgeStyle.METAL -> tinted(color, dark)
}

// Kolor symbolu na znaczku trybu (i na podglądzie koloru w wyborze).
fun badgeSymbol(dark: Boolean, @Suppress("UNUSED_PARAMETER") style: BadgeStyle = BadgeStyle.TINTED): Color =
    if (dark) Color(0xFFF4F5F7) else Color(0xFF17181C)

private fun tinted(color: Long, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toInt(), hsl) // Long ARGB → Int, jak (int)kolor w C#
    fun lum() = Color(ColorUtils.HSLToColor(hsl)).luminance()
    // Symbol to grafika, nie drobny tekst — wystarcza kontrast 3:1 (WCAG dla elementów graficznych).
    if (dark) {
        // Jasny symbol (luminancja ok. 0.91): tło ≤ 0.27.
        while (lum() > 0.27f && hsl[2] > 0.05f) hsl[2] -= 0.02f
    } else {
        // Ciemny symbol (luminancja ok. 0.01): tło ≥ 0.13.
        while (lum() < 0.13f && hsl[2] < 0.97f) hsl[2] += 0.02f
    }
    return Color(ColorUtils.HSLToColor(hsl))
}
