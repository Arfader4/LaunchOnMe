package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import pl.rafal.contextlauncher.data.db.ModeEntity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// --- Menu w ćwierć okręgu na Klawiszu ON (jak "pie menu") ---
// Przytrzymanie klawisza → wokół niego rozwija się łuk ze znaczkami trybów, w stronę środka ekranu.
// Palec się nie odrywa: przesuwasz po łuku, podświetla się tryb najbliżej kierunku palca (z drgnięciem),
// puszczenie = włączenie. Puszczenie blisko klawisza = anuluj. Gdy trybów jest więcej niż mieści łuk,
// przytrzymanie palca przy końcu łuku przewija go (kolejne tryby "wjeżdżają").

private const val VISIBLE = 5                 // ile znaczków mieści się na łuku naraz
private val ArcRadius = 150.dp               // promień łuku (od środka klawisza)
private val ArcBadge = 46.dp
val ModeArcDeadZone = 48.dp                  // palec bliżej środka klawisza = anuluj

// Stan łuku współdzielony przez gest (na klawiszu) i rysowanie (w oknie Popup).
@Stable
class ModeArcState {
    var open by mutableStateOf(false)
    var t by mutableFloatStateOf(0.5f)        // położenie palca na łuku: 0 = poziomo, 1 = pionowo w górę
    var tRaw by mutableFloatStateOf(0.5f)     // to samo bez przycięcia (poza 0..1 = palec za końcem łuku)
    var active by mutableStateOf(false)       // palec poza "martwą strefą" klawisza (coś jest wybrane)
    var scroll by mutableFloatStateOf(0f)     // o ile trybów przewinięty łuk (0 = od pierwszego)
    var selected by mutableIntStateOf(-1)
    var opensLeft by mutableStateOf(true)     // klawisz po prawej → łuk w lewo-w górę (leworęczni: w prawo-w górę)
}

// Położenie trybu i na łuku (0..1) przy danym przewinięciu; poza zakresem = poza łukiem.
private fun slotOf(i: Int, count: Int, scroll: Float): Float = when {
    count <= 1 -> 0.5f
    count <= VISIBLE -> i / (count - 1f)
    else -> (i - scroll) / (VISIBLE - 1f)
}

private fun nearestIndex(t: Float, count: Int, scroll: Float): Int = when {
    count <= 1 -> 0
    count <= VISIBLE -> (t * (count - 1)).roundToInt().coerceIn(0, count - 1)
    else -> (t * (VISIBLE - 1) + scroll).roundToInt().coerceIn(0, count - 1)
}

// Gest klawisza: krótkie dotknięcie → onTap (lista trybów jak dotąd), przytrzymanie → łuk.
fun Modifier.modeArcGesture(
    state: ModeArcState,
    modes: () -> List<ModeEntity>,
    onTap: () -> Unit,
    onPick: (ModeEntity) -> Unit,
    onOpen: () -> Unit,
    deadZonePx: Float,
): Modifier = pointerInput(state, deadZonePx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val center = Offset(size.width / 2f, size.height / 2f)
        // Czekamy na przytrzymanie: puszczenie wcześniej = zwykłe dotknięcie, ruch = nic (np. przewijanie).
        var released = false
        var moved = false
        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) { released = true; break }
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) { moved = true; break }
            }
        }
        if (held != null) { // pętla skończyła się przed czasem
            if (released && !moved) onTap()
            return@awaitEachGesture
        }
        // Przytrzymano — otwieramy łuk i od teraz śledzimy palec (zdarzenia zjadamy: karta ich nie dostanie).
        // withTimeoutOrNull wyżej to wersja z AwaitPointerEventScope (gest), nie z kotlinx.coroutines.
        state.scroll = 0f
        state.selected = -1
        state.active = false
        state.open = true
        onOpen()
        var pick: ModeEntity? = null
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                val d = change.position - center
                val dist = sqrt(d.x * d.x + d.y * d.y)
                // Kąt palca względem środka łuku (225° w lewo-górę, 315° w prawo-górę), zawinięty do −180..180 —
                // bez skoków "za plecami" łuku. Układ ekranu: y w dół, więc 270° = w górę.
                var deg = Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat()
                val middle = if (state.opensLeft) 225f else 315f
                var rel = (deg - middle) % 360f
                if (rel > 180f) rel -= 360f
                if (rel < -180f) rel += 360f
                val raw = if (state.opensLeft) 0.5f + rel / 90f else 0.5f - rel / 90f
                state.tRaw = raw
                state.t = raw.coerceIn(0f, 1f)
                // Wybór tylko poza klawiszem i w pobliżu łuku; dalej w bok albo w dół = anuluj.
                state.active = dist > deadZonePx && raw in -0.35f..1.35f
                val list = modes()
                state.selected = if (state.active) nearestIndex(state.t, list.size, state.scroll) else -1
            }
            val list = modes()
            pick = state.selected.takeIf { state.active }?.let { list.getOrNull(it) }
        } finally {
            // Także gdy gest przerwano (np. klawisz zniknął z ekranu) — łuk nie może zostać otwarty.
            state.open = false
            state.active = false
        }
        pick?.let(onPick)
    }
}

