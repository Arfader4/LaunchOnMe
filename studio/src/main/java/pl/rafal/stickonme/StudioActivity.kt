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
import androidx.annotation.StringRes
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
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
// Pełny zestaw ról kolorów — brakujące role brały domyślne fioletowo-szare wartości Material3, przez co przyciski,
// zaznaczone chipy i obramowania zlewały się z tekstem i tłem. Teraz każda rola ma wyraźny kontrast:
// przyciski tekstowe = jasny fiolet, zaznaczone chipy = turkus, obramowania chipów widoczne, tła neutralne (bez fioletu).
private val StudioColors = darkColorScheme(
    primary = Color(0xFFB794FF),            // przyciski tekstowe, suwaki, "Zapisz" — wyraźnie inne niż biały tekst
    onPrimary = Color(0xFF1C0B3A),
    primaryContainer = Color(0xFF4A2A8A),
    onPrimaryContainer = Color(0xFFEDE2FF),
    secondary = Color(0xFF2FD9B4),
    onSecondary = Color(0xFF00382C),
    secondaryContainer = Color(0xFF0E5A4A), // tło zaznaczonego chipa (narzędzie, kształt, ramka…)
    onSecondaryContainer = Color(0xFFB4FBEA),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF3A2200),
    background = Color(0xFF101214),
    onBackground = Color(0xFFECEEF0),
    surface = Color(0xFF1B1E22),
    onSurface = Color(0xFFECEEF0),
    surfaceVariant = Color(0xFF262A30),
    onSurfaceVariant = Color(0xFFB8C0C8),   // opisy i etykiety suwaków
    surfaceTint = Color(0xFFB794FF),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF16191C),
    surfaceContainer = Color(0xFF1B1E22),
    surfaceContainerHigh = Color(0xFF23272C),
    surfaceContainerHighest = Color(0xFF2E333A), // tło okien dialogowych, nieaktywna część suwaka
    outline = Color(0xFF6E7884),            // obramowanie niezaznaczonych chipów — wcześniej prawie niewidoczne
    outlineVariant = Color(0xFF3A4048),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0905),
)

// Znacznik ekranu biblioteki w AnimatedContent (pozostałe ekrany to po prostu otwarta tablica albo edytor).
private object LibraryScreenKey
private object DirectLoadingKey

