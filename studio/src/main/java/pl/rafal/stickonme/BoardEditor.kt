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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
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
    "sans-serif" to R.string.som_board_font_regular, "serif" to R.string.som_board_font_serif,
    "monospace" to R.string.som_board_font_mono, "cursive" to R.string.som_board_font_handwritten,
    "casual" to R.string.som_board_font_casual, "sans-serif-condensed" to R.string.som_board_font_condensed,
    "sans-serif-black" to R.string.som_board_font_heavy, "sans-serif-light" to R.string.som_board_font_light,
)
private val EmojiSet = listOf(
    "😀", "😂", "🥰", "😎", "🤩", "😴", "🥳", "😇", "🤔", "😱", "👍", "👏", "🙏", "💪", "✌️", "🤞",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "✨", "⭐", "🔥", "🌈", "☀️", "🌙", "⚡", "❄️", "🌸",
    "🌿", "🍀", "🍕", "☕", "🍰", "🎉", "🎁", "🎈", "📚", "🎧", "🎮", "⚽", "🚗", "✈️", "🏠", "📍",
)

// Dla edytora tekstu (TextEditor.kt) — te same kolory i czcionki co na tablicy.
internal val BoardPalette: List<Long> get() = Palette
internal val BoardFonts: List<Pair<String, Int>> get() = Fonts

private enum class BoardDialog { NONE, STICKER, TEXT, EMOJI, EXPORT }

