package pl.rafal.contextlauncher.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import pl.rafal.contextlauncher.data.CardFolderData
import pl.rafal.contextlauncher.data.folderRows
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import pl.rafal.contextlauncher.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.StickerStore
import pl.rafal.contextlauncher.data.db.FolderEntity
import pl.rafal.contextlauncher.data.db.PinnedItemEntity
import pl.rafal.contextlauncher.ui.KindBadge
import pl.rafal.contextlauncher.ui.LocalWidgetCorner
import pl.rafal.contextlauncher.ui.LocalShowFolderLabels
import pl.rafal.contextlauncher.ui.LocalLabelScale
import pl.rafal.contextlauncher.ui.LocalShowAppLabels
import pl.rafal.contextlauncher.ui.IconLabel
import pl.rafal.contextlauncher.ui.iconMetrics
import pl.rafal.contextlauncher.ui.LocalNotifiedApps
import pl.rafal.contextlauncher.ui.FolderBadge
import androidx.compose.ui.unit.Dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// Wspólna "obudowa" własnych widżetów: zaokrąglone tło jak na makiecie.
// onClick = null → widżet nie reaguje (np. w trybie edycji układu).
// Krycie tła widżetów launchera (0–100). Ustawienia → Wygląd; ma sens zwłaszcza z tapetą w tle.
val LocalWidgetOpacity = compositionLocalOf { 100 }
// Własny kolor tła konkretnego widżetu (⚙ → Wygląd); null = kolor powierzchni ze schematu.
val LocalWidgetBg = compositionLocalOf<Long?> { null }

@Composable
internal fun WidgetSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit, // lambda "z odbiorcą": wewnątrz działa jak w Column
) {
    // Mały widżet (np. jeden rząd) dostaje mniejsze odstępy, żeby treść w ogóle się zmieściła.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val small = maxHeight < 96.dp || maxWidth < 96.dp
        val custom = LocalWidgetBg.current?.let { androidx.compose.ui.graphics.Color(it) }
        val bg = custom ?: MaterialTheme.colorScheme.surface
        // Na własnym tle tekst dobieramy do jego jasności (ciemny na jasnym, jasny na ciemnym).
        val contentColor = if (custom == null) androidx.compose.material3.LocalContentColor.current
            else if (custom.luminance() > 0.5f) androidx.compose.ui.graphics.Color(0xFF15171A) else androidx.compose.ui.graphics.Color(0xFFF2F2F2)
        // Własne tło: także kolory "onSurface" z motywu (drugorzędne napisy) dopasowujemy do niego.
        val scheme = if (custom == null) MaterialTheme.colorScheme
            else MaterialTheme.colorScheme.copy(onSurface = contentColor, onSurfaceVariant = contentColor.copy(alpha = 0.7f))
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (small) 2.dp else 4.dp)
                .clip(RoundedCornerShape(minOf(LocalWidgetCorner.current, maxHeight / 3))) // zaokrąglenie z Ustawień → Zaawansowane
                .background(bg.copy(alpha = LocalWidgetOpacity.current / 100f))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = if (small) 8.dp else 14.dp, vertical = if (small) 4.dp else 14.dp),
            content = content,
        )
        }
        }
    }
}

// Bieżący czas, odświeżany dokładnie na początku każdej minuty (nie co sekundę, żeby oszczędzać baterię).
@Composable
internal fun rememberCurrentMinute(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = Instant.now()
        }
    }
    return now
}

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// --- Dwa zegary ---

// Strefy do wyboru: identyfikator IANA + polska nazwa miasta.
val ClockZones = listOf(
    "Europe/London" to "Londyn",
    "Europe/Lisbon" to "Lizbona",
    "Europe/Paris" to "Paryż",
    "Europe/Athens" to "Ateny",
    "Europe/Istanbul" to "Stambuł",
    "Asia/Dubai" to "Dubaj",
    "Asia/Kolkata" to "Delhi",
    "Asia/Bangkok" to "Bangkok",
    "Asia/Tokyo" to "Tokio",
    "Australia/Sydney" to "Sydney",
    "America/New_York" to "Nowy Jork",
    "America/Chicago" to "Chicago",
    "America/Los_Angeles" to "Los Angeles",
    "America/Sao_Paulo" to "São Paulo",
)

fun zoneLabel(zoneId: String): String =
    ClockZones.firstOrNull { it.first == zoneId }?.second
        ?: zoneId.substringAfterLast('/').replace('_', ' ')

