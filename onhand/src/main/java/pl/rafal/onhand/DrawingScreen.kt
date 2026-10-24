package pl.rafal.onhand

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

// 1.10.0 (On6): rysowanie w notatce — palcem albo rysikiem. Nacisk rysika zmienia grubość pióra,
// gumka rysika (albo przycisk boczny) ściera, a gdy pojawi się rysik, palec przestaje rysować (dłoń na ekranie
// nie zostawia kresek). Wynik to obraz PNG, który edytor wstawia do treści jak zdjęcie.

// Punkt kreski w pikselach widoku; pressure 0..1 (palec = 1).
internal data class InkPoint(val x: Float, val y: Float, val pressure: Float)

// Kreska: punkty + wygląd. Niezmienna (jak record w C#), więc historia Cofnij to zwykłe listy.
internal data class InkStroke(
    val points: List<InkPoint>,
    val color: Int,         // ARGB
    val width: Float,       // grubość bazowa w px widoku
    val marker: Boolean,    // zakreślacz: półprzezroczysty, stała grubość
    val pressureAware: Boolean,
)

internal enum class InkTool { PEN, MARKER, ERASER }

// Prostokąt, w którym leży obraz tła (dopasowany do widoku jak ContentScale.Fit): przesunięcie i skala.
private data class FitRect(val left: Float, val top: Float, val scale: Float, val width: Float, val height: Float)

private fun fit(view: IntSize, image: IntSize): FitRect {
    val scale = minOf(view.width / image.width.toFloat(), view.height / image.height.toFloat())
    val w = image.width * scale
    val h = image.height * scale
    return FitRect((view.width - w) / 2f, (view.height - h) / 2f, scale, w, h)
}

// Grubość odcinka: przy rysiku zależy od nacisku (lekko = cienko), przy palcu stała.
private fun segmentWidth(stroke: InkStroke, a: InkPoint, b: InkPoint): Float =
    if (stroke.marker || !stroke.pressureAware) stroke.width
    else stroke.width * (0.35f + 0.9f * ((a.pressure + b.pressure) / 2f).coerceIn(0f, 1f))

// Rysowanie kreski odcinek po odcinku — ta sama geometria na ekranie i przy zapisie do PNG.
// map: punkt widoku → punkt docelowy, scale: mnożnik grubości.
private inline fun forEachSegment(
    stroke: InkStroke,
    map: (InkPoint) -> Offset,
    scale: Float,
    draw: (Offset, Offset, Float) -> Unit,
) {
    val pts = stroke.points
    if (pts.size == 1) {
        val p = map(pts[0])
        draw(p, p, segmentWidth(stroke, pts[0], pts[0]) * scale)
    } else {
        for (i in 1 until pts.size) draw(map(pts[i - 1]), map(pts[i]), segmentWidth(stroke, pts[i - 1], pts[i]) * scale)
    }
}

private fun DrawScope.drawStroke(stroke: InkStroke) {
    val color = Color(stroke.color).let { if (stroke.marker) it.copy(alpha = 0.35f) else it }
    forEachSegment(stroke, { Offset(it.x, it.y) }, 1f) { a, b, w ->
        drawLine(color, a, b, strokeWidth = w, cap = StrokeCap.Round)
    }
}

