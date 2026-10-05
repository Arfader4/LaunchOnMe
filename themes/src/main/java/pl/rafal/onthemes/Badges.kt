package pl.rafal.onthemes

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils

// Kolory znaczka trybu (symbol na tle) — wspólne dla launchera i podglądu w OnThemes.
// Styl zależy od motywu (BadgeStyle):
//  TINTED   — kolorowe tło dopasowane do kontrastu, symbol jasny (ciemny motyw) albo ciemny (jasny motyw),
//  INVERTED — High Contrast: czarne / białe tło i symbol W KOLORZE trybu (kontrast ≥ 4,5:1),
//  MONO     — Black & White: tło w odcieniu szarości (jasność z koloru trybu), symbol czarny / biały,
//  METAL    — Luxury: tło jak TINTED (kolor trybu), symbol w kolorze metalu i metaliczna obwódka z połyskiem.

fun badgeBackground(color: Long, dark: Boolean, style: BadgeStyle = BadgeStyle.TINTED): Color = when (style) {
    BadgeStyle.TINTED -> fitToSymbol(color, dark, keepSaturation = true)
    BadgeStyle.MONO -> fitToSymbol(color, dark, keepSaturation = false)
    // Metalowy symbol jest mniej jasny / mniej ciemny niż biały / czarny, więc tło odsuwamy dalej (kontrast ≥ 3:1).
    BadgeStyle.METAL -> fitToSymbol(color, dark, keepSaturation = true, darkMax = 0.12f, lightMin = 0.42f)
    BadgeStyle.INVERTED -> if (dark) Color(0xFF0D0D0D) else Color.White
}

// Kolor symbolu na znaczku. color = kolor trybu (potrzebny w INVERTED, gdzie symbol jest kolorowy),
// metal = metal motywu Luxury (w METAL symbol jest metalowy).
fun badgeSymbol(dark: Boolean, style: BadgeStyle = BadgeStyle.TINTED, color: Long = 0xFF808080, metal: Metal? = null): Color =
    when (style) {
        BadgeStyle.INVERTED -> coloredSymbol(color, dark)
        BadgeStyle.METAL -> metal?.symbol(dark) ?: plainSymbol(dark)
        BadgeStyle.TINTED, BadgeStyle.MONO -> plainSymbol(dark)
    }

private fun plainSymbol(dark: Boolean): Color = if (dark) Color(0xFFF4F5F7) else Color(0xFF17181C)

// Metaliczna obwódka znaczka (Luxury) — gradient z refleksami; null w pozostałych stylach.
fun badgeRim(style: BadgeStyle, metal: Metal?): Brush? = if (style == BadgeStyle.METAL && metal != null) metal.brush() else null

// Cienka obwódka znaczka — tylko w INVERTED, bo czarny znaczek na czarnym tle (i biały na białym) inaczej znika.
fun badgeOutline(dark: Boolean, style: BadgeStyle): Color? =
    if (style == BadgeStyle.INVERTED) (if (dark) Color(0xFF4A4A4A) else Color(0xFF9A9A9A)) else null

// Kolor "kółka" w wyborze koloru trybu: to, co na znaczku niesie kolor trybu (tło, a w INVERTED — symbol).
fun badgeSwatch(color: Long, dark: Boolean, style: BadgeStyle): Color =
    if (style == BadgeStyle.INVERTED) badgeSymbol(dark, style, color) else badgeBackground(color, dark, style)

// Tło pod jasny/ciemny symbol. Zachowujemy odcień (i nasycenie, chyba że MONO), zmieniamy tylko jasność (HSL),
// aż kontrast z symbolem będzie dobry (luminancja jak w WCAG). Dotyczy też kolorów własnych z palety HSV.
private fun fitToSymbol(
    color: Long,
    dark: Boolean,
    keepSaturation: Boolean,
    darkMax: Float = 0.27f,  // ciemny motyw: najwyższa dozwolona luminancja tła
    lightMin: Float = 0.13f, // jasny motyw: najniższa dozwolona luminancja tła
): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toInt(), hsl) // Long ARGB → Int, jak (int)kolor w C#
    if (!keepSaturation) hsl[1] = 0f
    fun lum() = Color(ColorUtils.HSLToColor(hsl)).luminance()
    // Symbol to grafika, nie drobny tekst — wystarcza kontrast 3:1 (WCAG dla elementów graficznych).
    if (dark) {
        // Jasny symbol (luminancja ok. 0.91): tło ≤ 0.27.
        while (lum() > darkMax && hsl[2] > 0.05f) hsl[2] -= 0.02f
    } else {
        // Ciemny symbol (luminancja ok. 0.01): tło ≥ 0.13.
        while (lum() < lightMin && hsl[2] < 0.97f) hsl[2] += 0.02f
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

// Kolorowy symbol na czarnym / białym tle (High Contrast). Kontrast co najmniej 4,5:1 —
// na czarnym rozjaśniamy kolor (luminancja ≥ 0,18), na białym przyciemniamy (≤ 0,18).
private fun coloredSymbol(color: Long, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toInt(), hsl)
    fun lum() = Color(ColorUtils.HSLToColor(hsl)).luminance()
    if (dark) {
        while (lum() < 0.18f && hsl[2] < 0.97f) hsl[2] += 0.02f
    } else {
        while (lum() > 0.18f && hsl[2] > 0.03f) hsl[2] -= 0.02f
    }
    return Color(ColorUtils.HSLToColor(hsl))
}
