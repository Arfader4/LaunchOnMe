package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.db.ModeEntity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.navigationBarsPadding
import pl.rafal.contextlauncher.data.CardFolderData
import pl.rafal.contextlauncher.data.folderRows
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import pl.rafal.contextlauncher.data.suggestedApps

// Wszystko, co okno folderu na karcie może zlecić (jak interfejs z delegatami w C#).
// Zmiany samych danych (nazwa, wygląd, kolejność, podfoldery…) idą jedną drogą: onUpdate dostaje funkcję,
// która z całego folderu (z podfolderami) robi nowy — okno samo wie, którego podfolderu dotyczy zmiana.
class CardFolderActions(
    val onLaunch: (AppInfo) -> Unit,
    val onUpdate: ((CardFolderData) -> CardFolderData) -> Unit,
    val onTakeOut: (path: List<Int>, app: AppInfo, toCard: Boolean) -> Unit, // toCard = wyjmij na kartę
    val onAppInfo: (AppInfo) -> Unit,
    val onUninstall: (AppInfo) -> Unit,
    val onDissolve: () -> Unit,
    val onSaveToDrawer: (CardFolderData) -> Unit,
    val onCopyToMode: (CardFolderData, modeId: Long) -> Unit,
)

// Folder otwarty z karty. Domyślnie wysuwa się u dołu ekranu (bliżej kciuka, jak w One UI), opcjonalnie na środku.
// Podfoldery otwierają się w tym samym oknie (ścieżka ‹ wstecz). Nazwa i symbol są klikalne.
@Composable
fun CardFolderDialog(
    root: CardFolderData,
    lookup: (String) -> AppInfo?,            // klucz → zainstalowana aplikacja albo skrót
    launchCounts: Map<String, Int>,
    allApps: List<AppInfo>,
    modes: List<ModeEntity>,
    currentModeId: Long?,
    actions: CardFolderActions,
    onDismiss: () -> Unit,
    shortcuts: List<AppInfo> = emptyList(),
    hasShortcutAccess: Boolean = false,
    startPath: List<Int> = emptyList(),
    atBottom: Boolean = true,
) {
    var path by remember { mutableStateOf(startPath) }
    val current = root.at(path) ?: root.also { path = emptyList() }
    val apps = current.ordered(current.keys.mapNotNull(lookup), launchCounts)
    fun upd(change: (CardFolderData) -> CardFolderData) = actions.onUpdate { it.update(path, change) }

    var renameFor by remember { mutableStateOf<List<Int>?>(null) }  // ścieżka folderu do zmiany nazwy
    var lookFor by remember { mutableStateOf<List<Int>?>(null) }
    var pickOpen by remember { mutableStateOf(false) }
    var dissolveOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var arranging by remember(path) { mutableStateOf(false) } // przejście do innego folderu kończy układanie
    var shortcutOpen by remember { mutableStateOf(false) }
    var newSubOpen by remember { mutableStateOf(false) }
    var moveApp by remember { mutableStateOf<AppInfo?>(null) }      // "Przenieś do podfolderu…"
    var copyOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }

    // Otwarcie: okno "wyrasta" z miejsca dotknięcia (ikony folderu), zamknięcie: kurczy się z powrotem.
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(androidx.compose.ui.graphics.TransformOrigin.Center) }
    val touch = remember { LaunchOrigin.lastTouch() }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(closing) {
        if (closing) {
            grow.animateTo(0f, Motion.fastOut())
            dismiss()
        } else {
            grow.animateTo(1f, Motion.panel())
        }
    }
    val close = { closing = true }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Wstecz: najpierw z podfolderu do folderu wyżej, dopiero potem zamknięcie okna.
        // (Musi być wewnątrz Dialog — okno dialogowe ma własną obsługę przycisku Wstecz.)
        BackHandler(enabled = path.isNotEmpty()) { path = path.dropLast(1) }
        // Pełny ekran, żeby dało się ustawić okno u dołu; dotknięcie obok okna zamyka folder.
        Box(
            contentAlignment = if (atBottom) Alignment.BottomCenter else Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
        ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth(if (atBottom) 0.96f else 0.9f)
                .navigationBarsPadding()
                .padding(bottom = if (atBottom) 12.dp else 0.dp)
                .onGloballyPositioned { c ->
                    // Punkt dotknięcia jako ułamek rozmiaru okna (może wyjść poza 0..1 — wtedy "wyrasta" spoza okna).
                    val t = touch
                    if (t != null && c.size.width > 0 && c.size.height > 0) {
                        val p = c.positionOnScreen()
                        origin = androidx.compose.ui.graphics.TransformOrigin((t.x - p.x) / c.size.width, (t.y - p.y) / c.size.height)
                    }
                }
                .graphicsLayer {
                    val v = grow.value
                    transformOrigin = origin
                    scaleX = 0.55f + 0.45f * v
                    scaleY = 0.55f + 0.45f * v
                    alpha = v.coerceIn(0f, 1f)
                }
                // W trakcie zamykania okno nie przyjmuje już dotyku (uruchomienie aplikacji zamknęłoby je drugi raz).
                .then(
                    if (!closing) Modifier
                    else Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    },
                )
                // Dotknięcie wewnątrz okna nie może go zamykać (pochłaniamy je bez efektu).
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (path.isNotEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { path = path.dropLast(1) }
                                .semantics { contentDescription = "Wstecz" },
                        ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    }
                    FolderBadge(
                        current.asEntity(0),
                        apps,
                        36.dp,
                        Modifier
                            .clickable { lookFor = path }
                            .semantics { contentDescription = "Wygląd folderu" },
                        grid = current.grid,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        current.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { renameFor = path }
                            .padding(vertical = 6.dp),
                    )
                    Box {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { menuOpen = true }
                                .semantics { contentDescription = "Opcje folderu" },
                        ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            buildList {
                                // Krótkie menu: tylko czynności. Ustawienia (wygląd, kolejność, układ, udostępnianie)
                                // są pogrupowane w oknie "Opcje folderu" — zamiast 20 pozycji w jednej liście.
                                add(MenuAction("Dodaj aplikacje…") { pickOpen = true })
                                add(MenuAction("Dodaj skrót aplikacji…") { shortcutOpen = true })
                                add(MenuAction("Nowy podfolder…") { newSubOpen = true })
                                add(MenuAction("Ułóż kolejność") { arranging = true })
                                add(MenuAction("Opcje folderu…") { optionsOpen = true })
                            }.forEach { action ->
                                DropdownMenuItem(text = { Text(action.label) }, onClick = {
                                    menuOpen = false
                                    action.onClick()
                                })
                            }
                        }
                    }
                }
                if (arranging) {
                    ArrangeGrid(apps) { order ->
                        // Odinstalowane (niewidoczne) klucze zostają na końcu, żeby wróciły, gdy aplikacja wróci.
                        upd { it.copy(keys = order + (it.keys - order.toSet()), sort = CardFolderData.SORT_MANUAL) }
                        arranging = false
                    }
                } else if (apps.isEmpty() && current.children.isEmpty()) {
                    Text(
                        "Pusty folder. Dodaj aplikacje przyciskiem „+ Aplikacje” albo upuść ikonę na folder w edycji układu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    // Zwykłe rzędy zamiast LazyVerticalGrid — tylko tak da się wyrównać niepełny rząd
                    // (do lewej/środka/prawej) i przykleić pełne rzędy do dołu. Folder ma kilkanaście ikon, leniwość nie jest potrzebna.
                    val columns = 4
                    val cell = maxWidth / columns
                    val subCount = current.children.size
                    val total = subCount + apps.size
                    val scroll = rememberScrollState()
                    // "Od dołu" przy przewijaniu: startujemy przewinięci na sam dół (tam są pełne rzędy).
                    LaunchedEffect(path, current.fromBottom, total) {
                        if (current.fromBottom) {
                            withFrameNanos { } // poczekaj na pierwszy pomiar, żeby maxValue było znane
                            scroll.scrollTo(scroll.maxValue)
                        }
                    }
                    val arrangement = when (current.align) {
                        CardFolderData.ALIGN_CENTER -> Arrangement.Center
                        CardFolderData.ALIGN_END -> Arrangement.End
                        else -> Arrangement.Start
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .verticalScroll(scroll),
                    ) {
                        // Najpierw podfoldery (jak katalogi przed plikami w Eksploratorze), potem aplikacje.
                        folderRows(total, columns, current.fromBottom).forEach { range ->
                            Row(horizontalArrangement = arrangement, modifier = Modifier.fillMaxWidth()) {
                                for (i in range) {
                                    if (i < subCount) {
                                        val index = i
                                        val child = current.children[index]
                                        key("sub$index") {
                                            Box(Modifier.width(cell)) {
                                                FolderTile(
                                                    folder = child.asEntity(0),
                                                    name = child.name,
                                                    preview = child.keys.mapNotNull(lookup),
                                                    count = child.allKeys().size,
                                                    onClick = { path = path + index },
                                                    menu = listOf(
                                                        MenuAction("Zmień nazwę") { renameFor = path + index },
                                                        MenuAction("Wygląd (symbol i kolor)…") { lookFor = path + index },
                                                        MenuAction("Usuń podfolder (aplikacje tutaj)") { upd { it.removeChild(index) } },
                                                    ),
                                                )
                                            }
                                        }
                                    } else {
                                        val app = apps[i - subCount]
                                        key(app.key) {
                                            Box(Modifier.width(cell)) {
                                                AppTile(
                                                    app = app,
                                                    onClick = { actions.onLaunch(app) },
                                                    menu = buildList {
                                                        add(MenuAction("Wyjmij na kartę") { actions.onTakeOut(path, app, true) })
                                                        if (current.children.isNotEmpty()) add(MenuAction("Przenieś do podfolderu…") { moveApp = app })
                                                        if (path.isNotEmpty()) add(MenuAction("Przenieś do folderu wyżej") {
                                                            val index = path.last()
                                                            actions.onUpdate { r ->
                                                                r.update(path.dropLast(1)) { parent ->
                                                                    parent.copy(
                                                                        keys = if (app.key in parent.keys) parent.keys else parent.keys + app.key,
                                                                        children = parent.children.mapIndexed { j, ch -> if (j == index) ch.copy(keys = ch.keys - app.key) else ch },
                                                                    )
                                                                }
                                                            }
                                                        })
                                                        if (current.sort == CardFolderData.SORT_MANUAL && app.key != apps.firstOrNull()?.key) {
                                                            add(MenuAction("Na początek (miniatura)") { upd { it.copy(keys = listOf(app.key) + (it.keys - app.key)) } })
                                                        }
                                                        add(MenuAction("Usuń z folderu") { actions.onTakeOut(path, app, false) })
                                                        add(MenuAction("Informacje o aplikacji") { actions.onAppInfo(app) })
                                                        if (!app.isShortcut) add(MenuAction("Odinstaluj") { actions.onUninstall(app) })
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!arranging) Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { arranging = true }) { Text("Ułóż") }
                    TextButton(onClick = { pickOpen = true }) { Text("+ Aplikacje") }
                    TextButton(onClick = close) { Text("Zamknij") }
                }
            }
        }
        }
    }

    if (optionsOpen) {
        FolderOptionsDialog(
            folder = current,
            isRoot = path.isEmpty(),
            canCopy = modes.size > 1,
            onChange = { change -> upd(change) },
            onRename = { optionsOpen = false; renameFor = path },
            onLook = { optionsOpen = false; lookFor = path },
            onSaveToDrawer = { optionsOpen = false; actions.onSaveToDrawer(current) },
            onCopy = { optionsOpen = false; copyOpen = true },
            onRemove = {
                optionsOpen = false
                if (path.isEmpty()) dissolveOpen = true
                else {
                    val index = path.last()
                    val parent = path.dropLast(1)
                    path = parent
                    actions.onUpdate { r -> r.update(parent) { it.removeChild(index) } }
                }
            },
            onDismiss = { optionsOpen = false },
        )
    }

    renameFor?.let { target ->
        TextInputDialog(
            title = "Nazwa folderu",
            initial = root.at(target)?.name.orEmpty(),
            confirmLabel = "Zapisz",
            onConfirm = { name ->
                actions.onUpdate { r -> r.update(target) { it.copy(name = name.trim().ifBlank { "Folder" }) } }
                renameFor = null
            },
            onDismiss = { renameFor = null },
        )
    }

    lookFor?.let { target ->
        val folder = root.at(target)
        if (folder != null) {
            FolderLookDialog(
                folder = folder.asEntity(0),
                allApps = allApps,
                preview = folder.keys.mapNotNull(lookup),
                onSave = { icon, color ->
                    actions.onUpdate { r -> r.update(target) { it.copy(icon = icon, color = color) } }
                    lookFor = null
                },
                onDismiss = { lookFor = null },
            )
        }
    }

    if (pickOpen) {
        AppMultiPickerDialog(
            allApps = allApps,
            alreadySelected = current.keys.toSet(),
            suggestions = suggestedApps(allApps, apps, current.name),
            title = "Dodaj do „${current.name}”",
            onConfirm = { chosen ->
                upd { f -> f.copy(keys = f.keys + chosen.map { it.key }.filter { it !in f.keys }) }
                pickOpen = false
            },
            onDismiss = { pickOpen = false },
        )
    }

    if (shortcutOpen) {
        ShortcutPickerDialog(
            shortcuts = shortcuts.filter { it.key !in current.keys },
            apps = allApps,
            hasAccess = hasShortcutAccess,
            title = "Skrót do „${current.name}”",
            onPick = { sc ->
                upd { f -> f.copy(keys = f.keys + sc.key) }
                shortcutOpen = false
            },
            onDismiss = { shortcutOpen = false },
        )
    }

    if (newSubOpen) {
        TextInputDialog(
            title = "Nowy podfolder w „${current.name}”",
            initial = "",
            confirmLabel = "Utwórz",
            onConfirm = { name ->
                upd { it.copy(children = it.children + CardFolderData(name.trim(), null, null, emptyList(), keep = true)) }
                newSubOpen = false
            },
            onDismiss = { newSubOpen = false },
        )
    }

    moveApp?.let { app ->
        AlertDialog(
            onDismissRequest = { moveApp = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Przenieś „${app.label}” do…") },
            text = {
                Column {
                    current.children.forEachIndexed { index, child ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    upd { f ->
                                        f.copy(
                                            keys = f.keys - app.key,
                                            children = f.children.mapIndexed { i, ch ->
                                                if (i == index && app.key !in ch.keys) ch.copy(keys = ch.keys + app.key) else ch
                                            },
                                        )
                                    }
                                    moveApp = null
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            FolderBadge(child.asEntity(0), child.keys.mapNotNull(lookup), 28.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(child.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveApp = null }) { Text("Anuluj") } },
        )
    }

    if (copyOpen) {
        AlertDialog(
            onDismissRequest = { copyOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Kopiuj „${current.name}” do trybu") },
            text = {
                Column {
                    modes.filter { it.id != currentModeId }.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    actions.onCopyToMode(current, mode.id)
                                    copyOpen = false
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            ModeBadge(mode, size = 28.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(mode.name)
                        }
                    }
                    Text(
                        "To niezależna kopia — zmiany w jednym trybie nie przenoszą się do drugiego.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { copyOpen = false }) { Text("Anuluj") } },
        )
    }

    if (dissolveOpen) {
        AlertDialog(
            onDismissRequest = { dissolveOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Rozwiązać „${root.name}”?") },
            text = { Text("Aplikacje (także z podfolderów) wrócą na kartę jako osobne ikony, w pobliżu miejsca folderu.") },
            confirmButton = {
                TextButton(onClick = {
                    dissolveOpen = false
                    actions.onDissolve()
                }) { Text("Rozwiąż") }
            },
            dismissButton = { TextButton(onClick = { dissolveOpen = false }) { Text("Anuluj") } },
        )
    }
}


// Układanie kolejności: przytrzymaj ikonę i przeciągnij ją na nowe miejsce — reszta się rozsuwa na żywo.
// Szybkie przesunięcie palcem dalej przewija listę (przeciąganie startuje dopiero po przytrzymaniu).
@Composable
private fun ArrangeGrid(apps: List<AppInfo>, onDone: (List<String>) -> Unit) {
    // mutableStateListOf ≈ ObservableCollection: zmiana kolejności od razu przerysowuje siatkę.
    val order = remember { mutableStateListOf<String>().apply { addAll(apps.map { it.key }) } }
    val byKey = remember(apps) { apps.associateBy { it.key } }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val columns = 4
    val cellH = 84.dp
    val haptics = LocalHapticFeedback.current
    Column {
        Text(
            "Przytrzymaj ikonę i przeciągnij ją w nowe miejsce.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
        ) {
            val cellW = maxWidth / columns
            val density = LocalDensity.current
            val cellWPx = with(density) { cellW.toPx() }
            val cellHPx = with(density) { cellH.toPx() }
            val rows = (order.size + columns - 1) / columns
            Box(Modifier.fillMaxWidth().height(cellH * rows.coerceAtLeast(1))) {
                // Najpierw pary (indeks, aplikacja) bez odinstalowanych — w bloku key { } nie używamy "return",
                // bo kompilator zamienia go na konstrukcję, której Android (DEX) nie przyjmuje.
                order.mapIndexedNotNull { i, k -> byKey[k]?.let { Triple(i, k, it) } }.forEach { (index, k, app) ->
                    key(k) {
                        val dragged = draggingKey == k
                        Box(
                            Modifier
                                .offset {
                                    val base = IntOffset(((index % columns) * cellWPx).roundToInt(), ((index / columns) * cellHPx).roundToInt())
                                    if (dragged) base + IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) else base
                                }
                                .size(cellW, cellH)
                                .zIndex(if (dragged) 1f else 0f)
                                .graphicsLayer { if (dragged) { scaleX = 1.1f; scaleY = 1.1f; alpha = 0.9f } }
                                .pointerInput(k) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // drgnięcie: "złapane"
                                            draggingKey = k
                                            dragOffset = Offset.Zero
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            // Gdzie jest teraz środek przeciąganej ikony → jaki to indeks w siatce.
                                            val from = order.indexOf(k)
                                            val cx = (from % columns) * cellWPx + cellWPx / 2 + dragOffset.x
                                            val cy = (from / columns) * cellHPx + cellHPx / 2 + dragOffset.y
                                            val col = (cx / cellWPx).toInt().coerceIn(0, columns - 1)
                                            val row = (cy / cellHPx).toInt().coerceAtLeast(0)
                                            val to = (row * columns + col).coerceIn(0, order.lastIndex)
                                            if (to != from) {
                                                order.add(to, order.removeAt(from))
                                                // Ikona zmieniła "domowe" pole — przesunięcie liczymy od nowego, żeby została pod palcem.
                                                dragOffset -= Offset(
                                                    ((to % columns) - (from % columns)) * cellWPx,
                                                    ((to / columns) - (from / columns)) * cellHPx,
                                                )
                                            }
                                        },
                                        onDragEnd = { draggingKey = null; dragOffset = Offset.Zero },
                                        onDragCancel = { draggingKey = null; dragOffset = Offset.Zero },
                                    )
                                },
                        ) {
                            AppTile(app = app, onClick = {}, menu = emptyList(), enabled = false, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { onDone(order.toList()) }) { Text("Gotowe") }
        }
    }
}

// "Opcje folderu": ustawienia pogrupowane w sekcje, wybór jako chipy (widać od razu, co jest ustawione).
@Composable
private fun FolderOptionsDialog(
    folder: CardFolderData,
    isRoot: Boolean,
    canCopy: Boolean,
    onChange: ((CardFolderData) -> CardFolderData) -> Unit,
    onRename: () -> Unit,
    onLook: () -> Unit,
    onSaveToDrawer: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Opcje folderu") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OptionSection("Wygląd")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onRename) { Text("Zmień nazwę") }
                    TextButton(onClick = onLook) { Text("Symbol i kolor") }
                }
                OptionChips(
                    label = "Miniatura",
                    options = listOf(2 to "2×2", 3 to "3×3"),
                    selected = folder.grid,
                    onSelect = { g -> onChange { it.copy(grid = g) } },
                )
                OptionSection("Układ")
                OptionChips(
                    label = "Kolejność",
                    options = listOf(CardFolderData.SORT_MANUAL to "Moja", CardFolderData.SORT_ALPHA to "A–Z", CardFolderData.SORT_USAGE to "Najczęstsze"),
                    selected = folder.sort,
                    onSelect = { v -> onChange { it.copy(sort = v) } },
                )
                OptionChips(
                    label = "Wyrównanie",
                    options = listOf(CardFolderData.ALIGN_START to "⇤ Lewo", CardFolderData.ALIGN_CENTER to "↔ Środek", CardFolderData.ALIGN_END to "Prawo ⇥"),
                    selected = folder.align,
                    onSelect = { v -> onChange { it.copy(align = v) } },
                )
                OptionChips(
                    label = "Rzędy",
                    options = listOf(false to "Od góry", true to "Od dołu (kciuk)"),
                    selected = folder.fromBottom,
                    onSelect = { v -> onChange { it.copy(fromBottom = v) } },
                )
                OptionSection("Udostępnij")
                TextButton(onClick = onSaveToDrawer) { Text("Zapisz w szufladzie (Foldery)") }
                if (canCopy) TextButton(onClick = onCopy) { Text("Kopiuj do trybu…") }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onRemove) {
                    Text(if (isRoot) "Rozwiąż folder" else "Usuń podfolder (aplikacje wyżej)", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
    )
}

@Composable
private fun OptionSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

// Rząd chipów z podpisem; generyczne T (jak w C#), bo wartości to raz liczby, raz napisy, raz Boolean.
@Composable
private fun <T> OptionChips(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { (value, text) ->
            androidx.compose.material3.FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(text) },
            )
        }
    }
}
