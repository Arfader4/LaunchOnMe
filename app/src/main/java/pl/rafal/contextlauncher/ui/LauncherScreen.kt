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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import pl.rafal.contextlauncher.layout.GridRect
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.CardApp
import pl.rafal.contextlauncher.data.CardWidget
import pl.rafal.contextlauncher.data.CardCustomWidget
import pl.rafal.contextlauncher.data.CardElement
import pl.rafal.contextlauncher.data.StackData
import pl.rafal.contextlauncher.data.CustomWidgetKind
import pl.rafal.contextlauncher.data.CardFolderData
import pl.rafal.contextlauncher.data.SystemAction
import pl.rafal.contextlauncher.data.withActions
import pl.rafal.contextlauncher.data.describe
import pl.rafal.contextlauncher.system.systemActionIntent
import pl.rafal.contextlauncher.ui.widgets.StickerFrame
import pl.rafal.contextlauncher.ui.widgets.StickerShape
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
import pl.rafal.contextlauncher.ui.widgets.LocalWidgetBg
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
        val iconShape by viewModel.settings.iconShape.flow.collectAsState()
        val labelScale by viewModel.settings.labelScale.flow.collectAsState()
        val folderLabels by viewModel.settings.showFolderLabels.flow.collectAsState()
        val widgetCorner by viewModel.settings.widgetCorner.flow.collectAsState()
        val dotsEnabled by viewModel.settings.notificationDots.flow.collectAsState()
        val notified by pl.rafal.contextlauncher.system.NotificationDotsService.packages.collectAsState()
        val allInstalled by viewModel.installedApps.collectAsState()
        // remember: ta sama funkcja między przerysowaniami (static CompositionLocal inaczej przerysowałby cały ekran).
        val lookup = remember(allInstalled) {
            val byKey = allInstalled.associateBy { it.key }
            val fn: (String) -> AppInfo? = { key -> byKey[key] }
            fn
        }
        CompositionLocalProvider(
            LocalAppLookup provides lookup, // ikony aplikacji jako symbol folderu
            LocalNotifiedApps provides if (dotsEnabled) notified else emptySet(),
            LocalShowAppLabels provides showLabels,
            LocalBlockedApps provides blockedKeys,
            LocalWidgetOpacity provides widgetOpacity,
            LocalIconShape provides IconShape.of(iconShape),
            LocalLabelScale provides labelScale / 100f,
            LocalShowFolderLabels provides folderLabels,
            LocalWidgetCorner provides widgetCorner.dp,
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
    val cardElements by viewModel.cardElements.collectAsState() // wszystkie strony karty
    val currentPage by viewModel.currentPage.collectAsState()
    val usedPages by viewModel.usedPages.collectAsState()
    val maxPages by viewModel.settings.maxPages.flow.collectAsState()
    val drawerApps by viewModel.drawerApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val shortcuts by viewModel.shortcuts.collectAsState()
    val activeLayout by viewModel.activeLayout.collectAsState()
    val folderAtBottom by viewModel.settings.folderAtBottom.flow.collectAsState()
    val modeLayouts by viewModel.modeLayouts.collectAsState()
    val globalIconCells by viewModel.settings.appIconCells.flow.collectAsState()
    val query by viewModel.query.collectAsState()
    val homePresses by viewModel.homePresses.collectAsState()
    val widgetProviders by viewModel.widgetProviders.collectAsState()
    val pinnedItems by viewModel.pinnedItems.collectAsState()
    val folderTree by viewModel.folderTree.collectAsState()
    // Aplikacje i skróty po kluczu — foldery na karcie trzymają klucze i jednych, i drugich.
    val appsByKey = remember(installedApps, shortcuts) { (installedApps + shortcuts).associateBy { it.key } }
    val launchCounts by viewModel.launchCounts.collectAsState()
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
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    // --- Stan ekranu: co jest otwarte ---
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) } // tryb edycji układu karty
    var settingsOpen by rememberSaveable { mutableStateOf(false) } // pełnoekranowe Ustawienia
    var addAppsOpen by remember { mutableStateOf(false) } // "+ Aplikacje" w edycji układu
    var addShortcutOpen by remember { mutableStateOf(false) } // "+ Skrót" w edycji układu
    var appRulesOpen by rememberSaveable { mutableStateOf(false) } // blokowanie i ukrywanie aplikacji
    var appRulesModeId by rememberSaveable { mutableStateOf<Long?>(null) } // od którego trybu zacząć
    var drawerFocus by remember { mutableStateOf(false) }            // true = szuflada od razu z klawiaturą
    var newModeOpen by remember { mutableStateOf(false) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    // OnHand
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
    var cardFolderOpen by remember { mutableStateOf<Long?>(null) } // folder NA KARCIE (z ikon) otwarty dotknięciem
    var widgetLookFor by remember { mutableStateOf<Long?>(null) }  // ⚙ → wygląd własnego widżetu
    var stackToManage by remember { mutableStateOf<Long?>(null) }  // ⚙ na stosie → lista jego widżetów
    var cardFolderPath by remember { mutableStateOf<List<Int>>(emptyList()) } // od którego podfolderu otworzyć
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
    // StickOnMe: wybór gotowej naklejki albo zrobienie nowej — wraca ścieżka pliku z biblioteki studia.
    val pickFromStudio = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pl.rafal.stickonme.StickOnMe.resultPath(result.data)?.let { path ->
            viewModel.addSticker(android.net.Uri.fromFile(java.io.File(path)))
        }
    }
    var stickerToEdit by remember { mutableStateOf<CardCustomWidget?>(null) }
    // "Edytuj w StickOnMe": pamiętamy, którą naklejkę poprawiamy (okno naklejki zamyka się na czas edycji).
    // Samo id (rememberSaveable przeżyje nawet zamknięcie procesu launchera w czasie edycji w studiu).
    var stickerInStudio by rememberSaveable { mutableStateOf<Long?>(null) }
    val editInStudio = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = stickerInStudio
        stickerInStudio = null
        val path = pl.rafal.stickonme.StickOnMe.resultPath(result.data)
        if (target != null && path != null) viewModel.replaceStickerFile(target, android.net.Uri.fromFile(java.io.File(path)))
    }
    // Widżety Odliczanie i Lista: który jest właśnie edytowany (id elementu karty).
    var countdownToEdit by remember { mutableStateOf<Long?>(null) }
    // Tryb na czas: dla którego trybu wybieramy długość.
    var timedModeFor by remember { mutableStateOf<ModeEntity?>(null) }
    // Nowe reguły: ładowanie i bateria mają małe okienka, słuchawki dodają się od razu.
    var addingChargingRule by remember(settingsModeId) { mutableStateOf(false) }
    var addingBatteryRule by remember(settingsModeId) { mutableStateOf(false) }
    // Tapeta: dla którego trybu wybieramy obraz.
    var wallpaperFor by rememberSaveable { mutableStateOf<Long?>(null) } // przeżyje zamknięcie procesu w czasie wyboru
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val modeId = wallpaperFor
        if (uri != null && modeId != null) viewModel.setWallpaper(modeId, uri)
        wallpaperFor = null
    }
    // Tapeta z tablicy StickOnMe: studio oddaje ścieżkę gotowego obrazu, dalej jak przy zdjęciu (kopia + "Dopasuj").
    val boardWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val modeId = wallpaperFor
        val path = pl.rafal.stickonme.StickOnMe.resultPath(result.data)
        if (path != null && modeId != null) viewModel.setWallpaper(modeId, android.net.Uri.fromFile(java.io.File(path)))
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
    var actionDraft by remember { mutableStateOf<List<TapAction>>(emptyList()) } // lista akcji w trakcie edycji
    var phoneForSms by remember { mutableStateOf(false) }
    val pickPhone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val widget = cardElements.filterIsInstance<CardCustomWidget>().firstOrNull { it.item.id == actionFor }
        val picked = result.data?.data?.let(viewModel::readPickedPhone)
        if (widget != null && picked != null) {
            val (number, name) = picked // dekonstrukcja pary, jak (var a, var b) = tuple w C#
            val action = if (phoneForSms) TapAction.Sms(number, name) else TapAction.Dial(number, name)
            actionDraft = actionDraft + action // trafia do listy w oknie akcji; zapis dopiero "Zapisz"
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
            is TapAction.OpenShortcut -> shortcuts.firstOrNull {
                it.packageName == action.packageName && it.shortcutId == action.shortcutId && it.userSerial == action.userSerial
            }?.let { viewModel.launch(it) }
                ?: Toast.makeText(context, "Skrót „${action.name}” jest niedostępny", Toast.LENGTH_SHORT).show()
            is TapAction.System -> when (action.action) {
                SystemAction.NOTIFICATIONS -> pl.rafal.contextlauncher.system.StatusBar.expandNotifications(context)
                SystemAction.QUICK_SETTINGS -> pl.rafal.contextlauncher.system.StatusBar.expandQuickSettings(context)
                else -> systemActionIntent(action.action)?.let(::startActivitySafely)
            }
        }
    }

    // Systemowe okno "Odinstalować?" (wymaga uprawnienia REQUEST_DELETE_PACKAGES, bez okienka zgody).
    fun uninstall(app: AppInfo) {
        startActivitySafely(
            android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.fromParts("package", app.packageName, null))
                .putExtra(android.content.Intent.EXTRA_USER, app.user),
        )
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
        },
        onAppInfo = viewModel::openAppInfo,
        onCreateFolder = viewModel::createFolder,
        onRenameFolder = viewModel::renameFolder,
        onDeleteFolder = viewModel::deleteFolder,
        onAddApps = viewModel::addAppsToFolder,
        onMoveApp = viewModel::moveAppToFolder,
        onRemoveApp = viewModel::removeAppFromFolder,
        onSetLook = viewModel::setFolderLook,
        onPlaceOnCard = viewModel::placeFolderOnCard,
        sizeOnCard = { folderId ->
            // Widżet tego folderu na aktywnej karcie (jeśli jest) → jego rozmiar w komórkach.
            cardElements.firstOrNull {
                it is CardCustomWidget && it.kind == CustomWidgetKind.FOLDER && it.config.optLong("folderId", -1) == folderId
            }?.let { it.item.w to it.item.h }
        },
        modeName = activeMode?.name.orEmpty(),
        // Kopia: folder na karcie żyje dalej sam (zmiany nie wracają do szuflady) — jak "Zapisz jako" w edytorze.
        onCopyToCard = { folderId ->
            viewModel.copyDrawerFolderToCard(folderId)
            Toast.makeText(context, "Skopiowano folder na kartę ${activeMode?.name.orEmpty()}", Toast.LENGTH_SHORT).show()
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
        cardFolderOpen = null
        settingsModeId = null
        editing = false
        viewModel.setPage(0) // Home = pierwsza strona karty (jak w każdym launcherze)
    }

    // Edycja układu jak transakcja: wejście robi zdjęcie karty, wyjście (✓, Wstecz, Home) zatwierdza,
    // a ✕ wcześniej woła cancelEdit() i przywraca zdjęcie.
    LaunchedEffect(editing) { if (editing) viewModel.beginEdit() else viewModel.commitEdit() }
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
    var draggedItem by remember { mutableStateOf<DragItem?>(null) }
    // Osobna flaga: po upuszczeniu szuflada ma zostać niewidoczna aż do zamknięcia (bez mignięcia).
    var drawerHidden by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var gridBounds by remember { mutableStateOf<Rect?>(null) }
    var gridCellW by remember { mutableStateOf(1f) }
    var gridCellH by remember { mutableStateOf(1f) }

    // Cel upuszczenia z szuflady:
    //  - aplikacja nad widżetem folderu (szuflady albo karty) → do folderu,
    //  - aplikacja nad środkiem ikony na karcie → nowy folder na karcie z obu,
    //  - w pozostałych przypadkach (i zawsze dla folderu) → pole w rozmiarze ikony pod palcem.
    fun dropTarget(): DropSpot? {
        val bounds = gridBounds ?: return null
        if (!bounds.contains(dragPos)) return null
        val cx = (dragPos.x - bounds.left) / gridCellW
        val cy = (dragPos.y - bounds.top) / gridCellH
        val size = pl.rafal.contextlauncher.layout.CardGrid.APP_SIZE
        val x = (cx - size / 2f).roundToInt().coerceIn(0, pl.rafal.contextlauncher.layout.CardGrid.COLUMNS - size)
        val y = (cy - size / 2f).roundToInt().coerceIn(0, (pl.rafal.contextlauncher.layout.CardGrid.rows - size).coerceAtLeast(0))
        val cell = GridRect(x, y, size, size)
        if (draggedItem !is DragItem.App) return DropSpot(null, null, cell)
        fun GridRect.has(inset: Float) =
            cx >= this.x + this.w * inset && cx < this.x + this.w * (1 - inset) &&
                cy >= this.y + this.h * inset && cy < this.y + this.h * (1 - inset)
        // Tylko bieżąca strona (odczyt stanu w środku, więc derivedStateOf niżej widzi zmianę strony).
        val onPage = cardElements.filter { it.item.page == currentPage }
        val folder = onPage.filterIsInstance<CardCustomWidget>().firstOrNull { w ->
            val alive = w.kind == CustomWidgetKind.CARD_FOLDER ||
                (w.kind == CustomWidgetKind.FOLDER && folderTree.folder(w.config.optLong("folderId", -1)) != null)
            alive && w.rect.has(0f)
        }
        if (folder != null) return DropSpot(folder, null, folder.rect)
        val app = onPage.filterIsInstance<CardApp>().firstOrNull { it.rect.has(0f) }
        if (app != null) return DropSpot(null, app, app.rect)
        return DropSpot(null, null, cell)
    }

    // derivedStateOf: ekran przerysowuje się, gdy palec przejdzie na INNE pole, a nie przy każdym ruchu o piksel.
    val dragTarget by remember { derivedStateOf { if (draggedItem == null) null else dropTarget() } }

    val drawerDrag = ExternalDrag(
        onStart = { item, pos ->
            draggedItem = item
            dragPos = pos
            drawerHidden = true
        },
        onMove = { dragPos = it },
        onEnd = {
            val item = draggedItem
            val target = dropTarget()
            if (item != null && target != null) {
                when (item) {
                    is DragItem.Folder -> viewModel.placeFolderAt(item.folder.id, target.rect.x, target.rect.y)
                    is DragItem.App -> {
                        val app = item.app
                        val folder = target.folder
                        when {
                            folder != null && folder.kind == CustomWidgetKind.CARD_FOLDER -> viewModel.addToCardFolder(folder, listOf(app))
                            folder != null -> {
                                viewModel.addAppsToFolder(folder.config.optLong("folderId", -1), listOf(app))
                                Toast.makeText(context, "Dodano ${app.label} do folderu", Toast.LENGTH_SHORT).show()
                            }
                            target.mergeWith != null -> viewModel.mergeIntoCardFolder(target.mergeWith, app, null)
                            else -> viewModel.addToActiveModeAt(app, target.rect.x, target.rect.y)
                        }
                    }
                }
            }
            draggedItem = null
            closeDrawer()
        },
        onCancel = {
            draggedItem = null
            drawerHidden = false
        },
    )

    // Akcje karty (dawniej menu ⋯ w nagłówku) — teraz w menu przełącznika trybów i w widżecie "Tryby".
    val cardActions: List<MenuAction> = buildList {
        manualTasks.forEach { task -> add(MenuAction("⚙ ${task.label}") { startActivitySafely(viewModel.intentFor(task)) }) }
        if (manualTasks.isNotEmpty()) add(MenuAction("Ukryj przypomnienia") { viewModel.dismissManualTasks() })
        if (timedUntil > 0) {
            add(MenuAction("Przedłuż o 30 min") { viewModel.extendTimed(30 * 60_000L) })
            add(MenuAction("Zakończ tryb na czas") { viewModel.endTimedNow() })
        }
        // Widżety, układ i foldery są teraz pod przytrzymaniem karty (edycja) i w szufladzie — tu zostaje reszta.
        add(MenuAction("OnHand") { handyOpen = true })
        add(MenuAction("Ustawienia") { settingsOpen = true })
    }
    val modeMenuHeader = activeMode?.let { mode ->
        mode.name + if (timedUntil > 0) " · do ${formatClock(timedUntil)}" else ""
    }

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
            modeKey = activeMode?.id,
            editing = editing,
            header = {
                // Nagłówka już nie ma (więcej miejsca na kartę). Zostaje tylko pasek narzędzi w edycji układu.
                if (editing) {
                    // Ciasny pasek: ikony zamiast napisów — zębatka, ✕ (anuluj zmiany), ✓ (zapisz).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Trzy "dodaj" dzielą miejsce po równo, a napisy maleją zamiast się zawijać (wąskie telefony).
                        Row(Modifier.weight(1f)) {
                            val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                            TextButton(onClick = { addAppsOpen = true }, contentPadding = pad, modifier = Modifier.weight(1f)) {
                                pl.rafal.contextlauncher.ui.widgets.FitText("+ Aplikacje", maxSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = {
                                viewModel.loadWidgetProviders()
                                widgetPickerOpen = true
                            }, contentPadding = pad, modifier = Modifier.weight(1f)) {
                                pl.rafal.contextlauncher.ui.widgets.FitText("+ Widżet", maxSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { addShortcutOpen = true }, contentPadding = pad, modifier = Modifier.weight(1f)) {
                                pl.rafal.contextlauncher.ui.widgets.FitText("+ Skrót", maxSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        RoundIconButton(MaterialTheme.colorScheme.onSurfaceVariant, "Ustawienia", { settingsOpen = true }) {
                            Icon(
                                painterResource(pl.rafal.contextlauncher.R.drawable.ic_settings),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        RoundAction("✕", MaterialTheme.colorScheme.error, "Anuluj zmiany", {
                            viewModel.cancelEdit()
                            editing = false
                        })
                        RoundAction("✓", AcceptColor, "Zapisz układ", { editing = false }, filled = true)
                    }
                }
            },
            prompt = {
                // Sugestie i komunikaty automatu jako pływający pasek nad dolnym rzędem — nie zabierają miejsca karcie.
                val notice = autoNotice
                val noticeMode = notice?.let { n -> modes.firstOrNull { it.id == n.modeId } }
                val current = suggestion
                val promptModifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                when {
                    editing -> Unit
                    noticeMode != null -> AutoSwitchPrompt(
                        mode = noticeMode,
                        previous = modes.firstOrNull { it.id == notice?.previousModeId },
                        reason = notice?.reason.orEmpty(),
                        onKeep = viewModel::keepAutoSwitch,
                        onUndo = viewModel::undoAutoSwitch,
                        modifier = promptModifier,
                    )
                    current != null -> SuggestionPrompt(
                        suggestion = current,
                        modes = modes,
                        activeMode = activeMode,
                        onAccept = { viewModel.acceptSuggestion(current) },
                        onDismiss = { viewModel.dismissSuggestion(current) },
                        autoStatus = autoStatus,
                        modifier = promptModifier,
                    )
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
                    compact = true,
                    extraActions = cardActions,
                    menuHeader = modeMenuHeader,
                    badge = manualTasks.isNotEmpty(),
                    onReorder = viewModel::reorderModes,
                )
            },
            leftHanded = leftHanded,
            elements = cardElements,
            page = currentPage,
            // Strony: zajęte + w edycji jedna pusta "na zapas" (w granicach limitu z Ustawień).
            pageCount = run {
                val used = usedPages // z bazy — liczy też strony z samymi odinstalowanymi aplikacjami
                if (editing) maxOf(used, minOf(used + 1, maxPages)) else used
            },
            usedPageCount = usedPages,
            maxPages = maxPages,
            onPageChange = viewModel::setPage,
            onMoveToPage = viewModel::moveToPage,
            animatePageChange = { viewModel.animateNextPage },
            // Widżety stosu leżą w cardElements (strona STACKED_PAGE) — wybieramy je po id z konfiguracji stosu.
            stackMembers = { stack ->
                val byId = cardElements.associateBy { it.item.id }
                pl.rafal.contextlauncher.data.StackData.of(stack.item.config).members.mapNotNull { byId[it] }
            },
            onStack = viewModel::stackWidgets,
            onStackIndex = viewModel::setStackIndex,
            widgets = widgets,
            onLaunch = { viewModel.launch(it.app) },
            menuFor = { item ->
                listOf(
                    MenuAction("Usuń z karty") { viewModel.removeFromMode(item) },
                    MenuAction("Informacje o aplikacji") { viewModel.openAppInfo(item.app) },
                ) + if (item.app.isShortcut) emptyList() else listOf(MenuAction("Odinstaluj") { uninstall(item.app) })
            },
            onLayout = viewModel::applyLayout,
            onRemove = viewModel::removeFromMode,
            onUninstall = { if (it.app.isShortcut) viewModel.removeFromMode(it) else uninstall(it.app) },
            onLongPressItem = {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                editing = true
            },
            onOpenDrawer = ::openDrawer,
            onEmptyLongPress = {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                editing = true
            },
            onSwipeDown = { right ->
                if (right) pl.rafal.contextlauncher.system.StatusBar.expandQuickSettings(context)
                else pl.rafal.contextlauncher.system.StatusBar.expandNotifications(context)
            },
            onGridPlaced = { bounds, cellW, cellH ->
                gridBounds = bounds
                gridCellW = cellW
                gridCellH = cellH
            },
            onMergeApps = { dragged, target -> viewModel.mergeIntoCardFolder(target, dragged.app, dragged.item.id) },
            // ⚙ w edycji układu: ustawienia własnego widżetu (naklejka, notatka, zegar, odliczanie, folder).
            // ⚙ ma każdy własny widżet: naklejka od razu swoje ustawienia, reszta okno "Wygląd" (tło, krycie)
            // z przyciskiem do ustawień treści, jeśli widżet je ma.
            canConfigure = { true },
            onConfigure = { w ->
                when (w.kind) {
                    CustomWidgetKind.STICKER -> stickerToEdit = w
                    CustomWidgetKind.STACK -> stackToManage = w.item.id // stos: lista jego widżetów
                    else -> widgetLookFor = w.item.id
                }
            },
            gap = activeLayout.gap.dp,
            onDropIntoFolder = { app, folder ->
                // Widżet może wskazywać folder, który już usunięto — wtedy nic nie robimy.
                if (folder.kind == CustomWidgetKind.CARD_FOLDER) {
                    viewModel.addToCardFolder(folder, listOf(app.app), app.item.id)
                } else if (app.app.isShortcut) {
                    // Foldery szuflady trzymają tylko aplikacje (bez id skrótu) — skrót by tam przepadł.
                    Toast.makeText(context, "Skróty można wrzucać tylko do folderów na karcie", Toast.LENGTH_SHORT).show()
                } else if (folderTree.folder(folder.config.optLong("folderId", -1)) != null) {
                    viewModel.moveCardAppIntoFolder(app, folder)
                    Toast.makeText(context, "Przeniesiono ${app.app.label} do folderu", Toast.LENGTH_SHORT).show()
                }
            },
            customWidget = { widget, enabled ->
                // Uwaga wydajnościowa: widget.config parsuje JSON przy każdym odczycie — tu bierzemy go raz
                // na zmianę treści (remember z kluczem), a nie przy każdym przerysowaniu karty.
                val cfg = remember(widget.item.config) { widget.config }
                // when na enumie: każdy rodzaj widżetu ma swój wygląd i swoją akcję po dotknięciu.
                // Wygląd tego widżetu (⚙ → Wygląd): własne tło i krycie, inaczej globalne z Ustawień.
                val globalOpacity = LocalWidgetOpacity.current
                CompositionLocalProvider(
                    LocalWidgetOpacity provides (cfg.optInt("alpha", -1).takeIf { it >= 0 } ?: globalOpacity),
                    LocalWidgetBg provides (if (cfg.has("bg") && !cfg.isNull("bg")) cfg.getLong("bg") else null),
                ) {
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
                            preview = folderTree.previewApps(folderId),
                            subfolders = folderTree.subfolders(folderId).map { it to folderTree.previewApps(it.id) },
                            apps = folderTree.apps(folderId).map { it.app },
                            onOpenFolder = if (enabled) ({ id -> folderSheetId = id }) else null,
                            onLaunch = if (enabled) ({ app -> viewModel.launch(app) }) else null, // lambda: launch ma parametr domyślny
                        )
                    }
                    CustomWidgetKind.CARD_FOLDER -> {
                        val data = remember(widget.item.config) { CardFolderData.of(cfg) }
                        val apps = data.ordered(data.keys.mapNotNull { appsByKey[it] }, launchCounts) // odinstalowane znikają z widoku
                        FolderWidget(
                            folder = data.asEntity(widget.item.id),
                            preview = apps,
                            // Podfoldery z ujemnym id (-1, -2…) — po dotknięciu wiemy, który otworzyć.
                            subfolders = data.children.mapIndexed { i, c -> c.asEntity(-(i + 1).toLong()) to c.keys.mapNotNull { appsByKey[it] } },
                            apps = apps,
                            onOpenFolder = if (enabled) ({ id ->
                                cardFolderPath = if (id < 0) listOf((-id - 1).toInt()) else emptyList()
                                cardFolderOpen = widget.item.id
                            }) else null,
                            onLaunch = if (enabled) ({ app -> viewModel.launch(app) }) else null,
                            grid = data.grid,
                            align = data.align,
                            fromBottom = data.fromBottom,
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
                        subtitle = if (timedUntil > 0) "do ${formatClock(timedUntil)}" else null,
                        badge = manualTasks.isNotEmpty(),
                        // Małe rozmiary widżetu: po dotknięciu to samo menu co przycisk trybu na dole.
                        menu = { expanded, onDismiss ->
                            ModeDropdown(
                                expanded = expanded,
                                onDismiss = onDismiss,
                                active = activeMode,
                                modes = modes,
                                onSelect = viewModel::selectMode,
                                onManage = { settingsModeId = it.id },
                                onNewMode = { newModeOpen = true },
                                onTimed = { timedModeFor = it },
                                extraActions = cardActions,
                                header = modeMenuHeader,
                                onReorder = viewModel::reorderModes,
                            )
                        },
                    )
                    CustomWidgetKind.GLANCE -> GlanceWidget(
                        event = glance.event,
                        hasCalendar = glance.hasCalendar,
                        alarmAt = glance.alarmAt,
                        alarmApp = glance.alarmApp,
                        weather = weather,
                        hasWeatherPermission = remember(refreshTick) { viewModel.hasWeatherPermission() },
                        callbacks = if (!enabled) null else GlanceCallbacks(
                            onClock = { startActivitySafely(GlanceActions.clock(context)) },
                            onDate = { startActivitySafely(GlanceActions.calendarAt(System.currentTimeMillis())) },
                            onEvent = { startActivitySafely(GlanceActions.event(it)) },
                            onGrantCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
                            onWeather = {
                                if (viewModel.hasWeatherPermission()) startActivitySafely(GlanceActions.weather(context))
                                else weatherPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            // Budzik otwiera aplikację, która go ustawiła (np. Zegar, Kalendarz, aplikacja snu).
                            onAlarm = { if (!viewModel.openAlarmSource()) startActivitySafely(GlanceActions.alarms()) },
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
                    CustomWidgetKind.STACK -> Unit // stos rysuje CardGridView (StackCell) — tu nigdy nie trafia
                    CustomWidgetKind.STICKER -> {
                        val actions = remember(widget.item.config) { TapAction.listFrom(cfg) }
                        StickerWidget(
                            path = cfg.optString("file"),
                            rotation = cfg.optDouble("rotation", 0.0).toFloat(),
                            flipped = cfg.optBoolean("flipped", false),
                            // Z akcjami: dotknięcie je wykonuje; bez akcji: otwiera ustawienia naklejki.
                            // Przytrzymanie = edycja układu (jak wszędzie), a tam ⚙ otwiera ustawienia naklejki.
                            onClick = if (!enabled) null else if (actions.isNotEmpty()) ({ actions.forEach(::runTapAction) }) else ({ stickerToEdit = widget }),
                            onLongClick = null,
                            shape = StickerShape.of(cfg.optString("shape")),
                            frame = StickerFrame.of(cfg.optString("frame")),
                        )
                    }
                }
                }
            },
        )

        // Szuflada wjeżdża od dołu nad kartę.
        AnimatedVisibility(
            visible = drawerOpen,
            // Wjazd na sprężynie (miękkie "dojechanie"), wyjazd szybki — jak w systemowych launcherach.
            enter = slideInVertically(Motion.panelOffset) { it / 3 } + fadeIn(Motion.fastOut()),
            exit = slideOutVertically(Motion.fastOut()) { it / 4 } + fadeOut(Motion.fastOut()),
        ) {
            AppDrawer(
                onClose = ::closeDrawer, // przesunięcie w dół na górze listy zamyka szufladę
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
                    // Skróty tej aplikacji (jak po przytrzymaniu ikony w Pixel Launcherze): dotknięcie je otwiera.
                    shortcuts.filter { it.packageName == app.packageName && it.userSerial == app.userSerial }.take(4).map { sc ->
                        MenuAction("↗  ${sc.label}") {
                            viewModel.launch(sc)
                            closeDrawer()
                        }
                    } + listOf(
                        MenuAction("Dodaj do trybu $modeName") { viewModel.addToActiveMode(app) },
                        MenuAction("Dodaj do folderu…") { appToFile = app },
                        if (blocked) MenuAction("Odblokuj w trybie $modeName") { viewModel.setRestriction(listOf(app), scope, null) }
                        else MenuAction("Zablokuj w trybie $modeName") { viewModel.setRestriction(listOf(app), scope, AppRestrictionEntity.KIND_BLOCK) },
                        if (hidden) MenuAction("Pokaż w trybie $modeName") { viewModel.setRestriction(listOf(app), scope, null) }
                        else MenuAction("Ukryj w trybie $modeName") { viewModel.setRestriction(listOf(app), scope, AppRestrictionEntity.KIND_HIDE) },
                        MenuAction("Blokowanie i ukrywanie…") { openAppRules(activeMode?.id) },
                        MenuAction("Informacje o aplikacji") { viewModel.openAppInfo(app) },
                        MenuAction("Odinstaluj") { uninstall(app) },
                    )
                },
                foldersContent = {
                    FolderBrowser(
                        tree = folderTree,
                        allApps = installedApps,
                        startFolderId = null,
                        callbacks = folderCallbacks,
                        dragOut = drawerDrag,
                    )
                },
                frequent = frequentApps,
                frequentLabel = "Często w trybie ${activeMode?.name.orEmpty()}",
                autoFocusSearch = drawerFocus,
                hiddenApps = hiddenApps,
                dragOut = drawerDrag,
                // Systemowa lista aplikacji (odinstalowywanie hurtem, uprawnienia, domyślne aplikacje).
                onOpenAppSettings = {
                    startActivitySafely(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS))
                },
                // Podczas przeciągania szuflada znika z oczu, ale zostaje w drzewie UI —
                // inaczej ikona, która "trzyma" gest palca, przestałaby istnieć.
                modifier = if (drawerHidden) Modifier.alpha(0f) else Modifier,
            )
        }

        // Przeciągana ikona i podświetlenie miejsca, gdzie wyląduje.
        draggedItem?.let { dragged ->
            val density = LocalDensity.current
            dragTarget?.let { spot ->
                val bounds = gridBounds ?: return@let
                val rect = spot.rect
                val folder = spot.folder ?: spot.mergeWith // "do środka" — mocniejsze podświetlenie
                // Zajęte pole: czerwone podświetlenie (ikona trafi wtedy w najbliższe wolne miejsce).
                // Folder z szuflady odsuwa sąsiadów, więc dla niego zajęte pole też jest "dobre".
                val free = folder != null || dragged is DragItem.Folder ||
                    pl.rafal.contextlauncher.layout.CardGrid.canPlace(rect, cardElements.filter { it.item.page == currentPage }.map { it.rect })
                with(density) {
                    Box(
                        Modifier
                            .offset { IntOffset((bounds.left + rect.x * gridCellW).roundToInt(), (bounds.top + rect.y * gridCellH).roundToInt()) }
                            .size((rect.w * gridCellW).toDp(), (rect.h * gridCellH).toDp())
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                (if (free) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    .copy(alpha = if (folder != null) 0.3f else 0.18f),
                            ),
                    )
                }
            }
            val iconPx = with(density) { 56.dp.toPx() }
            val floating = Modifier
                .offset { IntOffset((dragPos.x - iconPx / 2).roundToInt(), (dragPos.y - iconPx / 2).roundToInt()) }
                .graphicsLayer { scaleX = 1.15f; scaleY = 1.15f; alpha = 0.9f }
            when (dragged) {
                is DragItem.App -> Image(bitmap = dragged.app.icon, contentDescription = null, modifier = floating.size(56.dp))
                is DragItem.Folder -> FolderBadge(dragged.folder, dragged.preview, 56.dp, floating)
            }
        }

        // Ustawienia przykrywają kartę (ale nie kreator, który może się z nich otworzyć).
        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInVertically(Motion.panelOffset) { it / 4 } + fadeIn(Motion.fastOut()),
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
            enter = slideInVertically(Motion.panelOffset) { it / 4 } + fadeIn(Motion.fastOut()),
            exit = slideOutVertically { it / 4 } + fadeOut(),
        ) {
            AppRulesScreen(
                viewModel = viewModel,
                initialModeId = appRulesModeId,
                onClose = { appRulesOpen = false },
            )
        }

        // Samouczek gestów: po pierwszym kreatorze (gdy są już tryby), nad kartą, dopóki go nie obejrzysz / pominiesz.
        val tutorialDone by viewModel.settings.tutorialDone.flow.collectAsState()
        if (!tutorialDone && !wizardOpen && !needsOnboarding && modes.isNotEmpty() &&
            !settingsOpen && !appRulesOpen && !drawerOpen && !editing
        ) {
            TutorialOverlay(onFinish = { viewModel.settings.tutorialDone.set(true) })
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
            onBoardWallpaper = {
                wallpaperFor = mode.id
                boardWallpaper.launch(pl.rafal.stickonme.StickOnMe.wallpaperIntent(context))
            },
            onClearWallpaper = { viewModel.clearWallpaper(mode.id) },
            onCropWallpaper = { viewModel.openWallpaperCrop(mode.id) },
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
            layout = modeLayouts[mode.id] ?: pl.rafal.contextlauncher.data.ModeLayout.DEFAULT,
            globalIconCells = globalIconCells,
            onLayoutChange = { viewModel.setModeLayout(mode, it) },
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
            PlaceMapDialog(
                start = remember { viewModel.lastKnownPoint() },
                onConfirm = { label, lat, lon, radius ->
                    viewModel.addPlaceRule(mode, label, lat, lon, radius)
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
                text = { Text("Znikną jego karta, OnHand i reguły. Aplikacje i foldery zostaną.") },
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

    if (addAppsOpen) {
        AppMultiPickerDialog(
            allApps = installedApps,
            alreadySelected = cardElements.filterIsInstance<CardApp>().map { it.app.key }.toSet(),
            onConfirm = { apps ->
                viewModel.addAppsToActiveMode(apps)
                addAppsOpen = false
            },
            onDismiss = { addAppsOpen = false },
        )
    }

    if (addShortcutOpen) {
        ShortcutPickerDialog(
            shortcuts = shortcuts,
            apps = installedApps,
            hasAccess = remember { viewModel.hasShortcutAccess() },
            title = "Skrót na kartę ${activeMode?.name.orEmpty()}",
            onPick = { sc ->
                viewModel.addAppsToActiveMode(listOf(sc))
                addShortcutOpen = false
            },
            onDismiss = { addShortcutOpen = false },
        )
    }

    if (newModeOpen) {
        // Kreator (wywiad): cel → styl karty → wygląd → podgląd. "Pusty" cel daje dawny pusty tryb.
        ModeWizardDialog(
            installedApps = installedApps,
            existingNames = modes.map { it.name.lowercase() }.toSet(),
            onCreate = { plan, apps ->
                viewModel.createModeFromPlan(plan, apps)
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

    // Kadrowanie tapety (po wybraniu obrazu albo z "Dopasuj") — okno nad wszystkim, także nad Ustawieniami.
    val cropRequest by viewModel.wallpaperCrop.collectAsState()
    cropRequest?.let { request ->
        key(request) {
            WallpaperCropDialog(
                load = { viewModel.wallpaperPreview(request.modeId) },
                initial = remember { viewModel.wallpaperCropOf(request.modeId) },
                onSave = { viewModel.saveWallpaperCrop(request.modeId, it) },
                onDismiss = viewModel::closeWallpaperCrop,
            )
        }
    }

    stickerToEdit?.let { sticker ->
        // Bierzemy świeżą wersję widżetu z listy, żeby okno pokazywało aktualny obrót po każdej zmianie.
        val current = cardElements.filterIsInstance<CardCustomWidget>().firstOrNull { it.item.id == sticker.item.id } ?: sticker
        StickerDialog(
            rotation = current.config.optDouble("rotation", 0.0).toFloat(),
            flipped = current.config.optBoolean("flipped", false),
            actionLabel = TapAction.listFrom(current.config).describe(),
            onPickAction = {
                actionDraft = TapAction.listFrom(current.config)
                actionFor = current.item.id
                stickerToEdit = null
            },
            onChange = { rotation, flipped ->
                // Zachowujemy ścieżkę pliku, zmieniamy tylko obrót i odbicie.
                viewModel.updateStickerConfig(current) { it.put("rotation", rotation.toDouble()).put("flipped", flipped) }
            },
            onDismiss = { stickerToEdit = null },
            hasOriginal = current.config.optString("original").isNotBlank(),
            onRestore = { viewModel.restoreStickerOriginal(current) },
            legacyLook = current.config.optString("shape").let { it.isNotBlank() && it != "NONE" } ||
                current.config.optString("frame").let { it.isNotBlank() && it != "NONE" },
            onEditInStudio = current.config.optString("file").takeIf { it.isNotBlank() }?.let { file ->
                {
                    stickerInStudio = current.item.id
                    stickerToEdit = null
                    // Dawny kształt / ramka z karty (nazwy enumów) → te same w edytorze StickOnMe.
                    val shape = current.config.optString("shape").takeIf { it.isNotBlank() && it != "NONE" }?.lowercase()
                    val frame = current.config.optString("frame").takeIf { it.isNotBlank() && it != "NONE" }?.lowercase()
                    editInStudio.launch(pl.rafal.stickonme.StickOnMe.editIntent(context, file, shape, frame))
                }
            },
        )
    }

    customWidget(actionFor)?.let { widget ->
        TapActionDialog(
            actions = actionDraft,
            apps = installedApps,
            shortcuts = shortcuts,
            hasShortcutAccess = remember { viewModel.hasShortcutAccess() },
            modes = modes,
            onPickPhone = { sms ->
                phoneForSms = sms
                pickPhone.launch(
                    android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                )
            },
            onChange = { actionDraft = it },
            onSave = {
                viewModel.updateStickerConfig(widget) { it.withActions(actionDraft) }
                actionFor = null
            },
            onDismiss = { actionFor = null },
        )
    }

    customWidget(stackToManage)?.let { stack ->
        val data = StackData.of(stack.item.config)
        val byId = cardElements.associateBy { it.item.id }
        val shownMembers = data.members.mapNotNull { byId[it] }
        val topId = data.members.getOrNull(data.index)
        StackDialog(
            members = shownMembers,
            current = shownMembers.indexOfFirst { it.item.id == topId },
            labelOf = { member ->
                when (member) {
                    is CardCustomWidget -> member.kind.title
                    is CardWidget -> widgets.info(member.appWidgetId)?.loadLabel(context.packageManager) ?: "Widżet"
                    is CardApp -> member.app.label
                }
            },
            onShow = { i -> viewModel.setStackIndex(stack, data.members.indexOf(shownMembers[i].item.id)) },
            onMove = { member, delta -> viewModel.moveInStack(stack, member.item.id, delta) },
            onSettings = { member ->
                when {
                    member !is CardCustomWidget -> null
                    member.kind == CustomWidgetKind.STICKER -> ({ stackToManage = null; stickerToEdit = member })
                    else -> ({ stackToManage = null; widgetLookFor = member.item.id })
                }
            },
            onTakeOut = { member -> viewModel.unstack(stack, member.item.id) },
            swipe = data.swipe,
            onSwipe = { viewModel.setStackSwipe(stack, it) },
            onDissolve = {
                stackToManage = null
                viewModel.dissolveStack(stack)
            },
            onDismiss = { stackToManage = null },
        )
    }

    customWidget(widgetLookFor)?.let { widget ->
        val cfg = widget.config
        // Które widżety mają własne ustawienia treści (otwieramy je z okna wyglądu).
        val openContent: (() -> Unit)? = when (widget.kind) {
            CustomWidgetKind.MODE_NOTE -> ({ noteWidgetToEdit = widget })
            CustomWidgetKind.DUAL_CLOCK -> ({ clockToChange = widget })
            CustomWidgetKind.COUNTDOWN -> ({ countdownToEdit = widget.item.id })
            CustomWidgetKind.CARD_FOLDER -> ({ cardFolderPath = emptyList(); cardFolderOpen = widget.item.id })
            CustomWidgetKind.FOLDER -> cfg.optLong("folderId", -1).takeIf { it > 0 }?.let { id -> { folderSheetId = id } }
            else -> null
        }
        WidgetLookDialog(
            title = widget.kind.title,
            background = if (cfg.has("bg") && !cfg.isNull("bg")) cfg.getLong("bg") else null,
            opacity = cfg.optInt("alpha", -1).takeIf { it >= 0 },
            globalOpacity = viewModel.settings.widgetOpacity.value,
            onChange = { bg, alpha ->
                viewModel.updateStickerConfig(widget) { c ->
                    if (bg == null) c.remove("bg") else c.put("bg", bg)
                    if (alpha == null) c.remove("alpha") else c.put("alpha", alpha)
                }
            },
            onOpenContent = openContent?.let { open -> { widgetLookFor = null; open() } },
            onDismiss = { widgetLookFor = null },
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

    // Folder na karcie (z ikon): okno na środku. Gdy folder zniknie (rozwiązany, ostatnia ikona wyjęta), okno się zamyka.
    cardFolderOpen?.let { openId ->
        val widget = cardElements.firstOrNull { it.item.id == openId } as? CardCustomWidget
        if (widget == null) {
            LaunchedEffect(openId) { cardFolderOpen = null }
        } else {
            val data = remember(widget.item.config) { CardFolderData.of(widget.item.config) }
            CardFolderDialog(
                root = data,
                lookup = { appsByKey[it] },
                launchCounts = launchCounts,
                allApps = installedApps,
                modes = modes,
                currentModeId = activeMode?.id,
                shortcuts = shortcuts,
                hasShortcutAccess = remember { viewModel.hasShortcutAccess() },
                startPath = cardFolderPath,
                atBottom = folderAtBottom,
                actions = CardFolderActions(
                    onLaunch = { app ->
                        cardFolderOpen = null
                        viewModel.launch(app)
                    },
                    onUpdate = { change -> viewModel.updateCardFolder(widget, change) },
                    onTakeOut = { path, app, toCard -> viewModel.takeOutOfCardFolder(widget, app, toCard, path) },
                    onAppInfo = viewModel::openAppInfo,
                    onUninstall = { app -> uninstall(app) },
                    onDissolve = {
                        cardFolderOpen = null
                        viewModel.dissolveCardFolder(widget)
                    },
                    onSaveToDrawer = viewModel::saveCardFolderToDrawer,
                    onCopyToMode = { folder, modeId -> viewModel.copyCardFolderToMode(folder, modeId, widget.item.w, widget.item.h) },
                ),
                onDismiss = { cardFolderOpen = null },
            )
        }
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

    if (widgetPickerOpen) {
        WidgetPickerSheet(
            providers = widgetProviders,
            appIcon = { pkg -> installedApps.firstOrNull { it.packageName == pkg }?.icon },
            onPick = { provider ->
                widgetPickerOpen = false
                onAddWidget(provider)
            },
            onPickCustom = { kind ->
                widgetPickerOpen = false
                when (kind) {
                    CustomWidgetKind.DUAL_CLOCK -> addingClock = true         // najpierw wybór strefy
                    CustomWidgetKind.FOLDER -> addingFolderWidget = true      // najpierw wybór folderu
                    // Pusty folder tylko dla tego trybu; "keep" = nie znika sam, gdy zostanie w nim jedna aplikacja.
                    CustomWidgetKind.CARD_FOLDER -> viewModel.addCustomWidget(kind, CardFolderData("Folder", null, null, emptyList(), keep = true).toJson())
                    // Nowa lista i odliczanie od razu z sensownym stanem początkowym.
                    CustomWidgetKind.CHECKLIST -> viewModel.addCustomWidget(kind, Checklist.of("Lista"))
                    CustomWidgetKind.COUNTDOWN -> viewModel.addCustomWidget(kind, Countdown.of(""))
                    CustomWidgetKind.STICKER -> pickFromStudio.launch(pl.rafal.stickonme.StickOnMe.pickIntent(context)) // wybór albo nowa w StickOnMe
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
    modeKey: Long?, // zmiana = inny tryb → karta "oddycha" (pomniejszenie i powrót)
    editing: Boolean,
    header: @Composable () -> Unit, // slot: miejsce na dowolny komponent (jak ContentPresenter w XAML)
    prompt: @Composable () -> Unit, // pływająca podpowiedź nad dolnym paskiem
    modeSwitcher: @Composable () -> Unit,
    leftHanded: Boolean,
    elements: List<CardElement>, // wszystkie strony; każda strona bierze swoje (item.page)
    page: Int,
    pageCount: Int,
    usedPageCount: Int, // strony z elementami (reszta to pusta strona "na zapas" w edycji)
    maxPages: Int,
    onPageChange: (Int) -> Unit,
    onMoveToPage: (CardElement, Int, GridRect) -> Unit,
    animatePageChange: () -> Boolean,
    stackMembers: (CardCustomWidget) -> List<CardElement>,
    onStack: (CardElement, CardElement) -> Unit,
    onStackIndex: (CardCustomWidget, Int) -> Unit,
    widgets: LauncherWidgets,
    onLaunch: (CardApp) -> Unit,
    menuFor: (CardApp) -> List<MenuAction>,
    onLayout: (Map<Long, GridRect>) -> Unit,
    onRemove: (CardElement) -> Unit,
    onUninstall: (CardApp) -> Unit,
    onLongPressItem: () -> Unit,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    customWidget: @Composable (CardCustomWidget, Boolean) -> Unit,
    onGridPlaced: (Rect, Float, Float) -> Unit,
    onEmptyLongPress: () -> Unit,
    onSwipeDown: (rightSide: Boolean) -> Unit,
    onDropIntoFolder: (CardApp, CardCustomWidget) -> Unit,
    onMergeApps: (CardApp, CardApp) -> Unit,
    onConfigure: (CardCustomWidget) -> Unit,
    canConfigure: (CardCustomWidget) -> Boolean,
    gap: androidx.compose.ui.unit.Dp,
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
                var startX = 0f
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        total = 0f
                        startX = offset.x
                    },
                    onDragEnd = {
                        if (total < -120f) onOpenDrawer(false) // ujemne = ruch w górę → szuflada
                        // W dół: lewa połowa ekranu → powiadomienia, prawa → szybkie ustawienia (jak w nowym Androidzie).
                        else if (total > 120f) onSwipeDown(startX > size.width / 2f)
                    },
                ) { _, dragAmount -> total += dragAmount }
            },
    ) {
        Spacer(Modifier.height(4.dp))

        // --- Strony karty: własny "pager" ---
        // Nie HorizontalPager, bo ten przycina zawartość do swoich granic, a pasek "Usuń z karty" i przeciągany
        // element wychodzą poza stronę. Tu strony to warstwy przesunięte o translationX (bez przycinania).
        val scope = rememberCoroutineScope()
        val swipe = remember { Animatable(0f) }                // przesunięcie palcem w pikselach
        var shown by remember { mutableIntStateOf(page) }       // strona na ekranie (VM dogania ją asynchronicznie)
        var tapTarget by remember { mutableIntStateOf(-1) } // strona wybrana kropkami — to przejście animujemy
        var dragPage by remember { mutableIntStateOf(-1) }  // strona, na której trwa przeciąganie elementu
        var widthPx by remember { mutableFloatStateOf(1f) }
        val pageGapPx = with(LocalDensity.current) { 24.dp.toPx() } // odstęp między stronami przy przesuwaniu
        val lastPage = (pageCount - 1).coerceAtLeast(0)
        // Zmiana z zewnątrz (przeniesienie elementu na inną stronę, Home, inny tryb) — płynnie przewijamy.
        LaunchedEffect(page) {
            if (page == shown) return@LaunchedEffect
            val d = page - shown
            // Animujemy tylko przejście o jedną stronę zlecone przez VM (przeniesienie elementu).
            // Home, inny tryb, porządkowanie numerów stron → od razu, bez przesuwania przez obce strony.
            val animate = (animatePageChange() || tapTarget == page) && kotlin.math.abs(d) == 1
            tapTarget = -1
            try {
                if (animate) swipe.animateTo(-d * (widthPx + pageGapPx), Motion.page())
            } finally {
                // Także gdy palec przerwał animację: przesunięcie przeliczamy względem nowej strony, bez skoku obrazu.
                shown = page
                swipe.snapTo(if (animate) swipe.value + d * (widthPx + pageGapPx) else 0f)
            }
        }
        // Mniej stron (koniec edycji z pustą stroną na zapas) → cofamy się na ostatnią istniejącą.
        LaunchedEffect(lastPage) {
            if (shown > lastPage) {
                shown = lastPage
                onPageChange(lastPage)
            }
        }
        val currentOnPageChange by rememberUpdatedState(onPageChange)
        val currentPageProp by rememberUpdatedState(page)
        // Siatka ma na każdej stronie ten sam kształt, więc granice liczymy względem nieprzesuwanego kontenera
        // (x z kontenera, y i rozmiar z siatki) — dzięki temu upuszczanie z szuflady trafia w dobre pola.
        val placed = remember { arrayOfNulls<Rect>(2) } // [0] = kontener, [1] = ostatnia siatka
        val cells = remember { FloatArray(2) }
        val currentOnGridPlaced by rememberUpdatedState(onGridPlaced)
        fun emitGrid() {
            val box = placed[0] ?: return
            val grid = placed[1] ?: return
            val left = box.left + (box.width - grid.width) / 2f
            currentOnGridPlaced(Rect(left, grid.top, left + grid.width, grid.bottom), cells[0], cells[1])
        }

        // Zmiana trybu: nowa karta pojawia się z lekkiego pomniejszenia i przygaszenia (sprężyście).
        // Tylko prawdziwa zmiana trybu (nie start aplikacji, gdy tryb dopiero się wczytuje z null).
        val modeIn = remember { Animatable(1f) }
        var lastModeKey by remember { mutableStateOf(modeKey) }
        LaunchedEffect(modeKey) {
            val previous = lastModeKey
            lastModeKey = modeKey
            if (previous != null && previous != modeKey) {
                modeIn.snapTo(0f)
                modeIn.animateTo(1f, Motion.mode())
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned {
                    widthPx = it.size.width.toFloat().coerceAtLeast(1f)
                    placed[0] = it.boundsInRoot()
                    emitGrid()
                }
                // Przesunięcie w bok zmienia stronę. Przeciąganie elementów w edycji zużywa ruch (consume),
                // więc wtedy strona się nie przesuwa — działa tylko na pustym miejscu albo poza edycją.
                .pointerInput(lastPage) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = widthPx * 0.2f
                            val dir = when {
                                swipe.value < -threshold && shown < lastPage -> 1
                                swipe.value > threshold && shown > 0 -> -1
                                else -> 0
                            }
                            scope.launch {
                                if (dir == 0) {
                                    currentOnPageChange(shown) // na wszelki wypadek wyrównujemy stronę z VM
                                    swipe.animateTo(0f, Motion.page())
                                } else {
                                    val target = shown + dir
                                    try {
                                        swipe.animateTo(-dir * (widthPx + pageGapPx), Motion.page())
                                    } finally {
                                        // Jak wyżej: nawet przerwane przejście kończy się na nowej stronie, bez skoku.
                                        shown = target
                                        swipe.snapTo(swipe.value + dir * (widthPx + pageGapPx))
                                        currentOnPageChange(target)
                                    }
                                }
                            }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f, Motion.page()) } },
                    ) { change, dx ->
                        change.consume()
                        // Na pierwszej/ostatniej stronie tylko lekki opór (jak gumka), bez przejścia.
                        val atEdge = (swipe.value + dx > 0 && shown == 0) || (swipe.value + dx < 0 && shown == lastPage)
                        scope.launch { swipe.snapTo(swipe.value + if (atEdge) dx / 4f else dx) }
                    }
                }
                // Na końcu łańcucha: skala animacji nie zmienia granic zgłaszanych wyżej (upuszczanie, siatka).
                .graphicsLayer {
                    val v = modeIn.value
                    alpha = 0.3f + 0.7f * v
                    scaleX = 0.94f + 0.06f * v
                    scaleY = 0.94f + 0.06f * v
                },
        ) {
            // Rysujemy bieżącą stronę i sąsiednie (widać je tylko w trakcie przesuwania).
            // (bez "continue" w pętli composable — zakres od razu przycięty do istniejących stron)
            val center = shown.coerceIn(0, lastPage) // gdy stron ubyło, zanim efekt wyżej poprawi "shown"
            // Strona, z której coś przeciągamy, zostaje narysowana (i na wierzchu), nawet gdy przewinęliśmy o 2+ strony —
            // inaczej zniknąłby przeciągany element razem z gestem.
            val drawn = ((center - 1).coerceAtLeast(0)..(center + 1).coerceAtMost(lastPage)).toMutableList()
            if (dragPage in 0..lastPage && dragPage !in drawn) drawn += dragPage
            for (p in drawn) {
                key(p) {
                    val onPage = remember(elements, p) { elements.filter { it.item.page == p } }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (p == dragPage) 1f else 0f)
                            .graphicsLayer { translationX = (p - center) * (widthPx + pageGapPx) + swipe.value },
                    ) {
                        if (onPage.isEmpty()) {
                            Text(
                                text = when {
                                    p > 0 && editing -> "Pusta strona.\nPrzeciągnij tu element (przytrzymaj go przy krawędzi poprzedniej strony) albo dodaj coś przyciskami na dole."
                                    p > 0 -> ""
                                    else -> "Tryb ${modeName.orEmpty()} jest pusty.\nPrzeciągnij aplikacje z szuflady albo przytrzymaj puste miejsce, aby dodać widżet."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                            )
                        }
                        CardGridView(
                            elements = onPage,
                            editing = editing,
                            widgets = widgets,
                            onLaunch = onLaunch,
                            menuFor = menuFor,
                            onLayout = onLayout,
                            onRemove = onRemove,
                            onUninstall = onUninstall,
                            onLongPressItem = onLongPressItem,
                            customWidget = customWidget,
                            modifier = Modifier.fillMaxSize(),
                            onGridPlaced = { bounds, cellW, cellH ->
                                // Tylko widoczna strona: sąsiednie leżą poza ekranem (granice mogłyby wyjść puste).
                                if (p == shown) {
                                    placed[1] = bounds
                                    cells[0] = cellW
                                    cells[1] = cellH
                                    emitGrid()
                                }
                            },
                            onEmptyLongPress = onEmptyLongPress,
                            onDropIntoFolder = onDropIntoFolder,
                            onMergeApps = onMergeApps,
                            onConfigure = onConfigure,
                            canConfigure = canConfigure,
                            gap = gap,
                            onMoveToPage = onMoveToPage,
                            // W prawo można też założyć nową stronę (do limitu); w lewo — tylko gdy jest dokąd.
                            // "o ile stron dalej" od tej strony: musi istnieć (albo być pustą stroną na zapas w edycji) i mieścić się w limicie.
                            canMoveToPage = { delta -> (p + delta) in 0..lastPage && p + delta < maxPages },
                            onEdgeDwell = { delta ->
                                val target = p + delta
                                if (target in 0..lastPage && target < maxPages) {
                                    tapTarget = target // przejście z animacją
                                    currentOnPageChange(target)
                                    true
                                } else false
                            },
                            pageShift = { d -> (p + d - shown.coerceIn(0, lastPage)) * (widthPx + pageGapPx) + swipe.value },
                            onDraggingChange = { active ->
                                if (active) dragPage = p else if (dragPage == p) dragPage = -1
                            },
                            stackMembers = stackMembers,
                            onStack = onStack,
                            onStackIndex = onStackIndex,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
            ) { prompt() }
        }

        // Kropki stron w odstępie nad dolnym paskiem (bez zabierania miejsca siatce).
        // Kropki da się dotknąć (i przesunąć po nich palcem) — tak zmienisz stronę także wtedy, gdy strona jest
        // pełna i nie ma pustego miejsca na przesunięcie. W edycji ostatnia pusta strona "na zapas" ma znak +.
        // Pole dotyku (24 dp) wystaje poza 8-dp pasek — siatka nie traci przez to wysokości.
        Box(Modifier.fillMaxWidth().height(8.dp), contentAlignment = Alignment.Center) {
            if (pageCount > 1) {
                val dotSlot = 22.dp
                val slotPx = with(LocalDensity.current) { dotSlot.toPx() }
                fun goTo(i: Int) {
                    val target = i.coerceIn(0, lastPage)
                    // Porównujemy z docelową stroną (VM), nie z "shown", które dogania ją dopiero po animacji.
                    if (target != currentPageProp) {
                        tapTarget = target
                        currentOnPageChange(target)
                    }
                }
                // Tylko w edycji: wtedy strona bywa pełna (każdy dotyk łapie element). Poza edycją przesuwa się po ikonach,
                // a pole kropek nie zabiera dotyku dolnym ikonom.
                Row(
                    Modifier
                        .requiredHeight(24.dp)
                        .then(
                            if (!editing) Modifier
                            else Modifier
                                .pointerInput(pageCount) {
                                    detectTapGestures { pos -> goTo((pos.x / slotPx).toInt()) }
                                }
                                .pointerInput(pageCount) {
                                    detectHorizontalDragGestures { change, _ ->
                                        change.consume()
                                        goTo((change.position.x / slotPx).toInt())
                                    }
                                },
                        )
                        .semantics { contentDescription = "Strona ${shown + 1} z $pageCount — dotknij kropki, aby zmienić" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pageCount) { i ->
                        Box(Modifier.size(dotSlot, 24.dp), contentAlignment = Alignment.Center) {
                            val spare = editing && i == pageCount - 1 && i >= usedPageCount
                            if (spare) {
                                Text(
                                    "+",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (i == shown) 1f else 0.6f),
                                )
                            } else {
                                // Aktywna kropka rozciąga się w "pigułkę" (płynnie przy zmianie strony).
                                val dotW by animateDpAsState(if (i == shown) 16.dp else 5.dp, Motion.page(), label = "kropka strony")
                                Box(
                                    Modifier
                                        .size(width = dotW, height = if (i == shown) 6.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i == shown) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (editing) {
            // Pasek narzędzi edycji zajmuje miejsce dolnego paska (ta sama wysokość), więc siatka
            // nie zmienia liczby rzędów i nic nie zasłania górnego rzędu.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { header() }
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

// Gdzie wyląduje to, co przeciągamy z szuflady: do folderu, na ikonę (nowy folder) albo na wolne pole.
private data class DropSpot(val folder: CardCustomWidget?, val mergeWith: CardApp?, val rect: GridRect)
