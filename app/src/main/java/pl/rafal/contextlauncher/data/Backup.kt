package pl.rafal.contextlauncher.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
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

// Eksport i import całej konfiguracji (np. przeniesienie z emulatora na telefon albo nowy telefon).
// Dwa formaty: sam JSON (ustawienia + notatki OnHand) albo .zip = ten sam JSON + pliki: naklejki z kart,
// tapety trybów, biblioteka i tablice StickOnMe, załączniki notatek OnHand.
// Ścieżki plików w JSON-ie zapisujemy względnie ("@FILES@/…"), bo na innym
// telefonie (albo profilu) folder aplikacji może się nazywać inaczej.
// Nigdy nie przenosimy: widżetów innych aplikacji (ich identyfikatory działają tylko na jednym telefonie),
// plików przypiętych na kartach (rodzaj FILE — kopie cudzych plików) i statystyk uruchomień.
object Backup {
    private const val VERSION = 3          // 3 = notatki OnHand; 2 = naklejki, tapety i układy trybów (czytamy też 1–2)
    private const val FILES = "@FILES@"   // znacznik folderu aplikacji w ścieżkach
    private const val CONFIG = "config.json"

    suspend fun export(context: Context, target: Uri, withFiles: Boolean = false) = withContext(Dispatchers.IO) {
        val db = LauncherDatabase.get(context)
        val theme = ThemePrefs.get(context)
        val prefs = AppPrefs.get(context)
        val root = context.filesDir.canonicalFile
        val files = linkedSetOf<File>() // co trafi do .zip (zbiór — ten sam plik raz)

        // Ścieżka w pamięci aplikacji → "@FILES@/stickers/…" (i plik do spakowania). Cudze ścieżki → null.
        fun portable(path: String): String? {
            val f = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
            if (!f.isFile || !f.path.startsWith(root.path + File.separator)) return null
            files += f
            return FILES + "/" + f.relativeTo(root).invariantSeparatorsPath
        }

        val cards = db.cardItemDao().getAll().filter { isPortable(it, withFiles) }.mapNotNull { card ->
            if (card.widgetKind != CustomWidgetKind.STICKER.name) return@mapNotNull card
            // Naklejka: pliki (obecny i pierwsza wersja) jako ścieżki względne; bez pliku naklejka nie ma sensu.
            val cfg = runCatching { JSONObject(card.config ?: "{}") }.getOrDefault(JSONObject())
            for (key in listOf("file", "original")) {
                val path = cfg.optString(key)
                if (path.isNotBlank()) {
                    val rel = portable(path)
                    if (rel != null) cfg.put(key, rel) else cfg.remove(key)
                }
            }
            if (cfg.has("file")) card.copy(config = cfg.toString()) else null
        }
        val modeList = db.modeDao().getAll()

        val json = JSONObject()
            .put("version", VERSION)
            // Stare położenie (obie pisownie: /data/user/0/… i kanoniczna /data/data/…) — do poprawienia ścieżek w tablicach.
            .put("filesDir", context.filesDir.absolutePath)
            .put("filesDirCanonical", root.path)
            .put("modes", JSONArray(modeList.map { it.toJson() }))
            .put("cards", JSONArray(cards.map { it.toJson() }))
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
                    .put("customTheme", pl.rafal.contextlauncher.ui.theme.CustomTheme.colors.let {
                        JSONObject().put("bg", it.background).put("surface", it.surface).put("accent", it.accent).put("text", it.text)
                    })
                    .put("homeModeId", prefs.homeModeId.value ?: -1)
                    .put("leftHanded", prefs.leftHanded.value)
                    .put("returnToHome", prefs.returnToHome.value)
                    .put("autoSwitch", prefs.autoSwitch.value)
                    .put("uniformLook", prefs.uniformLook.value)
                    .put("showLabels", prefs.showLabels.value)
                    .put("modeLayouts", prefs.modeLayouts.value), // odstępy i rozmiar ikon per tryb (klucze = id trybów)
            )

