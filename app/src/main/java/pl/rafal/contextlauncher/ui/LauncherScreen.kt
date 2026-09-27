package pl.rafal.contextlauncher.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import pl.rafal.contextlauncher.layout.GridRect
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.CardApp
import pl.rafal.contextlauncher.data.CardCustomWidget
import pl.rafal.contextlauncher.data.CardElement
import pl.rafal.contextlauncher.data.CustomWidgetKind
import pl.rafal.contextlauncher.data.db.PinnedItemEntity
import pl.rafal.contextlauncher.data.db.AppRestrictionEntity
import pl.rafal.contextlauncher.data.widgets.LauncherWidgets
import pl.rafal.contextlauncher.data.widgets.WidgetProvider
import pl.rafal.contextlauncher.ui.theme.ContextLauncherTheme
import pl.rafal.contextlauncher.ui.theme.Palette
import pl.rafal.contextlauncher.ui.widgets.DualClockWidget
import pl.rafal.contextlauncher.ui.widgets.FolderWidget
import pl.rafal.contextlauncher.ui.widgets.HandyWidget
import pl.rafal.contextlauncher.ui.widgets.ModeNoteWidget
import pl.rafal.contextlauncher.ui.widgets.ClockWidget
import pl.rafal.contextlauncher.ui.widgets.ModeDialWidget
import pl.rafal.contextlauncher.ui.widgets.WeatherWidget
import pl.rafal.contextlauncher.ui.widgets.GlanceCallbacks
import pl.rafal.contextlauncher.ui.widgets.GlanceWidget
import pl.rafal.contextlauncher.ui.widgets.LocalWidgetOpacity
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.contextlauncher.ui.widgets.TodayWidget
import pl.rafal.contextlauncher.ui.widgets.TodayCallbacks
import pl.rafal.contextlauncher.ui.widgets.CountdownWidget
import pl.rafal.contextlauncher.ui.widgets.CountdownDialog
import pl.rafal.contextlauncher.ui.widgets.ChecklistWidget
import pl.rafal.contextlauncher.ui.widgets.ChecklistCallbacks
import pl.rafal.contextlauncher.ui.widgets.ChecklistAddDialog
import pl.rafal.contextlauncher.ui.widgets.QuickTogglesWidget
import pl.rafal.contextlauncher.ui.widgets.ContactsWidget
import pl.rafal.contextlauncher.ui.widgets.ContactCallbacks
import pl.rafal.contextlauncher.data.Checklist
import pl.rafal.contextlauncher.data.CheckItem
import pl.rafal.contextlauncher.data.Countdown
import pl.rafal.contextlauncher.data.TapAction
import pl.rafal.contextlauncher.data.withAction
import pl.rafal.contextlauncher.ui.widgets.TapActionDialog
import pl.rafal.contextlauncher.system.GlanceActions
import pl.rafal.contextlauncher.ui.widgets.StickerDialog
import pl.rafal.contextlauncher.ui.widgets.StickerWidget
import pl.rafal.contextlauncher.ui.widgets.NoteWidgetDialog
import pl.rafal.contextlauncher.ui.widgets.ZonePickerDialog

