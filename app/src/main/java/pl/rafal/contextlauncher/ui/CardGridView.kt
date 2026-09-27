package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import pl.rafal.contextlauncher.data.CustomWidgetKind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import pl.rafal.contextlauncher.data.CardApp
import pl.rafal.contextlauncher.data.CardCustomWidget
import pl.rafal.contextlauncher.data.CardElement
import pl.rafal.contextlauncher.data.CardWidget
import pl.rafal.contextlauncher.data.widgets.LauncherWidgets
import pl.rafal.contextlauncher.layout.CardGrid
import pl.rafal.contextlauncher.layout.GridRect
import kotlin.math.roundToInt

// Swobodny układ karty: elementy leżą na niewidocznej siatce 8×12.
// W trybie edycji siatka pokazuje się jako kropki; elementy można przeciągać, zmieniać im rozmiar i usuwać.
@Composable
fun CardGridView(
    elements: List<CardElement>,
    editing: Boolean,
    widgets: LauncherWidgets,
    onLaunch: (CardApp) -> Unit,
    menuFor: (CardApp) -> List<MenuAction>,
    onMove: (CardElement, x: Int, y: Int) -> Unit,
    onResize: (CardElement, w: Int, h: Int) -> Unit,
    onRemove: (CardElement) -> Unit,
    // Slot na własne widżety: CardGridView ich nie zna, tylko wie, gdzie je narysować.
    customWidget: @Composable (widget: CardCustomWidget, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onGridPlaced: (bounds: Rect, cellPx: Float) -> Unit = { _, _ -> }, // gdzie leży siatka (do upuszczania z szuflady)
    onDropIntoFolder: (CardApp, CardCustomWidget) -> Unit = { _, _ -> }, // ikona upuszczona na widżet folderu
) {
    // BoxWithConstraints zna dostępne miejsce (maxWidth, maxHeight), więc liczymy rozmiar komórki.
    BoxWithConstraints(modifier) {
        // Komórki są kwadratowe; bierzemy mniejszy wymiar, żeby cała siatka się zmieściła.
        val cell = min(maxWidth / CardGrid.COLUMNS, maxHeight / CardGrid.ROWS)
        val cellPx = with(LocalDensity.current) { cell.toPx() }

        // Stan gestów: przeciąganie (przesuwa element) albo zmiana rozmiaru (ciągnięcie za uchwyt).
        var draggingId by remember { mutableStateOf<Long?>(null) }
        var resizingId by remember { mutableStateOf<Long?>(null) }
        var gestureOffset by remember { mutableStateOf(Offset.Zero) }
        // Pozycja/rozmiar "na chwilę" po upuszczeniu, zanim baza odeśle nowe dane (bez tego element by mignął).
        var pending by remember { mutableStateOf<Pair<Long, GridRect>?>(null) }
        LaunchedEffect(elements) { pending = null } // przyszły świeże dane z bazy

        // rememberUpdatedState: gesty żyją dłużej niż jedno przerysowanie, a mają widzieć najnowsze dane.
        val currentElements by rememberUpdatedState(elements)
        val currentOnMove by rememberUpdatedState(onMove)
        val currentOnResize by rememberUpdatedState(onResize)
        val currentOnDropIntoFolder by rememberUpdatedState(onDropIntoFolder)

        val dotColor = MaterialTheme.colorScheme.outline
        val okColor = MaterialTheme.colorScheme.primary
        val badColor = MaterialTheme.colorScheme.error

        // Gdzie wyląduje element po zakończeniu gestu (dla podglądu i dla zapisu).
        fun targetOf(rect: GridRect, id: Long): GridRect = when (id) {
            draggingId -> CardGrid.snap(rect, gestureOffset.x / cellPx, gestureOffset.y / cellPx)
            resizingId -> CardGrid.resize(rect, gestureOffset.x / cellPx, gestureOffset.y / cellPx)
            else -> rect
        }

        fun fits(target: GridRect, id: Long): Boolean =
            CardGrid.canPlace(target, currentElements.filter { it.item.id != id }.map { it.rect })

        fun resetGesture() {
            draggingId = null
            resizingId = null
            gestureOffset = Offset.Zero
        }

        Box(
            Modifier
                .size(cell * CardGrid.COLUMNS, cell * CardGrid.ROWS)
                .align(Alignment.TopCenter)
                .onGloballyPositioned { onGridPlaced(it.boundsInRoot(), cellPx) }
                .drawBehind {
                    if (editing) {
                        for (gx in 0..CardGrid.COLUMNS) for (gy in 0..CardGrid.ROWS) {
                            drawCircle(dotColor, radius = 1.5.dp.toPx(), center = Offset(gx * cellPx, gy * cellPx))
                        }
                    }
                },
        ) {
            // Podgląd miejsca docelowego: bursztynowy, gdy się mieści, czerwony, gdy koliduje.
            val active = elements.firstOrNull { it.item.id == draggingId || it.item.id == resizingId }
            if (active != null) {
                val target = targetOf(active.rect, active.item.id)
                Box(
                    Modifier
                        .offset(cell * target.x, cell * target.y)
                        .size(cell * target.w, cell * target.h)
                        .clip(RoundedCornerShape(16.dp))
                        .background((if (fits(target, active.item.id)) okColor else badColor).copy(alpha = 0.18f)),
                )
            }

            elements.forEach { element ->
                // key(...) mówi Compose, który element jest który, gdy lista się zmienia.
                key(element.item.id) {
                    val id = element.item.id
                    val isDragged = id == draggingId
                    val shown = pending?.takeIf { it.first == id }?.second ?: element.rect

                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (shown.x * cellPx + if (isDragged) gestureOffset.x else 0f).roundToInt(),
                                    (shown.y * cellPx + if (isDragged) gestureOffset.y else 0f).roundToInt(),
                                )
                            }
                            .size(cell * shown.w, cell * shown.h)
                            .zIndex(if (isDragged || id == resizingId) 1f else 0f)
                            .then(
                                if (!editing) Modifier
                                else Modifier.pointerInput(id, shown, cellPx) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = id
                                            gestureOffset = Offset.Zero
                                        },
                                        onDragEnd = {
                                            // Ikona aplikacji puszczona środkiem na widżet folderu → trafia do folderu.
                                            val cx = shown.x + shown.w / 2f + gestureOffset.x / cellPx
                                            val cy = shown.y + shown.h / 2f + gestureOffset.y / cellPx
                                            val folder = (element as? CardApp)?.let {
                                                currentElements.filterIsInstance<CardCustomWidget>().firstOrNull { w ->
                                                    w.kind == CustomWidgetKind.FOLDER &&
                                                        cx >= w.rect.x && cx < w.rect.x + w.rect.w && cy >= w.rect.y && cy < w.rect.y + w.rect.h
                                                }
                                            }
                                            val target = targetOf(shown, id)
                                            if (folder != null) {
                                                currentOnDropIntoFolder(element as CardApp, folder)
                                            } else if (target != shown && fits(target, id)) {
                                                pending = id to target
                                                currentOnMove(element, target.x, target.y)
                                            }
                                            resetGesture()
                                        },
                                        onDragCancel = { resetGesture() },
                                    ) { change, amount ->
                                        change.consume()
                                        gestureOffset += amount
                                    }
                                },
                            ),
                    ) {
                        // when na sealed interface: kompilator pilnuje, że obsłużyliśmy każdy typ elementu.
                        when (element) {
                            is CardApp -> AppTile(
                                app = element.app,
                                onClick = { onLaunch(element) },
                                menu = menuFor(element),
                                enabled = !editing,
                                modifier = Modifier.fillMaxSize(),
                            )
                            is CardWidget -> WidgetCell(
                                widget = element,
                                widgets = widgets,
                                width = cell * shown.w,
                                height = cell * shown.h,
                                editing = editing,
                            )
                            is CardCustomWidget -> customWidget(element, !editing)
                        }

                        if (editing) {
                            RemoveBadge(
                                onClick = { onRemove(element) },
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                            // Rozmiar zmieniamy tylko widżetom; ikona aplikacji ma stałe 2×2.
                            if (element !is CardApp) {
                                ResizeHandle(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        // Uchwyt ma własny gest; change.consume() sprawia, że rodzic nie zacznie przesuwania.
                                        .pointerInput(id, shown, cellPx) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    resizingId = id
                                                    gestureOffset = Offset.Zero
                                                },
                                                onDragEnd = {
                                                    val target = targetOf(shown, id)
                                                    if (target != shown && fits(target, id)) {
                                                        pending = id to target
                                                        currentOnResize(element, target.w, target.h)
                                                    }
                                                    resetGesture()
                                                },
                                                onDragCancel = { resetGesture() },
                                            ) { change, amount ->
                                                change.consume()
                                                gestureOffset += amount
                                            }
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

// Widżet innej aplikacji osadzony w Compose. AndroidView to "most" do zwykłych widoków Androida
// (jak WindowsFormsHost osadzający kontrolkę WinForms w WPF).
@Composable
private fun WidgetCell(
    widget: CardWidget,
    widgets: LauncherWidgets,
    width: Dp,
    height: Dp,
    editing: Boolean,
) {
    val padding = 4.dp
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        AndroidView(
            factory = { context -> widgets.createView(context, widget.appWidgetId) },
            update = { view ->
                view.blockTouches = editing
                // Po zmianie rozmiaru widżet dostaje nowe wymiary i sam dobiera układ (np. więcej dni pogody).
                view.updateSizeDp((width - padding * 2).value, (height - padding * 2).value)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// Kółko z krzyżykiem w rogu elementu w trybie edycji.
@Composable
private fun RemoveBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp) // wygodny obszar dotyku
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Usuń z trybu" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text("×", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// Uchwyt zmiany rozmiaru w prawym dolnym rogu: dwie ukośne kreski na kółku.
@Composable
private fun ResizeHandle(modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.primary
    val fg = MaterialTheme.colorScheme.onPrimary
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = "Zmień rozmiar" },
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawCircle(bg)
            val stroke = 2.dp.toPx()
            val s = size.width
            drawLine(fg, Offset(s * 0.35f, s * 0.7f), Offset(s * 0.7f, s * 0.35f), stroke, StrokeCap.Round)
            drawLine(fg, Offset(s * 0.55f, s * 0.72f), Offset(s * 0.72f, s * 0.55f), stroke, StrokeCap.Round)
        }
    }
}