// Studio naklejek. Otwarte z szuflady: biblioteka + edytor. Otwarte z launchera (EXTRA_PICK):
// wybór gotowej naklejki albo nowa — a wynik (link content:// do pliku) wraca do launchera.
class StudioActivity : ComponentActivity() {
    // System prosi o oddanie pamięci (aplikacja w tle albo telefonowi brakuje RAM) — miniatury można wczytać ponownie.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) StickerLibrary.trimMemory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StudioText.init(this) // teksty spoza Compose (na wypadek startu bez klasy aplikacji launchera)
        enableEdgeToEdge()
        val pickMode = intent.getBooleanExtra(StickOnMe.EXTRA_PICK, false)
        val wallpaperMode = intent.getBooleanExtra(StickOnMe.EXTRA_WALLPAPER, false)
        // Obraz od launchera przychodzi jako link content:// w intent.data (z prawem odczytu na czas tej aktywności).
        // Z szuflady intent.data jest puste — wtedy zwykła biblioteka. Tylko w trybie wyboru (EXTRA_PICK).
        val startUri = if (pickMode) intent.data else null
        val start = StudioStart(
            uri = startUri,
            edit = intent.getBooleanExtra(StickOnMe.EXTRA_EDIT, false),
            shape = intent.getStringExtra(StickOnMe.EXTRA_SHAPE),
            frame = intent.getStringExtra(StickOnMe.EXTRA_FRAME),
        )
        setContent {
            MaterialTheme(colorScheme = StudioColors) {
              // Bez Surface/Scaffold domyślny kolor tekstu (LocalContentColor) to CZARNY — stąd niewidoczne
              // tytuły i nazwy kart na ciemnym tle. Ustawiamy go raz dla całego studia (jak Foreground w stylu WPF).
              CompositionLocalProvider(LocalContentColor provides StudioColors.onBackground) {
                StudioApp(
                    pickMode = pickMode,
                    wallpaperMode = wallpaperMode,
                    start = start,
                    onPicked = { file ->
                        // Wynik jako link content:// — launcher to inna aplikacja i nie przeczyta naszej ścieżki.
                        val result = runCatching { StickOnMe.resultIntent(this, file) }.getOrNull()
                        setResult(if (result != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
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
}

// Z czym studio startuje, gdy otwiera je launcher: poprawka naklejki z karty albo zdjęcie do wycięcia.
// uri = obraz od launchera; edit = true → gotowa naklejka do poprawki, false → zdjęcie do wycięcia.
private data class StudioStart(val uri: android.net.Uri?, val edit: Boolean, val shape: String?, val frame: String?) {
    val direct get() = uri != null
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
        scope.launch { peel = withContext(Dispatchers.IO) { StickerLibrary.thumbnail(context, file, 512, cache = false) } }
    }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) } // zmiana = biblioteka czytana od nowa
    // Z launchera: "Edytuj w StickOnMe" (gotowa naklejka) albo "Udostępnij → naklejka" (zdjęcie) — od razu edytor.
    LaunchedEffect(start) {
        val uri = start.uri
        if (uri != null) {
            val image = StickerLibrary.decode(context, uri)
            if (image == null) {
                onExit()
            } else {
                // Przygotowanie maski z przezroczystości to pętla po pikselach — poza wątkiem UI.
                editing = withContext(Dispatchers.Default) {
                    val ready = start.edit || StickerLibrary.hasTransparency(image)
                    CutoutEditor(image, fromSticker = ready).apply {
                        start.shape?.let { chooseShape(StickerShapes.of(it).id) } // kształt z karty launchera (koło → kadr 1:1)
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
            // Sam nagłówek pliku (inJustDecodeBounds — bez dekodowania pikseli), potem rozmiar logiczny warstwy (max 1400 px).
            val size = withContext(Dispatchers.IO) {
                runCatching { android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { android.graphics.BitmapFactory.decodeFile(path, it) } }
                    .getOrNull()?.takeIf { it.outWidth > 0 && it.outHeight > 0 }
                    ?.let { maxOf(it.outWidth, it.outHeight) * BoardRenderer.logicalScale(it.outWidth, it.outHeight) } ?: 1024f
            }
            board = Board(
                name = context.getString(R.string.som_board_collage_name), width = 1080, height = 1080, bgType = Board.BG_COLOR,
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
                                // Pełna jakość: osobny renderer eksportu (oryginalne obrazy), wyrzucany po użyciu.
                                val image = BoardRenderer.forExport().render(finished, 2560)
                                // Tapeta nie może być przezroczysta (system pokazałby czarne dziury) — kładziemy obraz
                                // na kolorze tablicy, a zapisujemy jako JPEG (mniejszy plik, i tak bez przezroczystości).
                                val opaque = android.graphics.Bitmap.createBitmap(image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888)
                                android.graphics.Canvas(opaque).apply {
                                    drawColor(finished.bgColor.toInt() or 0xFF000000.toInt())
                                    drawBitmap(image, 0f, 0f, null)
                                }
                                image.recycle() // dwie bitmapy tapety naraz to kilkadziesiąt MB — zwalniamy od razu
                                try {
                                    // cache/out/ — jedyny folder cache udostępniany przez FileProvider (stickonme_paths.xml).
                                    File(File(context.cacheDir, "out").apply { mkdirs() }, "board_wallpaper.jpg").also { f ->
                                        f.outputStream().use { opaque.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                                    }
                                } finally {
                                    opaque.recycle()
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
                        wallpaperMode -> stringResource(R.string.som_lib_hint_wallpaper)
                        pickMode -> stringResource(R.string.som_lib_hint_pick)
                        else -> stringResource(R.string.som_lib_hint_default)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = if (tab == 0) onNew else onNewBoard) { Text(if (tab == 0) stringResource(R.string.som_lib_new_sticker) else stringResource(R.string.som_lib_new_board)) }
        }
        Spacer(Modifier.height(12.dp))
        if (!wallpaperMode) { // tapeta = tylko tablice
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = tab == 0, onClick = { onTab(0) }, label = { Text(stringResource(R.string.som_lib_tab_stickers)) })
                FilterChip(selected = tab == 1, onClick = { onTab(1) }, label = { Text(stringResource(R.string.som_lib_tab_boards)) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (tab == 1) {
            BoardsGrid(refresh = refresh, onOpen = onOpenBoard)
        } else if (files.isEmpty()) {
            Text(
                stringResource(R.string.som_lib_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(files, key = { _, f -> f.absolutePath }) { index, file ->
                    // Najpierw z pamięci (od razu, bez mignięcia pustego kafelka), inaczej w tle — max 3 naraz.
                    val initial = remember(file) { StickerLibrary.cachedThumbnail(file) } // raz, nie w każdej rekompozycji
                    val image by produceState(initial, file) {
                        if (value == null) value = withContext(StickerLibrary.thumbDispatcher) { StickerLibrary.thumbnail(context, file) }
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
                        image?.let { Image(it, contentDescription = stringResource(R.string.som_lib_sticker), modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
        }
    }

    menuFor?.let { file ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.som_lib_sticker)) },
            text = {
                Column {
                    if (pickMode) TextButton(onClick = { menuFor = null; onPick(file) }) { Text(stringResource(R.string.som_lib_menu_pick)) }
                    TextButton(onClick = { menuFor = null; onEdit(file) }) { Text(stringResource(R.string.som_lib_menu_edit)) }
                    TextButton(onClick = { menuFor = null; onToBoard(file) }) { Text(stringResource(R.string.som_lib_menu_to_board)) }
                    TextButton(onClick = {
                        menuFor = null
                        runCatching { StickerLibrary.share(context, file) }
                    }) { Text(stringResource(R.string.som_lib_menu_share)) }
                    TextButton(onClick = { menuFor = null; toDelete = file }) { Text(stringResource(R.string.som_lib_menu_delete), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text(stringResource(R.string.som_close)) } },
        )
    }

    toDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.som_lib_delete_sticker_title)) },
            text = { Text(stringResource(R.string.som_lib_delete_sticker_text)) },
            confirmButton = {
                TextButton(onClick = {
                    StickerLibrary.delete(context, file)
                    toDelete = null
                    version++
                }) { Text(stringResource(R.string.som_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.som_cancel)) } },
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
            stringResource(R.string.som_lib_boards_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(list, key = { _, b -> b.id }) { index, board ->
                val thumbFile = remember(board.id) { BoardStore.thumbnailFile(context, board.id) }
                val initial = remember(thumbFile, refresh) { StickerLibrary.cachedThumbnail(thumbFile) }
                val thumb by produceState(initial, board.id, refresh) {
                    // Po zapisie tablicy plik ma nową datę → nowy klucz → świeża miniatura.
                    value = withContext(StickerLibrary.thumbDispatcher) { StickerLibrary.thumbnail(context, thumbFile) } ?: value
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
            title = { Text(stringResource(R.string.som_lib_delete_board_title)) },
            text = { Text(stringResource(R.string.som_lib_delete_board_text)) },
            confirmButton = {
                TextButton(onClick = {
                    BoardStore.delete(context, board)
                    toDelete = null
                    version++
                }) { Text(stringResource(R.string.som_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.som_cancel)) } },
        )
    }
}

// --- Edytor ---

private const val PREVIEW_WORK_SIDE = 800 // podgląd "Wykończenia" liczony na mniejszej kopii

// Maska ML Kit przygotowana w całości w tle (model, przeliczenie pewności, skalowanie). null = nie wyszło.
// Na wątku UI zostaje tylko szybka podmiana: editor.applyMask(...).
private suspend fun autoMaskFor(editor: CutoutEditor): android.graphics.Bitmap? = try {
    // Limit czasu: ML Kit przy pierwszym użyciu pobiera model — bez sieci potrafi czekać bez końca,
    // a wtedy edytor był zablokowany (przycisk „Zapisz” wyłączony). Po 20 s zostaje całe zdjęcie.
    kotlinx.coroutines.withTimeoutOrNull(20_000L) {
        withContext(Dispatchers.Default) {
            autoMask(editor.photo)?.let { (values, w, h) -> editor.prepareMask(values, w, h) }
        }
    }
} catch (e: kotlin.coroutines.cancellation.CancellationException) {
    throw e // anulowanie (wyjście z edytora) przechodzi dalej — jak OperationCanceledException w C#
} catch (e: Exception) {
    null
}

private enum class Tool(@StringRes val labelRes: Int) {
    ADD(R.string.som_add), ERASE(R.string.som_editor_tool_erase), CROP(R.string.som_editor_tool_crop), OVAL(R.string.som_editor_tool_oval), MOVE(R.string.som_editor_tool_move);
    val paints get() = this == ADD || this == ERASE
    val crops get() = this == CROP || this == OVAL
}

@Composable
private fun EditorScreen(editor: CutoutEditor, onCancel: () -> Unit, onSaved: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Jak w edytorach zdjęć w telefonie: ekran główny = podgląd GOTOWEJ naklejki + pasek narzędzi.
    // Dotknięcie narzędzia otwiera je (section), a ✓ zatwierdza / ✕ anuluje zmiany z tego narzędzia.
    var section by remember(editor) { mutableStateOf<EditSection?>(null) }
    val maskView = section?.maskView == true
    val tab = if (maskView) 0 else 1 // 0 = maska (wycinanie), 1 = gotowa naklejka / kadr
    var brushMode by remember { mutableStateOf(Tool.ERASE) } // start od całego zdjęcia → najpierw zwykle się usuwa
    val tool = if (section == EditSection.BRUSH) brushMode else Tool.MOVE // poza pędzlem palec tylko przesuwa widok
    var brush by remember { mutableFloatStateOf(BRUSH_SIZES[1]) } // średnica pędzla w dp ekranu
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var objectShare by remember(editor) { mutableStateOf<Int?>(null) } // ile % zdjęcia zostaje po „Wytnij obiekt”
    // Podgląd liczony w tle chwilę po ostatniej zmianie; kolejna zmiana anuluje poprzednie liczenie (ensureActive).
    // Dwa etapy: (1) zdjęcie × maska + krawędź → cutPreview (wolniejsze, tylko po zmianie maski / krawędzi; ten obraz
    // pokazuje też ekran kadru), (2) kadr + kształt + ramka z cutPreview → resultPreview (szybkie). Obrót całości
    // nie liczy się w tle — podgląd obraca obraz przy rysowaniu, więc działa na żywo.
    // cutPreview nie zwalniamy ręcznie (recycle): może go jeszcze rysować płótno — zbierze go GC (ok. 2,5 MB).
    var cutPreview by remember(editor) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var resultPreview by remember(editor) { mutableStateOf<ImageBitmap?>(null) }
    var cutting by remember { mutableStateOf(false) }   // etap 1 w toku
    var finishing by remember { mutableStateOf(false) } // etap 2 w toku
    val previewing = cutting || finishing
    val cropping = section == EditSection.CROP
    LaunchedEffect(editor, tab, editor.edgeSmooth, editor.edgeShrink, editor.version) {
        if (tab == 1) {
            cutting = true
            try {
                kotlinx.coroutines.delay(120)
                // Podgląd na kopii do 800 px (zapis i tak liczy w pełnej rozdzielczości).
                // Błąd podglądu pokazujemy (nazwa wyjątku), zamiast po cichu zostawiać stary obraz.
                val cut = try {
                    withContext(Dispatchers.Default) { editor.cutout(workSide = PREVIEW_WORK_SIDE) { ensureActive() } }
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = context.getString(R.string.som_preview_failed, e.javaClass.simpleName)
                    null
                }
                if (cut != null) cutPreview = cut
            } finally {
                cutting = false
            }
        }
    }
    LaunchedEffect(cutPreview, tab, cropping, editor.crop, editor.shape, editor.frame, editor.frameColor, editor.filters) {
        val cut = cutPreview
        if (tab == 1 && !cropping && cut != null) {
            finishing = true
            try {
                kotlinx.coroutines.delay(60)
                val image = try {
                    withContext(Dispatchers.Default) { editor.finish(cut, rotate = false) { ensureActive() } }
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = context.getString(R.string.som_preview_failed, e.javaClass.simpleName)
                    null
                }
                resultPreview = image?.asImageBitmap()
            } finally {
                finishing = false
            }
        }
    }

    // Zamknięcie edytora: kopie robocze od razu do zwolnienia (zdjęcie i maskę może jeszcze rysować animacja wyjścia).
    DisposableEffect(editor) { onDispose { editor.release() } }

    // Na start NIE wycinamy automatycznie — zaczynamy od całego zdjęcia (maska pełna). Wycinanie = narzędzie „Obiekt”.
    LaunchedEffect(editor) { busy = false }

    // „Wytnij obiekt” (ML Kit). W narzędziu „Obiekt” — od razu po wejściu i ponownie na żądanie.
    fun runAuto() {
        busy = true
        message = null
        objectShare = null
        scope.launch {
            val prepared = autoMaskFor(editor)
            if (prepared != null) {
                editor.applyMask(prepared)
                objectShare = withContext(Dispatchers.Default) { editor.coverage() }
            } else {
                message = context.getString(R.string.som_editor_auto_failed)
            }
            busy = false
        }
    }
    // Po narzędziu z maską stary podgląd jest nieaktualny — czyścimy go (kółko zamiast starego obrazu do czasu przeliczenia).
    fun dropStalePreview() {
        if (section?.maskView == true) {
            cutPreview = null
            resultPreview = null
        }
    }
    fun open(s: EditSection) {
        editor.beginStep()
        message = null
        section = s
        if (s == EditSection.OBJECT) runAuto()
    }
    fun applyStep() {
        editor.commitStep()
        dropStalePreview()
        section = null
    }
    fun cancelStep() {
        editor.cancelStep()
        dropStalePreview()
        message = null
        section = null
    }
    // Zapis naklejki („✓ Gotowe” na ekranie głównym).
    fun save() {
        busy = true
        scope.launch {
            try {
                // Gest jest wyłączony (busy), więc maska się nie zmienia w trakcie liczenia.
                val sticker = runCatching { withContext(Dispatchers.Default) { editor.render() } }.getOrNull()
                if (sticker == null) {
                    message = context.getString(R.string.som_editor_empty)
                } else {
                    val file = StickerLibrary.save(context, sticker)
                    sticker.recycle() // już w pliku PNG
                    onSaved(file)
                }
            } finally {
                busy = false
            }
        }
    }
    // Wstecz w narzędziu = ✕ (anuluj krok), a nie wyjście z całego edytora.
    BackHandler(enabled = section != null && !busy) { cancelStep() }

    val original = remember(editor) { editor.photo.asImageBitmap() }
    var comparing by remember { mutableStateOf(false) } // przytrzymany przycisk porównania = widać oryginał
    val compareCd = stringResource(R.string.som_compare_cd)
    val current = section

    Column(Modifier.fillMaxSize()) {
        // Górny pasek. Ekran główny: ✕ | ✓ Gotowe. W narzędziu: nazwa + Cofnij / Ponów (przy masce).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (current == null) {
                StudioIconButton(StudioIcons.Close, stringResource(R.string.som_cancel), outlined = true, onClick = onCancel)
                Spacer(Modifier.weight(1f))
                StudioPrimaryButton(stringResource(R.string.som_done), enabled = !busy && !previewing) { save() }
            } else {
                Text(
                    stringResource(current.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (current.maskView) {
                    // W trakcie wycinania / zapisu maska nie może się zmieniać (czyta ją wątek w tle).
                    StudioIconButton(StudioIcons.Undo, stringResource(R.string.som_undo_cd), enabled = !busy && editor.canUndo) { editor.undoLast() }
                    StudioIconButton(StudioIcons.Redo, stringResource(R.string.som_redo_cd), enabled = !busy && editor.canRedo) { editor.redoLast() }
                }
            }
        }

        // Płótno na prawie cały ekran.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            val cut = cutPreview
            if (tab == 0) {
                MaskCanvas(editor, tool = tool, brushDp = brush, rotation = 0f, enabled = !busy, ghost = section == EditSection.BRUSH)
            } else if (cropping) {
                if (cut != null) CropCanvas(editor, cut)
            } else {
                ResultPreview(resultPreview, editor.rotation)
            }
            if (comparing) {
                Image(
                    original,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                )
            }
            val waiting = busy || (tab == 1 && (if (cropping) cut == null else previewing && resultPreview == null))
            if (waiting) CircularProgressIndicator(Modifier.align(Alignment.Center))

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE60E0F12))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            // Porównanie z oryginałem: przytrzymaj (jak w edytorach zdjęć w telefonie).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xE60E0F12))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .semantics { contentDescription = compareCd }
                    .pointerInput(editor) {
                        detectTapGestures(onPress = {
                            comparing = true
                            tryAwaitRelease()
                            comparing = false
                        })
                    },
            ) {
                Icon(StudioIcons.Compare, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }

        if (current == null) {
            // Ekran główny: pasek narzędzi (przewijany), ikona + podpis.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
            ) {
                EditSection.entries.forEach { s ->
                    StudioRailItem(s.icon, stringResource(s.labelRes), selected = false) { if (!busy) open(s) }
                }
            }
        } else {
            // Opcje narzędzia — bez suwaków: pigułki i kropki do dotknięcia.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 230.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EditorOptions(current, editor, busy, objectShare, brushMode, { brushMode = it }, brush, { brush = it }) { runAuto() }
            }
            StudioStepBar(
                cancelLabel = stringResource(R.string.som_cancel),
                applyLabel = stringResource(R.string.som_apply),
                enabled = !busy,
                onCancel = { cancelStep() },
                onApply = { applyStep() },
            )
        }
    }
}

// Opcje otwartego narzędzia (osobna funkcja — czytelniej niż jeden długi when w EditorScreen).
@Composable
private fun EditorOptions(
    section: EditSection,
    editor: CutoutEditor,
    busy: Boolean,
    objectShare: Int?,
    brushMode: Tool,
    onBrushMode: (Tool) -> Unit,
    brush: Float,
    onBrush: (Float) -> Unit,
    onAuto: () -> Unit,
) {
    val hint: @Composable (Int) -> Unit = { res ->
        Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    when (section) {
        EditSection.OBJECT -> {
            if (objectShare != null) {
                // Ile zdjęcia zostaje — od razu widać, czy tło naprawdę zniknęło.
                Text(
                    stringResource(if (objectShare >= 95) R.string.som_object_share_all else R.string.som_object_share, objectShare),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (objectShare >= 95) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                )
            }
            hint(R.string.som_object_hint)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioChip(stringResource(R.string.som_object_again), false) { if (!busy) onAuto() }
                StudioChip(stringResource(R.string.som_editor_whole_photo), false) { if (!busy) editor.fill(true) }
            }
        }
        EditSection.BRUSH -> {
            StudioSegmented(
                options = listOf(
                    Tool.ADD to stringResource(R.string.som_add),
                    Tool.ERASE to stringResource(R.string.som_editor_tool_erase),
                    Tool.MOVE to stringResource(R.string.som_editor_tool_move),
                ),
                selected = brushMode,
                onSelect = onBrushMode,
            )
            if (brushMode != Tool.MOVE) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioLabel(stringResource(R.string.som_brush_size))
                    BRUSH_SIZES.forEachIndexed { i, size ->
                        BrushDot(index = i, selected = brush == size) { onBrush(size) }
                    }
                }
            }
            hint(R.string.som_brush_hint)
        }
        EditSection.CROP -> {
            StudioLabel(stringResource(R.string.som_crop_aspect))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                CROP_ASPECTS.forEach { (value, label) ->
                    StudioChip(if (value == null) stringResource(R.string.som_crop_free) else label, editor.aspect == value) { editor.changeAspect(value) }
                }
            }
            StudioLabel(stringResource(R.string.som_editor_shape))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                StickerShapes.all.forEach { s -> StudioChip(s.label, editor.shape == s.id) { editor.chooseShape(s.id) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioChip(stringResource(R.string.som_crop_reset), false) { editor.resetCrop() }
            }
            hint(R.string.som_crop_hint)
        }
        EditSection.EDGE -> {
            // Poziomy względem wielkości zdjęcia — wtedy efekt widać tak samo na małym i na 12-megapikselowym zdjęciu.
            val longest = maxOf(editor.photo.width, editor.photo.height).toFloat()
            StudioLabel(stringResource(R.string.som_edge_smooth))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(0f to R.string.som_level_none, 0.004f to R.string.som_level_light, 0.012f to R.string.som_level_strong).forEach { (f, label) ->
                    val value = f * longest
                    StudioChip(stringResource(label), editor.edgeSmooth == value) { editor.edgeSmooth = value }
                }
            }
            StudioLabel(stringResource(R.string.som_edge_shrink))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(0f to R.string.som_level_none, 0.002f to R.string.som_level_light, 0.005f to R.string.som_level_medium, 0.01f to R.string.som_level_strong).forEach { (f, label) ->
                    val value = f * longest
                    StudioChip(stringResource(label), editor.edgeShrink == value) { editor.edgeShrink = value }
                }
            }
            hint(R.string.som_edge_hint)
        }
        EditSection.FILTER -> {
            FilterPicker(editor.filters) { editor.filters = it }
        }
        EditSection.FRAME -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Layer.FRAMES.forEach { (key, label) -> StudioChip(label, editor.frame == key) { editor.frame = key } }
            }
            if (Layer.hasColor(editor.frame)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FrameColors.forEach { c ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (editor.frameColor == c) 3.dp else 1.dp, if (editor.frameColor == c) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { editor.frameColor = c },
                        )
                    }
                }
            }
        }
        EditSection.ROTATE -> {
            Text(
                stringResource(R.string.som_editor_rotation, editor.rotation.roundToInt()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                StudioChip("↺ 90°", false) { editor.rotation = normalizedDegrees(editor.rotation - 90f) }
                StudioChip("−15°", false) { editor.rotation = normalizedDegrees(editor.rotation - 15f) }
                StudioChip("−5°", false) { editor.rotation = normalizedDegrees(editor.rotation - 5f) }
                StudioChip(stringResource(R.string.som_rotate_straight), editor.rotation == 0f) { editor.rotation = 0f }
                StudioChip("+5°", false) { editor.rotation = normalizedDegrees(editor.rotation + 5f) }
                StudioChip("+15°", false) { editor.rotation = normalizedDegrees(editor.rotation + 15f) }
                StudioChip("↻ 90°", false) { editor.rotation = normalizedDegrees(editor.rotation + 90f) }
            }
        }
        EditSection.SIZE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(512 to R.string.som_editor_size_small, 768 to R.string.som_editor_size_medium, 1024 to R.string.som_editor_size_large).forEach { (side, label) ->
                    StudioChip(stringResource(R.string.som_editor_size_chip, stringResource(label), side), editor.outputSide == side) { editor.outputSide = side }
                }
            }
            hint(R.string.som_size_hint)
        }
    }
}