// Panele na dole ekranu (zamiast okien): zmiany widać od razu na tablicy, ✓ Zastosuj / ✕ Anuluj jak w edytorze naklejki.
private enum class BoardPanel { BACKGROUND, FRAME, FILTER }

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
    val renderer = remember { BoardRenderer() } // podgląd: mniejsze kopie obrazów, bez dekodowania na wątku UI
    // Zamknięcie tablicy = puszczamy pamięć podręczną obrazów (nie czekamy, aż GC znajdzie cały renderer).
    DisposableEffect(renderer) { onDispose { renderer.clear() } }
    // Stan tablicy jako obiekt State (nie "by"): ekran NIE czyta go w kompozycji, tylko w akcjach i przy rysowaniu,
    // więc przesuwanie warstwy palcem przerysowuje płótno, a nie przebudowuje całego ekranu co klatkę.
    val boardState = remember { mutableStateOf(initial) }
    var board by boardState
    var selectedId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(BoardDialog.NONE) }
    var textDraft by remember { mutableStateOf<Layer?>(null) } // nowy albo edytowany tekst
    val history = remember { ArrayDeque<Board>() }             // cofanie (do 30 kroków)
    var historySize by remember { mutableStateOf(0) }
    val redoStack = remember { ArrayDeque<Board>() }           // „Ponów” — kroki zdjęte przez Cofnij
    var redoSize by remember { mutableStateOf(0) }
    var panel by remember { mutableStateOf<BoardPanel?>(null) }
    var panelStart by remember { mutableStateOf<Board?>(null) } // tablica sprzed otwarcia panelu (do ✕)
    var message by remember { mutableStateOf<String?>(null) }
    // Dla paska narzędzi wystarczy rodzaj zaznaczonej warstwy i nazwa tablicy — derivedStateOf zmienia się tylko,
    // gdy zmieni się wynik (jak INotifyPropertyChanged tylko dla tej jednej właściwości), a nie przy każdym ruchu.
    val selectedKind by remember { derivedStateOf { boardState.value.layers.firstOrNull { it.id == selectedId }?.kind } }
    val boardName by remember { derivedStateOf { boardState.value.name } }
    fun selectedLayer(): Layer? = board.layers.firstOrNull { it.id == selectedId } // czytane w chwili akcji

    // Obrazy warstw i tła wczytywane w tle (podgląd nigdy nie dekoduje na wątku UI). Po wczytaniu "loaded" rośnie,
    // a płótno rysuje się od nowa. Ścieżki jako zbiór — przesuwanie warstwy go nie zmienia, więc nic się nie restartuje.
    // Klucz to zbiór obrazów z ramkami — zmiana ramki też liczy ją w tle.
    var loaded by remember { mutableIntStateOf(0) }
    var preloading by remember { mutableStateOf(true) }
    val needs by remember {
        derivedStateOf {
            boardState.value.let { b ->
                b.layers.mapNotNull { l -> l.path?.let { BoardRenderer.Need(it, l.frame, l.frameColor, l.filters) } }.toSet() +
                    setOfNotNull(b.bgImage?.let { BoardRenderer.Need(it) })
            }
        }
    }
    // Ponowne wczytanie, gdy podgląd nie znalazł obrazu w pamięci (najwyżej 2 razy na ten sam zestaw — przy bardzo wielu
    // zdjęciach cache może nie zmieścić wszystkich i nie chcemy wczytywać w kółko).
    var reload by remember { mutableIntStateOf(0) }
    var reloadsLeft by remember { mutableIntStateOf(2) }
    LaunchedEffect(needs) { reloadsLeft = 2 }
    LaunchedEffect(needs, reload) {
        renderer.previewMiss = false
        withContext(Dispatchers.IO) { renderer.preload(needs) { ensureActive() } }
        loaded++ // zawsze — po wczytaniu podgląd wypala się od nowa
        preloading = false
    }

    fun push() {
        history.addLast(board)
        while (history.size > 30) history.removeFirst()
        historySize = history.size
        redoStack.clear() // nowa zmiana = stara „przyszłość” przepada
        redoSize = 0
    }
    fun undo() {
        history.removeLastOrNull()?.let { previous ->
            redoStack.addLast(board)
            board = previous
        }
        historySize = history.size
        redoSize = redoStack.size
    }
    fun redo() {
        redoStack.removeLastOrNull()?.let { next ->
            history.addLast(board)
            board = next
        }
        historySize = history.size
        redoSize = redoStack.size
    }
    // Panel: jeden krok „Cofnij” na całą sesję panelu; ✕ przywraca tablicę sprzed otwarcia.
    fun openPanel(p: BoardPanel) {
        panelStart = board
        history.addLast(board) // bez czyszczenia „Ponów” — panel może skończyć się bez zmian
        while (history.size > 30) history.removeFirst()
        historySize = history.size
        panel = p
    }
    fun closePanel(apply: Boolean) {
        val start = panelStart
        if (!apply && start != null) board = start
        if (!apply || board == start) { // nic się nie zmieniło albo anulowano — krok historii niepotrzebny
            history.removeLastOrNull()
            historySize = history.size
        } else {
            redoStack.clear() // zatwierdzona zmiana = stara „przyszłość” przepada
            redoSize = 0
        }
        panel = null
        panelStart = null
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
                val withPhoto = board.copy(bgType = Board.BG_IMAGE, bgImage = path)
                if (panel == BoardPanel.BACKGROUND) board = withPhoto else change(withPhoto) // w panelu historia jest już zapisana
            } else {
                val (cx, cy) = center()
                // Zdjęcie na start zajmuje ok. 60% szerokości tablicy (skala względem rozmiaru logicznego, max 1400 px).
                val scale = board.width * 0.6f / (photo.width * BoardRenderer.logicalScale(photo.width, photo.height))
                addLayer(Layer(kind = Layer.KIND_IMAGE, path = path, x = cx, y = cy, scale = scale, frame = Layer.FRAME_INSTAX))
            }
        }
    }

    // Komunikat (np. „Zapisano w Galerii”) znika sam po 3 s.
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3000)
            message = null
        }
    }

    // Wstecz: najpierw zamyka panel (= ✕), potem odznacza warstwę, a dopiero potem wychodzi z tablicy (zapis).
    BackHandler(enabled = panel != null) { closePanel(apply = false) }
    BackHandler(enabled = panel == null && selectedKind != null) { selectedId = null }

    // Akcje zaznaczonej warstwy (biorą warstwę w chwili dotknięcia — zawsze aktualne położenie).
    fun toFront() = selectedLayer()?.let { sel -> change(board.copy(layers = board.layers.filter { it.id != sel.id } + sel)) }
    fun toBack() = selectedLayer()?.let { sel -> change(board.copy(layers = listOf(sel) + board.layers.filter { it.id != sel.id })) }
    fun duplicate() = selectedLayer()?.let { sel ->
        addLayer(sel.copy(id = java.util.UUID.randomUUID().toString(), x = sel.x + board.width * 0.04f, y = sel.y + board.width * 0.04f))
    }
    fun straighten() = selectedId?.let { id -> change(board.updateLayer(id) { it.copy(rotation = 0f) }) }
    fun deleteLayer(id: String) {
        change(board.copy(layers = board.layers.filter { it.id != id }))
        if (selectedId == id) selectedId = null
    }

    Box(Modifier.fillMaxSize().backPreview(backProgress)) {
    Column(Modifier.fillMaxSize()) {
        // Górny pasek (makieta): ‹ | nazwa + rozmiar | Cofnij · Ponów | Zapisz / Na tapetę.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            StudioIconButton(StudioIcons.Back, stringResource(R.string.som_board_back_cd), enabled = !closing && panel == null, outlined = true) { close() }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(boardName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.som_board_size_sub, initial.width, initial.height),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            StudioIconButton(StudioIcons.Undo, stringResource(R.string.som_undo_cd), enabled = panel == null && historySize > 0) { undo() }
            StudioIconButton(StudioIcons.Redo, stringResource(R.string.som_redo_cd), enabled = panel == null && redoSize > 0) { redo() }
            if (onWallpaper != null) {
                StudioPrimaryButton(stringResource(R.string.som_board_wallpaper_short), enabled = !closing && panel == null) {
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
                }
            } else {
                StudioPrimaryButton(stringResource(R.string.som_board_save_short), enabled = panel == null, icon = null) { dialog = BoardDialog.EXPORT }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            BoardCanvas(
                board = { boardState.value },
                loaded = { loaded },
                onMiss = {
                    if (!preloading && reloadsLeft > 0) {
                        reloadsLeft--
                        reload++
                    }
                },
                renderer = renderer,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onGestureStart = { push() },
                onUpdate = { f -> board = f(board) },
                onDelete = { id -> deleteLayer(id) },
                interactive = panel == null, // w panelu (✓/✕) tablica tylko pokazuje zmiany — inaczej ✕ cofałby nie ten krok
                modifier = Modifier.fillMaxSize(),
            )
            // Pierwsze wczytanie obrazów tablicy (wiele zdjęć) — kółko zamiast zacięcia ekranu.
            if (preloading) CircularProgressIndicator(Modifier.align(Alignment.Center))

            // Pływający pasek nad tablicą: opcje zaznaczonej warstwy (makieta). Przewijany, gdy się nie mieści.
            val kind = selectedKind
            if (kind != null && panel == null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(4.dp),
                ) {
                    if (kind == Layer.KIND_TEXT) {
                        StudioContextItem(StudioIcons.Text, stringResource(R.string.som_ctx_style)) { textDraft = selectedLayer(); dialog = BoardDialog.TEXT }
                    } else {
                        StudioContextItem(StudioIcons.Frame, stringResource(R.string.som_rail_frame)) { openPanel(BoardPanel.FRAME) }
                        StudioContextItem(StudioIcons.Filter, stringResource(R.string.som_rail_filter)) { openPanel(BoardPanel.FILTER) }
                    }
                    StudioContextItem(StudioIcons.ToFront, stringResource(R.string.som_ctx_front)) { toFront() }
                    StudioContextItem(StudioIcons.ToBack, stringResource(R.string.som_ctx_back)) { toBack() }
                    StudioContextItem(StudioIcons.Duplicate, stringResource(R.string.som_ctx_duplicate)) { duplicate() }
                    StudioContextItem(StudioIcons.Rotate, stringResource(R.string.som_rotate_straight)) { straighten() }
                    StudioContextItem(StudioIcons.Trash, stringResource(R.string.som_ctx_delete), tint = MaterialTheme.colorScheme.error) {
                        selectedId?.let { deleteLayer(it) }
                    }
                    StudioContextItem(StudioIcons.Check, stringResource(R.string.som_done), tint = MaterialTheme.colorScheme.secondary) { selectedId = null }
                }
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE60E0F12))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        val shownPanel = panel
        if (shownPanel == null) {
            // Dolny pasek: dodawanie (ikona + podpis), przewijany.
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 14.dp),
            ) {
                StudioRailItem(StudioIcons.Sticker, stringResource(R.string.som_rail_sticker), selected = false) { dialog = BoardDialog.STICKER }
                StudioRailItem(StudioIcons.Photo, stringResource(R.string.som_rail_photo), selected = false) {
                    photoForBackground = false
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                StudioRailItem(StudioIcons.Text, stringResource(R.string.som_rail_text), selected = false) {
                    val (cx, cy) = center()
                    textDraft = Layer(kind = Layer.KIND_TEXT, x = cx, y = cy, text = "", size = board.width * 0.09f, color = 0xFFFFFFFF, shadow = true) // jak w Instagramie: biały z lekkim cieniem
                    dialog = BoardDialog.TEXT
                }
                StudioRailItem(StudioIcons.Emoji, stringResource(R.string.som_rail_emoji), selected = false) { dialog = BoardDialog.EMOJI }
                StudioRailItem(StudioIcons.Background, stringResource(R.string.som_rail_background), selected = false) {
                    selectedId = null
                    openPanel(BoardPanel.BACKGROUND)
                }
            }
        } else {
            // Panel (tło / ramka warstwy) — zmiany od razu na tablicy, na dole ✕ / ✓.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(
                        when (shownPanel) {
                            BoardPanel.BACKGROUND -> R.string.som_board_bg_title
                            BoardPanel.FRAME -> R.string.som_board_frame_title
                            BoardPanel.FILTER -> R.string.som_board_filter_title
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (shownPanel == BoardPanel.BACKGROUND) {
                    BackgroundPanel(
                        board = board,
                        onChange = { board = it },
                        onPhoto = {
                            photoForBackground = true
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                } else {
                    val layer = board.layers.firstOrNull { it.id == selectedId } // panel otwarty = czytanie w kompozycji jest ok
                    if (layer != null && shownPanel == BoardPanel.FILTER) {
                        FilterPicker(layer.filters) { f -> board = board.updateLayer(layer.id) { it.copy(filters = f) } }
                    } else if (layer != null) {
                        FramePanel(layer = layer, onChange = { updated -> board = board.updateLayer(layer.id) { updated } })
                    }
                }
            }
            StudioStepBar(
                cancelLabel = stringResource(R.string.som_cancel),
                applyLabel = stringResource(R.string.som_apply),
                enabled = true,
                onCancel = { closePanel(apply = false) },
                onApply = { closePanel(apply = true) },
            )
        }
    }

    // Tekst: pełnoekranowy edytor nad tablicą (Instagram), nie okno.
    val draft = textDraft
    if (dialog == BoardDialog.TEXT && draft != null) {
        TextEditorOverlay(
            initial = draft,
            base = initial.width * 0.09f, // ta sama „normalna” wielkość co nowy tekst (bez czytania tablicy w kompozycji)
            onDone = { layer ->
                dialog = BoardDialog.NONE
                textDraft = null
                val exists = board.layers.any { it.id == layer.id }
                when {
                    layer.text.isNotBlank() && exists -> change(board.updateLayer(layer.id) { layer })
                    layer.text.isNotBlank() -> addLayer(layer)
                    exists -> deleteLayer(layer.id) // wyczyszczony tekst = usunięcie warstwy
                }
            },
            onCancel = { dialog = BoardDialog.NONE; textDraft = null },
        )
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
                    val img = withContext(Dispatchers.IO) { renderer.bitmap(path) } // dekodowanie poza wątkiem UI (od razu w pamięci podglądu)
                    val (cx, cy) = center()
                    val scale = if (img != null) board.width * 0.45f / maxOf(img.width, img.height) else 1f // rozmiar logiczny
                    addLayer(Layer(kind = Layer.KIND_IMAGE, path = path, x = cx, y = cy, scale = scale))
                }
            },
            onDismiss = { dialog = BoardDialog.NONE },
        )
        BoardDialog.TEXT -> Unit // edytor tekstu jest wyżej (nakładka w Box)
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
                        message = context.getString(R.string.som_board_keyboard_failed)
                        return@launch
                    }
                    val file = StickerLibrary.save(context, bmp)
                    val path = BoardStore.copyAsset(context, file) ?: file.absolutePath
                    val (cx, cy) = center()
                    val logical = maxOf(bmp.width, bmp.height) * BoardRenderer.logicalScale(bmp.width, bmp.height)
                    addLayer(Layer(kind = Layer.KIND_IMAGE, path = path, x = cx, y = cy, scale = board.width * 0.4f / logical))
                }
            },
            onDismiss = { dialog = BoardDialog.NONE },
        )
        BoardDialog.EXPORT -> ExportDialog(
            onSticker = {
                dialog = BoardDialog.NONE
                val snapshot = board
                scope.launch {
                    // Eksport w pełnej jakości: osobny renderer (oryginalne obrazy), po zapisie do wyrzucenia.
                    val sticker = withContext(Dispatchers.Default) { runCatching { BoardRenderer.forExport().renderSticker(snapshot) }.getOrNull() }
                    if (sticker == null) {
                        message = context.getString(R.string.som_board_empty)
                    } else {
                        saveBoard()
                        val file = StickerLibrary.save(context, sticker)
                        sticker.recycle() // już w pliku — nie czekamy na GC z kilkudziesięcioma MB
                        onStickerSaved(file)
                        message = context.getString(R.string.som_board_sticker_saved)
                    }
                }
            },
            onGallery = {
                dialog = BoardDialog.NONE
                val snapshot = board
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val image = BoardRenderer.forExport().render(snapshot) // pełna jakość, pełny rozmiar tablicy
                            try {
                                saveToGallery(context, image, snapshot.name)
                            } finally {
                                image.recycle()
                            }
                        }.isSuccess
                    }
                    message = if (ok) context.getString(R.string.som_board_gallery_saved)
                        else context.getString(R.string.som_board_gallery_failed)
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

