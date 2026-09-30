package pl.rafal.contextlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

// Stos widżetów: kilka widżetów w jednym miejscu, przewijanych palcem w górę / w dół (jak Smart Stack w iOS).
// Przewijanie jest "w kółko": po ostatnim wraca pierwszy.
//
// Gdzie gest zmienia widżet:
//  - uchwyt (pasek z kropkami przy prawej krawędzi) — zawsze: przesunięcie w pionie albo dotknięcie (= następny),
//  - reszta powierzchni — tylko gdy swipeAnywhere(nr) = true (widżet bez przewijanej treści, np. zegar),
//    inaczej przesunięcie przewija treść widżetu (lista zakupów, poczta, kalendarz),
//  - dwa palce w pionie — zawsze, w dowolnym miejscu (skrót).
// Gest na powierzchni łapiemy w fazie Initial (rodzic przed dziećmi): widżety systemowe to zwykłe widoki Androida
// i same zjadają dotyk. Dopóki palec się nie rusza, dotyk idzie do widżetu; gdy ruszy się w pionie — przejmujemy go,
// a widżet dostaje "anuluj". Ruch w poziomie zostawiamy stronom karty.
@Composable
fun WidgetStack(
    count: Int,
    index: Int,
    swipeEnabled: Boolean,
    onIndexChange: (Int) -> Unit,
    keyOf: (Int) -> Any,          // stały klucz widżetu (id wiersza) — po zmianie kolejności widoki idą za widżetem
    swipeAnywhere: (Int) -> Boolean = { true }, // czy przesunięcie na powierzchni widżetu nr i zmienia widżet
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    // Bez wczesnego "return" w funkcji @Composable — całe ciało w if.
    if (count > 0) WidgetStackBody(count, index, swipeEnabled, onIndexChange, keyOf, swipeAnywhere, modifier, content)
}