// Narzędzia edytora naklejki (pasek na ekranie głównym). maskView = pokazuje maskę na zdjęciu zamiast gotowej naklejki.
private enum class EditSection(@StringRes val labelRes: Int, @StringRes val titleRes: Int, val maskView: Boolean) {
    OBJECT(R.string.som_rail_object, R.string.som_title_object, true),
    BRUSH(R.string.som_rail_brush, R.string.som_title_brush, true),
    CROP(R.string.som_rail_crop, R.string.som_title_crop, false),
    EDGE(R.string.som_rail_edge, R.string.som_title_edge, false),
    FILTER(R.string.som_rail_filter, R.string.som_title_filter, false),
    FRAME(R.string.som_rail_frame, R.string.som_title_frame, false),
    ROTATE(R.string.som_rail_rotate, R.string.som_title_rotate, false),
    SIZE(R.string.som_rail_size, R.string.som_title_size, false);

    val icon: ImageVector
        get() = when (this) {
            OBJECT -> StudioIcons.Subject
            BRUSH -> StudioIcons.Brush
            CROP -> StudioIcons.Crop
            EDGE -> StudioIcons.Edge
            FILTER -> StudioIcons.Filter
            FRAME -> StudioIcons.Frame
            ROTATE -> StudioIcons.Rotate
            SIZE -> StudioIcons.Resize
        }
}

