package pl.rafal.contextlauncher.data

import pl.rafal.contextlauncher.data.db.FolderAppEntity
import pl.rafal.contextlauncher.data.db.FolderEntity
import java.text.Collator

// Aplikacja w folderze: wiersz z bazy + zainstalowana aplikacja (ikona, nazwa).
data class FolderApp(val entry: FolderAppEntity, val app: AppInfo)

// Całe drzewo folderów złożone z płaskich list z bazy (jak budowanie drzewa z tabeli z ParentId w C#).
class FolderTree(
    val folders: List<FolderEntity>,
    folderApps: List<FolderAppEntity>,
    installed: List<AppInfo>,
) {
    private val byId = folders.associateBy { it.id }         // ≈ ToDictionary(f => f.Id)
    private val children = folders.groupBy { it.parentId }   // ≈ ToLookup(f => f.ParentId)

    private val appsByFolder: Map<Long, List<FolderApp>> = run {
        val installedByKey = installed.associateBy { it.key }
        val collator = Collator.getInstance()
        folderApps
            .mapNotNull { entry ->
                // Klucz budujemy tak samo jak AppInfo.key, żeby znaleźć zainstalowaną aplikację.
                val key = "${entry.packageName}/${entry.className}#${entry.userSerial}"
                installedByKey[key]?.let { FolderApp(entry, it) } // odinstalowane pomijamy
            }
            .groupBy { it.entry.folderId }
            .mapValues { (_, apps) -> apps.sortedWith(compareBy(collator) { it.app.label }) }
    }

    fun folder(id: Long?): FolderEntity? = id?.let { byId[it] }

    fun subfolders(parentId: Long?): List<FolderEntity> = children[parentId].orEmpty()

    fun apps(folderId: Long): List<FolderApp> = appsByFolder[folderId].orEmpty()

    // Ścieżka od korzenia do folderu, np. [Praca, Dokumenty, Faktury].
    fun path(id: Long?): List<FolderEntity> =
        generateSequence(folder(id)) { folder(it.parentId) }.toList().reversed()

    // Wszystkie aplikacje w folderze razem z podfolderami (rekurencja).
    fun totalApps(id: Long): Int = apps(id).size + subfolders(id).sumOf { totalApps(it.id) }

    // Płaska lista całego drzewa z głębokością, do wyboru folderu z wcięciami.
    fun flatten(parentId: Long? = null, depth: Int = 0): List<Pair<FolderEntity, Int>> =
        subfolders(parentId).flatMap { listOf(it to depth) + flatten(it.id, depth + 1) }

    // Kilka pierwszych ikon z folderu i jego podfolderów, do miniatury folderu.
    fun previewApps(id: Long, count: Int = 4): List<AppInfo> =
        (apps(id).map { it.app } + subfolders(id).flatMap { previewApps(it.id, count) }).take(count)

    companion object {
        val EMPTY = FolderTree(emptyList(), emptyList(), emptyList())
    }
}
