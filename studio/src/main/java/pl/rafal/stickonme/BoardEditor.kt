package pl.rafal.stickonme

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

// Kolory do wyboru (tekst, ramki, tło). Na końcu przezroczysty "brak" tam, gdzie ma sens.
private val Palette = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFFFF5A5F, 0xFFFFB020, 0xFFFFE066, 0xFF7FD6AE, 0xFF2FD9B4,
    0xFF4C8DF6, 0xFF9B5CF6, 0xFFF0508C, 0xFFB5652E, 0xFFF5F1E8, 0xFF2A2E34, 0xFF6FC3DF,
)
private val Gradients = listOf(
    0xFF4C8DF6 to 0xFF9B5CF6, 0xFFF0508C to 0xFFFFB020, 0xFF2FD9B4 to 0xFF4C8DF6,
    0xFF101214 to 0xFF3A2A5A, 0xFFFFE066 to 0xFFFF5A5F, 0xFFF5F1E8 to 0xFFCDEBFF,
)
private val Fonts = listOf(
    "sans-serif" to "Zwykła", "serif" to "Szeryfowa", "monospace" to "Maszyna", "cursive" to "Odręczna",
    "casual" to "Luźna", "sans-serif-condensed" to "Wąska", "sans-serif-black" to "Gruba", "sans-serif-light" to "Cienka",
)
private val EmojiSet = listOf(
    "😀", "😂", "🥰", "😎", "🤩", "😴", "🥳", "😇", "🤔", "😱", "👍", "👏", "🙏", "💪", "✌️", "🤞",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "✨", "⭐", "🔥", "🌈", "☀️", "🌙", "⚡", "❄️", "🌸",
    "🌿", "🍀", "🍕", "☕", "🍰", "🎉", "🎁", "🎈", "📚", "🎧", "🎮", "⚽", "🚗", "✈️", "🏠", "📍",
)

private enum class BoardDialog { NONE, STICKER, TEXT, EMOJI, BACKGROUND, FRAME, EXPORT }

