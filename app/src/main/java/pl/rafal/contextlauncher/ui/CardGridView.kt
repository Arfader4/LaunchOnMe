package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import pl.rafal.contextlauncher.data.CustomWidgetKind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R
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
import pl.rafal.contextlauncher.data.StackData
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
    onLayout: (Map<Long, GridRect>) -> Unit, // nowe pozycje: przesuwany element + wypchnięte sąsiednie
    onRemove: (CardElement) -> Unit,
    onUninstall: (CardApp) -> Unit = {},     // upuszczenie ikony na "Odinstaluj"
    onLongPressItem: () -> Unit = {},        // przytrzymanie czegokolwiek na karcie → edycja układu
    // Slot na własne widżety: CardGridView ich nie zna, tylko wie, gdzie je narysować.
    customWidget: @Composable (widget: CardCustomWidget, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onGridPlaced: (bounds: Rect, cellW: Float, cellH: Float) -> Unit = { _, _, _ -> }, // gdzie leży siatka (do upuszczania z szuflady)
    onEmptyLongPress: () -> Unit = {}, // przytrzymanie pustego miejsca karty → edycja układu
    onDropIntoFolder: (CardApp, CardCustomWidget) -> Unit = { _, _ -> }, // ikona upuszczona na widżet folderu
    onMergeApps: (dragged: CardApp, target: CardApp) -> Unit = { _, _ -> }, // ikona na ikonę → nowy folder
    onConfigure: (CardCustomWidget) -> Unit = {},              // ⚙ w edycji: ustawienia widżetu
    canConfigure: (CardCustomWidget) -> Boolean = { false },
    gap: Dp = 0.dp, // odstęp między elementami karty (ustawienia trybu → Układ karty)
    // Upuszczenie przy lewej/prawej krawędzi w edycji → element na sąsiednią stronę (-1 / +1).
    // Przeniesienie na inną stronę: przytrzymanie przy krawędzi przewija stronę (onEdgeDwell), a puszczenie
    // na nowej stronie wywołuje onMoveToPage(element, o ile stron, miejsce na tamtej stronie).
    onMoveToPage: (CardElement, Int, GridRect) -> Unit = { _, _, _ -> },
    canMoveToPage: (Int) -> Boolean = { false },      // czy strona "o tyle dalej" (od tej) istnieje / może powstać
    onEdgeDwell: (Int) -> Boolean = { false },        // przewiń do strony "o tyle dalej"; true = przewinięto
    pageShift: (Int) -> Float = { 0f },               // bieżące przesunięcie na ekranie strony "o tyle dalej" (0 = ta)
    onDraggingChange: (Boolean) -> Unit = {},         // przeciąganie trwa — strona musi zostać narysowana na wierzchu
    // Stosy: widżety danego stosu (w kolejności), upuszczenie widżetu na widżet, przewinięcie stosu.
    stackMembers: (CardCustomWidget) -> List<CardElement> = { emptyList() },
    onStack: (dragged: CardElement, target: CardElement) -> Unit = { _, _ -> },
    onStackIndex: (CardCustomWidget, Int) -> Unit = { _, _ -> },
) {
    // BoxWithConstraints zna dostępne miejsce (maxWidth, maxHeight), więc liczymy rozmiar komórki.
    BoxWithConstraints(modifier) {
        // Komórki są kwadratowe; bierzemy mniejszy wymiar, żeby cała siatka się zmieściła.
        // Komórki (prawie) kwadratowe, a rzędów tyle, ile zmieści ekran: na wysokim telefonie więcej niż 12.
        // Dzięki temu zyskujemy miejsce, zamiast rozciągać ikony w pionie. Resztę wysokości rozdzielamy równo.
        val cellW = maxWidth / CardGrid.COLUMNS
        val rows = maxOf(CardGrid.ROWS, (maxHeight / cellW).toInt())
        val cellH = maxHeight / rows
        SideEffect { CardGrid.rows = rows } // logika siatki (kolizje, wolne miejsca) zna teraz prawdziwą liczbę rzędów
        val cellWPx = with(LocalDensity.current) { cellW.toPx() }
        val cellHPx = with(LocalDensity.current) { cellH.toPx() }

        // Stan gestów: przeciąganie (przesuwa element) albo zmiana rozmiaru (ciągnięcie za uchwyt).
        var draggingId by remember { mutableStateOf<Long?>(null) }
        var resizingId by remember { mutableStateOf<Long?>(null) }
        var gestureOffset by remember { mutableStateOf(Offset.Zero) }
        var dragStartPointer by remember { mutableStateOf(Offset.Zero) } // gdzie palec złapał element (piksele siatki)
        var menuOpenFor by remember { mutableStateOf<Long?>(null) }       // menu aplikacji w edycji (dotknięcie ikony)
        // Upuszczenie wyżej niż ta linia (8 dp poniżej górnej krawędzi karty) = pasek usuwania.
        val zoneLimitPx = with(LocalDensity.current) { (ZoneTop + ZoneHeight).toPx() }
        val currentOnLongPressItem by rememberUpdatedState(onLongPressItem)
        val haptics = LocalHapticFeedback.current // drgnięcie przy złapaniu elementu (jak w każdym launcherze)
        val gapHalfPx = with(LocalDensity.current) { (gap / 2).toPx() }
        // Pas przy krawędzi (plus margines karty poza siatką), w którym upuszczenie zmienia stronę.
        val edgePx = with(LocalDensity.current) { 10.dp.toPx() }
        val gridWidthPx = cellWPx * CardGrid.COLUMNS
        val currentOnMoveToPage by rememberUpdatedState(onMoveToPage)
        val currentCanMoveToPage by rememberUpdatedState(canMoveToPage)
        val currentOnEdgeDwell by rememberUpdatedState(onEdgeDwell)
        val currentPageShift by rememberUpdatedState(pageShift)
        // O ile stron przeniesiono przeciągany element (przytrzymaniem przy krawędzi). 0 = wciąż na swojej stronie.
        var carried by remember { mutableIntStateOf(0) }
        // Przesunięcie tej strony w chwili złapania elementu. Ruch palca (gestureOffset) jest liczony w układzie ekranu,
        // więc gdy strona odjeżdża, rysujemy element z poprawką (start − teraz) — zostaje pod palcem.
        val startShift = remember { FloatArray(1) }
        // -1 = lewa krawędź, 1 = prawa, 0 = żadna (albo w tę stronę nie ma już stron).
        // Liczone względem strony WIDOCZNEJ (pointer + przesunięcie tej strony na ekranie).
        fun edgeOf(pointer: Offset): Int {
            val x = pointer.x + startShift[0]
            return when {
                x < edgePx && currentCanMoveToPage(carried - 1) -> -1
                x > gridWidthPx - edgePx && currentCanMoveToPage(carried + 1) -> 1
                else -> 0
            }
        }
        var edgeHover by remember { mutableIntStateOf(0) }
        // Pozycja/rozmiar "na chwilę" po upuszczeniu, zanim baza odeśle nowe dane (bez tego element by mignął).
        var pending by remember { mutableStateOf<Map<Long, GridRect>?>(null) }
        LaunchedEffect(elements) { pending = null } // przyszły świeże dane z bazy

        // rememberUpdatedState: gesty żyją dłużej niż jedno przerysowanie, a mają widzieć najnowsze dane.
        val currentElements by rememberUpdatedState(elements)
        val currentOnLayout by rememberUpdatedState(onLayout)
        val currentOnDropIntoFolder by rememberUpdatedState(onDropIntoFolder)
        val currentOnMergeApps by rememberUpdatedState(onMergeApps)
        val currentOnStack by rememberUpdatedState(onStack)
        val currentOnRemove by rememberUpdatedState(onRemove)
        val currentOnUninstall by rememberUpdatedState(onUninstall)

        val dotColor = MaterialTheme.colorScheme.outline
        val okColor = MaterialTheme.colorScheme.primary
        val badColor = MaterialTheme.colorScheme.error

        // Gdzie wyląduje element po zakończeniu gestu (dla podglądu i dla zapisu).
        fun targetOf(rect: GridRect, id: Long): GridRect = when (id) {
            draggingId -> CardGrid.snap(rect, gestureOffset.x / cellWPx, gestureOffset.y / cellHPx)
            resizingId -> CardGrid.resize(rect, gestureOffset.x / cellWPx, gestureOffset.y / cellHPx)
            else -> rect
        }

        // Ikona aplikacji, której środek jest nad widżetem folderu (wtedy upuszczenie = wrzucenie do folderu).
        fun folderUnder(element: CardElement, shown: GridRect): CardCustomWidget? {
            if (element !is CardApp) return null
            val cx = shown.x + shown.w / 2f + gestureOffset.x / cellWPx
            val cy = shown.y + shown.h / 2f + gestureOffset.y / cellHPx
            return currentElements.filterIsInstance<CardCustomWidget>().firstOrNull { w ->
                (w.kind == CustomWidgetKind.FOLDER || w.kind == CustomWidgetKind.CARD_FOLDER) &&
                    cx >= w.rect.x && cx < w.rect.x + w.rect.w && cy >= w.rect.y && cy < w.rect.y + w.rect.h
            }
        }

        // Inna ikona, nad którą jest ŚRODEK przeciąganej → upuszczenie tworzy folder (jak w Androidzie).
        // Gdy środek jest obok (ikony tylko częściowo na siebie nachodzą), działa zwykłe odsuwanie.
        fun mergeTargetUnder(element: CardElement, shown: GridRect): CardApp? {
            if (element !is CardApp) return null
            val cx = shown.x + shown.w / 2f + gestureOffset.x / cellWPx
            val cy = shown.y + shown.h / 2f + gestureOffset.y / cellHPx
            return currentElements.filterIsInstance<CardApp>().firstOrNull { other ->
                val r = other.rect
                other.item.id != element.item.id && cx >= r.x && cx < r.x + r.w && cy >= r.y && cy < r.y + r.h
            }
        }

        // Widżet (nie ikona, nie folder, nie sam stos) środkiem nad innym widżetem → stos w miejscu tamtego.
        // Liczy się tylko środkowa połowa celu, żeby zwykłe przesuwanie widżetów obok siebie nie tworzyło stosów.
        fun stackTargetUnder(element: CardElement, shown: GridRect): CardElement? {
            fun stackable(e: CardElement) = when (e) {
                is CardApp -> false
                is CardWidget -> true
                is CardCustomWidget -> e.kind != CustomWidgetKind.FOLDER && e.kind != CustomWidgetKind.CARD_FOLDER
            }
            if (!stackable(element) || (element is CardCustomWidget && element.kind == CustomWidgetKind.STACK)) return null
            val cx = shown.x + shown.w / 2f + gestureOffset.x / cellWPx
            val cy = shown.y + shown.h / 2f + gestureOffset.y / cellHPx
            return currentElements.firstOrNull { other ->
                val r = other.rect
                other.item.id != element.item.id && stackable(other) &&
                    cx >= r.x + r.w * 0.25f && cx < r.x + r.w * 0.75f && cy >= r.y + r.h * 0.25f && cy < r.y + r.h * 0.75f
            }
        }

        // Plan z wypychaniem: gdzie wyląduje przesuwany element i dokąd odsuną się ci, na których nachodzi.
        fun planFor(target: GridRect, id: Long): Map<Long, GridRect>? =
            CardGrid.placeWithPush(id, target, currentElements.associate { it.item.id to it.rect })

        fun fits(target: GridRect, id: Long): Boolean = planFor(target, id) != null

        fun resetGesture() {
            draggingId = null
            resizingId = null
            gestureOffset = Offset.Zero
            carried = 0
            edgeHover = 0
        }

        // Przytrzymanie przy krawędzi ok. 0,65 s → strona obok (także nowa pusta w edycji). Palec dalej przy krawędzi →
        // co 0,75 s kolejna strona. Timer, bo palec, który stoi, nie wysyła zdarzeń.
        LaunchedEffect(edgeHover) {
            val dir = edgeHover
            if (dir == 0) return@LaunchedEffect
            delay(650)
            while (draggingId != null && edgeOf(dragStartPointer + gestureOffset) == dir) {
                if (!currentOnEdgeDwell(carried + dir)) break
                carried += dir
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(750)
            }
        }
        val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)
        LaunchedEffect(draggingId != null) { currentOnDraggingChange(draggingId != null) }

        Box(
            Modifier
                .size(cellW * CardGrid.COLUMNS, cellH * rows)
                .align(Alignment.TopCenter)
                .onGloballyPositioned { onGridPlaced(it.boundsInRoot(), cellWPx, cellHPx) }
                .drawBehind {
                    if (editing) {
                        for (gx in 0..CardGrid.COLUMNS) for (gy in 0..rows) {
                            drawCircle(dotColor, radius = 1.5.dp.toPx(), center = Offset(gx * cellWPx, gy * cellHPx))
                        }
                    }
                },
        ) {
            // Warstwa tła: przytrzymanie pustego miejsca otwiera edycję układu (jak w każdym launcherze).
            // Leży pod elementami, więc ikony i widżety dostają dotyk pierwsze.
            if (!editing) {
                val currentOnEmptyLongPress by rememberUpdatedState(onEmptyLongPress)
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { currentOnEmptyLongPress() }) },
                )
            }

            // Podgląd miejsca docelowego: bursztynowy, gdy się mieści, czerwony, gdy koliduje.
            // Wypychane elementy już w trakcie przeciągania odsuwają się tam, gdzie wylądują.
            val active = elements.firstOrNull { it.item.id == draggingId || it.item.id == resizingId }
            // Cel "do folderu": widżet folderu albo ikona, z której powstanie folder (wtedy nikogo nie odsuwamy).
            // Element przeniesiony na inną stronę: na starej stronie nie pokazujemy podglądu ani odsuwania.
            val dropInto: GridRect? = active?.takeIf { it.item.id == draggingId && carried == 0 }?.let {
                (folderUnder(it, it.rect) ?: mergeTargetUnder(it, it.rect) ?: stackTargetUnder(it, it.rect))?.rect
            }
            val livePlan: Map<Long, GridRect>? = active?.let {
                if (dropInto != null || carried != 0) null else planFor(targetOf(it.rect, it.item.id), it.item.id)
            }
            val settleScope = rememberCoroutineScope()
            elements.forEach { element ->
                // key(...) mówi Compose, który element jest który, gdy lista się zmienia.
                key(element.item.id) {
                    val id = element.item.id
                    val isDragged = id == draggingId
                    val shown = pending?.get(id)
                        ?: livePlan?.get(id)?.takeIf { id != draggingId && id != resizingId } // wypychany sąsiad
                        ?: element.rect
                    // Ruch w siatce: miejsce elementu dojeżdża sprężyście (wypychani sąsiedzi, nowe ułożenie),
                    // a odłożony element "dopływa" z miejsca, gdzie puścił go palec (settle → 0).
                    // W jednostkach kratek (nie pikselach): zmiana rozmiaru kratki (wstawki, pasek edycji, obrót)
                    // nie uruchamia przesuwania wszystkich elementów naraz.
                    val base by animateOffsetAsState(
                        Offset(shown.x.toFloat(), shown.y.toFloat()),
                        Motion.page(),
                        label = "miejsce elementu",
                    )
                    val settle = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
                    val lift by animateFloatAsState(if (isDragged) 1f else 0f, Motion.press(), label = "uniesienie")
                    // Cień tylko pod własnymi widżetami z tłem; ikona, naklejka i widżety systemowe (często przezroczyste)
                    // dostałyby cień prostokąta jak plamę.
                    val hasCard = element is CardCustomWidget && element.kind != CustomWidgetKind.STICKER
                    val corner = LocalWidgetCorner.current // zaokrąglenie cienia = zaokrąglenie widżetu

                    Box(
                        Modifier
                            .offset {
                                if (isDragged) {
                                    IntOffset(
                                        (shown.x * cellWPx + gestureOffset.x + startShift[0] - currentPageShift(0)).roundToInt(),
                                        (shown.y * cellHPx + gestureOffset.y).roundToInt(),
                                    )
                                } else {
                                    IntOffset((base.x * cellWPx + settle.value.x).roundToInt(), (base.y * cellHPx + settle.value.y).roundToInt())
                                }
                            }
                            .size(cellW * shown.w, cellH * shown.h)
                            .padding(gap / 2) // połowa odstępu z każdej strony = pełny odstęp między sąsiadami
                            .zIndex(if (isDragged || id == resizingId || settle.isRunning) 1f else 0f) // dopływający nad sąsiadami
                            // Przeciągany element "unosi się": lekko przezroczysty i powiększony, a pod nim widać cel.
                            .graphicsLayer {
                                // Uniesienie narasta sprężyście: powiększenie, lekka przezroczystość i cień pod widżetem.
                                val l = lift.coerceIn(0f, 1f) // sprężyna może "przestrzelić" poniżej zera
                                val s = 1f + (if (hasCard) 0.05f else 0.12f) * l
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.18f * l
                                if (hasCard && l > 0f) {
                                    shadowElevation = 14.dp.toPx() * l
                                    shape = RoundedCornerShape(corner)
                                    clip = false
                                }
                            }
                            .then(
                                // Poza edycją: przytrzymanie CZEGOKOLWIEK (ikony, widżetu) otwiera edycję układu.
                                if (!editing) Modifier.longPressAnywhere { currentOnLongPressItem() }
                                else Modifier.pointerInput(id, shown, cellWPx, cellHPx) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            settleScope.launch { settle.snapTo(Offset.Zero) } // przerwane dopływanie z poprzedniego odłożenia
                                            draggingId = id
                                            gestureOffset = Offset.Zero
                                            menuOpenFor = null
                                            // + połowa odstępu: "start" liczy się od treści, a ta jest wcięta o gap/2.
                                            dragStartPointer = Offset(shown.x * cellWPx + gapHalfPx, shown.y * cellHPx + gapHalfPx) + start
                                            startShift[0] = currentPageShift(0)
                                        },
                                        onDragEnd = {
                                            // Upuszczenie ponad kartą (na pasku u góry): "Usuń z karty" (lewa połowa) / "Odinstaluj" (prawa, tylko aplikacje).
                                            val pointer = dragStartPointer + gestureOffset
                                            if (pointer.y < zoneLimitPx) {
                                                val rightHalf = pointer.x + startShift[0] - currentPageShift(carried) > cellWPx * CardGrid.COLUMNS / 2f
                                                if (element is CardApp && rightHalf && !element.app.isShortcut) currentOnUninstall(element) else currentOnRemove(element)
                                                resetGesture()
                                                return@detectDragGestures
                                            }
                                            // Przeniesiony na inną stronę: miejsce liczymy w układzie tamtej (widocznej) strony.
                                            if (carried != 0) {
                                                val left = shown.x * cellWPx + gestureOffset.x + startShift[0] - currentPageShift(carried)
                                                val top = shown.y * cellHPx + gestureOffset.y
                                                val cx = (left / cellWPx).roundToInt().coerceIn(0, (CardGrid.COLUMNS - shown.w).coerceAtLeast(0))
                                                val cy = (top / cellHPx).roundToInt().coerceIn(0, (rows - shown.h).coerceAtLeast(0))
                                                currentOnMoveToPage(element, carried, GridRect(cx, cy, shown.w, shown.h))
                                                resetGesture()
                                                return@detectDragGestures
                                            }
                                            // Ikona aplikacji puszczona środkiem na widżet folderu → trafia do folderu.
                                            val folder = folderUnder(element, shown)
                                            val mergeWith = if (folder == null) mergeTargetUnder(element, shown) else null
                                            val stackWith = if (folder == null && mergeWith == null) stackTargetUnder(element, shown) else null
                                            val target = targetOf(shown, id)
                                            val plan = planFor(target, id)
                                            // Każde odłożenie (też do folderu, stosu, na to samo miejsce): element dopływa sprężyście
                                            // od palca do nowego miejsca zamiast przeskoczyć. base jedzie stare→nowe, settle (palec−stare)→0.
                                            val drawn = Offset(
                                                shown.x * cellWPx + gestureOffset.x + startShift[0] - currentPageShift(0),
                                                shown.y * cellHPx + gestureOffset.y,
                                            )
                                            val from = drawn - Offset(base.x * cellWPx, base.y * cellHPx)
                                            settleScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { // od razu, bez klatki "przeskoku"
                                                settle.snapTo(from)
                                                settle.animateTo(Offset.Zero, Motion.page())
                                            }
                                            if (folder != null) {
                                                pending = mapOf(id to folder.rect) // ikona "zostaje" w folderze do odświeżenia z bazy
                                                currentOnDropIntoFolder(element as CardApp, folder)
                                            } else if (mergeWith != null) {
                                                pending = mapOf(id to mergeWith.rect)
                                                currentOnMergeApps(element as CardApp, mergeWith)
                                            } else if (stackWith != null) {
                                                pending = mapOf(id to stackWith.rect) // widżet "wchodzi" pod stos do odświeżenia z bazy
                                                currentOnStack(element, stackWith)
                                            } else if (target != shown && plan != null) {
                                                pending = plan
                                                currentOnLayout(plan)
                                            }
                                            resetGesture()
                                        },
                                        onDragCancel = { resetGesture() },
                                    ) { change, amount ->
                                        change.consume()
                                        gestureOffset += amount
                                        val pointerNow = dragStartPointer + gestureOffset
                                        edgeHover = if (pointerNow.y < zoneLimitPx) 0 else edgeOf(pointerNow)
                                    }
                                }.then(
                                    // W edycji dotknięcie ikony pokazuje jej menu (informacje, odinstaluj, usuń z trybu).
                                    if (element is CardApp) Modifier.pointerInput(id) { detectTapGestures(onTap = { menuOpenFor = id }) }
                                    else Modifier,
                                ),
                            ),
                    ) {
                        // when na sealed interface: kompilator pilnuje, że obsłużyliśmy każdy typ elementu.
                        when (element) {
                            is CardApp -> {
                                AppTile(
                                    app = element.app,
                                    onClick = { onLaunch(element) },
                                    menu = emptyList(),        // menu jest teraz w edycji układu (dotknięcie ikony)
                                    enabled = !editing,
                                    longPressMenu = false,     // przytrzymanie = edycja układu, nie menu
                                    modifier = Modifier.fillMaxSize(),
                                )
                                DropdownMenu(expanded = menuOpenFor == id, onDismissRequest = { menuOpenFor = null }) {
                                    menuFor(element).forEach { action ->
                                        DropdownMenuItem(
                                            text = { Text(action.label) },
                                            onClick = {
                                                menuOpenFor = null
                                                action.onClick()
                                            },
                                        )
                                    }
                                }
                            }
                            is CardWidget -> WidgetCell(
                                widget = element,
                                widgets = widgets,
                                width = cellW * shown.w,
                                height = cellH * shown.h,
                                editing = editing,
                            )
                            is CardCustomWidget -> if (element.kind == CustomWidgetKind.STACK) {
                                // Stos: widżety z tej samej listy elementów (mają stronę STACKED_PAGE, więc nie ma ich na siatce).
                                val members = stackMembers(element)
                                val data = remember(element.item.config) { StackData.of(element.item.config) }
                                // Numer w stosie liczymy po id: lista "members" może być krótsza (np. brak widżetu systemowego).
                                val topId = data.members.getOrNull(data.index)
                                WidgetStack(
                                    count = members.size,
                                    index = members.indexOfFirst { it.item.id == topId }.coerceAtLeast(0),
                                    swipeEnabled = !editing,
                                    onIndexChange = { i -> onStackIndex(element, data.members.indexOf(members[i].item.id)) },
                                    keyOf = { members[it].item.id },
                                    // Auto: widżet z przewijaną treścią (lista, kalendarz, widżet systemowy z listą)
                                    // przewija się sam, a stos zmienia tylko uchwyt; pozostałe — przesunięcie wszędzie.
                                    swipeAnywhere = { i ->
                                        when (data.swipe) {
                                            StackData.SWIPE_ANYWHERE -> true
                                            StackData.SWIPE_HANDLE -> false
                                            else -> when (val m = members.getOrNull(i)) {
                                                is CardCustomWidget -> m.kind != CustomWidgetKind.TODAY && m.kind != CustomWidgetKind.CHECKLIST
                                                is CardWidget -> !widgets.scrollsVertically(m.appWidgetId)
                                                else -> true
                                            }
                                        }
                                    },
                                ) { i ->
                                    when (val member = members[i]) {
                                        is CardWidget -> WidgetCell(member, widgets, cellW * shown.w, cellH * shown.h, editing)
                                        is CardCustomWidget -> customWidget(member, !editing)
                                        is CardApp -> Unit
                                    }
                                }
                            } else {
                                customWidget(element, !editing)
                            }
                        }

                        if (editing) {
                            // Mały element: mniejsze uchwyty. ✕ w lewym górnym rogu, rozmiar w prawym dolnym —
                            // po przekątnej, żeby na widżecie 1×1 nie nachodziły na siebie.
                            val side = minOf(cellW * shown.w, cellH * shown.h)
                            val handle = (side * 0.45f).coerceIn(22.dp, 40.dp)
                            RemoveBadge(
                                onClick = { onRemove(element) },
                                size = handle,
                                modifier = Modifier.align(Alignment.TopStart),
                            )
                            if (element is CardCustomWidget && canConfigure(element)) {
                                ConfigBadge(
                                    onClick = { onConfigure(element) },
                                    size = handle,
                                    modifier = Modifier.align(Alignment.BottomStart),
                                )
                            }
                            // Rozmiar zmieniamy tylko widżetom; ikona aplikacji ma stałe 2×2.
                            if (element !is CardApp) {
                                ResizeHandle(
                                    size = handle,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        // Uchwyt ma własny gest; change.consume() sprawia, że rodzic nie zacznie przesuwania.
                                        .pointerInput(id, shown, cellWPx, cellHPx) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    resizingId = id
                                                    gestureOffset = Offset.Zero
                                                },
                                                onDragEnd = {
                                                    val target = targetOf(shown, id)
                                                    val plan = planFor(target, id)
                                                    if (target != shown && plan != null) {
                                                        pending = plan
                                                        currentOnLayout(plan)
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

            // Podgląd miejsca docelowego NAD elementami (dawniej był pod widżetem i go nie było widać):
            // obrys + lekkie wypełnienie; bursztynowy, gdy się mieści, czerwony, gdy nie ma miejsca.
            if (dropInto != null) {
                // Nad folderem / nad ikoną: podświetlamy CEL (tam wpadnie ikona), a nie pole na siatce.
                Box(
                    Modifier
                        .zIndex(2f)
                        .offset(cellW * dropInto.x, cellH * dropInto.y)
                        .size(cellW * dropInto.w, cellH * dropInto.h)
                        .padding(2.dp)
                        .border(2.dp, okColor, RoundedCornerShape(22.dp))
                        .background(okColor.copy(alpha = 0.28f), RoundedCornerShape(22.dp)),
                )
            } else if (active != null && carried == 0) {
                val target = targetOf(active.rect, active.item.id)
                val color = if (fits(target, active.item.id)) okColor else badColor
                Box(
                    Modifier
                        .zIndex(2f)
                        .offset(cellW * target.x, cellH * target.y)
                        .size(cellW * target.w, cellH * target.h)
                        .padding(2.dp)
                        .border(1.5.dp, color.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                        .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                )
            }

            // Pasek u góry w trakcie przeciągania: "Usuń z karty" / "Odinstaluj" (jak w launcherze Androida).
            val dragged = elements.firstOrNull { it.item.id == draggingId }
            if (dragged != null) {
                val pointer = dragStartPointer + gestureOffset
                // Podświetlony pas przy krawędzi: "przytrzymaj tutaj, a strona się przewinie" (na stronie widocznej).
                val edge = edgeHover
                if (edge != 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .zIndex(3f)
                            .align(if (edge < 0) Alignment.CenterStart else Alignment.CenterEnd)
                            .offset { IntOffset(-currentPageShift(0).roundToInt(), 0) }
                            .fillMaxHeight()
                            .width(cellW * 0.5f)
                            .background(okColor.copy(alpha = 0.30f), RoundedCornerShape(16.dp)),
                    ) {
                        Text(if (edge < 0) "‹" else "›", style = MaterialTheme.typography.headlineMedium, color = okColor)
                    }
                }
                val overZone = pointer.y < zoneLimitPx
                val rightHalf = pointer.x + startShift[0] > cellWPx * CardGrid.COLUMNS / 2f // względem ekranu (także po przewinięciu)
                // Pasek zaczyna się tuż pod paskiem stanu i tylko lekko wchodzi na kartę (półprzezroczysty),
                // więc nie chowa się za notchem, a górny rząd karty nadal da się zająć.
                Row(
                    Modifier
                        .zIndex(3f)
                        .offset { IntOffset(-currentPageShift(0).roundToInt(), ZoneTop.roundToPx()) } // na widocznej stronie
                        .fillMaxWidth()
                        .height(ZoneHeight),
                ) {
                    DropZone(
                        label = "✕  " + stringResource(R.string.grid_remove_from_card),
                        active = overZone && (!rightHalf || dragged !is CardApp || dragged.app.isShortcut),
                        modifier = Modifier.weight(1f),
                    )
                    if (dragged is CardApp && !dragged.app.isShortcut) {
                        DropZone(label = "🗑  " + stringResource(R.string.grid_uninstall), active = overZone && rightHalf, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DropZone(label: String, active: Boolean, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .padding(4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (active) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            ),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// Przytrzymanie w dowolnym miejscu elementu. Patrzymy na dotyk w fazie Initial (rodzic przed dzieckiem),
// więc działa także na widżetach, które same obsługują kliknięcia. Po rozpoznaniu "zjadamy" resztę gestu,
// żeby ikona czy widżet pod spodem nie dostały kliknięcia przy puszczeniu palca.
private fun Modifier.longPressAnywhere(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            }
            true
        }
        if (released == null) { // minął czas przytrzymania, a palec wciąż jest w miejscu
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
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
            .clip(RoundedCornerShape(LocalWidgetCorner.current)),
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

// Krzyżyk w rogu elementu w trybie edycji; size = obszar dotyku (mniejszy na małych widżetach).
@Composable
private fun RemoveBadge(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    val removeDesc = stringResource(R.string.grid_remove_from_mode)
    Box(
        contentAlignment = Alignment.TopStart,
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick)
            .semantics { contentDescription = removeDesc },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.55f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
        ) {
            Text("×", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ⚙ w lewym dolnym rogu widżetu w edycji: jego ustawienia (naklejka, notatka, zegar…).
@Composable
private fun ConfigBadge(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    val settingsDesc = stringResource(R.string.grid_widget_settings)
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick)
            .semantics { contentDescription = settingsDesc },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.6f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)),
        ) {
            Icon(
                painter = painterResource(pl.rafal.contextlauncher.R.drawable.ic_settings),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.38f),
            )
        }
    }
}

// Uchwyt zmiany rozmiaru: pogrubiony narożnik z podwójną linią (jak róg ramki do chwycenia), rysowany
// dokładnie w rogu elementu. Pod spodem ciemniejszy obrys, żeby był widoczny na każdej tapecie.
@Composable
private fun ResizeHandle(size: Dp, modifier: Modifier = Modifier) {
    val fg = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    val shade = Color.Black.copy(alpha = 0.28f)
    val resizeDesc = stringResource(R.string.grid_resize)
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = resizeDesc },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width // rozmiar płótna (DrawScope), nie parametr size
            val thick = 2.5.dp.toPx()
            val inset = thick / 2 + 1.dp.toPx()
            // Dwa zaokrąglone narożniki (jak róg zaokrąglonego widżetu): zewnętrzny dłuższy, wewnętrzny krótszy.
            fun corner(offset: Float, length: Float, radius: Float, color: Color, width: Float) {
                val x = w - inset - offset
                val y = w - inset - offset
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x, y - length)
                    lineTo(x, y - radius)
                    quadraticTo(x, y, x - radius, y) // łagodny łuk zamiast ostrego kąta
                    lineTo(x - length, y)
                }
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = width, cap = StrokeCap.Round))
            }
            val gap = 5.dp.toPx()
            val radius = 8.dp.toPx()
            corner(0f, w * 0.62f, radius, shade, thick + 1.5.dp.toPx())
            corner(gap, w * 0.36f, radius * 0.7f, shade, thick + 1.5.dp.toPx())
            corner(0f, w * 0.62f, radius, fg, thick)
            corner(gap, w * 0.36f, radius * 0.7f, fg, thick)
        }
    }
}

// Położenie paska "Usuń z karty / Odinstaluj" względem górnej krawędzi karty.
private val ZoneTop = (-36).dp
private val ZoneHeight = 44.dp