@Composable
private fun WidgetStackBody(
    count: Int,
    index: Int,
    swipeEnabled: Boolean,
    onIndexChange: (Int) -> Unit,
    keyOf: (Int) -> Any,
    swipeAnywhere: (Int) -> Boolean,
    modifier: Modifier,
    content: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) } // przesunięcie palcem w pikselach (w pionie)
    var shown by remember { mutableIntStateOf(index.coerceIn(0, count - 1)) }
    val lastReported = remember { intArrayOf(-1) } // ostatni numer wysłany do bazy (jego "echo" pomijamy)
    val currentOnIndex by rememberUpdatedState { i: Int ->
        lastReported[0] = i
        onIndexChange(i)
    }
    val currentSwipeAnywhere by rememberUpdatedState(swipeAnywhere)
    // Zmiana z zewnątrz (np. okno "Stos" → Pokaż, nowy widżet dołożony na wierzch). Echo własnego zapisu
    // (przychodzi z opóźnieniem, gdy palec mógł już przewinąć dalej) nie cofa stosu.
    LaunchedEffect(index, count) {
        if (index != lastReported[0]) shown = index.coerceIn(0, count - 1)
        lastReported[0] = -1
    }
    fun wrap(i: Int) = Math.floorMod(i, count)

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Dokończenie gestu: najpierw zmieniamy widżet na wierzchu (przesunięcie przeliczone względem niego,
        // więc obraz nie skacze), potem dojeżdżamy do zera. Przerwanie animacji niczego już nie psuje.
        suspend fun settle(dir: Int) {
            if (dir != 0) {
                shown = wrap(shown + dir)
                offset.snapTo((offset.value + dir * heightPx).coerceIn(-heightPx, heightPx))
                currentOnIndex(shown)
            }
            offset.animateTo(0f, if (dir == 0) spring(stiffness = Spring.StiffnessMediumLow) else Motion.page())
        }

        // Koniec przesuwania: dalej niż 1/4 wysokości albo szybkie machnięcie = sąsiedni widżet.
        fun finish(drag: Float, velocity: Float) {
            val dir = when {
                drag < -heightPx * 0.25f || velocity < -900f -> 1  // w górę → następny
                drag > heightPx * 0.25f || velocity > 900f -> -1   // w dół → poprzedni
                else -> 0
            }
            scope.launch { settle(dir) }
        }

        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (!swipeEnabled || count < 2) Modifier
                    else Modifier.pointerInput(count, heightPx) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            // Czy jednym palcem wolno zmieniać widżet (sprawdzane na starcie gestu, dla widżetu na wierzchu).
                            val oneFinger = currentSwipeAnywhere(shown)
                            val tracker = VelocityTracker()
                            var dragging = false
                            var totalX = 0f
                            var totalY = 0f
                            var drag = offset.value
                            var accY = 0f // przebyta droga w pionie (średnia palców) — do liczenia prędkości
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                val eligible = oneFinger || pressed.size >= 2
                                // Ruch: średnia z palców (przy dwóch palcach oba jadą razem).
                                val dy = pressed.map { it.position.y - it.previousPosition.y }.average().toFloat()
                                val dx = pressed.map { it.position.x - it.previousPosition.x }.average().toFloat()
                                // Prędkość z drogi, nie z pozycji palca: gdy jeden z dwóch palców się podniesie,
                                // "pierwszy" palec się zmienia i skok pozycji udawałby szybkie machnięcie.
                                accY += dy
                                tracker.addPosition(pressed.first().uptimeMillis, Offset(0f, accY))
                                if (!dragging) {
                                    if (!eligible) continue // jeden palec na przewijanej treści — dotyk należy do widżetu
                                    totalX += dx
                                    totalY += dy
                                    if (abs(totalY) > viewConfiguration.touchSlop && abs(totalY) > abs(totalX) * 1.2f) {
                                        dragging = true
                                        drag = offset.value
                                    } else if (abs(totalX) > viewConfiguration.touchSlop && pressed.size < 2) {
                                        break // ruch w bok jednym palcem: to przesuwanie stron karty, nie nasze
                                    }
                                }
                                if (dragging) {
                                    event.changes.forEach { it.consume() } // widżet dostaje "anuluj", karta nie otwiera szuflady
                                    drag += dy
                                    val target = drag
                                    scope.launch { offset.snapTo(target) }
                                }
                            }
                            if (dragging) finish(drag, tracker.calculateVelocity().y)
                        }
                    },
                ),
        ) {
            // Wszystkie widżety stosu zostają w kompozycji (widżety systemowe nie tworzą się od nowa przy przewijaniu),
            // widać tylko bieżący i sąsiada, w którego stronę przesuwa się palec.
            for (i in 0 until count) {
                key(keyOf(i)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val o = offset.value
                                var rel = Math.floorMod(i - shown, count)
                                if (rel > count / 2) rel -= count
                                // Przy dwóch widżetach "drugi" jest i nad, i pod — stawiamy go tam, dokąd jedzie palec.
                                if (count == 2 && rel != 0) rel = if (o > 0f) -1 else 1
                                val pos = rel * heightPx + o
                                translationY = pos
                                // Głębia: widżet, który odjeżdża (albo dopiero wjeżdża), jest lekko mniejszy i przygaszony —
                                // jak karta zsuwana z talii. t = 0 na wierzchu, 1 = cała wysokość dalej.
                                val t = (abs(pos) / heightPx).coerceIn(0f, 1f)
                                val depth = 1f - 0.1f * t
                                scaleX = depth
                                scaleY = depth
                                alpha = if (abs(rel) <= 1) 1f - 0.55f * t else 0f
                            },
                    ) { content(i) }
                }
            }
        }

        if (count > 1) {
            // Uchwyt: pasek z kropkami przy prawej krawędzi. Przesunięcie po nim w pionie zawsze zmienia widżet
            // (także gdy treść widżetu się przewija), dotknięcie = następny. Wyraźniejszy, gdy to jedyny sposób.
            val handleOnly = swipeEnabled && !swipeAnywhere(shown)
            val railTracker = remember { VelocityTracker() }
            val railDrag = remember { FloatArray(1) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.8f)
                    .width(16.dp) // wąski pas: nie zasłania przycisków widżetu przy prawej krawędzi
                    .pointerInput(count, heightPx) { detectTapGestures { scope.launch { settle(1) } } }
                    .then(
                        if (!swipeEnabled) Modifier
                        else Modifier.pointerInput(count, heightPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    railTracker.resetTracking()
                                    railDrag[0] = offset.value
                                },
                                onDragEnd = { finish(railDrag[0], railTracker.calculateVelocity().y) },
                                onDragCancel = { finish(railDrag[0], 0f) },
                            ) { change, dy ->
                                change.consume()
                                railTracker.addPosition(change.uptimeMillis, change.position)
                                railDrag[0] += dy
                                val target = railDrag[0]
                                scope.launch { offset.snapTo(target) }
                            }
                        },
                    )
                    .semantics {
                        contentDescription = "Uchwyt stosu: przesuń w pionie albo dotknij (${shown + 1} z $count)"
                        onClick(label = "Następny widżet") {
                            scope.launch { settle(1) }
                            true
                        }
                    },
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(end = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (handleOnly) 0.8f else 0.55f))
                        .then(
                            if (handleOnly) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            else Modifier,
                        )
                        .padding(horizontal = 3.dp, vertical = if (handleOnly) 8.dp else 5.dp),
                ) {
                    repeat(count) { i ->
                        Box(
                            Modifier
                                .size(if (i == shown) 6.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == shown) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                ),
                        )
                    }
                }
            }
        }
    }
}