// Rozmiary pędzla (średnica w dp ekranu) — cztery kropki zamiast suwaka.
private val BRUSH_SIZES = listOf(14f, 28f, 48f, 80f)

// Proporcje kadru (szerokość / wysokość w pikselach); null = dowolne.
private val CROP_ASPECTS: List<Pair<Float?, String>> = listOf(
    null to "", 1f to "1:1", 4f / 5f to "4:5", 3f / 4f to "3:4", 9f / 16f to "9:16", 4f / 3f to "4:3", 16f / 9f to "16:9",
)

@Composable
private fun BrushDot(index: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val desc = stringResource(R.string.som_brush_size_cd, index + 1)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
    ) {
        Box(Modifier.size((6 + index * 6).dp).clip(CircleShape).background(colors.onSurface))
    }
}

// Kadrowanie jak w zwykłym edytorze zdjęć: ramka na całym obrazie, rogi do przeciągania, środek przesuwa ramkę.
// Przy zablokowanych proporcjach (1:1, 4:5…) rogi trzymają proporcje. Kształt widać jako obrys w ramce.
// image = zdjęcie × maska w pełnym kadrze (cutPreview), współrzędne kadru w ułamkach 0..1.
@Composable
private fun CropCanvas(editor: CutoutEditor, image: android.graphics.Bitmap) {
    val bitmap = remember(image) { image.asImageBitmap() }
    val checker = rememberCheckerBrush()
    val accent = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current
    val grab = with(density) { 32.dp.toPx() }   // promień „chwytu” rogu
    val pad = with(density) { 24.dp.toPx() }    // margines, żeby rogi dało się złapać przy krawędzi ekranu
    val corner = with(density) { 22.dp.toPx() } // długość ramion narożnika
    val line = with(density) { 2.dp.toPx() }
    val thick = with(density) { 4.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val scale = minOf((cw - 2 * pad) / bitmap.width, (ch - 2 * pad) / bitmap.height).coerceAtLeast(0.01f)
        val iw = bitmap.width * scale
        val ih = bitmap.height * scale
        val ox = (cw - iw) / 2f
        val oy = (ch - ih) / 2f

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(editor, iw, ih, ox, oy) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = editor.crop
                        // Który róg (0 = lewy górny, 1 = prawy górny, 2 = prawy dolny, 3 = lewy dolny) albo środek (4).
                        val corners = listOf(
                            Offset(ox + start.left * iw, oy + start.top * ih),
                            Offset(ox + start.right * iw, oy + start.top * ih),
                            Offset(ox + start.right * iw, oy + start.bottom * ih),
                            Offset(ox + start.left * iw, oy + start.bottom * ih),
                        )
                        val nearest = corners.indices.minByOrNull { (corners[it] - down.position).getDistance() } ?: 0
                        val inside = down.position.x in corners[0].x..corners[1].x && down.position.y in corners[0].y..corners[2].y
                        val mode = when {
                            (corners[nearest] - down.position).getDistance() <= grab -> nearest
                            inside -> 4
                            else -> -1
                        }
                        if (mode >= 0) {
                            down.consume()
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    pressed = false
                                } else {
                                    val nx = ((change.position.x - ox) / iw).coerceIn(0f, 1f)
                                    val ny = ((change.position.y - oy) / ih).coerceIn(0f, 1f)
                                    editor.crop = if (mode == 4) {
                                        val dx = (change.position.x - down.position.x) / iw
                                        val dy = (change.position.y - down.position.y) / ih
                                        CropBox(start.left + dx, start.top + dy, start.right + dx, start.bottom + dy).clamped()
                                    } else {
                                        dragCorner(start, mode, nx, ny, editor.aspect?.let { it / editor.photoAspect })
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                },
        ) {
            val c = editor.crop
            val l = ox + c.left * iw
            val t = oy + c.top * ih
            val r = ox + c.right * iw
            val b = oy + c.bottom * ih
            drawRect(checker, topLeft = Offset(ox, oy), size = Size(iw, ih))
            drawImage(bitmap, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()), dstSize = IntSize(iw.roundToInt(), ih.roundToInt()))
            // Przyciemnienie poza kadrem (cztery pasy).
            val dim = Color(0xB3000000)
            drawRect(dim, Offset(ox, oy), Size(iw, t - oy))
            drawRect(dim, Offset(ox, b), Size(iw, oy + ih - b))
            drawRect(dim, Offset(ox, t), Size(l - ox, b - t))
            drawRect(dim, Offset(r, t), Size(ox + iw - r, b - t))
            // Siatka trójpodziału + ramka.
            val grid = Color(0x66FFFFFF)
            for (i in 1..2) {
                val x = l + (r - l) * i / 3f
                val y = t + (b - t) * i / 3f
                drawLine(grid, Offset(x, t), Offset(x, b), strokeWidth = 1f)
                drawLine(grid, Offset(l, y), Offset(r, y), strokeWidth = 1f)
            }
            drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(line))
            // Kształt w kadrze (obrys).
            val shapePath = StickerShapes.path(editor.shape, r - l, b - t)
            if (shapePath != null) {
                shapePath.offset(l, t)
                drawPath(shapePath.asComposePath(), accent, style = Stroke(thick))
            }
            // Narożniki do chwytania.
            listOf(Offset(l, t) to Offset(1f, 1f), Offset(r, t) to Offset(-1f, 1f), Offset(r, b) to Offset(-1f, -1f), Offset(l, b) to Offset(1f, -1f)).forEach { (p, d) ->
                drawLine(Color.White, p, Offset(p.x + d.x * corner, p.y), strokeWidth = thick, cap = StrokeCap.Round)
                drawLine(Color.White, p, Offset(p.x, p.y + d.y * corner), strokeWidth = thick, cap = StrokeCap.Round)
            }
        }
    }
}

