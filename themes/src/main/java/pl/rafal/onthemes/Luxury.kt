package pl.rafal.onthemes

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Luxury = metal × baza. Metal daje akcent, obwódki i symbole znaczków (z połyskiem), baza — kolor tła.
// Każde połączenie to osobny ThemeSpec o id "LUXURY:<METAL>:<BAZA>"; złoto na butelkowej zieleni ma
// stare id "LUXURY" (dawny "Elegancki"), więc zapisane tryby i kopie zapasowe działają bez zmian.

enum class Metal(@StringRes private val labelRes: Int, val deep: Long, val main: Long, val light: Long) {
    GOLD(R.string.ot_metal_gold, 0xFF7A5A12, 0xFFD4AF37, 0xFFFFE9A8),
    SILVER(R.string.ot_metal_silver, 0xFF5E636B, 0xFFC0C4CA, 0xFFF4F6F9),
    COPPER(R.string.ot_metal_copper, 0xFF73381A, 0xFFB87333, 0xFFF5C6A0),
    BRONZE(R.string.ot_metal_bronze, 0xFF5E3F14, 0xFFCD7F32, 0xFFF2CF9E),
    ;

    val label: String get() = OnThemesText.get(labelRes)

    // Połysk metalu: ciemny → jasny refleks → właściwy kolor → ciemny → refleks (jak światło na wygiętej blasze).
    fun brush(): Brush = Brush.linearGradient(listOf(Color(deep), Color(light), Color(main), Color(deep), Color(light)))

    // Symbol na znaczku: jasny metal w ciemnym motywie, ciemny metal w jasnym (inaczej zginąłby na jasnym tle).
    fun symbol(dark: Boolean): Color = if (dark) Color(light) else Color(deep)
}

// Bazy: kolory ciemnej wersji (tło, powierzchnie, ramka, tekst) + "klejnot" — nasycony kolor bazy do palety.
enum class LuxuryBase(
    @StringRes private val labelRes: Int,
    val bg: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val outline: Long,
    val text: Long,
    val textDim: Long,
    val jewel: Long,
) {
    BOTTLE(R.string.ot_base_bottle, 0xFF0E1A15, 0xFF15251E, 0xFF1D3128, 0xFF2F4A3D, 0xFFEDE6D3, 0xFFAFC0B4, 0xFF1F6B4A),
    PURPLE(R.string.ot_base_purple, 0xFF160E1C, 0xFF21152A, 0xFF2C1C37, 0xFF45304F, 0xFFEFE6F2, 0xFFBFAEC8, 0xFF6A2C70),
    NAVY(R.string.ot_base_navy, 0xFF0B1220, 0xFF121B2E, 0xFF1A253C, 0xFF2C3A55, 0xFFE8ECF4, 0xFFA9B4C8, 0xFF1F3A68),
    BURGUNDY(R.string.ot_base_burgundy, 0xFF1A0C10, 0xFF271219, 0xFF331822, 0xFF4E2A35, 0xFFF2E4E6, 0xFFC9A9AF, 0xFF8C1C3A),
    BLACK(R.string.ot_base_black, 0xFF0A0A0A, 0xFF141414, 0xFF1E1E1E, 0xFF333333, 0xFFF0EDE6, 0xFFB5B0A6, 0xFF2B2B2B),
    ;

    val label: String get() = OnThemesText.get(labelRes)
}

object Luxury {
    const val CATALOG_ID = "LUXURY"

    private val cache = HashMap<String, ThemeSpec>()

    fun id(metal: Metal, base: LuxuryBase): String =
        if (metal == Metal.GOLD && base == LuxuryBase.BOTTLE) CATALOG_ID else "$CATALOG_ID:${metal.name}:${base.name}"

    // Jeden obiekt na połączenie (cache) — porównywanie i StateFlow nie widzą "zmian" przy tym samym wyborze.
    fun spec(metal: Metal, base: LuxuryBase): ThemeSpec = synchronized(cache) {
        cache.getOrPut(id(metal, base)) { build(metal, base) }
    }

    // "LUXURY" → złoto / butelkowa zieleń; "LUXURY:COPPER:NAVY" → miedź na granacie; brak części = domyślne.
    fun fromId(id: String): ThemeSpec {
        val parts = id.split(':')
        val metal = Metal.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: Metal.GOLD
        val base = LuxuryBase.entries.firstOrNull { it.name == parts.getOrNull(2) } ?: LuxuryBase.BOTTLE
        return spec(metal, base)
    }

    private fun build(metal: Metal, base: LuxuryBase): ThemeSpec {
        // Jasna wersja: kość słoniowa lekko zabarwiona bazą, tekst w ciemnym kolorze bazy, akcent = ciemny metal.
        val light = Roles(
            background = mix(0xFFF6F2E8, base.jewel, 0.05f),
            surface = 0xFFFFFFFF,
            surfaceVariant = mix(0xFFECE6D8, base.jewel, 0.10f),
            outline = mix(0xFFD6CDB8, base.jewel, 0.18f),
            onBackground = base.bg,
            onSurfaceVariant = mix(base.bg, 0xFFFFFFFF, 0.40f),
            primary = metal.deep,
            onPrimary = 0xFFFFFFFF,
        )
        val dark = Roles(base.bg, base.surface, base.surfaceVariant, base.outline, base.text, base.textDim, metal.main, base.bg)
        return ThemeSpec(
            id = id(metal, base),
            labelRes = R.string.ot_theme_luxury,
            family = ThemeFamily.LUXURY,
            light = light,
            dark = dark,
            // cztery metale + klejnoty baz (butelkowa zieleń, purpura, granat, bordo) — po odcieniu
            palette = Metal.entries.map { it.main } + LuxuryBase.entries.filter { it != LuxuryBase.BLACK }.map { it.jewel },
            badgeStyle = BadgeStyle.METAL,
            finish = Finish.METAL,
            metal = metal,
            luxuryBase = base,
            catalogId = CATALOG_ID,
        )
    }
}
