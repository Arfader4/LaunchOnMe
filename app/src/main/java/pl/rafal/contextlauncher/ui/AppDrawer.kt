package pl.rafal.contextlauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.AppInfo

// Szuflada z dwiema zakładkami: wszystkie aplikacje (z wyszukiwarką) i foldery.
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
    foldersContent: @Composable () -> Unit,       // zakładka "Foldery" (slot)
    frequent: List<AppInfo>,                      // najczęściej uruchamiane w aktywnym trybie
    frequentLabel: String,
    autoFocusSearch: Boolean,                     // true = od razu kursor i klawiatura w wyszukiwarce
    hiddenApps: List<AppInfo> = emptyList(),      // ukryte w aktywnym trybie (na końcu listy, po rozwinięciu)
    dragOut: ExternalDrag? = null,                // przeciąganie ikon na kartę
    translucent: Boolean = false,                 // tapeta prześwituje przez tło (jak w One UI)
    modifier: Modifier = Modifier,
    onOpenAppSettings: (() -> Unit)? = null,      // skrót do systemowych ustawień "Aplikacje"
) {
    var showFolders by rememberSaveable { mutableStateOf(false) }

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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            FilterChip(selected = !showFolders, onClick = { showFolders = false }, label = { Text("Wszystkie") })
            FilterChip(selected = showFolders, onClick = { showFolders = true }, label = { Text("Foldery") })
            Spacer(Modifier.weight(1f))
            if (onOpenAppSettings != null) {
                AssistChip(onClick = onOpenAppSettings, label = { Text("⚙ Aplikacje") })
            }
        }

        // Przesunięcie palcem w lewo → Foldery, w prawo → Wszystkie (jak zakładki w Galerii).
        // Przeciąganie ikony po przytrzymaniu "zjada" gest, więc nie przełącza zakładek przypadkiem.
        val swipeThreshold = with(density) { 72.dp.toPx() }
        var swipe by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipe = 0f },
                        onDragEnd = {
                            if (swipe < -swipeThreshold) showFolders = true
                            if (swipe > swipeThreshold) showFolders = false
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
                    Column { foldersContent() }
                } else {
                    Column { AllAppsTab(apps, query, onQueryChange, onAppClick, menuFor, frequent, frequentLabel, autoFocusSearch, hiddenApps, dragOut) }
                }
            }
        }
    }
}

@Composable
private fun AllAppsTab(
    apps: List<AppInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,
    frequent: List<AppInfo>,
    frequentLabel: String,
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
            placeholder = { Text("Szukaj aplikacji") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            // Enter na klawiaturze uruchamia pierwszą znalezioną aplikację.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { apps.firstOrNull()?.let(onAppClick) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .focusRequester(focusRequester),
        )

        // Lazy = rysuje tylko to, co widać (jak wirtualizacja w ListView/ItemsControl).
        // Szuflada wchodzi do kompozycji przy otwarciu, więc to jest chwila otwarcia (start "fali" ikon).
        val openedAt = remember { System.currentTimeMillis() }
        val frequentOffset = if (query.isBlank()) frequent.size else 0
        LazyVerticalGrid(
            columns = GridCells.Fixed(LauncherViewModel.COLUMNS),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Bez wpisanego tekstu: najpierw rząd "Często w trybie…", potem wszystkie.
            if (query.isBlank() && frequent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(frequentLabel) }
                itemsIndexed(frequent, key = { _, a -> "fav:" + a.key }) { index, app ->
                    AppTile(
                        app = app, onClick = { onAppClick(app) }, menu = menuFor(app), dragOut = dragOut,
                        modifier = Modifier.animateItem().staggerIn(index, openedAt),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Wszystkie aplikacje") }
            }
            // animateItem: przy wpisywaniu w wyszukiwarkę ikony płynnie przesuwają się na nowe miejsca.
            itemsIndexed(apps, key = { _, a -> a.key }) { index, app ->
                AppTile(
                    app = app, onClick = { onAppClick(app) }, menu = menuFor(app), dragOut = dragOut,
                    modifier = Modifier.animateItem().staggerIn(index + frequentOffset, openedAt),
                )
            }
            // Ukryte: tylko na liście (nie w wyszukiwaniu), zwinięte za jednym dotknięciem.
            if (query.isBlank() && hiddenApps.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = { showHidden = !showHidden }) {
                        Text(if (showHidden) "Schowaj ukryte" else "Pokaż ukryte w tym trybie (${hiddenApps.size})")
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}