// Edytor tablicy: warstwy można przesuwać palcem, dwoma palcami obracać i skalować. Warstwy mogą wychodzić
// poza krawędź tablicy — tam są półprzezroczyste i przy zapisie zostaną przycięte.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardEditorScreen(
    initial: Board,
    onClose: () -> Unit,
    onStickerSaved: (File) -> Unit,
    onWallpaper: ((Board) -> Unit)? = null, // null = zwykła edycja; inaczej przycisk "Na tapetę" (wybór z launchera)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val renderer = remember { BoardRenderer() }
    var board by remember { mutableStateOf(initial) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(BoardDialog.NONE) }
    var textDraft by remember { mutableStateOf<Layer?>(null) } // nowy albo edytowany tekst
    val history = remember { ArrayDeque<Board>() }             // cofanie (do 30 kroków)
    var historySize by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val selected = board.layers.firstOrNull { it.id == selectedId }

    fun push() {
        history.addLast(board)
        while (history.size > 30) history.removeFirst()
        historySize = history.size
    }
    fun change(newBoard: Board) {
        push()
        board = newBoard
    }
    fun addLayer(layer: Layer) {
        change(board.copy(layers = board.layers + layer))
        selectedId = layer.id
    }
    fun center() = board.width / 2f to board.height / 2f

    // Zapis przy wyjściu (także "Wstecz") — tablica i miniatura do listy. Zamykamy dopiero PO zapisie,
    // inaczej lista tablic odświeży się, zanim plik powstanie (nowa tablica by nie wskoczyła).
    var closing by remember { mutableStateOf(false) }
    fun saveBoard(after: () -> Unit = {}) {
        val snapshot = board
        scope.launch(NonCancellable) {
            val thumb = withContext(Dispatchers.Default) { runCatching { renderer.render(snapshot, 360) }.getOrNull() }
            runCatching { BoardStore.save(context, snapshot, thumb) }
            after()
        }
    }
    fun close() {
        if (!closing) {
            closing = true
            saveBoard(onClose)
        }
    }
    // Wstecz z podglądem (Android 14+): tablica lekko się odsuwa w trakcie gestu, puszczenie = zapis i wyjście.
    val backProgress = predictiveBackProgress { close() }
    // Wyjście do ekranu głównego (Home) też zapisuje — system może potem zamknąć proces.
    // Aktywność jest LifecycleOwnerem (jak zdarzenia OnSleep/OnResume w MAUI) — bez dodatkowej biblioteki lifecycle-compose.
    val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) saveBoard()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // Zdjęcie z galerii jako warstwa albo jako tło (kopia do assets tablicy).
    var photoForBackground by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val photo = StickerLibrary.decode(context, uri, 1800) ?: return@launch
            val path = BoardStore.importPhoto(context, photo)
            if (photoForBackground) {
                change(board.copy(bgType = Board.BG_IMAGE, bgImage = path))
            } else {
                val (cx, cy) = center()
                // Zdjęcie na start zajmuje ok. 60% szerokości tablicy.
                val scale = board.width * 0.6f / photo.width
                addLayer(Layer(kind = Layer.KIND_IMAGE, path = path, x = cx, y = cy, scale = scale, frame = Layer.FRAME_INSTAX))
            }
        }
    }

    Column(Modifier.fillMaxSize().backPreview(backProgress)) {
        // Pasek górny: zamknij (zapisuje), cofnij, eksport.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(enabled = !closing, onClick = { close() }) { Text("‹ Gotowe") }
            Text(board.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(enabled = historySize > 0, onClick = {
                history.removeLastOrNull()?.let { board = it }
                historySize = history.size
            }) { Text("↶") }
            if (onWallpaper != null) {
                TextButton(enabled = !closing, onClick = {
                    closing = true // jedno dotknięcie = jeden zapis i jeden obraz (podwójne nie wyśle dwóch)
                    val finished = board
                    selectedId = null
                    saveBoard {
                        onWallpaper(finished)
                        // Jeśli nie udało się zrobić obrazu, studio zostaje otwarte — po chwili przyciski znów działają.
                        scope.launch {
                            kotlinx.coroutines.delay(3000)
                            closing = false
                        }
                    }
                }) { Text("✓ Na tapetę") }
            } else {
                TextButton(onClick = { dialog = BoardDialog.EXPORT }) { Text("Zapisz jako…") }
            }
        }

        BoardCanvas(
            board = board,
            renderer = renderer,
            selectedId = selectedId,
            onSelect = { selectedId = it },
            onGestureStart = { push() },
            onUpdate = { f -> board = f(board) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }

        // Dolny pasek: dla zaznaczonej warstwy jej opcje, inaczej dodawanie.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            if (selected == null) {
                ToolButton("+ Naklejka") { dialog = BoardDialog.STICKER }
                ToolButton("+ Zdjęcie") { photoForBackground = false; pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                ToolButton("+ Tekst") {
                    val (cx, cy) = center()
                    textDraft = Layer(kind = Layer.KIND_TEXT, x = cx, y = cy, text = "", size = board.width * 0.09f, color = 0xFFFFFFFF, outlineWidth = board.width * 0.004f)
                    dialog = BoardDialog.TEXT
                }
                ToolButton("+ Emoji / z klawiatury") { dialog = BoardDialog.EMOJI }
                ToolButton("Tło") { dialog = BoardDialog.BACKGROUND }
            } else {
                if (selected.kind == Layer.KIND_TEXT) ToolButton("✎ Styl tekstu") { textDraft = selected; dialog = BoardDialog.TEXT }
                else ToolButton("▢ Ramka") { dialog = BoardDialog.FRAME }
                ToolButton("⤒ Na wierzch") {
                    change(board.copy(layers = board.layers.filter { it.id != selected.id } + selected))
                }
                ToolButton("⤓ Na spód") {
                    change(board.copy(layers = listOf(selected) + board.layers.filter { it.id != selected.id }))
                }
                ToolButton("⧉ Duplikuj") {
                    addLayer(selected.copy(id = java.util.UUID.randomUUID().toString(), x = selected.x + board.width * 0.04f, y = selected.y + board.width * 0.04f))
                }
                ToolButton("⟲ Prosto") { change(board.updateLayer(selected.id) { it.copy(rotation = 0f) }) }
                ToolButton("✕ Usuń") {
                    change(board.copy(layers = board.layers.filter { it.id != selected.id }))
                    selectedId = null
                }
                ToolButton("Gotowe") { selectedId = null }
            }
        }
    }

    // --- Okna ---
    when (dialog) {
        BoardDialog.STICKER -> StickerPickDialog(
            onPick = { file ->
                dialog = BoardDialog.NONE
                scope.launch {
                    // Kopia w assets tablicy: usunięcie naklejki z biblioteki nie psuje tablic (jak w launcherze).
                    val path = BoardStore.copyAsset(context, file) ?: file.absolutePath
                    val bmp = withContext(Dispatchers.IO) { renderer.bitmap(path) } // dekodowanie poza wątkiem UI
                    val (cx, cy) = center()
                    val scale = if (bmp != null) board.width * 0.45f / maxOf(bmp.width, bmp.height) else 1f
                    addLayer(Layer(kind = Layer.KIND_IMAGE, path = path, x = cx, y = cy, scale = scale))
                }
            },
            onDismiss = { dialog = BoardDialog.NONE },
        )
        BoardDialog.TEXT -> textDraft?.let { draft ->
            TextStyleDialog(
                initial = draft,
                onDone = { layer ->
                    dialog = BoardDialog.NONE
                    textDraft = null
                    if (layer.text.isNotBlank()) {
                        if (board.layers.any { it.id == layer.id }) change(board.updateLayer(layer.id) { layer }) else addLayer(layer)
                    }
                },
                onDismiss = { dialog = BoardDialog.NONE; textDraft = null },
            )
        }
        BoardDialog.EMOJI -> EmojiDialog(
            onEmoji = { emoji ->
                dialog = BoardDialog.NONE
                val (cx, cy) = center()
                addLayer(Layer(kind = Layer.KIND_TEXT, x = cx, y = cy, text = emoji, size = board.width * 0.18f))
            },
            onKeyboardImage = { uri ->
                dialog = BoardDialog.NONE
                scope.launch {
                    // Naklejka / GIF z klawiatury: pierwsza klatka jako PNG w bibliotece i od razu na tablicę.
                    val bmp = StickerLibrary.decode(context, uri, 1024)
                    if (bmp == null) {
                        message = "Nie udało się wczytać naklejki z klawiatury."
                        return@launch
                    }
                    val file = StickerLibrary.save(context, bmp)
                    val path = BoardStore.copyAsset(context, file) ?: file.absolutePath
                    val (cx, cy) = center()
                    addLayer(Layer(kind = Layer.KIND_IMAGE, path = path, x = cx, y = cy, scale = board.width * 0.4f / maxOf(bmp.width, bmp.height)))
                }
            },
            onDismiss = { dialog = BoardDialog.NONE },
        )
        BoardDialog.BACKGROUND -> BackgroundDialog(
            board = board,
            onChange = { change(it) },
            onPhoto = {
                dialog = BoardDialog.NONE
                photoForBackground = true
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { dialog = BoardDialog.NONE },
        )
        BoardDialog.FRAME -> selected?.let { layer ->
            FrameDialog(
                layer = layer,
                onChange = { updated -> change(board.updateLayer(layer.id) { updated }) },
                onDismiss = { dialog = BoardDialog.NONE },
            )
        }
        BoardDialog.EXPORT -> ExportDialog(
            onSticker = {
                dialog = BoardDialog.NONE
                scope.launch {
                    val sticker = withContext(Dispatchers.Default) { runCatching { renderer.renderSticker(board) }.getOrNull() }
                    if (sticker == null) {
                        message = "Tablica jest pusta — nie ma czego zapisać."
                    } else {
                        saveBoard()
                        onStickerSaved(StickerLibrary.save(context, sticker))
                        message = "Zapisano naklejkę w bibliotece."
                    }
                }
            },
            onGallery = {
                dialog = BoardDialog.NONE
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { saveToGallery(context, renderer.render(board), board.name) }.isSuccess
                    }
                    message = if (ok) "Zapisano w Galerii (Obrazy › StickOnMe) — ustawisz jako tapetę trybu w LaunchOnMe."
                        else "Nie udało się zapisać w Galerii."
                }
            },
            onDismiss = { dialog = BoardDialog.NONE },
        )
        BoardDialog.NONE -> Unit
    }
}

