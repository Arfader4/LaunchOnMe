package pl.rafal.contextlauncher.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// Wspólna "obudowa" własnych widżetów: zaokrąglone tło jak na makiecie.
// onClick = null → widżet nie reaguje (np. w trybie edycji układu).
// Krycie tła widżetów launchera (0–100). Ustawienia → Wygląd; ma sens zwłaszcza z tapetą w tle.
val LocalWidgetOpacity = compositionLocalOf { 100 }

@Composable
internal fun WidgetSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit, // lambda "z odbiorcą": wewnątrz działa jak w Column
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = LocalWidgetOpacity.current / 100f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
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

// --- Pod ręką jako widżet ---

@Composable
fun HandyWidget(
    items: List<PinnedItemEntity>,
    onOpenItem: ((PinnedItemEntity) -> Unit)?,
    onOpenAll: (() -> Unit)?,
) {
    WidgetSurface(onClick = onOpenAll) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pod ręką", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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

// --- Folder jako widżet ---

@Composable
fun FolderWidget(
    folder: FolderEntity?,
    subfolderCount: Int,
    apps: List<AppInfo>,
    onOpenFolder: (() -> Unit)?,
    onLaunch: ((AppInfo) -> Unit)?,
) {
    WidgetSurface(onClick = onOpenFolder) {
        if (folder == null) {
            Text("Folder został usunięty", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@WidgetSurface // wyjście z lambdy (jak return z funkcji anonimowej)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                folder.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (subfolderCount > 0) {
                Text(
                    "+$subfolderCount fold.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Tyle ikon, ile zmieści się w bieżącym rozmiarze widżetu.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val slot = 52.dp
            val columns = (maxWidth / slot).toInt().coerceAtLeast(1)
            val rows = (maxHeight / slot).toInt().coerceAtLeast(1)
            Column {
                apps.take(columns * rows).chunked(columns).forEach { row ->
                    Row {
                        row.forEach { app ->
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(slot)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(if (onLaunch != null) Modifier.clickable { onLaunch(app) } else Modifier)
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
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
            Image(
                bitmap = it,
                contentDescription = "Naklejka",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    // graphicsLayer: obrót i odbicie robi karta graficzna, bez przeliczania obrazka.
                    .graphicsLayer {
                        rotationZ = rotation
                        scaleX = if (flipped) -1f else 1f
                    },
            )
        }
    }
}

// Okno naklejki: obrót co 15° i odbicie lustrzane. Każda zmiana zapisuje się od razu.
@Composable
fun StickerDialog(
    rotation: Float,
    flipped: Boolean,
    actionLabel: String?, // null = naklejka bez akcji
    onChange: (rotation: Float, flipped: Boolean) -> Unit,
    onPickAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Naklejka") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Obrót: ${rotation.toInt()}°", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        "Rozmiar i miejsce zmienisz w ⋯ → Zmień układ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
    )
}