// Okno "Stos" (⚙ na stosie w edycji układu): kolejność widżetów, który na wierzchu, ustawienia i wyjmowanie.
@Composable
fun StackDialog(
    members: List<pl.rafal.contextlauncher.data.CardElement>,
    current: Int,
    labelOf: (pl.rafal.contextlauncher.data.CardElement) -> String,
    onShow: (Int) -> Unit,
    onMove: (pl.rafal.contextlauncher.data.CardElement, Int) -> Unit,
    onSettings: (pl.rafal.contextlauncher.data.CardElement) -> (() -> Unit)?,
    onTakeOut: (pl.rafal.contextlauncher.data.CardElement) -> Unit,
    swipe: String,
    onSwipe: (String) -> Unit,
    onDissolve: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { androidx.compose.material3.Text("Stos widżetów") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.Text(
                    "Na karcie przesuwasz stos palcem w górę i w dół. Dotknij nazwy, aby pokazać ten widżet na wierzchu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Gdzie przesunięcie zmienia widżet. Uchwyt z kropkami działa zawsze, dwa palce też.
                androidx.compose.material3.Text("Przełączanie palcem", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        pl.rafal.contextlauncher.data.StackData.SWIPE_AUTO to "Auto",
                        pl.rafal.contextlauncher.data.StackData.SWIPE_ANYWHERE to "Cały widżet",
                        pl.rafal.contextlauncher.data.StackData.SWIPE_HANDLE to "Tylko uchwyt",
                    ).forEach { (value, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = swipe == value,
                            onClick = { onSwipe(value) },
                            label = { androidx.compose.material3.Text(label) },
                        )
                    }
                }
                androidx.compose.material3.Text(
                    when (swipe) {
                        pl.rafal.contextlauncher.data.StackData.SWIPE_ANYWHERE -> "Przesunięcie w dowolnym miejscu zmienia widżet (listy w stosie się nie przewiną)."
                        pl.rafal.contextlauncher.data.StackData.SWIPE_HANDLE -> "Widżet zmieniasz tylko uchwytem z kropkami przy prawej krawędzi (albo dwoma palcami)."
                        else -> "Widżety z listą (zakupy, poczta, kalendarz) przewijają treść — zmieniasz je uchwytem; pozostałe przesunięciem w dowolnym miejscu."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                members.forEachIndexed { i, member ->
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (i == current) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(start = 8.dp),
                    ) {
                        androidx.compose.material3.Text(
                            (if (i == current) "✓ " else "") + labelOf(member),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onShow(i) }
                                .padding(vertical = 10.dp),
                        )
                        // Ciasno: same symbole (▲ ▼ ⚙ ⇱) z opisem dla czytnika ekranu.
                        StackIconButton("▲", "Wyżej", enabled = i > 0) { onMove(member, -1) }
                        StackIconButton("▼", "Niżej", enabled = i < members.lastIndex) { onMove(member, 1) }
                        onSettings(member)?.let { open -> StackIconButton("⚙", "Ustawienia widżetu", enabled = true, onClick = open) }
                        StackIconButton("⇱", "Wyjmij na kartę", enabled = true) { onTakeOut(member) }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Gotowe") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDissolve) { androidx.compose.material3.Text("Rozdziel stos") } },
    )
}

@Composable
private fun StackIconButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        androidx.compose.material3.Text(
            symbol,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}
