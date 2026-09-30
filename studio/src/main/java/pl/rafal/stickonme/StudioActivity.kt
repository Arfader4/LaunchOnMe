package pl.rafal.stickonme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// Kolory StickOnMe: ciemne tło jak w LaunchOnMe, akcent z tęczowego logo.
private val StudioColors = darkColorScheme(
    primary = Color(0xFF9B5CF6),
    onPrimary = Color.White,
    secondary = Color(0xFF2FD9B4),
    background = Color(0xFF101214),
    surface = Color(0xFF1B1E22),
    surfaceVariant = Color(0xFF262A30),
    onSurface = Color(0xFFECEEF0),
    onSurfaceVariant = Color(0xFFA9B0B8),
    outline = Color(0xFF3A4048),
)

// Znacznik ekranu biblioteki w AnimatedContent (pozostałe ekrany to po prostu otwarta tablica albo edytor).
private object LibraryScreenKey
private object DirectLoadingKey

// Studio naklejek. Otwarte z szuflady: biblioteka + edytor. Otwarte z launchera (EXTRA_PICK):
// wybór gotowej naklejki albo nowa — a wynik (ścieżka pliku) wraca do launchera.
class StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pickMode = intent.getBooleanExtra(StickOnMe.EXTRA_PICK, false)
        val wallpaperMode = intent.getBooleanExtra(StickOnMe.EXTRA_WALLPAPER, false)
        val start = StudioStart(
            editPath = intent.getStringExtra(StickOnMe.EXTRA_EDIT),
            photoPath = intent.getStringExtra(StickOnMe.EXTRA_PHOTO),
            shape = intent.getStringExtra(StickOnMe.EXTRA_SHAPE),
            frame = intent.getStringExtra(StickOnMe.EXTRA_FRAME),
        )
        setContent {
            MaterialTheme(colorScheme = StudioColors) {
                StudioApp(
                    pickMode = pickMode,
                    wallpaperMode = wallpaperMode,
                    start = start,
                    onPicked = { file ->
                        setResult(Activity.RESULT_OK, Intent().putExtra(StickOnMe.EXTRA_PATH, file.absolutePath))
                        finish()
                    },
                    onExit = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }
}

// Z czym studio startuje, gdy otwiera je launcher: poprawka naklejki z karty albo zdjęcie do wycięcia.
private data class StudioStart(val editPath: String?, val photoPath: String?, val shape: String?, val frame: String?) {
    val direct get() = editPath != null || photoPath != null
}

@Composable
private fun StudioApp(pickMode: Boolean, wallpaperMode: Boolean, start: StudioStart, onPicked: (File) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CutoutEditor?>(null) }
    var board by remember { mutableStateOf<Board?>(null) }       // otwarta tablica
    var newBoardOpen by remember { mutableStateOf(false) }
    // Tu, nie w bibliotece: po zamknięciu tablicy wracamy na „Tablice”. Wybór tapety startuje od razu na tablicach.
    var libraryTab by remember { mutableIntStateOf(if (wallpaperMode) 1 else 0) }
    var peel by remember { mutableStateOf<ImageBitmap?>(null) } // animacja "odklejenia" po zapisie
    fun peelFrom(file: File) {
        scope.launch { peel = withContext(Dispatchers.IO) { StickerLibrary.thumbnail(file, 512) } }
    }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) } // zmiana = biblioteka czytana od nowa
    // Z launchera: "Edytuj w StickOnMe" (gotowa naklejka) albo "Udostępnij → naklejka" (zdjęcie) — od razu edytor.
    LaunchedEffect(start) {
        val path = start.editPath ?: start.photoPath
        if (path != null) {
            val image = StickerLibrary.decodeFile(path)
            if (image == null) {
                onExit()
            } else {
                // Przygotowanie maski z przezroczystości to pętla po pikselach — poza wątkiem UI.
                editing = withContext(Dispatchers.Default) {
                    val ready = start.editPath != null || StickerLibrary.hasTransparency(image)
                    CutoutEditor(image, fromSticker = ready).apply {
                        start.shape?.let { shape = StickerShapes.of(it).id }
                        start.frame?.let { f -> if (Layer.FRAMES.any { it.first == f }) frame = f }
                    }
                }
            }
        }
    }
    // Menu naklejki z biblioteki: "Edytuj" i "Na nową tablicę".
    fun editFromLibrary(file: File) {
        scope.launch {
            loading = true
            val image = StickerLibrary.decodeFile(file.absolutePath)
            if (image != null) editing = withContext(Dispatchers.Default) { CutoutEditor(image, fromSticker = true) }
            loading = false
        }
    }
    fun boardFromSticker(file: File) {
        scope.launch {
            loading = true
            val path = BoardStore.copyAsset(context, file) ?: file.absolutePath
            val size = withContext(Dispatchers.IO) {
                runCatching { android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { android.graphics.BitmapFactory.decodeFile(path, it) } }
                    .getOrNull()?.let { maxOf(it.outWidth, it.outHeight) }?.takeIf { it > 0 } ?: 1024
            }
            board = Board(
                name = "Kolaż", width = 1080, height = 1080, bgType = Board.BG_COLOR,
                layers = listOf(Layer(kind = Layer.KIND_IMAGE, path = path, x = 540f, y = 540f, scale = 1080f * 0.6f / size)),
            )
            libraryTab = 1
            loading = false
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            loading = true
            val photo = StickerLibrary.decode(context, uri)
            loading = false
            if (photo != null) editing = CutoutEditor(photo)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        // Ekrany studia przechodzą płynnie: w głąb (edytor, tablica) wjeżdżają z prawej, powrót do biblioteki
        // wysuwa je w prawo, a biblioteka "wraca" z lekkiego pomniejszenia (jak nawigacja w systemie).
        // Otwarte prosto z launchera: do chwili wczytania naklejki pusty ekran, a nie mignięcie biblioteki.
        val screen: Any = board ?: editing ?: if (start.direct) DirectLoadingKey else LibraryScreenKey
        AnimatedContent(
            targetState = screen,
            contentKey = { s ->
                when (s) {
                    is Board -> "board:" + s.id
                    is CutoutEditor -> "editor"
                    DirectLoadingKey -> "loading"
                    else -> "library"
                }
            },
            transitionSpec = {
                if (targetState !== LibraryScreenKey) {
                    (slideInHorizontally(tween(320)) { it / 6 } + fadeIn(tween(220))) togetherWith
                        (fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.96f))
                } else {
                    (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith
                        (slideOutHorizontally(tween(260)) { it / 6 } + fadeOut(tween(200)))
                }
            },
            label = "ekran studia",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
        when (current) {
        is Board -> {
            BoardEditorScreen(
                initial = current,
                // Wybór tapety dla launchera: gotowa tablica jako obraz (pełna rozdzielczość tablicy, max 2560 px).
                onWallpaper = if (!wallpaperMode) null else { finished ->
                    scope.launch {
                        loading = true
                        val file = withContext(Dispatchers.IO) {
                            runCatching {
                                val image = BoardRenderer().render(finished, 2560)
                                // Tapeta nie może być przezroczysta (system pokazałby czarne dziury) — kładziemy obraz
                                // na kolorze tablicy, a zapisujemy jako JPEG (mniejszy plik, i tak bez przezroczystości).
                                val opaque = android.graphics.Bitmap.createBitmap(image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888)
                                android.graphics.Canvas(opaque).apply {
                                    drawColor(finished.bgColor.toInt() or 0xFF000000.toInt())
                                    drawBitmap(image, 0f, 0f, null)
                                }
                                File(context.cacheDir, "board_wallpaper.jpg").also { f ->
                                    f.outputStream().use { opaque.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                                }
                            }.getOrNull()
                        }
                        loading = false
                        if (file != null) onPicked(file)
                    }
                },
                onClose = {
                    board = null
                    refresh++
                },
                onStickerSaved = { file ->
                    refresh++
                    if (pickMode && !wallpaperMode) onPicked(file) else peelFrom(file) // wybór tapety nie może oddać naklejki
                },
            )
        }
        is CutoutEditor -> {
            // Otwarte prosto z launchera: "Anuluj" wraca do launchera, a nie do biblioteki.
            val cancel: () -> Unit = { if (start.direct) onExit() else editing = null }
            val back = predictiveBackProgress { cancel() }
            Box(Modifier.fillMaxSize().backPreview(back)) {
                EditorScreen(
                    editor = current,
                    onCancel = cancel,
                    onSaved = { file ->
                        editing = null
                        refresh++
                        if (pickMode && !wallpaperMode) onPicked(file) else peelFrom(file)
                    },
                )
            }
        }
        DirectLoadingKey -> Box(Modifier.fillMaxSize()) // chwila wczytywania (kółko postępu rysuje się niżej)
        else -> {
            LibraryScreen(
                pickMode = pickMode,
                wallpaperMode = wallpaperMode,
                refresh = refresh,
                onNew = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onPick = onPicked,
                onNewBoard = { newBoardOpen = true },
                onOpenBoard = { board = it },
                onEdit = { editFromLibrary(it) },
                onToBoard = { boardFromSticker(it) },
                tab = libraryTab,
                onTab = { libraryTab = it },
            )
        }
        }
        }
        // Zapisana naklejka "odkleja się" i odlatuje do biblioteki.
        peel?.let { image -> key(image) { PeelAway(image) { peel = null } } } // nowa naklejka = animacja od zera
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
    if (newBoardOpen) {
        NewBoardDialog(
            onCreate = {
                newBoardOpen = false
                board = it
            },
            onDismiss = { newBoardOpen = false },
        )
    }
}

// --- Biblioteka ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryScreen(
    pickMode: Boolean,
    wallpaperMode: Boolean,
    refresh: Int,
    onNew: () -> Unit,
    onPick: (File) -> Unit,
    onNewBoard: () -> Unit,
    onOpenBoard: (Board) -> Unit,
    onEdit: (File) -> Unit,
    onToBoard: (File) -> Unit,
    tab: Int,              // 0 = naklejki, 1 = tablice (kolaże, tapety, sklejone naklejki)
    onTab: (Int) -> Unit,
) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val files by produceState(emptyList<File>(), refresh, version) {
        value = withContext(Dispatchers.IO) { StickerLibrary.list(context) }
    }
    var menuFor by remember { mutableStateOf<File?>(null) }
    val openedAt = remember(tab) { System.currentTimeMillis() } // start "fali" miniatur (też po zmianie zakładki)
    var toDelete by remember { mutableStateOf<File?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_stickonme_foreground), contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("StickOnMe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        wallpaperMode -> "Wybierz tablicę na tapetę trybu (albo zrób nową) i dotknij „✓ Na tapetę”"
                        pickMode -> "Wybierz naklejkę dla karty albo zrób nową"
                        else -> "Twoje naklejki"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = if (tab == 0) onNew else onNewBoard) { Text(if (tab == 0) "+ Nowa" else "+ Tablica") }
        }
        Spacer(Modifier.height(12.dp))
        if (!wallpaperMode) { // tapeta = tylko tablice
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = tab == 0, onClick = { onTab(0) }, label = { Text("Naklejki") })
                FilterChip(selected = tab == 1, onClick = { onTab(1) }, label = { Text("Tablice") })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (tab == 1) {
            BoardsGrid(refresh = refresh, onOpen = onOpenBoard)
        } else if (files.isEmpty()) {
            Text(
                "Jeszcze nie masz naklejek. Dotknij „+ Nowa”, wybierz zdjęcie — obiekt wytnie się sam, a pędzlem poprawisz szczegóły.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(files, key = { _, f -> f.absolutePath }) { index, file ->
                    val image by produceState<ImageBitmap?>(null, file) {
                        value = withContext(Dispatchers.IO) { StickerLibrary.thumbnail(file) }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .studioStaggerIn(index, openedAt)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .combinedClickable(
                                // Wybór dla launchera: dotknięcie = wybierz. Inaczej dotknięcie otwiera menu naklejki.
                                onClick = { if (pickMode) onPick(file) else menuFor = file },
                                onLongClick = { menuFor = file },
                            )
                            .padding(8.dp),
                    ) {
                        image?.let { Image(it, contentDescription = "Naklejka", modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
        }
    }

    menuFor?.let { file ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Naklejka") },
            text = {
                Column {
                    if (pickMode) TextButton(onClick = { menuFor = null; onPick(file) }) { Text("✓ Wybierz na kartę") }
                    TextButton(onClick = { menuFor = null; onEdit(file) }) { Text("✎ Edytuj (zapisze nową wersję)") }
                    TextButton(onClick = { menuFor = null; onToBoard(file) }) { Text("▦ Na nową tablicę") }
                    TextButton(onClick = {
                        menuFor = null
                        runCatching { StickerLibrary.share(context, file) }
                    }) { Text("↗ Udostępnij (czat, poczta…)") }
                    TextButton(onClick = { menuFor = null; toDelete = file }) { Text("✕ Usuń", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Zamknij") } },
        )
    }

    toDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Usunąć naklejkę?") },
            text = { Text("Naklejki już położone na kartach zostają (to ich kopie).") },
            confirmButton = {
                TextButton(onClick = {
                    StickerLibrary.delete(file)
                    toDelete = null
                    version++
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Anuluj") } },
        )
    }
}

// Tablice: miniatury zapisanych kolaży / tapet / sklejonych naklejek. Dotknięcie otwiera, przytrzymanie usuwa.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardsGrid(refresh: Int, onOpen: (Board) -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val boards by produceState<List<Board>?>(null, refresh, version) {
        value = withContext(Dispatchers.IO) { BoardStore.list(context) }
    }
    var toDelete by remember { mutableStateOf<Board?>(null) }
    val openedAt = remember { System.currentTimeMillis() }
    val list = boards
    when {
        list == null -> Unit
        list.isEmpty() -> Text(
            "Tablica to płótno na kolaż, tapetę albo kilka naklejek sklejonych w jedną. Dotknij „+ Tablica” i wybierz rozmiar.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(list, key = { _, b -> b.id }) { index, board ->
                val thumb by produceState<ImageBitmap?>(null, board.id, refresh) {
                    value = withContext(Dispatchers.IO) {
                        BoardStore.thumbnailFile(context, board.id).takeIf { it.exists() }?.let { StickerLibrary.thumbnail(it) }
                    }
                }
                Column(
                    Modifier
                        .studioStaggerIn(index, openedAt)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .combinedClickable(onClick = { onOpen(board) }, onLongClick = { toDelete = board })
                        .padding(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(board.width.toFloat() / board.height), contentAlignment = Alignment.Center) {
                        thumb?.let { Image(it, contentDescription = board.name, modifier = Modifier.fillMaxSize()) }
                    }
                    Text(board.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
    toDelete?.let { board ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Usunąć tablicę?") },
            text = { Text("Zapisane z niej naklejki i obrazy w Galerii zostają.") },
            confirmButton = {
                TextButton(onClick = {
                    BoardStore.delete(context, board)
                    toDelete = null
                    version++
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Anuluj") } },
        )
    }
}

// --- Edytor ---

private enum class Tool(val label: String) {
    ADD("Dodaj"), ERASE("Usuń"), CROP("Kadr"), OVAL("Owal"), MOVE("Przesuń");
    val paints get() = this == ADD || this == ERASE
    val crops get() = this == CROP || this == OVAL
}

@Composable
private fun EditorScreen(editor: CutoutEditor, onCancel: () -> Unit, onSaved: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) } // 0 = wycinanie, 1 = obrót i rozmiar
    var tool by remember { mutableStateOf(Tool.ADD) }
    var brush by remember { mutableFloatStateOf(36f) } // średnica pędzla w dp ekranu
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    // Zakładka "Wykończenie" pokazuje GOTOWĄ naklejkę (krawędź, kształt, ramka, obrót) — liczoną w tle
    // chwilę po ostatniej zmianie; kolejna zmiana anuluje poprzednie liczenie (ensureActive w pętlach).
    var resultPreview by remember(editor) { mutableStateOf<ImageBitmap?>(null) }
    var previewing by remember { mutableStateOf(false) }
    LaunchedEffect(
        editor, tab, editor.edgeSmooth, editor.edgeShrink, editor.version,
        editor.shape, editor.frame, editor.frameColor, // obrót nie — podgląd obraca się przy rysowaniu
    ) {
        if (tab == 1) {
            previewing = true
            try {
                kotlinx.coroutines.delay(150) // suwak jeszcze jedzie
                val image = withContext(Dispatchers.Default) { runCatching { editor.render(rotate = false) { ensureActive() } }.getOrNull() }
                resultPreview = image?.asImageBitmap()
            } finally {
                previewing = false
            }
        }
    }

    // Na start: automatyczne wycięcie obiektu (ML Kit). Brak obiektu → zostaje całe zdjęcie, poprawiasz pędzlem.
    LaunchedEffect(editor) {
        if (editor.fromSticker) { // poprawka gotowej naklejki — maska już jest (z jej przezroczystości)
            busy = false
            return@LaunchedEffect
        }
        val result = runCatching { autoMask(editor.photo) }.getOrNull()
        if (result != null) editor.applyConfidence(result.first, result.second, result.third)
        else message = "Nie udało się wyciąć obiektu automatycznie — zaznacz go pędzlem („Usuń” zdejmuje tło)."
        busy = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onCancel) { Text("Anuluj") }
            Spacer(Modifier.weight(1f))
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Wycinanie") })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Wykończenie") })
            Spacer(Modifier.weight(1f))
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            // Gest jest wyłączony (busy), więc maska się nie zmienia w trakcie liczenia.
                            val sticker = runCatching { withContext(Dispatchers.Default) { editor.render() } }.getOrNull()
                            if (sticker == null) {
                                message = "Naklejka jest pusta — dodaj coś pędzlem (albo za mało pamięci)."
                            } else {
                                onSaved(StickerLibrary.save(context, sticker))
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Zapisz") }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            if (tab == 0) {
                MaskCanvas(editor, tool = tool, brushDp = brush, rotation = 0f, enabled = !busy)
            } else {
                ResultPreview(resultPreview, editor.rotation)
            }
            if (busy || (tab == 1 && previewing && resultPreview == null)) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }

        Column(
            Modifier
                .heightIn(max = 320.dp) // wiele ustawień → przewijane, podgląd zostaje duży
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tab == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Tool.entries.forEach { t -> FilterChip(selected = tool == t, onClick = { tool = t }, label = { Text(t.label) }) }
                    TextButton(enabled = editor.canUndo, onClick = { editor.undoLast() }) { Text("↶ Cofnij") }
                    TextButton(onClick = {
                        busy = true
                        scope.launch {
                            val result = runCatching { autoMask(editor.photo) }.getOrNull()
                            if (result != null) editor.applyConfidence(result.first, result.second, result.third)
                            busy = false
                        }
                    }) { Text("Auto") }
                    TextButton(onClick = { editor.fill(true) }) { Text("Całe zdjęcie") }
                }
                if (tool.crops) {
                    Text(
                        "Przeciągnij palcem ramkę — wszystko poza nią zniknie (↶ Cofnij przywraca). Dwa palce = przybliż i przesuń.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Pędzel: ${brush.roundToInt()} dp · dwa palce = przybliż i przesuń", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = brush, onValueChange = { brush = it }, valueRange = 8f..96f)
                }
            } else {
                Text("Obrót: ${editor.rotation.roundToInt()}°", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { editor.rotation = ((editor.rotation - 90f + 540f) % 360f) - 180f }) { Text("↺ 90°") }
                    Slider(
                        value = editor.rotation,
                        onValueChange = { editor.rotation = it },
                        valueRange = -180f..180f,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { editor.rotation = ((editor.rotation + 90f + 540f) % 360f) - 180f }) { Text("↻ 90°") }
                }
                Text("Wygładzenie krawędzi: ${editor.edgeSmooth.roundToInt()} px", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = editor.edgeSmooth, onValueChange = { editor.edgeSmooth = it }, valueRange = 0f..12f)
                Text("Zwężenie (usuwa resztki tła przy brzegu): ${editor.edgeShrink.roundToInt()} px", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = editor.edgeShrink, onValueChange = { editor.edgeShrink = it }, valueRange = 0f..8f)
                Text("Kształt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    StickerShapes.all.forEach { s -> FilterChip(selected = editor.shape == s.id, onClick = { editor.shape = s.id }, label = { Text(s.label) }) }
                }
                Text("Ramka", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Layer.FRAMES.forEach { (key, label) -> FilterChip(selected = editor.frame == key, onClick = { editor.frame = key }, label = { Text(label) }) }
                }
                if (Layer.hasColor(editor.frame)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FrameColors.forEach { c ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(if (editor.frameColor == c) 3.dp else 1.dp, if (editor.frameColor == c) MaterialTheme.colorScheme.secondary else Color(0x55FFFFFF), CircleShape)
                                    .clickable { editor.frameColor = c },
                            )
                        }
                    }
                }
                Text("Rozmiar naklejki", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(512 to "Mała", 768 to "Średnia", 1024 to "Duża").forEach { (side, label) ->
                        FilterChip(selected = editor.outputSide == side, onClick = { editor.outputSide = side }, label = { Text("$label · $side px") })
                    }
                }
            }
        }
    }
}

