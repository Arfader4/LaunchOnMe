package pl.rafal.contextlauncher.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.DismissDuration
import pl.rafal.contextlauncher.ui.theme.ThemeMode

// Ekran Ustawień: pełnoekranowa nakładka z sekcjami. Korzysta bezpośrednio z ViewModelu,
// bo to "centrum sterowania" całą aplikacją, a nie komponent do wielokrotnego użytku.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LauncherViewModel,
    onOpenWizard: () -> Unit,
    onOpenAppRules: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = viewModel.settings
    val modes by viewModel.modes.collectAsState()
    val homeModeId by viewModel.homeModeId.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val defaultPalette by viewModel.defaultPalette.collectAsState()
    val leftHanded by prefs.leftHanded.flow.collectAsState()
    val returnToHome by prefs.returnToHome.flow.collectAsState()
    val autoSwitch by prefs.autoSwitch.flow.collectAsState()
    val applyPhone by prefs.applyPhoneSettings.flow.collectAsState()
    val dismissDuration by prefs.dismissDuration.flow.collectAsState()
    val uniformLook by prefs.uniformLook.flow.collectAsState()
    val showLabels by prefs.showLabels.flow.collectAsState()
    val shortcuts by prefs.modeShortcuts.flow.collectAsState()
    val showWallpaper by prefs.showWallpaper.flow.collectAsState()
    val notificationDots by prefs.notificationDots.flow.collectAsState()
    val iconShape by prefs.iconShape.flow.collectAsState()
    val appIconCells by prefs.appIconCells.flow.collectAsState()
    val maxPages by prefs.maxPages.flow.collectAsState()
    val labelScale by prefs.labelScale.flow.collectAsState()
    val showFolderLabels by prefs.showFolderLabels.flow.collectAsState()
    val folderAtBottom by prefs.folderAtBottom.flow.collectAsState()
    val widgetCorner by prefs.widgetCorner.flow.collectAsState()
    val wallpaperDim by prefs.wallpaperDim.flow.collectAsState()
    val widgetOpacity by prefs.widgetOpacity.flow.collectAsState()
    val wallpaperVersion by viewModel.wallpaperVersion.collectAsState()
    val pickDefaultWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setWallpaper(null, uri)
    }
    val refreshTick by viewModel.refreshTick.collectAsState() // odświeża statusy uprawnień po powrocie z ustawień

    var confirmImport by remember { mutableStateOf<Uri?>(null) }

    // Zapis i odczyt pliku przez systemowy wybór miejsca (bez uprawnień do całej pamięci).
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    // Pełna kopia: ustawienia + pliki (naklejki, tapety trybów, StickOnMe) w jednym .zip.
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportTo(uri, withFiles = true)
    }
    var exportChoice by remember { mutableStateOf(false) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Nie udało się otworzyć ustawień", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .semantics { contentDescription = "Wróć" },
            ) { Text("←", style = MaterialTheme.typography.titleLarge) }
            Text("Ustawienia", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(Modifier.padding(horizontal = 20.dp)) {
            // --- Ogólne ---
            item { Section("Ogólne") }
            item {
                Choice(
                    title = "Strona główna",
                    subtitle = "Tryb, do którego wracasz po zakończeniu innego.",
                    options = listOf<Long?>(null) + modes.map { it.id },
                    selected = homeModeId,
                    labelOf = { id -> modes.firstOrNull { it.id == id }?.name ?: "Brak" },
                    onSelect = { id -> viewModel.setHomeMode(modes.firstOrNull { it.id == id }) },
                )
            }
            item {
                Choice(
                    title = "Po zakończeniu trybu wracaj do",
                    options = listOf(true, false),
                    selected = returnToHome,
                    labelOf = { if (it) "Strony głównej" else "Poprzedniego trybu" },
                    onSelect = prefs.returnToHome::set,
                )
            }
            item {
                Choice(
                    title = "Ręka",
                    subtitle = "Po której stronie ekranu jest przycisk trybu.",
                    options = listOf(false, true),
                    selected = leftHanded,
                    labelOf = { if (it) "Leworęczny" else "Praworęczny" },
                    onSelect = prefs.leftHanded::set,
                )
            }

            // --- Tryby i sugestie ---
            item { Section("Tryby i sugestie") }
            item {
                Toggle(
                    title = "Przełączaj tryby automatycznie",
                    subtitle = "Zamiast pytać. Nie dotyczy trybów z opcją „zawsze pytaj” i przez 30 min po ręcznej zmianie trybu.",
                    checked = autoSwitch,
                    onChange = prefs.autoSwitch::set,
                )
            }
            item {
                Choice(
                    title = "Odrzuconą sugestię ukrywaj na",
                    options = DismissDuration.entries,
                    selected = dismissDuration,
                    labelOf = { it.label },
                    onSelect = prefs.dismissDuration::set,
                )
            }
            item {
                Toggle(
                    title = "Stosuj ustawienia telefonu",
                    subtitle = "Dźwięk, Nie przeszkadzać, jasność itd. przy włączaniu trybu.",
                    checked = applyPhone,
                    onChange = prefs.applyPhoneSettings::set,
                )
            }

            // --- Wygląd ---
            item { Section("Wygląd") }
            item {
                Choice(
                    title = "Jasność",
                    options = ThemeMode.entries,
                    selected = themeMode,
                    labelOf = { it.label },
                    onSelect = viewModel::setThemeMode,
                )
            }
            item {
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Domyślny schemat", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Dla nowych trybów i trybów bez własnego schematu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PaletteRow(selected = defaultPalette, onSelect = { it?.let(viewModel::setDefaultPalette) }, allowDefault = false)
                    var creatorOpen by remember { mutableStateOf(false) }
                    TextButton(onClick = { creatorOpen = true }) { Text("🎨 Kreator własnego schematu…") }
                    if (creatorOpen) {
                        ThemeCreatorDialog(
                            initial = pl.rafal.contextlauncher.ui.theme.CustomTheme.colors,
                            onSave = {
                                viewModel.saveCustomTheme(it)
                                creatorOpen = false
                            },
                            onDismiss = { creatorOpen = false },
                        )
                    }
                }
            }
            item {
                Toggle(
                    title = "Jednolity wygląd",
                    subtitle = "Wszystkie tryby w domyślnym schemacie, bez własnych kolorów.",
                    checked = uniformLook,
                    onChange = prefs.uniformLook::set,
                )
            }
            item {
                Choice(
                    title = "Rozmiar ikon aplikacji na karcie",
                    subtitle = "Mała ikona zajmuje jedną kratkę — mieści się dwa razy więcej aplikacji.",
                    options = listOf(1, 2),
                    selected = appIconCells,
                    labelOf = { if (it == 1) "Małe (1×1)" else "Duże (2×2)" },
                    onSelect = viewModel::setAppIconCells,
                )
            }
            item {
                Choice(
                    title = "Liczba stron karty",
                    subtitle = "Ile stron może mieć karta trybu (przesuwasz w bok). W edycji przytrzymaj przeciągany element przy krawędzi — strona się przewinie (za ostatnią powstaje nowa).",
                    options = listOf(1, 2, 3, 4, 5),
                    selected = maxPages,
                    labelOf = { "$it" },
                    onSelect = prefs.maxPages::set,
                )
            }
            item { Toggle(title = "Podpisy pod ikonami aplikacji", checked = showLabels, onChange = prefs.showLabels::set) }
            item { Toggle(title = "Nazwy folderów na widżetach", checked = showFolderLabels, onChange = prefs.showFolderLabels::set) }
            item {
                Toggle(
                    title = "Folder otwiera się u dołu",
                    subtitle = "Bliżej kciuka (jak w One UI). Wyłączone: okno na środku ekranu.",
                    checked = folderAtBottom,
                    onChange = prefs.folderAtBottom::set,
                )
            }
            item {
                Toggle(
                    title = "Kropki powiadomień",
                    subtitle = "Wymaga dostępu do powiadomień (sekcja Uprawnienia). Treści powiadomień nie są czytane.",
                    checked = notificationDots,
                    onChange = prefs.notificationDots::set,
                )
            }
            item {
                Toggle(
                    title = "Tapeta w tle launchera",
                    subtitle = "Zamiast jednolitego koloru widać tapetę telefonu (lub tapetę trybu).",
                    checked = showWallpaper,
                    onChange = prefs.showWallpaper::set,
                )
            }
            if (showWallpaper) {
                item {
                    Choice(
                        title = "Przyciemnienie tapety",
                        options = listOf(0, 35, 60),
                        selected = wallpaperDim,
                        labelOf = { when (it) { 0 -> "Brak"; 35 -> "Lekkie"; else -> "Mocne" } },
                        onSelect = prefs.wallpaperDim::set,
                    )
                }
            }
            item {
                val hasDefault = remember(wallpaperVersion) { viewModel.wallpaperPath(null) != null }
                Column {
                Action(
                    title = if (hasDefault) "Tapeta domyślna: zmień" else "Tapeta domyślna: wybierz",
                    subtitle = "Dla trybów bez własnej tapety (własną ustawisz w ustawieniach trybu).",
                    onClick = { pickDefaultWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                if (hasDefault) {
                    Action(
                        title = "Tapeta domyślna: dopasuj",
                        subtitle = "Powiększ i przesuń obraz, jak w systemowym wyborze tapety.",
                        onClick = { viewModel.openWallpaperCrop(null) },
                    )
                    Action(title = "Usuń tapetę domyślną", onClick = { viewModel.clearWallpaper(null) })
                }
                }
            }
            item {
                Choice(
                    title = "Tło widżetów",
                    options = listOf(100, 60, 0),
                    selected = widgetOpacity,
                    labelOf = { when (it) { 100 -> "Pełne"; 60 -> "Półprzezroczyste"; else -> "Przezroczyste" } },
                    onSelect = prefs.widgetOpacity::set,
                )
            }

            // --- Uprawnienia ---
            item { Section("Uprawnienia") }
            item {
                // remember(refreshTick): statusy czytamy na nowo po każdym powrocie do launchera.
                val status = remember(refreshTick) { viewModel.permissionStatus() }
                Column {
                    PermissionRow("Domyślny ekran główny", status.isDefaultLauncher) { open(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    PermissionRow("Kalendarz (reguły kalendarza)", status.calendar) {
                        requestPermission.launch(Manifest.permission.READ_CALENDAR)
                    }
                    PermissionRow("Lokalizacja (Wi-Fi, miejsca, pogoda)", status.location) {
                        requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PermissionRow("Urządzenia w pobliżu (Bluetooth)", status.bluetooth) {
                            requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }
                    PermissionRow("Tryb Nie przeszkadzać", status.dnd) { open(viewModel.dndAccessIntent()) }
                    PermissionRow("Modyfikowanie ustawień systemu", status.writeSettings) { open(viewModel.writeSettingsIntent()) }
                    PermissionRow("Dostęp do powiadomień (kropki na ikonach)", status.notificationDots) {
                        open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    TextButton(onClick = {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text("Wszystkie uprawnienia aplikacji") }
                }
            }

            // --- Aplikacje ---
            item { Section("Aplikacje") }
            item {
                Action(
                    title = "Blokowanie i ukrywanie",
                    subtitle = "Które aplikacje są zablokowane albo ukryte w poszczególnych trybach i w nowych trybach.",
                    onClick = onOpenAppRules,
                )
            }

            // --- Kopia i skróty ---
            item { Section("Kopia i skróty") }
            item {
                Action(
                    title = "Eksportuj konfigurację",
                    subtitle = "Pełna kopia (.zip, z naklejkami, tapetami i StickOnMe) albo same ustawienia (.json).",
                ) { exportChoice = true }
            }
            item {
                Action(
                    title = "Importuj konfigurację",
                    subtitle = "Zastępuje obecną (.zip albo .json). Widżety innych aplikacji trzeba dodać ponownie.",
                ) { importFile.launch(arrayOf("application/zip", "application/json", "*/*")) }
            }
            item {
                Toggle(
                    title = "Skróty trybów",
                    subtitle = "„Włącz tryb …” dla innych aplikacji: procedury One UI, Tasker, tagi NFC, menu ikony w innym launcherze.",
                    checked = shortcuts,
                    onChange = viewModel::setModeShortcuts,
                )
            }

            // --- Zaawansowane ---
            item { Section("Zaawansowane") }
            item {
                Choice(
                    title = "Rozmiar tekstu podpisów",
                    subtitle = "Przy małych ikonach tekst i tak zmniejsza się, żeby się zmieścił.",
                    options = listOf(85, 100, 115),
                    selected = labelScale,
                    labelOf = { when (it) { 85 -> "Mały"; 100 -> "Normalny"; else -> "Duży" } },
                    onSelect = prefs.labelScale::set,
                )
            }
            item {
                Choice(
                    title = "Kształt ikon trybów",
                    options = IconShape.entries,
                    selected = IconShape.of(iconShape),
                    labelOf = { it.label },
                    onSelect = { prefs.iconShape.set(it.name) },
                )
            }
            item {
                Choice(
                    title = "Zaokrąglenie widżetów",
                    options = listOf(0, 12, 20, 28),
                    selected = widgetCorner,
                    labelOf = { when (it) { 0 -> "Brak"; 12 -> "Małe"; 20 -> "Średnie"; else -> "Duże" } },
                    onSelect = prefs.widgetCorner::set,
                )
            }
            item { Action(title = "Kreator trybów", subtitle = "Dodaj tryby z gotowych szablonów.", onClick = onOpenWizard) }
            item {
                Action(
                    title = "Pokaż samouczek gestów",
                    subtitle = "Szuflada, edycja układu, foldery, stosy i strony — pojawi się po zamknięciu Ustawień.",
                    onClick = { prefs.tutorialDone.set(false) },
                )
            }
            item { Action(title = "Wyczyść „często używane”", subtitle = "Statystyki uruchomień we wszystkich trybach.", onClick = viewModel::clearLaunchStats) }
            item {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                }
                Text(
                    "LaunchOnMe ${version.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }

    if (exportChoice) {
        AlertDialog(
            onDismissRequest = { exportChoice = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Eksport konfiguracji") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        exportChoice = false
                        exportZip.launch("launchonme-kopia.zip")
                    }) { Text("Pełna kopia (.zip) — z naklejkami, tapetami trybów i StickOnMe") }
                    TextButton(onClick = {
                        exportChoice = false
                        exportFile.launch("launchonme-ustawienia.json")
                    }) { Text("Same ustawienia (.json) — mały plik, bez obrazów") }
                }
            },
            confirmButton = { TextButton(onClick = { exportChoice = false }) { Text("Anuluj") } },
        )
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Zastąpić obecną konfigurację?") },
            text = {
                Text(
                    "Obecne tryby, karty, reguły i foldery zostaną zastąpione zawartością pliku. " +
                        "Z pełnej kopii (.zip) wrócą też naklejki i tapety trybów, a biblioteka StickOnMe zostanie uzupełniona.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importFrom(uri)
                    confirmImport = null
                }) { Text("Importuj") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Anuluj") } },
        )
    }
}

// --- Klocki ekranu ustawień ---

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(labelOf(option)) })
            }
        }
    }
}

@Composable
private fun Action(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Text(
            if (granted) "✓" else "✗",
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (!granted) TextButton(onClick = onGrant) { Text("Nadaj") }
    }
}