// Obraz tablicy do Galerii telefonu (Pictures/StickOnMe) — bez uprawnień od Androida 10 (MediaStore).
private fun saveToGallery(context: Context, bitmap: Bitmap, name: String) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "${name.ifBlank { "StickOnMe" }}_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StickOnMe")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Brak Galerii")
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("Zapis nieudany")
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

// Płótno: tablica dopasowana do ekranu z marginesem (widać, co wystaje poza krawędź).
// Jeden palec: zaznacz i przesuń warstwę. Dwa palce: skala i obrót zaznaczonej warstwy.
@Composable
private fun BoardCanvas(
    board: Board,
    renderer: BoardRenderer,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onGestureStart: () -> Unit,
    onUpdate: ((Board) -> Board) -> Unit, // zmiana liczona na AKTUALNEJ tablicy (kilka zdarzeń przed przerysowaniem)
    modifier: Modifier,
) {
    val currentBoard by rememberUpdatedState(board)
    val currentSelected by rememberUpdatedState(selectedId)
    val outside = Color(0xFF0B0C0E)
    val selection = MaterialTheme.colorScheme.secondary
    BoxWithConstraints(modifier.background(outside)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val scale = minOf(w * 0.84f / board.width, h * 0.84f / board.height)
        val origin = Offset((w - board.width * scale) / 2f, (h - board.height * scale) / 2f)
        fun toBoard(p: Offset) = (p - origin) / scale

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(scale, origin) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = toBoard(down.position)
                        val hit = renderer.hit(currentBoard, start.x, start.y)
                        // Dotknięcie pustego miejsca odznacza; dotknięcie warstwy ją zaznacza.
                        // Dwa palce działają na zaznaczonej warstwie, nawet gdy drugi palec jest obok niej.
                        var target = hit?.id ?: currentSelected
                        if (hit != null) onSelect(hit.id)
                        var started = false
                        var twoFingers = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) twoFingers = true
                            val id = target
                            if (id != null && (hit != null || pressed.size >= 2)) {
                                val zoom = event.calculateZoom()
                                val rotation = event.calculateRotation()
                                val pan = event.calculatePan() / scale
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (!started && (pan.getDistance() > 0.5f || zoom != 1f || rotation != 0f)) {
                                    onGestureStart() // jeden krok "Cofnij" na cały gest
                                    started = true
                                }
                                if (started) {
                                    val twoPointers = pressed.size >= 2 && centroid.isSpecified
                                    val pivot = if (twoPointers) toBoard(centroid) else Offset.Zero
                                    onUpdate { b ->
                                        b.updateLayer(id) { l ->
                                            val newScale = (l.scale * zoom).coerceIn(0.02f, 30f)
                                            if (twoPointers) {
                                                // Obrót i skala wokół środka palców (warstwa "trzyma się" palców, jak zdjęcie w galerii).
                                                val z = newScale / l.scale
                                                val rad = Math.toRadians(rotation.toDouble())
                                                val dx = l.x - pivot.x
                                                val dy = l.y - pivot.y
                                                l.copy(
                                                    x = pivot.x + ((dx * cos(rad) - dy * sin(rad)) * z).toFloat() + pan.x,
                                                    y = pivot.y + ((dx * sin(rad) + dy * cos(rad)) * z).toFloat() + pan.y,
                                                    scale = newScale,
                                                    rotation = l.rotation + rotation,
                                                )
                                            } else {
                                                l.copy(x = l.x + pan.x, y = l.y + pan.y)
                                            }
                                        }
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                        if (hit == null && !twoFingers && !started) onSelect(null)
                        target = null
                    }
                },
        ) {
            drawIntoCanvas { c ->
                val canvas = c.nativeCanvas
                canvas.save()
                canvas.translate(origin.x, origin.y)
                canvas.scale(scale, scale)
                val area = RectF(0f, 0f, board.width.toFloat(), board.height.toFloat())
                // Przezroczyste tło = szachownica.
                if (board.bgType == Board.BG_TRANSPARENT) drawChecker(canvas, area, 18.dp.toPx() / scale)
                canvas.save()
                canvas.clipRect(area)
                renderer.drawBackground(canvas, board)
                canvas.restore()
                // Najpierw całe warstwy przygaszone (to, co wystaje poza tablicę), potem w tablicy w pełnym kolorze.
                board.layers.forEach { renderer.drawLayer(canvas, it, alpha = 90) }
                canvas.save()
                canvas.clipRect(area)
                board.layers.forEach { renderer.drawLayer(canvas, it) }
                canvas.restore()
                // Ramka zaznaczenia (obrócona razem z warstwą).
                board.layers.firstOrNull { it.id == selectedId }?.let { layer ->
                    val (lw, lh) = renderer.contentSize(layer)
                    canvas.save()
                    canvas.translate(layer.x, layer.y)
                    canvas.rotate(layer.rotation)
                    val hw = lw * layer.scale / 2f
                    val hh = lh * layer.scale / 2f
                    canvas.drawRect(RectF(-hw, -hh, hw, hh), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 2.dp.toPx() / scale
                        color = android.graphics.Color.argb(255, (selection.red * 255).toInt(), (selection.green * 255).toInt(), (selection.blue * 255).toInt())
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10.dp.toPx() / scale, 6.dp.toPx() / scale), 0f)
                    })
                    canvas.restore()
                }
                // Krawędź tablicy.
                canvas.drawRect(area, android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.dp.toPx() / scale
                    color = 0x66FFFFFF
                })
                canvas.restore()
            }
        }
    }
}

