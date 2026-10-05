package pl.rafal.onthemes

// Katalog wbudowanych motywów. object = singleton (jak klasa statyczna w C#).
// Kolejność listy = kolejność kart w ustawieniach i w OnThemes.
object Themes {
    val NIGHT = ThemeSpec(
        id = "NIGHT",
        labelRes = R.string.ot_theme_night,
        family = ThemeFamily.NIGHT,
        light = Roles(0xFFF4F2EE, 0xFFFFFFFF, 0xFFE9E6E0, 0xFFD3CFC7, 0xFF1B1C1E, 0xFF5C6068, 0xFFB26A00, 0xFFFFFFFF),
        dark = Roles(0xFF0F1012, 0xFF1A1C20, 0xFF23262B, 0xFF2E3238, 0xFFECEAE4, 0xFFA3A7AE, 0xFFF0A844, 0xFF1A1206),
        palette = listOf(0xFFF0A844, 0xFFE0736B, 0xFF7FD6AE, 0xFF4FB3BF, 0xFF8FB2FF, 0xFFB9A5FF, 0xFFECEAE4),
    )

    val VINTAGE = ThemeSpec(
        id = "VINTAGE",
        labelRes = R.string.ot_theme_vintage,
        family = ThemeFamily.VINTAGE,
        light = Roles(0xFFF6EEDF, 0xFFFFF9EF, 0xFFEEDFC8, 0xFFD8C3A5, 0xFF3B2A1D, 0xFF7A6450, 0xFF8B5A2B, 0xFFFFFFFF),
        dark = Roles(0xFF1C1611, 0xFF2A2119, 0xFF362A20, 0xFF4A3A2C, 0xFFF1E6D6, 0xFFC2AE96, 0xFFD4A373, 0xFF2A1A0C),
        palette = listOf(0xFFB5655A, 0xFFD4A373, 0xFF8B5A2B, 0xFFC9A227, 0xFF6B8F71, 0xFF5E7C8C, 0xFF8E6C8A),
    )

    // Dawniej "CONTRAST" — stare zapisy (tryby w bazie, kopie zapasowe) trafiają tu przez ALIASES.
    val HIGH_CONTRAST = ThemeSpec(
        id = "HIGH_CONTRAST",
        labelRes = R.string.ot_theme_high_contrast,
        family = ThemeFamily.HIGH_CONTRAST,
        light = Roles(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF0F0F0, 0xFF7A7A7A, 0xFF000000, 0xFF333333, 0xFF000000, 0xFFFFFFFF),
        dark = Roles(0xFF000000, 0xFF0D0D0D, 0xFF1A1A1A, 0xFF5A5A5A, 0xFFFFFFFF, 0xFFD0D0D0, 0xFFFFFFFF, 0xFF000000),
        palette = listOf(0xFFFF2D55, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF00E5FF, 0xFF0A84FF, 0xFFBF5AF2),
    )

    // Własny motyw z kreatora. Role są tylko zastępcze — prawdziwe kolory bierze z CustomTheme.
    val CUSTOM = ThemeSpec(
        id = "CUSTOM",
        labelRes = R.string.ot_theme_custom,
        family = ThemeFamily.CUSTOM,
        light = CustomColors().roles(),
        dark = CustomColors().roles(),
        palette = listOf(0xFFEF476F, 0xFFFFD166, 0xFF84CC16, 0xFF4DF5CD, 0xFF118AB2, 0xFFA78BFA),
    )

    // Dawniej "ELEGANT" (złoto na butelkowej zieleni) — zalążek Luxury; metale i bazy do wyboru od T3.
    val LUXURY = ThemeSpec(
        id = "LUXURY",
        labelRes = R.string.ot_theme_luxury,
        family = ThemeFamily.LUXURY,
        light = Roles(0xFFF3F1E8, 0xFFFFFFFF, 0xFFE3E8E1, 0xFFC3CFC5, 0xFF17261E, 0xFF4F6358, 0xFF8C6D1F, 0xFFFFFFFF),
        dark = Roles(0xFF0E1A15, 0xFF15251E, 0xFF1D3128, 0xFF2F4A3D, 0xFFEDE6D3, 0xFFAFC0B4, 0xFFD4AF37, 0xFF1E1A08),
        // złoto, miedź, brąz, srebro + butelkowa zieleń, purpura, bordo, granat
        palette = listOf(0xFFD4AF37, 0xFFB87333, 0xFFCD7F32, 0xFFC0C0C8, 0xFF1F6B4A, 0xFF6A2C70, 0xFF8C1C3A, 0xFF1F3A68),
    )

    // Kolejność jak dotąd w launcherze (Nocny, Vintage, Kontrast, Własny, Elegancki → Luxury).
    val all: List<ThemeSpec> = listOf(NIGHT, VINTAGE, HIGH_CONTRAST, CUSTOM, LUXURY)

    // Stare nazwy schematów z wersji ≤ 1.4 → nowe id. Dzięki temu baza i kopie zapasowe nie wymagają migracji.
    private val ALIASES = mapOf("CONTRAST" to "HIGH_CONTRAST", "ELEGANT" to "LUXURY")

    fun normalizeId(id: String?): String? = id?.let { ALIASES[it] ?: it }

    // Motyw po id (także starym); null, gdy id puste albo nieznane (wtedy wywołujący bierze domyślny).
    fun find(id: String?): ThemeSpec? {
        val key = normalizeId(id) ?: return null
        return all.firstOrNull { it.id == key }
    }
}