// Płótno: tablica dopasowana do ekranu z marginesem (widać, co wystaje poza krawędź).
// Jeden palec: zaznacz i przesuń warstwę. Dwa palce: skala i obrót zaznaczonej warstwy.
//
// Wydajność (1.3.0): warstwy, które się nie ruszają, są "wypalone" w dwa obrazy wielkości płótna — wszystko POD
// zaznaczoną warstwą (z tłem) i wszystko NAD nią. W każdej klatce gestu rysujemy więc trzy rzeczy: spód, ruszaną
// warstwę i wierzch, zamiast całej tablicy dwa razy. Obrazy wypalają się od nowa tylko, gdy zmieni się ich zawartość
// (inne zaznaczenie, zmiana warstwy, tła, wczytany obraz) — porównanie list warstw (data class) jak Equals w C#.
// Tablica jest czytana dopiero przy rysowaniu (board()), więc ruch palca nie przebudowuje kompozycji, tylko rysunek.
@Composable
private fun BoardCanvas(
    board: () -> Board,
    loaded: () -> Int,                    // licznik wczytanych w tle obrazów (zmiana = przerysuj)
    onMiss: () -> Unit,                   // podgląd nie znalazł obrazu w pamięci → wczytaj w tle
    renderer: BoardRenderer,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onGestureStart: () -> Unit,
    onUpdate: ((Board) -> Board) -> Unit, // zmiana liczona na AKTUALNEJ tablicy (kilka zdarzeń przed przerysowaniem)
    onDelete: (String) -> Unit,           // uchwyt ✕ na rogu zaznaczonej warstwy
    interactive: Boolean,                 // false = bez gestów (otwarty panel)
    modifier: Modifier,
) {
    val currentBoard by rememberUpdatedState(board)
    val currentSelected by rememberUpdatedState(selectedId)
    val currentOnMiss by rememberUpdatedState(onMiss)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val deleteColor = MaterialTheme.colorScheme.error
    val outside = Color(0xFF0B0C0E)
    val selection = MaterialTheme.colorScheme.secondary
    // Wymiary tablicy — jedyne, czego potrzebuje kompozycja (skala i położenie na ekranie). Nie zmieniają się przy ruchu.
    val dims by remember { derivedStateOf { currentBoard().let { it.width to it.height } } }
    val bake = remember { SceneBake() }
    // Puszczenie wypalonych obrazów razem z płótnem (wyjście z tablicy).
    DisposableEffect(bake) { onDispose { bake.release() } }
    BoxWithConstraints(modifier.background(outside)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val (boardW, boardH) = dims
        val scale = minOf(w * 0.84f / boardW, h * 0.84f / boardH)
        val origin = Offset((w - boardW * scale) / 2f, (h - boardH * scale) / 2f)
        fun toBoard(p: Offset) = (p - origin) / scale
        fun toScreen(p: Offset) = origin + p * scale
        // Róg warstwy (dx, dy względem środka, w jednostkach tablicy) po obrocie warstwy — w układzie tablicy.
        fun corner(l: Layer, dx: Float, dy: Float): Offset {
            val rad = Math.toRadians(l.rotation.toDouble())
            return Offset(l.x + (dx * cos(rad) - dy * sin(rad)).toFloat(), l.y + (dx * sin(rad) + dy * cos(rad)).toFloat())
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(scale, origin, interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!interactive) return@awaitEachGesture // panel otwarty — gesty wyłączone
                        // Uchwyty zaznaczonej warstwy (makieta): ✕ na lewym górnym rogu usuwa, kółko na prawym dolnym
                        // obraca i skaluje JEDNYM palcem (jak w edytorach w telefonie).
                        val grab = 26.dp.toPx()
                        val sel = currentSelected?.let { id -> currentBoard().layers.firstOrNull { it.id == id } }
                        var handle = 0 // 0 = brak, 1 = usuń, 2 = obrót i skala
                        if (sel != null) {
                            val (lw, lh) = renderer.contentSize(sel, blocking = false)
                            val hw = lw * sel.scale / 2f
                            val hh = lh * sel.scale / 2f
                            if ((toScreen(corner(sel, -hw, -hh)) - down.position).getDistance() <= grab) handle = 1
                            else if ((toScreen(corner(sel, hw, hh)) - down.position).getDistance() <= grab) handle = 2
                        }
                        if (sel != null && handle == 1) {
                            down.consume()
                            var up: Offset? = null
                            while (up == null) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) up = down.position
                                else if (!change.pressed) up = change.position
                                change?.consume()
                            }
                            // Usunięcie tylko, gdy palec puszczono nadal na uchwycie (przesunięcie = rezygnacja).
                            if ((up - down.position).getDistance() <= grab) currentOnDelete(sel.id)
                        } else if (sel != null && handle == 2) {
                            down.consume()
                            var begun = false // krok „Cofnij” dopiero przy pierwszym ruchu (samo dotknięcie niczego nie zmienia)
                            val center = toScreen(Offset(sel.x, sel.y))
                            val startVec = down.position - center
                            val startDist = startVec.getDistance().coerceAtLeast(1f)
                            val startAngle = kotlin.math.atan2(startVec.y, startVec.x)
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    pressed = false
                                } else {
                                    val v = change.position - center
                                    val newScale = (sel.scale * v.getDistance() / startDist).coerceIn(0.02f, 30f)
                                    val turn = Math.toDegrees((kotlin.math.atan2(v.y, v.x) - startAngle).toDouble()).toFloat()
                                    if (!begun) {
                                        onGestureStart() // jeden krok „Cofnij” na cały gest
                                        begun = true
                                    }
                                    onUpdate { b -> b.updateLayer(sel.id) { it.copy(scale = newScale, rotation = sel.rotation + turn) } }
                                    change.consume()
                                }
                            }
                        } else {
                        val start = toBoard(down.position)
                        val hit = renderer.hit(currentBoard(), start.x, start.y)
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
                    }
                },
        ) {
            val b = board()           // odczyt stanu w fazie rysowania → zmiana = tylko przerysowanie
            val version = loaded()    // wczytany obraz → wypalić od nowa
            val layers = b.layers
            val activeIndex = layers.indexOfFirst { it.id == selectedId }
            val below = if (activeIndex < 0) layers else layers.subList(0, activeIndex)
            val above = if (activeIndex < 0) emptyList() else layers.subList(activeIndex + 1, layers.size)
            val area = RectF(0f, 0f, b.width.toFloat(), b.height.toFloat())
            val pxW = size.width.toInt()
            val pxH = size.height.toInt()
            val checkerCell = 18.dp.toPx() / scale
            // Tło tablicy bez warstw — klucz spodu (zmiana tła = nowy spód).
            val background = b.copy(layers = emptyList())

            // Rysowanie grupy warstw w układzie tablicy: najpierw całe przygaszone (to, co wystaje), potem w tablicy w pełnym kolorze.
            fun drawGroup(canvas: android.graphics.Canvas, group: List<Layer>, withBackground: Boolean) {
                canvas.save()
                canvas.translate(origin.x, origin.y)
                canvas.scale(scale, scale)
                if (withBackground) {
                    if (b.bgType == Board.BG_TRANSPARENT) drawChecker(canvas, area, checkerCell) // przezroczyste = szachownica
                    canvas.save()
                    canvas.clipRect(area)
                    renderer.drawBackground(canvas, b, blocking = false)
                    canvas.restore()
                }
                group.forEach { renderer.drawLayer(canvas, it, alpha = 90, blocking = false) }
                canvas.save()
                canvas.clipRect(area)
                group.forEach { renderer.drawLayer(canvas, it, blocking = false) }
                canvas.restore()
                canvas.restore()
            }

            drawIntoCanvas { c ->
                val canvas = c.nativeCanvas
                bake.below.draw(canvas, BakeKey(below, background, pxW, pxH, scale, origin, version), pxW, pxH) {
                    drawGroup(it, below, withBackground = true)
                }
                if (activeIndex >= 0) {
                    // Ruszana warstwa — jedyna rysowana na żywo.
                    drawGroup(canvas, listOf(layers[activeIndex]), withBackground = false)
                    bake.above.draw(canvas, BakeKey(above, null, pxW, pxH, scale, origin, version), pxW, pxH) {
                        drawGroup(it, above, withBackground = false)
                    }
                }
                canvas.save()
                canvas.translate(origin.x, origin.y)
                canvas.scale(scale, scale)
                // Ramka zaznaczenia (obrócona razem z warstwą).
                if (activeIndex >= 0) {
                    val layer = layers[activeIndex]
                    val (lw, lh) = renderer.contentSize(layer, blocking = false)
                    canvas.save()
                    canvas.translate(layer.x, layer.y)
                    canvas.rotate(layer.rotation)
                    val hw = lw * layer.scale / 2f
                    val hh = lh * layer.scale / 2f
                    canvas.drawRect(RectF(-hw, -hh, hw, hh), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 2.dp.toPx() / scale
                        color = selection.toArgb()
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10.dp.toPx() / scale, 6.dp.toPx() / scale), 0f)
                    })
                    // Uchwyty: ✕ (lewy górny róg) i obrót/skala (prawy dolny). Stały rozmiar na ekranie (÷ scale).
                    val r = 15.dp.toPx() / scale
                    val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 2.dp.toPx() / scale
                        color = 0xFF0E0F12.toInt()
                    }
                    val glyph = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 2.5f.dp.toPx() / scale
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        color = 0xFF0E0F12.toInt()
                    }
                    canvas.drawCircle(-hw, -hh, r, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = deleteColor.toArgb() })
                    canvas.drawCircle(-hw, -hh, r, ring)
                    val g = r * 0.4f
                    canvas.drawLine(-hw - g, -hh - g, -hw + g, -hh + g, glyph)
                    canvas.drawLine(-hw + g, -hh - g, -hw - g, -hh + g, glyph)
                    canvas.drawCircle(hw, hh, r, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = selection.toArgb() })
                    canvas.drawCircle(hw, hh, r, ring)
                    val a = r * 0.5f
                    canvas.drawArc(RectF(hw - a, hh - a, hw + a, hh + a), -60f, 270f, false, glyph) // strzałka obrotu
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
            // Zmiana stanu nie w trakcie rysowania, tylko w następnej kolejce głównego wątku.
            if (renderer.previewMiss) {
                renderer.previewMiss = false
                mainHandler.post { currentOnMiss() }
            }
        }
    }
}