private fun drawChecker(canvas: android.graphics.Canvas, area: RectF, cell: Float) {
    val a = android.graphics.Paint().apply { color = 0xFF2A2E34.toInt() }
    val b = android.graphics.Paint().apply { color = 0xFF1E2126.toInt() }
    canvas.drawRect(area, a)
    var y = area.top
    var row = 0
    while (y < area.bottom) {
        var x = area.left + if (row % 2 == 0) 0f else cell
        while (x < area.right) {
            canvas.drawRect(x, y, minOf(x + cell, area.right), minOf(y + cell, area.bottom), b)
            x += cell * 2
        }
        y += cell
        row++
    }
}

// --- Okna edytora ---

@Composable
private fun StickerPickDialog(onPick: (File) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val files by produceState<List<File>?>(null) { value = withContext(Dispatchers.IO) { StickerLibrary.list(context) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Naklejka z biblioteki") },
        text = {
            val list = files
            when {
                list == null -> Text("Wczytywanie…")
                list.isEmpty() -> Text("Biblioteka jest pusta — najpierw zrób naklejkę („+ Nowa naklejka”).")
                else -> LazyVerticalGrid(columns = GridCells.Adaptive(80.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.absolutePath }) { file ->
                        val image by produceState<ImageBitmap?>(null, file) { value = withContext(Dispatchers.IO) { StickerLibrary.thumbnail(file, 200) } }
                        Box(
                            Modifier
                                .padding(4.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPick(file) }
                                .padding(6.dp),
                        ) { image?.let { Image(it, contentDescription = "Naklejka", modifier = Modifier.fillMaxSize()) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun Swatches(colors: List<Long>, selected: Long?, onPick: (Long) -> Unit, allowNone: Boolean = false, onNone: () -> Unit = {}) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        if (allowNone) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(if (selected == null) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable(onClick = onNone),
            ) { Text("∅", style = MaterialTheme.typography.labelMedium) }
        }
        colors.forEach { c ->
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (c == selected) 3.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

// Tekst i jego styl: czcionka, kolor, wielkość, pogrubienie/kursywa, obrys, podkreślenie (grubość), cień, zakreślacz.
@Composable
private fun TextStyleDialog(initial: Layer, onDone: (Layer) -> Unit, onDismiss: () -> Unit) {
    var layer by remember { mutableStateOf(initial) }
    val base = initial.size // skala suwaków względem wielkości startowej
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Tekst") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = layer.text, onValueChange = { layer = layer.copy(text = it) }, label = { Text("Napis (Enter = nowa linia)") })
                Text("Czcionka", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Fonts.forEach { (family, label) ->
                        FilterChip(selected = layer.font == family, onClick = { layer = layer.copy(font = family) }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = layer.bold, onClick = { layer = layer.copy(bold = !layer.bold) }, label = { Text("B", fontWeight = FontWeight.Bold) })
                    FilterChip(selected = layer.italic, onClick = { layer = layer.copy(italic = !layer.italic) }, label = { Text("I") })
                }
                Text("Kolor", style = MaterialTheme.typography.labelLarge)
                Swatches(Palette, layer.color, { layer = layer.copy(color = it) })
                Text("Wielkość", style = MaterialTheme.typography.labelLarge)
                Slider(value = layer.size, onValueChange = { layer = layer.copy(size = it) }, valueRange = base * 0.3f..base * 3f)
                Text("Obrys liter", style = MaterialTheme.typography.labelLarge)
                Slider(value = layer.outlineWidth, onValueChange = { layer = layer.copy(outlineWidth = it) }, valueRange = 0f..base * 0.15f)
                Swatches(Palette, layer.outlineColor, { layer = layer.copy(outlineColor = it) })
                Text("Podkreślenie (grubość)", style = MaterialTheme.typography.labelLarge)
                Slider(value = layer.underline, onValueChange = { layer = layer.copy(underline = it) }, valueRange = 0f..base * 0.2f)
                Text("Zakreślacz", style = MaterialTheme.typography.labelLarge)
                Swatches(Palette.map { (it and 0x00FFFFFFL) or 0xCC000000L }, layer.highlight, { layer = layer.copy(highlight = it) }, allowNone = true, onNone = { layer = layer.copy(highlight = null) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cień", modifier = Modifier.weight(1f))
                    Switch(checked = layer.shadow, onCheckedChange = { layer = layer.copy(shadow = it) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(layer) }) { Text("Gotowe") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// Emoji z listy albo z klawiatury — to pole przyjmuje też naklejki, GIF-y i Emoji Kitchen z Gboard / klawiatury Samsunga.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiDialog(onEmoji: (String) -> Unit, onKeyboardImage: (Uri) -> Unit, onDismiss: () -> Unit) {
    val field = rememberTextFieldState()
    val currentOnImage by rememberUpdatedState(onKeyboardImage)
    val receiver = remember {
        object : ReceiveContentListener {
            override fun onReceive(transferableContent: TransferableContent): TransferableContent? =
                transferableContent.consume { item ->
                    val uri = item.uri
                    if (uri != null) {
                        currentOnImage(uri)
                        true
                    } else {
                        false
                    }
                }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Emoji i naklejki z klawiatury") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyVerticalGrid(columns = GridCells.Adaptive(44.dp), modifier = Modifier.heightIn(max = 240.dp)) {
                    items(EmojiSet) { e ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onEmoji(e) },
                        ) { Text(e, fontSize = 26.sp) }
                    }
                }
                Text(
                    "Albo dotknij pola poniżej i wybierz z klawiatury: emoji (wpisz i „Dodaj”) albo naklejkę / GIF — wskoczy od razu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    state = field,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                        // Treść "bogata" z klawiatury (obrazek): bierzemy adres i od razu kopiujemy (dostęp jest chwilowy).
                        .contentReceiver(receiver),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val text = field.text.toString().trim()
                if (text.isNotEmpty()) onEmoji(text)
            }) { Text("Dodaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun BackgroundDialog(board: Board, onChange: (Board) -> Unit, onPhoto: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Tło tablicy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = board.bgType == Board.BG_TRANSPARENT, onClick = { onChange(board.copy(bgType = Board.BG_TRANSPARENT)) }, label = { Text("Przezroczyste") })
                    FilterChip(selected = board.bgType == Board.BG_IMAGE, onClick = onPhoto, label = { Text("Zdjęcie z galerii…") })
                }
                Text("Kolor", style = MaterialTheme.typography.labelLarge)
                Swatches(Palette, board.bgColor.takeIf { board.bgType == Board.BG_COLOR }, { onChange(board.copy(bgType = Board.BG_COLOR, bgColor = it)) })
                Text("Gradient", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Gradients.forEach { (a, b) ->
                        val sel = board.bgType == Board.BG_GRADIENT && board.bgColor == a && board.bgColor2 == b
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(a), Color(b))))
                                .border(if (sel) 3.dp else 1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                .clickable { onChange(board.copy(bgType = Board.BG_GRADIENT, bgColor = a, bgColor2 = b)) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
    )
}

@Composable
private fun FrameDialog(layer: Layer, onChange: (Layer) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Ramka") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Layer.FRAMES.forEach { (key, label) ->
                        FilterChip(selected = layer.frame == key, onClick = { onChange(layer.copy(frame = key)) }, label = { Text(label) })
                    }
                }
                if (Layer.hasColor(layer.frame)) {
                    Text("Kolor ramki", style = MaterialTheme.typography.labelLarge)
                    Swatches(Palette, layer.frameColor, { onChange(layer.copy(frameColor = it)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
    )
}

@Composable
private fun ExportDialog(onSticker: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Zapisz jako…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tablica zapisuje się sama przy wyjściu. Tu robisz z niej gotowy obraz:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSticker) { Text("Naklejka (bez tła, przycięta) → biblioteka") }
                TextButton(onClick = onGallery) { Text("Obraz do Galerii (kolaż, tapeta, do druku)") }
                Text(
                    "Tapetę trybu ustawisz potem w launcherze: edycja trybu → Tapeta → obraz z albumu „StickOnMe”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

// Nowa tablica: wybór rozmiaru (gotowe albo własny).
@Composable
fun NewBoardDialog(onCreate: (Board) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val presets = remember { boardPresets(context) }
    var customW by remember { mutableStateOf("1500") }
    var customH by remember { mutableStateOf("1500") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Nowa tablica") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                presets.forEach { p ->
                    TextButton(onClick = {
                        onCreate(Board(name = p.label, width = p.width, height = p.height, bgType = if (p.transparent) Board.BG_TRANSPARENT else Board.BG_COLOR))
                    }) { Text("${p.label} · ${p.width}×${p.height}") }
                }
                Text("Własny rozmiar (px)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = customW, onValueChange = { customW = it.filter(Char::isDigit).take(4) }, label = { Text("szer.") }, modifier = Modifier.width(96.dp))
                    Text("  ×  ")
                    OutlinedTextField(value = customH, onValueChange = { customH = it.filter(Char::isDigit).take(4) }, label = { Text("wys.") }, modifier = Modifier.width(96.dp))
                }
                TextButton(onClick = {
                    val w = customW.toIntOrNull()?.coerceIn(128, 5000) ?: 1500
                    val h = customH.toIntOrNull()?.coerceIn(128, 5000) ?: 1500
                    onCreate(Board(name = "Tablica $w×$h", width = w, height = h, bgType = Board.BG_COLOR))
                }) { Text("Utwórz własną") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
