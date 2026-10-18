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

// Układanie własnej kolejności: przytrzymaj ikonę i przeciągnij na nowe miejsce (pozostałe się rozstępują).
// Zmiany trafiają do zapisu dopiero po "Gotowe"; "Anuluj" i Wstecz zostawiają poprzednią kolejność.
// Wspólne dla szuflady i folderów (1.7.1). <T> = typ generyczny, jak ReorderGrid<T> w C#.
@Composable
fun <T> ReorderGrid(
    initial: List<T>,
    key: (T) -> String,
    label: (T) -> String,
    hint: String,
    onDone: (List<String>) -> Unit,
    onCancel: () -> Unit,
    tile: @Composable (T) -> Unit,
) {
    val collator = remember { Collator.getInstance() }
    var list by remember { mutableStateOf(initial) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var awaitIndex by remember { mutableStateOf(-1) } // po zamianie: czekamy, aż układ siatki ją pokaże
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 64.dp.toPx() }

    BackHandler(onBack = onCancel)

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(
                stringResource(R.string.drawer_arrange_title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Punkt wyjścia od nowa: A–Z (domyślna "własna" kolejność).
            TextButton(onClick = { list = list.sortedWith(compareBy<T, Any>(collator) { label(it) }) }) {
                Text(stringResource(R.string.drawer_sort_alpha_short))
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
            Button(onClick = { onDone(list.map(key)) }) { Text(stringResource(R.string.drawer_arrange_done)) }
        }
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(LauncherViewModel.COLUMNS),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    fun LazyGridItemInfo.hit(p: Offset) =
                        p.x >= offset.x && p.x < offset.x + size.width && p.y >= offset.y && p.y < offset.y + size.height
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.hit(pos) }?.let {
                                dragKey = it.key as? String
                                dragOffset = Offset.Zero
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, amount ->
                            val dragged = dragKey
                            val info = gridState.layoutInfo
                            val current = if (dragged == null) null else info.visibleItemsInfo.firstOrNull { it.key == dragged }
                            if (dragged != null && current != null) {
                                change.consume()
                                dragOffset += amount
                                val center = Offset(
                                    current.offset.x + current.size.width / 2f,
                                    current.offset.y + current.size.height / 2f,
                                ) + dragOffset
                                // Po zamianie stare położenie z układu jest nieaktualne do następnej klatki —
                                // do tego czasu nie sprawdzamy kolejnej zamiany (inaczej ikony "migają" tam i z powrotem).
                                if (awaitIndex >= 0 && current.index == awaitIndex) awaitIndex = -1
                                // Środek przeciąganej ikony nad inną → zamiana miejsc (reszta przesuwa się o jedno).
                                val target = if (awaitIndex >= 0) null
                                else info.visibleItemsInfo.firstOrNull { it.key != dragged && it.hit(center) }
                                val from = list.indexOfFirst { key(it) == dragKey }
                                val to = if (target == null) -1 else list.indexOfFirst { key(it) == target.key }
                                if (target != null && from >= 0 && to >= 0) {
                                    list = list.toMutableList().apply { add(to, removeAt(from)) }
                                    awaitIndex = to
                                    // Ikona "przeskoczyła" w układzie na miejsce celu — przesunięcie liczymy od nowego miejsca.
                                    dragOffset += Offset(
                                        (current.offset.x - target.offset.x).toFloat(),
                                        (current.offset.y - target.offset.y).toFloat(),
                                    )
                                }
                                // Przy krawędzi lista sama się przewija; ikona zostaje pod palcem.
                                val y = change.position.y
                                val step = when {
                                    y < edge -> -24f
                                    y > info.viewportSize.height - edge -> 24f
                                    else -> 0f
                                }
                                if (step != 0f) scope.launch {
                                    val used = gridState.scrollBy(step)
                                    dragOffset += Offset(0f, used)
                                }
                            }
                        },
                        onDragEnd = { dragKey = null; dragOffset = Offset.Zero; awaitIndex = -1 },
                        onDragCancel = { dragKey = null; dragOffset = Offset.Zero; awaitIndex = -1 },
                    )
                },
        ) {
            items(list, key = { key(it) }) { item ->
                val dragging = key(item) == dragKey
                Box(
                    if (dragging) Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationX = dragOffset.x; translationY = dragOffset.y }
                        .scale(1.08f)
                    else Modifier.animateItem(),
                ) { tile(item) }
            }
        }
    }
}

// Przycisk sortowania (szuflada i folder): A–Z / najczęściej / własna + "Ułóż ręcznie…" i ewentualne dodatkowe pozycje.
// compact = sam symbol ⇅ (ciasny pasek akcji folderu).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortChip(
    sort: DrawerSort,
    onSortChange: (DrawerSort) -> Unit,
    onArrange: () -> Unit,
    extra: List<MenuAction> = emptyList(),
    compact: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(if (compact) "⇅" else "⇅ " + drawerSortShortLabel(sort)) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DrawerSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(drawerSortLabel(option)) },
                    leadingIcon = { Text(if (option == sort) "✓" else " ") },
                    onClick = {
                        open = false
                        onSortChange(option)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.drawer_arrange)) }, onClick = { open = false; onArrange() })
            extra.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, onClick = { open = false; action.onClick() })
            }
        }
    }
}

@Composable
fun drawerSortShortLabel(sort: DrawerSort): String = when (sort) {
    DrawerSort.ALPHA -> stringResource(R.string.drawer_sort_alpha_short)
    DrawerSort.MOST_USED -> stringResource(R.string.drawer_sort_most_used_short)
    DrawerSort.MANUAL -> stringResource(R.string.drawer_sort_manual_short)
}
