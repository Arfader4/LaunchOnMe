package pl.rafal.contextlauncher.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pl.rafal.contextlauncher.data.db.AppRestrictionEntity
import pl.rafal.contextlauncher.data.db.CardItemEntity
import pl.rafal.contextlauncher.data.db.FolderAppEntity
import pl.rafal.contextlauncher.data.db.FolderEntity
import pl.rafal.contextlauncher.data.db.LauncherDatabase
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.contextlauncher.data.db.PinnedItemEntity
import pl.rafal.contextlauncher.data.db.SuggestionRuleEntity
import pl.rafal.contextlauncher.ui.theme.Palette
import pl.rafal.contextlauncher.ui.theme.ThemeMode

// Eksport i import całej konfiguracji do jednego pliku JSON (np. przeniesienie z emulatora na telefon).
// Nie przenosimy: widżetów innych aplikacji (ich identyfikatory działają tylko na jednym telefonie),
// naklejek i plików z "Pod ręką" (to kopie plików, nie dane) oraz statystyk uruchomień.
object Backup {
    private const val VERSION = 1

    suspend fun export(context: Context, target: Uri) = withContext(Dispatchers.IO) {
        val db = LauncherDatabase.get(context)
        val theme = ThemePrefs.get(context)
        val prefs = AppPrefs.get(context)

        val json = JSONObject()
            .put("version", VERSION)
            .put("modes", JSONArray(db.modeDao().getAll().map { it.toJson() }))
            .put("cards", JSONArray(db.cardItemDao().getAll().filter(::isPortable).map { it.toJson() }))
            .put("pinned", JSONArray(db.pinnedItemDao().getAll().filter { it.kind != PinnedItemEntity.KIND_FILE }.map { it.toJson() }))
            .put("folders", JSONArray(db.folderDao().getFolders().map { it.toJson() }))
            .put("folderApps", JSONArray(db.folderDao().getApps().map { it.toJson() }))
            .put("rules", JSONArray(db.suggestionDao().getRules().map { it.toJson() }))
            .put(
                "restrictions",
                JSONArray(
                    db.appRestrictionDao().getAll().map {
                        JSONObject().put("modeId", it.modeId).put("packageName", it.packageName)
                            .put("userSerial", it.userSerial).put("kind", it.kind)
                    },
                ),
            )
            .put(
                "prefs",
                JSONObject()
                    .put("themeMode", theme.themeMode.value.name)
                    .put("defaultPalette", theme.defaultPalette.value.name)
                    .put("homeModeId", prefs.homeModeId.value ?: -1)
                    .put("leftHanded", prefs.leftHanded.value)
                    .put("returnToHome", prefs.returnToHome.value)
                    .put("autoSwitch", prefs.autoSwitch.value)
                    .put("uniformLook", prefs.uniformLook.value)
                    .put("showLabels", prefs.showLabels.value),
            )

        val stream = context.contentResolver.openOutputStream(target, "wt") ?: error("Nie można zapisać pliku")
        stream.bufferedWriter().use { it.write(json.toString(2)) }
    }

