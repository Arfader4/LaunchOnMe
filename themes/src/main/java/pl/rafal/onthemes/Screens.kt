package pl.rafal.onthemes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Ekrany OnThemes: galeria (lista motywów i trybów), ekran motywu (podgląd na żywo) i kreator motywu własnego.

// ——— Galeria ———
@Composable
internal fun GalleryScreen(
    store: ThemeStore,
    mode: ThemeMode,
    global: ThemeSpec,
    dark: Boolean,
    modes: List<HostMode>?,
    onOpen: (String) -> Unit,
    onNewCustom: () -> Unit,
    onPickForMode: (HostMode) -> Unit,
) {
    val sheen by store.luxurySheen.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ot_app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Hint(stringResource(R.string.ot_subtitle))
            }
        }
        // Podgląd bieżącego wyglądu (motyw globalny + Twoje tryby).
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LauncherPreview(global.look(dark), modes.orEmpty(), sheen)
            }
        }

        item { SectionTitle(stringResource(R.string.ot_brightness)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = m == mode, onClick = { store.setThemeMode(m) }, label = { Text(m.label) })
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle(stringResource(R.string.ot_themes))
                Hint(stringResource(R.string.ot_themes_hint))
            }
        }
        // Karty po catalogId: wszystkie warianty Luxury to jedna karta (pokazuje wariant globalny, jeśli jest).
        items(Themes.all, key = { it.catalogId }) { base ->
            val isGlobal = base.catalogId == global.catalogId
            val shown = if (isGlobal) global else base
            ThemeCard(spec = shown, dark = dark, selected = isGlobal, onClick = { onOpen(shown.id) })
        }
        item {
            OutlinedButton(onClick = onNewCustom, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ot_new_custom))
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle(stringResource(R.string.ot_modes))
                Hint(stringResource(R.string.ot_modes_hint))
            }
        }
        if (modes != null && modes.isEmpty()) {
            item { Hint(stringResource(R.string.ot_modes_empty)) }
        }
        if (modes != null) {
            items(modes, key = { it.id }) { m -> ModeRow(m, global, dark, onClick = { onPickForMode(m) }) }
        }
    }
}

// ——— Ekran motywu ———
@Composable
internal fun ThemeDetailScreen(
    store: ThemeStore,
    startId: String,
    dark: Boolean,
    modes: List<HostMode>?,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (ThemeSpec, Boolean) -> Unit,
    onModesChanged: () -> Unit,
) {
    val global by store.defaultTheme.collectAsState()
    val sheen by store.luxurySheen.collectAsState()
    // Luxury: metal i bazę można przymierzać na podglądzie, zanim się je zastosuje.
    var spec by remember(startId) { mutableStateOf(Themes.find(startId) ?: Themes.NIGHT) }
    var previewDark by remember(startId) { mutableStateOf(dark) }
    var applyOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isGlobal = spec == global

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        BackRow(spec.label, onBack)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LauncherPreview(spec.look(previewDark), modes.orEmpty(), sheen)
        }
        // Przełącznik podglądu (nie zmienia ustawień); AMOLED jest zawsze ciemny.
        if (!spec.darkOnly && spec.family != ThemeFamily.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                FilterChip(selected = !previewDark, onClick = { previewDark = false }, label = { Text(stringResource(R.string.ot_mode_light)) })
                FilterChip(selected = previewDark, onClick = { previewDark = true }, label = { Text(stringResource(R.string.ot_mode_dark)) })
            }
        }
        SectionTitle(stringResource(R.string.ot_palette))
        PaletteDots(spec.palette, 22)
        if (spec.family == ThemeFamily.LUXURY) LuxuryPicker(store, spec, onChange = { spec = it })

        Button(onClick = { store.setDefaultTheme(spec) }, enabled = !isGlobal, modifier = Modifier.fillMaxWidth()) {
            Text(if (isGlobal) stringResource(R.string.ot_is_global) else stringResource(R.string.ot_use_global))
        }
        OutlinedButton(onClick = { applyOpen = true }, enabled = modes != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ot_apply_modes))
        }
        if (spec.family == ThemeFamily.CUSTOM) {
            OutlinedButton(onClick = { onEdit(spec.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ot_edit)) }
        }
        OutlinedButton(onClick = { onDuplicate(spec, previewDark) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ot_duplicate))
        }
        if (spec.family == ThemeFamily.CUSTOM) {
            TextButton(onClick = { deleteOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ot_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (applyOpen && modes != null) {
        ApplyToModesDialog(
            spec = spec,
            modes = modes,
            onConfirm = { changes ->
                applyOpen = false
                scope.launch {
                    val host = OnThemes.host
                    if (host != null) {
                        changes.forEach { (id, themeId) -> host.setModeTheme(id, themeId) }
                    }
                    onModesChanged()
                }
            },
            onDismiss = { applyOpen = false },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.ot_delete_title, spec.label)) },
            text = { Text(stringResource(R.string.ot_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteOpen = false
                    store.deleteCustom(spec.id)
                    onModesChanged()
                    onBack()
                }) { Text(stringResource(R.string.ot_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.ot_cancel)) } },
        )
    }
}