// Przeciąganie rogu: przeciwległy róg stoi, ten idzie za palcem. ratio = proporcje w ułamkach zdjęcia (null = dowolne).
private fun dragCorner(start: CropBox, corner: Int, x: Float, y: Float, ratio: Float?): CropBox {
    val ax = if (corner == 0 || corner == 3) start.right else start.left  // przeciwległy róg (kotwica)
    val ay = if (corner == 0 || corner == 1) start.bottom else start.top
    val dirX = if (x >= ax) 1f else -1f
    val dirY = if (y >= ay) 1f else -1f
    val maxW = if (dirX > 0) 1f - ax else ax
    val maxH = if (dirY > 0) 1f - ay else ay
    var w = kotlin.math.abs(x - ax).coerceIn(CropBox.MIN, maxOf(CropBox.MIN, maxW))
    var h = kotlin.math.abs(y - ay).coerceIn(CropBox.MIN, maxOf(CropBox.MIN, maxH))
    if (ratio != null) {
        if (w / h > ratio) w = h * ratio else h = w / ratio
        if (w > maxW) { w = maxW; h = w / ratio }
        if (h > maxH) { h = maxH; w = h * ratio }
    }
    val left = if (dirX > 0) ax else ax - w
    val top = if (dirY > 0) ay else ay - h
    return CropBox(left, top, left + w, top + h).clamped()
}