// Główny ekran launchera: karta aktywnego trybu + szuflada nad nią + arkusze i okna.
// Tu łączymy ViewModel z komponentami; same komponenty (ModeHeader, AppDrawer...) nie znają ViewModelu.
@Composable
fun LauncherApp(
    viewModel: LauncherViewModel,
    widgets: LauncherWidgets,
    onAddWidget: (WidgetProvider) -> Unit, // dodawanie widżetu wymaga aktywności (zgody, konfiguracja)
) {
    val activeMode by viewModel.activeMode.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val defaultPalette by viewModel.defaultPalette.collectAsState()
    val uniformLook by viewModel.settings.uniformLook.flow.collectAsState()
    val showLabels by viewModel.settings.showLabels.flow.collectAsState()

    // Motyw bierzemy z aktywnego trybu (jego schemat i kolor główny), a gdy tryb nie ma własnego — z ustawień.
    // "Jednolity wygląd" w Ustawieniach wyłącza motywy trybów: wszędzie domyślny schemat.
    val modePalette = if (uniformLook) null else Palette.fromName(activeMode?.palette)
    ContextLauncherTheme(
        palette = modePalette ?: defaultPalette,
        accent = if (uniformLook) null else activeMode?.accent,
        themeMode = themeMode,
    ) {
        // CompositionLocal ≈ wartość "dziedziczona" w dół drzewa (jak DynamicResource w WPF).
        val blockedKeys by viewModel.blockedKeys.collectAsState()
        val widgetOpacity by viewModel.settings.widgetOpacity.flow.collectAsState()
        val dotsEnabled by viewModel.settings.notificationDots.flow.collectAsState()
        val notified by pl.rafal.contextlauncher.system.NotificationDotsService.packages.collectAsState()
        CompositionLocalProvider(
            LocalNotifiedApps provides if (dotsEnabled) notified else emptySet(),
            LocalShowAppLabels provides showLabels,
            LocalBlockedApps provides blockedKeys,
            LocalWidgetOpacity provides widgetOpacity,
        ) {
            LauncherContent(viewModel, widgets, onAddWidget)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherContent(
    viewModel: LauncherViewModel,
    widgets: LauncherWidgets,
    onAddWidget: (WidgetProvider) -> Unit,
) {
    val modes by viewModel.modes.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    val cardElements by viewModel.cardElements.collectAsState()
    val drawerApps by viewModel.drawerApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val query by viewModel.query.collectAsState()
    val homePresses by viewModel.homePresses.collectAsState()
    val widgetProviders by viewModel.widgetProviders.collectAsState()
    val pinnedItems by viewModel.pinnedItems.collectAsState()
    val folderTree by viewModel.folderTree.collectAsState()
    val suggestion by viewModel.suggestion.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val frequentApps by viewModel.frequentApps.collectAsState()
    val needsOnboarding by viewModel.needsOnboarding.collectAsState()
    val manualTasks by viewModel.manualTasks.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val autoNotice by viewModel.autoNotice.collectAsState()
    val autoStatus by viewModel.autoStatus.collectAsState()
    val blockedKeys by viewModel.blockedKeys.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val blockedLaunch by viewModel.blockedLaunch.collectAsState()
    val restrictions by viewModel.restrictions.collectAsState()
    val glance by viewModel.glance.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val quickStates by viewModel.quickStates.collectAsState()
    val timedUntil by viewModel.timedUntil.collectAsState()
    val showWallpaper by viewModel.settings.showWallpaper.flow.collectAsState()
    val wallpaperDim by viewModel.settings.wallpaperDim.flow.collectAsState()
    val wallpaperVersion by viewModel.wallpaperVersion.collectAsState()
    val leftHanded by viewModel.settings.leftHanded.flow.collectAsState()
    val alwaysAskModes by viewModel.settings.alwaysAskModes.flow.collectAsState()
    val context = LocalContext.current

    // --- Stan ekranu: co jest otwarte ---
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) } // tryb edycji układu karty
    var settingsOpen by rememberSaveable { mutableStateOf(false) } // pełnoekranowe Ustawienia
    var appRulesOpen by rememberSaveable { mutableStateOf(false) } // blokowanie i ukrywanie aplikacji
    var appRulesModeId by rememberSaveable { mutableStateOf<Long?>(null) } // od którego trybu zacząć
    var drawerFocus by remember { mutableStateOf(false) }            // true = szuflada od razu z klawiaturą
    var newModeOpen by remember { mutableStateOf(false) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    // Pod ręką
    var handyOpen by remember { mutableStateOf(false) }
    var linkDialogOpen by remember { mutableStateOf(false) }
    var newNoteOpen by remember { mutableStateOf(false) }
    var editedNote by remember { mutableStateOf<PinnedItemEntity?>(null) }
    // Własne widżety
    var addingClock by remember { mutableStateOf(false) }
    var addingFolderWidget by remember { mutableStateOf(false) }
    var clockToChange by remember { mutableStateOf<CardCustomWidget?>(null) }
    var noteWidgetToEdit by remember { mutableStateOf<CardCustomWidget?>(null) }
    // Foldery
    var folderSheetId by remember { mutableStateOf<Long?>(null) } // folder otwarty z widżetu na karcie
    var foldersOpen by remember { mutableStateOf(false) }          // zarządzanie folderami z menu ⋯
    var appToFile by remember { mutableStateOf<AppInfo?>(null) }  // aplikacja dodawana do folderu z szuflady
    // Ustawienia trybu (reguły sugestii, nazwa, usuwanie) — trzymamy id, a tryb bierzemy świeży z listy.
    var settingsModeId by remember { mutableStateOf<Long?>(null) }
    // Kreator: otwiera się sam przy świeżej instalacji albo z menu ⋯.
    var wizardOpen by rememberSaveable { mutableStateOf(false) }
    var wizardFirstRun by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(needsOnboarding) {
        if (needsOnboarding) {
            wizardFirstRun = true
            wizardOpen = true
        }
    }
    var addingTimeRule by remember(settingsModeId) { mutableStateOf(false) }
    var addingCalendarRule by remember(settingsModeId) { mutableStateOf(false) }
    var addingBluetoothRule by remember(settingsModeId) { mutableStateOf(false) }
    var addingWifiRule by remember(settingsModeId) { mutableStateOf(false) }
    var addingPlaceRule by remember(settingsModeId) { mutableStateOf(false) }
    // Zgody systemowe (Nie przeszkadzać, modyfikowanie ustawień) odczytujemy na nowo po każdym powrocie do launchera.
    val refreshTick by viewModel.refreshTick.collectAsState()

    // Uprawnienia do nowych reguł: po zgodzie od razu otwieramy właściwe okno.
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) addingBluetoothRule = true
        else Toast.makeText(context, "Bez zgody na urządzenia w pobliżu reguła Bluetooth nie zadziała", Toast.LENGTH_LONG).show()
    }
    var pendingLocationDialog by remember { mutableStateOf<String?>(null) } // "wifi" albo "place"
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            when (pendingLocationDialog) {
                "wifi" -> addingWifiRule = true
                "place" -> addingPlaceRule = true
            }
        } else {
            Toast.makeText(context, "Nazwa sieci i miejsce wymagają zgody na lokalizację", Toast.LENGTH_LONG).show()
        }
        pendingLocationDialog = null
    }

    fun startActivitySafely(intent: android.content.Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) { // brak ekranu albo brak uprawnienia (np. Bluetooth)
            Toast.makeText(context, "Brak aplikacji, która to otworzy", Toast.LENGTH_SHORT).show()
        }
    }
    var renamingMode by remember(settingsModeId) { mutableStateOf(false) }
    var deletingMode by remember(settingsModeId) { mutableStateOf(false) }
    val settingsMode = modes.firstOrNull { it.id == settingsModeId }

    // Prośba o dostęp do kalendarza (systemowe okno "Zezwolić na dostęp do kalendarza?").
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onCalendarPermissionResult()
        if (!granted) {
            Toast.makeText(context, "Bez dostępu do kalendarza nie pokażę wydarzeń ani reguł kalendarza", Toast.LENGTH_LONG).show()
        }
    }

    // Systemowy wybór zdjęć (bez uprawnień do całej galerii) — na naklejki.
    val pickSticker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addSticker(uri)
    }
    var stickerToEdit by remember { mutableStateOf<CardCustomWidget?>(null) }
    // Widżety Odliczanie i Lista: który jest właśnie edytowany (id elementu karty).
    var countdownToEdit by remember { mutableStateOf<Long?>(null) }
    // Tryb na czas: dla którego trybu wybieramy długość.
    var timedModeFor by remember { mutableStateOf<ModeEntity?>(null) }
    // Nowe reguły: ładowanie i bateria mają małe okienka, słuchawki dodają się od razu.
    var addingChargingRule by remember(settingsModeId) { mutableStateOf(false) }
    var addingBatteryRule by remember(settingsModeId) { mutableStateOf(false) }
    // Tapeta: dla którego trybu wybieramy obraz.
    var wallpaperFor by remember { mutableStateOf<Long?>(null) }
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val modeId = wallpaperFor
        if (uri != null && modeId != null) viewModel.setWallpaper(modeId, uri)
        wallpaperFor = null
    }
    var checklistAddTo by remember { mutableStateOf<Long?>(null) }
    var checklistRename by remember { mutableStateOf<Long?>(null) }

    // Kontakty: prośba o dostęp dopiero po dotknięciu widżetu.
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onContactsPermissionResult()
    }

    // Akcja naklejki: dla którego widżetu wybieramy i czy kontakt ma być do SMS-a (false = telefon).
    var actionFor by remember { mutableStateOf<Long?>(null) }
    var phoneForSms by remember { mutableStateOf(false) }
    val pickPhone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val widget = cardElements.filterIsInstance<CardCustomWidget>().firstOrNull { it.item.id == actionFor }
        val picked = result.data?.data?.let(viewModel::readPickedPhone)
        if (widget != null && picked != null) {
            val (number, name) = picked // dekonstrukcja pary, jak (var a, var b) = tuple w C#
            val action = if (phoneForSms) TapAction.Sms(number, name) else TapAction.Dial(number, name)
            viewModel.updateWidgetConfig(widget, widget.config.withAction(action).toString())
            actionFor = null
        }
    }

    // Świeża wersja własnego widżetu po id (config zmienia się po każdym odhaczeniu).
    fun customWidget(id: Long?): CardCustomWidget? =
        cardElements.filterIsInstance<CardCustomWidget>().firstOrNull { it.item.id == id }

    fun updateChecklist(widget: CardCustomWidget, change: (Checklist) -> Checklist) {
        viewModel.updateWidgetConfig(widget, change(Checklist.parse(widget.config)).toJson())
    }

    // Pogoda potrzebuje przybliżonej lokalizacji; po zgodzie od razu ją pobieramy.
    val weatherPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.forceRefreshWeather()
    }

    // Systemowy wybór pliku; wynik (adres pliku albo null) trafia do lambdy.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pinFile(uri)
    }

    fun closeDrawer() {
        drawerOpen = false
        viewModel.clearQuery()
    }

    // Wykonanie akcji naklejki. Aplikacja idzie przez viewModel.launch, więc blokady trybu też działają.
    fun runTapAction(action: TapAction) {
        when (action) {
            is TapAction.OpenApp -> installedApps.firstOrNull { it.matches(action.packageName, action.className, action.userSerial) }
                ?.let { viewModel.launch(it) }
                ?: Toast.makeText(context, "Nie ma już aplikacji ${action.appLabel}", Toast.LENGTH_SHORT).show()
            is TapAction.Dial -> startActivitySafely(GlanceActions.dial(action.number))
            is TapAction.Sms -> startActivitySafely(GlanceActions.sms(action.number))
            is TapAction.Toggle -> viewModel.quickToggle(action.toggle)?.let(::startActivitySafely)
            is TapAction.SwitchMode -> modes.firstOrNull { it.id == action.modeId }?.let(viewModel::selectMode)
                ?: Toast.makeText(context, "Tryb ${action.modeName} został usunięty", Toast.LENGTH_SHORT).show()
            is TapAction.OpenLink -> startActivitySafely(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(action.url)))
        }
    }

    fun openPinned(item: PinnedItemEntity) {
        if (item.kind == PinnedItemEntity.KIND_NOTE) {
            editedNote = item
            return
        }
        val intent = viewModel.openIntent(item) ?: return
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Brak aplikacji, która to otworzy", Toast.LENGTH_SHORT).show()
        }
    }

    fun addFolderWidget(folderId: Long) {
        viewModel.addCustomWidget(CustomWidgetKind.FOLDER, JSONObject().put("folderId", folderId).toString())
    }

    // Akcje przeglądarki folderów, wspólne dla szuflady i arkusza z widżetu.
    val folderCallbacks = FolderCallbacks(
        onLaunch = { app ->
            viewModel.launch(app)
            drawerOpen = false
            folderSheetId = null
            foldersOpen = false
        },
        onAppInfo = viewModel::openAppInfo,
        onCreateFolder = viewModel::createFolder,
        onRenameFolder = viewModel::renameFolder,
        onDeleteFolder = viewModel::deleteFolder,
        onAddApps = viewModel::addAppsToFolder,
        onMoveApp = viewModel::moveAppToFolder,
        onRemoveApp = viewModel::removeAppFromFolder,
        onAddToCard = { folderId ->
            addFolderWidget(folderId)
            Toast.makeText(context, "Dodano folder do trybu ${activeMode?.name.orEmpty()}", Toast.LENGTH_SHORT).show()
        },
    )

    // Home naciśnięty na launcherze: zamknij wszystko i wróć do karty.
    // Zapamiętujemy obsłużone naciśnięcie: po obrocie ekranu / zmianie motywu efekt uruchamia się ponownie,
    // a wtedy nie powinien zamykać tego, co było otwarte.
    var handledHome by remember { mutableStateOf(homePresses) }
    LaunchedEffect(homePresses) {
        if (homePresses == handledHome) return@LaunchedEffect
        handledHome = homePresses
        drawerOpen = false
        settingsOpen = false
        appRulesOpen = false
        widgetPickerOpen = false
        handyOpen = false
        folderSheetId = null
        foldersOpen = false
        settingsModeId = null
        editing = false
    }

    // Wstecz: najpierw zamyka szufladę, potem kończy edycję; na samej karcie nic nie robi.
    // Kolejność ma znaczenie: później zarejestrowany BackHandler wygrywa, więc Ustawienia (niżej) mają pierwszeństwo.
    BackHandler(enabled = drawerOpen) { closeDrawer() }
    BackHandler(enabled = !drawerOpen && editing) { editing = false }
    BackHandler(enabled = !drawerOpen && !editing) { }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = appRulesOpen) { appRulesOpen = false }

    fun openAppRules(modeId: Long?) {
        appRulesModeId = modeId
        appRulesOpen = true
    }

    // --- Przeciąganie z szuflady na kartę ---
    var draggedApp by remember { mutableStateOf<AppInfo?>(null) }
    // Osobna flaga: po upuszczeniu szuflada ma zostać niewidoczna aż do zamknięcia (bez mignięcia).
    var drawerHidden by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var gridBounds by remember { mutableStateOf<Rect?>(null) }
    var gridCellPx by remember { mutableStateOf(1f) }

    // Cel upuszczenia: widżet folderu pod palcem albo pole 2×2 na siatce (środek pod palcem).
    fun dropTarget(): Pair<CardCustomWidget?, GridRect>? {
        val bounds = gridBounds ?: return null
        if (!bounds.contains(dragPos)) return null
        val cx = (dragPos.x - bounds.left) / gridCellPx
        val cy = (dragPos.y - bounds.top) / gridCellPx
        val folder = cardElements.filterIsInstance<CardCustomWidget>().firstOrNull { w ->
            w.kind == CustomWidgetKind.FOLDER && folderTree.folder(w.config.optLong("folderId", -1)) != null &&
                cx >= w.rect.x && cx < w.rect.x + w.rect.w && cy >= w.rect.y && cy < w.rect.y + w.rect.h
        }
        val size = pl.rafal.contextlauncher.layout.CardGrid.APP_SIZE
        val x = (cx - size / 2f).roundToInt().coerceIn(0, pl.rafal.contextlauncher.layout.CardGrid.COLUMNS - size)
        val y = (cy - size / 2f).roundToInt().coerceIn(0, pl.rafal.contextlauncher.layout.CardGrid.ROWS - size)
        return folder to (folder?.rect ?: GridRect(x, y, size, size))
    }

    // derivedStateOf: ekran przerysowuje się, gdy palec przejdzie na INNE pole, a nie przy każdym ruchu o piksel.
    val dragTarget by remember { derivedStateOf { if (draggedApp == null) null else dropTarget() } }

    val drawerDrag = ExternalDrag(
        onStart = { app, pos ->
            draggedApp = app
            dragPos = pos
            drawerHidden = true
        },
        onMove = { dragPos = it },
        onEnd = {
            val app = draggedApp
            val target = dropTarget()
            if (app != null && target != null) {
                val (folder, rect) = target
                if (folder != null) {
                    val folderId = folder.config.optLong("folderId", -1)
                    viewModel.addAppsToFolder(folderId, listOf(app))
                    Toast.makeText(context, "Dodano ${app.label} do folderu", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addToActiveModeAt(app, rect.x, rect.y)
                }
            }
            draggedApp = null
            closeDrawer()
        },
        onCancel = {
            draggedApp = null
            drawerHidden = false
        },
    )

    fun openDrawer(focusSearch: Boolean) {
        drawerHidden = false
        drawerFocus = focusSearch
        drawerOpen = true
    }

    Box(
        Modifier
            .fillMaxSize()
            // Z tapetą: tło to tylko półprzezroczysta "szyba" nad tapetą systemu (okno jest przezroczyste).
            .background(
                if (showWallpaper) MaterialTheme.colorScheme.background.copy(alpha = wallpaperDim / 100f)
                else MaterialTheme.colorScheme.background,
            ),
    ) {
        ModeCard(
            modeName = activeMode?.name,
            editing = editing,
            header = {
                // Nagłówek ma stałą wysokość: sugestie i komunikaty automatu pojawiają się W NIM,
                // zamiast w osobnym banerze, więc układ karty się nie przesuwa.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                    val notice = autoNotice
                    val noticeMode = notice?.let { n -> modes.firstOrNull { it.id == n.modeId } }
                    val current = suggestion
                    when {
                        editing -> HeaderTitle(activeMode, "Edycja układu", Modifier.weight(1f))
                        noticeMode != null -> AutoSwitchPrompt(
                            mode = noticeMode,
                            previous = modes.firstOrNull { it.id == notice?.previousModeId },
                            reason = notice?.reason.orEmpty(),
                            onKeep = viewModel::keepAutoSwitch,
                            onUndo = viewModel::undoAutoSwitch,
                            modifier = Modifier.weight(1f),
                        )
                        current != null -> SuggestionPrompt(
                            suggestion = current,
                            modes = modes,
                            activeMode = activeMode,
                            onAccept = { viewModel.acceptSuggestion(current) },
                            onDismiss = { viewModel.dismissSuggestion(current) },
                            autoStatus = autoStatus,
                            modifier = Modifier.weight(1f),
                        )
                        else -> HeaderTitle(
                            activeMode,
                            // Tryb na czas: w nagłówku widać, do kiedy trwa.
                            activeMode?.name.orEmpty() + if (timedUntil > 0) " · do ${formatClock(timedUntil)}" else "",
                            Modifier.weight(1f),
                        )
                    }
                    if (editing) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { editing = false }) { Text("Gotowe") }
                    } else {
                        if (manualTasks.isNotEmpty()) {
                            ManualTasksChip(
                                modeName = activeMode?.name.orEmpty(),
                                tasks = manualTasks,
                                onTask = { task -> startActivitySafely(viewModel.intentFor(task)) },
                                onDismiss = viewModel::dismissManualTasks,
                            )
                        }
                        CardMenuButton(
                            actions = listOf(
                                MenuAction("Pod ręką") { handyOpen = true },
                                MenuAction("Foldery") { foldersOpen = true },
                                MenuAction("Dodaj widżet") {
                                    viewModel.loadWidgetProviders()
                                    widgetPickerOpen = true
                                },
                                MenuAction("Zmień układ") { editing = true },
                                MenuAction("Ustawienia") { settingsOpen = true },
                            ) + if (timedUntil > 0) listOf(
                                MenuAction("Przedłuż o 30 min") { viewModel.extendTimed(30 * 60_000L) },
                                MenuAction("Zakończ tryb na czas") { viewModel.endTimedNow() },
                            ) else emptyList(),
                        )
                    }
                }
            },
            modeSwitcher = {
                ModeSwitcherButton(
                    active = activeMode,
                    modes = modes,
                    closeSignal = homePresses,
                    onSelect = viewModel::selectMode,
                    onManage = { settingsModeId = it.id },
                    onNewMode = { newModeOpen = true },
                    onTimed = { timedModeFor = it },
                    compact = true, // nazwa trybu jest już w nagłówku
                )
            },
            leftHanded = leftHanded,
            elements = cardElements,
            widgets = widgets,
            onLaunch = { viewModel.launch(it.app) },
            menuFor = { item ->
                listOf(
                    MenuAction("Zmień układ") { editing = true },
                    MenuAction("Usuń z trybu") { viewModel.removeFromMode(item) },
                    MenuAction("Informacje o aplikacji") { viewModel.openAppInfo(item.app) },
                )
            },
            onMove = viewModel::moveItem,
            onResize = viewModel::resizeItem,
            onRemove = viewModel::removeFromMode,
            onOpenDrawer = ::openDrawer,
            onGridPlaced = { bounds, cellPx ->
                gridBounds = bounds
                gridCellPx = cellPx
            },
            onDropIntoFolder = { app, folder ->
                // Widżet może wskazywać folder, który już usunięto — wtedy nic nie robimy.
                if (folderTree.folder(folder.config.optLong("folderId", -1)) != null) {
                    viewModel.moveCardAppIntoFolder(app, folder)
                    Toast.makeText(context, "Przeniesiono ${app.app.label} do folderu", Toast.LENGTH_SHORT).show()
                }
            },
            customWidget = { widget, enabled ->
                // Uwaga wydajnościowa: widget.config parsuje JSON przy każdym odczycie — tu bierzemy go raz
                // na zmianę treści (remember z kluczem), a nie przy każdym przerysowaniu karty.
                val cfg = remember(widget.item.config) { widget.config }
                // when na enumie: każdy rodzaj widżetu ma swój wygląd i swoją akcję po dotknięciu.
                when (widget.kind) {
                    CustomWidgetKind.DUAL_CLOCK -> DualClockWidget(
                        zoneId = cfg.optString("zone", "Europe/London"),
                        onClick = if (enabled) ({ clockToChange = widget }) else null,
                    )
                    CustomWidgetKind.MODE_NOTE -> ModeNoteWidget(
                        text = cfg.optString("text", ""),
                        onClick = if (enabled) ({ noteWidgetToEdit = widget }) else null,
                    )
                    CustomWidgetKind.HANDY -> HandyWidget(
                        items = pinnedItems.filter { it.archivedAt == null },
                        onOpenItem = if (enabled) ::openPinned else null,
                        onOpenAll = if (enabled) ({ handyOpen = true }) else null,
                    )
                    CustomWidgetKind.FOLDER -> {
                        val folderId = cfg.optLong("folderId", -1)
                        FolderWidget(
                            folder = folderTree.folder(folderId),
                            subfolderCount = folderTree.subfolders(folderId).size,
                            apps = folderTree.apps(folderId).map { it.app },
                            onOpenFolder = if (enabled) ({ folderSheetId = folderId }) else null,
                            onLaunch = if (enabled) ({ app -> viewModel.launch(app) }) else null, // lambda: launch ma parametr domyślny
                        )
                    }
                    CustomWidgetKind.CLOCK -> ClockWidget(
                        // Dotknięcie zegara otwiera budziki systemowej aplikacji Zegar.
                        onClick = if (enabled) ({ startActivitySafely(android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }) else null,
                    )
                    CustomWidgetKind.WEATHER -> WeatherWidget(
                        weather = weather,
                        hasPermission = remember(refreshTick) { viewModel.hasWeatherPermission() },
                        onClick = if (enabled) ({
                            if (viewModel.hasWeatherPermission()) viewModel.forceRefreshWeather()
                            else weatherPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }) else null,
                    )
                    CustomWidgetKind.MODE_DIAL -> ModeDialWidget(
                        modes = modes,
                        activeId = activeMode?.id,
                        enabled = enabled,
                        onSelect = viewModel::selectMode,
                        onNewMode = { newModeOpen = true },
                    )
                    CustomWidgetKind.GLANCE -> GlanceWidget(
                        event = glance.event,
                        hasCalendar = glance.hasCalendar,
                        alarmAt = glance.alarmAt,
                        weather = weather,
                        callbacks = if (!enabled) null else GlanceCallbacks(
                            onClock = { startActivitySafely(GlanceActions.clock(context)) },
                            onDate = { startActivitySafely(GlanceActions.calendarAt(System.currentTimeMillis())) },
                            onEvent = { startActivitySafely(GlanceActions.event(it)) },
                            onGrantCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
                            onWeather = {
                                if (viewModel.hasWeatherPermission()) startActivitySafely(GlanceActions.weather(context))
                                else weatherPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            onAlarm = { startActivitySafely(GlanceActions.alarms()) },
                        ),
                    )
                    CustomWidgetKind.TODAY -> TodayWidget(
                        agenda = glance.agenda,
                        hasCalendar = glance.hasCalendar,
                        callbacks = if (!enabled) null else TodayCallbacks(
                            onOpenDay = { startActivitySafely(GlanceActions.calendarAt(System.currentTimeMillis())) },
                            onOpenEvent = { startActivitySafely(GlanceActions.event(it)) },
                            onNewEvent = { startActivitySafely(GlanceActions.newEvent()) },
                            onGrant = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
                        ),
                    )
                    CustomWidgetKind.COUNTDOWN -> CountdownWidget(
                        countdown = Countdown.parse(cfg),
                        onClick = if (enabled) ({ countdownToEdit = widget.item.id }) else null,
                    )
                    CustomWidgetKind.CHECKLIST -> ChecklistWidget(
                        list = Checklist.parse(cfg),
                        callbacks = if (!enabled) null else ChecklistCallbacks(
                            onToggle = { index ->
                                updateChecklist(widget) { list ->
                                    list.copy(items = list.items.mapIndexed { i, item -> if (i == index) item.copy(done = !item.done) else item })
                                }
                            },
                            onAdd = { checklistAddTo = widget.item.id },
                            onRename = { checklistRename = widget.item.id },
                            onClearDone = { updateChecklist(widget) { it.copy(items = it.items.filterNot { item -> item.done }) } },
                        ),
                    )
                    CustomWidgetKind.QUICK_TOGGLES -> QuickTogglesWidget(
                        states = quickStates,
                        onToggle = if (!enabled) null else { toggle -> viewModel.quickToggle(toggle)?.let(::startActivitySafely) },
                    )
                    CustomWidgetKind.CONTACTS -> ContactsWidget(
                        contacts = favorites,
                        hasPermission = remember(refreshTick, favorites) { viewModel.hasContactsPermission() },
                        callbacks = if (!enabled) null else ContactCallbacks(
                            onDial = { startActivitySafely(GlanceActions.dial(it)) },
                            onSms = { startActivitySafely(GlanceActions.sms(it)) },
                            onOpen = { startActivitySafely(GlanceActions.contact(it.lookupUri)) },
                            onOpenContacts = { startActivitySafely(GlanceActions.contactsApp()) },
                            onGrant = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                        ),
                    )
                    CustomWidgetKind.STICKER -> {
                        val action = TapAction.from(cfg)
                        StickerWidget(
                            path = cfg.optString("file"),
                            rotation = cfg.optDouble("rotation", 0.0).toFloat(),
                            flipped = cfg.optBoolean("flipped", false),
                            // Z akcją: dotknięcie ją wykonuje; bez akcji: otwiera ustawienia jak dawniej.
                            onClick = if (!enabled) null else if (action != null) ({ runTapAction(action) }) else ({ stickerToEdit = widget }),
                            onLongClick = { stickerToEdit = widget },
                        )
                    }
                }
            },
        )

        // Szuflada wjeżdża od dołu nad kartę.
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(
                apps = drawerApps,
                query = query,
                onQueryChange = viewModel::onQueryChange, // :: ≈ przekazanie metody jako delegata
                onAppClick = { app ->
                    viewModel.launch(app)
                    closeDrawer()
                },
                menuFor = { app ->
                    val modeName = activeMode?.name.orEmpty()
                    val scope = listOf(activeMode?.id)
                    val blocked = app.appKey in blockedKeys
                    val hidden = hiddenApps.any { it.appKey == app.appKey }
                    listOf(
                        MenuAction("Dodaj do trybu $modeName") { viewModel.addToActiveMode(app) },
                        MenuAction("Dodaj do folderu…") { appToFile = app },
                        if (blocked) MenuAction("Odblokuj w trybie $modeName") { viewModel.setRestriction(listOf(app), scope, null) }
                        else MenuAction("Zablokuj w trybie $modeName") { viewModel.setRestriction(listOf(app), scope, AppRestrictionEntity.KIND_BLOCK) },
                        if (hidden) MenuAction("Pokaż w trybie $modeName") { viewModel.setRestriction(listOf(app), scope, null) }
                        else MenuAction("Ukryj w trybie $modeName") { viewModel.setRestriction(listOf(app), scope, AppRestrictionEntity.KIND_HIDE) },
                        MenuAction("Blokowanie i ukrywanie…") { openAppRules(activeMode?.id) },
                        MenuAction("Informacje o aplikacji") { viewModel.openAppInfo(app) },
                    )
                },
                foldersContent = {
                    FolderBrowser(
                        tree = folderTree,
                        allApps = installedApps,
                        startFolderId = null,
                        callbacks = folderCallbacks,
                    )
                },
                frequent = frequentApps,
                frequentLabel = "Często w trybie ${activeMode?.name.orEmpty()}",
                autoFocusSearch = drawerFocus,
                hiddenApps = hiddenApps,
                dragOut = drawerDrag,
                // Podczas przeciągania szuflada znika z oczu, ale zostaje w drzewie UI —
                // inaczej ikona, która "trzyma" gest palca, przestałaby istnieć.
                modifier = if (drawerHidden) Modifier.alpha(0f) else Modifier,
            )
        }

        // Przeciągana ikona i podświetlenie miejsca, gdzie wyląduje.
        draggedApp?.let { app ->
            val density = LocalDensity.current
            dragTarget?.let { (folder, rect) ->
                val bounds = gridBounds ?: return@let
                // Zajęte pole: czerwone podświetlenie (ikona trafi wtedy w najbliższe wolne miejsce).
                val free = folder != null || pl.rafal.contextlauncher.layout.CardGrid.canPlace(rect, cardElements.map { it.rect })
                with(density) {
                    Box(
                        Modifier
                            .offset { IntOffset((bounds.left + rect.x * gridCellPx).roundToInt(), (bounds.top + rect.y * gridCellPx).roundToInt()) }
                            .size((rect.w * gridCellPx).toDp(), (rect.h * gridCellPx).toDp())
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                (if (free) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    .copy(alpha = if (folder != null) 0.3f else 0.18f),
                            ),
                    )
                }
            }
            val iconPx = with(density) { 56.dp.toPx() }
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset((dragPos.x - iconPx / 2).roundToInt(), (dragPos.y - iconPx / 2).roundToInt()) }
                    .size(56.dp)
                    .graphicsLayer { scaleX = 1.15f; scaleY = 1.15f; alpha = 0.9f },
            )
        }

        // Ustawienia przykrywają kartę (ale nie kreator, który może się z nich otworzyć).
        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInVertically { it / 4 } + fadeIn(),
            exit = slideOutVertically { it / 4 } + fadeOut(),
        ) {
            SettingsScreen(
                viewModel = viewModel,
                onOpenWizard = {
                    settingsOpen = false
                    wizardFirstRun = false
                    wizardOpen = true
                },
                onOpenAppRules = { openAppRules(activeMode?.id) },
                onClose = { settingsOpen = false },
            )
        }

        // Blokowanie i ukrywanie — nad Ustawieniami (otwiera się z nich albo z ustawień trybu).
        AnimatedVisibility(
            visible = appRulesOpen,
            enter = slideInVertically { it / 4 } + fadeIn(),
            exit = slideOutVertically { it / 4 } + fadeOut(),
        ) {
            AppRulesScreen(
                viewModel = viewModel,
                initialModeId = appRulesModeId,
                onClose = { appRulesOpen = false },
            )
        }

        // Kreator przykrywa cały ekran.
        if (wizardOpen) {
            OnboardingScreen(
                installedApps = installedApps,
                existingModeNames = modes.map { it.name.lowercase() }.toSet(),
                firstRun = wizardFirstRun,
                onCreate = viewModel::createModesFromTemplates,
                // Pominięcie kreatora = sama strona główna "Start" (zegar, pogoda, tarcza trybów).
                onSkip = { viewModel.createModesFromTemplates(listOf(pl.rafal.contextlauncher.data.ModeTemplates.Start)) },
                onClose = { wizardOpen = false },
            )
        }
    }

    // --- Arkusze i okna dialogowe ---

    settingsMode?.let { mode ->
        ModeSettingsSheet(
            mode = mode,
            rules = rules.filter { it.modeId == mode.id },
            onAddTimeRule = { addingTimeRule = true },
            onAddCalendarRule = { addingCalendarRule = true },
            onAddBluetoothRule = {
                val permission = viewModel.bluetoothPermission()
                if (permission == null || viewModel.hasBluetoothPermission()) addingBluetoothRule = true
                else bluetoothPermission.launch(permission)
            },
            onAddWifiRule = {
                if (viewModel.hasLocationPermission()) addingWifiRule = true
                else {
                    pendingLocationDialog = "wifi"
                    locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            onAddPlaceRule = {
                if (viewModel.hasLocationPermission()) addingPlaceRule = true
                else {
                    pendingLocationDialog = "place"
                    locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            onAddChargingRule = { addingChargingRule = true },
            onAddHeadphonesRule = { viewModel.addHeadphonesRule(mode) },
            onAddBatteryRule = { addingBatteryRule = true },
            wallpaperSet = remember(wallpaperVersion, mode.id) { viewModel.wallpaperPath(mode.id) != null },
            wallpaperOnLock = remember(wallpaperVersion, mode.id) { viewModel.wallpaperOnLock(mode.id) },
            onPickWallpaper = {
                wallpaperFor = mode.id
                pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onClearWallpaper = { viewModel.clearWallpaper(mode.id) },
            onWallpaperLockChange = { viewModel.setWallpaperOnLock(mode.id, it) },
            phoneSettings = pl.rafal.contextlauncher.data.ModePhoneSettings.parse(mode.settings),
            // refreshTick w kluczu: po powrocie z ustawień systemu odczytujemy zgody na nowo.
            hasDndAccess = remember(refreshTick, mode.id) { viewModel.hasDndAccess() },
            canWriteSettings = remember(refreshTick, mode.id) { viewModel.canWriteSettings() },
            onPhoneSettingsChange = { viewModel.updatePhoneSettings(mode, it) },
            onGrantDnd = {
                startActivitySafely(viewModel.dndAccessIntent())
            },
            onGrantWrite = {
                startActivitySafely(viewModel.writeSettingsIntent())
            },
            onDeleteRule = viewModel::deleteRule,
            onRename = { renamingMode = true },
            onDelete = { deletingMode = true },
            onAppearanceChange = { icon, color, palette, accent ->
                viewModel.updateModeAppearance(mode, icon, color, palette, accent)
            },
            alwaysAsk = mode.id.toString() in alwaysAskModes,
            onAlwaysAskChange = { viewModel.setAlwaysAsk(mode, it) },
            appRulesSummary = restrictions.filter { it.modeId == mode.id }.let { list ->
                val b = list.count { it.kind == AppRestrictionEntity.KIND_BLOCK }
                val h = list.size - b
                if (list.isEmpty()) "Nic nie jest zablokowane ani ukryte" else "Zablokowane: $b · ukryte: $h"
            },
            onManageApps = {
                settingsModeId = null
                openAppRules(mode.id)
            },
            onDismiss = { settingsModeId = null },
        )

        if (addingTimeRule) {
            TimeRuleDialog(
                onConfirm = { days, start, end ->
                    viewModel.addTimeRule(mode, days, start, end)
                    addingTimeRule = false
                },
                onDismiss = { addingTimeRule = false },
            )
        }

        if (addingCalendarRule) {
            CalendarRuleDialog(
                onConfirm = { keyword ->
                    viewModel.addCalendarRule(mode, keyword)
                    addingCalendarRule = false
                    // O dostęp pytamy dopiero teraz, gdy użytkownik wie, po co nam kalendarz.
                    if (!viewModel.hasCalendarPermission()) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                },
                onDismiss = { addingCalendarRule = false },
            )
        }

        if (addingBluetoothRule) {
            BluetoothRuleDialog(
                devices = remember { viewModel.pairedDevices() },
                onPick = { device ->
                    viewModel.addBluetoothRule(mode, device)
                    addingBluetoothRule = false
                },
                onDismiss = { addingBluetoothRule = false },
            )
        }

        if (addingWifiRule) {
            WifiRuleDialog(
                currentSsid = remember { viewModel.currentSsid() },
                onConfirm = { ssid ->
                    viewModel.addWifiRule(mode, ssid)
                    addingWifiRule = false
                },
                onDismiss = { addingWifiRule = false },
            )
        }

        if (addingChargingRule) {
            ChargingRuleDialog(
                onConfirm = {
                    viewModel.addChargingRule(mode, it)
                    addingChargingRule = false
                },
                onDismiss = { addingChargingRule = false },
            )
        }

        if (addingBatteryRule) {
            BatteryRuleDialog(
                onConfirm = {
                    viewModel.addBatteryRule(mode, it)
                    addingBatteryRule = false
                },
                onDismiss = { addingBatteryRule = false },
            )
        }

        if (addingPlaceRule) {
            PlaceRuleDialog(
                onConfirm = { label, radius ->
                    viewModel.addPlaceRule(mode, label, radius)
                    addingPlaceRule = false
                },
                onDismiss = { addingPlaceRule = false },
            )
        }

        if (renamingMode) {
            TextInputDialog(
                title = "Nazwa trybu",
                initial = mode.name,
                confirmLabel = "Zapisz",
                onConfirm = { name ->
                    viewModel.renameMode(mode, name)
                    renamingMode = false
                },
                onDismiss = { renamingMode = false },
            )
        }

        if (deletingMode) {
            AlertDialog(
                onDismissRequest = { deletingMode = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Usunąć tryb ${mode.name}?") },
                text = { Text("Znikną jego karta, Pod ręką i reguły. Aplikacje i foldery zostaną.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteMode(mode)
                        deletingMode = false
                        settingsModeId = null
                    }) { Text("Usuń") }
                },
                dismissButton = { TextButton(onClick = { deletingMode = false }) { Text("Anuluj") } },
            )
        }
    }

    blockedLaunch?.let { app ->
        BlockedLaunchDialog(
            app = app,
            modeName = activeMode?.name.orEmpty(),
            onCancel = viewModel::cancelBlockedLaunch,
            onOpenAnyway = {
                viewModel.launchAnyway(app)
                drawerOpen = false
            },
            onUnblock = {
                viewModel.setRestriction(listOf(app), listOf(activeMode?.id), null)
                viewModel.launchAnyway(app)
                drawerOpen = false
            },
        )
    }

    timedModeFor?.let { mode ->
        TimedModeDialog(
            modeName = mode.name,
            onPick = { duration ->
                viewModel.selectModeFor(mode, duration)
                timedModeFor = null
            },
            onDismiss = { timedModeFor = null },
        )
    }

    if (newModeOpen) {
        NewModeDialog(
            onConfirm = { name, color, icon ->
                viewModel.createMode(name, color, icon)
                newModeOpen = false
            },
            onDismiss = { newModeOpen = false },
        )
    }

    if (handyOpen) {
        HandySheet(
            modeName = activeMode?.name.orEmpty(),
            items = pinnedItems,
            onAddFile = { pickFile.launch(arrayOf("*/*")) },
            onAddLink = { linkDialogOpen = true },
            onAddNote = { newNoteOpen = true },
            onOpen = ::openPinned, // referencja do lokalnej funkcji
            onArchive = viewModel::archivePinned,
            onRestore = viewModel::restorePinned,
            onDelete = viewModel::deletePinned,
            onDismiss = { handyOpen = false },
        )
    }

    if (linkDialogOpen) {
        LinkDialog(
            onConfirm = { url, title ->
                viewModel.pinLink(url, title)
                linkDialogOpen = false
            },
            onDismiss = { linkDialogOpen = false },
        )
    }

    if (newNoteOpen) {
        NoteDialog(
            initialTitle = "",
            initialText = "",
            confirmLabel = "Przypnij",
            onConfirm = { title, text ->
                viewModel.pinNote(title, text)
                newNoteOpen = false
            },
            onDismiss = { newNoteOpen = false },
        )
    }

    // ?.let { } wykona blok tylko, gdy wartość nie jest null (jak "if (editedNote is { } note)" w C#).
    editedNote?.let { note ->
        NoteDialog(
            initialTitle = note.title,
            initialText = note.text.orEmpty(),
            confirmLabel = "Zapisz",
            onConfirm = { title, text ->
                viewModel.updateNote(note, title, text)
                editedNote = null
            },
            onDismiss = { editedNote = null },
        )
    }

    stickerToEdit?.let { sticker ->
        // Bierzemy świeżą wersję widżetu z listy, żeby okno pokazywało aktualny obrót po każdej zmianie.
        val current = cardElements.filterIsInstance<CardCustomWidget>().firstOrNull { it.item.id == sticker.item.id } ?: sticker
        StickerDialog(
            rotation = current.config.optDouble("rotation", 0.0).toFloat(),
            flipped = current.config.optBoolean("flipped", false),
            actionLabel = TapAction.from(current.config)?.label,
            onPickAction = {
                actionFor = current.item.id
                stickerToEdit = null
            },
            onChange = { rotation, flipped ->
                // Zachowujemy ścieżkę pliku, zmieniamy tylko obrót i odbicie.
                val config = current.config.put("rotation", rotation.toDouble()).put("flipped", flipped)
                viewModel.updateWidgetConfig(current, config.toString())
            },
            onDismiss = { stickerToEdit = null },
        )
    }

    customWidget(actionFor)?.let { widget ->
        TapActionDialog(
            current = TapAction.from(widget.config),
            apps = installedApps,
            modes = modes,
            onPickPhone = { sms ->
                phoneForSms = sms
                pickPhone.launch(
                    android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                )
            },
            onDone = { action ->
                viewModel.updateWidgetConfig(widget, widget.config.withAction(action).toString())
                actionFor = null
            },
            onDismiss = { actionFor = null },
        )
    }

    customWidget(countdownToEdit)?.let { widget ->
        CountdownDialog(
            initial = Countdown.parse(widget.config),
            onSave = {
                viewModel.updateWidgetConfig(widget, it.toJson())
                countdownToEdit = null
            },
            onDismiss = { countdownToEdit = null },
        )
    }

    customWidget(checklistAddTo)?.let { widget ->
        ChecklistAddDialog(
            onAdd = { lines ->
                updateChecklist(widget) { it.copy(items = it.items + lines.map { text -> CheckItem(text) }) }
                checklistAddTo = null
            },
            onDismiss = { checklistAddTo = null },
        )
    }

    customWidget(checklistRename)?.let { widget ->
        TextInputDialog(
            title = "Nazwa listy",
            initial = Checklist.parse(widget.config).title,
            confirmLabel = "Zapisz",
            onConfirm = { name ->
                updateChecklist(widget) { it.copy(title = name) }
                checklistRename = null
            },
            onDismiss = { checklistRename = null },
        )
    }

    if (addingClock) {
        ZonePickerDialog(
            onPick = { zone ->
                viewModel.addCustomWidget(CustomWidgetKind.DUAL_CLOCK, JSONObject().put("zone", zone).toString())
                addingClock = false
            },
            onDismiss = { addingClock = false },
        )
    }

    clockToChange?.let { clock ->
        ZonePickerDialog(
            onPick = { zone ->
                viewModel.updateWidgetConfig(clock, JSONObject().put("zone", zone).toString())
                clockToChange = null
            },
            onDismiss = { clockToChange = null },
        )
    }

    noteWidgetToEdit?.let { note ->
        NoteWidgetDialog(
            initialText = note.config.optString("text", ""),
            onSave = { text ->
                viewModel.updateWidgetConfig(note, JSONObject().put("text", text).toString())
                noteWidgetToEdit = null
            },
            onDismiss = { noteWidgetToEdit = null },
        )
    }

    if (addingFolderWidget) {
        FolderPickerDialog(
            tree = folderTree,
            title = "Który folder położyć na karcie?",
            onPick = { folder ->
                addFolderWidget(folder.id)
                addingFolderWidget = false
            },
            onDismiss = { addingFolderWidget = false },
            onCreateNew = { name ->
                viewModel.createFolderWithWidget(name)
                addingFolderWidget = false
            },
        )
    }

    appToFile?.let { app ->
        FolderPickerDialog(
            tree = folderTree,
            title = "Dodaj „${app.label}” do folderu",
            onPick = { folder ->
                viewModel.addAppsToFolder(folder.id, listOf(app))
                appToFile = null
            },
            onDismiss = { appToFile = null },
            onCreateNew = { name ->
                viewModel.createFolderWithApps(name, listOf(app))
                appToFile = null
            },
        )
    }

    // Folder otwarty z widżetu na karcie: ta sama przeglądarka co w szufladzie, ale od tego folderu.
    folderSheetId?.let { folderId ->
        ModalBottomSheet(
            onDismissRequest = { folderSheetId = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            FolderBrowser(
                tree = folderTree,
                allApps = installedApps,
                startFolderId = folderId,
                callbacks = folderCallbacks,
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .padding(horizontal = 16.dp),
            )
        }
    }

    // Zarządzanie folderami z menu ⋯: pełna przeglądarka od korzenia.
    if (foldersOpen) {
        ModalBottomSheet(
            onDismissRequest = { foldersOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            FolderBrowser(
                tree = folderTree,
                allApps = installedApps,
                startFolderId = null,
                callbacks = folderCallbacks,
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp),
            )
        }
    }

    if (widgetPickerOpen) {
        WidgetPickerSheet(
            providers = widgetProviders,
            onPick = { provider ->
                widgetPickerOpen = false
                onAddWidget(provider)
            },
            onPickCustom = { kind ->
                widgetPickerOpen = false
                when (kind) {
                    CustomWidgetKind.DUAL_CLOCK -> addingClock = true         // najpierw wybór strefy
                    CustomWidgetKind.FOLDER -> addingFolderWidget = true      // najpierw wybór folderu
                    // Nowa lista i odliczanie od razu z sensownym stanem początkowym.
                    CustomWidgetKind.CHECKLIST -> viewModel.addCustomWidget(kind, Checklist.of("Lista"))
                    CustomWidgetKind.COUNTDOWN -> viewModel.addCustomWidget(kind, Countdown.of(""))
                    CustomWidgetKind.STICKER -> pickSticker.launch(           // najpierw wybór obrazka
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                    else -> viewModel.addCustomWidget(kind)
                }
            },
            onDismiss = { widgetPickerOpen = false },
        )
    }
}

// Przycisk "⋯" z menu karty.
@Composable
private fun CardMenuButton(actions: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { open = true }
                .semantics { contentDescription = "Menu karty" },
        ) {
            Text("⋯", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

// Karta trybu. Przeciągnięcie w górę albo dotknięcie paska wyszukiwania otwiera szufladę.
@Composable
private fun ModeCard(
    modeName: String?,
    editing: Boolean,
    header: @Composable () -> Unit, // slot: miejsce na dowolny komponent (jak ContentPresenter w XAML)
    modeSwitcher: @Composable () -> Unit,
    leftHanded: Boolean,
    elements: List<CardElement>,
    widgets: LauncherWidgets,
    onLaunch: (CardApp) -> Unit,
    menuFor: (CardApp) -> List<MenuAction>,
    onMove: (CardElement, Int, Int) -> Unit,
    onResize: (CardElement, Int, Int) -> Unit,
    onRemove: (CardElement) -> Unit,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    customWidget: @Composable (CardCustomWidget, Boolean) -> Unit,
    onGridPlaced: (Rect, Float) -> Unit,
    onDropIntoFolder: (CardApp, CardCustomWidget) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // Gest w górę działa tylko poza edycją, żeby nie gryzł się z przeciąganiem elementów.
            .pointerInput(editing) {
                if (editing) return@pointerInput
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -120f) onOpenDrawer(false) }, // ujemne = ruch w górę
                ) { _, dragAmount -> total += dragAmount }
            },
    ) {
        header()
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (elements.isEmpty()) {
                Text(
                    text = "Tryb ${modeName.orEmpty()} jest pusty.\nDodaj aplikacje z szuflady (przytrzymaj ikonę) albo widżet z menu ⋯.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                )
            }
            CardGridView(
                elements = elements,
                editing = editing,
                widgets = widgets,
                onLaunch = onLaunch,
                menuFor = menuFor,
                onMove = onMove,
                onResize = onResize,
                onRemove = onRemove,
                customWidget = customWidget,
                modifier = Modifier.fillMaxSize(),
                onGridPlaced = onGridPlaced,
                onDropIntoFolder = onDropIntoFolder,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (editing) {
            Text(
                text = "Przeciągnij, aby przesunąć. Róg ⤡ zmienia rozmiar widżetu, × usuwa element.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        } else {
            // Dolny rząd w zasięgu kciuka. Praworęczni: [Szukaj][szuflada][tryb];
            // leworęczni: lustrzane odbicie, żeby przełącznik trybu był pod kciukiem.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leftHanded) {
                    modeSwitcher()
                    Spacer(Modifier.width(8.dp))
                    DrawerButton { onOpenDrawer(false) }
                    Spacer(Modifier.width(8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpenDrawer(true) }
                        .padding(horizontal = 20.dp),
                ) {
                    Text("⌕", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Szukaj aplikacji",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!leftHanded) {
                    Spacer(Modifier.width(8.dp))
                    DrawerButton { onOpenDrawer(false) }
                    Spacer(Modifier.width(8.dp))
                    modeSwitcher()
                }
            }
        }
    }
}

// Okrągły przycisk szuflady (wszystkie aplikacje) — bez klawiatury, od razu lista.
@Composable
private fun DrawerButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Wszystkie aplikacje" },
    ) {
        Image(
            painter = painterResource(pl.rafal.contextlauncher.R.drawable.ic_apps),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.size(24.dp),
        )
    }
}

// Zwykły nagłówek: ikona i nazwa trybu (albo "Edycja układu").
@Composable
private fun HeaderTitle(mode: pl.rafal.contextlauncher.data.db.ModeEntity?, text: String, modifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        mode?.let { ModeBadge(it, size = 22.dp) }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatClock(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