@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ——— Kreator motywu własnego ———
// Gotowe punkty wyjścia (te same co w dawnym kreatorze launchera) — potem każdy kolor można zmienić.
private val Starters = listOf(
    R.string.ot_starter_mint to CustomColors(0xFF10151A, 0xFF1A2229, 0xFF4DF5CD, 0xFFE8EEF0),
    R.string.ot_starter_navy to CustomColors(0xFF0B1220, 0xFF141E33, 0xFF6EA8FE, 0xFFE6ECF7),
    R.string.ot_starter_burgundy to CustomColors(0xFF1A0E12, 0xFF2A161C, 0xFFE0736B, 0xFFF3E6E8),
    R.string.ot_starter_paper to CustomColors(0xFFF5F1E8, 0xFFFFFFFF, 0xFF8B5A2B, 0xFF2A2118),
    R.string.ot_starter_snow to CustomColors(0xFFF7F9FC, 0xFFFFFFFF, 0xFF2563EB, 0xFF111827),
)
private val BackgroundPresets = listOf(0xFF000000, 0xFF0F1012, 0xFF10151A, 0xFF0B1220, 0xFF1A0E12, 0xFF14120F, 0xFFF5F1E8, 0xFFF7F9FC, 0xFFFFFFFF)
private val SurfacePresets = listOf(0xFF0D0D0D, 0xFF1A1C20, 0xFF1A2229, 0xFF141E33, 0xFF2A161C, 0xFF24201A, 0xFFFFFFFF, 0xFFEFEAE0, 0xFFE8EDF5)
private val TextPresets = listOf(0xFFFFFFFF, 0xFFECEAE4, 0xFFE8EEF0, 0xFFD0D0D0, 0xFF111111, 0xFF2A2118, 0xFF1F2937)

// draft = szkic (nowy albo kopia istniejącego). Zapis podmienia/dodaje motyw w ThemeStore.
@Composable
internal fun CustomEditorScreen(
    store: ThemeStore,
    initial: CustomThemeDef,
    modes: List<HostMode>?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val defaultName = stringResource(R.string.ot_theme_custom)
    val c = draft.colors
    val look = PreviewLook(
        roles = c.roles(),
        dark = Color(c.background).luminance() < 0.5f,
        badge = draft.badge,
        metal = null,
        palette = sortByHue(listOf(c.accent) + AllColors.take(5)),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        BackRow(stringResource(R.string.ot_editor_title), onBack)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LauncherPreview(look, modes.orEmpty(), sheen = false)
        }
        OutlinedTextField(
            value = draft.name.orEmpty(),
            onValueChange = { draft = draft.copy(name = it.take(30)) },
            label = { Text(stringResource(R.string.ot_editor_name)) },
            placeholder = { Text(defaultName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SectionTitle(stringResource(R.string.ot_editor_starters))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Starters.forEach { (name, colors) ->
                TextButton(onClick = { draft = draft.copy(colors = colors) }) { Text(stringResource(name)) }
            }
        }
        ColorRow(stringResource(R.string.ot_editor_background), BackgroundPresets, c.background) { draft = draft.copy(colors = c.copy(background = it)) }
        ColorRow(stringResource(R.string.ot_editor_surfaces), SurfacePresets, c.surface) { draft = draft.copy(colors = c.copy(surface = it)) }
        ColorRow(stringResource(R.string.ot_editor_accent), AllColors, c.accent) { draft = draft.copy(colors = c.copy(accent = it)) }
        ColorRow(stringResource(R.string.ot_editor_text), TextPresets, c.text) { draft = draft.copy(colors = c.copy(text = it)) }
        val bgLight = Color(c.background).luminance() > 0.5f
        val textLight = Color(c.text).luminance() > 0.5f
        if (bgLight == textLight) {
            Text(stringResource(R.string.ot_editor_low_contrast), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        SectionTitle(stringResource(R.string.ot_editor_badges))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                BadgeStyle.TINTED to R.string.ot_badge_tinted,
                BadgeStyle.INVERTED to R.string.ot_badge_inverted,
                BadgeStyle.MONO to R.string.ot_badge_mono,
            ).forEach { (style, label) ->
                FilterChip(selected = draft.badge == style, onClick = { draft = draft.copy(badge = style) }, label = { Text(stringResource(label)) })
            }
        }
        Spacer(Modifier.size(4.dp))
        Button(
            onClick = {
                val clean = draft.copy(name = draft.name?.trim()?.takeIf { it.isNotEmpty() })
                store.saveCustom(clean)
                onSaved(clean.id)
            },
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.ot_save)) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ot_cancel)) }
    }
}

// Rząd kolorów z podpisem: gotowe + "+" (dowolny kolor). Wybrany spoza listy pokazuje się na początku.
@Composable
private fun ColorRow(title: String, presets: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    Text(title, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        val list = if (selected in presets) presets else listOf(selected) + presets
        list.forEach { c ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (c == selected) 3.dp else 1.dp,
                        color = if (c == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(c) },
            ) {
                if (c == selected) {
                    Text("✓", color = if (Color(c).luminance() > 0.5f) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.8f, 1f) }))
                .clickable { pickerOpen = true },
        ) { Text("+", color = Color.Black, style = MaterialTheme.typography.labelLarge) }
    }
    if (pickerOpen) {
        HsvColorDialog(initial = selected, onPick = { onSelect(it); pickerOpen = false }, onDismiss = { pickerOpen = false })
    }
}