// Podgląd z maską: przygaszone zdjęcie w tle (widać, co można "domalować"), na nim wycięty obiekt na szachownicy.
// Jeden palec = pędzel (albo przesuwanie), dwa palce = przybliżanie i przesuwanie widoku.
@Composable
private fun MaskCanvas(editor: CutoutEditor, tool: Tool, brushDp: Float, rotation: Float, enabled: Boolean, maskOverride: ImageBitmap? = null) {
    val photo = remember(editor) { editor.photo.asImageBitmap() }
    val mask = remember(editor) { editor.mask.asImageBitmap() }
    var zoom by remember(editor) { mutableFloatStateOf(1f) }
    var pan by remember(editor) { mutableStateOf(Offset.Zero) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var cropFrom by remember { mutableStateOf<Offset?>(null) } // ramka kadru w trakcie przeciągania (ekran)
    var cropTo by remember { mutableStateOf<Offset?>(null) }
    val checkA = Color(0xFF2A2E34)
    val checkB = Color(0xFF1E2126)
    val cursorColor = MaterialTheme.colorScheme.secondary

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val base = minOf(w / photo.width, h / photo.height) * 0.92f
        fun scale() = base * zoom
        // Lewy górny róg zdjęcia na ekranie (wyśrodkowane + przesunięcie palcami).
        fun origin() = Offset((w - photo.width * scale()) / 2f, (h - photo.height * scale()) / 2f) + pan
        fun toImage(p: Offset) = (p - origin()) / scale()
        val brushPx = brushDp * (LocalContext.current.resources.displayMetrics.density)

        Canvas(
            Modifier
                .fillMaxSize()
                // Klucze w, h: gdy płótno zmieni rozmiar (np. pojawi się komunikat), gest dostaje świeżą geometrię.
                .pointerInput(editor, tool, brushPx, w, h, enabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!enabled) return@awaitEachGesture // trwa wycinanie albo zapis — maski nie ruszamy
                        var last = down.position
                        var painting = tool.paints
                        var cropping = tool.crops
                        if (cropping) {
                            cropFrom = down.position
                            cropTo = down.position
                        }
                        var started = false // pierwszy ślad pędzla dopiero przy ruchu albo puszczeniu (nie przy starcie szczypania)
                        cursor = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                val from = cropFrom
                                val to = cropTo
                                if (cropping && from != null && to != null) {
                                    val a = toImage(from)
                                    val b = toImage(to)
                                    val left = minOf(a.x, b.x).coerceIn(0f, photo.width.toFloat())
                                    val right = maxOf(a.x, b.x).coerceIn(0f, photo.width.toFloat())
                                    val top = minOf(a.y, b.y).coerceIn(0f, photo.height.toFloat())
                                    val bottom = maxOf(a.y, b.y).coerceIn(0f, photo.height.toFloat())
                                    // Za mała ramka = przypadkowe dotknięcie, nic nie wycinamy.
                                    if (right - left > 8f && bottom - top > 8f) editor.cropTo(left, top, right, bottom, oval = tool == Tool.OVAL)
                                }
                                cropFrom = null
                                cropTo = null
                                // Samo dotknięcie jednym palcem = kropka pędzla.
                                if (painting && !started) {
                                    editor.snapshot()
                                    val a = toImage(down.position)
                                    editor.stroke(a.x, a.y, a.x, a.y, brushPx / scale(), tool == Tool.ADD)
                                }
                                break
                            }
                            if (pressed.size >= 2) {
                                // Dwa palce: przybliżanie wokół środka palców + przesuwanie. Do końca tego gestu już nie malujemy.
                                painting = false
                                cropping = false
                                cropFrom = null
                                cropTo = null
                                val previous = event.calculateCentroid(useCurrent = false)
                                val current = event.calculateCentroid(useCurrent = true)
                                val imagePoint = toImage(previous)   // punkt obrazu pod palcami PRZED zmianą
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 8f)
                                pan += current - (origin() + imagePoint * scale()) // ten punkt jedzie z palcami
                                cursor = null
                                last = pressed.first().position
                            } else {
                                val c = pressed.first()
                                if (painting) {
                                    if (!started) editor.snapshot() // jeden krok "Cofnij" na całe pociągnięcie
                                    started = true
                                    val a = toImage(last)
                                    val b = toImage(c.position)
                                    editor.stroke(a.x, a.y, b.x, b.y, brushPx / scale(), tool == Tool.ADD)
                                } else if (cropping) {
                                    cropTo = c.position
                                } else if (tool == Tool.MOVE) {
                                    pan += c.position - last
                                }
                                last = c.position
                                cursor = c.position
                            }
                            event.changes.forEach { it.consume() }
                        }
                        cursor = null
                        cropFrom = null
                        cropTo = null
                    }
                },
        ) {
            @Suppress("UNUSED_VARIABLE")
            val maskVersion = editor.version // odczyt = przerysowanie po każdej zmianie maski
            val s = scale()
            val o = origin()
            val dst = IntSize((photo.width * s).roundToInt(), (photo.height * s).roundToInt())
            val dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt())
            rotate(rotation, pivot = Offset(o.x + dst.width / 2f, o.y + dst.height / 2f)) {
                checkerboard(Rect(o, androidx.compose.ui.geometry.Size(dst.width.toFloat(), dst.height.toFloat())), checkA, checkB)
                if (rotation == 0f) drawImage(photo, dstOffset = dstOffset, dstSize = dst, alpha = 0.22f)
                // Warstwa: zdjęcie, a potem maska w trybie DstIn (zostaje tylko to, co zamalowane).
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                    drawImage(photo, dstOffset = dstOffset, dstSize = dst)
                    drawImage(maskOverride ?: mask, dstOffset = dstOffset, dstSize = dst, blendMode = BlendMode.DstIn)
                    canvas.restore()
                }
            }
            val from = cropFrom
            val to = cropTo
            if (from != null && to != null) {
                val topLeft = Offset(minOf(from.x, to.x), minOf(from.y, to.y))
                val frameSize = androidx.compose.ui.geometry.Size(kotlin.math.abs(to.x - from.x), kotlin.math.abs(to.y - from.y))
                val dash = Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                if (tool == Tool.OVAL) drawOval(cursorColor, topLeft, frameSize, style = dash)
                else drawRect(cursorColor, topLeft, frameSize, style = dash)
            } else if (tool.paints) {
                cursor?.let { drawCircle(cursorColor, radius = brushPx / 2f, center = it, style = Stroke(width = 2.dp.toPx())) }
            }
        }
    }
}

