package pl.rafal.contextlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.DrawerArrange
import pl.rafal.contextlauncher.data.DrawerItem
import pl.rafal.contextlauncher.data.DrawerOrder
import pl.rafal.contextlauncher.data.DrawerSettings
import pl.rafal.contextlauncher.data.DrawerSort
import pl.rafal.contextlauncher.data.DrawerView
import pl.rafal.contextlauncher.data.FolderTree
import pl.rafal.contextlauncher.data.db.FolderEntity
import java.text.Collator

// Pozycja na liście szuflady: aplikacja albo folder (w widoku "jedna lista" foldery stoją między aplikacjami).
private sealed interface DrawerEntry {
    val key: String
    val label: String

    data class App(val app: AppInfo) : DrawerEntry {
        override val key get() = DrawerOrder.APP + app.key
        override val label get() = app.label
    }

    data class Folder(val folder: FolderEntity) : DrawerEntry {
        override val key get() = DrawerOrder.FOLDER + folder.id
        override val label get() = folder.name
    }
}

// Szuflada aplikacji. Od 1.7.0 jej wygląd zależy od OnMode (DrawerSettings):
//  • widok: zakładki Wszystkie | Foldery albo jedna lista z folderami,
//  • sortowanie: A–Z, najczęściej używane w tym trybie, własna kolejność (układana palcem),
//  • sekcje: "Nowe" (świeżo zainstalowane) i "Często w …".
// Ekran nie zna ViewModelu: dostaje dane i funkcje zwrotne, więc łatwo go testować i podglądać.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(
    onClose: () -> Unit,                          // gest w dół (gdy lista jest na samej górze)
    apps: List<AppInfo>,
    query: String,
    onQueryChange: (String) -> Unit,              // (String) -> Unit ≈ Action<string>
    onAppClick: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,       // ≈ Func<AppInfo, List<MenuAction>>
    foldersContent: @Composable (startFolderId: Long?) -> Unit, // przeglądarka folderów (od korzenia albo od danego folderu)
    frequent: List<AppInfo>,                      // najczęściej uruchamiane w aktywnym trybie
    frequentLabel: String,
    autoFocusSearch: Boolean,                     // true = od razu kursor i klawiatura w wyszukiwarce
    hiddenApps: List<AppInfo> = emptyList(),      // ukryte w aktywnym trybie (na końcu listy, po rozwinięciu)
    dragOut: ExternalDrag? = null,                // przeciąganie ikon na kartę
    translucent: Boolean = false,                 // tapeta prześwituje przez tło (jak w One UI)
    modifier: Modifier = Modifier,
    onOpenAppSettings: (() -> Unit)? = null,      // skrót do systemowych ustawień "Aplikacje"
    // --- 1.7.0: szuflada per OnMode ---
    settings: DrawerSettings = DrawerSettings.DEFAULT,
    onSettingsChange: (DrawerSettings) -> Unit = {},
    launchCounts: Map<String, Int> = emptyMap(),  // uruchomienia w aktywnym trybie (AppInfo.key → liczba)
    manualOrder: List<String> = emptyList(),      // własna kolejność aktywnego trybu (DrawerOrder)
    onManualOrderChange: (List<String>) -> Unit = {},
    newApps: List<AppInfo> = emptyList(),
    onDismissNew: (AppInfo) -> Unit = {},
    folderTree: FolderTree = FolderTree.EMPTY,
    onCreateFolder: (String) -> Unit = {},        // nowy folder w korzeniu (widok "jedna lista")
    onOpenDrawerSettings: (() -> Unit)? = null,   // pełne ustawienia szuflady w ustawieniach OnMode
) {
    val integrated = settings.view == DrawerView.INTEGRATED
    var showFolders by rememberSaveable { mutableStateOf(false) }
    var arranging by rememberSaveable { mutableStateOf(false) }
    var openFolderId by rememberSaveable { mutableStateOf<Long?>(null) } // folder otwarty z jednej listy
    var newFolderOpen by remember { mutableStateOf(false) }

    // Wpisywanie w wyszukiwarkę wraca z otwartego folderu do listy wyników.
    LaunchedEffect(query) { if (query.isNotBlank()) openFolderId = null }

    val collator = remember { Collator.getInstance() }
    val rootFolders = if (integrated) folderTree.subfolders(null) else emptyList()
    val entries = remember(apps, rootFolders, folderTree, settings, launchCounts, manualOrder, query) {
        val needle = query.trim()
        val folders = if (needle.isEmpty()) rootFolders else rootFolders.filter { it.name.contains(needle, ignoreCase = true) }
        // 1.9.2: w jednej liście aplikacja z folderu jest tylko w folderze (bez dubli). Wyszukiwanie dalej ją znajduje.
        val listed = if (integrated && needle.isEmpty()) apps.filter { it.key !in folderTree.appKeysInFolders } else apps
        arrangeEntries(listed, folders, folderTree, settings, launchCounts, manualOrder, collator)
    }

    // Zamykanie gestem w dół. Lista sama przewija się w dół/górę; to, czego NIE zużyła (bo jest już na górze),
    // trafia do nas przez "nested scroll" — jak zdarzenie, które kontrolka przepuszcza do rodzica.
    val density = LocalDensity.current
    val closeThreshold = with(density) { 120.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val currentOnClose by rememberUpdatedState(onClose)
    val pullToClose = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Palec wraca w górę po pociągnięciu: najpierw "odciągamy" szufladę, potem przewija się lista.
                if (pull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val close = pull > closeThreshold || (pull > 0f && available.y > 2500f)
                pull = 0f
                if (close) currentOnClose()
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(pullToClose)
            // Szuflada jedzie za palcem (z tłumieniem), więc widać, że gest zadziała.
            .graphicsLayer { translationY = pull * 0.6f }
            // Pasek z zakładkami nie przewija się sam — tam gest w dół łapiemy bezpośrednio.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val close = pull > closeThreshold
                        pull = 0f
                        if (close) currentOnClose()
                    },
                    onDragCancel = { pull = 0f },
                ) { _, dy -> pull = (pull + dy).coerceAtLeast(0f) }
            }
            .background(MaterialTheme.colorScheme.background.copy(alpha = if (translucent) 0.86f else 1f))
            .systemBarsPadding()
            .imePadding() // lista kończy się nad klawiaturą
            .padding(horizontal = 16.dp),
    ) {
        if (arranging) {
            // Układanie własnej kolejności: w zakładkach same aplikacje, w jednej liście — z folderami.
            ReorderGrid(
                initial = entries.filter { integrated || it is DrawerEntry.App },
                key = { it.key },
                label = { it.label },
                hint = stringResource(R.string.drawer_arrange_hint),
                onDone = { keys ->
                    onManualOrderChange(keys)
                    if (settings.sort != DrawerSort.MANUAL) onSettingsChange(settings.copy(sort = DrawerSort.MANUAL))
                    arranging = false
                },
                onCancel = { arranging = false },
            ) { entry -> ArrangeTile(entry, folderTree) }
        } else {
            DrawerContent(
                integrated = integrated,
                showFolders = showFolders,
                onShowFolders = { showFolders = it },
                settings = settings,
                onSettingsChange = onSettingsChange,
                onArrange = {
                    onQueryChange("") // układamy całą listę, nie wyniki wyszukiwania
                    arranging = true
                },
                onNewFolder = { newFolderOpen = true },
                onOpenDrawerSettings = onOpenDrawerSettings,
                onOpenAppSettings = onOpenAppSettings,
                entries = entries,
                sections = DrawerSections(
                    newApps = if (settings.showNew) newApps else emptyList(),
                    // Przy sortowaniu "najczęściej używane" ten rząd powtarzałby początek listy.
                    frequent = if (settings.showFrequent && settings.sort != DrawerSort.MOST_USED) frequent else emptyList(),
                    frequentLabel = frequentLabel,
                ),
                folderTree = folderTree,
                openFolderId = openFolderId,
                onOpenFolder = { openFolderId = it },
                foldersContent = foldersContent,
                query = query,
                onQueryChange = onQueryChange,
                onAppClick = onAppClick,
                menuFor = menuFor,
                onDismissNew = onDismissNew,
                autoFocusSearch = autoFocusSearch,
                hiddenApps = hiddenApps,
                dragOut = dragOut,
            )
        }
    }
    if (newFolderOpen) {
        TextInputDialog(
            title = stringResource(R.string.folder_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.common_create),
            onConfirm = { name ->
                onCreateFolder(name)
                newFolderOpen = false
            },
            onDismiss = { newFolderOpen = false },
        )
    }
}

