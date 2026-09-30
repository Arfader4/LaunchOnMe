package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import pl.rafal.contextlauncher.data.ModeLayout
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.ModePhoneSettings
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.contextlauncher.data.db.SuggestionRuleEntity
import pl.rafal.contextlauncher.suggest.Suggestion
import pl.rafal.contextlauncher.suggest.SuggestionEngine
import pl.rafal.contextlauncher.suggest.parseTimeOrNull
import pl.rafal.contextlauncher.suggest.toRule
import pl.rafal.contextlauncher.ui.theme.AccentColors
import pl.rafal.contextlauncher.ui.theme.Palette
import java.time.DayOfWeek
import java.time.LocalTime

// --- Baner sugestii na karcie ---

private data class BannerText(val color: Long, val title: String, val subtitle: String, val action: String)

@Composable
fun SuggestionBanner(
    suggestion: Suggestion,
    modes: List<ModeEntity>,
    activeMode: ModeEntity?,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val text = when (suggestion) {
        is Suggestion.SwitchTo -> modes.firstOrNull { it.id == suggestion.modeId }?.let { target ->
            BannerText(
                color = target.color,
                title = "Przełączyć na ${target.name}?",
                subtitle = suggestion.reason.replaceFirstChar { it.uppercase() },
                action = "Przełącz",
            )
        }
        is Suggestion.EndMode -> modes.firstOrNull { it.id == suggestion.backToModeId }?.let { back ->
            BannerText(
                color = back.color,
                title = "Zakończyć tryb ${activeMode?.name.orEmpty()}?",
                subtitle = "Żadna z jego reguł już nie pasuje",
                action = "Wróć do ${back.name}",
            )
        }
    } ?: return // tryb z sugestii mógł zostać usunięty

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color(text.color).copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        ModeDot(text.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onAccept) { Text(text.action) }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "Odrzuć sugestię" },
        ) { Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium) }
    }
}

