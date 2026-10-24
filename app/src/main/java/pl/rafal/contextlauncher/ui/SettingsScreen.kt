package pl.rafal.contextlauncher.ui

import android.Manifest
import android.app.Activity
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
import androidx.compose.runtime.rememberCoroutineScope
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
import pl.rafal.onthemes.ThemeMode
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.StickOnMeBridge
import kotlinx.coroutines.launch

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
    val autoDelay by prefs.autoDelaySec.flow.collectAsState()
    val autoCountdown by prefs.autoCountdown.flow.collectAsState()
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
    val drawerDefault by viewModel.drawerDefault.collectAsState() // 1.7.0
    val usageSource by viewModel.usageSource.collectAsState()    // 1.7.1
    val widgetCorner by prefs.widgetCorner.flow.collectAsState()
    val wallpaperDim by prefs.wallpaperDim.flow.collectAsState()
    val widgetOpacity by prefs.widgetOpacity.flow.collectAsState()
    val wallpaperVersion by viewModel.wallpaperVersion.collectAsState()
    val pickDefaultWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setWallpaper(null, uri)
    }
    val refreshTick by viewModel.refreshTick.collectAsState() // odświeża statusy uprawnień po powrocie z ustawień

    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    // Teksty chipów czytamy tu, bo lambdy labelOf nie są @Composable.
    val backCd = stringResource(R.string.set_back_cd)
    val noneLabel = stringResource(R.string.common_none)
    val lblReturnHome = stringResource(R.string.set_return_home)
    val lblReturnPrevious = stringResource(R.string.set_return_previous)
    val lblLeftHanded = stringResource(R.string.set_left_handed)
    val lblRightHanded = stringResource(R.string.set_right_handed)
    val lblIconSmall = stringResource(R.string.set_icon_small)
    val lblIconLarge = stringResource(R.string.set_icon_large)
    val lblDimLight = stringResource(R.string.set_dim_light)
    val lblDimStrong = stringResource(R.string.set_dim_strong)
    val lblBgFull = stringResource(R.string.set_widget_bg_full)
    val lblBgSemi = stringResource(R.string.set_widget_bg_semi)
    val lblBgClear = stringResource(R.string.set_widget_bg_clear)
    val lblTextSmall = stringResource(R.string.set_text_small)
    val lblTextNormal = stringResource(R.string.set_text_normal)
    val lblTextLarge = stringResource(R.string.set_text_large)
    val lblCornerSmall = stringResource(R.string.set_corner_small)
    val lblCornerMedium = stringResource(R.string.set_corner_medium)
    val lblCornerLarge = stringResource(R.string.set_corner_large)

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
            Toast.makeText(context, context.getString(R.string.set_open_settings_failed), Toast.LENGTH_SHORT).show()
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
                    .semantics { contentDescription = backCd },
            ) { Text("←", style = MaterialTheme.typography.titleLarge) }
            Text(stringResource(R.string.common_settings), style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(Modifier.padding(horizontal = 20.dp)) {
            // --- Ogólne ---
            item { Section(stringResource(R.string.set_section_general)) }
            item {
                Choice(
                    title = stringResource(R.string.set_home_mode_title),
                    subtitle = stringResource(R.string.set_home_mode_sub),
                    options = listOf<Long?>(null) + modes.map { it.id },
                    selected = homeModeId,
                    labelOf = { id -> modes.firstOrNull { it.id == id }?.name ?: noneLabel },
                    onSelect = { id -> viewModel.setHomeMode(modes.firstOrNull { it.id == id }) },
                )
            }
            item {
                Choice(
                    title = stringResource(R.string.set_return_title),
                    options = listOf(true, false),
                    selected = returnToHome,
                    labelOf = { if (it) lblReturnHome else lblReturnPrevious },
                    onSelect = prefs.returnToHome::set,
                )
            }
            item {
                Choice(
                    title = stringResource(R.string.set_hand_title),
                    subtitle = stringResource(R.string.set_hand_sub),
                    options = listOf(false, true),
                    selected = leftHanded,
                    labelOf = { if (it) lblLeftHanded else lblRightHanded },
                    onSelect = prefs.leftHanded::set,
                )
            }

            // --- Tryby i sugestie ---
            item { Section(stringResource(R.string.set_section_modes)) }
            item {
                Toggle(
                    title = stringResource(R.string.set_auto_switch_title),
                    subtitle = stringResource(R.string.set_auto_switch_sub),
                    checked = autoSwitch,
                    onChange = prefs.autoSwitch::set,
                )
            }
            // 1.6.2: ile automat czeka i jak pokazuje odliczanie (widoczne tylko z włączonym automatem).
            if (autoSwitch) {
                item {
                    Choice(
                        title = stringResource(R.string.set_auto_delay_title),
                        options = pl.rafal.contextlauncher.data.AppPrefs.AUTO_DELAY_OPTIONS,
                        selected = autoDelay,
                        labelOf = { sec -> if (sec == 0) pl.rafal.contextlauncher.AppText.get(R.string.set_auto_delay_now) else pl.rafal.contextlauncher.AppText.get(R.string.set_auto_delay_sec, sec) },
                        onSelect = prefs.autoDelaySec::set,
                    )
                }
                item {
                    Choice(
                        title = stringResource(R.string.set_auto_countdown_title),
                        options = pl.rafal.contextlauncher.data.AutoCountdown.entries,
                        selected = autoCountdown,
                        labelOf = { it.label },
                        onSelect = prefs.autoCountdown::set,
                    )
                }
            }
            item {
                Choice(
                    title = stringResource(R.string.set_dismiss_title),
                    options = DismissDuration.entries,
                    selected = dismissDuration,
                    labelOf = { it.label },
                    onSelect = prefs.dismissDuration::set,
                )
            }
            item {
                Toggle(
                    title = stringResource(R.string.set_apply_phone_title),
                    subtitle = stringResource(R.string.set_apply_phone_sub),
                    checked = applyPhone,
                    onChange = prefs.applyPhoneSettings::set,
                )
            }

            // --- 1.11.0: KeepMeOn ---
            item { Section(stringResource(R.string.set_section_keepmeon)) }
            item { KeepMeOnSettings(viewModel) }

            // --- Wygląd ---
            item { Section(stringResource(R.string.set_section_appearance)) }
            item {
                Choice(
                    title = stringResource(R.string.set_theme_title),
                    options = ThemeMode.entries,
                    selected = themeMode,
                    labelOf = { it.label },
                    onSelect = viewModel::setThemeMode,
                )
            }
            item {
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.set_default_scheme_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.set_default_scheme_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PaletteRow(selected = defaultPalette, onSelect = { it?.let(viewModel::setDefaultPalette) }, allowDefault = false)
                    // Kreator i wszystkie motywy mieszkają w OnThemes (moduł :themes, ikona w szufladzie).
                    TextButton(onClick = { context.startActivity(pl.rafal.onthemes.OnThemes.editIntent(context)) }) {
                        Text(stringResource(R.string.set_theme_creator))
                    }
                    TextButton(onClick = { context.startActivity(pl.rafal.onthemes.OnThemes.openIntent(context)) }) {
                        Text(stringResource(R.string.set_open_onthemes))
                    }
                }
            }
            item {
                Toggle(
                    title = stringResource(R.string.set_uniform_title),
                    subtitle = stringResource(R.string.set_uniform_sub),
                    checked = uniformLook,
                    onChange = prefs.uniformLook::set,
                )
            }
            item {
                Choice(
                    title = stringResource(R.string.set_icon_size_title),
                    subtitle = stringResource(R.string.set_icon_size_sub),
                    options = listOf(1, 2),
                    selected = appIconCells,
                    labelOf = { if (it == 1) lblIconSmall else lblIconLarge },
                    onSelect = viewModel::setAppIconCells,
                )
            }
            item {
                Choice(
                    title = stringResource(R.string.set_pages_title),
                    subtitle = stringResource(R.string.set_pages_sub),
                    options = listOf(1, 2, 3, 4, 5),
                    selected = maxPages,
                    labelOf = { "$it" },
                    onSelect = prefs.maxPages::set,
                )
            }
            item { Toggle(title = stringResource(R.string.set_show_labels), checked = showLabels, onChange = prefs.showLabels::set) }
            item { Toggle(title = stringResource(R.string.set_folder_labels), checked = showFolderLabels, onChange = prefs.showFolderLabels::set) }
            item {
                Toggle(
                    title = stringResource(R.string.set_folder_bottom_title),
                    subtitle = stringResource(R.string.set_folder_bottom_sub),
                    checked = folderAtBottom,
                    onChange = prefs.folderAtBottom::set,
                )
            }
            // 1.7.0: szuflada aplikacji — ustawienia ogólne (OnMode może mieć własne w swoich ustawieniach).
            item { Section(stringResource(R.string.dset_title)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.dset_global_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DrawerSettingsSection(settings = drawerDefault, onChange = viewModel::setDrawerDefault)
                }
            }
            // 1.7.1: skąd "najczęściej używane" (w szufladzie i w folderach) bierze dane.
            item {
                val usageAccess = remember(refreshTick) { viewModel.hasUsageAccess() }
                Choice(
                    title = stringResource(R.string.set_usage_source_title),
                    subtitle = if (usageSource == pl.rafal.contextlauncher.data.UsageSource.SYSTEM && !usageAccess)
                        stringResource(R.string.set_usage_need_access) else stringResource(R.string.set_usage_source_sub),
                    options = pl.rafal.contextlauncher.data.UsageSource.entries.toList(),
                    selected = usageSource,
                    labelOf = { it.label },
                    onSelect = { source ->
                        viewModel.setUsageSource(source)
                        // Bez dostępu od razu prowadzimy do ekranu systemu, gdzie się go nadaje.
                        if (source == pl.rafal.contextlauncher.data.UsageSource.SYSTEM && !viewModel.hasUsageAccess()) {
                            open(viewModel.usageAccessIntent())
                        }
                    },
                )
            }
            item {
                Toggle(
                    title = stringResource(R.string.set_dots_title),
                    subtitle = stringResource(R.string.set_dots_sub),
                    checked = notificationDots,
                    onChange = prefs.notificationDots::set,
                )
            }
            item {
                Toggle(
                    title = stringResource(R.string.set_wallpaper_title),
                    subtitle = stringResource(R.string.set_wallpaper_sub),
                    checked = showWallpaper,
                    onChange = prefs.showWallpaper::set,
                )
            }
            if (showWallpaper) {
                item {
                    Choice(
                        title = stringResource(R.string.set_dim_title),
                        options = listOf(0, 35, 60),
                        selected = wallpaperDim,
                        labelOf = { when (it) { 0 -> noneLabel; 35 -> lblDimLight; else -> lblDimStrong } },
                        onSelect = prefs.wallpaperDim::set,
                    )
                }
            }
            item {
                val hasDefault = remember(wallpaperVersion) { viewModel.wallpaperPath(null) != null }
                Column {
                Action(
                    title = if (hasDefault) stringResource(R.string.set_default_wp_change) else stringResource(R.string.set_default_wp_pick),
                    subtitle = stringResource(R.string.set_default_wp_sub),
                    onClick = { pickDefaultWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                if (hasDefault) {
                    Action(
                        title = stringResource(R.string.set_default_wp_adjust),
                        subtitle = stringResource(R.string.set_default_wp_adjust_sub),
                        onClick = { viewModel.openWallpaperCrop(null) },
                    )
                    Action(title = stringResource(R.string.set_default_wp_remove), onClick = { viewModel.clearWallpaper(null) })
                }
                }
            }
            item {
                Choice(
                    title = stringResource(R.string.set_widget_bg_title),
                    options = listOf(100, 60, 0),
                    selected = widgetOpacity,
                    labelOf = { when (it) { 100 -> lblBgFull; 60 -> lblBgSemi; else -> lblBgClear } },
                    onSelect = prefs.widgetOpacity::set,
                )
            }

            // --- Uprawnienia ---
            item { Section(stringResource(R.string.set_section_permissions)) }
            item {
                // remember(refreshTick): statusy czytamy na nowo po każdym powrocie do launchera.
                val status = remember(refreshTick) { viewModel.permissionStatus() }
                Column {
                    PermissionRow(stringResource(R.string.set_perm_default_home), status.isDefaultLauncher) { open(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    PermissionRow(stringResource(R.string.set_perm_calendar), status.calendar) {
                        requestPermission.launch(Manifest.permission.READ_CALENDAR)
                    }
                    PermissionRow(stringResource(R.string.set_perm_location), status.location) {
                        requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PermissionRow(stringResource(R.string.set_perm_nearby), status.bluetooth) {
                            requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }
                    PermissionRow(stringResource(R.string.set_perm_dnd), status.dnd) { open(viewModel.dndAccessIntent()) }
                    PermissionRow(stringResource(R.string.set_perm_write_settings), status.writeSettings) { open(viewModel.writeSettingsIntent()) }
                    PermissionRow(stringResource(R.string.set_perm_usage), remember(refreshTick) { viewModel.hasUsageAccess() }) {
                        open(viewModel.usageAccessIntent())
                    }
                    PermissionRow(stringResource(R.string.set_perm_notifications), status.notificationDots) {
                        open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    TextButton(onClick = {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text(stringResource(R.string.set_perm_all)) }
                }
            }

            // --- Aplikacje ---
            item { Section(stringResource(R.string.set_section_apps)) }
            item {
                Action(
                    title = stringResource(R.string.set_app_rules_title),
                    subtitle = stringResource(R.string.set_app_rules_sub),
                    onClick = onOpenAppRules,
                )
            }

            // --- Kopia i skróty ---
            item { Section(stringResource(R.string.set_section_backup)) }
            item {
                Action(
                    title = stringResource(R.string.set_export_title),
                    subtitle = stringResource(R.string.set_export_sub),
                ) { exportChoice = true }
            }
            item {
                Action(
                    title = stringResource(R.string.set_import_title),
                    subtitle = stringResource(R.string.set_import_sub),
                ) { importFile.launch(arrayOf("application/zip", "application/json", "*/*")) }
            }
            item { StickOnMeMigration() } // widoczne tylko, gdy w launcherze zostały naklejki / tablice sprzed 1.9.0
            item {
                Toggle(
                    title = stringResource(R.string.set_shortcuts_title),
                    subtitle = stringResource(R.string.set_shortcuts_sub),
                    checked = shortcuts,
                    onChange = viewModel::setModeShortcuts,
                )
            }

            // --- Zaawansowane ---
            item { Section(stringResource(R.string.set_section_advanced)) }
            item {
                Choice(
                    title = stringResource(R.string.set_label_size_title),
                    subtitle = stringResource(R.string.set_label_size_sub),
                    options = listOf(85, 100, 115),
                    selected = labelScale,
                    labelOf = { when (it) { 85 -> lblTextSmall; 100 -> lblTextNormal; else -> lblTextLarge } },
                    onSelect = prefs.labelScale::set,
                )
            }
            item {
                Choice(
                    title = stringResource(R.string.set_icon_shape_title),
                    options = IconShape.entries,
                    selected = IconShape.of(iconShape),
                    labelOf = { it.label },
                    onSelect = { prefs.iconShape.set(it.name) },
                )
            }
            item {
                Choice(
                    title = stringResource(R.string.set_widget_corner_title),
                    options = listOf(0, 12, 20, 28),
                    selected = widgetCorner,
                    labelOf = { when (it) { 0 -> noneLabel; 12 -> lblCornerSmall; 20 -> lblCornerMedium; else -> lblCornerLarge } },
                    onSelect = prefs.widgetCorner::set,
                )
            }
            item { Action(title = stringResource(R.string.set_wizard_title), subtitle = stringResource(R.string.set_wizard_sub), onClick = onOpenWizard) }
            item {
                Action(
                    title = stringResource(R.string.set_tutorial_title),
                    subtitle = stringResource(R.string.set_tutorial_sub),
                    onClick = { prefs.tutorialDone.set(false) },
                )
            }
            item { Action(title = stringResource(R.string.set_clear_stats_title), subtitle = stringResource(R.string.set_clear_stats_sub), onClick = viewModel::clearLaunchStats) }
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
            title = { Text(stringResource(R.string.set_export_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        exportChoice = false
                        exportZip.launch("launchonme-kopia.zip")
                    }) { Text(stringResource(R.string.set_export_full)) }
                    TextButton(onClick = {
                        exportChoice = false
                        exportFile.launch("launchonme-ustawienia.json")
                    }) { Text(stringResource(R.string.set_export_settings_only)) }
                }
            },
            confirmButton = { TextButton(onClick = { exportChoice = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.set_import_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.set_import_confirm_text),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importFrom(uri)
                    confirmImport = null
                }) { Text(stringResource(R.string.set_import_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text(stringResource(R.string.common_cancel)) } },
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

// 1.11.0: KeepMeOn — tryb na słabą baterię. Bez trybu: jedno „Utwórz”; z trybem: próg, blokada i co wyłączyć.
@Composable
private fun KeepMeOnSettings(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val prefs = viewModel.settings
    val modes by viewModel.modes.collectAsState()
    val keepMeOnMode by viewModel.keepMeOnMode.collectAsState()
    val active by viewModel.keepMeOnActive.collectAsState()
    val battery by prefs.keepMeOnBattery.flow.collectAsState()
    val auto by prefs.keepMeOnAuto.flow.collectAsState()
    val lock by prefs.keepMeOnLock.flow.collectAsState()
    val noMotion by prefs.keepMeOnNoMotion.flow.collectAsState()
    val noWallpaper by prefs.keepMeOnNoWallpaper.flow.collectAsState()
    val pauseWidgets by prefs.keepMeOnPauseWidgets.flow.collectAsState()
    Column {
        val mode = keepMeOnMode
        if (mode == null) {
            Action(
                title = stringResource(R.string.set_keepmeon_create_title),
                subtitle = stringResource(R.string.set_keepmeon_create_sub),
                onClick = { viewModel.createKeepMeOn() },
            )
        } else {
            if (!active) {
                Action(
                    title = stringResource(R.string.set_keepmeon_now_title, mode.name),
                    subtitle = stringResource(R.string.set_keepmeon_now_sub),
                    onClick = { viewModel.createKeepMeOn(activate = true) },
                )
            }
            // Zablokowany i aktywny KeepMeOn: zmiana trybu ani wyłączenie blokady nie są stąd dostępne
            // (inaczej Ustawienia omijałyby odcisk palca) — najpierw wyjście z KeepMeOn.
            val guarded = active && lock
            if (!guarded) Choice(
                title = stringResource(R.string.set_keepmeon_mode_title),
                options = modes.map { it.id },
                selected = mode.id,
                labelOf = { id -> modes.firstOrNull { it.id == id }?.name.orEmpty() },
                onSelect = { id -> viewModel.setKeepMeOnMode(modes.firstOrNull { it.id == id }) },
            )
            Choice(
                title = stringResource(R.string.set_keepmeon_battery_title),
                subtitle = stringResource(R.string.set_keepmeon_battery_sub),
                options = pl.rafal.contextlauncher.data.AppPrefs.KEEPMEON_BATTERY_OPTIONS,
                selected = battery,
                labelOf = { p -> if (p == 0) pl.rafal.contextlauncher.AppText.get(R.string.set_keepmeon_battery_off) else "$p%" },
                onSelect = viewModel::setKeepMeOnBattery,
            )
            Toggle(
                title = stringResource(R.string.set_keepmeon_auto_title),
                subtitle = stringResource(R.string.set_keepmeon_auto_sub),
                checked = auto,
                onChange = prefs.keepMeOnAuto::set,
            )
            if (!guarded) Toggle(
                title = stringResource(R.string.set_keepmeon_lock_title),
                subtitle = stringResource(R.string.set_keepmeon_lock_sub),
                checked = lock,
                onChange = prefs.keepMeOnLock::set,
            )
            Toggle(
                title = stringResource(R.string.set_keepmeon_motion_title),
                checked = noMotion,
                onChange = prefs.keepMeOnNoMotion::set,
            )
            Toggle(
                title = stringResource(R.string.set_keepmeon_wallpaper_title),
                checked = noWallpaper,
                onChange = prefs.keepMeOnNoWallpaper::set,
            )
            Toggle(
                title = stringResource(R.string.set_keepmeon_widgets_title),
                subtitle = stringResource(R.string.set_keepmeon_widgets_sub),
                checked = pauseWidgets,
                onChange = prefs.keepMeOnPauseWidgets::set,
            )
        }
        // Systemowego oszczędzania baterii launcher sam nie włączy (wymaga uprawnienia nadawanego przez adb),
        // więc tylko otwieramy jego ekran w ustawieniach telefonu.
        Action(
            title = stringResource(R.string.set_keepmeon_system_title),
            subtitle = stringResource(R.string.set_keepmeon_system_sub),
            onClick = {
                runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
            },
        )
    }
}

// Do 1.8 StickOnMe było częścią launchera, więc jego naklejki i tablice zostały w pamięci launchera.
// Jedno dotknięcie: paczka .zip → StickOnMe (osobna aplikacja) rozpakowuje u siebie → tu usuwamy starą kopię.
@Composable
private fun StickOnMeMigration() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var legacy by remember { mutableStateOf(StickOnMeBridge.hasLegacyLibrary(context)) }
    var working by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        working = false
        if (result.resultCode == Activity.RESULT_OK) scope.launch {
            StickOnMeBridge.clearLegacyLibrary(context)
            legacy = false
        }
    }
    if (legacy) {
        val installed = remember(working) { StickOnMeBridge.isInstalled(context) }
        Action(
            title = stringResource(R.string.set_stickonme_move_title),
            subtitle = stringResource(if (installed) R.string.set_stickonme_move_sub else R.string.set_stickonme_missing_sub),
        ) {
            if (!StickOnMeBridge.isInstalled(context)) {
                Toast.makeText(context, context.getString(R.string.set_stickonme_missing_sub), Toast.LENGTH_LONG).show()
            } else if (!working) {
                working = true
                scope.launch {
                    val started = runCatching { importer.launch(StickOnMeBridge.migrationIntent(context)) }.isSuccess
                    if (!started) {
                        working = false
                        Toast.makeText(context, context.getString(R.string.set_stickonme_move_failed), Toast.LENGTH_SHORT).show()
                    }
                }
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
        if (!granted) TextButton(onClick = onGrant) { Text(stringResource(R.string.set_grant)) }
    }
}