// Rysowanie łuku w oknie Popup wyśrodkowanym na klawiszu (okno nie przycina się do ekranu karty).
@Composable
fun ModeArcOverlay(state: ModeArcState, modes: List<ModeEntity>, activeId: Long?) {
    val haptics = LocalHapticFeedback.current
    val currentModes by rememberUpdatedState(modes)
    // Przewijanie łuku, gdy palec stoi przy jego końcu (timer, bo stojący palec nie wysyła zdarzeń).
    LaunchedEffect(state.open) {
        if (!state.open) return@LaunchedEffect
        var last = 0L
        var lastSel = state.selected
        while (state.open) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                val max = (currentModes.size - VISIBLE).coerceAtLeast(0).toFloat()
                if (max > 0f && state.active) {
                    val speed = 3f // trybów na sekundę
                    // Tylko gdy palec jest ZA końcem łuku — skrajne znaczki da się wybrać bez przewijania.
                    if (state.tRaw > 1.02f) state.scroll = (state.scroll + speed * dt).coerceAtMost(max)
                    if (state.tRaw < -0.02f) state.scroll = (state.scroll - speed * dt).coerceAtLeast(0f)
                    val sel = nearestIndex(state.t, currentModes.size, state.scroll)
                    if (sel != state.selected) state.selected = sel
                }
            }
            // Drgnięcie przy każdej zmianie wybranego trybu (palcem albo przez przewinięcie łuku).
            if (state.selected != lastSel && state.selected >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastSel = state.selected
        }
    }
    // Bez wczesnego "return" w @Composable — rysujemy tylko, gdy łuk jest otwarty.
    if (state.open) ArcContent(state, modes, activeId)
}