// --- Ustawienia trybu: reguły, nazwa, usuwanie ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSettingsSheet(
    mode: ModeEntity,
    rules: List<SuggestionRuleEntity>,
    onAddTimeRule: () -> Unit,
    onAddCalendarRule: () -> Unit,
    onAddBluetoothRule: () -> Unit,
    onAddWifiRule: () -> Unit,
    onAddPlaceRule: () -> Unit,
    onAddChargingRule: () -> Unit,
    onAddHeadphonesRule: () -> Unit,
    onAddBatteryRule: () -> Unit,
    wallpaperSet: Boolean,
    wallpaperOnLock: Boolean,
    onPickWallpaper: () -> Unit,
    onBoardWallpaper: () -> Unit,   // tapeta z tablicy StickOnMe (kolaż, własna grafika)
    onClearWallpaper: () -> Unit,
    onCropWallpaper: () -> Unit,
    onWallpaperLockChange: (Boolean) -> Unit,
    phoneSettings: ModePhoneSettings,
    hasDndAccess: Boolean,
    canWriteSettings: Boolean,
    onPhoneSettingsChange: (ModePhoneSettings) -> Unit,
    onGrantDnd: () -> Unit,
    onGrantWrite: () -> Unit,
    onDeleteRule: (SuggestionRuleEntity) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAppearanceChange: (icon: String?, color: Long, palette: Palette?, accent: Long?) -> Unit,
    alwaysAsk: Boolean,                    // wyjątek od automatycznego przełączania
    onAlwaysAskChange: (Boolean) -> Unit,
    appRulesSummary: String,               // np. "3 zablokowane, 1 ukryta"
    onManageApps: () -> Unit,
    onDismiss: () -> Unit,
    layout: ModeLayout = ModeLayout.DEFAULT,   // układ karty tego trybu
    globalIconCells: Int = 2,
    onLayoutChange: (ModeLayout) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState()) // arkusz jest długi: wygląd + reguły
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            val icon = ModeIcon.of(mode.icon)
            val palette = Palette.fromName(mode.palette)

            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeBadge(mode, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(mode.name, style = MaterialTheme.typography.titleLarge)
            }

            // Każda zmiana zapisuje się od razu, a karta za arkuszem przemalowuje się na żywo.
            Text("Ikona", style = MaterialTheme.typography.titleSmall)
            IconPicker(selected = icon, color = mode.color) { onAppearanceChange(it.key, mode.color, palette, mode.accent) }

            Text("Kolor ikony", style = MaterialTheme.typography.titleSmall)
            ColorSwatches(colors = ModeColors, selected = mode.color, modeBadge = true, onSelect = { c ->
                if (c != null) onAppearanceChange(mode.icon, c, palette, mode.accent)
            })

            Text("Schemat kolorów", style = MaterialTheme.typography.titleSmall)
            PaletteRow(selected = palette, onSelect = { onAppearanceChange(mode.icon, mode.color, it, mode.accent) }, allowDefault = true)

            Text("Kolor główny", style = MaterialTheme.typography.titleSmall)
            ColorSwatches(
                colors = AccentColors,
                selected = mode.accent,
                onSelect = { onAppearanceChange(mode.icon, mode.color, palette, it) },
                allowNone = true, // "A" = kolor ze schematu
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))

            // Układ karty tylko tego trybu (np. "Praca" gęsto z małymi ikonami, "Dom" luźno z dużymi).
            Text("Układ karty", style = MaterialTheme.typography.titleSmall)
            Text("Rozmiar ikon aplikacji", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = layout.iconCells == null,
                    onClick = { onLayoutChange(layout.copy(iconCells = null)) },
                    label = { Text("Jak w Ustawieniach (${globalIconCells}×$globalIconCells)") },
                )
                FilterChip(selected = layout.iconCells == 1, onClick = { onLayoutChange(layout.copy(iconCells = 1)) }, label = { Text("1×1") })
                FilterChip(selected = layout.iconCells == 2, onClick = { onLayoutChange(layout.copy(iconCells = 2)) }, label = { Text("2×2") })
            }
            Text("Odstępy między elementami", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(0 to "Brak", 4 to "Małe", 8 to "Średnie", 14 to "Duże").forEach { (gap, label) ->
                    FilterChip(selected = layout.gap == gap, onClick = { onLayoutChange(layout.copy(gap = gap)) }, label = { Text(label) })
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))

            Text("Kiedy podpowiadać ten tryb", style = MaterialTheme.typography.titleSmall)
            Text(
                "Gdy któraś reguła pasuje, w nagłówku pojawi się podpowiedź ✓ / ✕ albo — jeśli w Ustawieniach włączono automat — tryb przełączy się sam.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Wyjątek od automatu: ten tryb zawsze tylko podpowiadamy banerem.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onAlwaysAskChange(!alwaysAsk) }
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Zawsze pytaj przed włączeniem", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Automat nie włączy ani nie wyłączy tego trybu bez Ciebie",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = alwaysAsk, onCheckedChange = onAlwaysAskChange)
            }

            rules.forEach { rule ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .heightIn(min = 52.dp)
                        .padding(start = 14.dp),
                ) {
                    Text(
                        rule.toRule()?.let { SuggestionEngine.describe(it) }?.replaceFirstChar { it.uppercase() } ?: "Nieznana reguła",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onDeleteRule(rule) }
                            .semantics { contentDescription = "Usuń regułę" },
                    ) { Text("×", style = MaterialTheme.typography.titleMedium) }
                }
            }

            // Rodzaje reguł w dwóch rzędach, żeby zmieściły się na wąskim ekranie.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onAddTimeRule, label = { Text("+ Pora dnia") })
                AssistChip(onClick = onAddCalendarRule, label = { Text("+ Kalendarz") })
                AssistChip(onClick = onAddPlaceRule, label = { Text("+ Miejsce") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onAddBluetoothRule, label = { Text("+ Bluetooth") })
                AssistChip(onClick = onAddWifiRule, label = { Text("+ Wi-Fi") })
                AssistChip(onClick = onAddHeadphonesRule, label = { Text("+ Słuchawki") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onAddChargingRule, label = { Text("+ Ładowanie") })
                AssistChip(onClick = onAddBatteryRule, label = { Text("+ Bateria") })
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))

            // Tapeta ustawiana przy włączeniu trybu (tryby bez własnej dostają domyślną z Ustawień).
            Text("Tapeta trybu", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                AssistChip(onClick = onPickWallpaper, label = { Text(if (wallpaperSet) "Zmień obraz" else "Wybierz obraz") })
                AssistChip(onClick = onBoardWallpaper, label = { Text("Z tablicy StickOnMe") })
                if (wallpaperSet) AssistChip(onClick = onCropWallpaper, label = { Text("Dopasuj") })
                if (wallpaperSet) AssistChip(onClick = onClearWallpaper, label = { Text("Usuń") })
            }
            if (wallpaperSet) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWallpaperLockChange(!wallpaperOnLock) },
                ) {
                    Text("Też na ekranie blokady", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = wallpaperOnLock, onCheckedChange = onWallpaperLockChange)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))

            // Aplikacje zablokowane i ukryte w tym trybie — szczegóły na osobnym ekranie.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onManageApps)
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Blokowanie i ukrywanie aplikacji", style = MaterialTheme.typography.bodyLarge)
                    Text(appRulesSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))

            PhoneSettingsSection(
                settings = phoneSettings,
                hasDndAccess = hasDndAccess,
                canWriteSettings = canWriteSettings,
                onChange = onPhoneSettingsChange,
                onGrantDnd = onGrantDnd,
                onGrantWrite = onGrantWrite,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))

            Row {
                TextButton(onClick = onRename) { Text("Zmień nazwę") }
                TextButton(onClick = onDelete) { Text("Usuń tryb", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private val DayNames = listOf(
    DayOfWeek.MONDAY to "Pn", DayOfWeek.TUESDAY to "Wt", DayOfWeek.WEDNESDAY to "Śr", DayOfWeek.THURSDAY to "Cz",
    DayOfWeek.FRIDAY to "Pt", DayOfWeek.SATURDAY to "Sb", DayOfWeek.SUNDAY to "Nd",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRuleDialog(onConfirm: (Set<DayOfWeek>, LocalTime, LocalTime) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(DayNames.take(5).map { it.first }.toSet()) } // domyślnie pon.–pt.
    var startText by remember { mutableStateOf("8:00") }
    var endText by remember { mutableStateOf("16:00") }
    val start = parseTimeOrNull(startText)
    val end = parseTimeOrNull(endText)
    val valid = days.isNotEmpty() && start != null && end != null && start != end

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Pora dnia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dni", style = MaterialTheme.typography.labelLarge)
                // 7 dni w dwóch rzędach, bo w jednym się nie mieszczą.
                DayNames.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (day, label) ->
                            FilterChip(
                                selected = day in days,
                                onClick = { days = if (day in days) days - day else days + day },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text("Od") },
                        isError = start == null,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text("Do") },
                        isError = end == null,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Format 8:00. „Do” wcześniej niż „Od” oznacza okno przez północ, np. 22:00–6:00.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { if (start != null && end != null) onConfirm(days, start, end) }) {
                Text("Dodaj")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
fun CalendarRuleDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var keyword by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Wydarzenie w kalendarzu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tryb będzie podpowiadany, gdy w kalendarzu jest wydarzenie z tym słowem w tytule, " +
                        "trwające albo zaczynające się w ciągu 3 godzin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Słowo, np. lot, pociąg, zjazd") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = keyword.isNotBlank(), onClick = { onConfirm(keyword) }) { Text("Dodaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