        // OnHand: notatki zawsze (to sam tekst), załączniki tylko w .zip.
        json.put("onhand", pl.rafal.onhand.OnHandBackup.export(context))
        if (withFiles) {
            pl.rafal.onhand.OnHandBackup.attachmentFiles(context).forEach { portable(it.path) }
        }

        if (withFiles) {
            // Tapety trybów (i domyślna: modeId = -1) razem z kadrem i ekranem blokady.
            val wallpapers = WallpaperStore(context).saved(modeList.map { it.id }).mapNotNull { w ->
                portable(w.path)?.let { rel ->
                    JSONObject().put("modeId", w.modeId ?: -1L).put("file", rel).put("lock", w.lock)
                        .putOpt("crop", w.crop?.let { "${it.left},${it.top},${it.right},${it.bottom}" })
                }
            }
            json.put("wallpapers", JSONArray(wallpapers))
            // StickOnMe: cała biblioteka naklejek i wszystkie tablice (JSON, miniatury, zdjęcia w assets/).
            listOf("stickers/library", "stickers/boards").forEach { dir ->
                File(root, dir).walkTopDown().filter { it.isFile }.forEach { files += it.canonicalFile }
            }
        }

        val stream = context.contentResolver.openOutputStream(target, "wt") ?: error(AppText.get(R.string.data_backup_cannot_write))
        if (!withFiles) {
            stream.bufferedWriter().use { it.write(json.toString(2)) }
        } else {
            // ZipOutputStream ≈ System.IO.Compression.ZipArchive: najpierw config.json, potem pliki w files/…
            ZipOutputStream(stream.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(CONFIG))
                zip.write(json.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                files.forEach { f ->
                    runCatching {
                        zip.putNextEntry(ZipEntry("files/" + f.relativeTo(root).invariantSeparatorsPath))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    // Czy plik to .zip (zaczyna się od "PK"), a nie sam JSON.
    private fun isZip(context: Context, source: Uri): Boolean =
        context.contentResolver.openInputStream(source)?.use { it.read() == 0x50 && it.read() == 0x4B }
            ?: error(AppText.get(R.string.data_backup_cannot_read))

    // Rozpakowanie .zip: config.json musi być pierwszy (sprawdzamy wersję, zanim cokolwiek zapiszemy),
    // pliki trafiają tylko do stickers/ i wallpapers/ w folderze aplikacji (ochrona przed "../" w nazwach),
    // a załączniki OnHand do folderu przejściowego onhand_import/.
    private fun unzip(context: Context, source: Uri, root: File): String {
        val stream = context.contentResolver.openInputStream(source) ?: error(AppText.get(R.string.data_backup_cannot_read))
        return ZipInputStream(stream.buffered()).use { zip ->
            val first = zip.nextEntry ?: error(AppText.get(R.string.data_backup_empty_file))
            require(first.name == CONFIG) { AppText.get(R.string.data_backup_not_a_backup) }
            val text = zip.readBytes().toString(Charsets.UTF_8)
            val version = JSONObject(text).optInt("version")
            require(version in 1..VERSION) { AppText.get(R.string.data_backup_unsupported_version) }
            val allowed = listOf(File(root, "stickers"), File(root, "wallpapers")).map { it.canonicalPath + File.separator }
            // Załączniki OnHand: do folderu przejściowego (OnHandBackup.import rozkłada je potem do nowych notatek).
            val staging = File(root, pl.rafal.onhand.OnHandBackup.STAGING)
            staging.deleteRecursively()
            val stagingDir = staging.canonicalPath + File.separator
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.startsWith("files/")) continue
                val rel = entry.name.removePrefix("files/")
                if (rel.startsWith("onhand/")) {
                    val staged = File(staging, rel.removePrefix("onhand/")).canonicalFile
                    if (!staged.path.startsWith(stagingDir)) continue // ochrona przed "../"
                    staged.parentFile?.mkdirs()
                    staged.outputStream().use { zip.copyTo(it) }
                    continue
                }
                val out = File(root, rel).canonicalFile
                // Istniejących plików nie nadpisujemy: nazwy to UUID / id tablic, więc to ten sam plik
                // (a tablica edytowana po zrobieniu kopii nie wróci do starej wersji).
                if (allowed.none { out.path.startsWith(it) } || out.exists()) continue
                out.parentFile?.mkdirs()
                out.outputStream().use { zip.copyTo(it) }
            }
            text
        }
    }

    // Import ZASTĘPUJE obecną konfigurację. Wszystko w jednej transakcji: błąd w połowie = nic się nie zmienia.
    suspend fun import(context: Context, source: Uri) = withContext(Dispatchers.IO) {
        val root = context.filesDir.canonicalFile
        val zipped = isZip(context, source)
        val text = if (zipped) unzip(context, source, root)
            else context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
                ?: error(AppText.get(R.string.data_backup_cannot_read))
        val json = JSONObject(text)
        require(json.optInt("version") in 1..VERSION) { AppText.get(R.string.data_backup_unsupported_version) }
        val home = context.filesDir // ścieżki budujemy w tej samej pisowni, której używa reszta aplikacji
        val oldRoots = listOf(json.optString("filesDir"), json.optString("filesDirCanonical")).filter { it.isNotBlank() }

        // "@FILES@/…" (albo stara ścieżka z innego telefonu) → ścieżka w tym telefonie.
        fun local(path: String): String {
            if (path.startsWith("$FILES/")) return File(home, path.removePrefix("$FILES/")).path
            val old = oldRoots.firstOrNull { path.startsWith("$it/") } ?: return path
            return File(home, path.removePrefix("$old/")).path
        }
        // Tylko wewnątrz danego folderu aplikacji ("@FILES@/../databases" z podrobionego pliku odpada).
        fun inside(path: String, dir: String): String? {
            val limit = File(root, dir).canonicalPath + File.separator
            return path.takeIf { runCatching { File(it).canonicalPath.startsWith(limit) }.getOrDefault(false) }
        }

        // Najpierw sprawdzamy, czy część launchera jest kompletna — zanim OnHand (osobna baza, osobna transakcja)
        // zastąpi swoje notatki. Brak któregoś pola = wyjątek tutaj, a nic jeszcze się nie zmieniło.
        listOf("modes", "folders", "folderApps", "cards", "pinned", "rules").forEach { json.getJSONArray(it) }
        json.getJSONObject("prefs")

        // OnHand przed launcherem: potrzebujemy mapy "stare id notatki → nowe" dla odnośników.
        // Starsze kopie nie mają sekcji "onhand" — wtedy notatek OnHand nie ruszamy.
        // Folder przejściowy sprzątamy zawsze (finally ≈ finally w C#), także po błędzie.
        val onHandIds: Map<Long, Long>? = try {
            if (!zipped) pl.rafal.onhand.OnHandBackup.clearStaging(context) // resztki po dawnym, nieudanym imporcie
            json.optJSONObject("onhand")?.let { pl.rafal.onhand.OnHandBackup.import(context, it) }
        } finally {
            pl.rafal.onhand.OnHandBackup.clearStaging(context)
        }

        val db = LauncherDatabase.get(context)
        var newHomeId: Long? = null
        // Nowe id nadaje baza, więc budujemy mapy "stare id → nowe id" (jak przy imporcie z kluczami IDENTITY).
        val modeIds = mutableMapOf<Long, Long>()

        db.withTransaction {
            db.modeDao().deleteAll()   // kaskadowo: karty, OnHand, reguły, statystyki
            db.folderDao().deleteAll() // kaskadowo: aplikacje w folderach

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
                        FolderEntity(
                            parentId = parent, name = o.getString("name"), createdAt = o.optLong("createdAt"),
                            icon = o.optStringOrNull("icon"), color = o.optLongOrNull("color"),
                        ),
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

            val cardIds = mutableMapOf<Long, Long>() // stare id wiersza karty → nowe
            json.getJSONArray("cards").objects().forEach { o ->
                val modeId = modeIds[o.getLong("modeId")] ?: return@forEach
                var config = o.optStringOrNull("config")
                // Widżet folderu wskazuje na folder — przepinamy go na nowe id.
                if (o.optStringOrNull("widgetKind") == CustomWidgetKind.FOLDER.name && config != null) {
                    val cfg = JSONObject(config)
                    folderIds[cfg.optLong("folderId", -1)]?.let { cfg.put("folderId", it) }
                    config = cfg.toString()
                }
                // Naklejka: ścieżki plików (rozpakowanych z .zip) w tym telefonie.
                if (o.optStringOrNull("widgetKind") == CustomWidgetKind.STICKER.name && config != null) {
                    val cfg = JSONObject(config)
                    for (key in listOf("file", "original")) {
                        val p = cfg.optString(key)
                        if (p.isNotBlank()) {
                            val safe = inside(local(p), "stickers")
                            if (safe != null) cfg.put(key, safe) else cfg.remove(key)
                        }
                    }
                    config = cfg.toString()
                }
                val newId = db.cardItemDao().insert(o.toCardItem(modeId, config))
                if (o.has("id")) cardIds[o.getLong("id")] = newId
            }
            // Stosy: stare id widżetów → nowe (widżetów systemowych nie eksportujemy, więc te po prostu wypadają),
            // a potem porządki (stos z jednym widżetem się rozpada, pusty znika).
            db.cardItemDao().getAll().filter { it.widgetKind == CustomWidgetKind.STACK.name }.forEach { stack ->
                val data = StackData.of(stack.config)
                db.cardItemDao().updateConfig(stack.id, data.copy(members = data.members.mapNotNull { cardIds[it] }).toJson())
            }
            modeIds.values.forEach { repairStacks(db.cardItemDao(), it) }

            json.getJSONArray("pinned").objects().forEach { o ->
                val modeId = modeIds[o.getLong("modeId")] ?: return@forEach
                val kind = o.getString("kind")
                var uri = o.optStringOrNull("uri")
                // Odnośnik do notatki OnHand → nowe id notatki (bez notatki w kopii odnośnik pomijamy).
                if (kind == PinnedItemEntity.KIND_ONHAND && onHandIds != null) {
                    uri = uri?.toLongOrNull()?.let { onHandIds[it] }?.toString() ?: return@forEach
                }
                db.pinnedItemDao().insert(
                    PinnedItemEntity(
                        modeId = modeId,
                        kind = kind,
                        title = o.getString("title"),
                        uri = uri,
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
            // Własny schemat przed ustawieniem domyślnego, żeby od razu miał właściwe kolory.
            p.optJSONObject("customTheme")?.let { c ->
                runCatching {
                    setCustomColors(
                        pl.rafal.contextlauncher.ui.theme.CustomColors(c.getLong("bg"), c.getLong("surface"), c.getLong("accent"), c.getLong("text")),
                    )
                }
            }
            Palette.fromName(p.optString("defaultPalette"))?.let { setDefaultPalette(it) }
        }
        AppPrefs.get(context).apply {
            setHomeModeId(newHomeId)
            leftHanded.set(p.optBoolean("leftHanded", false))
            returnToHome.set(p.optBoolean("returnToHome", true))
            autoSwitch.set(p.optBoolean("autoSwitch", false))
            uniformLook.set(p.optBoolean("uniformLook", false))
            showLabels.set(p.optBoolean("showLabels", true))
            // Układy trybów: klucze to stare id trybów → przepinamy na nowe.
            p.optString("modeLayouts").takeIf { it.isNotBlank() }?.let { text ->
                val remapped = ModeLayout.parseAll(text).mapNotNull { (id, layout) -> modeIds[id]?.let { it to layout } }.toMap()
                modeLayouts.set(ModeLayout.writeAll(remapped))
            }
        }

        if (zipped) {
            // Tapety trybów z kopii zastępują obecne (stare tryby już nie istnieją).
            json.optJSONArray("wallpapers")?.let { array ->
                val entries = array.objects().mapNotNull { o ->
                    val oldId = o.optLong("modeId", -1L)
                    val modeId = if (oldId == -1L) null else (modeIds[oldId] ?: return@mapNotNull null)
                    val path = inside(local(o.getString("file")), "wallpapers")?.takeIf { File(it).isFile } ?: return@mapNotNull null
                    WallpaperStore.Saved(modeId, path, o.optBoolean("lock", false), o.optStringOrNull("crop")?.let { WallpaperStore.parseCrop(it) })
                }
                WallpaperStore(context).replaceAll(entries)
            }
            // Tablice StickOnMe: ścieżki zdjęć i naklejek w ich plikach JSON wskazywały na stary telefon.
            File(root, "stickers/boards").listFiles { f -> f.extension == "json" }?.forEach { file ->
                runCatching {
                    val board = pl.rafal.stickonme.Board.of(file.readText()) ?: return@runCatching
                    val fixed = board.copy(
                        bgImage = board.bgImage?.let(::local),
                        layers = board.layers.map { l -> l.copy(path = l.path?.let(::local)) },
                    )
                    if (fixed != board) file.writeText(fixed.toJson())
                }
            }
        }

        // Stara kopia mogła przynieść notatki w bazie launchera (rodzaj NOTE) — przenosimy je do OnHand.
        runCatching { OnHandBridge.migrateLegacyNotes(context) }

        // Porządek (tylko pełna kopia, która przynosi własne naklejki): pliki naklejek, których nie używa już
        // żadna karta, usuwamy. Import samego JSON-a naklejek nie ma — wtedy niczego nie kasujemy.
        if (zipped) runCatching {
            val used = db.cardItemDao().getAll().filter { it.widgetKind == CustomWidgetKind.STICKER.name }.flatMap { card ->
                val cfg = runCatching { JSONObject(card.config ?: "{}") }.getOrDefault(JSONObject())
                listOf(cfg.optString("file"), cfg.optString("original")).filter { it.isNotBlank() }.map { File(it).canonicalPath }
            }.toSet()
            File(root, "stickers").listFiles { f -> f.isFile }?.filter { it.canonicalPath !in used }?.forEach { it.delete() }
        }
    }

    // Co da się przenieść na inny telefon (naklejki tylko razem z plikami, czyli w .zip).
    private fun isPortable(item: CardItemEntity, withFiles: Boolean): Boolean =
        item.type != CardItemEntity.TYPE_WIDGET && (withFiles || item.widgetKind != CustomWidgetKind.STICKER.name)

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
        .put("id", id) // potrzebne stosom: wskazują swoje widżety po id (przy imporcie przepinamy na nowe)
        .put("modeId", modeId).put("type", type).put("packageName", packageName).put("className", className)
        .put("userSerial", userSerial).put("x", x).put("y", y).put("w", w).put("h", h)
        .putOpt("widgetKind", widgetKind).putOpt("config", config).put("page", page)

    private fun JSONObject.toCardItem(modeId: Long, config: String?) = CardItemEntity(
        modeId = modeId,
        type = getString("type"),
        packageName = getString("packageName"),
        className = getString("className"),
        userSerial = getLong("userSerial"),
        x = getInt("x"), y = getInt("y"), w = getInt("w"), h = getInt("h"),
        widgetKind = optStringOrNull("widgetKind"),
        config = config,
        page = optInt("page", 0).coerceIn(STACKED_PAGE, 4), // starsze kopie nie mają stron → pierwsza; max 5 stron; -1 = w stosie
    )

    private fun PinnedItemEntity.toJson() = JSONObject()
        .put("modeId", modeId).put("kind", kind).put("title", title).putOpt("uri", uri).putOpt("text", text)
        .put("createdAt", createdAt).putOpt("archivedAt", archivedAt)

    private fun FolderEntity.toJson() = JSONObject()
        .put("id", id).putOpt("parentId", parentId).put("name", name).put("createdAt", createdAt)
        .putOpt("icon", icon).putOpt("color", color)

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