@Composable
private fun ArcContent(state: ModeArcState, modes: List<ModeEntity>, activeId: Long?) {
    val density = LocalDensity.current
    val radiusPx = with(density) { ArcRadius.toPx() }
    val box = ArcRadius * 2 + ArcBadge * 2 // kwadrat wokół środka klawisza
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val glow = MaterialTheme.colorScheme.primary
    val fan = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) // kolory odczytane poza Canvas (tam nie ma MaterialTheme)
    // Otwarcie jak wachlarz: tło rozkłada się od początku łuku, a znaczki "wysuwają się" po kolei spod klawisza.
    // g = postęp całości (0..1); każdy znaczek startuje trochę później (zależnie od miejsca na łuku).
    val unfold = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        unfold.animateTo(1f, androidx.compose.animation.core.tween(360, easing = androidx.compose.animation.core.LinearEasing))
    }
    fun eased(x: Float) = androidx.compose.animation.core.FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))

    Popup(
        popupPositionProvider = CenteredOnAnchor,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Box(Modifier.size(box), contentAlignment = Alignment.Center) {
            // Tło łuku: półprzezroczysty "wachlarz" i linia toru.
            Canvas(Modifier.size(box)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val start = if (state.opensLeft) 180f else 270f
                val outer = radiusPx + ArcBadge.toPx() * 0.75f
                val sweep = if (state.opensLeft) 90f else -90f
                drawArc(
                    color = fan,
                    // opensLeft: od 180° zgodnie z zegarem; inaczej od 360° wstecz — tak samo jak tor znaczków.
                    startAngle = if (state.opensLeft) 180f else 360f,
                    sweepAngle = sweep * eased(unfold.value / 0.7f),
                    useCenter = true,
                    topLeft = Offset(c.x - outer, c.y - outer),
                    size = androidx.compose.ui.geometry.Size(outer * 2, outer * 2),
                )
                drawArc(
                    color = track,
                    startAngle = start,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(c.x - radiusPx, c.y - radiusPx),
                    size = androidx.compose.ui.geometry.Size(radiusPx * 2, radiusPx * 2),
                    style = Stroke(width = 2.dp.toPx()),
                )
                // Strzałki na końcach łuku: w tę stronę są jeszcze tryby (palec przy końcu przewija łuk).
                val extra = (modes.size - VISIBLE).coerceAtLeast(0).toFloat()
                if (extra > 0f) {
                    val arrowR = radiusPx + ArcBadge.toPx() * 0.62f // na zewnątrz toru, nie pod znaczkami
                    fun arrow(slot: Float, forward: Boolean) {
                        val deg = if (state.opensLeft) 180f + 90f * slot else 360f - 90f * slot
                        val a = Math.toRadians(deg.toDouble())
                        val p = c + Offset(cos(a).toFloat(), sin(a).toFloat()) * arrowR
                        // Styczna w stronę rosnącego "slot" (kierunek przewijania łuku) i normalna (od środka).
                        val sign = (if (state.opensLeft) 1f else -1f) * (if (forward) 1f else -1f)
                        val t = Offset(-sin(a).toFloat(), cos(a).toFloat()) * sign
                        val n = Offset(cos(a).toFloat(), sin(a).toFloat())
                        val tip = p + t * 6.dp.toPx()
                        val base = p - t * 4.dp.toPx()
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(base.x + n.x * 5.dp.toPx(), base.y + n.y * 5.dp.toPx())
                            lineTo(base.x - n.x * 5.dp.toPx(), base.y - n.y * 5.dp.toPx())
                            close()
                        }
                        drawPath(path, glow.copy(alpha = 0.85f))
                    }
                    if (state.scroll < extra - 0.05f) arrow(1.04f, forward = true)
                    if (state.scroll > 0.05f) arrow(-0.04f, forward = false)
                }
                // Wskaźnik kierunku palca.
                if (state.active) {
                    val deg = if (state.opensLeft) 180f + 90f * state.t else 360f - 90f * state.t
                    val rad = Math.toRadians(deg.toDouble())
                    drawCircle(glow.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = c + Offset(cos(rad).toFloat(), sin(rad).toFloat()) * (radiusPx * 0.55f))
                }
            }
            // Zwykła pętla for i if zamiast "return@forEachIndexed" (powrót z lambdy inline w @Composable psuje DEX).
            for (i in modes.indices) {
                val mode = modes[i]
                val slot = slotOf(i, modes.size, state.scroll)
                if (slot >= -0.39f && slot <= 1.39f) { // dalej poza łukiem (przewinięte) — nie rysujemy
                key(mode.id) { // stan animacji (powiększenie wybranego) należy do trybu, nie do pozycji w pętli
                // Wachlarz: znaczek zaczyna na początku łuku, blisko klawisza, i rozjeżdża się na swoje miejsce.
                val delay = slot.coerceIn(0f, 1f) * 0.45f
                val e = eased((unfold.value - delay) / 0.55f)
                val shownSlot = slot * e
                val deg = if (state.opensLeft) 180f + 90f * shownSlot else 360f - 90f * shownSlot
                val rad = Math.toRadians(deg.toDouble())
                val r = radiusPx * (0.35f + 0.65f * e)
                val x = r * cos(rad).toFloat()
                val y = r * sin(rad).toFloat()
                val isSel = i == state.selected
                val selScale by androidx.compose.animation.core.animateFloatAsState(
                    if (isSel) 1.25f else 1f, Motion.press(), label = "wybrany tryb",
                )
                // Sąsiad tuż za końcem łuku "wystaje" przygaszony i mniejszy — widać, że dalej są kolejne tryby.
                val fade = when {
                    slot < 0f -> 1f + slot * 2.6f
                    slot > 1f -> 1f - (slot - 1f) * 2.6f
                    else -> 1f
                }.coerceIn(0f, 1f)
                val peekScale = 0.72f + 0.28f * fade
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .graphicsLayer {
                            alpha = fade * e
                            val s = selScale * (0.6f + 0.4f * e) * peekScale
                            scaleX = s
                            scaleY = s
                        }
                        .size(ArcBadge + 6.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSel) Modifier.border(2.dp, glow, CircleShape)
                            else if (mode.id == activeId) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), CircleShape)
                            else Modifier,
                        ),
                ) { ModeBadge(mode, size = ArcBadge, shape = CircleShape) }
                }
                }
            }
            // Nazwa wybranego trybu: w środku łuku (między klawiszem a znaczkami).
            val label = modes.getOrNull(state.selected)?.name?.takeIf { state.active } ?: "Puść na klawiszu = anuluj"
            val mid = Math.toRadians((if (state.opensLeft) 225.0 else 315.0))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (radiusPx * 0.5f * cos(mid)).roundToInt(),
                            (radiusPx * 0.5f * sin(mid)).roundToInt(),
                        )
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

// Popup wyśrodkowany na klawiszu (środek łuku = środek klawisza).
private val CenteredOnAnchor = object : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset(
            anchorBounds.center.x - popupContentSize.width / 2,
            anchorBounds.center.y - popupContentSize.height / 2,
        )
}