private val FrameColors = listOf(0xFFFFFFFFL, 0xFF16171AL, 0xFFFFD54FL, 0xFFFF8A80L, 0xFF80D8FFL, 0xFFB9F6CAL, 0xFFE1BEE7L)

// Gotowa naklejka na szachownicy (tak zostanie zapisana).
@Composable
private fun ResultPreview(image: ImageBitmap?, rotation: Float) {
    val checkA = Color(0xFF2A2E34)
    val checkB = Color(0xFF1E2126)
    Canvas(Modifier.fillMaxSize()) {
        checkerboard(Rect(Offset.Zero, size), checkA, checkB)
        if (image != null) {
            val s = minOf(size.width * 0.9f / image.width, size.height * 0.9f / image.height)
            val w = (image.width * s).roundToInt()
            val h = (image.height * s).roundToInt()
            // Obrót tylko przy rysowaniu (tani); zapis obraca naprawdę. Skala 0.9 zostawia miejsce na rogi.
            rotate(rotation, pivot = Offset(size.width / 2f, size.height / 2f)) {
                drawImage(
                    image,
                    dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
                    dstSize = IntSize(w, h),
                )
            }
        }
    }
}

// Szachownica = "tu jest przezroczyście" (jak w programach graficznych).
private fun DrawScope.checkerboard(area: Rect, a: Color, b: Color) {
    val cell = 14.dp.toPx()
    drawRect(a, topLeft = area.topLeft, size = area.size)
    var y = area.top
    var row = 0
    while (y < area.bottom) {
        var x = area.left + if (row % 2 == 0) 0f else cell
        while (x < area.right) {
            drawRect(b, topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(minOf(cell, area.right - x), minOf(cell, area.bottom - y)))
            x += cell * 2
        }
        y += cell
        row++
    }
}