private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

// Co jest na wypalonym obrazie — gdy klucz się nie zmienił, obraz jest aktualny (data class = porównanie wartości).
private data class BakeKey(
    val layers: List<Layer>,
    val background: Board?, // tablica bez warstw (tło) albo null dla warstw "nad"
    val width: Int,
    val height: Int,
    val scale: Float,
    val origin: Offset,
    val loaded: Int,
)

// Jeden wypalony obraz płótna (zwykła klasa, nie stan Compose — zmienia się tylko w fazie rysowania).
private class BakedLayer {
    private var key: BakeKey? = null
    private var bitmap: Bitmap? = null

    fun draw(target: android.graphics.Canvas, newKey: BakeKey, width: Int, height: Int, paint: (android.graphics.Canvas) -> Unit) {
        if (width <= 0 || height <= 0) return
        if (newKey.layers.isEmpty() && newKey.background == null) return // nic do narysowania
        var bmp = bitmap
        if (newKey != key || bmp == null) {
            // Ten sam rozmiar = czyścimy i rysujemy w starej bitmapie (bez nowej alokacji kilku MB).
            if (bmp == null || bmp.width != width || bmp.height != height) {
                bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap = bmp
            } else {
                bmp.eraseColor(android.graphics.Color.TRANSPARENT)
            }
            paint(android.graphics.Canvas(bmp))
            key = newKey
        }
        target.drawBitmap(bmp, 0f, 0f, null)
    }

