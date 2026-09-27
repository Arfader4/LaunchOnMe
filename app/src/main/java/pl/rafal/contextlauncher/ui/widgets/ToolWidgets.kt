package pl.rafal.contextlauncher.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.rafal.contextlauncher.data.CheckItem
import pl.rafal.contextlauncher.data.Checklist
import pl.rafal.contextlauncher.data.Countdown
import pl.rafal.contextlauncher.data.FavoriteContact
import pl.rafal.contextlauncher.data.GlanceEvent
import pl.rafal.contextlauncher.data.day
import pl.rafal.contextlauncher.system.QuickStates
import pl.rafal.contextlauncher.system.QuickToggle
import pl.rafal.contextlauncher.system.RingerState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Pl = Locale("pl")
private val Hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DayHeader: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM", Pl)
private val FullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Pl)

// ============================== Dziś ==============================

class TodayCallbacks(
    val onOpenDay: () -> Unit,
    val onOpenEvent: (GlanceEvent) -> Unit,
    val onNewEvent: () -> Unit,
    val onGrant: () -> Unit,
)

// Plan na dziś i jutro. Nagłówek → kalendarz, wydarzenie → to wydarzenie, "+" → nowe wydarzenie.
@Composable
fun TodayWidget(agenda: List<GlanceEvent>, hasCalendar: Boolean, callbacks: TodayCallbacks?) {
    val now = rememberCurrentMinute()
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()

    WidgetSurface(onClick = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                today.format(DayHeader).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(if (callbacks != null) Modifier.clickable(onClick = callbacks.onOpenDay) else Modifier),
            )
            if (callbacks != null && hasCalendar) SmallRoundButton("+", "Nowe wydarzenie", callbacks.onNewEvent)
        }
        Spacer(Modifier.height(6.dp))
        when {
            !hasCalendar -> HintText("Dotknij, aby pokazać plan z kalendarza", callbacks?.onGrant)
            agenda.isEmpty() -> HintText("Dziś i jutro nic w kalendarzu 🎉", callbacks?.onOpenDay)
            // W edycji układu lista nie przewija się — inaczej "zjadałaby" gest przesuwania widżetu.
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), userScrollEnabled = callbacks != null) {
                var lastDay: LocalDate? = null
                agenda.forEach { event ->
                    val day = event.day(zone)
                    if (day != lastDay && day != today) {
                        item(key = "h$day") {
                            Text(
                                if (day == today.plusDays(1)) "Jutro" else day.format(DayHeader).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    lastDay = day
                    item(key = "e${event.id}-${event.begin}") {
                        AgendaRow(event, now, onClick = callbacks?.let { cb -> { cb.onOpenEvent(event) } })
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(event: GlanceEvent, now: Instant, onClick: (() -> Unit)?) {
    val zone = ZoneId.systemDefault()
    val ongoing = !event.allDay && event.begin <= now.toEpochMilli()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 3.dp),
    ) {
        // Kolorowa kreska jak w aplikacji Kalendarz; trwające wydarzenie w kolorze trybu.
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (ongoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(event.title.ifBlank { "(bez tytułu)" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    event.allDay -> "Cały dzień"
                    ongoing -> "Teraz · do " + Instant.ofEpochMilli(event.end).atZone(zone).format(Hm)
                    else -> Instant.ofEpochMilli(event.begin).atZone(zone).format(Hm) + "–" + Instant.ofEpochMilli(event.end).atZone(zone).format(Hm)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============================== Odliczanie ==============================

@Composable
fun CountdownWidget(countdown: Countdown, onClick: (() -> Unit)?) {
    val now = rememberCurrentMinute()
    WidgetSurface(onClick = onClick) {
        val date = countdown.date
        if (date == null) {
            Text("Odliczanie", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            HintText("Dotknij, aby ustawić datę${if (countdown.title.isNotBlank()) " — ${countdown.title}" else ""}", null)
            return@WidgetSurface
        }
        // ChronoUnit.DAYS.between ≈ (date - today).TotalDays w C#, ale na pełnych dniach kalendarzowych.
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, date)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val big = (maxHeight.value / 2.4f).coerceIn(28f, 64f).sp
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                Text(
                    countdown.title.ifBlank { "Odliczanie" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        when {
                            days == 0L -> "Dziś!"
                            days > 0 -> "$days"
                            else -> "${-days}"
                        },
                        fontSize = big,
                        lineHeight = big,
                        fontWeight = FontWeight.SemiBold,
                        color = if (days == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (days != 0L) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (if (days > 0) daysWord(days) else daysWord(-days) + " temu"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Text(date.format(FullDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Polska odmiana: 1 dzień, 2–4 dni, 5 dni… (i 22 dni, 25 dni). Tu akurat "dni" wystarcza dla wszystkiego poza 1.
private fun daysWord(n: Long) = if (n == 1L) "dzień" else "dni"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownDialog(initial: Countdown, onSave: (Countdown) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    // DatePicker liczy w milisekundach UTC od północy — stąd konwersje przez ZoneOffset.UTC.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = (initial.date ?: LocalDate.now().plusDays(7)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val date = pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                onSave(Countdown(title.trim(), date))
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Do czego odliczamy?") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        DatePicker(state = pickerState, title = null, headline = null, showModeToggle = false)
    }
}

// ============================== Lista ==============================

class ChecklistCallbacks(
    val onToggle: (index: Int) -> Unit,
    val onAdd: () -> Unit,
    val onRename: () -> Unit,
    val onClearDone: () -> Unit,
)

// Lista do odhaczania. Dotknięcie pozycji = zrobione / niezrobione; zrobione spadają na koniec.
@Composable
fun ChecklistWidget(list: Checklist, callbacks: ChecklistCallbacks?) {
    WidgetSurface(onClick = null) {
        val doneCount = list.items.count { it.done }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                list.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(if (callbacks != null) Modifier.clickable(onClick = callbacks.onRename) else Modifier),
            )
            if (list.items.isNotEmpty()) {
                Text("$doneCount/${list.items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
            }
            if (callbacks != null) {
                if (doneCount > 0) SmallRoundButton("🧹", "Usuń zrobione", callbacks.onClearDone)
                SmallRoundButton("+", "Dodaj pozycję", callbacks.onAdd)
            }
        }
        if (list.items.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            HintText("Pusto. Dotknij +, aby dodać (kilka linijek = kilka pozycji).", callbacks?.onAdd)
            return@WidgetSurface
        }
        // Indeksy z oryginalnej listy, żeby po posortowaniu odhaczyć właściwą pozycję.
        val ordered = list.items.withIndex().sortedBy { it.value.done }
        LazyColumn(userScrollEnabled = callbacks != null) {
            items(ordered, key = { it.index }) { (index, item) ->
                CheckRow(item, onClick = callbacks?.let { cb -> { cb.onToggle(index) } })
            }
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 32.dp),
    ) {
        Checkbox(
            checked = item.done,
            onCheckedChange = onClick?.let { { _: Boolean -> it() } },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(32.dp),
        )
        Text(
            item.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Jedno pole na kilka pozycji naraz: każda linijka to osobna pozycja (wygodne przy zakupach).
@Composable
fun ChecklistAddDialog(onAdd: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Dodaj do listy") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("mleko\nchleb\njajka") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text.lines().map { it.trim().removePrefix("•").trim() }.filter { it.isNotEmpty() }) }) { Text("Dodaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// ============================== Szybkie przełączniki ==============================

@Composable
fun QuickTogglesWidget(states: QuickStates, onToggle: ((QuickToggle) -> Unit)?) {
    WidgetSurface(onClick = null) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            QuickToggle.entries
                .filter { it != QuickToggle.TORCH || states.torchAvailable }
                .forEach { toggle ->
                    val (symbol, on, label) = when (toggle) {
                        QuickToggle.TORCH -> Triple("🔦", states.torch, toggle.label)
                        QuickToggle.DND -> Triple("🌙", states.dnd, toggle.label)
                        QuickToggle.RINGER -> when (states.ringer) {
                            RingerState.NORMAL -> Triple("🔔", true, "Dzwonek")
                            RingerState.VIBRATE -> Triple("📳", false, "Wibracje")
                            RingerState.SILENT -> Triple("🔕", false, "Cisza")
                        }
                        QuickToggle.ROTATION -> Triple("⟳", states.rotation, toggle.label)
                        QuickToggle.INTERNET -> Triple("📶", states.wifi, if (states.wifi) "Wi-Fi" else toggle.label)
                        QuickToggle.BLUETOOTH -> Triple("ᛒ", states.bluetooth, toggle.label)
                    }
                    ToggleButton(symbol, label, on, onClick = onToggle?.let { { it(toggle) } })
                }
        }
    }
}

@Composable
private fun ToggleButton(symbol: String, label: String, on: Boolean, onClick: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, onClick = onClick) else Modifier)
            .padding(2.dp)
            .semantics { contentDescription = "$label: ${if (on) "włączone" else "wyłączone"}" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                symbol,
                fontSize = 18.sp,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================== Ulubione kontakty ==============================

class ContactCallbacks(
    val onDial: (String) -> Unit,
    val onSms: (String) -> Unit,
    val onOpen: (FavoriteContact) -> Unit,
    val onOpenContacts: () -> Unit,
    val onGrant: () -> Unit,
)

@Composable
fun ContactsWidget(contacts: List<FavoriteContact>, hasPermission: Boolean, callbacks: ContactCallbacks?) {
    WidgetSurface(onClick = null) {
        when {
            !hasPermission -> HintText("Dotknij, aby pokazać ulubione kontakty", callbacks?.onGrant)
            contacts.isEmpty() -> HintText("Oznacz kontakty gwiazdką w aplikacji Kontakty", callbacks?.onOpenContacts)
            else -> LazyRow(
                userScrollEnabled = callbacks != null,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(contacts, key = { it.id }) { contact -> ContactBubble(contact, callbacks) }
            }
        }
    }
}

// Dotknięcie kontaktu rozwija małe menu: zadzwoń / SMS / karta kontaktu.
@Composable
private fun ContactBubble(contact: FavoriteContact, callbacks: ContactCallbacks?) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(if (callbacks != null) Modifier.clickable { menu = true } else Modifier),
        ) {
            Avatar(contact, 44)
            Text(
                contact.name.substringBefore(' '),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (callbacks != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                contact.number?.let { number ->
                    DropdownMenuItem(text = { Text("📞  Zadzwoń") }, onClick = { menu = false; callbacks.onDial(number) })
                    DropdownMenuItem(text = { Text("💬  SMS") }, onClick = { menu = false; callbacks.onSms(number) })
                }
                DropdownMenuItem(text = { Text("👤  ${contact.name}") }, onClick = { menu = false; callbacks.onOpen(contact) })
            }
        }
    }
}

@Composable
private fun Avatar(contact: FavoriteContact, sizeDp: Int) {
    val photo = contact.photo
    if (photo != null) {
        Image(bitmap = photo, contentDescription = null, modifier = Modifier.size(sizeDp.dp).clip(CircleShape))
    } else {
        // Kolor z nazwy: ten sam kontakt zawsze ma ten sam kolor (hashCode jak GetHashCode w C#).
        val palette = listOf(0xFF8FB2FF, 0xFF7FD6AE, 0xFFF0A844, 0xFFFF9A7F, 0xFFB9A5FF, 0xFF6FC7D9)
        val color = Color(palette[Math.floorMod(contact.name.hashCode(), palette.size)])
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(color),
        ) {
            val initials = contact.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
            Text(initials, color = Color(0xFF16181B), fontWeight = FontWeight.SemiBold)
        }
    }
}

// ============================== Wspólne ==============================

@Composable
private fun HintText(text: String, onClick: (() -> Unit)?) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun SmallRoundButton(symbol: String, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}