@Composable
fun DualClockWidget(zoneId: String, onClick: (() -> Unit)?) {
    val now = rememberCurrentMinute()
    val here = ZoneId.systemDefault()
    // runCatching: gdyby w bazie była nieznana strefa, pokazujemy czas UTC zamiast awarii.
    val there = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC"))

    // Różnica w godzinach, np. -1 albo +5,5 (Indie mają przesunięcie o pół godziny).
    val diffSeconds = there.rules.getOffset(now).totalSeconds - here.rules.getOffset(now).totalSeconds
    val diffText = when {
        diffSeconds == 0 -> "ten sam czas"
        diffSeconds % 3600 == 0 -> "%+d h".format(diffSeconds / 3600).replace('-', '−')
        else -> "%+.1f h".format(diffSeconds / 3600f).replace('-', '−').replace('.', ',')
    }

    WidgetSurface(onClick = onClick) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val dSize = widgetSizeOf(maxWidth + 16.dp, maxHeight + 8.dp)
        // 1×1 / 2×2: tylko czas "za granicą" (swój widać na pasku stanu) + nazwa miasta drobnym drukiem.
        if (dSize == WidgetSize.TINY || dSize == WidgetSize.SMALL) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                FitText(
                    now.atZone(there).format(TimeFormat),
                    Modifier.fillMaxWidth().weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                FitText(
                    zoneLabel(zoneId),
                    Modifier.fillMaxWidth(),
                    maxSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }
        // Niski pasek: oba czasy w jednej linii — maleją, zamiast się zawijać.
        if (dSize == WidgetSize.STRIP) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                FitText(now.atZone(here).format(TimeFormat), Modifier.weight(0.4f), maxSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                FitText(
                    "${zoneLabel(zoneId)} ${now.atZone(there).format(TimeFormat)}",
                    Modifier.weight(0.6f),
                    maxSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            return@BoxWithConstraints
        }
      Column {
        Text("Tutaj", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(now.atZone(here).format(TimeFormat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Text(
            "${zoneLabel(zoneId)} · $diffText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            now.atZone(there).format(TimeFormat),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
      }
      }
    }
}

// Wybór drugiej strefy czasowej (przy dodawaniu widżetu i po jego dotknięciu).
@Composable
fun ZonePickerDialog(onPick: (zoneId: String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Druga strefa czasowa") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(ClockZones) { (id, label) -> // dekonstrukcja pary w parametrach lambdy
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(id) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// --- Notatka trybu ---

@Composable
fun ModeNoteWidget(text: String, onClick: (() -> Unit)?) {
  AdaptiveWidget { size, _, _ ->
    // 1×1 / 2×2: sama ikonka notatki — delikatnie "mruga", gdy coś jest zapisane.
    if (size == WidgetSize.TINY || size == WidgetSize.SMALL) {
        CompactTile(
            icon = R.drawable.ic_w_note,
            onClick = onClick,
            label = if (size == WidgetSize.SMALL) text.lineSequence().firstOrNull { it.isNotBlank() }?.take(20) ?: "Notatka" else null,
            pulse = text.isNotBlank(),
            tint = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        return@AdaptiveWidget
    }
    WidgetSurface(onClick = onClick) {
        Text("Notatka", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(
            text.ifBlank { "Dotknij, aby napisać" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
        )
    }
  }
}

@Composable
fun NoteWidgetDialog(initialText: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Notatka trybu") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// --- OnHand jako widżet ---

@Composable
fun HandyWidget(
    items: List<PinnedItemEntity>,
    onOpenItem: ((PinnedItemEntity) -> Unit)?,
    onOpenAll: (() -> Unit)?,
) {
  AdaptiveWidget { size, _, _ ->
    // Mały: spinacz z liczbą przypiętych rzeczy; dotknięcie otwiera całe OnHand.
    if (size == WidgetSize.TINY || size == WidgetSize.SMALL) {
        CompactTile(
            icon = R.drawable.ic_w_pin,
            onClick = onOpenAll,
            label = if (size == WidgetSize.SMALL) "OnHand" else null,
            badge = items.size.takeIf { it > 0 }?.toString(),
        )
        return@AdaptiveWidget
    }
    WidgetSurface(onClick = onOpenAll) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("OnHand", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${items.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(6.dp))
        if (items.isEmpty()) {
            Text(
                "Pusto. Przypnij bilet albo link przez „Udostępnij”.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // BoxWithConstraints: widżet wie, ile ma miejsca, i pokazuje tyle elementów, ile się zmieści.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rowHeight = 38.dp
            val columns = if (maxWidth > 320.dp) 2 else 1 // szeroki widżet: dwie kolumny
            val rows = (maxHeight / rowHeight).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // take ≈ Take z LINQ, chunked ≈ Chunk: dzielimy na wiersze po "columns" elementów.
                items.take(rows * columns).chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(if (onOpenItem != null) Modifier.clickable { onOpenItem(item) } else Modifier)
                                    .padding(vertical = 2.dp),
                            ) {
                                KindBadge(item, size = 30)
                                Spacer(Modifier.width(10.dp))
                                Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (row.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
  }
}

// --- Folder jako widżet ---

// Rozmiar decyduje o treści:
//  1×1 / 2×2 — wygląda jak ikona aplikacji: symbol (albo miniatura) + nazwa pod spodem,
//  pasek     — symbol i rząd aplikacji do uruchomienia jednym dotknięciem,
//  większy   — nagłówek, podfoldery (otwierają się w arkuszu) i siatka aplikacji.
@Composable
fun FolderWidget(
    folder: FolderEntity?,
    preview: List<AppInfo>,
    subfolders: List<Pair<FolderEntity, List<AppInfo>>>, // podfolder + jego miniatura
    apps: List<AppInfo>,
    onOpenFolder: ((folderId: Long) -> Unit)?,
    onLaunch: ((AppInfo) -> Unit)?,
    grid: Int = 2, // styl miniatury (foldery na karcie: 2×2 albo 3×3)
    align: String = CardFolderData.ALIGN_START, // wyrównanie niepełnego rzędu w dużym widżecie
    fromBottom: Boolean = false,                 // pełne rzędy przy dole widżetu
) {
    if (folder == null) {
        WidgetSurface(onClick = null) {
            FitText("Folder usunięty", maxSize = 13.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val open = onOpenFolder?.let { { it(folder.id) } }
    val showName = LocalShowFolderLabels.current
    // Kropka powiadomień na folderze, gdy którakolwiek aplikacja w środku ją ma (jak w launcherze Androida).
    val notified = LocalNotifiedApps.current
    val dot = apps.any { it.packageName in notified }
    AdaptiveWidget { measured, w, h ->
        // 4×2 ma ok. 90–100 dp wysokości — dla folderu to jeszcze pasek, nie siatka.
        val size = if (measured == WidgetSize.LARGE && h < 110.dp && w > h * 1.6f) WidgetSize.STRIP else measured
        // Wąski i wysoki (1×n): znaczek u góry, a pod nim aplikacje w jednej kolumnie.
        val tall = size == WidgetSize.LARGE && w < 125.dp && h > w * 1.6f
        // Marginesy WidgetSurface: mały widżet 2+8 w poziomie / 2+4 w pionie z każdej strony, duży 4+14.
        val smallPad = h < 96.dp || w < 96.dp
        val padH = if (smallPad) 20.dp else 36.dp
        val padV = if (smallPad) 12.dp else 36.dp
        if (tall) {
            WidgetSurface(onClick = open) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                    val badge = (w - padH).coerceIn(20.dp, 44.dp)
                    Box {
                        FolderBadge(folder, preview, badge, grid = grid, subfolders = subfolders)
                        if (dot) NotificationDot(Modifier.align(Alignment.TopEnd), badge * 0.22f)
                    }
                    Spacer(Modifier.height(6.dp))
                    val room = h - padV - badge - 6.dp
                    val slots = (room / (badge * 0.85f)).toInt().coerceAtLeast(0)
                    val entries: List<Any> = subfolders + apps
                    if (slots > 0 && entries.isNotEmpty()) {
                        val slot = minOf(room / slots, w - padH)
                        entries.take(slots).forEach { entry -> FolderEntryIcon(entry, slot, onLaunch, onOpenFolder) }
                    }
                }
            }
        } else when (size) {
            WidgetSize.TINY, WidgetSize.SMALL -> FolderAsIcon(folder, preview, showName, w, h, open, dot, grid, subfolders)
            WidgetSize.STRIP -> WidgetSurface(onClick = open) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                    val badge = (h - padV).coerceIn(20.dp, 44.dp)
                    Box {
                        FolderBadge(folder, preview, badge, grid = grid, subfolders = subfolders)
                        if (dot) NotificationDot(Modifier.align(Alignment.TopEnd), badge * 0.22f)
                    }
                    Spacer(Modifier.width(8.dp))
                    // Miejsce na ikony: szerokość minus marginesy widżetu (2+8 z każdej strony), znaczek i odstęp.
                    // Ikony mogą być ok. 15% mniejsze od znaczka — mieści się o jedną więcej, a i tak wypełniają rząd.
                    val room = w - padH - badge - 8.dp
                    val slots = (room / (badge * 0.85f)).toInt().coerceAtLeast(0)
                    val entries: List<Any> = subfolders + apps
                    if (entries.isEmpty() || slots == 0) {
                        FitText(folder.name, modifier = Modifier.weight(1f), maxSize = 16.sp)
                    } else {
                        val slot = room / slots
                        entries.take(slots).forEach { entry -> FolderEntryIcon(entry, slot, onLaunch, onOpenFolder) }
                    }
                }
            }
            WidgetSize.LARGE -> WidgetSurface(onClick = open) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FolderBadge(folder, preview, 24.dp)
                    if (showName) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            folder.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        "${apps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Kolumny tak, żeby ikony wypełniły całą szerokość (w - poziome marginesy WidgetSurface).
                val inner = w - if (h < 96.dp || w < 96.dp) 20.dp else 36.dp // marginesy WidgetSurface
                val columns = (inner / 52.dp).toInt().coerceAtLeast(1)
                val slot = inner / columns
                val rows = ((h - 72.dp) / slot).toInt().coerceAtLeast(1)
                val entries: List<Any> = (subfolders + apps).take(columns * rows) // podfoldery najpierw
                val arrangement = when (align) {
                    CardFolderData.ALIGN_CENTER -> Arrangement.Center
                    CardFolderData.ALIGN_END -> Arrangement.End
                    else -> Arrangement.Start
                }
                Column(
                    verticalArrangement = if (fromBottom) Arrangement.Bottom else Arrangement.Top,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    folderRows(entries.size, columns, fromBottom).map { entries.slice(it) }.forEach { row ->
                        Row(horizontalArrangement = arrangement, modifier = Modifier.fillMaxWidth()) {
                            row.forEach { entry ->
                                if (entry is AppInfo) {
                                    AppIcon(entry, slot, onLaunch)
                                } else {
                                    val pair = entry as Pair<*, *>
                                    val sub = pair.first as FolderEntity
                                    @Suppress("UNCHECKED_CAST")
                                    val subPreview = pair.second as List<AppInfo>
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(slot)
                                            .clip(RoundedCornerShape(12.dp))
                                            .then(if (onOpenFolder != null) Modifier.clickable { onOpenFolder(sub.id) } else Modifier),
                                    ) { FolderBadge(sub, subPreview, slot * 0.78f) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Pozycja w pasku folderu: aplikacja albo podfolder (miniatura, dotknięcie go otwiera).
@Composable
private fun FolderEntryIcon(entry: Any, slot: Dp, onLaunch: ((AppInfo) -> Unit)?, onOpenFolder: ((Long) -> Unit)?) {
    if (entry is AppInfo) {
        AppIcon(entry, slot, onLaunch)
    } else {
        val pair = entry as Pair<*, *>
        val sub = pair.first as FolderEntity
        @Suppress("UNCHECKED_CAST")
        val subPreview = pair.second as List<AppInfo>
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(slot)
                .clip(RoundedCornerShape(12.dp))
                .then(if (onOpenFolder != null) Modifier.clickable { onOpenFolder(sub.id) } else Modifier),
        ) { FolderBadge(sub, subPreview, slot * 0.8f) }
    }
}

// Folder w rozmiarze ikony: bez tła widżetu, jak aplikacja na karcie.
@Composable
private fun FolderAsIcon(
    folder: FolderEntity,
    preview: List<AppInfo>,
    withName: Boolean,
    w: Dp,
    h: Dp,
    onClick: (() -> Unit)?,
    dot: Boolean,
    grid: Int,
    subfolders: List<Pair<FolderEntity, List<AppInfo>>> = emptyList(),
) {
    // Te same wymiary co ikona aplikacji (AppTile) — folder i aplikacje obok stoją w jednej linii.
    val appLabels = LocalShowAppLabels.current
    val metrics = iconMetrics(w, h, withLabel = withName || appLabels)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = metrics.pad),
    ) {
        Box {
            FolderBadge(folder, preview, metrics.icon, grid = grid, subfolders = subfolders)
            if (dot) NotificationDot(Modifier.align(Alignment.TopEnd), metrics.icon * 0.22f)
        }
        when {
            withName && appLabels -> IconLabel(folder.name, metrics)
            appLabels -> Spacer(Modifier.height(metrics.labelDp)) // miejsce na podpis jak u aplikacji obok
            withName -> IconLabel(folder.name, metrics)
        }
    }
}

@Composable
private fun NotificationDot(modifier: Modifier, size: Dp) {
    Box(
        modifier
            .size(size.coerceAtLeast(8.dp))
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun AppIcon(app: AppInfo, slot: Dp, onLaunch: ((AppInfo) -> Unit)?) {
    Image(
        bitmap = app.icon,
        contentDescription = app.label,
        modifier = Modifier
            .size(slot)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onLaunch != null) Modifier.clickable { onLaunch(app) } else Modifier)
            .padding(slot * 0.1f),
    )
}

// --- Naklejka ---

// Obrazek bez tła i ramki. Wczytywany w tle (LaunchedEffect) i pomniejszony, żeby nie zapchać pamięci.
// Dotknięcie: akcja naklejki (jeśli ustawiona), przytrzymanie: ustawienia naklejki.
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun StickerWidget(
    path: String,
    rotation: Float,
    flipped: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    shape: StickerShape = StickerShape.NONE,
    frame: StickerFrame = StickerFrame.NONE,
) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = StickerStore.load(path)?.asImageBitmap()
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .then(
                if (onClick != null) {
                    // combinedClickable = osobne zdarzenia Click i LongClick (jak w kontrolce z menu kontekstowym).
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            ),
    ) {
        bitmap?.let {
            // graphicsLayer: obrót całości (razem z ramką) robi karta graficzna, bez przeliczania obrazka.
            Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }) {
                StickerContent(it, shape, frame, flipped)
            }
        }
    }
}

// Okno naklejki na karcie: tylko to, co dotyczy KARTY (obrót, odbicie, akcja). Wygląd samej naklejki
// (wycięcie, kształt, ramka, krawędź) poprawia się w StickOnMe. Każda zmiana zapisuje się od razu.
@Composable
fun StickerDialog(
    rotation: Float,
    flipped: Boolean,
    actionLabel: String?, // null = naklejka bez akcji
    onChange: (rotation: Float, flipped: Boolean) -> Unit,
    onPickAction: () -> Unit,
    onDismiss: () -> Unit,
    hasOriginal: Boolean = false,         // jest pierwsza wersja (sprzed poprawek) → można do niej wrócić
    onRestore: () -> Unit = {},
    onEditInStudio: (() -> Unit)? = null, // null = brak (np. naklejka bez pliku)
    legacyLook: Boolean = false,          // stara naklejka z kształtem/ramką ustawionymi jeszcze na karcie
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Naklejka") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (onEditInStudio != null) {
                    Button(onClick = onEditInStudio, modifier = Modifier.fillMaxWidth()) { Text("Edytuj w StickOnMe") }
                    Text(
                        "Wycięcie, kształt, ramka i krawędź." +
                            if (legacyLook) " Kształt i ramka z karty przejdą do edytora." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasOriginal) TextButton(onClick = onRestore) { Text("Przywróć pierwszą wersję") }

                Text("Obrót: ${rotation.toInt()}°", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onChange((rotation - 15f) % 360f, flipped) }) { Text("↺ 15°") }
                    TextButton(onClick = { onChange((rotation + 15f) % 360f, flipped) }) { Text("↻ 15°") }
                    TextButton(onClick = { onChange(0f, false) }) { Text("Zeruj") }
                }
                TextButton(onClick = { onChange(rotation, !flipped) }) { Text(if (flipped) "Cofnij odbicie" else "Odbij lustrzanie") }
                Text("Po dotknięciu", style = MaterialTheme.typography.titleSmall)
                Text(
                    actionLabel ?: "Nic — tylko ozdoba",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (actionLabel != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onPickAction) { Text(if (actionLabel != null) "Zmień akcję" else "Ustaw akcję") }
                Text(
                    "Z akcją: dotknięcie ją wykonuje, a te ustawienia otworzysz przytrzymaniem naklejki. " +
                        "Rozmiar i miejsce zmienisz w edycji układu (przytrzymaj kartę).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
    )
}