// Zapis do bitmapy: bez tła → biała kartka wielkości widoku; z tłem → obraz w pełnej rozdzielczości (max 2048 px),
// a kreski przeliczone z widoku na piksele obrazu.
internal fun renderInk(view: IntSize, strokes: List<InkStroke>, background: Bitmap?): Bitmap {
    val out: Bitmap
    val mapX: (Float) -> Float
    val mapY: (Float) -> Float
    val widthScale: Float
    if (background != null) {
        val limit = minOf(1f, 2048f / maxOf(background.width, background.height))
        val w = (background.width * limit).roundToInt().coerceAtLeast(1)
        val h = (background.height * limit).roundToInt().coerceAtLeast(1)
        out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawBitmap(background, null, android.graphics.Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        val r = fit(view, IntSize(w, h))
        mapX = { (it - r.left) / r.scale }
        mapY = { (it - r.top) / r.scale }
        widthScale = 1f / r.scale
    } else {
        out = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        out.eraseColor(android.graphics.Color.WHITE)
        mapX = { it }
        mapY = { it }
        widthScale = 1f
    }
    val canvas = android.graphics.Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    for (stroke in strokes) {
        paint.color = stroke.color
        if (stroke.marker) paint.alpha = (255 * 0.35f).roundToInt()
        forEachSegment(stroke, { Offset(mapX(it.x), mapY(it.y)) }, widthScale) { a, b, w ->
            paint.strokeWidth = w
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
    }
    return out
}

private val InkColors = listOf(
    0xFF111111, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA,
).map { it.toInt() }

private val InkWidths = listOf(3f, 6f, 12f) // dp: cienko / średnio / grubo

// Ekran rysowania na cały ekran. background = obraz do dorysowania (null = nowa kartka).
// onDone dostaje gotowy PNG jako bitmapę albo null, gdy nic nie narysowano.
@OptIn(ExperimentalComposeUiApi::class) // historical (punkty pośrednie ruchu) bywało oznaczone jako eksperymentalne
@Composable
internal fun DrawingScreen(
    background: File?,
    onCancel: () -> Unit,
    onDone: (Bitmap?) -> Unit,
) {
    var strokes by remember { mutableStateOf(emptyList<InkStroke>()) }
    val undo = remember { ArrayList<List<InkStroke>>() } // poprzednie stany (≈ Stack<T> w C#)
    val redo = remember { ArrayList<List<InkStroke>>() }
    var historyVersion by remember { mutableStateOf(0) } // odświeża przyciski Cofnij / Ponów
    val live = remember { mutableStateListOf<InkPoint>() } // kreska rysowana właśnie teraz
    var tool by remember { mutableStateOf(InkTool.PEN) }
    var color by remember { mutableStateOf(InkColors[0]) }
    var widthIndex by remember { mutableStateOf(1) }
    var sawStylus by remember { mutableStateOf(false) }
    var fingerDraws by remember { mutableStateOf(true) } // po wykryciu rysika domyślnie wyłączane
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var confirmExit by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Tło (zdjęcie / poprzedni rysunek) w pełnej rozdzielczości do zapisu, na ekran ta sama bitmapa.
    // bgReady: bez tła od razu true; z tłem dopiero po wczytaniu — wcześniej ✓ zapisałoby białą kartkę zamiast obrazu.
    var bgReady by remember { mutableStateOf(background == null) }
    var bgFailed by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundBitmap by produceState<Bitmap?>(initialValue = null, background) {
        value = background?.let { file ->
            withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                        val max = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                        val scale = minOf(1f, 2048f / max)
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                }.getOrNull()
            }
        }
        if (background != null) {
            bgReady = value != null
            bgFailed = value == null
        }
    }
    val backgroundImage: ImageBitmap? = remember(backgroundBitmap) { backgroundBitmap?.asImageBitmap() }

    fun commit(next: List<InkStroke>) {
        undo += strokes
        redo.clear()
        strokes = next
        historyVersion++
    }

    // Aktualne ustawienia dla gestu (pointerInput startuje raz — jak przechwycenie zmiennej przez referencję).
    val currentTool by rememberUpdatedState(tool)
    val currentColor by rememberUpdatedState(color)
    val currentWidthPx by rememberUpdatedState(with(density) { InkWidths[widthIndex].dp.toPx() })
    val currentFingerDraws by rememberUpdatedState(fingerDraws)
    val eraseRadius = with(density) { 14.dp.toPx() }

    val dirty = strokes.isNotEmpty()
    BackHandler { if (dirty) confirmExit = true else onCancel() }

    Column(Modifier.fillMaxSize().background(Color(0xFF15171A)).systemBarsPadding()) {
        // Górny pasek: Anuluj, Cofnij / Ponów, Gotowe.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (dirty) confirmExit = true else onCancel() }) {
                Text("✕  " + stringResource(R.string.oh_cancel), color = Color(0xFFFF6B6B))
            }
            Spacer(Modifier.weight(1f))
            val v = historyVersion // odczyt = przebudowa paska po zmianie historii
            TextButton(onClick = {
                if (undo.isNotEmpty()) {
                    redo += strokes
                    strokes = undo.removeAt(undo.lastIndex)
                    historyVersion = v + 1
                }
            }, enabled = undo.isNotEmpty()) { Text("↶", style = MaterialTheme.typography.titleLarge) }
            TextButton(onClick = {
                if (redo.isNotEmpty()) {
                    undo += strokes
                    strokes = redo.removeAt(redo.lastIndex)
                    historyVersion = v + 1
                }
            }, enabled = redo.isNotEmpty()) { Text("↷", style = MaterialTheme.typography.titleLarge) }
            TextButton(
                enabled = !saving && bgReady,
                onClick = {
                    if (!dirty) {
                        onDone(null)
                    } else {
                        saving = true
                        val size = viewSize
                        val snapshot = strokes
                        val bg = backgroundBitmap
                        scope.launch {
                            val bitmap = withContext(Dispatchers.Default) { runCatching { renderInk(size, snapshot, bg) }.getOrNull() }
                            if (bitmap != null) {
                                onDone(bitmap)
                            } else {
                                // Błąd zapisu (np. brak pamięci): rysunek zostaje na ekranie, można spróbować jeszcze raz.
                                saving = false
                                android.widget.Toast.makeText(context, OnHandText.get(R.string.oh_draw_failed), android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
            ) { Text("✓  " + stringResource(R.string.oh_draw_done), color = Color(0xFF4FC27E), fontWeight = FontWeight.SemiBold) }
        }

        // Płótno: biała kartka albo obraz tła dopasowany do ekranu.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (background == null) Color.White else Color(0xFF0E0F11))
                .onSizeChanged { viewSize = it }
                .pointerInput(bgReady) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val isStylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                        if (isStylus && !sawStylus) {
                            sawStylus = true
                            fingerDraws = false // dłoń opiera się o ekran — od teraz rysuje tylko rysik (do zmiany w pasku)
                        }
                        val allowed = bgReady && (isStylus || down.type != PointerType.Touch || currentFingerDraws) // tło jeszcze się wczytuje → nic
                        if (allowed) {
                            // Gumka: narzędzie, koniec „gumka” rysika albo trzymany przycisk boczny.
                            val erasing = currentTool == InkTool.ERASER || down.type == PointerType.Eraser ||
                                currentEvent.buttons.isSecondaryPressed
                            val before = strokes
                            var working = strokes
                            fun eraseAt(p: Offset) {
                                val left = working.filterNot { s -> s.points.any { hypot(it.x - p.x, it.y - p.y) <= eraseRadius + s.width / 2 } }
                                if (left.size != working.size) {
                                    working = left
                                    strokes = left
                                }
                            }
                            fun add(change: PointerInputChange) {
                                change.historical.forEach { h ->
                                    if (erasing) eraseAt(h.position) else live += InkPoint(h.position.x, h.position.y, if (isStylus) change.pressure else 1f)
                                }
                                if (erasing) eraseAt(change.position)
                                else live += InkPoint(change.position.x, change.position.y, if (isStylus) change.pressure else 1f)
                            }
                            live.clear()
                            if (erasing) eraseAt(down.position) else live += InkPoint(down.position.x, down.position.y, if (isStylus) down.pressure else 1f)
                            down.consume()
                            var active = true
                            while (active) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    active = false
                                } else {
                                    add(change)
                                    change.consume()
                                }
                            }
                            if (erasing) {
                                if (working.size != before.size) {
                                    strokes = before // commit zapisuje stan sprzed starcia do historii
                                    commit(working)
                                }
                            } else if (live.isNotEmpty()) {
                                val t = currentTool
                                commit(
                                    strokes + InkStroke(
                                        points = live.toList(),
                                        color = currentColor,
                                        width = if (t == InkTool.MARKER) currentWidthPx * 3f else currentWidthPx,
                                        marker = t == InkTool.MARKER,
                                        pressureAware = isStylus,
                                    ),
                                )
                                live.clear()
                            }
                        }
                    }
                },
        ) {
            val bg = backgroundImage
            if (bgFailed) {
                Text(
                    stringResource(R.string.oh_draw_load_failed),
                    color = Color(0xFFFF6B6B),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                if (bg != null) {
                    val r = fit(IntSize(size.width.toInt(), size.height.toInt()), IntSize(bg.width, bg.height))
                    drawImage(
                        bg,
                        dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                        dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt()),
                    )
                }
                strokes.forEach { drawStroke(it) }
                if (live.isNotEmpty()) {
                    drawStroke(
                        InkStroke(
                            points = live.toList(),
                            color = color,
                            width = if (tool == InkTool.MARKER) currentWidthPx * 3f else currentWidthPx,
                            marker = tool == InkTool.MARKER,
                            pressureAware = sawStylus && !fingerDraws,
                        ),
                    )
                }
            }
        }

        // Dolny pasek: narzędzia, grubość, kolory, „palec też rysuje” (gdy jest rysik).
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolChip("✏ " + stringResource(R.string.oh_draw_pen), tool == InkTool.PEN) { tool = InkTool.PEN }
            ToolChip("🖍 " + stringResource(R.string.oh_draw_marker), tool == InkTool.MARKER) { tool = InkTool.MARKER }
            ToolChip("⌫ " + stringResource(R.string.oh_draw_eraser), tool == InkTool.ERASER) { tool = InkTool.ERASER }
            Spacer(Modifier.width(6.dp))
            InkWidths.forEachIndexed { i, w ->
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (i == widthIndex) Color(0xFF2C3036) else Color.Transparent)
                        .clickable { widthIndex = i },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size((w + 4f).dp).clip(CircleShape).background(Color(0xFFE6E6E6))) }
            }
            if (sawStylus) {
                ToolChip("☝ " + stringResource(R.string.oh_draw_finger), fingerDraws) { fingerDraws = !fingerDraws }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InkColors.forEach { c ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (c == color) 3.dp else 1.dp, if (c == color) Color(0xFF4FC27E) else Color(0x55FFFFFF), CircleShape)
                        .clickable {
                            color = c
                            if (tool == InkTool.ERASER) tool = InkTool.PEN
                        },
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.oh_draw_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    onCancel()
                }) { Text(stringResource(R.string.oh_draw_discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.oh_draw_keep)) }
            },
        )
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFF2E7D57) else Color(0xFF24272C))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge) }
}