    fun release() {
        bitmap = null // bez recycle(): płótno mogło już zlecić rysowanie tej bitmapy w tej klatce
        key = null
    }
}

private class SceneBake {
    val below = BakedLayer()
    val above = BakedLayer()
    fun release() {
        below.release()
        above.release()
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
        title = { Text(stringResource(R.string.som_board_sticker_dialog_title)) },
        text = {
            val list = files
            when {
                list == null -> Text(stringResource(R.string.som_board_loading))
                list.isEmpty() -> Text(stringResource(R.string.som_board_library_empty))
                else -> LazyVerticalGrid(columns = GridCells.Adaptive(80.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.absolutePath }) { file ->
                        // Te same miniatury co w bibliotece (wspólna pamięć podręczna), max 3 dekodowania naraz.
                        val initial = remember(file) { StickerLibrary.cachedThumbnail(file) }
                        val image by produceState(initial, file) {
                            if (value == null) value = withContext(StickerLibrary.thumbDispatcher) { StickerLibrary.thumbnail(context, file) }
                        }
                        Box(
                            Modifier
                                .padding(4.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPick(file) }
                                .padding(6.dp),
                        ) { image?.let { Image(it, contentDescription = stringResource(R.string.som_lib_sticker), modifier = Modifier.fillMaxSize()) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.som_close)) } },
    )
}

@Composable
private fun Swatches(colors: List<Long>, selected: Long?, onPick: (Long) -> Unit, allowNone: Boolean = false, onNone: () -> Unit = {}) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        if (allowNone) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(if (selected == null) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable(onClick = onNone),
            ) { Text("∅", style = MaterialTheme.typography.labelMedium) }
        }
        colors.forEach { c ->
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
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
        title = { Text(stringResource(R.string.som_board_emoji_title)) },
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
                    stringResource(R.string.som_board_emoji_hint),
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
            }) { Text(stringResource(R.string.som_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.som_close)) } },
    )
}