// Podgląd z maską: przygaszone zdjęcie w tle (widać, co można "domalować"), na nim wycięty obiekt na szachownicy.
// Jeden palec = pędzel (albo przesuwanie), dwa palce = przybliżanie i przesuwanie widoku.
@Composable
// ghost = przygaszone zdjęcie pod spodem (przy pędzlu widać, co można domalować); bez niego — dokładnie to, co zostanie.
private fun MaskCanvas(editor: CutoutEditor, tool: Tool, brushDp: Float, rotation: Float, enabled: Boolean, maskOverride: ImageBitmap? = null, ghost: Boolean = true) {
    val photo = remember(editor) { editor.photo.asImageBitmap() }
    val mask = remember(editor) { editor.mask.asImageBitmap() }
    var zoom by remember(editor) { mutableFloatStateOf(1f) }
    var pan by remember(editor) { mutableStateOf(Offset.Zero) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var cropFrom by remember { mutableStateOf<Offset?>(null) } // ramka kadru w trakcie przeciągania (ekran)
    var cropTo by remember { mutableStateOf<Offset?>(null) }
    val checker = rememberCheckerBrush()
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
                // Wzór zaczepiony w rogu zdjęcia — przesuwa się razem z nim.
                translate(o.x, o.y) { drawRect(checker, size = androidx.compose.ui.geometry.Size(dst.width.toFloat(), dst.height.toFloat())) }
                if (rotation == 0f && ghost) drawImage(photo, dstOffset = dstOffset, dstSize = dst, alpha = 0.22f)
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

// Kąt sprowadzony do -180..180.
private fun normalizedDegrees(d: Float): Float = (((d + 180f) % 360f + 360f) % 360f) - 180f

private val FrameColors = listOf(0xFFFFFFFFL, 0xFF16171AL, 0xFFFFD54FL, 0xFFFF8A80L, 0xFF80D8FFL, 0xFFB9F6CAL, 0xFFE1BEE7L)

// Gotowa naklejka na szachownicy (tak zostanie zapisana); obrót całości tylko przy rysowaniu, więc działa na żywo.
@Composable
private fun ResultPreview(image: ImageBitmap?, rotation: Float) {
    val checker = rememberCheckerBrush()
    Canvas(Modifier.fillMaxSize()) {
        drawRect(checker) // cała powierzchnia
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

// Szachownica = "tu jest przezroczyście" (jak w programach graficznych). Jeden mały kafelek 2×2 pola powtarzany
// przez shader (jak TileBrush w WPF) — jedno rysowanie zamiast setek prostokątów w każdej klatce.
@Composable
private fun rememberCheckerBrush(): ShaderBrush {
    val cell = with(LocalDensity.current) { 14.dp.roundToPx() }.coerceAtLeast(1)
    return remember(cell) {
        val tile = android.graphics.Bitmap.createBitmap(cell * 2, cell * 2, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(tile).apply {
            drawColor(0xFF2A2E34.toInt())
            val dark = android.graphics.Paint().apply { color = 0xFF1E2126.toInt() }
            drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), dark)
            drawRect(cell.toFloat(), cell.toFloat(), cell * 2f, cell * 2f, dark)
        }
        ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}