// Zwykły widok szuflady (poza układaniem): nagłówek + zakładki albo jedna lista.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerContent(
    integrated: Boolean,
    showFolders: Boolean,
    onShowFolders: (Boolean) -> Unit,
    settings: DrawerSettings,
    onSettingsChange: (DrawerSettings) -> Unit,
    onArrange: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenDrawerSettings: (() -> Unit)?,
    onOpenAppSettings: (() -> Unit)?,
    entries: List<DrawerEntry>,
    sections: DrawerSections,
    folderTree: FolderTree,
    openFolderId: Long?,
    onOpenFolder: (Long?) -> Unit,
    foldersContent: @Composable (startFolderId: Long?) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,
    onDismissNew: (AppInfo) -> Unit,
    autoFocusSearch: Boolean,
    hiddenApps: List<AppInfo>,
    dragOut: ExternalDrag?,
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            if (!integrated) {
                FilterChip(selected = !showFolders, onClick = { onShowFolders(false) }, label = { Text(stringResource(R.string.drawer_tab_all)) })
                FilterChip(selected = showFolders, onClick = { onShowFolders(true) }, label = { Text(stringResource(R.string.drawer_tab_folders)) })
            }
            Spacer(Modifier.weight(1f))
            if (integrated || !showFolders) {
                SortMenu(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onArrange = onArrange,
                    onNewFolder = if (integrated) onNewFolder else null,
                    onOpenDrawerSettings = onOpenDrawerSettings,
                )
            }
            if (onOpenAppSettings != null) {
                AssistChip(onClick = onOpenAppSettings, label = { Text(stringResource(R.string.drawer_app_settings)) })
            }
        }

        if (integrated) {
            if (openFolderId != null && folderTree.folder(openFolderId) != null) {
                // Wstecz z folderu otwartego z listy wraca do listy (głębiej cofa sama przeglądarka folderów).
                BackHandler { onOpenFolder(null) }
                Column {
                    TextButton(onClick = { onOpenFolder(null) }) { Text(stringResource(R.string.drawer_back_to_list)) }
                    foldersContent(openFolderId)
                }
            } else {
                AllAppsTab(
                    entries, sections, folderTree, query, onQueryChange, onAppClick, menuFor, onDismissNew,
                    onFolderClick = { onOpenFolder(it) }, autoFocusSearch, hiddenApps, dragOut,
                )
            }
        } else {
            // Przesunięcie palcem w lewo → Foldery, w prawo → Wszystkie (jak zakładki w Galerii).
            // Przeciąganie ikony po przytrzymaniu "zjada" gest, więc nie przełącza zakładek przypadkiem.
            val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
            var swipe by remember { mutableFloatStateOf(0f) }
            val currentOnShowFolders by rememberUpdatedState(onShowFolders)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { swipe = 0f },
                            onDragEnd = {
                                if (swipe < -swipeThreshold) currentOnShowFolders(true)
                                if (swipe > swipeThreshold) currentOnShowFolders(false)
                                swipe = 0f
                            },
                            onDragCancel = { swipe = 0f },
                        ) { _, dx -> swipe += dx }
                    },
            ) {
                AnimatedContent(
                    targetState = showFolders,
                    transitionSpec = {
                        // Foldery wjeżdżają z prawej, Wszystkie z lewej.
                        val dir = if (targetState) 1 else -1
                        (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
                    },
                    label = "zakladki",
                ) { folders ->
                    if (folders) {
                        Column { foldersContent(null) }
                    } else {
                        Column {
                            AllAppsTab(
                                entries, sections, folderTree, query, onQueryChange, onAppClick, menuFor, onDismissNew,
                                onFolderClick = {}, autoFocusSearch, hiddenApps, dragOut,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Sekcje nad główną listą (tylko bez wpisanego tekstu).
private class DrawerSections(val newApps: List<AppInfo>, val frequent: List<AppInfo>, val frequentLabel: String)

private fun arrangeEntries(
    apps: List<AppInfo>,
    folders: List<FolderEntity>,
    tree: FolderTree,
    settings: DrawerSettings,
    counts: Map<String, Int>,
    manualOrder: List<String>,
    collator: Collator,
): List<DrawerEntry> {
    val all: List<DrawerEntry> = folders.map { DrawerEntry.Folder(it) } + apps.map { DrawerEntry.App(it) }
    val byKey = all.associateBy { it.key }
    val items = all.map { e ->
        when (e) {
            is DrawerEntry.App -> DrawerItem(e.key, e.label, isFolder = false, uses = counts[e.app.key] ?: 0)
            is DrawerEntry.Folder -> DrawerItem(e.key, e.label, isFolder = true, uses = folderUses(tree, e.folder.id, counts))
        }
    }
    return DrawerArrange.arrange(items, settings.sort, settings.foldersFirst && folders.isNotEmpty(), manualOrder, collator)
        .mapNotNull { byKey[it.key] }
}

// Folder w sortowaniu "najczęściej": suma uruchomień jego aplikacji (z podfolderami).
private fun folderUses(tree: FolderTree, id: Long, counts: Map<String, Int>): Int =
    tree.apps(id).sumOf { counts[it.app.key] ?: 0 } + tree.subfolders(id).sumOf { folderUses(tree, it.id, counts) }

// Przycisk sortowania w nagłówku szuflady: szybka zmiana (w tym OnMode albo ogólnie) + układanie + skrót do ustawień.
@Composable
private fun SortMenu(
    settings: DrawerSettings,
    onSettingsChange: (DrawerSettings) -> Unit,
    onArrange: () -> Unit,
    onNewFolder: (() -> Unit)?,
    onOpenDrawerSettings: (() -> Unit)?,
) {
    val newFolder = stringResource(R.string.folder_new_folder)
    val drawerSettings = stringResource(R.string.drawer_settings)
    SortChip(
        sort = settings.sort,
        onSortChange = { onSettingsChange(settings.copy(sort = it)) },
        onArrange = onArrange,
        extra = listOfNotNull(
            onNewFolder?.let { MenuAction(newFolder, it) },
            onOpenDrawerSettings?.let { MenuAction(drawerSettings, it) },
        ),
    )
}

@Composable
fun drawerSortLabel(sort: DrawerSort): String = when (sort) {
    DrawerSort.ALPHA -> stringResource(R.string.drawer_sort_alpha)
    DrawerSort.MOST_USED -> stringResource(R.string.drawer_sort_most_used)
    DrawerSort.MANUAL -> stringResource(R.string.drawer_sort_manual)
}

@Composable
private fun AllAppsTab(
    entries: List<DrawerEntry>,
    sections: DrawerSections,
    folderTree: FolderTree,
    query: String,
    onQueryChange: (String) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,
    onDismissNew: (AppInfo) -> Unit,
    onFolderClick: (Long) -> Unit,
    autoFocusSearch: Boolean,
    hiddenApps: List<AppInfo>,
    dragOut: ExternalDrag?,
) {
    val focusRequester = remember { FocusRequester() }
    var showHidden by rememberSaveable { mutableStateOf(false) }

    // Wykonuje się raz, gdy zakładka się pojawia: kursor w wyszukiwarce tylko po "Szukaj aplikacji".
    LaunchedEffect(Unit) { if (autoFocusSearch) focusRequester.requestFocus() }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.drawer_search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            // Enter na klawiaturze uruchamia pierwszą znalezioną aplikację.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                entries.firstNotNullOfOrNull { (it as? DrawerEntry.App)?.app }?.let(onAppClick)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .focusRequester(focusRequester),
        )

        // Lazy = rysuje tylko to, co widać (jak wirtualizacja w ListView/ItemsControl).
        // Szuflada wchodzi do kompozycji przy otwarciu, więc to jest chwila otwarcia (start "fali" ikon).
        val openedAt = remember { System.currentTimeMillis() }
        val browsing = query.isBlank()
        val newApps = if (browsing) sections.newApps else emptyList()
        val frequent = if (browsing) sections.frequent else emptyList()
        val headOffset = newApps.size + frequent.size
        val dismissLabel = stringResource(R.string.drawer_new_dismiss)
        LazyVerticalGrid(
            columns = GridCells.Fixed(LauncherViewModel.COLUMNS),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // "Nowe": świeżo zainstalowane, znikają po pierwszym otwarciu (albo ręcznie z menu).
            if (newApps.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.drawer_section_new)) }
                itemsIndexed(newApps, key = { _, a -> "new:" + a.key }) { index, app ->
                    AppTile(
                        app = app, onClick = { onAppClick(app) },
                        menu = listOf(MenuAction(dismissLabel) { onDismissNew(app) }) + menuFor(app),
                        dragOut = dragOut,
                        modifier = Modifier.animateItem().staggerIn(index, openedAt),
                    )
                }
            }
            // Rząd "Często w trybie…".
            if (frequent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(sections.frequentLabel) }
                itemsIndexed(frequent, key = { _, a -> "fav:" + a.key }) { index, app ->
                    AppTile(
                        app = app, onClick = { onAppClick(app) }, menu = menuFor(app), dragOut = dragOut,
                        modifier = Modifier.animateItem().staggerIn(index + newApps.size, openedAt),
                    )
                }
            }
            if (headOffset > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.drawer_all_apps)) }
            }
            // animateItem: przy wpisywaniu w wyszukiwarkę i zmianie sortowania ikony płynnie jadą na nowe miejsca.
            itemsIndexed(entries, key = { _, e -> e.key }) { index, entry ->
                val itemModifier = Modifier.animateItem().staggerIn(index + headOffset, openedAt)
                when (entry) {
                    is DrawerEntry.App -> AppTile(
                        app = entry.app, onClick = { onAppClick(entry.app) }, menu = menuFor(entry.app), dragOut = dragOut,
                        modifier = itemModifier,
                    )
                    is DrawerEntry.Folder -> Box(itemModifier) {
                        FolderTile(
                            folder = entry.folder,
                            name = entry.folder.name,
                            preview = folderTree.previewApps(entry.folder.id),
                            count = folderTree.totalApps(entry.folder.id),
                            onClick = { onFolderClick(entry.folder.id) },
                            dragOut = dragOut,
                        )
                    }
                }
            }
            // Ukryte: tylko na liście (nie w wyszukiwaniu), zwinięte za jednym dotknięciem.
            if (browsing && hiddenApps.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = { showHidden = !showHidden }) {
                        Text(if (showHidden) stringResource(R.string.drawer_hide_hidden) else stringResource(R.string.drawer_show_hidden, hiddenApps.size))
                    }
                }
                if (showHidden) {
                    items(hiddenApps, key = { "hidden:" + it.key }) { app ->
                        AppTile(app = app, onClick = { onAppClick(app) }, menu = menuFor(app), dragOut = dragOut)
                    }
                }
            }
        }
    }
}

// Kafelek w trybie układania: ikona bez menu i bez reakcji na dotyk (gest przejmuje siatka).
@Composable
private fun ArrangeTile(entry: DrawerEntry, folderTree: FolderTree) {
    when (entry) {
        is DrawerEntry.App -> AppTile(app = entry.app, onClick = {}, menu = emptyList(), enabled = false)
        is DrawerEntry.Folder -> FolderArrangeTile(entry.folder, folderTree)
    }
}

// Folder w trybie układania (szuflada i podfoldery w folderze): miniatura + nazwa, bez menu.
@Composable
fun FolderArrangeTile(folder: FolderEntity, folderTree: FolderTree) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        FolderBadge(folder, folderTree.previewApps(folder.id), 52.dp)
        Text(
            folder.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}