// Tło tablicy: przezroczyste, kolor, gradient albo zdjęcie — zmiany od razu na tablicy.
@Composable
private fun BackgroundPanel(board: Board, onChange: (Board) -> Unit, onPhoto: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        StudioChip(stringResource(R.string.som_board_bg_transparent), board.bgType == Board.BG_TRANSPARENT) { onChange(board.copy(bgType = Board.BG_TRANSPARENT)) }
        StudioChip(stringResource(R.string.som_board_bg_photo), board.bgType == Board.BG_IMAGE) { onPhoto() }
    }
    StudioLabel(stringResource(R.string.som_board_color))
    Swatches(Palette, board.bgColor.takeIf { board.bgType == Board.BG_COLOR }, { onChange(board.copy(bgType = Board.BG_COLOR, bgColor = it)) })
    StudioLabel(stringResource(R.string.som_board_gradient))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Gradients.forEach { (a, b) ->
            val sel = board.bgType == Board.BG_GRADIENT && board.bgColor == a && board.bgColor2 == b
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(a), Color(b))))
                    .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable { onChange(board.copy(bgType = Board.BG_GRADIENT, bgColor = a, bgColor2 = b)) },
            )
        }
    }
}

// Ramka zdjęcia / naklejki na tablicy (polaroid, instax, kontur…) i jej kolor.
@Composable
private fun FramePanel(layer: Layer, onChange: (Layer) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Layer.FRAMES.forEach { (key, label) -> StudioChip(label, layer.frame == key) { onChange(layer.copy(frame = key)) } }
    }
    if (Layer.hasColor(layer.frame)) {
        StudioLabel(stringResource(R.string.som_board_frame_color))
        Swatches(Palette, layer.frameColor, { onChange(layer.copy(frameColor = it)) })
    }
}