    // Import ZASTĘPUJE obecną konfigurację. Wszystko w jednej transakcji: błąd w połowie = nic się nie zmienia.
    suspend fun import(context: Context, source: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
            ?: error("Nie można odczytać pliku")
        val json = JSONObject(text)
        require(json.optInt("version") == VERSION) { "Nieobsługiwana wersja pliku" }

        val db = LauncherDatabase.get(context)
        var newHomeId: Long? = null

        db.withTransaction {
            db.modeDao().deleteAll()   // kaskadowo: karty, Pod ręką, reguły, statystyki
            db.folderDao().deleteAll() // kaskadowo: aplikacje w folderach

            // Nowe id nadaje baza, więc budujemy mapy "stare id → nowe id" (jak przy imporcie z kluczami IDENTITY).
            val modeIds = mutableMapOf<Long, Long>()
            json.getJSONArray("modes").objects().forEach { o ->
                modeIds[o.getLong("id")] = db.modeDao().insert(o.toMode())
            }

            // Foldery wstawiamy "od korzenia w dół", bo podfolder potrzebuje już istniejącego rodzica.
            val folderIds = mutableMapOf<Long, Long>()
            var pending = json.getJSONArray("folders").objects()
            while (pending.isNotEmpty()) {
                val ready = pending.filter { o -> o.optLongOrNull("parentId")?.let { it in folderIds } ?: true }
                if (ready.isEmpty()) break // uszkodzone drzewo (brakujący rodzic) — resztę pomijamy
                ready.forEach { o ->
                    val parent = o.optLongOrNull("parentId")?.let { folderIds[it] }
                    folderIds[o.getLong("id")] = db.folderDao().insertFolder(
                        FolderEntity(parentId = parent, name = o.getString("name"), createdAt = o.optLong("createdAt")),
                    )
                }
                pending = pending - ready.toSet()
            }

            json.getJSONArray("folderApps").objects().mapNotNull { o ->
                folderIds[o.getLong("folderId")]?.let { folderId ->
                    FolderAppEntity(
                        folderId = folderId,
                        packageName = o.getString("packageName"),
                        className = o.getString("className"),
                        userSerial = o.getLong("userSerial"),
                    )
                }
            }.let { if (it.isNotEmpty()) db.folderDao().insertApps(it) }

            json.getJSONArray("cards").objects().forEach { o ->
                val modeId = modeIds[o.getLong("modeId")] ?: return@forEach
                var config = o.optStringOrNull("config")
                // Widżet folderu wskazuje na folder — przepinamy go na nowe id.
                if (o.optStringOrNull("widgetKind") == CustomWidgetKind.FOLDER.name && config != null) {
                    val cfg = JSONObject(config)
                    folderIds[cfg.optLong("folderId", -1)]?.let { cfg.put("folderId", it) }
                    config = cfg.toString()
                }
                db.cardItemDao().insert(o.toCardItem(modeId, config))
            }

            json.getJSONArray("pinned").objects().forEach { o ->
                val modeId = modeIds[o.getLong("modeId")] ?: return@forEach
                db.pinnedItemDao().insert(
                    PinnedItemEntity(
                        modeId = modeId,
                        kind = o.getString("kind"),
                        title = o.getString("title"),
                        uri = o.optStringOrNull("uri"),
                        text = o.optStringOrNull("text"),
                        createdAt = o.optLong("createdAt"),
                        archivedAt = o.optLongOrNull("archivedAt"),
                    ),
                )
            }

            json.getJSONArray("rules").objects().forEach { o ->
                val modeId = modeIds[o.getLong("modeId")] ?: return@forEach
                db.suggestionDao().insertRule(
                    SuggestionRuleEntity(modeId = modeId, type = o.getString("type"), params = o.getString("params")),
                )
            }

            // Brak w starszych plikach — optJSONArray zwróci wtedy null.
            json.optJSONArray("restrictions")?.objects()?.mapNotNull { o ->
                modeIds[o.getLong("modeId")]?.let { modeId ->
                    AppRestrictionEntity(
                        modeId = modeId,
                        packageName = o.getString("packageName"),
                        userSerial = o.getLong("userSerial"),
                        kind = o.getString("kind"),
                    )
                }
            }?.let { if (it.isNotEmpty()) db.appRestrictionDao().upsert(it) }

            newHomeId = json.getJSONObject("prefs").optLongOrNull("homeModeId")?.let { modeIds[it] }
        }

        // Ustawienia poza bazą (SharedPreferences).
        val p = json.getJSONObject("prefs")
        ThemePrefs.get(context).apply {
            runCatching { setThemeMode(ThemeMode.valueOf(p.getString("themeMode"))) }
            Palette.fromName(p.optString("defaultPalette"))?.let { setDefaultPalette(it) }
        }
        AppPrefs.get(context).apply {
            setHomeModeId(newHomeId)
            leftHanded.set(p.optBoolean("leftHanded", false))
            returnToHome.set(p.optBoolean("returnToHome", true))
            autoSwitch.set(p.optBoolean("autoSwitch", false))
            uniformLook.set(p.optBoolean("uniformLook", false))
            showLabels.set(p.optBoolean("showLabels", true))
        }
    }

    // Co da się przenieść na inny telefon.
    private fun isPortable(item: CardItemEntity): Boolean =
        item.type != CardItemEntity.TYPE_WIDGET && item.widgetKind != CustomWidgetKind.STICKER.name

    // --- Zamiana encji na JSON i z powrotem ---

    private fun ModeEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("color", color).put("sortOrder", sortOrder)
        .put("lastActiveAt", lastActiveAt).putOpt("icon", icon).putOpt("palette", palette)
        .putOpt("accent", accent).putOpt("settings", settings)

    private fun JSONObject.toMode() = ModeEntity(
        name = getString("name"),
        color = getLong("color"),
        sortOrder = optInt("sortOrder"),
        lastActiveAt = optLong("lastActiveAt"),
        icon = optStringOrNull("icon"),
        palette = optStringOrNull("palette"),
        accent = optLongOrNull("accent"),
        settings = optStringOrNull("settings"),
    )

    private fun CardItemEntity.toJson() = JSONObject()
        .put("modeId", modeId).put("type", type).put("packageName", packageName).put("className", className)
        .put("userSerial", userSerial).put("x", x).put("y", y).put("w", w).put("h", h)
        .putOpt("widgetKind", widgetKind).putOpt("config", config)

    private fun JSONObject.toCardItem(modeId: Long, config: String?) = CardItemEntity(
        modeId = modeId,
        type = getString("type"),
        packageName = getString("packageName"),
        className = getString("className"),
        userSerial = getLong("userSerial"),
        x = getInt("x"), y = getInt("y"), w = getInt("w"), h = getInt("h"),
        widgetKind = optStringOrNull("widgetKind"),
        config = config,
    )

    private fun PinnedItemEntity.toJson() = JSONObject()
        .put("modeId", modeId).put("kind", kind).put("title", title).putOpt("uri", uri).putOpt("text", text)
        .put("createdAt", createdAt).putOpt("archivedAt", archivedAt)

    private fun FolderEntity.toJson() = JSONObject()
        .put("id", id).putOpt("parentId", parentId).put("name", name).put("createdAt", createdAt)

    private fun FolderAppEntity.toJson() = JSONObject()
        .put("folderId", folderId).put("packageName", packageName).put("className", className).put("userSerial", userSerial)

    private fun SuggestionRuleEntity.toJson() = JSONObject()
        .put("modeId", modeId).put("type", type).put("params", params)

    // Pomocnicze: JSONArray → lista obiektów; brakujące pola → null (org.json domyślnie zwraca 0 albo "").
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key).takeIf { it != -1L } else null
}
