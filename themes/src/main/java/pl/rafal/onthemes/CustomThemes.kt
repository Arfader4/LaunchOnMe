package pl.rafal.onthemes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

// Motyw własny: nazwa (null = "Własny"), 4 kolory z kreatora i styl znaczków.
// Pierwszy ma id "CUSTOM" (dawny jedyny "Własny schemat"), kolejne "custom:2", "custom:3"…
data class CustomThemeDef(
    val id: String,
    val name: String?,
    val colors: CustomColors,
    val badge: BadgeStyle = BadgeStyle.TINTED,
)

// Lista motywów własnych jako stan Compose: ekrany, które ją czytają (galeria, launcher), przerysują się same.
// Zapisuje ją ThemeStore (JSON w SharedPreferences) — tu tylko pamięć i zamiana na ThemeSpec.
object CustomThemes {
    const val LEGACY_ID = "CUSTOM"
    private const val PREFIX = "custom:"

    var defs by mutableStateOf<List<CustomThemeDef>>(emptyList())
        internal set

    private val specs = HashMap<String, ThemeSpec>()

    fun isCustomId(id: String): Boolean = id == LEGACY_ID || id.startsWith(PREFIX)

    fun def(id: String): CustomThemeDef? = defs.firstOrNull { it.id == id }

    // ThemeSpec motywu własnego. Kolory, nazwę i styl czyta "na żywo" z defs (ThemeSpec ich nie kopiuje),
    // więc zmiana w kreatorze od razu przemalowuje launcher — także tam, gdzie StateFlow trzyma stary obiekt.
    fun spec(id: String): ThemeSpec = synchronized(specs) {
        specs.getOrPut(id) {
            ThemeSpec(
                id = id,
                labelRes = R.string.ot_theme_custom,
                family = ThemeFamily.CUSTOM,
                light = CustomColors().roles(),
                dark = CustomColors().roles(),
                palette = listOf(0xFFEF476F, 0xFFFFD166, 0xFF84CC16, 0xFF4DF5CD, 0xFF118AB2, 0xFFA78BFA),
            )
        }
    }

    val all: List<ThemeSpec> get() = defs.map { spec(it.id) }

    // Pierwsze wolne id: custom:2, custom:3… ("CUSTOM" zajmuje pierwszy).
    fun nextId(): String {
        var n = 2
        while (defs.any { it.id == "$PREFIX$n" }) n++
        return if (defs.none { it.id == LEGACY_ID }) LEGACY_ID else "$PREFIX$n"
    }

    internal fun toJson(list: List<CustomThemeDef>): String = JSONArray().apply {
        list.forEach { d ->
            put(
                JSONObject()
                    .put("id", d.id)
                    .putOpt("name", d.name)
                    .put("bg", d.colors.background)
                    .put("surface", d.colors.surface)
                    .put("accent", d.colors.accent)
                    .put("text", d.colors.text)
                    .put("badge", d.badge.name),
            )
        }
    }.toString()

    internal fun fromJson(text: String): List<CustomThemeDef> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { isCustomId(it) } ?: return@mapNotNull null
            val d = CustomColors()
            CustomThemeDef(
                id = id,
                name = if (o.has("name") && !o.isNull("name")) o.getString("name") else null,
                colors = CustomColors(
                    background = o.optLong("bg", d.background),
                    surface = o.optLong("surface", d.surface),
                    accent = o.optLong("accent", d.accent),
                    text = o.optLong("text", d.text),
                ),
                badge = runCatching { BadgeStyle.valueOf(o.optString("badge")) }.getOrDefault(BadgeStyle.TINTED)
                    .let { if (it == BadgeStyle.METAL) BadgeStyle.TINTED else it }, // metal tylko w Luxury
            )
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())
}