@Composable
private fun ExportDialog(onSticker: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.som_board_save_as)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.som_board_export_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSticker) { Text(stringResource(R.string.som_board_export_sticker)) }
                TextButton(onClick = onGallery) { Text(stringResource(R.string.som_board_export_gallery)) }
                Text(
                    stringResource(R.string.som_board_export_wallpaper_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.som_close)) } },
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
        title = { Text(stringResource(R.string.som_board_new_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                presets.forEach { p ->
                    TextButton(onClick = {
                        onCreate(Board(name = p.label, width = p.width, height = p.height, bgType = if (p.transparent) Board.BG_TRANSPARENT else Board.BG_COLOR))
                    }) { Text(stringResource(R.string.som_board_preset_chip, p.label, p.width, p.height)) }
                }
                Text(stringResource(R.string.som_board_custom_size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = customW, onValueChange = { customW = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.som_board_width)) }, modifier = Modifier.width(96.dp))
                    Text("  ×  ")
                    OutlinedTextField(value = customH, onValueChange = { customH = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.som_board_height)) }, modifier = Modifier.width(96.dp))
                }
                TextButton(onClick = {
                    val w = customW.toIntOrNull()?.coerceIn(128, 5000) ?: 1500
                    val h = customH.toIntOrNull()?.coerceIn(128, 5000) ?: 1500
                    onCreate(Board(name = context.getString(R.string.som_board_custom_name, w, h), width = w, height = h, bgType = Board.BG_COLOR))
                }) { Text(stringResource(R.string.som_board_create_custom)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.som_cancel)) } },
    )
}
