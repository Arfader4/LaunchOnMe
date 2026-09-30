package pl.rafal.contextlauncher.data

import org.json.JSONObject

// Układ karty konkretnego trybu: odstęp między elementami i (opcjonalnie) własny rozmiar ikon aplikacji.
// iconCells = null → jak w Ustawieniach (globalnie).
data class ModeLayout(val gap: Int = 0, val iconCells: Int? = null) {
    fun toJson(): JSONObject = JSONObject().put("gap", gap).putOpt("icons", iconCells)

    companion object {
        val DEFAULT = ModeLayout()

        // Wszystkie tryby w jednym JSON-ie w ustawieniach: {"12": {"gap": 4, "icons": 1}, ...}
        fun parseAll(json: String): Map<Long, ModeLayout> = runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().mapNotNull { k ->
                val id = k.toLongOrNull() ?: return@mapNotNull null
                val v = o.optJSONObject(k) ?: return@mapNotNull null
                id to ModeLayout(
                    gap = v.optInt("gap", 0).coerceIn(0, 16),
                    iconCells = if (v.has("icons") && !v.isNull("icons")) v.getInt("icons").coerceIn(1, 2) else null,
                )
            }.toMap()
        }.getOrDefault(emptyMap())

        fun writeAll(map: Map<Long, ModeLayout>): String =
            JSONObject().apply { map.forEach { (id, l) -> put(id.toString(), l.toJson()) } }.toString()
    }
}
