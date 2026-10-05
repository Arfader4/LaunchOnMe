package pl.rafal.onthemes

// Katalog wbudowanych motywów. object = singleton (jak klasa statyczna w C#).
// Kolejność listy = kolejność kart w ustawieniach i w OnThemes.
object Themes {
    // Neutralny: grafit / biel, niebieski akcent — "jak w zwykłym telefonie".
    val STANDARD = ThemeSpec(
        id = "STANDARD",
        labelRes = R.string.ot_theme_standard,
        family = ThemeFamily.STANDARD,
        light = Roles(0xFFF7F7F8, 0xFFFFFFFF, 0xFFEDEEF0, 0xFFD5D7DB, 0xFF16181C, 0xFF5B6069, 0xFF2F6FED, 0xFFFFFFFF),
        dark = Roles(0xFF121316, 0xFF1C1E22, 0xFF25282D, 0xFF33373D, 0xFFECEDEF, 0xFFA4A9B1, 0xFF8AB4FF, 0xFF0B1A33),
        palette = listOf(0xFFE5484D, 0xFFF59E0B, 0xFF22C55E, 0xFF14B8A6, 0xFF3B82F6, 0xFF8B5CF6, 0xFFEC4899, 0xFF94A3B8),
    )

    // Czysta czerń (piksele OLED wyłączone = mniej prądu). Zawsze ciemny; żywe kolory jak w nocnym trybie telefonu.
    val AMOLED = ThemeSpec(
        id = "AMOLED",
        labelRes = R.string.ot_theme_amoled,
        family = ThemeFamily.AMOLED,
        light = Roles(0xFF000000, 0xFF0A0A0A, 0xFF141414, 0xFF262626, 0xFFF5F5F5, 0xFFA0A0A0, 0xFF5EEAD4, 0xFF00201B),
        dark = Roles(0xFF000000, 0xFF0A0A0A, 0xFF141414, 0xFF262626, 0xFFF5F5F5, 0xFFA0A0A0, 0xFF5EEAD4, 0xFF00201B),
        palette = listOf(0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF32D74B, 0xFF5EEAD4, 0xFF64D2FF, 0xFF0A84FF, 0xFFBF5AF2),
        darkOnly = true,
    )

    // Odcienie szarości: znaczki szare (jasność z koloru trybu), akcent też szary.
    val BW = ThemeSpec(
        id = "BW",
        labelRes = R.string.ot_theme_bw,
        family = ThemeFamily.BW,
        light = Roles(0xFFFFFFFF, 0xFFF4F4F4, 0xFFE6E6E6, 0xFFBDBDBD, 0xFF0A0A0A, 0xFF555555, 0xFF1A1A1A, 0xFFFFFFFF),
        dark = Roles(0xFF0B0B0B, 0xFF171717, 0xFF222222, 0xFF3A3A3A, 0xFFF2F2F2, 0xFFA8A8A8, 0xFFE6E6E6, 0xFF0B0B0B),
        palette = listOf(0xFF1A1A1A, 0xFF3D3D3D, 0xFF666666, 0xFF8F8F8F, 0xFFB8B8B8, 0xFFE0E0E0),
        badgeStyle = BadgeStyle.MONO,
    )

    // Jasne, mało nasycone kolory; ciemna wersja to "pastele nocą" (śliwkowe tło).
    val PASTEL = ThemeSpec(
        id = "PASTEL",
        labelRes = R.string.ot_theme_pastel,
        family = ThemeFamily.PASTEL,
        light = Roles(0xFFFBF7FA, 0xFFFFFFFF, 0xFFF2EAF1, 0xFFE0D3DE, 0xFF2E2433, 0xFF7A6C80, 0xFFB57EDC, 0xFFFFFFFF),
        dark = Roles(0xFF1B1820, 0xFF25212B, 0xFF2F2A36, 0xFF3D3745, 0xFFF3ECF6, 0xFFBBAFC2, 0xFFD9B8F2, 0xFF2A1838),
        palette = listOf(0xFFFFB3BA, 0xFFFFDFBA, 0xFFFFF5BA, 0xFFBAFFC9, 0xFFBAE1FF, 0xFFD7BAFF, 0xFFFFC6E7),
    )

    // Kolory systemu (Material You / One UI) — liczone z tapety, Android 12+. Role poniżej to tylko zapas.
    val SYSTEM = ThemeSpec(
        id = "SYSTEM",
        labelRes = R.string.ot_theme_system,
        family = ThemeFamily.SYSTEM,
        light = Roles(0xFFF7F7F8, 0xFFFFFFFF, 0xFFEDEEF0, 0xFFD5D7DB, 0xFF16181C, 0xFF5B6069, 0xFF2F6FED, 0xFFFFFFFF),
        dark = Roles(0xFF121316, 0xFF1C1E22, 0xFF25282D, 0xFF33373D, 0xFFECEDEF, 0xFFA4A9B1, 0xFF8AB4FF, 0xFF0B1A33),
        palette = listOf(0xFF2F6FED, 0xFF14B8A6, 0xFF8B5CF6),
    )

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
        badgeStyle = BadgeStyle.INVERTED, // czarne / białe tło znaczka + kolorowy symbol
    )

    // Pierwszy motyw własny (dawny jedyny "Własny schemat"). Kolejne: CustomThemes.
    val CUSTOM: ThemeSpec get() = CustomThemes.spec(CustomThemes.LEGACY_ID)

    // Dawniej "ELEGANT" (złoto na butelkowej zieleni). Pozostałe połączenia metal × baza: Luxury.spec(...).
    val LUXURY: ThemeSpec = Luxury.spec(Metal.GOLD, LuxuryBase.BOTTLE)

    // Kolejność kart: od codziennych, przez wyraziste, do systemowego i własnego.
    // Systemowy tylko na Androidzie 12+ (wcześniej system nie liczy kolorów z tapety).
    private val catalog: List<ThemeSpec> =
        listOf(STANDARD, NIGHT, AMOLED, HIGH_CONTRAST, BW, VINTAGE, PASTEL, LUXURY, SYSTEM)

    // Wbudowane + motywy własne (na końcu, w kolejności tworzenia).
    val all: List<ThemeSpec>
        get() = (if (SystemColors.available) catalog else catalog.filter { it.family != ThemeFamily.SYSTEM }) + CustomThemes.all

    // Stare nazwy schematów z wersji ≤ 1.4 → nowe id. Dzięki temu baza i kopie zapasowe nie wymagają migracji.
    private val ALIASES = mapOf("CONTRAST" to "HIGH_CONTRAST", "ELEGANT" to "LUXURY")

    fun normalizeId(id: String?): String? = id?.let { ALIASES[it] ?: it }

    // Motyw po id (także starym); null, gdy id puste albo nieznane (wtedy wywołujący bierze domyślny).
    fun find(id: String?): ThemeSpec? {
        val key = normalizeId(id) ?: return null
        // Warianty Luxury nie są osobno na liście — budujemy je z id ("LUXURY:<METAL>:<BAZA>").
        if (key == Luxury.CATALOG_ID || key.startsWith(Luxury.CATALOG_ID + ":")) return Luxury.fromId(key)
        // Motyw własny tylko, jeśli jeszcze istnieje (usunięty → null → tryb wraca do motywu globalnego).
        if (CustomThemes.isCustomId(key)) return if (CustomThemes.def(key) != null) CustomThemes.spec(key) else null
        return all.firstOrNull { it.id == key }
    }
}
